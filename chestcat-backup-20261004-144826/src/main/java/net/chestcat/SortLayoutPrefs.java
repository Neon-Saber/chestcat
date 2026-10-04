package net.chestcat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Session-only per-player sort layout settings (server side). The client re-sends them before every sort. */
public final class SortLayoutPrefs {

    /**
     * The first six fields are the original layout options; chain / favoriteMode / mergeStacks /
     * exclusions were added with the multi-level sort overhaul, and {@link SortMore} (empty slots, extra
     * hotbar behaviour, category / mod order, name direction) after that. The older constructors are
     * kept so existing callers still compile (new options default to the stock behaviour).
     */
    public record Settings(SortLayout layout, boolean reverse, boolean includeHotbar,
                           boolean groupArmorBySlot, TiebreakPriority tiebreakPriority,
                           boolean floatEnchantedFirst,
                           String chain, FavoriteMode favoriteMode, boolean mergeStacks, String exclusions,
                           SortMore more) {

        public static final Settings DEFAULT = new Settings(
                SortLayout.ROWS, false, true, true, TiebreakPriority.TYPE_FIRST, false);

        public Settings(SortLayout layout, boolean reverse, boolean includeHotbar,
                        boolean groupArmorBySlot, TiebreakPriority tiebreakPriority,
                        boolean floatEnchantedFirst) {
            this(layout, reverse, includeHotbar, groupArmorBySlot, tiebreakPriority, floatEnchantedFirst,
                    SortChain.DEFAULT_STRING, FavoriteMode.PIN, true, "", SortMore.DEFAULT);
        }

        /** Pre-"more options" constructor, kept so existing callers (profile loading) still compile. */
        public Settings(SortLayout layout, boolean reverse, boolean includeHotbar,
                        boolean groupArmorBySlot, TiebreakPriority tiebreakPriority,
                        boolean floatEnchantedFirst,
                        String chain, FavoriteMode favoriteMode, boolean mergeStacks, String exclusions) {
            this(layout, reverse, includeHotbar, groupArmorBySlot, tiebreakPriority, floatEnchantedFirst,
                    chain, favoriteMode, mergeStacks, exclusions, SortMore.DEFAULT);
        }

        public Settings withExtras(String chain, FavoriteMode favoriteMode, boolean mergeStacks, String exclusions) {
            return new Settings(layout, reverse, includeHotbar, groupArmorBySlot, tiebreakPriority,
                    floatEnchantedFirst, chain, favoriteMode, mergeStacks, exclusions, more);
        }

        /** The same extras with new layout options (used when a layout packet arrives). */
        public Settings withLayout(SortLayout layout, boolean reverse, boolean includeHotbar,
                                   boolean groupArmorBySlot, TiebreakPriority tiebreakPriority,
                                   boolean floatEnchantedFirst) {
            return new Settings(layout, reverse, includeHotbar, groupArmorBySlot, tiebreakPriority,
                    floatEnchantedFirst, chain, favoriteMode, mergeStacks, exclusions, more);
        }

        /** The same settings with new empty-slot / hotbar / ordering options. */
        public Settings withMore(SortMore more) {
            return new Settings(layout, reverse, includeHotbar, groupArmorBySlot, tiebreakPriority,
                    floatEnchantedFirst, chain, favoriteMode, mergeStacks, exclusions,
                    more == null ? SortMore.DEFAULT : more);
        }
    }

    private static final Map<UUID, Settings> PREFS = new HashMap<>();

    private SortLayoutPrefs() {}

    public static Settings get(UUID player) {
        return PREFS.getOrDefault(player, Settings.DEFAULT);
    }

    public static void set(UUID player, Settings settings) {
        PREFS.put(player, settings);
    }

    /** Frees the entry when a player leaves so the map can't grow forever on a long-running server. */
    public static void forget(UUID player) {
        PREFS.remove(player);
    }
}
