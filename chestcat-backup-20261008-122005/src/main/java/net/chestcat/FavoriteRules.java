package net.chestcat;

import net.chestcat.data.FavoritesData;
import net.chestcat.data.ProtectedItemsData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Turns a player's favorites + exclusion rules + legacy protected items into the
 * predicates and comparator wrappers the sorters use, so every sorter treats
 * "don't touch this" and "favorites first/last" identically.
 */
public final class FavoriteRules {

    private FavoriteRules() {}

    /** Items a SORT must leave exactly where they are (favorites only when mode is PIN). */
    public static Predicate<ItemStack> skipForSort(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();
        SortLayoutPrefs.Settings prefs = SortLayoutPrefs.get(id);
        ProtectedItemsData legacy = ProtectedItemsData.get(level);
        FavoritesData favorites = FavoritesData.get(level);
        ExclusionRules rules = ExclusionRules.parse(prefs.exclusions());
        boolean pin = prefs.favoriteMode() == FavoriteMode.PIN;
        boolean only = prefs.favoriteMode() == FavoriteMode.ONLY;
        return stack -> legacy.isProtected(stack) || rules.matches(stack)
                || (pin && favorites.isFavorite(id, stack))
                // "Favorites only": every stack that is NOT a favorite is left in place.
                || (only && !stack.isEmpty() && !favorites.isFavorite(id, stack));
    }

    /** True for this player's favorited items. Looks the data up once, so it is cheap to call per stack. */
    public static Predicate<ItemStack> favoritePredicate(ServerPlayer player) {
        FavoritesData favorites = FavoritesData.get(player.serverLevel());
        UUID id = player.getUUID();
        return stack -> favorites.isFavorite(id, stack);
    }

    /** Items quick-stack / dump / push must never move: favorites are always protected there. */
    public static Predicate<ItemStack> skipForTransfer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();
        SortLayoutPrefs.Settings prefs = SortLayoutPrefs.get(id);
        ProtectedItemsData legacy = ProtectedItemsData.get(level);
        FavoritesData favorites = FavoritesData.get(level);
        ExclusionRules rules = ExclusionRules.parse(prefs.exclusions());
        return stack -> legacy.isProtected(stack) || rules.matches(stack) || favorites.isFavorite(id, stack);
    }

    /** Puts favorites first or last when the player's favorite mode asks for it. */
    public static Comparator<ItemStack> wrap(Comparator<ItemStack> base, ServerPlayer player) {
        SortLayoutPrefs.Settings prefs = SortLayoutPrefs.get(player.getUUID());
        FavoriteMode mode = prefs.favoriteMode();
        if (mode != FavoriteMode.FIRST && mode != FavoriteMode.LAST) return base;

        FavoritesData favorites = FavoritesData.get(player.serverLevel());
        UUID id = player.getUUID();
        Comparator<ItemStack> favFirst = Comparator.<ItemStack>comparingInt(s -> favorites.isFavorite(id, s) ? 0 : 1);
        if (mode == FavoriteMode.LAST) favFirst = favFirst.reversed();
        return favFirst.thenComparing(base);
    }
}
