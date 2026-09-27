package net.chestcat.client;

import net.chestcat.network.ApplyProfileGroupingPayload;
import net.chestcat.network.ApplyProfileLayoutPayload;
import net.chestcat.network.ApplyProfileModesPayload;
import net.chestcat.network.ChestCategoryUpdatePayload;
import net.chestcat.network.NearbyChestsResponsePayload;
import net.chestcat.network.NetworkHandler;
import net.chestcat.network.SortProfilesResponsePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    /** Last list of saved profile names the server sent - SortProfilesScreen reads this
     *  after requesting a refresh, and it's re-populated after every save/load/delete too. */
    public static List<String> latestProfileNames = List.of();

    public static void handleProfilesResponse(SortProfilesResponsePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            latestProfileNames = payload.names();
            if (mc.screen instanceof SortProfilesScreen screen) {
                screen.updateNames(payload.names());
            }
        });
    }

    public static void handleApplyProfileLayout(ApplyProfileLayoutPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            SortSettings.layout = payload.layout();
            SortSettings.reverse = payload.reverse();
            SortSettings.includeHotbar = payload.includeHotbar();
            SortSettings.groupArmorBySlot = payload.groupArmorBySlot();
            SortSettings.tiebreakPriority = payload.tiebreakPriority();
            SortSettings.floatEnchantedFirst = payload.floatEnchantedFirst();
        });
    }

    public static void handleApplyProfileGrouping(ApplyProfileGroupingPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            Set<net.chestcat.ItemCategory> disabled = new HashSet<>();
            for (String name : payload.disabledCategories()) {
                try {
                    disabled.add(net.chestcat.ItemCategory.valueOf(name));
                } catch (IllegalArgumentException ignored) {
                }
            }
            GroupingSettings.current = new net.chestcat.GroupingConfig.Settings(
                    payload.splitOres(), payload.splitWood(), payload.splitStone(),
                    disabled, new HashSet<>(payload.mergedSubKeys()));
        });
    }

    public static void handleApplyProfileModes(ApplyProfileModesPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ChestCatClient.inventorySortMode = payload.inventoryMode();
            ChestCatClient.chestSortMode = payload.chestMode();
            ChestCatClient.nearbySortMode = payload.nearbyMode();
        });
    }

    public static void handleChestCategoryUpdate(ChestCategoryUpdatePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> ClientChestCategoryCache.updateOne(payload.entry()));
    }

    public static void handleNearbyResponse(NearbyChestsResponsePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            BlockPos playerPos = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;

            // Every response (menu request or silent background poll) refreshes the floating icons.
            ClientChestCategoryCache.update(payload.chests(), playerPos);

            // The menu only ever lists the sort radius, even when a wider background poll answered.
            List<NearbyChestsResponsePayload.Entry> menuEntries = withinMenuRadius(payload.chests(), playerPos);

            if (mc.screen instanceof NearbyChestsScreen existing) {
                existing.updateEntries(menuEntries);
            } else if (ChestCatClient.pendingMenuOpen) {
                ChestCatClient.pendingMenuOpen = false;
                mc.setScreen(new NearbyChestsScreen(menuEntries));
            }
        });
    }

    private static List<NearbyChestsResponsePayload.Entry> withinMenuRadius(
            List<NearbyChestsResponsePayload.Entry> all, BlockPos center) {
        int r = NetworkHandler.DEFAULT_RADIUS;
        List<NearbyChestsResponsePayload.Entry> result = new ArrayList<>();
        for (NearbyChestsResponsePayload.Entry e : all) {
            BlockPos p = e.pos();
            if (Math.abs(p.getX() - center.getX()) <= r
                    && Math.abs(p.getY() - center.getY()) <= r
                    && Math.abs(p.getZ() - center.getZ()) <= r) {
                result.add(e);
            }
        }
        return result;
    }
}
