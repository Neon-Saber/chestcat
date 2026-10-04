package net.chestcat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A user-defined, multi-condition filter for a single "rule chest" (set via
 * {@code /chestcat rule set <conditions>}). Conditions are ANDed by default; a
 * trailing {@code any} switches the whole rule to OR. Each condition can be negated
 * with a leading {@code !} on its value.
 *
 * <p>Syntax: {@code FIELD=value[,FIELD=value...] [any]}, e.g.
 * {@code CATEGORY=TOOLS,MATERIAL=!wood} (tools, but not wooden ones) or
 * {@code TOOL_TYPE=pickaxe,MATERIAL=diamond any} (diamond pickaxes OR anything else
 * matching either condition).
 *
 * <p>Fields: CATEGORY ({@link ItemCategory} name), SUBKEY (ore/wood/stone sub-group),
 * MOD (registry namespace), TOOL_TYPE / MATERIAL (see {@link ItemMeta}), TAG (a
 * {@code c:} common tag path), ITEM_ID (full namespaced registry ID).
 *
 * <p>The same string is used for both the command syntax and NBT storage (see
 * {@link #serialize} / {@link #deserialize}), so save/load is a straight round trip
 * through {@link #parseCommand}.
 */
public final class ContainerRule {

    public enum Field { CATEGORY, SUBKEY, MOD, TOOL_TYPE, MATERIAL, TAG, ITEM_ID }

    public record Condition(Field field, String value, boolean negate) {
        String describe() {
            return field.name() + "=" + (negate ? "!" : "") + value;
        }
    }

    private final List<Condition> conditions;
    private final boolean matchAny;

    public ContainerRule(List<Condition> conditions, boolean matchAny) {
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("a rule needs at least one condition");
        }
        this.conditions = List.copyOf(conditions);
        this.matchAny = matchAny;
    }

    public List<Condition> conditions() {
        return conditions;
    }

    public boolean matchAny() {
        return matchAny;
    }

    /** Whether this rule accepts the given stack: AND (all conditions) by default, OR when matchAny. */
    public boolean matches(ItemStack stack, GroupingConfig.Settings grouping) {
        if (matchAny) {
            for (Condition c : conditions) {
                if (matchesOne(c, stack, grouping)) return true;
            }
            return false;
        }
        for (Condition c : conditions) {
            if (!matchesOne(c, stack, grouping)) return false;
        }
        return true;
    }

    private boolean matchesOne(Condition c, ItemStack stack, GroupingConfig.Settings grouping) {
        boolean result = switch (c.field()) {
            case CATEGORY -> matchesCategory(c.value(), stack, grouping);
            case SUBKEY -> matchesSubKey(c.value(), stack, grouping);
            case MOD -> ItemMeta.modId(stack).equalsIgnoreCase(c.value());
            case TOOL_TYPE -> ItemMeta.toolType(stack).equalsIgnoreCase(c.value());
            case MATERIAL -> ItemMeta.materialTier(stack).toLowerCase(Locale.ROOT)
                    .contains(c.value().toLowerCase(Locale.ROOT));
            case TAG -> matchesTag(c.value(), stack);
            case ITEM_ID -> ItemMeta.itemId(stack).equalsIgnoreCase(c.value());
        };
        return c.negate() != result;
    }

    private boolean matchesCategory(String value, ItemStack stack, GroupingConfig.Settings grouping) {
        try {
            ItemCategory want = ItemCategory.valueOf(value.toUpperCase(Locale.ROOT));
            return ItemGrouping.effectiveCategory(stack, grouping) == want;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean matchesSubKey(String value, ItemStack stack, GroupingConfig.Settings grouping) {
        String sub = ItemGrouping.classify(stack, grouping).subKey();
        return sub != null && sub.equalsIgnoreCase(value);
    }

    private boolean matchesTag(String value, ItemStack stack) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", value));
        return stack.is(tag);
    }

    /** Human-readable form for chat feedback, e.g. "CATEGORY=TOOLS AND MATERIAL=!wood". */
    public String describe() {
        String joiner = matchAny ? " OR " : " AND ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) sb.append(joiner);
            sb.append(conditions.get(i).describe());
        }
        return sb.toString();
    }

    /** The exact command-syntax string that would reparse into this rule. */
    public String toCommandString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(conditions.get(i).describe());
        }
        if (matchAny) sb.append(" any");
        return sb.toString();
    }

    /**
     * Parses the {@code /chestcat rule set} argument syntax. Throws
     * {@link IllegalArgumentException} (message is chat-safe and shown directly to the
     * player) on any malformed input.
     */
    public static ContainerRule parseCommand(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty rule - expected FIELD=value[,FIELD=value...] [any]");
        }
        String trimmed = raw.trim();
        boolean any = false;
        if (trimmed.length() > 4 && trimmed.substring(trimmed.length() - 4).equalsIgnoreCase(" any")) {
            any = true;
            trimmed = trimmed.substring(0, trimmed.length() - 4).trim();
        }
        String[] parts = trimmed.split(",");
        List<Condition> conditions = new ArrayList<>();
        for (String part : parts) {
            String piece = part.trim();
            if (!piece.isEmpty()) conditions.add(parseCondition(piece));
        }
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("no conditions found in: " + raw);
        }
        return new ContainerRule(conditions, any);
    }

    private static Condition parseCondition(String part) {
        int eq = part.indexOf('=');
        if (eq <= 0) {
            throw new IllegalArgumentException("expected FIELD=value in: " + part);
        }
        String fieldName = part.substring(0, eq).trim().toUpperCase(Locale.ROOT);
        String value = part.substring(eq + 1).trim();
        Field field;
        try {
            field = Field.valueOf(fieldName);
        } catch (IllegalArgumentException e) {
            StringBuilder valid = new StringBuilder();
            for (Field f : Field.values()) {
                if (valid.length() > 0) valid.append(", ");
                valid.append(f.name());
            }
            throw new IllegalArgumentException("unknown field '" + fieldName + "' - expected one of: " + valid);
        }
        boolean negate = value.startsWith("!");
        if (negate) value = value.substring(1);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("missing value in: " + part);
        }
        return new Condition(field, value, negate);
    }

    public static String serialize(ContainerRule rule) {
        return rule.toCommandString();
    }

    public static ContainerRule deserialize(String stored) {
        return parseCommand(stored);
    }
}
