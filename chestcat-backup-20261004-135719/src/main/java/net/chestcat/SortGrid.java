package net.chestcat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Turns a visual grid of slot indices into the order slots should be filled in. */
public final class SortGrid {

    private SortGrid() {}

    /**
     * @param slotAt slotAt[row][col] = container slot at that visual cell (row 0 = top),
     *               or -1 for a cell that must be skipped (locked / excluded)
     * @return slot indices in fill order; reversed means starting from the bottom-right corner
     */
    public static List<Integer> fillOrder(int[][] slotAt, SortLayout layout, boolean reverse) {
        List<Integer> order = new ArrayList<>();
        int rows = slotAt.length;
        int cols = rows == 0 ? 0 : slotAt[0].length;

        if (layout == SortLayout.COLUMNS) {
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) add(order, slotAt[r][c]);
            }
        } else {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) add(order, slotAt[r][c]);
            }
        }

        if (reverse) Collections.reverse(order);
        return order;
    }

    private static void add(List<Integer> order, int slot) {
        if (slot >= 0) order.add(slot);
    }
}
