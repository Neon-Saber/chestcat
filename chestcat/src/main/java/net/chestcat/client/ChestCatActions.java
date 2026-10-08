package net.chestcat.client;

import net.chestcat.ItemSortMode;
import net.chestcat.MenuSorting;
import net.chestcat.network.NetworkHandler;
import net.chestcat.network.QuickStackPayload;
import net.chestcat.network.SortInventoryPayload;
import net.chestcat.network.SortNearbyPayload;
import net.chestcat.network.SortOpenContainerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Every ChestCat action in one place, so the button panel, the hotkeys, the auto-sort and the in-world keys
 * all do exactly the same thing. Each sort re-sends the settings first so client and server always agree.
 */
@OnlyIn(Dist.CLIENT)
public final class ChestCatActions {

    private ChestCatActions() {}

    // Whether a menu counts as sortable storage never changes for a given menu, and the check touches every
    // slot, so remember the answer instead of redoing it every frame.
    private static final Map<AbstractContainerMenu, Boolean> STORAGE_CACHE = new WeakHashMap<>();

    /** The survival inventory, or the creative screen's "Inventory" tab. */
    public static boolean isInventoryStyle(AbstractContainerScreen<?> screen) {
        if (screen.getMenu() instanceof InventoryMenu && !(screen instanceof CreativeModeInventoryScreen)) return true;
        if (screen instanceof CreativeModeInventoryScreen cmis) {
            CreativeModeTab tab = cmis.selectedTab;
            return tab != null && tab.getType() == CreativeModeTab.Type.INVENTORY;
        }
        return false;
    }

    /** A chest-style or generic storage container (shulker box, hopper, modded storage...), never the creative menu. */
    public static boolean isStorageScreen(AbstractContainerScreen<?> screen) {
        if (screen instanceof CreativeModeInventoryScreen) return false;
        AbstractContainerMenu menu = screen.getMenu();
        if (menu instanceof ChestMenu) return true;
        if (menu instanceof InventoryMenu) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return STORAGE_CACHE.computeIfAbsent(menu, m -> MenuSorting.isStorageMenu(m, mc.player));
    }

    public static void sortInventory(ItemSortMode mode, boolean quiet) {
        SortSettings.sync();
        PacketDistributor.sendToServer(new SortInventoryPayload(mode, quiet));
    }

    public static void sortContainer(ItemSortMode mode, boolean quiet) {
        SortSettings.sync();
        PacketDistributor.sendToServer(new SortOpenContainerPayload(mode, quiet));
    }

    public static void sortNearby() {
        SortSettings.sync();
        PacketDistributor.sendToServer(new SortNearbyPayload(NetworkHandler.DEFAULT_RADIUS,
                ChestCatClient.inventorySortMode));
    }

    public static void quickStack() {
        SortSettings.sync();
        PacketDistributor.sendToServer(new QuickStackPayload());
    }

    /** Switches to the next sort mode for whichever side (inventory or container) the screen is about. */
    public static void cycleMode(boolean container) {
        if (container) {
            ChestCatClient.chestSortMode = ChestCatClient.chestSortMode.next();
            actionBar("Chest sort mode: " + ChestCatClient.chestSortMode.getDisplayName());
        } else {
            ChestCatClient.inventorySortMode = ChestCatClient.inventorySortMode.next();
            actionBar("Inventory sort mode: " + ChestCatClient.inventorySortMode.getDisplayName());
        }
    }

    public static void toggleFavoritesFilter() {
        ClientUi.favoritesFilter = !ClientUi.favoritesFilter;
        actionBar(ClientUi.favoritesFilter
                ? "Favorites filter ON - other items are dimmed"
                : "Favorites filter OFF");
        SlotLockHandler.playFeedback(ClientUi.favoritesFilter);
    }

    public static void openSettings(Screen parent) {
        Minecraft.getInstance().setScreen(new ChestCatHomeScreen(parent));
    }

    /** Applies the next built-in preset. */
    public static void cyclePreset() {
        SortPresets.Preset p = SortPresets.cycle();
        actionBar("ChestCat preset: " + p.name());
    }

    public static void actionBar(String text, net.minecraft.ChatFormatting color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text).withStyle(color), true);
    }

    public static void actionBar(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), true);
    }
}
