package net.chestcat.network;

import net.chestcat.FavoriteMode;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: the sort chain, favorite behaviour, stack merging and exclusion rules. */
public record SetSortExtrasPayload(String chain, FavoriteMode favoriteMode, boolean mergeStacks, String exclusions)
        implements CustomPacketPayload {

    public static final Type<SetSortExtrasPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "set_sort_extras"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SetSortExtrasPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(512), SetSortExtrasPayload::chain,
                    ByteBufCodecs.VAR_INT.map(FavoriteMode::fromOrdinal, FavoriteMode::ordinal),
                    SetSortExtrasPayload::favoriteMode,
                    ByteBufCodecs.BOOL, SetSortExtrasPayload::mergeStacks,
                    ByteBufCodecs.stringUtf8(1024), SetSortExtrasPayload::exclusions,
                    SetSortExtrasPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
