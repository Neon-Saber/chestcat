package net.chestcat.client;

import net.chestcat.ItemGrouping;
import net.chestcat.network.NearbyChestsResponsePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side cache of "which grouping key does the chest at this position hold", fed by every
 * nearby-chests response (the C menu and the silent background poll alike). It drives the
 * floating category icons and is cleared whenever the world/dimension changes.
 */
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class ClientChestCategoryCache {

    public record Entry(ItemGrouping.Key key, long lastSeenMs, boolean excluded) {}

    /** Entries not refreshed for this long are treated as gone (the poll runs every ~5 seconds). */
    public static final long EXPIRE_MS = 30_000L;
    private static final long PURGE_MS = 60_000L;
    /** Every request radius covers at least this far, so a chest that vanished inside it is really gone. */
    private static final int SURE_RADIUS = 14;

    private static final Map<BlockPos, Entry> CACHE = new HashMap<>();
    private static ClientLevel syncedLevel = null;

    /** The live cache map (main thread only). Callers may iterate it or clear() it. */
    public static Map<BlockPos, Entry> snapshot() {
        return CACHE;
    }

    /**
     * Best-effort exclusion lookup for UI labels (e.g. the C menu's toggle button). Only reflects
     * what the last nearby-chests poll saw - it's a label hint, not authoritative; the server's
     * toggle response chat message is the source of truth for what actually changed.
     */
    public static boolean isExcluded(BlockPos pos) {
        Entry e = CACHE.get(pos.immutable());
        return e != null && e.excluded();
    }

    public static void update(List<NearbyChestsResponsePayload.Entry> chests, BlockPos playerPos) {
        long now = System.currentTimeMillis();
        Set<BlockPos> seen = new HashSet<>();

        for (NearbyChestsResponsePayload.Entry e : chests) {
            if ("ENDER_CHEST".equals(e.kindTag())) continue;

            // categoryName carries a "|EX" flag suffix for excluded chests (see NetworkHandler) -
            // strip it before treating the rest as a plain storage key.
            boolean excluded = e.categoryName().contains("|EX");
            String baseCategoryName = excluded
                    ? e.categoryName().substring(0, e.categoryName().indexOf('|'))
                    : e.categoryName();

            ItemGrouping.Key key;
            if (!"UNASSIGNED".equals(baseCategoryName)) {
                key = ItemGrouping.Key.parse(baseCategoryName);
            } else if (e.itemCount() > 0) {
                key = ItemGrouping.Key.parse(e.autoCategoryName());
            } else if (excluded) {
                // Unassigned and empty, but excluded - still worth caching so the C menu and
                // nearby list show its excluded state correctly; the indicator renderer skips
                // excluded chests regardless of which key ends up here.
                key = ItemGrouping.Key.parse(e.autoCategoryName());
            } else {
                continue; // unassigned, empty, and not excluded: nothing meaningful to show
            }

            BlockPos pos = e.pos().immutable();
            seen.add(pos);
            CACHE.put(pos, new Entry(key, now, excluded));
        }

        CACHE.entrySet().removeIf(entry -> {
            if (now - entry.getValue().lastSeenMs() > PURGE_MS) return true;
            BlockPos p = entry.getKey();
            return !seen.contains(p)
                    && Math.abs(p.getX() - playerPos.getX()) <= SURE_RADIUS
                    && Math.abs(p.getY() - playerPos.getY()) <= SURE_RADIUS
                    && Math.abs(p.getZ() - playerPos.getZ()) <= SURE_RADIUS;
        });
    }

    /**
     * Pushes the client's sort + Category Splitting settings to the server once per world join, so
     * keybind sorts use them even if the player never opens the options screen this session.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            syncedLevel = null;
            return;
        }
        if (mc.level != syncedLevel) {
            syncedLevel = mc.level;
            SortSettings.sync();
        }
    }
}
