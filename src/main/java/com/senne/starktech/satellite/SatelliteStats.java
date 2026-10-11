package com.senne.starktech.satellite;

import com.senne.starktech.config.StarkConfig;

/**
 * Wat de upgrades van de satelliet echt doen: zoekt voor elk niveau het juiste getal op in de rijtjes
 * van {@link StarkConfig.Satellite}. Aanpassen doe je dus daar (of in config/starktech.toml), niet hier.
 *
 * Recharge, Power en Targeting gaan van niveau 0 tot 10: hun rijtjes hebben 11 getallen. De drie
 * schietstanden (Precision, Bombardment, Sweep) zitten op niveau 0 op slot: hun rijtjes beginnen bij
 * niveau 1 en hebben er 10.
 */
public final class SatelliteStats {
    private SatelliteStats() {}

    /** Power: schade maal dit, voor alle standen. */
    public static float powerFactor(int power) {
        return StarkConfig.Satellite.DAMAGE_MULTIPLIER[upgrade(power, StarkConfig.Satellite.DAMAGE_MULTIPLIER.length)];
    }

    /** Precision: schade van de precisie-straal, met Power erbij. */
    public static float precisionDamage(int precision, int power) {
        float[] damage = StarkConfig.Satellite.PRECISION_DAMAGE;
        return damage[mode(precision, damage.length)] * powerFactor(power);
    }

    /** Precision: binnen deze straal raakt de straal alles. */
    public static double precisionRadius(int precision) {
        double[] radius = StarkConfig.Satellite.PRECISION_RADIUS;
        return radius[mode(precision, radius.length)];
    }

    /** Bombardment: hoeveel inslagen. */
    public static int bombardmentShots(int bombardment) {
        float[] shots = StarkConfig.Satellite.BOMBARDMENT_SHOTS;
        return Math.round(shots[mode(bombardment, shots.length)]);
    }

    /** Bombardment: hoe ver de inslagen uit elkaar liggen. */
    public static double bombardmentSpread(int bombardment) {
        double[] spread = StarkConfig.Satellite.BOMBARDMENT_SPREAD;
        return spread[mode(bombardment, spread.length)];
    }

    /** Bombardment: kracht van één explosie, met een beetje extra per Power-niveau (TNT = 4). */
    public static float explosionPower(int bombardment, int power) {
        float[] explosion = StarkConfig.Satellite.BOMBARDMENT_EXPLOSION;
        return explosion[mode(bombardment, explosion.length)] + StarkConfig.Satellite.EXPLOSION_PER_POWER_LEVEL * power;
    }

    /** Sweep: hoe lang de laser schuift (ticks). */
    public static int sweepTicks(int sweep) {
        float[] ticks = StarkConfig.Satellite.SWEEP_TICKS;
        return Math.round(ticks[mode(sweep, ticks.length)]);
    }

    /** Sweep: schade per treffer, met Power erbij. */
    public static float sweepDamage(int sweep, int power) {
        float[] damage = StarkConfig.Satellite.SWEEP_DAMAGE;
        return damage[mode(sweep, damage.length)] * powerFactor(power);
    }

    /** Recharge: de wachttijd tussen twee aanvallen (ticks). */
    public static int cooldownTicks(int recharge) {
        float[] cooldown = StarkConfig.Satellite.COOLDOWN;
        return Math.round(cooldown[upgrade(recharge, cooldown.length)]);
    }

    /** Targeting: hoe lang de satelliet richt voor de inslag (ticks). */
    public static int chargeTicks(int targeting) {
        float[] charge = StarkConfig.Satellite.CHARGE_TIME;
        return Math.round(charge[upgrade(targeting, charge.length)]);
    }

    /** Targeting: hoe ver weg je kunt richten (blokken). */
    public static double range(int targeting) {
        double[] range = StarkConfig.Satellite.RANGE;
        return range[upgrade(targeting, range.length)];
    }

    /** Hoeveel drones een upgrade naar niveau {@code nextLevel} naar boven brengen. */
    public static int deliveryDrones(int nextLevel) {
        float[] drones = StarkConfig.Satellite.DELIVERY_DRONES;
        return Math.round(drones[mode(nextLevel, drones.length)]);
    }

    /** Plek in een rijtje van niveau 0 t/m 10. */
    private static int upgrade(int level, int length) {
        return Math.max(0, Math.min(length - 1, level));
    }

    /** Plek in een rijtje van niveau 1 t/m 10 (niveau 0 telt als 1). */
    private static int mode(int level, int length) {
        return Math.max(0, Math.min(length - 1, level - 1));
    }
}
