package com.senne.starktech.client.zone;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.senne.starktech.StarkTech;
import com.senne.starktech.client.EdithClientConfig;
import com.senne.starktech.network.ZoneSyncPayload;
import com.senne.starktech.security.zone.ZoneShape;
import com.senne.starktech.security.zone.ZoneShape.Rect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Tekent de rand van je zones (hoofdstuk 10): doorzichtige blauwe wanden met een fijn raster aan de
 * buitenkant van de zone, met een zachte glans die er langzaam overheen schuift, zoals de rand van de
 * wereld in Minecraft. Van binnen en van buiten te zien, tot ongeveer {@link #RANGE} blokken ver:
 * dichtbij is hij het felst. Heeft de zone geen stroom (hij beschermt dan niet), dan is hij rood en
 * klopt hij langzaam.
 *
 * Je ziet alleen zones waar je zelf mag bouwen (de server stuurt geen andere, zie ZoneSync), en alleen
 * als "Zone border" aan staat (G > Information, of een eigen toets).
 *
 * Een zone bestaat uit kubussen die tegen of over elkaar mogen liggen. Waar twee kubussen elkaar
 * raken, hoort geen wand: we tekenen alleen de echte buitenkant (zie ZoneShape.boundary).
 */
public final class ZoneBorderRenderer {
    private ZoneBorderRenderer() {}

    private static final ResourceLocation GRID = ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/misc/zone_grid.png");
    private static final ResourceLocation SHEEN = ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "textures/misc/zone_sheen.png");

    /** Zo ver (blokken) zie je de rand; daarbuiten tekenen we niets. */
    private static final double RANGE = 48.0;
    /** De wand bestaat uit tegels van zoveel blokken (elk hoekpunt krijgt zijn eigen helderheid). */
    private static final int TILE = 4;
    /** Zo ver ligt de wand buiten de blokken van de zone (anders flikkert hij met de buitenmuur). */
    private static final double LIFT = 0.012;
    /** Om de zoveel blokken herhaalt de glans. */
    private static final double SHEEN_SIZE = 8.0;

    /** Blauw: de zone beschermt. Rood: geen stroom, iedereen kan er nu bouwen. */
    private static final int ACTIVE_RGB = 0x3FD8FF;
    private static final int OFFLINE_RGB = 0xFF5A3C;

    private static final RenderType GRID_TYPE = field("zone_grid", GRID);
    private static final RenderType SHEEN_TYPE = field("zone_sheen", SHEEN);

    /**
     * Licht dat opgeteld wordt bij wat erachter zit (zoals de schildkoepel): zwart is onzichtbaar,
     * wit is fel. Hij schrijft niet in de diepte, zodat water en deeltjes erachter gewoon te zien
     * blijven, en is van twee kanten te zien.
     */
    private static RenderType field(String name, ResourceLocation texture) {
        return RenderType.create("starktech_" + name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536,
                false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENERGY_SWIRL_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .createCompositeState(false));
    }

    /** De buitenkant van één zone (zie ZoneShape), en of hij beschermt. */
    private record Walls(boolean active, List<Rect> rects) {}

    /** Wat we de laatste keer uitrekenden (opnieuw als de server nieuwe zones stuurt). */
    private static int cachedVersion = -1;
    private static List<Walls> cached = List.of();

    // ================================================================ Tekenen

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !EdithClientConfig.zoneBorder() || ZoneClientState.zones().isEmpty()) {
            return;
        }
        if (cachedVersion != ZoneClientState.version()) {
            List<Walls> walls = new ArrayList<>();
            for (ZoneSyncPayload.View zone : ZoneClientState.zones()) {
                walls.add(new Walls(zone.active(), ZoneShape.boundary(zone.boxes())));
            }
            cached = walls;
            cachedVersion = ZoneClientState.version();
        }
        float time = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();

        // Eerst alle tegels in de buurt verzamelen, dan twee keer tekenen: het raster en de glans.
        List<Tile> tiles = new ArrayList<>();
        for (Walls walls : cached) {
            float strength = walls.active() ? 1.0f : 0.55f + 0.25f * Mth.sin(time * 0.12f);
            int rgb = walls.active() ? ACTIVE_RGB : OFFLINE_RGB;
            for (Rect rect : walls.rects()) {
                collectTiles(rect, cam, rgb, strength, tiles);
            }
        }
        if (tiles.isEmpty()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer grid = buffers.getBuffer(GRID_TYPE);
        for (Tile tile : tiles) {
            tile.draw(m, grid, 0.55f, false, 0);
        }
        buffers.endBatch(GRID_TYPE);
        VertexConsumer sheen = buffers.getBuffer(SHEEN_TYPE);
        // De glans schuift langzaam (een keer rond in ongeveer 20 seconden).
        float slide = (time * 0.0025f) % 1.0f;
        for (Tile tile : tiles) {
            tile.draw(m, sheen, 0.35f, true, slide);
        }
        buffers.endBatch(SHEEN_TYPE);
        pose.popPose();
    }

    /**
     * Eén tegel van een wand: vier hoeken (in de wereld), hoe fel elke hoek is (dichtbij fel, ver weg
     * zwak), en de kleur.
     */
    private record Tile(Direction face, double n, double a0, double a1, double b0, double b1,
                        float k00, float k10, float k11, float k01, int rgb) {
        void draw(Matrix4f m, VertexConsumer out, float brightness, boolean sheen, float slide) {
            // Het raster: één blok = het hele plaatje, met de lijnen op de blokranden (a en b zijn
            // hele getallen, dus 0 tot de breedte van de tegel). De glans: een plaatje per SHEEN_SIZE
            // blokken, dat langzaam opschuift.
            double baseA = sheen ? Math.floor(a0 / SHEEN_SIZE) * SHEEN_SIZE : a0;
            double baseB = sheen ? Math.floor(b0 / SHEEN_SIZE) * SHEEN_SIZE : b0;
            double scale = sheen ? 1.0 / SHEEN_SIZE : 1.0;
            float u0 = (float) ((a0 - baseA) * scale) + slide;
            float u1 = (float) ((a1 - baseA) * scale) + slide;
            float v0 = (float) ((b0 - baseB) * scale);
            float v1 = (float) ((b1 - baseB) * scale);
            vertex(m, out, a0, b0, u0, v0, k00 * brightness);
            vertex(m, out, a1, b0, u1, v0, k10 * brightness);
            vertex(m, out, a1, b1, u1, v1, k11 * brightness);
            vertex(m, out, a0, b1, u0, v1, k01 * brightness);
        }

        private void vertex(Matrix4f m, VertexConsumer out, double a, double b, float u, float v, float k) {
            Vec3 p = point(face, n, a, b);
            // Het licht wordt opgeteld: hoe fel hij is zit in de kleur (donkerder = zwakker).
            int r = (int) (((rgb >> 16) & 0xFF) * k);
            int g = (int) (((rgb >> 8) & 0xFF) * k);
            int bl = (int) ((rgb & 0xFF) * k);
            out.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, bl, 255).setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(face.getStepX(), face.getStepY(), face.getStepZ());
        }
    }

    /** Knipt een stuk wand in tegels, alleen het deel binnen RANGE van de camera. */
    private static void collectTiles(Rect rect, Vec3 cam, int rgb, float strength, List<Tile> out) {
        Direction.Axis normal = rect.face().getAxis();
        Direction.Axis axisA = ZoneShape.axisA(normal);
        Direction.Axis axisB = ZoneShape.axisB(normal);
        double camN = cam.get(normal);
        double camA = cam.get(axisA);
        double camB = cam.get(axisB);
        // Het dichtstbijzijnde punt van de rechthoek: te ver weg? Dan de hele rechthoek overslaan.
        double nearA = Mth.clamp(camA, rect.a0(), rect.a1());
        double nearB = Mth.clamp(camB, rect.b0(), rect.b1());
        double dn = camN - rect.plane();
        if (dn * dn + (nearA - camA) * (nearA - camA) + (nearB - camB) * (nearB - camB) > RANGE * RANGE) {
            return;
        }
        double n = rect.plane() + (rect.face().getAxisDirection() == Direction.AxisDirection.POSITIVE ? LIFT : -LIFT);
        int fromA = Math.max(rect.a0(), (int) Math.floor(camA - RANGE));
        int toA = Math.min(rect.a1(), (int) Math.ceil(camA + RANGE));
        int fromB = Math.max(rect.b0(), (int) Math.floor(camB - RANGE));
        int toB = Math.min(rect.b1(), (int) Math.ceil(camB + RANGE));
        for (int a = fromA; a < toA; a = nextTile(a, toA)) {
            int a2 = nextTile(a, toA);
            for (int b = fromB; b < toB; b = nextTile(b, toB)) {
                int b2 = nextTile(b, toB);
                float k00 = fade(cam, rect.face(), n, a, b) * strength;
                float k10 = fade(cam, rect.face(), n, a2, b) * strength;
                float k11 = fade(cam, rect.face(), n, a2, b2) * strength;
                float k01 = fade(cam, rect.face(), n, a, b2) * strength;
                if (k00 + k10 + k11 + k01 <= 0.001f) {
                    continue;
                }
                out.add(new Tile(rect.face(), n, a, a2, b, b2, k00, k10, k11, k01, rgb));
            }
        }
    }

    /** De volgende tegelrand: het volgende veelvoud van TILE (zo liggen de tegels altijd op dezelfde plek). */
    private static int nextTile(int from, int limit) {
        return Math.min(limit, Math.floorDiv(from, TILE) * TILE + TILE);
    }

    /** Hoe fel de wand op dit punt is: 1 vlakbij, 0 op RANGE blokken (zacht aflopend). */
    private static float fade(Vec3 cam, Direction face, double n, double a, double b) {
        double distance = point(face, n, a, b).distanceTo(cam);
        float t = (float) Mth.clamp(1.0 - distance / RANGE, 0.0, 1.0);
        return t * t;
    }

    // ================================================================ Rekenen

    /** Een punt in de wereld uit de plek op de normaal en de twee assen in het vlak (zie ZoneShape). */
    private static Vec3 point(Direction face, double n, double a, double b) {
        return switch (face.getAxis()) {
            case X -> new Vec3(n, a, b);
            case Y -> new Vec3(a, n, b);
            case Z -> new Vec3(a, b, n);
        };
    }
}
