package net.chestcat.client;

import net.chestcat.network.ToggleSlotLockPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

/**
 * Favorites/locks a slot belonging to the player's own Inventory container.
 * Toggle via middle-click, or left-click the small dot badge in the slot's
 * top-left corner (only present/clickable when the slot has an item).
 * @OnlyIn(Dist.CLIENT) since this renders GUI and touches client-only
 * classes (GuiGraphics, AbstractContainerScreen) - must never load on a
 * dedicated server's classpath.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class SlotLockHandler {

    public static final Set<Integer> lockedMirror = new HashSet<>();
    private static final int BADGE_SIZE = 4;

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        Slot hovered = findHoveredInventorySlot(screen, event.getMouseX(), event.getMouseY());
        if (hovered == null || !hovered.hasItem()) return;

        int index = hovered.getSlotIndex();

        boolean middleClick = event.getButton() == 2;
        boolean badgeClick = event.getButton() == 0 && isOverBadge(screen, hovered, event.getMouseX(), event.getMouseY());
        if (!middleClick && !badgeClick) return;

        if (lockedMirror.contains(index)) {
            lockedMirror.remove(index);
        } else {
            lockedMirror.add(index);
        }
        PacketDistributor.sendToServer(new ToggleSlotLockPayload(index));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        GuiGraphics graphics = event.getGuiGraphics();

        for (Slot slot : screen.getMenu().slots) {
            if (!(slot.container instanceof Inventory)) continue;
            if (!slot.hasItem()) continue;

            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            boolean locked = lockedMirror.contains(slot.getSlotIndex());

            if (locked) {
                graphics.fill(x, y, x + 16, y + 16, 0x30FFD24A);
            }
            drawDot(graphics, x, y, locked);
        }
    }

    private static void drawDot(GuiGraphics graphics, int x, int y, boolean locked) {
        if (locked) {
            graphics.fill(x, y, x + BADGE_SIZE, y + BADGE_SIZE, 0xFF6B4E00);
            graphics.fill(x + 1, y, x + BADGE_SIZE - 1, y + BADGE_SIZE, 0xFFFFD24A);
            graphics.fill(x, y + 1, x + BADGE_SIZE, y + BADGE_SIZE - 1, 0xFFFFD24A);
        } else {
            graphics.fill(x, y, x + BADGE_SIZE, y + 1, 0x55FFFFFF);
            graphics.fill(x, y + BADGE_SIZE - 1, x + BADGE_SIZE, y + BADGE_SIZE, 0x55FFFFFF);
            graphics.fill(x, y, x + 1, y + BADGE_SIZE, 0x55FFFFFF);
            graphics.fill(x + BADGE_SIZE - 1, y, x + BADGE_SIZE, y + BADGE_SIZE, 0x55FFFFFF);
        }
    }

    private static boolean isOverBadge(AbstractContainerScreen<?> screen, Slot slot, double mouseX, double mouseY) {
        if (!slot.hasItem()) return false;
        int x = screen.getGuiLeft() + slot.x;
        int y = screen.getGuiTop() + slot.y;
        return mouseX >= x - 1 && mouseX < x + BADGE_SIZE + 1 && mouseY >= y - 1 && mouseY < y + BADGE_SIZE + 1;
    }

    private static Slot findHoveredInventorySlot(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        for (Slot slot : screen.getMenu().slots) {
            if (!(slot.container instanceof Inventory)) continue;
            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }
}