package org.snowypea.aica.client;

import com.google.gson.Gson;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.TreeNodePosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Client presentation only: no progress, criteria, or server advancement changes. */
public final class AicaAdvancementSupport {
    private static final String CATALOG_PATH = "/assets/aica/advancement_catalog.json";
    private static ClientAdvancements layoutClient;
    private static int layoutGeneration;
    private static Map<ResourceLocation, Map<ResourceLocation, PresentationPosition>> rootLayouts = Map.of();
    private static Map<ResourceLocation, PresentationPosition> positions = Map.of();
    private static Map<ResourceLocation, Integer> rootGenerations = Map.of();

    private AicaAdvancementSupport() {}

    /** Exact MC 1.20.1 SRG/official names, independent of access-transformer visibility. */
    public static Field vanillaField(Class<?> owner, String srgName, String officialName) {
        for (String name : List.of(srgName, officialName)) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Production Forge uses SRG; the mapped development class uses official names.
            }
        }
        throw new IllegalStateException("Missing MC 1.20.1 field " + owner.getName()
                + "." + srgName + " / " + officialName);
    }

    /** Runs before the listener creates tabs, so their position and page agree with order. */
    public static void orderRoots(ClientAdvancements client) {
        // MC 1.20.1 returns its mutable LinkedHashSet here; BetterAdvancements also
        // reorders this collection in its screen-opening handler, before setListener.
        @SuppressWarnings("unchecked")
        Set<Advancement> roots = (Set<Advancement>) client.getAdvancements().getRoots();
        List<Advancement> ordered = orderedRoots(roots);
        if (!new ArrayList<>(roots).equals(ordered)) {
            roots.clear();
            roots.addAll(ordered);
        }
    }

    /** Insert one catalog-ordered AICA group at its first existing position. */
    public static List<Advancement> orderedRoots(Iterable<Advancement> roots) {
        List<Advancement> original = new ArrayList<>();
        List<Advancement> aica = new ArrayList<>();
        for (Advancement root : roots) {
            original.add(root);
            if (isCatalogRoot(root)) {
                aica.add(root);
            }
        }
        if (aica.isEmpty()) {
            return original;
        }
        aica.sort(Comparator.comparingInt(root -> CatalogHolder.ORDER.get(root.getId())));
        List<Advancement> result = new ArrayList<>(original.size());
        boolean inserted = false;
        for (Advancement root : original) {
            if (isCatalogRoot(root)) {
                if (!inserted) {
                    result.addAll(aica);
                    inserted = true;
                }
            } else {
                result.add(root);
            }
        }
        return result;
    }

    public static boolean isCatalogRoot(Advancement advancement) {
        return advancement != null && CatalogHolder.ORDER.containsKey(advancement.getId());
    }

    public static Set<ResourceLocation> rootIds(ClientAdvancements client) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        client.getAdvancements().getRoots().forEach(root -> result.add(root.getId()));
        return result;
    }

    /** Roots affect tab slots; received AICA descendants affect compact tree geometry. */
    public static Set<ResourceLocation> knownUiIds(ClientAdvancements client) {
        Set<ResourceLocation> result = rootIds(client);
        for (Advancement advancement : client.getAdvancements().getAllAdvancements()) {
            if (isCatalogRoot(advancement.getRoot())) {
                result.add(advancement.getId());
            }
        }
        return result;
    }

    /** Build geometry from received nodes before any screen widgets are constructed. */
    public static void prepareLayouts(ClientAdvancements client) {
        AicaAdvancementViewport.bindClient(client);
        Map<ResourceLocation, Map<ResourceLocation, PresentationPosition>> fresh =
                calculateLayouts(client.getAdvancements().getRoots(),
                        client.getAdvancements().getAllAdvancements());
        Map<ResourceLocation, Integer> generations = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Map<ResourceLocation, PresentationPosition>> entry : fresh.entrySet()) {
            ResourceLocation root = entry.getKey();
            boolean same = layoutClient == client && Objects.equals(rootLayouts.get(root), entry.getValue());
            generations.put(root, same ? rootGenerations.get(root) : ++layoutGeneration);
        }
        layoutClient = client;
        rootLayouts = fresh;
        rootGenerations = java.util.Collections.unmodifiableMap(generations);
        positions = flatten(fresh);
    }

    /** Pure graph calculation, also usable by an isolated mapped-Minecraft harness. */
    public static Map<ResourceLocation, PresentationPosition> compactPositions(
            Iterable<Advancement> roots, Iterable<Advancement> knownNodes) {
        return flatten(calculateLayouts(roots, knownNodes));
    }

    public static PresentationPosition position(Advancement advancement) {
        return positions.get(advancement.getId());
    }

    public static int rootLayoutGeneration(Advancement root) {
        return rootGenerations.getOrDefault(root.getId(), 0);
    }

    public static int rootPixelY(Advancement root) {
        PresentationPosition position = position(root);
        return position == null ? (int) Math.floor(root.getDisplay().getY() * 27.0) : position.pixelY();
    }

    private static Map<ResourceLocation, Map<ResourceLocation, PresentationPosition>> calculateLayouts(
            Iterable<Advancement> roots, Iterable<Advancement> knownNodes) {
        Map<ResourceLocation, Advancement> known = new LinkedHashMap<>();
        knownNodes.forEach(node -> known.put(node.getId(), node));
        Map<ResourceLocation, List<Advancement>> children = new LinkedHashMap<>();
        for (Advancement node : known.values()) {
            Advancement parent = node.getParent();
            if (parent != null && known.containsKey(parent.getId())) {
                children.computeIfAbsent(parent.getId(), id -> new ArrayList<>()).add(node);
            }
        }
        // AdvancementList.remove does not detach removed objects from their parents'
        // child sets. Parent references can also point at an earlier object with the
        // same ID after rediscovery. Only the current ID map defines this layout.
        Comparator<Advancement> sourceOrder = Comparator.comparingInt((Advancement child) ->
                        CatalogHolder.NODE_ORDER.getOrDefault(child.getId(), Integer.MAX_VALUE))
                .thenComparing(child -> child.getId().toString());
        children.values().forEach(siblings -> siblings.sort(sourceOrder));
        Map<ResourceLocation, Map<ResourceLocation, PresentationPosition>> result = new LinkedHashMap<>();
        for (Advancement candidate : orderedRoots(roots)) {
            Advancement root = known.get(candidate.getId());
            if (!isCatalogRoot(root) || root.getParent() != null || root.getDisplay() == null) {
                continue;
            }
            Map<ResourceLocation, Advancement> clones = new LinkedHashMap<>();
            Advancement presentation = clonePresentation(root, null, clones, children);
            // Never pass the network graph to this method: local integrated-server
            // packets can share DisplayInfo instances with the server's full tree.
            TreeNodePosition.run(presentation);
            Map<ResourceLocation, PresentationPosition> layout = new LinkedHashMap<>();
            clones.forEach((id, clone) -> {
                DisplayInfo display = clone.getDisplay();
                if (display != null) {
                    layout.put(id, new PresentationPosition(display.getX(), display.getY()));
                }
            });
            result.put(root.getId(), java.util.Collections.unmodifiableMap(layout));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Advancement clonePresentation(Advancement original, Advancement parent,
                                                  Map<ResourceLocation, Advancement> clones,
                                                  Map<ResourceLocation, List<Advancement>> children) {
        DisplayInfo source = original.getDisplay();
        DisplayInfo display = source == null ? null : new DisplayInfo(source.getIcon(), source.getTitle(),
                source.getDescription(), source.getBackground(), source.getFrame(), source.shouldShowToast(),
                source.shouldAnnounceChat(), source.isHidden());
        Advancement clone = new Advancement(original.getId(), parent, display, original.getRewards(),
                original.getCriteria(), original.getRequirements(), original.sendsTelemetryEvent());
        clones.put(original.getId(), clone);
        for (Advancement child : children.getOrDefault(original.getId(), List.of())) {
            clonePresentation(child, clone, clones, children);
        }
        return clone;
    }

    private static Map<ResourceLocation, PresentationPosition> flatten(
            Map<ResourceLocation, Map<ResourceLocation, PresentationPosition>> layouts) {
        Map<ResourceLocation, PresentationPosition> result = new LinkedHashMap<>();
        layouts.values().forEach(result::putAll);
        return java.util.Collections.unmodifiableMap(result);
    }

    public record PresentationPosition(float x, float y) {
        public int pixelX() { return (int) Math.floor(x * 32.0); }
        public int pixelY() { return (int) Math.floor(y * 27.0); }
    }

    /** Full replay restores widget progress and fixes constructor-assigned tab indices. */
    public static void refreshOpenScreen(ClientAdvancements client) {
        AicaAdvancementViewport.requestRefresh(client);
    }

    /** Narrow trees are centered; a wide tree opens at its leftmost hierarchy column. */
    public static double initialHorizontal(int min, int max, int viewport) {
        return max - min <= viewport - 24
                ? (viewport - (double) max - min) / 2.0 : 12.0 - min;
    }

    /** Keep the root visible even when a tall taxonomy cannot fit in one viewport. */
    public static double initialVertical(int min, int max, int viewport, double rootY) {
        if (max - min <= viewport - 24) {
            return (viewport - (double) max - min) / 2.0;
        }
        double wanted = viewport / 2.0 - rootY - 13.0;
        return Math.max(viewport - 12.0 - max, Math.min(12.0 - min, wanted));
    }

    /** Plain values for DebugBridge checks; ordering never reads completion state. */
    public static Map<String, Object> diagnostics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", 1);
        result.put("catalog_roots", CatalogHolder.ORDER.keySet().stream()
                .map(ResourceLocation::toString).toList());
        result.put("layout_generation", layoutGeneration);
        result.put("preserved_view_refreshes", AicaAdvancementViewport.preservedRefreshes());
        result.put("layout_node_count", positions.size());
        Map<String, Object> layout = new LinkedHashMap<>();
        positions.forEach((id, point) -> layout.put(id.toString(),
                Map.of("x", point.pixelX(), "y", point.pixelY())));
        result.put("layout", layout);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            ClientAdvancements client = minecraft.getConnection().getAdvancements();
            result.put("roots", rootIds(client).stream().map(ResourceLocation::toString).toList());
            result.put("ordered_roots", orderedRoots(client.getAdvancements().getRoots()).stream()
                    .map(root -> root.getId().toString()).toList());
        }
        result.put("screen", minecraft.screen == null ? "" : minecraft.screen.getClass().getName());
        return result;
    }

    private static LoadedCatalog loadCatalog() {
        try (InputStream stream = AicaAdvancementSupport.class.getResourceAsStream(CATALOG_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled AICA advancement catalog");
            }
            Catalog catalog = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), Catalog.class);
            if (catalog == null || catalog.schema != 1 || catalog.tabs == null
                    || catalog.tabs.isEmpty()) {
                throw new IllegalStateException("Invalid AICA advancement catalog schema");
            }
            Map<ResourceLocation, Integer> result = new LinkedHashMap<>();
            Set<Integer> orders = new HashSet<>();
            List<TabDefinition> tabs = new ArrayList<>(catalog.tabs);
            for (TabDefinition tab : tabs) {
                ResourceLocation id = tab == null || tab.id == null ? null
                        : ResourceLocation.tryParse(tab.id);
                if (id == null || !id.getNamespace().equals("aica") || tab.order == null
                        || tab.order < 0 || !orders.add(tab.order)
                        || result.putIfAbsent(id, tab.order) != null) {
                    throw new IllegalStateException("Invalid or duplicate AICA advancement tab");
                }
            }
            for (int index = 0; index < tabs.size(); index++) {
                if (!orders.contains(index)) {
                    throw new IllegalStateException("AICA advancement tab orders must be contiguous");
                }
            }
            tabs.sort(Comparator.comparingInt(tab -> tab.order));
            result.clear();
            tabs.forEach(tab -> result.put(new ResourceLocation(tab.id), tab.order));
            Map<ResourceLocation, Integer> nodes = new LinkedHashMap<>();
            if (catalog.node_order != null) {
                for (int index = 0; index < catalog.node_order.size(); index++) {
                    String raw = catalog.node_order.get(index);
                    ResourceLocation id = raw == null ? null : ResourceLocation.tryParse(raw);
                    if (id == null || !id.getNamespace().equals("aica")
                            || nodes.putIfAbsent(id, index) != null) {
                        throw new IllegalStateException("Invalid or duplicate AICA advancement node order");
                    }
                }
            }
            return new LoadedCatalog(java.util.Collections.unmodifiableMap(result),
                    java.util.Collections.unmodifiableMap(nodes));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled AICA advancement catalog", exception);
        }
    }

    private static final class CatalogHolder {
        private static final LoadedCatalog CATALOG = loadCatalog();
        private static final Map<ResourceLocation, Integer> ORDER = CATALOG.roots();
        private static final Map<ResourceLocation, Integer> NODE_ORDER = CATALOG.nodes();
    }

    private record LoadedCatalog(Map<ResourceLocation, Integer> roots,
                                 Map<ResourceLocation, Integer> nodes) {}

    private static final class Catalog {
        int schema;
        List<TabDefinition> tabs;
        List<String> node_order;
    }

    private static final class TabDefinition {
        String id;
        Integer order;
    }
}
