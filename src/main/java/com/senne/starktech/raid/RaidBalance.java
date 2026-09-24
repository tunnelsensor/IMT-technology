package com.senne.starktech.raid;

/**
 * De getallen van de raiddrones. Hoeveel er komen en hoe vaak staat in de serverconfig
 * (EdithConfig), want dat wil je per wereld kunnen instellen.
 */
public final class RaidBalance {
    private RaidBalance() {}

    // ---------------------------------------------------------------- De drone zelf
    /** Levenspunten (10 = 5 hartjes). Was 16; na jouw feedback afgezwakt. */
    public static final double HEALTH = 10.0;
    /** Schade van één laserschot (3 = 1,5 hartje). Was 5. */
    public static final float LASER_DAMAGE = 3.0f;
    /** Ticks tussen twee schoten (40 = 2 seconden). Was 25. */
    public static final int FIRE_INTERVAL = 40;
    /** Hoeveel ervaring hij laat vallen. */
    public static final int XP_REWARD = 8;

    // ---------------------------------------------------------------- Jagen
    /** Tot hoe ver hij schiet (in blokken). */
    public static final double ATTACK_RANGE = 18.0;
    /** Tot hoe ver hij een doelwit zoekt. */
    public static final double FIND_RANGE = 48.0;
    /** Verder dan dit geeft hij zijn doelwit op. */
    public static final double GIVE_UP_RANGE = 80.0;
    /** Hoe hoog boven zijn doelwit hij blijft hangen. */
    public static final double HOVER_ABOVE = 4.0;
    /** Hoe wijd hij om zijn doelwit cirkelt. */
    public static final double CIRCLE_RADIUS = 6.0;
    /** Hoe hard hij bijstuurt (hoger = wendbaarder). */
    public static final double ACCELERATION = 0.06;

    // ---------------------------------------------------------------- Muren slopen
    /** Ticks tussen twee blokken. 30 ticks = anderhalve seconde. */
    public static final int BREAK_INTERVAL = 30;
    /** Hoe ver vooruit hij blokken weghaalt (in blokken). */
    public static final double BREAK_REACH = 6.0;

    // ---------------------------------------------------------------- Aankomst
    /** Hoe hoog boven je ze verschijnen (in blokken). */
    public static final int ARRIVAL_HEIGHT = 80;
    /** Hoe snel ze naar beneden duiken (blokken per tick). */
    public static final double DIVE_SPEED = 1.8;
    /** Hoe ver de drones van een golf uit elkaar liggen als ze aankomen. */
    public static final double SPREAD = 8.0;

    // ---------------------------------------------------------------- Wanneer ze komen
    /**
     * Hoeveel "aandacht" je trekt door een blok weg te halen in een Stark Tower.
     * Bij {@link #HEAT_FOR_RAID} komen ze meteen.
     */
    public static final int HEAT_PER_TOWER_BLOCK = 4;
    /** Bij deze hoeveelheid aandacht begint er een raid. */
    public static final int HEAT_FOR_RAID = 100;
    /** Hoeveel aandacht er per minuut vanzelf weer afgaat. */
    public static final int HEAT_COOLDOWN_PER_MINUTE = 10;
}
