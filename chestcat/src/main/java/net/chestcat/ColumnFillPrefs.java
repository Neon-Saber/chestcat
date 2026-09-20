package net.chestcat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Session-only per-player toggle: fill sorted items column-first instead
 * of the default row-first order. */
public final class ColumnFillPrefs {

    private static final Set<UUID> COLUMN_MODE = new HashSet<>();

    private ColumnFillPrefs() {}

    public static boolean toggle(UUID player) {
        if (COLUMN_MODE.contains(player)) {
            COLUMN_MODE.remove(player);
            return false;
        }
        COLUMN_MODE.add(player);
        return true;
    }

    public static boolean isColumnMode(UUID player) {
        return COLUMN_MODE.contains(player);
    }
}