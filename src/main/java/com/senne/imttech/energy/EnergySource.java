package com.senne.imttech.energy;

/**
 * Iets dat stroom aan het net kan geven: een zonnepaneel of een warmtegenerator.
 *
 * Wat erop aangesloten is (via kabels, of er direct tegenaan), haalt hier stroom weg met
 * {@link #drain}. Zie PowerGrid.
 *
 * Voor het overzicht "is er genoeg stroom?" (in het scherm van een generator) houdt elke leverancier
 * ook bij wat hij de laatste paar seconden gemiddeld per tick maakte, wat het net eruit haalde, en
 * hoeveel apparaten op zijn net tekortkwamen.
 */
public interface EnergySource {
    /** Hoeveel stroom er nu in zit. */
    int energy();

    /** Hoeveel stroom er maximaal in past. */
    int capacity();

    /** Haalt maximaal {@code max} stroom weg en geeft terug hoeveel het echt was. */
    int drain(int max);

    /** Een apparaat op dit net kreeg zoveel stroom niet, omdat er te weinig was. */
    default void missed(double amount) {}

    /** Wat hij de laatste paar seconden gemiddeld per tick opwekte (of kon opwekken). */
    default double madePerTick() {
        return 0;
    }

    /** Wat het net de laatste paar seconden gemiddeld per tick uit hem haalde. */
    default double usedPerTick() {
        return 0;
    }

    /** Wat apparaten op zijn net de laatste paar seconden gemiddeld per tick tekortkwamen. */
    default double missedPerTick() {
        return 0;
    }

    /** Heeft hij al lang genoeg gemeten om iets te zeggen? */
    default boolean measured() {
        return false;
    }
}
