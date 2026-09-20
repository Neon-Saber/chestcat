package net.chestcat.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ToggleSlotLockPayload(int slotIndex) implements CustomPacketPayload {

    public static final Type<ToggleSlotLockPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "toggle_slot_lock"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ToggleSlotLockPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ToggleSlotLockPayload::slotIndex,
                    ToggleSlotLockPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}