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
    /** Hoeveel sneller je vliegt dan normaal (1.0 = zoals creative). */
    public static final float FLY_SPEED = 1.6f;

    // ---------------------------------------------------------------- Landen
    /** Valschade tot deze hoogte vangen de laarzen helemaal op (in blokken). */
    public static final float SAFE_FALL = 40.0f;
    /** Wat het opvangen van een val kost. */
    public static final int LANDING_COST = 200;

    // ---------------------------------------------------------------- Repulsor
    /** Schade van één repulsorstoot (12 = 6 hartjes). */
    public static final float REPULSOR_DAMAGE = 12.0f;
    /** Wat één stoot kost. */
    public static final int REPULSOR_COST = 500;
    /** Wachttijd tussen twee stoten (in ticks). 20 ticks = 1 seconde. */
    public static final int REPULSOR_COOLDOWN = 20;
    /** Hoe ver de stoot reikt (in blokken). */
    public static final double REPULSOR_RANGE = 24.0;
    /** Hoe hard hij wegduwt wat hij raakt. */
    public static final double REPULSOR_KNOCKBACK = 1.4;
    /** Hoe ver naast de straal je nog geraakt wordt (in blokken). */
    public static final double REPULSOR_WIDTH = 1.2;
}
