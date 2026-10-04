package net.chestcat;

import net.minecraft.world.item.ItemStack;

import java.util.Comparator;

/**
 * One building block of a sort chain ("Category", "Mod", "Rarity", ...). Register new
 * ones with {@link SortKeys#register(SortKey)} - the sort-chain editor lists every
 * registered key automatically, so adding a key needs no other code changes.
 */
public interface SortKey {

    /** Stable id used in saved chains and the config file (lower_snake_case). */
    String id();

    String displayName();

    String description();

    /** Ascending order of this key. A chain entry may reverse it. */
    Comparator<ItemStack> comparator();

    /**
     * Same order, but allowed to use the player's {@link SortContext} (custom category / mod order, usage
     * history, favorites). Keys that don't need it can ignore this - the default returns {@link #comparator()}.
     */
    default Comparator<ItemStack> comparator(SortContext context) {
        return comparator();
    }

    /** True when the natural order for this key is high-to-low (counts, durability, rarity...). */
    default boolean defaultDescending() {
        return false;
    }
}
