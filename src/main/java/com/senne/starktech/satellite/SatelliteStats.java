package com.senne.starktech.satellite;

import com.senne.starktech.edith.EdithBalance;

/**
 * Wat de upgrades van de satelliet echt doen: de getallen per niveau, op één plek.
 *
 * Alles begint bij de getallen uit EdithBalance (dat is niveau "zoals vroeger") en schuift
 * per niveau een stukje op. Aanpassen? Verander de getallen hier en bouw opnieuw.
 */
public final class SatelliteStats {
    private SatelliteStats() {}

    /** Kracht: +8% schade per niveau, voor alle standen (niveau 10: +80%). */
    public static float powerFactor(int power) {
        return 1.0f + 0.08f * power;
    }

    /** Precisie: +12% schade per niveau boven 1 (niveau 1 = 30 schade, zoals vroeger). */
    public static float precisionDamage(int precision, int power) {
        return EdithBalance.PRECISION_DAMAGE * (1.0f + 0.12f * Math.max(0, precision - 1)) * powerFactor(power);
    }

    /** Precisie: de straal wordt per niveau 0,2 blok breder. */
    public static double precisionRadius(int precision) {
        return EdithBalance.PRECISION_RADIUS + 0.2 * Math.max(0, precision - 1);
    }

    /** Bombardement: 4 inslagen op niveau 1, één meer per niveau (13 op niveau 10). */
    public static int bombardmentShots(int bombardment) {
        return 3 + Math.max(1, bombardment);
    }

    /** Bombardement: de inslagen liggen iets verder uit elkaar naarmate het er meer zijn. */
    public static double bombardmentSpread(int bombardment) {
        return EdithBalance.BOMBARDMENT_SPREAD + 0.3 * Math.max(0, bombardment - 1);
    }

    /** Bombardement: grotere explosies met niveau en kracht (TNT = 4). */
    public static float explosionPower(int bombardment, int power) {
        return EdithBalance.SATELLITE_EXPLOSION_POWER * (0.9f + 0.04f * bombardment) + 0.1f * power;
    }

    /** Schuivende laser: 2,8 seconde op niveau 1, per niveau 0,3 seconde langer. */
    public static int sweepTicks(int sweep) {
        return 50 + 6 * Math.max(1, sweep);
    }

    /** Schuivende laser: schade per treffer. */
    public static float sweepDamage(int sweep, int power) {
        return EdithBalance.SWEEP_DAMAGE * (0.8f + 0.07f * Math.max(1, sweep)) * powerFactor(power);
    }

    /** Opladen: 6% kortere wachttijd per niveau (niveau 10: 36 in plaats van 90 seconden). */
    public static int cooldownTicks(int recharge) {
        return Math.round(EdithBalance.SATELLITE_COOLDOWN * (1.0f - 0.06f * recharge));
    }

    /** Richten: 5% korter laden voor de inslag per niveau (niveau 10: 1,5 in plaats van 3 seconden). */
    public static int chargeTicks(int targeting) {
        return Math.round(EdithBalance.SATELLITE_CHARGE_TIME * (1.0f - 0.05f * targeting));
    }

    /** Richten: per niveau 50 blokken verder (niveau 10: 1000 blokken). */
    public static double range(int targeting) {
        return EdithBalance.SATELLITE_RANGE + 50.0 * targeting;
    }
}
