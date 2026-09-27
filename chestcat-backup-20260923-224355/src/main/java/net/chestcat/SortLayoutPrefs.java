package net.chestcat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Session-only per-player sort settings (server side). The client re-sends them before every sort. */
public final class SortLayoutPrefs {

    public record Settings(SortLayout layout, boolean reverse, boolean includeHotbar,
                           boolean groupArmorBySlot, boolean floatEnchantedFirst,
                           TiebreakPriority tiebreakPriority) {
        public static final Settings DEFAULT =
                new Settings(SortLayout.ROWS, false, true, true, true, TiebreakPriority.TYPE_FIRST);
    }

    private static final Map<UUID, Settings> PREFS = new HashMap<>();

    private SortLayoutPrefs() {}

    public static Settings get(UUID player) {
        return PREFS.getOrDefault(player, Settings.DEFAULT);
    }

    public static void set(UUID player, SortLayout layout, boolean reverse, boolean includeHotbar,
                           boolean groupArmorBySlot, boolean floatEnchantedFirst,
                           TiebreakPriority tiebreakPriority) {
        PREFS.put(player, new Settings(layout, reverse, includeHotbar,
                groupArmorBySlot, floatEnchantedFirst, tiebreakPriority));
    }
}