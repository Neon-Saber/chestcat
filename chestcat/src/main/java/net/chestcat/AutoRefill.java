package net.chestcat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;

/**
 * When a held tool/weapon breaks, swaps in a matching item from the main
 * inventory (slots 9-35). Only fires for hand items - armor breaking uses
 * a different event and isn't covered here.
 */
@EventBusSubscriber(modid = "chestcat")
public class AutoRefill {

    @SubscribeEvent
    public static void onItemBroken(PlayerDestroyItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        InteractionHand hand = event.getHand();
        if (hand == null) return;

        ItemStack broken = event.getOriginal();
        Inventory inv = player.getInventory();

        for (int i = 9; i < 36; i++) {
            ItemStack candidate = inv.getItem(i);
            if (candidate.isEmpty() || candidate.getItem() != broken.getItem()) continue;

            inv.setItem(i, ItemStack.EMPTY);
            if (hand == InteractionHand.MAIN_HAND) {
                inv.setItem(inv.selected, candidate);
            } else {
                inv.offhand.set(0, candidate);
            }
            inv.setChanged();
            return;
        }
    }
}