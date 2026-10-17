package com.senne.imttech.drone;

import net.minecraft.world.phys.Vec3;

/**
 * Een duik uit de lucht in een boog, voor elke drone die van hoog komt (drones en
 * raiddrones).
 *
 * Vroeger vielen ze recht naar beneden en stonden dan ineens stil. Nu beginnen ze een stuk
 * opzij (zie ImtDrones.arcStart), komen steil naar beneden, buigen onderweg af en glijden in
 * een schuine lijn naar hun doel. Snel vanuit de lucht, en rustig afremmend bij aankomst.
 *
 * Wiskundig is het een "kwadratische Bézier-kromme": een lijn van {@code from} naar {@code to}
 * die naar een derde punt {@code via} toe gebogen wordt. Dat punt ligt een stukje boven en
 * opzij van het doel, zodat de drone op het laatst schuin binnenkomt.
 */
public final class SkyDive {
    /** Hoe snel een duik gemiddeld gaat (blokken per tick). Door het afremmen begint hij sneller. */
    private static final double AVERAGE_SPEED = 1.7;

    private final Vec3 from;
    private final Vec3 via;
    private final Vec3 to;
    private final int ticks;
    private int age;

    public SkyDive(Vec3 from, Vec3 to) {
        this.from = from;
        this.to = to;
        Vec3 sideways = new Vec3(from.x - to.x, 0, from.z - to.z);
        double height = Math.max(0, from.y - to.y);
        // Het buigpunt: 40% van de weg opzij, en ongeveer even hoog als dat opzij is. Dan komt
        // hij op het laatst onder zo'n 40 graden binnen. Recht erboven begonnen? Dan is het
        // gewoon een rechte duik.
        this.via = to.add(sideways.scale(0.4)).add(0, Math.min(height * 0.5, sideways.length() * 0.35 + 1.5), 0);
        this.ticks = Math.max(8, (int) Math.round(length() / AVERAGE_SPEED));
    }

    /** Waar de duik eindigt. */
    public Vec3 to() {
        return to;
    }

    /** Zet één tick verder en geeft de plek waar de drone nu hoort te zijn. */
    public Vec3 next() {
        age = Math.min(ticks, age + 1);
        return at(ease(age / (double) ticks));
    }

    public boolean done() {
        return age >= ticks;
    }

    /** Snel beginnen en rustig aankomen: in het begin gaat de tijd hard, op het eind langzaam. */
    private static double ease(double u) {
        double left = 1 - u;
        return 1 - left * left * left;
    }

    /** Het punt op de kromme bij {@code t} (0 = begin, 1 = doel). */
    public Vec3 at(double t) {
        double a = (1 - t) * (1 - t);
        double b = 2 * (1 - t) * t;
        double c = t * t;
        return new Vec3(a * from.x + b * via.x + c * to.x,
                a * from.y + b * via.y + c * to.y,
                a * from.z + b * via.z + c * to.z);
    }

    /** Ongeveer hoe lang de kromme is (in stukjes gemeten). */
    private double length() {
        double total = 0;
        Vec3 last = from;
        for (int i = 1; i <= 16; i++) {
            Vec3 point = at(i / 16.0);
            total += point.distanceTo(last);
            last = point;
        }
        return total;
    }
}
