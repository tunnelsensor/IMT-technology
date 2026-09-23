package com.senne.starktech.suit;

/**
 * Alle getallen van het harnas. Net als EdithBalance: aanpassen, opnieuw bouwen, klaar.
 *
 * Het harnas heeft geen eigen batterij. Alles wat het doet, betaalt de bril.
 */
public final class SuitBalance {
    private SuitBalance() {}

    // ---------------------------------------------------------------- Bescherming
    /** Bescherming zonder stroom: goed metaal, meer niet. */
    public static final int ARMOR_CHEST = 6;
    public static final int ARMOR_LEGS = 4;
    public static final int ARMOR_BOOTS = 3;
    /** Hoe goed het harnas zware klappen opvangt (net als netherite). */
    public static final float TOUGHNESS = 2.0f;
    /** Hoe goed het je op de been houdt bij een klap. */
    public static final float KNOCKBACK_RESISTANCE = 0.1f;

    /**
     * Welk deel van de schade het harnas wegneemt zolang het stroom heeft, per gedragen stuk.
     * Drie stukken is dus 0,60: zestig procent eraf, bovenop de gewone bescherming.
     */
    public static final float POWERED_REDUCTION_PER_PIECE = 0.20f;
    /** Wat één punt tegengehouden schade kost (2 schade = 1 hartje). */
    public static final int REDUCTION_COST_PER_DAMAGE = 60;

    // ---------------------------------------------------------------- Vliegen
    /** Energie per seconde als je stilhangt. */
    public static final int DRAIN_HOVER = 12;
    /** Energie per seconde als je vooruit vliegt. */
    public static final int DRAIN_FLY = 26;
    /** Energie per seconde met de naverbrander (sprinttoets). */
    public static final int DRAIN_BOOST = 45;
    /** Vanaf deze snelheid (blokken per tick) "vlieg" je in plaats van te zweven. */
    public static final double CRUISE_SPEED = 0.25;
    /** Vanaf deze snelheid gaat de naverbrander mee. */
    public static final double BOOST_SPEED = 1.3;

    // De natuurkunde van het vliegen (zie client/SuitFlight). Snelheden in blokken per tick:
    // 1 blok per tick = 20 blokken per seconde = 72 km/u.
    /** Stuwkracht per tick in de richting waarin je kijkt (W/S) of opzij (A/D). */
    public static final double THRUST = 0.045;
    /** Zoveel keer sterker met de naverbrander (sprinttoets). */
    public static final double BOOST = 2.3;
    /**
     * Stuwkracht omhoog (spatie) en omlaag (shift). Eerst 0,07 en 0,06: dan ging je na ruim
     * een seconde met 22 m/s omhoog en knalde je met 25 m/s op de grond.
     */
    public static final double THRUST_UP = 0.04;
    public static final double THRUST_DOWN = 0.035;
    /** Vlak boven de grond remt het harnas zelf af tot hooguit deze valsnelheid. */
    public static final double LANDING_SPEED = 0.3;
    /** Vanaf zoveel blokken boven de grond begint dat afremmen. */
    public static final double LANDING_HEIGHT = 5.0;
    /**
     * Hoeveel vaart je elke tick houdt (0.96 = 96%). Lopend in de lucht is dat 91%; met het
     * harnas glij je dus langer door: je bouwt snelheid op en remt langzaam af.
     */
    public static final double KEEP_SPEED = 0.96;
    /** Zonder op/neer te sturen zakt je verticale vaart snel weg: het harnas houdt je op hoogte. */
    public static final double HOVER_DAMPING = 0.8;
    /** Hoger dan dit gaat het niet (blokken per tick), met en zonder naverbrander. */
    public static final double MAX_SPEED = 1.3;
    public static final double MAX_BOOST_SPEED = 2.6;

    // ---------------------------------------------------------------- Landen
    /** Valschade tot deze hoogte vangen de laarzen helemaal op (in blokken). */
    public static final float SAFE_FALL = 40.0f;
    /** Wat het opvangen van een val kost. */
    public static final int LANDING_COST = 200;

    // ---------------------------------------------------------------- Repulsor
    /** Schade van één repulsorstoot (7 = 3,5 hartje). */
    public static final float REPULSOR_DAMAGE = 7.0f;
    /** Wat één stoot kost. */
    public static final int REPULSOR_COST = 200;
    /** Wachttijd tussen twee stoten (in ticks). 20 ticks = 1 seconde. */
    public static final int REPULSOR_COOLDOWN = 20;
    /** Hoe ver de stoot reikt (in blokken). */
    public static final double REPULSOR_RANGE = 24.0;
    /** Hoe hard hij wegduwt wat hij raakt. */
    public static final double REPULSOR_KNOCKBACK = 0.7;
    /** Hoe hard de stoot jou zelf naar achteren duwt. Klein houden: anders schiet je jezelf weg. */
    public static final double REPULSOR_RECOIL = 0.04;
    /** Hoe ver naast de straal je nog geraakt wordt (in blokken). */
    public static final double REPULSOR_WIDTH = 1.2;
}
