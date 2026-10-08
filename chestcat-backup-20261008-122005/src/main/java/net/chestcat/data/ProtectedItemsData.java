package net.chestcat.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashSet;
import java.util.Set;

/**
 * Global (not per-dimension) set of item registry ids that ChestCat's automatic
 * sorting operations - Sort Nearby, single-container sort, inventory sort,
 * QuickStack, Dump Chest / Push Into Chest - will never move. Protection is by
 * item TYPE, not a specific stack or slot: once diamonds are protected, every
 * diamond stack anywhere is left alone by automatic sorting until un-protected.
 *
 * Always stored on the overworld's DimensionDataStorage (mirrors LockedSlotsData)
 * so the same protection list applies no matter which dimension a player is in.
 */
public class ProtectedItemsData extends SavedData {

    private static final String DATA_NAME = "chestcat_protected_items";

    private final Set<String> protectedIds = new HashSet<>();

    public static ProtectedItemsData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        DimensionDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(ProtectedItemsData::new, ProtectedItemsData::load),
                DATA_NAME
        );
    }

    public boolean isProtected(ItemStack stack) {
        return !stack.isEmpty() && protectedIds.contains(idOf(stack));
    }

    /** Flips protection for this stack's item type and returns the new state. */
    public boolean toggle(ItemStack stack) {
        String id = idOf(stack);
        boolean nowProtected = protectedIds.add(id);
        if (!nowProtected) protectedIds.remove(id);
        setDirty();
        return nowProtected;
    }

    public Set<String> snapshot() {
        return Set.copyOf(protectedIds);
    }

    private static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (String id : protectedIds) {
            list.add(StringTag.valueOf(id));
        }
        tag.put("items", list);
        return tag;
    }

    public static ProtectedItemsData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ProtectedItemsData data = new ProtectedItemsData();
        ListTag list = tag.getList("items", StringTag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            data.protectedIds.add(list.getString(i));
        }
        return data;
    }
}
