package net.chestcat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Never move this" rules, one per entry, stored as a ';'-separated string:
 * <pre>
 *   item:minecraft:netherite_sword   one specific item
 *   mod:create                       everything from a mod
 *   cat:food                         a sort category (Food, Armor, Tools...)
 *   tag:c:ores                       everything in an item tag
 *   menu:minecraft:hopper            a whole container type (its menu id) - ChestCat never sorts it
 * </pre>
 * Matching items are never picked up or moved by any ChestCat sort, quick stack or dump. A "menu:" rule
 * works on containers instead of items: sorting an open container of that type is skipped entirely.
 */
public final class ExclusionRules {

    public static final int MAX_RULES = 12;
    public static final ExclusionRules NONE = new ExclusionRules(List.of(), "");

    private record Rule(char kind, String value, TagKey<Item> tag, SortCategory category) {}

    private static final Map<String, ExclusionRules> CACHE = new ConcurrentHashMap<>();

    private final List<Rule> rules;
    private final String normalized;

    private ExclusionRules(List<Rule> rules, String normalized) {
        this.rules = rules;
        this.normalized = normalized;
    }

    public static ExclusionRules parse(String text) {
        String t = text == null ? "" : text.strip();
        if (t.isEmpty()) return NONE;
        ExclusionRules cached = CACHE.get(t);
        if (cached != null) return cached;

        List<Rule> list = new ArrayList<>();
        List<String> norm = new ArrayList<>();
        for (String part : t.split(";")) {
            if (list.size() >= MAX_RULES) break;
            String p = part.strip();
            int colon = p.indexOf(':');
            if (colon <= 0 || colon == p.length() - 1) continue;
            String kind = p.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = p.substring(colon + 1).strip();
            switch (kind) {
                case "item" -> {
                    ResourceLocation id = ResourceLocation.tryParse(value.toLowerCase(Locale.ROOT));
                    if (id != null) {
                        list.add(new Rule('i', id.toString(), null, null));
                        norm.add("item:" + id);
                    }
                }
                case "mod" -> {
                    String mod = value.toLowerCase(Locale.ROOT);
                    list.add(new Rule('m', mod, null, null));
                    norm.add("mod:" + mod);
                }
                case "cat" -> {
                    SortCategory cat = SortCategory.byName(value);
                    if (cat != null) {
                        list.add(new Rule('c', cat.name(), null, cat));
                        norm.add("cat:" + cat.name().toLowerCase(Locale.ROOT));
                    }
                }
                case "menu" -> {
                    ResourceLocation id = ResourceLocation.tryParse(value.toLowerCase(Locale.ROOT));
                    if (id != null) {
                        list.add(new Rule('u', id.toString(), null, null));
                        norm.add("menu:" + id);
                    }
                }
                case "tag" -> {
                    ResourceLocation id = ResourceLocation.tryParse(value.toLowerCase(Locale.ROOT));
                    if (id != null) {
                        list.add(new Rule('t', id.toString(), TagKey.create(Registries.ITEM, id), null));
                        norm.add("tag:" + id);
                    }
                }
                default -> {
                }
            }
        }
        ExclusionRules result = list.isEmpty() ? NONE : new ExclusionRules(List.copyOf(list), String.join(";", norm));
        if (CACHE.size() > 64) CACHE.clear();
        CACHE.put(t, result);
        return result;
    }

    /** Cleaned-up string form: invalid rules dropped, case and spacing normalized. */
    public static String normalize(String text) {
        return parse(text).normalized;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /** True when a "menu:" rule names this container type (menu registry id like "minecraft:generic_9x3"). */
    public boolean matchesMenu(String menuId) {
        if (rules.isEmpty() || menuId == null || menuId.isEmpty()) return false;
        for (Rule r : rules) {
            if (r.kind() == 'u' && r.value().equals(menuId)) return true;
        }
        return false;
    }

    public boolean matches(ItemStack stack) {
        if (rules.isEmpty() || stack.isEmpty()) return false;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (Rule r : rules) {
            switch (r.kind()) {
                case 'i' -> {
                    if (key.toString().equals(r.value())) return true;
                }
                case 'm' -> {
                    if (key.getNamespace().equals(r.value())) return true;
                }
                case 'c' -> {
                    if (SortCategory.of(stack) == r.category()) return true;
                }
                case 't' -> {
                    if (stack.is(r.tag())) return true;
                }
                default -> {
                }
            }
        }
        return false;
    }
}
