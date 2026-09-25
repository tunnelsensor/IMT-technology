package com.senne.starktech.hydra;

/**
 * De getallen van Hydra: hun soldaten (en straks hun torentjes en bunker).
 */
public final class HydraBalance {
    private HydraBalance() {}

    // ---------------------------------------------------------------- De soldaat
    /** Levenspunten (24 = 12 hartjes): iets taaier dan een zombie. */
    public static final double SOLDIER_HEALTH = 24.0;
    /** Pantser (een beetje, van het vest). */
    public static final double SOLDIER_ARMOR = 4.0;
    /** Hoe snel hij loopt. */
    public static final double SOLDIER_SPEED = 0.27;
    /** Schade van één laserschot (4 = 2 hartjes). */
    public static final float LASER_DAMAGE = 4.0f;
    /** Ticks tussen twee schoten (35 = bijna 2 seconden). */
    public static final int FIRE_INTERVAL = 35;
    /** Tot hoe ver hij schiet (blokken). */
    public static final float ATTACK_RANGE = 18.0f;
    /** Hoeveel ervaring hij laat vallen. */
    public static final int XP_REWARD = 10;
}
