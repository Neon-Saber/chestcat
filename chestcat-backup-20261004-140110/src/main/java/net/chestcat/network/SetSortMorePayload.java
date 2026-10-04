package net.chestcat.network;

import net.chestcat.SortMore;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: empty-slot handling, extra hotbar behaviour, category / mod order and name direction. */
public record SetSortMorePayload(SortMore more) implements CustomPacketPayload {

    public static final Type<SetSortMorePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "set_sort_more"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SetSortMorePayload> STREAM_CODEC =
            StreamCodec.composite(SortMore.STREAM_CODEC, SetSortMorePayload::more, SetSortMorePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
