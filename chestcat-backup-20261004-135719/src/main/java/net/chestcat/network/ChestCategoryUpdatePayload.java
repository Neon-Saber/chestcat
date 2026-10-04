package net.chestcat.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: pushed the instant a player assigns/resets/excludes a single chest,
 * so the floating icon updates immediately instead of waiting for the next ~5s background poll.
 */
public record ChestCategoryUpdatePayload(NearbyChestsResponsePayload.Entry entry) implements CustomPacketPayload {

    public static final Type<ChestCategoryUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "chest_category_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChestCategoryUpdatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    NearbyChestsResponsePayload.ENTRY_CODEC, ChestCategoryUpdatePayload::entry,
                    ChestCategoryUpdatePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}