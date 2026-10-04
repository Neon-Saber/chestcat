package net.chestcat.network;

import net.chestcat.GroupingConfig;
import net.chestcat.ItemCategory;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server -> Client: the Category Splitting settings from a just-loaded sort profile.
 *  Wire-identical to SetGroupingConfigPayload, just sent the other direction. */
public record ApplyProfileGroupingPayload(boolean splitOres, boolean splitWood, boolean splitStone,
                                          List<String> disabledCategories, List<String> mergedSubKeys)
        implements CustomPacketPayload {

    public static final Type<ApplyProfileGroupingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "apply_profile_grouping"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ApplyProfileGroupingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ApplyProfileGroupingPayload::splitOres,
                    ByteBufCodecs.BOOL, ApplyProfileGroupingPayload::splitWood,
                    ByteBufCodecs.BOOL, ApplyProfileGroupingPayload::splitStone,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), ApplyProfileGroupingPayload::disabledCategories,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(128)), ApplyProfileGroupingPayload::mergedSubKeys,
                    ApplyProfileGroupingPayload::new
            );

    public static ApplyProfileGroupingPayload of(GroupingConfig.Settings settings) {
        List<String> disabled = new ArrayList<>();
        for (ItemCategory category : settings.disabledCategories()) {
            disabled.add(category.name());
        }
        return new ApplyProfileGroupingPayload(settings.splitOres(), settings.splitWood(), settings.splitStone(),
                disabled, new ArrayList<>(settings.mergedSubKeys()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
