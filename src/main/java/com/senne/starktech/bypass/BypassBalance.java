package com.senne.starktech.bypass;

import com.senne.starktech.edith.Tunable;

/**
 * De getallen van inbreken (hoofdstuk 12): lezers en panelen hacken met de Bypass Kit, en
 * zakkenrollen. Met {@link Tunable} erboven staan ze ook in config/starktech-balance.toml, onder
 * [bypass].
 */
public final class BypassBalance {
    private BypassBalance() {}

    /** Zo vaak gaat een Bypass Kit mee (elke hack die je begint kost er één). Vast bij het opstarten. */
    public static final int KIT_USES = 16;

    /** Zo dichtbij moet je blijven tijdens een hack (blokken); verder weg = de hack stopt. */
    public static final double REACH = 6.0;

    // ---------------------------------------------------------------- Het stroomcircuit (lezer)

    /**
     * Hoe groot het raster is per niveau van de kaart die je namaakt (index 0 = niveau 1), en hoeveel
     * ALARM-punten erlangs zitten. Jouw keuze: hoe hoger het niveau, hoe meer stukjes.
     */
    public static final int[] CIRCUIT_COLUMNS = {5, 6, 6, 7, 8};
    public static final int[] CIRCUIT_ROWS = {4, 4, 5, 5, 6};
    public static final int[] CIRCUIT_ALARMS = {1, 1, 2, 2, 3};

    @Tunable(section = "bypass", comment = "Seconds you get to rewire a keycard reader when you fake a level 1 card")
    public static int CIRCUIT_SECONDS = 60;
    @Tunable(section = "bypass", comment = "Seconds less for every card level above 1 (level 5 gets 4 times this less)")
    public static int CIRCUIT_SECONDS_LESS_PER_LEVEL = 5;
    @Tunable(section = "bypass", comment = "How long the signal may touch ALARM before the alarm goes off (ticks; 20 = 1 second)")
    public static int ALARM_GRACE_TICKS = 20;

    // ---------------------------------------------------------------- Na een mislukte hack

    @Tunable(section = "bypass", comment = "After a failed hack, that reader or panel ignores the hacker for this long (seconds)")
    public static int LOCKOUT_SECONDS = 60;

    /** Hoeveel seconden je hebt voor een kaart van dit niveau (1-5). */
    public static int circuitSeconds(int level) {
        return Math.max(10, CIRCUIT_SECONDS - CIRCUIT_SECONDS_LESS_PER_LEVEL * (clampLevel(level) - 1));
    }

    /** Een niveau tussen 1 en 5. */
    public static int clampLevel(int level) {
        return Math.max(1, Math.min(CIRCUIT_COLUMNS.length, level));
    }
}
