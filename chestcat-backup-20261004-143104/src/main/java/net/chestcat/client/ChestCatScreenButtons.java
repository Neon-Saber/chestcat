package net.chestcat.client;

import net.chestcat.ItemSortMode;
import net.chestcat.network.DumpChestPayload;
import net.chestcat.network.NetworkHandler;
import net.chestcat.network.QuickStackPayload;
import net.chestcat.network.SortIntoChestPayload;
import net.chestcat.network.SortInventoryPayload;
import net.chestcat.network.SortNearbyPayload;
import net.chestcat.network.SortOpenContainerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
 * Self-drawn / self-hit-tested single-left-click buttons (bypasses ScreenEvent.Init.Post).
 *
 * Inventory grid (survival inventory or creative "Inventory" tab): 3x2, floating outside
 * the panel's right edge. S sort, M next mode, L mode list, O options, N sort nearby, Q quick stack.
 * Chest row above the panel: S sort, M next mode, L mode list, O options, G dump, P push.
 *
 * Every sort re-sends the layout settings first so client and server always agree.
 * Tooltips clamp to the screen and flip above the button when there's no room below.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class ChestCatScreenButtons {

    private static final int GAP = 3;
    private static final int ROW_MARGIN = 3;
    private static final int INNER_MARGIN = 8;
    private static final int OUTER_MARGIN = 8;
    private static final int GRID_COLS = 3;

    private record VButton(int x, int y, String label, String tooltip, Runnable onClick) {}

    private record Def(String label, String tooltip, Runnable action) {}

    // Ctrl+drag moves the whole panel; the offset is saved with the rest of the client prefs.
    private static boolean dragging = false;
    private static double grabMouseX, grabMouseY;
    private static int grabOffsetX, grabOffsetY;

    /** Button edge length: the base size scaled by the panel-scale setting. */
    private static int size() {
        return Math.max(8, Math.round(ClientUi.buttonSize * ClientUi.panelScale / 100.0F));
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        List<VButton> buttons = buildButtons(screen);
        if (buttons.isEmpty()) return;
        ClientUi.currentMenuId = ChestCatActions.isInventoryStyle(screen) ? "" : net.chestcat.MenuSorting.menuId(screen.getMenu());

        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

        VButton hovered = null;
        for (VButton b : buttons) {
            boolean over = mx >= b.x() && mx < b.x() + size() && my >= b.y() && my < b.y() + size();
            if (over) hovered = b;
            drawButton(graphics, font, b, over && ClientUi.hoverEffects);
        }

        if (hovered != null) {
            int top = Integer.MAX_VALUE, bottom = 0;
            for (VButton b : buttons) { top = Math.min(top, b.y()); bottom = Math.max(bottom, b.y() + size()); }
            drawTooltip(graphics, font, hovered.tooltip(), hovered.x(), top, bottom);
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (event.getButton() != 0) return; // left click only

        for (VButton b : buildButtons(screen)) {
            double mx = event.getMouseX(), my = event.getMouseY();
            if (mx < b.x() || mx >= b.x() + size() || my < b.y() || my >= b.y() + size()) continue;
            if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                dragging = true;
                grabMouseX = mx;
                grabMouseY = my;
                grabOffsetX = ClientUi.offsetX;
                grabOffsetY = ClientUi.offsetY;
            } else if (b.onClick() != null) {
                b.onClick().run();
            }
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!dragging) return;
        ClientUi.offsetX = grabOffsetX + (int) Math.round(event.getMouseX() - grabMouseX);
        ClientUi.offsetY = grabOffsetY + (int) Math.round(event.getMouseY() - grabMouseY);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (dragging && event.getButton() == 0) {
            dragging = false;
            event.setCanceled(true);
        }
    }

    private static List<VButton> buildButtons(AbstractContainerScreen<?> screen) {
        boolean inventoryStyle = ChestCatActions.isInventoryStyle(screen);
        List<Def> defs;
        if (inventoryStyle) {
            defs = inventoryDefs(screen);
        } else if (ChestCatActions.isStorageScreen(screen)) {
            defs = chestDefs(screen);
        } else {
            return List.of();
        }
        return layout(screen, defs, inventoryStyle);
    }

    private static String modeTooltip(ItemSortMode mode) {
        String base = mode.getDisplayName() + " / " + SortSettings.summary();
        if (mode == ItemSortMode.CUSTOM) {
            base += ". Chain: " + net.chestcat.SortChain.parse(SortSettings.chain).describe();
        }
        return base;
    }

    private static List<Def> inventoryDefs(AbstractContainerScreen<?> screen) {
        List<Def> defs = new ArrayList<>();
        defs.add(new Def("S", "Sort inventory: " + modeTooltip(ChestCatClient.inventorySortMode),
                () -> ChestCatActions.sortInventory(ChestCatClient.inventorySortMode, false)));
        defs.add(new Def("M", "Choose how to sort (pick from a list - it sorts right away)",
                () -> Minecraft.getInstance().setScreen(new SortModePickerScreen(screen, picked -> {
                    ChestCatClient.inventorySortMode = picked;
                    ChestCatActions.sortInventory(picked, false);
                }))));
        defs.add(new Def("O", "Settings (favorites, presets, hotbar...). Ctrl+drag any button to move this panel.",
                () -> Minecraft.getInstance().setScreen(new SimpleSettingsScreen(screen))));
        defs.add(new Def("N", "Sort inventory + nearby chests", ChestCatActions::sortNearby));
        defs.add(new Def("Q", "Quick-stack matching items into nearby chests",
                ChestCatActions::quickStack));
        return defs;
    }

    private static List<Def> chestDefs(AbstractContainerScreen<?> screen) {
        List<Def> defs = new ArrayList<>();
        defs.add(new Def("S", "Sort chest: " + modeTooltip(ChestCatClient.chestSortMode),
                () -> ChestCatActions.sortContainer(ChestCatClient.chestSortMode, false)));
        defs.add(new Def("M", "Choose how to sort (pick from a list - it sorts right away)",
                () -> Minecraft.getInstance().setScreen(new SortModePickerScreen(screen, picked -> {
                    ChestCatClient.chestSortMode = picked;
                    ChestCatActions.sortContainer(picked, false);
                }))));
        defs.add(new Def("O", "Settings (favorites, presets, hotbar...). Ctrl+drag any button to move this panel.",
                () -> Minecraft.getInstance().setScreen(new SimpleSettingsScreen(screen))));
        defs.add(new Def("G", "Pull everything from this chest into your inventory",
                () -> PacketDistributor.sendToServer(new DumpChestPayload())));
        defs.add(new Def("P", "Push your inventory into this chest",
                () -> PacketDistributor.sendToServer(new SortIntoChestPayload())));
        return defs;
    }

    /**
     * Places the buttons for the chosen panel position. DEFAULT keeps the classic spots (a 3-wide grid beside
     * inventory screens, a single row above chests); the other presets move the panel above, below, left or right
     * of the GUI. The saved Ctrl+drag offset is added on top, then the whole panel is kept on screen.
     */
    private static List<VButton> layout(AbstractContainerScreen<?> screen, List<Def> defs, boolean inventoryStyle) {
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int s = size();

        ClientUi.GuiPreset placement = ClientUi.preset;
        if (placement == ClientUi.GuiPreset.DEFAULT) {
            placement = inventoryStyle ? ClientUi.GuiPreset.RIGHT : ClientUi.GuiPreset.ABOVE;
        }

        boolean grid;
        int originX;
        int originY;
        int gridWidth = GRID_COLS * (s + GAP) - GAP;
        switch (placement) {
            case LEFT -> {
                grid = true;
                originX = screen.getGuiLeft() - OUTER_MARGIN - gridWidth;
                originY = screen.getGuiTop() + INNER_MARGIN;
            }
            case ABOVE -> {
                grid = false;
                originX = screen.getGuiLeft();
                originY = screen.getGuiTop() - s - ROW_MARGIN;
            }
            case BELOW -> {
                grid = false;
                originX = screen.getGuiLeft();
                originY = screen.getGuiTop() + screen.getYSize() + ROW_MARGIN;
            }
            default -> { // RIGHT
                grid = true;
                originX = screen.getGuiLeft() + screen.getXSize() + OUTER_MARGIN;
                originY = screen.getGuiTop() + INNER_MARGIN;
            }
        }
        originX += ClientUi.offsetX;
        originY += ClientUi.offsetY;

        int n = defs.size();
        int panelW = grid ? gridWidth : n * (s + GAP) - GAP;
        int panelH = grid ? ((n + GRID_COLS - 1) / GRID_COLS) * (s + GAP) - GAP : s;
        originX = Math.max(0, Math.min(originX, screenW - panelW));
        originY = Math.max(0, Math.min(originY, screenH - panelH));

        List<VButton> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int x = grid ? originX + (i % GRID_COLS) * (s + GAP) : originX + i * (s + GAP);
            int y = grid ? originY + (i / GRID_COLS) * (s + GAP) : originY;
            Def d = defs.get(i);
            list.add(new VButton(x, y, d.label(), d.tooltip(), d.action()));
        }
        return list;
    }

    private static void drawButton(GuiGraphics graphics, Font font, VButton b, boolean hovered) {
        int x = b.x(), y = b.y();
        int bg = hovered ? 0xF03A3F4B : 0xE0242730;
        int accent = hovered ? 0xFF6FB4FF : 0xFF4A4E58;

        fillRounded(graphics, x, y + 1, size(), size(), 0x40000000);
        fillRounded(graphics, x, y, size(), size(), bg);
        graphics.fill(x + 1, y, x + size() - 1, y + 1, accent);
        graphics.fill(x + 1, y + size() - 1, x + size() - 1, y + size(), accent);
        graphics.fill(x, y + 1, x + 1, y + size() - 1, accent);
        graphics.fill(x + size() - 1, y + 1, x + size(), y + size() - 1, accent);
        float ts = Math.max(0.6F, size() / 14.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(x + size() / 2.0F, y + size() / 2.0F, 0.0F);
        graphics.pose().scale(ts, ts, 1.0F);
        graphics.drawCenteredString(font, b.label(), 0, -4, 0xFFEDEDED);
        graphics.pose().popPose();
    }

    private static void fillRounded(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x + 1, y, x + w - 1, y + h, color);
        graphics.fill(x, y + 1, x + w, y + h - 1, color);
    }

    private static void drawTooltip(GuiGraphics graphics, Font font, String tooltip,
                                    int x, int blockTop, int blockBottom) {
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int maxW = Math.max(60, Math.min(170, screenW / 2));
        java.util.List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(net.minecraft.network.chat.Component.literal(tooltip), maxW);
        if (lines.isEmpty()) return;

        int w = 0;
        for (net.minecraft.util.FormattedCharSequence line : lines) w = Math.max(w, font.width(line));
        int h = lines.size() * 10;

        // Sit below the whole button block so it never covers a neighbouring button.
        int tx = Math.max(2, Math.min(x, screenW - w - 6));
        int ty = blockBottom + 5;
        if (ty + h + 1 > screenH) {
            ty = blockTop - 5 - h; // no room below: go above the block instead
        }
        if (ty < 2) ty = 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F); // draw on top of the buttons, like vanilla tooltips
        graphics.fill(tx - 3, ty - 2, tx + w + 3, ty + h + 1, 0xF0181A20);
        graphics.fill(tx - 3, ty - 2, tx + w + 3, ty - 1, 0xFF3A3F4B);
        int ly = ty;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawString(font, line, tx, ly, 0xFFEDEDED);
            ly += 10;
        }
        graphics.pose().popPose();
    }
}
