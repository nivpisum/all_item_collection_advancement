package org.snowypea.aica.client;

import com.google.gson.Gson;
import forge.me.hypherionmc.morecreativetabs.client.tabs.CustomCreativeTabRegistry;
import forge.me.hypherionmc.morecreativetabs.mixin.accessors.CreativeModeTabAccessor;
import forge.me.hypherionmc.morecreativetabs.platform.PlatformServices;
import forge.me.hypherionmc.morecreativetabs.utils.CreativeTabUtils;
import io.github.bizcub.inventoryItemGroups.Group;
import io.github.bizcub.inventoryItemGroups.Main;
import io.github.bizcub.inventoryItemGroups.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraftforge.client.CreativeModeTabSearchRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Client integration for the exact MCT 1.2.1 and IIG 1.5.1 interfaces. */
public final class AicaCreativeSupport {
    private static final String CATALOG_PATH = "/assets/aica/creative_catalog.json";
    private static volatile Map<CreativeModeTab, TabDefinition> activeTabs = Map.of();
    private static volatile int tabGeneration;
    private static final Set<String> REQUIRED_VARIANT_FAMILIES = Set.of(
            "minecraft:enchanted_book", "minecraft:potion", "minecraft:splash_potion",
            "minecraft:lingering_potion", "minecraft:tipped_arrow", "minecraft:suspicious_stew");
    // White and ominous banners belong to the existing colour-banner genus.
    private static final Set<String> GENUS_VARIANT_FAMILIES = Set.of("minecraft:white_banner");
    private static final Set<String> VANILLA_CATEGORY_TABS = Set.of(
            "building_blocks", "colored_blocks", "natural_blocks", "functional_blocks", "redstone_blocks",
            "tools_and_utilities", "combat", "food_and_drinks", "ingredients", "spawn_eggs", "op_blocks");
    private static volatile Map<String, List<ItemStack>> nativeVariants = Map.of();
    private static volatile List<ItemStack> nativeSearchItems = List.of();
    private static volatile Map<CreativeModeTab, List<ItemStack>> catalogItems = Map.of();

    private AicaCreativeSupport() {}

    /** Called after MCT has created a fresh collection of resource-backed tabs. */
    public static void enforceTabs() {
        List<TabDefinition> definitions = CatalogHolder.CATALOG.tabs;
        Map<String, CreativeModeTab> byKey = new LinkedHashMap<>();
        for (CreativeModeTab tab : CustomCreativeTabRegistry.custom_tabs) {
            String key = tabKey(tab);
            if (isCatalogKey(key) && byKey.putIfAbsent(key, tab) != null) {
                throw new IllegalStateException("Duplicate AICA creative tab: " + key);
            }
        }

        List<CreativeModeTab> ordered = new ArrayList<>(definitions.size());
        Map<CreativeModeTab, TabDefinition> freshTabs = new IdentityHashMap<>();
        for (TabDefinition definition : definitions) {
            CreativeModeTab tab = byKey.get(definition.key);
            if (tab == null) {
                throw new IllegalStateException("Missing AICA creative tab: " + definition.key);
            }
            ordered.add(tab);
            freshTabs.put(tab, definition);
        }

        // MCT positions fourteen slots as two rows of seven, including utility tabs.
        CreativeModeTab hotbar = BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.HOTBAR);
        CreativeModeTab search = CreativeModeTabs.searchTab();
        CreativeModeTab inventory = BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.INVENTORY);
        ordered.add(5, hotbar);
        ordered.add(6, search);
        ordered.add(inventory);

        // MCT's ordered JSON can omit native utility tabs and registered mod tabs.
        // Recover the original objects so their SEARCH/HOTBAR/INVENTORY behavior survives.
        LinkedHashSet<CreativeModeTab> surviving = new LinkedHashSet<>();
        for (CreativeModeTab tab : CustomCreativeTabRegistry.current_tabs) {
            if (!CustomCreativeTabRegistry.custom_tabs.contains(tab)) {
                surviving.add(tab);
            }
        }
        if (CustomCreativeTabRegistry.tabs_before != null) {
            surviving.addAll(CustomCreativeTabRegistry.tabs_before);
        }
        CustomCreativeTabRegistry.custom_tabs.stream()
                .sorted(java.util.Comparator.comparing(AicaCreativeSupport::tabKey))
                .forEach(surviving::add);
        // Also include registered tabs missing from MCT's captured baseline.
        BuiltInRegistries.CREATIVE_MODE_TAB.forEach(surviving::add);
        for (CreativeModeTab tab : surviving) {
            if (tab != null && !ordered.contains(tab) && !hideVanillaCategory(tab)
                    && !hideLegacyTab(tab)) {
                ordered.add(tab);
            }
        }
        CustomCreativeTabRegistry.current_tabs = List.copyOf(ordered);
        activeTabs = Collections.unmodifiableMap(freshTabs);
        Map<CreativeModeTab, List<ItemStack>> freshItems = new IdentityHashMap<>();
        freshTabs.keySet().forEach(tab -> freshItems.put(tab,
                List.copyOf(CustomCreativeTabRegistry.tab_items.getOrDefault(tab, List.of()))));
        catalogItems = Collections.unmodifiableMap(freshItems);
        // Upstream clearTabs does not discard old tab_items keys on resource reload.
        CustomCreativeTabRegistry.tab_items.keySet().retainAll(CustomCreativeTabRegistry.custom_tabs);
        captureNativeContents();
        expandVariantItems();
        updateSearchContents();
        clearGroupState();

        int generation = ++tabGeneration;
        Minecraft minecraft = Minecraft.getInstance();
        // tell always queues; execute may run inside MCT's resource-reload callback.
        minecraft.tell(() -> {
            if (generation != tabGeneration || minecraft.player == null
                    || minecraft.gameMode == null) {
                return;
            }
            // MCT updates the search tab collections, but does not refresh cached queries.
            refreshSearchTrees();
            if (minecraft.screen instanceof CreativeModeInventoryScreen screen) {
                // init rebuilds Forge page lists and replaces a stale selected tab.
                screen.init(minecraft, screen.width, screen.height);
            }
        });
    }

    /** Vanilla rebuilds the registered categories even when MCT hides their public tabs. */
    public static void onNativeContentsBuilt() {
        captureNativeContents();
        expandVariantItems();
        updateSearchContents();
        clearGroupState();
        refreshSearchTrees();
    }

    private static void captureNativeContents() {
        Set<ItemStack> originals = ItemStackLinkedSet.createTypeAndTagSet();
        // Use the unfiltered fields, not MCT's replaced/hidden public getters. In
        // particular, SEARCH_TAB_ONLY contains every enchantment level.
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id != null && "minecraft".equals(id.getNamespace())
                    && VANILLA_CATEGORY_TABS.contains(id.getPath())
                    && tab.getType() == CreativeModeTab.Type.CATEGORY) {
                CreativeModeTabAccessor raw = (CreativeModeTabAccessor) tab;
                originals.addAll(raw.getDisplayItemSearchTab());
                // Keep PARENT_TAB_ONLY variants too; search entries establish the
                // canonical all-level order before display-only additions.
                originals.addAll(raw.getDisplayItems());
            }
        }
        Map<String, List<ItemStack>> byItem = new LinkedHashMap<>();
        for (ItemStack stack : originals) {
            if (!stack.isEmpty()) {
                byItem.computeIfAbsent(itemId(stack), ignored -> new ArrayList<>()).add(stack);
            }
        }
        Map<String, List<ItemStack>> families = new LinkedHashMap<>();
        byItem.forEach((id, variants) -> {
            if (variants.size() > 1 || REQUIRED_VARIANT_FAMILIES.contains(id)) {
                families.put(id, List.copyOf(variants));
            }
        });
        nativeVariants = Collections.unmodifiableMap(families);
        nativeSearchItems = List.copyOf(originals);
    }

    private static void expandVariantItems() {
        activeTabs.forEach((tab, definition) -> {
            Map<String, ItemStack> existing = new LinkedHashMap<>();
            // Always fall back to the original catalogue stack after permission or
            // feature changes, rather than a stale variant from the previous build.
            for (ItemStack stack : catalogItems.getOrDefault(tab,
                    CustomCreativeTabRegistry.tab_items.getOrDefault(tab, List.of()))) {
                if (!stack.isEmpty()) {
                    existing.putIfAbsent(itemId(stack), stack);
                }
            }
            List<ItemStack> expanded = new ArrayList<>();
            for (String id : definition.items) {
                List<ItemStack> variants = nativeVariants.get(id);
                if (variants != null) {
                    // Retain the original full stacks in native search order.
                    expanded.addAll(variants);
                } else if (existing.containsKey(id)) {
                    expanded.add(existing.get(id));
                }
            }
            CustomCreativeTabRegistry.tab_items.put(tab, expanded);
        });
    }

    private static void updateSearchContents() {
        PlatformServices.TAB_HELPER.updateCreativeTabs(CustomCreativeTabRegistry.current_tabs);
        CreativeModeTabAccessor search = (CreativeModeTabAccessor) CreativeModeTabs.searchTab();
        // Preserve original search membership as well as AICA's complete catalogue
        // and foreign tabs. The native type+tag set removes only exact duplicates.
        Set<ItemStack> all = ItemStackLinkedSet.createTypeAndTagSet();
        all.addAll(nativeSearchItems);
        all.addAll(search.getDisplayItemSearchTab());
        search.getDisplayItems().clear();
        search.getDisplayItems().addAll(all);
        search.getDisplayItemSearchTab().clear();
        search.getDisplayItemSearchTab().addAll(all);
    }

    private static void refreshSearchTrees() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        CreativeModeTab search = CreativeModeTabs.searchTab();
        List<ItemStack> items = List.copyOf(search.getSearchTabDisplayItems());
        minecraft.populateSearchTree(CreativeModeTabSearchRegistry.getNameSearchKey(search), items);
        minecraft.populateSearchTree(CreativeModeTabSearchRegistry.getTagSearchKey(search), items);
    }

    /** Called for every screen selection, including utility tabs that skip IIG createGroups. */
    public static void onTabSelected(CreativeModeTab tab) {
        Main.pendingGroup = null;
        Main.selectedTab = tab;
        if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
            clearGroupState();
        }
    }

    /** Returns true when the upstream config-driven createGroups call is replaced. */
    public static boolean createAicaGroups() {
        CreativeModeTab selected = Main.selectedTab;
        TabDefinition definition = activeTabs.get(selected);
        if (definition == null) {
            return false;
        }

        clearGroupState();
        Map<String, List<ItemStack>> displayed = new LinkedHashMap<>();
        for (ItemStack stack : selected.getDisplayItems()) {
            if (!stack.isEmpty()) {
                displayed.computeIfAbsent(itemId(stack), ignored -> new ArrayList<>()).add(stack);
            }
        }

        Config previous = Config.get();
        try {
            // Group's constructor reads Config.sort; never let user sorting reorder AICA.
            Config.set(new Config() {});
            Set<ItemStack> assigned = Collections.newSetFromMap(new IdentityHashMap<>());
            for (GroupDefinition genus : definition.groups) {
                ArrayList<ItemStack> members = new ArrayList<>();
                LinkedHashSet<String> memberIds = new LinkedHashSet<>(genus.items);
                // IIG uses its first member as the fixed collapsed icon.
                if (memberIds.remove(genus.icon)) {
                    addGenusMembers(members, displayed, assigned, genus.icon);
                }
                for (String id : memberIds) {
                    addGenusMembers(members, displayed, assigned, id);
                }
                if (members.size() < 3) {
                    continue;
                }
                // Preserve the exact tab-owned references: IIG uses indexOf/removeAll.
                Group group = new Group(Component.translatableWithFallback(genus.key, genus.name),
                        selected, members);
                group.setVisibility(false);
                if (group.getItems().size() >= 3) {
                    Main.groups.add(group);
                    assigned.addAll(members);
                }
            }
            // Expanded single-ID families get their own icon, even if their item
            // was part of a larger genus. Unrelated genus members stay together.
            for (String id : definition.items) {
                if (!hasSeparateVariantGroup(id)) {
                    continue;
                }
                ArrayList<ItemStack> members = new ArrayList<>();
                for (ItemStack stack : displayed.getOrDefault(id, List.of())) {
                    if (!assigned.contains(stack)) {
                        members.add(stack);
                    }
                }
                if (members.isEmpty()) {
                    continue;
                }
                Group group = new Group(Component.translatable(members.get(0).getItem().getDescriptionId()),
                        selected, members);
                group.setVisibility(false);
                Main.groups.add(group);
                assigned.addAll(members);
            }
        } finally {
            Config.set(previous);
        }
        return true;
    }

    private static void addGenusMembers(ArrayList<ItemStack> members,
            Map<String, List<ItemStack>> displayed, Set<ItemStack> assigned, String id) {
        if (hasSeparateVariantGroup(id)) {
            return;
        }
        for (ItemStack stack : displayed.getOrDefault(id, List.of())) {
            if (!assigned.contains(stack)) {
                members.add(stack);
            }
        }
    }

    private static boolean hasSeparateVariantGroup(String id) {
        return nativeVariants.containsKey(id) && !GENUS_VARIANT_FAMILIES.contains(id);
    }

    /** Plain values only, suitable for DebugBridge or JSON serialization on client thread. */
    public static Map<String, Object> diagnostics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", 1);
        result.put("tab_generation", tabGeneration);
        Map<String, Object> variants = new LinkedHashMap<>();
        nativeVariants.forEach((id, stacks) -> variants.put(id,
                stacks.stream().map(AicaCreativeSupport::stackDetails).toList()));
        result.put("native_variant_families", variants);
        result.put("native_search_item_count", nativeSearchItems.size());
        List<Map<String, Object>> tabs = new ArrayList<>();
        for (CreativeModeTab tab : CustomCreativeTabRegistry.current_tabs) {
            TabDefinition definition = activeTabs.get(tab);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", definition == null ? "" : definition.id);
            row.put("key", tabKey(tab));
            row.put("registry_id", registryId(tab));
            row.put("kind", tabKind(tab));
            row.put("type", tab.getType().name());
            row.put("row", tab.row() == null ? "VIRTUAL" : tab.row().name());
            row.put("column", tab.column());
            row.put("name", tab.getDisplayName().getString());
            row.put("items", tab.getDisplayItems().stream().filter(s -> !s.isEmpty())
                    .map(AicaCreativeSupport::itemId).toList());
            row.put("variant_items", tab.getDisplayItems().stream().filter(s -> !s.isEmpty()
                    && nativeVariants.containsKey(itemId(s))).map(AicaCreativeSupport::stackDetails).toList());
            row.put("catalog_group_count", definition == null ? 0 : definition.groups.size());
            tabs.add(row);
        }
        result.put("tab_count", tabs.size());
        result.put("aica_tab_count", activeTabs.size());
        result.put("tabs", tabs);
        TabDefinition selected = activeTabs.get(Main.selectedTab);
        result.put("selected_tab", selected == null ? "" : selected.id);
        result.put("selected_tab_key", Main.selectedTab == null ? "" : tabKey(Main.selectedTab));
        result.put("selected_tab_kind", Main.selectedTab == null ? "" : tabKind(Main.selectedTab));
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Group group : Main.groups) {
            TabDefinition definition = activeTabs.get(group.getTab());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tab", definition == null ? "" : definition.id);
            row.put("tab_key", tabKey(group.getTab()));
            row.put("name", group.getName().getString());
            row.put("icon", itemId(group.getIcon()));
            row.put("icon_index", group.getIconIndex());
            row.put("expanded", group.isVisibility());
            row.put("items", group.getItems().stream().map(AicaCreativeSupport::itemId).toList());
            row.put("variant_items", group.getItems().stream().filter(s -> nativeVariants.containsKey(itemId(s)))
                    .map(AicaCreativeSupport::stackDetails).toList());
            groups.add(row);
        }
        result.put("groups", groups);
        result.put("pending_group", Main.pendingGroup != null);
        result.put("visible_items", Main.tempItemStacks.stream()
                .map(AicaCreativeSupport::itemId).toList());
        return result;
    }

    private static void clearGroupState() {
        Main.pendingGroup = null;
        Main.groups.clear();
        Main.rawDefaultGroups.clear();
        Main.tempItemStacks.clear();
    }

    private static boolean hideVanillaCategory(CreativeModeTab tab) {
        ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        return id != null && "minecraft".equals(id.getNamespace())
                && VANILLA_CATEGORY_TABS.contains(id.getPath());
    }

    private static boolean hideLegacyTab(CreativeModeTab tab) {
        // Restrict this to the exact stable key of the retired resource tab.
        return "morecreativetabs.daily_redstone".equals(tabKey(tab));
    }

    private static Map<String, Object> stackDetails(ItemStack stack) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", itemId(stack));
        row.put("count", stack.getCount());
        row.put("nbt", stack.hasTag() ? stack.getTag().toString() : "");
        return row;
    }

    private static String registryId(CreativeModeTab tab) {
        ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        return id == null ? "" : id.toString();
    }

    private static String tabKind(CreativeModeTab tab) {
        if (activeTabs.containsKey(tab)) {
            return "aica";
        }
        return tab.getType() == CreativeModeTab.Type.CATEGORY ? "other_mod" : "utility";
    }

    private static String tabKey(CreativeModeTab tab) {
        return CreativeTabUtils.getTabKey(
                ((CreativeModeTabAccessor) tab).getInternalDisplayName());
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static boolean isCatalogKey(String key) {
        return CatalogHolder.CATALOG.tabs.stream().anyMatch(tab -> tab.key.equals(key));
    }

    private static Catalog loadCatalog() {
        try (InputStream stream = AicaCreativeSupport.class.getResourceAsStream(CATALOG_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled AICA catalog: " + CATALOG_PATH);
            }
            Catalog catalog = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), Catalog.class);
            if (catalog == null || catalog.schema != 1 || catalog.tabs == null
                    || catalog.tabs.size() != 11) {
                throw new IllegalStateException("AICA catalog must have schema 1 and eleven tabs");
            }
            Set<String> tabKeys = new HashSet<>();
            Set<String> groupKeys = new HashSet<>();
            for (int index = 0; index < catalog.tabs.size(); index++) {
                TabDefinition tab = catalog.tabs.get(index);
                String idPattern = index < 4 ? "aica_material_[a-z0-9_]+"
                        : index < 10 ? "aica_class_[a-z0-9_]+" : "aica_illegal";
                if (tab == null || tab.id == null || !tab.id.matches(idPattern)
                        || !("morecreativetabs." + tab.id).equals(tab.key)
                        || tab.name == null || tab.name.isBlank() || tab.icon == null
                        || !tabKeys.add(tab.key) || tab.items == null || tab.groups == null) {
                    throw new IllegalStateException("Invalid AICA tab at index " + index);
                }
                Set<String> tabItems = new HashSet<>(tab.items);
                if (tabItems.contains(null) || tabItems.size() != tab.items.size()
                        || !tabItems.contains(tab.icon)) {
                    throw new IllegalStateException("Invalid AICA tab items or icon in " + tab.id);
                }
                Set<String> groupedItems = new HashSet<>();
                for (GroupDefinition group : tab.groups) {
                    if (group == null || group.key == null || group.key.isBlank()
                            || group.name == null || group.name.isBlank()
                            || !groupKeys.add(group.key) || group.items == null
                            || group.icon == null || !group.items.contains(group.icon)) {
                        throw new IllegalStateException("Invalid AICA genus in " + tab.id);
                    }
                    for (String id : new LinkedHashSet<>(group.items)) {
                        if (id == null || !tabItems.contains(id) || !groupedItems.add(id)) {
                            throw new IllegalStateException("Invalid or overlapping genus item in "
                                    + tab.id + ": " + id);
                        }
                    }
                }
            }
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled AICA catalog", exception);
        }
    }

    private static final class CatalogHolder {
        private static final Catalog CATALOG = loadCatalog();
    }

    private static final class Catalog {
        int schema;
        List<TabDefinition> tabs;
    }

    private static final class TabDefinition {
        String id;
        String key;
        String name;
        String icon;
        List<String> items;
        List<GroupDefinition> groups;
    }

    private static final class GroupDefinition {
        String key;
        String name;
        String icon;
        List<String> items;
    }
}
