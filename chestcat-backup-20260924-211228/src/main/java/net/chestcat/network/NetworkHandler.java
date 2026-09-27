package net.chestcat.network;

import net.chestcat.ChestSorter;
import net.chestcat.ChestUtil;
import net.chestcat.CustomGroups;
import net.chestcat.GroupingConfig;
import net.chestcat.InventorySorter;
import net.chestcat.ItemCategory;
import net.chestcat.ItemGrouping;
import net.chestcat.data.ChestCategoryData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = "chestcat", bus = EventBusSubscriber.Bus.MOD)
public class NetworkHandler {

    public static final int DEFAULT_RADIUS = 16;

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(RequestNearbyChestsPayload.TYPE, RequestNearbyChestsPayload.STREAM_CODEC, NetworkHandler::handleRequestNearby);
        registrar.playToClient(NearbyChestsResponsePayload.TYPE, NearbyChestsResponsePayload.STREAM_CODEC,
                (payload, context) -> net.chestcat.client.ClientPacketHandlers.handleNearbyResponse(payload));
        registrar.playToServer(AssignCategoryPayload.TYPE, AssignCategoryPayload.STREAM_CODEC, NetworkHandler::handleAssignCategory);
        registrar.playToServer(SortNearbyPayload.TYPE, SortNearbyPayload.STREAM_CODEC, NetworkHandler::handleSortNearby);
        registrar.playToServer(SortInventoryPayload.TYPE, SortInventoryPayload.STREAM_CODEC, NetworkHandler::handleSortInventory);
        registrar.playToServer(SortOpenContainerPayload.TYPE, SortOpenContainerPayload.STREAM_CODEC, NetworkHandler::handleSortOpenContainer);
        registrar.playToServer(ToggleSlotLockPayload.TYPE, ToggleSlotLockPayload.STREAM_CODEC, NetworkHandler::handleToggleSlotLock);
        registrar.playToServer(QuickStackPayload.TYPE, QuickStackPayload.STREAM_CODEC, NetworkHandler::handleQuickStack);
        registrar.playToServer(SortIntoChestPayload.TYPE, SortIntoChestPayload.STREAM_CODEC, NetworkHandler::handleSortIntoChest);
        registrar.playToServer(SetSortLayoutPayload.TYPE, SetSortLayoutPayload.STREAM_CODEC, NetworkHandler::handleToggleColumnFill);
        registrar.playToServer(DumpChestPayload.TYPE, DumpChestPayload.STREAM_CODEC, NetworkHandler::handleDumpChest);
        registrar.playToServer(SetGroupingConfigPayload.TYPE, SetGroupingConfigPayload.STREAM_CODEC, NetworkHandler::handleSetGroupingConfig);
    }

    private static void handleRequestNearby(RequestNearbyChestsPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            ChestCategoryData data = ChestCategoryData.get(level);
            GroupingConfig.Settings grouping = GroupingConfig.get(player.getUUID());

            List<NearbyChestsResponsePayload.Entry> entries = new ArrayList<>();

            List<ChestUtil.Storage> storages = ChestUtil.findNearbyStorages(level, player.blockPosition(), payload.radius());
            for (ChestUtil.Storage storage : storages) {
                ItemGrouping.Key assigned = data.getKey(storage.canonicalPos()).orElse(null);
                String categoryName = assigned != null ? assigned.storageKey() : "UNASSIGNED";
                ItemGrouping.Key autoKey = ChestSorter.detectDominantKey(storage.container(), grouping);

                int itemCount = 0;
                for (int i = 0; i < storage.container().getContainerSize(); i++) {
                    itemCount += storage.container().getItem(i).getCount();
                }
                int free = ChestUtil.freeCapacityEstimate(storage.container());

                String kindTag = switch (storage.kind()) {
                    case CHEST -> "CHEST";
                    case BARREL -> "BARREL";
                    case MODDED -> "MODDED:" + storage.modId();
                };

                entries.add(new NearbyChestsResponsePayload.Entry(
                        storage.canonicalPos(), categoryName, autoKey.storageKey(), itemCount, free, kindTag));
            }

            for (BlockPos enderPos : ChestUtil.findNearbyEnderChests(level, player.blockPosition(), payload.radius())) {
                Container enderInv = player.getEnderChestInventory();
                int itemCount = 0;
                int free = 0;
                for (int i = 0; i < enderInv.getContainerSize(); i++) {
                    ItemStack stack = enderInv.getItem(i);
                    if (stack.isEmpty()) free++; else itemCount += stack.getCount();
                }
                entries.add(new NearbyChestsResponsePayload.Entry(
                        enderPos, "UNASSIGNED", "MISC", itemCount, free, "ENDER_CHEST"));
            }

            context.reply(new NearbyChestsResponsePayload(entries));
        });
    }

    private static void handleAssignCategory(AssignCategoryPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            BlockEntity be = level.getBlockEntity(payload.pos());
            if (be == null) return;

            ChestCategoryData data = ChestCategoryData.get(level);
            if (payload.categoryName().equals("AUTO")) {
                data.clearCategory(payload.pos());
                player.sendSystemMessage(Component.literal("Chest category reset to auto-detect.").withStyle(ChatFormatting.GRAY));
                return;
            }

            ItemGrouping.Key key = parseAssignableKey(payload.categoryName());
            if (key == null) return;
            data.setKey(payload.pos(), key);
            player.sendSystemMessage(Component.literal("Chest set to category: " + key.displayName()).withStyle(ChatFormatting.GREEN));
        });
    }

    /** Parses "WOOD" or "WOOD:oak"; returns null for an unknown category or a sub-type that category can't split into. */
    private static ItemGrouping.Key parseAssignableKey(String stored) {
        int colon = stored.indexOf(':');
        String categoryName = colon < 0 ? stored : stored.substring(0, colon);
        String subKey = colon < 0 ? null : stored.substring(colon + 1);
        try {
            ItemCategory category = ItemCategory.valueOf(categoryName);
            if (category == ItemCategory.CUSTOM) {
                CustomGroups.refresh();
                return subKey != null && CustomGroups.hasGroup(subKey) ? new ItemGrouping.Key(category, subKey) : null;
            }
            if (subKey != null && !ItemGrouping.isValidSubKey(category, subKey)) return null;
            return new ItemGrouping.Key(category, subKey);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void handleSortNearby(SortNearbyPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ChestSorter.Result result = ChestSorter.sortNearby(player, payload.radius(), payload.sortMode());
            Component msg = Component.literal(
                    "Sorted " + result.itemsMoved() + " items across " + result.chestsTouched() + " chests"
                            + (result.itemsDropped() > 0 ? " (" + result.itemsDropped() + " dropped, no room)" : "") + "."
            ).withStyle(result.itemsDropped() > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
            player.sendSystemMessage(msg);
        });
    }

    private static void handleSortInventory(SortInventoryPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            int count = InventorySorter.sortMainInventory(player, payload.sortMode());
            player.sendSystemMessage(Component.literal("Inventory sorted (" + count + " stacks).").withStyle(ChatFormatting.GREEN));
        });
    }

    private static void handleSortOpenContainer(SortOpenContainerPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.containerMenu instanceof ChestMenu chestMenu) {
                Container container = chestMenu.getSlot(0).container;
                ChestSorter.sortContainer(container, payload.sortMode(), player.getUUID());
            }
        });
    }

    private static void handleToggleSlotLock(ToggleSlotLockPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            net.chestcat.LockedSlots.toggle(player.getUUID(), payload.slotIndex());
        });
    }

    private static void handleQuickStack(QuickStackPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            net.chestcat.QuickStack.run(player);
        });
    }

    private static void handleSortIntoChest(SortIntoChestPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            net.chestcat.DumpIntoChest.run(player);
        });
    }

    private static void handleToggleColumnFill(SetSortLayoutPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            net.chestcat.SortLayoutPrefs.set(player.getUUID(), new net.chestcat.SortLayoutPrefs.Settings(
                    payload.layout(), payload.reverse(), payload.includeHotbar(),
                    payload.groupArmorBySlot(), payload.tiebreakPriority(), payload.floatEnchantedFirst()));
        });
    }

    private static void handleSetGroupingConfig(SetGroupingConfigPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            GroupingConfig.set(player.getUUID(), payload.toSettings());
        });
    }

    private static void handleDumpChest(DumpChestPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            net.chestcat.DumpChestIntoInventory.run(player);
        });
    }
}
