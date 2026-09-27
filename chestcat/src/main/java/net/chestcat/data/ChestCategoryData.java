package net.chestcat.data;

import net.chestcat.ItemCategory;
import net.chestcat.ItemGrouping;
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
 * Per-world persistent map of chest BlockPos -> assigned grouping key (an ItemCategory,
 * optionally narrowed to a sub-type such as WOOD:oak; stored as "WOOD" or "WOOD:oak").
 *
 * Keyed by a simple "x,y,z" string within one level's storage, since each
 * dimension already has its own DimensionDataStorage / save file.
 * Double chests should be stored keyed by BOTH halves' positions pointing
 * at the same category, see ChestUtil.
 */
public class ChestCategoryData extends SavedData {

    private static final String DATA_NAME = "chestcat_categories";

    private final Map<BlockPos, ItemGrouping.Key> categories = new HashMap<>();
    private final java.util.Set<BlockPos> excluded = new java.util.HashSet<>();
    private final java.util.Set<BlockPos> whitelisted = new java.util.HashSet<>();
    private final java.util.Set<BlockPos> locked = new java.util.HashSet<>();
    /** When on, Sort Nearby and QuickStack only ever touch chests in {@link #whitelisted} -
     *  everything else is treated as excluded, regardless of the exclude set above. */
    private boolean whitelistMode = false;

    public static ChestCategoryData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(ChestCategoryData::new, ChestCategoryData::load),
                DATA_NAME
        );
    }

    public Optional<ItemGrouping.Key> getKey(BlockPos pos) {
        return Optional.ofNullable(categories.get(pos.immutable()));
    }

    public void setKey(BlockPos pos, ItemGrouping.Key key) {
        categories.put(pos.immutable(), key);
        setDirty();
    }

    public Optional<ItemCategory> getCategory(BlockPos pos) {
        return getKey(pos).map(ItemGrouping.Key::category);
    }

    public void setCategory(BlockPos pos, ItemCategory category) {
        setKey(pos, new ItemGrouping.Key(category, null));
    }

    public void clearCategory(BlockPos pos) {
        if (categories.remove(pos.immutable()) != null) {
            setDirty();
        }
    }

    /** Excluded chests are skipped by every bulk auto-sort operation (Sort All Nearby,
     * QuickStack), but a player can still open and use them completely normally. */
    public boolean isExcluded(BlockPos pos) {
        return excluded.contains(pos.immutable());
    }

    /** Flips the exclusion flag for this chest and returns the new state. */
    public boolean toggleExcluded(BlockPos pos) {
        BlockPos key = pos.immutable();
        boolean nowExcluded = excluded.add(key);
        if (!nowExcluded) excluded.remove(key);
        setDirty();
        return nowExcluded;
    }

    /** Whether a chest is on the whitelist (only meaningful, on its own, when whitelist mode is on). */
    public boolean isWhitelisted(BlockPos pos) {
        return whitelisted.contains(pos.immutable());
    }

    /** Flips whitelist membership for this chest and returns the new state. */
    public boolean toggleWhitelisted(BlockPos pos) {
        BlockPos key = pos.immutable();
        boolean nowWhitelisted = whitelisted.add(key);
        if (!nowWhitelisted) whitelisted.remove(key);
        setDirty();
        return nowWhitelisted;
    }

    /**
     * A locked chest is strict: Sort Nearby will only ever route items into it that
     * genuinely match its assigned category (or sub-type), and - critically - it is
     * never used as last-resort overflow for an unrelated leftover item just because it
     * has empty slots. Unlocked (the default, for backward compatibility) behaves exactly
     * as before: flexible, and eligible to soak up overflow when nothing else fits.
     */
    public boolean isLocked(BlockPos pos) {
        return locked.contains(pos.immutable());
    }

    /** Flips the lock flag for this chest and returns the new state. */
    public boolean toggleLocked(BlockPos pos) {
        BlockPos key = pos.immutable();
        boolean nowLocked = locked.add(key);
        if (!nowLocked) locked.remove(key);
        setDirty();
        return nowLocked;
    }

    public boolean isWhitelistMode() {
        return whitelistMode;
    }

    /** Flips whitelist-only mode for this dimension and returns the new state. */
    public boolean toggleWhitelistMode() {
        whitelistMode = !whitelistMode;
        setDirty();
        return whitelistMode;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, ItemGrouping.Key> entry : categories.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt("x", entry.getKey().getX());
            entryTag.putInt("y", entry.getKey().getY());
            entryTag.putInt("z", entry.getKey().getZ());
            entryTag.putString("category", entry.getValue().storageKey());
            list.add(entryTag);
        }
        tag.put("entries", list);

        ListTag excludedList = new ListTag();
        for (BlockPos pos : excluded) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            excludedList.add(posTag);
        }
        tag.put("excluded", excludedList);

        ListTag whitelistedList = new ListTag();
        for (BlockPos pos : whitelisted) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            whitelistedList.add(posTag);
        }
        tag.put("whitelisted", whitelistedList);
        tag.putBoolean("whitelistMode", whitelistMode);

        ListTag lockedList = new ListTag();
        for (BlockPos pos : locked) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            lockedList.add(posTag);
        }
        tag.put("locked", lockedList);
        return tag;
    }

    public static ChestCategoryData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ChestCategoryData data = new ChestCategoryData();
        ListTag list = tag.getList("entries", StringTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            BlockPos pos = new BlockPos(entryTag.getInt("x"), entryTag.getInt("y"), entryTag.getInt("z"));
            try {
                String stored = entryTag.getString("category");
                int colon = stored.indexOf(':');
                // Validates the category name (throws if it no longer exists); old saves have no ":subKey".
                ItemCategory.valueOf(colon < 0 ? stored : stored.substring(0, colon));
                data.categories.put(pos, ItemGrouping.Key.parse(stored));
            } catch (IllegalArgumentException ignored) {
                // Stored category name no longer exists (e.g. mod updated); skip it.
            }
        }

        ListTag excludedList = tag.getList("excluded", StringTag.TAG_COMPOUND);
        for (int i = 0; i < excludedList.size(); i++) {
            CompoundTag posTag = excludedList.getCompound(i);
            data.excluded.add(new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z")));
        }

        ListTag whitelistedList = tag.getList("whitelisted", StringTag.TAG_COMPOUND);
        for (int i = 0; i < whitelistedList.size(); i++) {
            CompoundTag posTag = whitelistedList.getCompound(i);
            data.whitelisted.add(new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z")));
        }
        data.whitelistMode = tag.getBoolean("whitelistMode");

        ListTag lockedList = tag.getList("locked", StringTag.TAG_COMPOUND);
        for (int i = 0; i < lockedList.size(); i++) {
            CompoundTag posTag = lockedList.getCompound(i);
            data.locked.add(new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z")));
        }
        return data;
    }
}