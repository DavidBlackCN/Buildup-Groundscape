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
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A stable decorative surface, with no grass spreading or melting behavior. */
public final class SodPatchBlock extends Block {
	public static final int MAX_LAYERS = 8;
	public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
	private static final VoxelShape[] SHAPES = createShapes();

	public SodPatchBlock(BlockBehaviour.Properties properties) {
		super(properties.isValidSpawn((state, level, pos, entityType) -> false));
		registerDefaultState(stateDefinition.any().setValue(LAYERS, 1));
	}

	private static VoxelShape[] createShapes() {
		VoxelShape[] shapes = new VoxelShape[MAX_LAYERS + 1];
		shapes[0] = Shapes.empty();
		for (int layers = 1; layers <= MAX_LAYERS; layers++) {
			shapes[layers] = Block.box(0, 0, 0, 16, layers * 2, 16);
		}
		return shapes;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(LAYERS)];
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(LAYERS)];
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
		return SHAPES[state.getValue(LAYERS)];
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return SHAPES[state.getValue(LAYERS)];
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return type == PathComputationType.LAND && state.getValue(LAYERS) <= MAX_LAYERS / 2;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
			// The vanilla neighbor-update pipeline destroys this state and evaluates its loot once.
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return context.getItemInHand().is(asItem())
				&& state.getValue(LAYERS) < MAX_LAYERS
				&& (!context.replacingClickedOnBlock() || context.getClickedFace() == Direction.UP);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		if (!context.getLevel().getFluidState(context.getClickedPos()).isEmpty()) {
			return null;
		}
		BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
		if (existing.is(this)) {
			int layers = existing.getValue(LAYERS);
			return layers < MAX_LAYERS ? existing.setValue(LAYERS, layers + 1) : null;
		}
		return defaultBlockState();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LAYERS);
	}
}
