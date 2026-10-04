package net.chestcat.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: delete the named saved sort profile (SortProfilesScreen's Delete button). */
public record DeleteSortProfilePayload(String name) implements CustomPacketPayload {

    public static final Type<DeleteSortProfilePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "delete_sort_profile"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, DeleteSortProfilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, DeleteSortProfilePayload::name,
                    DeleteSortProfilePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
