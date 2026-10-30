package com.senne.imttech.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.senne.imttech.energy.CableCells;
import com.senne.imttech.energy.CableGeometry;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.energy.EnergySource;
import com.senne.imttech.energy.PowerConnectable;
import com.senne.imttech.energy.PowerCables;
import com.senne.imttech.lan.NetworkConnectable;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

/**
 * Tekent de getekende kabels (zie Cables): een dun kabeltje dat plat op de vloer ligt, tegen een muur omhoog loopt of
 * onder een plafond hangt, met een knik op de hoeken. Naar een apparaat ernaast loopt een stukje kabel. Hoe het kabeltje
 * precies in een blok ligt, staat in CableGeometry.
 *
 * Sinds hoofdstuk 38 zijn er drie soorten, elk met een eigen kleur (jouw tabel van 29 oktober):
 * <ul>
 *   <li><b>stroomkabel</b>: donkergrijs rubber; met de bril door de muur heen grijs;</li>
 *   <li><b>netwerkkabel</b>: wit; met de bril ook wit;</li>
 *   <li><b>PoE-kabel</b>: groen; met de bril gloeiend groen (een felle kern met een zachte gloed eromheen).</li>
 * </ul>
 * Liggen er meer soorten op één plek, dan liggen ze boven elkaar (de "banen", zie CableGeometry).
 *
 * Zit een kabel IN een blok (je bouwde er een muur overheen), dan zie je hem niet: het blok zit ervoor. Draag je de bril,
 * dan licht hij door de muur heen op (tot 24 blokken ver), zodat je weet waar de stroom en het netwerk lopen, en waar je
 * moet knippen. Die "kabel-xray" zet je aan of uit in G > Information (zie ImtClientConfig.CABLE_XRAY).
 */
public final class CableRenderer {
    private CableRenderer() {}

    /** Een effen kleur met licht van de wereld (dezelfde tekenstijl als een leiband). */
    private static final RenderType CABLE = RenderType.create("imttech_cable", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LEASH_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false));

    /**
     * Een half doorzichtige kleur die door alles heen te zien is (verborgen stroom- en netwerkkabels met de bril op). Gewoon
     * doorzichtig, niet oplichtend: zo blijft grijs echt grijs.
     */
    private static final RenderType XRAY = RenderType.create("imttech_cable_xray", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    /** Licht dat door alles heen te zien is en oplicht (verborgen PoE-kabels met de bril op: gloeiend groen). */
    private static final RenderType XRAY_GLOW = RenderType.create("imttech_cable_xray_glow", DefaultVertexFormat.POSITION_COLOR,
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
    private static final double HALF = CableGeometry.HALF;

    /**
     * Hoe een soort kabel eruitziet. Gewoon: de bovenkant iets lichter, de onderkant iets donkerder (schaduw). Met de bril
     * door een muur heen: één kleur, half doorzichtig; {@code glow} = met een zachte gloed eromheen.
     */
    private record Look(int top, int side, int bottom, int xray, int xrayAlpha, boolean glow) {}

    private static final Look POWER_LOOK = new Look(0x4A505C, 0x33373F, 0x24272D, 0x50565F, 200, false);
    private static final Look NETWORK_LOOK = new Look(0xF2F4F7, 0xD5D9DF, 0xB2B8C1, 0xF6F8FC, 170, false);
    private static final Look POE_LOOK = new Look(0x4CC764, 0x35A04C, 0x267A38, 0x3DFF6E, 170, true);

    private static Look look(CableKind kind) {
        return switch (kind) {
            case POWER -> POWER_LOOK;
            case NETWORK -> NETWORK_LOOK;
            case POE -> POE_LOOK;
        };
    }

    /** Een kabel in een muur die we met de bril tekenen: waar, en welke soort. */
    private record Hidden(BlockPos cell, CableKind kind) {}

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
        boolean xray = ImtClientState.operational && ImtClientConfig.cableXray();
        List<Hidden> hidden = new ArrayList<>();

        ChunkPos centre = new ChunkPos(BlockPos.containing(cam));
        for (int cx = centre.x - RANGE_CHUNKS; cx <= centre.x + RANGE_CHUNKS; cx++) {
            for (int cz = centre.z - RANGE_CHUNKS; cz <= centre.z + RANGE_CHUNKS; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                for (CableKind kind : CableKind.values()) {
                    CableCells cells = Cables.in(kind, chunk);
                    if (cells.isEmpty()) {
                        continue;
                    }
                    cells.forEach(cell -> {
                        if (PowerCables.isSolid(level, cell)) {
                            if (xray && cell.distToCenterSqr(cam) < XRAY_RANGE * XRAY_RANGE) {
                                hidden.add(new Hidden(cell, kind));
                            }
                        } else {
                            drawCell(m, solid, level, cell, kind, LevelRenderer.getLightColor(level, cell), false);
                        }
                    });
                }
            }
        }
        buffers.endBatch(CABLE);
        if (!hidden.isEmpty()) {
            // Eerst de gewone (stroom en netwerk), dan wat gloeit (PoE): elk soort licht in zijn eigen keer.
            for (boolean glowing : new boolean[] {false, true}) {
                RenderType type = glowing ? XRAY_GLOW : XRAY;
                VertexConsumer vc = buffers.getBuffer(type);
                for (Hidden cable : hidden) {
                    if (look(cable.kind()).glow() == glowing) {
                        drawCell(m, vc, level, cable.cell(), cable.kind(), 0, true);
                    }
                }
                buffers.endBatch(type);
            }
        }
        // Wat er over de kabels loopt (een signaal, stroom): zie CableTraffic.
        CableTraffic.render(m, buffers, level, cam, xray, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        pose.popPose();
    }

    // ================================================================ Eén plek

    private static void drawCell(Matrix4f m, VertexConsumer vc, ClientLevel level, BlockPos cell, CableKind kind,
                                 int light, boolean xray) {
        CableGeometry.Lay lay = CableGeometry.lay(level, cell, kind, xray);
        Look look = look(kind);
        EnumSet<Direction> links = linksOf(level, cell, kind);
        Vec3 base = Vec3.atLowerCornerOf(cell);
        Vec3 p = base.add(lay.point());
        if (links.isEmpty()) {
            box(m, vc, p, p, light, look, xray);
            return;
        }
        for (Direction d : links) {
            Vec3 f = base.add(lay.facePoint(d));
            path(m, vc, p, f, lay.anchors(), light, look, xray);
            // Ligt de buurkabel anders (tegen een andere kant, of op een andere baan)? Dan een bruggetje over het
            // raakvlak. Eén keer per raakvlak: alleen vanaf de kant met de laagste coördinaat.
            BlockPos next = cell.relative(d);
            CableKind other = neighbourKind(level, next, kind);
            if (other != null && d.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    && PowerCables.isSolid(level, next) == xray) {
                Vec3 g = Vec3.atLowerCornerOf(next).add(CableGeometry.lay(level, next, other, xray).facePoint(d.getOpposite()));
                if (g.distanceToSqr(f) > 1.0E-6) {
                    path(m, vc, f, g, List.of(), light, look, xray);
                }
            }
        }
    }

    /**
     * Naar welke kanten loopt de kabel: naar kabels waar hij aan vastzit (dezelfde soort; een PoE-kabel ook naar een
     * stroom- of netwerkkabel, zie CableKind.joins), en naar apparaten die op deze soort aansluiten.
     */
    private static EnumSet<Direction> linksOf(ClientLevel level, BlockPos cell, CableKind kind) {
        EnumSet<Direction> links = EnumSet.noneOf(Direction.class);
        for (Direction d : Direction.values()) {
            BlockPos next = cell.relative(d);
            if (neighbourKind(level, next, kind) != null || connectsDevice(level, next, kind)) {
                links.add(d);
            }
        }
        return links;
    }

    /** De kabel op {@code next} waar een kabel van soort {@code kind} aan vastzit (liefst dezelfde soort), of null. */
    private static @Nullable CableKind neighbourKind(ClientLevel level, BlockPos next, CableKind kind) {
        if (Cables.has(kind, level, next)) {
            return kind;
        }
        for (CableKind other : CableKind.values()) {
            if (other != kind && kind.joins(other) && Cables.has(other, level, next)) {
                return other;
            }
        }
        return null;
    }

    /** Sluit het blok op {@code next} aan op deze soort kabel? Stroom: apparaten met stroom; netwerk: netwerkapparaten. */
    private static boolean connectsDevice(ClientLevel level, BlockPos next, CableKind kind) {
        Block block = level.getBlockState(next).getBlock();
        if (kind.carriesPower() && (block instanceof PowerConnectable || level.getBlockEntity(next) instanceof EnergySource)) {
            return true;
        }
        return kind.carriesNetwork() && block instanceof NetworkConnectable;
    }

    /** Van {@code from} naar {@code to} in rechte stukken langs de assen (zie CableGeometry.corners). */
    private static void path(Matrix4f m, VertexConsumer vc, Vec3 from, Vec3 to, List<Direction> anchors,
                             int light, Look look, boolean xray) {
        List<Vec3> corners = CableGeometry.corners(from, to, anchors);
        for (int i = 1; i < corners.size(); i++) {
            box(m, vc, corners.get(i - 1), corners.get(i), light, look, xray);
        }
    }

    /**
     * Een dun blokje van {@code a} naar {@code b} (een recht stuk kabel), met de dikte eromheen. Een PoE-kabel door de
     * muur heen krijgt er nog een dikkere, zwakke doos omheen: de gloed.
     */
    private static void box(Matrix4f m, VertexConsumer vc, Vec3 a, Vec3 b, int light, Look look, boolean xray) {
        if (xray) {
            cuboid(m, vc, a, b, HALF * 1.6, light, true, look.xray(), look.xray(), look.xray(), look.xrayAlpha());
            if (look.glow()) {
                cuboid(m, vc, a, b, HALF * 3.4, light, true, look.xray(), look.xray(), look.xray(), look.xrayAlpha() / 4);
            }
        } else {
            cuboid(m, vc, a, b, HALF, light, false, look.top(), look.side(), look.bottom(), 255);
        }
    }

    private static void cuboid(Matrix4f m, VertexConsumer vc, Vec3 a, Vec3 b, double h, int light, boolean xray,
                               int top, int side, int bottom, int alpha) {
        float x0 = (float) (Math.min(a.x, b.x) - h), x1 = (float) (Math.max(a.x, b.x) + h);
        float y0 = (float) (Math.min(a.y, b.y) - h), y1 = (float) (Math.max(a.y, b.y) + h);
        float z0 = (float) (Math.min(a.z, b.z) - h), z1 = (float) (Math.max(a.z, b.z) + h);
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
