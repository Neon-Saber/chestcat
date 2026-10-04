package net.chestcat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Single shared place for mod ID / tool type / material tier lookups, so a
 * {@link ContainerRule} condition and a sort tiebreak in {@link ItemSortUtils} always
 * mean the same thing for the same item. Everything here is a best-effort guess off
 * the item's registry path - cheap, no NBT reads, works for vanilla and most modded items.
 */
public final class ItemMeta {

    private ItemMeta() {}

    // Low tier -> high tier; same list ItemSortUtils uses for its MATERIAL sort mode.
    private static final List<String> MATERIAL_ORDER = List.of(
            "wooden", "leather", "stone", "chainmail",
            "iron", "golden", "diamond", "netherite"
    );

    private static final List<String> TOOL_SUFFIXES = List.of(
            "pickaxe", "axe", "shovel", "hoe", "sword"
    );

    /** Full namespaced registry ID, e.g. "minecraft:diamond_pickaxe". */
    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Registry namespace, e.g. "minecraft" or a mod's ID. */
    public static String modId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    private static String path(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    /**
     * Best-guess tool type from the registry path: pickaxe/axe/shovel/hoe/sword, plus a
     * handful of named specials (shears, fishing_rod, flint_and_steel, bow, crossbow,
     * trident). Returns "none" for anything else (armor, blocks, food, ...).
     */
    public static String toolType(ItemStack stack) {
        String path = path(stack);
        for (String suffix : TOOL_SUFFIXES) {
            if (path.endsWith("_" + suffix) || path.equals(suffix)) return suffix;
        }
        if (path.contains("shears")) return "shears";
        if (path.contains("fishing_rod")) return "fishing_rod";
        if (path.contains("flint_and_steel")) return "flint_and_steel";
        if (path.contains("crossbow")) return "crossbow";
        if (path.contains("bow")) return "bow";
        if (path.contains("trident")) return "trident";
        return "none";
    }

    /**
     * Best-guess material tier from the registry path: wooden/leather/stone/chainmail/
     * iron/golden/diamond/netherite. Returns "none" when nothing matches (most items
     * that aren't tools or armor). Matches on {@code contains}, so a rule condition can
     * use the short form ("wood") and still catch "wooden_pickaxe".
     */
    public static String materialTier(ItemStack stack) {
        String path = path(stack);
        for (String material : MATERIAL_ORDER) {
            if (path.contains(material)) return material;
        }
        return "none";
    }
}
