package net.chestcat.client;

import net.chestcat.ItemSortMode;
import net.chestcat.network.NetworkHandler;
import net.chestcat.network.SortInventoryPayload;
import net.chestcat.network.SortNearbyPayload;
import net.chestcat.network.SortOpenContainerPayload;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Small sort button injected into the top-right of the player inventory and
 * any chest/barrel screen. Left-click: sort now. Right-click: cycle mode.
 * Shift+Left-click on the inventory button: quick-stack/sort nearby storage too.
 */
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class ChestCatScreenButtons {

    private static final int SIZE = 14;
    private static final int MARGIN = 4;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen inv) {
            int x = inv.getGuiLeft() + inv.getXSize() - SIZE - MARGIN;
            int y = inv.getGuiTop() + MARGIN;
            addButton(event, x, y,
                    () -> ChestCatClient.inventorySortMode,
                    mode -> ChestCatClient.inventorySortMode = mode,
                    mode -> PacketDistributor.sendToServer(new SortInventoryPayload(mode)),
                    mode -> PacketDistributor.sendToServer(new SortNearbyPayload(NetworkHandler.DEFAULT_RADIUS, mode)));
        } else if (event.getScreen() instanceof AbstractContainerScreen<?> acs
                && acs.getMenu() instanceof ChestMenu) {
            int x = acs.getGuiLeft() + acs.getXSize() - SIZE - MARGIN;
            int y = acs.getGuiTop() + MARGIN;
            addButton(event, x, y,
                    () -> ChestCatClient.chestSortMode,
                    mode -> ChestCatClient.chestSortMode = mode,
                    mode -> PacketDistributor.sendToServer(new SortOpenContainerPayload(mode)),
                    null);
        }
    }

    private interface ModeGetter { ItemSortMode get(); }
    private interface ModeSetter { void set(ItemSortMode mode); }
    private interface SortAction { void run(ItemSortMode mode); }

    private static void addButton(ScreenEvent.Init.Post event, int x, int y,
                                   ModeGetter getter, ModeSetter setter,
                                   SortAction sortAction, SortAction quickStackAction) {
        Button button = new Button(x, y, SIZE, SIZE, Component.literal("S"),
                b -> sortAction.run(getter.get()), supplier -> supplier.get()) {
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
                if (!this.isMouseOver(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, mouseButton);

                if (mouseButton == 1) {
                    setter.set(getter.get().next());
                    updateTooltip(this, getter.get(), quickStackAction != null);
                    return true;
                }
                if (mouseButton == 0 && quickStackAction != null && Screen.hasShiftDown()) {
                    quickStackAction.run(getter.get());
                    return true;
                }
                return super.mouseClicked(mouseX, mouseY, mouseButton);
            }
        };
        updateTooltip(button, getter.get(), quickStackAction != null);
        event.addListener(button);
    }

    private static void updateTooltip(Button button, ItemSortMode mode, boolean hasQuickStack) {
        String text = "Sort mode: " + mode.getDisplayName()
                + "\nLeft-click: sort  |  Right-click: change mode";
        if (hasQuickStack) {
            text += "\nShift+Left-click: sort nearby storage too";
        }
        button.setTooltip(Tooltip.create(Component.literal(text)));
    }
}