package com.davidblackcn.worldgen;

/** Pure S1 coverage and thickness field. Nothing depends on previously placed patches. */
public final class SlopePlanner {
	private SlopePlanner() {
	}

	public static int layers(long seed, int x, int z, SurfaceProfile profile, SlopeTerrain terrain,
			GroundscapeWorldgenConfig config) {
		if (config.density() == 0 || !config.material(profile.family()).enabled()) return 0;
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
		if (terrain.hasUphillStep()) {
			// Every safe ramp participates. Noise varies thickness instead of deleting hillsides.
			double detail = SurfaceNoise.heightDetail(seed, anchorX, anchorZ);
			double relief = Math.clamp((3 - distanceToStep) / 2, 0, 1);
			double variation = 0.7 + (detail - 0.5) * (0.3 + config.heightVariation() * 0.8);
			double densityScale = Math.clamp(config.density() / 0.12, 0.2, 1.3);
			double strength = relief * variation * config.layerScale() * config.material(profile.family()).thicknessScale()
					* (0.65 + config.slopeInfluence() * 0.35 / 0.55) * densityScale;
			// Scale the whole profile into the requested range; never clamp 4..7 into a 3-layer plateau.
			double normalized = Math.pow(Math.clamp(strength, 0, 1), 1.5);
			return Math.min(config.maxLayers(), 1 + (int) Math.floor(normalized * config.maxLayers()));
		}
		// Sparse flat decoration retains the original coverage controls.
		double threshold = 0.84 - config.density() * 1.2
				+ (1 - config.flatCoverage()) * 0.08 + (1 - profile.coverageScale()) * 0.10;
		double interior = Math.clamp((SurfaceNoise.densityScore(seed, x, z) - threshold) / 0.16, 0, 1);
		if (interior <= 0) return 0;
		double detail = SurfaceNoise.heightDetail(seed, x, z);
		return Math.min(config.maxLayers(), 1 + (int) Math.floor(detail * interior * config.material(profile.family()).thicknessScale() + 0.5));
	}
}
