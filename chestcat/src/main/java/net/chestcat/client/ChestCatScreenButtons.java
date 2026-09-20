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
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Self-drawn/self-hit-tested button row (bypasses ScreenEvent.Init.Post).
 *
 * Inventory-style row anchored OUTSIDE the panel, floating in the open
 * space to the right of it, top-aligned with the panel
 * (guiLeft + getXSize() + OUTER_MARGIN, guiTop + INNER_MARGIN).
 * getXSize()/getGuiLeft()/getGuiTop() are public NeoForge patches on
 * AbstractContainerScreen - no AT needed for these.
 *
 * "Is this a player-inventory-style screen" covers two cases:
 *  1. Survival inventory - menu is InventoryMenu
 *  2. Creative mode's "Inventory" tab specifically, via the AT-widened
 *     `selectedTab` field on CreativeModeInventoryScreen (no public
 *     accessor exists for this in 1.21.1 - see accesstransformer.cfg).
 *
 * Chest row stays anchored ABOVE the panel, unchanged.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class ChestCatScreenButtons {

    private static final int SIZE = 14;
    private static final int GAP = 3;
    private static final int ROW_MARGIN = 3;
    private static final int INNER_MARGIN = 8;
    private static final int OUTER_MARGIN = 8;

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
        int rowBottom = buttons.get(0).y() + SIZE;
        for (VButton b : buttons) {
            boolean over = mx >= b.x() && mx < b.x() + SIZE && my >= b.y() && my < b.y() + SIZE;
            if (over) hovered = b;
            drawButton(graphics, font, b, over);
        }
        if (hovered != null) {
            drawTooltip(graphics, font, hovered.tooltip(), hovered.x(), rowBottom + 4);
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

    private static boolean isPlayerInventoryStyleScreen(AbstractContainerScreen<?> screen) {
        if (screen.getMenu() instanceof InventoryMenu) return true;
        if (screen instanceof CreativeModeInventoryScreen cmis) {
            CreativeModeTab tab = cmis.selectedTab;
            return tab != null && tab.getType() == CreativeModeTab.Type.INVENTORY;
        }
        return false;
    }

    private static List<VButton> buildButtons(AbstractContainerScreen<?> screen) {
        List<VButton> list = new ArrayList<>();

        if (isPlayerInventoryStyleScreen(screen)) {
            // Float just outside the panel's right edge, top-aligned with it.
            int x = screen.getGuiLeft() + screen.getXSize() + OUTER_MARGIN;
            int y = screen.getGuiTop() + INNER_MARGIN;
            ItemSortMode mode = ChestCatClient.inventorySortMode;

            list.add(new VButton(x, y, "S",
                    "Sort: " + mode.getDisplayName()
                            + "  |  Left: sort  |  Right: change mode  |  Shift+Left: sort nearby too",
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
                    "Sort: " + mode.getDisplayName() + "  |  Left: sort  |  Right: change mode",
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
        int bg = hovered ? 0xF03A3F4B : 0xE0242730;
        int accent = hovered ? 0xFF6FB4FF : 0xFF4A4E58;

        fillRounded(graphics, x, y + 1, SIZE, SIZE, 0x40000000);
        fillRounded(graphics, x, y, SIZE, SIZE, bg);

        graphics.fill(x + 1, y, x + SIZE - 1, y + 1, accent);
        graphics.fill(x + 1, y + SIZE - 1, x + SIZE - 1, y + SIZE, accent);
        graphics.fill(x, y + 1, x + 1, y + SIZE - 1, accent);
        graphics.fill(x + SIZE - 1, y + 1, x + SIZE, y + SIZE - 1, accent);

        graphics.drawCenteredString(font, b.label(), x + SIZE / 2, y + (SIZE - 8) / 2, 0xFFEDEDED);
    }

    private static void fillRounded(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x + 1, y, x + w - 1, y + h, color);
        graphics.fill(x, y + 1, x + w, y + h - 1, color);
    }

    private static void drawTooltip(GuiGraphics graphics, Font font, String tooltip, int x, int y) {
        int w = font.width(tooltip);
        graphics.fill(x - 3, y - 2, x + w + 3, y + 11, 0xF0181A20);
        graphics.fill(x - 3, y - 2, x + w + 3, y - 1, 0xFF3A3F4B);
        graphics.drawString(font, tooltip, x, y, 0xFFEDEDED);
    }
}