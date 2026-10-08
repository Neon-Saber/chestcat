package net.chestcat.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.chestcat.EmptySlotMode;
import net.chestcat.FavoriteMode;
import net.chestcat.GroupingConfig;
import net.chestcat.ItemCategory;
import net.chestcat.ItemSortMode;
import net.chestcat.SortChain;
import net.chestcat.SortLayout;
import net.chestcat.SortMore;
import net.chestcat.TiebreakPriority;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Saves every client-side ChestCat preference to config/chestcat/client.json and loads it on startup,
 * so nothing resets when Minecraft is closed. Instead of hooking every button, the current state is
 * compared to the last saved state about once a second and written only when something changed
 * (and once more when leaving a world).
 */
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public final class ClientPrefs {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_INTERVAL_TICKS = 20;

    private static String lastSaved = "";
    private static int timer = 0;
    private static boolean loaded = false;
    /** Set on world join; the next tick pushes settings to the server so every sort path (keybind, C menu, commands) uses them. */
    private static boolean pendingLoginSync = false;
    /** Set on world join; the next tick shows the update log if this version hasn't been shown yet. */
    private static boolean pendingUpdateLog = false;
    /** The last changelog version the player was shown (saved, so the popup appears once per update). */
    static String lastSeenVersion = "";

    private ClientPrefs() {}

    /** Everything that gets written. Field defaults are what a missing key falls back to. */
    private static final class Data {
        int version = 1;
        String lastSeenVersion = "";

        // sorting
        String layout = SortLayout.ROWS.name();
        boolean reverse = false;
        boolean includeHotbar = true;
        boolean groupArmorBySlot = true;
        String tiebreak = TiebreakPriority.TYPE_FIRST.name();
        boolean floatEnchantedFirst = false;
        String chain = SortChain.DEFAULT_STRING;
        String favoriteMode = FavoriteMode.PIN.name();
        boolean mergeStacks = true;
        List<String> exclusions = new ArrayList<>();
        // empty slots / hotbar extras / ordering (second overhaul pass)
        String emptySlots = EmptySlotMode.LAST.name();
        boolean hotbarSeparate = false;
        boolean hotbarFavoritesStay = false;
        boolean preserveSelected = false;
        String categoryOrder = "";
        String modOrder = "";
        boolean alphaDescending = false;
        String inventoryMode = ItemSortMode.CREATIVE_ORDER.name();
        String chestMode = ItemSortMode.CREATIVE_ORDER.name();
        String nearbyMode = ItemSortMode.ITEM_TYPE.name();

        // category splitting
        boolean groupingSaved = false;
        boolean splitOres = false;
        boolean splitWood = false;
        boolean splitStone = false;
        List<String> disabledCategories = new ArrayList<>();
        List<String> mergedSubKeys = new ArrayList<>();

        // interface
        boolean floatingIcons = true;
        String guiPreset = ClientUi.GuiPreset.DEFAULT.name();
        int guiOffsetX = 0;
        int guiOffsetY = 0;
        int buttonSize = 14;
        int panelScale = 100;
        boolean hoverEffects = true;
        String autoSort = ClientUi.AutoSort.MANUAL.name();
        boolean showFavorites = true;
        String favoriteStyle = ClientUi.FavoriteStyle.STAR_AND_OUTLINE.name();
        String starPosition = ClientUi.StarPosition.TOP_RIGHT.name();
        boolean highContrast = false;
        boolean animations = true;
        boolean sounds = true;
        int soundVolume = 60;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("chestcat").resolve("client.json");
    }

    private static Data capture() {
        Data d = new Data();
        d.layout = SortSettings.layout.name();
        d.reverse = SortSettings.reverse;
        d.includeHotbar = SortSettings.includeHotbar;
        d.groupArmorBySlot = SortSettings.groupArmorBySlot;
        d.tiebreak = SortSettings.tiebreakPriority.name();
        d.floatEnchantedFirst = SortSettings.floatEnchantedFirst;
        d.chain = SortSettings.chain;
        d.favoriteMode = SortSettings.favoriteMode.name();
        d.mergeStacks = SortSettings.mergeStacks;
        d.exclusions = new ArrayList<>(SortSettings.exclusions);
        d.lastSeenVersion = lastSeenVersion;
        SortMore m = SortSettings.more;
        d.emptySlots = m.emptySlots().name();
        d.hotbarSeparate = m.hotbarSeparate();
        d.hotbarFavoritesStay = m.hotbarFavoritesStay();
        d.preserveSelected = m.preserveSelected();
        d.categoryOrder = m.categoryOrder();
        d.modOrder = m.modOrder();
        d.alphaDescending = m.alphaDescending();
        d.inventoryMode = ChestCatClient.inventorySortMode.name();
        d.chestMode = ChestCatClient.chestSortMode.name();
        d.nearbyMode = ChestCatClient.nearbySortMode.name();

        GroupingConfig.Settings g = GroupingSettings.current;
        d.groupingSaved = true;
        d.splitOres = g.splitOres();
        d.splitWood = g.splitWood();
        d.splitStone = g.splitStone();
        for (ItemCategory c : g.disabledCategories()) d.disabledCategories.add(c.name());
        d.mergedSubKeys = new ArrayList<>(g.mergedSubKeys());

        d.floatingIcons = ChestCatIndicatorRenderer.enabled;
        d.guiPreset = ClientUi.preset.name();
        d.guiOffsetX = ClientUi.offsetX;
        d.guiOffsetY = ClientUi.offsetY;
        d.buttonSize = ClientUi.buttonSize;
        d.panelScale = ClientUi.panelScale;
        d.hoverEffects = ClientUi.hoverEffects;
        d.autoSort = ClientUi.autoSort.name();
        d.showFavorites = ClientUi.showFavorites;
        d.favoriteStyle = ClientUi.favoriteStyle.name();
        d.starPosition = ClientUi.starPosition.name();
        d.highContrast = ClientUi.highContrast;
        d.animations = ClientUi.animations;
        d.sounds = ClientUi.sounds;
        d.soundVolume = ClientUi.soundVolume;
        return d;
    }

    private static void apply(Data d) {
        applySort(d);
        applyUi(d);
    }

    /** JSON snapshot of just the sorting settings (modes, chain, favorites, hotbar, order, grouping) for client profiles. */
    static String snapshotSort() {
        return GSON.toJson(capture());
    }

    /** Applies the sorting part of a snapshot made by {@link #snapshotSort()}; the interface settings stay as they are. */
    static boolean applySortSnapshot(String json) {
        try {
            Data d = GSON.fromJson(json, Data.class);
            if (d == null) return false;
            applySort(d);
            SortSettings.sync();
            return true;
        } catch (RuntimeException e) {
            LOGGER.warn("ChestCat: could not read a saved client profile", e);
            return false;
        }
    }

    private static void applySort(Data d) {
        SortSettings.layout = enumOr(d.layout, SortLayout.class, SortLayout.ROWS);
        SortSettings.reverse = d.reverse;
        SortSettings.includeHotbar = d.includeHotbar;
        SortSettings.groupArmorBySlot = d.groupArmorBySlot;
        SortSettings.tiebreakPriority = enumOr(d.tiebreak, TiebreakPriority.class, TiebreakPriority.TYPE_FIRST);
        SortSettings.floatEnchantedFirst = d.floatEnchantedFirst;
        SortSettings.chain = SortChain.parse(d.chain).serialize();
        SortSettings.favoriteMode = enumOr(d.favoriteMode, FavoriteMode.class, FavoriteMode.PIN);
        SortSettings.mergeStacks = d.mergeStacks;
        SortSettings.exclusions = d.exclusions == null ? new ArrayList<>() : new ArrayList<>(d.exclusions);
        SortSettings.more = new SortMore(
                enumOr(d.emptySlots, EmptySlotMode.class, EmptySlotMode.LAST),
                d.hotbarSeparate, d.hotbarFavoritesStay, d.preserveSelected,
                SortMore.cleanCategoryOrder(d.categoryOrder), SortMore.cleanModOrder(d.modOrder), d.alphaDescending);
        ChestCatClient.inventorySortMode = enumOr(d.inventoryMode, ItemSortMode.class, ItemSortMode.CREATIVE_ORDER);
        ChestCatClient.chestSortMode = enumOr(d.chestMode, ItemSortMode.class, ItemSortMode.CREATIVE_ORDER);
        ChestCatClient.nearbySortMode = enumOr(d.nearbyMode, ItemSortMode.class, ItemSortMode.ITEM_TYPE);

        if (d.groupingSaved) {
            Set<ItemCategory> disabled = EnumSet.noneOf(ItemCategory.class);
            if (d.disabledCategories != null) {
                for (String name : d.disabledCategories) {
                    try {
                        disabled.add(ItemCategory.valueOf(name));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            Set<String> merged = d.mergedSubKeys == null ? new HashSet<>() : new HashSet<>(d.mergedSubKeys);
            GroupingSettings.current = new GroupingConfig.Settings(
                    d.splitOres, d.splitWood, d.splitStone, disabled, merged);
        }

    }

    private static void applyUi(Data d) {
        lastSeenVersion = d.lastSeenVersion == null ? "" : d.lastSeenVersion;
        ChestCatIndicatorRenderer.enabled = d.floatingIcons;
        ClientUi.preset = enumOr(d.guiPreset, ClientUi.GuiPreset.class, ClientUi.GuiPreset.DEFAULT);
        ClientUi.offsetX = d.guiOffsetX;
        ClientUi.offsetY = d.guiOffsetY;
        ClientUi.buttonSize = Math.max(10, Math.min(20, d.buttonSize));
        ClientUi.panelScale = Math.max(75, Math.min(150, d.panelScale == 0 ? 100 : d.panelScale));
        ClientUi.hoverEffects = d.hoverEffects;
        ClientUi.autoSort = enumOr(d.autoSort, ClientUi.AutoSort.class, ClientUi.AutoSort.MANUAL);
        ClientUi.showFavorites = d.showFavorites;
        ClientUi.favoriteStyle = enumOr(d.favoriteStyle, ClientUi.FavoriteStyle.class, ClientUi.FavoriteStyle.STAR_AND_OUTLINE);
        ClientUi.starPosition = enumOr(d.starPosition, ClientUi.StarPosition.class, ClientUi.StarPosition.TOP_RIGHT);
        ClientUi.highContrast = d.highContrast;
        ClientUi.animations = d.animations;
        ClientUi.sounds = d.sounds;
        ClientUi.soundVolume = Math.max(0, Math.min(100, d.soundVolume));
    }

    private static <E extends Enum<E>> E enumOr(String name, Class<E> type, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Loads config/chestcat/client.json if it exists. Call once at client startup. */
    public static void load() {
        Path path = file();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                Data d = GSON.fromJson(reader, Data.class);
                if (d != null) apply(d);
            } catch (Exception e) {
                LOGGER.warn("ChestCat: could not read {} - using defaults", path, e);
            }
        }
        loaded = true;
        lastSaved = GSON.toJson(capture());
    }

    /** Restores every ChestCat client setting to its default and pushes the sort settings to the server. */
    public static void resetAll() {
        SortSettings.resetDefaults();
        ClientUi.resetDefaults();
        ChestCatClient.inventorySortMode = ItemSortMode.CREATIVE_ORDER;
        ChestCatClient.chestSortMode = ItemSortMode.CREATIVE_ORDER;
        ChestCatClient.nearbySortMode = ItemSortMode.ITEM_TYPE;
        ChestCatIndicatorRenderer.enabled = true;
        GroupingSettings.current = GroupingConfig.Settings.DEFAULT;
        SortSettings.sync();
    }

    /** Writes the file now if anything changed since the last write. */
    public static void saveIfChanged() {
        if (!loaded) return;
        String json = GSON.toJson(capture());
        if (json.equals(lastSaved)) return;
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, json);
            lastSaved = json;
        } catch (IOException e) {
            LOGGER.warn("ChestCat: could not write {}", path, e);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (pendingLoginSync && mc.player != null) {
            pendingLoginSync = false;
            SortSettings.sync();
        }
        // Wait for a moment with no other screen open (the world has to be fully loaded first).
        if (pendingUpdateLog && mc.player != null && mc.screen == null) {
            pendingUpdateLog = false;
            UpdateLog.showIfNew();
        }
        if (++timer < CHECK_INTERVAL_TICKS) return;
        timer = 0;
        saveIfChanged();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        pendingLoginSync = true;
        pendingUpdateLog = true;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        saveIfChanged();
    }
}
