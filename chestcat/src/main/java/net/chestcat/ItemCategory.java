package net.chestcat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;

/**
 * The set of storage categories a chest can be assigned to, and the logic
 * used to guess an ItemStack's category when auto-detecting.
 *
 * Order matters slightly for display purposes only; detection uses explicit
 * checks below, not enum order.
 */
public enum ItemCategory {
    TOOLS("Tools"),
    WEAPONS("Weapons"),
    ARMOR("Armor"),
    FOOD("Food"),
    ORES_AND_INGOTS("Ores & Ingots"),
    REDSTONE("Redstone"),
    POTIONS_AND_BREWING("Potions & Brewing"),
    DYES_AND_DECORATION("Dyes & Decoration"),
    WOOD("Wood"),
    BLOCKS("Blocks"),
    MISC("Misc");

    private final String displayName;

    ItemCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Best-effort classification of an ItemStack into a single category.
     * Uses vanilla item classes and tags first (cheap, reliable), falls back
     * to MISC when nothing matches.
     */
    public static ItemCategory categorize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return MISC;
        Item item = stack.getItem();

        if (item instanceof TieredItem || item instanceof DiggerItem
                || item instanceof ShearsItem || item instanceof FishingRodItem
                || item instanceof FlintAndSteelItem || item instanceof HoeItem) {
            if (item instanceof SwordItem) return WEAPONS;
            return TOOLS;
        }

        if (item instanceof SwordItem || item instanceof BowItem
                || item instanceof CrossbowItem || item instanceof TridentItem) {
            return WEAPONS;
        }

        if (item instanceof ArmorItem || item instanceof ElytraItem) {
            return ARMOR;
        }

        if (stack.getFoodProperties(null) != null || stack.is(ItemTags.MEAT)
                || stack.is(ItemTags.FISHES)) {
            return FOOD;
        }

        if (item instanceof PotionItem || item instanceof SplashPotionItem
                || item instanceof LingeringPotionItem
                || stack.is(Items.BLAZE_POWDER)
                || item == Items.BREWING_STAND || item == Items.CAULDRON
                || item == Items.GLASS_BOTTLE || item == Items.NETHER_WART) {
            return POTIONS_AND_BREWING;
        }

        if (stack.is(ItemTags.DYEABLE) || item instanceof DyeItem
                || stack.is(ItemTags.BANNERS) || item == Items.PAINTING
                || item == Items.ITEM_FRAME || item == Items.FLOWER_POT) {
            return DYES_AND_DECORATION;
        }

        if (item == Items.REDSTONE || item == Items.REDSTONE_TORCH
                || item == Items.REPEATER || item == Items.COMPARATOR
                || item == Items.PISTON || item == Items.STICKY_PISTON
                || item == Items.OBSERVER || item == Items.HOPPER
                || item == Items.DISPENSER || item == Items.DROPPER
                || item == Items.TARGET || item == Items.LEVER
                || item == Items.REDSTONE_LAMP || item == Items.REDSTONE_BLOCK
                || item == Items.TRIPWIRE_HOOK || item == Items.DAYLIGHT_DETECTOR) {
            return REDSTONE;
        }

        if (isOreOrIngot(item)) {
            return ORES_AND_INGOTS;
        }

        if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS)
                || stack.is(ItemTags.WOODEN_SLABS) || stack.is(ItemTags.WOODEN_STAIRS)
                || stack.is(ItemTags.WOODEN_FENCES) || stack.is(ItemTags.WOODEN_DOORS)
                || stack.is(ItemTags.WOODEN_TRAPDOORS) || stack.is(ItemTags.SAPLINGS)) {
            return WOOD;
        }

        if (item instanceof BlockItem) {
            return BLOCKS;
        }

        return MISC;
    }

    private static boolean isOreOrIngot(Item item) {
        String name = BuiltInRegistries.ITEM.getKey(item).getPath();
        return name.endsWith("_ore") || name.endsWith("_ingot")
                || name.endsWith("_nugget") || name.equals("raw_iron")
                || name.equals("raw_gold") || name.equals("raw_copper")
                || name.equals("coal") || name.equals("charcoal")
                || name.equals("diamond") || name.equals("emerald")
                || name.equals("lapis_lazuli") || name.equals("quartz")
                || name.equals("netherite_scrap") || name.equals("netherite_ingot");
    }
}
