package com.davidblackcn.worldgen;

import com.davidblackcn.registry.SurfaceFamily;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Conservative G1 material/biome rules; third-party mapping remains a later milestone. */
public record SurfaceProfile(SurfaceFamily family, double coverageScale) {
	private static final class BuiltinBiomeKeys {
		// Use the vanilla bootstrap, not the loaded world's datapack/mod registry.
		// Retain only keys; the complete bootstrap lookup need not stay reachable.
		private static final Set<ResourceKey<Biome>> KEYS = VanillaRegistries.createWorldLookup()
				.lookupOrThrow(Registries.BIOME).listElementIds().collect(Collectors.toUnmodifiableSet());
	}

	public static Optional<SurfaceProfile> forSurface(BlockState ground, Holder<Biome> biome) {
		var key = biome.unwrapKey();
		if (key.isEmpty() || !key.get().identifier().getNamespace().equals("minecraft")
				|| !BuiltinBiomeKeys.KEYS.contains(key.get())) {
			return Optional.empty();
		}
		String name = key.get().identifier().getPath();
		if (biome.value().getBaseTemperature() < 0.15F || name.contains("snow") || name.contains("frozen")
				|| name.contains("ocean") || name.contains("river")
				|| (name.contains("beach") && !ground.is(net.minecraft.world.level.block.Blocks.SAND)
						&& !ground.is(net.minecraft.world.level.block.Blocks.GRAVEL))
				|| name.contains("cave") || name.equals("grove") || name.equals("jagged_peaks")
				|| name.equals("deep_dark") || name.startsWith("end_") || name.equals("the_end")) {
			return Optional.empty();
		}
		for (SurfaceFamily family : SurfaceFamily.values()) {
			if (!ground.is(family.vanillaBlock())) continue;
			double scale = switch (family) {
				case GRASS, SAND, DIRT, COARSE_DIRT, RED_SAND, GRAVEL, ROOTED_DIRT -> 1.0;
				case MYCELIUM -> 0.8;
				case PODZOL -> 0.75;
				case CRIMSON_NYLIUM, WARPED_NYLIUM -> 0.65;
			};
			if (name.contains("swamp")) scale *= 0.35;
			else if (name.contains("forest") || name.contains("taiga") || name.contains("jungle")) scale *= 0.75;
			return Optional.of(new SurfaceProfile(family, scale));
		}
		return Optional.empty();
	}
}
