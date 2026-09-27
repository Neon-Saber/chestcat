package net.chestcat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.chestcat.CustomGroups;
import net.chestcat.ItemGrouping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a small spinning icon of each chest's category (or sub-type) floating just above it.
 * Uses vanilla items as icons, so no textures are needed. Toggle from Sort Options.
 */
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class ChestCatIndicatorRenderer {

    /** Toggled from the Sort Options screen. */
    public static boolean enabled = true;

    private static final double MAX_DISTANCE = 24.0;
    private static final double HEIGHT_ABOVE_BLOCK = 1.35;
    private static final float ICON_SCALE = 0.9f;

    private static final Map<ItemGrouping.Key, ItemStack> ICONS = new HashMap<>();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (!enabled) return;

        Map<BlockPos, ClientChestCategoryCache.Entry> cache = ClientChestCategoryCache.snapshot();
        if (cache.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        long now = System.currentTimeMillis();
        float spin = (now % 4000L) / 4000f * 360f;
        double maxDistSq = MAX_DISTANCE * MAX_DISTANCE;
        boolean drewAny = false;

        for (Map.Entry<BlockPos, ClientChestCategoryCache.Entry> entry : cache.entrySet()) {
            BlockPos pos = entry.getKey();
            ClientChestCategoryCache.Entry data = entry.getValue();
            if (now - data.lastSeenMs() > ClientChestCategoryCache.EXPIRE_MS) continue;

            if (level.getBlockEntity(pos) == null) continue; // broken, or not loaded on the client

            // Double chests: split between both halves instead of sitting off to one side.
            Vec3 anchor = chestCenter(level, pos);
            double dx = anchor.x - cam.x;
            double dy = anchor.y - cam.y;
            double dz = anchor.z - cam.z;
            if (dx * dx + dy * dy + dz * dz > maxDistSq) continue;

            double bob = Math.sin(now / 500.0 + pos.getX() * 0.7 + pos.getZ() * 1.3) * 0.04;

            pose.pushPose();
            pose.translate(dx, dy + HEIGHT_ABOVE_BLOCK + bob, dz);
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            pose.scale(ICON_SCALE, ICON_SCALE, ICON_SCALE);
            // Real block/sky light at the icon, not a hardcoded full-bright override - shader packs
            // (Iris/Oculus) recolor items using the lightmap they're given, and a synthetic
            // always-max value renders as invisible/black under several of them.
            int packedLight = LevelRenderer.getLightColor(level, pos.above());
            mc.getItemRenderer().renderStatic(iconFor(data.key()), ItemDisplayContext.FIXED,
                    packedLight, OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
            pose.popPose();
            drewAny = true;
        }

        if (drewAny) buffers.endBatch();
    }

    /** Center point between both halves of a double chest, or the single chest's own center.
     *  Uses the chest's actual TYPE/FACING blockstate to find its one true paired half, instead
     *  of grabbing the first same-block neighbor in any direction - otherwise two unrelated
     *  chests (e.g. the last chest of one double-chest unit sitting next to the first chest of
     *  the next unit) get mistaken for a pair and their icons get pulled toward each other. */
    private static Vec3 chestCenter(ClientLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity)) {
            return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE && state.hasProperty(ChestBlock.FACING)) {
                Direction facing = state.getValue(ChestBlock.FACING);
                Direction connected = type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
                BlockPos neighbor = pos.relative(connected);
                BlockState neighborState = level.getBlockState(neighbor);
                if (level.getBlockEntity(neighbor) instanceof ChestBlockEntity
                        && neighborState.getBlock() == state.getBlock()) {
                    return new Vec3((pos.getX() + neighbor.getX()) / 2.0 + 0.5, pos.getY(),
                            (pos.getZ() + neighbor.getZ()) / 2.0 + 0.5);
                }
            }
        }
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private static ItemStack iconFor(ItemGrouping.Key key) {
        return ICONS.computeIfAbsent(key, k -> new ItemStack(pickItem(k)));
    }

    private static Item pickItem(ItemGrouping.Key key) {
        String sub = key.subKey();
        return switch (key.category()) {
            case TOOLS -> Items.IRON_PICKAXE;
            case WEAPONS -> Items.IRON_SWORD;
            case ARMOR -> Items.IRON_CHESTPLATE;
            case FOOD -> Items.COOKED_BEEF;
            case ORES_AND_INGOTS -> "raw".equals(sub) ? Items.RAW_IRON : Items.IRON_INGOT;
            case REDSTONE -> Items.REDSTONE;
            case POTIONS_AND_BREWING -> Items.BREWING_STAND;
            case DYES_AND_DECORATION -> Items.RED_DYE;
            case WOOD -> woodIcon(sub);
            case STONE_AND_DEEPSLATE -> stoneIcon(sub);
            case BLOCKS -> Items.BRICKS;
            case MISC -> Items.ENDER_PEARL;
            case CUSTOM -> CustomGroups.iconItem(sub);
        };
    }

    private static Item woodIcon(String sub) {
        if (sub == null) return Items.OAK_LOG;
        return switch (sub) {
            case "spruce" -> Items.SPRUCE_LOG;
            case "birch" -> Items.BIRCH_LOG;
            case "jungle" -> Items.JUNGLE_LOG;
            case "acacia" -> Items.ACACIA_LOG;
            case "dark_oak" -> Items.DARK_OAK_LOG;
            case "mangrove" -> Items.MANGROVE_LOG;
            case "cherry" -> Items.CHERRY_LOG;
            case "bamboo" -> Items.BAMBOO_BLOCK;
            case "crimson" -> Items.CRIMSON_STEM;
            case "warped" -> Items.WARPED_STEM;
            default -> Items.OAK_LOG;
        };
    }

    private static Item stoneIcon(String sub) {
        if (sub == null) return Items.STONE;
        return switch (sub) {
            case "deepslate" -> Items.DEEPSLATE;
            case "cobblestone" -> Items.COBBLESTONE;
            case "blackstone" -> Items.BLACKSTONE;
            case "andesite" -> Items.ANDESITE;
            case "diorite" -> Items.DIORITE;
            case "granite" -> Items.GRANITE;
            case "tuff" -> Items.TUFF;
            default -> Items.STONE;
        };
    }
}
