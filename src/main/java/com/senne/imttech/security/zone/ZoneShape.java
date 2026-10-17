package com.senne.imttech.security.zone;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Rekenwerk over de vorm van een zone: waar is zijn buitenkant? Daar tekent jouw spel de rand (zie
 * client/zone/ZoneBorderRenderer). Het staat hier (en niet bij het tekenen), zodat de tests het
 * kunnen nakijken.
 *
 * Een zone bestaat uit kubussen die tegen of over elkaar mogen liggen. Waar twee kubussen elkaar
 * raken, hoort geen wand: alleen de echte buitenkant telt.
 */
public final class ZoneShape {
    private ZoneShape() {}

    /**
     * Eén stuk buitenkant: een rechthoek op het vlak {@code plane} (een blokrand) loodrecht op de as
     * van {@code face}, van {@code a0} tot {@code a1} en van {@code b0} tot {@code b1} op de twee
     * andere assen (zie {@link #axisA} en {@link #axisB}). {@code face} wijst naar buiten.
     */
    public record Rect(Direction face, int plane, int a0, int a1, int b0, int b1) {
        /** Hoeveel blokvlakjes groot. */
        public long area() {
            return (long) (a1 - a0) * (b1 - b0);
        }
    }

    /** De eerste as in het vlak (voor een wand loodrecht op x: y; anders x). */
    public static Direction.Axis axisA(Direction.Axis normal) {
        return normal == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
    }

    /** De tweede as in het vlak (voor een wand loodrecht op z: y; anders z). */
    public static Direction.Axis axisB(Direction.Axis normal) {
        return normal == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
    }

    /**
     * De buitenkant van kubussen, als rechthoeken. We knippen elke kant van elke kubus op langs alle
     * randen van alle kubussen; elk stukje waar het blok net buiten die kant NIET in een kubus ligt,
     * is buitenkant. Stukjes die dubbel voorkomen (twee kubussen met dezelfde kant) tellen één keer.
     */
    public static List<Rect> boundary(List<ZoneBox> boxes) {
        if (boxes.isEmpty()) {
            return List.of();
        }
        List<TreeSet<Integer>> cuts = List.of(new TreeSet<>(), new TreeSet<>(), new TreeSet<>());
        for (ZoneBox box : boxes) {
            for (Direction.Axis axis : Direction.Axis.values()) {
                cuts.get(axis.ordinal()).add(box.min().get(axis));
                cuts.get(axis.ordinal()).add(box.max().get(axis) + 1);
            }
        }
        LinkedHashSet<Rect> rects = new LinkedHashSet<>();
        for (ZoneBox box : boxes) {
            for (Direction face : Direction.values()) {
                Direction.Axis normal = face.getAxis();
                Direction.Axis a = axisA(normal);
                Direction.Axis b = axisB(normal);
                boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
                int plane = positive ? box.max().get(normal) + 1 : box.min().get(normal);
                int outside = positive ? plane : plane - 1;
                List<Integer> as = between(cuts.get(a.ordinal()), box.min().get(a), box.max().get(a) + 1);
                List<Integer> bs = between(cuts.get(b.ordinal()), box.min().get(b), box.max().get(b) + 1);
                for (int i = 0; i + 1 < as.size(); i++) {
                    for (int j = 0; j + 1 < bs.size(); j++) {
                        BlockPos probe = block(normal, outside, a, as.get(i), b, bs.get(j));
                        if (!inAny(boxes, probe)) {
                            rects.add(new Rect(face, plane, as.get(i), as.get(i + 1), bs.get(j), bs.get(j + 1)));
                        }
                    }
                }
            }
        }
        return List.copyOf(rects);
    }

    /** {@code from}, dan alle randen ertussen, dan {@code to}. */
    private static List<Integer> between(TreeSet<Integer> cuts, int from, int to) {
        List<Integer> out = new ArrayList<>();
        out.add(from);
        out.addAll(cuts.subSet(from, false, to, false));
        out.add(to);
        return out;
    }

    /** Een blok uit de plek op de normaal en de twee assen in het vlak. */
    private static BlockPos block(Direction.Axis normal, int n, Direction.Axis a, int av, Direction.Axis b, int bv) {
        int[] xyz = new int[3];
        xyz[normal.ordinal()] = n;
        xyz[a.ordinal()] = av;
        xyz[b.ordinal()] = bv;
        return new BlockPos(xyz[0], xyz[1], xyz[2]);
    }

    /** Ligt dit blok in een van de kubussen? */
    public static boolean inAny(List<ZoneBox> boxes, BlockPos pos) {
        for (ZoneBox box : boxes) {
            if (box.contains(pos)) {
                return true;
            }
        }
        return false;
    }
}
