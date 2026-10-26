package com.davidblackcn.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSources;

import com.davidblackcn.registry.GroundscapeBlocks;
import com.davidblackcn.registry.SurfaceFamily;

import java.util.List;

public class BuildupGroundscapeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		var grass = GroundscapeBlocks.get(SurfaceFamily.GRASS);
		// The native source uses the actual block position and the configured biome blend radius.
		// It also colors terrain particles; the item tint is generated separately in items/*.json.
		BlockColorRegistry.register(List.of(BlockTintSources.grass()),
				grass.block(), grass.patch(), grass.slab(), grass.stairs());
		for (var variant : GroundscapeBlocks.variants()) {
			// Plant planes use index 1; non-grass sod faces remain untinted in their models.
			BlockColorRegistry.register(List.of(variant.family().usesGrassTint()
					? BlockTintSources.grass() : BlockTintSources.constant(-1), BlockTintSources.grass()), variant.patch());
		}
	}
}
