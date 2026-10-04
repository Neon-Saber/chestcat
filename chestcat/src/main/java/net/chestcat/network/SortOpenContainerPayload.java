package net.chestcat.network;

import net.chestcat.ItemSortMode;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: sort the container the player has open. {@code quiet} suppresses feedback (auto-sort on open). */
public record SortOpenContainerPayload(ItemSortMode sortMode, boolean quiet) implements CustomPacketPayload {

    public SortOpenContainerPayload(ItemSortMode sortMode) {
        this(sortMode, false);
    }

    public static final Type<SortOpenContainerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "sort_open_container"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SortOpenContainerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(
                            i -> ItemSortMode.values()[Math.floorMod(i, ItemSortMode.values().length)],
                            ItemSortMode::ordinal
                    ), SortOpenContainerPayload::sortMode,
                    ByteBufCodecs.BOOL, SortOpenContainerPayload::quiet,
                    SortOpenContainerPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
