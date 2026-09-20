package net.chestcat.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record NearbyChestsResponsePayload(List<Entry> chests) implements CustomPacketPayload {

    public record Entry(BlockPos pos, String categoryName, int itemCount, int freeSlots) {}

    public static final Type<NearbyChestsResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "nearby_response"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Entry::pos,
            ByteBufCodecs.STRING_UTF8, Entry::categoryName,
            ByteBufCodecs.VAR_INT, Entry::itemCount,
            ByteBufCodecs.VAR_INT, Entry::freeSlots,
            Entry::new
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, NearbyChestsResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ENTRY_CODEC.apply(ByteBufCodecs.list()), NearbyChestsResponsePayload::chests,
                    NearbyChestsResponsePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
