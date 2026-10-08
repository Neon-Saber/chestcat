package net.chestcat.client;

import net.chestcat.FavoriteMode;
import net.chestcat.ItemSortMode;
import net.chestcat.SortChain;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Ready-made sort chains selectable from the R button. Each one is just a chain string plus a favorite
 * behaviour, so they are easy to tweak afterwards in the Sort Chain editor. Your own setups can be saved as
 * named profiles (they store the chain, favorite mode and ignore rules too).
 */
@OnlyIn(Dist.CLIENT)
public final class SortPresets {

    private SortPresets() {}

    public record Preset(String name, String description, String chain, FavoriteMode favorites) {}

    public static final List<Preset> BUILT_IN = List.of(
            new Preset("Default", "Category, then mod, then name.",
                    "category:asc,mod:asc,name:asc", FavoriteMode.PIN),
            new Preset("Combat", "Weapons by type and tier, best first.",
                    "category:asc,tool_type:asc,material:desc,rarity:desc,enchant_count:desc,name:asc", FavoriteMode.FIRST),
            new Preset("Building", "Blocks grouped by color and name.",
                    "category:asc,color:asc,mod:asc,name:asc", FavoriteMode.PIN),
            new Preset("Mining", "Tools by type and tier, then ores and materials.",
                    "category:asc,tool_type:asc,material:desc,durability_left:desc,name:asc", FavoriteMode.FIRST),
            new Preset("Farming", "Food and crops, most filling first.",
                    "category:asc,food_value:desc,name:asc", FavoriteMode.PIN),
            new Preset("Modded", "Everything grouped by mod, then category.",
                    "mod:asc,category:asc,name:asc", FavoriteMode.PIN),
            new Preset("Survival", "Favorites first, rare and enchanted gear up front.",
                    "category:asc,rarity:desc,enchant_count:desc,count:desc,name:asc", FavoriteMode.FIRST),
            new Preset("Storage", "Favorites first, then category, mod and stack size.",
                    "category:asc,mod:asc,count:desc,name:asc", FavoriteMode.FIRST)
    );

    private static int cycleIndex = -1;

    /** Applies the next built-in preset (wrapping around) and returns it - used by the "next preset" hotkey. */
    public static Preset cycle() {
        cycleIndex = (cycleIndex + 1) % BUILT_IN.size();
        Preset p = BUILT_IN.get(cycleIndex);
        apply(p);
        return p;
    }

    /** Makes the preset the active Custom Chain for inventory, chests and nearby sorting. */
    public static void apply(Preset preset) {
        SortSettings.chain = SortChain.parse(preset.chain()).serialize();
        SortSettings.favoriteMode = preset.favorites();
        ChestCatClient.inventorySortMode = ItemSortMode.CUSTOM;
        ChestCatClient.chestSortMode = ItemSortMode.CUSTOM;
        ChestCatClient.nearbySortMode = ItemSortMode.CUSTOM;
        SortSettings.sync();
    }
}
