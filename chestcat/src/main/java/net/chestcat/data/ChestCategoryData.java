package net.chestcat.data;

import net.chestcat.ItemCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-world persistent map of chest BlockPos -> assigned ItemCategory.
 *
 * Keyed by a simple "x,y,z" string within one level's storage, since each
 * dimension already has its own DimensionDataStorage / save file.
 * Double chests should be stored keyed by BOTH halves' positions pointing
 * at the same category, see ChestUtil.
 */
public class ChestCategoryData extends SavedData {

    private static final String DATA_NAME = "chestcat_categories";

    private final Map<BlockPos, ItemCategory> categories = new HashMap<>();

    public static ChestCategoryData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(ChestCategoryData::new, ChestCategoryData::load),
                DATA_NAME
        );
    }

    public Optional<ItemCategory> getCategory(BlockPos pos) {
        return Optional.ofNullable(categories.get(pos.immutable()));
    }

    public void setCategory(BlockPos pos, ItemCategory category) {
        categories.put(pos.immutable(), category);
        setDirty();
    }

    public void clearCategory(BlockPos pos) {
        if (categories.remove(pos.immutable()) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, ItemCategory> entry : categories.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt("x", entry.getKey().getX());
            entryTag.putInt("y", entry.getKey().getY());
            entryTag.putInt("z", entry.getKey().getZ());
            entryTag.putString("category", entry.getValue().name());
            list.add(entryTag);
        }
        tag.put("entries", list);
        return tag;
    }

    public static ChestCategoryData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ChestCategoryData data = new ChestCategoryData();
        ListTag list = tag.getList("entries", StringTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            BlockPos pos = new BlockPos(entryTag.getInt("x"), entryTag.getInt("y"), entryTag.getInt("z"));
            try {
                ItemCategory category = ItemCategory.valueOf(entryTag.getString("category"));
                data.categories.put(pos, category);
            } catch (IllegalArgumentException ignored) {
                // Stored category name no longer exists (e.g. mod updated); skip it.
            }
        }
        return data;
    }
}
