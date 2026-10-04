package net.chestcat;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stable identity for a favorited item - never a slot number, so a favorite follows the
 * item through sorting, chests, hotbar and every other container.
 *
 * Stackable items (blocks, food, materials) use their registry id, so favoriting diamonds
 * favorites all diamonds. Unstackable gear (tools, armor, weapons) also includes enchantments
 * and a custom name, so your enchanted Netherite Sword is favorited without also favoriting
 * every plain one. Damage is deliberately left out so wear never un-favorites an item.
 */
public final class FavoriteKey {

    private FavoriteKey() {}

    public static String of(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (stack.getMaxStackSize() > 1) return id;

        StringBuilder sb = new StringBuilder(id);
        ItemEnchantments ench = stack.get(DataComponents.ENCHANTMENTS);
        if (ench != null && !ench.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Object2IntMap.Entry<Holder<Enchantment>> e : ench.entrySet()) {
                parts.add(e.getKey().getRegisteredName() + "=" + e.getIntValue());
            }
            Collections.sort(parts);
            sb.append('#').append(String.join(",", parts));
        }
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom != null) sb.append('@').append(custom.getString());
        return sb.toString();
    }
}
