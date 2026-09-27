package net.chestcat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Single source of truth for sort-mode comparators, shared by ChestSorter and InventorySorter. */
public final class ItemSortUtils {

    private ItemSortUtils() {}

    // Neutrals first, then a rainbow sweep - dyed/colored variants of a block (wool,
    // concrete, terracotta, glass, candles, beds, banners...) all cluster by hue.
    private static final List<String> COLOR_ORDER = List.of(
            "white", "light_gray", "gray", "black",
            "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue",
            "blue", "purple", "magenta", "pink"
    );

    // Low tier -> high tier. One shared list works for both tools and armor since
    // "golden"/"iron"/"diamond"/"netherite" land in the same relative tier either way.
    private static final List<String> MATERIAL_ORDER = List.of(
            "wooden", "leather", "stone", "chainmail",
            "iron", "golden", "diamond", "netherite"
    );

    // Head -> chest -> legs -> feet -> off-hand, so a full armor set reads top to bottom.
    private static final List<String> ARMOR_SLOT_ORDER = List.of(
            "helmet", "cap", "chestplate", "tunic", "leggings", "pants",
            "boots", "sandals", "shield"
    );

    /** Convenience overload for callers with no per-player settings (e.g. programmatic sorts). */
    public static Comparator<ItemStack> comparator(ItemSortMode mode) {
        return comparator(mode, SortLayoutPrefs.Settings.DEFAULT);
    }

    public static Comparator<ItemStack> comparator(ItemSortMode mode, SortLayoutPrefs.Settings settings) {
        Comparator<ItemStack> tie = tiebreak(settings);
        return switch (mode) {
            case ALPHABETICAL -> byName().thenComparing(tie);
            case COUNT_DESC -> Comparator.comparingInt(ItemStack::getCount).reversed().thenComparing(tie);
            case COUNT_ASC -> Comparator.comparingInt(ItemStack::getCount).thenComparing(tie);
            case MOD_ID -> Comparator.comparing(ItemSortUtils::modId).thenComparing(tie);
            case MOD_THEN_TYPE -> Comparator.comparing(ItemSortUtils::modId)
                    .thenComparingInt(ItemSortUtils::typeRank)
                    .thenComparing(tie);
            case REGISTRY_ORDER -> Comparator.comparingInt(ItemSortUtils::registryId);
            case CREATIVE_ORDER -> Comparator.comparingInt(s -> CreativeOrder.getOrder(s.getItem()));
            case ITEM_TYPE -> Comparator.comparingInt(ItemSortUtils::typeRank).thenComparing(tie);
            case MATERIAL -> Comparator.comparingInt(ItemSortUtils::materialRank).thenComparing(tie);
            case COLOR -> Comparator.comparingInt(ItemSortUtils::colorRank).thenComparing(tie);
            case SMART -> smartComparator(settings);
        };
    }

    /**
     * The shared tail every mode above falls back through once its own primary key
     * ties: type, [armor slot if enabled], material/color/mod (in the player's chosen
     * priority order), then name. "groupArmorBySlot" and "tiebreakPriority" are the
     * two customization knobs exposed in Sort Options.
     */
    private static Comparator<ItemStack> tiebreak(SortLayoutPrefs.Settings settings) {
        Comparator<ItemStack> c = Comparator.comparingInt(ItemSortUtils::typeRank);
        if (settings.groupArmorBySlot()) {
            c = c.thenComparingInt(ItemSortUtils::armorSlotRank);
        }
        return c.thenComparing(middleChain(settings.tiebreakPriority())).thenComparing(byName());
    }

    /**
     * The flagship "just make it look right" mode: type -> [armor slot] -> material
     * tier (always, so a full set stays lined up regardless of the priority setting)
     * -> [enchanted items floated first] -> color -> mod -> name.
     */
    private static Comparator<ItemStack> smartComparator(SortLayoutPrefs.Settings settings) {
        Comparator<ItemStack> c = Comparator.comparingInt(ItemSortUtils::typeRank);
        if (settings.groupArmorBySlot()) {
            c = c.thenComparingInt(ItemSortUtils::armorSlotRank);
        }
        c = c.thenComparingInt(ItemSortUtils::materialRank);
        if (settings.floatEnchantedFirst()) {
            c = c.thenComparingInt(ItemSortUtils::enchantedRank);
        }
        return c.thenComparingInt(ItemSortUtils::colorRank)
                .thenComparing(ItemSortUtils::modId)
                .thenComparing(byName());
    }

    /** Material, color and mod, reordered by the player's tiebreak priority setting. */
    private static Comparator<ItemStack> middleChain(TiebreakPriority priority) {
        Comparator<ItemStack> material = Comparator.comparingInt(ItemSortUtils::materialRank);
        Comparator<ItemStack> color = Comparator.comparingInt(ItemSortUtils::colorRank);
        Comparator<ItemStack> mod = Comparator.comparing(ItemSortUtils::modId);
        return switch (priority) {
            case COLOR_FIRST -> color.thenComparing(material).thenComparing(mod);
            case MOD_FIRST -> mod.thenComparing(material).thenComparing(color);
            case TYPE_FIRST, MATERIAL_FIRST -> material.thenComparing(color).thenComparing(mod);
        };
    }

    /** Reorders a sorted list so filling slots left-to-right, top-to-bottom
     * produces a column-major visual layout instead of row-major. No-op
     * (returns the same list) when columnMajor is false. */
    public static List<ItemStack> toFillOrder(List<ItemStack> sorted, int rows, int cols, boolean columnMajor) {
        if (!columnMajor) return sorted;

        List<ItemStack> result = new ArrayList<>(java.util.Collections.nCopies(sorted.size(), null));
        int idx = 0;
        for (int col = 0; col < cols && idx < sorted.size(); col++) {
            for (int row = 0; row < rows && idx < sorted.size(); row++) {
                int rowMajorPos = row * cols + col;
                if (rowMajorPos < result.size()) {
                    result.set(rowMajorPos, sorted.get(idx++));
                }
            }
        }
        List<ItemStack> trimmed = new ArrayList<>();
        for (ItemStack s : result) {
            if (s != null) trimmed.add(s);
        }
        return trimmed;
    }

    private static Comparator<ItemStack> byName() {
        return Comparator.comparing(s -> s.getHoverName().getString());
    }

    private static String modId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    private static int registryId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getId(stack.getItem());
    }

    private static String path(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static int typeRank(ItemStack stack) {
        return ItemCategory.categorize(stack).ordinal();
    }

    private static int armorSlotRank(ItemStack stack) {
        String path = path(stack);
        for (int i = 0; i < ARMOR_SLOT_ORDER.size(); i++) {
            if (path.contains(ARMOR_SLOT_ORDER.get(i))) return i;
        }
        return ARMOR_SLOT_ORDER.size();
    }

    private static int materialRank(ItemStack stack) {
        String path = path(stack);
        for (int i = 0; i < MATERIAL_ORDER.size(); i++) {
            if (path.contains(MATERIAL_ORDER.get(i))) return i;
        }
        return MATERIAL_ORDER.size();
    }

    private static int colorRank(ItemStack stack) {
        String path = path(stack);
        for (int i = 0; i < COLOR_ORDER.size(); i++) {
            if (path.contains(COLOR_ORDER.get(i))) return i;
        }
        return COLOR_ORDER.size();
    }

    private static int enchantedRank(ItemStack stack) {
        return stack.isEnchanted() ? 0 : 1;
    }
}