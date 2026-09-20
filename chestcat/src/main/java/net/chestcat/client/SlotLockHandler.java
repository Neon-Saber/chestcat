package net.chestcat.client;

import net.chestcat.network.ToggleSlotLockPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
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
 * on any screen showing those slots. Toggle via middle-click on the slot,
 * or left-click the star badge in its top-left corner. Badge only appears
 * - and can only be clicked - when the slot has an item.
 */
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class SlotLockHandler {

    public static final Set<Integer> lockedMirror = new HashSet<>();
    private static final int BADGE_SIZE = 6;
    private static final ResourceLocation STAR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("chestcat", "textures/gui/favorite_star.png");

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
                graphics.fill(x, y, x + 16, y + 16, 0x40FFD700);
            }

            graphics.blit(STAR_TEXTURE, x, y, BADGE_SIZE, BADGE_SIZE, 0f, 0f, 16, 16, 16, 16);
            if (!locked) {
                // dim the icon when not favorited so it reads as "click to favorite"
                graphics.fill(x, y, x + BADGE_SIZE, y + BADGE_SIZE, 0x90000000);
            }
        }
    }

    private static boolean isOverBadge(AbstractContainerScreen<?> screen, Slot slot, double mouseX, double mouseY) {
        if (!slot.hasItem()) return false;
        int x = screen.getGuiLeft() + slot.x;
        int y = screen.getGuiTop() + slot.y;
        return mouseX >= x && mouseX < x + BADGE_SIZE && mouseY >= y && mouseY < y + BADGE_SIZE;
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