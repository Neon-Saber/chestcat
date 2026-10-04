package net.chestcat.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server -> Client: which inventory slots are locked (sent on login and after every toggle, so the client never drifts). */
public record SlotLocksSyncPayload(List<Integer> slots) implements CustomPacketPayload {

    public static final Type<SlotLocksSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "slot_locks_sync"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SlotLocksSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)), SlotLocksSyncPayload::slots,
                    SlotLocksSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
