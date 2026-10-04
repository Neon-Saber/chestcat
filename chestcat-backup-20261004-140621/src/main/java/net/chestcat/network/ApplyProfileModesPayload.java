package net.chestcat.network;

import net.chestcat.ItemSortMode;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> Client: the inventory/chest/nearby sort modes from a just-loaded sort profile. */
public record ApplyProfileModesPayload(ItemSortMode inventoryMode, ItemSortMode chestMode, ItemSortMode nearbyMode)
        implements CustomPacketPayload {

    public static final Type<ApplyProfileModesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "apply_profile_modes"));

    private static final StreamCodec<io.netty.buffer.ByteBuf, ItemSortMode> MODE_CODEC =
            ByteBufCodecs.VAR_INT.map(
                    i -> ItemSortMode.values()[Math.floorMod(i, ItemSortMode.values().length)],
                    ItemSortMode::ordinal);

    public static final StreamCodec<io.netty.buffer.ByteBuf, ApplyProfileModesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    MODE_CODEC, ApplyProfileModesPayload::inventoryMode,
                    MODE_CODEC, ApplyProfileModesPayload::chestMode,
                    MODE_CODEC, ApplyProfileModesPayload::nearbyMode,
                    ApplyProfileModesPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
