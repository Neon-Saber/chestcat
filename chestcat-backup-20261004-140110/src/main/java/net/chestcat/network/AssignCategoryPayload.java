package net.chestcat.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: "set this chest's category to X" (or "AUTO" to clear the manual override). */
public record AssignCategoryPayload(BlockPos pos, String categoryName) implements CustomPacketPayload {

    public static final Type<AssignCategoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "assign_category"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssignCategoryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AssignCategoryPayload::pos,
                    ByteBufCodecs.STRING_UTF8, AssignCategoryPayload::categoryName,
                    AssignCategoryPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
