package com.senne.starktech.edith;

/**
 * Alle getallen die bepalen hoe sterk (of zwak) E.D.I.T.H. is.
 *
 * Wil je de mod makkelijker of moeilijker maken? Pas dan hier de getallen aan,
 * bouw de mod opnieuw (gradlew build) en start het spel opnieuw.
 *
 * Energie wordt gemeten in "SE" (Stark Energy).
 * Tijd wordt gemeten in "ticks": 20 ticks = 1 seconde.
 */
public final class EdithBalance {
    private EdithBalance() {}

    // ---------------------------------------------------------------- Batterij
    /** Hoeveel energie de bril kan opslaan zonder batterij-upgrades. */
    @Tunable(section = "glasses", comment = "Energy the glasses hold without battery upgrades (SE)")
    public static int BASE_CAPACITY = 10_000;
    /** Hoeveel extra opslag elke batterij-upgrade geeft. */
    @Tunable(section = "glasses", comment = "Extra energy per battery upgrade (SE)")
    public static int CAPACITY_PER_BATTERY_UPGRADE = 10_000;
    /** Hoeveel batterij-upgrades er maximaal in één bril passen. */
    public static final int MAX_BATTERY_UPGRADES = 3;

    // ---------------------------------------------------------------- Verbruik per seconde
    /** Wat de HUD zelf kost zolang je de bril draagt. */
    @Tunable(section = "glasses", comment = "Energy per second while the HUD is on")
    public static int DRAIN_HUD = 1;
    /** Extra kosten per seconde als nachtzicht aan staat. */
    @Tunable(section = "glasses", comment = "Energy per second for night vision")
    public static int DRAIN_NIGHT_VISION = 4;
    /** Extra kosten per seconde als "vijanden door muren" aan staat. */
    @Tunable(section = "glasses", comment = "Energy per second for the threat scan")
    public static int DRAIN_THREAT_SCAN = 3;
    /** Extra kosten per seconde als doelwit-analyse aan staat. */
    @Tunable(section = "glasses", comment = "Energy per second for target analysis")
    public static int DRAIN_TARGET_ANALYSIS = 1;

    // ---------------------------------------------------------------- Ertsen-scanner
    /** Kosten van één ertsen-scan. */
    @Tunable(section = "scanner", comment = "Energy cost of an ore scan")
    public static int ORE_SCAN_COST = 400;
    /** Wachttijd tussen twee scans (in ticks). 200 ticks = 10 seconden. */
    @Tunable(section = "scanner", comment = "Wait between two ore scans (ticks, 20 = 1 second)")
    public static int ORE_SCAN_COOLDOWN = 200;
    /** Hoe lang de gevonden ertsen zichtbaar blijven (in ticks). */
    @Tunable(section = "scanner", comment = "How long ore markers stay visible (ticks)")
    public static int ORE_SCAN_DURATION = 300;
    /** Scan-straal in blokken, per scanner-upgrade niveau (0, 1, 2, 3). */
    public static final int[] ORE_SCAN_RADIUS = {12, 18, 24, 32};

    // ---------------------------------------------------------------- Dreigingsscan
    /** Tot hoe ver (in blokken) vijanden door muren heen oplichten, per scanner-niveau. */
    public static final int[] THREAT_RADIUS = {24, 32, 48, 64};
    /** Maximale scanner-upgrades. */
    public static final int MAX_SCANNER_UPGRADES = 3;

    // ---------------------------------------------------------------- Doelwit-analyse
    /** Tot hoe ver je een doelwit kunt analyseren (in blokken). */
    @Tunable(section = "glasses", comment = "How far away you can analyse and mark targets (blocks)")
    public static double TARGET_RANGE = 64.0;

    // ---------------------------------------------------------------- Zoom
    /** Hoeveel keer de bril inzoomt. */
    public static final float ZOOM_FACTOR = 4.0f;

    // ---------------------------------------------------------------- Laadstation
    /** Hoeveel energie het laadstation zelf kan opslaan (max 32000, anders klopt de GUI niet). */
    @Tunable(section = "power", comment = "Energy a charging station can store")
    public static int STATION_CAPACITY = 30_000;
    /**
     * Energie uit één stuk redstone.
     *
     * Bewust weinig: een volle bril kost zo ongeveer twee stapels redstone. Redstone is
     * bedoeld als noodvoorraad die je in je zak hebt, niet als je gewone stroom. Daarvoor
     * zet je een zonnepaneel of een warmtegenerator neer.
     */
    @Tunable(section = "power", comment = "Energy from one redstone dust in a charging station")
    public static int ENERGY_PER_REDSTONE = 50;
    /** Energie uit één redstone-blok (9 redstone + een beetje bonus). */
    @Tunable(section = "power", comment = "Energy from one redstone block in a charging station")
    public static int ENERGY_PER_REDSTONE_BLOCK = 480;
    /** Hoeveel energie het station per tick naar de bril stuurt. */
    @Tunable(section = "power", comment = "Energy per tick a charging station puts into the glasses")
    public static int STATION_CHARGE_RATE = 50;
    /**
     * Hoeveel energie het station per tick uit het stroomnet (Stark-kabels) mag halen. Ruim meer
     * dan één zonnepaneel maakt, zodat een heel veld panelen ook echt sneller laadt.
     */
    @Tunable(section = "power", comment = "Energy per tick a charging station draws from the grid")
    public static int STATION_GRID_RATE = 40;
    /**
     * Zijn eigen voorraad vult het station ook uit het net, maar alleen met wat over is: de
     * generatoren houden altijd dit deel van wat erin past achter voor de rest van het net
     * (holotafel, laserpoorten, koepel). En langzaam: zoveel per tick.
     */
    public static final float STATION_GRID_RESERVE = 0.5f;
    public static final int STATION_STORE_RATE = 10;
    // ---------------------------------------------------------------- Zonnepaneel en warmte
    /** Hoeveel een zonnepaneel kan opslaan. */
    @Tunable(section = "power", comment = "Energy a solar panel can store")
    public static int SOLAR_CAPACITY = 12_000;
    /** Energie per tick bij een zon recht boven je (minder bij regen, 's nachts niets). */
    @Tunable(section = "power", comment = "Energy per tick a solar panel makes in full sun")
    public static int SOLAR_RATE = 6;
    /** Hoeveel een warmtegenerator kan opslaan. */
    @Tunable(section = "power", comment = "Energy a heat generator can store")
    public static int HEAT_CAPACITY = 20_000;
    /** Energie per tick zolang er brandstof brandt. */
    @Tunable(section = "power", comment = "Energy per tick a heat generator makes while burning fuel")
    public static int HEAT_RATE_FUEL = 14;
    /** Energie per tick per heet blok dat tegen hem aan staat (lava, vuur, magma...). */
    @Tunable(section = "power", comment = "Energy per tick per hot block (lava, fire, magma) next to a heat generator")
    public static int HEAT_RATE_AMBIENT = 3;
    /** Meer dan zoveel hete buren tellen niet meer mee. */
    public static final int HEAT_MAX_NEIGHBOURS = 8;

    /** Hoe lang het installeren van een upgrade duurt (in ticks). */
    @Tunable(section = "power", comment = "How long installing an upgrade in the charging station takes (ticks)")
    public static int UPGRADE_INSTALL_TIME = 60;

    /** Batterij-percentage waarop E.D.I.T.H. waarschuwt. */
    public static final float LOW_BATTERY_WARNING = 0.10f;

    // ================================================================ Fase 2: drones en satelliet

    // ---------------------------------------------------------------- Doelwitten markeren
    /** Hoeveel doelwitten je tegelijk kunt markeren. */
    @Tunable(section = "glasses", comment = "How many targets you can mark at once")
    public static int MAX_MARKED_TARGETS = 10;

    // ---------------------------------------------------------------- Aanvalsdrones (de zwerm)
    /** Kosten om een zwerm te lanceren. */
    @Tunable(section = "drones", comment = "Energy cost of launching a drone swarm")
    public static int SWARM_COST = 1_500;
    /** Wachttijd tussen twee zwermen (in ticks). 600 ticks = 30 seconden. */
    @Tunable(section = "drones", comment = "Wait between two swarms (ticks)")
    public static int SWARM_COOLDOWN = 600;
    /** Hoe lang een zwerm blijft (in ticks). 1200 ticks = 1 minuut. */
    @Tunable(section = "drones", comment = "How long a swarm stays before flying home (ticks)")
    public static int SWARM_LIFETIME = 1_200;

    // Let op: HOEVEEL drones er in een zwerm of escorte zitten staat in EdithConfig,
    // want dat kan de eigenaar van de wereld zelf instellen (serverconfig/starktech-server.toml).

    // ---------------------------------------------------------------- Eén drone op één doelwit
    /** Kosten om één drone op één doelwit af te sturen. */
    @Tunable(section = "drones", comment = "Energy cost of sending a single drone")
    public static int STRIKE_DRONE_COST = 400;
    /** Wachttijd tussen twee losse drones (in ticks). 160 ticks = 8 seconden. */
    @Tunable(section = "drones", comment = "Wait between two single drones (ticks)")
    public static int STRIKE_DRONE_COOLDOWN = 160;
    /** Hoe lang die ene drone blijft jagen (in ticks). 1800 ticks = 90 seconden. */
    @Tunable(section = "drones", comment = "How long a single drone hunts before flying home (ticks)")
    public static int STRIKE_DRONE_LIFETIME = 1_800;
    /** Tot hoe ver je een los doelwit kunt aanwijzen (in blokken). */
    @Tunable(section = "drones", comment = "How far away you can send a single drone (blocks)")
    public static double STRIKE_DRONE_RANGE = 96.0;

    // ---------------------------------------------------------------- Beschermende drones
    /** Extra energie per seconde zolang de beschermende drones aan staan. */
    @Tunable(section = "drones", comment = "Energy per second while escort drones are on")
    public static int DRAIN_GUARD_DRONES = 6;
    /** Tot hoe ver van jou de beschermende drones vijanden aanvallen (in blokken). */
    @Tunable(section = "drones", comment = "How far from you escort drones attack threats (blocks)")
    public static double GUARD_RANGE = 12.0;

    // ---------------------------------------------------------------- Drones algemeen
    /** Levenspunten van één drone (20 = 10 hartjes). */
    public static final double DRONE_HEALTH = 12.0;
    /** Schade van één laserschot (2 = 1 hartje). */
    @Tunable(section = "drones", comment = "Damage of one drone laser shot")
    public static float DRONE_LASER_DAMAGE = 4.0f;
    /** Tijd tussen twee schoten van dezelfde drone (in ticks). */
    @Tunable(section = "drones", comment = "Ticks between two drone laser shots")
    public static int DRONE_FIRE_INTERVAL = 15;
    /** Tot hoe ver een drone kan schieten (in blokken). */
    @Tunable(section = "drones", comment = "How far a drone shoots (blocks)")
    public static double DRONE_ATTACK_RANGE = 16.0;

    // ---------------------------------------------------------------- Satelliet
    /** Kosten van een precisie-straal (geen blokschade). */
    @Tunable(section = "satellite", comment = "Energy cost of a precision beam")
    public static int SATELLITE_COST_PRECISION = 2_000;
    /** Kosten van een bombardement (meerdere inslagen, krater). */
    @Tunable(section = "satellite", comment = "Energy cost of a bombardment")
    public static int SATELLITE_COST_BOMBARDMENT = 5_000;
    /** Kosten van de schuivende laser. */
    @Tunable(section = "satellite", comment = "Energy cost of a sweeping laser")
    public static int SATELLITE_COST_SWEEP = 3_500;
    /** Wachttijd tussen twee aanvallen (in ticks). 1800 ticks = 90 seconden. */
    @Tunable(section = "satellite", comment = "Wait between two satellite strikes without recharge upgrades (ticks)")
    public static int SATELLITE_COOLDOWN = 1_800;
    /** Hoe ver weg je een satelliet-aanval kunt richten (in blokken). */
    @Tunable(section = "satellite", comment = "How far away the satellite can aim without targeting upgrades (blocks)")
    public static double SATELLITE_RANGE = 500.0;

    // --- Precisie
    /** Binnen deze straal (blokken) raakt de precisie-straal alles. */
    public static final double PRECISION_RADIUS = 3.5;
    /** Schade van de precisie-straal (30 = 15 hartjes). */
    @Tunable(section = "satellite", comment = "Damage of the precision beam")
    public static float PRECISION_DAMAGE = 30.0f;

    // --- Bombardement
    /** Hoeveel inslagen een bombardement heeft. */
    @Tunable(section = "satellite", comment = "Number of impacts in a bombardment")
    public static int BOMBARDMENT_SHOTS = 5;
    /** Ticks tussen twee inslagen. */
    public static final int BOMBARDMENT_INTERVAL = 12;
    /** Hoe ver de inslagen rond het doel verspreid liggen (in blokken). */
    public static final double BOMBARDMENT_SPREAD = 6.0;

    // --- Schuivende laser
    /** Hoe lang de laser over de grond schuift (in ticks). 70 ticks = 3,5 seconde. */
    public static final int SWEEP_TICKS = 70;
    /**
     * Het beginpunt dat je met de eerste K kiest, blijft zo lang bewaard (ticks): 600 = een halve
     * minuut. Daarna begint een nieuwe K weer bij het beginpunt. De laser zelf gaat in de hele
     * schuiftijd van het begin- naar het eindpunt, hoe ver die ook uit elkaar liggen.
     */
    public static final int SWEEP_START_TIMEOUT = 600;
    /** Hoe breed de laser raakt (in blokken). */
    public static final double SWEEP_RADIUS = 2.5;
    /** Schade per treffer van de schuivende laser. */
    @Tunable(section = "satellite", comment = "Damage per hit of the sweeping laser")
    public static float SWEEP_DAMAGE = 8.0f;

    // --- Schokgolf
    /** Tot hoe ver de schokgolf wezens wegduwt (in blokken). */
    public static final double SHOCKWAVE_RADIUS = 10.0;
    /** Hoe hard de schokgolf duwt. */
    public static final double SHOCKWAVE_POWER = 1.2;
    /** Minimale afstand tot het doel, zodat je jezelf niet opblaast. */
    @Tunable(section = "satellite", comment = "Closest distance to yourself you can aim the satellite (blocks)")
    public static double SATELLITE_MIN_DISTANCE = 10.0;
    /** Hoe lang de satelliet oplaadt voordat hij inslaat (in ticks). 60 ticks = 3 seconden. */
    @Tunable(section = "satellite", comment = "How long the satellite aims before it fires (ticks)")
    public static int SATELLITE_CHARGE_TIME = 60;
    /** Kracht van de explosie (TNT = 4). */
    @Tunable(section = "satellite", comment = "Explosion power of a bombardment impact (a creeper is 3)")
    public static float SATELLITE_EXPLOSION_POWER = 5.0f;
    /**
     * Mag de satelliet blokken kapotmaken? Als dit true is, volgt het de spelregel
     * "mobGriefing" (staat die uit, dan blijven blokken toch heel).
     */
    @Tunable(section = "satellite", comment = "Do satellite explosions break blocks?")
    public static boolean SATELLITE_BREAKS_BLOCKS = true;

    // ================================================================ Fase 3: navigatie, drone-camera, comms

    // ---------------------------------------------------------------- Navigatie
    /** Hoeveel waypoints je kunt opslaan. */
    public static final int MAX_WAYPOINTS = 20;
    /** Maximale lengte van de naam van een waypoint. */
    public static final int MAX_WAYPOINT_NAME = 24;
    /** Binnen deze afstand (in blokken) ben je "aangekomen". */
    public static final double ARRIVAL_DISTANCE = 4.0;

    // ---------------------------------------------------------------- Drone-camera (verkenner)
    /** Kosten om de verkenningsdrone te lanceren. */
    @Tunable(section = "recon", comment = "Energy cost of launching the drone camera")
    public static int SCOUT_COST = 200;
    /** Extra energie per seconde zolang je door de drone kijkt. */
    @Tunable(section = "recon", comment = "Energy per second while you look through the drone camera")
    public static int DRAIN_SCOUT = 8;
    /** Hoe ver de drone van je lichaam mag vliegen (in blokken). */
    @Tunable(section = "recon", comment = "How far the drone camera may fly from you (blocks)")
    public static double SCOUT_RANGE = 64.0;
    /** Vliegsnelheid (blokken per tick; 0.35 = 7 blokken per seconde). */
    public static final double SCOUT_SPEED = 0.35;
    /** Vliegsnelheid met sprinten. */
    public static final double SCOUT_BOOST_SPEED = 0.7;

    // ---------------------------------------------------------------- Spionagedrone
    /** Kosten om een spionagedrone te sturen. */
    @Tunable(section = "recon", comment = "Energy cost of a spy drone")
    public static int SPY_COST = 800;
    /** Wachttijd tussen twee spionagedrones (in ticks). 400 ticks = 20 seconden. */
    @Tunable(section = "recon", comment = "Wait between two spy drones (ticks)")
    public static int SPY_COOLDOWN = 400;
    /** Hoe lang de spionagedrone iemand volgt (in ticks). 12000 ticks = 10 minuten. */
    @Tunable(section = "recon", comment = "How long a spy drone follows its target (ticks)")
    public static int SPY_DURATION = 12_000;
    /** Hoe vaak hij een rapport stuurt (in ticks). 600 ticks = elke 30 seconden. */
    public static final int SPY_REPORT_INTERVAL = 600;
    /** Tot hoe ver je een doelwit kunt aanwijzen voor de spionagedrone (in blokken). */
    @Tunable(section = "recon", comment = "How far away you can send a spy drone (blocks)")
    public static double SPY_RANGE = 96.0;
    /** Hoe ver achter het doelwit de drone blijft hangen (in blokken). */
    public static final double SPY_FOLLOW_DISTANCE = 4.0;
    /** Hoe hoog boven het doelwit de drone blijft hangen (in blokken). */
    public static final double SPY_FOLLOW_HEIGHT = 5.0;
    /** Extra energie per seconde zolang je door de spionagedrone meekijkt. */
    @Tunable(section = "recon", comment = "Energy per second while you watch through the spy drone")
    public static int DRAIN_SPY_VIEW = 4;
    /** Tot hoe ver je nog live door de spionagedrone kunt kijken (in blokken). */
    public static final double SPY_VIEW_RANGE = 110.0;

    // ---------------------------------------------------------------- Transportdrones
    /** Kosten om je te laten oppikken (of om een vracht te versturen). */
    @Tunable(section = "transport", comment = "Energy cost of a transport (ride, autopilot, cargo, carry)")
    public static int TRANSPORT_COST = 600;
    /** Wachttijd tussen twee ritten (in ticks). 200 ticks = 10 seconden. */
    @Tunable(section = "transport", comment = "Wait between two transports (ticks)")
    public static int TRANSPORT_COOLDOWN = 200;
    /** Energie per seconde zolang je door drones gedragen wordt. */
    @Tunable(section = "transport", comment = "Energy per second while drones carry you")
    public static int DRAIN_TRANSPORT = 14;
    /** Vliegsnelheid als je zelf stuurt (blokken per tick; 0.45 = 9 blokken per seconde). */
    public static final double TRANSPORT_SPEED = 0.45;
    /** Vliegsnelheid met sprinten. */
    public static final double TRANSPORT_BOOST_SPEED = 0.85;
    /** Hoe snel de drone met je meezakt als je landt (blokken per tick). */
    public static final double TRANSPORT_LAND_SPEED = 0.35;
    /** Zo ver kun je een illusie van je af neerzetten (in blokken). */
    public static final double ILLUSION_AIM_RANGE = 48.0;
    /** Hoe hoog boven de grond de autopiloot vliegt (in blokken). */
    public static final int TRANSPORT_CRUISE_HEIGHT = 14;
    /** Hoe lang een rit of levering maximaal duurt (in ticks). 6000 ticks = 5 minuten. */
    @Tunable(section = "transport", comment = "Longest a transport may take (ticks)")
    public static int TRANSPORT_MAX_TICKS = 6_000;
    /** Hoeveel stapels een vrachtdrone kan meenemen. */
    public static final int CARGO_SLOTS = 27;

    // ---------------------------------------------------------------- Cloaking / illusies
    /** Kosten om een illusie te starten. */
    @Tunable(section = "illusions", comment = "Energy cost of an illusion")
    public static int ILLUSION_COST = 1_200;
    /** Hoeveel illusies je tegelijk kunt projecteren. */
    @Tunable(section = "illusions", comment = "How many illusions one player can run at once")
    public static int MAX_ILLUSIONS = 4;
    /** Hoe groot het veld van één cloakingdrone is (in blokken). */
    @Tunable(section = "illusions", comment = "Radius of a cloak field (blocks)")
    public static double ILLUSION_RADIUS = 16.0;
    /** Hoe lang een cloakingdrone blijft hangen (in ticks). 6000 ticks = 5 minuten. */
    @Tunable(section = "illusions", comment = "How long an illusion lasts (ticks)")
    public static int ILLUSION_LIFETIME = 6_000;
    /** De maat van een valse muur. */
    public static final int ILLUSION_WALL_WIDTH = 9;
    public static final int ILLUSION_WALL_HEIGHT = 5;
    /** Hoeveel nep-wezens er in een nep-leger staan. */
    @Tunable(section = "illusions", comment = "How many fake mobs a fake army has")
    public static int ILLUSION_ARMY_SIZE = 8;
    /** Hoe ver een E.M.P.-puls reikt (in blokken). */
    @Tunable(section = "illusions", comment = "Radius of an E.M.P. pulse (blocks)")
    public static double EMP_RADIUS = 24.0;
    /** Hoe lang illusies plat liggen na een E.M.P. (in ticks). 600 ticks = 30 seconden. */
    @Tunable(section = "illusions", comment = "How long an E.M.P. jams illusions (ticks)")
    public static int EMP_JAM_TICKS = 600;
    /** Wachttijd van de E.M.P.-zender (in ticks). 300 ticks = 15 seconden. */
    @Tunable(section = "illusions", comment = "Wait between two E.M.P. pulses (ticks)")
    public static int EMP_COOLDOWN = 300;

    // ---------------------------------------------------------------- Rayshield
    /** Extra energie per seconde zolang je persoonlijke schild aan staat. */
    @Tunable(section = "shields", comment = "Energy per second while the personal rayshield is on")
    public static int DRAIN_SHIELD = 5;
    /** Hoeveel energie één punt tegengehouden schade kost (2 schade = 1 hartje). */
    @Tunable(section = "shields", comment = "Energy the personal shield uses per point of damage it stops")
    public static int SHIELD_COST_PER_DAMAGE = 120;
    /** Hoe lang een laserwand maximaal wordt (in blokken). */
    public static final int LASER_MAX_LENGTH = 16;
    /** Schade als je de laserwand aanraakt (4 = 2 hartjes). */
    @Tunable(section = "shields", comment = "Damage from touching a laser wall")
    public static float LASER_WALL_DAMAGE = 4.0f;
    /**
     * Hoeveel stroom een laserzender per tick verbruikt zolang zijn wand aan staat. Een paar
     * zenders (twee) kost dus 2 per tick: een zonnepaneel houdt er overdag een paar aan.
     */
    @Tunable(section = "power", comment = "Energy per tick a laser wall emitter uses while on")
    public static int LASER_UPKEEP = 1;
    /** Hoeveel stroom een holotafel per tick verbruikt. */
    @Tunable(section = "power", comment = "Energy per tick a holo table uses while on")
    public static int HOLO_UPKEEP = 1;

    // ---------------------------------------------------------------- Hack player
    /** Hoeveel spelers je tegelijk kunt hacken. */
    @Tunable(section = "tech", comment = "How many players you can hack at once")
    public static int MAX_HACKS = 3;
    /** Energie per seconde per gehackte speler. */
    @Tunable(section = "tech", comment = "Energy per second per hacked player")
    public static int DRAIN_HACK = 3;
    /** Tot hoe ver je een gehackte speler kunt "afluisteren" (in blokken). */
    @Tunable(section = "tech", comment = "How far away you can hack a player (blocks)")
    public static double HACK_RANGE = 64.0;
    /**
     * Hoe recht je naar hem moet kijken (1 = precies in het midden van je beeld, 0 = naast je).
     * 0,45 komt ongeveer overeen met "hij staat in je beeld".
     */
    public static final double HACK_VIEW_DOT = 0.45;
    /** Hoe lang een onderschept bericht boven zijn hoofd blijft staan (in milliseconden). */
    public static final long HACK_BUBBLE_MILLIS = 9_000;

    // ---------------------------------------------------------------- Redstone-relais
    /** Hoeveel relais je kunt aanmelden. */
    @Tunable(section = "tech", comment = "How many relays and switchable devices one player can register")
    public static int MAX_RELAYS = 64;
    /** Maximale lengte van de naam (en van de groepsnaam) van een relais. */
    public static final int MAX_RELAY_NAME = 24;
    /** Wat één schakeling kost. */
    @Tunable(section = "tech", comment = "Energy cost of switching a relay remotely")
    public static int RELAY_COST = 5;
    /** Hoe lang een puls duurt (in ticks). 20 ticks = 1 seconde. */
    public static final int RELAY_PULSE_TICKS = 20;

    // ---------------------------------------------------------------- Comms
    /** Maximale lengte van een bericht. */
    public static final int MAX_MESSAGE_LENGTH = 100;
    /** Wachttijd tussen twee berichten (in ticks), tegen spam. */
    public static final int MESSAGE_COOLDOWN = 20;
    /** Wachttijd tussen twee pings (in ticks). */
    public static final int PING_COOLDOWN = 40;
    /** Hoe lang een ping zichtbaar blijft (in ticks). 300 ticks = 15 seconden. */
    public static final int PING_DURATION = 300;
    /** Tot hoe ver weg anderen je ping zien (in blokken). */
    @Tunable(section = "tech", comment = "How far away other E.D.I.T.H. wearers see your pings (blocks)")
    public static double PING_RANGE = 512.0;
    /** Hoe ver je een ping kunt plaatsen (waar je naar kijkt). */
    public static final double PING_PLACE_RANGE = 128.0;
}
