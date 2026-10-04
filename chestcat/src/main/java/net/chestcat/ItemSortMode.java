package net.chestcat;

public enum ItemSortMode {
    CREATIVE_ORDER("Creative Menu Order"),
    ALPHABETICAL("Alphabetical"),
    COUNT_DESC("Quantity (high to low)"),
    COUNT_ASC("Quantity (low to high)"),
    MOD_ID("By Mod"),
    MOD_THEN_TYPE("By Mod, then Type"),
    ITEM_TYPE("Clean Sort (Type + Material + Color)"),
    MATERIAL("By Material Tier"),
    COLOR("By Color"),
    SMART("Smart Sort (Recommended)"),
    REGISTRY_ORDER("Registry Order"),
    // Added with the multi-level sort overhaul - appended so saved ordinals/names stay valid.
    CATEGORY_MOD("Category > Mod > Name"),
    MOD_CATEGORY("Mod > Category > Name"),
    RARITY("By Rarity"),
    DURABILITY("By Durability"),
    CUSTOM("Custom Chain (your own rules)");

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