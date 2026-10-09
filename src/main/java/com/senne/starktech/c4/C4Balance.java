package com.senne.starktech.c4;

import com.senne.starktech.edith.Tunable;

/**
 * Alle getallen van C4 (hoofdstuk 10, jouw keuze: "sterke muren kosten meer C4"). Met {@link Tunable}
 * erboven staan ze ook in config/starktech-balance.toml, onder [c4].
 *
 * Hoe de klap werkt: één C4 doet {@link #POWER} "breekpunten" vlak bij de lading, en dat loopt af tot
 * 0 op {@link #RADIUS} blokken. Elk blok kan een aantal breekpunten hebben voor het breekt: een zwak
 * blok (hout, glas, aarde...) {@link #WEAK}, een gewoon blok (steen, baksteen...) {@link #NORMAL}, een
 * sterk blok (obsidiaan...) {@link #STRONG}, en de beveiligingskern {@link #CORE}. Wat niet breekt,
 * krijgt barsten: de volgende C4 telt erbij op.
 *
 * Zo maakt één C4 tegen een stenen muur van één dik een gat waar je door kunt (het blok erachter en
 * de vier ernaast), en heb je voor obsidiaan er drie nodig.
 */
public final class C4Balance {
    private C4Balance() {}

    // ---------------------------------------------------------------- De klap
    @Tunable(section = "c4", comment = "Breach points of one C4 right at the charge (falling to 0 at the blast radius)")
    public static float POWER = 10.0f;
    @Tunable(section = "c4", comment = "How far a C4 blast reaches (blocks)")
    public static float RADIUS = 3.5f;
    @Tunable(section = "c4", comment = "Breach points a weak block (wood, glass, dirt, wool, sand...) takes before it breaks")
    public static float WEAK = 2.0f;
    @Tunable(section = "c4", comment = "Breach points a normal block (stone, bricks, deepslate, iron...) takes before it breaks")
    public static float NORMAL = 6.0f;
    @Tunable(section = "c4", comment = "Breach points a strong block (obsidian, netherite, the laser emitter...) takes before it breaks")
    public static float STRONG = 20.0f;
    @Tunable(section = "c4", comment = "Breach points the security core takes before it breaks (about 6 C4)")
    public static float CORE = 48.0f;
    /** Vanaf deze explosieweerstand (zoals in Minecraft) is een blok "gewoon"; daaronder zwak. */
    public static final float NORMAL_RESISTANCE = 4.0f;
    /** Vanaf deze explosieweerstand is een blok "sterk" (obsidiaan heeft 1200, steen 6). */
    public static final float STRONG_RESISTANCE = 100.0f;
    @Tunable(section = "c4", comment = "How hard a C4 blast hits players and mobs (TNT is 4)")
    public static float ENTITY_POWER = 4.0f;
    @Tunable(section = "c4", comment = "How long the cracks of a C4 blast stay in a block (ticks; 12000 = 10 minutes)")
    public static int CRACK_HEAL_TICKS = 12_000;
    /** Andere C4 in de klap gaat zoveel ticks later ook af (een kettingreactie). */
    public static final int CHAIN_DELAY = 3;

    // ---------------------------------------------------------------- De timer
    /** De kortste en langste tijd die je op een C4 (Timer) kunt zetten (seconden). */
    public static final int TIMER_MIN = 5;
    public static final int TIMER_MAX = 120;
    /** Zo staat een nieuwe C4 (Timer) als je nog niets koos (seconden). */
    public static final int TIMER_DEFAULT = 10;
}
