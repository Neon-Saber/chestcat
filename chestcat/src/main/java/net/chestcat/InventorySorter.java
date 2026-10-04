package net.chestcat;

import net.chestcat.data.LockedSlotsData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class InventorySorter {

    private InventorySorter() {}

    private static final int COLS = 9;
    private static final int MAIN_ROWS = 3;        // inventory slots 9..35, drawn top to bottom
    private static final int MAIN_FIRST_SLOT = 9;  // hotbar is slots 0..8, drawn as the bottom row

    /**
     * Sorts the player's main inventory (and the hotbar, depending on their settings).
     *
     * @return how many inventory slots actually changed - 0 means everything was already in order, so no
     *         item moved and nothing was re-sent to the client.
     */
    public static int sortMainInventory(ServerPlayer player, ItemSortMode mode) {
        Inventory inv = player.getInventory();
        Set<Integer> locked = LockedSlotsData.get(player.serverLevel()).get(player.getUUID());
        Predicate<ItemStack> skipStack = FavoriteRules.skipForSort(player);
        Predicate<ItemStack> isFavorite = FavoriteRules.favoritePredicate(player);
        SortLayoutPrefs.Settings prefs = SortLayoutPrefs.get(player.getUUID());
        SortMore more = prefs.more();
        Comparator<ItemStack> comparator = FavoriteRules.wrap(ItemSortUtils.comparator(mode, prefs, player), player);

        // A slot is left alone when it is locked, holds an item the rules say not to move, is the selected
        // hotbar slot (if that's preserved) or holds a favorite in the hotbar (if favorites stay put).
        int selected = inv.selected;
        Predicate<Integer> skipSlot = slot -> {
            if (locked.contains(slot)) return true;
            ItemStack stack = inv.getItem(slot);
            if (skipStack.test(stack)) return true;
            if (more.preserveSelected() && slot == selected) return true;
            return slot < COLS && more.hotbarFavoritesStay() && !stack.isEmpty() && isFavorite.test(stack);
        };

        boolean hotbar = prefs.includeHotbar();
        int changed;
        if (hotbar && more.hotbarSeparate()) {
            // Main rows and the hotbar are sorted independently, so nothing crosses between them.
            changed = sortRegion(player, inv, true, false, skipSlot, comparator, prefs);
            changed += sortRegion(player, inv, false, true, skipSlot, comparator, prefs);
        } else {
            changed = sortRegion(player, inv, true, hotbar, skipSlot, comparator, prefs);
        }

        if (changed > 0) inv.setChanged();
        return changed;
    }

    /** Sorts one region: the three main rows and/or the hotbar row, laid out as one visual grid. */
    private static int sortRegion(ServerPlayer player, Inventory inv, boolean main, boolean hotbar,
                                  Predicate<Integer> skipSlot, Comparator<ItemStack> comparator,
                                  SortLayoutPrefs.Settings prefs) {
        // Visual grid, top row first. A skipped slot is -1, so it is never picked up or moved.
        int rows = (main ? MAIN_ROWS : 0) + (hotbar ? 1 : 0);
        if (rows == 0) return 0;
        int[][] grid = new int[rows][COLS];
        int r = 0;
        if (main) {
            for (; r < MAIN_ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int slot = MAIN_FIRST_SLOT + r * COLS + c;
                    grid[r][c] = skipSlot.test(slot) ? -1 : slot;
                }
            }
        }
        if (hotbar) {
            for (int c = 0; c < COLS; c++) {
                grid[r][c] = skipSlot.test(c) ? -1 : c;
            }
        }

        // Pick up every movable stack, remembering what each slot held so we can tell what really changed.
        ItemStack[] before = new ItemStack[36];
        List<ItemStack> stacks = new ArrayList<>();
        for (int[] row : grid) {
            for (int slot : row) {
                if (slot < 0) continue;
                ItemStack stack = inv.getItem(slot);
                before[slot] = stack.copy();
                if (!stack.isEmpty()) {
                    stacks.add(stack.copy());
                    inv.setItem(slot, ItemStack.EMPTY);
                }
            }
        }

        List<ItemStack> merged = new ArrayList<>(stacks.size());
        outer:
        for (ItemStack stack : stacks) {
            if (!prefs.mergeStacks()) {
                merged.add(stack);
                continue;
            }
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

        merged.sort(comparator);

        List<Integer> order = SortGrid.fillOrder(grid, prefs.layout(), prefs.reverse());
        int start = SortGrid.startIndex(order.size(), merged.size(), prefs.more().emptySlots() == EmptySlotMode.FIRST);
        int i = start;
        for (ItemStack stack : merged) {
            if (i >= order.size()) {
                player.drop(stack, false);
                continue;
            }
            inv.setItem(order.get(i++), stack);
        }

        int changed = 0;
        for (int[] row : grid) {
            for (int slot : row) {
                if (slot >= 0 && !same(before[slot], inv.getItem(slot))) changed++;
            }
        }
        return changed;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
    }
}
