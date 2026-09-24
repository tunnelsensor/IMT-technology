package com.senne.starktech.energy;

/**
 * Iets dat stroom aan het net kan geven: een zonnepaneel of een warmtegenerator.
 *
 * Wat erop aangesloten is (via kabels, of er direct tegenaan), haalt hier stroom weg met
 * {@link #drain}. Zie StarkGrid.
 */
public interface EnergySource {
    /** Hoeveel stroom er nu in zit. */
    int energy();

    /** Haalt maximaal {@code max} stroom weg en geeft terug hoeveel het echt was. */
    int drain(int max);
}
