package net.chestcat.client;

import net.chestcat.ItemSortMode;
import net.chestcat.network.NetworkHandler;
import net.chestcat.network.RequestNearbyChestsPayload;
import net.chestcat.network.SortInventoryPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
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
    /** Outline box for locateTarget - spans both halves when it's a double chest, so the
     *  highlight isn't cut in half. Computed once in startLocate() and reused every frame. */
    public static AABB locateBox = null;

    /** True for the one round trip right after the player explicitly opens the C
     *  menu, so ClientPacketHandlers knows to open/refresh the screen instead of
     *  treating the response as just another silent background icon-cache refresh. */
    public static boolean pendingMenuOpen = false;

    // Silent poll so the floating chest-category icons (see ChestCatIndicatorRenderer)
    // stay reasonably fresh even when the player never opens the C menu. Kept slow and
    // radius-limited since it's purely cosmetic, not gameplay-critical.
    private static final int BACKGROUND_POLL_INTERVAL_TICKS = 100;
    private static final int BACKGROUND_POLL_RADIUS = 24;
    private static int backgroundPollTimer = 0;
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel = null;

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
        locateBox = wholeChestBox(pos);
        locateActive = true;
    }

    public static void stopLocate() {
        locateActive = false;
        locateTarget = null;
        locateBox = null;
    }

    /** pos's own 1x1x1 box, widened to cover the other half if pos is one side of a double chest. */
    private static AABB wholeChestBox(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        AABB box = new AABB(pos);
        if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof ChestBlockEntity)) return box;
        var block = mc.level.getBlockState(pos).getBlock();
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos neighbor = pos.relative(dir);
            if (mc.level.getBlockEntity(neighbor) instanceof ChestBlockEntity
                    && mc.level.getBlockState(neighbor).getBlock() == block) {
                return box.minmax(new AABB(neighbor));
            }
        }
        return box;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (mc.level != lastLevel) {
            // Changed dimension/world - the block-position keyed icon cache would
            // otherwise show stale/wrong categories at the same coordinates elsewhere.
            lastLevel = mc.level;
            ClientChestCategoryCache.snapshot().clear();
        }

        if (++backgroundPollTimer >= BACKGROUND_POLL_INTERVAL_TICKS) {
            backgroundPollTimer = 0;
            PacketDistributor.sendToServer(new RequestNearbyChestsPayload(BACKGROUND_POLL_RADIUS));
        }

        if (mc.screen != null) return;

        while (OPEN_MENU_KEY.consumeClick()) {
            if (locateActive) {
                stopLocate();
            } else {
                pendingMenuOpen = true;
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
