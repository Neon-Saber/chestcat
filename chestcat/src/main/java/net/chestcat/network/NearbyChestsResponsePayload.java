package net.chestcat.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record NearbyChestsResponsePayload(List<Entry> chests) implements CustomPacketPayload {

    /**
     * kindTag is one of "CHEST", "BARREL", "ENDER_CHEST", or "MODDED:<mod id>" for
     * anything that isn't vanilla storage - the client splits on ':' to group by mod.
     * autoCategoryName is the server's best guess at what's dominant in the container,
     * used by the client to sort/label chests that have no manual category assigned.
     */
    public record Entry(BlockPos pos, String categoryName, String autoCategoryName,
                         int itemCount, int freeSlots, String kindTag) {}

    public static final Type<NearbyChestsResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "nearby_response"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Entry::pos,
            ByteBufCodecs.STRING_UTF8, Entry::categoryName,
            ByteBufCodecs.STRING_UTF8, Entry::autoCategoryName,
            ByteBufCodecs.VAR_INT, Entry::itemCount,
            ByteBufCodecs.VAR_INT, Entry::freeSlots,
            ByteBufCodecs.STRING_UTF8, Entry::kindTag,
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
