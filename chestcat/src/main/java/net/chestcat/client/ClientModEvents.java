package net.chestcat.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ChestCatClient.OPEN_MENU_KEY);
        event.register(ChestCatClient.SORT_INVENTORY_KEY);
        event.register(ChestCatClient.ASSIGN_CATEGORY_KEY);
        event.register(ChestCatClient.ASSIGN_CATEGORY_MODIFIER_KEY);
    }
}
