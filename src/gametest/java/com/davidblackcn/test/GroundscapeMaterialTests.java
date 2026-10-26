package com.davidblackcn.test;

import com.davidblackcn.registry.GroundscapeBlocks;
import com.davidblackcn.registry.SurfaceFamily;
import com.davidblackcn.worldgen.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** S2 contracts, including material boundaries and stable granular building variants. */
public final class GroundscapeMaterialTests {
    private static final long SEED = 20261024;
    private static final GroundscapeWorldgenConfig CONFIG = new GroundscapeWorldgenConfig(true, .12, 7);

    @GameTest
    public void mixedMaterialSlopesKeepTheSameHeightField(GameTestHelper h) {
        Terrain control = new Terrain(SurfaceFamily.GRASS, false);
        for (SurfaceFamily family : SurfaceFamily.values()) {
            for (boolean mixed : List.of(false, true)) {
                Terrain terrain = new Terrain(family, mixed);
                for (int x = -32; x < 32; x++) for (int z = -8; z < 8; z++) {
                    BlockPos target = new BlockPos(x, terrain.height(x) + 1, z);
                    var reference = SlopeTerrain.sample(control, target, p -> true);
                    var sampled = SlopeTerrain.sample(terrain, target, p -> true);
                    h.assertTrue(sampled != null, "supported original terrain can be sampled: " + family);
                    h.assertValueEqual(sampled, reference, "material boundary cannot alter original geometry");
                    int actual = SlopePlanner.layers(SEED, x, z, new SurfaceProfile(family, 1), sampled, CONFIG);
                    int expected = SlopePlanner.layers(SEED, x, z, new SurfaceProfile(SurfaceFamily.GRASS, 1), reference, CONFIG);
                    h.assertValueEqual(actual, expected, "equal default material settings preserve ramp heights");
                }
            }
        }
        h.succeed();
    }

    @GameTest
    public void materialControlsAreIndependentAndBounded(GameTestHelper h) {
        var slope = new SlopeTerrain(.25, 0, .3, 1, 3, 0, 1, 0, false);
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (family.hasSodBlock()) continue;
            var disabled = config(family, false, 1);
            var low = config(family, true, .5);
            var high = config(family, true, 1.5);
            int thin = 0, thick = 0;
            for (int x = -128; x < 128; x++) {
                var profile = new SurfaceProfile(family, 1);
                h.assertValueEqual(SlopePlanner.layers(SEED, x, 0, profile, slope, disabled), 0, "material disabled");
                int a = SlopePlanner.layers(SEED, x, 0, profile, slope, low);
                int b = SlopePlanner.layers(SEED, x, 0, profile, slope, high);
                h.assertTrue(a >= 1 && a <= b && b <= 7, "thickness control is monotone and capped");
                thin += a; thick += b;
                var grass = new SurfaceProfile(SurfaceFamily.GRASS, 1);
                h.assertValueEqual(SlopePlanner.layers(SEED, x, 0, grass, slope, disabled),
                        SlopePlanner.layers(SEED, x, 0, grass, slope, CONFIG), "new material toggle leaves old family unchanged");
            }
            h.assertTrue(thick > thin, "material thickness setting has a measurable effect");
        }
        h.succeed();
    }

    @GameTest
    public void materialConfigurationLoadsWithoutRewritingAndFailsClosed(GameTestHelper h) throws Exception {
        Path file = Files.createTempFile(Path.of("."), "s2-config-", ".json");
        try {
            String valid = "{\"enabled\":true,\"maxLayers\":3,\"materials\":{\"sand\":{\"enabled\":false},\"dirt\":{\"thicknessScale\":0.7}}}";
            Files.writeString(file, valid);
            var settings = GroundscapeWorldgenConfig.load(file);
            h.assertTrue(settings.enabled(), "valid material settings load");
            h.assertFalse(settings.material(SurfaceFamily.SAND).enabled(), "sand override");
            h.assertValueEqual(settings.material(SurfaceFamily.DIRT).thicknessScale(), .7, "dirt override");
            h.assertValueEqual(settings.maxLayers(), 3, "old explicit cap preserved");
            h.assertValueEqual(Files.readString(file), valid, "configuration never rewritten");
            for (String value : List.of("null", "[]", "{\"sand\":null}", "{\"unknown\":{}}",
                    "{\"sand\":{\"enabled\":\"false\"}}", "{\"sand\":{\"thicknessScale\":1e309}}",
                    "{\"sand\":{\"thicknessScale\":0}}", "{\"sand\":{\"thicknessScale\":1.6}}",
                    "{\"sand\":{\"thicknessScael\":1}}")) {
                String text = "{\"enabled\":true,\"materials\":" + value + "}";
                Files.writeString(file, text);
                h.assertFalse(GroundscapeWorldgenConfig.load(file).enabled(), "invalid material setting fails closed");
                h.assertValueEqual(Files.readString(file), text, "invalid input retained for repair");
            }
        } finally { Files.deleteIfExists(file); }
        h.succeed();
    }

    @GameTest
    public void beachAdmissionAndPlantWhitelistDoNotRemoveSafetyGuards(GameTestHelper h) {
        var biomes = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        for (var family : List.of(SurfaceFamily.SAND, SurfaceFamily.GRAVEL)) {
            h.assertTrue(SurfaceProfile.forSurface(family.vanillaBlock().defaultBlockState(), biomes.getOrThrow(Biomes.BEACH)).isPresent(), "dry beach materials admitted");
            h.assertTrue(SurfaceProfile.forSurface(family.vanillaBlock().defaultBlockState(), biomes.getOrThrow(Biomes.OCEAN)).isEmpty(), "ocean remains excluded");
        }
        h.setBiome(Biomes.PLAINS);
        for (var family : SurfaceFamily.values()) {
            if (family.hasSodBlock()) continue;
            for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) for (int y = 1; y <= 6; y++) {
                h.setBlock(new BlockPos(x, y, z), y < 3 ? Blocks.STONE : y == 3 ? family.vanillaBlock() : Blocks.AIR);
            }
            BlockPos target = new BlockPos(3, 4, 3);
            for (var plant : List.of(Blocks.SHORT_GRASS, Blocks.FERN)) {
                h.getLevel().setBlock(h.absolutePos(target), plant.defaultBlockState(), Block.UPDATE_CLIENTS);
                h.assertValueEqual(SurfaceAnalyzer.analyze(h.getLevel(), h.absolutePos(target), p -> true).accepted(),
                        family.supportsVegetation(), "only soil families retain grass and fern");
            }
            h.setBlock(target, Blocks.AIR);
            h.setBlock(target.east(), Blocks.WATER);
            h.assertFalse(SurfaceAnalyzer.analyze(h.getLevel(), h.absolutePos(target), p -> true).accepted(), "water edge still protected");
        }
        h.succeed();
    }

    @GameTest(maxTicks = 60)
    public void granularBuildingVariantsStayStableAfterSupportRemoval(GameTestHelper h) {
        int column = 1;
        for (var family : List.of(SurfaceFamily.SAND, SurfaceFamily.RED_SAND, SurfaceFamily.GRAVEL)) {
            var variant = GroundscapeBlocks.get(family);
            h.assertTrue(variant.block() == family.vanillaBlock(), "native full block reused");
            for (Block block : List.of(variant.slab(), variant.stairs())) {
                BlockPos p = new BlockPos(column++, 5, 3);
                h.setBlock(p.below(), Blocks.STONE);
                h.setBlock(p, block);
                h.setBlock(p.below(), Blocks.AIR);
                h.getLevel().scheduleTick(h.absolutePos(p), block, 1);
            }
        }
        h.runAtTickTime(20, () -> {
            int x = 1;
            for (var family : List.of(SurfaceFamily.SAND, SurfaceFamily.RED_SAND, SurfaceFamily.GRAVEL)) {
                var variant = GroundscapeBlocks.get(family);
                h.assertBlockPresent(variant.slab(), new BlockPos(x++, 5, 3));
                h.assertBlockPresent(variant.stairs(), new BlockPos(x++, 5, 3));
            }
            h.succeed();
        });
    }

    private static GroundscapeWorldgenConfig config(SurfaceFamily family, boolean enabled, double scale) {
        return new GroundscapeWorldgenConfig(true, .12, 7, .55, .1, 1, .35,
                Map.of(family.id(), new GroundscapeWorldgenConfig.MaterialSettings(enabled, scale)));
    }

    private record Terrain(SurfaceFamily family, boolean mixed) implements BlockGetter {
        int height(int x) { return 40 + Math.floorDiv(x, 4); }
        @Override public BlockState getBlockState(BlockPos pos) {
            int top = height(pos.getX());
            if (pos.getY() > top) return Blocks.AIR.defaultBlockState();
            if (pos.getY() < top - 2) return Blocks.STONE.defaultBlockState();
            if (pos.getY() < top && family == SurfaceFamily.RED_SAND)
                return (pos.getY() % 2 == 0 ? Blocks.DYED_TERRACOTTA.orange() : Blocks.DYED_TERRACOTTA.yellow()).defaultBlockState();
            return (mixed && Math.floorMod(pos.getZ(), 2) == 0 ? Blocks.GRASS_BLOCK : family.vanillaBlock()).defaultBlockState();
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public int getHeight() { return 128; }
        @Override public int getMinY() { return 0; }
    }
}
