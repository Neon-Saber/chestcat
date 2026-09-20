package net.chestcat.client;

import net.chestcat.SortLayout;
import net.chestcat.network.SetSortLayoutPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-side copy of the sort layout settings. Call sync() right before any sort packet. */
public final class SortSettings {

    public static SortLayout layout = SortLayout.ROWS;
    public static boolean reverse = false;
    public static boolean includeHotbar = true;

    private SortSettings() {}

    public static void sync() {
        PacketDistributor.sendToServer(new SetSortLayoutPayload(layout, reverse, includeHotbar));
    }

    public static String summary() {
        return layout.getDisplayName() + ", from " + (reverse ? "bottom-right" : "top-left");
    }
}
