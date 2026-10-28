package com.davidblackcn.test;

import com.davidblackcn.block.SodPatchBlock;
import com.davidblackcn.registry.GroundscapeBlocks;
import com.davidblackcn.registry.SurfaceFamily;
import com.davidblackcn.worldgen.*;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.List;

public final class GroundscapeContinuityTests {
    private static final BlockPos CELL = new BlockPos(3, 3, 3);

    @GameTest
    public void overheadConstructionClearsOnlyVegetation(GameTestHelper h) {
        h.setBlock(CELL.below(), Blocks.STONE);
        h.setBlock(CELL, GroundscapeBlocks.get(SurfaceFamily.GRASS).patch().defaultBlockState()
                .setValue(SodPatchBlock.LAYERS, 6).setValue(SodPatchBlock.VEGETATION, SodPatchBlock.Vegetation.GRASS));
        h.setBlock(CELL.above(), Blocks.STONE);
        h.runAfterDelay(3, () -> {
            h.assertBlockProperty(CELL, SodPatchBlock.LAYERS, 6);
            h.assertBlockProperty(CELL, SodPatchBlock.VEGETATION, SodPatchBlock.Vegetation.NONE);
            h.succeed();
        });
    }

    @GameTest
    public void layerCapsRescaleInsteadOfFlatteningEveryHighProposal(GameTestHelper h) {
        int[] low = new int[4], cardinal = new int[4], full = new int[8];
        var profile = new SurfaceProfile(SurfaceFamily.GRASS, 1);
        for (int x = -160; x < 160; x++) for (int z = -32; z < 32; z++) {
            for (int distance : new int[]{1, 2}) {
                var terrain = new SlopeTerrain(0, 0, 0, 1, 1, 0, distance, distance == 2 ? 2 : 0, false);
                low[SlopePlanner.layers(20261024, x, z, profile, terrain, new GroundscapeWorldgenConfig(true, .16, 3))]++;
                full[SlopePlanner.layers(20261024, x, z, profile, terrain, new GroundscapeWorldgenConfig(true, .12, 7))]++;
                var straight = new SlopeTerrain(0, 0, 0, 1, 1, 0, distance, 0, false);
                cardinal[SlopePlanner.layers(20261024, x, z, profile, straight, new GroundscapeWorldgenConfig(true, .16, 3))]++;
            }
        }
        h.assertValueEqual(low[0], 0, "safe ramps have no coverage holes");
        for (int i = 1; i <= 3; i++) h.assertTrue(low[i] > 100, "all capped heights occur: " + i);
        for (int i = 1; i <= 3; i++) h.assertTrue(cardinal[i] > 100, "straight terraces retain all capped heights: " + i);
        h.assertTrue(cardinal[1] > (cardinal[1] + cardinal[2] + cardinal[3]) * .2, "one layer remains common under the user's three-layer cap");
        h.assertTrue(low[3] < low[1] + low[2], "three layers do not swallow the transition distribution");
        h.assertTrue(full[1] > 0 && full[7] > 0, "default natural profile includes both endpoints");
        h.succeed();
    }

    @GameTest
    public void structureProtectionRespectsHeightAndPieceFootprints(GameTestHelper h) {
        var underground = new BoundingBox(0, 10, 0, 15, 25, 15).inflatedBy(2, 3, 2);
        var building = new BoundingBox(4, 63, 4, 8, 72, 8).inflatedBy(2, 3, 2);
        var boxes = List.of(underground, building);
        h.assertFalse(StructureProtection.protects(boxes, new BlockPos(0, 65, 0)), "underground structure does not veto the surface chunk");
        h.assertTrue(StructureProtection.protects(boxes, new BlockPos(5, 65, 5)), "surface structure protected");
        h.assertTrue(StructureProtection.protects(boxes, new BlockPos(2, 65, 5)), "piece edge buffer protected");
        h.assertFalse(StructureProtection.protects(boxes, new BlockPos(14, 65, 14)), "unrelated ground in same chunk remains eligible");
        var decoded = BoundingBox.CODEC.listOf().parse(JsonOps.INSTANCE,
                BoundingBox.CODEC.listOf().encodeStart(JsonOps.INSTANCE, boxes).getOrThrow()).getOrThrow();
        h.assertValueEqual(decoded, boxes, "pending structure snapshot survives serialization");
        h.succeed();
    }

    @GameTest
    public void oldPatchStatesDecodeWithoutLosingLayers(GameTestHelper h) {
        for (var family : GroundscapeBlocks.variants()) {
            for (int n = 1; n <= 8; n++) {
                var json = BlockState.CODEC.encodeStart(JsonOps.INSTANCE,
                        family.patch().defaultBlockState().setValue(SodPatchBlock.LAYERS, n)).getOrThrow();
                if (json.isJsonObject()) for (var entry : json.getAsJsonObject().entrySet()) {
                    if (entry.getValue().isJsonObject()) entry.getValue().getAsJsonObject().remove("vegetation");
                }
                BlockState state = BlockState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                h.assertValueEqual(state.getValue(SodPatchBlock.LAYERS), n, "legacy height retained");
                h.assertValueEqual(state.getValue(SodPatchBlock.VEGETATION), SodPatchBlock.Vegetation.NONE, "legacy state has no invented plant");
            }
        }
        h.succeed();
    }

    @GameTest
    public void vegetationKeepsSodCollisionAndNativeShearsLoot(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(CELL);
        for (var variant : GroundscapeBlocks.variants()) for (int n = 1; n <= 8; n++) {
            var bare = variant.patch().defaultBlockState().setValue(SodPatchBlock.LAYERS, n);
            for (var plant : List.of(SodPatchBlock.Vegetation.GRASS, SodPatchBlock.Vegetation.FERN)) {
                var state = bare.setValue(SodPatchBlock.VEGETATION, plant);
                h.assertFalse(Shapes.joinIsNotEmpty(state.getCollisionShape(level, pos), bare.getCollisionShape(level, pos), BooleanOp.NOT_SAME), "plant adds no solid collision");
                var drops = Block.getDrops(state, level, pos, null, null, new ItemStack(Items.SHEARS));
                int sod = drops.stream().filter(i -> i.is(variant.patch().asItem())).mapToInt(ItemStack::getCount).sum();
                int plants = drops.stream().filter(i -> i.is(plant.plant().getBlock().asItem())).mapToInt(ItemStack::getCount).sum();
                h.assertValueEqual(sod, 0, "ordinary shears do not recover the patch");
                h.assertValueEqual(plants, 1, "native shears loot returns the plant once");
            }
        }
        h.succeed();
    }

    @GameTest
    public void rightClickNoLongerClipsVegetation(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        var pos = h.absolutePos(CELL); var level = h.getLevel();
        var tool = new ItemStack(Items.SHEARS); player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        h.setBlock(CELL.below(), Blocks.STONE);
        var state = GroundscapeBlocks.get(SurfaceFamily.GRASS).patch().defaultBlockState()
                .setValue(SodPatchBlock.LAYERS, 5).setValue(SodPatchBlock.VEGETATION, SodPatchBlock.Vegetation.FERN);
        h.setBlock(CELL, state);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        h.assertFalse(state.useItemOn(tool, level, player, InteractionHand.MAIN_HAND, hit).consumesAction(), "shears right click passes through");
        h.assertValueEqual(h.getBlockState(CELL), state, "right click preserves composite");
        h.assertValueEqual(tool.getDamageValue(), 0, "right click consumes no durability");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertFalse(state.useWithoutItem(level, player, hit).consumesAction(), "empty hand right click passes through");
        h.assertValueEqual(h.getBlockState(CELL), state, "empty hand leaves plant intact");
        h.succeed();
    }
}
