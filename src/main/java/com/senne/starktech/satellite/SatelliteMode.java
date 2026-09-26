package com.senne.starktech.satellite;

import com.senne.starktech.edith.EdithBalance;

/**
 * De drie standen van de satelliet. Je kiest ze in het G-menu (of met je stem);
 * de keuze wordt in de bril zelf opgeslagen.
 *
 * - PRECISION:   één dunne, felle straal. Veel schade op één plek, maar GEEN schade aan blokken.
 *                Perfect als je in je eigen basis of in een dorp schiet.
 * - BOMBARDMENT: een regen van inslagen rond het doel. Maakt een flinke krater.
 * - SWEEP:       een laser die een paar seconden over de grond schuift, weg van jou.
 *                Goed tegen een rij vijanden; hij maakt geen krater.
 */
public enum SatelliteMode {
    // De kosten staan er als "() -> getal", zodat een ander getal uit het configbestand meteen telt.
    PRECISION("precision", () -> EdithBalance.SATELLITE_COST_PRECISION),
    BOMBARDMENT("bombardment", () -> EdithBalance.SATELLITE_COST_BOMBARDMENT),
    SWEEP("sweep", () -> EdithBalance.SATELLITE_COST_SWEEP);

    private final String id;
    private final java.util.function.IntSupplier cost;

    SatelliteMode(String id, java.util.function.IntSupplier cost) {
        this.id = id;
        this.cost = cost;
    }

    /** Wat deze stand aan energie kost. */
    public int cost() {
        return cost.getAsInt();
    }

    public String translationKey() {
        return "edith.starktech.satellite.mode." + id;
    }

    /** De volgende stand (na de laatste komt weer de eerste). */
    public SatelliteMode next() {
        SatelliteMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static SatelliteMode byId(int id) {
        SatelliteMode[] all = values();
        return all[Math.floorMod(id, all.length)];
    }
}
