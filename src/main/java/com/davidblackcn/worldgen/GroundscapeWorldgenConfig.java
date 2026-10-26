package com.davidblackcn.worldgen;

import com.davidblackcn.BuildupGroundscape;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Immutable startup settings. Experimental decoration is opt-in and never retro-generates. */
public record GroundscapeWorldgenConfig(boolean enabled, double density, int maxLayers,
		double slopeInfluence, double flatCoverage, double layerScale, double heightVariation) {
	/** Existing three-field configurations retain their explicit layer cap. */
	public GroundscapeWorldgenConfig(boolean enabled, double density, int maxLayers) {
		this(enabled, density, maxLayers, 0.55, 0.10, 1.0, 0.35);
	}

	public GroundscapeWorldgenConfig {
		if (!Double.isFinite(density) || density < 0 || density > 0.35) {
			throw new IllegalArgumentException("density must be between 0 and 0.35");
		}
		if (maxLayers < 1 || maxLayers > 7) {
			throw new IllegalArgumentException("maxLayers must be between 1 and 7");
		}
		range("slopeInfluence", slopeInfluence, 0, 1);
		range("flatCoverage", flatCoverage, 0, 1);
		range("layerScale", layerScale, 0.5, 1.5);
		range("heightVariation", heightVariation, 0, 1);
	}

	private static void range(String name, double value, double low, double high) {
		if (!Double.isFinite(value) || value < low || value > high) {
			throw new IllegalArgumentException(name + " must be between " + low + " and " + high);
		}
	}

	public static GroundscapeWorldgenConfig defaults() {
		return new GroundscapeWorldgenConfig(false, 0.12, 7);
	}

	public static GroundscapeWorldgenConfig current() {
		return Startup.SETTINGS;
	}

	private static final class Startup {
		private static final GroundscapeWorldgenConfig SETTINGS = load(FabricLoader.getInstance()
				.getConfigDir().resolve("buildup_groundscape-worldgen.json"));
	}

	/** Invalid configuration is preserved for repair; failure always disables generation. */
	public static GroundscapeWorldgenConfig load(Path path) {
		try {
			if (!Files.exists(path)) {
				Path parent = path.toAbsolutePath().getParent();
				Files.createDirectories(parent);
				Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(defaults()) + "\n",
						StandardOpenOption.CREATE_NEW);
				return defaults();
			}
			JsonObject object;
			try (JsonReader reader = new JsonReader(Files.newBufferedReader(path))) {
				reader.setStrictness(Strictness.STRICT);
				var document = JsonParser.parseReader(reader);
				if (reader.peek() != JsonToken.END_DOCUMENT || !document.isJsonObject()) {
					throw new IllegalArgumentException("Expected exactly one JSON object");
				}
				object = document.getAsJsonObject();
			}
			GroundscapeWorldgenConfig defaults = defaults();
			boolean enabled = defaults.enabled();
			if (object.has("enabled")) {
				var value = object.get("enabled");
				if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
					throw new IllegalArgumentException("enabled must be a boolean");
				}
				enabled = value.getAsBoolean();
			}
			double density = number(object, "density", defaults.density());
			double layers = number(object, "maxLayers", defaults.maxLayers());
			if (layers != Math.rint(layers) || layers < 1 || layers > 7) {
				throw new IllegalArgumentException("maxLayers must be an integer between 1 and 7");
			}
			return new GroundscapeWorldgenConfig(enabled, density, (int) layers,
					number(object, "slopeInfluence", defaults.slopeInfluence()),
					number(object, "flatCoverage", defaults.flatCoverage()),
					number(object, "layerScale", defaults.layerScale()),
					number(object, "heightVariation", defaults.heightVariation()));
		} catch (IOException | IllegalArgumentException | com.google.gson.JsonParseException exception) {
			BuildupGroundscape.LOGGER.error("Cannot read worldgen config {}; generation disabled: {}",
					path, exception.getMessage());
			return defaults();
		}
	}

	private static double number(JsonObject object, String name, double fallback) {
		if (!object.has(name)) return fallback;
		var value = object.get(name);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException(name + " must be a number");
		}
		return value.getAsDouble();
	}
}
