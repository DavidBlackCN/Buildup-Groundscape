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
public final class SodPatchBlock extends Block implements net.minecraft.world.level.block.BonemealableBlock {
    private final Block substrate;
    public Block substrate() { return substrate; }
	public static final int MAX_LAYERS = 8;
	public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
	public static final EnumProperty<Vegetation> VEGETATION = EnumProperty.create("vegetation", Vegetation.class);
	public enum Vegetation implements StringRepresentable {
		NONE("none", Blocks.AIR),
		GRASS("grass", Blocks.SHORT_GRASS),
		FERN("fern", Blocks.FERN),
		DANDELION("dandelion", Blocks.DANDELION),
		POPPY("poppy", Blocks.POPPY),
		BLUE_ORCHID("blue_orchid", Blocks.BLUE_ORCHID),
		ALLIUM("allium", Blocks.ALLIUM),
		AZURE_BLUET("azure_bluet", Blocks.AZURE_BLUET),
		RED_TULIP("red_tulip", Blocks.RED_TULIP),
		ORANGE_TULIP("orange_tulip", Blocks.ORANGE_TULIP),
		WHITE_TULIP("white_tulip", Blocks.WHITE_TULIP),
		PINK_TULIP("pink_tulip", Blocks.PINK_TULIP),
		OXEYE_DAISY("oxeye_daisy", Blocks.OXEYE_DAISY),
		CORNFLOWER("cornflower", Blocks.CORNFLOWER),
		LILY_OF_THE_VALLEY("lily_of_the_valley", Blocks.LILY_OF_THE_VALLEY),
		TORCHFLOWER("torchflower", Blocks.TORCHFLOWER),
		DEAD_BUSH("dead_bush", Blocks.DEAD_BUSH),
		SHORT_DRY_GRASS("short_dry_grass", Blocks.SHORT_DRY_GRASS),
		TALL_DRY_GRASS("tall_dry_grass", Blocks.TALL_DRY_GRASS),
		BUSH("bush", Blocks.BUSH),
		LEAF_LITTER_NORTH_1("leaf_litter_north_1", Blocks.LEAF_LITTER, Direction.NORTH, 1),
		LEAF_LITTER_NORTH_2("leaf_litter_north_2", Blocks.LEAF_LITTER, Direction.NORTH, 2),
		LEAF_LITTER_NORTH_3("leaf_litter_north_3", Blocks.LEAF_LITTER, Direction.NORTH, 3),
		LEAF_LITTER_NORTH_4("leaf_litter_north_4", Blocks.LEAF_LITTER, Direction.NORTH, 4),
		LEAF_LITTER_EAST_1("leaf_litter_east_1", Blocks.LEAF_LITTER, Direction.EAST, 1),
		LEAF_LITTER_EAST_2("leaf_litter_east_2", Blocks.LEAF_LITTER, Direction.EAST, 2),
		LEAF_LITTER_EAST_3("leaf_litter_east_3", Blocks.LEAF_LITTER, Direction.EAST, 3),
		LEAF_LITTER_EAST_4("leaf_litter_east_4", Blocks.LEAF_LITTER, Direction.EAST, 4),
		LEAF_LITTER_SOUTH_1("leaf_litter_south_1", Blocks.LEAF_LITTER, Direction.SOUTH, 1),
		LEAF_LITTER_SOUTH_2("leaf_litter_south_2", Blocks.LEAF_LITTER, Direction.SOUTH, 2),
		LEAF_LITTER_SOUTH_3("leaf_litter_south_3", Blocks.LEAF_LITTER, Direction.SOUTH, 3),
		LEAF_LITTER_SOUTH_4("leaf_litter_south_4", Blocks.LEAF_LITTER, Direction.SOUTH, 4),
		LEAF_LITTER_WEST_1("leaf_litter_west_1", Blocks.LEAF_LITTER, Direction.WEST, 1),
		LEAF_LITTER_WEST_2("leaf_litter_west_2", Blocks.LEAF_LITTER, Direction.WEST, 2),
		LEAF_LITTER_WEST_3("leaf_litter_west_3", Blocks.LEAF_LITTER, Direction.WEST, 3),
		LEAF_LITTER_WEST_4("leaf_litter_west_4", Blocks.LEAF_LITTER, Direction.WEST, 4),
		WILDFLOWERS_NORTH_1("wildflowers_north_1", Blocks.WILDFLOWERS, Direction.NORTH, 1),
		WILDFLOWERS_NORTH_2("wildflowers_north_2", Blocks.WILDFLOWERS, Direction.NORTH, 2),
		WILDFLOWERS_NORTH_3("wildflowers_north_3", Blocks.WILDFLOWERS, Direction.NORTH, 3),
		WILDFLOWERS_NORTH_4("wildflowers_north_4", Blocks.WILDFLOWERS, Direction.NORTH, 4),
		WILDFLOWERS_EAST_1("wildflowers_east_1", Blocks.WILDFLOWERS, Direction.EAST, 1),
		WILDFLOWERS_EAST_2("wildflowers_east_2", Blocks.WILDFLOWERS, Direction.EAST, 2),
		WILDFLOWERS_EAST_3("wildflowers_east_3", Blocks.WILDFLOWERS, Direction.EAST, 3),
		WILDFLOWERS_EAST_4("wildflowers_east_4", Blocks.WILDFLOWERS, Direction.EAST, 4),
		WILDFLOWERS_SOUTH_1("wildflowers_south_1", Blocks.WILDFLOWERS, Direction.SOUTH, 1),
		WILDFLOWERS_SOUTH_2("wildflowers_south_2", Blocks.WILDFLOWERS, Direction.SOUTH, 2),
		WILDFLOWERS_SOUTH_3("wildflowers_south_3", Blocks.WILDFLOWERS, Direction.SOUTH, 3),
		WILDFLOWERS_SOUTH_4("wildflowers_south_4", Blocks.WILDFLOWERS, Direction.SOUTH, 4),
		WILDFLOWERS_WEST_1("wildflowers_west_1", Blocks.WILDFLOWERS, Direction.WEST, 1),
		WILDFLOWERS_WEST_2("wildflowers_west_2", Blocks.WILDFLOWERS, Direction.WEST, 2),
		WILDFLOWERS_WEST_3("wildflowers_west_3", Blocks.WILDFLOWERS, Direction.WEST, 3),
		WILDFLOWERS_WEST_4("wildflowers_west_4", Blocks.WILDFLOWERS, Direction.WEST, 4),
		PINK_PETALS_NORTH_1("pink_petals_north_1", Blocks.PINK_PETALS, Direction.NORTH, 1),
		PINK_PETALS_NORTH_2("pink_petals_north_2", Blocks.PINK_PETALS, Direction.NORTH, 2),
		PINK_PETALS_NORTH_3("pink_petals_north_3", Blocks.PINK_PETALS, Direction.NORTH, 3),
		PINK_PETALS_NORTH_4("pink_petals_north_4", Blocks.PINK_PETALS, Direction.NORTH, 4),
		PINK_PETALS_EAST_1("pink_petals_east_1", Blocks.PINK_PETALS, Direction.EAST, 1),
		PINK_PETALS_EAST_2("pink_petals_east_2", Blocks.PINK_PETALS, Direction.EAST, 2),
		PINK_PETALS_EAST_3("pink_petals_east_3", Blocks.PINK_PETALS, Direction.EAST, 3),
		PINK_PETALS_EAST_4("pink_petals_east_4", Blocks.PINK_PETALS, Direction.EAST, 4),
		PINK_PETALS_SOUTH_1("pink_petals_south_1", Blocks.PINK_PETALS, Direction.SOUTH, 1),
		PINK_PETALS_SOUTH_2("pink_petals_south_2", Blocks.PINK_PETALS, Direction.SOUTH, 2),
		PINK_PETALS_SOUTH_3("pink_petals_south_3", Blocks.PINK_PETALS, Direction.SOUTH, 3),
		PINK_PETALS_SOUTH_4("pink_petals_south_4", Blocks.PINK_PETALS, Direction.SOUTH, 4),
		PINK_PETALS_WEST_1("pink_petals_west_1", Blocks.PINK_PETALS, Direction.WEST, 1),
		PINK_PETALS_WEST_2("pink_petals_west_2", Blocks.PINK_PETALS, Direction.WEST, 2),
		PINK_PETALS_WEST_3("pink_petals_west_3", Blocks.PINK_PETALS, Direction.WEST, 3),
		PINK_PETALS_WEST_4("pink_petals_west_4", Blocks.PINK_PETALS, Direction.WEST, 4);

        private final String name;
        private final BlockState plant;
        Vegetation(String name, Block block) { this.name = name; this.plant = block.defaultBlockState(); }
        Vegetation(String name, Block block, Direction facing, int amount) {
            this.name = name;
            this.plant = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                    .setValue(((net.minecraft.world.level.block.SegmentableBlock) block).getSegmentAmountProperty(), amount);
        }
        @Override public String getSerializedName() { return name; }
        public BlockState plant() { return plant; }
        public static Vegetation from(BlockState state) {
            for (Vegetation value : values()) if (value.plant.equals(state)) return value;
            return NONE;
        }
    }

	public static boolean canContain(BlockState state) { return Vegetation.from(state) != Vegetation.NONE; }
	private static final VoxelShape[] SHAPES = createShapes();

	public SodPatchBlock(BlockBehaviour.Properties properties, Block substrate) {
		super(properties.isValidSpawn((state, level, pos, entityType) -> false));
		this.substrate = substrate;
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
				: Shapes.or(SHAPES[layers], state.getValue(VEGETATION).plant().getShape(level, pos, context).move(0, layers / 8.0, 0));
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
		if (direction == Direction.UP && !PlantSupport.hasRoom(level, pos, state) && state.getValue(VEGETATION) != Vegetation.NONE) {
			ticks.scheduleTick(pos, this, 1);
		}
		return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(VEGETATION) != Vegetation.NONE && !PlantSupport.hasRoom(level, pos, state)) {
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
                && PlantSupport.hasRoom(context.getLevel(), context.getClickedPos(), state.setValue(LAYERS, state.getValue(LAYERS) + 1))
				&& (!context.replacingClickedOnBlock() || context.getClickedFace() == Direction.UP);
	}

    @Override
    protected net.minecraft.world.InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem item)
                || !canContain(item.getBlock().defaultBlockState())) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!player.getAbilities().mayBuild || !player.mayUseItemAt(pos, hit.getDirection(), stack)) return net.minecraft.world.InteractionResult.FAIL;
        Vegetation current = state.getValue(VEGETATION);
        if (state.getValue(LAYERS) == 8 && current == Vegetation.NONE) return net.minecraft.world.InteractionResult.PASS;
        if (hit.getDirection() != Direction.UP) return net.minecraft.world.InteractionResult.FAIL;
        BlockState plant = item.getBlock().defaultBlockState();
        if (!PlantSupport.accepts(plant, substrate.defaultBlockState(), level, pos)) return net.minecraft.world.InteractionResult.FAIL;
        if (current != Vegetation.NONE) {
            if (current.plant().getBlock() != item.getBlock() || !(item.getBlock() instanceof net.minecraft.world.level.block.SegmentableBlock segments))
                return net.minecraft.world.InteractionResult.FAIL;
            var amount = segments.getSegmentAmountProperty();
            if (current.plant().getValue(amount) == 4) return net.minecraft.world.InteractionResult.FAIL;
            plant = current.plant().setValue(amount, current.plant().getValue(amount) + 1);
        } else if (plant.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            plant = plant.setValue(BlockStateProperties.HORIZONTAL_FACING, player.getDirection().getOpposite());
        }
        BlockState result = state.setValue(VEGETATION, Vegetation.from(plant));
        if (!PlantSupport.hasRoom(level, pos, result)) return net.minecraft.world.InteractionResult.FAIL;
        if (!level.setBlock(pos, result, Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE)) return net.minecraft.world.InteractionResult.FAIL;
        var sound = plant.getSoundType();
        level.playSound(player, pos, sound.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
        level.gameEvent(player, net.minecraft.world.level.gameevent.GameEvent.BLOCK_PLACE, pos);
        stack.consume(1, player);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, net.minecraft.world.level.block.BonemealSource source) {
        BlockState plant = state.getValue(VEGETATION).plant();
        if (plant.getBlock() instanceof net.minecraft.world.level.block.FlowerBedBlock) return true;
        if (plant.is(Blocks.SHORT_DRY_GRASS)) return PlantSupport.hasRoom(level, pos, state.setValue(VEGETATION, Vegetation.TALL_DRY_GRASS));
        if (plant.is(Blocks.BUSH) || plant.is(Blocks.TALL_DRY_GRASS)) return ((net.minecraft.world.level.block.BonemealableBlock) plant.getBlock()).isValidBonemealTarget(level, pos, plant, source);
        // Two-block grass/fern growth is deliberately unsupported: do not consume bone meal.
        return false;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, net.minecraft.world.level.block.BonemealSource source) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, net.minecraft.world.level.block.BonemealSource source) {
        BlockState plant = state.getValue(VEGETATION).plant();
        if (plant.getBlock() instanceof net.minecraft.world.level.block.FlowerBedBlock flower) {
            var amount = flower.getSegmentAmountProperty();
            if (plant.getValue(amount) < 4) level.setBlock(pos, state.setValue(VEGETATION, Vegetation.from(plant.setValue(amount, plant.getValue(amount) + 1))), Block.UPDATE_ALL);
            else Block.popResource(level, pos, new ItemStack(plant.getBlock()));
        } else if (plant.is(Blocks.SHORT_DRY_GRASS)) {
            level.setBlock(pos, state.setValue(VEGETATION, Vegetation.TALL_DRY_GRASS), Block.UPDATE_ALL);
        } else if (plant.is(Blocks.BUSH) || plant.is(Blocks.TALL_DRY_GRASS)) {
            ((net.minecraft.world.level.block.BonemealableBlock) plant.getBlock()).performBonemeal(level, random, pos, plant, source);
        }
    }

    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(VEGETATION, Vegetation.from(state.getValue(VEGETATION).plant().rotate(rotation)));
    }

    @Override
    public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.setValue(VEGETATION, Vegetation.from(state.getValue(VEGETATION).plant().mirror(mirror)));
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
		if (canContain(existing)) {
            if (!PlantSupport.accepts(existing, substrate.defaultBlockState(), context.getLevel(), context.getClickedPos())) return null;
            BlockState combined = defaultBlockState().setValue(VEGETATION, Vegetation.from(existing));
            return PlantSupport.hasRoom(context.getLevel(), context.getClickedPos(), combined) ? combined : null;
        }
        return defaultBlockState();
    }

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LAYERS, VEGETATION);
	}
}
