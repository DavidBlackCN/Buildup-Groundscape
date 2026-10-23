package com.davidblackcn.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The five surface materials shared by registration, data generation and tinting. */
public enum SurfaceFamily {
	GRASS("grass", Blocks.GRASS_BLOCK),
	MYCELIUM("mycelium", Blocks.MYCELIUM),
	PODZOL("podzol", Blocks.PODZOL),
	CRIMSON_NYLIUM("crimson_nylium", Blocks.CRIMSON_NYLIUM),
	WARPED_NYLIUM("warped_nylium", Blocks.WARPED_NYLIUM);

	private final String id;
	private final Block vanillaBlock;

	SurfaceFamily(String id, Block vanillaBlock) {
		this.id = id;
		this.vanillaBlock = vanillaBlock;
	}

	public String id() {
		return id;
	}

	public Block vanillaBlock() {
		return vanillaBlock;
	}

	public boolean usesGrassTint() {
		return this == GRASS;
	}

	/** Copy material properties deliberately, without inherited random ticks or vanilla loot. */
	public BlockBehaviour.Properties properties() {
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
				.mapColor(vanillaBlock.defaultMapColor())
				.strength(vanillaBlock.defaultDestroyTime(), vanillaBlock.getExplosionResistance())
				.sound(vanillaBlock.defaultBlockState().getSoundType());
		if (vanillaBlock.defaultBlockState().requiresCorrectToolForDrops()) {
			properties.requiresCorrectToolForDrops();
		}
		return properties;
	}
}
