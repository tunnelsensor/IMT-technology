package com.senne.starktech.edith;

/**
 * Alle wachttijden ("cooldowns") van E.D.I.T.H. Voor elke zware functie houdt de server bij
 * wanneer je hem weer mag gebruiken. Ze zitten in één lijst, zodat we voor een nieuwe functie
 * alleen hier een regel hoeven bij te zetten (en niet overal in de netwerkberichten).
 *
 * Let op: de volgorde bepaalt het nummer dat over het netwerk gaat. Zet nieuwe wachttijden
 * daarom ONDERAAN, en verwijder er nooit een zonder de protocolversie in ModNetwork te verhogen.
 */
public enum EdithCooldown {
    /** Ertsen-scan. */
    ORE_SCAN,
    /** Zwerm aanvalsdrones. */
    SWARM,
    /** Satelliet-aanval. */
    SATELLITE,
    /** Eén drone op één doelwit. */
    STRIKE,
    /** Spionagedrone. */
    SPY,
    /** Transportdrones (zelf vliegen, automatische piloot, vracht). */
    TRANSPORT,
    /** Speler hacken. */
    HACK,
    /** Illusie-/cloakingdrones. */
    ILLUSION,
    /** Rayshield (persoonlijk schild, muur, koepel). */
    SHIELD,
    /** Redstone aansturen. */
    REDSTONE,
    /** Muziek (Spotify). */
    MUSIC;

    public static final int COUNT = values().length;
}
