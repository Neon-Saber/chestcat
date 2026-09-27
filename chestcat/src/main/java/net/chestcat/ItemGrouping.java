package net.chestcat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves an ItemStack (or a raw category+subkey pair) down to the fine-grained
 * grouping key that decides which chest it belongs in, given a player's
 * GroupingConfig. This is the single place that knows about raw/refined ores,
 * wood types, and stone/deepslate families - ChestSorter just asks it for a Key
 * and routes accordingly.
 */
public final class ItemGrouping {

    private ItemGrouping() {}

    /** subKey is null when the category isn't split (or this item's sub-type was
     *  individually merged back into the shared bucket). */
    public record Key(ItemCategory category, String subKey) {

        public String storageKey() {
            return subKey == null ? category.name() : category.name() + ":" + subKey;
        }

        public static Key parse(String stored) {
            int colon = stored.indexOf(':');
            try {
                if (colon < 0) return new Key(ItemCategory.valueOf(stored), null);
                return new Key(ItemCategory.valueOf(stored.substring(0, colon)), stored.substring(colon + 1));
            } catch (IllegalArgumentException e) {
                return new Key(ItemCategory.MISC, null);
            }
        }

        public String displayName() {
            if (category == ItemCategory.CUSTOM) return subKey == null ? category.getDisplayName() : subKey;
            if (subKey == null) return category.getDisplayName();
            return category.getDisplayName() + " (" + capitalize(subKey) + ")";
        }
    }

    public static final List<String> WOOD_TYPES = List.of(
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
            "mangrove", "cherry", "bamboo", "crimson", "warped", "pale_oak"
    );

    /** Effective category, honoring the player's disabledCategories toggle
     *  (a disabled category is routed exactly like Misc). */
    public static ItemCategory effectiveCategory(ItemStack stack, GroupingConfig.Settings config) {
        ItemCategory category = ItemCategory.categorize(stack);
        // Items ChestCat can't place by itself (usually modded) may have an AI-chosen category cached.
        if (category == ItemCategory.BLOCKS || category == ItemCategory.MISC) {
            String label = AiClassifier.cachedLabel(itemId(stack));
            if (label != null && !label.startsWith("CUSTOM:")) {
                try {
                    category = ItemCategory.valueOf(label);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return config.isDisabled(category) ? ItemCategory.MISC : category;
    }

    /** The custom group (config/chestcat/groups.json, or an AI answer naming one) this item belongs to, as a Key; else null. */
    public static Key customKey(ItemStack stack) {
        String name = CustomGroups.match(stack);
        if (name == null) {
            String label = AiClassifier.cachedLabel(itemId(stack));
            if (label != null && label.startsWith("CUSTOM:") && CustomGroups.hasGroup(label.substring("CUSTOM:".length()))) {
                name = label.substring("CUSTOM:".length());
            }
        }
        return name == null ? null : new Key(ItemCategory.CUSTOM, name);
    }

    /** True for an item type nothing has classified yet: not in any custom group, not cached by the AI, and only Blocks/Misc by the built-in rules. */
    public static boolean needsAi(ItemStack stack) {
        if (stack.isEmpty() || CustomGroups.match(stack) != null || AiClassifier.cachedLabel(itemId(stack)) != null) return false;
        ItemCategory category = ItemCategory.categorize(stack);
        return category == ItemCategory.BLOCKS || category == ItemCategory.MISC;
    }

    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static Key classify(ItemStack stack, GroupingConfig.Settings config) {
        ItemCategory category = effectiveCategory(stack, config);
        String sub = switch (category) {
            case ORES_AND_INGOTS -> config.splitOres() ? oreSubKey(stack) : null;
            case WOOD -> config.splitWood() ? woodSubKey(stack) : null;
            case STONE_AND_DEEPSLATE -> config.splitStone() ? stoneSubKey(stack) : null;
            default -> null;
        };
        if (sub != null && config.isMerged(category, sub)) sub = null;
        return new Key(category, sub);
    }

    /** Every sub-key a category can split into, for the settings screen - in the
     *  same order categorization checks them, so the UI reads top-to-bottom the
     *  way items would actually land. */
    public static List<String> subKeysFor(ItemCategory category) {
        return switch (category) {
            case ORES_AND_INGOTS -> List.of("raw", "refined");
            case WOOD -> WOOD_TYPES;
            case STONE_AND_DEEPSLATE -> ItemCategory.STONE_FAMILY_ORDER;
            default -> List.of();
        };
    }

    /** subKeysFor plus the "other" catch-all that wood (unlisted wood types) can also land in. */
    public static List<String> pickerSubKeysFor(ItemCategory category) {
        List<String> keys = new ArrayList<>(subKeysFor(category));
        if (category == ItemCategory.WOOD) keys.add("other");
        return keys;
    }

    /** True if sub is a sub-key this category can really produce. */
    public static boolean isValidSubKey(ItemCategory category, String sub) {
        return sub != null && pickerSubKeysFor(category).contains(sub);
    }

    public static boolean isSplittable(ItemCategory category) {
        return category == ItemCategory.ORES_AND_INGOTS
                || category == ItemCategory.WOOD
                || category == ItemCategory.STONE_AND_DEEPSLATE;
    }

    /** Ore blocks and raw_* metals are "raw"; ingots, nuggets, gems and coal are "refined". */
    private static String oreSubKey(ItemStack stack) {
        String path = path(stack);
        return path.startsWith("raw_") || path.endsWith("_ore") ? "raw" : "refined";
    }

    private static String woodSubKey(ItemStack stack) {
        String path = path(stack);
        // Longest match wins so dark_oak / pale_oak items aren't filed under plain "oak".
        String best = null;
        for (String type : WOOD_TYPES) {
            if (path.contains(type) && (best == null || type.length() > best.length())) best = type;
        }
        return best != null ? best : "other";
    }

    private static String stoneSubKey(ItemStack stack) {
        String path = path(stack);
        for (String family : ItemCategory.STONE_FAMILY_ORDER) {
            if (path.contains(family)) return family;
        }
        // Only reached for items an AI label placed here; plain stone is the safest family.
        return "stone";
    }

    private static String path(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    public static String capitalize(String s) {
        if (s.isEmpty()) return s;
        List<String> words = new ArrayList<>();
        for (String part : s.split("_")) {
            if (part.isEmpty()) continue;
            words.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return String.join(" ", words);
    }
}
