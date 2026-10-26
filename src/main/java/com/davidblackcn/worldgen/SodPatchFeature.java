package com.davidblackcn.worldgen;

import com.davidblackcn.BuildupGroundscape;
import com.davidblackcn.block.SodPatchBlock;
import com.davidblackcn.registry.GroundscapeBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

import java.util.function.Predicate;

/** Feature admission followed by decoration after every neighbouring native feature is complete. */
public record SodPatchFeature() implements Feature {
	public static final MapCodec<SodPatchFeature> CODEC = MapCodec.unit(SodPatchFeature::new);
	public static final int MAX_BLOCK_READS_PER_CHUNK = 65_536;
	public static final int MAX_VERTICAL_SCAN_PER_COLUMN = 256;

	@Override
	public MapCodec<SodPatchFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		GroundscapeWorldgenConfig config = GroundscapeWorldgenConfig.current();
		// In particular, /place feature on a live ServerLevel must never retrofit old chunks.
		if (!config.enabled() || config.density() == 0 || !(level instanceof WorldGenRegion region)) {
			return false;
		}
		ChunkPos center = region.getCenter();
		// This overload requests EMPTY, not FULL or the in-progress FEATURES status.
		ChunkAccess chunk = region.getChunk(center.x(), center.z());
		if (!supportedDimension(region.getLevel()) || chunk.isUpgrading() || chunk.isOldNoiseGeneration()
				|| chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES) || hasStructures(chunk)) {
			return false;
		}
		// Adjacent FEATURES tasks may still place native vegetation in this chunk.
		// Preserve real biome/datapack feature admission without changing their inputs.
		chunk.setAttached(GroundscapeWorldgen.PENDING_PATCHES, Boolean.TRUE);
		return true;
	}

	/** First-FULL callback only: old chunk loads never decorate or remove stored data. */
	public static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		if (!generated || !Boolean.TRUE.equals(chunk.getAttached(GroundscapeWorldgen.PENDING_PATCHES))) {
			return;
		}
		// Consume even if configuration changed while the ProtoChunk was saved on disk.
		chunk.removeAttached(GroundscapeWorldgen.PENDING_PATCHES);
		GroundscapeWorldgenConfig config = GroundscapeWorldgenConfig.current();
		if (!config.enabled() || config.density() == 0 || !supportedDimension(level)
				|| chunk.isUpgrading() || chunk.isOldNoiseGeneration() || hasStructures(chunk)) {
			return;
		}
		// FULL depends on LIGHT, whose radius-one INITIALIZE_LIGHT dependencies
		// guarantee all eight neighbours have completed FEATURES. Capture only
		// already-present results; never request FULL from this callback itself.
		FinalizedChunkView view = FinalizedChunkView.capture(level, chunk);
		if (view == null) {
			BuildupGroundscape.LOGGER.debug("Skipped sod decoration {}: finalized neighbours unavailable", chunk.getPos());
			return;
		}
		GenerationStatistics result = decorate(level, chunk, view, config);
		BuildupGroundscape.LOGGER.debug("Sod decoration {}: {}", chunk.getPos(), result);
	}

	private static boolean supportedDimension(ServerLevel level) {
		return level.dimension().equals(Level.OVERWORLD) || level.dimension().equals(Level.NETHER);
	}

	/** Per-call measurements, never retained in a global world/chunk registry. */
	public record GenerationStatistics(int columns, int candidates, int rejected, int placed,
			int reads, boolean budgetExhausted, boolean chunkSkipped, long nanos) {
	}

	private static GenerationStatistics decorate(ServerLevel level, LevelChunk chunk,
			FinalizedChunkView view, GroundscapeWorldgenConfig config) {
		long started = System.nanoTime();
		ChunkPos center = chunk.getPos();
		boolean nether = level.dimension().equals(Level.NETHER);
		ReadBudget budget = new ReadBudget(view);
		int columns = 0;
		int candidates = 0;
		int rejected = 0;
		int placed = 0;
		for (int dx = 0; dx < 16 && !budget.exhausted(); dx++) {
			for (int dz = 0; dz < 16 && !budget.exhausted(); dz++) {
				int x = center.getMinBlockX() + dx;
				int z = center.getMinBlockZ() + dz;
				columns++;
				if (!nether) {
					// NO_LEAVES can find a forest floor; the analyzer still protects all vegetation.
					int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dx, dz) + 1;
					BlockPos target = new BlockPos(x, y, z);
					candidates++;
					if (tryPlace(level, chunk, view, target, config, budget)) {
						placed++;
					} else {
						rejected++;
					}
				} else {
					// A Nether heightmap points at the bedrock roof. Search only actual local
					// nylium faces below the dimension's logical ceiling, with a hard read cap.
					int top = Math.min(chunk.getMaxY() - 3,
							chunk.getMinY() + level.dimensionType().logicalHeight() - 1);
					int bottom = Math.max(chunk.getMinY() + 2, top - MAX_VERTICAL_SCAN_PER_COLUMN + 1);
					for (int y = top; y >= bottom && !budget.exhausted(); y--) {
						BlockPos groundPos = new BlockPos(x, y, z);
						if (!budget.test(groundPos)) {
							break;
						}
						BlockState ground = view.getBlockState(groundPos);
						if (!ground.is(Blocks.CRIMSON_NYLIUM) && !ground.is(Blocks.WARPED_NYLIUM)) {
							continue;
						}
						candidates++;
						if (tryPlace(level, chunk, view, groundPos.above(), config, budget)) {
							placed++;
							break; // At most one decoration per column, also in multi-floor caverns.
						}
						rejected++;
					}
				}
			}
		}
		return new GenerationStatistics(columns, candidates, rejected, placed, budget.reads,
				budget.exhausted(), false, System.nanoTime() - started);
	}

	/** Conservative chunk-wide protection avoids resolving references into distant chunks. */
	public static boolean hasStructures(ChunkAccess chunk) {
		return !chunk.getAllStarts().isEmpty()
				|| chunk.getAllReferences().values().stream().anyMatch(references -> !references.isEmpty());
	}

	private static boolean tryPlace(ServerLevel level, LevelChunk chunk, FinalizedChunkView view, BlockPos target,
			GroundscapeWorldgenConfig config, ReadBudget budget) {
		SurfaceAnalyzer.Analysis analysis = SurfaceAnalyzer.analyze(view, target, budget);
		if (!analysis.accepted()) {
			return false;
		}
		SlopeTerrain terrain = SlopeTerrain.sample(view, target, budget);
		if (terrain == null) return false;
		int layers = SlopePlanner.layers(level.getSeed(), target.getX(), target.getZ(),
				analysis.profile(), terrain, config);
		if (layers == 0 || !budget.test(target) || !view.getBlockState(target).isAir()) {
			return false;
		}
		ChunkPos center = chunk.getPos();
		if ((target.getX() >> 4) != center.x() || (target.getZ() >> 4) != center.z()
				|| !chunk.isInsideBuildHeight(target)) {
			return false;
		}
		BlockState patch = GroundscapeBlocks.get(analysis.profile().family()).patch().defaultBlockState()
				.setValue(SodPatchBlock.LAYERS, layers);
		// The supplied FULL chunk has not yet been published to clients. This native
		// setter updates its heightmaps, sky-light sources, light checks and save flag;
		// using level.setBlock here would recursively request this pending FULL future.
		return chunk.setBlockState(target, patch, Block.UPDATE_CLIENTS) != null;
	}

	private static final class ReadBudget implements Predicate<BlockPos> {
		private final FinalizedChunkView view;
		private int reads;

		private ReadBudget(FinalizedChunkView view) {
			this.view = view;
		}

		@Override
		public boolean test(BlockPos pos) {
			if (exhausted() || !view.canRead(pos)) {
				return false;
			}
			reads++;
			return true;
		}

		private boolean exhausted() {
			return reads >= MAX_BLOCK_READS_PER_CHUNK;
		}
	}
}
