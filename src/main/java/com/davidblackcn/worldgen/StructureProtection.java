package com.davidblackcn.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Capture immutable piece bounds while the generation region already owns structure starts. */
public final class StructureProtection {
	private StructureProtection() { }

	/** Null means unresolved/over-budget metadata: fail closed, without requesting chunks. */
	public static List<BoundingBox> capture(WorldGenRegion region, ChunkAccess center) {
		Set<StructureStart> starts = new HashSet<>(center.getAllStarts().values());
		int references = 0;
		for (var entry : center.getAllReferences().entrySet()) {
			for (long key : entry.getValue()) {
				if (++references > 256) return null;
				ChunkPos pos = ChunkPos.unpack(key);
				if (!region.hasChunk(pos.x(), pos.z())) return null;
				ChunkAccess source = region.getChunk(pos.x(), pos.z(), ChunkStatus.STRUCTURE_STARTS, false);
				if (source == null) return null;
				StructureStart start = source.getAllStarts().get(entry.getKey());
				if (start == null) return null;
				starts.add(start);
			}
		}
		List<BoundingBox> result = new ArrayList<>();
		ChunkPos pos = center.getPos();
		int pieces = 0;
		for (StructureStart start : starts) {
			if (!start.isValid()) continue;
			for (var piece : start.getPieces()) {
				if (++pieces > 4096) return null;
				BoundingBox bounds = piece.getBoundingBox().inflatedBy(2, 3, 2);
				if (bounds.intersects(pos.getMinBlockX(), pos.getMinBlockZ(), pos.getMaxBlockX(), pos.getMaxBlockZ())) {
					result.add(bounds);
				}
			}
		}
		return List.copyOf(result);
	}

	/** Bounds already include the horizontal buffer and the surface/support vertical buffer. */
	public static boolean protects(List<BoundingBox> bounds, BlockPos target) {
		return bounds.stream().anyMatch(box -> box.isInside(target));
	}
}
