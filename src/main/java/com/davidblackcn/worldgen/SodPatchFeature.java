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
import java.util.List;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

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
		if (!config.enabled() || (config.density() == 0 && !config.leafPatches().active()) || !(level instanceof WorldGenRegion region)) {
			return false;
		}
		ChunkPos center = region.getCenter();
		// This overload requests EMPTY, not FULL or the in-progress FEATURES status.
		ChunkAccess chunk = region.getChunk(center.x(), center.z());
		if (!supportedDimension(region.getLevel()) || (config.density() == 0 && region.getLevel().dimension().equals(Level.NETHER)) || chunk.isUpgrading() || chunk.isOldNoiseGeneration()
				|| chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
			return false;
		}
		List<BoundingBox> bounds = StructureProtection.capture(region, chunk);
		if (bounds == null) return false;
		chunk.setAttached(GroundscapeWorldgen.STRUCTURE_BOUNDS, bounds);
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
		List<BoundingBox> bounds = chunk.getAttached(GroundscapeWorldgen.STRUCTURE_BOUNDS);
		chunk.removeAttached(GroundscapeWorldgen.STRUCTURE_BOUNDS);
		GroundscapeWorldgenConfig config = GroundscapeWorldgenConfig.current();
		if (!config.enabled() || (config.density() == 0 && !config.leafPatches().active()) || !supportedDimension(level)
				|| chunk.isUpgrading() || chunk.isOldNoiseGeneration()) {
			return;
		}
		// Old pending ProtoChunks have no piece snapshot; retain their conservative protection.
		if (bounds == null && hasStructures(chunk)) return;
		// FULL depends on LIGHT, whose radius-one INITIALIZE_LIGHT dependencies
		// guarantee all eight neighbours have completed FEATURES. Capture only
		// already-present results; never request FULL from this callback itself.
		FinalizedChunkView view = FinalizedChunkView.capture(level, chunk);
		if (view == null) {
			BuildupGroundscape.LOGGER.debug("Skipped sod decoration {}: finalized neighbours unavailable", chunk.getPos());
			return;
		}
		GenerationStatistics result = decorate(level, chunk, view, config, bounds == null ? List.of() : bounds);
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
			FinalizedChunkView view, GroundscapeWorldgenConfig config, List<BoundingBox> bounds) {
		long started = System.nanoTime();
		ChunkPos center = chunk.getPos();
		boolean nether = level.dimension().equals(Level.NETHER);
		ReadBudget budget = new ReadBudget(view);
		int columns = 0;
		int candidates = 0;
		int rejected = 0;
		int placed = 0;
        java.util.List<LeafCandidate> leaves = new java.util.ArrayList<>();
        ReadBudget leafBudget = new ReadBudget(view);
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
					if (config.density() > 0 && !StructureProtection.protects(bounds, target) && tryPlace(level, chunk, view, target, config, budget)) {
						placed++;
					} else {
						rejected++;
					}
                    if (config.leafPatches().active() && view.getBlockState(target).isAir()
                            && !StructureProtection.protects(bounds, target)) {
                        int layers = LeafPatchPlanner.layers(level.getSeed(), x, z, config.leafPatches());
                        if (layers > 0 && SurfaceAnalyzer.analyze(view, target, leafBudget).accepted()) {
                            var family = LeafPatchPlanner.canopy(view, target, leafBudget, config.leafPatches());
                            if (family != null) leaves.add(new LeafCandidate(target, family, layers));
                        }
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
						if (!StructureProtection.protects(bounds, groundPos.above()) && tryPlace(level, chunk, view, groundPos.above(), config, budget)) {
							placed++;
							break; // At most one decoration per column, also in multi-floor caverns.
						}
						rejected++;
					}
				}
			}
		}
		leaves.sort(java.util.Comparator.comparingDouble(candidate -> LeafPatchPlanner.rank(level.getSeed(), candidate.pos())));
        int leafCount = 0;
        for (var candidate : leaves) {
            if (leafCount >= config.leafPatches().maxPerChunk()) break;
            if (!chunk.getBlockState(candidate.pos()).isAir()) continue;
            var state = com.davidblackcn.registry.GroundscapeLeaves.get(candidate.family()).defaultBlockState()
                    .setValue(com.davidblackcn.block.LeafPatchBlock.LAYERS, candidate.layers());
            if (chunk.setBlockState(candidate.pos(), state, Block.UPDATE_CLIENTS) != null) leafCount++;
        }
        return new GenerationStatistics(columns, candidates, rejected, placed + leafCount, budget.reads + leafBudget.reads,
				budget.exhausted() || leafBudget.exhausted(), false, System.nanoTime() - started);
	}

    private record LeafCandidate(BlockPos pos, com.davidblackcn.registry.LeafFamily family, int layers) { }

	/** Metadata diagnostic and fallback for pre-1.3 pending chunks, not a current generation veto. */
	public static boolean hasStructures(ChunkAccess chunk) {
		return !chunk.getAllStarts().isEmpty()
				|| chunk.getAllReferences().values().stream().anyMatch(references -> !references.isEmpty());
	}

	private static boolean tryPlace(ServerLevel level, LevelChunk chunk, FinalizedChunkView view, BlockPos target,
			GroundscapeWorldgenConfig config, ReadBudget budget) {
		SurfaceAnalyzer.Analysis analysis = SurfaceAnalyzer.analyze(view, target, budget);
		if (!analysis.accepted() || !config.material(analysis.profile().family()).enabled()) {
			return false;
		}
		SlopeTerrain terrain = SlopeTerrain.sample(view, target, budget);
		if (terrain == null) return false;
		int layers = SlopePlanner.layers(level.getSeed(), target.getX(), target.getZ(),
				analysis.profile(), terrain, config);
		if (layers == 0 || !budget.test(target)) {
			return false;
		}
		BlockState original = view.getBlockState(target);
		if (!original.isAir() && !SodPatchBlock.canContain(original)) return false;
		ChunkPos center = chunk.getPos();
		if ((target.getX() >> 4) != center.x() || (target.getZ() >> 4) != center.z()
				|| !chunk.isInsideBuildHeight(target)) {
			return false;
		}
		BlockState patch = GroundscapeBlocks.get(analysis.profile().family()).patch().defaultBlockState()
				.setValue(SodPatchBlock.LAYERS, layers)
				.setValue(SodPatchBlock.VEGETATION, SodPatchBlock.Vegetation.from(original));
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
