package net.chestcat.client;

import net.chestcat.GroupingConfig;
import net.chestcat.network.SetGroupingConfigPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-side copy of the Category Splitting settings (edited in CategorySplitScreen). */
public final class GroupingSettings {

    public static GroupingConfig.Settings current = GroupingConfig.Settings.DEFAULT;

    private GroupingSettings() {}

    public static void sync() {
        PacketDistributor.sendToServer(SetGroupingConfigPayload.of(current));
    }
}
