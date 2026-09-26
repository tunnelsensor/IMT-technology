package com.senne.starktech.turret;

/**
 * Alle getallen van de torentjes. Net als EdithBalance: aanpassen, opnieuw bouwen, klaar.
 */
public final class TurretBalance {
    private TurretBalance() {}

    // ---------------------------------------------------------------- Stark-torentje
    /** Tot hoe ver hij doelwitten ziet en met de laser raakt (in blokken). */
    public static final double RANGE = 16.0;
    /** Laserschade per schot (4 = 2 hartjes). */
    public static final float LASER_DAMAGE = 4.0f;
    /** Ticks tussen twee laserschoten (20 = 1 seconde). */
    public static final int LASER_INTERVAL = 20;
    /**
     * Wat één laserschot van het stroomnet haalt. Een zonnepaneel maakt 120 per seconde (in de
     * zon), dus één paneel houdt één torentje dat elke seconde schiet makkelijk bij.
     */
    public static final int LASER_COST = 60;

    /** Raketten (munitie in het torentje) reiken iets verder dan de laser. */
    public static final double ROCKET_RANGE = 24.0;
    /** Ticks tussen twee raketten. */
    public static final int ROCKET_INTERVAL = 50;
    /** Zoveel raketten passen er in het torentje. */
    public static final int MAX_AMMO = 64;

    // ---------------------------------------------------------------- Hydra-torentje
    /** Het Hydra-torentje (in de bunker) heeft geen stroom nodig, maar schiet iets minder hard. */
    public static final float HYDRA_LASER_DAMAGE = 3.0f;
    public static final int HYDRA_LASER_INTERVAL = 25;
    public static final double HYDRA_RANGE = 14.0;

    // ---------------------------------------------------------------- Draaien
    /** Hoe vaak (in ticks) hij rondkijkt naar een nieuw doelwit als hij er geen heeft. */
    public static final int SCAN_INTERVAL = 10;
    /** Zoveel graden per tick draait de kop hooguit (in je eigen spel, voor het beeld). */
    public static final float TURN_SPEED = 14.0f;
    /** Zo snel draait hij rond als hij niemand ziet (graden per tick). */
    public static final float IDLE_TURN_SPEED = 1.2f;
}
