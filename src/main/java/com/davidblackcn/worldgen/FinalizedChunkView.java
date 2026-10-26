package com.davidblackcn.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;

/** A fixed 3x3, non-loading view used before a newly generated FULL chunk is published. */
final class FinalizedChunkView implements BlockGetter, SurfaceAnalyzer.View {
	private final LevelChunk center;
	private final ChunkAccess[][] chunks;
	private final BiomeManager biomeManager;
	private final int seaLevel;

	private FinalizedChunkView(ServerLevel level, LevelChunk center, ChunkAccess[][] chunks) {
		this.center = center;
		this.chunks = chunks;
		this.seaLevel = level.getSeaLevel();
		this.biomeManager = level.getBiomeManager().withDifferentSource((x, y, z) -> {
			// Quart coordinates have four cells per chunk. BiomeManager's fuzzed
			// lookup for a center-column position stays inside the captured 3x3.
			ChunkAccess chunk = chunk(x >> 2, z >> 2);
			if (chunk == null) {
				throw new IllegalArgumentException("Biome read outside finalized chunk view");
			}
			return chunk.getNoiseBiome(x, y, z);
		});
	}

	static FinalizedChunkView capture(ServerLevel level, LevelChunk center) {
		ChunkAccess[][] chunks = new ChunkAccess[3][3];
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					chunks[1][1] = center;
					continue;
				}
				// This verified API only reads a present holder's FEATURES result.
				// It never creates a ticket, schedules generation, or waits on FULL.
				var present = level.getChunkSource().getChunkForLighting(
						center.getPos().x() + dx, center.getPos().z() + dz);
				if (!(present instanceof ChunkAccess chunk)
						|| !chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
					return null;
				}
				chunks[dx + 1][dz + 1] = chunk;
			}
		}
		return new FinalizedChunkView(level, center, chunks);
	}

	boolean canRead(BlockPos pos) {
		return isInsideBuildHeight(pos) && chunk(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	private ChunkAccess chunk(int chunkX, int chunkZ) {
		int dx = chunkX - center.getPos().x() + 1;
		int dz = chunkZ - center.getPos().z() + 1;
		return dx >= 0 && dx < 3 && dz >= 0 && dz < 3 ? chunks[dx][dz] : null;
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		ChunkAccess chunk = chunk(pos.getX() >> 4, pos.getZ() >> 4);
		return isInsideBuildHeight(pos) && chunk != null ? chunk.getBlockState(pos) : Blocks.BARRIER.defaultBlockState();
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Override
	public BlockEntity getBlockEntity(BlockPos pos) {
		// Entity-bearing states are rejected before shape analysis. Never instantiate
		// a block entity in a neighbouring ProtoChunk as a side effect of inspection.
		return null;
	}

	@Override
	public int getHeight() {
		return center.getHeight();
	}

	@Override
	public int getMinY() {
		return center.getMinY();
	}

	@Override
	public BlockGetter blocks() {
		return this;
	}

	@Override
	public Holder<Biome> biome(BlockPos pos) {
		return biomeManager.getBiome(pos);
	}

	@Override
	public int seaLevel() {
		return seaLevel;
	}
}
