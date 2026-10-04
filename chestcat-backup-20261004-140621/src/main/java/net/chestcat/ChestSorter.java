package net.chestcat;

import net.chestcat.data.ChestCategoryData;
import net.chestcat.data.ContainerRulesData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class ChestSorter {

    private ChestSorter() {}

    public record Result(int itemsMoved, int itemsDropped, int chestsTouched) {}

    /** One planned move a {@link #previewSortNearby} run would make: this many of this item,
     *  landing in the chest at this position. Reported at item-type granularity (not a
     *  slot-by-slot log) - when several source chests contribute the same item type to the
     *  same destination, it's reported as one combined entry. */
    public record PlannedMove(String item, int count, BlockPos destination) {}

    /** Read-only result of {@link #previewSortNearby}: the moves it would make, how many
     *  items would move, how many would have nowhere to go (and would be dropped if this
     *  were a real sort), and how many chests are involved. Nothing is touched to produce this. */
    public record Preview(List<PlannedMove> moves, int itemsWouldMove, int itemsWithNoDestination, int chestsInvolved) {}

    public static Result sortNearby(ServerPlayer player, int radius, ItemSortMode sortMode) {
        return runSort(player, radius, sortMode, false, new ArrayList<>());
    }

    /**
     * Calculates exactly what {@link #sortNearby} would do, without moving a single item,
     * without persisting any newly auto-detected chest category, and without triggering the
     * background AI classifier. The same routing method (rule chests, then exact key, then
     * category, then MISC, then open overflow) runs against in-memory copies of the real
     * chests, so the preview is never out of sync with the real algorithm.
     */
    public static Preview previewSortNearby(ServerPlayer player, int radius, ItemSortMode sortMode) {
        List<PlannedMove> moves = new ArrayList<>();
        Result r = runSort(player, radius, sortMode, true, moves);
        return new Preview(moves, r.itemsMoved(), r.itemsDropped(), r.chestsTouched());
    }

    private static Result runSort(ServerPlayer player, int radius, ItemSortMode sortMode,
                                   boolean dryRun, List<PlannedMove> movesOut) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        ChestCategoryData data = ChestCategoryData.get(level);
        ContainerRulesData rulesData = ContainerRulesData.get(level);
        java.util.function.Predicate<ItemStack> skipStack = FavoriteRules.skipForSort(player);
        SortLayoutPrefs.Settings layout = SortLayoutPrefs.get(player.getUUID());
        GroupingConfig.Settings grouping = GroupingConfig.get(player.getUUID());
        CustomGroups.refresh();

        List<ChestUtil.Storage> storages = ChestUtil.findNearbyStorages(level, center, radius);
        // Whitelist mode overrides everything else: only whitelisted chests participate,
        // full stop. Otherwise the normal per-chest exclude flag applies as before.
        if (data.isWhitelistMode()) {
            storages.removeIf(storage -> !data.isWhitelisted(storage.canonicalPos()));
        } else {
            storages.removeIf(storage -> data.isExcluded(storage.canonicalPos()));
        }
        if (storages.isEmpty()) {
            return new Result(0, 0, 0);
        }

        // A dry run never touches a real chest: work against an in-memory copy of each one.
        // Everything below reads/writes through workingContainer, never storage.container()
        // directly, so the exact same code path is correct for both a real sort and a preview.
        Map<ChestUtil.Storage, Container> workingContainer = new LinkedHashMap<>();
        Map<Container, BlockPos> posOf = new LinkedHashMap<>();
        for (ChestUtil.Storage storage : storages) {
            Container working;
            if (dryRun) {
                SimpleContainer clone = new SimpleContainer(storage.container().getContainerSize());
                for (int i = 0; i < storage.container().getContainerSize(); i++) {
                    clone.setItem(i, storage.container().getItem(i).copy());
                }
                working = clone;
            } else {
                working = storage.container();
            }
            workingContainer.put(storage, working);
            posOf.put(working, storage.canonicalPos());
        }

        // Each chest's key: its manual/locked-in assignment, else the dominant key of what's inside.
        Map<ChestUtil.Storage, ItemGrouping.Key> storageKey = new LinkedHashMap<>();
        for (ChestUtil.Storage storage : storages) {
            Optional<ItemGrouping.Key> assigned = data.getKey(storage.canonicalPos());
            ItemGrouping.Key key = assigned.orElseGet(() -> detectDominantKey(workingContainer.get(storage), grouping));
            storageKey.put(storage, key);
            if (assigned.isEmpty() && !dryRun) {
                data.setKey(storage.canonicalPos(), key);
            }
        }

        List<Container> allContainersInOrder = new ArrayList<>();
        // Everything eligible for the last-resort "any container" overflow step - a rule chest
        // or a locked chest is NEVER in here, since that's exactly the bug this fixes: an
        // unrelated leftover item must not be able to land in a chest the player explicitly
        // restricted, just because nothing else took it.
        List<Container> openContainersInOrder = new ArrayList<>();
        // Subset of the above whose chest isn't already dedicated to a specific category
        // (its detected/assigned key is plain MISC). Preferred over openContainersInOrder for
        // true last-resort overflow, so a chest you've built up as "the dyes chest" or "the
        // weapons chest" doesn't silently become the dumping ground for everything unrelated
        // that had nowhere else to go - a generic/unlabeled chest takes that role instead.
        List<Container> openMiscContainersInOrder = new ArrayList<>();
        // Chests with a hand-written rule, in storage order, paired with that rule - checked
        // per item before any category-based routing, since an explicit rule is more specific
        // than an automatic category guess.
        Map<Container, ContainerRule> containerRule = new LinkedHashMap<>();

        for (ChestUtil.Storage storage : storages) {
            Container working = workingContainer.get(storage);
            allContainersInOrder.add(working);
            Optional<ContainerRule> rule = rulesData.getRule(storage.canonicalPos());
            rule.ifPresent(r -> containerRule.put(working, r));
            if (!rulesData.isRestricted(storage.canonicalPos())) {
                openContainersInOrder.add(working);
                if (storageKey.get(storage).category() == ItemCategory.MISC) {
                    openMiscContainersInOrder.add(working);
                }
            }
        }

        // Protected item types are left exactly where they are - never pulled into the
        // shared pool, so they can never be redistributed to a different chest.
        List<ItemStack> pool = new ArrayList<>();
        for (ChestUtil.Storage storage : storages) {
            pool.addAll(ChestUtil.extractAll(workingContainer.get(storage), skipStack));
        }

        // Identical stacks merge first, then everything is laid out in sort order, so the same items
        // end up side by side even when a category spans several chests.
        pool = mergeStacks(pool);
        pool.sort(FavoriteRules.wrap(ItemSortUtils.comparator(sortMode, layout, player), player));

        // Item types nothing could place (usually modded): ask the optional AI once, in the
        // background. Never during a preview - a preview must have zero side effects.
        if (!dryRun && AiClassifier.isEnabled()) {
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
        // A rule chest is deliberately excluded from both maps: once a chest has an explicit
        // rule, that rule is the ONLY way anything reaches it - it must not also keep
        // receiving items through the normal category/key fallback chain.
        Map<ItemGrouping.Key, List<Container>> byKey = new HashMap<>();
        Map<ItemCategory, List<Container>> byCategory = new EnumMap<>(ItemCategory.class);
        for (Map.Entry<ChestUtil.Storage, ItemGrouping.Key> entry : storageKey.entrySet()) {
            ChestUtil.Storage storage = entry.getKey();
            if (rulesData.getRule(storage.canonicalPos()).isPresent()) continue;
            Container container = workingContainer.get(storage);
            byKey.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(container);
            byCategory.computeIfAbsent(entry.getValue().category(), c -> new ArrayList<>()).add(container);
        }

        int moved = 0;
        int dropped = 0;

        for (ItemStack stack : pool) {
            int originalCount = stack.getCount();
            String label = stack.getHoverName().getString();
            ItemGrouping.Key key = ItemGrouping.classify(stack, grouping);
            ItemGrouping.Key custom = ItemGrouping.customKey(stack);

            ItemStack remaining = stack;

            // A chest pointed at this item's custom group wins over everything else.
            if (custom != null) {
                remaining = insertInto(remaining, byKey.get(custom), posOf, movesOut, label);
            }

            // Explicit per-chest rules next - more specific than any automatic category guess.
            if (!remaining.isEmpty() && !containerRule.isEmpty()) {
                List<Container> matchingRuleChests = new ArrayList<>();
                for (Map.Entry<Container, ContainerRule> e : containerRule.entrySet()) {
                    if (e.getValue().matches(remaining, grouping)) matchingRuleChests.add(e.getKey());
                }
                if (!matchingRuleChests.isEmpty()) {
                    remaining = insertInto(remaining, matchingRuleChests, posOf, movesOut, label);
                }
            }

            // Most specific chest first, then progressively looser fallbacks.
            if (!remaining.isEmpty()) {
                remaining = insertInto(remaining, byKey.get(key), posOf, movesOut, label);
            }
            if (!remaining.isEmpty() && key.subKey() != null) {
                remaining = insertInto(remaining, byKey.get(new ItemGrouping.Key(key.category(), null)), posOf, movesOut, label);
            }
            if (!remaining.isEmpty()) {
                remaining = insertInto(remaining, byCategory.get(key.category()), posOf, movesOut, label);
            }
            // Broader parent category next (e.g. Stone/Wood/Redstone/Dyes -> Blocks), for when
            // this exact category has no dedicated chest but a close relative does. Keeps items
            // in a sensible home instead of skipping straight to undifferentiated overflow.
            ItemCategory parentCategory = ItemCategory.broadFallback(key.category());
            if (!remaining.isEmpty() && parentCategory != null) {
                remaining = insertInto(remaining, byCategory.get(parentCategory), posOf, movesOut, label);
            }
            if (!remaining.isEmpty()) {
                remaining = insertInto(remaining, byCategory.get(ItemCategory.MISC), posOf, movesOut, label);
            }
            if (!remaining.isEmpty()) {
                // Last resort, part 1: any open chest that isn't already dedicated to some
                // other specific category - a generic/unlabeled chest absorbs true overflow
                // before a category-specific chest ever does.
                remaining = insertInto(remaining, openMiscContainersInOrder, posOf, movesOut, label);
            }
            if (!remaining.isEmpty()) {
                // Last resort, part 2 - deliberately excludes rule chests and locked chests (see
                // openContainersInOrder above). This is the actual fix for the "Tools-only
                // chest quietly accepts a diamond because nothing else wanted it" bug. Only
                // reached when there's no generic chest left at all, so it's a true last resort
                // rather than the first place overflow lands.
                remaining = insertInto(remaining, openContainersInOrder, posOf, movesOut, label);
            }

            int leftover = remaining.isEmpty() ? 0 : remaining.getCount();
            moved += originalCount - leftover;

            if (!remaining.isEmpty()) {
                dropped += leftover;
                // Never drop a real item on the ground for a preview - remaining still wraps a
                // real ItemStack even though it was extracted from a cloned container.
                if (!dryRun) {
                    player.drop(remaining, false);
                }
            }
        }

        for (Container c : allContainersInOrder) {
            sortContainerContents(c, FavoriteRules.wrap(ItemSortUtils.comparator(sortMode, layout, player), player), layout, skipStack);
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

    /** Inserts as much of stack as possible across targets, in order. If movesOut is non-null,
     *  records how much landed in each target that actually accepted some. */
    private static ItemStack insertInto(ItemStack stack, List<Container> targets, Map<Container, BlockPos> posOf,
                                         List<PlannedMove> movesOut, String label) {
        if (targets == null || targets.isEmpty() || stack.isEmpty()) return stack;
        ItemStack remaining = stack;
        for (Container target : targets) {
            if (remaining.isEmpty()) break;
            int before = remaining.getCount();
            remaining = ChestUtil.insertStack(target, remaining);
            int placed = before - (remaining.isEmpty() ? 0 : remaining.getCount());
            if (placed > 0 && movesOut != null) {
                movesOut.add(new PlannedMove(label, placed, posOf.get(target)));
            }
        }
        return remaining;
    }

    public static void sortContainer(Container container, ItemSortMode mode) {
        sortContainerContents(container, ItemSortUtils.comparator(mode, SortLayoutPrefs.Settings.DEFAULT),
                SortLayoutPrefs.Settings.DEFAULT, stack -> false);
    }

    /** Sorts one container for a player using their settings. @return how many slots changed (0 = already sorted). */
    public static int sortContainer(Container container, ItemSortMode mode, ServerPlayer player) {
        SortLayoutPrefs.Settings prefs = SortLayoutPrefs.get(player.getUUID());
        return sortContainerContents(container, FavoriteRules.wrap(ItemSortUtils.comparator(mode, prefs, player), player),
                prefs, FavoriteRules.skipForSort(player));
    }

    private static int sortContainerContents(Container container, java.util.Comparator<ItemStack> comparator,
                                              SortLayoutPrefs.Settings layout,
                                              java.util.function.Predicate<ItemStack> protect) {
        int size = container.getContainerSize();
        if (size == 0) return 0;

        // A protected stack is left in its slot entirely - never picked up, so it
        // can't be reordered or moved even within the same container.
        List<ItemStack> items = new ArrayList<>();
        boolean[] protectedSlot = new boolean[size];
        ItemStack[] before = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            ItemStack s = container.getItem(i);
            before[i] = s.copy();
            if (s.isEmpty()) continue;
            if (protect.test(s)) {
                protectedSlot[i] = true;
                continue;
            }
            items.add(s.copy());
            container.setItem(i, ItemStack.EMPTY);
        }

        items = mergeStacks(items);
        items.sort(comparator);

        int cols = (size % 9 == 0) ? 9 : size;
        int rows = size / cols;
        int[][] grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int slot = r * cols + c;
                grid[r][c] = protectedSlot[slot] ? -1 : slot;
            }
        }

        List<Integer> order = SortGrid.fillOrder(grid, layout.layout(), layout.reverse());
        int start = SortGrid.startIndex(order.size(), items.size(), layout.more().emptySlots() == EmptySlotMode.FIRST);
        for (int i = 0; i < items.size() && start + i < order.size(); i++) {
            container.setItem(order.get(start + i), items.get(i));
        }

        int changed = 0;
        for (int i = 0; i < size; i++) {
            if (protectedSlot[i]) continue;
            ItemStack now = container.getItem(i);
            ItemStack was = before[i];
            boolean same = (was.isEmpty() && now.isEmpty())
                    || (was.getCount() == now.getCount() && ItemStack.isSameItemSameComponents(was, now));
            if (!same) changed++;
        }
        return changed;
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
