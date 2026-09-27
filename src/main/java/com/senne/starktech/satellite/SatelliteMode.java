package com.senne.starktech.satellite;

import com.senne.starktech.edith.EdithBalance;

/**
 * De drie aanvallen van de satelliet. Elk heeft een eigen toets die meteen vuurt (K, P en C),
 * een eigen optie in het G-menu en eigen spraakcommando's; er is geen "stand" om in te stellen.
 * De bril onthoudt alleen welke aanval er onderweg is (voor de HUD).
 *
 * - PRECISION:   één raket uit de ruimte. Veel schade op één plek, maar GEEN schade aan blokken.
 *                Perfect als je in je eigen basis of in een dorp schiet. (K)
 * - BOMBARDMENT: een regen van raketten rond het doel. Maakt flinke kraters. (P)
 * - SWEEP:       een laser die een paar seconden over de grond schuift, van het beginpunt naar
 *                het eindpunt dat je koos. Goed tegen een rij vijanden; geen krater. (C)
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

    public static SatelliteMode byId(int id) {
        SatelliteMode[] all = values();
        return all[Math.floorMod(id, all.length)];
    }
}
