package net.chestcat.network;

import net.chestcat.FavoriteMode;
import net.chestcat.SortLayoutPrefs;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> Client: the sort chain / favorite behaviour / exclusions from a just-loaded sort profile. */
public record ApplyProfileExtrasPayload(String chain, FavoriteMode favoriteMode, boolean mergeStacks, String exclusions)
        implements CustomPacketPayload {

    public static final Type<ApplyProfileExtrasPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "apply_profile_extras"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ApplyProfileExtrasPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(512), ApplyProfileExtrasPayload::chain,
                    ByteBufCodecs.VAR_INT.map(FavoriteMode::fromOrdinal, FavoriteMode::ordinal),
                    ApplyProfileExtrasPayload::favoriteMode,
                    ByteBufCodecs.BOOL, ApplyProfileExtrasPayload::mergeStacks,
                    ByteBufCodecs.stringUtf8(1024), ApplyProfileExtrasPayload::exclusions,
                    ApplyProfileExtrasPayload::new
            );

    public static ApplyProfileExtrasPayload of(SortLayoutPrefs.Settings s) {
        return new ApplyProfileExtrasPayload(s.chain(), s.favoriteMode(), s.mergeStacks(), s.exclusions());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
