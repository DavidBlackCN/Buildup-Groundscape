package com.davidblackcn.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/** Original terrain over a 5x5 footprint, including immediate terrace edges. */
public record SlopeTerrain(double gradientX, double gradientZ, double meanOffset,
		int relief, int higherNeighbors, int lowerNeighbors, int stepX, int stepZ, boolean downhillEdge) {
	public static final SlopeTerrain FLAT = new SlopeTerrain(0, 0, 0, 0, 0, 0, 0, 0, false);
	private static final int RADIUS = 2;
	private static final int[][] CARDINALS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

	public boolean hasUphillStep() {
		return stepX != 0 || stepZ != 0;
	}

	public double strength() {
		return Math.min(1, Math.hypot(gradientX, gradientZ) * 2.0 + relief * 0.12);
	}

	/** Null means insufficient information or dangerous terrain: callers must skip. */
	public static SlopeTerrain sample(BlockGetter blocks, BlockPos target, Predicate<BlockPos> canRead) {
		int[] heights = new int[9];
		int groundY = target.getY() - 1;
		int min = 0, max = 0, higher = 0, lower = 0, sum = 0;
		int stepX = 0, stepZ = 0, stepDistance = Integer.MAX_VALUE;
		for (int ix = 0; ix < 3; ix++) {
			for (int iz = 0; iz < 3; iz++) {
				if (ix == 1 && iz == 1) continue;
				int x = target.getX() + (ix - 1) * RADIUS;
				int z = target.getZ() + (iz - 1) * RADIUS;
				int offset = heightOffset(blocks, x, groundY, z, canRead);
				if (offset == Integer.MIN_VALUE) return null; // Cliff, cave mouth or missing support.
				heights[ix * 3 + iz] = offset;
				min = Math.min(min, offset);
				max = Math.max(max, offset);
				sum += offset;
				if (offset > 0) higher++;
				if (offset < 0) lower++;
				int dx = x - target.getX(), dz = z - target.getZ();
				int distance = dx * dx + dz * dz;
				if (offset > 0 && distance < stepDistance) {
					stepX = dx; stepZ = dz; stepDistance = distance;
				}
			}
		}
		if (max - min > 3) return null;
		boolean downhillEdge = false;
		for (int[] direction : CARDINALS) {
			int offset = heightOffset(blocks, target.getX() + direction[0], groundY,
					target.getZ() + direction[1], canRead);
			if (offset == Integer.MIN_VALUE) return null;
			min = Math.min(min, offset); max = Math.max(max, offset);
			downhillEdge |= offset < 0;
			if (offset > 0 && stepDistance > 1) {
				stepX = direction[0]; stepZ = direction[1]; stepDistance = 1;
			}
		}
		if (max - min > 3) return null;
		// Sobel estimate at radius two: the vector points uphill, with diagonal support.
		double gx = (heights[6] + 2 * heights[7] + heights[8]
				- heights[0] - 2 * heights[1] - heights[2]) / 16.0;
		double gz = (heights[2] + 2 * heights[5] + heights[8]
				- heights[0] - 2 * heights[3] - heights[6]) / 16.0;
		return new SlopeTerrain(gx, gz, sum / 9.0, max - min, higher, lower, stepX, stepZ, downhillEdge);
	}

	private static int heightOffset(BlockGetter blocks, int x, int groundY, int z, Predicate<BlockPos> canRead) {
		for (int dy = 3; dy >= -2; dy--) {
			BlockPos pos = new BlockPos(x, groundY + dy, z);
			BlockState state = read(blocks, pos, canRead);
			if (state == null || SurfaceAnalyzer.isProtected(state)) return Integer.MIN_VALUE;
			if (!isTerrain(state)) continue;
			if (dy == 3 || !SurfaceAnalyzer.isFullNaturalSupport(blocks, pos, state)) return Integer.MIN_VALUE;
			for (int depth = 1; depth <= 2; depth++) {
				BlockPos supportPos = pos.below(depth);
				BlockState support = read(blocks, supportPos, canRead);
				if (support == null || !isTerrain(support)
						|| !SurfaceAnalyzer.isFullNaturalSupport(blocks, supportPos, support)) return Integer.MIN_VALUE;
			}
			return dy;
		}
		return Integer.MIN_VALUE;
	}

	private static BlockState read(BlockGetter blocks, BlockPos pos, Predicate<BlockPos> canRead) {
		return blocks.isInsideBuildHeight(pos) && canRead.test(pos) ? blocks.getBlockState(pos) : null;
	}

	private static boolean isTerrain(BlockState state) {
		// Never let a datapack tag turn our earlier decoration into terrain input.
		return !SurfaceAnalyzer.isAirBeforeDecoration(state) && !SurfaceAnalyzer.isProtected(state) && (state.is(BlockTags.DIRT)
				|| state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
				|| state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
				|| state.is(BlockTags.BADLANDS_TERRACOTTA)
				|| state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
				|| state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
				|| state.is(Blocks.CRIMSON_NYLIUM) || state.is(Blocks.WARPED_NYLIUM));
	}
}
