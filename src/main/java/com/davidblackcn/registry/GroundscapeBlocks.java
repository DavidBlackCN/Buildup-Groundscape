package com.davidblackcn.registry;

import com.davidblackcn.BuildupGroundscape;
import com.davidblackcn.block.SodPatchBlock;
import com.davidblackcn.block.SodStairsBlock;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public final class GroundscapeBlocks {
	private static final List<Variant> VARIANTS = Arrays.stream(SurfaceFamily.values())
			.map(GroundscapeBlocks::registerFamily).toList();
	private static final List<Block> ALL_BLOCKS = VARIANTS.stream().flatMap(variant -> variant.blocks().stream()).toList();

	private GroundscapeBlocks() {
	}

	public record Variant(SurfaceFamily family, Block block, SodPatchBlock patch, SlabBlock slab, SodStairsBlock stairs) {
		public List<Block> blocks() {
			return family.hasSodBlock() ? List.of(block, patch, slab, stairs) : List.of(patch, slab, stairs);
		}
	}

	public static List<Variant> variants() {
		return VARIANTS;
	}

	public static Variant get(SurfaceFamily family) {
		return VARIANTS.get(family.ordinal());
	}

	public static List<Block> allBlocks() {
		return ALL_BLOCKS;
	}

	public static void initialize() {
		// Vanilla's tab keys are private in 26.3; use the verified registered identifier.
		CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB,
				Identifier.withDefaultNamespace("natural_blocks")))
				.register(output -> ALL_BLOCKS.forEach(output::accept));
	}

	private static Variant registerFamily(SurfaceFamily family) {
		Block block = family.hasSodBlock() ? register(family, "block", Block::new) : family.vanillaBlock();
		SodPatchBlock patch = register(family, "patch", properties -> new SodPatchBlock(properties, family.vanillaBlock()));
		SlabBlock slab = register(family, "slab", SlabBlock::new);
		SodStairsBlock stairs = register(family, "stairs", properties -> new SodStairsBlock(block.defaultBlockState(), properties));
		return new Variant(family, block, patch, slab, stairs);
	}

	private static <T extends Block> T register(SurfaceFamily family, String shape,
			Function<BlockBehaviour.Properties, T> factory) {
		Identifier id = BuildupGroundscape.id(family.path(shape));
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		T block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(family.properties(!shape.equals("patch")).setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
