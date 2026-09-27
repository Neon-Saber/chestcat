package net.chestcat.client;

import net.chestcat.network.NearbyChestsResponsePayload;
import net.minecraft.client.Minecraft;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void handleNearbyResponse(NearbyChestsResponsePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof NearbyChestsScreen existing) {
                existing.updateEntries(payload.chests());
            } else {
                mc.setScreen(new NearbyChestsScreen(payload.chests()));
            }
        });
    }
}
