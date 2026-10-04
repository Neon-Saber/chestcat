package net.chestcat.client;

import net.chestcat.network.ToggleFavoritePayload;
import net.chestcat.network.ToggleSlotLockPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

/**
 * Favorites and slot locks in every container screen (and the creative inventory).
 *
 * FAVORITES are tied to the ITEM (see FavoriteKey), not the slot: favorite a Diamond Sword once and it
 * stays favorited through sorting, chests and the hotbar. Drawn as a gold star ABOVE the item (and a
 * gold outline), on a high z-layer so it is never buried under the item icon.
 * To avoid accidental clicks the star only reacts after the mouse has rested on it for a moment
 * (hover feedback shows when it is ready); Alt+Click on the slot and the Favorite hotkey (default Z)
 * work too, and a normal click anywhere else on the item behaves exactly like vanilla.
 *
 * SLOT LOCKS (position based, inventory slots only): middle-click toggles one slot, Shift+middle-click
 * toggles the whole row. Locked slots show a padlock. The lock list comes from the server so it stays in sync.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class SlotLockHandler {

    /** Mirror of the server's locked inventory slot indices (0-35). Replaced whenever the server syncs. */
    public static final Set<Integer> lockedMirror = new HashSet<>();

    private static final int STAR_W = 7;
    private static final int STAR_H = 7;
    private static final long DWELL_MS = 140;

    // 7x7 star as horizontal runs: {row, startX, endXExclusive}
    private static final int[][] STAR_RUNS = {
            {0, 3, 4}, {1, 2, 5}, {2, 0, 7}, {3, 1, 6}, {4, 2, 5}, {5, 1, 3}, {5, 4, 6}, {6, 1, 2}, {6, 5, 6}
    };
    // 5x6 padlock
    private static final int[][] LOCK_RUNS = {
            {0, 1, 4}, {1, 1, 2}, {1, 3, 4}, {2, 0, 5}, {3, 0, 5}, {4, 0, 2}, {4, 3, 5}, {5, 0, 5}
    };

    private static int dwellSlot = -1;
    private static long dwellStart = 0L;

    // ---------------------------------------------------------------- geometry

    /** Every container screen, including the creative inventory (where only the player's own slots take part). */
    private static boolean supported(Screen screen) {
        return screen instanceof AbstractContainerScreen<?>;
    }

    /**
     * Whether a slot can carry a favorite star. In the creative screen the item-picker tabs show fake stacks, so
     * only slots backed by the player's real inventory count there; everywhere else every slot counts.
     */
    private static boolean eligible(AbstractContainerScreen<?> screen, Slot slot) {
        return !(screen instanceof CreativeModeInventoryScreen) || slot.container instanceof Inventory;
    }

    /**
     * The slot number the SERVER knows for this stack. Normally it is just the slot's index in the open menu. The
     * creative screen has its own client-only menu, so there the matching slot of the player's real inventory
     * menu is found by the stack itself (the creative inventory tab shows the very same stack objects).
     */
    private static int serverMenuIndex(AbstractContainerScreen<?> screen, Slot slot) {
        if (!(screen instanceof CreativeModeInventoryScreen)) return screen.getMenu().slots.indexOf(slot);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        ItemStack stack = slot.getItem();
        var slots = mc.player.inventoryMenu.slots;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).getItem() == stack) return i;
        }
        return -1;
    }

    private static int slotX(AbstractContainerScreen<?> screen, Slot slot) {
        return screen.getGuiLeft() + slot.x;
    }

    private static int slotY(AbstractContainerScreen<?> screen, Slot slot) {
        return screen.getGuiTop() + slot.y;
    }

    /** Where the star sits on the slot, per the player's star-position setting (default: top-right, rising above the item). */
    private static int starX(int slotX) {
        return switch (ClientUi.starPosition) {
            case TOP_LEFT, BOTTOM_LEFT -> slotX - 1;
            case TOP_CENTER -> slotX + (16 - STAR_W) / 2;
            default -> slotX + 16 - STAR_W + 1;
        };
    }

    private static int starY(int slotY) {
        return ClientUi.starPosition == ClientUi.StarPosition.BOTTOM_LEFT ? slotY + 16 - STAR_H + 1 : slotY - 2;
    }

    private static boolean overStar(int slotX, int slotY, double mx, double my) {
        int sx = starX(slotX), sy = starY(slotY);
        return mx >= sx - 1 && mx < sx + STAR_W + 1 && my >= sy - 1 && my < sy + STAR_H + 1;
    }

    private static boolean overSlot(int slotX, int slotY, double mx, double my) {
        return mx >= slotX && mx < slotX + 16 && my >= slotY && my < slotY + 16;
    }

    private static Slot slotAt(AbstractContainerScreen<?> screen, double mx, double my) {
        for (Slot slot : screen.getMenu().slots) {
            if (overSlot(slotX(screen, slot), slotY(screen, slot), mx, my)) return slot;
        }
        return null;
    }

    /** The slot whose star the mouse is on (only stars of slots holding an item count). */
    private static Slot starAt(AbstractContainerScreen<?> screen, double mx, double my) {
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.hasItem() || !eligible(screen, slot)) continue;
            if (overStar(slotX(screen, slot), slotY(screen, slot), mx, my)) return slot;
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!supported(event.getScreen())) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        double mx = event.getMouseX(), my = event.getMouseY();

        // 1) Left click on the star - only once it has been hovered for a moment (prevents accidents).
        if (event.getButton() == 0 && ClientUi.showFavorites) {
            Slot star = starAt(screen, mx, my);
            if (star != null) {
                int idx = screen.getMenu().slots.indexOf(star);
                boolean ready = idx == dwellSlot && System.currentTimeMillis() - dwellStart >= DWELL_MS;
                if (ready) {
                    toggleFavorite(screen, star);
                    event.setCanceled(true);
                }
                return; // not ready: let the click fall through to vanilla untouched
            }
        }

        Slot hovered = slotAt(screen, mx, my);
        if (hovered == null || !hovered.hasItem() || !eligible(screen, hovered)) return;

        // 2) Alt + left click anywhere on the slot.
        if (event.getButton() == 0 && Screen.hasAltDown()) {
            toggleFavorite(screen, hovered);
            event.setCanceled(true);
            return;
        }

        // 3) Middle click: position lock for the player's own inventory slots.
        if (event.getButton() == 2 && hovered.container instanceof Inventory) {
            int index = hovered.getSlotIndex();
            if (index < 0 || index >= 36) return;
            if (Screen.hasShiftDown()) {
                toggleRow(index);
            } else {
                toggleLock(index);
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!supported(event.getScreen())) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        if (!ChestCatClient.FAVORITE_KEY.matches(event.getKeyCode(), event.getScanCode())) return;
        if (screen.getFocused() instanceof EditBox box && box.isFocused()) return; // typing in a search box

        Slot hovered = screen.getSlotUnderMouse();
        if (hovered == null || !hovered.hasItem() || !eligible(screen, hovered)) return;
        toggleFavorite(screen, hovered);
        event.setCanceled(true);
    }

    private static void toggleFavorite(AbstractContainerScreen<?> screen, Slot slot) {
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;
        int menuIndex = serverMenuIndex(screen, slot);
        if (menuIndex < 0) return;
        boolean nowFavorite = ClientFavorites.toggleLocal(stack);
        playFeedback(nowFavorite);
        PacketDistributor.sendToServer(new ToggleFavoritePayload(menuIndex));
    }

    private static void toggleLock(int index) {
        if (lockedMirror.contains(index)) lockedMirror.remove(index);
        else lockedMirror.add(index);
        PacketDistributor.sendToServer(new ToggleSlotLockPayload(index));
        playFeedback(lockedMirror.contains(index));
    }

    /** Locks (or unlocks) the whole row the slot is in - hotbar, or one of the three main rows. */
    private static void toggleRow(int index) {
        int start = index < 9 ? 0 : 9 + ((index - 9) / 9) * 9;
        boolean target = !lockedMirror.contains(index);
        for (int i = start; i < start + 9; i++) {
            if (lockedMirror.contains(i) == target) continue;
            if (target) lockedMirror.add(i);
            else lockedMirror.remove(i);
            PacketDistributor.sendToServer(new ToggleSlotLockPayload(i));
        }
        playFeedback(target);
    }

    /** Immediate UI click: higher pitch when turning something on, lower when turning it off. */
    public static void playFeedback(boolean on) {
        if (!ClientUi.sounds || ClientUi.soundVolume <= 0) return;
        float volume = ClientUi.soundVolume / 100.0F;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), on ? 1.5F : 0.8F, volume));
    }

    // ----------------------------------------------------------------- render

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!supported(event.getScreen())) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        double mx = event.getMouseX(), my = event.getMouseY();

        // Track how long the mouse has rested on a star.
        Slot starHover = ClientUi.showFavorites ? starAt(screen, mx, my) : null;
        int starHoverIdx = starHover == null ? -1 : screen.getMenu().slots.indexOf(starHover);
        if (starHoverIdx != dwellSlot) {
            dwellSlot = starHoverIdx;
            dwellStart = System.currentTimeMillis();
        }
        boolean dwellReady = starHoverIdx >= 0 && System.currentTimeMillis() - dwellStart >= DWELL_MS;

        double pulseTime = System.currentTimeMillis() / 450.0;
        int pulseAlpha = ClientUi.animations ? 0xA0 + (int) (Math.sin(pulseTime) * 0x30) : 0xC0;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F); // above item icons and counts

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.hasItem() || !eligible(screen, slot)) continue;
            int x = slotX(screen, slot);
            int y = slotY(screen, slot);
            ItemStack stack = slot.getItem();

            boolean favorite = ClientUi.showFavorites && ClientFavorites.isFavorite(stack);
            boolean locked = slot.container instanceof Inventory && lockedMirror.contains(slot.getSlotIndex());
            boolean slotHovered = overSlot(x, y, mx, my) || overStar(x, y, mx, my);
            int menuIndex = -1;

            if (favorite) {
                ClientUi.FavoriteStyle style = ClientUi.favoriteStyle;
                if (style != ClientUi.FavoriteStyle.STAR) {
                    int c = (pulseAlpha << 24) | 0xFFD24A;
                    drawFrame(graphics, x - 1, y - 1, 18, ClientUi.highContrast ? 2 : 1, c);
                }
                if (style != ClientUi.FavoriteStyle.OUTLINE) {
                    menuIndex = screen.getMenu().slots.indexOf(slot);
                    boolean hot = menuIndex == dwellSlot;
                    drawStar(graphics, starX(x), starY(y), hot && dwellReady ? 0xFFFFF0A0 : 0xFFFFD24A,
                            hot && !dwellReady ? 0xFFFFFFFF : 0xFF5A3E00, ClientUi.highContrast);
                }
            } else if (ClientUi.showFavorites && slotHovered) {
                // Faint hint star so the control is discoverable, brightening once it is ready to click.
                menuIndex = screen.getMenu().slots.indexOf(slot);
                boolean hot = menuIndex == dwellSlot;
                int fill = hot ? (dwellReady ? 0xFFFFD24A : 0xFFFFFFFF) : 0x66FFFFFF;
                drawStar(graphics, starX(x), starY(y), fill, hot ? 0xFF5A3E00 : 0x40000000, false);
            }

            // Favorites filter: everything that isn't a favorite is dimmed so favorites stand out.
            if (ClientUi.favoritesFilter && !ClientFavorites.isFavorite(stack)) {
                graphics.fill(x, y, x + 16, y + 16, 0xA0101010);
            }

            if (locked) {
                graphics.fill(x, y, x + 16, y + 16, 0x2870B8FF);
                drawRuns(graphics, LOCK_RUNS, x, y + 10, 0xFF7FB8FF, 0xFF10304F);
            }
        }
        graphics.pose().popPose();

        if (starHover != null) {
            boolean fav = ClientFavorites.isFavorite(starHover.getItem());
            String hint = dwellReady
                    ? (fav ? "Click to remove from favorites" : "Click to add to favorites")
                    : "Hold the mouse here to " + (fav ? "unfavorite" : "favorite");
            graphics.renderTooltip(font, Component.literal(hint + "  (Alt+Click / Z)"), (int) mx, (int) my);
        }
    }

    // ---------------------------------------------------------------- drawing

    private static void drawFrame(GuiGraphics g, int x, int y, int size, int thickness, int color) {
        g.fill(x, y, x + size, y + thickness, color);
        g.fill(x, y + size - thickness, x + size, y + size, color);
        g.fill(x, y + thickness, x + thickness, y + size - thickness, color);
        g.fill(x + size - thickness, y + thickness, x + size, y + size - thickness, color);
    }

    private static void drawStar(GuiGraphics g, int x, int y, int fill, int outline, boolean highContrast) {
        if (highContrast) {
            g.fill(x - 2, y - 2, x + STAR_W + 2, y + STAR_H + 2, 0xE0000000);
        }
        drawRuns(g, STAR_RUNS, x, y, fill, outline);
    }

    /** Draws a pixel shape from horizontal runs with a 1px outline: halo first, then the fill on top. */
    private static void drawRuns(GuiGraphics g, int[][] runs, int x, int y, int fill, int outline) {
        for (int[] r : runs) {
            g.fill(x + r[1] - 1, y + r[0], x + r[2] + 1, y + r[0] + 1, outline);
            g.fill(x + r[1], y + r[0] - 1, x + r[2], y + r[0], outline);
            g.fill(x + r[1], y + r[0] + 1, x + r[2], y + r[0] + 2, outline);
        }
        for (int[] r : runs) {
            g.fill(x + r[1], y + r[0], x + r[2], y + r[0] + 1, fill);
        }
    }
}
