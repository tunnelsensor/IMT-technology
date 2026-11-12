package com.senne.imttech.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.senne.imttech.energy.CableGeometry;
import com.senne.imttech.client.zone.ZoneClientState;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.energy.CableSight;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.energy.PowerCables;
import com.senne.imttech.network.CableTrafficPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

/**
 * Wat er over de kabels loopt, getekend (hoofdstuk 38, jouw tabel van 29 oktober). De server stuurt de weg (zie
 * lan/CableTraffic); dit spel tekent hem over de kabels zoals ze liggen (CableGeometry):
 * <ul>
 *   <li><b>een signaal</b>: een helder vakje dat de kabel volgt tot aan het eind, wit over een netwerkkabel en groen over
 *       een PoE-kabel;</li>
 *   <li><b>stroom</b>: gele streepjes die van de leverancier naar het apparaat lopen, over stroom- en PoE-kabels.</li>
 * </ul>
 * Over een kabel in een muur zie je het alleen met de kabel-xray van de bril aan (net als de kabel zelf), en dan alleen
 * over je eigen kabels en die in een zone waar je mag bouwen (hoofdstuk 92, zie CableSight). In een blok
 * (een switch, de kern) zie je het vakje niet: dat zit dan binnenin. Aan of uit in G > Information (Cable signals,
 * Power flow).
 */
public final class CableTraffic {
    private CableTraffic() {}

    /** Hoe snel een signaal over de kabel gaat (blokken per tick): 10 blokken per seconde. */
    private static final double SIGNAL_SPEED = 0.5;
    /** Zo kort duurt een signaal minstens (ticks), ook over een heel kort stukje kabel. */
    private static final double SIGNAL_MIN_TICKS = 8;
    /** Hoe snel de gele streepjes lopen (blokken per tick), hoe ver ze uit elkaar liggen en hoe lang ze zijn (blokken). */
    private static final double FLOW_SPEED = 0.08;
    private static final double DASH_PERIOD = 0.5;
    private static final double DASH_LENGTH = 0.2;
    /** Verder weg dan dit (blokken) tekenen we niets. */
    private static final double RANGE = 64;

    /** De kleuren: wit (netwerk), groen (PoE) en geel (stroom). */
    private static final int WHITE = 0xFFFFFF;
    private static final int GREEN = 0x66FF88;
    private static final int YELLOW = 0xFFD23C;

    /** Licht dat oplicht en achter blokken verdwijnt (een signaal of stroom over een kabel die je ziet). */
    private static final RenderType GLOW = RenderType.create("imttech_cable_traffic", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** Hetzelfde, maar door alles heen (over een kabel in een muur, met de kabel-xray aan). */
    private static final RenderType GLOW_XRAY = RenderType.create("imttech_cable_traffic_xray", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    /** Een recht stukje van de weg: van a naar b, in welke plek van de weg ({@code cell}), vanaf hoe ver van het begin. */
    private record Segment(Vec3 a, Vec3 b, int cell, double from, double length) {
        Vec3 at(double distance) {
            return length <= 0 ? a : a.lerp(b, Math.max(0, Math.min(1, (distance - from) / length)));
        }
    }

    /**
     * Een plek van de weg: ligt er een kabel om op te tekenen, zit die in een muur, in welke kleur, en mag jij hem door een
     * muur heen zien (je eigen kabel, of een in je zone: hoofdstuk 92).
     */
    private record Cell(boolean cable, boolean hidden, int colour, boolean yours) {}

    /** Een hele weg: de stukjes, de plekken, hoe lang hij is, en een punt om te kijken of hij dichtbij is. */
    private record Route(List<Segment> segments, List<Cell> cells, double length, Vec3 centre) {}

    private record Signal(Route route, double start, double duration) {}

    private record Flow(Route route, double until) {}

    private static final List<Signal> SIGNALS = new ArrayList<>();
    /** De stroom per apparaat (het eind van de weg): een nieuw pakketje vervangt het oude. */
    private static final Map<BlockPos, Flow> FLOWS = new HashMap<>();
    /** Alleen voor de screenshots: de tijd stilgezet (speltijd), of null. */
    private static @Nullable Double frozen;

    // ================================================================ Binnenkomen

    /** Een weg van de server: een signaal begint nu, of de stroom naar een apparaat loopt (weer) even. */
    public static void receive(CableTrafficPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || payload.path().size() < 2) {
            return;
        }
        Route route = route(level, payload.path(), payload.power());
        if (route.length() <= 0) {
            return;
        }
        double now = level.getGameTime();
        if (payload.power()) {
            FLOWS.put(payload.path().getLast(), new Flow(route, now + payload.ticks()));
        } else {
            SIGNALS.add(new Signal(route, now, Math.max(SIGNAL_MIN_TICKS, route.length() / SIGNAL_SPEED)));
            if (SIGNALS.size() > 64) {
                SIGNALS.removeFirst();
            }
        }
    }

    /**
     * De weg in stukjes, zoals de kabels liggen: in elke plek met een kabel van de kant waar hij binnenkomt naar het midden
     * en naar de kant waar hij verder gaat (met de knikken langs vloer en muur, zie CableGeometry); door een blok zonder
     * kabel (een switch, de kern, het apparaat zelf) door het midden.
     */
    private static Route route(ClientLevel level, List<BlockPos> path, boolean power) {
        List<Segment> segments = new ArrayList<>();
        List<Cell> cells = new ArrayList<>();
        double length = 0;
        Vec3 last = null;
        var player = Minecraft.getInstance().player;
        for (int i = 0; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            CableKind kind = kindAt(level, pos, power);
            boolean hidden = kind != null && PowerCables.isSolid(level, pos);
            boolean yours = kind != null && player != null
                    && CableSight.mayXray(Cables.owner(kind, level, pos), player.getUUID(), ZoneClientState.zones(), pos);
            cells.add(new Cell(kind != null, hidden, power ? YELLOW : kind == CableKind.POE ? GREEN : WHITE, yours));
            for (Vec3 point : points(level, path, i, kind, hidden)) {
                if (last != null && point.distanceToSqr(last) > 1.0E-8) {
                    double piece = last.distanceTo(point);
                    segments.add(new Segment(last, point, i, length, piece));
                    length += piece;
                }
                last = point;
            }
        }
        Vec3 centre = Vec3.atCenterOf(path.get(path.size() / 2));
        return new Route(segments, cells, length, centre);
    }

    /** Over welke kabel op deze plek het loopt: stroom over stroom of PoE, een signaal over netwerk of PoE (of geen). */
    private static @Nullable CableKind kindAt(ClientLevel level, BlockPos pos, boolean power) {
        CableKind own = power ? CableKind.POWER : CableKind.NETWORK;
        if (Cables.has(own, level, pos)) {
            return own;
        }
        return Cables.has(CableKind.POE, level, pos) ? CableKind.POE : null;
    }

    /** De punten van de weg in plek {@code i}: van de kant waar hij binnenkomt, via het midden, naar de kant waar hij verder gaat. */
    private static List<Vec3> points(ClientLevel level, List<BlockPos> path, int i, @Nullable CableKind kind, boolean hidden) {
        BlockPos pos = path.get(i);
        if (kind == null) {
            return List.of(Vec3.atCenterOf(pos));
        }
        Vec3 base = Vec3.atLowerCornerOf(pos);
        CableGeometry.Lay lay = CableGeometry.lay(level, pos, kind, hidden);
        Vec3 centre = base.add(lay.point());
        List<Vec3> points = new ArrayList<>();
        Direction in = i > 0 ? towards(pos, path.get(i - 1)) : null;
        Direction out = i < path.size() - 1 ? towards(pos, path.get(i + 1)) : null;
        if (in != null) {
            List<Vec3> corners = CableGeometry.corners(centre, base.add(lay.facePoint(in)), lay.anchors());
            for (int k = corners.size() - 1; k >= 0; k--) {
                points.add(corners.get(k));
            }
        } else {
            points.add(centre);
        }
        if (out != null) {
            List<Vec3> corners = CableGeometry.corners(centre, base.add(lay.facePoint(out)), lay.anchors());
            points.addAll(corners.subList(1, corners.size()));
        }
        return points;
    }

    /** De kant van {@code from} waar {@code to} naast ligt, of null als ze niet naast elkaar liggen. */
    private static @Nullable Direction towards(BlockPos from, BlockPos to) {
        return Direction.fromDelta(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    // ================================================================ Tekenen

    /** Tekent wat er nu loopt (vanuit CableRenderer, met de camera al in {@code m}). */
    static void render(Matrix4f m, MultiBufferSource.BufferSource buffers, ClientLevel level, Vec3 cam, boolean xray,
                       float partialTick) {
        double now = frozen != null ? frozen : level.getGameTime() + partialTick;
        prune(now);
        boolean signals = ImtClientConfig.cableSignals() && !SIGNALS.isEmpty();
        boolean flows = ImtClientConfig.powerFlow() && !FLOWS.isEmpty();
        if (!signals && !flows) {
            return;
        }
        // Eerst gewoon (alleen over open kabels, achter een muur onzichtbaar); met de kabel-xray aan daarna nog eens door
        // alles heen, over ALLE kabels (ook open kabels in een andere kamer, hoofdstuk 54) die van jou zijn of in je zone
        // liggen (hoofdstuk 92).
        for (boolean through : new boolean[] {false, true}) {
            if (through && !xray) {
                continue;
            }
            RenderType type = through ? GLOW_XRAY : GLOW;
            VertexConsumer vc = buffers.getBuffer(type);
            if (flows) {
                for (Flow flow : FLOWS.values()) {
                    if (flow.route().centre().distanceToSqr(cam) < RANGE * RANGE) {
                        drawFlow(m, vc, flow, now, through);
                    }
                }
            }
            if (signals) {
                for (Signal signal : SIGNALS) {
                    if (signal.route().centre().distanceToSqr(cam) < RANGE * RANGE) {
                        drawSignal(m, vc, signal, now, through);
                    }
                }
            }
            buffers.endBatch(type);
        }
    }

    /** Weg wat voorbij is (of van een andere wereld: daar loopt de speltijd anders). */
    private static void prune(double now) {
        SIGNALS.removeIf(signal -> now > signal.start() + signal.duration() || now < signal.start() - 100);
        Iterator<Flow> flows = FLOWS.values().iterator();
        while (flows.hasNext()) {
            Flow flow = flows.next();
            if (now > flow.until() || flow.until() - now > 2000) {
                flows.remove();
            }
        }
    }

    /** Het vakje van een signaal, op de plek waar het nu is (met een zachte gloed eromheen). */
    private static void drawSignal(Matrix4f m, VertexConsumer vc, Signal signal, double now, boolean through) {
        Route route = signal.route();
        double distance = (now - signal.start()) / signal.duration() * route.length();
        for (Segment segment : route.segments()) {
            if (distance < segment.from() || distance > segment.from() + segment.length()) {
                continue;
            }
            Cell cell = route.cells().get(segment.cell());
            if (cell.cable() && (through ? cell.yours() : !cell.hidden())) {
                Vec3 p = segment.at(distance);
                cuboid(m, vc, p, p, 0.07, cell.colour(), 230);
                cuboid(m, vc, p, p, 0.14, cell.colour(), 70);
            }
            return;
        }
    }

    /** De gele streepjes van de stroom over elk stukje kabel van de weg, lopend van de leverancier naar het apparaat. */
    private static void drawFlow(Matrix4f m, VertexConsumer vc, Flow flow, double now, boolean through) {
        Route route = flow.route();
        // Het laatste halve seconde vervaagt hij (als er geen nieuw pakketje kwam: het apparaat haalt geen stroom meer).
        int alpha = (int) (210 * Math.max(0, Math.min(1, (flow.until() - now) / 10.0)));
        if (alpha <= 0) {
            return;
        }
        double offset = (now * FLOW_SPEED) % DASH_PERIOD;
        for (Segment segment : route.segments()) {
            Cell cell = route.cells().get(segment.cell());
            if (!cell.cable() || (through ? !cell.yours() : cell.hidden()) || segment.length() <= 0) {
                continue;
            }
            double end = segment.from() + segment.length();
            for (double start = offset + Math.floor((segment.from() - offset) / DASH_PERIOD) * DASH_PERIOD - DASH_PERIOD;
                 start < end; start += DASH_PERIOD) {
                double s0 = Math.max(start, segment.from());
                double s1 = Math.min(start + DASH_LENGTH, end);
                if (s1 > s0) {
                    cuboid(m, vc, segment.at(s0), segment.at(s1), CableGeometry.HALF * 1.35, cell.colour(), alpha);
                }
            }
        }
    }

    /** Een blokje van {@code a} naar {@code b} met {@code h} dikte eromheen, in één kleur. */
    private static void cuboid(Matrix4f m, VertexConsumer vc, Vec3 a, Vec3 b, double h, int rgb, int alpha) {
        float x0 = (float) (Math.min(a.x, b.x) - h), x1 = (float) (Math.max(a.x, b.x) + h);
        float y0 = (float) (Math.min(a.y, b.y) - h), y1 = (float) (Math.max(a.y, b.y) + h);
        float z0 = (float) (Math.min(a.z, b.z) - h), z1 = (float) (Math.max(a.z, b.z) + h);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int bl = rgb & 0xFF;
        float[][] faces = {
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0},
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        for (float[] f : faces) {
            for (int k = 0; k < 12; k += 3) {
                vc.addVertex(m, f[k], f[k + 1], f[k + 2]).setColor(r, g, bl, alpha);
            }
        }
    }

    // ================================================================ Voor de screenshots

    /** Zet de tijd van de signalen en de stroom stil (of met false weer verder), zodat een screenshot ze vangt. */
    public static void freeze(boolean on) {
        ClientLevel level = Minecraft.getInstance().level;
        frozen = on && level != null ? (double) level.getGameTime() : null;
    }
}
