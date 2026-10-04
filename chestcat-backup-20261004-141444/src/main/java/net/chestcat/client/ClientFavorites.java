package net.chestcat.client;

import net.chestcat.FavoriteKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** The client's copy of the player's favorites. The server is authoritative and re-sends the full list after every change. */
@OnlyIn(Dist.CLIENT)
public final class ClientFavorites {

    private ClientFavorites() {}

    private static Set<String> keys = new HashSet<>();

    public static void replace(Collection<String> newKeys) {
        keys = new HashSet<>(newKeys);
    }

    public static boolean isFavorite(ItemStack stack) {
        return !keys.isEmpty() && !stack.isEmpty() && keys.contains(FavoriteKey.of(stack));
    }

    /** Optimistic local flip so the click feels instant; the server's sync corrects it if it disagrees. */
    public static boolean toggleLocal(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String key = FavoriteKey.of(stack);
        if (keys.remove(key)) return false;
        keys.add(key);
        return true;
    }
}
