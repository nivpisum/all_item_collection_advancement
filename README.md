# AICA · 全物品收集进度

**All Item Collection Advancement 1.3.0** for Minecraft Java **1.20.1**, by **nivpisum** (historically SnowyPea).

Collect an item to discover a branch, collect every member to complete its category, and follow the overview toward the full collection. AICA provides **27 order pages plus one overview**, **721 visible advancements**, and **1,146 legal item IDs**. All visible categories require **all** their members. Advancements keep vanilla popups and announcements and grant no rewards.

[Downloads on Modrinth](https://modrinth.com/project/aica_nivpisum) · [GitHub releases](https://github.com/nivpisum/all_item_collection_advancement/releases) · [Installation and use](reference/guide.md)

[Original design essay and reconstruction contract](reference/design.md) (Chinese) preserves the motivation, arguments and tradeoffs behind the taxonomy. The author's later classification edits and the implemented 1.3.0 behavior appear in endnotes.

## Install

| Format | Installation | What it adds |
|---|---|---|
| `aica_1.20.1_1.3.0.zip` | Place the unextracted ZIP in the world's `datapacks` folder; run `/reload` or reopen the world. | Collection advancements, legacy import and the built-in bundle recipe. No mod dependencies. |
| `aica_1.20.1_forge_1.3.0.jar` | Place in `mods` and restart. Target: Forge **47.4.23**, Java **17**. | The same game data, plus classification creative tabs, ordered advancement pages and retained views. |

**Forge clients must separately install** [MoreCreativeTabs **1.2.1**](https://modrinth.com/mod/morecreativetabs/version/gbaKXRAB) and [Inventory Item Groups **1.5.1-forge+1.20**](https://modrinth.com/mod/inventory-item-groups/version/ktqJe6qa). These are client-only requirements and are not bundled. Cloth Config, Better Advancements and Click Advancements are optional.

Enable **one ZIP/JAR format per world**. Back up the world and remove the previous AICA package before upgrading. For multiplayer, the server world must enable AICA game data; clients need the Forge mod and its dependencies for extra interface features. A client-only installation cannot add progress to a remote world. This release does not claim dedicated-server or complete modpack coverage.

## Forge interface

There are **11 AICA creative tabs**: the Material class's four orders, the six other legal classes, and the illegal branch. Hotbar and Search occupy the upper-right slots; Survival Inventory occupies the lower-right slot. Other mods' tabs remain available on later pages. The vanilla category tabs and MoreCreativeTabs' old daily-redstone tab are replaced.

Genera with at least three item IDs can collapse; smaller genera stay flat. Native variants preserve their complete item stacks and NBT. Both the ordinary white banner and ominous banner are included **inside the existing 16-colour banner genus**.

| Native family | Variants available in Minecraft 1.20.1 |
|---|---|
| Enchanted books | 113 |
| Each of potion, splash potion, lingering potion and tipped arrow | 42 |
| Suspicious stew | 9 |
| Goat horns | 8 |
| Firework rockets | 3 |
| Paintings | 27 normally; 31 with operator content enabled |
| Light blocks | 16 with operator content enabled |

Availability follows native feature and permission settings. Search retains native variants. Collection progress counts item IDs, not individual variants.

Advancement pages follow classification order. Viewed pages keep their position when new nodes appear, when switching pages, and after closing and reopening the screen. Supports the vanilla screen and optional **Better Advancements 0.6.0.73**.

## Classification and existing worlds

The authored classification remains **1.0.0**, with **1,656 nodes** and **1,255 item IDs**: **1,146 legal** and **109 illegal**. Illegal items do not enter survival advancements. Air remains in the classification but is an empty game stack, so creative display has 1,254 actual item IDs.

The original Minecraft 1.20.1 bundle recipe is enabled: six rabbit hides and two strings. No separate bundle experimental datapack is needed.

Old `all_item` and AICA 1.0.0 item criteria import once per player. Existing timestamps remain unchanged; newly created criteria receive the import date. Completion of an old group does not bypass the new group's all-member requirement. World progress remains stored when switching between ZIP and JAR.

## Source and build

| Path | Purpose |
|---|---|
| `data/aica_item_1.20.1_1.0.0.mm` | Author's classification; existing node IDs determine stable advancement IDs. |
| `data/catalog.json` | Compatibility mapping for legacy records. |
| `src/` | Forge and optional client integrations. |
| `tool/` | Classification reader, data generation and verification. |
| `asset/` | Badge and build assets. |
| `reference/guide.md` | Public installation and use guide. |

Use **Python 3.11+**, **Java 17**, and the included Gradle wrapper. Set `JAVA_HOME` (or `AICA_JAVA_HOME`) to your Java 17 installation. For a first build, run from the repository root:

```text
python tool/build.py --online
python tool/verify.py
```

`--online` allows the Gradle wrapper to fetch its distribution and Forge build dependencies for a clean checkout. Once those dependencies are cached, `python tool/build.py` uses the default offline Gradle mode. `python tool/build.py --data-only` builds just the datapack. `--output <directory>` changes the output directory. The build downloads missing pinned client dependencies into `build/dependencies` and verifies their SHA-256 hashes. Outputs go to `build/`, with version, classification and artifact hashes in `manifest.json`. Generation does not overwrite the classification map.

**CC0-1.0** covers AICA's original code, authored classification and documentation. You may use, modify and redistribute them, including commercially, without attribution or further permission. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md). Third-party material retains its own rights. AICA is not an official Mojang or Microsoft product.

---

## 中文说明

**AICA 1.3.0 为正式发布版**。获得物品来发现分类分支，收齐该类所有成员来完成目标，再从局部收藏走向全物品。27 个目页面加一个总览，共 28 页、721 个可见进度，覆盖 1146 种合法物品 ID；所有可见目标均要求收齐全部成员，保留原版弹窗和广播且无奖励。

**ZIP** 不解压放入世界 `datapacks`，执行 `/reload` 或重新进世界，无模组前置。**JAR** 放入 Forge 实例 `mods` 并重启，目标为 Forge 47.4.23 / Java 17；客户端另装 **MoreCreativeTabs 1.2.1** 和 **Inventory Item Groups 1.5.1-forge+1.20**，两者不嵌入 AICA。Cloth Config、Better Advancements、Click Advancements 为可选。联机进度由服务器世界启用游戏数据，额外界面功能装在客户端。

Forge 创造栏分为材料四目、其余六纲及非法门，共 11 页；快捷栏与搜索在右上、生存物品栏在右下，其他模组标签页保留。三种及以上物品的属可折叠，一两种平铺。原生 NBT 变体完整保留；**白旗和不祥旗一同放在已有的 16 色旗帜属内**。进度页在新节点出现、切页、关闭重开后保持位置，兼容原版与 Better Advancements 0.6.0.73。

分类版本 1.0.0 不变：1656 节点、1255 ID，合法门 1146、非法门 109。收纳袋使用六个兔子皮与两个线的原版内置配方。旧 `all_item` 和 AICA 1.0.0 的物品条件一次性导入，已有日期保留、新条件使用导入日期。升级前备份世界并移走旧包，同一世界只启用 ZIP/JAR 一种形式。

[把判断依据留给后来者](reference/design.md) 保留原文章的设计动机、推导与改判理由，文末补记后续分类修订及现用实现契约，供 AI 和开发者接续工作。

原创代码、自编分类和文档采用 **CC0-1.0**，包括商业使用、修改和再分发，无需署名或另行许可。**nivpisum**（旧署名 **SnowyPea**）仅作来源记录；第三方素材及前置依赖保留原许可。
