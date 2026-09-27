package net.chestcat.client;

import net.chestcat.network.NearbyChestsResponsePayload;
import net.chestcat.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

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
