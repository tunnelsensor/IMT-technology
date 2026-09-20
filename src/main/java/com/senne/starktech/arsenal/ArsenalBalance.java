package com.senne.starktech.arsenal;

/**
 * De getallen van het arsenaal. Net als EdithBalance: pas ze aan, bouw opnieuw, klaar.
 */
public final class ArsenalBalance {
    private ArsenalBalance() {}

    /** Levenspunten van een gloednieuwe drone. */
    public static final double DRONE_MAX_HEALTH = 12.0;

    /**
     * Hoe hoog boven je de drones vandaan komen, en hoe hoog ze weer verdwijnen (in blokken).
     *
     * Ze horen echt uit de lucht te vallen, niet naast je te verschijnen. Zit er een plafond
     * boven je (een grot, een kelder), dan komen ze zo hoog als er ruimte is.
     */
    public static final int ORBIT_HEIGHT = 96;

    /** Hoe snel een drone naar beneden duikt (blokken per tick). 2,5 = ruim 2 seconden van boven. */
    public static final double DIVE_SPEED = 2.5;
    /** Op deze hoogte boven zijn doel vangt hij de duik op. */
    public static final double DIVE_BRAKE_HEIGHT = 3.0;

    /** Hoe snel een vertrekkende drone omhoog gaat (blokken per tick). */
    public static final double LAUNCH_SPEED = 2.2;
    /**
     * Na zoveel ticks is een vertrekkende drone hoe dan ook weg, ook als hij tegen een
     * plafond aan blijft duwen.
     */
    public static final int LAUNCH_TICKS = 80;

    /**
     * Welk deel van de bouwkosten je terugkrijgt als een drone wordt neergehaald.
     * De rest is verloren: een kapotte drone vervang je door een nieuwe te bouwen.
     */
    public static final double WRECK_RECOVERY = 0.10;

    /**
     * Hoeveel levenspunten één reparatie-onderdeel (een ijzerstaaf) herstelt.
     * Een drone die helemaal op is, kost dus DRONE_MAX_HEALTH / REPAIR_PER_INGOT staven.
     */
    public static final double REPAIR_PER_INGOT = 3.0;
}
