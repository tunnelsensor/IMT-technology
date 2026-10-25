package com.senne.imttech.client.weapons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senne.imttech.client.suit.SuitEffects;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Wat je ziet als een EMP-granaat afgaat (hoofdstuk 35): een bol van blauw licht die in een paar ticks tot de rand van
 * de puls groeit en dan wegvaagt, met bliksemflitsen die vanuit het midden naar buiten schieten. Getekend met hetzelfde
 * licht als het laserkanon (SuitEffects.GLOW): het telt op bij wat erachter zit, dus het gloeit ook in het donker.
 */
public final class EmpEffects {
    private EmpEffects() {}

    /** Zo lang (ticks) groeit de bol... */
    private static final float GROW_TICKS = 5;
    /** ...en zo lang is hij in totaal te zien. */
    private static final float LIFE_TICKS = 16;
    /** Zo lang flitsen de bliksems. */
    private static final float ARC_TICKS = 10;

    private record Blast(Vec3 center, float radius, long start, long seed) {}

    private static final List<Blast> BLASTS = new ArrayList<>();

    /** Er ging net een granaat af. */
    public static void blast(Vec3 center, float radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        BLASTS.add(new Blast(center, radius, mc.level.getGameTime(), mc.level.getRandom().nextLong()));
    }

    public static void clear() {
        BLASTS.clear();
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BLASTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BLASTS.clear();
            return;
        }
        float time = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(SuitEffects.GLOW);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        for (Iterator<Blast> it = BLASTS.iterator(); it.hasNext(); ) {
            Blast blast = it.next();
            float age = time - blast.start();
            if (age > LIFE_TICKS) {
                it.remove();
                continue;
            }
            if (age < 0) {
                continue;
            }
            shell(m, out, blast, age);
            if (age < ARC_TICKS) {
                arcs(m, out, blast, age, cam);
            }
        }
        pose.popPose();
        buffers.endBatch(SuitEffects.GLOW);
    }

    /** De bol: groeit snel (en remt af), en vaagt dan weg. Van binnen en van buiten te zien. */
    private static void shell(Matrix4f m, VertexConsumer out, Blast blast, float age) {
        float grow = Mth.clamp(age / GROW_TICKS, 0, 1);
        grow = 1 - (1 - grow) * (1 - grow);
        float radius = 0.3f + (blast.radius() - 0.3f) * grow;
        float fade = 1 - Mth.clamp((age - GROW_TICKS) / (LIFE_TICKS - GROW_TICKS), 0, 1);
        float alpha = 0.22f * fade;
        sphere(m, out, blast.center(), radius, 0x3FA8FF, alpha);
        // Een fellere, dunnere rand vlak achter de voorkant: zo zie je de golf voorbij komen.
        sphere(m, out, blast.center(), radius * 0.97f, 0xBFF4FF, alpha * 0.5f);
        // Een witte flits in het midden, alleen in het begin.
        if (age < 4) {
            SuitEffects.disc(m, out, blast.center(), Minecraft.getInstance().gameRenderer.getMainCamera()
                    .getPosition().subtract(blast.center()).normalize(), 1.2f * (1 - age / 4), 0xF2FCFF, 0.9f);
        }
    }

    /** Een bol van vierhoekjes (lengte- en breedtegraden). */
    private static void sphere(Matrix4f m, VertexConsumer out, Vec3 c, float r, int rgb, float alpha) {
        int rings = 12;
        int segments = 24;
        for (int i = 0; i < rings; i++) {
            double a0 = Math.PI * i / rings - Math.PI / 2;
            double a1 = Math.PI * (i + 1) / rings - Math.PI / 2;
            for (int j = 0; j < segments; j++) {
                double b0 = Math.PI * 2 * j / segments;
                double b1 = Math.PI * 2 * (j + 1) / segments;
                SuitEffects.quad(m, out, point(c, r, a0, b0), point(c, r, a0, b1), point(c, r, a1, b1),
                        point(c, r, a1, b0), rgb, alpha);
            }
        }
    }

    private static Vec3 point(Vec3 c, float r, double lat, double lon) {
        double cos = Math.cos(lat);
        return c.add(Math.cos(lon) * cos * r, Math.sin(lat) * r, Math.sin(lon) * cos * r);
    }

    /**
     * Bliksems: zigzaggende lijnen van het midden naar buiten. Om de twee ticks een nieuwe set (met een vast getal per
     * granaat, zodat ze niet elke frame verspringen).
     */
    private static void arcs(Matrix4f m, VertexConsumer out, Blast blast, float age, Vec3 cam) {
        RandomSource random = RandomSource.create(blast.seed() + (long) (age / 2));
        float fade = 1 - age / ARC_TICKS;
        int count = 7;
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.7, random.nextGaussian()).normalize();
            double length = blast.radius() * (0.5 + random.nextDouble() * 0.5) * Math.min(1, age / GROW_TICKS + 0.3);
            Vec3 from = blast.center();
            int steps = 8;
            for (int s = 1; s <= steps; s++) {
                Vec3 to = blast.center().add(dir.scale(length * s / steps))
                        .add(random.nextGaussian() * 0.25, random.nextGaussian() * 0.25, random.nextGaussian() * 0.25);
                line(m, out, from, to, cam, 0.06f, 0x8FE6FF, 0.8f * fade);
                line(m, out, from, to, cam, 0.02f, 0xF2FCFF, fade);
                from = to;
            }
        }
    }

    /** Een streep licht van {@code a} naar {@code b}, gedraaid naar de camera. */
    static void line(Matrix4f m, VertexConsumer out, Vec3 a, Vec3 b, Vec3 cam, float width, int rgb, float alpha) {
        Vec3 dir = b.subtract(a);
        Vec3 side = dir.cross(a.subtract(cam));
        if (side.lengthSqr() < 1.0E-8) {
            return;
        }
        side = side.normalize().scale(width / 2);
        SuitEffects.quad(m, out, a.add(side), b.add(side), b.subtract(side), a.subtract(side), rgb, alpha);
    }
}
