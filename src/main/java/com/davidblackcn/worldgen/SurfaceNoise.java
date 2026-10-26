package com.davidblackcn.worldgen;

/** Stateless world-coordinate sampling; no chunk seed, mutable RNG, or visit-order dependency. */
public final class SurfaceNoise {
	private static final long DETAIL_SALT = 0x61C8864680B583EBL;
	private static final long COLUMN_SALT = 0xD1B54A32D192ED03L;

	private SurfaceNoise() {
	}

	public static double heightDetail(long seed, double x, double z) {
		return 0.75 * valueNoise(seed ^ DETAIL_SALT, x / 12.0, z / 12.0)
				+ 0.25 * valueNoise(seed ^ COLUMN_SALT, x / 5.0, z / 5.0);
	}

	/** Smooth broad clusters with smaller variations, continuous across positive and negative chunks. */
	public static double densityScore(long seed, double x, double z) {
		return 0.20 * valueNoise(seed, x / 32.0, z / 32.0)
				+ 0.55 * valueNoise(seed ^ DETAIL_SALT, x / 12.0, z / 12.0)
				+ 0.25 * valueNoise(seed ^ COLUMN_SALT, x / 5.0, z / 5.0);
	}

	private static double valueNoise(long seed, double x, double z) {
		long ix = (long) Math.floor(x);
		long iz = (long) Math.floor(z);
		double tx = smooth(x - ix);
		double tz = smooth(z - iz);
		double north = lerp(unit(seed, ix, iz), unit(seed, ix + 1, iz), tx);
		double south = lerp(unit(seed, ix, iz + 1), unit(seed, ix + 1, iz + 1), tx);
		return lerp(north, south, tz);
	}

	private static double smooth(double t) {
		return t * t * (3.0 - 2.0 * t);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	private static double unit(long seed, long x, long z) {
		long hash = seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
		hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
		hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
		hash ^= hash >>> 31;
		return (hash >>> 11) * 0x1.0p-53;
	}
}
