package net.chestcat.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Rebindable hotkeys for the rest of ChestCat's actions (the original sort / favorite / menu keys live in
 * {@link ChestCatClient}), plus the auto-sort that runs when a container screen opens.
 *
 * Every key works both inside inventory / container screens and in the world, except the ones that only make
 * sense with a container open. Typing in a search box never triggers a hotkey.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public final class ChestCatKeys {

    private ChestCatKeys() {}

    private static final String CATEGORY = "key.categories.chestcat";

    public static final KeyMapping SORT_CONTAINER_KEY = new KeyMapping(
            "key.chestcat.sort_container", InputConstants.Type.KEYSYM, InputConstants.KEY_PERIOD, CATEGORY);
    public static final KeyMapping SORT_NEARBY_KEY = new KeyMapping(
            "key.chestcat.sort_nearby", InputConstants.Type.KEYSYM, InputConstants.KEY_SEMICOLON, CATEGORY);
    public static final KeyMapping QUICK_STACK_KEY = new KeyMapping(
            "key.chestcat.quick_stack", InputConstants.Type.KEYSYM, InputConstants.KEY_BACKSLASH, CATEGORY);
    public static final KeyMapping CYCLE_MODE_KEY = new KeyMapping(
            "key.chestcat.cycle_mode", InputConstants.Type.KEYSYM, InputConstants.KEY_M, CATEGORY);
    public static final KeyMapping NEXT_PRESET_KEY = new KeyMapping(
            "key.chestcat.next_preset", InputConstants.Type.KEYSYM, InputConstants.KEY_H, CATEGORY);
    public static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping(
            "key.chestcat.open_settings", InputConstants.Type.KEYSYM, InputConstants.KEY_O, CATEGORY);
    public static final KeyMapping FAVORITES_FILTER_KEY = new KeyMapping(
            "key.chestcat.favorites_filter", InputConstants.Type.KEYSYM, InputConstants.KEY_G, CATEGORY);

    public static final List<KeyMapping> ALL = List.of(
            SORT_CONTAINER_KEY, SORT_NEARBY_KEY, QUICK_STACK_KEY, CYCLE_MODE_KEY,
            NEXT_PRESET_KEY, OPEN_SETTINGS_KEY, FAVORITES_FILTER_KEY);

    private static WeakReference<Screen> lastAutoSorted = new WeakReference<>(null);

    // ------------------------------------------------------------- in a screen

    @SubscribeEvent
    public static void onKeyInScreen(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (screen.getFocused() instanceof EditBox box && box.isFocused()) return; // typing in a search field
        if (screen instanceof CreativeModeInventoryScreen && !ChestCatActions.isInventoryStyle(screen)) return;

        int key = event.getKeyCode();
        int scan = event.getScanCode();
        boolean inventory = ChestCatActions.isInventoryStyle(screen);
        boolean storage = ChestCatActions.isStorageScreen(screen);

        if (SORT_CONTAINER_KEY.matches(key, scan) && storage) {
            ChestCatActions.sortContainer(ChestCatClient.chestSortMode, false);
        } else if (ChestCatClient.SORT_INVENTORY_KEY.matches(key, scan)) {
            ChestCatActions.sortInventory(ChestCatClient.inventorySortMode, false);
        } else if (SORT_NEARBY_KEY.matches(key, scan) && inventory) {
            ChestCatActions.sortNearby();
        } else if (QUICK_STACK_KEY.matches(key, scan) && inventory) {
            ChestCatActions.quickStack();
        } else if (CYCLE_MODE_KEY.matches(key, scan) && (inventory || storage)) {
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

        while (SORT_NEARBY_KEY.consumeClick()) ChestCatActions.sortNearby();
        while (QUICK_STACK_KEY.consumeClick()) ChestCatActions.quickStack();
        while (CYCLE_MODE_KEY.consumeClick()) ChestCatActions.cycleMode(false);
        while (NEXT_PRESET_KEY.consumeClick()) ChestCatActions.cyclePreset();
        while (OPEN_SETTINGS_KEY.consumeClick()) ChestCatActions.openSettings(null);
        while (FAVORITES_FILTER_KEY.consumeClick()) ChestCatActions.toggleFavoritesFilter();
        // SORT_CONTAINER_KEY needs an open container, so it does nothing in the world.
        while (SORT_CONTAINER_KEY.consumeClick()) { /* consumed so it never queues up */ }
    }

    /**
     * Auto-sort: the first tick a new container screen is showing, sort whatever the player asked for. The
     * server does the sorting from its own copy of the container, so it doesn't matter that the screen has just
     * opened. The screen is remembered (weakly), so each opening sorts exactly once.
     */
    private static void autoSortOnOpen(Minecraft mc) {
        if (ClientUi.autoSort == ClientUi.AutoSort.MANUAL) return;
        Screen current = mc.screen;
        if (!(current instanceof AbstractContainerScreen<?> screen) || current instanceof CreativeModeInventoryScreen) {
            return;
        }
        if (lastAutoSorted.get() == current) return;
        lastAutoSorted = new WeakReference<>(current);

        if (ClientUi.autoSort.containers() && ChestCatActions.isStorageScreen(screen)) {
            ChestCatActions.sortContainer(ChestCatClient.chestSortMode, true);
        }
        if (ClientUi.autoSort.inventory()) {
            ChestCatActions.sortInventory(ChestCatClient.inventorySortMode, true);
        }
    }
}
