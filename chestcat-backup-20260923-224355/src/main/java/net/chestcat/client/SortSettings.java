package net.chestcat.client;

import net.chestcat.SortLayout;
import net.chestcat.TiebreakPriority;
import net.chestcat.network.SetSortLayoutPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-side copy of the sort settings. Call sync() right before any sort packet. */
public final class SortSettings {

    public static SortLayout layout = SortLayout.ROWS;
    public static boolean reverse = false;
    public static boolean includeHotbar = true;

    public static boolean groupArmorBySlot = true;
    public static boolean floatEnchantedFirst = true;
    public static TiebreakPriority tiebreakPriority = TiebreakPriority.TYPE_FIRST;

    private SortSettings() {}

    public static void sync() {
        PacketDistributor.sendToServer(new SetSortLayoutPayload(
                layout, reverse, includeHotbar, groupArmorBySlot, floatEnchantedFirst, tiebreakPriority));
    }

    public static String summary() {
        return layout.getDisplayName() + ", from " + (reverse ? "bottom-right" : "top-left");
    }
}