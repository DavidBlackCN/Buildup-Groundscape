package com.davidblackcn.client.data;

import com.davidblackcn.BuildupGroundscape;
import com.davidblackcn.registry.SurfaceFamily;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Original, deterministic assets and data for sod and ground families.
 *
 * <p>Explicit JSON generation keeps the original cuboid meshes in one place, including tinted
 * faces that the vanilla stairs templates do not expose. The schemas are checked against the
 * actual 26.3 assets: notably loot uses condition/modifier and ingredients are identifier strings.
 * No textures or models from the reference mod are read or distributed.
 */
public final class GroundscapeResourceProvider implements DataProvider {
	private static final String NAMESPACE = BuildupGroundscape.MOD_ID;
	private static final String[] FORMS = {"block", "patch", "slab", "stairs"};
	private final Path outputRoot;
	private final List<CompletableFuture<?>> writes = new ArrayList<>();
	private CachedOutput output;

	public GroundscapeResourceProvider(FabricPackOutput output) {
		this.outputRoot = output.getOutputFolder();
	}

	@Override
	public CompletableFuture<?> run(CachedOutput output) {
		this.output = output;
		writes.clear();
		JsonObject english = new JsonObject();
		JsonObject chinese = new JsonObject();
		english.addProperty("tag.item." + NAMESPACE + ".sod", "Sod");
		english.addProperty("tag.item." + NAMESPACE + ".sod_patches", "Sod Patches");
		chinese.addProperty("tag.item." + NAMESPACE + ".sod", "草皮");
		chinese.addProperty("tag.item." + NAMESPACE + ".sod_patches", "草皮薄层");
		for (SurfaceFamily family : SurfaceFamily.values()) {
			models(family);
			blockStates(family);
			itemsAndLanguage(family, english, chinese);
			loot(family);
			recipes(family);
		}
		plantModels();
        leafResources(english, chinese);
        ConfigTranslations.add(english, chinese);
		asset("lang/en_us", english);
		asset("lang/zh_cn", chinese);
		tags();
		return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
	}

	@Override
	public String getName() {
		return "Groundscape original models, recipes, loot, tags and translations";
	}

	private void models(SurfaceFamily family) {
		model(family, "block", mesh(family, List.of(new Box(0, 0, 0, 16, 16, 16))));
		for (int layers = 1; layers <= 8; layers++) {
			model(family, "patch_" + layers, mesh(family, List.of(new Box(0, 0, 0, 16, layers * 2, 16))));
		}
		model(family, "slab", mesh(family, List.of(new Box(0, 0, 0, 16, 8, 16))));
		model(family, "slab_top", mesh(family, List.of(new Box(0, 8, 0, 16, 16, 16))));
		model(family, "stairs", mesh(family, stairCells("straight")));
		model(family, "stairs_inner", mesh(family, stairCells("inner")));
		model(family, "stairs_outer", mesh(family, stairCells("outer")));
	}

	/** Default meshes point east; right corners occupy the south-east quarter. */
	private static List<Box> stairCells(String shape) {
		List<Box> cells = new ArrayList<>();
		for (int x = 0; x < 2; x++) {
			for (int z = 0; z < 2; z++) {
				cells.add(new Box(x * 8, 0, z * 8, x * 8 + 8, 8, z * 8 + 8));
				boolean upper = switch (shape) {
					case "inner" -> x == 1 || z == 1;
					case "outer" -> x == 1 && z == 1;
					default -> x == 1;
				};
				if (upper) {
					cells.add(new Box(x * 8, 8, z * 8, x * 8 + 8, 16, z * 8 + 8));
				}
			}
		}
		return cells;
	}

	/** Emit only exposed faces. Global UV coordinates keep neighboring cells continuous. */
	private static JsonObject mesh(SurfaceFamily family, List<Box> cells) {
		JsonArray elements = new JsonArray();
		for (Box box : cells) {
			JsonObject faces = new JsonObject();
			for (Face face : Face.values()) {
				if (cells.stream().anyMatch(other -> other != box && box.touches(other, face))) {
					continue;
				}
				JsonObject faceJson = object("texture", "#surface", "uv", box.uv(face));
				if (family.usesGrassTint()) {
					faceJson.addProperty("tintindex", 0);
				}
				if (box.isBoundary(face)) {
					faceJson.addProperty("cullface", face.id);
				}
				faces.add(face.id, faceJson);
			}
			elements.add(object("from", array(box.x0, box.y0, box.z0),
					"to", array(box.x1, box.y1, box.z1), "faces", faces));
		}
		return object("parent", "minecraft:block/block", "textures",
				object("particle", "#surface", "surface", texture(family)), "elements", elements);
	}

	private void blockStates(SurfaceFamily family) {
		if (family.hasSodBlock()) blockState(family, "block", object("", reference(family, "block")));
        JsonArray parts = new JsonArray();
        for (int count = 1; count <= 8; count++) {
            parts.add(object("when", object("layers", Integer.toString(count)), "apply", reference(family, "patch_" + count)));
            for (var vegetation : com.davidblackcn.block.SodPatchBlock.Vegetation.values()) {
                if (vegetation == com.davidblackcn.block.SodPatchBlock.Vegetation.NONE) continue;
                parts.add(object("when", object("layers", Integer.toString(count), "vegetation", vegetation.getSerializedName()),
                        "apply", plantReferences(vegetation, count)));
            }
        }
        asset("blockstates/" + path(family, "patch"), object("multipart", parts));

		blockState(family, "slab", object("type=bottom", reference(family, "slab"),
				"type=top", reference(family, "slab_top"), "type=double", reference(family, "block")));
		JsonObject stairs = new JsonObject();
		String[] directions = {"east", "south", "west", "north"};
		for (int direction = 0; direction < directions.length; direction++) {
			for (String half : List.of("bottom", "top")) {
				for (String shape : List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right")) {
					boolean top = half.equals("top");
					String suffix = shape.startsWith("inner") ? "_inner" : shape.startsWith("outer") ? "_outer" : "";
					int y = direction * 90;
					if (shape.endsWith("left") && !top) {
						y += 270;
					} else if (shape.endsWith("right") && top) {
						y += 90;
					}
					JsonObject variant = reference(family, "stairs" + suffix);
					if (top) {
						variant.addProperty("x", 180);
					}
					if (y % 360 != 0) {
						variant.addProperty("y", y % 360);
					}
					if (top || y % 360 != 0) {
						variant.addProperty("uvlock", true);
					}
					stairs.add("facing=" + directions[direction] + ",half=" + half + ",shape=" + shape, variant);
				}
			}
		}
		blockState(family, "stairs", stairs);
	}

	private void itemsAndLanguage(SurfaceFamily family, JsonObject english, JsonObject chinese) {
		String englishFamily = switch (family) {
			case GRASS -> "Grass";
			case MYCELIUM -> "Mycelium";
			case PODZOL -> "Podzol";
			case CRIMSON_NYLIUM -> "Crimson Nylium";
			case WARPED_NYLIUM -> "Warped Nylium";
			case SAND -> "Sand";
			case DIRT -> "Dirt";
			case COARSE_DIRT -> "Coarse Dirt";
			case RED_SAND -> "Red Sand";
			case GRAVEL -> "Gravel";
			case ROOTED_DIRT -> "Rooted Dirt";
		};
		String chineseFamily = switch (family) {
			case GRASS -> "草地";
			case MYCELIUM -> "菌丝";
			case PODZOL -> "灰化土";
			case CRIMSON_NYLIUM -> "绯红菌岩";
			case WARPED_NYLIUM -> "诡异菌岩";
			case SAND -> "沙子";
			case DIRT -> "泥土";
			case COARSE_DIRT -> "砂土";
			case RED_SAND -> "红沙";
			case GRAVEL -> "沙砾";
			case ROOTED_DIRT -> "缠根泥土";
		};
		String[] englishForms = {"Sod Block", "Sod Patch", "Sod Slab", "Sod Stairs"};
		String[] chineseForms = {"草皮方块", "草皮薄层", "草皮台阶", "草皮楼梯"};
		String tagKey = "tag.item." + NAMESPACE + "." + family.id() + "_sod";
		english.addProperty(tagKey, englishFamily + (family.hasSodBlock() ? " Sod" : " Ground"));
		chinese.addProperty(tagKey, chineseFamily + (family.hasSodBlock() ? "草皮" : "地表变种"));
		for (int index = 0; index < FORMS.length; index++) {
			String form = FORMS[index];
			if (!family.forms().contains(form)) continue;
			JsonObject item = object("type", "minecraft:model", "model", modelId(family, form.equals("patch") ? "patch_1" : form));
			if (family.usesGrassTint()) {
				item.add("tints", array(object("type", "minecraft:grass", "temperature", 0.5, "downfall", 1.0)));
			}
			asset("items/" + path(family, form), object("model", item));
			String key = "block." + NAMESPACE + "." + path(family, form);
			english.addProperty(key, englishFamily + " " + (family.hasSodBlock() ? englishForms[index] : englishForms[index].replace("Sod ", "")));
			chinese.addProperty(key, chineseFamily + (family.hasSodBlock() ? chineseForms[index] : chineseForms[index].replace("草皮", "")));
		}
	}

	private void loot(SurfaceFamily family) {
		for (String form : family.forms()) {
			JsonObject entry;
			String id = id(family, form);
			if (form.equals("patch")) {
				JsonArray alternatives = new JsonArray();
				for (int count = 1; count <= 8; count++) {
					alternatives.add(object("type", "minecraft:item", "name", id,
							"condition", stateCondition(id, "layers", Integer.toString(count)),
							"modifier", array(object("type", "minecraft:set_count", "count", count),
									object("type", "minecraft:explosion_decay"))));
				}
				entry = object("type", "minecraft:alternatives", "condition", "minecraft:tool/can_silk_touch", "children", alternatives);
			} else if (form.equals("slab")) {
				entry = object("type", "minecraft:item", "name", id,
						"modifier", array(object("type", "minecraft:set_count", "count", 2,
								"condition", stateCondition(id, "type", "double")), object("type", "minecraft:explosion_decay")));
			} else {
				entry = object("type", "minecraft:item", "name", id,
						"condition", object("type", "minecraft:survives_explosion"));
			}
			data("loot_table/blocks/" + path(family, form), object("type", "minecraft:block",
					"pools", array(object("rolls", 1, "entries", array(entry))),
					"random_sequence", NAMESPACE + ":blocks/" + path(family, form)));
		}
	}

	private static JsonObject stateCondition(String block, String property, String value) {
		return object("type", "minecraft:match_block", "blocks", block, "state", object(property, value));
	}

	private void recipes(SurfaceFamily family) {
		String full = family.hasSodBlock() ? id(family, "block") : "minecraft:" + family.id();
		String patch = id(family, "patch");
		String slab = id(family, "slab");
		String stairs = id(family, "stairs");
		String source = "minecraft:" + (family == SurfaceFamily.GRASS ? "grass_block" : family.id());
		if (family.hasSodBlock()) shapeless(path(family, "block"), source, 1, full, 1);
		shapeless(path(family, "patch"), full, 1, patch, 8);
		shapeless(path(family, "block_from_patches"), patch, 8, full, 1);
		shaped(path(family, "slab"), full, array("###"), slab, 6);
		shapeless(path(family, "block_from_slabs"), slab, 2, full, 1);
		shaped(path(family, "stairs"), full, array("#  ", "## ", "###"), stairs, 8);
		shapeless(path(family, "patch_from_stairs"), stairs, 1, patch, 6);
		shapeless(path(family, "patch_from_slab"), slab, 1, patch, 4);
	}

	private void shapeless(String name, String ingredient, int ingredientCount, String result, int resultCount) {
		JsonArray ingredients = new JsonArray();
		for (int i = 0; i < ingredientCount; i++) {
			ingredients.add(ingredient);
		}
		recipe(name, object("type", "minecraft:crafting_shapeless", "category", "building",
				"ingredients", ingredients, "result", object("id", result, "count", resultCount)), ingredient);
	}

	private void shaped(String name, String ingredient, JsonArray pattern, String result, int resultCount) {
		recipe(name, object("type", "minecraft:crafting_shaped", "category", "building", "pattern", pattern,
				"key", object("#", ingredient), "result", object("id", result, "count", resultCount)), ingredient);
	}

	private void recipe(String name, JsonObject recipe, String ingredient) {
		data("recipe/" + name, recipe);
		String recipeId = NAMESPACE + ":" + name;
		data("advancement/recipes/building_blocks/" + name, object("parent", "minecraft:recipes/root",
				"criteria", object("has_ingredient", object("trigger", "minecraft:inventory_changed",
						"conditions", object("items", array(object("items", ingredient)))),
						"has_the_recipe", object("trigger", "minecraft:recipe_unlocked",
								"conditions", object("recipes", recipeId))),
				"requirements", array(array("has_ingredient", "has_the_recipe")),
				"rewards", object("recipes", array(recipeId))));
	}

	private void tags() {
		JsonArray all = new JsonArray();
		JsonArray patches = new JsonArray();
		JsonArray slabs = new JsonArray();
		JsonArray stairs = new JsonArray();
		JsonArray shovels = new JsonArray();
		JsonArray pickaxes = new JsonArray();
		for (SurfaceFamily family : SurfaceFamily.values()) {
			JsonArray familyBlocks = new JsonArray();
			for (String form : family.forms()) {
				String id = id(family, form);
				all.add(id);
				familyBlocks.add(id);
				boolean nylium = family == SurfaceFamily.CRIMSON_NYLIUM || family == SurfaceFamily.WARPED_NYLIUM;
				if (!form.equals("patch")) (nylium ? pickaxes : shovels).add(id);
			}
			tag(NAMESPACE, "block", family.id() + "_sod", familyBlocks);
			tag(NAMESPACE, "item", family.id() + "_sod", familyBlocks);
			patches.add(id(family, "patch"));
			slabs.add(id(family, "slab"));
			stairs.add(id(family, "stairs"));
		}
		for (String registry : List.of("block", "item")) {
			tag(NAMESPACE, registry, "sod", all);
			tag(NAMESPACE, registry, "sod_patches", patches);
			tag("minecraft", registry, "slabs", slabs);
			tag("minecraft", registry, "stairs", stairs);
		}
		tag("minecraft", "block", "mineable/shovel", shovels);
		tag("minecraft", "block", "mineable/pickaxe", pickaxes);
		// Low patches occupy the candidate spawn cell as well as forming a surface below it.
		for (var leaf : com.davidblackcn.registry.LeafFamily.values()) patches.add(NAMESPACE + ":" + leaf.path());
        tag("minecraft", "block", "prevent_mob_spawning_inside", patches);
        tag("minecraft", "block", "washed_away_by_fluids", patches);
        tag("minecraft", "block", "mineable/hoe", patches);
	}

    private static JsonObject nativeJson(String path) {
        try (var stream = GroundscapeResourceProvider.class.getResourceAsStream("/assets/minecraft/" + path + ".json")) {
            if (stream == null) throw new IllegalStateException("Missing native resource " + path);
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    private static JsonObject resolvedModel(String id) {
        String path = id.replace("minecraft:", "");
        JsonObject child = nativeJson("models/" + path);
        JsonObject result = child.has("parent") ? resolvedModel(child.get("parent").getAsString()) : new JsonObject();
        for (var entry : child.entrySet()) {
            if (entry.getKey().equals("parent")) continue;
            if (entry.getKey().equals("textures") && result.has("textures")) {
                for (var texture : entry.getValue().getAsJsonObject().entrySet()) result.getAsJsonObject("textures").add(texture.getKey(), texture.getValue());
            } else result.add(entry.getKey(), entry.getValue().deepCopy());
        }
        return result;
    }

    private static List<JsonObject> nativePlantParts(com.davidblackcn.block.SodPatchBlock.Vegetation vegetation) {
        var state = vegetation.plant();
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        JsonObject json = nativeJson("blockstates/" + path);
        List<JsonObject> result = new ArrayList<>();
        if (json.has("variants")) result.add(json.getAsJsonObject("variants").getAsJsonObject(""));
        else for (var element : json.getAsJsonArray("multipart")) {
            JsonObject part = element.getAsJsonObject();
            boolean matches = true;
            if (part.has("when")) for (var condition : part.getAsJsonObject("when").entrySet()) {
                var property = state.getBlock().getStateDefinition().getProperty(condition.getKey());
                if (property == null || !List.of(condition.getValue().getAsString().split("\\|")).contains(state.getValue(property).toString().toLowerCase(java.util.Locale.ROOT))) matches = false;
            }
            if (matches) result.add(part.getAsJsonObject("apply"));
        }
        return result;
    }

    private JsonArray plantReferences(com.davidblackcn.block.SodPatchBlock.Vegetation vegetation, int layers) {
        // One exact-state model composes all native pieces and is shared by every ground material.
        return array(object("model", NAMESPACE + ":block/vegetation/" + vegetation.getSerializedName() + "_" + layers));
    }

    private void plantModels() {
        for (var vegetation : com.davidblackcn.block.SodPatchBlock.Vegetation.values()) {
            if (vegetation == com.davidblackcn.block.SodPatchBlock.Vegetation.NONE) continue;
            for (int layers = 1; layers <= 8; layers++) {
                JsonArray elements = new JsonArray();
                JsonObject textures = new JsonObject();
                int partNumber = 0;
                for (JsonObject part : nativePlantParts(vegetation)) {
                    JsonObject model = resolvedModel(part.get("model").getAsString());
                    String prefix = "p" + partNumber++ + "_";
                    for (var texture : model.getAsJsonObject("textures").entrySet()) {
                        String value = texture.getValue().getAsString();
                        textures.addProperty(prefix + texture.getKey(), value.startsWith("#") ? "#" + prefix + value.substring(1) : value);
                    }
                    int turns = part.has("y") ? part.get("y").getAsInt() / 90 : 0;
                    for (var raw : model.getAsJsonArray("elements")) {
                        JsonObject element = raw.getAsJsonObject().deepCopy();
                        JsonArray from = element.getAsJsonArray("from"), to = element.getAsJsonArray("to");
                        for (int turn = 0; turn < turns; turn++) {
                            double x0 = from.get(0).getAsDouble(), x1 = to.get(0).getAsDouble();
                            from.set(0, json(16 - to.get(2).getAsDouble())); to.set(0, json(16 - from.get(2).getAsDouble()));
                            from.set(2, json(x0)); to.set(2, json(x1));
                            JsonObject oldFaces = element.getAsJsonObject("faces"), newFaces = new JsonObject();
                            for (var face : oldFaces.entrySet()) {
                                String direction = switch (face.getKey()) { case "north" -> "east"; case "east" -> "south"; case "south" -> "west"; case "west" -> "north"; default -> face.getKey(); };
                                JsonObject value = face.getValue().getAsJsonObject();
                                if (direction.equals("up") || direction.equals("down")) value.addProperty("rotation", ((value.has("rotation") ? value.get("rotation").getAsInt() : 0) + (direction.equals("up") ? 90 : 270)) % 360);
                                newFaces.add(direction, value);
                            }
                            element.add("faces", newFaces);
                            if (element.has("rotation")) {
                                var rotation = element.getAsJsonObject("rotation");
                                var origin = rotation.getAsJsonArray("origin");
                                double ox = origin.get(0).getAsDouble();
                                origin.set(0, json(16 - origin.get(2).getAsDouble())); origin.set(2, json(ox));
                                String axis = rotation.get("axis").getAsString();
                                if (axis.equals("x")) rotation.addProperty("axis", "z");
                                else if (axis.equals("z")) { rotation.addProperty("axis", "x"); rotation.addProperty("angle", -rotation.get("angle").getAsDouble()); }
                            }
                        }
                        from.set(1, json(from.get(1).getAsDouble() + layers * 2));
                        to.set(1, json(to.get(1).getAsDouble() + layers * 2));
                        if (element.has("rotation")) {
                            var origin = element.getAsJsonObject("rotation").getAsJsonArray("origin");
                            origin.set(1, json(origin.get(1).getAsDouble() + layers * 2));
                        }
                        for (var face : element.getAsJsonObject("faces").entrySet()) {
                            JsonObject f = face.getValue().getAsJsonObject();
                            f.addProperty("texture", "#" + prefix + f.get("texture").getAsString().substring(1));
                            f.remove("cullface");
                            if (f.has("tintindex")) f.addProperty("tintindex", vegetation.plant().is(net.minecraft.world.level.block.Blocks.LEAF_LITTER) ? 2 : 1);
                        }
                        elements.add(element);
                    }
                }
                textures.addProperty("particle", "#p0_particle");
                asset("models/block/vegetation/" + vegetation.getSerializedName() + "_" + layers,
                        object("ambientocclusion", false, "textures", textures, "elements", elements));
            }
        }
    }

    private void leafResources(JsonObject english, JsonObject chinese) {
        english.addProperty("tag.item." + NAMESPACE + ".leaf_patches", "Leaf Patches");
        chinese.addProperty("tag.item." + NAMESPACE + ".leaf_patches", "树叶薄层");
        String[] names = {"橡树", "云杉", "白桦", "丛林", "金合欢", "深色橡树", "红树", "樱花", "苍白橡树", "杜鹃", "盛开的杜鹃", "红色杨树", "橙色杨树", "黄色杨树"};
        JsonArray leafTag = new JsonArray();
        for (var family : com.davidblackcn.registry.LeafFamily.values()) {
            String path = family.path(), id = NAMESPACE + ":" + path;
            leafTag.add(id);
            JsonObject variants = new JsonObject();
            JsonArray alternatives = new JsonArray();
            for (int n = 1; n <= 8; n++) {
                JsonObject mesh = mesh(SurfaceFamily.GRASS, List.of(new Box(0, 0, 0, 16, n * 2, 16)));
                mesh.getAsJsonObject("textures").addProperty("surface", "minecraft:block/" + family.id() + "_leaves");
                if (!family.tinted()) for (var element : mesh.getAsJsonArray("elements")) for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) face.getValue().getAsJsonObject().remove("tintindex");
                asset("models/block/" + path + "_" + n, mesh);
                variants.add("layers=" + n, object("model", NAMESPACE + ":block/" + path + "_" + n));
                alternatives.add(object("type", "minecraft:item", "name", id, "condition", stateCondition(id, "layers", Integer.toString(n)),
                        "modifier", array(object("type", "minecraft:set_count", "count", n), object("type", "minecraft:explosion_decay"))));
            }
            asset("blockstates/" + path, object("variants", variants));
            JsonObject item = nativeJson("items/" + family.id() + "_leaves");
            item.getAsJsonObject("model").addProperty("model", NAMESPACE + ":block/" + path + "_1");
            asset("items/" + path, item);
            String englishName = java.util.Arrays.stream(family.id().split("_")).map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1)).collect(java.util.stream.Collectors.joining(" "));
            english.addProperty("block." + NAMESPACE + "." + path, englishName + " Leaves Patch");
            chinese.addProperty("block." + NAMESPACE + "." + path, names[family.ordinal()] + "树叶薄层");
            data("loot_table/blocks/" + path, object("type", "minecraft:block", "pools", array(object("rolls", 1, "entries", array(object("type", "minecraft:alternatives", "condition", "minecraft:tool/can_silk_touch", "children", alternatives))))));
            shapeless(path, "minecraft:" + family.id() + "_leaves", 1, id, 8);
            shapeless(path + "_to_leaves", id, 8, "minecraft:" + family.id() + "_leaves", 1);
        }
        tag(NAMESPACE, "block", "leaf_patches", leafTag);
        tag(NAMESPACE, "item", "leaf_patches", leafTag);

    }

	private void tag(String namespace, String registry, String name, JsonArray values) {
		write("data/" + namespace + "/tags/" + registry + "/" + name,
				object("replace", false, "values", values.deepCopy()));
	}

	private void blockState(SurfaceFamily family, String form, JsonObject variants) {
		asset("blockstates/" + path(family, form), object("variants", variants));
	}

	private void model(SurfaceFamily family, String form, JsonObject model) {
		asset("models/block/" + path(family, form), model);
	}

	private static JsonObject reference(SurfaceFamily family, String form) {
		return object("model", modelId(family, form));
	}

	private static String modelId(SurfaceFamily family, String form) {
		return NAMESPACE + ":block/" + path(family, form);
	}

	private static String path(SurfaceFamily family, String form) {
		return family.path(form);
	}

	private static String id(SurfaceFamily family, String form) {
		return NAMESPACE + ":" + path(family, form);
	}

	private static String texture(SurfaceFamily family) {
		return "minecraft:block/" + switch (family) {
			case GRASS -> "grass_block_top";
			case MYCELIUM -> "mycelium_top";
			case PODZOL -> "podzol_top";
			case CRIMSON_NYLIUM -> "crimson_nylium";
			case WARPED_NYLIUM -> "warped_nylium";
			default -> family.id();
		};
	}

	private void asset(String path, JsonObject json) {
		write("assets/" + NAMESPACE + "/" + path, json);
	}

	private void data(String path, JsonObject json) {
		write("data/" + NAMESPACE + "/" + path, json);
	}

	private void write(String path, JsonObject json) {
		writes.add(DataProvider.saveStable(output, json, outputRoot.resolve(path + ".json")));
	}

	private static JsonObject object(Object... pairs) {
		if (pairs.length % 2 != 0) {
			throw new IllegalArgumentException("JSON object needs key/value pairs");
		}
		JsonObject result = new JsonObject();
		for (int i = 0; i < pairs.length; i += 2) {
			result.add((String) pairs[i], json(pairs[i + 1]));
		}
		return result;
	}

	private static JsonArray array(Object... values) {
		JsonArray result = new JsonArray();
		for (Object value : values) {
			result.add(json(value));
		}
		return result;
	}

	private static JsonElement json(Object value) {
		return switch (value) {
			case JsonElement element -> element;
			case String string -> new JsonPrimitive(string);
			case Number number -> new JsonPrimitive(number);
			case Boolean bool -> new JsonPrimitive(bool);
			default -> throw new IllegalArgumentException("Unsupported JSON value: " + value);
		};
	}

	private enum Face {
		DOWN("down"), UP("up"), NORTH("north"), SOUTH("south"), WEST("west"), EAST("east");
		final String id;

		Face(String id) {
			this.id = id;
		}
	}

	private record Box(int x0, int y0, int z0, int x1, int y1, int z1) {
		boolean touches(Box other, Face face) {
			return switch (face) {
				case DOWN -> y0 == other.y1 && x0 == other.x0 && x1 == other.x1 && z0 == other.z0 && z1 == other.z1;
				case UP -> y1 == other.y0 && x0 == other.x0 && x1 == other.x1 && z0 == other.z0 && z1 == other.z1;
				case NORTH -> z0 == other.z1 && x0 == other.x0 && x1 == other.x1 && y0 == other.y0 && y1 == other.y1;
				case SOUTH -> z1 == other.z0 && x0 == other.x0 && x1 == other.x1 && y0 == other.y0 && y1 == other.y1;
				case WEST -> x0 == other.x1 && y0 == other.y0 && y1 == other.y1 && z0 == other.z0 && z1 == other.z1;
				case EAST -> x1 == other.x0 && y0 == other.y0 && y1 == other.y1 && z0 == other.z0 && z1 == other.z1;
			};
		}

		boolean isBoundary(Face face) {
			return switch (face) {
				case DOWN -> y0 == 0;
				case UP -> y1 == 16;
				case NORTH -> z0 == 0;
				case SOUTH -> z1 == 16;
				case WEST -> x0 == 0;
				case EAST -> x1 == 16;
			};
		}

		JsonArray uv(Face face) {
			return switch (face) {
				case DOWN -> array(x0, 16 - z1, x1, 16 - z0);
				case UP -> array(x0, z0, x1, z1);
				case NORTH -> array(16 - x1, 16 - y1, 16 - x0, 16 - y0);
				case SOUTH -> array(x0, 16 - y1, x1, 16 - y0);
				case WEST -> array(z0, 16 - y1, z1, 16 - y0);
				case EAST -> array(16 - z1, 16 - y1, 16 - z0, 16 - y0);
			};
		}
	}
}
