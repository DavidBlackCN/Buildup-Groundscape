package com.davidblackcn.test;

import com.davidblackcn.block.SodPatchBlock;
import com.davidblackcn.registry.GroundscapeBlocks;
import com.davidblackcn.registry.SurfaceFamily;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Exercise the real server game-mode method, including Fabric cancellation and our injection. */
public final class GroundscapePlantBreakingTests {
    private static final BlockPos CELL = new BlockPos(3, 3, 3);

    @GameTest
    public void firstBreakRemovesPlantSecondBreakRemovesPatch(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        BlockPos pos = h.absolutePos(CELL);
        h.setBlock(CELL.below(), Blocks.GRASS_BLOCK);
        try {
            for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
                player.setGameMode(mode);
                for (var variant : GroundscapeBlocks.variants()) for (int n = 1; n <= 8; n++) {
                    for (var vegetation : List.of(SodPatchBlock.Vegetation.GRASS, SodPatchBlock.Vegetation.FERN)) {
                        clearItems(h, pos);
                        ItemStack tool = new ItemStack(Items.SHEARS);
                        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                        var state = variant.patch().defaultBlockState().setValue(SodPatchBlock.LAYERS, n)
                                .setValue(SodPatchBlock.VEGETATION, vegetation);
                        h.setBlock(CELL, state);
                        var plant = vegetation.plant();
                        int statistic = player.getStats().getValue(Stats.BLOCK_MINED.get(plant.getBlock()));
                        h.assertTrue(state.getDestroyProgress(player, h.getLevel(), pos) >= 1, "plant uses native instant breaking speed");
                        h.assertTrue(player.gameMode.destroyBlock(pos), "first left break succeeds");
                        h.assertValueEqual(h.getBlockState(CELL), state.setValue(SodPatchBlock.VEGETATION, SodPatchBlock.Vegetation.NONE), "same patch and all layers survive first break");
                        h.assertItemEntityCountIs(variant.patch().asItem(), CELL, 1.5, 0);
                        h.assertItemEntityCountIs(plant.getBlock().asItem(), CELL, 1.5, mode == GameType.CREATIVE ? 0 : 1);
                        h.assertValueEqual(player.getStats().getValue(Stats.BLOCK_MINED.get(plant.getBlock())),
                                statistic + (mode == GameType.SURVIVAL ? 1 : 0), "native plant statistic, not patch statistic");
                        if (mode == GameType.SURVIVAL) {
                            // Compare durability with the same tool's native plant mining callback.
                            ItemStack expected = new ItemStack(Items.SHEARS);
                            expected.mineBlock(h.getLevel(), plant, pos, player);
                            h.assertValueEqual(tool.getDamageValue(), expected.getDamageValue(), "native shears durability exactly once");
                        } else h.assertValueEqual(tool.getDamageValue(), 0, "creative keeps tool intact");
                        clearItems(h, pos);
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                        h.assertTrue(player.gameMode.destroyBlock(pos), "second left break uses normal patch removal");
                        h.assertTrue(h.getBlockState(CELL).isAir(), "second break removes the patch");
                        h.assertItemEntityCountIs(variant.patch().asItem(), CELL, 1.5, 0);
                        h.assertItemEntityCountIs(plant.getBlock().asItem(), CELL, 1.5, 0);
                    }
                }
            }
        } finally {
            clearItems(h, pos);
            h.getLevel().getServer().getPlayerList().remove(player);
        }
        h.succeed();
    }

    @GameTest
    public void emptyHandUsesPlantLootAndBarePatchHardness(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        var pos = h.absolutePos(CELL);
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            h.setBlock(CELL.below(), Blocks.STONE);
            for (var plant : List.of(SodPatchBlock.Vegetation.GRASS, SodPatchBlock.Vegetation.FERN)) {
                clearItems(h, pos);
                var bare = GroundscapeBlocks.get(SurfaceFamily.GRASS).patch().defaultBlockState().setValue(SodPatchBlock.LAYERS, 5);
                h.setBlock(CELL, bare.setValue(SodPatchBlock.VEGETATION, plant));
                h.assertTrue(player.gameMode.destroyBlock(pos), "empty hand removes plant");
                h.assertValueEqual(h.getBlockState(CELL), bare, "empty hand keeps layers");
                h.assertTrue(bare.getDestroyProgress(player, h.getLevel(), pos) < 1, "bare patch returns to its normal hardness");
                for (ItemEntity item : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5))) {
                    h.assertTrue(item.getItem().is(Items.WHEAT_SEEDS), "empty hand only produces native seed loot");
                }
                h.assertTrue(player.gameMode.destroyBlock(pos), "bare patch can be broken normally");
                h.assertItemEntityCountIs(bare.getBlock().asItem(), CELL, 1.5, 0);
            }
        } finally {
            clearItems(h, pos);
            h.getLevel().getServer().getPlayerList().remove(player);
        }
        h.succeed();
    }

    @GameTest
    public void gameModeToolAndFabricProtectionStillCancelPlantBreaking(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        var pos = h.absolutePos(CELL);
        AtomicReference<BlockPos> veto = new AtomicReference<>();
        PlayerBlockBreakEvents.BEFORE.register((level, actor, target, state, entity) -> !target.equals(veto.get()));
        var state = GroundscapeBlocks.get(SurfaceFamily.GRASS).patch().defaultBlockState()
                .setValue(SodPatchBlock.LAYERS, 6).setValue(SodPatchBlock.VEGETATION, SodPatchBlock.Vegetation.FERN);
        h.setBlock(CELL.below(), Blocks.STONE);
        try {
            for (var mode : List.of(GameType.SPECTATOR, GameType.ADVENTURE)) {
                player.setGameMode(mode); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                h.setBlock(CELL, state);
                h.assertFalse(player.gameMode.destroyBlock(pos), "restricted mode cannot remove vegetation");
                h.assertValueEqual(h.getBlockState(CELL), state, "restricted composite remains intact");
            }
            player.setGameMode(GameType.CREATIVE);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            h.assertFalse(player.gameMode.destroyBlock(pos), "native creative sword restriction retained");
            h.assertValueEqual(h.getBlockState(CELL), state, "sword leaves plant intact");
            player.setGameMode(GameType.SURVIVAL);
            ItemStack shears = new ItemStack(Items.SHEARS); player.setItemInHand(InteractionHand.MAIN_HAND, shears);
            veto.set(pos);
            h.assertFalse(player.gameMode.destroyBlock(pos), "Fabric protection vetoes before our mutation");
            h.assertValueEqual(h.getBlockState(CELL), state, "protection keeps plant and height");
            h.assertValueEqual(shears.getDamageValue(), 0, "protection consumes no tool durability");
            h.assertItemEntityCountIs(Items.FERN, CELL, 1.5, 0);
        } finally {
            veto.set(null);
            h.getLevel().getServer().getPlayerList().remove(player);
        }
        h.succeed();
    }

    private static void clearItems(GameTestHelper h, BlockPos pos) {
        h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5)).forEach(ItemEntity::discard);
    }
}
