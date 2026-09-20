package net.chestcat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ToggleColumnFillPayload() implements CustomPacketPayload {

    public static final Type<ToggleColumnFillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "toggle_column_fill"));

    public static final StreamCodec<ByteBuf, ToggleColumnFillPayload> STREAM_CODEC =
            StreamCodec.unit(new ToggleColumnFillPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}