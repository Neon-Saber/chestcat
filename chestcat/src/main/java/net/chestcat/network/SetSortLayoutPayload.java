package net.chestcat.network;

import net.chestcat.SortLayout;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetSortLayoutPayload(SortLayout layout, boolean reverse, boolean includeHotbar)
        implements CustomPacketPayload {

    public static final Type<SetSortLayoutPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "set_sort_layout"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SetSortLayoutPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(i -> SortLayout.fromOrdinal(i), SortLayout::ordinal),
                    SetSortLayoutPayload::layout,
                    ByteBufCodecs.BOOL, SetSortLayoutPayload::reverse,
                    ByteBufCodecs.BOOL, SetSortLayoutPayload::includeHotbar,
                    SetSortLayoutPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
