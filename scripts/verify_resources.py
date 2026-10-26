r"""Audit generated assets independently of the Java data generator (Python standard library).

Run after .\gradlew.bat runDatagen. This checks actual emitted geometry in block coordinates,
including all rotations, exposed faces, complete item/state coverage and conservation of sod.
It does not replace visual testing of biome blending, resource packs or shaders.
"""

import argparse
import itertools
import json
import zipfile
from pathlib import Path


NAMESPACE = "buildup_groundscape"
FAMILIES = ("grass", "mycelium", "podzol", "crimson_nylium", "warped_nylium")
FORMS = ("block", "patch", "slab", "stairs")
DIRECTIONS = {
    "down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1),
    "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0),
}


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def identifier(family, form):
    return f"{NAMESPACE}:{family}_sod_{form}"


def cells(model):
    occupied = set()
    for element in model["elements"]:
        start, end = element["from"], element["to"]
        check(all(type(v) is int and 0 <= v <= 16 for v in start + end), "Cuboid outside block")
        check(all(a < b for a, b in zip(start, end)), "Non-positive cuboid")
        part = set(itertools.product(*(range(a, b) for a, b in zip(start, end))))
        check(not occupied.intersection(part), "Overlapping cuboids")
        occupied.update(part)
    return occupied


def exposed_faces(model, occupied):
    expected = set()
    for x, y, z in occupied:
        for name, (dx, dy, dz) in DIRECTIONS.items():
            if (x + dx, y + dy, z + dz) not in occupied:
                expected.add((x, y, z, name))
    emitted = set()
    for element in model["elements"]:
        start, end = element["from"], element["to"]
        for name, face in element["faces"].items():
            offset = DIRECTIONS[name]
            axis = next(i for i, value in enumerate(offset) if value)
            limits = [range(a, b) for a, b in zip(start, end)]
            boundary = start[axis] if offset[axis] < 0 else end[axis] - 1
            limits[axis] = [boundary]
            squares = {(x, y, z, name) for x, y, z in itertools.product(*limits)}
            check(not emitted.intersection(squares), "Duplicate model faces")
            emitted.update(squares)
            if "cullface" in face:
                check(face["cullface"] == name, "Incorrect cull direction")
                check(boundary == (0 if offset[axis] < 0 else 15), "Interior face has cullface")
            check(len(face["uv"]) == 4 and all(0 <= v <= 16 for v in face["uv"]), "UV outside source texture")
    check(emitted == expected, f"Mesh faces mismatch: missing={len(expected - emitted)}, hidden={len(emitted - expected)}")


def rotated(occupied, variant):
    result = set()
    for x, y, z in occupied:
        for _ in range(variant.get("x", 0) // 90):
            y, z = 15 - z, y
        for _ in range(variant.get("y", 0) // 90):
            x, z = 15 - z, x
        result.add((x, y, z))
    return result


def expected_stairs(facing, half, shape):
    vector = {"east": (1, 0), "south": (0, 1), "west": (-1, 0), "north": (0, -1)}[facing]
    dx, dz = vector
    right = (-dz, dx)
    side = right if shape.endswith("right") else (-right[0], -right[1])
    result = set()
    for x, y, z in itertools.product(range(16), repeat=3):
        base = y < 8 if half == "bottom" else y >= 8
        forward = (x - 7.5) * dx + (z - 7.5) * dz > 0
        lateral = (x - 7.5) * side[0] + (z - 7.5) * side[1] > 0
        raised = forward
        if shape.startswith("inner"):
            raised = forward or lateral
        elif shape.startswith("outer"):
            raised = forward and lateral
        if base or raised:
            result.add((x, y, z))
    return result


def unit_count(item):
    if not item.startswith(NAMESPACE + ":"):
        check(item in {"minecraft:" + ("grass_block" if f == "grass" else f) for f in FAMILIES}, "Unexpected recipe input")
        return 8
    form = item.split("_sod_")[1]
    return {"block": 8, "patch": 1, "slab": 4, "stairs": 6}[form]


def matches(condition, state):
    check(condition["type"] == "minecraft:match_block", "Unexpected loot condition")
    return all(state.get(key) == value for key, value in condition["state"].items())


def loot_count(entry, state):
    if "condition" in entry:
        condition = entry["condition"]
        if condition["type"] == "minecraft:match_block" and not matches(condition, state):
            return None
        check(condition["type"] in {"minecraft:match_block", "minecraft:survives_explosion"}, "Unexpected loot condition")
    if entry["type"] == "minecraft:alternatives":
        counts = [loot_count(child, state) for child in entry["children"]]
        counts = [count for count in counts if count is not None]
        check(len(counts) == 1, "Loot must select exactly one branch")
        return counts[0]
    check(entry["type"] == "minecraft:item", "Unexpected loot entry")
    count = 1
    modifiers = entry.get("modifier", [])
    if isinstance(modifiers, dict):
        modifiers = [modifiers]
    for modifier in modifiers:
        check(modifier["type"] in {"minecraft:set_count", "minecraft:explosion_decay"}, "Unexpected loot modifier")
        if modifier["type"] == "minecraft:set_count":
            if "condition" not in modifier or matches(modifier["condition"], state):
                count = modifier["count"]
    return count


def audit(root):
    assets = root / "assets" / NAMESPACE
    data = root / "data" / NAMESPACE
    ids = {f"{family}_sod_{form}" for family in FAMILIES for form in FORMS}
    for folder in (assets / "blockstates", assets / "items", data / "loot_table" / "blocks"):
        check({p.stem for p in folder.glob("*.json")} == ids, f"20-variant coverage failed: {folder}")
    for language in ("en_us", "zh_cn"):
        entries = read(assets / "lang" / (language + ".json"))
        check(all(entries.get(f"block.{NAMESPACE}.{name}") for name in ids), f"Missing {language} name")
        item_tags = {f"{family}_sod" for family in FAMILIES} | {"sod", "sod_patches"}
        check(all(entries.get(f"tag.item.{NAMESPACE}.{name}") for name in item_tags), f"Missing {language} item tag name")

    model_cache = {}
    for file in (assets / "models" / "block").glob("*.json"):
        model = read(file)
        planes = [e for e in model["elements"] if "rotation" in e]
        solid = dict(model, elements=[e for e in model["elements"] if "rotation" not in e])
        model_cache[f"{NAMESPACE}:block/{file.stem}"] = (model, cells(solid))
        exposed_faces(solid, model_cache[f"{NAMESPACE}:block/{file.stem}"][1])
        family = file.stem.split("_sod_")[0]
        texture = {
            "grass": "grass_block_top", "mycelium": "mycelium_top", "podzol": "podzol_top",
            "crimson_nylium": "crimson_nylium", "warped_nylium": "warped_nylium",
        }[family]
        check(model["textures"]["surface"] == f"minecraft:block/{texture}", "Non-vanilla texture")
        for element in solid["elements"]:
            for face in element["faces"].values():
                check(face.get("tintindex") == (0 if family == "grass" else None), "Incorrect grass tint")
        if planes:
            layer, plant = file.stem.split("_patch_")[1].split("_")
            y = int(layer) * 2
            check(len(planes) == 2 and plant in ("grass", "fern"), "Expected two plant planes")
            check(model["textures"]["plant"] == "minecraft:block/" + ("short_grass" if plant == "grass" else "fern"), "Plant texture mismatch")
            for plane in planes:
                check(plane["from"][1] == y and plane["to"][1] == y + 16, "Plant floats or sinks into sod")
                check(plane["rotation"] == {"origin": [8, y + 8, 8], "axis": "y", "angle": 45, "rescale": True}, "Plant rotation mismatch")
                check(all(f["texture"] == "#plant" and f["tintindex"] == 1 and f["uv"] == [0, 0, 16, 16]
                          and "cullface" not in f for f in plane["faces"].values()), "Plant tint/UV/culling mismatch")
    check(len(model_cache) == 150, "Expected 70 bare and 80 vegetated models")

    state_count = 0
    for family in FAMILIES:
        for form in FORMS:
            name = f"{family}_sod_{form}"
            item = read(assets / "items" / (name + ".json"))["model"]
            check(item["model"] in model_cache and item["type"] == "minecraft:model", "Invalid item model")
            if family == "grass":
                check(item["tints"] == [{"type": "minecraft:grass", "downfall": 1.0, "temperature": 0.5}], "Incorrect item grass tint")
            else:
                check("tints" not in item, "Non-grass item must be untinted")
            variants = read(assets / "blockstates" / (name + ".json"))["variants"]
            check(len(variants) == {"block": 1, "patch": 24, "slab": 3, "stairs": 40}[form], "Incomplete state variants")
            for properties, variant in variants.items():
                state = dict(part.split("=") for part in properties.split(",")) if properties else {}
                check(variant["model"] in model_cache, "Missing referenced model")
                actual = rotated(model_cache[variant["model"]][1], variant)
                if form == "stairs":
                    expected = expected_stairs(state["facing"], state["half"], state["shape"])
                else:
                    start, end = 0, 16
                    if form == "patch":
                        end = int(state["layers"]) * 2
                        vegetation = state["vegetation"]
                        suffix = "" if vegetation == "none" else "_" + vegetation
                        check(variant["model"] == f"{NAMESPACE}:block/{family}_sod_patch_{state['layers']}{suffix}", "Plant state/model mismatch")
                    elif form == "slab":
                        if state["type"] == "top":
                            start = 8
                        elif state["type"] == "bottom":
                            end = 8
                    expected = set(itertools.product(range(16), range(start, end), range(16)))
                check(actual == expected, f"Geometry does not match {name}[{properties}]")
                state_count += 1

            table = read(data / "loot_table" / "blocks" / (name + ".json"))
            pools = table["pools"]
            check(table["type"] == "minecraft:block" and len(pools) == 1 and pools[0]["rolls"] == 1, "Invalid loot pool")
            entry = pools[0]["entries"][0]
            state_expectations = [({}, 1)]
            if form == "patch":
                state_expectations = [({"layers": str(layer)}, layer) for layer in range(1, 9)]
            elif form == "slab":
                state_expectations = [({"type": half}, 2 if half == "double" else 1) for half in ("bottom", "top", "double")]
            for state, expected in state_expectations:
                check(loot_count(entry, state) == expected, f"Incorrect loot count for {name}: {state}")

    recipes = list((data / "recipe").glob("*.json"))
    check(len(recipes) == 40, "Expected all 40 crafting recipes")
    for file in recipes:
        recipe = read(file)
        if recipe["type"] == "minecraft:crafting_shaped":
            ingredients = [recipe["key"][letter] for row in recipe["pattern"] for letter in row if letter != " "]
        else:
            check(recipe["type"] == "minecraft:crafting_shapeless", "Unexpected crafting serializer")
            ingredients = recipe["ingredients"]
        result = recipe["result"]
        check(result["id"].startswith(NAMESPACE + ":"), "Recipe converts sod back into vanilla blocks")
        check(sum(unit_count(item) for item in ingredients) == unit_count(result["id"]) * result.get("count", 1), f"Sod conservation failed: {file.name}")
        advancement = read(data / "advancement" / "recipes" / "building_blocks" / file.name)
        check(advancement["rewards"]["recipes"] == [f"{NAMESPACE}:{file.stem}"], "Recipe unlock mismatch")
    for tool, families in (("shovel", FAMILIES[:3]), ("pickaxe", FAMILIES[3:])):
        values = read(root / "data" / "minecraft" / "tags" / "block" / "mineable" / (tool + ".json"))
        check(not values["replace"] and set(values["values"]) == {identifier(family, form) for family in families for form in FORMS}, "Incorrect mining tags")
    no_spawn = read(root / "data" / "minecraft" / "tags" / "block" / "prevent_mob_spawning_inside.json")
    check(not no_spawn["replace"] and set(no_spawn["values"]) == {identifier(family, "patch") for family in FAMILIES}, "Patch spawn-blocking tag incomplete")
    check(not list(root.rglob("*.png")), "Generated resources must not bundle textures")
    print(f"PASS: 20 variants, 150 models including 80 plant composites, {state_count} geometry states (200 stairs rotations), 40 conserving recipes, 20 loot tables, 2 languages, tool/spawn tags.")


def audit_native_resources(root, minecraft_client_jar):
    assets = root / "assets" / NAMESPACE
    with zipfile.ZipFile(minecraft_client_jar) as jar:
        vanilla = json.loads(jar.read("assets/minecraft/blockstates/oak_stairs.json"))["variants"]
        for family in FAMILIES:
            states = read(assets / "blockstates" / f"{family}_sod_stairs.json")["variants"]
            for key, variant in states.items():
                actual = {k: v for k, v in variant.items() if k != "model"}
                expected = {k: v for k, v in vanilla[key].items() if k != "model"}
                check(actual == expected, f"Native rotation mismatch: {family}[{key}]")
        entries = set(jar.namelist())
        for file in (assets / "models" / "block").glob("*.json"):
            for texture in read(file)["textures"].values():
                if not texture.startswith("#"):
                    namespace, path = texture.split(":")
                    check(f"assets/{namespace}/textures/{path}.png" in entries, f"Native texture missing: {texture}")
    print("PASS: all 200 stair rotations/uvlock match native blockstates; every native texture reference exists.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generated", type=Path, default=Path(__file__).resolve().parents[1] / "src" / "main" / "generated")
    parser.add_argument("--minecraft-client-jar", type=Path, help="Optionally verify rotations and runtime texture IDs against the actual target Minecraft JAR")
    arguments = parser.parse_args()
    audit(arguments.generated)
    if arguments.minecraft_client_jar:
        audit_native_resources(arguments.generated, arguments.minecraft_client_jar)
