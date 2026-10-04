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

    private static final int SIZE = 14;
    private static final int GAP = 3;
    private static final int ROW_MARGIN = 3;
    private static final int INNER_MARGIN = 8;
    private static final int OUTER_MARGIN = 8;
    private static final int GRID_COLS = 3;

    private record VButton(int x, int y, String label, String tooltip, Runnable onClick) {}

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
            int top = Integer.MAX_VALUE, bottom = 0;
            for (VButton b : buttons) { top = Math.min(top, b.y()); bottom = Math.max(bottom, b.y() + SIZE); }
            drawTooltip(graphics, font, hovered.tooltip(), hovered.x(), top, bottom);
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (event.getButton() != 0) return; // left click only

        for (VButton b : buildButtons(screen)) {
            double mx = event.getMouseX(), my = event.getMouseY();
            if (mx < b.x() || mx >= b.x() + SIZE || my < b.y() || my >= b.y() + SIZE) continue;
            if (b.onClick() != null) b.onClick().run();
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

    private static void sortInventory(ItemSortMode mode) {
        SortSettings.sync();
        PacketDistributor.sendToServer(new SortInventoryPayload(mode));
    }

    private static void sortChest(ItemSortMode mode) {
        SortSettings.sync();
        PacketDistributor.sendToServer(new SortOpenContainerPayload(mode));
    }

    private static void sortNearby() {
        SortSettings.sync();
        PacketDistributor.sendToServer(new SortNearbyPayload(NetworkHandler.DEFAULT_RADIUS,
                ChestCatClient.inventorySortMode));
    }

    private static List<VButton> buildButtons(AbstractContainerScreen<?> screen) {
        List<VButton> list = new ArrayList<>();

        if (isPlayerInventoryStyleScreen(screen)) {
            int gridX = screen.getGuiLeft() + screen.getXSize() + OUTER_MARGIN;
            int gridY = screen.getGuiTop() + INNER_MARGIN;
            ItemSortMode mode = ChestCatClient.inventorySortMode;

            String[] labels = {"S", "M", "L", "O", "N", "Q"};
            String[] tooltips = {
                    "Sort inventory: " + mode.getDisplayName() + " / " + SortSettings.summary(),
                    "Next sort mode",
                    "Pick sort mode from a list",
                    "Sort options: rows or columns, start corner, hotbar",
                    "Sort inventory + nearby chests",
                    "Quick-stack matching items into nearby chests"
            };
            Runnable[] actions = {
                    () -> sortInventory(ChestCatClient.inventorySortMode),
                    () -> ChestCatClient.inventorySortMode = ChestCatClient.inventorySortMode.next(),
                    () -> Minecraft.getInstance().setScreen(new SortModePickerScreen(screen,
                            picked -> {
                                ChestCatClient.inventorySortMode = picked;
                                sortInventory(picked);
                            })),
                    () -> Minecraft.getInstance().setScreen(new SortSettingsScreen(screen)),
                    ChestCatScreenButtons::sortNearby,
                    () -> PacketDistributor.sendToServer(new QuickStackPayload())
            };
            addGrid(list, gridX, gridY, labels, tooltips, actions);

        } else if (screen.getMenu() instanceof ChestMenu) {
            int x = Math.max(0, screen.getGuiLeft());
            int y = Math.max(0, screen.getGuiTop() - SIZE - ROW_MARGIN);
            ItemSortMode mode = ChestCatClient.chestSortMode;

            list.add(new VButton(x, y, "S",
                    "Sort chest: " + mode.getDisplayName() + " / " + SortSettings.summary(),
                    () -> sortChest(ChestCatClient.chestSortMode)));
            x += SIZE + GAP;
            list.add(new VButton(x, y, "M", "Next sort mode",
                    () -> ChestCatClient.chestSortMode = ChestCatClient.chestSortMode.next()));
            x += SIZE + GAP;
            list.add(new VButton(x, y, "L", "Pick sort mode from a list",
                    () -> Minecraft.getInstance().setScreen(new SortModePickerScreen(screen,
                            picked -> {
                                ChestCatClient.chestSortMode = picked;
                                sortChest(picked);
                            }))));
            x += SIZE + GAP;
            list.add(new VButton(x, y, "O", "Sort options: rows or columns, start corner",
                    () -> Minecraft.getInstance().setScreen(new SortSettingsScreen(screen))));
            x += SIZE + GAP;
            list.add(new VButton(x, y, "G", "Pull everything from this chest into your inventory",
                    () -> PacketDistributor.sendToServer(new DumpChestPayload())));
            x += SIZE + GAP;
            list.add(new VButton(x, y, "P", "Push your inventory into this chest",
                    () -> PacketDistributor.sendToServer(new SortIntoChestPayload())));
        }

        return list;
    }

    private static void addGrid(List<VButton> list, int gridX, int gridY,
                                String[] labels, String[] tooltips, Runnable[] actions) {
        for (int i = 0; i < labels.length; i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int x = gridX + col * (SIZE + GAP);
            int y = gridY + row * (SIZE + GAP);
            list.add(new VButton(x, y, labels[i], tooltips[i], actions[i]));
        }
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
