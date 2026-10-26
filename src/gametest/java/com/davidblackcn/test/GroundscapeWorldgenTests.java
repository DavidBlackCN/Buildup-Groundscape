package com.davidblackcn.test;

import com.davidblackcn.registry.GroundscapeBlocks;
import com.davidblackcn.registry.SurfaceFamily;
import com.davidblackcn.worldgen.GroundscapeWorldgenConfig;
import com.davidblackcn.worldgen.GroundscapeWorldgen;
import com.davidblackcn.worldgen.SurfaceNoise;
import com.davidblackcn.worldgen.SlopePlanner;
import com.davidblackcn.worldgen.SlopeTerrain;
import com.davidblackcn.worldgen.SurfaceProfile;
import com.davidblackcn.worldgen.SurfaceAnalyzer;
import com.davidblackcn.worldgen.SodPatchFeature;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/** G1's opt-in, deterministic sampling and conservative world-generation safety contracts. */
public final class GroundscapeWorldgenTests {
    private static final long SEED = 0x4D435F47524F554EL;
    private static final SurfaceProfile GRASS = new SurfaceProfile(SurfaceFamily.GRASS, 1.0);
    private static final BlockPos TARGET = new BlockPos(3, 4, 3);

    @GameTest
    public void configurationDefaultsAreOffAndMissingFilesStayOptIn(GameTestHelper helper) throws IOException {
        GroundscapeWorldgenConfig defaults = GroundscapeWorldgenConfig.defaults();
        helper.assertFalse(defaults.enabled(), "new installations must not alter natural terrain");
        helper.assertValueEqual(defaults.density(), 0.12, "conservative default density");
        helper.assertValueEqual(defaults.maxLayers(), 7, "S1 maximum natural height");
        Path directory = Files.createTempDirectory(Path.of("."), "groundscape-config-default-");
        Path file = directory.resolve("worldgen.json");
        try {
            helper.assertValueEqual(GroundscapeWorldgenConfig.load(file), defaults, "missing file uses disabled defaults");
            helper.assertValueEqual(GroundscapeWorldgenConfig.load(file), defaults, "generated configuration remains disabled");
            Files.writeString(file, "{}\n");
            helper.assertValueEqual(GroundscapeWorldgenConfig.load(file), defaults, "omitted settings use defaults");
            helper.assertValueEqual(Files.readString(file), "{}\n", "loading does not rewrite user configuration");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        helper.succeed();
    }

    @GameTest
    public void validConfigurationRetainsUserValuesAndOriginalText(GameTestHelper helper) throws IOException {
        Path directory = Files.createTempDirectory(Path.of("."), "groundscape-config-valid-");
        Path file = directory.resolve("worldgen.json");
        try {
            for (double density : List.of(0.0, 0.16, 0.35)) {
                for (int layers = 1; layers <= 7; layers++) {
                    String json = "{ \"enabled\": true, \"density\": " + density + ", \"maxLayers\": " + layers + " }\n";
                    Files.writeString(file, json);
                    helper.assertValueEqual(GroundscapeWorldgenConfig.load(file),
                            new GroundscapeWorldgenConfig(true, density, layers), "valid configuration is preserved");
                    helper.assertValueEqual(Files.readString(file), json, "valid user file is not normalized or overwritten");
                }
            }
            Files.writeString(file, "{\"enabled\":true}");
            helper.assertValueEqual(GroundscapeWorldgenConfig.load(file),
                    new GroundscapeWorldgenConfig(true, 0.12, 7), "explicit opt-in may use default density and height");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        helper.succeed();
    }

    @GameTest
    public void invalidConfigurationFailsClosedWithoutDestroyingUserText(GameTestHelper helper) throws IOException {
        Path directory = Files.createTempDirectory(Path.of("."), "groundscape-config-invalid-");
        Path file = directory.resolve("expected-invalid-worldgen.json");
        List<String> documents = List.of("", "{", "null", "[]", "true",
                "{enabled:true}", "{'enabled':true}", "{/*comment*/\"enabled\":true}",
                "{\"enabled\":true} {}", "{\"enabled\":TRUE}", "{\"enabled\":true,}",
                "{\"enabled\":\"true\"}", "{\"enabled\":null}",
                "{\"enabled\":true,\"density\":\"0.16\"}", "{\"enabled\":true,\"density\":null}",
                "{\"enabled\":true,\"density\":-0.01}", "{\"enabled\":true,\"density\":0.350001}",
                "{\"enabled\":true,\"density\":1e309}",
                "{\"enabled\":true,\"maxLayers\":0}", "{\"enabled\":true,\"maxLayers\":8}",
                "{\"enabled\":true,\"maxLayers\":2.5}", "{\"enabled\":true,\"maxLayers\":\"2\"}");
        try {
            for (String document : documents) {
                Files.writeString(file, document);
                helper.assertValueEqual(GroundscapeWorldgenConfig.load(file), GroundscapeWorldgenConfig.defaults(),
                        "invalid configuration disables generation: " + document);
                helper.assertValueEqual(Files.readString(file), document, "invalid user text remains available for repair");
            }
            helper.assertValueEqual(GroundscapeWorldgenConfig.load(directory), GroundscapeWorldgenConfig.defaults(),
                    "unreadable configuration path fails closed");
            helper.assertTrue(Files.isDirectory(directory), "I/O error must not replace the user's directory");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        helper.succeed();
    }

    @GameTest
    public void configurationConstructorRejectsUnsafeDensityAndHeight(GameTestHelper helper) {
        for (double density : new double[]{-0.01, 0.350001, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            expectIllegalArgument(helper, () -> new GroundscapeWorldgenConfig(true, density, 3));
        }
        for (int layers : new int[]{Integer.MIN_VALUE, 0, 8, Integer.MAX_VALUE}) {
            expectIllegalArgument(helper, () -> new GroundscapeWorldgenConfig(true, 0.16, layers));
        }
        helper.succeed();
    }

    @GameTest
    public void nativeSurfaceFamiliesResolveAndSensitiveBiomesAreExcluded(GameTestHelper helper) {
        for (SurfaceFamily family : SurfaceFamily.values()) {
            var profile = SurfaceProfile.forSurface(family.vanillaBlock().defaultBlockState(), biome(helper, nativeBiome(family)));
            helper.assertTrue(profile.isPresent(), "native surface resolves: " + family);
            helper.assertValueEqual(profile.orElseThrow().family(), family, "surface keeps its actual material");
            helper.assertTrue(profile.orElseThrow().coverageScale() > 0 && profile.orElseThrow().coverageScale() <= 1,
                    "material profile only reduces coverage");
        }
        double plains = profile(helper, Biomes.PLAINS).coverageScale();
        helper.assertTrue(profile(helper, Biomes.FOREST).coverageScale() < plains, "forests receive less decoration than plains");
        helper.assertTrue(profile(helper, Biomes.SWAMP).coverageScale() < plains, "swamps receive less decoration than plains");
        for (ResourceKey<Biome> key : List.of(Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.FROZEN_RIVER,
                Biomes.FROZEN_OCEAN, Biomes.DRIPSTONE_CAVES, Biomes.DEEP_DARK, Biomes.GROVE,
                Biomes.JAGGED_PEAKS, Biomes.THE_END, Biomes.OCEAN, Biomes.RIVER, Biomes.BEACH)) {
            helper.assertTrue(SurfaceProfile.forSurface(Blocks.GRASS_BLOCK.defaultBlockState(), biome(helper, key)).isEmpty(),
                    "sensitive biome excluded: " + key);
        }
        helper.succeed();
    }

    @GameTest
    public void unknownMaterialsAndUnmappedForeignBiomesAreLeftAlone(GameTestHelper helper) {
        Holder<Biome> plains = biome(helper, Biomes.PLAINS);
        List<Block> unknown = new ArrayList<>(List.of(Blocks.MUD, Blocks.STONE, Blocks.FARMLAND, Blocks.DIRT_PATH));
        unknown.addAll(GroundscapeBlocks.allBlocks());
        for (Block block : unknown) {
            helper.assertTrue(SurfaceProfile.forSurface(block.defaultBlockState(), plains).isEmpty(),
                    "unrecognized or already decorated ground remains untouched: " + block);
        }
        helper.assertTrue(SurfaceProfile.forSurface(Blocks.GRASS_BLOCK.defaultBlockState(), Holder.direct(plains.value())).isEmpty(),
                "unkeyed biome cannot silently inherit vanilla mapping");
        Holder<Biome> foreign = new NamedTestBiome(plains.value(), "third_party", "plains");
        helper.assertTrue(SurfaceProfile.forSurface(Blocks.GRASS_BLOCK.defaultBlockState(), foreign).isEmpty(),
                "foreign biome with vanilla-like path is conservatively skipped");
        Holder<Biome> unknownMinecraft = new NamedTestBiome(plains.value(), "minecraft", "unmapped_biome");
        helper.assertTrue(SurfaceProfile.forSurface(Blocks.GRASS_BLOCK.defaultBlockState(), unknownMinecraft).isEmpty(),
                "unknown biome does not inherit vanilla mapping merely by using the minecraft namespace");
        helper.succeed();
    }

    @GameTest
    public void worldCoordinateNoiseIsDeterministicAcrossVisitOrdersAndThreads(GameTestHelper helper) {
        List<Column> columns = new ArrayList<>();
        for (int z = -48; z <= 48; z += 3) {
            for (int x = -48; x <= 48; x += 3) columns.add(new Column(x, 64 + Math.floorMod(x + z, 5), z));
        }
        int[] expected = columns.stream().mapToInt(column -> sample(column, SEED, 0.16, 3)).toArray();
        int[] parallel = columns.parallelStream().mapToInt(column -> sample(column, SEED, 0.16, 3)).toArray();
        helper.assertTrue(java.util.Arrays.equals(expected, parallel), "parallel sampling preserves every column result");
        List<Integer> order = new ArrayList<>(IntStream.range(0, columns.size()).boxed().toList());
        Collections.shuffle(order, new Random(17));
        int changesFromSeed = 0;
        for (int index : order) {
            helper.assertValueEqual(sample(columns.get(index), SEED, 0.16, 3), expected[index], "visit order cannot alter terrain");
            if (sample(columns.get(index), SEED + 1, 0.16, 3) != expected[index]) changesFromSeed++;
        }
        helper.assertTrue(changesFromSeed > 0, "world seed actually affects the result");
        helper.succeed();
    }

    @GameTest
    public void densityAndHeightControlsCannotIntroduceOutOfRangeLayers(GameTestHelper helper) {
        int accepted = 0;
        int empty = 0;
        for (int z = -96; z <= 96; z += 2) {
            for (int x = -96; x <= 96; x += 2) {
                Column column = new Column(x, 65, z);
                helper.assertValueEqual(sample(column, SEED, 0, 3), 0, "zero density produces no proposals");
                int lowerDensity = sample(column, SEED, 0.08, 3);
                int maximumDensity = sample(column, SEED, 0.35, 3);
                helper.assertTrue(maximumDensity >= 0 && maximumDensity <= 3, "natural patch height is bounded");
                if (lowerDensity > 0) helper.assertTrue(maximumDensity > 0, "raising density must not remove an accepted column");
                for (int cap = 1; cap <= 3; cap++) {
                    int layers = sample(column, SEED, 0.35, cap);
                    helper.assertTrue(layers >= 0 && layers <= cap, "maxLayers is respected");
                    helper.assertValueEqual(layers == 0, maximumDensity == 0, "height cap does not change coverage");
                }
                if (maximumDensity > 0) accepted++; else empty++;
            }
        }
        helper.assertTrue(accepted > 0 && empty > 0, "fixture contains decorated and undecorated ground");
        helper.succeed();
    }

    @GameTest
    public void densityFieldIsContinuousAcrossPositiveAndNegativeChunkEdges(GameTestHelper helper) {
        final double epsilon = 0.000001;
        int nonRepeatingColumns = 0;
        for (int boundary = -256; boundary <= 256; boundary += 16) {
            for (int other = -71; other <= 71; other += 7) {
                double west = SurfaceNoise.densityScore(SEED, boundary - epsilon, other + 0.37);
                double east = SurfaceNoise.densityScore(SEED, boundary + epsilon, other + 0.37);
                double north = SurfaceNoise.densityScore(SEED, other + 0.37, boundary - epsilon);
                double south = SurfaceNoise.densityScore(SEED, other + 0.37, boundary + epsilon);
                helper.assertTrue(Double.isFinite(west) && west >= 0 && west <= 1, "noise range is bounded");
                helper.assertTrue(Math.abs(west - east) < 0.00001, "no discontinuity at x chunk edge " + boundary);
                helper.assertTrue(Math.abs(north - south) < 0.00001, "no discontinuity at z chunk edge " + boundary);
                if (Math.abs(SurfaceNoise.densityScore(SEED, boundary + 1.25, other)
                        - SurfaceNoise.densityScore(SEED, boundary + 17.25, other)) > 0.00001) nonRepeatingColumns++;
            }
        }
        helper.assertTrue(nonRepeatingColumns > 100, "field does not restart or repeat every 16 blocks");
        helper.succeed();
    }

    @GameTest
    public void denseNaturalPlateausResolveWithoutChangingTheWorld(GameTestHelper helper) {
        for (SurfaceFamily family : SurfaceFamily.values()) {
            preparePlateau(helper, family);
            SurfaceAnalyzer.Analysis result = analyze(helper);
            helper.assertTrue(result.accepted(), "dense native ground is eligible: " + family + " " + result.rejection());
            helper.assertValueEqual(result.profile().family(), family, "analyzer keeps the actual surface material");
            helper.assertTrue(helper.getBlockState(TARGET).isAir(), "analyzer only proposes and never places");
            helper.assertBlockPresent(family.vanillaBlock(), TARGET.below());
        }
        helper.succeed();
    }

    @GameTest
    public void plantsSnowFluidsBlockEntitiesAndHeadroomAreNotOverwritten(GameTestHelper helper) {
        for (Block obstacle : List.of(Blocks.TALL_GRASS, Blocks.WITHER_ROSE, Blocks.SNOW, Blocks.CHEST, Blocks.WATER,
                Blocks.LAVA, Blocks.STONE)) {
            preparePlateau(helper, SurfaceFamily.GRASS);
            helper.setBlock(TARGET, obstacle);
            BlockState before = helper.getBlockState(TARGET);
            helper.assertFalse(analyze(helper).accepted(), "occupied target is protected: " + obstacle);
            helper.assertValueEqual(helper.getBlockState(TARGET), before, "existing target remains untouched");
        }
        for (int height = 1; height <= 2; height++) {
            preparePlateau(helper, SurfaceFamily.GRASS);
            helper.setBlock(TARGET.above(height), Blocks.STONE);
            helper.assertFalse(analyze(helper).accepted(), "low ceiling retains player headroom");
            helper.assertTrue(helper.getBlockState(TARGET).isAir(), "ceiling rejection leaves candidate empty");
        }
        helper.succeed();
    }

    @GameTest
    public void shortGrassAndFernAreEligibleWithoutBeingRemovedByAnalysis(GameTestHelper helper) {
        for (Block plant : List.of(Blocks.SHORT_GRASS, Blocks.FERN)) {
            preparePlateau(helper, SurfaceFamily.GRASS);
            helper.setBlock(TARGET, plant);
            helper.assertTrue(analyze(helper).accepted(), "supported vegetation may coexist: " + plant);
            helper.assertBlockPresent(plant, TARGET);
        }
        helper.succeed();
    }

    @GameTest
    public void pathsFarmlandSnowWaterAndBlockEntitiesProtectNearbyGround(GameTestHelper helper) {
        for (Block protectedBlock : List.of(Blocks.DIRT_PATH, Blocks.FARMLAND, Blocks.SNOW,
                Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW, Blocks.WATER, Blocks.CHEST)) {
            preparePlateau(helper, SurfaceFamily.GRASS);
            BlockPos neighbor = TARGET.east().below();
            helper.setBlock(neighbor, protectedBlock);
            BlockState before = helper.getBlockState(neighbor);
            helper.assertFalse(analyze(helper).accepted(), "protected neighbor is not decorated around: " + protectedBlock);
            helper.assertValueEqual(helper.getBlockState(neighbor), before, "neighbor remains untouched");
            helper.assertTrue(helper.getBlockState(TARGET).isAir(), "protected neighborhood remains undecorated");
        }
        helper.succeed();
    }

    @GameTest
    public void thinShellsAndExposedEdgesAreConservativelySkipped(GameTestHelper helper) {
        for (BlockPos hole : List.of(TARGET.below(2), TARGET.below(3), TARGET.east().below(2),
                TARGET.north().below(2), TARGET.east().north().below(2))) {
            preparePlateau(helper, SurfaceFamily.GRASS);
            helper.setBlock(hole, Blocks.AIR);
            helper.assertFalse(analyze(helper).accepted(), "hollow support or cliff edge is skipped: " + hole);
            helper.assertTrue(helper.getBlockState(hole).isAir(), "analyzer never fills a hole");
        }
        preparePlateau(helper, SurfaceFamily.GRASS);
        helper.setBlock(TARGET.below(2), Blocks.STONE_SLAB);
        helper.assertFalse(analyze(helper).accepted(), "partial block is not a dense supporting layer");
        helper.succeed();
    }

    @GameTest
    public void analyzerStopsAtReadBoundariesAndHonorsSmallBudgets(GameTestHelper helper) {
        preparePlateau(helper, SurfaceFamily.GRASS);
        BlockPos target = helper.absolutePos(TARGET);
        AtomicInteger deniedCalls = new AtomicInteger();
        var refused = SurfaceAnalyzer.analyze(helper.getLevel(), target, pos -> {
            deniedCalls.incrementAndGet();
            return false;
        });
        helper.assertValueEqual(refused.rejection(), SurfaceAnalyzer.Rejection.OUT_OF_BOUNDS, "denied initial read aborts safely");
        helper.assertValueEqual(deniedCalls.get(), 1, "does not probe further after denial");

        AtomicInteger budget = new AtomicInteger();
        var exhausted = SurfaceAnalyzer.analyze(helper.getLevel(), target, pos -> budget.incrementAndGet() <= 4);
        helper.assertValueEqual(exhausted.rejection(), SurfaceAnalyzer.Rejection.OUT_OF_BOUNDS, "exhausted budget refuses candidate");
        helper.assertValueEqual(budget.get(), 5, "first denied budget check aborts without extra reads");

        AtomicInteger acceptedReads = new AtomicInteger();
        var accepted = SurfaceAnalyzer.analyze(helper.getLevel(), target, pos -> {
            acceptedReads.incrementAndGet();
            return fixtureBounds(target).test(pos);
        });
        helper.assertTrue(accepted.accepted(), "bounded dense fixture remains eligible");
        helper.assertTrue(acceptedReads.get() <= 64, "one candidate has a bounded local read footprint");
        helper.assertTrue(helper.getBlockState(TARGET).isAir(), "budget probes do not mutate world");
        helper.succeed();
    }

    @GameTest
    public void neighboringGeneratedPatchesDoNotChangeLaterAnalysis(GameTestHelper helper) {
        preparePlateau(helper, SurfaceFamily.GRASS);
        SurfaceAnalyzer.Analysis before = analyze(helper);
        helper.assertTrue(before.accepted(), "baseline ground is eligible");
        helper.setBlock(TARGET.east(), GroundscapeBlocks.get(SurfaceFamily.GRASS).patch().defaultBlockState()
                .setValue(BlockStateProperties.LAYERS, 3));
        helper.assertValueEqual(analyze(helper), before, "previous neighboring decoration cannot alter visitation results");
        helper.assertBlockProperty(TARGET.east(), BlockStateProperties.LAYERS, 3);
        helper.succeed();
    }

    @GameTest
    public void structureStartsAndReferencesProtectTheirWholeChunk(GameTestHelper helper) {
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(TARGET));
        var originalStarts = new HashMap<>(chunk.getAllStarts());
        var originalReferences = new HashMap<>(chunk.getAllReferences());
        var structure = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .listElements().findFirst().orElseThrow().value();
        try {
            chunk.setAllStarts(Map.of());
            chunk.setAllReferences(Map.of());
            helper.assertFalse(SodPatchFeature.hasStructures(chunk), "chunk without structure metadata is not excluded");
            // Even unresolved/invalid starts are protected without resolving neighboring chunks.
            chunk.setAllStarts(Map.of(structure, StructureStart.INVALID_START));
            helper.assertTrue(SodPatchFeature.hasStructures(chunk), "any structure start conservatively protects the chunk");
            chunk.setAllStarts(Map.of());
            chunk.setAllReferences(Map.of(structure, new LongOpenHashSet()));
            helper.assertFalse(SodPatchFeature.hasStructures(chunk), "empty reference set does not invent a structure");
            LongOpenHashSet references = new LongOpenHashSet();
            references.add(chunk.getPos().pack());
            chunk.setAllReferences(Map.of(structure, references));
            helper.assertTrue(SodPatchFeature.hasStructures(chunk), "nonempty structure reference protects the chunk");
        } finally {
            chunk.setAllStarts(originalStarts);
            chunk.setAllReferences(originalReferences);
        }
        helper.succeed();
    }

    @GameTest
    public void liveServerInvocationCannotRetrofitExistingGround(GameTestHelper helper) {
        preparePlateau(helper, SurfaceFamily.GRASS);
        var level = helper.getLevel();
        BlockState ground = helper.getBlockState(TARGET.below());
        boolean placed = new SodPatchFeature().place(level, level.getChunkSource().getGenerator(),
                RandomSource.create(SEED), helper.absolutePos(TARGET));
        helper.assertFalse(placed, "live ServerLevel invocation is never a new-chunk decoration operation");
        helper.assertTrue(helper.getBlockState(TARGET).isAir(), "existing world target remains empty");
        helper.assertValueEqual(helper.getBlockState(TARGET.below()), ground, "existing ground is preserved");
        helper.succeed();
    }

    @GameTest
    public void loadingOldChunksNeitherConsumesAdmissionNorChangesGround(GameTestHelper helper) {
        preparePlateau(helper, SurfaceFamily.GRASS);
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(TARGET));
        Boolean original = chunk.getAttached(GroundscapeWorldgen.PENDING_PATCHES);
        try {
            for (Boolean marker : List.of(Boolean.TRUE, Boolean.FALSE)) {
                chunk.setAttached(GroundscapeWorldgen.PENDING_PATCHES, marker);
                SodPatchFeature.onChunkLoad(helper.getLevel(), chunk, false);
                helper.assertValueEqual(chunk.getAttached(GroundscapeWorldgen.PENDING_PATCHES), marker,
                        "ordinary old-chunk loading never consumes stored admission data");
                helper.assertTrue(helper.getBlockState(TARGET).isAir(), "old chunk remains undecorated");
                helper.assertBlockPresent(Blocks.GRASS_BLOCK, TARGET.below());
            }
            chunk.removeAttached(GroundscapeWorldgen.PENDING_PATCHES);
            SodPatchFeature.onChunkLoad(helper.getLevel(), chunk, false);
            helper.assertTrue(chunk.getAttached(GroundscapeWorldgen.PENDING_PATCHES) == null,
                    "loading old chunks cannot manufacture admission");
        } finally {
            if (original == null) chunk.removeAttached(GroundscapeWorldgen.PENDING_PATCHES);
            else chunk.setAttached(GroundscapeWorldgen.PENDING_PATCHES, original);
        }
        helper.succeed();
    }

    @GameTest
    public void disabledGenerationConsumesFirstFullAdmissionWithoutDecorating(GameTestHelper helper) {
        helper.assertFalse(GroundscapeWorldgenConfig.current().enabled(), "standard server tests use disabled startup defaults");
        preparePlateau(helper, SurfaceFamily.GRASS);
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(TARGET));
        Boolean original = chunk.getAttached(GroundscapeWorldgen.PENDING_PATCHES);
        try {
            chunk.setAttached(GroundscapeWorldgen.PENDING_PATCHES, Boolean.TRUE);
            SodPatchFeature.onChunkLoad(helper.getLevel(), chunk, true);
            helper.assertTrue(chunk.getAttached(GroundscapeWorldgen.PENDING_PATCHES) == null,
                    "disabled first-FULL conversion consumes stale admission exactly once");
            helper.assertTrue(helper.getBlockState(TARGET).isAir(), "disabled generation leaves target empty");
            helper.assertBlockPresent(Blocks.GRASS_BLOCK, TARGET.below());
            SodPatchFeature.onChunkLoad(helper.getLevel(), chunk, true);
            helper.assertTrue(chunk.getAttached(GroundscapeWorldgen.PENDING_PATCHES) == null,
                    "repeated callbacks do not recreate admission");
            helper.assertTrue(helper.getBlockState(TARGET).isAir(), "repeated callback leaves the world unchanged");
        } finally {
            if (original == null) chunk.removeAttached(GroundscapeWorldgen.PENDING_PATCHES);
            else chunk.setAttached(GroundscapeWorldgen.PENDING_PATCHES, original);
        }
        helper.succeed();
    }

    @GameTest
    public void partialPatchesPreserveVanillaGroundThroughRealRandomTicks(GameTestHelper helper) {
        for (SurfaceFamily family : List.of(SurfaceFamily.GRASS, SurfaceFamily.MYCELIUM,
                SurfaceFamily.CRIMSON_NYLIUM, SurfaceFamily.WARPED_NYLIUM)) {
            for (int layers = 1; layers < 8; layers++) {
                preparePlateau(helper, family);
                BlockState patch = GroundscapeBlocks.get(family).patch().defaultBlockState()
                        .setValue(BlockStateProperties.LAYERS, layers);
                helper.setBlock(TARGET, patch);
                BlockState ground = helper.getBlockState(TARGET.below());
                helper.assertTrue(ground.isRandomlyTicking(), "fixture exercises native random-tick behavior");
                helper.assertTrue(LightEngine.getLightDampeningInto(ground, patch, Direction.UP,
                                patch.getLightDampening()) < 15,
                        "partial patch must not suffocate native ground: " + family + " layers=" + layers);
                for (int tick = 0; tick < 16; tick++) {
                    helper.getBlockState(TARGET.below()).randomTick(helper.getLevel(),
                            helper.absolutePos(TARGET.below()), RandomSource.create(SEED + tick));
                }
                helper.assertBlockPresent(family.vanillaBlock(), TARGET.below());
                helper.assertBlockProperty(TARGET, BlockStateProperties.LAYERS, layers);
            }
        }
        helper.succeed();
    }

    @GameTest
    public void fullHeightPatchesKeepVanillaGroundOcclusionAndDecay(GameTestHelper helper) {
        for (SurfaceFamily family : List.of(SurfaceFamily.GRASS, SurfaceFamily.MYCELIUM,
                SurfaceFamily.CRIMSON_NYLIUM, SurfaceFamily.WARPED_NYLIUM)) {
            preparePlateau(helper, family);
            BlockState patch = GroundscapeBlocks.get(family).patch().defaultBlockState()
                    .setValue(BlockStateProperties.LAYERS, 8);
            helper.setBlock(TARGET, patch);
            BlockState ground = helper.getBlockState(TARGET.below());
            helper.assertTrue(LightEngine.getLightDampeningInto(ground, patch, Direction.UP,
                    patch.getLightDampening()) >= 15, "full cube retains native ground occlusion");
            ground.randomTick(helper.getLevel(), helper.absolutePos(TARGET.below()), RandomSource.create(SEED));
            Block expected = family == SurfaceFamily.GRASS || family == SurfaceFamily.MYCELIUM
                    ? Blocks.DIRT : Blocks.NETHERRACK;
            helper.assertBlockPresent(expected, TARGET.below());
            helper.assertBlockProperty(TARGET, BlockStateProperties.LAYERS, 8);
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 160)
    public void nativeAnimalsActuallyNavigateAcrossOneToThreeLayerPatches(GameTestHelper helper) {
        navigateContour(helper, new int[]{1, 2, 3});
    }

    @GameTest(maxTicks = 160)
    public void nativeAnimalsNavigateS1SevenLayerContour(GameTestHelper helper) {
        navigateContour(helper, new int[]{3, 6, 7});
    }

    private static void navigateContour(GameTestHelper helper, int[] heights) {
        helper.setBiome(Biomes.PLAINS);
        // Two enclosed lanes prevent either animal from bypassing the patch bands.
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 6; z++) {
                helper.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
                for (int y = 2; y <= 5; y++) {
                    boolean wall = x == 0 || x == 7 || z == 0 || z == 3 || z == 6;
                    helper.setBlock(x, y, z, wall ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
        for (int x = 2; x <= 4; x++) {
            BlockState patch = GroundscapeBlocks.get(SurfaceFamily.GRASS).patch().defaultBlockState()
                    .setValue(BlockStateProperties.LAYERS, heights[x - 2]);
            for (int z : new int[]{1, 2, 4, 5}) helper.setBlock(x, 2, z, patch);
        }
        List<Mob> animals = List.of(
                helper.spawnWithNoFreeWill(EntityTypes.PIG, new Vec3(1.5, 2, 1.5)),
                helper.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(1.5, 2, 4.5)));
        List<Vec3> destinations = List.of(
                helper.absoluteVec(new Vec3(6.5, 2, 1.5)),
                helper.absoluteVec(new Vec3(6.5, 2, 4.5)));
        int[] crossedLayers = new int[animals.size()];
        helper.runAfterDelay(2, () -> {
            for (int i = 0; i < animals.size(); i++) {
                Vec3 destination = destinations.get(i);
                // The four-argument overload accepts a one-block target radius,
                // which legitimately finishes before the final bare-ground cell.
                helper.assertTrue(animals.get(i).getNavigation().moveTo(
                        destination.x, destination.y, destination.z, 0, 1.0),
                        "native navigation creates a path across the partial blocks");
                var path = animals.get(i).getNavigation().getPath();
                helper.assertTrue(path != null && path.canReach(), "native path reaches the requested endpoint cell");
                helper.assertValueEqual(path.getNodePos(path.getNodeCount() - 1), BlockPos.containing(destination),
                        "zero-radius path ends in the final bare-ground cell");
            }
        });
        helper.onEachTick(() -> {
            for (int i = 0; i < animals.size(); i++) {
                Vec3 feet = helper.relativeVec(animals.get(i).position());
                int column = (int) Math.floor(feet.x);
                if (column >= 2 && column <= 4 && feet.y >= 2 + heights[column - 2] / 8.0 - 0.03) {
                    crossedLayers[i] |= 1 << (column - 2);
                }
            }
        });
        helper.succeedWhen(() -> {
            for (int i = 0; i < animals.size(); i++) {
                Mob animal = animals.get(i);
                helper.assertTrue(animal.isAlive(), "navigating animal remains alive");
                helper.assertValueEqual(crossedLayers[i], 7, "animal actually traverses all three patch heights");
                helper.assertTrue(animal.position().distanceToSqr(destinations.get(i)) < 0.75 * 0.75,
                        "animal " + i + " reaches the destination using normal navigation and movement ticks; relative position="
                                + helper.relativeVec(animal.position()) + ", destination=" + helper.relativeVec(destinations.get(i))
                                + ", navigationDone=" + animal.getNavigation().isDone());
            }
        });
    }

    private static SurfaceProfile profile(GameTestHelper helper, ResourceKey<Biome> key) {
        return SurfaceProfile.forSurface(Blocks.GRASS_BLOCK.defaultBlockState(), biome(helper, key)).orElseThrow();
    }

    private static Holder<Biome> biome(GameTestHelper helper, ResourceKey<Biome> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(key);
    }

    private static ResourceKey<Biome> nativeBiome(SurfaceFamily family) {
        return switch (family) {
            case GRASS -> Biomes.PLAINS;
            case MYCELIUM -> Biomes.MUSHROOM_FIELDS;
            case PODZOL -> Biomes.OLD_GROWTH_PINE_TAIGA;
            case CRIMSON_NYLIUM -> Biomes.CRIMSON_FOREST;
            case WARPED_NYLIUM -> Biomes.WARPED_FOREST;
            case SAND -> Biomes.DESERT;
            case RED_SAND -> Biomes.BADLANDS;
            case GRAVEL -> Biomes.PLAINS;
            default -> Biomes.PLAINS;
        };
    }

    private static int sample(Column column, long seed, double density, int maxLayers) {
        return SlopePlanner.layers(seed, column.x(), column.z(), GRASS, SlopeTerrain.FLAT,
                new GroundscapeWorldgenConfig(true, density, maxLayers));
    }

    private static void preparePlateau(GameTestHelper helper, SurfaceFamily family) {
        helper.setBiome(nativeBiome(family));
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                for (int y = 1; y <= 6; y++) {
                    helper.setBlock(new BlockPos(x, y, z), y < 3 ? Blocks.STONE : y == 3 ? family.vanillaBlock() : Blocks.AIR);
                }
            }
        }
    }

    private static SurfaceAnalyzer.Analysis analyze(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(TARGET);
        return SurfaceAnalyzer.analyze(helper.getLevel(), target, fixtureBounds(target));
    }

    private static Predicate<BlockPos> fixtureBounds(BlockPos target) {
        return pos -> Math.abs(pos.getX() - target.getX()) <= 1 && Math.abs(pos.getZ() - target.getZ()) <= 1
                && pos.getY() >= target.getY() - 3 && pos.getY() <= target.getY() + 2;
    }

    private static void expectIllegalArgument(GameTestHelper helper, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        helper.fail("Unsafe configuration value was accepted");
    }

    private record Column(int x, int y, int z) {
    }

    private static final class NamedTestBiome extends Holder.Reference<Biome> {
        private NamedTestBiome(Biome value, String namespace, String path) {
            super(Type.STAND_ALONE, new HolderOwner<>() {},
                    ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(namespace, path)), value);
        }
    }
}
