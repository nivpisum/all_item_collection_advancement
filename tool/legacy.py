"""Build AICA's vanilla datapack and Forge lowcodefml wrapper from one catalog.

Python 3.11+, standard library only. No Java code or Gradle installation needed.
The catalog retains the original all_item advancement IDs and item criteria.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
ASSET = ROOT / "asset"
REFERENCE = ROOT / "reference"
VERSION = "1.2.0"
MINECRAFT = "1.20.1"
NAMESPACE = "all_item"
SUMMARY = "all/root"
MIGRATION = "internal/upgrade_1_20_1"
UPGRADE_CRITERION = "survival_1145"
CLASS_CRITERIA = {"arm": "11", "build": "12", "life": "13", "stone": "14", "work": "15"}


def json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def validate_catalog(catalog: dict) -> None:
    assert catalog["schema"] == 1, "Unknown catalog schema"
    classes, orders, families, groups = (catalog[k] for k in ("classes", "orders", "families", "groups"))
    ids = [node["id"] for nodes in (classes, orders, families, groups) for node in nodes]
    assert len(ids) == len(set(ids)), "Duplicate category ID"
    assert all(re.fullmatch(r"[a-z0-9_/]+", key) for key in ids), "Invalid category ID"
    class_ids = {node["id"] for node in classes}
    order_ids = {node["id"] for node in orders}
    family_ids = {node["id"] for node in families}
    assert class_ids == set(CLASS_CRITERIA)
    assert all(node["id"].rsplit("/", 1)[0] in class_ids for node in orders)
    assert all(node["id"].rsplit("/", 1)[0] in order_ids for node in families)
    assert all(node["id"].rsplit("/", 1)[0] in family_ids for node in groups)
    counts = Counter(item for node in groups for item in node["items"])
    assert all(n == 1 for n in counts.values()), "An item must belong to exactly one group"
    assert all(node["items"] for node in groups), "Empty group"
    assert all(re.fullmatch(r"[a-z0-9_]+", item) for item in counts), "Invalid vanilla item ID"
    excluded = [node["item"] for node in catalog["excluded"]]
    assert len(excluded) == len(set(excluded)), "Duplicate survival exclusion"
    assert set(excluded) <= set(counts), "Excluded ID missing from the full classification"
    registry = json.loads((DATA / "registry_1_20_1.json").read_text(encoding="utf-8"))
    known = {node["id"].removeprefix("minecraft:") for node in registry["items"]}
    assert registry["minecraft"] == MINECRAFT and len(known) == registry["count"] == 1255
    assert set(counts) == known, f"Registry mismatch: missing={known - set(counts)}, extra={set(counts) - known}"
    assert all(any(g["id"].startswith(f["id"] + "/") for g in groups) for f in families)


def survival_catalog(catalog: dict) -> dict:
    """The complete taxonomy is independent of the game's obtainable-item goals."""
    excluded = {node["item"] for node in catalog["excluded"]}
    groups = [{**node, "items": [item for item in node["items"] if item not in excluded]}
              for node in catalog["groups"]]
    groups = [node for node in groups if node["items"]]
    result = {**catalog, "groups": groups}
    for level in ("classes", "orders", "families"):
        result[level] = [node for node in catalog[level]
                         if any(group["id"].startswith(node["id"] + "/") for group in groups)]
    return result


def display(node: dict, *, summary: bool = False, root: bool = False) -> dict:
    result = {
        "icon": {"item": "minecraft:" + node["icon"]},
        "title": node["title"],
        "description": node.get("description", ""),
        "frame": "challenge" if summary else "task",
        "show_toast": True,
        "announce_to_chat": True,
        "hidden": False,
    }
    if root:
        texture = "adventure" if summary else "stone"
        result["background"] = f"minecraft:textures/gui/advancements/backgrounds/{texture}.png"
    return result


def inventory_advancement(node: dict, items: list[str], *, parent: str | None,
                          any_item: bool = False, summary: bool = False, root: bool = False) -> dict:
    criteria = {
        "minecraft:" + item: {
            "trigger": "minecraft:inventory_changed",
            "conditions": {"items": [{"items": ["minecraft:" + item]}]},
        }
        for item in items
    }
    result = {
        "display": display(node, summary=summary, root=root),
        "criteria": criteria,
        "requirements": [list(criteria)] if any_item else [[key] for key in criteria],
    }
    if parent is not None:
        result["parent"] = NAMESPACE + ":" + parent
    return result


def generate(catalog: dict) -> dict[str, bytes]:
    validate_catalog(catalog)
    catalog = survival_catalog(catalog)
    advancements = {}
    groups = catalog["groups"]

    def items_below(prefix: str) -> list[str]:
        return [item for group in groups if group["id"].startswith(prefix + "/") for item in group["items"]]

    for node in groups:
        family = node["id"].rsplit("/", 1)[0]
        advancements[node["id"]] = inventory_advancement(node, node["items"], parent=family + "/_family")
    for node in catalog["families"]:
        order = node["id"].rsplit("/", 1)[0]
        advancements[node["id"] + "/_family"] = inventory_advancement(
            node, items_below(node["id"]), parent=order + "/root", any_item=True)
    for node in catalog["orders"]:
        items = items_below(node["id"])
        advancements[node["id"] + "/root"] = inventory_advancement(node, items, parent=None, any_item=True, root=True)
        class_id = node["id"].split("/")[0]
        advancements["all/" + node["id"]] = inventory_advancement(
            node, items, parent="all/" + class_id + "/_class", summary=True)
    for node in catalog["classes"]:
        advancement = inventory_advancement(node, items_below(node["id"]), parent=SUMMARY, summary=True)
        advancement["rewards"] = {"function": NAMESPACE + ":complete/" + node["id"]}
        advancements["all/" + node["id"] + "/_class"] = advancement

    # Native tick criteria reconcile completed categories for old/offline players.
    # Each listener is removed once met; no global per-tick function or inventory scan.
    criteria = {
        criterion: {
            "trigger": "minecraft:tick",
            "conditions": {"player": {"type_specific": {
                "type": "player", "advancements": {f"{NAMESPACE}:all/{class_id}/_class": True}
            }}},
        }
        for class_id, criterion in CLASS_CRITERIA.items()
    }
    advancements[SUMMARY] = {
        "display": display({"icon": "player_head", "title": "全物品收集完成!",
                            "description": f"收集 Minecraft 1.20.1 的 {sum(len(g['items']) for g in groups)} 种生存物品；按物品 ID 计数。"},
                           summary=True, root=True),
        "criteria": criteria,
        "requirements": [[key] for key in criteria],
    }
    # Keep all old criteria and timestamps. Only stale total-category markers
    # are revoked once per requirement revision. The added criterion re-runs this
    # migration for 1.1.0 players without resetting their original installed date.
    advancements[MIGRATION] = {
        "criteria": {"installed": {"trigger": "minecraft:tick"},
                     UPGRADE_CRITERION: {"trigger": "minecraft:tick"}},
        "requirements": [["installed"], [UPGRADE_CRITERION]],
        "rewards": {"function": NAMESPACE + ":upgrade_1_2_0"},
    }
    files = {f"data/{NAMESPACE}/advancements/{key}.json": json_bytes(value)
             for key, value in advancements.items()}
    for class_id, criterion in CLASS_CRITERIA.items():
        # Only the completing player's advancement state is changed.
        files[f"data/{NAMESPACE}/functions/complete/{class_id}.mcfunction"] = (
            f"advancement grant @s only {NAMESPACE}:{SUMMARY} {criterion}\n".encode())
    files[f"data/{NAMESPACE}/functions/upgrade_1_2_0.mcfunction"] = "".join(
        f"execute if entity @s[advancements={{{NAMESPACE}:all/{class_id}/_class=false}}] "
        f"run advancement revoke @s only {NAMESPACE}:{SUMMARY} {criterion}\n"
        for class_id, criterion in CLASS_CRITERIA.items()
    ).encode()
    files["pack.mcmeta"] = json_bytes({"pack": {"pack_format": 15,
        "description": f"AICA {VERSION} | SnowyPea 物品分类与收集进度 | Minecraft {MINECRAFT}"}})
    files["pack.png"] = (ASSET / "pack.png").read_bytes()
    files["NOTICE.txt"] = (ROOT / "NOTICE.md").read_bytes()
    files["README.md"] = (REFERENCE / "guide.md").read_bytes()
    return files


def write_archive(path: Path, files: dict[str, bytes]) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (2026, 9, 25, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            entry.create_system = 3
            archive.writestr(entry, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build(destination: Path) -> dict:
    catalog = json.loads((DATA / "catalog.json").read_text(encoding="utf-8"))
    taxonomy = json.loads((DATA / "taxonomy.json").read_text(encoding="utf-8"))
    files = generate(catalog)
    outputs = {}
    for kind, filename, additions in (
        ("datapack", f"aica_{MINECRAFT}_{VERSION}.zip", {}),
        ("forge", f"aica_{MINECRAFT}_forge_{VERSION}.jar", {
            "META-INF/mods.toml": (ASSET / "mods.toml").read_bytes(),
            "META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\r\nFMLModType: MOD\r\n\r\n",
        }),
    ):
        output = destination / filename
        outputs[kind] = {"file": filename, "sha256": write_archive(output, files | additions)}
    import export_freeplane
    mindmap = destination / "aica_item_1.20.1.mm"
    mindmap_check = export_freeplane.export(catalog, mindmap)
    outputs["mindmap"] = {"file": mindmap.name, "sha256": hashlib.sha256(mindmap.read_bytes()).hexdigest()}
    selected = survival_catalog(catalog)
    report = {
        "version": VERSION, "minecraft": MINECRAFT, "pack_format": 15,
        "items": sum(len(node["items"]) for node in selected["groups"]),
        "classification_items": sum(len(node["items"]) for node in catalog["groups"]),
        "advancements": sum("/advancements/" in name for name in files),
        "groups": len(selected["groups"]), "classes": len(selected["classes"]),
        "orders": len(selected["orders"]), "families": len(selected["families"]),
        "taxonomy": {
            "phyla": mindmap_check["phyla"],
            "item_order_source": taxonomy["ordering"]["item_sequence_source"],
            "item_order_source_items": len(taxonomy["ordering"]["item_sequence"]),
            "subgroups": mindmap_check["subgroups"],
            "freeplane_nodes": mindmap_check["nodes"],
            "phylum_children": mindmap_check["phylum_children"],
            "class_positions_by_door": mindmap_check["class_positions_by_door"],
            "door_positions": mindmap_check["door_positions"],
            "infinite_class_order": mindmap_check["infinite_class_order"],
            "expanded_paths": mindmap_check["expanded_paths"],
            "open_nodes": mindmap_check["open_nodes"],
            "pinned_descendant_positions": mindmap_check["node_pinned_side_count"],
        },
        "excluded": catalog["excluded"], "artifacts": outputs,
    }
    (destination / "manifest.json").write_bytes(json_bytes(report))
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "build")
    args = parser.parse_args()
    report = build(args.output)
    print(json.dumps({key: value for key, value in report.items() if key != "excluded"}, ensure_ascii=False, indent=2))
