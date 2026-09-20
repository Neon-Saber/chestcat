package net.chestcat;

public enum ItemSortMode {
    CREATIVE_ORDER("Creative Menu Order"),
    ALPHABETICAL("Alphabetical"),
    COUNT_DESC("Quantity (high to low)"),
    COUNT_ASC("Quantity (low to high)"),
    MOD_ID("By Mod"),
    ITEM_TYPE("By Type"),
    COLOR("By Color"),
    REGISTRY_ORDER("Registry Order");

    private final String displayName;

    ItemSortMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ItemSortMode next() {
        ItemSortMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}