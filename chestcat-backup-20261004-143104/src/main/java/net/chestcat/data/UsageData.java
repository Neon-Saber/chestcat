package net.chestcat.data;

import net.chestcat.SortContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player item usage history that powers the "Recently Acquired", "Recently Used", "Most Used" and
 * "Least Used" sort keys: for every item type, when it was last picked up / crafted, when it was last
 * used, and how many times. Stored on the overworld like the other ChestCat player data, capped per player
 * so it can never grow without bound, and held in memory keyed by {@link Item} so comparisons are plain
 * hash lookups.
 */
public class UsageData extends SavedData {

    private static final String DATA_NAME = "chestcat_usage";
    private static final int MAX_ITEMS_PER_PLAYER = 2048;

    public static final class Entry {
        long acquired;
        long used;
        int uses;

        long lastActivity() {
            return Math.max(acquired, used);
        }
    }

    private final Map<UUID, Map<Item, Entry>> players = new HashMap<>();

    public static UsageData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        DimensionDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(UsageData::new, UsageData::load),
                DATA_NAME
        );
    }

    private Entry entry(UUID player, Item item) {
        Map<Item, Entry> map = players.computeIfAbsent(player, k -> new HashMap<>());
        Entry e = map.get(item);
        if (e != null) return e;
        if (map.size() >= MAX_ITEMS_PER_PLAYER) evictOldest(map);
        e = new Entry();
        map.put(item, e);
        return e;
    }

    private static void evictOldest(Map<Item, Entry> map) {
        Item oldest = null;
        long oldestTime = Long.MAX_VALUE;
        for (Map.Entry<Item, Entry> e : map.entrySet()) {
            long t = e.getValue().lastActivity();
            if (t < oldestTime) {
                oldestTime = t;
                oldest = e.getKey();
            }
        }
        if (oldest != null) map.remove(oldest);
    }

    /** The player picked up or crafted this item. */
    public void recordAcquired(UUID player, Item item, long gameTime) {
        if (item == Items.AIR) return;
        entry(player, item).acquired = gameTime;
        setDirty();
    }

    /** The player used this item (swung, placed, ate, right-clicked). Repeat calls within one tick count once. */
    public void recordUsed(UUID player, Item item, long gameTime) {
        if (item == Items.AIR) return;
        Entry e = entry(player, item);
        if (e.used == gameTime && e.uses > 0) return;
        e.used = gameTime;
        e.uses++;
        setDirty();
    }

    /** Read-only view for sort comparators. Items never seen read as 0. */
    public SortContext.UsageView view(UUID player) {
        Map<Item, Entry> map = players.get(player);
        if (map == null || map.isEmpty()) {
            return new SortContext.UsageView() {
                @Override
                public long acquired(Item item) {
                    return 0;
                }

                @Override
                public long used(Item item) {
                    return 0;
                }

                @Override
                public int uses(Item item) {
                    return 0;
                }
            };
        }
        return new SortContext.UsageView() {
            @Override
            public long acquired(Item item) {
                Entry e = map.get(item);
                return e == null ? 0 : e.acquired;
            }

            @Override
            public long used(Item item) {
                Entry e = map.get(item);
                return e == null ? 0 : e.used;
            }

            @Override
            public int uses(Item item) {
                Entry e = map.get(item);
                return e == null ? 0 : e.uses;
            }
        };
    }

    /** Wipes one player's history (used by the reset command / future UI). */
    public void clear(UUID player) {
        if (players.remove(player) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Map<Item, Entry>> pe : players.entrySet()) {
            if (pe.getValue().isEmpty()) continue;
            CompoundTag p = new CompoundTag();
            p.putUUID("player", pe.getKey());
            ListTag items = new ListTag();
            for (Iterator<Map.Entry<Item, Entry>> it = pe.getValue().entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Item, Entry> ie = it.next();
                CompoundTag t = new CompoundTag();
                t.putString("id", BuiltInRegistries.ITEM.getKey(ie.getKey()).toString());
                t.putLong("a", ie.getValue().acquired);
                t.putLong("u", ie.getValue().used);
                t.putInt("n", ie.getValue().uses);
                items.add(t);
            }
            p.put("items", items);
            list.add(p);
        }
        tag.put("players", list);
        return tag;
    }

    public static UsageData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        UsageData data = new UsageData();
        ListTag list = tag.getList("players", StringTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag p = list.getCompound(i);
            Map<Item, Entry> map = new HashMap<>();
            ListTag items = p.getList("items", StringTag.TAG_COMPOUND);
            for (int j = 0; j < items.size() && map.size() < MAX_ITEMS_PER_PLAYER; j++) {
                CompoundTag t = items.getCompound(j);
                ResourceLocation id = ResourceLocation.tryParse(t.getString("id"));
                if (id == null) continue;
                // Items from a mod that was removed simply drop out of the history.
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> {
                    Entry e = new Entry();
                    e.acquired = t.getLong("a");
                    e.used = t.getLong("u");
                    e.uses = t.getInt("n");
                    map.put(item, e);
                });
            }
            data.players.put(p.getUUID("player"), map);
        }
        return data;
    }
}
