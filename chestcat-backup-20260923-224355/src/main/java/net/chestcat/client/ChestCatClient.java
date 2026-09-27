package net.chestcat.client;

import net.chestcat.ItemSortMode;
import net.chestcat.network.NetworkHandler;
import net.chestcat.network.RequestNearbyChestsPayload;
import net.chestcat.network.SortInventoryPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class ChestCatClient {

    public static ItemSortMode inventorySortMode = ItemSortMode.CREATIVE_ORDER;
    public static ItemSortMode chestSortMode = ItemSortMode.CREATIVE_ORDER;
    /** Sort mode used by the "Sort All Nearby" button in the C menu (NearbyChestsScreen). */
    public static ItemSortMode nearbySortMode = ItemSortMode.ITEM_TYPE;

    public static boolean locateActive = false;
    public static BlockPos locateTarget = null;

    public static final KeyMapping OPEN_MENU_KEY = new KeyMapping(
            "key.chestcat.open_menu", InputConstants.Type.KEYSYM, InputConstants.KEY_C, "key.categories.chestcat");

    public static final KeyMapping SORT_INVENTORY_KEY = new KeyMapping(
            "key.chestcat.sort_inventory", InputConstants.Type.KEYSYM, InputConstants.KEY_COMMA, "key.categories.chestcat");

    /** The action key - rebindable, fires only while ASSIGN_CATEGORY_MODIFIER_KEY is held. */
    public static final KeyMapping ASSIGN_CATEGORY_KEY = new KeyMapping(
            "key.chestcat.assign_category", InputConstants.Type.KEYSYM, InputConstants.KEY_V, "key.categories.chestcat");

    /**
     * The modifier that must be held for ASSIGN_CATEGORY_KEY to fire. Its own
     * independent, rebindable KeyMapping - defaults to Left Shift (the same
     * physical key as vanilla sneak) but is not tied to actual sneaking, so it
     * works the same whether the player uses hold-to-sneak or toggle-sneak,
     * and can be freely reassigned to any key via Controls > ChestCat.
     */
    public static final KeyMapping ASSIGN_CATEGORY_MODIFIER_KEY = new KeyMapping(
            "key.chestcat.assign_category_modifier", InputConstants.Type.KEYSYM, InputConstants.KEY_LSHIFT, "key.categories.chestcat");

    public static void startLocate(BlockPos pos) {
        locateTarget = pos;
        locateActive = true;
    }

    public static void stopLocate() {
        locateActive = false;
        locateTarget = null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;

        while (OPEN_MENU_KEY.consumeClick()) {
            if (locateActive) {
                stopLocate();
            } else {
                PacketDistributor.sendToServer(new RequestNearbyChestsPayload(NetworkHandler.DEFAULT_RADIUS));
            }
        }

        while (SORT_INVENTORY_KEY.consumeClick()) {
            PacketDistributor.sendToServer(new SortInventoryPayload(inventorySortMode));
        }

        while (ASSIGN_CATEGORY_KEY.consumeClick()) {
            if (!ASSIGN_CATEGORY_MODIFIER_KEY.isDown()) continue;
            if (mc.hitResult instanceof BlockHitResult bhr && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                var be = mc.level.getBlockEntity(bhr.getBlockPos());
                if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
                        || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity) {
                    mc.setScreen(new CategoryPickerScreen(bhr.getBlockPos(), null));
                }
            }
        }
    }
}
