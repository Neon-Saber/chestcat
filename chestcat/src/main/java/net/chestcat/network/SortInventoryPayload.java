package net.chestcat.network;

import net.chestcat.ItemSortMode;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SortInventoryPayload(ItemSortMode sortMode) implements CustomPacketPayload {

    public static final Type<SortInventoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "sort_inventory"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SortInventoryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(
                            i -> ItemSortMode.values()[i],
                            ItemSortMode::ordinal
                    ), SortInventoryPayload::sortMode,
                    SortInventoryPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}