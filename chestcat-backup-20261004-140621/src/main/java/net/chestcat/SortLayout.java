package net.chestcat;

public enum SortLayout {
    ROWS("Rows"),
    COLUMNS("Columns");

    private final String displayName;

    SortLayout(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SortLayout next() {
        SortLayout[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static SortLayout fromOrdinal(int i) {
        SortLayout[] values = values();
        return values[Math.floorMod(i, values.length)];
    }
}
