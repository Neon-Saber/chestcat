package net.chestcat;

import net.chestcat.data.FavoritesData;
import net.chestcat.data.LockedSlotsData;
import net.chestcat.network.FavoritesSyncPayload;
import net.chestcat.network.SlotLocksSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;

/** Sends the player's saved favorites and slot locks to their client on login, and frees per-session state on logout. */
@EventBusSubscriber(modid = "chestcat")
public final class PlayerSyncEvents {

    private PlayerSyncEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        sendFavorites(player);
        sendLocks(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SortLayoutPrefs.forget(player.getUUID());
        }
    }

    public static void sendFavorites(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new FavoritesSyncPayload(FavoritesData.get(player.serverLevel()).snapshot(player.getUUID())));
    }

    public static void sendLocks(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SlotLocksSyncPayload(
                new ArrayList<>(LockedSlotsData.get(player.serverLevel()).get(player.getUUID()))));
    }
}
