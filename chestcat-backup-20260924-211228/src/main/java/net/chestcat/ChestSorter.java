package net.chestcat;

import net.chestcat.data.ChestCategoryData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
        GroupingConfig.Settings grouping = GroupingConfig.get(player.getUUID());
        CustomGroups.refresh();

        List<ChestUtil.Storage> storages = ChestUtil.findNearbyStorages(level, center, radius);
        if (storages.isEmpty()) {
            return new Result(0, 0, 0);
        }

        // Each chest's key: its manual/locked-in assignment, else the dominant key of what's inside.
        Map<ChestUtil.Storage, ItemGrouping.Key> storageKey = new LinkedHashMap<>();
        for (ChestUtil.Storage storage : storages) {
            Optional<ItemGrouping.Key> assigned = data.getKey(storage.canonicalPos());
            ItemGrouping.Key key = assigned.orElseGet(() -> detectDominantKey(storage.container(), grouping));
            storageKey.put(storage, key);
            if (assigned.isEmpty()) {
                data.setKey(storage.canonicalPos(), key);
            }
        }

        List<Container> allContainersInOrder = new ArrayList<>();
        for (ChestUtil.Storage storage : storages) {
            allContainersInOrder.add(storage.container());
        }

        List<ItemStack> pool = new ArrayList<>();
        for (ChestUtil.Storage storage : storages) {
            pool.addAll(ChestUtil.extractAll(storage.container()));
        }

        // Identical stacks merge first, then everything is laid out in sort order, so the same items
        // end up side by side even when a category spans several chests.
        pool = mergeStacks(pool);
        pool.sort(ItemSortUtils.comparator(sortMode, layout));

        // Item types nothing could place (usually modded): ask the optional AI once, in the background.
        if (AiClassifier.isEnabled()) {
            Set<String> unknown = new LinkedHashSet<>();
            for (ItemStack stack : pool) {
                if (unknown.size() < 150 && ItemGrouping.needsAi(stack)) unknown.add(ItemGrouping.itemId(stack));
            }
            if (!unknown.isEmpty() && AiClassifier.classifyAsync(unknown, player)) {
                player.sendSystemMessage(Component.literal("ChestCat AI is classifying " + unknown.size()
                        + " unknown item type(s) in the background - sort again in a few seconds.")
                        .withStyle(ChatFormatting.AQUA));
            }
        }

        // Exact key (e.g. WOOD:oak, or plain WOOD) -> chests, plus every chest of a category regardless of sub-type.
        Map<ItemGrouping.Key, List<Container>> byKey = new HashMap<>();
        Map<ItemCategory, List<Container>> byCategory = new EnumMap<>(ItemCategory.class);
        for (Map.Entry<ChestUtil.Storage, ItemGrouping.Key> entry : storageKey.entrySet()) {
            Container container = entry.getKey().container();
            byKey.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(container);
            byCategory.computeIfAbsent(entry.getValue().category(), c -> new ArrayList<>()).add(container);
        }

        int moved = 0;
        int dropped = 0;

        for (ItemStack stack : pool) {
            int originalCount = stack.getCount();
            ItemGrouping.Key key = ItemGrouping.classify(stack, grouping);
            ItemGrouping.Key custom = ItemGrouping.customKey(stack);

            // A chest pointed at this item's custom group wins; otherwise the normal category chain.
            ItemStack remaining = stack;
            if (custom != null) {
                remaining = insertInto(remaining, byKey.get(custom));
            }
            // Most specific chest first, then progressively looser fallbacks.
            if (!remaining.isEmpty()) {
                remaining = insertInto(remaining, byKey.get(key));
            }
            if (!remaining.isEmpty() && key.subKey() != null) {
                remaining = insertInto(remaining, byKey.get(new ItemGrouping.Key(key.category(), null)));
            }
            if (!remaining.isEmpty()) {
                remaining = insertInto(remaining, byCategory.get(key.category()));
            }
            if (!remaining.isEmpty()) {
                remaining = insertInto(remaining, byCategory.get(ItemCategory.MISC));
            }
            if (!remaining.isEmpty()) {
                remaining = insertInto(remaining, allContainersInOrder);
            }

            int leftover = remaining.isEmpty() ? 0 : remaining.getCount();
            moved += originalCount - leftover;

            if (!remaining.isEmpty()) {
                dropped += leftover;
                player.drop(remaining, false);
            }
        }

        for (Container c : allContainersInOrder) {
            sortContainerContents(c, sortMode, layout);
        }

        return new Result(moved, dropped, storages.size());
    }

    /** Combines partial stacks of the same item (same components) up to the max stack size. */
    private static List<ItemStack> mergeStacks(List<ItemStack> stacks) {
        List<ItemStack> merged = new ArrayList<>();
        outer:
        for (ItemStack stack : stacks) {
            for (ItemStack existing : merged) {
                if (ItemStack.isSameItemSameComponents(existing, stack)
                        && existing.getCount() < existing.getMaxStackSize()) {
                    int move = Math.min(existing.getMaxStackSize() - existing.getCount(), stack.getCount());
                    existing.grow(move);
                    stack.shrink(move);
                    if (stack.isEmpty()) continue outer;
                }
            }
            merged.add(stack);
        }
        return merged;
    }

    private static ItemStack insertInto(ItemStack stack, List<Container> targets) {
        if (targets == null || targets.isEmpty() || stack.isEmpty()) return stack;
        ItemStack remaining = stack;
        for (Container target : targets) {
            if (remaining.isEmpty()) break;
            remaining = ChestUtil.insertStack(target, remaining);
        }
        return remaining;
    }

    public static void sortContainer(Container container, ItemSortMode mode) {
        sortContainerContents(container, mode, SortLayoutPrefs.Settings.DEFAULT);
    }

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

        items = mergeStacks(items);
        items.sort(ItemSortUtils.comparator(mode, layout));

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

    /** Dominant key (category + sub-type, when splitting is on) of what's currently in the container. */
    public static ItemGrouping.Key detectDominantKey(Container container, GroupingConfig.Settings grouping) {
        Map<ItemGrouping.Key, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            counts.merge(ItemGrouping.classify(stack, grouping), stack.getCount(), Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(new ItemGrouping.Key(ItemCategory.MISC, null));
    }

    public static ItemCategory detectDominantCategory(Container container) {
        return detectDominantKey(container, GroupingConfig.Settings.DEFAULT).category();
    }
}
