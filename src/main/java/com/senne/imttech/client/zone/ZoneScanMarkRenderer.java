package com.senne.imttech.client.zone;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.senne.imttech.network.ZoneScanMarksPayload;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.zone.ZoneScan;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * De pings van de Autoscan in de wereld (hoofdstuk 116, jouw wens: "ik wil dat je een ping krijgt van waar die autoscan
 * dat ziet"). Boven het blok waar je kamers openliggen staat een tekstje ("Opening: cave · 12 m") dat je door muren heen
 * ziet, en dichtbij (tot 24 blokken) een dun kader om het blok dat zacht klopt. Een stippellijn laat de weg zien die de
 * scan nam: ook als de scan de opening niet precies vond, zie je zo waar het lek naartoe gaat. Je ziet het ook zonder
 * I.M.T-bril (net als de oranje doos van de Zone Marker), een paar minuten lang of tot je opnieuw scant. Houd je de
 * Zone Marker vast en mag de grot erbij, dan zegt het tekstje hoe.
 * <p>
 * De kleuren botsen niet met de oranje doos van de marker of het gele stipje op de kaart: roze = een opening naar een
 * grot, rood = een opening naar buiten, paars = waar een grot zelf de open lucht raakt.
 */
public final class ZoneScanMarkRenderer {
    private ZoneScanMarkRenderer() {}

    /** Verder dan dit (blokken) tekenen we de ping niet (zo hangt hij niet aan de horizon als je via SSH werkt). */
    private static final double RANGE = 160.0;
    /** Tot zo ver (blokken) staat het kader om het blok; verder weg alleen het tekstje. */
    private static final double OUTLINE_RANGE = 24.0;
    /** Tot zo ver (blokken) zie je de stippellijn. */
    private static final double PATH_RANGE = 64.0;
    /**
     * Zo dik (blokken per blok afstand) maken we een lijn aan elke kant van de rand: samen ongeveer twee pixels op een
     * scherm van 1080 hoog met FOV 110 (één blok op d blokken afstand is daar zo'n 378 / d pixels).
     */
    private static final double LINE_PER_BLOCK = 0.0026;

    /** Lijnen zonder diepte: altijd te zien, ook achter een muur. */
    private static final RenderType THROUGH = RenderType.create("imttech_zone_scan_marks", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** De kleur van een ping (ook op de kaart van de app Zone). */
    public static int colour(ZoneScan.Kind kind) {
        return switch (kind) {
            case CAVE -> 0xFF5ACD;
            case OUTSIDE -> 0xFF4A3C;
            case CAVE_EXIT -> 0xB57BFF;
        };
    }

    /** Tekent de pings (roept ZoneBorderRenderer aan, in dezelfde fase van het tekenen). */
    static void render(RenderLevelStageEvent event, Minecraft mc) {
        ZoneScanMarksPayload scan = ZoneClientState.scan();
        if (scan == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        long now = Util.getMillis();
        long age = now - ZoneClientState.scanSince();
        long left = ZoneClientState.scanUntil() - now;
        // Zacht kloppen, de laatste twee seconden uitfaden.
        float pulse = 0.65f + 0.35f * Mth.sin(now / 160.0f);
        float fade = left < 2000 ? Math.max(0, left / 2000.0f) : 1.0f;
        int pathRgb = scan.marks().isEmpty() ? 0xC8D2D7 : colour(scan.marks().getFirst().kindOf());
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        VertexConsumer lines = buffers.getBuffer(THROUGH);
        // De weg die de scan nam: kleine, zwakke stipjes (één klein blokje elk) in het midden van elk blok.
        for (BlockPos point : scan.path()) {
            Vec3 centre = Vec3.atCenterOf(point);
            double distance = centre.distanceTo(cam);
            if (distance > PATH_RANGE) {
                continue;
            }
            double dot = Mth.clamp(distance * LINE_PER_BLOCK * 1.5, 0.02, 0.12);
            cube(m, lines, centre, dot, pathRgb, 0.35f * fade);
        }
        for (ZoneScanMarksPayload.Entry mark : scan.marks()) {
            BlockPos pos = mark.pos();
            double distance = Vec3.atCenterOf(pos).distanceTo(cam);
            if (distance > OUTLINE_RANGE) {
                continue;
            }
            // Bij het begin dijt het kader even uit (zoals een ping), daarna staat het om het blok.
            double grow = age < 600 ? 0.6 * (1.0 - age / 600.0) : 0.0;
            double inset = -0.02 - grow;
            double line = Mth.clamp(distance * LINE_PER_BLOCK, 0.008, 0.2);
            ZoneBorderRenderer.outline(m, lines, pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                    pos.getX() + 1 - inset, pos.getY() + 1 - inset, pos.getZ() + 1 - inset, colour(mark.kindOf()),
                    pulse * fade, line);
        }
        buffers.endBatch(THROUGH);
        boolean marker = mc.player != null && (mc.player.getMainHandItem().is(ModItems.ZONE_MARKER.get())
                || mc.player.getOffhandItem().is(ModItems.ZONE_MARKER.get()));
        for (ZoneScanMarksPayload.Entry mark : scan.marks()) {
            Vec3 above = Vec3.atBottomCenterOf(mark.pos()).add(0, 1.45, 0);
            double distance = above.distanceTo(cam);
            if (distance > RANGE) {
                continue;
            }
            String text = mark.kindOf().label() + " · " + Math.round(distance) + " m";
            label(pose, buffers, camera, mc, above, text, colour(mark.kindOf()), fade);
            // Alleen zo dichtbij als de server een blik op de ping laat tellen (verder weg zou de klik een nieuwe scan zijn).
            if (marker && scan.offer() && mark.kindOf() != ZoneScan.Kind.CAVE_EXIT
                    && distance <= com.senne.imttech.security.zone.ZoneMarkerItem.LOOK_RANGE) {
                // Met de Zone Marker in je hand: hoe je de grot erbij neemt (jouw wens: "bevestigen dat cave er bij moet").
                label(pose, buffers, camera, mc, above.add(0, -0.32 * Math.max(1.0, distance / 8.0), 0),
                        "Look here + sneak + right-click: include", 0xE6E6E6, fade);
            }
        }
        buffers.endBatch();
        pose.popPose();
    }

    /** Een klein blokje (zes vlakjes) rond {@code centre}, {@code half} blokken naar elke kant. */
    private static void cube(Matrix4f m, VertexConsumer out, Vec3 centre, double half, int rgb, float alpha) {
        float ax = (float) (centre.x - half);
        float ay = (float) (centre.y - half);
        float az = (float) (centre.z - half);
        float bx = (float) (centre.x + half);
        float by = (float) (centre.y + half);
        float bz = (float) (centre.z + half);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        float[][] faces = {
                {ax, ay, az, bx, ay, az, bx, by, az, ax, by, az},
                {ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz},
                {ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az},
                {bx, ay, az, bx, ay, bz, bx, by, bz, bx, by, az},
                {ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz},
                {ax, by, az, bx, by, az, bx, by, bz, ax, by, bz}};
        for (float[] f : faces) {
            for (int i = 0; i < 12; i += 3) {
                out.addVertex(m, f[i], f[i + 1], f[i + 2]).setColor(r, g, b, a);
            }
        }
    }

    /** Een tekstje dat altijd naar je toe kijkt, door muren heen (zwakker) en ervoor fel; verder weg wat groter. */
    private static void label(PoseStack pose, MultiBufferSource buffers, Camera camera, Minecraft mc, Vec3 at, String text,
                              int rgb, float fade) {
        float size = 0.025f * (float) Math.max(1.0, at.distanceTo(camera.getPosition()) / 8.0);
        int alpha = Mth.clamp((int) (255 * fade), 4, 255);
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(camera.rotation());
        pose.scale(size, -size, size);
        Matrix4f m = pose.last().pose();
        float x = -mc.font.width(text) / 2.0f;
        mc.font.drawInBatch(text, x, 0, (Math.max(4, alpha * 3 / 5) << 24) | rgb, false, m, buffers, Font.DisplayMode.SEE_THROUGH,
                (alpha / 3) << 24, LightTexture.FULL_BRIGHT);
        mc.font.drawInBatch(text, x, 0, (alpha << 24) | rgb, false, m, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
