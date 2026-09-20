package net.chestcat.client;

import net.chestcat.network.ToggleSlotLockPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

/**
 * Favorites/locks a slot belonging to the player's own Inventory container
 * (hotbar 0-8, main storage 9-35, armor 36-39, offhand 40) on ANY screen
 * that shows those slots - the plain inventory screen AND any chest/
 * container screen, since your hotbar+inventory row is always visible
 * at the bottom of those too. Toggle via middle-click on the slot, or
 * left-click the small star icon in its top-left corner. Locked slots
 * are skipped by sorting, quick-stack, and dump/pull.
 */
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class SlotLockHandler {

    public static final Set<Integer> lockedMirror = new HashSet<>();
    private static final int STAR_SIZE = 6;

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        Slot hovered = findHoveredInventorySlot(screen, event.getMouseX(), event.getMouseY());
        if (hovered == null) return;

        int index = hovered.getSlotIndex();

        boolean middleClick = event.getButton() == 2;
        boolean starClick = event.getButton() == 0 && isOverStar(screen, hovered, event.getMouseX(), event.getMouseY());
        if (!middleClick && !starClick) return;

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

            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            boolean locked = lockedMirror.contains(slot.getSlotIndex());

            if (locked) {
                graphics.fill(x, y, x + 16, y + 16, 0x55FF0000);
            }
            int starColor = locked ? 0xFFFFD700 : 0x40FFFFFF;
            graphics.fill(x, y, x + STAR_SIZE, y + STAR_SIZE, starColor);
        }
    }

    private static boolean isOverStar(AbstractContainerScreen<?> screen, Slot slot, double mouseX, double mouseY) {
        int x = screen.getGuiLeft() + slot.x;
        int y = screen.getGuiTop() + slot.y;
        return mouseX >= x && mouseX < x + STAR_SIZE && mouseY >= y && mouseY < y + STAR_SIZE;
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