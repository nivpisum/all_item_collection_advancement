"""Build AICA 1.3.0 from the formal classification and pinned Forge toolchain."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
import urllib.request
import classification
import legacy

ROOT = classification.ROOT
VERSION = "1.3.0"
MINECRAFT = "1.20.1"
NAMESPACE = "aica"
JAVA = Path(os.environ.get("AICA_JAVA_HOME") or os.environ.get("JAVA_HOME") or "")
GENERATED = ROOT / "build/generated/resources"
DEPENDENCIES = {
    "MoreCreativeTabs-combo-1.20-1.2.1.jar": {
        "sha256": "279a44285bdecd551e040c5522f04adba32dd255214a4ff152c28387786d746e",
        "url": "https://cdn.modrinth.com/data/nOiHJ1dx/versions/gbaKXRAB/MoreCreativeTabs-combo-1.20-1.2.1.jar",
        "modid": "morecreativetabs", "version": "1.2.1",
        "source": "https://github.com/hypherionmc/MoreCreativeTabs", "license": "MIT",
    },
    "inventory-item-groups-1.5.1-forge+1.20-all-srg.jar": {
        "sha256": "c6a8615b931fa4ca97d1a8f3f8e7df1ebddb3986dbe8f73402c571fe715d5d8b",
        "url": "https://cdn.modrinth.com/data/adriDDJt/versions/ktqJe6qa/inventory-item-groups-1.5.1-forge%2B1.20-all-srg.jar",
        "modid": "inventory_item_groups", "version": "1.5.1-forge+1.20",
        "source": "https://github.com/BizCub/inventory-item-groups", "license": "MIT",
    },
}
CLASS_BACKGROUNDS = {
    "材料纲": "stone", "建筑纲": "adventure", "工程纲": "nether",
    "工艺纲": "end", "农牧纲": "husbandry", "装备纲": "adventure", "交通纲": "end",
}
IMPORT_SCORE = "aica_v130"


def page_id(order):
    return "page/" + order.key


def creative_branches(root):
    legal = root.children[0]
    material = legal.children[0]
    assert material.name == "材料纲" and len(material.children) == 4
    return [("aica_material_" + n.key, n) for n in material.children] + \
           [("aica_class_" + n.key, n) for n in legal.children[1:]] + [("aica_illegal", root.children[1])]


def advancement_catalog(root):
    legal = root.children[0]
    tabs = [{"id": "aica:" + root.advancement, "order": 0, "name": "物品界",
             "class": "总览", "icon": legal.icon, "background": "stone", "overview": True}]
    nodes = ["aica:" + root.advancement, "aica:" + legal.advancement]
    def page_nodes(node):
        if node.rank == "属" and len(node.items) == 1:
            return page_nodes(next(child for child in node.walk() if child.item))
        nodes.append("aica:" + node.advancement)
        if not node.item and node.rank != "属":
            for child in node.children:
                page_nodes(child)
    for cls in legal.children:
        nodes.append("aica:" + cls.advancement)
        for order in cls.children:
            assert order.rank == "目"
            tabs.append({"id": "aica:" + page_id(order), "order": len(tabs), "name": order.name,
                         "class": cls.name, "icon": order.icon, "background": CLASS_BACKGROUNDS[cls.name]})
            nodes.extend(["aica:" + order.advancement, "aica:" + page_id(order)])
            for child in order.children:
                page_nodes(child)
    return {"schema": 1, "tabs": tabs, "node_order": nodes}


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def inventory_criteria(items):
    return {item: {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": [item]}]}}
            for item in items}


def advancement_files(root):
    files, targets = {}, {}
    legal = root.children[0]
    discovery_count = 0

    def emit(node, parent=None, *, key=None, items=None, background=None, recurse=True, mirrored=False):
        nonlocal discovery_count
        if node.rank == "属" and len(node.items) == 1:
            emit(next(child for child in node.walk() if child.item), parent)
            return
        key = key or node.advancement
        terminal = bool(node.item) or node.rank == "属"
        items = items if items is not None else node.items
        frame = "challenge" if node.rank in {"界", "门", "纲", "目", "亚目"} else \
                "goal" if node.rank in {"科", "亚科"} else "task"
        description = "获得" + node.name if node.item else \
                      "获得合法门的所有物品" if node.rank == "界" else \
                      "获得" + node.name + "的所有物品"
        display = {"icon": {"item": items[0]}, "title": node.name, "description": description,
                   "frame": frame, "show_toast": not mirrored,
                   "announce_to_chat": not mirrored, "hidden": False}
        if background:
            display["background"] = "minecraft:textures/gui/advancements/backgrounds/" + background + ".png"
        criteria = inventory_criteria(items)
        value = {"display": display, "criteria": criteria, "requirements": [[item] for item in items]}
        if parent:
            value["parent"] = f"{NAMESPACE}:{parent}"
        files[f"data/{NAMESPACE}/advancements/{key}.json"] = json_bytes(value)
        for item in items:
            targets.setdefault(item, []).append(key)
        if not node.item:
            # A hidden leaf discovers the branch without completing its visible
            # all-members goal. This also works in the standalone vanilla datapack.
            discovery = "discovery/" + ("page_" if mirrored else "") + node.key
            files[f"data/aica/advancements/{discovery}.json"] = json_bytes({
                "parent": "aica:" + key, "criteria": inventory_criteria(items), "requirements": [items]})
            discovery_count += 1
            for item in items:
                targets.setdefault(item, []).append(discovery)
        if recurse and not terminal:
            for child in node.children:
                emit(child, key)

    # One legal hierarchy overview stops at order; illegal never enters progress.
    emit(root, items=legal.items, background="stone", recurse=False)
    emit(legal, root.advancement, recurse=False)
    for cls in legal.children:
        emit(cls, legal.advancement, recurse=False)
        for order in cls.children:
            emit(order, cls.advancement, recurse=False)
            emit(order, key=page_id(order), background=CLASS_BACKGROUNDS[cls.name], mirrored=True)

    # Retain old saved criteria without old pages, listeners, rewards or potion effects.
    old_catalog = json.loads((ROOT / "data/catalog.json").read_text(encoding="utf-8"))
    old_files = legacy.generate(old_catalog)
    legacy_count = 0
    for name, payload in old_files.items():
        if not name.startswith("data/all_item/advancements/"):
            continue
        value = json.loads(payload)
        value.pop("display", None)
        value.pop("rewards", None)
        value["criteria"] = {key: {"trigger": "minecraft:impossible"} for key in value["criteria"]}
        files[name] = json_bytes(value)
        legacy_count += 1
    selected = legacy.survival_catalog(old_catalog)
    old_items = {"minecraft:" + item: "all_item:" + group["id"]
                 for group in selected["groups"] for item in group["items"]}
    migration = []
    grant_count = 0
    for item in legal.items:
        short = item.removeprefix("minecraft:")
        predicate = f"collected/{short}"
        # Accept the recorded item criterion from any retained classification
        # level, including a terminal genus/species granted independently.
        old_classification_keys = ["aica:" + key for key in targets[item]
                                   if key.startswith("node/")
                                   and key not in {root.advancement, legal.advancement}]
        terms = [{"condition": "minecraft:entity_properties", "entity": "this", "predicate": {
                  "type_specific": {"type": "player", "advancements": {key: {item: True}}}}}
                 for key in ([old_items[item]] if item in old_items else []) + old_classification_keys]
        files[f"data/aica/predicates/{predicate}.json"] = json_bytes({"condition": "minecraft:any_of", "terms": terms})
        grants = []
        for target in targets[item]:
            command = f"advancement grant @s only aica:{target} {item}"
            if target.startswith("discovery/"):
                command = f"execute unless entity @s[advancements={{aica:{target}=true}}] run " + command
            grants.append(command)
        grant_count += len(grants)
        files[f"data/aica/functions/import_item/{short}.mcfunction"] = ("\n".join(grants) + "\n").encode()
        migration.append(f"execute if predicate aica:{predicate} run function aica:import_item/{short}")
    files["data/aica/functions/internal/upgrade_1_3.mcfunction"] = (
        "\n".join(migration + [f"scoreboard players set @s {IMPORT_SCORE} 1"]) + "\n").encode()
    files["data/aica/functions/internal/load.mcfunction"] = f"scoreboard objectives add {IMPORT_SCORE} dummy\n".encode()
    files["data/aica/functions/internal/tick.mcfunction"] = (
        f"execute as @a unless score @s {IMPORT_SCORE} matches 1 run function aica:internal/upgrade_1_3\n").encode()
    files["data/minecraft/tags/functions/load.json"] = json_bytes({"values": ["aica:internal/load"]})
    files["data/minecraft/tags/functions/tick.json"] = json_bytes({"values": ["aica:internal/tick"]})
    # Preserve the old migration timestamp; this revision has no rewards anywhere.
    files["data/aica/advancements/internal/import_legacy.json"] = json_bytes({
        "criteria": {"installed": {"trigger": "minecraft:impossible"}}, "requirements": [["installed"]],
    })
    # Exact built-in 1.20.1 bundle recipe: makes the legal collection attainable.
    files["data/minecraft/recipes/bundle.json"] = (ROOT / "data/bundle_recipe_1_20_1.json").read_bytes()
    files["pack.mcmeta"] = json_bytes({"pack": {"pack_format": 15,
        "description": f"AICA {VERSION} · nivpisum · 全物品分类与收集进度 · Minecraft {MINECRAFT}"}})
    files["pack.png"] = (ROOT / "asset/AICA全物品收集进度徽章.png").read_bytes()
    files["NOTICE.txt"] = (ROOT / "NOTICE.md").read_bytes()
    files["LICENSE"] = (ROOT / "LICENSE").read_bytes()
    guide = (ROOT / "reference/guide.md").read_text(encoding="utf-8")
    # Repository references have no relative destination inside the release archive.
    guide = guide.replace(
        "[`data/aica_item_1.20.1_1.0.0.mm`](../data/aica_item_1.20.1_1.0.0.mm)",
        "源码目录中的 `data/aica_item_1.20.1_1.0.0.mm`")
    guide = guide.replace("[验证记录](validation.json)", "源码目录的 `reference/validation.json`")
    guide = guide.replace("[NOTICE.md](../NOTICE.md)", "`NOTICE.txt`")
    files["README.md"] = guide.encode("utf-8")
    return files, {
        "visible_advancements": len([name for name in files if name.startswith((
            "data/aica/advancements/node/", "data/aica/advancements/page/"))]),
        "legacy_hidden_advancements": legacy_count, "legacy_import_commands": len(migration) + grant_count,
        "discovery_advancements": discovery_count, "survival_items": len(legal.items),
        "advance_roots": len(advancement_catalog(root)["tabs"]),
    }


def creative_files(root):
    files, tabs, language = {}, [], {}
    registry = json.loads((ROOT / "data/registry_1_20_1.json").read_text(encoding="utf-8"))
    translations = {row["id"]: row["translation_key"] for row in registry["items"]}
    names = {node.item: node.name for node in root.walk() if node.item}
    for tab_id, branch in creative_branches(root):
        tab_key = "morecreativetabs." + tab_id
        language[tab_key] = branch.name
        groups = []
        for node in branch.walk():
            if node.rank != "属" or len(node.items) < 3:
                continue
            key = "aica.genus." + node.key
            language[key] = node.name
            groups.append({"key": key, "name": node.name, "icon": node.icon, "items": node.items})
        tabs.append({"id": tab_id, "key": tab_key, "name": branch.name,
                     "icon": branch.icon, "items": branch.items, "groups": groups})
        stacks = []
        for item in branch.items:
            if item == "minecraft:air":
                continue  # Air is an empty ItemStack, not a physical creative object.
            stack = {"name": item, "hide_old_tab": False}
            if item in {"minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion"}:
                stack["nbt"] = '{Potion:"minecraft:water"}'
            elif item == "minecraft:tipped_arrow":
                stack["nbt"] = '{Potion:"minecraft:poison"}'
            stacks.append(stack)
        files[f"assets/aica/morecreativetabs/{tab_id}.json"] = json_bytes({
            "tab_enabled": True, "tab_name": tab_id, "tab_stack": {"name": branch.icon},
            "replace": False, "tab_items": stacks})
    files["assets/aica/creative_catalog.json"] = json_bytes({"schema": 1, "tabs": tabs})
    keys = [tab["key"] for tab in tabs]
    files["assets/aica/morecreativetabs/ordered_tabs.json"] = json_bytes({"tabs": keys[:5] +
        ["itemGroup.hotbar", "itemGroup.search"] + keys[5:] + ["itemGroup.inventory"]})
    files["assets/aica/morecreativetabs/disabled_tabs.json"] = json_bytes({"disabled_tabs": [
        "itemGroup." + key for key in ("buildingBlocks", "coloredBlocks", "natural", "functional", "redstone",
            "tools", "combat", "foodAndDrink", "ingredients", "spawnEggs", "op")] +
        ["morecreativetabs.daily_redstone"]})
    files["assets/aica/lang/en_us.json"] = json_bytes(language)
    for item, name in names.items():
        language[translations[item]] = name
    files["assets/aica/lang/zh_cn.json"] = json_bytes(language)
    return files, tabs


def write_archive(path, files):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, payload in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 4, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            info.create_system = 3
            archive.writestr(info, payload, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare_dependencies():
    destination = ROOT / "build/dependencies"
    destination.mkdir(parents=True, exist_ok=True)
    for filename, record in DEPENDENCIES.items():
        target = destination / filename
        if not target.exists():
            urllib.request.urlretrieve(record["url"], target)
        assert hashlib.sha256(target.read_bytes()).hexdigest() == record["sha256"], filename
    return destination


def build(output, skip_java=False, offline=True):
    root = classification.read()
    data, stats = advancement_files(root)
    assets, tabs = creative_files(root)
    generated = data | assets | {"assets/aica/advancement_catalog.json": json_bytes(advancement_catalog(root))}
    GENERATED.mkdir(parents=True, exist_ok=True)
    assert GENERATED.resolve().is_relative_to((ROOT / "build").resolve())
    expected = {str(Path(name)) for name in generated}
    for previous in GENERATED.rglob("*"):
        if previous.is_file() and str(previous.relative_to(GENERATED)) not in expected:
            assert previous.resolve().is_relative_to(GENERATED.resolve())
            previous.unlink()
    for name, payload in generated.items():
        target = GENERATED / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(payload)
    output.mkdir(parents=True, exist_ok=True)
    zip_name = f"aica_{MINECRAFT}_{VERSION}.zip"
    zip_hash = write_archive(output / zip_name, data)
    manifest = {"version": VERSION, "status": "release", "author": "nivpisum",
                "classification_version": "1.0.0", "minecraft": MINECRAFT, "forge": "47.4.23", "java": 17,
                "namespace": NAMESPACE, "classification_items": len(root.items),
                "illegal_items": len(root.children[1].items),
                "classification_sha256": hashlib.sha256(classification.SOURCE.read_bytes()).hexdigest(),
                **stats, "creative_tabs": len(tabs),
                "catalogue_genera_with_at_least_three_ids": sum(len(tab["groups"]) for tab in tabs),
                "creative_visible_item_ids": sum(len([i for i in tab["items"] if i != "minecraft:air"]) for tab in tabs),
                "advancement_tabs": advancement_catalog(root)["tabs"],
                "artifacts": {"datapack": {"file": zip_name, "sha256": zip_hash}},
                "dependencies": DEPENDENCIES}
    if not skip_java:
        prepare_dependencies()
        executable = JAVA / "bin" / ("java.exe" if os.name == "nt" else "java")
        if not executable.is_file():
            raise RuntimeError("Set AICA_JAVA_HOME or JAVA_HOME to a Java 17 JDK directory")
        environment = dict(os.environ)
        environment["JAVA_HOME"] = str(JAVA)
        wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
        command = [str(wrapper)] if os.name == "nt" else ["sh", str(wrapper)]
        if offline:
            command.append("--offline")
        command.extend(["--console=plain", "build"])
        subprocess.run(command,
                       cwd=ROOT, env=environment, check=True)
        jar_name = f"aica_{MINECRAFT}_forge_{VERSION}.jar"
        compiled = ROOT / "build/libs" / jar_name
        shutil.copy2(compiled, output / jar_name)
        manifest["artifacts"]["forge"] = {"file": jar_name,
            "sha256": hashlib.sha256((output / jar_name).read_bytes()).hexdigest()}
    (output / "manifest.json").write_bytes(json_bytes(manifest))
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "build")
    parser.add_argument("--data-only", action="store_true")
    parser.add_argument("--online", action="store_true", help="Allow Gradle to resolve dependencies for a clean checkout")
    args = parser.parse_args()
    print(json.dumps(build(args.output, args.data_only, offline=not args.online), ensure_ascii=False, indent=2))


if __name__ == "__main__": main()
