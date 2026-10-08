package net.chestcat.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * ChestCat's hotkeys (the favorite, open-menu and assign-category keys live in {@link ChestCatClient}).
 *
 * Rules that keep them out of your way:
 *  - In a screen they only work in your inventory and in storage containers (chests, barrels, shulker boxes,
 *    hoppers...). Crafting tables, furnaces, anvils, enchanting tables, villager trades and every other
 *    workstation ignore them completely.
 *  - They are handled AFTER the screen has had its chance, so a key you type into a search or rename box
 *    (creative search, anvil name) is never stolen, and they are switched off entirely while the crafting
 *    recipe book is open, so typing a recipe search can never sort, open settings or favorite anything.
 *  - Ctrl / Alt combinations are left alone, so they never clash with other mods' shortcuts.
 *  - Defaults are keys vanilla doesn't use. Rarely needed actions are unbound until you pick a key.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public final class ChestCatKeys {

    private ChestCatKeys() {}

    private static final String CATEGORY = "key.categories.chestcat";
    private static final int UNBOUND = InputConstants.UNKNOWN.getValue();

    /** G: sort what you're looking at - the open chest, or your inventory. Shift+G inside a chest sorts your inventory instead. */
    public static final KeyMapping SORT_KEY = new KeyMapping(
            "key.chestcat.sort", InputConstants.Type.KEYSYM, InputConstants.KEY_G, CATEGORY);
    /** B: sort your inventory and every chest nearby. */
    public static final KeyMapping SORT_NEARBY_KEY = new KeyMapping(
            "key.chestcat.sort_nearby_chests", InputConstants.Type.KEYSYM, InputConstants.KEY_B, CATEGORY);
    /** X: send matching items from your inventory into nearby chests. */
    public static final KeyMapping QUICK_STACK_KEY = new KeyMapping(
            "key.chestcat.quick_stack_items", InputConstants.Type.KEYSYM, InputConstants.KEY_X, CATEGORY);
    /** O: the settings screen. */
    public static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping(
            "key.chestcat.open_settings", InputConstants.Type.KEYSYM, InputConstants.KEY_O, CATEGORY);
    // Unbound by default - bind them in Controls > ChestCat if you want them.
    public static final KeyMapping CYCLE_MODE_KEY = new KeyMapping(
            "key.chestcat.next_sort_mode", InputConstants.Type.KEYSYM, UNBOUND, CATEGORY);
    public static final KeyMapping NEXT_PRESET_KEY = new KeyMapping(
            "key.chestcat.next_sort_preset", InputConstants.Type.KEYSYM, UNBOUND, CATEGORY);
    public static final KeyMapping FAVORITES_FILTER_KEY = new KeyMapping(
            "key.chestcat.toggle_favorites_filter", InputConstants.Type.KEYSYM, UNBOUND, CATEGORY);

    public static final List<KeyMapping> ALL = List.of(
            SORT_KEY, SORT_NEARBY_KEY, QUICK_STACK_KEY, OPEN_SETTINGS_KEY,
            CYCLE_MODE_KEY, NEXT_PRESET_KEY, FAVORITES_FILTER_KEY);

    private static WeakReference<Screen> lastAutoSorted = new WeakReference<>(null);

    // ------------------------------------------------------ one-click keybinds

    /** The ChestCat actions that get a key when the player asks for "comfortable keys". */
    private static KeyMapping[] rebindable() {
        return new KeyMapping[]{SORT_KEY, SORT_NEARBY_KEY, QUICK_STACK_KEY, OPEN_SETTINGS_KEY, ChestCatClient.FAVORITE_KEY};
    }

    /** Preferred keys for each action above, in order: letters close to WASD that vanilla doesn't use. */
    private static final int[][] PREFERRED = {
            {InputConstants.KEY_G, InputConstants.KEY_R, InputConstants.KEY_H, InputConstants.KEY_Y, InputConstants.KEY_U},   // sort
            {InputConstants.KEY_B, InputConstants.KEY_N, InputConstants.KEY_H, InputConstants.KEY_J, InputConstants.KEY_K},   // sort nearby
            {InputConstants.KEY_X, InputConstants.KEY_M, InputConstants.KEY_J, InputConstants.KEY_K, InputConstants.KEY_U},   // quick stack
            {InputConstants.KEY_O, InputConstants.KEY_K, InputConstants.KEY_J, InputConstants.KEY_U, InputConstants.KEY_Y},   // settings
            {InputConstants.KEY_Z, InputConstants.KEY_Y, InputConstants.KEY_U, InputConstants.KEY_J, InputConstants.KEY_K},   // favorite
    };

    /**
     * Gives every main ChestCat action a comfortable key that nothing else uses: each action takes its first
     * preferred key that no other control (vanilla or from another mod) already has, falling back to any free
     * letter. Saves the controls file. @return a one-line summary of what was assigned.
     */
    public static String applyComfortableKeys() {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping[] targets = rebindable();
        java.util.Set<KeyMapping> mine = new java.util.HashSet<>(List.of(targets));
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (KeyMapping km : mc.options.keyMappings) {
            if (!mine.contains(km) && !km.isUnbound()) taken.add(km.getKey().getName());
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < targets.length; i++) {
            int code = pickFree(PREFERRED[i], taken);
            KeyMapping km = targets[i];
            if (code < 0) {
                out.append(shortName(km)).append(": no free key; ");
                continue;
            }
            InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(code);
            taken.add(key.getName());
            mc.options.setKey(km, key);
            out.append(shortName(km)).append(": ").append(key.getDisplayName().getString()).append("; ");
        }
        KeyMapping.resetMapping();
        mc.options.save();
        return out.toString().strip();
    }

    /** Puts the main ChestCat keys back to their defaults. */
    public static String resetKeysToDefaults() {
        Minecraft mc = Minecraft.getInstance();
        for (KeyMapping km : rebindable()) mc.options.setKey(km, km.getDefaultKey());
        KeyMapping.resetMapping();
        mc.options.save();
        return "Keys reset to ChestCat's defaults.";
    }

    private static int pickFree(int[] preferred, java.util.Set<String> taken) {
        for (int code : preferred) {
            if (!taken.contains(InputConstants.Type.KEYSYM.getOrCreate(code).getName())) return code;
        }
        for (int code = InputConstants.KEY_A; code <= InputConstants.KEY_Z; code++) {
            if (!taken.contains(InputConstants.Type.KEYSYM.getOrCreate(code).getName())) return code;
        }
        return -1;
    }

    private static String shortName(KeyMapping km) {
        if (km == SORT_KEY) return "Sort";
        if (km == SORT_NEARBY_KEY) return "Nearby";
        if (km == QUICK_STACK_KEY) return "Quick stack";
        if (km == OPEN_SETTINGS_KEY) return "Settings";
        return "Favorite";
    }

    // ------------------------------------------------------------ the gate

    /**
     * True only for the screens ChestCat hotkeys belong in: the player inventory (survival, or the creative
     * "Inventory" tab) and storage containers. Everything else - crafting, smelting, anvils, trading... - is out.
     */
    public static boolean hotkeysAllowed(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return false;
        if (!(ChestCatActions.isInventoryStyle(container) || ChestCatActions.isStorageScreen(container))) return false;
        // A text box that has focus always wins (belt and braces - such keys are normally consumed before this point).
        if (screen.getFocused() instanceof EditBox box && box.isFocused()) return false;
        // While the crafting recipe book is open its search box takes your typing, so no letter may trigger anything.
        // (The panel buttons still work; close the recipe book to use the hotkeys.)
        if (screen instanceof net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener recipes
                && recipes.getRecipeBookComponent().isVisible()) {
            return false;
        }
        return !Screen.hasControlDown() && !Screen.hasAltDown();
    }

    // ------------------------------------------------------------- in a screen

    @SubscribeEvent
    public static void onKeyInScreen(ScreenEvent.KeyPressed.Post event) {
        Screen current = event.getScreen();
        if (!hotkeysAllowed(current)) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) current;

        int key = event.getKeyCode();
        int scan = event.getScanCode();
        boolean inventory = ChestCatActions.isInventoryStyle(screen);
        boolean storage = ChestCatActions.isStorageScreen(screen);

        if (SORT_KEY.matches(key, scan)) {
            // In a chest G sorts the chest; hold Shift to sort your own inventory instead.
            if (storage && !Screen.hasShiftDown()) {
                ChestCatActions.sortContainer(ChestCatClient.chestSortMode, false);
            } else {
                ChestCatActions.sortInventory(ChestCatClient.inventorySortMode, false);
            }
        } else if (SORT_NEARBY_KEY.matches(key, scan) && inventory) {
            ChestCatActions.sortNearby();
        } else if (QUICK_STACK_KEY.matches(key, scan) && inventory) {
            ChestCatActions.quickStack();
        } else if (CYCLE_MODE_KEY.matches(key, scan)) {
            ChestCatActions.cycleMode(storage);
        } else if (NEXT_PRESET_KEY.matches(key, scan)) {
            ChestCatActions.cyclePreset();
        } else if (OPEN_SETTINGS_KEY.matches(key, scan)) {
            ChestCatActions.openSettings(screen);
        } else if (FAVORITES_FILTER_KEY.matches(key, scan)) {
            ChestCatActions.toggleFavoritesFilter();
        } else {
            return;
        }
        event.setCanceled(true);
    }

    // ---------------------------------------------------------------- in world

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        autoSortOnOpen(mc);

        if (mc.screen != null) return;

        while (SORT_KEY.consumeClick()) ChestCatActions.sortInventory(ChestCatClient.inventorySortMode, false);
        while (SORT_NEARBY_KEY.consumeClick()) ChestCatActions.sortNearby();
        while (QUICK_STACK_KEY.consumeClick()) ChestCatActions.quickStack();
        while (CYCLE_MODE_KEY.consumeClick()) ChestCatActions.cycleMode(false);
        while (NEXT_PRESET_KEY.consumeClick()) ChestCatActions.cyclePreset();
        while (OPEN_SETTINGS_KEY.consumeClick()) ChestCatActions.openSettings(null);
        while (FAVORITES_FILTER_KEY.consumeClick()) ChestCatActions.toggleFavoritesFilter();
    }

    /**
     * Auto-sort: the first tick a new container screen is showing, sort whatever the player asked for. Only
     * inventory and storage screens count - a crafting table or furnace is never auto-sorted. The server does the
     * sorting from its own copy of the container, so it doesn't matter that the screen has just opened.
     */
    private static void autoSortOnOpen(Minecraft mc) {
        if (ClientUi.autoSort == ClientUi.AutoSort.MANUAL) return;
        Screen current = mc.screen;
        if (!(current instanceof AbstractContainerScreen<?> screen)) return;
        if (current instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) return;
        if (lastAutoSorted.get() == current) return;
        lastAutoSorted = new WeakReference<>(current);

        boolean storage = ChestCatActions.isStorageScreen(screen);
        if (ClientUi.autoSort.containers() && storage) {
            ChestCatActions.sortContainer(ChestCatClient.chestSortMode, true);
        }
        if (ClientUi.autoSort.inventory() && (storage || ChestCatActions.isInventoryStyle(screen))) {
            ChestCatActions.sortInventory(ChestCatClient.inventorySortMode, true);
        }
    }
}
