package com.davidblackcn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Stable leaf material, deliberately not a native tree canopy or a vegetation container. */
public final class LeafPatchBlock extends Block {
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    private static final VoxelShape[] SHAPES = new VoxelShape[9];
    static { for (int i = 1; i <= 8; i++) SHAPES[i] = Block.box(0, 0, 0, 16, i * 2, 16); }
    public LeafPatchBlock(BlockBehaviour.Properties properties) {
        super(properties.destroyTime(Blocks.MOSS_CARPET.defaultDestroyTime()).noOcclusion().isValidSpawn((s, l, p, e) -> false));
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1));
    }
    @Override
    protected boolean canBeReplaced(BlockState state, net.minecraft.world.level.material.Fluid fluid) {
        return fluid.is(net.minecraft.tags.FluidTags.WATER) || super.canBeReplaced(state, fluid);
    }

    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPES[s.getValue(LAYERS)]; }
    @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPES[s.getValue(LAYERS)]; }
    @Override protected VoxelShape getBlockSupportShape(BlockState s, BlockGetter l, BlockPos p) { return SHAPES[s.getValue(LAYERS)]; }
    @Override protected boolean canSurvive(BlockState s, LevelReader l, BlockPos p) { return l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP); }
    @Override protected BlockState updateShape(BlockState s, LevelReader l, ScheduledTickAccess t, BlockPos p, Direction d, BlockPos n, BlockState ns, RandomSource r) {
        return d == Direction.DOWN && !s.canSurvive(l, p) ? Blocks.AIR.defaultBlockState() : super.updateShape(s, l, t, p, d, n, ns, r);
    }
    @Override protected boolean canBeReplaced(BlockState s, BlockPlaceContext c) {
        return c.getItemInHand().is(asItem()) && s.getValue(LAYERS) < 8 && (!c.replacingClickedOnBlock() || c.getClickedFace() == Direction.UP);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        if (!c.getLevel().getFluidState(c.getClickedPos()).isEmpty()) return null;
        BlockState s = c.getLevel().getBlockState(c.getClickedPos());
        return s.is(this) ? (s.getValue(LAYERS) < 8 ? s.setValue(LAYERS, s.getValue(LAYERS) + 1) : null) : defaultBlockState();
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LAYERS); }
}
