package com.senne.starktech.arsenal;

/**
 * De getallen van het arsenaal. Net als EdithBalance: pas ze aan, bouw opnieuw, klaar.
 */
public final class ArsenalBalance {
    private ArsenalBalance() {}

    /** Levenspunten van een gloednieuwe drone. */
    public static final double DRONE_MAX_HEALTH = 12.0;

    /** Hoe lang de lancering naar de baan duurt (in ticks). 60 ticks = 3 seconden. */
    public static final int LAUNCH_TICKS = 60;
    /** Hoe snel een lancerende drone omhoog gaat (blokken per tick). */
    public static final double LAUNCH_SPEED = 0.55;

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
