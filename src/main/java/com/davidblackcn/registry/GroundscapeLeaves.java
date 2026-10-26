package com.davidblackcn.registry;
import com.davidblackcn.BuildupGroundscape;
import com.davidblackcn.block.LeafPatchBlock;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.Arrays;
import java.util.List;
public final class GroundscapeLeaves {
    private static final List<LeafPatchBlock> BLOCKS = Arrays.stream(LeafFamily.values()).map(GroundscapeLeaves::register).toList();
    private GroundscapeLeaves() { }
    public static List<LeafPatchBlock> blocks() { return BLOCKS; }
    public static LeafPatchBlock get(LeafFamily family) { return BLOCKS.get(family.ordinal()); }
    private static LeafPatchBlock register(LeafFamily f) {
        var id = BuildupGroundscape.id(f.path());
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        var block = Registry.register(BuiltInRegistries.BLOCK, key, new LeafPatchBlock(BlockBehaviour.Properties.of()
                .setId(key).mapColor(f.source().defaultMapColor()).strength(f.source().defaultDestroyTime(), f.source().getExplosionResistance())
                .sound(f.source().defaultBlockState().getSoundType())));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }
    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("natural_blocks")))
                .register(output -> BLOCKS.forEach(output::accept));
    }
}
