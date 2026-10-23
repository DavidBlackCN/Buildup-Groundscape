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
 * Original, deterministic assets and data for the five sod families.
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
		blockState(family, "block", object("", reference(family, "block")));
		JsonObject layers = new JsonObject();
		for (int count = 1; count <= 8; count++) {
			layers.add("layers=" + count, reference(family, "patch_" + count));
		}
		blockState(family, "patch", layers);
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
		};
		String chineseFamily = switch (family) {
			case GRASS -> "草地";
			case MYCELIUM -> "菌丝";
			case PODZOL -> "灰化土";
			case CRIMSON_NYLIUM -> "绯红菌岩";
			case WARPED_NYLIUM -> "诡异菌岩";
		};
		String[] englishForms = {"Sod Block", "Sod Patch", "Sod Slab", "Sod Stairs"};
		String[] chineseForms = {"草皮方块", "草皮薄层", "草皮台阶", "草皮楼梯"};
		String tagKey = "tag.item." + NAMESPACE + "." + family.id() + "_sod";
		english.addProperty(tagKey, englishFamily + " Sod");
		chinese.addProperty(tagKey, chineseFamily + "草皮");
		for (int index = 0; index < FORMS.length; index++) {
			String form = FORMS[index];
			JsonObject item = object("type", "minecraft:model", "model", modelId(family, form.equals("patch") ? "patch_1" : form));
			if (family.usesGrassTint()) {
				item.add("tints", array(object("type", "minecraft:grass", "temperature", 0.5, "downfall", 1.0)));
			}
			asset("items/" + path(family, form), object("model", item));
			String key = "block." + NAMESPACE + "." + path(family, form);
			english.addProperty(key, englishFamily + " " + englishForms[index]);
			chinese.addProperty(key, chineseFamily + chineseForms[index]);
		}
	}

	private void loot(SurfaceFamily family) {
		for (String form : FORMS) {
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
				entry = object("type", "minecraft:alternatives", "children", alternatives);
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
		String full = id(family, "block");
		String patch = id(family, "patch");
		String slab = id(family, "slab");
		String stairs = id(family, "stairs");
		String source = "minecraft:" + (family == SurfaceFamily.GRASS ? "grass_block" : family.id());
		shapeless(path(family, "block"), source, 1, full, 1);
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
			for (String form : FORMS) {
				String id = id(family, form);
				all.add(id);
				familyBlocks.add(id);
				boolean nylium = family == SurfaceFamily.CRIMSON_NYLIUM || family == SurfaceFamily.WARPED_NYLIUM;
				(nylium ? pickaxes : shovels).add(id);
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
		tag("minecraft", "block", "prevent_mob_spawning_inside", patches);
	}

	private void tag(String namespace, String registry, String name, JsonArray values) {
		write("data/" + namespace + "/tags/" + registry + "/" + name,
				object("replace", false, "values", values));
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
		return family.id() + "_sod_" + form;
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
