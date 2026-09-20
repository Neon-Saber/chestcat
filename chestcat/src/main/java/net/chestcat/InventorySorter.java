package net.chestcat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class InventorySorter {

    private InventorySorter() {}

    private static final int MAIN_START = 0;
    private static final int MAIN_END = 36;
    private static final int ROWS = 4;
    private static final int COLS = 9;

    public static int sortMainInventory(ServerPlayer player, ItemSortMode mode) {
        Inventory inv = player.getInventory();
        Set<Integer> locked = LockedSlots.get(player.getUUID());

        List<ItemStack> stacks = new ArrayList<>();
        for (int i = MAIN_START; i < MAIN_END; i++) {
            if (locked.contains(i)) continue;
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
                inv.setItem(i, ItemStack.EMPTY);
            }
        }

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

        merged.sort(ItemSortUtils.comparator(mode));

        boolean columnMode = ColumnFillPrefs.isColumnMode(player.getUUID());
        List<ItemStack> fillOrder = ItemSortUtils.toFillOrder(merged, ROWS, COLS, columnMode);

        int slot = MAIN_START;
        for (ItemStack stack : fillOrder) {
            while (slot < MAIN_END && locked.contains(slot)) slot++;
            if (slot >= MAIN_END) {
                player.drop(stack, false);
                continue;
            }
            inv.setItem(slot++, stack);
        }

        inv.setChanged();
        return merged.size();
    }
}