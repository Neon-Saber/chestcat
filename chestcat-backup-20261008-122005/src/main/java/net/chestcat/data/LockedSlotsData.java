package net.chestcat.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent (survives restarts) per-player set of locked/favorited slot indices,
 * using vanilla Inventory's own combined slot-index scheme (0-35 main incl.
 * hotbar, 36-39 armor, 40 offhand) - whichever slot the client badge/middle-click
 * reports. Previously this was a session-only in-memory map (LockedSlots); this
 * class is its persistent replacement. Stored globally on the overworld so the
 * same locks apply no matter which dimension the player is in.
 */
public class LockedSlotsData extends SavedData {

    private static final String DATA_NAME = "chestcat_locked_slots";

    private final Map<UUID, Set<Integer>> locked = new HashMap<>();

    public static LockedSlotsData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        DimensionDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(LockedSlotsData::new, LockedSlotsData::load),
                DATA_NAME
        );
    }

    public boolean toggle(UUID player, int slotIndex) {
        Set<Integer> set = locked.computeIfAbsent(player, k -> new HashSet<>());
        boolean nowLocked = set.add(slotIndex);
        if (!nowLocked) set.remove(slotIndex);
        setDirty();
        return nowLocked;
    }

    public Set<Integer> get(UUID player) {
        return locked.getOrDefault(player, Collections.emptySet());
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Set<Integer>> entry : locked.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("player", entry.getKey());
            int[] slots = new int[entry.getValue().size()];
            int i = 0;
            for (int slot : entry.getValue()) slots[i++] = slot;
            entryTag.put("slots", new IntArrayTag(slots));
            list.add(entryTag);
        }
        tag.put("entries", list);
        return tag;
    }

    public static LockedSlotsData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        LockedSlotsData data = new LockedSlotsData();
        ListTag list = tag.getList("entries", StringTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            UUID player = entryTag.getUUID("player");
            int[] slots = entryTag.getIntArray("slots");
            Set<Integer> set = new HashSet<>();
            for (int s : slots) set.add(s);
            data.locked.put(player, set);
        }
        return data;
    }
}
