package com.senne.starktech.energy;

/**
 * Onthoudt het stukje energie dat nog geen heel getal is.
 *
 * De bril, de schermen en het net rekenen in hele stukken energie, maar in de config mag ook 0.3 per
 * tick staan. Dan geeft {@link #take} drie ticks 0 en de vierde 1 (0.3 + 0.3 + 0.3 + 0.3 = 1.2: 1 eruit,
 * 0.2 blijft bewaard voor de volgende keer). Gemiddeld klopt het dus precies: 0.3 per tick = 6 per seconde.
 */
public final class EnergyCarry {
    private double rest;

    /** Telt {@code amount} erbij en geeft het hele deel terug; wat overblijft (minder dan 1) bewaren we. */
    public int take(double amount) {
        if (amount <= 0) {
            return 0;
        }
        rest += amount;
        int whole = (int) Math.min(Integer.MAX_VALUE, rest);
        rest -= whole;
        return whole;
    }
}
