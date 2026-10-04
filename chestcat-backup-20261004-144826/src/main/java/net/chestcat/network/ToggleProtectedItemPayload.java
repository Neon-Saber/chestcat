package net.chestcat.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: flip global auto-sort protection for the item in the given hovered slot. */
public record ToggleProtectedItemPayload(int slotIndex) implements CustomPacketPayload {

    public static final Type<ToggleProtectedItemPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "toggle_protected_item"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ToggleProtectedItemPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ToggleProtectedItemPayload::slotIndex,
                    ToggleProtectedItemPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
