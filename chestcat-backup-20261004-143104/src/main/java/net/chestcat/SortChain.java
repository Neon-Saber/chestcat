package net.chestcat;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A user-built multi-level sort: an ordered list of sort keys, each ascending or
 * descending ("Category > Mod > Rarity (desc) > Name"). Immutable. Serialized as a plain
 * string ("category:asc,mod:asc,rarity:desc,name:asc") so it travels in packets, NBT
 * and the client config file without any extra codec.
 */
public final class SortChain {

    public record Entry(String keyId, boolean descending) {}

    public static final int MAX_ENTRIES = 6;
    public static final String DEFAULT_STRING = "category:asc,mod:asc,name:asc";

    private static final Map<String, SortChain> CACHE = new ConcurrentHashMap<>();

    private final List<Entry> entries;
    private volatile Comparator<ItemStack> comparator;

    private SortChain(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static SortChain defaultChain() {
        return parse(DEFAULT_STRING);
    }

    public static SortChain of(List<Entry> entries) {
        List<Entry> clean = new ArrayList<>();
        for (Entry e : entries) {
            if (clean.size() >= MAX_ENTRIES) break;
            if (SortKeys.get(e.keyId()) == null) continue;
            boolean dupe = false;
            for (Entry c : clean) {
                if (c.keyId().equals(e.keyId())) {
                    dupe = true;
                    break;
                }
            }
            if (!dupe) clean.add(e);
        }
        if (clean.isEmpty()) {
            return parse(DEFAULT_STRING);
        }
        return new SortChain(clean);
    }

    /** Unknown keys are dropped, duplicates ignored; an empty/invalid string gives the default chain. */
    public static SortChain parse(String text) {
        String t = text == null ? "" : text.strip();
        SortChain cached = CACHE.get(t);
        if (cached != null) return cached;

        List<Entry> list = new ArrayList<>();
        for (String part : t.split(",")) {
            String p = part.strip();
            if (p.isEmpty()) continue;
            String id = p;
            boolean desc;
            int colon = p.indexOf(':');
            if (colon >= 0) {
                id = p.substring(0, colon).strip();
                desc = p.substring(colon + 1).strip().equalsIgnoreCase("desc");
            } else {
                SortKey k = SortKeys.get(id);
                desc = k != null && k.defaultDescending();
            }
            list.add(new Entry(id, desc));
        }

        SortChain chain;
        if (list.isEmpty() && !t.equals(DEFAULT_STRING)) {
            chain = parse(DEFAULT_STRING);
        } else {
            chain = of(list);
        }
        if (CACHE.size() > 64) CACHE.clear();
        CACHE.put(t, chain);
        return chain;
    }

    public List<Entry> entries() {
        return entries;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.keyId()).append(e.descending() ? ":desc" : ":asc");
        }
        return sb.toString();
    }

    /** Human readable form for tooltips: "Category > Mod > Rarity (high-low) > Name". */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            if (sb.length() > 0) sb.append(" > ");
            SortKey k = SortKeys.get(e.keyId());
            sb.append(k == null ? e.keyId() : k.displayName());
            if (e.descending() != (k != null && k.defaultDescending())) sb.append(e.descending() ? " (reversed)" : " (reversed)");
        }
        return sb.toString();
    }

    public Comparator<ItemStack> comparator() {
        Comparator<ItemStack> c = comparator;
        if (c == null) {
            c = build();
            comparator = c;
        }
        return c;
    }

    /**
     * The chain's comparator for one player's context (custom category / mod order, usage history, favorites).
     * A neutral context reuses the shared cached comparator; anything else is built fresh, which is cheap
     * (a handful of closures) and happens once per sort.
     */
    public Comparator<ItemStack> comparator(SortContext context) {
        if (context == null || context.isNeutral()) return comparator();
        return build(context);
    }

    private Comparator<ItemStack> build() {
        return build(SortContext.DEFAULT);
    }

    private Comparator<ItemStack> build(SortContext context) {
        Comparator<ItemStack> result = null;
        for (Entry e : entries) {
            SortKey key = SortKeys.get(e.keyId());
            if (key == null) continue;
            Comparator<ItemStack> part = key.comparator(context);
            if (e.descending()) part = part.reversed();
            result = result == null ? part : result.thenComparing(part);
        }
        if (result == null) return SortKeys.tiebreak(context);
        return result.thenComparing(SortKeys.tiebreak(context));
    }
}
