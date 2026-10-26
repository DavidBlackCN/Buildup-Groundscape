package com.davidblackcn.test;

import com.davidblackcn.registry.GroundscapeBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;

/** Exercises vanilla terrain sampling and its final map pixel buffer, not only block properties. */
public final class GroundscapeMapTests {
    @GameTest(maxTicks = 100)
    public void allThirtyEightVariantsRasterizeWithTheirVanillaMapMaterial(GameTestHelper helper) {
        List<Sample> samples = new ArrayList<>();
        int sampleIndex = 0;
        for (var variant : GroundscapeBlocks.variants()) {
            for (Block block : variant.blocks()) {
                BlockPos relative = new BlockPos(1 + sampleIndex % 7, 6, 1 + sampleIndex++ / 7);
                placeSample(helper, relative, block);
                samples.add(new Sample(helper.absolutePos(relative),
                        variant.family().vanillaBlock().defaultMapColor().id,
                        variant.family().id() + " " + block.getDescriptionId()));
            }
            // A separate physical vanilla column proves that the map scanner has a valid control.
            BlockPos control = new BlockPos(1 + sampleIndex % 7, 6, 1 + sampleIndex++ / 7);
            placeSample(helper, control, variant.family().vanillaBlock());
            samples.add(new Sample(helper.absolutePos(control),
                    variant.family().vanillaBlock().defaultMapColor().id,
                    variant.family().id() + " vanilla control"));
        }
        helper.assertValueEqual(samples.size(), 49, "thirty-eight variants and eleven native controls");

        // Scale zero maps one terrain column to one pixel. A fixture can straddle vanilla's
        // 128-block map boundary, so keep as many actual maps as required to cover the samples.
        List<MapItemSavedData> maps = new ArrayList<>();
        for (Sample sample : samples) {
            if (maps.stream().noneMatch(map -> contains(map, sample.pos()))) {
                ItemStack stack = MapItem.create(helper.getLevel(), sample.pos().getX(), sample.pos().getZ(),
                        (byte) 0, false, false);
                MapItemSavedData map = MapItem.getSavedData(stack, helper.getLevel());
                helper.assertTrue(map != null, "created map has server saved data");
                helper.assertTrue(contains(map, sample.pos()), "new map covers its requested terrain column");
                helper.assertTrue(map.scale == 0, "unscaled map uses one terrain column per pixel");
                maps.add(map);
            }
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos center = helper.absolutePos(new BlockPos(3, 7, 3));
        player.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        MapItem mapItem = (MapItem) Items.FILLED_MAP;
        for (MapItemSavedData map : maps) {
            // update advances HoldingPlayer.step and samples one column stripe modulo 16.
            // No test writes map.colors or substitutes a terrain color into the rasterizer.
            for (int step = 0; step < 16; step++) {
                mapItem.update(helper.getLevel(), player, map);
            }
        }

        for (Sample sample : samples) {
            MapItemSavedData map = maps.stream().filter(candidate -> contains(candidate, sample.pos()))
                    .findFirst().orElseThrow();
            int pixelX = sample.pos().getX() - map.centerX + 64;
            int pixelZ = sample.pos().getZ() - map.centerZ + 64;
            int packedColor = Byte.toUnsignedInt(map.colors[pixelX + pixelZ * 128]);
            helper.assertTrue(packedColor != 0, "terrain pixel was populated: " + sample.label());
            // Native shading depends on height and neighboring columns; compare material only.
            helper.assertValueEqual(packedColor >>> 2, sample.materialId(),
                    "vanilla rasterized map material for " + sample.label());
        }
        helper.succeed();
    }

    private static void placeSample(GameTestHelper helper, BlockPos relative, Block block) {
        helper.setBlock(relative.below(), Blocks.STONE);
        helper.setBlock(relative, block.defaultBlockState());
    }

    private static boolean contains(MapItemSavedData map, BlockPos pos) {
        int x = pos.getX() - map.centerX + 64;
        int z = pos.getZ() - map.centerZ + 64;
        return x >= 0 && x < 128 && z >= 0 && z < 128;
    }

    private record Sample(BlockPos pos, int materialId, String label) {
    }
}
