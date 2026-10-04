package net.chestcat.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server -> Client: the player's complete favorite list (sent on login and after every change). */
public record FavoritesSyncPayload(List<String> keys) implements CustomPacketPayload {

    public static final Type<FavoritesSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "favorites_sync"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, FavoritesSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(1024)), FavoritesSyncPayload::keys,
                    FavoritesSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
