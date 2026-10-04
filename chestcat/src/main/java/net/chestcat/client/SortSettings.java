package net.chestcat.client;

import net.chestcat.FavoriteMode;
import net.chestcat.SortChain;
import net.chestcat.SortLayout;
import net.chestcat.SortMore;
import net.chestcat.TiebreakPriority;
import net.chestcat.network.SetSortExtrasPayload;
import net.chestcat.network.SetSortLayoutPayload;
import net.chestcat.network.SetSortMorePayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Client-side copy of the sort settings. Call sync() right before any sort packet. Persisted by {@link ClientPrefs}. */
public final class SortSettings {

    public static SortLayout layout = SortLayout.ROWS;
    public static boolean reverse = false;
    public static boolean includeHotbar = true;
    public static boolean groupArmorBySlot = true;
    public static TiebreakPriority tiebreakPriority = TiebreakPriority.TYPE_FIRST;
    public static boolean floatEnchantedFirst = false;

    /** Multi-level sort chain, serialized ("category:asc,mod:asc,name:asc"). Used by the "Custom Chain" sort mode. */
    public static String chain = SortChain.DEFAULT_STRING;
    public static FavoriteMode favoriteMode = FavoriteMode.PIN;
    public static boolean mergeStacks = true;
    /** Never-move rules ("mod:create", "cat:food", "item:minecraft:diamond", "tag:c:ores"). */
    public static List<String> exclusions = new ArrayList<>();

    /** Empty-slot handling, extra hotbar behaviour, category / mod order and name direction. */
    public static SortMore more = SortMore.DEFAULT;

    private SortSettings() {}

    public static String exclusionString() {
        return String.join(";", exclusions);
    }

    /** Pushes the layout/ordering options, chain/favorites/exclusions, the extra options AND the Category Splitting options to the server. */
    public static void sync() {
        PacketDistributor.sendToServer(new SetSortLayoutPayload(layout, reverse, includeHotbar,
                groupArmorBySlot, tiebreakPriority, floatEnchantedFirst));
        PacketDistributor.sendToServer(new SetSortExtrasPayload(chain, favoriteMode, mergeStacks, exclusionString()));
        PacketDistributor.sendToServer(new SetSortMorePayload(more));
        GroupingSettings.sync();
    }

    public static void resetDefaults() {
        layout = SortLayout.ROWS;
        reverse = false;
        includeHotbar = true;
        groupArmorBySlot = true;
        tiebreakPriority = TiebreakPriority.TYPE_FIRST;
        floatEnchantedFirst = false;
        chain = SortChain.DEFAULT_STRING;
        favoriteMode = FavoriteMode.PIN;
        mergeStacks = true;
        exclusions = new ArrayList<>();
        more = SortMore.DEFAULT;
    }

    public static String summary() {
        return layout.getDisplayName() + ", from " + (reverse ? "bottom-right" : "top-left");
    }
}
