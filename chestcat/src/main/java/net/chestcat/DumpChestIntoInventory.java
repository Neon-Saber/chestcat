package net.chestcat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Moves everything from the currently-open chest into the player's
 * unlocked hotbar+main-inventory slots (0-35). Merges into matching stacks
 * first, then fills empty slots. Stops per-item once inventory is full;
 * remainder stays in the chest rather than being dropped. */
public final class DumpChestIntoInventory {

    private static final int MAIN_START = 0;
    private static final int MAIN_END = 36;

    private DumpChestIntoInventory() {}

    public static int run(ServerPlayer player) {
        if (!(player.containerMenu instanceof ChestMenu chestMenu)) return 0;
        Container container = chestMenu.getSlot(0).container;
        Set<Integer> locked = LockedSlots.get(player.getUUID());
        Inventory inv = player.getInventory();
        int moved = 0;

        for (int c = 0; c < container.getContainerSize(); c++) {
            ItemStack stack = container.getItem(c);
            if (stack.isEmpty()) continue;

            int remaining = stack.getCount();
            for (int i = MAIN_START; i < MAIN_END && remaining > 0; i++) {
                if (locked.contains(i)) continue;
                ItemStack slotStack = inv.getItem(i);
                if (slotStack.isEmpty() || !ItemStack.isSameItemSameComponents(slotStack, stack)) continue;
                int space = slotStack.getMaxStackSize() - slotStack.getCount();
                if (space <= 0) continue;
                int move = Math.min(space, remaining);
                slotStack.grow(move);
                remaining -= move;
                moved += move;
            }
            for (int i = MAIN_START; i < MAIN_END && remaining > 0; i++) {
                if (locked.contains(i) || !inv.getItem(i).isEmpty()) continue;
                int move = Math.min(remaining, stack.getMaxStackSize());
                ItemStack placed = stack.copy();
                placed.setCount(move);
                inv.setItem(i, placed);
                remaining -= move;
                moved += move;
            }
            int used = stack.getCount() - remaining;
            if (used > 0) {
                stack.shrink(used);
                container.setItem(c, stack);
            }
        }
        container.setChanged();
        inv.setChanged();
        return moved;
    }
}