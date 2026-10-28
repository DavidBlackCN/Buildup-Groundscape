package com.davidblackcn.test;

import com.davidblackcn.registry.*;
import com.davidblackcn.block.*;
import com.davidblackcn.config.*;
import com.davidblackcn.worldgen.GroundscapeWorldgenConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import java.util.*;
import java.nio.file.*;

public final class GroundscapePatchConfigTests {
    private static final BlockPos CELL = new BlockPos(3, 3, 3);
    private static List<Block> patches() {
        List<Block> blocks = new ArrayList<>();
        GroundscapeBlocks.variants().forEach(v -> blocks.add(v.patch()));
        blocks.addAll(GroundscapeLeaves.blocks());
        return blocks;
    }
    private static void reset(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(CELL);
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2)))
            level.setBlock(p, (p.getY() == pos.getY() - 1 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).forEach(ItemEntity::discard);
    }
    @GameTest
    public void allPatchHeightsMatchMossMiningAndRequireSilk(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var level = h.getLevel(); var pos = h.absolutePos(CELL);
        var silk = new ItemStack(Items.DIAMOND_HOE);
        silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        var fortune = new ItemStack(Items.DIAMOND_HOE);
        fortune.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), 3);
        var tools = List.of(ItemStack.EMPTY, new ItemStack(Items.SHEARS), new ItemStack(Items.DIAMOND_SHOVEL),
                new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.DIAMOND_HOE), fortune, silk);
        for (Block patch : patches()) for (int n = 1; n <= 8; n++) {
            BlockState state = patch.defaultBlockState().setValue(BlockStateProperties.LAYERS, n);
            h.assertFalse(state.hasProperty(BlockStateProperties.WATERLOGGED), "no persisted waterlogging");
            h.assertTrue(state.getFluidState().isEmpty(), "dry state");
            for (ItemStack tool : tools) {
                player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                h.assertValueEqual(state.getDestroyProgress(player, level, pos), Blocks.MOSS_CARPET.defaultBlockState().getDestroyProgress(player, level, pos), "same-tool moss mining speed");
                var drops = Block.getDrops(state, level, pos, null, player, tool);
                h.assertValueEqual(drops.stream().mapToInt(ItemStack::getCount).sum(), tool == silk ? n : 0, "silk-only layer count");
                h.assertTrue(drops.stream().allMatch(i -> i.is(patch.asItem())), "no native material or seed drops");
            }
        }
        h.succeed();
    }
    @GameTest
    public void waterOnlyWashesFirstThreeLayersAcrossAndDown(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(CELL);
        for (Block patch : patches()) for (int n = 1; n <= 8; n++) for (boolean falling : List.of(false, true)) {
            reset(h);
            var state = patch.defaultBlockState().setValue(BlockStateProperties.LAYERS, n);
            level.setBlock(pos, state, 2);
            var source = falling ? pos.above() : pos.west();
            if (!falling) for (BlockPos wall : List.of(source.west(), source.north(), source.south(), pos.east(), pos.north(), pos.south())) level.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(source, Blocks.WATER.defaultBlockState(), 3);
            h.assertTrue(level.getBlockState(pos).is(patch), "adjacent water alone does not erase patches");
            Fluids.WATER.tick(level, source, level.getBlockState(source), level.getFluidState(source));
            h.assertValueEqual(level.getBlockState(pos), n < 4 ? level.getFluidState(pos).createLegacyBlock() : state, "water threshold " + patch + " layers=" + n + " falling=" + falling);
            h.assertTrue(n < 4 ? level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER) : level.getFluidState(pos).isEmpty(), "only thin patches admit water");
            h.assertTrue(level.getBlockState(pos.below()).is(Blocks.STONE), "substrate unchanged");
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).isEmpty(), "water does not bypass silk touch");
        }
        h.succeed();
    }
    @GameTest
    public void bucketsRespectLayerThresholdAndPlantsSurviveBesideWater(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(CELL);
        for (Block patch : patches()) for (int n = 1; n <= 8; n++) {
            reset(h);
            level.setBlock(pos, patch.defaultBlockState().setValue(BlockStateProperties.LAYERS, n), 2);
            boolean placed = ((BucketItem) Items.WATER_BUCKET).emptyContents(null, level, pos, null);
            h.assertValueEqual(placed, n < 4, "bucket directly enters only layers 1-3");
            h.assertTrue(n < 4 ? level.getBlockState(pos).is(Blocks.WATER) : level.getBlockState(pos).is(patch), "bucket respects thick patches");
            if (n >= 4) {
                h.assertTrue(((BucketItem) Items.WATER_BUCKET).emptyContents(null, level, pos, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)), "blocked bucket may place water above as vanilla does");
                h.assertTrue(level.getBlockState(pos.above()).is(Blocks.WATER), "fallback water above");
                h.assertValueEqual(level.getBlockState(pos), patch.defaultBlockState().setValue(BlockStateProperties.LAYERS, n), "fallback does not erase thick patch");
            }
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).isEmpty(), "bucket drops no patch");
        }
        for (var plant : List.of(SodPatchBlock.Vegetation.DANDELION, SodPatchBlock.Vegetation.WILDFLOWERS_NORTH_4)) for (int n = 1; n <= 8; n++) {
            reset(h);
            var patch = GroundscapeBlocks.get(SurfaceFamily.GRASS).patch();
            var composite = patch.defaultBlockState().setValue(SodPatchBlock.LAYERS, n).setValue(SodPatchBlock.VEGETATION, plant);
            level.setBlock(pos, composite, 2);
            var source = pos.west();
            for (BlockPos wall : List.of(source.west(), source.north(), source.south(), pos.east(), pos.north(), pos.south())) level.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(source, Blocks.WATER.defaultBlockState(), 3);
            Fluids.WATER.tick(level, source, level.getBlockState(source), level.getFluidState(source));
            if (n >= 4) h.assertValueEqual(level.getBlockState(pos), composite, "thick composite and plant retained");
            var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
            h.assertTrue(items.stream().allMatch(e -> e.getItem().is(plant.plant().getBlock().asItem())), "only native plant loot survives washing");
            h.assertValueEqual(items.stream().mapToInt(e -> e.getItem().getCount()).sum(), n >= 4 ? 0 : plant == SodPatchBlock.Vegetation.DANDELION ? 1 : 4, "plant loot only when thin composite washes away");
        }
        h.succeed();
    }
    @GameTest
    public void waterResistanceTracksCurrentLayerCount(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(CELL); var source = pos.west();
        for (Block patch : patches()) {
            reset(h);
            for (BlockPos wall : List.of(source.west(), source.north(), source.south(), pos.east(), pos.north(), pos.south()))
                level.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
            var three = patch.defaultBlockState().setValue(BlockStateProperties.LAYERS, 3);
            var four = three.setValue(BlockStateProperties.LAYERS, 4);
            level.setBlock(pos, three, 2);
            level.setBlock(source, Blocks.WATER.defaultBlockState(), 3);
            // Stacking before the next fluid tick immediately protects the patch.
            level.setBlock(pos, four, 3);
            for (int tick = 0; tick < 3; tick++) Fluids.WATER.tick(level, source, level.getBlockState(source), level.getFluidState(source));
            h.assertValueEqual(level.getBlockState(pos), four, "four layers resist repeated water ticks");
            // No cached block-type decision: returning to three layers allows the existing water in.
            level.setBlock(pos, three, 3);
            Fluids.WATER.tick(level, source, level.getBlockState(source), level.getFluidState(source));
            h.assertTrue(level.getBlockState(pos).is(Blocks.WATER), "three layers wash away after height change");
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).isEmpty(), "threshold transitions do not duplicate items");
        }
        h.succeed();
    }
    @GameTest
    public void configValidationPersistenceConflictsAndPermissions(GameTestHelper h) throws Exception {
        var original = GroundscapeConfigManager.snapshot();
        var player = h.makeMockServerPlayerInLevel();
        Path directory = Files.createTempDirectory("groundscape-config-test-");
        try {
            var config = GroundscapeWorldgenConfig.parse("{\"enabled\":true,\"density\":0,\"leafPatches\":{\"enabled\":true}}");
            h.assertTrue(config.leafPatches().active(), "independent leaves at density zero");
            Path file = directory.resolve("settings.json");
            GroundscapeConfigManager.save(file, config);
            h.assertValueEqual(GroundscapeWorldgenConfig.load(file), config, "lossless complete serialization");
            Path blocked = directory.resolve("directory-instead-of-file"); Files.createDirectory(blocked); Files.writeString(blocked.resolve("keep"), "keep");
            boolean failed = false;
            try { GroundscapeConfigManager.save(blocked, config); } catch (java.io.IOException expected) { failed = true; }
            h.assertTrue(failed && Files.readString(blocked.resolve("keep")).equals("keep"), "failed replacement preserves old data");
            var denied = ConfigNetworking.handle(player, new ConfigNetworking.Request(1, original.revision(), true, config.json()));
            h.assertValueEqual(denied.error(), "permission", "unprivileged packet rejected");
            h.assertValueEqual(GroundscapeConfigManager.snapshot(), original, "rejected packet changes nothing");
            var saved = GroundscapeConfigManager.apply(original.revision(), config.json());
            h.assertValueEqual(GroundscapeWorldgenConfig.current(), config, "hot snapshot visible immediately");
            failed = false;
            try { GroundscapeConfigManager.apply(original.revision(), original.config().json()); } catch (IllegalArgumentException expected) { failed = true; }
            h.assertTrue(failed && GroundscapeConfigManager.snapshot().equals(saved), "stale editor cannot overwrite newer settings");
            for (String bad : List.of("{\"enabled\":1}", "{\"density\":2}", "{\"unknown\":true}", "{\"maxLayers\":2.5}", "{\"leafPatches\":{\"coverage\":0.1}}")) {
                failed = false;
                try { GroundscapeConfigManager.apply(saved.revision(), bad); } catch (IllegalArgumentException | com.google.gson.JsonParseException expected) { failed = true; }
                h.assertTrue(failed && GroundscapeConfigManager.snapshot().equals(saved), "invalid save preserves current settings");
            }
            h.succeed();
        } finally {
            GroundscapeConfigManager.apply(GroundscapeConfigManager.snapshot().revision(), original.config().json());
            player.discard();
            try (var paths = Files.walk(directory)) { for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
        }
    }
}
