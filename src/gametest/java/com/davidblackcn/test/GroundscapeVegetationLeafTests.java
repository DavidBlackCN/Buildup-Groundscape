package com.davidblackcn.test;

import com.davidblackcn.block.*;
import com.davidblackcn.registry.*;
import com.davidblackcn.worldgen.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Map;

public final class GroundscapeVegetationLeafTests {
    private static final BlockPos CELL = new BlockPos(3, 3, 3);

    @GameTest
    public void plantingMatchesEveryNativeSubstrateAndRetainsLayers(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        var level = h.getLevel(); var pos = h.absolutePos(CELL);
        h.setBlock(CELL.below(), Blocks.STONE);
        for (var family : SurfaceFamily.values()) for (var vegetation : SodPatchBlock.Vegetation.values()) {
            if (vegetation == SodPatchBlock.Vegetation.NONE) continue;
            var plant = vegetation.plant();
            h.setBlock(CELL, family.vanillaBlock());
            boolean nativeAllowed = plant.canSurvive(level, pos.above());
            h.assertValueEqual(PlantSupport.accepts(plant, family.vanillaBlock().defaultBlockState(), level, pos), nativeAllowed, "native substrate equivalence " + family + "/" + vegetation);
            for (int layers : new int[]{1, 7}) {
                var bare = GroundscapeBlocks.get(family).patch().defaultBlockState().setValue(SodPatchBlock.LAYERS, layers);
                h.setBlock(CELL, bare);
                var stack = new ItemStack(plant.getBlock(), 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var result = bare.useItemOn(stack, level, player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                h.assertValueEqual(result.consumesAction(), nativeAllowed, "placement matches native support");
                h.assertValueEqual(stack.getCount(), nativeAllowed ? 1 : 2, "consume only successful placement");
                h.assertBlockProperty(CELL, SodPatchBlock.LAYERS, layers);
                h.assertValueEqual(h.getBlockState(CELL).getValue(SodPatchBlock.VEGETATION).plant().getBlock(), nativeAllowed ? plant.getBlock() : Blocks.AIR, "correct plant remains");
            }
            h.setBlock(CELL, GroundscapeBlocks.get(family).patch().defaultBlockState().setValue(SodPatchBlock.LAYERS, 8));
            h.assertValueEqual(plant.canSurvive(level, pos.above()), nativeAllowed, "eight layers support native plant above");
        }
        h.succeed();
    }

    @GameTest
    public void decorativeAmountsRotationDropsAndBoneMeal(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(CELL);
        var player = h.makeMockServerPlayerInLevel(); player.setGameMode(GameType.SURVIVAL);
        h.setBlock(CELL.below(), Blocks.STONE);
        try {
            for (Block block : new Block[]{Blocks.LEAF_LITTER, Blocks.WILDFLOWERS, Blocks.PINK_PETALS}) {
                var bare = GroundscapeBlocks.get(SurfaceFamily.GRASS).patch().defaultBlockState().setValue(SodPatchBlock.LAYERS, 4);
                h.setBlock(CELL, bare);
                var stack = new ItemStack(block, 5); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var amount = ((SegmentableBlock) block).getSegmentAmountProperty();
                for (int n = 1; n <= 4; n++) {
                    var state = h.getBlockState(CELL);
                    h.assertTrue(state.useItemOn(stack, level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)).consumesAction(), "append native decoration");
                    var plant = h.getBlockState(CELL).getValue(SodPatchBlock.VEGETATION).plant();
                    h.assertValueEqual(plant.getValue(amount), n, "native amount retained");
                    var rotated = h.getBlockState(CELL).rotate(Rotation.CLOCKWISE_90).getValue(SodPatchBlock.VEGETATION).plant();
                    h.assertValueEqual(rotated, plant.rotate(Rotation.CLOCKWISE_90), "native facing rotation");
                }
                h.assertFalse(h.getBlockState(CELL).useItemOn(stack, level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)).consumesAction(), "no fifth segment");
                h.assertValueEqual(stack.getCount(), 1, "four segments consume exactly four items");
                if (block instanceof FlowerBedBlock) {
                    var state = h.getBlockState(CELL);
                    ((SodPatchBlock) state.getBlock()).performBonemeal(level, net.minecraft.util.RandomSource.create(42), pos, state, BonemealSource.INTERACTION);
                    h.assertItemEntityCountIs(block.asItem(), CELL, 1.5, 1);
                    clearItems(h, pos);
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
                h.assertTrue(player.gameMode.destroyBlock(pos), "first break removes decoration");
                h.assertValueEqual(h.getBlockState(CELL), bare, "first break keeps layer height");
                // Litter uses its native loot table; flowers drop their stored amount.
                if (block != Blocks.LEAF_LITTER) h.assertItemEntityCountIs(block.asItem(), CELL, 1.5, 4);
                clearItems(h, pos);
            }
        } finally { level.getServer().getPlayerList().remove(player); }
        h.succeed();
    }

    @GameTest
    public void leafLayersRecoverExactlyAndNeverBecomeCanopies(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(CELL);
        h.setBlock(CELL.below(), Blocks.STONE);
        h.assertValueEqual(GroundscapeLeaves.blocks().size(), 14, "all fourteen native leaf types");
        for (var family : LeafFamily.values()) for (int n = 1; n <= 8; n++) {
            var patch = GroundscapeLeaves.get(family);
            var state = patch.defaultBlockState().setValue(LeafPatchBlock.LAYERS, n);
            h.setBlock(CELL, state);
            h.assertFalse(state.is(net.minecraft.tags.BlockTags.LEAVES), "patch is not native canopy");
            h.assertFalse(state.isRandomlyTicking(), "no decay tick");
            h.assertTrue(state.getCollisionShape(level, pos).max(Direction.Axis.Y) == n / 8.0, "exact layer collision");
            var drops = Block.getDrops(state, level, pos, null, null, ItemStack.EMPTY);
            h.assertValueEqual(drops.stream().mapToInt(ItemStack::getCount).sum(), n, "one drop per layer");
            h.assertTrue(drops.stream().allMatch(item -> item.is(patch.asItem())), "no sapling or apple drops");
            h.setBlock(CELL.below(), Blocks.AIR);
            h.assertTrue(h.getBlockState(CELL).isAir(), "unsupported leaf patch breaks");
            h.setBlock(CELL.below(), Blocks.STONE); clearItems(h, pos);
        }
        h.succeed();
    }

    @GameTest
    public void leafDistributionIsSparseSeededAndBounded(GameTestHelper h) {
        var config = new GroundscapeWorldgenConfig.LeafSettings(true, .03, 2, 24, 8, Map.of());
        int total = 0, doubles = 0, changed = 0;
        for (int x = -256; x < 256; x++) for (int z = -256; z < 256; z++) {
            int n = LeafPatchPlanner.layers(20261026, x, z, config);
            if (n > 0) total++;
            if (n == 2) doubles++;
            h.assertValueEqual(n, LeafPatchPlanner.layers(20261026, x, z, config), "deterministic coordinate sample");
            if (n != LeafPatchPlanner.layers(123, x, z, config)) changed++;
        }
        double coverage = total / 262144.0;
        h.assertTrue(coverage > .02 && coverage < .04, "raw candidate coverage within 2-4 percent: " + coverage);
        h.assertTrue(doubles > total * .07 && doubles < total * .13, "mostly single layers");
        h.assertTrue(changed > total, "seed changes placement");
        h.assertValueEqual(LeafPatchPlanner.layers(1, 0, 0, GroundscapeWorldgenConfig.LeafSettings.defaults()), 0, "default off");
        h.succeed();
    }

    @GameTest
    public void nearestCanopyRejectsPlayerLeavesAndObstructions(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(CELL);
        var config = new GroundscapeWorldgenConfig.LeafSettings(true, .03, 2, 24, 8, Map.of());
        for (var family : LeafFamily.values()) {
            h.setBlock(CELL.above(3), family.source().defaultBlockState().setValue(BlockStateProperties.DISTANCE, 1).setValue(BlockStateProperties.PERSISTENT, false));
            h.assertValueEqual(LeafPatchPlanner.canopy(level, pos, p -> true, config), family, "matching canopy");
            h.setBlock(CELL.above(2), Blocks.STONE);
            h.assertTrue(LeafPatchPlanner.canopy(level, pos, p -> true, config) == null, "ceiling blocks canopy");
            h.setBlock(CELL.above(2), Blocks.AIR);
            h.setBlock(CELL.above(3), family.source().defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true));
            h.assertTrue(LeafPatchPlanner.canopy(level, pos, p -> true, config) == null, "player canopy excluded");
        }
        h.succeed();
    }

    private static void clearItems(GameTestHelper h, BlockPos pos) {
        h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(1.5)).forEach(net.minecraft.world.entity.item.ItemEntity::discard);
    }

    @GameTest
    public void leafConfigDefaultsValidationAndIndependentDensity(GameTestHelper h) throws java.io.IOException {
        var path = java.nio.file.Files.createTempFile("groundscape-leaf-config-", ".json");
        try {
            java.nio.file.Files.writeString(path, "{\"enabled\":true,\"density\":0}");
            var legacy = GroundscapeWorldgenConfig.load(path);
            h.assertTrue(legacy.enabled() && !legacy.leafPatches().active(), "old settings never enable new decoration");
            java.nio.file.Files.writeString(path, "{\"enabled\":true,\"density\":0,\"leafPatches\":{\"enabled\":true,\"types\":{\"oak\":false}}}");
            var enabled = GroundscapeWorldgenConfig.load(path);
            h.assertTrue(enabled.enabled() && enabled.density() == 0 && enabled.leafPatches().active(), "independent leaf enable");
            h.assertFalse(enabled.leafPatches().types().get("oak"), "per species off");
            h.assertTrue(enabled.leafPatches().types().get("birch"), "missing species defaults on inside enabled group");
            for (String invalid : new String[]{"null", "{\"enabled\":1}", "{\"coverage\":0.2}", "{\"maxLayers\":3}", "{\"searchHeight\":1.2}", "{\"maxPerChunk\":-1}", "{\"types\":{\"unknown\":true}}", "{\"types\":{\"oak\":null}}"}) {
                String content = "{\"enabled\":true,\"leafPatches\":" + invalid + "}";
                java.nio.file.Files.writeString(path, content);
                h.assertFalse(GroundscapeWorldgenConfig.load(path).enabled(), "invalid leaf settings disable generation");
                h.assertValueEqual(java.nio.file.Files.readString(path), content, "invalid user settings preserved");
            }
        } finally { java.nio.file.Files.deleteIfExists(path); }
        h.succeed();
    }

    @GameTest
    public void plantingRejectsBlockedSpaceAndUnsupportedGrowth(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        var level = h.getLevel(); var pos = h.absolutePos(CELL);
        h.setBlock(CELL.below(), Blocks.STONE); h.setBlock(CELL.above(), Blocks.STONE);
        var patch = GroundscapeBlocks.get(SurfaceFamily.DIRT).patch();
        var bare = patch.defaultBlockState().setValue(SodPatchBlock.LAYERS, 7);
        h.setBlock(CELL, bare);
        var stack = new ItemStack(Blocks.POPPY, 2); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        h.assertFalse(bare.useItemOn(stack, level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)).consumesAction(), "blocked flower rejected");
        h.assertValueEqual(stack.getCount(), 2, "no failed consumption");
        h.assertValueEqual(h.getBlockState(CELL), bare, "no layer loss");
        h.setBlock(CELL.above(), Blocks.AIR);
        var grass = bare.setValue(SodPatchBlock.VEGETATION, SodPatchBlock.Vegetation.GRASS);
        h.setBlock(CELL, grass);
        h.assertFalse(patch.isValidBonemealTarget(level, pos, grass, BonemealSource.INTERACTION), "unsupported two-block growth consumes no bone meal");
        var dry = bare.setValue(SodPatchBlock.VEGETATION, SodPatchBlock.Vegetation.SHORT_DRY_GRASS);
        h.setBlock(CELL, dry);
        h.assertTrue(patch.isValidBonemealTarget(level, pos, dry, BonemealSource.INTERACTION), "single-block dry grass growth supported");
        patch.performBonemeal(level, net.minecraft.util.RandomSource.create(2), pos, dry, BonemealSource.INTERACTION);
        h.assertBlockProperty(CELL, SodPatchBlock.VEGETATION, SodPatchBlock.Vegetation.TALL_DRY_GRASS);
        h.assertBlockProperty(CELL, SodPatchBlock.LAYERS, 7);
        h.succeed();
    }
}
