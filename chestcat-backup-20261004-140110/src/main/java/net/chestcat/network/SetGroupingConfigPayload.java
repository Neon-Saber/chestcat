package net.chestcat.network;

import net.chestcat.GroupingConfig;
import net.chestcat.ItemCategory;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Client -> Server: the player's current Category Splitting settings. */
public record SetGroupingConfigPayload(boolean splitOres, boolean splitWood, boolean splitStone,
                                       List<String> disabledCategories, List<String> mergedSubKeys)
        implements CustomPacketPayload {

    public static final Type<SetGroupingConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "set_grouping_config"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SetGroupingConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SetGroupingConfigPayload::splitOres,
                    ByteBufCodecs.BOOL, SetGroupingConfigPayload::splitWood,
                    ByteBufCodecs.BOOL, SetGroupingConfigPayload::splitStone,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), SetGroupingConfigPayload::disabledCategories,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(128)), SetGroupingConfigPayload::mergedSubKeys,
                    SetGroupingConfigPayload::new
            );

    public static SetGroupingConfigPayload of(GroupingConfig.Settings settings) {
        List<String> disabled = new ArrayList<>();
        for (ItemCategory category : settings.disabledCategories()) {
            disabled.add(category.name());
        }
        return new SetGroupingConfigPayload(settings.splitOres(), settings.splitWood(), settings.splitStone(),
                disabled, new ArrayList<>(settings.mergedSubKeys()));
    }

    /** Server side: rebuilds Settings, silently skipping anything unrecognised or oversized. */
    public GroupingConfig.Settings toSettings() {
        Set<ItemCategory> disabled = EnumSet.noneOf(ItemCategory.class);
        for (String name : disabledCategories) {
            try {
                disabled.add(ItemCategory.valueOf(name));
            } catch (IllegalArgumentException ignored) {
            }
        }
        Set<String> merged = new HashSet<>();
        for (String key : mergedSubKeys) {
            if (key.length() <= 64) merged.add(key);
        }
        return new GroupingConfig.Settings(splitOres, splitWood, splitStone, disabled, merged);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
