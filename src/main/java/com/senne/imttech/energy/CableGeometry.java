package com.senne.imttech.energy;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Hoe een getekende kabel in een blokplek ligt: tegen welke kanten hij aan ligt, en waar hij het blok uit gaat. Dit stond
 * vroeger in CableRenderer; sinds hoofdstuk 38 staat het hier, omdat ook de server het nodig heeft (een schaar knipt de
 * kabel waar je naar kijkt, ook als er twee soorten op één plek liggen) en straks de bewegende vakjes op de kabels.
 *
 * Hoe het kabeltje loopt: elke plek kiest een "ankerkant" (de vloer, een muur of het plafond waar hij tegenaan ligt). Van
 * het midden van die kant loopt hij naar elke buur, eerst langs de ankerkant en dan pas omhoog of opzij; zo liggen de
 * stukken netjes tegen de oppervlakken.
 *
 * <b>Banen</b> (hoofdstuk 38): liggen er twee of drie soorten op dezelfde plek, dan ligt de eerste (stroom, dan netwerk,
 * dan PoE) tegen het oppervlak, de volgende er een kabeldikte boven, en zo verder. Hangt een plek nergens tegenaan (of zit
 * hij in een muur en zie je hem met de bril), dan schuift elke soort een vast stukje schuin opzij.
 */
public final class CableGeometry {
    private CableGeometry() {}

    /** Half de dikte van het kabeltje. */
    public static final double HALF = 0.045;
    /** Hoe ver het midden van een kabel van het midden van het blok af ligt als hij tegen een oppervlak ligt. */
    public static final double LIFT = 0.5 - HALF - 0.005;
    /** Hoe ver twee kabels op dezelfde plek van elkaar af liggen (midden tot midden): een kabeldikte en een spleetje. */
    public static final double LANE_STEP = 2 * HALF + 0.02;
    /** Zo ver schuift een kabel per soort schuin opzij als hij nergens tegenaan ligt. */
    public static final double FLOAT_STEP = 0.12;

    private static final Direction[] WALLS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /**
     * Hoe één soort kabel in één plek ligt.
     *
     * @param anchors de kanten waar hij tegenaan ligt (hooguit twee; leeg = nergens)
     * @param lift    hoe ver van het midden naar die kanten toe (minder als hij op een hogere baan ligt)
     * @param offset  de vaste schuine verschuiving als hij nergens tegenaan ligt (anders nul)
     */
    public record Lay(List<Direction> anchors, double lift, Vec3 offset) {
        /** Het punt waar de kabel in dit blok ligt (plek 0..1 binnen het blok). */
        public Vec3 point() {
            Vec3 point = new Vec3(0.5, 0.5, 0.5).add(offset);
            for (Direction a : anchors) {
                point = point.add(a.getStepX() * lift, a.getStepY() * lift, a.getStepZ() * lift);
            }
            return point;
        }

        /**
         * Waar de kabel het blok uit gaat naar kant {@code d}: op die kant, tegen de ankerkanten aan (behalve een ankerkant
         * in dezelfde richting als {@code d}: daar gaat hij juist doorheen).
         */
        public Vec3 facePoint(Direction d) {
            Vec3 face = new Vec3(0.5 + d.getStepX() * 0.5, 0.5 + d.getStepY() * 0.5, 0.5 + d.getStepZ() * 0.5).add(offset);
            for (Direction a : anchors) {
                if (a.getAxis() != d.getAxis()) {
                    face = face.add(a.getStepX() * lift, a.getStepY() * lift, a.getStepZ() * lift);
                }
            }
            return face;
        }
    }

    /**
     * Hoe deze soort kabel in deze plek ligt. {@code hidden}: de plek zit in een muur en je ziet hem door de bril (dan
     * hangt hij nergens tegenaan, maar ligt hij door het midden).
     */
    public static Lay lay(Level level, BlockPos cell, CableKind kind, boolean hidden) {
        List<Direction> anchors = hidden ? List.of() : anchors(level, cell);
        if (anchors.isEmpty()) {
            return new Lay(List.of(), LIFT, floatOffset(kind));
        }
        return new Lay(anchors, LIFT - lane(level, cell, kind) * LANE_STEP, Vec3.ZERO);
    }

    /**
     * Op welke baan ligt deze soort in deze plek? 0 = tegen het oppervlak, 1 = daarboven, 2 = daar weer boven. Alleen de
     * soorten die er echt liggen tellen mee, in de vaste volgorde stroom, netwerk, PoE.
     */
    public static int lane(Level level, BlockPos cell, CableKind kind) {
        int lane = 0;
        for (CableKind other : CableKind.values()) {
            if (other == kind) {
                break;
            }
            if (Cables.has(other, level, cell)) {
                lane++;
            }
        }
        return lane;
    }

    /** De vaste schuine verschuiving per soort (voor kabels die nergens tegenaan liggen): stroom in het midden. */
    public static Vec3 floatOffset(CableKind kind) {
        double s = switch (kind) {
            case POWER -> 0.0;
            case NETWORK -> FLOAT_STEP;
            case POE -> -FLOAT_STEP;
        };
        return new Vec3(s, s, s);
    }

    /**
     * Waar het kabeltje tegenaan ligt: hooguit twee kanten. Een vloer (of plafond) en een muur ernaast: dan ligt hij in de
     * hoek ertussen, zodat hij netjes van de vloer de muur op gaat. Raakt deze plek niets, maar zit er schuin onder of
     * boven een blok (de rand bovenop een muurtje), dan ligt hij op die rand. Anders nergens tegenaan (door het midden).
     */
    public static List<Direction> anchors(Level level, BlockPos cell) {
        Direction vertical = PowerCables.isSolid(level, cell.below()) ? Direction.DOWN
                : PowerCables.isSolid(level, cell.above()) ? Direction.UP : null;
        Direction wall = null;
        for (Direction d : WALLS) {
            if (PowerCables.isSolid(level, cell.relative(d))) {
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
                if (PowerCables.isSolid(level, cell.relative(d).relative(v))) {
                    return List.of(d, v);
                }
            }
        }
        return List.of();
    }

    /**
     * Van {@code from} naar {@code to}, in rechte stukken langs de assen: eerst de assen die langs de ankerkanten lopen,
     * pas dan de assen die ervan af gaan (zo blijft hij tegen vloer en muur). Geeft de hoekpunten, met {@code from} als
     * eerste en {@code to} als laatste.
     */
    public static List<Vec3> corners(Vec3 from, Vec3 to, List<Direction> anchors) {
        List<Direction.Axis> order = new ArrayList<>(List.of(Direction.Axis.X, Direction.Axis.Z, Direction.Axis.Y));
        for (Direction a : anchors) {
            order.remove(a.getAxis());
            order.add(a.getAxis());
        }
        List<Vec3> points = new ArrayList<>(4);
        points.add(from);
        Vec3 at = from;
        for (Direction.Axis axis : order) {
            double target = to.get(axis);
            if (Math.abs(at.get(axis) - target) < 1.0E-6) {
                continue;
            }
            at = switch (axis) {
                case X -> new Vec3(target, at.y, at.z);
                case Y -> new Vec3(at.x, target, at.z);
                case Z -> new Vec3(at.x, at.y, target);
            };
            points.add(at);
        }
        return points;
    }
}
