package net.chestcat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QuickStackPayload() implements CustomPacketPayload {

    public static final Type<QuickStackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "quick_stack"));

    public static final StreamCodec<ByteBuf, QuickStackPayload> STREAM_CODEC =
            StreamCodec.unit(new QuickStackPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}