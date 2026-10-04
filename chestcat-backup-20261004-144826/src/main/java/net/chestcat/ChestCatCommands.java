package net.chestcat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.chestcat.data.ContainerRulesData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@code /chestcat} command tree: everything from the sorting-overhaul spec that doesn't
 * (yet) have a dedicated GUI button lives here rather than being a fake/inert control.
 *
 * <pre>
 * /chestcat rule set <FIELD=value[,FIELD=value...] [any]>   - assign a multi-condition rule
 *                                                              to the chest you're looking at
 * /chestcat rule clear                                       - remove that chest's rule
 * /chestcat rule show                                        - show that chest's rule, if any
 * /chestcat lock                                              - toggle strict category-only
 *                                                              enforcement on that chest
 * /chestcat unlock                                            - same toggle, explicit off
 * /chestcat preview [radius]                                  - dry-run Sort Nearby: reports
 *                                                              what WOULD move, moves nothing
 * /chestcat test [count]                                      - fills your inventory with a
 *                                                              messy, diverse item set (op only)
 * /chestcat testchest [tools|building|food|modded|mixed|stress] [radius]
 *                                                              - same, into a target chest (op only)
 * </pre>
 */
@EventBusSubscriber(modid = "chestcat")
public final class ChestCatCommands {

    private ChestCatCommands() {}

    private static final double REACH = 5.0d;
    private static final int DEFAULT_PREVIEW_RADIUS = 16;

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("chestcat")
                .then(Commands.literal("rule")
                        .then(Commands.literal("set")
                                .then(Commands.argument("conditions", StringArgumentType.greedyString())
                                        .executes(ctx -> ruleSet(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "conditions")))))
                        .then(Commands.literal("clear").executes(ctx -> ruleClear(ctx.getSource())))
                        .then(Commands.literal("show").executes(ctx -> ruleShow(ctx.getSource()))))
                .then(Commands.literal("lock").executes(ctx -> setLocked(ctx.getSource(), true)))
                .then(Commands.literal("unlock").executes(ctx -> setLocked(ctx.getSource(), false)))
                .then(Commands.literal("preview")
                        .executes(ctx -> preview(ctx.getSource(), DEFAULT_PREVIEW_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> preview(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("test")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> test(ctx.getSource(), 200))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 2000))
                                .executes(ctx -> test(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("testchest")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> testChest(ctx.getSource(), "mixed"))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .executes(ctx -> testChest(ctx.getSource(), StringArgumentType.getString(ctx, "mode"))))));
    }

    // --- rule / lock ---

    private static Optional<BlockPos> targetedChest(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        HitResult hit = player.pick(REACH, 1.0f, false);
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        BlockPos pos = bhr.getBlockPos();
        if (level.getBlockEntity(pos) == null) {
            return Optional.empty();
        }
        return Optional.of(ChestUtil.canonicalChestPos(level, pos));
    }

    private static int ruleSet(CommandSourceStack source, String raw) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<BlockPos> pos = targetedChest(player);
        if (pos.isEmpty()) {
            source.sendFailure(Component.literal("Look at a chest, barrel, or supported storage block first."));
            return 0;
        }
        ContainerRule rule;
        try {
            rule = ContainerRule.parseCommand(raw);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        ContainerRulesData.get(player.serverLevel()).setRule(pos.get(), rule);
        source.sendSuccess(() -> Component.literal("Rule set (and chest locked): " + rule.describe())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int ruleClear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<BlockPos> pos = targetedChest(player);
        if (pos.isEmpty()) {
            source.sendFailure(Component.literal("Look at a chest, barrel, or supported storage block first."));
            return 0;
        }
        ContainerRulesData.get(player.serverLevel()).clearRule(pos.get());
        source.sendSuccess(() -> Component.literal("Rule cleared. (Lock state left as-is - use /chestcat unlock if needed.)")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int ruleShow(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<BlockPos> pos = targetedChest(player);
        if (pos.isEmpty()) {
            source.sendFailure(Component.literal("Look at a chest, barrel, or supported storage block first."));
            return 0;
        }
        ContainerRulesData data = ContainerRulesData.get(player.serverLevel());
        Optional<ContainerRule> rule = data.getRule(pos.get());
        boolean locked = data.isLocked(pos.get());
        if (rule.isPresent()) {
            source.sendSuccess(() -> Component.literal("Rule: " + rule.get().describe() + " (locked)")
                    .withStyle(ChatFormatting.AQUA), false);
        } else {
            source.sendSuccess(() -> Component.literal(locked
                    ? "No custom rule. Locked to its assigned category only."
                    : "No custom rule. Not locked - can receive overflow items.").withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }

    private static int setLocked(CommandSourceStack source, boolean lock) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<BlockPos> pos = targetedChest(player);
        if (pos.isEmpty()) {
            source.sendFailure(Component.literal("Look at a chest, barrel, or supported storage block first."));
            return 0;
        }
        ContainerRulesData data = ContainerRulesData.get(player.serverLevel());
        boolean current = data.isLocked(pos.get());
        if (current != lock) {
            data.toggleLocked(pos.get());
        }
        boolean now = data.isLocked(pos.get());
        source.sendSuccess(() -> Component.literal(now
                ? "Chest locked - it will only ever receive items matching its assigned category/rule."
                : "Chest unlocked - it can receive overflow items again.")
                .withStyle(now ? ChatFormatting.YELLOW : ChatFormatting.GREEN), false);
        return 1;
    }

    // --- preview ---

    private static int preview(CommandSourceStack source, int radius) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ChestSorter.Preview p = ChestSorter.previewSortNearby(player, radius, ItemSortMode.SMART);
        if (p.moves().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Nothing would move.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("--- Sort preview (radius " + radius + ", not applied) ---")
                .withStyle(ChatFormatting.GOLD), false);
        int shown = 0;
        for (ChestSorter.PlannedMove move : p.moves()) {
            if (shown >= 25) {
                int remaining = p.moves().size() - shown;
                source.sendSuccess(() -> Component.literal("...and " + remaining + " more move(s).")
                        .withStyle(ChatFormatting.GRAY), false);
                break;
            }
            BlockPos to = move.destination();
            source.sendSuccess(() -> Component.literal(move.count() + "x " + move.item()
                            + " -> chest at " + to.getX() + "," + to.getY() + "," + to.getZ())
                    .withStyle(ChatFormatting.WHITE), false);
            shown++;
        }
        source.sendSuccess(() -> Component.literal(
                "Would move " + p.itemsWouldMove() + " item(s) across " + p.chestsInvolved() + " chest(s)"
                        + (p.itemsWithNoDestination() > 0 ? " (" + p.itemsWithNoDestination() + " would have no destination)." : ".")
        ).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    // --- /test and /testchest ---

    /** A deliberately messy, diverse item pool: tools, weapons, armor, food, ores, wood, stone,
     *  redstone, potions/brewing, dyes, plus partial and full stacks, so a sort actually has
     *  something to prove itself against. Counts are intentionally uneven (partial stacks). */
    private static List<ItemStack> messyPool() {
        List<ItemStack> pool = new ArrayList<>();
        // Tools & weapons, mixed materials/durability.
        pool.add(new ItemStack(Items.DIAMOND_PICKAXE));
        pool.add(new ItemStack(Items.IRON_PICKAXE));
        pool.add(new ItemStack(Items.WOODEN_AXE));
        pool.add(new ItemStack(Items.STONE_AXE));
        pool.add(new ItemStack(Items.GOLDEN_SHOVEL));
        pool.add(new ItemStack(Items.NETHERITE_SWORD));
        pool.add(new ItemStack(Items.IRON_SWORD));
        pool.add(new ItemStack(Items.BOW));
        pool.add(new ItemStack(Items.CROSSBOW));
        pool.add(new ItemStack(Items.SHEARS));
        pool.add(new ItemStack(Items.FISHING_ROD));
        pool.add(new ItemStack(Items.TRIDENT));
        // Armor.
        pool.add(new ItemStack(Items.DIAMOND_HELMET));
        pool.add(new ItemStack(Items.IRON_CHESTPLATE));
        pool.add(new ItemStack(Items.LEATHER_BOOTS));
        pool.add(new ItemStack(Items.GOLDEN_LEGGINGS));
        // Food, partial stacks.
        pool.add(new ItemStack(Items.APPLE, 5));
        pool.add(new ItemStack(Items.BREAD, 12));
        pool.add(new ItemStack(Items.COOKED_BEEF, 3));
        pool.add(new ItemStack(Items.GOLDEN_CARROT, 1));
        pool.add(new ItemStack(Items.CAKE, 1));
        // Ores / ingots / raw materials.
        pool.add(new ItemStack(Items.RAW_IRON, 17));
        pool.add(new ItemStack(Items.IRON_INGOT, 32));
        pool.add(new ItemStack(Items.DIAMOND, 4));
        pool.add(new ItemStack(Items.COPPER_INGOT, 64));
        pool.add(new ItemStack(Items.GOLD_NUGGET, 9));
        pool.add(new ItemStack(Items.COAL, 40));
        // Wood, several types.
        pool.add(new ItemStack(Items.OAK_LOG, 20));
        pool.add(new ItemStack(Items.SPRUCE_PLANKS, 64));
        pool.add(new ItemStack(Items.DARK_OAK_LOG, 6));
        pool.add(new ItemStack(Items.CHERRY_PLANKS, 14));
        // Stone family.
        pool.add(new ItemStack(Items.COBBLESTONE, 64));
        pool.add(new ItemStack(Items.DEEPSLATE, 22));
        pool.add(new ItemStack(Items.GRANITE, 10));
        pool.add(new ItemStack(Items.DIRT, 64));
        // Redstone.
        pool.add(new ItemStack(Items.REDSTONE, 30));
        pool.add(new ItemStack(Items.REPEATER, 4));
        pool.add(new ItemStack(Items.PISTON, 2));
        // Potions / brewing.
        pool.add(new ItemStack(Items.GLASS_BOTTLE, 5));
        pool.add(new ItemStack(Items.NETHER_WART, 6));
        pool.add(new ItemStack(Items.BLAZE_POWDER, 8));
        // Dyes / decoration, several colors.
        pool.add(new ItemStack(Items.RED_DYE, 6));
        pool.add(new ItemStack(Items.BLUE_DYE, 6));
        pool.add(new ItemStack(Items.LIME_DYE, 6));
        pool.add(new ItemStack(Items.BLACK_DYE, 6));
        // Mob drops / misc.
        pool.add(new ItemStack(Items.BONE, 15));
        pool.add(new ItemStack(Items.STRING, 22));
        pool.add(new ItemStack(Items.ENDER_PEARL, 3));
        pool.add(new ItemStack(Items.SLIME_BALL, 7));
        return pool;
    }

    /** Live-scans the item registry for a sample of non-vanilla items, so /test and
     *  /testchest genuinely exercise whatever modpack is actually installed, rather than
     *  a hardcoded guess at what mods might be present. */
    private static List<ItemStack> moddedSample(int max) {
        List<ItemStack> result = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (result.size() >= max) break;
            if (id.getNamespace().equals("minecraft")) continue;
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == null || item == Items.AIR) continue;
            result.add(new ItemStack(item));
        }
        return result;
    }

    private static int test(CommandSourceStack source, int targetCount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<ItemStack> pool = new ArrayList<>(messyPool());
        pool.addAll(moddedSample(15));

        int placed = 0;
        int stacksAdded = 0;
        int idx = 0;
        int guard = 0;
        while (placed < targetCount && guard < 4000) {
            ItemStack template = pool.get(idx % pool.size()).copy();
            idx++;
            guard++;
            int before = player.getInventory().countItem(template.getItem());
            boolean added = player.getInventory().add(template.copy());
            if (!added) break; // inventory full
            placed += template.getCount();
            stacksAdded++;
            if (before == player.getInventory().countItem(template.getItem())) break; // safety: nothing actually landed
        }
        int placedFinal = placed;
        int stacksFinal = stacksAdded;
        source.sendSuccess(() -> Component.literal("/test: added roughly " + placedFinal
                        + " item(s) across " + stacksFinal + " stack(s) - "
                        + "development/stress-testing tool, not meant for survival use.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int testChest(CommandSourceStack source, String mode) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        Optional<BlockPos> targeted = targetedChest(player);
        BlockPos pos = targeted.orElse(null);
        Container container = null;
        if (pos != null) {
            for (ChestUtil.Storage s : ChestUtil.findNearbyStorages(level, pos, 1)) {
                if (s.canonicalPos().equals(pos)) {
                    container = s.container();
                    break;
                }
            }
        }
        if (container == null) {
            // Fall back to the nearest storage within a short radius.
            for (ChestUtil.Storage s : ChestUtil.findNearbyStorages(level, player.blockPosition(), 6)) {
                container = s.container();
                pos = s.canonicalPos();
                break;
            }
        }
        if (container == null) {
            source.sendFailure(Component.literal("No chest, barrel, or supported storage found - look at one or stand within 6 blocks of one."));
            return 0;
        }

        List<ItemStack> pool = switch (mode.toLowerCase(java.util.Locale.ROOT)) {
            case "tools" -> List.of(
                    new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.IRON_AXE),
                    new ItemStack(Items.WOODEN_SHOVEL), new ItemStack(Items.IRON_SWORD),
                    new ItemStack(Items.STONE, 10), new ItemStack(Items.DIRT, 5),
                    new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.APPLE, 3),
                    new ItemStack(Items.OAK_LOG, 4), new ItemStack(Items.IRON_INGOT, 6));
            case "building" -> List.of(
                    new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64),
                    new ItemStack(Items.GLASS, 20), new ItemStack(Items.BRICKS, 30),
                    new ItemStack(Items.OAK_LOG, 15), new ItemStack(Items.SAND, 40),
                    new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.APPLE, 2));
            case "food" -> List.of(
                    new ItemStack(Items.BREAD, 20), new ItemStack(Items.APPLE, 10),
                    new ItemStack(Items.COOKED_BEEF, 8), new ItemStack(Items.CARROT, 12),
                    new ItemStack(Items.GOLDEN_APPLE, 1), new ItemStack(Items.COBBLESTONE, 5));
            case "modded" -> moddedSample(20);
            case "stress" -> {
                List<ItemStack> big = new ArrayList<>(messyPool());
                big.addAll(messyPool());
                big.addAll(moddedSample(25));
                yield big;
            }
            default -> messyPool();
        };

        int placed = 0;
        for (ItemStack stack : pool) {
            ItemStack leftover = ChestUtil.insertStack(container, stack.copy());
            placed += stack.getCount() - leftover.getCount();
        }
        int placedFinal = placed;
        BlockPos posFinal = pos;
        source.sendSuccess(() -> Component.literal("/testchest " + mode + ": placed " + placedFinal
                        + " item(s) into the chest at " + posFinal.getX() + "," + posFinal.getY() + "," + posFinal.getZ()
                        + " - development/stress-testing tool.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
}
