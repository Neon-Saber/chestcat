package net.chestcat;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Session-only (not saved to disk) per-player set of locked main-inventory slot indices. */
public final class LockedSlots {

    private static final Map<UUID, Set<Integer>> LOCKED = new HashMap<>();

    private LockedSlots() {}

    public static boolean toggle(UUID player, int slotIndex) {
        Set<Integer> set = LOCKED.computeIfAbsent(player, k -> new HashSet<>());
        if (set.contains(slotIndex)) {
            set.remove(slotIndex);
            return false;
        }
        set.add(slotIndex);
        return true;
    }

    public static Set<Integer> get(UUID player) {
        return LOCKED.getOrDefault(player, Collections.emptySet());
    }
}