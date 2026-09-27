package net.chestcat.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server -> Client: this player's saved sort-profile names, sorted. Sent after a
 *  RequestSortProfilesPayload and again after every save/load/delete so the list stays fresh. */
public record SortProfilesResponsePayload(List<String> names) implements CustomPacketPayload {

    public static final Type<SortProfilesResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "sort_profiles_response"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SortProfilesResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), SortProfilesResponsePayload::names,
                    SortProfilesResponsePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
