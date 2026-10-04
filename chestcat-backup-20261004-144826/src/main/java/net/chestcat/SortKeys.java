package net.chestcat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Registry of every sort key available to sort chains. Built-ins are registered below;
 * anything else (including other mods) can call {@link #register(SortKey)} at any time.
 */
public final class SortKeys {

    private SortKeys() {}

    private static final Map<String, SortKey> KEYS = new LinkedHashMap<>();

    public static synchronized void register(SortKey key) {
        KEYS.put(key.id(), key);
    }

    public static synchronized SortKey get(String id) {
        return id == null ? null : KEYS.get(id);
    }

    public static synchronized List<SortKey> all() {
        return new ArrayList<>(KEYS.values());
    }

    /** The key after {@code id} in registration order (wraps around), skipping any in {@code used}. */
    public static synchronized String nextUnused(String id, java.util.Collection<String> used) {
        List<String> ids = new ArrayList<>(KEYS.keySet());
        if (ids.isEmpty()) return id;
        int start = ids.indexOf(id);
        for (int i = 1; i <= ids.size(); i++) {
            String candidate = ids.get((start + i) % ids.size());
            if (candidate.equals(id) || !used.contains(candidate)) return candidate;
        }
        return id;
    }

    public static void clearCaches() {
        TAG_CACHE.clear();
        NAME_CACHE.clear();
    }

    // Lower-cased display name per item. Stacks that carry no custom data (the vast majority) never rebuild
    // the string, so sorting thousands of stacks does one hash lookup per comparison instead of a string build.
    private static final Map<Item, String> NAME_CACHE = new ConcurrentHashMap<>();

    /** Lower-case display name used for alphabetical order (cached for plain stacks, computed for renamed ones). */
    public static String sortName(ItemStack stack) {
        if (stack.getComponentsPatch().isEmpty()) {
            return NAME_CACHE.computeIfAbsent(stack.getItem(),
                    item -> item.getDefaultInstance().getHoverName().getString().toLowerCase(Locale.ROOT));
        }
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT);
    }

    // ----------------------------------------------------------- registration

    private record Simple(String id, String displayName, String description,
                          Comparator<ItemStack> comparator, boolean defaultDescending,
                          Function<SortContext, Comparator<ItemStack>> contextual) implements SortKey {
        @Override
        public Comparator<ItemStack> comparator(SortContext context) {
            return contextual == null ? comparator : contextual.apply(context);
        }
    }

    private static void reg(String id, String name, String desc, Comparator<ItemStack> cmp) {
        register(new Simple(id, name, desc, cmp, false, null));
    }

    private static void regDesc(String id, String name, String desc, Comparator<ItemStack> cmp) {
        register(new Simple(id, name, desc, cmp, true, null));
    }

    /** A key whose order depends on the player's {@link SortContext} (custom orders, usage, favorites). */
    private static void regCtx(String id, String name, String desc, boolean descending,
                               Function<SortContext, Comparator<ItemStack>> fn) {
        register(new Simple(id, name, desc, fn.apply(SortContext.DEFAULT), descending, fn));
    }

    private static Comparator<ItemStack> ints(java.util.function.ToIntFunction<ItemStack> f) {
        return Comparator.comparingInt(f);
    }

    private static Comparator<ItemStack> strs(Function<ItemStack, String> f) {
        return Comparator.comparing(f);
    }

    // ------------------------------------------------------------ rank helpers

    private static final List<String> TOOL_ORDER = List.of(
            "pickaxe", "axe", "shovel", "hoe", "sword", "mace", "trident", "bow", "crossbow", "shield",
            "shears", "fishing_rod", "flint_and_steel");

    private static int toolRank(ItemStack stack) {
        String type = ItemMeta.toolType(stack);
        if (type.equals("none")) {
            if (stack.getItem() instanceof ShieldItem) type = "shield";
            else if (stack.getItem() instanceof MaceItem) type = "mace";
        }
        int i = TOOL_ORDER.indexOf(type);
        return i < 0 ? TOOL_ORDER.size() : i;
    }

    private static int enchantCount(ItemStack stack) {
        int n = 0;
        ItemEnchantments e = stack.get(DataComponents.ENCHANTMENTS);
        if (e != null) n += e.size();
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null) n += stored.size();
        return n;
    }

    private static String firstEnchantName(ItemStack stack) {
        String best = "~";
        ItemEnchantments e = stack.get(DataComponents.ENCHANTMENTS);
        if (e != null) {
            for (var holder : e.keySet()) {
                String n = holder.getRegisteredName();
                if (n.compareTo(best) < 0) best = n;
            }
        }
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null) {
            for (var holder : stored.keySet()) {
                String n = holder.getRegisteredName();
                if (n.compareTo(best) < 0) best = n;
            }
        }
        return best;
    }

    private static String potionName(ItemStack stack) {
        PotionContents pc = stack.get(DataComponents.POTION_CONTENTS);
        if (pc == null) return "~";
        return pc.potion().map(h -> h.getRegisteredName()).orElse("~");
    }

    private static int foodValue(ItemStack stack) {
        FoodProperties fp = stack.getFoodProperties(null);
        return fp == null ? 0 : fp.nutrition();
    }

    private static int fuel(ItemStack stack) {
        try {
            return stack.getBurnTime(RecipeType.SMELTING);
        } catch (Throwable t) {
            return 0;
        }
    }

    // Item -> first tag matching a namespace filter. Tags are static per world, so cache it.
    private static final Map<String, Map<Item, String>> TAG_CACHE = new ConcurrentHashMap<>();

    private static String firstTag(ItemStack stack, String cacheName, Predicate<String> namespaceFilter) {
        Map<Item, String> cache = TAG_CACHE.computeIfAbsent(cacheName, k -> new ConcurrentHashMap<>());
        return cache.computeIfAbsent(stack.getItem(), item -> {
            String best = "~";
            for (var tag : (Iterable<net.minecraft.tags.TagKey<Item>>)
                    item.builtInRegistryHolder().tags()::iterator) {
                String ns = tag.location().getNamespace();
                if (!namespaceFilter.test(ns)) continue;
                String id = tag.location().toString();
                if (id.compareTo(best) < 0) best = id;
            }
            return best;
        });
    }

    // ------------------------------------------------------------- the keys

    static {
        regCtx("name", "Name", "Alphabetical by display name (A-Z or Z-A in the behavior settings).", false,
                SortContext::nameComparator);
        reg("id", "Item ID", "By full registry id (namespace:path).",
                strs(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()));
        regCtx("mod", "Mod", "Groups items by the mod they come from. Minecraft first, then mods A-Z (or your custom mod order).",
                false, SortContext::modComparator);
        reg("mod_id", "Mod ID", "Groups items by mod namespace (technical id).", strs(ModInfo::namespace));
        regCtx("category", "Category", "Combat, Armor, Tools, Food, Ores, Materials, Building... (works for modded items; your custom category order applies).",
                false, SortContext::categoryComparator);
        reg("type", "Chest Type", "The same broad types ChestCat uses to route items to chests.",
                ints(ItemSortUtils::typeRank));
        regDesc("rarity", "Rarity", "Common, Uncommon, Rare, Epic (enchanted items count as rarer).",
                ints(s -> s.getRarity().ordinal()));
        regDesc("count", "Quantity", "How many items are in the stack.", ints(ItemStack::getCount));
        regDesc("max_stack", "Max Stack Size", "Items that stack higher first.", ints(ItemStack::getMaxStackSize));
        reg("stack_fill", "Full Stacks", "Full stacks before partial stacks.",
                ints(s -> s.getCount() >= s.getMaxStackSize() ? 0 : 1));
        regDesc("durability_max", "Max Durability", "Tools and armor with more total durability first.",
                ints(ItemStack::getMaxDamage));
        regDesc("durability_left", "Durability Left", "Items with the most durability remaining first.",
                ints(s -> s.isDamageableItem() ? s.getMaxDamage() - s.getDamageValue() : 0));
        reg("condition", "Condition", "Undamaged items before damaged ones.", ints(s -> s.isDamaged() ? 1 : 0));
        reg("tool_type", "Tool / Weapon Type", "Pickaxe, axe, shovel, hoe, sword, mace, bow, shield...",
                ints(SortKeys::toolRank));
        reg("armor_slot", "Armor Slot", "Helmet, chestplate, leggings, boots.", ints(ItemSortUtils::armorSlotRank));
        reg("material", "Material Tier", "Wood, stone, iron, gold, diamond, netherite.", ints(ItemSortUtils::materialRank));
        reg("color", "Color", "Rainbow order for dyed variants.", ints(ItemSortUtils::colorRank));
        regDesc("food_value", "Food Value", "Foods that restore more hunger first.", ints(SortKeys::foodValue));
        reg("potion_type", "Potion Type", "Groups potions by effect.", strs(SortKeys::potionName));
        regDesc("enchant_count", "Enchantment Count", "Items with more enchantments first.", ints(SortKeys::enchantCount));
        reg("enchantment", "Enchantment", "Groups items by their first enchantment.", strs(SortKeys::firstEnchantName));
        reg("enchanted", "Enchanted", "Enchanted items before plain ones.", ints(ItemSortUtils::enchantedRank));
        regDesc("enchantability", "Enchantability", "Higher enchantability first.", ints(ItemStack::getEnchantmentValue));
        regDesc("fuel", "Fuel Value", "Better furnace fuel first.", ints(SortKeys::fuel));
        reg("tag", "Item Tag", "Groups by the first item tag (any namespace).",
                strs(s -> firstTag(s, "any", ns -> true)));
        reg("tag_minecraft", "Minecraft Tag", "Groups by the first minecraft: item tag.",
                strs(s -> firstTag(s, "mc", ns -> ns.equals("minecraft"))));
        reg("tag_common", "Common Tag", "Groups by the first c: (convention) item tag.",
                strs(s -> firstTag(s, "c", ns -> ns.equals("c") || ns.equals("forge"))));
        reg("tag_mod", "Mod Tag", "Groups by the first tag added by a mod.",
                strs(s -> firstTag(s, "mod", ns -> !ns.equals("minecraft") && !ns.equals("c") && !ns.equals("forge"))));
        reg("creative", "Creative Tab Order", "The order items appear in the creative menu.",
                ints(s -> CreativeOrder.getOrder(s.getItem())));
        reg("registry", "Registry Order", "The order items were registered in.", ints(ItemSortUtils::registryId));

        // ---- added in the second overhaul pass
        regCtx("favorites", "Favorites First", "Favorited items before everything else (use Reversed for favorites last).",
                false, SortContext::favoriteFirstComparator);
        reg("duplicates", "Exact Duplicates", "Keeps stacks of the exact same item (same enchantments, name, data) side by side.",
                Comparator.<ItemStack>comparingInt(ItemSortUtils::registryId)
                        .thenComparingInt(ItemStack::hashItemAndComponents));
        regCtx("recent_acquired", "Recently Acquired", "Items you picked up or crafted most recently first.",
                false, SortContext::recentAcquiredComparator);
        regCtx("recent_used", "Recently Used", "Items you used, placed, swung or ate most recently first.",
                false, SortContext::recentUsedComparator);
        regCtx("most_used", "Most Used", "Items you use the most first.",
                false, SortContext::mostUsedComparator);
        regCtx("least_used", "Least Used", "Items you rarely use first (good for finding clutter).",
                false, SortContext::leastUsedComparator);
    }

    /** Final fallback so a chain always yields one stable order: name, then registry id, then count. */
    public static Comparator<ItemStack> tiebreak() {
        return tiebreak(SortContext.DEFAULT);
    }

    public static Comparator<ItemStack> tiebreak(SortContext context) {
        return context.nameComparator()
                .thenComparingInt(ItemSortUtils::registryId)
                .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());
    }

    @SuppressWarnings("unused")
    private static boolean isBook(ItemStack s) {
        return s.getItem() instanceof EnchantedBookItem;
    }

    @SuppressWarnings("unused")
    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
