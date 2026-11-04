package com.senne.imttech.client.cuffs;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.client.zone.ZoneBorderRenderer;
import com.senne.imttech.cuffs.Handcuffs;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Het touw van de handboeien (hoofdstuk 48): van de hand van wie iemand vasthoudt naar de geboeide, een beetje
 * doorhangend, zoals een lijn aan een dier. En om de polsen van wie geboeid is een klein donker bandje, zodat je ook
 * zonder touw ziet dat hij geboeid is. Wie geboeid is en door wie, komt mee met het wezen (de attachment CUFFS).
 */
public final class CuffRenderer {
    private CuffRenderer() {}

    /** Touwkleur (beige) en de boeien zelf (donker staal). */
    private static final int ROPE = 0xD2BC8E;
    private static final int CUFF = 0x3A4048;
    /** Hoeveel stukjes het touw heeft. */
    private static final int SEGMENTS = 12;

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean any = false;
        VertexConsumer out = null;
        Matrix4f m = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !Handcuffs.isCuffed(living) || living.distanceToSqr(cam) > 64 * 64) {
                continue;
            }
            if (!any) {
                any = true;
                pose.pushPose();
                pose.translate(-cam.x, -cam.y, -cam.z);
                m = pose.last().pose();
                out = buffers.getBuffer(ZoneBorderRenderer.lines());
            }
            Vec3 at = living.getPosition(partial);
            // Het bandje om zijn polsen: een dun donker balkje op heuphoogte, achter zijn rug.
            double hip = at.y + living.getBbHeight() * 0.42;
            float yaw = living instanceof Player ? living.yBodyRotO + (living.yBodyRot - living.yBodyRotO) * partial : living.getYRot();
            Vec3 back = new Vec3(Mth.sin(yaw * Mth.DEG_TO_RAD), 0, -Mth.cos(yaw * Mth.DEG_TO_RAD)).scale(living.getBbWidth() * 0.35);
            Vec3 side = new Vec3(-back.z, 0, back.x).normalize().scale(0.16);
            Vec3 wrists = new Vec3(at.x, hip, at.z).add(back);
            segment(m, out, wrists.subtract(side), wrists.add(side), 0.035, CUFF, 0.95f);
            UUID holderId = Handcuffs.holderOf(living);
            Player holder = holderId == null ? null : mc.level.getPlayerByUUID(holderId);
            if (holder == null || holder == living) {
                continue;
            }
            // Het touw: van de hand van wie hem vasthoudt naar zijn polsen, met een buik erin.
            Vec3 look = holder.getViewVector(partial);
            Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
            Vec3 hand = holder.getEyePosition(partial).add(look.scale(0.35)).add(right.scale(0.3)).subtract(0, 0.45, 0);
            double distance = hand.distanceTo(wrists);
            double sag = Math.min(0.7, 0.12 * distance);
            Vec3 previous = hand;
            for (int i = 1; i <= SEGMENTS; i++) {
                double t = (double) i / SEGMENTS;
                Vec3 point = hand.lerp(wrists, t).subtract(0, sag * 4 * t * (1 - t), 0);
                segment(m, out, previous, point, 0.02, ROPE, 0.9f);
                previous = point;
            }
        }
        if (any) {
            buffers.endBatch(ZoneBorderRenderer.lines());
            pose.popPose();
        }
    }

    /** Een stukje touw van a naar b: twee kruislings staande vlakjes (van alle kanten te zien), {@code width} dik. */
    private static void segment(Matrix4f m, VertexConsumer out, Vec3 a, Vec3 b, double width, int rgb, float alpha) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0e-8) {
            return;
        }
        Vec3 axis = Math.abs(d.normalize().y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = d.cross(axis).normalize().scale(width);
        Vec3 v = d.cross(u).normalize().scale(width);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int bl = rgb & 0xFF;
        int al = Mth.clamp((int) (alpha * 255), 0, 255);
        quad(m, out, a.add(u), b.add(u), b.subtract(u), a.subtract(u), r, g, bl, al);
        quad(m, out, a.add(v), b.add(v), b.subtract(v), a.subtract(v), r, g, bl, al);
    }

    private static void quad(Matrix4f m, VertexConsumer out, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int r, int g, int b, int a) {
        for (Vec3 p : new Vec3[] {p0, p1, p2, p3}) {
            out.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
        }
    }
}
