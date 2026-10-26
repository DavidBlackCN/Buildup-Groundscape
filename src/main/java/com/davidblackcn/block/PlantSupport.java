package com.davidblackcn.block;

import com.davidblackcn.mixin.VegetationBlockAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The supported plants use vanilla substrate predicates; litter additionally requires a sturdy face. */
public final class PlantSupport {
    private PlantSupport() { }

    public static boolean accepts(BlockState plant, BlockState substrate, BlockGetter level, BlockPos pos) {
        if (!SodPatchBlock.canContain(plant)) return false;
        if (plant.is(Blocks.LEAF_LITTER)) return substrate.isFaceSturdy(level, pos, Direction.UP);
        return ((VegetationBlockAccessor) plant.getBlock()).groundscape$mayPlaceOn(substrate, level, pos);
    }

    public static boolean hasRoom(BlockGetter level, BlockPos pos, BlockState patch) {
        var vegetation = patch.getValue(SodPatchBlock.VEGETATION);
        if (vegetation == SodPatchBlock.Vegetation.NONE) return true;
        // Cross models extend a full block even when their selection box is shorter.
        // Native segmented decorations have low models contained by their selection shape.
        double plantHeight = vegetation.plant().getBlock() instanceof net.minecraft.world.level.block.SegmentableBlock
                ? vegetation.plant().getShape(level, pos).max(Direction.Axis.Y) : 1;
        double top = plantHeight + patch.getValue(SodPatchBlock.LAYERS) / 8.0;
        return top <= 1 || level.getBlockState(pos.above()).isAir();
    }
}
