package com.senne.starktech.security;

import com.senne.starktech.edith.Tunable;

/**
 * Alle getallen van de beveiliging van de Stark Tower. Met {@link Tunable} erboven staan ze ook
 * in config/starktech-balance.toml.
 */
public final class SecurityBalance {
    private SecurityBalance() {}

    // ---------------------------------------------------------------- Het gebied van een kern
    /**
     * Zover opzij (x en z) beschermt een beveiligingskern de toren. De toren is 41 breed en de
     * kern staat niet in het midden (in de rij serverkasten), dus ruim genomen.
     */
    public static final int AREA_RADIUS = 30;
    /** Zover onder de kern (tot het plein) en erboven (tot de antennes). */
    public static final int AREA_DOWN = 60;
    public static final int AREA_UP = 55;

    // ---------------------------------------------------------------- De drones
    /** Levens van een beveiligingsdrone (8 = 4 hartjes). Vast bij het opstarten, dus niet in de config. */
    public static final double DRONE_HEALTH = 8.0;
    @Tunable(section = "security", comment = "How many security drones a Stark Tower keeps flying")
    public static int MAX_DRONES = 4;
    @Tunable(section = "security", comment = "How long the tower waits before replacing a lost security drone (ticks)")
    public static int DRONE_RESPAWN = 900;
    @Tunable(section = "security", comment = "Damage of one security drone laser shot")
    public static float DRONE_LASER_DAMAGE = 3.0f;
    @Tunable(section = "security", comment = "Ticks between two security drone shots")
    public static int DRONE_FIRE_INTERVAL = 30;
    @Tunable(section = "security", comment = "How far a security drone sees and shoots intruders (blocks)")
    public static double DRONE_RANGE = 16.0;
    /** Zover van de kern patrouilleert een drone als hij niemand ziet. */
    public static final double PATROL_RADIUS = 10.0;
    /** Hoe hard hij bijstuurt (zoals een raiddrone, iets rustiger). */
    public static final double ACCELERATION = 0.05;

    /** Stroom die de beveiligingskern elke twee seconden van het net haalt (anders staat hij uit). */
    @Tunable(section = "security", comment = "Grid power the tower security core uses every two seconds (without it, it and its drones are off)")
    public static int CORE_UPKEEP = 20;

    // ---------------------------------------------------------------- Het systeem (Stark OS)
    /** Zo lang (ticks) mag wie een valstrik-bestand opende niet meer op dat systeem (30 s). */
    @Tunable(section = "security", comment = "How long a trap file locks its reader out of that system (ticks)")
    public static int TRAP_LOCKOUT = 600;
}
