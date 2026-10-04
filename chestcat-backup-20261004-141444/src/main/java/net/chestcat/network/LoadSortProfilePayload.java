package net.chestcat.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: apply the named saved sort profile (SortProfilesScreen's Load button). */
public record LoadSortProfilePayload(String name) implements CustomPacketPayload {

    public static final Type<LoadSortProfilePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "load_sort_profile"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, LoadSortProfilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, LoadSortProfilePayload::name,
                    LoadSortProfilePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
