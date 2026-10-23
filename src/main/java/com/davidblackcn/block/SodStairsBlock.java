package com.davidblackcn.block;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Exposes the protected vanilla constructor; all stair behavior stays vanilla. */
public final class SodStairsBlock extends StairBlock {
	public SodStairsBlock(BlockState baseState, BlockBehaviour.Properties properties) {
		super(baseState, properties);
	}
}
