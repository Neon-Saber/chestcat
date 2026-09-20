package net.chestcat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class InventorySorter {

    private InventorySorter() {}

    /** Main storage grid only: slots 9..35. Hotbar (0-8), offhand and armor are left untouched. */
    private static final int MAIN_START = 9;
    private static final int MAIN_END = 36; // exclusive

    public static int sortMainInventory(ServerPlayer player, ItemSortMode mode) {
        Inventory inv = player.getInventory();

        List<ItemStack> stacks = new ArrayList<>();
        for (int i = MAIN_START; i < MAIN_END; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
                inv.setItem(i, ItemStack.EMPTY);
            }
        }

        // Merge identical stacks first.
        List<ItemStack> merged = new ArrayList<>();
        outer:
        for (ItemStack stack : stacks) {
            for (ItemStack existing : merged) {
                if (ItemStack.isSameItemSameComponents(existing, stack)
                        && existing.getCount() < existing.getMaxStackSize()) {
                    int space = existing.getMaxStackSize() - existing.getCount();
                    int move = Math.min(space, stack.getCount());
                    existing.grow(move);
                    stack.shrink(move);
                    if (stack.isEmpty()) continue outer;
                }
            }
            merged.add(stack);
        }

        Comparator<ItemStack> comparator = ItemSortUtils.comparator(mode);

        merged.sort(comparator);

        int slot = MAIN_START;
        for (ItemStack stack : merged) {
            if (slot >= MAIN_END) {
                // Ran out of room (shouldn't normally happen since we started with
                // this many items), drop any remainder at the player's feet.
                player.drop(stack, false);
                continue;
            }
            inv.setItem(slot++, stack);
        }

        inv.setChanged();
        return merged.size();
    }
}