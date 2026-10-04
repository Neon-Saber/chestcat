package net.chestcat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Everything a sort key may need beyond the stack itself: the player's custom category / mod order, the
 * alphabetical direction, their usage history and their favorites. Built once per sort, so comparators stay
 * plain closures over a few small lookups (no per-comparison allocation, no global state).
 *
 * A context with nothing set ({@link #DEFAULT}) behaves exactly like the original static comparators, which
 * lets {@link SortChain} keep its shared comparator cache for the common case.
 */
public final class SortContext {

    /** Per-item usage history for "recently acquired / recently used / most used" keys. */
    public interface UsageView {
        long acquired(Item item);

        long used(Item item);

        int uses(Item item);
    }

    public static final SortContext DEFAULT = new SortContext(null, Map.of(), false, null, null);

    private final int[] categoryRank;        // index = SortCategory ordinal; null = natural order
    private final Map<String, Integer> modRank; // mod id -> position in the player's list
    private final boolean alphaDescending;
    private final UsageView usage;
    private final Predicate<ItemStack> favorite;

    private SortContext(int[] categoryRank, Map<String, Integer> modRank, boolean alphaDescending,
                        UsageView usage, Predicate<ItemStack> favorite) {
        this.categoryRank = categoryRank;
        this.modRank = modRank;
        this.alphaDescending = alphaDescending;
        this.usage = usage;
        this.favorite = favorite;
    }

    public static SortContext of(SortMore more, UsageView usage, Predicate<ItemStack> favorite) {
        SortMore m = more == null ? SortMore.DEFAULT : more;
        int[] cats = null;
        if (!m.categoryOrder().isEmpty()) {
            SortCategory[] all = SortCategory.values();
            String[] names = new String[all.length];
            for (int i = 0; i < all.length; i++) names[i] = all[i].name();
            cats = ranks(m.categoryOrder(), names);
        }
        Map<String, Integer> mods = Map.of();
        if (!m.modOrder().isEmpty()) {
            mods = new HashMap<>();
            int i = 0;
            for (String id : m.modList()) mods.putIfAbsent(id, i++);
        }
        if (cats == null && mods.isEmpty() && !m.alphaDescending() && usage == null && favorite == null) {
            return DEFAULT;
        }
        return new SortContext(cats, mods, m.alphaDescending(), usage, favorite);
    }

    /** True when this context changes nothing, so a cached shared comparator can be used. */
    public boolean isNeutral() {
        return this == DEFAULT;
    }

    /**
     * Pure helper: rank of every name, where names listed in {@code order} come first (in that order) and the
     * rest keep their natural order after them. Unknown names in {@code order} are ignored.
     */
    public static int[] ranks(String order, String[] names) {
        int[] rank = new int[names.length];
        java.util.Arrays.fill(rank, -1);
        int next = 0;
        for (String part : order.split(",")) {
            String p = part.strip();
            for (int i = 0; i < names.length; i++) {
                if (rank[i] < 0 && names[i].equalsIgnoreCase(p)) {
                    rank[i] = next++;
                    break;
                }
            }
        }
        for (int i = 0; i < names.length; i++) {
            if (rank[i] < 0) rank[i] = next++;
        }
        return rank;
    }

    // ---------------------------------------------------------------- lookups

    public int categoryRank(SortCategory category) {
        return categoryRank == null ? category.ordinal() : categoryRank[category.ordinal()];
    }

    /** Position of this mod in the player's custom order, or a large number when it isn't listed. */
    public int modRank(String namespace) {
        Integer r = modRank.get(namespace);
        return r == null ? Integer.MAX_VALUE : r;
    }

    public boolean alphaDescending() {
        return alphaDescending;
    }

    public UsageView usage() {
        return usage;
    }

    public boolean isFavorite(ItemStack stack) {
        return favorite != null && favorite.test(stack);
    }

    // ------------------------------------------------------------ comparators

    public Comparator<ItemStack> categoryComparator() {
        if (categoryRank == null) return Comparator.comparingInt(s -> SortCategory.of(s).ordinal());
        return Comparator.comparingInt(s -> categoryRank[SortCategory.of(s).ordinal()]);
    }

    /** Custom mod order first, then Minecraft before other mods, then A-Z by display name. */
    public Comparator<ItemStack> modComparator() {
        Comparator<ItemStack> byName = Comparator.comparing(ModInfo::sortName);
        if (modRank.isEmpty()) return byName;
        return Comparator.<ItemStack>comparingInt(s -> modRank(ModInfo.namespace(s))).thenComparing(byName);
    }

    /** Display-name order (cached per item), honouring the A-Z / Z-A setting. */
    public Comparator<ItemStack> nameComparator() {
        Comparator<ItemStack> c = Comparator.comparing(SortKeys::sortName);
        return alphaDescending ? c.reversed() : c;
    }

    public Comparator<ItemStack> favoriteFirstComparator() {
        if (favorite == null) return (a, b) -> 0;
        return Comparator.comparingInt(s -> favorite.test(s) ? 0 : 1);
    }

    /** Most recently acquired first (items never seen sort last). */
    public Comparator<ItemStack> recentAcquiredComparator() {
        if (usage == null) return (a, b) -> 0;
        return (a, b) -> Long.compare(usage.acquired(b.getItem()), usage.acquired(a.getItem()));
    }

    public Comparator<ItemStack> recentUsedComparator() {
        if (usage == null) return (a, b) -> 0;
        return (a, b) -> Long.compare(usage.used(b.getItem()), usage.used(a.getItem()));
    }

    public Comparator<ItemStack> mostUsedComparator() {
        if (usage == null) return (a, b) -> 0;
        return (a, b) -> Integer.compare(usage.uses(b.getItem()), usage.uses(a.getItem()));
    }

    public Comparator<ItemStack> leastUsedComparator() {
        if (usage == null) return (a, b) -> 0;
        return (a, b) -> Integer.compare(usage.uses(a.getItem()), usage.uses(b.getItem()));
    }

    @SuppressWarnings("unused")
    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
