package net.chestcat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fine-grained category used by the sort engine (the "Category" sort key and the
 * "cat:" exclusion rule). Separate from {@link ItemCategory}, which decides which
 * chest an item is routed to - this one only decides how items are grouped and ordered
 * inside a container, so it can be much more detailed without touching chest routing.
 *
 * Classification is data driven (item classes, vanilla tags, the shared "c:" convention
 * tags and registry-name suffixes), never a per-mod list, so modded items land in the right
 * place automatically. Results are cached per Item and the cache is dropped whenever tags
 * reload (see {@link SortCaches}).
 *
 * Enum order is the default display / sort order.
 */
public enum SortCategory {
    COMBAT("Combat"),
    ARMOR("Armor"),
    TOOLS("Tools"),
    FOOD("Food"),
    POTIONS("Potions"),
    ENCHANTING("Enchanting"),
    ORES("Ores"),
    INGOTS("Ingots"),
    GEMS("Gems"),
    MATERIALS("Materials"),
    REDSTONE("Redstone"),
    FARMING("Farming"),
    TRANSPORTATION("Transportation"),
    UTILITY("Utility"),
    MOB_DROPS("Mob Drops"),
    RARE("Rare Items"),
    BUILDING("Building"),
    DECORATION("Decoration"),
    MISC("Miscellaneous");

    private final String displayName;

    SortCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    // ------------------------------------------------------------------ cache

    private static final Map<Item, SortCategory> CACHE = new ConcurrentHashMap<>();

    public static void clearCache() {
        CACHE.clear();
    }

    public static SortCategory of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return MISC;
        return CACHE.computeIfAbsent(stack.getItem(), item -> classify(item.getDefaultInstance()));
    }

    /** Case-insensitive lookup by enum name or display name; null if nothing matches. */
    public static SortCategory byName(String text) {
        if (text == null) return null;
        String t = text.strip();
        for (SortCategory c : values()) {
            if (c.name().equalsIgnoreCase(t) || c.displayName.equalsIgnoreCase(t)) return c;
        }
        return null;
    }

    // ------------------------------------------------------------ tag helpers

    private static TagKey<Item> common(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private static List<TagKey<Item>> commonTags(String... paths) {
        List<TagKey<Item>> list = new ArrayList<>();
        for (String p : paths) list.add(common(p));
        return list;
    }

    private static Set<Item> items(Item... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    private static boolean anyTag(ItemStack stack, List<TagKey<Item>> tags) {
        for (TagKey<Item> tag : tags) {
            if (stack.is(tag)) return true;
        }
        return false;
    }

    private static final List<TagKey<Item>> COMBAT_TAGS = commonTags(
            "tools/bow", "tools/crossbow", "tools/shield", "tools/spear", "tools/mace",
            "tools/melee_weapon", "tools/ranged_weapon", "tools/sword", "tools/trident",
            "tools/bows", "tools/crossbows", "tools/shields", "tools/swords", "tools/spears");

    private static final List<TagKey<Item>> TOOL_TAGS = commonTags(
            "tools/pickaxe", "tools/shovel", "tools/hoe", "tools/fishing_rod", "tools/shears",
            "tools/brush", "tools/wrench", "tools/pickaxes", "tools/shovels", "tools/hoes",
            "tools/fishing_rods", "tools/wrenches", "tools");

    private static final List<TagKey<Item>> ORE_TAGS = commonTags("ores", "raw_materials");
    private static final List<TagKey<Item>> INGOT_TAGS = commonTags("ingots", "nuggets");
    private static final List<TagKey<Item>> GEM_TAGS = commonTags("gems");
    private static final List<TagKey<Item>> MATERIAL_TAGS = commonTags(
            "dusts", "plates", "gears", "rods", "wires", "slag", "ropes", "sheets");
    private static final List<TagKey<Item>> FARM_TAGS = commonTags(
            "seeds", "crops", "fertilizers", "vegetables", "fruits", "grains");
    private static final List<TagKey<Item>> DROP_TAGS = commonTags(
            "bones", "strings", "slime_balls", "gunpowders", "feathers", "leathers",
            "ender_pearls", "eggs", "rods/blaze", "rods/breeze", "nether_stars");
    private static final List<TagKey<Item>> DECOR_TAGS = commonTags(
            "dyes", "dyed", "glass_panes", "flowers", "music_discs");

    private static final Set<Item> ENCHANT_ITEMS = items(
            Items.BOOK, Items.ENCHANTING_TABLE, Items.BOOKSHELF, Items.EXPERIENCE_BOTTLE,
            Items.GRINDSTONE, Items.WRITABLE_BOOK, Items.WRITTEN_BOOK);

    private static final Set<Item> POTION_ITEMS = items(
            Items.GLASS_BOTTLE, Items.BREWING_STAND, Items.BLAZE_POWDER, Items.NETHER_WART,
            Items.FERMENTED_SPIDER_EYE, Items.DRAGON_BREATH, Items.GLISTERING_MELON_SLICE);

    private static final Set<Item> REDSTONE_ITEMS = items(
            Items.REDSTONE, Items.REDSTONE_TORCH, Items.REPEATER, Items.COMPARATOR, Items.PISTON,
            Items.STICKY_PISTON, Items.OBSERVER, Items.HOPPER, Items.DISPENSER, Items.DROPPER,
            Items.TARGET, Items.LEVER, Items.REDSTONE_LAMP, Items.REDSTONE_BLOCK, Items.TRIPWIRE_HOOK,
            Items.DAYLIGHT_DETECTOR, Items.NOTE_BLOCK, Items.LIGHTNING_ROD, Items.CRAFTER,
            Items.SCULK_SENSOR, Items.CALIBRATED_SCULK_SENSOR, Items.SLIME_BLOCK, Items.HONEY_BLOCK,
            Items.TNT, Items.BELL);

    private static final Set<Item> FARM_ITEMS = items(
            Items.WHEAT, Items.SUGAR_CANE, Items.BAMBOO, Items.CACTUS, Items.COCOA_BEANS,
            Items.PUMPKIN, Items.MELON, Items.HAY_BLOCK, Items.BONE_MEAL, Items.COMPOSTER,
            Items.KELP, Items.SEA_PICKLE);

    private static final Set<String> TRANSPORT_NAMES = new HashSet<>(List.of(
            "saddle", "carrot_on_a_stick", "warped_fungus_on_a_stick"));

    private static final Set<String> MOB_DROP_NAMES = new HashSet<>(List.of(
            "bone", "string", "gunpowder", "slime_ball", "ender_pearl", "blaze_rod", "ghast_tear",
            "feather", "leather", "rabbit_hide", "rabbit_foot", "phantom_membrane", "ink_sac",
            "glow_ink_sac", "magma_cream", "nautilus_shell", "shulker_shell", "scute", "armadillo_scute",
            "breeze_rod", "egg", "honeycomb", "wither_skeleton_skull", "prismarine_shard",
            "prismarine_crystals", "turtle_scute", "heart_of_the_sea", "echo_shard"));

    private static final Set<String> MATERIAL_NAMES = new HashSet<>(List.of(
            "stick", "paper", "flint", "clay_ball", "brick", "nether_brick", "coal", "charcoal",
            "glowstone_dust", "sugar", "netherite_upgrade_smithing_template", "resin_clump", "disc_fragment_5"));

    private static final Set<String> GEM_NAMES = new HashSet<>(List.of(
            "diamond", "emerald", "lapis_lazuli", "quartz", "amethyst_shard"));

    private static final Set<String> DECOR_WORDS_SET = new HashSet<>(List.of(
            "carpet", "banner", "candle", "lantern", "flower", "painting", "item_frame", "flower_pot",
            "sign", "coral", "torch", "pattern", "head", "skull", "bed", "pot", "armor_stand", "glow_item_frame"));

    private static final Set<String> UTILITY_NAMES = new HashSet<>(List.of(
            "crafting_table", "furnace", "blast_furnace", "smoker", "chest", "trapped_chest", "barrel",
            "ender_chest", "smithing_table", "stonecutter", "loom", "cartography_table", "fletching_table",
            "lectern", "clock", "ender_eye", "anvil", "chipped_anvil", "damaged_anvil", "cauldron",
            "totem_of_undying", "spyglass", "recovery_compass", "name_tag"));

    // -------------------------------------------------------------- classify

    private static SortCategory classify(ItemStack stack) {
        Item item = stack.getItem();
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();

        // Combat: swords, axes, bows, crossbows, tridents, maces, shields, arrows.
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof BowItem
                || item instanceof CrossbowItem || item instanceof TridentItem || item instanceof MaceItem
                || item instanceof ShieldItem || item instanceof ArrowItem
                || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.ARROWS)
                || anyTag(stack, COMBAT_TAGS)) {
            return COMBAT;
        }

        if (item instanceof ArmorItem || item instanceof ElytraItem || stack.is(ItemTags.TRIMMABLE_ARMOR)) {
            return ARMOR;
        }

        if (item instanceof DiggerItem || item instanceof ShearsItem || item instanceof FishingRodItem
                || item instanceof FlintAndSteelItem || item instanceof BrushItem
                || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)
                || anyTag(stack, TOOL_TAGS)) {
            return TOOLS;
        }

        if (stack.getFoodProperties(null) != null) {
            return FOOD;
        }

        if (item instanceof PotionItem || POTION_ITEMS.contains(item) || path.contains("potion")) {
            return POTIONS;
        }

        if (item instanceof EnchantedBookItem || ENCHANT_ITEMS.contains(item)) {
            return ENCHANTING;
        }

        if (REDSTONE_ITEMS.contains(item) || path.contains("redstone")
                || stack.is(ItemTags.WOODEN_BUTTONS) || stack.is(ItemTags.STONE_BUTTONS)
                || stack.is(ItemTags.WOODEN_PRESSURE_PLATES)
                || (item instanceof BlockItem plate && plate.getBlock() instanceof net.minecraft.world.level.block.BasePressurePlateBlock)
                || path.endsWith("_piston")) {
            return REDSTONE;
        }

        // Raw materials by shape: ores, ingots, gems, then everything else crafting-ish.
        if (path.endsWith("_ore") || path.startsWith("raw_") || path.equals("ancient_debris")
                || anyTag(stack, ORE_TAGS)) {
            return ORES;
        }
        if (path.endsWith("_ingot") || path.endsWith("_nugget") || path.equals("netherite_scrap")
                || anyTag(stack, INGOT_TAGS)) {
            return INGOTS;
        }
        if (GEM_NAMES.contains(path) || path.endsWith("_gem") || anyTag(stack, GEM_TAGS)) {
            return GEMS;
        }

        if (stack.is(ItemTags.RAILS) || stack.is(ItemTags.BOATS) || stack.is(ItemTags.CHEST_BOATS)
                || path.contains("minecart") || path.endsWith("_boat") || path.endsWith("_raft")
                || TRANSPORT_NAMES.contains(path)) {
            return TRANSPORTATION;
        }

        if (MOB_DROP_NAMES.contains(path) || anyTag(stack, DROP_TAGS)) {
            return MOB_DROPS;
        }

        if (stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) || stack.is(ItemTags.SAPLINGS)
                || FARM_ITEMS.contains(item) || path.contains("seed") || anyTag(stack, FARM_TAGS)) {
            return FARMING;
        }

        if (MATERIAL_NAMES.contains(path) || stack.is(ItemTags.COALS) || anyTag(stack, MATERIAL_TAGS)
                || path.endsWith("_dust") || path.endsWith("_plate") || path.endsWith("_gear")
                || path.endsWith("_sheet") || path.endsWith("_rod")) {
            return MATERIALS;
        }

        // Rare items that aren't equipment/food: beacons, dragon eggs, music discs, command blocks...
        Rarity rarity = stack.getRarity();
        if (rarity == Rarity.EPIC || rarity == Rarity.RARE) {
            return RARE;
        }

        if (item instanceof BucketItem || item instanceof MapItem || item instanceof EmptyMapItem
                || item instanceof CompassItem || item instanceof LeadItem || item instanceof NameTagItem
                || item instanceof SpawnEggItem || item instanceof FireworkRocketItem
                || item instanceof BundleItem || UTILITY_NAMES.contains(path)
                || (item instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock)) {
            return UTILITY;
        }

        if (stack.is(ItemTags.DYEABLE) || item instanceof DyeItem || item instanceof BannerItem
                || item instanceof BannerPatternItem || item instanceof HangingEntityItem
                || item instanceof SignItem || stack.is(ItemTags.BANNERS) || stack.is(ItemTags.FLOWERS)
                || stack.is(ItemTags.CANDLES) || stack.is(ItemTags.WOOL) || stack.is(ItemTags.WOOL_CARPETS)
                || stack.is(ItemTags.BEDS) || stack.is(ItemTags.SIGNS) || stack.is(ItemTags.HANGING_SIGNS)
                || stack.is(ItemTags.TERRACOTTA) || anyTag(stack, DECOR_TAGS) || containsWord(path, DECOR_WORDS_SET)) {
            return DECORATION;
        }

        if (item instanceof BlockItem) {
            return BUILDING;
        }

        return MISC;
    }

    /** True when any underscore-separated word of the path (or the whole path) is in the set. */
    private static boolean containsWord(String path, Set<String> words) {
        if (words.contains(path)) return true;
        for (String part : path.split("_")) {
            if (words.contains(part)) return true;
        }
        return false;
    }
}
