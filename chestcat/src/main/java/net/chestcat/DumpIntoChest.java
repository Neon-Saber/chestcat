package net.chestcat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Dumps the player's unlocked hotbar+main-inventory items (0-35) into the
 * chest they currently have open - merges into matching stacks first, then
 * fills empty chest slots. Skips favorited/locked inventory slots. */
public final class DumpIntoChest {

    private static final int MAIN_START = 0;
    private static final int MAIN_END = 36;

    private DumpIntoChest() {}

    public static int run(ServerPlayer player) {
        if (!(player.containerMenu instanceof ChestMenu chestMenu)) return 0;
        Container container = chestMenu.getSlot(0).container;
        Set<Integer> locked = LockedSlots.get(player.getUUID());
        Inventory inv = player.getInventory();
        int moved = 0;

        for (int i = MAIN_START; i < MAIN_END; i++) {
            if (locked.contains(i)) continue;
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            int remaining = stack.getCount();
            for (int c = 0; c < container.getContainerSize() && remaining > 0; c++) {
                ItemStack slotStack = container.getItem(c);
                if (slotStack.isEmpty() || !ItemStack.isSameItemSameComponents(slotStack, stack)) continue;
                int space = slotStack.getMaxStackSize() - slotStack.getCount();
                if (space <= 0) continue;
                int move = Math.min(space, remaining);
                slotStack.grow(move);
                remaining -= move;
                moved += move;
            }
            for (int c = 0; c < container.getContainerSize() && remaining > 0; c++) {
                if (!container.getItem(c).isEmpty()) continue;
                int move = Math.min(remaining, stack.getMaxStackSize());
                ItemStack placed = stack.copy();
                placed.setCount(move);
                container.setItem(c, placed);
                remaining -= move;
                moved += move;
            }
            int used = stack.getCount() - remaining;
            if (used > 0) {
                stack.shrink(used);
                inv.setItem(i, stack);
            }
        }
        container.setChanged();
        inv.setChanged();
        return moved;
    }
}