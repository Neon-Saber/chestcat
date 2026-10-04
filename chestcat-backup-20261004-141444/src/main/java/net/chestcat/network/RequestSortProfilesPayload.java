package net.chestcat.network;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: ask for this player's saved sort-profile names (SortProfilesScreen opening). */
public record RequestSortProfilesPayload() implements CustomPacketPayload {

    public static final Type<RequestSortProfilesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "request_sort_profiles"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, RequestSortProfilesPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestSortProfilesPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
