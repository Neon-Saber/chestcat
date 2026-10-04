package net.chestcat;

import net.chestcat.data.LockedSlotsData;
import net.chestcat.data.ProtectedItemsData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Set;

/**
 * Moves matching items from the player's hotbar+main inventory (0-35) into
 * any Container block entities within radius that already contain a
 * matching item. Locked/favorited slots are skipped.
 */
public final class QuickStack {

    private static final int RADIUS = 6;
    private static final int MAIN_START = 0;
    private static final int MAIN_END = 36;

    private QuickStack() {}

    public static int run(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        Set<Integer> locked = LockedSlotsData.get(player.serverLevel()).get(player.getUUID());
        Inventory inv = player.getInventory();
        net.chestcat.data.ChestCategoryData data = net.chestcat.data.ChestCategoryData.get(level);
        ProtectedItemsData protectedItems = ProtectedItemsData.get(level);
        int moved = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-RADIUS, -RADIUS, -RADIUS),
                center.offset(RADIUS, RADIUS, RADIUS))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container container) || !ChestUtil.isStorage(level, pos, be)) continue;
            BlockPos canonical = ChestUtil.canonicalChestPos(level, pos);
            if (data.isExcluded(canonical)) continue;
            if (data.isWhitelistMode() && !data.isWhitelisted(canonical)) continue;

            for (int i = MAIN_START; i < MAIN_END; i++) {
                if (locked.contains(i)) continue;
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty() || protectedItems.isProtected(stack) || !containerHasMatching(container, stack)) continue;

                int remaining = stack.getCount();
                for (int c = 0; c < container.getContainerSize() && remaining > 0; c++) {
                    ItemStack slotStack = container.getItem(c);
                    if (slotStack.isEmpty() || !ItemStack.isSameItemSameComponents(slotStack, stack)) continue;
                    int space = slotStack.getMaxStackSize() - slotStack.getCount();
                    if (space <= 0) continue;
                    int move = Math.min(space, remaining);
                    slotStack.grow(move);
                    remaining -= move;
                    moved += move;
                }
                int used = stack.getCount() - remaining;
                if (used > 0) {
                    stack.shrink(used);
                    inv.setItem(i, stack);
                }
            }
            container.setChanged();
        }
        inv.setChanged();
        return moved;
    }

    private static boolean containerHasMatching(Container container, ItemStack stack) {
        for (int c = 0; c < container.getContainerSize(); c++) {
            ItemStack slotStack = container.getItem(c);
            if (!slotStack.isEmpty() && ItemStack.isSameItemSameComponents(slotStack, stack)) return true;
        }
        return false;
    }
}