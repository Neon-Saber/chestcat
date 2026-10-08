package net.chestcat.client;

import net.chestcat.FavoriteMode;
import net.chestcat.ModInfo;
import net.chestcat.SortCategory;
import net.chestcat.SortMore;
import net.chestcat.client.OptionListScreen.Row;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * All of ChestCat's settings, organised into six small pages behind one hub: Sorting, Inventory, Favorites,
 * Appearance, Controls and Advanced (plus About). Everything uses plain words ("Sort by", "A to Z", "Keep these
 * slots where they are"); the technical options live under Advanced. Every page is just a list of rows.
 */
@OnlyIn(Dist.CLIENT)
public final class SettingsPages {

    private SettingsPages() {}

    private static final int[] BUTTON_SIZES = {12, 14, 16};
    private static final int[] VOLUMES = {0, 25, 50, 75, 100};
    private static final int[] SCALES = {75, 100, 125, 150};

    // ------------------------------------------------------------ plain words

    private static String yesNo(boolean v) {
        return v ? "Yes" : "No";
    }

    private static String onOff(boolean v) {
        return v ? "On" : "Off";
    }

    public static String favoriteWords(FavoriteMode mode) {
        return switch (mode) {
            case PIN -> "Stay where they are";
            case FIRST -> "Go to the front";
            case LAST -> "Go to the back";
            case MARK -> "Just show a star";
            case ONLY -> "Only sort favorites";
        };
    }

    private static String autoWords() {
        return switch (ClientUi.autoSort) {
            case MANUAL -> "Never";
            case CONTAINERS -> "Chests";
            case INVENTORY -> "My inventory";
            case BOTH -> "Chests and inventory";
        };
    }

    private static int nextOf(int[] values, int current) {
        for (int v : values) {
            if (v > current) return v;
        }
        return values[0];
    }

    // -------------------------------------------------------------------- hub

    public static Screen hub(Screen parent) {
        // The hub is the "More options" door: one button per topic, nothing else.
        OptionListScreen[] self = new OptionListScreen[1];
        List<Row> rows = new ArrayList<>();
        rows.add(Row.open("Sorting", () -> sorting(self[0]), "How items are ordered: sort style, your own order, names, category and mod order."));
        rows.add(Row.open("Inventory", () -> inventory(self[0]), "Hotbar, empty slots, auto-sort and locked slots."));
        rows.add(Row.open("Favorites", () -> favorites(self[0]), "What favorites do, how the star looks, and click sounds."));
        rows.add(Row.open("Appearance", () -> appearance(self[0]), "Where the button panel sits, its size, and animations."));
        rows.add(Row.open("Controls", () -> controls(self[0]), "Keyboard shortcuts - or let ChestCat pick comfortable free keys."));
        rows.add(Row.open("Advanced", () -> advanced(self[0]), "Ignore rules, profiles, category splitting and reset. Most players never need this."));
        rows.add(Row.open("About & Compatibility", () -> new AboutScreen(self[0]), "Version, what ChestCat is built for, and a copyable diagnostics report."));
        self[0] = new OptionListScreen(parent, "ChestCat Options", "Pick a topic. Everything saves automatically.", rows,
                null, null, true);
        return self[0];
    }

    // ---------------------------------------------------------------- sorting

    private static Screen sorting(Screen parent) {
        OptionListScreen[] self = new OptionListScreen[1];
        List<Row> rows = new ArrayList<>();
        rows.add(Row.open(() -> "Sort by: " + ChestCatClient.inventorySortMode.getDisplayName(),
                () -> new SortModePickerScreen(self[0], picked -> {
                    ChestCatClient.inventorySortMode = picked;
                    ChestCatClient.chestSortMode = picked;
                    SortSettings.sync();
                }),
                "How items are ordered (by type, name, mod, rarity...). Used for your inventory and chests."));
        rows.add(Row.open("Ready-made styles", () -> new PresetPickerScreen(self[0]),
                "Switch to a ready-made style like Combat, Building or Mining in one click."));
        rows.add(Row.open("Build your own order", () -> new SortChainScreen(self[0]),
                "Pick what to sort by first, then what to sort by next - for example Category, then Mod, then A to Z."));
        rows.add(Row.setting(() -> "Names: " + (SortSettings.more.alphaDescending() ? "Z \u2192 A" : "A \u2192 Z"),
                () -> SortSettings.more = SortSettings.more.withAlphaDescending(!SortSettings.more.alphaDescending()),
                "Which way names are ordered wherever sorting looks at the name."));
        rows.add(Row.setting(() -> "Combine stacks: " + yesNo(SortSettings.mergeStacks),
                () -> SortSettings.mergeStacks = !SortSettings.mergeStacks,
                "Automatically combine matching partial stacks when possible. No means every stack just changes place."));
        rows.add(Row.open("Category order...", () -> categoryOrder(self[0]),
                "Choose which kinds of items come first when sorting by category."));
        rows.add(Row.open("Mod order...", () -> modOrder(self[0]),
                "Choose which mods come first when sorting by mod. All installed mods are found automatically."));
        self[0] = new OptionListScreen(parent, "Sorting", "How your items are organised.", rows,
                () -> "Tip: \"Build your own order\" lets you mix rules, like Category, then Mod, then A \u2192 Z.",
                () -> {
                    SortSettings.more = SortSettings.more.withAlphaDescending(false).withCategoryOrder("").withModOrder("");
                    SortSettings.mergeStacks = true;
                }, false);
        return self[0];
    }

    // -------------------------------------------------------------- inventory

    private static Screen inventory(Screen parent) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.setting(() -> "Sort my hotbar too: " + yesNo(SortSettings.includeHotbar),
                () -> SortSettings.includeHotbar = !SortSettings.includeHotbar,
                "Also organize the items in your hotbar when sorting. No means your hotbar is never rearranged."));
        rows.add(Row.setting(() -> "Hotbar: " + (SortSettings.more.hotbarSeparate() ? "Sorted on its own" : "Sorted with inventory"),
                () -> SortSettings.more = SortSettings.more.withHotbarSeparate(!SortSettings.more.hotbarSeparate()),
                "On its own: items never move between your hotbar and the rest of your inventory. Only matters if you sort your hotbar."));
        rows.add(Row.setting(() -> "Favorites in hotbar: " + (SortSettings.more.hotbarFavoritesStay() ? "Stay put" : "Can move"),
                () -> SortSettings.more = SortSettings.more.withHotbarFavoritesStay(!SortSettings.more.hotbarFavoritesStay()),
                "Stay put: a favorite in your hotbar keeps its exact hotbar slot."));
        rows.add(Row.setting(() -> "Item in my hand: " + (SortSettings.more.preserveSelected() ? "Stays put" : "Can move"),
                () -> SortSettings.more = SortSettings.more.withPreserveSelected(!SortSettings.more.preserveSelected()),
                "Stays put: the hotbar slot you have selected is never moved, so what's in your hand stays there."));
        rows.add(Row.setting(() -> "Empty slots: " + (SortSettings.more.emptySlots() == net.chestcat.EmptySlotMode.LAST ? "At the end" : "At the start"),
                () -> SortSettings.more = SortSettings.more.withEmptySlots(SortSettings.more.emptySlots().next()),
                "Where the empty space ends up after sorting."));
        rows.add(Row.setting(() -> "Sort when I open: " + autoWords(),
                () -> ClientUi.autoSort = ClientUi.autoSort.next(),
                "Sort by itself every time you open a chest or your inventory. Never means only when you press Sort."));
        return page(parent, "Inventory", "Your hotbar, empty slots and locked slots.", rows,
                () -> "Keep these slots where they are: middle-click a slot to lock it (Shift + middle-click locks the whole row). "
                        + "Locked slots are never moved by sorting.",
                () -> {
                    ClientUi.autoSort = ClientUi.AutoSort.MANUAL;
                    SortSettings.includeHotbar = true;
                    SortSettings.more = SortSettings.more.withHotbarSeparate(false).withHotbarFavoritesStay(false)
                            .withPreserveSelected(false).withEmptySlots(net.chestcat.EmptySlotMode.LAST);
                });
    }

    // -------------------------------------------------------------- favorites

    private static Screen favorites(Screen parent) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.setting(() -> "When sorting: " + favoriteWords(SortSettings.favoriteMode),
                () -> SortSettings.favoriteMode = SortSettings.favoriteMode.next(),
                "What sorting does with items you starred. Stay where they are = never moved. Go to the front / back = grouped together. "
                        + "Just show a star = sorted like everything else. Only sort favorites = everything else is left alone."));
        rows.add(Row.setting(() -> "Show stars: " + yesNo(ClientUi.showFavorites),
                () -> ClientUi.showFavorites = !ClientUi.showFavorites,
                "Show the gold star above favorite items."));
        rows.add(Row.setting(() -> "Star style: " + ClientUi.favoriteStyle.getDisplayName(),
                () -> ClientUi.favoriteStyle = ClientUi.favoriteStyle.next(),
                "How favorite items are marked. Every style includes a star, so it never relies on color alone."));
        rows.add(Row.setting(() -> "Star position: " + ClientUi.starPosition.getDisplayName(),
                () -> ClientUi.starPosition = ClientUi.starPosition.next(),
                "Where the star sits on the item slot."));
        rows.add(Row.setting(() -> "Extra visible star: " + onOff(ClientUi.highContrast),
                () -> ClientUi.highContrast = !ClientUi.highContrast,
                "A thicker outline and a dark backdrop behind the star, easier to see."));
        rows.add(Row.setting(() -> "Dim everything else: " + onOff(ClientUi.favoritesFilter),
                () -> ClientUi.favoritesFilter = !ClientUi.favoritesFilter,
                "Dims every item that isn't a favorite so favorites stand out."));
        rows.add(Row.setting(() -> "Click sounds: " + (ClientUi.sounds && ClientUi.soundVolume > 0 ? ClientUi.soundVolume + "%" : "Off"),
                () -> {
                    int cur = ClientUi.sounds ? ClientUi.soundVolume : 0;
                    int next = nextOf(VOLUMES, cur);
                    ClientUi.soundVolume = next;
                    ClientUi.sounds = next > 0;
                    SlotLockHandler.playFeedback(true);
                },
                "Volume of the click when you favorite or lock something."));
        return page(parent, "Favorites", "Mark the items you care about.", rows,
                () -> "To favorite an item: hold Alt and click it, or hover it and press Z. Favorites follow the item, not the slot.",
                () -> {
                    SortSettings.favoriteMode = FavoriteMode.PIN;
                    ClientUi.showFavorites = true;
                    ClientUi.favoriteStyle = ClientUi.FavoriteStyle.STAR_AND_OUTLINE;
                    ClientUi.starPosition = ClientUi.StarPosition.TOP_RIGHT;
                    ClientUi.highContrast = false;
                    ClientUi.favoritesFilter = false;
                });
    }

    // ------------------------------------------------------------- appearance

    private static Screen appearance(Screen parent) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.setting(() -> "Panel position: " + ClientUi.preset.getDisplayName(),
                () -> ClientUi.preset = ClientUi.preset.next(),
                "Where the little button panel appears next to your inventory or chest."));
        rows.add(Row.setting(() -> "Panel size: " + ClientUi.panelScale + "%",
                () -> ClientUi.panelScale = nextOf(SCALES, ClientUi.panelScale),
                "Makes the whole button panel smaller or larger (75% to 150%)."));
        rows.add(Row.setting(() -> "Button size: " + ClientUi.buttonSize,
                () -> {
                    int next = BUTTON_SIZES[0];
                    for (int i = 0; i < BUTTON_SIZES.length; i++) {
                        if (BUTTON_SIZES[i] == ClientUi.buttonSize) {
                            next = BUTTON_SIZES[(i + 1) % BUTTON_SIZES.length];
                            break;
                        }
                    }
                    ClientUi.buttonSize = next;
                },
                "Base size of each panel button."));
        rows.add(Row.setting(() -> "Animations: " + onOff(ClientUi.animations),
                () -> ClientUi.animations = !ClientUi.animations,
                "A gentle pulse on favorite outlines."));
        rows.add(Row.setting(() -> "Highlight on hover: " + onOff(ClientUi.hoverEffects),
                () -> ClientUi.hoverEffects = !ClientUi.hoverEffects,
                "Light up panel buttons when the mouse is over them."));
        rows.add(Row.setting(() -> "Chest icons in the world: " + onOff(ChestCatIndicatorRenderer.enabled),
                () -> ChestCatIndicatorRenderer.enabled = !ChestCatIndicatorRenderer.enabled,
                "Show a small floating icon above chests that have a category."));
        return page(parent, "Appearance", "How ChestCat looks.", rows,
                () -> "Move the panel exactly where you want it: hold Ctrl and drag any panel button.",
                () -> {
                    ClientUi.resetPanelPosition();
                    ClientUi.panelScale = 100;
                    ClientUi.buttonSize = 14;
                    ClientUi.animations = true;
                    ClientUi.hoverEffects = true;
                    ChestCatIndicatorRenderer.enabled = true;
                });
    }

    // --------------------------------------------------------------- controls

    private static Screen controls(Screen parent) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.action("Comfortable keybinds", s -> s.setStatus(ChestCatKeys.applyComfortableKeys()),
                "Gives Sort, Sort Nearby, Quick Stack, Settings and Favorite an easy-to-reach key that nothing else in your game uses."));
        rows.add(Row.action("Default keybinds", s -> s.setStatus(ChestCatKeys.resetKeysToDefaults()),
                "Puts those five keys back to ChestCat's defaults."));
        return page(parent, "Controls", "Keyboard shortcuts.", rows,
                () -> "Now: Sort " + key(ChestCatKeys.SORT_KEY) + "  Nearby " + key(ChestCatKeys.SORT_NEARBY_KEY)
                        + "  Quick stack " + key(ChestCatKeys.QUICK_STACK_KEY) + "  Options " + key(ChestCatKeys.OPEN_SETTINGS_KEY)
                        + "  Favorite " + key(ChestCatClient.FAVORITE_KEY)
                        + ". Change any of them in Options > Controls > Key Binds > ChestCat. Shortcuts only work in your inventory and chests.",
                null);
    }

    private static String key(net.minecraft.client.KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString();
    }

    // --------------------------------------------------------------- advanced

    private static Screen advanced(Screen parent) {
        OptionListScreen[] self = new OptionListScreen[1];
        List<Row> rows = new ArrayList<>();
        rows.add(Row.open("Never sort these...", () -> new ExclusionsScreen(self[0]),
                "Tell ChestCat to leave certain items, mods, categories, tags or container types alone."));
        rows.add(Row.open("Split a category...", () -> new CategorySplitScreen(self[0]),
                "Spread one category over several chests, such as Tools into Pickaxes, Axes and Swords."));
        rows.add(Row.open("Saved styles (this world)", () -> new SortProfilesScreen(self[0]),
                "Styles you saved on this world or server."));
        rows.add(Row.open("My styles (this computer)", () -> new ClientProfilesScreen(self[0]),
                "Styles stored on this computer. They work in every world and server."));
        rows.add(Row.open("What's new", () -> new UpdateLogScreen(self[0], UpdateLog.latest()),
                "See what changed in each ChestCat version."));
        rows.add(Row.action("Reset ALL settings", s -> {
                    ClientPrefs.resetAll();
                    s.setStatus("All ChestCat settings were reset.");
                },
                "Puts every ChestCat setting back to its default. Your favorites and locked slots are kept."));
        self[0] = new OptionListScreen(parent, "Advanced", "For power users. You can ignore this page.", rows, null, null, false);
        return self[0];
    }

    // ---------------------------------------------------------- order screens

    private static Screen categoryOrder(Screen parent) {
        List<ReorderScreen.Item> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String name : SortSettings.more.categoryList()) {
            SortCategory c = SortCategory.byName(name);
            if (c != null && seen.add(c.name())) items.add(new ReorderScreen.Item(c.name(), c.getDisplayName()));
        }
        for (SortCategory c : SortCategory.values()) {
            if (seen.add(c.name())) items.add(new ReorderScreen.Item(c.name(), c.getDisplayName()));
        }
        return new ReorderScreen(parent, "Category Order",
                "Kinds of items higher in the list come first when sorting by category.", items,
                ids -> {
                    SortSettings.more = SortSettings.more.withCategoryOrder(String.join(",", ids));
                    SortSettings.sync();
                },
                () -> {
                    SortSettings.more = SortSettings.more.withCategoryOrder("");
                    SortSettings.sync();
                });
    }

    private static Screen modOrder(Screen parent) {
        // Every mod that adds items, found from the item registry itself - nothing here is a fixed list.
        Set<String> installed = new TreeSet<>();
        BuiltInRegistries.ITEM.keySet().forEach(id -> installed.add(id.getNamespace()));

        List<ReorderScreen.Item> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String id : SortSettings.more.modList()) {
            if (installed.contains(id) && seen.add(id)) items.add(new ReorderScreen.Item(id, ModInfo.displayName(id)));
        }
        List<String> rest = new ArrayList<>();
        for (String id : installed) {
            if (!seen.contains(id)) rest.add(id);
        }
        rest.sort((a, b) -> {
            if (a.equals("minecraft")) return -1;
            if (b.equals("minecraft")) return 1;
            return ModInfo.displayName(a).toLowerCase(Locale.ROOT).compareTo(ModInfo.displayName(b).toLowerCase(Locale.ROOT));
        });
        for (String id : rest) items.add(new ReorderScreen.Item(id, ModInfo.displayName(id)));

        return new ReorderScreen(parent, "Mod Order",
                "Mods higher in the list come first when sorting by mod (the first " + SortMore.MAX_MODS + " are kept).", items,
                ids -> {
                    List<String> kept = ids.size() > SortMore.MAX_MODS ? ids.subList(0, SortMore.MAX_MODS) : ids;
                    SortSettings.more = SortSettings.more.withModOrder(String.join(",", kept));
                    SortSettings.sync();
                },
                () -> {
                    SortSettings.more = SortSettings.more.withModOrder("");
                    SortSettings.sync();
                });
    }

    private static Screen page(Screen parent, String title, String subtitle, List<Row> rows,
                               java.util.function.Supplier<String> hint, Runnable reset) {
        return new OptionListScreen(parent, title, subtitle, rows, hint, reset, false);
    }
}
