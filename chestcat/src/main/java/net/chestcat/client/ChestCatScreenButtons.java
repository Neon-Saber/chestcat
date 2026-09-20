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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Self-drawn/self-hit-tested button row - deliberately does NOT use
 * ScreenEvent.Init.Post + addListener(). That event only fires from inside
 * Screen.init(); if the screen that actually renders on E doesn't call
 * super.init() (e.g. it's wrapped/replaced by something else in the
 * modpack), the event silently never fires and nothing added that way ever
 * shows up - which matches exactly what's been happening. Render.Post and
 * MouseButtonPressed.Pre don't have that dependency (SlotLockHandler proves
 * they fire fine on this same screen), so drawing and hit-testing
 * ourselves sidesteps the problem entirely regardless of what wraps it.
 *
 * Inventory screen (menu is InventoryMenu): S (sort) M (pick mode) C (row/col fill toggle) Q (quick-stack into nearby chests)
 * Chest screen (menu is ChestMenu):          S (sort) M (pick mode) G (pull chest -> inventory) P (push inventory -> chest)
 */
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class ChestCatScreenButtons {

    private static final int SIZE = 14;
    private static final int GAP = 2;
    private static final int ROW_MARGIN = 2;

    private record VButton(int x, int y, String label, String tooltip,
                            Runnable onLeft, Runnable onRight, Runnable onShiftLeft) {}

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        List<VButton> buttons = buildButtons(screen);
        if (buttons.isEmpty()) return;

        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

        VButton hovered = null;
        for (VButton b : buttons) {
            boolean over = mx >= b.x() && mx < b.x() + SIZE && my >= b.y() && my < b.y() + SIZE;
            if (over) hovered = b;
            drawButton(graphics, font, b, over);
        }
        if (hovered != null) {
            drawTooltip(graphics, font, hovered.tooltip(), (int) mx, (int) my);
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        for (VButton b : buildButtons(screen)) {
            double mx = event.getMouseX(), my = event.getMouseY();
            if (mx < b.x() || mx >= b.x() + SIZE || my < b.y() || my >= b.y() + SIZE) continue;

            if (event.getButton() == 1 && b.onRight() != null) {
                b.onRight().run();
            } else if (event.getButton() == 0 && Screen.hasShiftDown() && b.onShiftLeft() != null) {
                b.onShiftLeft().run();
            } else if (event.getButton() == 0 && b.onLeft() != null) {
                b.onLeft().run();
            }
            event.setCanceled(true);
            return;
        }
    }

    private static List<VButton> buildButtons(AbstractContainerScreen<?> screen) {
        List<VButton> list = new ArrayList<>();

        if (screen.getMenu() instanceof InventoryMenu) {
            int x = Math.max(0, screen.getGuiLeft());
            int y = Math.max(0, screen.getGuiTop() - SIZE - ROW_MARGIN);
            ItemSortMode mode = ChestCatClient.inventorySortMode;

            list.add(new VButton(x, y, "S",
                    "Sort mode: " + mode.getDisplayName()
                            + "\nLeft: sort | Right: change mode\nShift+Left: sort nearby storage too",
                    () -> PacketDistributor.sendToServer(new SortInventoryPayload(ChestCatClient.inventorySortMode)),
                    () -> ChestCatClient.inventorySortMode = ChestCatClient.inventorySortMode.next(),
                    () -> PacketDistributor.sendToServer(new SortNearbyPayload(NetworkHandler.DEFAULT_RADIUS, ChestCatClient.inventorySortMode))));
            x += SIZE + GAP;

            list.add(new VButton(x, y, "M", "Pick sort mode from a list",
                    () -> Minecraft.getInstance().setScreen(new SortModePickerScreen(screen,
                            picked -> {
                                ChestCatClient.inventorySortMode = picked;
                                PacketDistributor.sendToServer(new SortInventoryPayload(picked));
                            })),
                    null, null));
            x += SIZE + GAP;

            list.add(new VButton(x, y, "C", "Toggle sort fill: rows vs columns",
                    () -> PacketDistributor.sendToServer(new ToggleColumnFillPayload()), null, null));
            x += SIZE + GAP;

            list.add(new VButton(x, y, "Q", "Quick-stack matching items into nearby chests",
                    () -> PacketDistributor.sendToServer(new QuickStackPayload()), null, null));

        } else if (screen.getMenu() instanceof ChestMenu) {
            int x = Math.max(0, screen.getGuiLeft());
            int y = Math.max(0, screen.getGuiTop() - SIZE - ROW_MARGIN);
            ItemSortMode mode = ChestCatClient.chestSortMode;

            list.add(new VButton(x, y, "S",
                    "Sort mode: " + mode.getDisplayName() + "\nLeft: sort | Right: change mode",
                    () -> PacketDistributor.sendToServer(new SortOpenContainerPayload(ChestCatClient.chestSortMode)),
                    () -> ChestCatClient.chestSortMode = ChestCatClient.chestSortMode.next(),
                    null));
            x += SIZE + GAP;

            list.add(new VButton(x, y, "M", "Pick sort mode from a list",
                    () -> Minecraft.getInstance().setScreen(new SortModePickerScreen(screen,
                            picked -> {
                                ChestCatClient.chestSortMode = picked;
                                PacketDistributor.sendToServer(new SortOpenContainerPayload(picked));
                            })),
                    null, null));
            x += SIZE + GAP;

            list.add(new VButton(x, y, "G", "Pull everything from this chest into your inventory",
                    () -> PacketDistributor.sendToServer(new DumpChestPayload()), null, null));
            x += SIZE + GAP;

            list.add(new VButton(x, y, "P", "Push your inventory into this chest",
                    () -> PacketDistributor.sendToServer(new SortIntoChestPayload()), null, null));
        }
        return list;
    }

    private static void drawButton(GuiGraphics graphics, Font font, VButton b, boolean hovered) {
        int x = b.x(), y = b.y();
        int body = hovered ? 0xFFA0A0A0 : 0xFF8B8B8B;
        graphics.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0xFF000000);
        graphics.fill(x, y, x + SIZE, y + SIZE, body);
        graphics.fill(x, y, x + SIZE, y + 1, 0x60FFFFFF);
        graphics.fill(x, y, x + 1, y + SIZE, 0x60FFFFFF);
        graphics.fill(x, y + SIZE - 1, x + SIZE, y + SIZE, 0x60000000);
        graphics.fill(x + SIZE - 1, y, x + SIZE, y + SIZE, 0x60000000);
        graphics.drawCenteredString(font, b.label(), x + SIZE / 2, y + (SIZE - 8) / 2, 0xFFFFFFFF);
    }

    private static void drawTooltip(GuiGraphics graphics, Font font, String tooltip, int mx, int my) {
        String[] lines = tooltip.split("\n");
        int w = 0;
        for (String l : lines) w = Math.max(w, font.width(l));
        int tx = mx + 10;
        int ty = my - 6;
        graphics.fill(tx - 3, ty - 3, tx + w + 3, ty + lines.length * 10 + 1, 0xF0100010);
        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(font, lines[i], tx, ty + i * 10, 0xFFFFFFFF);
        }
    }
}