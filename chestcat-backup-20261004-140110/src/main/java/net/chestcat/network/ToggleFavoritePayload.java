package net.chestcat.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: toggle the favorite state of the item in this slot of the player's open menu. */
public record ToggleFavoritePayload(int menuSlot) implements CustomPacketPayload {

    public static final Type<ToggleFavoritePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "toggle_favorite"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ToggleFavoritePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ToggleFavoritePayload::menuSlot,
                    ToggleFavoritePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
