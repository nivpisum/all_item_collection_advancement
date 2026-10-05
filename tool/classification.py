"""Read the author-edited Freeplane map, the sole current classification source."""
from __future__ import annotations
from collections import Counter
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "data/aica_item_1.20.1_1.0.0.mm"


@dataclass
class Node:
    uid: str
    name: str
    rank: str
    item: str | None
    children: list[Node]

    @property
    def items(self):
        return [self.item] if self.item else [i for c in self.children for i in c.items]

    @property
    def icon(self): return self.items[0]

    @property
    def key(self): return hashlib.sha1(self.uid.encode("utf-8")).hexdigest()[:16]

    @property
    def advancement(self): return "node/" + self.key

    def walk(self):
        yield self
        for c in self.children: yield from c.walk()


def attributes(node):
    return {a.get("NAME"): a.get("VALUE") for a in node.findall("attribute")}


def read(path=SOURCE):
    top = ET.parse(path).getroot().find("node")
    assert top is not None
    def parse(e):
        fields = attributes(e)
        item = fields.get("minecraft_id")
        children = [parse(c) for c in e.findall("node")]
        rank = fields.get("分类等级")
        if not rank:
            rank = "种" if item else next((r for r in ("亚目", "亚科", "亚属", "界", "门", "纲", "目", "科", "属", "组")
                                        if e.get("TEXT", "").endswith(r)), None)
        assert rank and e.get("ID") and e.get("TEXT"), e.attrib
        assert bool(item) != bool(children), e.attrib
        return Node(e.get("ID"), e.get("TEXT"), rank, item, children)
    result = parse(top)
    registry = json.loads((ROOT / "data/registry_1_20_1.json").read_text(encoding="utf-8"))
    known = {row["id"] for row in registry["items"]}
    counts = Counter(result.items)
    assert set(counts) == known and set(counts.values()) == {1}, "Each registry ID must occur once"
    nodes = list(result.walk())
    assert len({n.uid for n in nodes}) == len(nodes), "Duplicate Freeplane node ID"
    assert len({n.key for n in nodes}) == len(nodes), "Advancement key collision"
    assert [n.name for n in result.children] == ["合法门", "非法门"]
    assert len(result.children[0].children) == 7 and all(n.rank == "纲" for n in result.children[0].children)
    assert all(n.item or n.icon != "minecraft:air" for n in nodes), "Air cannot be a category icon"
    return result


def formalize(path=SOURCE):
    model = read(path)
    tree = ET.parse(path)
    top = tree.getroot().find("node")
    by_uid = {n.uid: n for n in model.walk()}
    names = {n.item: n.name for n in model.walk() if n.item}
    def set_attr(e, name, value):
        a = e.find(f"attribute[@NAME='{name}']")
        if a is None: a = ET.SubElement(e, "attribute", NAME=name)
        a.set("VALUE", str(value))
    for e in top.iter("node"):
        n = by_uid[e.get("ID")]
        details = e.find("richcontent[@TYPE='DETAILS']/html/body/p")
        assert details is not None, n.name
        details.text = n.item if n.item else str(len(n.items))
        if not n.item:
            set_attr(e, "直接首项", n.children[0].name)
            set_attr(e, "图标物品", n.icon)
            set_attr(e, "图标名称", names[n.icon])
    set_attr(top, "版本", "1.0.0")
    set_attr(top, "状态", "正式分类源")
    note = top.find("richcontent[@TYPE='NOTE']/html/body")
    if note is not None:
        for p in note.findall("p"):
            if p.text and p.text.startswith("AICA 分类图，临时版本"):
                p.text = "AICA 物品分类正式版 1.0.0。此图是数据包、Forge mod 与创造栏配置的共同分类源。"
            elif p.text and p.text.startswith("本轮交付分类图"):
                p.text = "进度追踪合法门七纲；创造栏展示七纲及非法门共八页。图标递归采用首支物品。"
            elif p.text and p.text.startswith("以下保留来源材料的修订说明"):
                p.text = "以下为制作参考的历史修订说明；当前结构、名称与数量以本图节点为准。"
    ET.indent(tree, space="  ")
    path.write_bytes(ET.tostring(tree.getroot(), encoding="utf-8", xml_declaration=False) + b"\n")
    after = read(path)
    assert [(n.uid,n.name,n.rank,n.item,[c.uid for c in n.children]) for n in model.walk()] == \
           [(n.uid,n.name,n.rank,n.item,[c.uid for c in n.children]) for n in after.walk()]
    return {"version":"1.0.0","items":len(model.items),"nodes":len(by_uid),
            "legal_items":len(model.children[0].items),"illegal_items":len(model.children[1].items),
            "sha256":hashlib.sha256(path.read_bytes()).hexdigest()}


if __name__ == "__main__":
    print(json.dumps(formalize(),ensure_ascii=False,indent=2))
