package net.chestcat.network;

import net.chestcat.ItemSortMode;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: save the player's current layout/grouping settings (already synced via
 *  SetSortLayoutPayload/SetGroupingConfigPayload) plus these three sort modes as a named profile. */
public record SaveSortProfilePayload(String name, ItemSortMode inventoryMode,
                                     ItemSortMode chestMode, ItemSortMode nearbyMode)
        implements CustomPacketPayload {

    public static final Type<SaveSortProfilePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "save_sort_profile"));

    private static final StreamCodec<io.netty.buffer.ByteBuf, ItemSortMode> MODE_CODEC =
            ByteBufCodecs.VAR_INT.map(
                    i -> ItemSortMode.values()[Math.floorMod(i, ItemSortMode.values().length)],
                    ItemSortMode::ordinal);

    public static final StreamCodec<io.netty.buffer.ByteBuf, SaveSortProfilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SaveSortProfilePayload::name,
                    MODE_CODEC, SaveSortProfilePayload::inventoryMode,
                    MODE_CODEC, SaveSortProfilePayload::chestMode,
                    MODE_CODEC, SaveSortProfilePayload::nearbyMode,
                    SaveSortProfilePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
