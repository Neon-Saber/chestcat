package net.chestcat.client;

import net.chestcat.ItemSortMode;
import net.chestcat.network.DumpChestPayload;
import net.chestcat.network.NetworkHandler;
import net.chestcat.network.QuickStackPayload;
import net.chestcat.network.SortIntoChestPayload;
import net.chestcat.network.SortInventoryPayload;
import net.chestcat.network.SortNearbyPayload;
import net.chestcat.network.SortOpenContainerPayload;
import net.chestcat.network.ToggleColumnFillPayload;
import net.minecraft.client.Minecraft;
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
 * Buttons anchored ABOVE the GUI panel, left-to-right from the panel's
 * left edge - can never overlap a slot regardless of container size.
 *
 * Inventory screen: S (sort) M (pick mode) C (row/col fill toggle) Q (quick-stack matching items into nearby chests)
 * Chest screen:      S (sort) M (pick mode) G (pull chest -> inventory) P (push inventory -> chest)
 */
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class ChestCatScreenButtons {

    private static final int SIZE = 14;
    private static final int GAP = 2;
    private static final int ROW_MARGIN = 2;

    private interface ModeGetter { ItemSortMode get(); }
    private interface ModeSetter { void set(ItemSortMode mode); }
    private interface SortAction { void run(ItemSortMode mode); }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen inv) {
            int x = inv.getGuiLeft();
            int y = inv.getGuiTop() - SIZE - ROW_MARGIN;

            x = addSortButton(event, x, y,
                    () -> ChestCatClient.inventorySortMode,
                    mode -> ChestCatClient.inventorySortMode = mode,
                    mode -> PacketDistributor.sendToServer(new SortInventoryPayload(mode)),
                    mode -> PacketDistributor.sendToServer(new SortNearbyPayload(NetworkHandler.DEFAULT_RADIUS, mode)));

            x = addSimpleButton(event, x, y, "M", "Pick sort mode from a list",
                    () -> Minecraft.getInstance().setScreen(new SortModePickerScreen(inv,
                            mode -> {
                                ChestCatClient.inventorySortMode = mode;
                                PacketDistributor.sendToServer(new SortInventoryPayload(mode));
                            })));

            x = addSimpleButton(event, x, y, "C", "Toggle sort fill: rows vs columns",
                    () -> PacketDistributor.sendToServer(new ToggleColumnFillPayload()));

            addSimpleButton(event, x, y, "Q", "Quick-stack matching items into nearby chests",
                    () -> PacketDistributor.sendToServer(new QuickStackPayload()));

        } else if (event.getScreen() instanceof AbstractContainerScreen<?> acs
                && acs.getMenu() instanceof ChestMenu) {
            int x = acs.getGuiLeft();
            int y = acs.getGuiTop() - SIZE - ROW_MARGIN;

            x = addSortButton(event, x, y,
                    () -> ChestCatClient.chestSortMode,
                    mode -> ChestCatClient.chestSortMode = mode,
                    mode -> PacketDistributor.sendToServer(new SortOpenContainerPayload(mode)),
                    null);

            x = addSimpleButton(event, x, y, "M", "Pick sort mode from a list",
                    () -> Minecraft.getInstance().setScreen(new SortModePickerScreen(acs,
                            mode -> {
                                ChestCatClient.chestSortMode = mode;
                                PacketDistributor.sendToServer(new SortOpenContainerPayload(mode));
                            })));

            x = addSimpleButton(event, x, y, "G", "Pull everything from this chest into your inventory",
                    () -> PacketDistributor.sendToServer(new DumpChestPayload()));

            addSimpleButton(event, x, y, "P", "Push your inventory into this chest",
                    () -> PacketDistributor.sendToServer(new SortIntoChestPayload()));
        }
    }

    private static int addSortButton(ScreenEvent.Init.Post event, int x, int y, ModeGetter getter, ModeSetter setter,
                                      SortAction sortAction, SortAction quickStackAction) {
        Button button = new Button(x, y, SIZE, SIZE, Component.literal("S"),
                b -> sortAction.run(getter.get()), supplier -> supplier.get()) {
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
                if (!this.isMouseOver(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, mouseButton);
                if (mouseButton == 1) {
                    setter.set(getter.get().next());
                    updateSortTooltip(this, getter.get(), quickStackAction != null);
                    return true;
                }
                if (mouseButton == 0 && quickStackAction != null && Screen.hasShiftDown()) {
                    quickStackAction.run(getter.get());
                    return true;
                }
                return super.mouseClicked(mouseX, mouseY, mouseButton);
            }
        };
        updateSortTooltip(button, getter.get(), quickStackAction != null);
        event.addListener(button);
        return x + SIZE + GAP;
    }

    private static int addSimpleButton(ScreenEvent.Init.Post event, int x, int y, String label, String tooltip, Runnable action) {
        Button button = Button.builder(Component.literal(label), b -> action.run())
                .bounds(x, y, SIZE, SIZE)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .build();
        event.addListener(button);
        return x + SIZE + GAP;
    }

    private static void updateSortTooltip(Button button, ItemSortMode mode, boolean hasQuickStack) {
        String text = "Sort mode: " + mode.getDisplayName()
                + "\nLeft-click: sort | Right-click: change mode";
        if (hasQuickStack) {
            text += "\nShift+Left-click: sort nearby storage too";
        }
        button.setTooltip(Tooltip.create(Component.literal(text)));
    }
}