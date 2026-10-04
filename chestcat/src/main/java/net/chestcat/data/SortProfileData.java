package net.chestcat.data;

import net.chestcat.GroupingConfig;
import net.chestcat.ItemCategory;
import net.chestcat.ItemSortMode;
import net.chestcat.SortLayout;
import net.chestcat.SortLayoutPrefs;
import net.chestcat.TiebreakPriority;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Named, per-player bundles of ChestCat's sort settings: the inventory/chest/nearby sort
 * modes, layout + tiebreak preferences, and category-splitting config. Lets a player save
 * a setup ("Mining", "Building", ...) and switch back to it instead of re-configuring
 * everything by hand each time.
 *
 * Deliberately does NOT capture per-chest category assignments, exclusions, the whitelist,
 * or protected items - those describe specific chests/items in the world, not a player
 * preference, so they wouldn't make sense to "switch" between profiles.
 *
 * Stored globally on the overworld (mirrors ProtectedItemsData / LockedSlotsData) so a
 * player's profiles are the same regardless of which dimension they're in.
 */
public class SortProfileData extends SavedData {

    private static final String DATA_NAME = "chestcat_sort_profiles";
    private static final int MAX_PROFILES_PER_PLAYER = 20;
    private static final int MAX_NAME_LENGTH = 32;

    public record Profile(ItemSortMode inventoryMode, ItemSortMode chestMode, ItemSortMode nearbyMode,
                          SortLayoutPrefs.Settings layout, GroupingConfig.Settings grouping) {}

    private final Map<UUID, Map<String, Profile>> profiles = new HashMap<>();

    public static SortProfileData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        DimensionDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(SortProfileData::new, SortProfileData::load),
                DATA_NAME
        );
    }

    public List<String> names(UUID player) {
        Map<String, Profile> map = profiles.get(player);
        if (map == null) return List.of();
        List<String> list = new ArrayList<>(map.keySet());
        Collections.sort(list);
        return list;
    }

    /** Saves (or overwrites) a named profile from the given settings. Silently truncates an
     *  overlong name and refuses once a player already has MAX_PROFILES_PER_PLAYER saved
     *  (unless overwriting one of their existing names). */
    public void save(UUID player, String name, Profile profile) {
        String trimmed = name.isBlank() ? "Unnamed" : name.strip();
        if (trimmed.length() > MAX_NAME_LENGTH) trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        Map<String, Profile> map = profiles.computeIfAbsent(player, k -> new HashMap<>());
        if (!map.containsKey(trimmed) && map.size() >= MAX_PROFILES_PER_PLAYER) return;
        map.put(trimmed, profile);
        setDirty();
    }

    public java.util.Optional<Profile> get(UUID player, String name) {
        Map<String, Profile> map = profiles.get(player);
        return map == null ? java.util.Optional.empty() : java.util.Optional.ofNullable(map.get(name));
    }

    public void delete(UUID player, String name) {
        Map<String, Profile> map = profiles.get(player);
        if (map != null && map.remove(name) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag playersList = new ListTag();
        for (Map.Entry<UUID, Map<String, Profile>> playerEntry : profiles.entrySet()) {
            if (playerEntry.getValue().isEmpty()) continue;
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("player", playerEntry.getKey());

            ListTag profilesList = new ListTag();
            for (Map.Entry<String, Profile> profileEntry : playerEntry.getValue().entrySet()) {
                profilesList.add(writeProfile(profileEntry.getKey(), profileEntry.getValue()));
            }
            playerTag.put("profiles", profilesList);
            playersList.add(playerTag);
        }
        tag.put("players", playersList);
        return tag;
    }

    private static CompoundTag writeProfile(String name, Profile profile) {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("inventoryMode", profile.inventoryMode().name());
        t.putString("chestMode", profile.chestMode().name());
        t.putString("nearbyMode", profile.nearbyMode().name());

        SortLayoutPrefs.Settings layout = profile.layout();
        t.putString("layout", layout.layout().name());
        t.putBoolean("reverse", layout.reverse());
        t.putBoolean("includeHotbar", layout.includeHotbar());
        t.putBoolean("groupArmorBySlot", layout.groupArmorBySlot());
        t.putString("tiebreak", layout.tiebreakPriority().name());
        t.putBoolean("floatEnchantedFirst", layout.floatEnchantedFirst());
        t.putString("chain", layout.chain());
        t.putString("favoriteMode", layout.favoriteMode().name());
        t.putBoolean("mergeStacks", layout.mergeStacks());
        t.putString("exclusions", layout.exclusions());

        net.chestcat.SortMore more = layout.more();
        t.putString("emptySlots", more.emptySlots().name());
        t.putBoolean("hotbarSeparate", more.hotbarSeparate());
        t.putBoolean("hotbarFavoritesStay", more.hotbarFavoritesStay());
        t.putBoolean("preserveSelected", more.preserveSelected());
        t.putString("categoryOrder", more.categoryOrder());
        t.putString("modOrder", more.modOrder());
        t.putBoolean("alphaDescending", more.alphaDescending());

        GroupingConfig.Settings grouping = profile.grouping();
        t.putBoolean("splitOres", grouping.splitOres());
        t.putBoolean("splitWood", grouping.splitWood());
        t.putBoolean("splitStone", grouping.splitStone());
        ListTag disabled = new ListTag();
        for (ItemCategory c : grouping.disabledCategories()) disabled.add(StringTag.valueOf(c.name()));
        t.put("disabledCategories", disabled);
        ListTag merged = new ListTag();
        for (String key : grouping.mergedSubKeys()) merged.add(StringTag.valueOf(key));
        t.put("mergedSubKeys", merged);
        return t;
    }

    private static Profile readProfile(CompoundTag t) {
        ItemSortMode inventoryMode = enumOr(t.getString("inventoryMode"), ItemSortMode.class, ItemSortMode.CREATIVE_ORDER);
        ItemSortMode chestMode = enumOr(t.getString("chestMode"), ItemSortMode.class, ItemSortMode.CREATIVE_ORDER);
        ItemSortMode nearbyMode = enumOr(t.getString("nearbyMode"), ItemSortMode.class, ItemSortMode.ITEM_TYPE);

        SortLayout sortLayout = enumOr(t.getString("layout"), SortLayout.class, SortLayout.ROWS);
        TiebreakPriority tiebreak = enumOr(t.getString("tiebreak"), TiebreakPriority.class, TiebreakPriority.TYPE_FIRST);
        // Profiles saved before the multi-level sort update have no chain/favorite fields - fall back to stock behaviour.
        String chain = t.contains("chain") ? t.getString("chain") : net.chestcat.SortChain.DEFAULT_STRING;
        net.chestcat.FavoriteMode favoriteMode = enumOr(t.getString("favoriteMode"),
                net.chestcat.FavoriteMode.class, net.chestcat.FavoriteMode.PIN);
        boolean mergeStacks = !t.contains("mergeStacks") || t.getBoolean("mergeStacks");
        // Profiles saved before the extra options existed simply have none of these tags - stock behaviour.
        net.chestcat.SortMore more = new net.chestcat.SortMore(
                enumOr(t.getString("emptySlots"), net.chestcat.EmptySlotMode.class, net.chestcat.EmptySlotMode.LAST),
                t.getBoolean("hotbarSeparate"), t.getBoolean("hotbarFavoritesStay"), t.getBoolean("preserveSelected"),
                net.chestcat.SortMore.cleanCategoryOrder(t.getString("categoryOrder")),
                net.chestcat.SortMore.cleanModOrder(t.getString("modOrder")),
                t.getBoolean("alphaDescending"));
        SortLayoutPrefs.Settings layout = new SortLayoutPrefs.Settings(sortLayout, t.getBoolean("reverse"),
                t.getBoolean("includeHotbar"), t.getBoolean("groupArmorBySlot"), tiebreak, t.getBoolean("floatEnchantedFirst"),
                chain, favoriteMode, mergeStacks, t.getString("exclusions"), more);

        Set<ItemCategory> disabled = EnumSet.noneOf(ItemCategory.class);
        ListTag disabledList = t.getList("disabledCategories", StringTag.TAG_STRING);
        for (int i = 0; i < disabledList.size(); i++) {
            try {
                disabled.add(ItemCategory.valueOf(disabledList.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        Set<String> merged = new HashSet<>();
        ListTag mergedList = t.getList("mergedSubKeys", StringTag.TAG_STRING);
        for (int i = 0; i < mergedList.size(); i++) merged.add(mergedList.getString(i));

        GroupingConfig.Settings grouping = new GroupingConfig.Settings(
                t.getBoolean("splitOres"), t.getBoolean("splitWood"), t.getBoolean("splitStone"), disabled, merged);

        return new Profile(inventoryMode, chestMode, nearbyMode, layout, grouping);
    }

    private static <E extends Enum<E>> E enumOr(String name, Class<E> type, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static SortProfileData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        SortProfileData data = new SortProfileData();
        ListTag playersList = tag.getList("players", StringTag.TAG_COMPOUND);
        for (int i = 0; i < playersList.size(); i++) {
            CompoundTag playerTag = playersList.getCompound(i);
            UUID player = playerTag.getUUID("player");
            Map<String, Profile> map = new HashMap<>();
            ListTag profilesList = playerTag.getList("profiles", StringTag.TAG_COMPOUND);
            for (int j = 0; j < profilesList.size(); j++) {
                CompoundTag profileTag = profilesList.getCompound(j);
                map.put(profileTag.getString("name"), readProfile(profileTag));
            }
            data.profiles.put(player, map);
        }
        return data;
    }
}
