package com.davidblackcn.worldgen;

import com.davidblackcn.BuildupGroundscape;
import com.davidblackcn.registry.SurfaceFamily;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.LinkedHashMap;

/** Immutable, validated settings snapshot. Decoration never retro-generates. */
public record GroundscapeWorldgenConfig(boolean enabled, double density, int maxLayers,
		double slopeInfluence, double flatCoverage, double layerScale, double heightVariation,
		Map<String, MaterialSettings> materials, LeafSettings leafPatches) {
    public record LeafSettings(boolean enabled, double coverage, int maxLayers, int searchHeight, int maxPerChunk, Map<String, Boolean> types) {
        public LeafSettings {
            range("leaf coverage", coverage, 0, 0.04);
            if (maxLayers < 1 || maxLayers > 2 || searchHeight < 1 || searchHeight > 32 || maxPerChunk < 0 || maxPerChunk > 32)
                throw new IllegalArgumentException("Invalid leaf layer/search/count limit");
            Map<String, Boolean> normalized = new LinkedHashMap<>();
            for (var family : com.davidblackcn.registry.LeafFamily.values()) normalized.put(family.id(), true);
            if (types == null) throw new IllegalArgumentException("leaf types must be an object");
            for (var entry : types.entrySet()) {
                if (!normalized.containsKey(entry.getKey()) || entry.getValue() == null) throw new IllegalArgumentException("Unknown leaf type");
                normalized.put(entry.getKey(), entry.getValue());
            }
            types = java.util.Collections.unmodifiableMap(normalized);
        }
        public static LeafSettings defaults() { return new LeafSettings(false, 0.03, 2, 24, 8, Map.of()); }
        public boolean active() { return enabled && coverage > 0 && maxPerChunk > 0; }
    }
    public GroundscapeWorldgenConfig(boolean enabled, double density, int maxLayers,
            double slopeInfluence, double flatCoverage, double layerScale, double heightVariation, Map<String, MaterialSettings> materials) {
        this(enabled, density, maxLayers, slopeInfluence, flatCoverage, layerScale, heightVariation, materials, LeafSettings.defaults());
    }

	private static final MaterialSettings DEFAULT_MATERIAL = new MaterialSettings(true, 1);
	public record MaterialSettings(boolean enabled, double thicknessScale) {
		public MaterialSettings { range("thicknessScale", thicknessScale, 0.5, 1.5); }
	}

	public GroundscapeWorldgenConfig(boolean enabled, double density, int maxLayers,
			double slopeInfluence, double flatCoverage, double layerScale, double heightVariation) {
		this(enabled, density, maxLayers, slopeInfluence, flatCoverage, layerScale, heightVariation, defaultMaterials());
	}

	private static Map<String, MaterialSettings> defaultMaterials() {
		Map<String, MaterialSettings> result = new LinkedHashMap<>();
		for (SurfaceFamily family : SurfaceFamily.values()) {
			if (!family.hasSodBlock()) result.put(family.id(), DEFAULT_MATERIAL);
		}
		return result;
	}

	public MaterialSettings material(SurfaceFamily family) {
		return materials.getOrDefault(family.id(), DEFAULT_MATERIAL);
	}
	/** Existing three-field configurations retain their explicit layer cap. */
	public GroundscapeWorldgenConfig(boolean enabled, double density, int maxLayers) {
		this(enabled, density, maxLayers, 0.55, 0.10, 1.0, 0.35);
	}

	public GroundscapeWorldgenConfig {
		if (leafPatches == null) throw new IllegalArgumentException("leafPatches must be an object");
        Map<String, MaterialSettings> normalized = defaultMaterials();
		if (materials == null) throw new IllegalArgumentException("materials must be an object");
		for (var entry : materials.entrySet()) {
			if (!normalized.containsKey(entry.getKey()) || entry.getValue() == null)
				throw new IllegalArgumentException("Unknown or null material: " + entry.getKey());
			normalized.put(entry.getKey(), entry.getValue());
		}
		materials = java.util.Collections.unmodifiableMap(normalized);
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
		return com.davidblackcn.config.GroundscapeConfigManager.snapshot().config();
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
            return parse(object);
		} catch (IOException | IllegalArgumentException | com.google.gson.JsonParseException exception) {
			BuildupGroundscape.LOGGER.error("Cannot read worldgen config {}; generation disabled: {}",
					path, exception.getMessage());
			return defaults();
		}
	}

    public static GroundscapeWorldgenConfig parse(String json) {
        try (JsonReader reader = new JsonReader(new java.io.StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            var document = JsonParser.parseReader(reader);
            if (!document.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Expected one JSON object");
            return parse(document.getAsJsonObject());
        } catch (IOException e) { throw new IllegalArgumentException("Invalid JSON", e); }
    }
    public String json() { return new GsonBuilder().setPrettyPrinting().create().toJson(this) + "\n"; }
    private static GroundscapeWorldgenConfig parse(JsonObject object) {
        for (String key : object.keySet()) if (!java.util.Set.of("enabled", "density", "maxLayers", "slopeInfluence", "flatCoverage", "layerScale", "heightVariation", "materials", "leafPatches").contains(key))
            throw new IllegalArgumentException("Unknown setting: " + key);

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
				number(object, "heightVariation", defaults.heightVariation()), readMaterials(object), readLeaves(object));
    }

    private static LeafSettings readLeaves(JsonObject root) {
        if (!root.has("leafPatches")) return LeafSettings.defaults();
        if (!root.get("leafPatches").isJsonObject()) throw new IllegalArgumentException("leafPatches must be an object");
        JsonObject object = root.getAsJsonObject("leafPatches");
        for (String key : object.keySet()) if (!java.util.Set.of("enabled", "coverage", "maxLayers", "searchHeight", "maxPerChunk", "types").contains(key))
            throw new IllegalArgumentException("Unknown leaf option: " + key);
        LeafSettings defaults = LeafSettings.defaults();
        boolean enabled = booleanValue(object, "enabled", defaults.enabled());
        Map<String, Boolean> types = new LinkedHashMap<>();
        if (object.has("types")) {
            if (!object.get("types").isJsonObject()) throw new IllegalArgumentException("leaf types must be an object");
            var values = object.getAsJsonObject("types");
            for (String key : values.keySet()) types.put(key, booleanValue(values, key, true));
        }
        return new LeafSettings(enabled, number(object, "coverage", defaults.coverage()),
                integer(object, "maxLayers", defaults.maxLayers()), integer(object, "searchHeight", defaults.searchHeight()),
                integer(object, "maxPerChunk", defaults.maxPerChunk()), types);
    }
    private static boolean booleanValue(JsonObject object, String name, boolean fallback) {
        if (!object.has(name)) return fallback;
        var value = object.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(name + " must be boolean");
        return value.getAsBoolean();
    }
    private static int integer(JsonObject object, String name, int fallback) {
        double value = number(object, name, fallback);
        if (!Double.isFinite(value) || value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException(name + " must be an integer");
        return (int) value;
    }

	private static Map<String, MaterialSettings> readMaterials(JsonObject object) {
		Map<String, MaterialSettings> result = defaultMaterials();
		if (!object.has("materials")) return result;
		if (!object.get("materials").isJsonObject()) throw new IllegalArgumentException("materials must be an object");
		for (var entry : object.getAsJsonObject("materials").entrySet()) {
			if (!result.containsKey(entry.getKey()) || !entry.getValue().isJsonObject())
				throw new IllegalArgumentException("Unknown material or invalid settings: " + entry.getKey());
			JsonObject settings = entry.getValue().getAsJsonObject();
			for (String key : settings.keySet()) {
				if (!key.equals("enabled") && !key.equals("thicknessScale"))
					throw new IllegalArgumentException("Unknown material setting: " + key);
			}
			boolean enabled = true;
			if (settings.has("enabled")) {
				var value = settings.get("enabled");
				if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
					throw new IllegalArgumentException("material enabled must be boolean");
				enabled = value.getAsBoolean();
			}
			result.put(entry.getKey(), new MaterialSettings(enabled, number(settings, "thicknessScale", 1)));
		}
		return result;
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
