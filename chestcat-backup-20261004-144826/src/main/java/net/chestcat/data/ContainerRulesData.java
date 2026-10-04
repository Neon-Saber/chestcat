package net.chestcat.data;

import net.chestcat.ContainerRule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Per-world persistent store for "rule chests": chests with a hand-written multi-condition
 * {@link ContainerRule} (set via {@code /chestcat rule set <conditions>}), plus plain "locked"
 * chests that have no custom rule but must still strictly enforce their assigned category
 * (the fix for the SmartSort "Tools-only chest actually rejects everything else" requirement).
 *
 * Setting a rule always locks the chest - a rule chest that could still receive unrelated
 * overflow items would defeat the entire point of writing the rule. {@link #isRestricted} is
 * the single source of truth ChestSorter consults before ever letting an item land in a locked
 * or rule chest as a last-resort ("any container") destination; the normal category/key/rule
 * routing steps already only route matching items there in the first place.
 */
public class ContainerRulesData extends SavedData {

    private static final String DATA_NAME = "chestcat_container_rules";

    private final Map<BlockPos, ContainerRule> rules = new HashMap<>();
    private final Set<BlockPos> locked = new HashSet<>();

    public static ContainerRulesData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(ContainerRulesData::new, ContainerRulesData::load),
                DATA_NAME
        );
    }

    public Optional<ContainerRule> getRule(BlockPos pos) {
        return Optional.ofNullable(rules.get(pos.immutable()));
    }

    /** Assigns a rule to this chest. Implicitly locks it - see class doc. */
    public void setRule(BlockPos pos, ContainerRule rule) {
        BlockPos key = pos.immutable();
        rules.put(key, rule);
        locked.add(key);
        setDirty();
    }

    /** Removes this chest's rule. Leaves the lock flag as-is - unlock separately if desired. */
    public void clearRule(BlockPos pos) {
        if (rules.remove(pos.immutable()) != null) {
            setDirty();
        }
    }

    public boolean isLocked(BlockPos pos) {
        return locked.contains(pos.immutable());
    }

    /**
     * Flips the plain lock flag on a chest with no custom rule (a category-assigned chest
     * that should strictly reject anything outside its category). Returns the new state.
     * A rule chest's lock can't be turned off this way - clear the rule first.
     */
    public boolean toggleLocked(BlockPos pos) {
        BlockPos key = pos.immutable();
        if (rules.containsKey(key)) {
            return true;
        }
        boolean nowLocked = locked.add(key);
        if (!nowLocked) {
            locked.remove(key);
        }
        setDirty();
        return nowLocked;
    }

    /** True if this chest has a custom rule, or is plain-locked. Either way it must never
     *  be used as a blind "any container" overflow destination for a non-matching item. */
    public boolean isRestricted(BlockPos pos) {
        BlockPos key = pos.immutable();
        return rules.containsKey(key) || locked.contains(key);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag ruleList = new ListTag();
        for (Map.Entry<BlockPos, ContainerRule> entry : rules.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", entry.getKey().getX());
            t.putInt("y", entry.getKey().getY());
            t.putInt("z", entry.getKey().getZ());
            t.putString("rule", ContainerRule.serialize(entry.getValue()));
            ruleList.add(t);
        }
        tag.put("rules", ruleList);

        ListTag lockedList = new ListTag();
        for (BlockPos pos : locked) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", pos.getX());
            t.putInt("y", pos.getY());
            t.putInt("z", pos.getZ());
            lockedList.add(t);
        }
        tag.put("locked", lockedList);
        return tag;
    }

    public static ContainerRulesData load(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerRulesData data = new ContainerRulesData();

        ListTag ruleList = tag.getList("rules", StringTag.TAG_COMPOUND);
        for (int i = 0; i < ruleList.size(); i++) {
            CompoundTag t = ruleList.getCompound(i);
            BlockPos pos = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
            try {
                data.rules.put(pos, ContainerRule.deserialize(t.getString("rule")));
            } catch (IllegalArgumentException ignored) {
                // Malformed/stale rule string (e.g. a field that no longer exists) - drop it
                // rather than fail the whole world load.
            }
        }

        ListTag lockedList = tag.getList("locked", StringTag.TAG_COMPOUND);
        for (int i = 0; i < lockedList.size(); i++) {
            CompoundTag t = lockedList.getCompound(i);
            data.locked.add(new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")));
        }

        return data;
    }
}
