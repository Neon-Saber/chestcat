package net.chestcat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DumpChestPayload() implements CustomPacketPayload {

    public static final Type<DumpChestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "dump_chest"));

    public static final StreamCodec<ByteBuf, DumpChestPayload> STREAM_CODEC =
            StreamCodec.unit(new DumpChestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}