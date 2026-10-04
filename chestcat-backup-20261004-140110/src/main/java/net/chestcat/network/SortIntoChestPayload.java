package net.chestcat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SortIntoChestPayload() implements CustomPacketPayload {

    public static final Type<SortIntoChestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "sort_into_chest"));

    public static final StreamCodec<ByteBuf, SortIntoChestPayload> STREAM_CODEC =
            StreamCodec.unit(new SortIntoChestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}