package net.chestcat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

/** Single source of truth for sort-mode comparators, shared by ChestSorter and InventorySorter. */
public final class ItemSortUtils {

    private ItemSortUtils() {}

    private static final List<String> COLOR_ORDER = List.of(
            "white", "light_gray", "gray", "black",
            "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue",
            "blue", "purple", "magenta", "pink"
    );

    public static Comparator<ItemStack> comparator(ItemSortMode mode) {
        return switch (mode) {
            case ALPHABETICAL -> byName();
            case COUNT_DESC -> Comparator.comparingInt(ItemStack::getCount).reversed();
            case COUNT_ASC -> Comparator.comparingInt(ItemStack::getCount);
            case MOD_ID -> Comparator.comparing(ItemSortUtils::modId).thenComparing(byName());
            case REGISTRY_ORDER -> Comparator.comparingInt(ItemSortUtils::registryId);
            case CREATIVE_ORDER -> Comparator.comparingInt(s -> CreativeOrder.getOrder(s.getItem()));
            case ITEM_TYPE -> Comparator.comparingInt(ItemSortUtils::typeRank).thenComparing(byName());
            case COLOR -> Comparator.comparingInt(ItemSortUtils::colorRank).thenComparing(byName());
        };
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

    /** Weapons > tools > armor > food > blocks > everything else. */
    private static int typeRank(ItemStack stack) {
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) return 0;
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) return 1;
        if (stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR)
                || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR)) return 2;
        if (stack.has(DataComponents.FOOD)) return 3;
        if (stack.getItem() instanceof BlockItem) return 4;
        return 5;
    }

    /** Pulls a color name out of the item's registry path (e.g. red_wool -> red), rainbow ordered. */
    private static int colorRank(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        for (int i = 0; i < COLOR_ORDER.size(); i++) {
            if (path.contains(COLOR_ORDER.get(i))) return i;
        }
        return COLOR_ORDER.size();
    }
}