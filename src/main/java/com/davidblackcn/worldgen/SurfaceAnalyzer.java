package com.davidblackcn.worldgen;

import com.davidblackcn.block.SodPatchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Predicate;

/** Read-only, bounded checks shared by natural generation and the gameplay fixtures. */
public final class SurfaceAnalyzer {
	private SurfaceAnalyzer() {
	}

	public enum Rejection {
		NONE, OUT_OF_BOUNDS, OCCUPIED, FLUID, BLOCK_ENTITY, UNKNOWN_SURFACE,
		UNSUPPORTED, STEEP_OR_HOLLOW, PROTECTED_NEIGHBOR
	}

	public record Analysis(SurfaceProfile profile, Rejection rejection) {
		public boolean accepted() {
			return rejection == Rejection.NONE;
		}
	}

	/** A bounded block/biome view can inspect finalized chunks without a loading LevelReader. */
	public interface View {
		BlockGetter blocks();

		Holder<Biome> biome(BlockPos pos);

		int seaLevel();
	}

	/**
	 * The target is the air cell above the original ground. Every block read is first
	 * admitted by canRead; production supplies a finalized chunk view and read budget.
	 * This method never obtains chunks, changes blocks, or queries block entities.
	 */
	public static Analysis analyze(WorldGenLevel level, BlockPos target, Predicate<BlockPos> canRead) {
		return analyze(new View() {
			@Override
			public BlockGetter blocks() {
				return level;
			}

			@Override
			public Holder<Biome> biome(BlockPos pos) {
				return level.getBiome(pos);
			}

			@Override
			public int seaLevel() {
				return level.getSeaLevel();
			}
		}, target, canRead);
	}

	public static Analysis analyze(View view, BlockPos target, Predicate<BlockPos> canRead) {
		BlockGetter level = view.blocks();
		BlockState destination = read(level, target, canRead);
		if (destination == null) {
			return reject(Rejection.OUT_OF_BOUNDS);
		}
		Rejection occupied = SodPatchBlock.canContain(destination) ? Rejection.NONE : occupiedReason(destination);
		if (occupied != Rejection.NONE) {
			return reject(occupied);
		}

		// Retain two full blocks of headroom even above a seven-layer patch.
		for (int dy = 1; dy <= 2; dy++) {
			BlockState headroom = read(level, target.above(dy), canRead);
			if (headroom == null) {
				return reject(Rejection.OUT_OF_BOUNDS);
			}
			Rejection obstruction = occupiedReason(headroom);
			if (obstruction != Rejection.NONE) {
				return reject(obstruction);
			}
		}

		BlockPos groundPos = target.below();
		BlockState ground = read(level, groundPos, canRead);
		if (ground == null) {
			return reject(Rejection.OUT_OF_BOUNDS);
		}
		if (isProtected(ground)) {
			return reject(Rejection.PROTECTED_NEIGHBOR);
		}
		var biome = view.biome(target);
		if (biome.value().coldEnoughToSnow(target, view.seaLevel())) {
			return reject(Rejection.PROTECTED_NEIGHBOR);
		}
		SurfaceProfile profile = SurfaceProfile.forSurface(ground, biome).orElse(null);
		if (profile == null) {
			return reject(Rejection.UNKNOWN_SURFACE);
		}
		if (SodPatchBlock.canContain(destination) && !profile.family().supportsVegetation()) {
			return reject(Rejection.OCCUPIED);
		}
		if (!isFullNaturalSupport(level, groundPos, ground)) {
			return reject(Rejection.UNSUPPORTED);
		}

		// A surface block over a one-block or two-block shell is not safe ground.
		for (int depth = 1; depth <= 2; depth++) {
			BlockPos supportPos = groundPos.below(depth);
			BlockState support = read(level, supportPos, canRead);
			if (support == null) {
				return reject(Rejection.OUT_OF_BOUNDS);
			}
			if (!isFullNaturalSupport(level, supportPos, support)) {
				return reject(Rejection.UNSUPPORTED);
			}
		}

		// Read across chunk edges only through the supplied bounded predicate. Looking
		// at local blocks instead of neighbouring heightmaps ignores our own patches.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos neighborPos = groundPos.offset(dx, dy, dz);
					BlockState neighbor = read(level, neighborPos, canRead);
					if (neighbor == null) {
						return reject(Rejection.OUT_OF_BOUNDS);
					}
					if (isProtected(neighbor)) {
						return reject(Rejection.PROTECTED_NEIGHBOR);
					}
					if (dy == -1 && !isFullNaturalSupport(level, neighborPos, neighbor)) {
						return reject(Rejection.STEEP_OR_HOLLOW);
					}
				}
			}
		}
		return new Analysis(profile, Rejection.NONE);
	}

	/** Earlier decorations by this mod must not change neighbouring decisions. */
	public static boolean isAirBeforeDecoration(BlockState state) {
		return state.isAir() || state.getBlock() instanceof SodPatchBlock;
	}

	private static BlockState read(BlockGetter level, BlockPos pos, Predicate<BlockPos> canRead) {
		return level.isInsideBuildHeight(pos) && canRead.test(pos) ? level.getBlockState(pos) : null;
	}

	private static Rejection occupiedReason(BlockState state) {
		if (state.hasBlockEntity()) {
			return Rejection.BLOCK_ENTITY;
		}
		if (!state.getFluidState().isEmpty()) {
			return Rejection.FLUID;
		}
		return isAirBeforeDecoration(state) ? Rejection.NONE : Rejection.OCCUPIED;
	}

	static boolean isProtected(BlockState state) {
		return state.hasBlockEntity() || !state.getFluidState().isEmpty()
				|| state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND)
				|| state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)
				|| (state.hasProperty(BlockStateProperties.SNOWY) && state.getValue(BlockStateProperties.SNOWY));
	}

	static boolean isFullNaturalSupport(BlockGetter level, BlockPos pos, BlockState state) {
		return !isProtected(state) && !isAirBeforeDecoration(state)
				&& BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft")
				&& state.isCollisionShapeFullBlock(level, pos);
	}

	private static Analysis reject(Rejection reason) {
		return new Analysis(null, reason);
	}
}
