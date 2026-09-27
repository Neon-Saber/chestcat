package net.chestcat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Your own sort groups, read from config/chestcat/groups.json (created with examples on first run,
 * re-read automatically whenever you save the file - no restart needed).
 *
 * A group matches an item if ANY of these hit (checked in file order, first group wins):
 *   "items"    - exact item ids ("minecraft:bone") or bare paths ("bone")
 *   "contains" - text found anywhere in the item's id path ("deepslate", "_ore")
 *   "tags"     - item tags ("minecraft:logs", "c:ingots")
 *   "mods"     - every item from those mod ids
 * ...unless one of its "exclude" texts is found in the item's path (explicit "items" still win).
 * A chest is pointed at a group through the category picker (Custom Groups). Chests without a
 * group keep working exactly as before.
 */
public final class CustomGroups {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String DEFAULT_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    public static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";

    public record Group(String name, String description, String icon, Set<String> items,
                        List<String> contains, List<String> exclude,
                        List<TagKey<Item>> tags, Set<String> mods) {}

    public record AiSettings(boolean enabled, String endpoint, String model, String apiKey) {}

    private static volatile List<Group> groups = List.of();
    private static volatile List<String> extraStorage = List.of();
    private static volatile AiSettings ai = new AiSettings(false, DEFAULT_ENDPOINT, DEFAULT_MODEL, "");
    private static long lastModified = Long.MIN_VALUE;
    private static long lastCheckMs = 0L;

    private CustomGroups() {}

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve("chestcat");
    }

    public static Path groupsFile() {
        return configDir().resolve("groups.json");
    }

    /** Loads / reloads the file if it changed. Cheap: looks at the disk at most once a second. */
    public static synchronized void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastCheckMs < 1000L) return;
        lastCheckMs = now;

        Path file = groupsFile();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_JSON, StandardCharsets.UTF_8);
            }
            long modified = Files.getLastModifiedTime(file).toMillis();
            if (modified == lastModified) return;
            lastModified = modified;
            parse(Files.readString(file, StandardCharsets.UTF_8));
            LOGGER.info("[ChestCat] Loaded {} custom group(s) from {}", groups.size(), file);
        } catch (Exception e) {
            LOGGER.warn("[ChestCat] Could not read {} (keeping previous groups): {}", file, e.toString());
        }
    }

    private static void parse(String text) {
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();

        List<Group> parsed = new ArrayList<>();
        if (root.has("groups") && root.get("groups").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("groups")) {
                if (!element.isJsonObject()) continue;
                JsonObject g = element.getAsJsonObject();
                String name = string(g, "name").trim();
                if (name.isEmpty()) continue;
                parsed.add(new Group(name, string(g, "description"), string(g, "icon"),
                        new HashSet<>(lowerList(g, "items")), lowerList(g, "contains"),
                        lowerList(g, "exclude"), tagList(g, "tags"), new HashSet<>(lowerList(g, "mods"))));
            }
        }

        AiSettings parsedAi = new AiSettings(false, DEFAULT_ENDPOINT, DEFAULT_MODEL, "");
        if (root.has("ai") && root.get("ai").isJsonObject()) {
            JsonObject a = root.getAsJsonObject("ai");
            String endpoint = string(a, "endpoint").trim();
            String model = string(a, "model").trim();
            parsedAi = new AiSettings(
                    a.has("enabled") && a.get("enabled").isJsonPrimitive() && a.get("enabled").getAsBoolean(),
                    endpoint.isEmpty() ? DEFAULT_ENDPOINT : endpoint,
                    model.isEmpty() ? DEFAULT_MODEL : model,
                    string(a, "apiKey").trim());
        }

        groups = List.copyOf(parsed);
        extraStorage = List.copyOf(lowerList(root, "extraStorage"));
        ai = parsedAi;
    }

    public static List<Group> groups() {
        return groups;
    }

    public static AiSettings ai() {
        return ai;
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (Group g : groups) names.add(g.name());
        return names;
    }

    public static boolean hasGroup(String name) {
        for (Group g : groups) {
            if (g.name().equals(name)) return true;
        }
        return false;
    }

    /** Name of the first custom group this item belongs to, or null. */
    public static String match(ItemStack stack) {
        List<Group> current = groups;
        if (current.isEmpty() || stack.isEmpty()) return null;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String full = id.toString();
        String path = id.getPath();
        String namespace = id.getNamespace();

        for (Group g : current) {
            if (matches(g, stack, full, path, namespace)) return g.name();
        }
        return null;
    }

    private static boolean matches(Group g, ItemStack stack, String full, String path, String namespace) {
        if (g.items().contains(full) || g.items().contains(path)) return true;
        for (String excluded : g.exclude()) {
            if (path.contains(excluded)) return false;
        }
        if (g.mods().contains(namespace)) return true;
        for (String part : g.contains()) {
            if (path.contains(part)) return true;
        }
        for (TagKey<Item> tag : g.tags()) {
            if (stack.is(tag)) return true;
        }
        return false;
    }

    /** The item drawn floating above chests assigned to this group ("icon" in the file, else a name tag). */
    public static Item iconItem(String name) {
        refresh();
        for (Group g : groups) {
            if (!g.name().equals(name) || g.icon().isBlank()) continue;
            ResourceLocation id = ResourceLocation.tryParse(g.icon().trim());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.get(id);
        }
        return Items.NAME_TAG;
    }

    /** True if "extraStorage" allows this block: "modid" or "modid:*" for a whole mod, or "modid:block". */
    public static boolean isExtraStorage(ResourceLocation blockId) {
        String full = blockId.toString();
        String namespace = blockId.getNamespace();
        for (String entry : extraStorage) {
            if (entry.equals(full) || entry.equals(namespace) || entry.equals(namespace + ":*")) return true;
        }
        return false;
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static List<String> lowerList(JsonObject o, String key) {
        List<String> result = new ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(key)) {
                if (e.isJsonPrimitive()) result.add(e.getAsString().trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static List<TagKey<Item>> tagList(JsonObject o, String key) {
        List<TagKey<Item>> result = new ArrayList<>();
        for (String raw : lowerList(o, key)) {
            ResourceLocation id = ResourceLocation.tryParse(raw.startsWith("#") ? raw.substring(1) : raw);
            if (id != null) result.add(TagKey.create(Registries.ITEM, id));
        }
        return result;
    }

    private static final String DEFAULT_JSON = """
            {
              "_help": [
                "Each group can match by: items (exact ids), contains (text in the item id), tags, mods. 'exclude' removes matches. First matching group wins.",
                "Point a chest at a group in-game: open the C menu, click a chest, Custom Groups. Chests without a group sort as usual.",
                "extraStorage: modded blocks ChestCat may use as storage, e.g. 'ironchest' (whole mod) or 'ironchest:gold_chest'. Furnaces, smokers, crafters, hoppers and machines are never touched.",
                "ai: optional. Set enabled=true and paste a FREE API key (Groq: console.groq.com, or Google AI Studio via its OpenAI-compatible endpoint). Unknown modded items get classified once, then cached forever."
              ],
              "groups": [
                {
                  "name": "Mob Drops",
                  "description": "Items dropped by mobs",
                  "icon": "minecraft:bone",
                  "items": ["rotten_flesh", "bone", "string", "spider_eye", "gunpowder", "ender_pearl", "blaze_rod", "ghast_tear", "slime_ball", "magma_cream", "feather", "leather", "rabbit_hide", "rabbit_foot", "ink_sac", "glow_ink_sac", "phantom_membrane", "prismarine_shard", "prismarine_crystals", "nautilus_shell", "scute", "armadillo_scute", "nether_star", "shulker_shell", "dragon_breath", "breeze_rod", "wither_skeleton_skull", "totem_of_undying", "egg"]
                },
                {
                  "name": "Wood",
                  "description": "Logs, planks and everything crafted from wood",
                  "icon": "minecraft:oak_log",
                  "items": ["stick", "bamboo"],
                  "tags": ["minecraft:logs", "minecraft:planks", "minecraft:wooden_slabs", "minecraft:wooden_stairs", "minecraft:wooden_fences", "minecraft:fence_gates", "minecraft:wooden_doors", "minecraft:wooden_trapdoors", "minecraft:wooden_buttons", "minecraft:wooden_pressure_plates", "minecraft:saplings", "minecraft:leaves", "minecraft:signs", "minecraft:hanging_signs", "minecraft:boats"]
                },
                {
                  "name": "Underground",
                  "description": "Stone, ores, minerals and ingots",
                  "icon": "minecraft:raw_iron",
                  "items": ["coal", "charcoal", "diamond", "emerald", "lapis_lazuli", "quartz", "amethyst_shard", "flint"],
                  "contains": ["_ore", "raw_", "deepslate", "cobble", "stone", "andesite", "diorite", "granite", "tuff", "gravel", "amethyst", "calcite", "dripstone", "basalt", "obsidian", "ancient_debris", "netherite_scrap", "_ingot", "_nugget"],
                  "exclude": ["redstone", "glowstone", "sandstone", "grindstone", "stonecutter", "lodestone", "_sword", "_pickaxe", "_axe", "_shovel", "_hoe", "_helmet", "_chestplate", "_leggings", "_boots", "_horse_armor"]
                },
                {
                  "name": "Farming",
                  "description": "Crops, seeds and farm produce",
                  "icon": "minecraft:wheat",
                  "items": ["wheat", "wheat_seeds", "beetroot", "beetroot_seeds", "carrot", "potato", "poisonous_potato", "pumpkin", "carved_pumpkin", "melon", "melon_slice", "melon_seeds", "pumpkin_seeds", "sugar_cane", "sugar", "cocoa_beans", "sweet_berries", "glow_berries", "kelp", "dried_kelp", "bone_meal", "hay_block", "torchflower_seeds", "pitcher_pod", "nether_wart"],
                  "tags": ["c:crops", "c:seeds"]
                }
              ],
              "extraStorage": [],
              "ai": {
                "enabled": false,
                "endpoint": "https://api.groq.com/openai/v1/chat/completions",
                "model": "llama-3.3-70b-versatile",
                "apiKey": ""
              }
            }
            """;
}
