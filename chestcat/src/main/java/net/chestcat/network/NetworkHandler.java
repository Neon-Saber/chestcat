package net.chestcat.network;

import net.chestcat.ChestSorter;
import net.chestcat.ChestUtil;
import net.chestcat.CustomGroups;
import net.chestcat.GroupingConfig;
import net.chestcat.InventorySorter;
import net.chestcat.ExclusionRules;
import net.chestcat.ItemCategory;
import net.chestcat.PlayerSyncEvents;
import net.chestcat.SortChain;
import net.chestcat.MenuSorting;
import net.chestcat.SortMore;
import net.chestcat.data.FavoritesData;
import net.chestcat.ItemGrouping;
import net.chestcat.SortLayoutPrefs;
import net.chestcat.data.ChestCategoryData;
import net.chestcat.data.LockedSlotsData;
import net.chestcat.data.ProtectedItemsData;
import net.chestcat.data.SortProfileData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
        registrar.playToClient(ChestCategoryUpdatePayload.TYPE, ChestCategoryUpdatePayload.STREAM_CODEC,
                (payload, context) -> net.chestcat.client.ClientPacketHandlers.handleChestCategoryUpdate(payload));
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

        // Item-level exclusions ("protected items") - client -> server toggle.
        registrar.playToServer(ToggleProtectedItemPayload.TYPE, ToggleProtectedItemPayload.STREAM_CODEC, NetworkHandler::handleToggleProtectedItem);

        // Item-based favorites, sort chain extras, and server -> client syncs.
        registrar.playToServer(ToggleFavoritePayload.TYPE, ToggleFavoritePayload.STREAM_CODEC, NetworkHandler::handleToggleFavorite);
        registrar.playToServer(SetSortExtrasPayload.TYPE, SetSortExtrasPayload.STREAM_CODEC, NetworkHandler::handleSetSortExtras);
        registrar.playToServer(SetSortMorePayload.TYPE, SetSortMorePayload.STREAM_CODEC, NetworkHandler::handleSetSortMore);
        registrar.playToClient(ApplyProfileMorePayload.TYPE, ApplyProfileMorePayload.STREAM_CODEC,
                (payload, context) -> net.chestcat.client.ClientPacketHandlers.handleApplyProfileMore(payload));
        registrar.playToClient(FavoritesSyncPayload.TYPE, FavoritesSyncPayload.STREAM_CODEC,
                (payload, context) -> net.chestcat.client.ClientPacketHandlers.handleFavoritesSync(payload));
        registrar.playToClient(SlotLocksSyncPayload.TYPE, SlotLocksSyncPayload.STREAM_CODEC,
                (payload, context) -> net.chestcat.client.ClientPacketHandlers.handleSlotLocksSync(payload));
        registrar.playToClient(ApplyProfileExtrasPayload.TYPE, ApplyProfileExtrasPayload.STREAM_CODEC,
                (payload, context) -> net.chestcat.client.ClientPacketHandlers.handleApplyProfileExtras(payload));

        // Sort profiles - client -> server requests, server -> client responses/applies.
        registrar.playToServer(SaveSortProfilePayload.TYPE, SaveSortProfilePayload.STREAM_CODEC, NetworkHandler::handleSaveSortProfile);
        registrar.playToServer(LoadSortProfilePayload.TYPE, LoadSortProfilePayload.STREAM_CODEC, NetworkHandler::handleLoadSortProfile);
        registrar.playToServer(DeleteSortProfilePayload.TYPE, DeleteSortProfilePayload.STREAM_CODEC, NetworkHandler::handleDeleteSortProfile);
        registrar.playToServer(RequestSortProfilesPayload.TYPE, RequestSortProfilesPayload.STREAM_CODEC, NetworkHandler::handleRequestSortProfiles);
        registrar.playToClient(SortProfilesResponsePayload.TYPE, SortProfilesResponsePayload.STREAM_CODEC,
                (payload, context) -> net.chestcat.client.ClientPacketHandlers.handleProfilesResponse(payload));
        registrar.playToClient(ApplyProfileModesPayload.TYPE, ApplyProfileModesPayload.STREAM_CODEC,
                (payload, context) -> net.chestcat.client.ClientPacketHandlers.handleApplyProfileModes(payload));
        registrar.playToClient(ApplyProfileLayoutPayload.TYPE, ApplyProfileLayoutPayload.STREAM_CODEC,
                (payload, context) -> net.chestcat.client.ClientPacketHandlers.handleApplyProfileLayout(payload));
        registrar.playToClient(ApplyProfileGroupingPayload.TYPE, ApplyProfileGroupingPayload.STREAM_CODEC,
                (payload, context) -> net.chestcat.client.ClientPacketHandlers.handleApplyProfileGrouping(payload));
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
                entries.add(toEntry(storage, data, grouping));
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

            // A double chest's two halves must resolve to the same saved key as
            // ChestSorter uses when actually filling it, whichever half was clicked.
            BlockPos pos = ChestUtil.canonicalChestPos(level, payload.pos());

            ChestCategoryData data = ChestCategoryData.get(level);
            if (payload.categoryName().equals("AUTO")) {
                data.clearCategory(pos);
                player.sendSystemMessage(Component.literal("Chest category reset to auto-detect.").withStyle(ChatFormatting.GRAY));
                pushSingleChestUpdate(player, level, pos, context);
                return;
            }

            if (payload.categoryName().equals("EXCLUDED")) {
                boolean nowExcluded = data.toggleExcluded(pos);
                player.sendSystemMessage((nowExcluded
                        ? Component.literal("Chest excluded - Sort All Nearby and QuickStack will skip it.").withStyle(ChatFormatting.YELLOW)
                        : Component.literal("Chest re-included in auto-sort.").withStyle(ChatFormatting.GREEN)));
                pushSingleChestUpdate(player, level, pos, context);
                return;
            }

            ItemGrouping.Key key = parseAssignableKey(payload.categoryName());
            if (key == null) return;
            data.setKey(pos, key);
            player.sendSystemMessage(Component.literal("Chest set to category: " + key.displayName()).withStyle(ChatFormatting.GREEN));
            pushSingleChestUpdate(player, level, pos, context);
        });
    }

    /** Builds one wire entry for a storage - shared by the full nearby-poll response and the
     *  single-chest instant push sent right after a category change. */
    private static NearbyChestsResponsePayload.Entry toEntry(ChestUtil.Storage storage, ChestCategoryData data, GroupingConfig.Settings grouping) {
        ItemGrouping.Key assigned = data.getKey(storage.canonicalPos()).orElse(null);
        String categoryName = assigned != null ? assigned.storageKey() : "UNASSIGNED";
        if (data.isExcluded(storage.canonicalPos())) {
            categoryName += "|EX";
        }
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

        return new NearbyChestsResponsePayload.Entry(
                storage.canonicalPos(), categoryName, autoKey.storageKey(), itemCount, free, kindTag);
    }

    /** Pushes the freshly-updated state for one chest straight back to the player who just
     *  changed it, so the floating icon updates instantly instead of waiting for the next
     *  ~5s background poll (see ChestCatClient.BACKGROUND_POLL_INTERVAL_TICKS). */
    private static void pushSingleChestUpdate(ServerPlayer player, ServerLevel level, BlockPos canonicalPos,
                                               net.neoforged.neoforge.network.handling.IPayloadContext context) {
        ChestCategoryData data = ChestCategoryData.get(level);
        GroupingConfig.Settings grouping = GroupingConfig.get(player.getUUID());
        for (ChestUtil.Storage storage : ChestUtil.findNearbyStorages(level, canonicalPos, 1)) {
            if (storage.canonicalPos().equals(canonicalPos)) {
                context.reply(new ChestCategoryUpdatePayload(toEntry(storage, data, grouping)));
                return;
            }
        }
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
            int changed = InventorySorter.sortMainInventory(player, payload.sortMode());
            if (payload.quiet()) return;
            player.displayClientMessage(changed == 0
                    ? Component.literal("Inventory already sorted.").withStyle(ChatFormatting.GRAY)
                    : Component.literal("Inventory sorted (" + changed + " slots rearranged).").withStyle(ChatFormatting.GREEN), true);
        });
    }

    private static void handleSortOpenContainer(SortOpenContainerPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            AbstractContainerMenu menu = player.containerMenu;
            if (menu == null || menu == player.inventoryMenu) return;

            // "menu:" ignore rules: a whole container type the player never wants sorted.
            String menuId = MenuSorting.menuId(menu);
            SortLayoutPrefs.Settings prefs = SortLayoutPrefs.get(player.getUUID());
            if (ExclusionRules.parse(prefs.exclusions()).matchesMenu(menuId)) {
                if (!payload.quiet()) {
                    player.displayClientMessage(Component.literal("This container type is on your ignore list.")
                            .withStyle(ChatFormatting.YELLOW), true);
                }
                return;
            }

            int changed;
            if (menu instanceof ChestMenu chestMenu) {
                Container container = chestMenu.getSlot(0).container;
                changed = ChestSorter.sortContainer(container, payload.sortMode(), player);
            } else {
                // Shulker boxes, hoppers, dispensers and modded storage: any menu whose non-player slots all
                // take ordinary items is sorted through its slots, so no per-mod support is needed.
                java.util.List<net.minecraft.world.inventory.Slot> slots = MenuSorting.storageSlots(menu, player);
                if (slots.isEmpty()) return;
                changed = ChestSorter.sortContainer(MenuSorting.view(slots), payload.sortMode(), player);
            }
            if (!payload.quiet()) {
                player.displayClientMessage(changed == 0
                        ? Component.literal("Container already sorted.").withStyle(ChatFormatting.GRAY)
                        : Component.literal("Container sorted (" + changed + " slots rearranged).").withStyle(ChatFormatting.GREEN), true);
            }
        });
    }

    private static void handleToggleSlotLock(ToggleSlotLockPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // Persisted (survives restarts) - replaces the old session-only net.chestcat.LockedSlots.
            LockedSlotsData.get(player.serverLevel()).toggle(player.getUUID(), payload.slotIndex());
            PlayerSyncEvents.sendLocks(player);
        });
    }

    private static void handleToggleFavorite(ToggleFavoritePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            List<net.minecraft.world.inventory.Slot> slots = player.containerMenu.slots;
            int idx = payload.menuSlot();
            if (idx < 0 || idx >= slots.size()) return;
            ItemStack stack = slots.get(idx).getItem();
            if (stack.isEmpty()) return;
            FavoritesData.get(player.serverLevel()).toggle(player.getUUID(), stack);
            // Always answer with the full list so the client can never drift from the server.
            PlayerSyncEvents.sendFavorites(player);
        });
    }

    private static void handleSetSortExtras(SetSortExtrasPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            SortLayoutPrefs.set(player.getUUID(), SortLayoutPrefs.get(player.getUUID()).withExtras(
                    SortChain.parse(payload.chain()).serialize(),
                    payload.favoriteMode(),
                    payload.mergeStacks(),
                    ExclusionRules.normalize(payload.exclusions())));
        });
    }

    private static void handleSetSortMore(SetSortMorePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            SortMore m = payload.more();
            // Re-clean on the server: never trust the wire for the free-text order lists.
            SortMore clean = new SortMore(m.emptySlots(), m.hotbarSeparate(), m.hotbarFavoritesStay(),
                    m.preserveSelected(), SortMore.cleanCategoryOrder(m.categoryOrder()),
                    SortMore.cleanModOrder(m.modOrder()), m.alphaDescending());
            SortLayoutPrefs.set(player.getUUID(), SortLayoutPrefs.get(player.getUUID()).withMore(clean));
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
            // Keep the chain / favorite mode / exclusions the player already sent - this packet only carries layout options.
            SortLayoutPrefs.set(player.getUUID(), SortLayoutPrefs.get(player.getUUID()).withLayout(
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

    // --- Item-level exclusions ("protected items") ---

    private static void handleToggleProtectedItem(ToggleProtectedItemPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack stack = player.getInventory().getItem(payload.slotIndex());
            if (stack.isEmpty()) return;
            boolean nowProtected = ProtectedItemsData.get(player.serverLevel()).toggle(stack);
            player.sendSystemMessage((nowProtected
                    ? Component.literal("Protected " + stack.getHoverName().getString() + " - auto-sort will never move it.").withStyle(ChatFormatting.YELLOW)
                    : Component.literal("Unprotected " + stack.getHoverName().getString() + ".").withStyle(ChatFormatting.GREEN)));
        });
    }

    // --- Sort profiles ---

    private static void handleSaveSortProfile(SaveSortProfilePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            SortLayoutPrefs.Settings layout = SortLayoutPrefs.get(player.getUUID());
            GroupingConfig.Settings grouping = GroupingConfig.get(player.getUUID());
            SortProfileData.get(level).save(player.getUUID(), payload.name(), new SortProfileData.Profile(
                    payload.inventoryMode(), payload.chestMode(), payload.nearbyMode(), layout, grouping));
            player.sendSystemMessage(Component.literal("Saved sort profile \"" + payload.name() + "\".").withStyle(ChatFormatting.GREEN));
            context.reply(new SortProfilesResponsePayload(SortProfileData.get(level).names(player.getUUID())));
        });
    }

    private static void handleLoadSortProfile(LoadSortProfilePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            SortProfileData.get(level).get(player.getUUID(), payload.name()).ifPresentOrElse(profile -> {
                // Update the server-side session state (mirrors what SetSortLayoutPayload /
                // SetGroupingConfigPayload normally do) as well as pushing it back to the client.
                SortLayoutPrefs.set(player.getUUID(), profile.layout());
                GroupingConfig.set(player.getUUID(), profile.grouping());

                context.reply(new ApplyProfileModesPayload(profile.inventoryMode(), profile.chestMode(), profile.nearbyMode()));
                context.reply(ApplyProfileLayoutPayload.of(profile.layout()));
                context.reply(ApplyProfileGroupingPayload.of(profile.grouping()));
                context.reply(ApplyProfileExtrasPayload.of(profile.layout()));
                context.reply(new ApplyProfileMorePayload(profile.layout().more()));

                player.sendSystemMessage(Component.literal("Loaded sort profile \"" + payload.name() + "\".").withStyle(ChatFormatting.GREEN));
            }, () -> player.sendSystemMessage(Component.literal("No such profile: \"" + payload.name() + "\".").withStyle(ChatFormatting.RED)));
        });
    }

    private static void handleDeleteSortProfile(DeleteSortProfilePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            SortProfileData.get(level).delete(player.getUUID(), payload.name());
            context.reply(new SortProfilesResponsePayload(SortProfileData.get(level).names(player.getUUID())));
        });
    }

    private static void handleRequestSortProfiles(RequestSortProfilesPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            context.reply(new SortProfilesResponsePayload(SortProfileData.get(level).names(player.getUUID())));
        });
    }
}
