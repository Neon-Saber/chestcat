package net.chestcat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the Locate Mode indicator two independent ways at once:
 *  - a 3D line box around the target (nice when no shader pack is active)
 *  - a 2D on-screen compass marker computed purely from player yaw/pitch/position
 *
 * Shader packs (Iris/OptiFine) frequently drop or reshade RenderType.lines() during
 * the world render pass, which made the 3D box invisible with shaders on. The 2D
 * marker below never touches the 3D render pipeline at all - it's plain trigonometry
 * drawn straight onto the HUD - so it keeps working identically with or without any
 * shader pack.
 */
@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class LocateModeRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!ChestCatClient.locateActive || ChestCatClient.locateTarget == null) return;

        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        AABB box = new AABB(ChestCatClient.locateTarget).inflate(0.03);

        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0));
        float alpha = 0.55f + 0.45f * pulse;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, consumer, box, 0.35f, 0.85f, 1.0f, alpha);
        bufferSource.endBatch(RenderType.lines());

        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ChestCatClient.locateActive || ChestCatClient.locateTarget == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int centerX = screenW / 2;
        int centerY = screenH / 2;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        Vec3 targetCenter = Vec3.atCenterOf(ChestCatClient.locateTarget);

        double dx = targetCenter.x - camPos.x;
        double dy = targetCenter.y - camPos.y;
        double dz = targetCenter.z - camPos.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        double dist3d = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double yawToTarget = Math.toDegrees(Mth.atan2(dz, dx)) - 90.0;
        double relYaw = Mth.wrapDegrees(yawToTarget - camera.getYRot());
        double pitchToTarget = -Math.toDegrees(Mth.atan2(dy, horizDist));
        double relPitch = Mth.wrapDegrees(pitchToTarget - camera.getXRot());

        double halfFovDeg = Math.max(15, mc.options.fov().get() / 2.0);
        double halfFovVertDeg = halfFovDeg * screenH / (double) screenW;

        int margin = 16;
        boolean inFront = Math.abs(relYaw) < 90.0;

        double rawX = centerX + (relYaw / halfFovDeg) * (screenW / 2.0);
        double rawY = centerY + (relPitch / halfFovVertDeg) * (screenH / 2.0);

        double markerX, markerY;
        if (inFront && rawX >= margin && rawX <= screenW - margin && rawY >= margin && rawY <= screenH - margin) {
            markerX = rawX;
            markerY = rawY;
        } else {
            // Off-screen (or behind): clamp a marker to the border, sliding around
            // the edge to keep pointing toward the target, like a waypoint arrow.
            double dirX = inFront ? (rawX - centerX) : (relYaw >= 0 ? 1 : -1);
            double dirY = inFront ? (rawY - centerY) : (relPitch >= 0 ? 1 : -1);
            double len = Math.max(1e-6, Math.sqrt(dirX * dirX + dirY * dirY));
            dirX /= len;
            dirY /= len;
            double halfW = centerX - margin, halfH = centerY - margin;
            double scale = Math.min(
                    dirX == 0 ? Double.MAX_VALUE : halfW / Math.abs(dirX),
                    dirY == 0 ? Double.MAX_VALUE : halfH / Math.abs(dirY));
            markerX = centerX + dirX * scale;
            markerY = centerY + dirY * scale;
        }

        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0));
        int alpha = (int) (140 + 100 * pulse);
        int color = (alpha << 24) | 0x59D9FF;

        int mx = (int) markerX, my = (int) markerY;
        drawDiamond(graphics, mx, my, 5, color);

        String label = Math.round(dist3d) + "m";
        int labelW = mc.font.width(label);
        graphics.drawString(mc.font, label, mx - labelW / 2, my + 8, 0xFFD9A441, true);

        Component keyName = ChestCatClient.OPEN_MENU_KEY.getTranslatedKeyMessage();
        Component text = Component.literal("Press ")
                .append(keyName)
                .append(Component.literal(" to exit Locate Mode"));
        graphics.drawCenteredString(mc.font, text, centerX, screenH - 68, 0xFFD9A441);
    }

    private static void drawDiamond(GuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int half = r - Math.abs(i);
            graphics.fill(cx - half, cy + i, cx + half + 1, cy + i + 1, color);
        }
    }
}
