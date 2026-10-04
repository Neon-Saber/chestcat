package net.chestcat.network;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: "scan around me and send back what chests/barrels you find." */
public record RequestNearbyChestsPayload(int radius) implements CustomPacketPayload {

    public static final Type<RequestNearbyChestsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "request_nearby"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, RequestNearbyChestsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT, RequestNearbyChestsPayload::radius,
                    RequestNearbyChestsPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
