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
        SortLayoutPrefs.Settings layout = SortLayoutPrefs.get(player.getUUID());

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
                sortContainerContents(c, sortMode, layout);
            }
        }

        return new Result(moved, dropped, storages.size());
    }

    /** Sorts one container in place using default layout settings (rows, top-left first). */
    public static void sortContainer(Container container, ItemSortMode mode) {
        sortContainerContents(container, mode, SortLayoutPrefs.Settings.DEFAULT);
    }

    /** Sorts one container in place using the given player's layout settings. */
    public static void sortContainer(Container container, ItemSortMode mode, UUID player) {
        sortContainerContents(container, mode, SortLayoutPrefs.get(player));
    }

    private static void sortContainerContents(Container container, ItemSortMode mode,
                                              SortLayoutPrefs.Settings layout) {
        int size = container.getContainerSize();
        if (size == 0) return;

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty()) items.add(s.copy());
            container.setItem(i, ItemStack.EMPTY);
        }

        items.sort(ItemSortUtils.comparator(mode));

        // Chest-style containers are 9 wide; anything else is treated as a single row.
        int cols = (size % 9 == 0) ? 9 : size;
        int rows = size / cols;
        int[][] grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) grid[r][c] = r * cols + c;
        }

        List<Integer> order = SortGrid.fillOrder(grid, layout.layout(), layout.reverse());
        for (int i = 0; i < items.size() && i < order.size(); i++) {
            container.setItem(order.get(i), items.get(i));
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
