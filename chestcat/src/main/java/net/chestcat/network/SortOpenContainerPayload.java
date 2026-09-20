package net.chestcat.network;

import net.chestcat.ItemSortMode;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SortOpenContainerPayload(ItemSortMode sortMode) implements CustomPacketPayload {

    public static final Type<SortOpenContainerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "sort_open_container"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SortOpenContainerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(
                            i -> ItemSortMode.values()[i],
                            ItemSortMode::ordinal
                    ), SortOpenContainerPayload::sortMode,
                    SortOpenContainerPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}