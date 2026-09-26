package com.senne.starktech.suit;

import com.senne.starktech.edith.Tunable;

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
    @Tunable(section = "suit", comment = "Share of damage each powered suit piece stops (0-1; three pieces = three times this)")
    public static float POWERED_REDUCTION_PER_PIECE = 0.20f;
    /** Wat één punt tegengehouden schade kost (2 schade = 1 hartje). */
    @Tunable(section = "suit", comment = "Energy per point of damage the suit stops")
    public static int REDUCTION_COST_PER_DAMAGE = 60;

    // ---------------------------------------------------------------- Vliegen
    /** Energie per seconde als je stilhangt. */
    @Tunable(section = "suit", comment = "Energy per second while hovering")
    public static int DRAIN_HOVER = 12;
    /** Energie per seconde als je vooruit vliegt. */
    @Tunable(section = "suit", comment = "Energy per second while flying")
    public static int DRAIN_FLY = 26;
    /** Energie per seconde met de naverbrander (sprinttoets). */
    @Tunable(section = "suit", comment = "Energy per second with the afterburner")
    public static int DRAIN_BOOST = 45;
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
    @Tunable(section = "suit", comment = "Falls up to this height do no damage with the boots on (blocks)")
    public static float SAFE_FALL = 40.0f;
    /** Wat het opvangen van een val kost. */
    @Tunable(section = "suit", comment = "Energy cost of catching a fall")
    public static int LANDING_COST = 200;

    // ---------------------------------------------------------------- Landingsplatform
    /** Zoveel energie per seconde laadt een landingsplatform in je bril (als het net het heeft). */
    @Tunable(section = "suit", comment = "Energy per second a landing pad puts into your glasses")
    public static int PAD_CHARGE_PER_SECOND = 400;
    /** Zoveel duurzaamheid per seconde repareert hij aan elk stuk harnas... */
    @Tunable(section = "suit", comment = "Durability per second a landing pad repairs on each suit piece")
    public static int PAD_REPAIR_PER_SECOND = 2;
    /** ...en dat kost zoveel stroom per punt. */
    @Tunable(section = "suit", comment = "Energy per point of durability a landing pad repairs")
    public static int PAD_REPAIR_ENERGY = 10;

    // ---------------------------------------------------------------- Repulsor
    /** Schade van één repulsorstoot (7 = 3,5 hartje). */
    @Tunable(section = "suit", comment = "Damage of one repulsor blast")
    public static float REPULSOR_DAMAGE = 7.0f;
    /** Wat één stoot kost. */
    @Tunable(section = "suit", comment = "Energy cost of one repulsor blast")
    public static int REPULSOR_COST = 200;
    /** Wachttijd tussen twee stoten (in ticks). 20 ticks = 1 seconde. */
    @Tunable(section = "suit", comment = "Ticks between two repulsor blasts")
    public static int REPULSOR_COOLDOWN = 20;
    /** Hoe ver de stoot reikt (in blokken). */
    @Tunable(section = "suit", comment = "How far a repulsor blast reaches (blocks)")
    public static double REPULSOR_RANGE = 24.0;
    /** Hoe hard hij wegduwt wat hij raakt. */
    public static final double REPULSOR_KNOCKBACK = 0.7;
    /** Hoe hard de stoot jou zelf naar achteren duwt. Klein houden: anders schiet je jezelf weg. */
    public static final double REPULSOR_RECOIL = 0.04;
    /** Hoe ver naast de straal je nog geraakt wordt (in blokken). */
    public static final double REPULSOR_WIDTH = 1.2;

    // ---------------------------------------------------------------- Mini-raketten
    /** Hoeveel raketten er in één salvo zitten. */
    @Tunable(section = "suit", comment = "Number of mini rockets in a salvo")
    public static int ROCKET_SALVO = 6;
    /** Wat een heel salvo kost. */
    @Tunable(section = "suit", comment = "Energy cost of a mini rocket salvo")
    public static int ROCKET_COST = 900;
    /** Wachttijd tussen twee salvo's (in ticks): 12 seconden. */
    @Tunable(section = "suit", comment = "Reload time of the rocket pack (ticks)")
    public static int ROCKET_COOLDOWN = 240;
    /** Zoveel ticks tussen twee raketten van hetzelfde salvo. */
    public static final int ROCKET_INTERVAL = 2;
    /** Hoe ver de raketten doelwitten zoeken en hoe ver ze vliegen zonder doelwit (in blokken). */
    @Tunable(section = "suit", comment = "How far away mini rockets pick targets (blocks)")
    public static double ROCKET_RANGE = 48.0;
    /** Schade voor wat de raket raakt (5 = 2,5 hartje). */
    @Tunable(section = "suit", comment = "Damage of one mini rocket hit")
    public static float ROCKET_DAMAGE = 5.0f;
    /** Schade voor wie vlak naast de inslag staat. */
    @Tunable(section = "suit", comment = "Damage to others right next to a mini rocket impact")
    public static float ROCKET_SPLASH_DAMAGE = 2.0f;
    /** Tot zover van de inslag krijg je die schade (in blokken). */
    public static final double ROCKET_SPLASH_RADIUS = 1.8;
    /** Snelheid bij het wegschieten en de hoogste snelheid (blokken per tick). */
    public static final double ROCKET_START_SPEED = 0.45;
    public static final double ROCKET_MAX_SPEED = 1.6;
    /** Zoveel snelheid komt er elke tick bij. */
    public static final double ROCKET_ACCELERATION = 0.08;
    /** Zo lang (ticks) vliegt een raket eerst rechtdoor voor hij gaat bijsturen: het salvo waaiert uit. */
    public static final int ROCKET_ARM_TICKS = 5;
    /** Hoe scherp een raket bijstuurt (0 = niet, 1 = meteen recht op het doel). */
    public static final double ROCKET_TURN = 0.28;
    /** Dichter dan dit bij zijn doel (in blokken) stuurt hij steeds scherper, tot recht erop. */
    public static final double ROCKET_SHARP_TURN_DISTANCE = 6.0;
    /** Na zoveel ticks ontploft een raket vanzelf (5 seconden). */
    public static final int ROCKET_LIFETIME = 100;
}
