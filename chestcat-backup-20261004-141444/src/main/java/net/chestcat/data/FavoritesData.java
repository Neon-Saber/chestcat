package net.chestcat.data;

import net.chestcat.FavoriteKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player favorited items, keyed by {@link FavoriteKey} (item identity, never a slot),
 * so a favorite follows the item through sorting, chests and the hotbar. Stored on the
 * overworld (like the lock/protection data) so it is the same in every dimension.
 */
public class FavoritesData extends SavedData {

    private static final String DATA_NAME = "chestcat_favorites";
    private static final int MAX_PER_PLAYER = 512;

    private final Map<UUID, Set<String>> favorites = new HashMap<>();

    public static FavoritesData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        DimensionDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(FavoritesData::new, FavoritesData::load),
                DATA_NAME
        );
    }

    public boolean isFavorite(UUID player, ItemStack stack) {
        if (stack.isEmpty()) return false;
        Set<String> set = favorites.get(player);
        return set != null && !set.isEmpty() && set.contains(FavoriteKey.of(stack));
    }

    /** Flips the favorite state of this item and returns the new state. */
    public boolean toggle(UUID player, ItemStack stack) {
        if (stack.isEmpty()) return false;
        Set<String> set = favorites.computeIfAbsent(player, k -> new HashSet<>());
        String key = FavoriteKey.of(stack);
        if (set.remove(key)) {
            setDirty();
            return false;
        }
        if (set.size() >= MAX_PER_PLAYER) return false;
        set.add(key);
        setDirty();
        return true;
    }

    public List<String> snapshot(UUID player) {
        Set<String> set = favorites.get(player);
        if (set == null) return List.of();
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Set<String>> e : favorites.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            CompoundTag p = new CompoundTag();
            p.putUUID("player", e.getKey());
            ListTag items = new ListTag();
            for (String key : e.getValue()) items.add(StringTag.valueOf(key));
            p.put("items", items);
            players.add(p);
        }
        tag.put("players", players);
        return tag;
    }

    public static FavoritesData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        FavoritesData data = new FavoritesData();
        ListTag players = tag.getList("players", StringTag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag p = players.getCompound(i);
            Set<String> set = new HashSet<>();
            ListTag items = p.getList("items", StringTag.TAG_STRING);
            for (int j = 0; j < items.size(); j++) set.add(items.getString(j));
            data.favorites.put(p.getUUID("player"), set);
        }
        return data;
    }
}
