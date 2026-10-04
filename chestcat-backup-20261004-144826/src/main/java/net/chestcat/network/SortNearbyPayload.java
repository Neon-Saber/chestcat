package net.chestcat.network;

import net.chestcat.ItemSortMode;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SortNearbyPayload(int radius, ItemSortMode sortMode) implements CustomPacketPayload {

    public static final Type<SortNearbyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "sort_nearby"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SortNearbyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SortNearbyPayload::radius,
                    ByteBufCodecs.VAR_INT.map(
                            i -> ItemSortMode.values()[i],
                            ItemSortMode::ordinal
                    ), SortNearbyPayload::sortMode,
                    SortNearbyPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}