package net.chestcat.client;

import net.chestcat.ItemSortMode;
import net.chestcat.network.NetworkHandler;
import net.chestcat.network.RequestNearbyChestsPayload;
import net.chestcat.network.SortInventoryPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class ChestCatClient {

    public static ItemSortMode inventorySortMode = ItemSortMode.CREATIVE_ORDER;
    public static ItemSortMode chestSortMode = ItemSortMode.CREATIVE_ORDER;

    public static final KeyMapping OPEN_MENU_KEY = new KeyMapping(
            "key.chestcat.open_menu",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_N,
            "key.categories.chestcat"
    );

    public static final KeyMapping SORT_INVENTORY_KEY = new KeyMapping(
            "key.chestcat.sort_inventory",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_COMMA,
            "key.categories.chestcat"
    );

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (mc.screen != null) return;

        while (OPEN_MENU_KEY.consumeClick()) {
            PacketDistributor.sendToServer(new RequestNearbyChestsPayload(NetworkHandler.DEFAULT_RADIUS));
        }

        while (SORT_INVENTORY_KEY.consumeClick()) {
            PacketDistributor.sendToServer(new SortInventoryPayload(inventorySortMode));
        }
    }
}