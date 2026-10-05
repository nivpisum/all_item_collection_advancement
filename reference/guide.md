# AICA 1.3.0 · Installation and use / 安装与使用

AICA（All Item Collection Advancement，全物品收集进度）by **nivpisum**, historically SnowyPea, is a Minecraft Java **1.20.1** collection challenge. **1.3.0 is a release.** Its classification remains **1.0.0**: 1,656 nodes and 1,255 item IDs, including 1,146 legal and 109 illegal IDs.

## Installation / 安装

| File / 文件 | Install / 安装 |
|---|---|
| `aica_1.20.1_1.3.0.zip` | Do not extract. Put it in the world's `datapacks` folder; run `/reload` or reopen the world. Use `/datapack list enabled` to confirm. / 不解压，放入世界 `datapacks`，执行 `/reload` 或重新进世界，用 `/datapack list enabled` 确认启用。 |
| `aica_1.20.1_forge_1.3.0.jar` | Put it in `mods` and restart. Target: Forge **47.4.23**, Java **17**. / 放入实例 `mods` 并重启，目标为 Forge **47.4.23 / Java 17**。 |

**Forge clients require these separate downloads / Forge 客户端另装：**

- [MoreCreativeTabs **1.2.1**](https://modrinth.com/mod/morecreativetabs/version/gbaKXRAB) — `MoreCreativeTabs-combo-1.20-1.2.1.jar`
- [Inventory Item Groups **1.5.1-forge+1.20**](https://modrinth.com/mod/inventory-item-groups/version/ktqJe6qa) — `inventory-item-groups-1.5.1-forge+1.20-all-srg.jar`

These dependencies are client-only and are not bundled. The ZIP needs no mod dependencies. Cloth Config, Better Advancements and Click Advancements are optional. / 两个前置仅客户端必装，不嵌入 AICA。ZIP 无模组前置；Cloth Config、Better Advancements、Click Advancements 为可选。

Enable **one format per world**. Back up before upgrading and remove the previous AICA ZIP/JAR. / **同一世界只启用一种形式**；升级前备份并移走旧 AICA ZIP/JAR。

Multiplayer requires AICA game data in the server world, through ZIP or Forge JAR. Clients install the JAR and its two dependencies for extra interface features. A client-only installation cannot add advancements to a remote world. Dedicated-server and full-modpack coverage is not claimed for this release. / 联机时服务器世界须启用 ZIP 或 JAR 的游戏数据，额外界面功能由客户端 JAR 与两个前置提供。仅装客户端无法给远程世界添加进度；此版本不宣称已覆盖专用服务器或完整整合包测试。

## Collection / 收集

There are **27 order pages and one overview**, with **721 visible advancements**. Every visible category requires **all** its item IDs; hidden discovery conditions only reveal branches. Illegal items do not enter survival advancements. Enchantment levels, potion effects and other NBT variants are not separate collection units. / **27 个目页面加总览，共 28 页、721 个可见进度**。可见分类目标须收齐全部物品 ID，隐藏条件仅用于发现分支；非法门不进入生存进度，附魔等级与药水效果等 NBT 变体不另计。

Vanilla popups and chat announcements remain. AICA grants no experience, items, recipe unlocks or function rewards. The world's `announceAdvancements` setting controls broadcasts. / 保留原版弹窗和广播，进度不奖励经验、物品、配方解锁或函数；广播仍服从世界 `announceAdvancements` 设置。

The original built-in 1.20.1 bundle recipe is enabled: six rabbit hides and two strings; no separate bundle experimental datapack is needed. / 启用原版 1.20.1 内置收纳袋配方：六个兔子皮、两个线，无须另开 bundle 实验数据包。

## Forge interface / Forge 界面

The first creative page has this arrangement; other mods' tabs remain on later pages. / 创造栏首页排列如下，其他模组标签页保留在后续页。

| Row / 行 | Left to right / 从左到右 |
|---|---|
| Upper / 上 | Earth and stone 土石、Wood 木材、Minerals 矿物、Finishing materials 饰面、Building 建筑、Hotbar 快捷栏、Search 搜索 |
| Lower / 下 | Engineering 工程、Crafts 工艺、Farming and food 农牧、Equipment 装备、Transport 交通、Illegal 非法门、Survival Inventory 生存物品栏 |

This provides **11 AICA tabs**, replacing vanilla category/operator tabs and the old MoreCreativeTabs daily-redstone tab. Native Hotbar, Search and Survival Inventory functions remain. / 共 **11 个 AICA 标签页**，替代原版分类页、管理员页及旧“生电常用”页，快捷栏、搜索、生存物品栏沿用原版功能。

Genera with at least three item IDs expand and collapse; one- and two-item genera stay flat. Native variants retain complete stacks and NBT: enchanted books, three potion types, tipped arrows, suspicious stew, horns, rockets, banners, paintings and operator light levels. **White and ominous banners remain inside the existing 16-colour banner genus.** Variants follow native feature and permission settings; search keeps native variants. / 至少三种物品的属可展开折叠，一种或两种属平铺。附魔书、三类药水、药箭、谜之炖菜、山羊角、烟花火箭、旗帜、画和管理员光源等级保留完整堆栈与 NBT。**白旗与不祥旗一同放在已有 16 色旗帜属内**。变体随功能与权限更新，搜索保留原生变体。

Viewed advancement pages retain their position when new nodes appear, when switching pages and after closing/reopening. This applies to the vanilla screen and optional **Better Advancements 0.6.0.73**. / 进度页在新节点出现、切页、关闭重开后保持已浏览位置，适用于原版及可选 **Better Advancements 0.6.0.73**。

## Existing worlds / 旧世界

Old `all_item` and AICA 1.0.0 item criteria import once per player. **Existing criterion dates are preserved; newly created criteria receive the import date.** Old category completion does not bypass the new all-member rules. Switching ZIP/JAR forms preserves world progress. / 旧 `all_item` 和 AICA 1.0.0 物品条件按玩家一次性导入。**已有条件日期保留，新条件使用导入日期**。旧类完成状态不会绕过新分类的全成员要求；切换 ZIP/JAR 继续使用世界中的记录。

Source and rights / 源码与权利：[GitHub](https://github.com/nivpisum/all_item_collection_advancement)。Original code, classification and documentation use **CC0-1.0**, without attribution requirements. Third-party material retains its own rights. / 原创代码、分类和文档采用 **CC0-1.0**，无需署名；第三方素材保留原许可。本项目不是 Mojang 或 Microsoft 的官方产品。
