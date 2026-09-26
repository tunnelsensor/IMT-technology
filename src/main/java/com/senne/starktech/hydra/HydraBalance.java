package com.senne.starktech.hydra;

import com.senne.starktech.edith.Tunable;

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
    @Tunable(section = "hydra", comment = "Damage of one Hydra soldier rifle shot")
    public static float LASER_DAMAGE = 4.0f;
    /** Ticks tussen twee schoten (35 = bijna 2 seconden). */
    @Tunable(section = "hydra", comment = "Ticks between two Hydra soldier shots")
    public static int FIRE_INTERVAL = 35;
    /** Tot hoe ver hij schiet (blokken). */
    @Tunable(section = "hydra", comment = "How far a Hydra soldier shoots (blocks)")
    public static float ATTACK_RANGE = 18.0f;
    /** Hoeveel ervaring hij laat vallen. */
    public static final int XP_REWARD = 10;
}
