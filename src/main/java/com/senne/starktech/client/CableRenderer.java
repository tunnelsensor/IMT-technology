package com.senne.starktech.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.senne.starktech.energy.CableCells;
import com.senne.starktech.energy.EnergySource;
import com.senne.starktech.energy.PowerConnectable;
import com.senne.starktech.energy.StarkCables;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Tekent de getekende Stark-kabels (zie StarkCables): een dun, donker kabeltje dat plat op de vloer
 * ligt, tegen een muur omhoog loopt of onder een plafond hangt, met een knik op de hoeken. Naar een
 * apparaat ernaast loopt een stukje kabel.
 *
 * Zit een kabel IN een blok (je bouwde er een muur overheen), dan zie je hem niet: het blok zit
 * ervoor. Draag je de bril, dan licht hij door de muur heen op als een zachte blauwe lijn (tot 24
 * blokken ver), zodat je weet waar de stroom loopt, en waar je moet knippen. Die "kabel-xray" zet
 * je aan of uit in G > Information (zie EdithClientConfig.CABLE_XRAY).
 *
 * Hoe het kabeltje loopt: elke plek kiest een "ankerkant" (de vloer, een muur of het plafond waar
 * hij tegenaan ligt). Van het midden van die kant loopt hij naar elke buur, eerst langs de
 * ankerkant en dan pas omhoog of opzij; zo liggen de stukken netjes tegen de oppervlakken.
 */
public final class CableRenderer {
    private CableRenderer() {}

    /** Een effen kleur met licht van de wereld (dezelfde tekenstijl als een leiband). */
    private static final RenderType CABLE = RenderType.create("starktech_cable", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LEASH_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false));

    /** Licht dat door alles heen te zien is (voor verborgen kabels met de bril op). */
    private static final RenderType XRAY = RenderType.create("starktech_cable_xray", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    /** Hoeveel chunks rond de camera we tekenen, en hoe ver verborgen kabels te zien zijn. */
    private static final int RANGE_CHUNKS = 4;
    private static final double XRAY_RANGE = 24.0;
    /** Half de dikte van het kabeltje, en hoe ver het van de vloer of muur af ligt. */
    private static final double HALF = 0.045;
    private static final double LIFT = 0.5 - HALF - 0.005;

    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Na de wezens: in deze fase draait de camera al mee in de PoseStack (net als bij de illusies).
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer solid = buffers.getBuffer(CABLE);
        // Verborgen kabels alleen met een werkende bril op, en alleen als de kabel-xray aan staat.
        boolean xray = EdithClientState.operational && EdithClientConfig.cableXray();
        List<BlockPos> hidden = new ArrayList<>();

        ChunkPos centre = new ChunkPos(BlockPos.containing(cam));
        for (int cx = centre.x - RANGE_CHUNKS; cx <= centre.x + RANGE_CHUNKS; cx++) {
            for (int cz = centre.z - RANGE_CHUNKS; cz <= centre.z + RANGE_CHUNKS; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                CableCells cells = StarkCables.in(chunk);
                if (cells.isEmpty()) {
                    continue;
                }
                cells.forEach(cell -> {
                    if (StarkCables.isSolid(level, cell)) {
                        if (xray && cell.distToCenterSqr(cam) < XRAY_RANGE * XRAY_RANGE) {
                            hidden.add(cell);
                        }
                    } else {
                        drawCell(m, solid, level, cell, LevelRenderer.getLightColor(level, cell), false);
                    }
                });
            }
        }
        buffers.endBatch(CABLE);
        if (!hidden.isEmpty()) {
            VertexConsumer glow = buffers.getBuffer(XRAY);
            for (BlockPos cell : hidden) {
                drawCell(m, glow, level, cell, 0, true);
            }
            buffers.endBatch(XRAY);
        }
        pose.popPose();
    }

    // ================================================================ Eén plek

    private static void drawCell(Matrix4f m, VertexConsumer vc, ClientLevel level, BlockPos cell, int light, boolean xray) {
        EnumSet<Direction> links = linksOf(level, cell);
        List<Direction> anchors = xray ? List.of() : anchors(level, cell);
        Vec3 base = Vec3.atLowerCornerOf(cell);
        Vec3 p = base.add(point(anchors));
        if (links.isEmpty()) {
            box(m, vc, p, p, light, xray);
            return;
        }
        for (Direction d : links) {
            Vec3 f = base.add(facePoint(d, anchors));
            path(m, vc, p, f, anchors, light, xray);
            // Ligt de buurkabel tegen een andere kant aan? Dan een bruggetje over het raakvlak.
            BlockPos next = cell.relative(d);
            if (!xray && StarkCables.has(level, next) && !StarkCables.isSolid(level, next)) {
                Vec3 g = Vec3.atLowerCornerOf(next).add(facePoint(d.getOpposite(), anchors(level, next)));
                if (g.distanceToSqr(f) > 1.0E-6) {
                    path(m, vc, f, g, List.of(), light, false);
                }
            }
        }
    }

    /** Naar welke kanten loopt de kabel: naar andere kabels, en naar apparaten met stroom. */
    private static EnumSet<Direction> linksOf(ClientLevel level, BlockPos cell) {
        EnumSet<Direction> links = EnumSet.noneOf(Direction.class);
        for (Direction d : Direction.values()) {
            BlockPos next = cell.relative(d);
            if (StarkCables.has(level, next) || level.getBlockState(next).getBlock() instanceof PowerConnectable
                    || level.getBlockEntity(next) instanceof EnergySource) {
                links.add(d);
            }
        }
        return links;
    }

    private static final Direction[] WALLS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /**
     * Waar het kabeltje tegenaan ligt: hooguit twee kanten. Een vloer (of plafond) en een muur
     * ernaast: dan ligt hij in de hoek ertussen, zodat hij netjes van de vloer de muur op gaat.
     * Raakt deze plek niets, maar zit er schuin onder of boven een blok (de rand bovenop een
     * muurtje), dan ligt hij op die rand. Anders nergens tegenaan (door het midden).
     */
    private static List<Direction> anchors(ClientLevel level, BlockPos cell) {
        Direction vertical = StarkCables.isSolid(level, cell.below()) ? Direction.DOWN
                : StarkCables.isSolid(level, cell.above()) ? Direction.UP : null;
        Direction wall = null;
        for (Direction d : WALLS) {
            if (StarkCables.isSolid(level, cell.relative(d))) {
                wall = d;
                break;
            }
        }
        if (vertical != null && wall != null) {
            return List.of(wall, vertical);
        }
        if (vertical != null) {
            return List.of(vertical);
        }
        if (wall != null) {
            return List.of(wall);
        }
        for (Direction v : new Direction[] {Direction.DOWN, Direction.UP}) {
            for (Direction d : WALLS) {
                if (StarkCables.isSolid(level, cell.relative(d).relative(v))) {
                    return List.of(d, v);
                }
            }
        }
        return List.of();
    }

    /** Het punt waar de kabel in dit blok ligt: tegen zijn ankerkanten aan (plek 0..1 binnen het blok). */
    private static Vec3 point(List<Direction> anchors) {
        Vec3 point = new Vec3(0.5, 0.5, 0.5);
        for (Direction a : anchors) {
            point = point.add(a.getStepX() * LIFT, a.getStepY() * LIFT, a.getStepZ() * LIFT);
        }
        return point;
    }

    /**
     * Waar de kabel het blok uit gaat naar kant {@code d}: op die kant, tegen de ankerkanten aan
     * (behalve een ankerkant in dezelfde richting als {@code d}: daar gaat hij juist doorheen).
     */
    private static Vec3 facePoint(Direction d, List<Direction> anchors) {
        Vec3 face = new Vec3(0.5 + d.getStepX() * 0.5, 0.5 + d.getStepY() * 0.5, 0.5 + d.getStepZ() * 0.5);
        for (Direction a : anchors) {
            if (a.getAxis() != d.getAxis()) {
                face = face.add(a.getStepX() * LIFT, a.getStepY() * LIFT, a.getStepZ() * LIFT);
            }
        }
        return face;
    }

    /**
     * Van {@code from} naar {@code to}, in rechte stukken langs de assen: eerst de assen die langs
     * de ankerkanten lopen, pas dan de assen die ervan af gaan (zo blijft hij tegen vloer en muur).
     */
    private static void path(Matrix4f m, VertexConsumer vc, Vec3 from, Vec3 to, List<Direction> anchors,
                             int light, boolean xray) {
        List<Direction.Axis> order = new ArrayList<>(List.of(Direction.Axis.X, Direction.Axis.Z, Direction.Axis.Y));
        for (Direction a : anchors) {
            order.remove(a.getAxis());
            order.add(a.getAxis());
        }
        Vec3 at = from;
        for (Direction.Axis axis : order) {
            double target = to.get(axis);
            if (Math.abs(at.get(axis) - target) < 1.0E-6) {
                continue;
            }
            Vec3 next = switch (axis) {
                case X -> new Vec3(target, at.y, at.z);
                case Y -> new Vec3(at.x, target, at.z);
                case Z -> new Vec3(at.x, at.y, target);
            };
            box(m, vc, at, next, light, xray);
            at = next;
        }
    }

    /** Een dun blokje van {@code a} naar {@code b} (een recht stuk kabel), met de dikte eromheen. */
    private static void box(Matrix4f m, VertexConsumer vc, Vec3 a, Vec3 b, int light, boolean xray) {
        double h = xray ? HALF * 1.6 : HALF;
        float x0 = (float) (Math.min(a.x, b.x) - h), x1 = (float) (Math.max(a.x, b.x) + h);
        float y0 = (float) (Math.min(a.y, b.y) - h), y1 = (float) (Math.max(a.y, b.y) + h);
        float z0 = (float) (Math.min(a.z, b.z) - h), z1 = (float) (Math.max(a.z, b.z) + h);
        // Donker rubber; de bovenkant iets lichter, de onderkant iets donkerder (schaduw).
        int top = xray ? 0x2A6FA0 : 0x4A505C;
        int side = xray ? 0x2A6FA0 : 0x33373F;
        int bottom = xray ? 0x2A6FA0 : 0x24272D;
        int alpha = xray ? 110 : 255;
        quad(m, vc, light, xray, top, alpha, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(m, vc, light, xray, bottom, alpha, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(m, vc, light, xray, side, alpha, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(m, vc, light, xray, side, alpha, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(m, vc, light, xray, side, alpha, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(m, vc, light, xray, side, alpha, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(Matrix4f m, VertexConsumer vc, int light, boolean xray, int rgb, int alpha,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        if (xray) {
            vc.addVertex(m, ax, ay, az).setColor(r, g, b, alpha);
            vc.addVertex(m, bx, by, bz).setColor(r, g, b, alpha);
            vc.addVertex(m, cx, cy, cz).setColor(r, g, b, alpha);
            vc.addVertex(m, dx, dy, dz).setColor(r, g, b, alpha);
        } else {
            vc.addVertex(m, ax, ay, az).setColor(r, g, b, alpha).setLight(light);
            vc.addVertex(m, bx, by, bz).setColor(r, g, b, alpha).setLight(light);
            vc.addVertex(m, cx, cy, cz).setColor(r, g, b, alpha).setLight(light);
            vc.addVertex(m, dx, dy, dz).setColor(r, g, b, alpha).setLight(light);
        }
    }
}
