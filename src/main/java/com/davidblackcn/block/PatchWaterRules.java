package com.davidblackcn.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Shared boundary for water buckets and natural water flow; no new persisted state. */
public final class PatchWaterRules {
    public static final int WATER_RESISTANT_LAYERS = 4;

    private PatchWaterRules() {}

    public static boolean blocksWater(BlockState state) {
        return (state.getBlock() instanceof SodPatchBlock || state.getBlock() instanceof LeafPatchBlock)
                && state.getValue(BlockStateProperties.LAYERS) >= WATER_RESISTANT_LAYERS;
    }
}
