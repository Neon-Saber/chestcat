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
 * (hotbar, main, armor, offhand) on any screen showing those slots. Toggle
 * via middle-click on the slot, or left-click the gold-block badge in its
 * top-left corner. The badge only appears - and can only be clicked - when
 * the slot actually has an item in it. Locked slots are skipped by sorting,
 * quick-stack, and dump/pull.
 */
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class SlotLockHandler {

    public static final Set<Integer> lockedMirror = new HashSet<>();
    private static final int BADGE_SIZE = 8;
    private static final ResourceLocation GOLD_BLOCK_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/gold_block.png");

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
            if (!slot.hasItem()) continue; // nothing to favorite in an empty slot

            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            boolean locked = lockedMirror.contains(slot.getSlotIndex());

            if (locked) {
                graphics.fill(x, y, x + 16, y + 16, 0x40FFD700);
            }

            // Polished little button: dark frame, gold-block texture inside,
            // dimmed with a translucent overlay when not currently favorited
            // (so it still reads as "clickable" without looking active).
            graphics.fill(x - 1, y - 1, x + BADGE_SIZE + 1, y + BADGE_SIZE + 1, 0xFF1A1A1A);
            graphics.blit(GOLD_BLOCK_TEXTURE, x, y, 0, 0, BADGE_SIZE, BADGE_SIZE, 16, 16);
            if (!locked) {
                graphics.fill(x, y, x + BADGE_SIZE, y + BADGE_SIZE, 0xA0000000);
            }
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