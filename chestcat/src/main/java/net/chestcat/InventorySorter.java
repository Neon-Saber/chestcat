package net.chestcat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class InventorySorter {

    private InventorySorter() {}

    private static final int COLS = 9;
    private static final int MAIN_ROWS = 3;        // inventory slots 9..35, drawn top to bottom
    private static final int MAIN_FIRST_SLOT = 9;  // hotbar is slots 0..8, drawn as the bottom row

    public static int sortMainInventory(ServerPlayer player, ItemSortMode mode) {
        Inventory inv = player.getInventory();
        Set<Integer> locked = LockedSlots.get(player.getUUID());
        SortLayoutPrefs.Settings prefs = SortLayoutPrefs.get(player.getUUID());

        // Visual grid, top row first: 3 main rows, then the hotbar row if it's included.
        int rows = prefs.includeHotbar() ? MAIN_ROWS + 1 : MAIN_ROWS;
        int[][] grid = new int[rows][COLS];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < COLS; c++) {
                int slot = r < MAIN_ROWS ? MAIN_FIRST_SLOT + r * COLS + c : c;
                grid[r][c] = locked.contains(slot) ? -1 : slot;
            }
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (int[] row : grid) {
            for (int slot : row) {
                if (slot < 0) continue;
                ItemStack stack = inv.getItem(slot);
                if (!stack.isEmpty()) {
                    stacks.add(stack.copy());
                    inv.setItem(slot, ItemStack.EMPTY);
                }
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

        List<Integer> order = SortGrid.fillOrder(grid, prefs.layout(), prefs.reverse());
        int i = 0;
        for (ItemStack stack : merged) {
            if (i >= order.size()) {
                player.drop(stack, false);
                continue;
            }
            inv.setItem(order.get(i++), stack);
        }

        inv.setChanged();
        return merged.size();
    }
}
