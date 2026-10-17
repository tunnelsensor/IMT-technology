package com.senne.imttech.satellite;

import com.senne.imttech.config.ImtConfig;


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
    PRECISION("precision", () -> ImtConfig.Satellite.COST_PRECISION),
    BOMBARDMENT("bombardment", () -> ImtConfig.Satellite.COST_BOMBARDMENT),
    SWEEP("sweep", () -> ImtConfig.Satellite.COST_SWEEP);

    private final String id;
    private final java.util.function.DoubleSupplier cost;

    SatelliteMode(String id, java.util.function.DoubleSupplier cost) {
        this.id = id;
        this.cost = cost;
    }

    /** Wat deze stand aan energie kost. */
    public double cost() {
        return cost.getAsDouble();
    }

    public String translationKey() {
        return "imt.imttech.satellite.mode." + id;
    }

    public static SatelliteMode byId(int id) {
        SatelliteMode[] all = values();
        return all[Math.floorMod(id, all.length)];
    }
}
