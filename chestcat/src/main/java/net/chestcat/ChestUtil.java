package net.chestcat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChestUtil {

    private ChestUtil() {}

    /** A single logical storage container: one chest, one double chest (merged), or one barrel. */
    public record Storage(BlockPos canonicalPos, Container container) {}

    /**
     * Scans a cube around center for chests and barrels, merging double chests into
     * one logical Storage so they're treated (and categorized/filled) as a single unit.
     */
    public static List<Storage> findNearbyStorages(ServerLevel level, BlockPos center, int radius) {
        List<Storage> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();

        BlockPos min = center.offset(-radius, -radius, -radius);
        BlockPos max = center.offset(radius, radius, radius);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (visited.contains(pos)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) continue;

            if (be instanceof ChestBlockEntity) {
                BlockState state = level.getBlockState(pos);
                Container container = ChestBlock.getContainer(
                        (net.minecraft.world.level.block.ChestBlock) state.getBlock(),
                        state, level, pos, true);
                if (container == null) continue;

                BlockPos canonical = canonicalChestPos(level, pos);
                if (visited.contains(canonical)) continue;
                visited.add(pos);
                visited.add(canonical);
                // Mark the connected half visited too, if any.
                for (net.minecraft.core.Direction dir : new net.minecraft.core.Direction[]{
                        net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                        net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST}) {
                    BlockPos neighbor = pos.relative(dir);
                    if (level.getBlockEntity(neighbor) instanceof ChestBlockEntity
                            && level.getBlockState(neighbor).getBlock() == state.getBlock()) {
                        visited.add(neighbor);
                    }
                }

                result.add(new Storage(canonical, container));
            } else if (be instanceof BarrelBlockEntity barrel) {
                visited.add(pos);
                result.add(new Storage(pos.immutable(), barrel));
            }
        }

        return result;
    }

    private static BlockPos canonicalChestPos(ServerLevel level, BlockPos pos) {
        // Use whichever half sorts lower on X then Z as the canonical position,
        // so both halves of a double chest always resolve to the same key.
        BlockState state = level.getBlockState(pos);
        for (net.minecraft.core.Direction dir : new net.minecraft.core.Direction[]{
                net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.WEST}) {
            BlockPos neighbor = pos.relative(dir);
            if (level.getBlockEntity(neighbor) instanceof ChestBlockEntity
                    && level.getBlockState(neighbor).getBlock() == state.getBlock()) {
                return neighbor.immutable();
            }
        }
        return pos.immutable();
    }

    /** Removes and returns copies of every non-empty stack in the container, then clears it. */
    public static List<ItemStack> extractAll(Container container) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        container.clearContent();
        return stacks;
    }

    /**
     * Attempts to insert as much of the stack as possible into the container
     * (merging into existing matching stacks first, then empty slots).
     * Returns the leftover ItemStack that did not fit (empty if it all fit).
     */
    public static ItemStack insertStack(Container container, ItemStack stack) {
        ItemStack remaining = stack.copy();
        if (remaining.isEmpty()) return ItemStack.EMPTY;

        // Pass 1: top up existing matching, non-full stacks.
        for (int i = 0; i < container.getContainerSize() && !remaining.isEmpty(); i++) {
            ItemStack existing = container.getItem(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remaining)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int move = Math.min(space, remaining.getCount());
                    existing.grow(move);
                    remaining.shrink(move);
                }
            }
        }

        // Pass 2: place into empty slots.
        for (int i = 0; i < container.getContainerSize() && !remaining.isEmpty(); i++) {
            if (container.getItem(i).isEmpty()) {
                int move = Math.min(remaining.getMaxStackSize(), remaining.getCount());
                ItemStack toPlace = remaining.copy();
                toPlace.setCount(move);
                container.setItem(i, toPlace);
                remaining.shrink(move);
            }
        }

        return remaining;
    }

    public static int freeCapacityEstimate(Container container) {
        int free = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            free += s.isEmpty() ? 64 : Math.max(0, s.getMaxStackSize() - s.getCount());
        }
        return free;
    }
}
