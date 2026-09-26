package com.senne.starktech.arsenal;

import com.senne.starktech.edith.Tunable;

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

    /**
     * Een drone duikt in een boog naar beneden (zie SkyDive), en begint daarvoor een stuk opzij:
     * dit deel van de valhoogte, maar nooit meer dan DIVE_ARC_MAX blokken.
     */
    public static final double DIVE_ARC_RATIO = 0.3;
    public static final double DIVE_ARC_MAX = 24.0;
    /** Zo hoog boven zijn doel komt een aanvallende drone na de duik uit. */
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
    @Tunable(section = "drones", comment = "Share of a drone's build cost you get back as scrap when it is shot down (0-1)")
    public static double WRECK_RECOVERY = 0.10;

    /**
     * Hoeveel levenspunten één reparatie-onderdeel (een ijzerstaaf) herstelt.
     * Een drone die helemaal op is, kost dus DRONE_MAX_HEALTH / REPAIR_PER_INGOT staven.
     */
    @Tunable(section = "drones", comment = "Health one iron ingot repairs on a drone")
    public static double REPAIR_PER_INGOT = 3.0;

    /**
     * Een drone op een drone-dock in je hangar wordt vanzelf gerepareerd, met stroom van het net:
     * zoveel levenspunten per seconde (een drone die op is, is zo in 48 seconden weer heel)...
     */
    @Tunable(section = "drones", comment = "Health a drone dock repairs per second on the drone standing on it")
    public static double DOCK_REPAIR_PER_SECOND = 0.25;
    /** ...en dat kost zoveel stroom per levenspunt. */
    @Tunable(section = "drones", comment = "Energy a drone dock uses per point of health it repairs")
    public static int DOCK_ENERGY_PER_HEALTH = 40;
}
