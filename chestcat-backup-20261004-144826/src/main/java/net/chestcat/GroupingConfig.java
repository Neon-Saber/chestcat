package net.chestcat;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Session-only per-player auto-sort grouping settings (server side); the client re-sends
 * them before every sort. Controls how finely items are split between chests:
 * ores raw vs refined, wood per type, stone/deepslate per family, per-category on/off,
 * and individual sub-types merged back into their category's shared chest.
 */
public final class GroupingConfig {

    public record Settings(boolean splitOres, boolean splitWood, boolean splitStone,
                           Set<ItemCategory> disabledCategories, Set<String> mergedSubKeys) {

        /** Splitting is opt-in, so sorting behaves exactly as before until enabled. */
        public static final Settings DEFAULT = new Settings(false, false, false, Set.of(), Set.of());

        public Settings {
            disabledCategories = disabledCategories.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(EnumSet.copyOf(disabledCategories));
            mergedSubKeys = Set.copyOf(mergedSubKeys);
        }

        public boolean isDisabled(ItemCategory category) {
            return disabledCategories.contains(category);
        }

        public boolean isMerged(ItemCategory category, String subKey) {
            return mergedSubKeys.contains(mergeKey(category, subKey));
        }

        public boolean splits(ItemCategory category) {
            return switch (category) {
                case ORES_AND_INGOTS -> splitOres;
                case WOOD -> splitWood;
                case STONE_AND_DEEPSLATE -> splitStone;
                default -> false;
            };
        }

        public Settings withSplit(ItemCategory category, boolean on) {
            return switch (category) {
                case ORES_AND_INGOTS -> new Settings(on, splitWood, splitStone, disabledCategories, mergedSubKeys);
                case WOOD -> new Settings(splitOres, on, splitStone, disabledCategories, mergedSubKeys);
                case STONE_AND_DEEPSLATE -> new Settings(splitOres, splitWood, on, disabledCategories, mergedSubKeys);
                default -> this;
            };
        }

        public Settings withDisabled(ItemCategory category, boolean disabled) {
            Set<ItemCategory> next = new HashSet<>(disabledCategories);
            if (disabled) next.add(category); else next.remove(category);
            return new Settings(splitOres, splitWood, splitStone, next, mergedSubKeys);
        }

        public Settings withMerged(ItemCategory category, String subKey, boolean merged) {
            Set<String> next = new HashSet<>(mergedSubKeys);
            String key = mergeKey(category, subKey);
            if (merged) next.add(key); else next.remove(key);
            return new Settings(splitOres, splitWood, splitStone, disabledCategories, next);
        }
    }

    private static final Map<UUID, Settings> CONFIGS = new HashMap<>();

    private GroupingConfig() {}

    public static String mergeKey(ItemCategory category, String subKey) {
        return category.name() + ":" + subKey;
    }

    public static Settings get(UUID player) {
        return CONFIGS.getOrDefault(player, Settings.DEFAULT);
    }

    public static void set(UUID player, Settings settings) {
        CONFIGS.put(player, settings);
    }
}
