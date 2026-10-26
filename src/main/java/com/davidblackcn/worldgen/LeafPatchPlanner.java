package com.davidblackcn.worldgen;

import com.davidblackcn.registry.LeafFamily;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.function.Predicate;

/** Stateless small clusters; sampling is world-relative, including across negative chunk edges. */
public final class LeafPatchPlanner {
    private static final long SALT = 0x17EAF123DAE5L;
    private LeafPatchPlanner() { }

    public static int layers(long seed, int x, int z, GroundscapeWorldgenConfig.LeafSettings config) {
        if (!config.active()) return 0;
        int cellX = Math.floorDiv(x, 8), cellZ = Math.floorDiv(z, 8);
        // Each active 8x8 world cell has a rotated 2-4 block patch (mean three). Examine adjacent
        // anchors as well so patches cross cell and chunk boundaries without being clipped.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int cx = cellX + dx, cz = cellZ + dz;
            if (SurfaceNoise.unit(seed ^ SALT, cx, cz) >= Math.min(1, config.coverage() * 64 / 3)) continue;
            int ax = cx * 8 + (int) (SurfaceNoise.unit(seed ^ (SALT + 1), cx, cz) * 8);
            int az = cz * 8 + (int) (SurfaceNoise.unit(seed ^ (SALT + 2), cx, cz) * 8);
            int size = 2 + (int) (SurfaceNoise.unit(seed ^ (SALT + 5), cx, cz) * 3);
            int turns = (int) (SurfaceNoise.unit(seed ^ (SALT + 6), cx, cz) * 4);
            for (int i = 0; i < size; i++) {
                int ox = i % 2, oz = i / 2;
                for (int turn = 0; turn < turns; turn++) { int oldX = ox; ox = -oz; oz = oldX; }
                if (x == ax + ox && z == az + oz)
                    return config.maxLayers() == 2 && SurfaceNoise.unit(seed ^ (SALT + 3), x, z) < 0.1 ? 2 : 1;
            }
        }
        return 0;
    }

    public static double rank(long seed, BlockPos pos) { return SurfaceNoise.unit(seed ^ (SALT + 4), pos.getX(), pos.getZ()); }

    public static LeafFamily canopy(BlockGetter view, BlockPos groundTarget, Predicate<BlockPos> canRead,
            GroundscapeWorldgenConfig.LeafSettings config) {
        for (int dy = 1; dy <= config.searchHeight(); dy++) {
            BlockPos pos = groundTarget.above(dy);
            if (!view.isInsideBuildHeight(pos) || !canRead.test(pos)) return null;
            var state = view.getBlockState(pos);
            if (state.isAir()) continue;
            LeafFamily family = LeafFamily.from(state);
            // The closest canopy decides the species. Buildings, logs, persistent player
            // leaves and unsupported/disabled species do not allow searching through them.
            if (family == null || !config.types().get(family.id())
                    || state.getValue(BlockStateProperties.PERSISTENT)
                    || state.getValue(BlockStateProperties.DISTANCE) == 7) return null;
            return family;
        }
        return null;
    }
}
