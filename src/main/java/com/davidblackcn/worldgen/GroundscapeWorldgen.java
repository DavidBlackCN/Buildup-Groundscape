package com.davidblackcn.worldgen;

import com.davidblackcn.BuildupGroundscape;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.List;

/** Registers the 26.3 feature codec independently of the experimental enable flag. */
public final class GroundscapeWorldgen {
	/** Persisted only while a real Feature-approved ProtoChunk awaits its first FULL conversion. */
	public static final AttachmentType<Boolean> PENDING_PATCHES = AttachmentRegistry.createPersistent(
			BuildupGroundscape.id("pending_natural_sod_patches"), Codec.BOOL);
	public static final AttachmentType<List<BoundingBox>> STRUCTURE_BOUNDS = AttachmentRegistry.createPersistent(
			BuildupGroundscape.id("pending_sod_structure_bounds"), BoundingBox.CODEC.listOf());
	public static final ResourceKey<PlacedFeature> NATURAL_SOD_PATCHES = ResourceKey.create(
			Registries.PLACED_FEATURE, BuildupGroundscape.id("natural_sod_patches"));

	private GroundscapeWorldgen() {
	}

	public static void initialize() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, BuildupGroundscape.id("natural_sod_patches"),
				SodPatchFeature.CODEC);
		ServerChunkEvents.CHUNK_LOAD.register(SodPatchFeature::onChunkLoad);
		GroundscapeWorldgenConfig config = GroundscapeWorldgenConfig.current();
		if (config.enabled()) {
			BiomeModifications.addFeature(BiomeSelectors.vanilla().and(
					BiomeSelectors.foundInOverworld().or(BiomeSelectors.foundInTheNether())),
					GenerationStep.Decoration.TOP_LAYER_MODIFICATION, NATURAL_SOD_PATCHES);
		}
		BuildupGroundscape.LOGGER.info("Natural sod patches: {} (density={}, maximum layers={})",
				config.enabled() ? "experimental enabled" : "disabled", config.density(), config.maxLayers());
	}
}
