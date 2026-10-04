package net.chestcat;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The second batch of per-player sort options (added after the multi-level sort overhaul): where empty
 * slots go, the extra hotbar behaviours, custom category / mod ordering and the alphabetical direction.
 * Kept as its own record so the original {@link SortLayoutPrefs.Settings} packets stay unchanged.
 *
 * @param emptySlots         empty slots after (default) or before the sorted items
 * @param hotbarSeparate     when the hotbar is included, sort it on its own instead of mixing it with the main rows
 * @param hotbarFavoritesStay favorited items that are in the hotbar are never moved by an inventory sort
 * @param preserveSelected   the currently selected hotbar slot is never moved
 * @param categoryOrder      comma separated category names in the order they should appear ("" = default order)
 * @param modOrder           comma separated mod ids in the order they should appear ("" = Minecraft first, then A-Z)
 * @param alphaDescending    names sort Z-A instead of A-Z
 */
public record SortMore(EmptySlotMode emptySlots, boolean hotbarSeparate, boolean hotbarFavoritesStay,
                       boolean preserveSelected, String categoryOrder, String modOrder, boolean alphaDescending) {

    public static final SortMore DEFAULT = new SortMore(EmptySlotMode.LAST, false, false, false, "", "", false);

    public static final int MAX_MODS = 48;
    private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_.-]{1,64}");

    public static final StreamCodec<io.netty.buffer.ByteBuf, SortMore> STREAM_CODEC = StreamCodec.of(
            (buf, m) -> {
                ByteBufCodecs.VAR_INT.encode(buf, m.emptySlots().ordinal());
                buf.writeBoolean(m.hotbarSeparate());
                buf.writeBoolean(m.hotbarFavoritesStay());
                buf.writeBoolean(m.preserveSelected());
                ByteBufCodecs.stringUtf8(512).encode(buf, m.categoryOrder());
                ByteBufCodecs.stringUtf8(1024).encode(buf, m.modOrder());
                buf.writeBoolean(m.alphaDescending());
            },
            buf -> new SortMore(
                    EmptySlotMode.fromOrdinal(ByteBufCodecs.VAR_INT.decode(buf)),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    cleanCategoryOrder(ByteBufCodecs.stringUtf8(512).decode(buf)),
                    cleanModOrder(ByteBufCodecs.stringUtf8(1024).decode(buf)),
                    buf.readBoolean()));

    /** Keeps only valid, distinct category names (canonical enum names), in the order given. */
    public static String cleanCategoryOrder(String text) {
        if (text == null || text.isBlank()) return "";
        Set<String> out = new LinkedHashSet<>();
        for (String part : text.split(",")) {
            SortCategory cat = SortCategory.byName(part);
            if (cat != null) out.add(cat.name());
        }
        return String.join(",", out);
    }

    /** Keeps only valid, distinct, lower-case mod ids (at most {@link #MAX_MODS}). */
    public static String cleanModOrder(String text) {
        if (text == null || text.isBlank()) return "";
        Set<String> out = new LinkedHashSet<>();
        for (String part : text.split(",")) {
            String id = part.strip().toLowerCase(Locale.ROOT);
            if (out.size() >= MAX_MODS) break;
            if (MOD_ID.matcher(id).matches()) out.add(id);
        }
        return String.join(",", out);
    }

    public List<String> categoryList() {
        return split(categoryOrder);
    }

    public List<String> modList() {
        return split(modOrder);
    }

    private static List<String> split(String s) {
        List<String> list = new ArrayList<>();
        if (s == null || s.isBlank()) return list;
        for (String p : s.split(",")) {
            if (!p.isBlank()) list.add(p.strip());
        }
        return list;
    }

    public SortMore withEmptySlots(EmptySlotMode v) {
        return new SortMore(v, hotbarSeparate, hotbarFavoritesStay, preserveSelected, categoryOrder, modOrder, alphaDescending);
    }

    public SortMore withHotbarSeparate(boolean v) {
        return new SortMore(emptySlots, v, hotbarFavoritesStay, preserveSelected, categoryOrder, modOrder, alphaDescending);
    }

    public SortMore withHotbarFavoritesStay(boolean v) {
        return new SortMore(emptySlots, hotbarSeparate, v, preserveSelected, categoryOrder, modOrder, alphaDescending);
    }

    public SortMore withPreserveSelected(boolean v) {
        return new SortMore(emptySlots, hotbarSeparate, hotbarFavoritesStay, v, categoryOrder, modOrder, alphaDescending);
    }

    public SortMore withCategoryOrder(String v) {
        return new SortMore(emptySlots, hotbarSeparate, hotbarFavoritesStay, preserveSelected, cleanCategoryOrder(v), modOrder, alphaDescending);
    }

    public SortMore withModOrder(String v) {
        return new SortMore(emptySlots, hotbarSeparate, hotbarFavoritesStay, preserveSelected, categoryOrder, cleanModOrder(v), alphaDescending);
    }

    public SortMore withAlphaDescending(boolean v) {
        return new SortMore(emptySlots, hotbarSeparate, hotbarFavoritesStay, preserveSelected, categoryOrder, modOrder, v);
    }
}
