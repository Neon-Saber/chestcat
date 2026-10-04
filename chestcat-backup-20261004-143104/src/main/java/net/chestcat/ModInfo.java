package net.chestcat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mod identity lookups for the sort engine. Mods are detected from each item's registry
 * namespace and resolved to a display name through the mod list, so every installed mod
 * works automatically - nothing here needs updating when a new mod is added.
 */
public final class ModInfo {

    private ModInfo() {}

    private static final Map<String, String> DISPLAY_NAMES = new ConcurrentHashMap<>();

    /** The registry namespace of the item, e.g. "minecraft" or "create". */
    public static String namespace(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    /** Human-readable mod name ("Create", "Minecraft"); falls back to the namespace itself. */
    public static String displayName(String modId) {
        return DISPLAY_NAMES.computeIfAbsent(modId, ModInfo::lookupDisplayName);
    }

    private static String lookupDisplayName(String modId) {
        try {
            net.neoforged.fml.ModList list = net.neoforged.fml.ModList.get();
            if (list == null) return modId;
            Optional<String> name = list.getModContainerById(modId)
                    .map(container -> container.getModInfo().getDisplayName());
            return name.orElse(modId);
        } catch (Throwable t) {
            return modId;
        }
    }

    /** Sort name: vanilla first (empty string), then every other mod by display name. */
    public static String sortName(ItemStack stack) {
        String ns = namespace(stack);
        if (ns.equals("minecraft")) return "";
        return displayName(ns).toLowerCase(Locale.ROOT);
    }
}
