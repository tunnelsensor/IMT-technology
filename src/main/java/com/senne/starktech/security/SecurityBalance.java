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
     * Zover (blokken) reikt een beveiligingskern, in elke richting: opzij, omhoog en omlaag. Dat is
     * zijn gebied: zo ver mag zijn zone liggen (jouw wens, 9 oktober: "een zone tot 200 blokken van de
     * security core"), en zo ver horen zijn apparaten bij hem: torentjes, camera's, keycard-lezers,
     * codepanelen, serverkasten, laserwanden, het landingsplatform, en zijn drones jagen er
     * (ook jouw wens, 9 oktober: "even grote range van de core als dat de zone groot kan").
     *
     * Eén getal voor alles, in de config: maak je het groter of kleiner, dan past alles zich meteen
     * aan. Twee kernen mogen niet in elkaars gebied staan, dus ze staan minstens 2 x dit + 1 uit
     * elkaar (met 200: 401 blokken).
     */
    @Tunable(section = "security", comment = "How far (blocks, in every direction) a security core reaches: its protected zone and all its devices (turrets, cameras, keycard readers, code panels, server racks, laser walls). Two cores must stand at least twice this far apart.")
    public static int ZONE_REACH = 200;

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

    /** Stroom die een bewakingscamera elke twee seconden van het net haalt (anders "NO SIGNAL"). */
    @Tunable(section = "security", comment = "Grid power a security camera uses every two seconds (without it, it shows NO SIGNAL)")
    public static int CAMERA_UPKEEP = 2;

    // ---------------------------------------------------------------- Het systeem (Stark OS)
    /** Zo lang (ticks) mag wie een valstrik-bestand opende niet meer op dat systeem (30 s). */
    @Tunable(section = "security", comment = "How long a trap file locks its reader out of that system (ticks)")
    public static int TRAP_LOCKOUT = 600;

    // ---------------------------------------------------------------- De zone (hoofdstuk 10)
    /** Zoveel kubussen kan één zone hebben (jouw keuze: "meerdere kubussen"; 16 is mijn invulling). */
    public static final int ZONE_MAX_BOXES = 16;
    /**
     * Een zone kost stroom: om de twee seconden 1 extra (bovenop CORE_UPKEEP) per zoveel blokken.
     * Een basis van 30 x 20 x 30 = 18000 blokken kost zo 9 extra; 100 x 40 x 100 kost 200.
     */
    @Tunable(section = "security", comment = "A protected zone costs 1 extra grid power every two seconds per this many blocks")
    public static int ZONE_BLOCKS_PER_POWER = 2000;
}
