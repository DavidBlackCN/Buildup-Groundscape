package com.davidblackcn.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Surface materials shared by registration, data generation and tinting. */
public enum SurfaceFamily {
	GRASS("grass", Blocks.GRASS_BLOCK),
	MYCELIUM("mycelium", Blocks.MYCELIUM),
	PODZOL("podzol", Blocks.PODZOL),
	CRIMSON_NYLIUM("crimson_nylium", Blocks.CRIMSON_NYLIUM),
	WARPED_NYLIUM("warped_nylium", Blocks.WARPED_NYLIUM),
	SAND("sand", Blocks.SAND),
	DIRT("dirt", Blocks.DIRT),
	COARSE_DIRT("coarse_dirt", Blocks.COARSE_DIRT),
	RED_SAND("red_sand", Blocks.RED_SAND),
	GRAVEL("gravel", Blocks.GRAVEL),
	ROOTED_DIRT("rooted_dirt", Blocks.ROOTED_DIRT);

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

	/** Existing IDs are permanent; new families reuse the native full block. */
	public boolean hasSodBlock() {
		return this == GRASS || this == MYCELIUM || this == PODZOL
				|| this == CRIMSON_NYLIUM || this == WARPED_NYLIUM;
	}

	public String path(String shape) {
		return id + (hasSodBlock() ? "_sod_" : "_") + shape;
	}

	public java.util.List<String> forms() {
		return hasSodBlock() ? java.util.List.of("block", "patch", "slab", "stairs")
				: java.util.List.of("patch", "slab", "stairs");
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
