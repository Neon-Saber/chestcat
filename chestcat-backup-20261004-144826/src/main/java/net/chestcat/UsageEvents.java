package net.chestcat;

import net.chestcat.data.UsageData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Feeds {@link UsageData}: what the player picks up, crafts and uses. Runs only on actual player actions
 * (a few events per second at most) and never scans an inventory, so it adds no per-tick cost.
 */
@EventBusSubscriber(modid = "chestcat")
public final class UsageEvents {

    private UsageEvents() {}

    private static void acquired(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        UsageData.get(player.serverLevel()).recordAcquired(player.getUUID(), stack.getItem(), player.level().getGameTime());
    }

    private static void used(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        UsageData.get(player.serverLevel()).recordUsed(player.getUUID(), stack.getItem(), player.level().getGameTime());
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player) acquired(player, event.getOriginalStack());
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) acquired(player, event.getCrafting());
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) used(player, event.getItemStack());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) used(player, event.getItemStack());
    }

    @SubscribeEvent
    public static void onUseFinished(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) used(player, event.getItem());
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) used(player, player.getMainHandItem());
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) used(player, player.getMainHandItem());
    }
}
