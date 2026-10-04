package net.chestcat.network;

import net.chestcat.SortLayout;
import net.chestcat.SortLayoutPrefs;
import net.chestcat.TiebreakPriority;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> Client: the layout + tiebreak settings from a just-loaded sort profile.
 *  Wire-identical to SetSortLayoutPayload, just sent the other direction. */
public record ApplyProfileLayoutPayload(SortLayout layout, boolean reverse, boolean includeHotbar,
                                        boolean groupArmorBySlot, TiebreakPriority tiebreakPriority,
                                        boolean floatEnchantedFirst)
        implements CustomPacketPayload {

    public static final Type<ApplyProfileLayoutPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("chestcat", "apply_profile_layout"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ApplyProfileLayoutPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(SortLayout::fromOrdinal, SortLayout::ordinal),
                    ApplyProfileLayoutPayload::layout,
                    ByteBufCodecs.BOOL, ApplyProfileLayoutPayload::reverse,
                    ByteBufCodecs.BOOL, ApplyProfileLayoutPayload::includeHotbar,
                    ByteBufCodecs.BOOL, ApplyProfileLayoutPayload::groupArmorBySlot,
                    ByteBufCodecs.VAR_INT.map(TiebreakPriority::fromOrdinal, TiebreakPriority::ordinal),
                    ApplyProfileLayoutPayload::tiebreakPriority,
                    ByteBufCodecs.BOOL, ApplyProfileLayoutPayload::floatEnchantedFirst,
                    ApplyProfileLayoutPayload::new
            );

    public static ApplyProfileLayoutPayload of(SortLayoutPrefs.Settings s) {
        return new ApplyProfileLayoutPayload(s.layout(), s.reverse(), s.includeHotbar(),
                s.groupArmorBySlot(), s.tiebreakPriority(), s.floatEnchantedFirst());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
