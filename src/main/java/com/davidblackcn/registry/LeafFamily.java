package com.davidblackcn.registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
public enum LeafFamily {
    OAK("oak", Blocks.OAK_LEAVES),
    SPRUCE("spruce", Blocks.SPRUCE_LEAVES),
    BIRCH("birch", Blocks.BIRCH_LEAVES),
    JUNGLE("jungle", Blocks.JUNGLE_LEAVES),
    ACACIA("acacia", Blocks.ACACIA_LEAVES),
    DARK_OAK("dark_oak", Blocks.DARK_OAK_LEAVES),
    MANGROVE("mangrove", Blocks.MANGROVE_LEAVES),
    CHERRY("cherry", Blocks.CHERRY_LEAVES),
    PALE_OAK("pale_oak", Blocks.PALE_OAK_LEAVES),
    AZALEA("azalea", Blocks.AZALEA_LEAVES),
    FLOWERING_AZALEA("flowering_azalea", Blocks.FLOWERING_AZALEA_LEAVES),
    RED_POPLAR("red_poplar", Blocks.RED_POPLAR_LEAVES),
    ORANGE_POPLAR("orange_poplar", Blocks.ORANGE_POPLAR_LEAVES),
    YELLOW_POPLAR("yellow_poplar", Blocks.YELLOW_POPLAR_LEAVES);
    private final String id;
    private final Block source;
    LeafFamily(String id, Block source) { this.id = id; this.source = source; }
    public String id() { return id; }
    public String path() { return id + "_leaves_patch"; }
    public Block source() { return source; }
    public boolean tinted() { return switch(this) { case OAK, SPRUCE, BIRCH, JUNGLE, ACACIA, DARK_OAK, MANGROVE -> true; default -> false; }; }
    public static LeafFamily from(BlockState state) {
        for (var family : values()) if (state.is(family.source)) return family;
        return null;
    }
}
