package com.davidblackcn.worldgen;

/** Pure S1 coverage and thickness field. Nothing depends on previously placed patches. */
public final class SlopePlanner {
	private SlopePlanner() {
	}

	public static int layers(long seed, int x, int z, SurfaceProfile profile, SlopeTerrain terrain,
			GroundscapeWorldgenConfig config) {
		if (config.density() == 0) return 0;
		// Raising the exposed lip makes the original step taller, never smoother.
		if (terrain.downhillEdge()) return 0;
		double slope = terrain.strength();
		if (slope == 0 && config.flatCoverage() == 0) return 0;
		if (slope > 0 && !terrain.hasUphillStep()) return 0;
		// Use the same original uphill block as the noise anchor along a short ramp.
		// Adjacent columns then cannot reverse the ramp just because their noise differs.
		double anchorX = x + terrain.stepX(), anchorZ = z + terrain.stepZ();
		double distanceToStep = Math.hypot(terrain.stepX(), terrain.stepZ());
		double gradient = Math.hypot(terrain.gradientX(), terrain.gradientZ());
		if (terrain.hasUphillStep() && gradient > 0) {
			double nx = terrain.gradientX() / gradient, nz = terrain.gradientZ() / gradient;
			double projection = terrain.stepX() * nx + terrain.stepZ() * nz;
			if (projection > 0) {
				anchorX = x + nx * projection; anchorZ = z + nz * projection;
				distanceToStep = Math.max(1, projection);
			}
		}
		// A verified step is a complete ramp opportunity even on a broad gentle hill.
		// Giving its near/far columns different coverage thresholds can invert them.
		if (terrain.hasUphillStep()) slope = 1;
		double threshold = 0.84 - config.density() * 1.2
				- config.slopeInfluence() * slope * 0.32
				+ (1 - config.flatCoverage()) * (1 - slope) * 0.08
				+ (1 - profile.coverageScale()) * 0.10;
		double interior = (SurfaceNoise.densityScore(seed, anchorX, anchorZ) - threshold) / 0.16;
		if (interior <= 0) return 0;
		interior = smooth(Math.min(1, interior));
		// Shorter coverage patches still need gradual edges. This bounded envelope
		// limits the field's change to 1/4 per block without reading any terrain.
		// It also keeps isolated selected cells low instead of making tall spikes.
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int distance = Math.abs(dx) + Math.abs(dz);
				if (distance == 0 || distance > 3 || interior <= distance * 0.25) continue;
				double neighbor = Math.clamp((SurfaceNoise.densityScore(seed, anchorX + dx, anchorZ + dz) - threshold) / 0.16, 0, 1);
				interior = Math.min(interior, smooth(neighbor) + distance * 0.25);
			}
		}
		double detail = SurfaceNoise.heightDetail(seed, anchorX, anchorZ);
		if (!terrain.hasUphillStep()) {
			// Sparse flat decoration remains possible, but cannot build a high crest.
			return Math.min(config.maxLayers(), 1 + (int) Math.floor(detail * interior + 0.5));
		}
		double variation = 1 - config.heightVariation() * (1 - detail) * 0.3;
		double thickness = 6.0 / distanceToStep * config.layerScale() * variation;
		// Quantize around half a layer so short ramps do not collapse mostly to one
		// layer. Coverage and the downhill-edge exclusion remain unchanged.
		return Math.min(config.maxLayers(), Math.max(1, (int) Math.ceil(0.5 + (thickness - 0.5) * interior)));
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}
}
