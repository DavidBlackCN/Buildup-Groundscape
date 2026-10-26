"""Independently audit generated patches against the disabled native terrain snapshots."""
import argparse
import array
import gzip
import json
from pathlib import Path
import re
import struct
import sys
import zipfile


def snapshot(path):
    with gzip.open(path, "rb") as stream:
        def utf():
            return stream.read(struct.unpack(">H", stream.read(2))[0]).decode("utf-8")
        assert utf() == "groundscape-g1-snapshot-v1"
        seed = struct.unpack(">q", stream.read(8))[0]
        x, z, y, width, height, size = struct.unpack(">6i", stream.read(24))
        palette = [utf() for _ in range(size)]
        count = struct.unpack(">i", stream.read(4))[0]
        states = array.array("H")
        states.frombytes(stream.read())
        if sys.byteorder == "little":
            states.byteswap()
        assert len(states) == count == width * width * height
        return x, z, y, width, height, palette, states, seed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft-common-jar", required=True, type=Path)
    parser.add_argument("--evidence-dir", type=Path,
                        default=Path(__file__).resolve().parents[2] / "docs/evidence/terrain-continuity",
                        help="Snapshot directory; defaults to ../docs/evidence/terrain-continuity outside the repository")
    args = parser.parse_args()
    evidence = args.evidence_dir
    terrain = {"minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium",
               "minecraft:crimson_nylium", "minecraft:warped_nylium"}
    with zipfile.ZipFile(args.minecraft_common_jar) as jar:
        def tag(name):
            namespace, key = name.split(":")
            entries = json.loads(jar.read(f"data/{namespace}/tags/block/{key}.json"))["values"]
            for entry in entries:
                entry = entry if isinstance(entry, str) else entry["id"]
                if entry.startswith("#"):
                    tag(entry[1:])
                else:
                    terrain.add(entry)
        for name in ["dirt", "base_stone_overworld", "base_stone_nether"]:
            tag("minecraft:" + name)
    reports = {}
    for mode, control in [("on", "off"), ("repeat", "off_reverse")]:
        report = json.loads((evidence / f"{mode}.json").read_text(encoding="utf-8"))
        assert report["result"] == "PASS", mode
        checked = boundary = high = 0
        for scene in report["scenarios"]:
            original = snapshot(evidence / f"{control}-{scene}.snapshot.gz")
            result = snapshot(evidence / f"{mode}-{scene}.snapshot.gz")
            ox, oz, oy, width, height, palette, states, seed = original
            assert original[:5] == result[:5] and seed == result[7]

            def state(x, y, z):
                return palette[states[((x - ox) * width + z - oz) * height + y - oy]].split("[")[0]

            def ground(x, base, z):
                for y in range(base + 3, base - 3, -1):
                    if oy <= y < oy + height and state(x, y, z) in terrain:
                        return y
                raise AssertionError(f"Unresolved original ground at {(x, base, z)}")

            for i, entry in enumerate(result[6]):
                name = result[5][entry]
                if not name.startswith("buildup_groundscape:") or "_sod_patch[" not in name:
                    continue
                x, z, base = ox + i // (width * height), oz + i // height % width, oy + i % height - 1
                if not (ox + 2 <= x < ox + width - 2 and oz + 2 <= z < oz + width - 2):
                    boundary += 1
                    continue
                checked += 1
                immediate = [ground(x + dx, base, z + dz) for dx, dz in [(-1, 0), (1, 0), (0, -1), (0, 1)]]
                assert min(immediate) >= base, (mode, scene, x, base, z, "raised downhill lip")
                layers = int(re.search(r"layers=(\d+)", name)[1])
                if layers > 2:
                    high += 1
                    neighbors = immediate + [ground(x + dx, base, z + dz)
                                             for dx in [-2, 0, 2] for dz in [-2, 0, 2] if dx or dz]
                    assert max(neighbors) > base, (mode, scene, x, base, z, "tall patch without an uphill step")
        reports[mode] = {"checked_patches": checked, "checked_layers_above_two": high,
                         "boundary_patches_not_checked": boundary, "violations": 0}
    output = {"result": "PASS", "scope": "Native control snapshots; interior locations only", "runs": reports}
    (evidence / "edge-audit.json").write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(output, indent=2))


if __name__ == "__main__":
    main()
