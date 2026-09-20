package net.chestcat;

import net.chestcat.data.ChestCategoryData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class ChestSorter {

    private ChestSorter() {}

    public record Result(int itemsMoved, int itemsDropped, int chestsTouched) {}

    public static Result sortNearby(ServerPlayer player, int radius, ItemSortMode sortMode) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        ChestCategoryData data = ChestCategoryData.get(level);

        List<ChestUtil.Storage> storages = ChestUtil.findNearbyStorages(level, center, radius);
        if (storages.isEmpty()) {
            return new Result(0, 0, 0);
        }

        Map<ChestUtil.Storage, ItemCategory> storageCategory = new LinkedHashMap<>();
        for (ChestUtil.Storage storage : storages) {
            Optional<ItemCategory> assigned = data.getCategory(storage.canonicalPos());
            ItemCategory category = assigned.orElseGet(() -> autoDetect(storage.container()));
            storageCategory.put(storage, category);
            if (assigned.isEmpty()) {
                data.setCategory(storage.canonicalPos(), category);
            }
        }

        List<ItemStack> pool = new ArrayList<>();
        for (ChestUtil.Storage storage : storages) {
            pool.addAll(ChestUtil.extractAll(storage.container()));
        }

        Map<ItemCategory, List<Container>> byCategory = new EnumMap<>(ItemCategory.class);
        for (Map.Entry<ChestUtil.Storage, ItemCategory> entry : storageCategory.entrySet()) {
            byCategory.computeIfAbsent(entry.getValue(), c -> new ArrayList<>())
                    .add(entry.getKey().container());
        }

        int moved = 0;
        int dropped = 0;

        for (ItemStack stack : pool) {
            ItemCategory category = ItemCategory.categorize(stack);
            List<Container> targets = byCategory.get(category);
            if (targets == null || targets.isEmpty()) {
                targets = byCategory.get(ItemCategory.MISC);
            }

            ItemStack remaining = stack;
            if (targets != null) {
                for (Container target : targets) {
                    if (remaining.isEmpty()) break;
                    remaining = ChestUtil.insertStack(target, remaining);
                }
            }

            int originalCount = stack.getCount();
            int leftover = remaining.isEmpty() ? 0 : remaining.getCount();
            moved += originalCount - leftover;

            if (!remaining.isEmpty()) {
                dropped += leftover;
                player.drop(remaining, false);
            }
        }

        for (List<Container> containers : byCategory.values()) {
            for (Container c : containers) {
                sortContainerContents(c, sortMode);
            }
        }

        return new Result(moved, dropped, storages.size());
    }

    /** Sorts a single container's contents in place, without moving items between containers. */
    public static void sortContainer(Container container, ItemSortMode mode) {
        sortContainerContents(container, mode);
    }

    private static void sortContainerContents(Container container, ItemSortMode mode) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty()) items.add(s.copy());
            container.setItem(i, ItemStack.EMPTY);
        }

        Comparator<ItemStack> comparator = ItemSortUtils.comparator(mode);

        items.sort(comparator);

        for (int i = 0; i < items.size() && i < container.getContainerSize(); i++) {
            container.setItem(i, items.get(i));
        }
    }

    private static ItemCategory autoDetect(Container container) {
        Map<ItemCategory, Integer> counts = new EnumMap<>(ItemCategory.class);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            ItemCategory cat = ItemCategory.categorize(stack);
            counts.merge(cat, stack.getCount(), Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(ItemCategory.MISC);
    }
}