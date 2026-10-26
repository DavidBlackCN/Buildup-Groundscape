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
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.ArrayList;
import java.util.List;

/** A stable decorative surface, with no grass spreading or melting behavior. */
public final class SodPatchBlock extends Block {
	public static final int MAX_LAYERS = 8;
	public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
	public static final EnumProperty<Vegetation> VEGETATION = EnumProperty.create("vegetation", Vegetation.class);
	public enum Vegetation implements StringRepresentable {
		NONE("none"), GRASS("grass"), FERN("fern");
		private final String name;
		Vegetation(String name) { this.name = name; }
		@Override public String getSerializedName() { return name; }
		public BlockState plant() { return (this == GRASS ? Blocks.SHORT_GRASS : this == FERN ? Blocks.FERN : Blocks.AIR).defaultBlockState(); }
		public static Vegetation from(BlockState state) {
			return state.is(Blocks.SHORT_GRASS) ? GRASS : state.is(Blocks.FERN) ? FERN : NONE;
		}
	}
	public static boolean canContain(BlockState state) { return Vegetation.from(state) != Vegetation.NONE; }
	private static final VoxelShape[] SHAPES = createShapes();

	public SodPatchBlock(BlockBehaviour.Properties properties) {
		super(properties.isValidSpawn((state, level, pos, entityType) -> false));
		registerDefaultState(stateDefinition.any().setValue(LAYERS, 1).setValue(VEGETATION, Vegetation.NONE));
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
		int layers = state.getValue(LAYERS);
		return state.getValue(VEGETATION) == Vegetation.NONE ? SHAPES[layers]
				: Shapes.or(SHAPES[layers], Block.box(2, layers * 2, 2, 14, layers * 2 + 13, 14));
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
		// Partial sod keeps its native grass/mycelium/nylium support alive. A full
		// bottom face must not make a thin patch an opaque ceiling for random ticks.
		return state.getValue(LAYERS) == MAX_LAYERS;
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
		if (direction == Direction.UP && !neighborState.isAir() && state.getValue(VEGETATION) != Vegetation.NONE) {
			ticks.scheduleTick(pos, this, 1);
		}
		return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(VEGETATION) != Vegetation.NONE && !level.getBlockState(pos.above()).isAir()) {
			level.setBlock(pos, state.setValue(VEGETATION, Vegetation.NONE), Block.UPDATE_ALL);
			Block.dropResources(state.getValue(VEGETATION).plant(), level, pos);
		}
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
		if (state.getValue(VEGETATION) != Vegetation.NONE) {
			// Delegate the plant to its native loot table, retaining tool, fortune and explosion context.
			try { drops.addAll(state.getValue(VEGETATION).plant().getDrops(params)); }
			finally { params.withParameter(LootContextParams.BLOCK_STATE, state); }
		}
		return drops;
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return state.getValue(VEGETATION) == Vegetation.NONE ? super.getDestroyProgress(state, player, level, pos)
				: state.getValue(VEGETATION).plant().getDestroyProgress(player, level, pos);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (state.getValue(VEGETATION) == Vegetation.NONE) return super.playerWillDestroy(level, pos, state, player);
		BlockState plant = state.getValue(VEGETATION).plant();
		// Native particles, sound and game event describe the plant being removed.
		return plant.getBlock().playerWillDestroy(level, pos, plant, player);
	}

	/** Called only after vanilla permissions and Fabric BEFORE listeners have accepted the break. */
	public static boolean removeVegetationForPlayer(Level level, BlockPos pos, Player player) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof SodPatchBlock) || state.getValue(VEGETATION) == Vegetation.NONE) return false;
		BlockState plant = state.getValue(VEGETATION).plant();
		// Keep the original block and height, including during client prediction. Do not briefly
		// replace it with air: that would invalidate neighboring blocks and lose the second target.
		if (!level.setBlock(pos, state.setValue(VEGETATION, Vegetation.NONE), Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE)) return true;
		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer && !serverPlayer.preventsBlockDrops()) {
			ItemStack tool = player.getMainHandItem();
			ItemStack lootTool = tool.copy();
			boolean correctTool = player.hasCorrectToolForDrops(plant);
			tool.mineBlock(level, plant, pos, player);
			if (correctTool) plant.getBlock().playerDestroy(server, serverPlayer, pos, plant, null, lootTool);
		}
		return true;
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
		builder.add(LAYERS, VEGETATION);
	}
}
