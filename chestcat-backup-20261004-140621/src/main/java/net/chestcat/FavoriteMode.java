package net.chestcat;

/** How favorited items take part in sorting. */
public enum FavoriteMode {
    PIN("Pinned (never moved)"),
    FIRST("Sorted first"),
    LAST("Sorted last"),
    MARK("Marker only (sorted normally)"),
    /** Only favorited items are sorted; everything else is left exactly where it is. */
    ONLY("Favorites only (others untouched)");

    private final String displayName;

    FavoriteMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public FavoriteMode next() {
        FavoriteMode[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public static FavoriteMode fromOrdinal(int i) {
        FavoriteMode[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
