package com.senne.starktech.client.c4;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.starktech.c4.DetonatorData;
import com.senne.starktech.c4.DetonatorItem;
import com.senne.starktech.config.StarkConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Met de detonator in je hand zie je waar je ladingen zitten (hoofdstuk 10): boven elke remote C4 die
 * bij deze detonator hoort een rood bordje "C4  12 m", ook door muren heen. Te ver weg voor het
 * signaal? Dan is het bordje grijs ("out of range").
 *
 * Waar de ladingen zitten, staat op de detonator zelf (zie DetonatorData); de server houdt dat bij.
 */
public final class DetonatorMarkers {
    private DetonatorMarkers() {}

    /** Verder weg dan dit (blokken) tekenen we geen bordje. */
    private static final double SHOW_RANGE = 256.0;

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        DetonatorData data = null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = mc.player.getItemInHand(hand);
            if (stack.getItem() instanceof DetonatorItem) {
                data = DetonatorItem.data(stack);
                break;
            }
        }
        if (data == null || data.charges().isEmpty()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (GlobalPos charge : data.charges()) {
            if (!charge.dimension().equals(mc.level.dimension())) {
                continue;
            }
            Vec3 at = charge.pos().getCenter().add(0, 0.6, 0);
            double distance = at.distanceTo(mc.player.position());
            if (distance > SHOW_RANGE) {
                continue;
            }
            boolean inRange = distance <= Math.round(StarkConfig.C4.DETONATOR_RANGE);
            String text = "C4  " + Math.round(distance) + " m" + (inRange ? "" : "  (out of range)");
            label(pose, buffers, camera, mc, at, text, inRange ? 0xFF4A3A : 0x9A9A9A);
        }
        buffers.endBatch();
        pose.popPose();
    }

    /** Een bordje dat naar je toe kijkt; verder weg groter, zodat je het kunt lezen. */
    private static void label(PoseStack pose, MultiBufferSource buffers, Camera camera, Minecraft mc, Vec3 at, String text, int rgb) {
        float size = 0.025f * (float) Math.max(1.0, at.distanceTo(camera.getPosition()) / 8.0);
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(camera.rotation());
        pose.scale(size, -size, size);
        Matrix4f m = pose.last().pose();
        float x = -mc.font.width(text) / 2.0f;
        mc.font.drawInBatch(text, x, 0, 0x80000000 | rgb, false, m, buffers, Font.DisplayMode.SEE_THROUGH,
                0x60000000, LightTexture.FULL_BRIGHT);
        mc.font.drawInBatch(text, x, 0, 0xFF000000 | rgb, false, m, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
