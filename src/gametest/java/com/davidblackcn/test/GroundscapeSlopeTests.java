package com.davidblackcn.test;

import com.davidblackcn.registry.GroundscapeBlocks;
import com.davidblackcn.registry.SurfaceFamily;
import com.davidblackcn.worldgen.*;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.IntBinaryOperator;
import java.util.stream.IntStream;

/** S1 contracts use original height fixtures, distribution metrics and real entity movement. */
public final class GroundscapeSlopeTests {
    private static final long SEED = 20261024;
    private static final SurfaceProfile GRASS = new SurfaceProfile(SurfaceFamily.GRASS, 1);
    private static final GroundscapeWorldgenConfig CONFIG = new GroundscapeWorldgenConfig(true, 0.12, 7);
    private static final SlopeTerrain SLOPE = new SlopeTerrain(0.5, 0, 0.25, 2, 3, 3, 1, 0, false);

    @GameTest
    public void terrainMeasuresSlopeDirectionAndMirrors(GameTestHelper h) {
        var east = sample(new Terrain((x, z) -> 40 + Math.floorDiv(x + 1, 3)));
        var west = sample(new Terrain((x, z) -> 40 + Math.floorDiv(-x + 1, 3)));
        var south = sample(new Terrain((x, z) -> 40 + Math.floorDiv(z + 1, 3)));
        h.assertTrue(east != null && west != null && south != null, "gentle slopes accepted");
        h.assertTrue(east.gradientX() > 0 && east.gradientZ() == 0, "eastward uphill gradient");
        h.assertValueEqual(west.gradientX(), -east.gradientX(), "mirror reverses uphill direction");
        h.assertValueEqual(south.gradientZ(), east.gradientX(), "rotating terrain rotates gradient");
        h.assertTrue(east.higherNeighbors() >= 2 && east.lowerNeighbors() >= 2, "both sides of slope measured: " + east);
        h.assertValueEqual(sample(new Terrain((x, z) -> 40)), SlopeTerrain.FLAT, "flat terrain stays flat");
        for (SurfaceFamily family : SurfaceFamily.values()) {
            Terrain ground = new Terrain((x, z) -> 40);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                ground.overrides.put(new BlockPos(x, 40, z), family.vanillaBlock().defaultBlockState());
            }
            h.assertValueEqual(sample(ground), SlopeTerrain.FLAT, "original surface height retained: " + family);
        }
        h.succeed();
    }

    @GameTest
    public void patchesAndTreesDoNotBecomeFakeTerrain(GameTestHelper h) {
        Terrain t = new Terrain((x, z) -> 40);
        SlopeTerrain before = sample(t);
        t.overrides.put(new BlockPos(2, 41, 0), GroundscapeBlocks.get(SurfaceFamily.GRASS).patch()
                .defaultBlockState().setValue(BlockStateProperties.LAYERS, 7));
        t.overrides.put(new BlockPos(-2, 41, 0), Blocks.OAK_LOG.defaultBlockState());
        t.overrides.put(new BlockPos(0, 41, 2), Blocks.SHORT_GRASS.defaultBlockState());
        h.assertValueEqual(sample(t), before, "original surface ignores previous sod, wood and vegetation");
        h.succeed();
    }

    @GameTest
    public void expandedFootprintPreservesFluidSnowRoadAndBlockEntityProtection(GameTestHelper h) {
        for (var block : List.of(Blocks.WATER, Blocks.LAVA, Blocks.SNOW, Blocks.DIRT_PATH, Blocks.FARMLAND, Blocks.CHEST)) {
            Terrain t = new Terrain((x, z) -> 40);
            t.overrides.put(new BlockPos(2, 41, 0), block.defaultBlockState());
            h.assertTrue(sample(t) == null, "protected radius-two cell rejects candidate: " + block);
        }
        h.succeed();
    }

    @GameTest
    public void cliffsAndThinShellsCannotBecomeSlopes(GameTestHelper h) {
        h.assertTrue(sample(new Terrain((x, z) -> x >= 2 ? 44 : 40)) == null, "high wall rejected");
        h.assertTrue(sample(new Terrain((x, z) -> x >= 2 ? 36 : 40)) == null, "deep drop rejected");
        Terrain hollow = new Terrain((x, z) -> 40);
        hollow.overrides.put(new BlockPos(2, 38, 0), Blocks.AIR.defaultBlockState());
        h.assertTrue(sample(hollow) == null, "two-block shell rejected in wider footprint");
        h.succeed();
    }

    @GameTest
    public void terrainReadsAreFixedAndFailClosed(GameTestHelper h) {
        Terrain t = new Terrain((x, z) -> 40);
        h.assertTrue(sample(t) != null && t.reads <= 96, "at most twelve bounded columns, no chunk scans");
        t.reads = 0;
        var result = SlopeTerrain.sample(t, new BlockPos(0, 41, 0), pos -> t.reads < 5);
        h.assertTrue(result == null && t.reads <= 5, "read budget exhaustion never produces a speculative slope");
        t.reads = 0;
        result = SlopeTerrain.sample(t, new BlockPos(0, 41, 0), pos -> pos.getX() >= 0);
        h.assertTrue(result == null && t.reads == 0, "unavailable neighbour skipped before reading");
        h.succeed();
    }

    @GameTest
    public void slopesHaveCoherentCoverageAndCommonMiddleLayers(GameTestHelper h) throws IOException {
        int[] flat = new int[8], slope = new int[8], high = new int[8];
        GroundscapeWorldgenConfig tall = new GroundscapeWorldgenConfig(true, .12, 7, .55, .10, 1.4, .35);
        int maxJump = 0, isolated = 0;
        for (int x = -256; x <= 256; x++) for (int z = -256; z <= 256; z++) {
            flat[layers(x, z, SlopeTerrain.FLAT, CONFIG)]++;
            int n = layers(x, z, SLOPE, CONFIG);
            slope[n]++;
            high[layers(x, z, SLOPE, tall)]++;
            int east = layers(x + 1, z, SLOPE, CONFIG), south = layers(x, z + 1, SLOPE, CONFIG);
            maxJump = Math.max(maxJump, Math.max(Math.abs(n - east), Math.abs(n - south)));
            if (n >= 4 && east == 0 && south == 0 && layers(x - 1, z, SLOPE, CONFIG) == 0
                    && layers(x, z - 1, SLOPE, CONFIG) == 0) isolated++;
        }
        int flatCount = Arrays.stream(flat).skip(1).sum(), slopeCount = Arrays.stream(slope).skip(1).sum();
        com.davidblackcn.BuildupGroundscape.LOGGER.info("Slope fixture layers: flat={}, slope={}, maxJump={}",
                Arrays.toString(flat), Arrays.toString(slope), maxJump);
        h.assertTrue(flatCount > 0 && slopeCount > flatCount * 3, "slopes preferentially covered, flats retain sparse patches");
        h.assertValueEqual(slopeCount, 513 * 513, "every safe uphill ramp participates without noise holes");
        h.assertTrue((slope[3] + slope[4] + slope[5] + slope[6]) > slopeCount * .6, "3-6 layers common on slopes");
        h.assertTrue(flat[3] + flat[4] + flat[5] + flat[6] + flat[7] == 0, "flat ground has no tall random spikes");
        h.assertTrue(high[7] > 0 && slope[7] > 0, "seven layers reachable with default and thicker profiles");
        h.assertTrue(maxJump <= 2, "default continuous field has no abrupt height jump on a uniform slope: " + maxJump);
        h.assertValueEqual(isolated, 0, "no isolated tall proposals in continuous field");
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("build.gradle"))) root = root.getParent();
        if (root == null) throw new IOException("Project root not found");
        Path file = root.getParent().resolve("docs/evidence/terrain-continuity/planner-metrics.json"); Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "seed", SEED, "samples", 513 * 513, "flat_layers", flat, "slope_layers", slope,
                "layer_scale_1_4", high, "maximum_adjacent_layer_delta", maxJump, "isolated_tall_proposals", isolated)) + "\n");
        h.succeed();
    }

    @GameTest
    public void actualTerracesRiseTowardTheStepAndNeverRaiseExposedLips(GameTestHelper h) {
        int testedRamps = 0;
        for (int rotation = 0; rotation < 4; rotation++) {
            final int dx = rotation == 0 ? 1 : rotation == 1 ? -1 : 0;
            final int dz = rotation == 2 ? 1 : rotation == 3 ? -1 : 0;
            Terrain t = new Terrain((x, z) -> 40 + Math.floorDiv(x * dx + z * dz, 4));
            for (int offset = -128; offset <= 128; offset += 4) {
                for (long seed : new long[]{0, 25, SEED, -918273}) {
                    for (int cap : new int[]{3, 7}) {
                        var config = new GroundscapeWorldgenConfig(true, cap == 3 ? .16 : .12, cap);
                        int previous = 0;
                        for (int local = 0; local < 4; local++) {
                            int x = (offset + local) * dx, z = (offset + local) * dz;
                            BlockPos target = new BlockPos(x, t.height.applyAsInt(x, z) + 1, z);
                            SlopeTerrain terrain = SlopeTerrain.sample(t, target, p -> true);
                            h.assertTrue(terrain != null, "terraced slope accepted");
                            int n = SlopePlanner.layers(seed, x, z, GRASS, terrain, config);
                            if (local == 0) h.assertValueEqual(n, 0, "do not raise downhill terrace lip");
                            h.assertTrue(n >= previous, "same terrace must rise uphill: " + previous + " -> " + n);
                            previous = n;
                        }
                        if (previous >= 3) testedRamps++;
                    }
                }
            }
        }
        h.assertTrue(testedRamps > 100, "test exercises real multi-layer ramps");
        h.succeed();
    }

    @GameTest
    public void continuousCoverageDoesNotSwitchWholeRegionsOnAndOff(GameTestHelper h) {
        double min = 1, max = 0;
        for (int tx = -4; tx < 4; tx++) for (int tz = -4; tz < 4; tz++) {
            int count = 0;
            for (int x = tx * 48; x < tx * 48 + 48; x++) for (int z = tz * 48; z < tz * 48 + 48; z++) {
                if (layers(x, z, SLOPE, CONFIG) > 0) count++;
            }
            double coverage = count / (48.0 * 48);
            min = Math.min(min, coverage); max = Math.max(max, coverage);
        }
        h.assertTrue(min == 1 && max == 1, "safe ramps have no regional coverage veto: " + min + " .. " + max);
        com.davidblackcn.BuildupGroundscape.LOGGER.info("48x48 uniform-slope regional coverage: {} .. {}", min, max);
        h.succeed();
    }

    @GameTest
    public void diagonalTerracesAlsoRiseUphill(GameTestHelper h) {
        int covered = 0;
        for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) {
            Terrain t = new Terrain((x, z) -> 40 + Math.floorDiv(x * sx + z * sz, 8));
            for (int offset = -128; offset <= 128; offset += 4) for (long seed : new long[]{25, SEED, -91}) {
                int previous = 0;
                for (int local = 0; local < 4; local++) {
                    int x = (offset + local) * sx, z = (offset + local) * sz;
                    var terrain = SlopeTerrain.sample(t, new BlockPos(x, t.height.applyAsInt(x, z) + 1, z), p -> true);
                    h.assertTrue(terrain != null, "diagonal terrace accepted");
                    int n = SlopePlanner.layers(seed, x, z, GRASS, terrain, CONFIG);
                    h.assertTrue(n >= previous, "diagonal terrace rises uphill: " + previous + " -> " + n);
                    previous = n;
                }
                if (previous >= 3) covered++;
            }
        }
        h.assertTrue(covered > 30, "diagonal test exercises multi-layer ramps");
        h.succeed();
    }

    @GameTest
    public void slopePlanningIsIndependentOfThreadsOrderAndChunkBorders(GameTestHelper h) {
        int[] expected = IntStream.range(0, 8192).map(i -> layers(i % 128 - 64, i / 128 - 32, SLOPE, CONFIG)).toArray();
        int[] parallel = IntStream.range(0, 8192).parallel().map(i -> layers(i % 128 - 64, i / 128 - 32, SLOPE, CONFIG)).toArray();
        h.assertTrue(Arrays.equals(expected, parallel), "parallel planning identical");
        List<Integer> order = new ArrayList<>(IntStream.range(0, 8192).boxed().toList());
        Collections.shuffle(order, new Random(25));
        for (int i : order) h.assertValueEqual(layers(i % 128 - 64, i / 128 - 32, SLOPE, CONFIG), expected[i], "shuffled planning identical");
        h.succeed();
    }

    @GameTest
    public void configurationCapsAllSevenLayersAndKeepsLegacyCap(GameTestHelper h) throws IOException {
        Path f = Files.createTempFile(Path.of("."), "s1-config-", ".json");
        try {
            Files.writeString(f, "{\"enabled\":true,\"density\":0.16,\"maxLayers\":3}");
            h.assertValueEqual(GroundscapeWorldgenConfig.load(f).maxLayers(), 3, "existing explicit cap is never silently raised");
            for (String entry : List.of("\"slopeInfluence\":1.1", "\"flatCoverage\":-1", "\"layerScale\":0.1", "\"heightVariation\":\"0.4\"", "\"heightVariation\":1e309")) {
                String text = "{\"enabled\":true," + entry + "}";
                Files.writeString(f, text);
                h.assertFalse(GroundscapeWorldgenConfig.load(f).enabled(), "invalid S1 config fails closed");
                h.assertValueEqual(Files.readString(f), text, "invalid file preserved");
            }
        } finally { Files.deleteIfExists(f); }
        for (int cap = 1; cap <= 7; cap++) {
            var cfg = new GroundscapeWorldgenConfig(true, .35, cap);
            for (int x = -128; x < 128; x++) h.assertTrue(layers(x, 20, SLOPE, cfg) <= cap, "explicit layer cap");
        }
        h.succeed();
    }

    private static int layers(int x, int z, SlopeTerrain terrain, GroundscapeWorldgenConfig config) {
        return SlopePlanner.layers(SEED, x, z, GRASS, terrain, config);
    }

    private static SlopeTerrain sample(Terrain t) {
        return SlopeTerrain.sample(t, new BlockPos(0, 41, 0), p -> Math.abs(p.getX()) <= 2 && Math.abs(p.getZ()) <= 2);
    }

    private static final class Terrain implements BlockGetter {
        final IntBinaryOperator height;
        final Map<BlockPos, BlockState> overrides = new HashMap<>();
        int reads;
        Terrain(IntBinaryOperator height) { this.height = height; }
        @Override public BlockState getBlockState(BlockPos pos) {
            reads++;
            if (overrides.containsKey(pos)) return overrides.get(pos);
            int y = height.applyAsInt(pos.getX(), pos.getZ());
            return (pos.getY() < y ? Blocks.STONE : pos.getY() == y ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState();
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public int getHeight() { return 128; }
        @Override public int getMinY() { return 0; }
    }
}
