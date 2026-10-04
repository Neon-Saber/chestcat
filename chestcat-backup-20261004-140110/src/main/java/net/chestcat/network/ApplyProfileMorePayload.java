package net.chestcat.network;

import net.chestcat.SortMore;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> Client: the extra sort options from a just-loaded sort profile. Wire-identical to SetSortMorePayload. */
public record ApplyProfileMorePayload(SortMore more) implements CustomPacketPayload {

    public static final Type<ApplyProfileMorePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "apply_profile_more"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ApplyProfileMorePayload> STREAM_CODEC =
            StreamCodec.composite(SortMore.STREAM_CODEC, ApplyProfileMorePayload::more, ApplyProfileMorePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
