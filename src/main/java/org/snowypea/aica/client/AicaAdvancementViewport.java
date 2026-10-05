package org.snowypea.aica.client;

import net.minecraft.advancements.Advancement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/** Retains the rendered root anchor while received nodes change the local tree. */
public final class AicaAdvancementViewport {
    private static ClientAdvancements owner;
    private static final Map<ResourceLocation, Anchor> remembered = new LinkedHashMap<>();
    private static Pending pending;
    private static int preservedRefreshes;

    private AicaAdvancementViewport() {}

    public static void bindClient(ClientAdvancements client) {
        if (owner != client) {
            owner = client;
            remembered.clear();
            pending = null;
        }
    }

    /** Capture before packet callbacks can clear or replace widgets. */
    public static void beforeUpdate(ClientAdvancements client) {
        bindClient(client);
        Screen screen = Minecraft.getInstance().screen;
        if (!supported(screen)) {
            pending = null;
            return;
        }
        if (pending == null || pending.screen != screen || pending.client != client) {
            pending = new Pending(screen, client);
        }
        capture(pending);
    }

    public static void unchangedUpdate() {
        if (pending != null && !pending.queued) {
            pending = null;
        }
    }

    public static void requestRefresh(ClientAdvancements client) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!supported(minecraft.screen)) {
            pending = null;
            return;
        }
        if (pending == null || pending.screen != minecraft.screen || pending.client != client) {
            beforeUpdate(client);
        }
        Pending retained = pending;
        if (retained.queued) {
            return;
        }
        retained.queued = true;
        minecraft.tell(() -> {
            if (pending != retained || minecraft.screen != retained.screen
                    || minecraft.getConnection() == null
                    || minecraft.getConnection().getAdvancements() != retained.client) {
                return;
            }
            // A later reset packet may erase widgets; retain the earliest live
            // anchor for each root, rather than a newer calculated position.
            capture(retained);
            pending = null;
            retained.screen.init(minecraft, retained.screen.width, retained.screen.height);
            ScreenFields fields = SCREENS.get(retained.screen.getClass());
            if (fields.zoom != null && retained.zoom != null) {
                set(fields.zoom, retained.screen, retained.zoom);
            }
            Map<?, ?> tabs = (Map<?, ?>) get(fields.tabs, retained.screen);
            for (Object tab : tabs.values()) {
                Anchor anchor = retained.anchors.get(id(tab));
                if (anchor != null) {
                    restore(tab, anchor);
                    storeBetterScroll(tab);
                }
            }
            if (retained.selected != null) {
                Advancement selected = client.getAdvancements().get(retained.selected);
                if (selected != null && selected.getParent() == null) {
                    client.setSelectedTab(selected, false);
                    Object tab = get(fields.selected, retained.screen);
                    Anchor anchor = tab == null ? null : retained.anchors.get(id(tab));
                    if (anchor != null) {
                        // Selection calls BetterAdvancements.loadScroll, whose
                        // bounds clamp must not replace the retained viewport.
                        restore(tab, anchor);
                        storeBetterScroll(tab);
                    }
                }
            }
            preservedRefreshes++;
        });
    }

    /** BetterAdvancements calls this when leaving or switching a visited tab. */
    public static void rememberTab(Object tab) {
        bindCurrentClient();
        Anchor anchor = anchor(tab);
        if (anchor != null && AicaAdvancementSupport.isCatalogRoot(advancement(tab))) {
            remembered.put(id(tab), anchor);
        }
    }

    /** Also covers direct GUI replacement, which bypasses BetterAdvancements.onClose. */
    public static void rememberScreen(Object candidate) {
        if (!(candidate instanceof Screen screen) || !supported(screen)) {
            return;
        }
        bindCurrentClient();
        ScreenFields fields = SCREENS.get(screen.getClass());
        for (Object tab : ((Map<?, ?>) get(fields.tabs, screen)).values()) {
            rememberTab(tab);
        }
    }

    /** Restore after the upstream history load, using the actual new widget. */
    public static void restoreTab(Object tab) {
        bindCurrentClient();
        Anchor anchor = remembered.get(id(tab));
        if (anchor != null && AicaAdvancementSupport.isCatalogRoot(advancement(tab))) {
            restore(tab, anchor);
        }
    }

    public static int preservedRefreshes() { return preservedRefreshes; }

    private static void bindCurrentClient() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            bindClient(minecraft.getConnection().getAdvancements());
        }
    }

    private static void capture(Pending snapshot) {
        ScreenFields fields = SCREENS.get(snapshot.screen.getClass());
        Map<?, ?> tabs = (Map<?, ?>) get(fields.tabs, snapshot.screen);
        for (Object tab : tabs.values()) {
            Anchor anchor = anchor(tab);
            if (anchor != null) {
                snapshot.anchors.putIfAbsent(id(tab), anchor);
                if (AicaAdvancementSupport.isCatalogRoot(advancement(tab))) {
                    remembered.putIfAbsent(id(tab), anchor);
                }
            }
        }
        Object selected = get(fields.selected, snapshot.screen);
        if (selected != null) {
            snapshot.selected = id(selected);
        }
        if (fields.zoom != null) {
            snapshot.zoom = ((Number) get(fields.zoom, snapshot.screen)).floatValue();
        }
    }

    private static Anchor anchor(Object tab) {
        TabFields fields = TABS.get(tab.getClass());
        if (!(Boolean) get(fields.centered, tab)) {
            return null;
        }
        Object root = get(fields.root, tab);
        WidgetFields widget = WIDGETS.get(root.getClass());
        return new Anchor(((Number) get(fields.scrollX, tab)).doubleValue()
                + ((Number) get(widget.x, root)).intValue(),
                ((Number) get(fields.scrollY, tab)).doubleValue()
                + ((Number) get(widget.y, root)).intValue());
    }

    private static void restore(Object tab, Anchor anchor) {
        TabFields fields = TABS.get(tab.getClass());
        Object root = get(fields.root, tab);
        WidgetFields widget = WIDGETS.get(root.getClass());
        setScroll(fields.scrollX, tab, anchor.x - ((Number) get(widget.x, root)).intValue());
        setScroll(fields.scrollY, tab, anchor.y - ((Number) get(widget.y, root)).intValue());
        set(fields.centered, tab, true);
    }

    private static void setScroll(Field field, Object tab, double value) {
        if (field.getType() == int.class) {
            set(field, tab, (int) Math.round(value));
        } else {
            set(field, tab, value);
        }
    }

    private static void storeBetterScroll(Object tab) {
        if (tab.getClass().getName().equals("betteradvancements.common.gui.BetterAdvancementTab")) {
            try {
                tab.getClass().getMethod("storeScroll").invoke(tab);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot retain BetterAdvancements scroll", exception);
            }
        }
    }

    private static Advancement advancement(Object tab) {
        return (Advancement) get(TABS.get(tab.getClass()).advancement, tab);
    }

    private static ResourceLocation id(Object tab) { return advancement(tab).getId(); }

    private static boolean supported(Screen screen) {
        return screen instanceof AdvancementsScreen || screen != null
                && screen.getClass().getName().equals("betteradvancements.common.gui.BetterAdvancementsScreen");
    }

    private static Field field(Class<?> type, String srg, String official) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (String name : new String[]{srg, official}) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) { }
            }
        }
        throw new IllegalStateException("Missing advancement view field " + type.getName() + "." + official);
    }

    private static Object get(Field field, Object owner) {
        try { return field.get(owner); }
        catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
    }

    private static void set(Field field, Object owner, Object value) {
        try { field.set(owner, value); }
        catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
    }

    private record Anchor(double x, double y) { }
    private record ScreenFields(Field tabs, Field selected, Field zoom) { }
    private record TabFields(Field advancement, Field root, Field scrollX, Field scrollY, Field centered) { }
    private record WidgetFields(Field x, Field y) { }

    private static final ClassValue<ScreenFields> SCREENS = new ClassValue<>() {
        protected ScreenFields computeValue(Class<?> type) {
            boolean better = type.getName().equals("betteradvancements.common.gui.BetterAdvancementsScreen");
            return new ScreenFields(field(type, "f_97335_", "tabs"),
                    field(type, "f_97336_", "selectedTab"), better ? field(type, "zoom", "zoom") : null);
        }
    };
    private static final ClassValue<TabFields> TABS = new ClassValue<>() {
        protected TabFields computeValue(Class<?> type) {
            return new TabFields(field(type, "f_97130_", "advancement"), field(type, "f_97134_", "root"),
                    field(type, "f_97136_", "scrollX"), field(type, "f_97137_", "scrollY"),
                    field(type, "f_97143_", "centered"));
        }
    };
    private static final ClassValue<WidgetFields> WIDGETS = new ClassValue<>() {
        protected WidgetFields computeValue(Class<?> type) {
            return new WidgetFields(field(type, "f_97251_", "x"), field(type, "f_97252_", "y"));
        }
    };

    private static final class Pending {
        final Screen screen;
        final ClientAdvancements client;
        final Map<ResourceLocation, Anchor> anchors = new LinkedHashMap<>();
        ResourceLocation selected;
        Float zoom;
        boolean queued;
        Pending(Screen screen, ClientAdvancements client) { this.screen = screen; this.client = client; }
    }
}
