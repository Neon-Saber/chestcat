package net.chestcat;

/**
 * Which of material / color / mod wins first once two items tie on their main sort key.
 * MATERIAL_FIRST behaves exactly like TYPE_FIRST in the comparator, so next() skips it
 * (it only exists so old/other clients that send it still decode cleanly).
 */
public enum TiebreakPriority {
    TYPE_FIRST("Type > Material > Color > Mod"),
    MATERIAL_FIRST("Type > Material > Color > Mod"),
    COLOR_FIRST("Type > Color > Material > Mod"),
    MOD_FIRST("Type > Mod > Material > Color");

    private final String displayName;

    TiebreakPriority(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TiebreakPriority next() {
        TiebreakPriority[] values = values();
        TiebreakPriority candidate = values[(this.ordinal() + 1) % values.length];
        return candidate == MATERIAL_FIRST ? candidate.next() : candidate;
    }

    public static TiebreakPriority fromOrdinal(int i) {
        TiebreakPriority[] values = values();
        return values[Math.floorMod(i, values.length)];
    }
}
