package net.chestcat;

/** Where the empty slots end up after a sort: after the items (default) or before them. */
public enum EmptySlotMode {
    LAST("Empty slots last"),
    FIRST("Empty slots first");

    private final String displayName;

    EmptySlotMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EmptySlotMode next() {
        EmptySlotMode[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public static EmptySlotMode fromOrdinal(int i) {
        EmptySlotMode[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
