package com.senne.starktech.turret;

import com.senne.starktech.edith.Tunable;

/**
 * Alle getallen van de torentjes. Net als EdithBalance: aanpassen, opnieuw bouwen, klaar.
 */
public final class TurretBalance {
    private TurretBalance() {}

    // ---------------------------------------------------------------- Stark-torentje
    /** Tot hoe ver hij doelwitten ziet en met de laser raakt (in blokken). */
    @Tunable(section = "turrets", comment = "How far a Stark turret's laser reaches (blocks)")
    public static double RANGE = 16.0;
    /** Laserschade per schot (4 = 2 hartjes). */
    @Tunable(section = "turrets", comment = "Damage of one Stark turret laser shot")
    public static float LASER_DAMAGE = 4.0f;
    /** Ticks tussen twee laserschoten (20 = 1 seconde). */
    @Tunable(section = "turrets", comment = "Ticks between two Stark turret laser shots")
    public static int LASER_INTERVAL = 20;
    /**
     * Wat één laserschot van het stroomnet haalt. Een zonnepaneel maakt 120 per seconde (in de
     * zon), dus één paneel houdt één torentje dat elke seconde schiet makkelijk bij.
     */
    @Tunable(section = "turrets", comment = "Grid energy one Stark turret laser shot uses")
    public static int LASER_COST = 60;

    /** Raketten (munitie in het torentje) reiken iets verder dan de laser. */
    @Tunable(section = "turrets", comment = "How far a Stark turret fires rockets (blocks)")
    public static double ROCKET_RANGE = 24.0;
    /** Ticks tussen twee raketten. */
    @Tunable(section = "turrets", comment = "Ticks between two turret rockets")
    public static int ROCKET_INTERVAL = 50;
    /** Zoveel raketten passen er in het torentje. */
    public static final int MAX_AMMO = 64;

    // ---------------------------------------------------------------- Hydra-torentje
    /** Het Hydra-torentje (in de bunker) heeft geen stroom nodig, maar schiet iets minder hard. */
    @Tunable(section = "turrets", comment = "Damage of one Hydra turret laser shot")
    public static float HYDRA_LASER_DAMAGE = 3.0f;
    @Tunable(section = "turrets", comment = "Ticks between two Hydra turret laser shots")
    public static int HYDRA_LASER_INTERVAL = 25;
    @Tunable(section = "turrets", comment = "How far a Hydra turret reaches (blocks)")
    public static double HYDRA_RANGE = 14.0;

    // ---------------------------------------------------------------- Beveiligingstorentje
    /** Het beveiligingstorentje in de Stark Tower: geen stroom nodig, schiet op indringers. */
    @Tunable(section = "turrets", comment = "Damage of one Stark Tower security turret shot")
    public static float SECURITY_LASER_DAMAGE = 3.0f;
    @Tunable(section = "turrets", comment = "Ticks between two security turret shots")
    public static int SECURITY_LASER_INTERVAL = 20;
    @Tunable(section = "turrets", comment = "How far a security turret reaches (blocks)")
    public static double SECURITY_RANGE = 16.0;

    // ---------------------------------------------------------------- Draaien
    /** Hoe vaak (in ticks) hij rondkijkt naar een nieuw doelwit als hij er geen heeft. */
    public static final int SCAN_INTERVAL = 10;
    /** Zoveel graden per tick draait de kop hooguit (in je eigen spel, voor het beeld). */
    public static final float TURN_SPEED = 14.0f;
    /** Zo snel draait hij rond als hij niemand ziet (graden per tick). */
    public static final float IDLE_TURN_SPEED = 1.2f;
}
