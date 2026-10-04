package net.chestcat.network;

import net.chestcat.ItemSortMode;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: sort the player's inventory. {@code quiet} suppresses the chat / action-bar feedback (auto-sort on open). */
public record SortInventoryPayload(ItemSortMode sortMode, boolean quiet) implements CustomPacketPayload {

    public SortInventoryPayload(ItemSortMode sortMode) {
        this(sortMode, false);
    }

    public static final Type<SortInventoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "sort_inventory"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SortInventoryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(
                            i -> ItemSortMode.values()[Math.floorMod(i, ItemSortMode.values().length)],
                            ItemSortMode::ordinal
                    ), SortInventoryPayload::sortMode,
                    ByteBufCodecs.BOOL, SortInventoryPayload::quiet,
                    SortInventoryPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
