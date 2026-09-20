package net.chestcat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * True creative-tab ordering needs a different hook than
 * BuildCreativeModeTabContentsEvent (no public entries accessor in this
 * NeoForge version). Falls back to registry order for now.
 */
public final class CreativeOrder {

    private CreativeOrder() {}

    public static int getOrder(Item item) {
        return BuiltInRegistries.ITEM.getId(item);
    }
}