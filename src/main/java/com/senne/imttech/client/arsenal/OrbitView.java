package com.senne.imttech.client.arsenal;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.senne.imttech.ImtTech;
import com.senne.imttech.arsenal.ArsenalDrone;
import com.senne.imttech.client.drone.DamageLayer;
import com.senne.imttech.client.drone.DroneModel;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.drone.DroneDamage;
import com.senne.imttech.satellite.SatelliteUpgrades;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Het 3D-beeld van je arsenaal: de aarde, de Biebia's Industries-satelliet en je drones in hun baan.
 *
 * Elke drone wordt als echt model getekend, met zijn schade eraan (kromme of ontbrekende
 * rotors, roet, haperende lampjes), precies zoals hij er in de wereld uit zou zien.
 *
 * Hoe je 3D tekent in een scherm: het scherm is plat (x naar rechts, y naar beneden), maar je
 * kunt de tekenstapel (PoseStack) laten draaien en schalen zoals je wilt. We zetten het midden
 * van de ruimte in het midden van het vakje, maken 1 "eenheid" een aantal pixels groot, draaien
 * de camera, en tekenen dan de modellen zoals Minecraft dat met wezens doet.
 *
 * In "eenheden": de aarde heeft straal 1, de drones vliegen op 1,55 tot 1,95, de satelliet op
 * 2,4.
 */
public final class OrbitView {
    private static final ResourceLocation PLANET = texture("gui/arsenal/planet.png");
    private static final ResourceLocation SATELLITE = texture("gui/arsenal/satellite.png");
    private static final ResourceLocation SATELLITE_LIGHTS = texture("gui/arsenal/satellite_lights.png");
    private static final ResourceLocation DRONE = texture("entity/drone/drone.png");
    private static final ResourceLocation DRONE_LIGHTS = texture("entity/drone/drone_lights_guard.png");
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    /** De banen: zo schuin staan ze ten opzichte van de evenaar (graden). */
    private static final float DRONE_TILT = 14;
    private static final float SATELLITE_TILT = -28;
    private static final float SATELLITE_RADIUS = 2.4f;

    private final ModelPart satellite = SatelliteModel.bake();
    private final DroneModel<Entity> drone = new DroneModel<>(DroneModel.createBodyLayer().bakeRoot());

    /** De camera: draaien (yaw), kantelen (pitch) en inzoomen. Het scherm past ze aan. */
    public float yaw = 25;
    public float pitch = 24;
    public float zoom = 1.0f;

    /** Waar elke drone op het scherm stond bij het laatste tekenen, om hem aan te klikken. */
    public record Spot(UUID id, float x, float y, float depth) {}

    private final List<Spot> spots = new ArrayList<>();

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/" + path);
    }

    public List<Spot> spots() {
        return spots;
    }

    /** De drone het dichtst bij (x, y) op het scherm, binnen een paar pixels, of null. */
    public @Nullable UUID pick(double x, double y) {
        Spot best = null;
        double bestDistance = 10 * 10;
        for (Spot spot : spots) {
            double dx = spot.x() - x;
            double dy = spot.y() - y;
            double distance = dx * dx + dy * dy;
            // Bij twee drones op dezelfde plek: de voorste (grootste diepte = dichter bij jou).
            if (distance < bestDistance || (best != null && distance == bestDistance && spot.depth() > best.depth())) {
                best = spot;
                bestDistance = distance;
            }
        }
        return best == null ? null : best.id();
    }

    // ================================================================ Het grote beeld

    /**
     * Tekent de ruimte in het vakje (x0, y0) - (x1, y1).
     *
     * @param orbit    de drones die in hun baan hangen
     * @param selected de gekozen drone (krijgt hoekjes eromheen), of null
     * @param upgrades hoe ver de satelliet is opgewaardeerd (dan zie je zijn onderdelen)
     * @param seconds  de tijd in seconden, voor het draaien van alles
     */
    public void render(GuiGraphics g, int x0, int y0, int x1, int y1, List<ArsenalDrone> orbit,
                       @Nullable UUID selected, SatelliteUpgrades upgrades, float seconds) {
        g.enableScissor(x0, y0, x1, y1);
        g.fill(x0, y0, x1, y1, HudStyle.argb(235, 0x02050C));
        stars(g, x0, y0, x1, y1);

        PoseStack pose = g.pose();
        pose.pushPose();
        float scale = Math.min(x1 - x0, y1 - y0) / 6.0f * zoom;
        pose.translate((x0 + x1) / 2.0f, (y0 + y1) / 2.0f, 400);
        // y omhoog, z het scherm in: dan werkt het als een gewone camera.
        pose.scale(scale, -scale, -scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        // Het beeld draait heel langzaam vanzelf rond; slepen draait er nog bij.
        pose.mulPose(Axis.YP.rotationDegrees(yaw + seconds * 2));
        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buffers = g.bufferSource();

        planet(pose, buffers, seconds);
        rings(pose, buffers);
        satellite(pose, buffers, upgrades, seconds);

        spots.clear();
        int count = Math.min(orbit.size(), 60);
        for (int i = 0; i < count; i++) {
            ArsenalDrone d = orbit.get(i);
            pose.pushPose();
            placeOnOrbit(pose, i, count, seconds);
            Vector3f onScreen = pose.last().pose().transformPosition(new Vector3f());
            spots.add(new Spot(d.id(), onScreen.x, onScreen.y, onScreen.z));
            drawDrone(pose, buffers, d, seconds, 0.32f);
            pose.popPose();
        }
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();

        // Hoekjes om de gekozen drone.
        for (Spot spot : spots) {
            if (spot.id().equals(selected)) {
                int sx = Math.round(spot.x());
                int sy = Math.round(spot.y());
                HudStyle.brackets(g, sx - 8, sy - 7, sx + 8, sy + 7, 4, HudStyle.argb(255, HudStyle.CYAN));
            }
        }
        g.disableScissor();
    }

    /** Waar drone nummer i in zijn baan hangt: drie ringen, elk net anders van hoogte en snelheid. */
    private static void placeOnOrbit(PoseStack pose, int i, int count, float seconds) {
        int ring = i % 3;
        float radius = 1.55f + ring * 0.2f;
        float speed = 0.22f - ring * 0.04f;
        float angle = (float) (i * (Math.PI * 2 / count)) + seconds * speed;
        pose.mulPose(Axis.ZP.rotationDegrees(DRONE_TILT));
        pose.translate(Mth.cos(angle) * radius, Mth.sin(angle * 3 + i) * 0.05f, Mth.sin(angle) * radius);
        // Met de neus in de vliegrichting.
        pose.mulPose(Axis.YP.rotation(-angle));
    }

    /** De aarde: een bol met de wereldkaart erop, die langzaam draait, met een blauwe gloed. */
    private static void planet(PoseStack pose, MultiBufferSource buffers, float seconds) {
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(-12)); // de aardas staat schuin
        pose.mulPose(Axis.YP.rotationDegrees(seconds * 6));
        sphere(pose.last(), buffers.getBuffer(RenderType.entityCutoutNoCull(PLANET)), 1.0f, 16, 32, 0xFFFFFFFF, false);
        pose.popPose();
        // De dampkring: een net iets grotere, doorzichtige blauwe bol.
        sphere(pose.last(), buffers.getBuffer(RenderType.entityTranslucent(PLANET)), 1.06f, 12, 24, 0x3066C8FF, true);
    }

    /**
     * Een bol van vierkantjes (breedte- en lengtegraden), met de kaart erom gevouwen.
     * Bij {@code plain} gebruiken we overal hetzelfde stukje van de kaart (het ijs, bijna wit),
     * zodat de kleur alleen van {@code argb} komt.
     */
    private static void sphere(PoseStack.Pose pose, VertexConsumer out, float radius, int rows, int columns,
                               int argb, boolean plain) {
        for (int i = 0; i < rows; i++) {
            float v0 = (float) i / rows;
            float v1 = (float) (i + 1) / rows;
            for (int j = 0; j < columns; j++) {
                float u0 = (float) j / columns;
                float u1 = (float) (j + 1) / columns;
                corner(pose, out, radius, u0, v0, argb, plain);
                corner(pose, out, radius, u0, v1, argb, plain);
                corner(pose, out, radius, u1, v1, argb, plain);
                corner(pose, out, radius, u1, v0, argb, plain);
            }
        }
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer out, float radius, float u, float v,
                               int argb, boolean plain) {
        double theta = Math.PI * v;          // 0 = noordpool, pi = zuidpool
        double phi = Math.PI * 2 * u;
        float x = (float) (Math.sin(theta) * Math.cos(phi));
        float y = (float) Math.cos(theta);
        float z = (float) (Math.sin(theta) * Math.sin(phi));
        out.addVertex(pose, x * radius, y * radius, z * radius)
                .setColor(argb)
                .setUv(plain ? 0.5f : u, plain ? 0.02f : v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LIGHT)
                .setNormal(pose, x, y, z);
    }

    /** Dunne ringen waar de drones en de satelliet vliegen. */
    private static void rings(PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(DRONE_TILT));
        ring(pose.last(), lines, 1.75f, HudStyle.CYAN, 70);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(SATELLITE_TILT));
        ring(pose.last(), lines, SATELLITE_RADIUS, HudStyle.ORANGE, 60);
        pose.popPose();
    }

    private static void ring(PoseStack.Pose pose, VertexConsumer lines, float radius, int rgb, int alpha) {
        int steps = 72;
        for (int i = 0; i < steps; i++) {
            double a0 = i * (Math.PI * 2 / steps);
            double a1 = (i + 1) * (Math.PI * 2 / steps);
            float x0 = (float) Math.cos(a0) * radius;
            float z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;
            float nx = x1 - x0;
            float nz = z1 - z0;
            float length = Mth.sqrt(nx * nx + nz * nz);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            lines.addVertex(pose, x0, 0, z0).setColor(r, g, b, alpha).setNormal(pose, nx / length, 0, nz / length);
            lines.addVertex(pose, x1, 0, z1).setColor(r, g, b, alpha).setNormal(pose, nx / length, 0, nz / length);
        }
    }

    /** De satelliet, in zijn eigen baan, met zijn zonnepanelen naar buiten. */
    private void satellite(PoseStack pose, MultiBufferSource buffers, SatelliteUpgrades upgrades, float seconds) {
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(SATELLITE_TILT));
        float angle = seconds * 0.12f + 1.0f;
        pose.translate(Mth.cos(angle) * SATELLITE_RADIUS, 0, Mth.sin(angle) * SATELLITE_RADIUS);
        pose.mulPose(Axis.YP.rotation(-angle));
        drawSatellite(pose, buffers, upgrades, 1.0f);
        pose.popPose();
    }

    /**
     * De satelliet zelf, met de onderdelen van zijn upgrades, en zijn midden op de oorsprong.
     * {@code size} is hoe breed hij is zonder verlengde zonnepanelen (in eenheden); met langere
     * panelen wordt hij breder, zodat je ziet dat hij gegroeid is.
     */
    public void drawSatellite(PoseStack pose, MultiBufferSource buffers, SatelliteUpgrades upgrades, float size) {
        drawSatellite(pose, buffers, upgrades, size, 0);
    }

    /**
     * De satelliet als HOLOGRAM (voor de holotafel): zijn eigen plaatje, maar doorschijnend,
     * lichtgevend en gekleurd met {@code argb} (0xAARRGGBB). Met {@code argb} 0 gewoon echt.
     */
    public void drawSatellite(PoseStack pose, MultiBufferSource buffers, SatelliteUpgrades upgrades, float size,
                              int argb) {
        SatelliteModel.showUpgrades(satellite, upgrades);
        pose.pushPose();
        // Modellen staan "op hun kop" (y naar beneden): omdraaien. Minecraft rekent de pixels van
        // een model zelf al om naar blokken (16 pixels = 1 blok): BASE_WIDTH pixels breed wordt
        // dus BASE_WIDTH / 16 blok, en die breedte maken we {@code size}.
        float k = size / (SatelliteModel.BASE_WIDTH / 16.0f);
        pose.scale(-k, -k, k);
        if (argb != 0) {
            satellite.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(SATELLITE)), LIGHT,
                    OverlayTexture.NO_OVERLAY, argb);
            pose.popPose();
            return;
        }
        satellite.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(SATELLITE)), LIGHT, OverlayTexture.NO_OVERLAY);
        satellite.render(pose, buffers.getBuffer(RenderType.eyes(SATELLITE_LIGHTS)), LIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /**
     * Eén drone met zijn schade: {@code size} eenheden groot, met zijn midden op de oorsprong.
     * Ook gebruikt voor de grote drone in het inspectievakje.
     */
    public void drawDrone(PoseStack pose, MultiBufferSource buffers, ArsenalDrone d, float seconds, float size) {
        drawDrone(pose, buffers, d, seconds, size, 0);
    }

    /**
     * Een drone als HOLOGRAM (voor de holotafel): doorschijnend en gekleurd met {@code argb}
     * (0xAARRGGBB), zonder roet en lampjes. Met {@code argb} 0 gewoon echt.
     */
    public void drawDrone(PoseStack pose, MultiBufferSource buffers, ArsenalDrone d, float seconds, float size,
                          int argb) {
        float damage = 1.0f - d.healthFraction();
        int seed = d.id().hashCode();
        float age = seconds * 20 + Math.floorMod(seed, 100);
        drone.setDamage(damage, seed);
        drone.pose(age);
        pose.pushPose();
        // Het model is 12 pixels = 0,75 blok breed (Minecraft rekent pixels zelf om naar blokken).
        float k = size / 0.75f;
        pose.scale(-k, -k, k);
        pose.translate(0, -20 / 16.0f, 0); // de romp zit 20 pixels hoog in het model
        if (argb != 0) {
            drone.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(DRONE)), LIGHT,
                    OverlayTexture.NO_OVERLAY, argb);
            pose.popPose();
            return;
        }
        drone.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(DRONE)), LIGHT,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        ResourceLocation soot = DamageLayer.overlay(damage);
        if (soot != null) {
            drone.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(soot)), LIGHT,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
        if (!DroneDamage.lightsFlickerOff(damage, age, seed)) {
            drone.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(DRONE_LIGHTS)), LIGHT,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
        pose.popPose();
    }

    // ================================================================ Inspectievakje

    /** Eén drone groot in beeld, langzaam draaiend: hier zie je de schade van dichtbij. */
    public void renderCloseUp(GuiGraphics g, int x0, int y0, int x1, int y1, ArsenalDrone d, float seconds) {
        g.enableScissor(x0, y0, x1, y1);
        PoseStack pose = g.pose();
        pose.pushPose();
        float scale = Math.min(x1 - x0, y1 - y0) * 0.55f;
        pose.translate((x0 + x1) / 2.0f, (y0 + y1) / 2.0f, 400);
        pose.scale(scale, -scale, -scale);
        pose.mulPose(Axis.XP.rotationDegrees(25));
        pose.mulPose(Axis.YP.rotationDegrees(seconds * 30));
        Lighting.setupForEntityInInventory();
        drawDrone(pose, g.bufferSource(), d, seconds, 1.0f);
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
        g.disableScissor();
    }

    /**
     * De satelliet groot in beeld (voor het satellietscherm). Zo groot dat hij met de langste
     * zonnepanelen nog net in het vakje past; met korte panelen blijft er dus ruimte over.
     */
    public void renderSatelliteCloseUp(GuiGraphics g, int x0, int y0, int x1, int y1,
                                       SatelliteUpgrades upgrades, float seconds) {
        g.enableScissor(x0, y0, x1, y1);
        PoseStack pose = g.pose();
        pose.pushPose();
        // Het vakje is meestal breder dan hoog; de satelliet ook. Met de langste panelen vult hij
        // de hele breedte, maar hij wordt nooit hoger dan het vakje.
        // Slepen en scrollen (yaw, pitch, zoom) werken hier net als in het grote beeld.
        float scale = Math.min((x1 - x0) * 1.0f, (y1 - y0) * 2.6f) * zoom;
        pose.translate((x0 + x1) / 2.0f, (y0 + y1) / 2.0f, 400);
        pose.scale(scale, -scale, -scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw + seconds * 20));
        Lighting.setupForEntityInInventory();
        drawSatellite(pose, g.bufferSource(), upgrades, SatelliteModel.BASE_WIDTH / SatelliteModel.MAX_WIDTH);
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
        g.disableScissor();
    }

    /** Sterretjes op vaste plekken, zodat ze niet flikkeren. */
    private static void stars(GuiGraphics g, int x0, int y0, int x1, int y1) {
        int w = x1 - x0;
        int h = y1 - y0;
        for (int i = 0; i < 90; i++) {
            int hash = i * 73856093 ^ 19349663;
            int x = x0 + Math.floorMod(hash, Math.max(1, w));
            int y = y0 + Math.floorMod(hash / 7 + i * 131, Math.max(1, h));
            int brightness = 90 + Math.floorMod(hash >> 3, 140);
            g.fill(x, y, x + 1, y + 1, HudStyle.argb(brightness, 0xDDEEFF));
        }
    }
}
