package com.senne.starktech.config;

/*
 * =================================================================================================
 *  ALLE GETALLEN VAN STARK TECH, OP ÉÉN PLEK
 * =================================================================================================
 *
 * Hier staat elk getal dat bepaalt hoe de mod speelt: wat iets kost, hoeveel schade het doet, hoe
 * ver het reikt, hoe lang je moet wachten, en hoeveel stroom elk blok maakt of gebruikt. De rest van
 * de mod leest de getallen rechtstreeks hier, bijvoorbeeld StarkConfig.Satellite.COST_SWEEP.
 *
 * HET CONFIGBESTAND
 *   Uit dit bestand maakt de mod vanzelf config/starktech.toml (in .minecraft, of op een server in de
 *   map van de server). Daar staat alles in, met dezelfde hoofdstukken; de getallen hieronder zijn de
 *   standaardwaarden. Pas je het .toml-bestand aan, dan hoef je niets opnieuw te bouwen: het spel
 *   leest het opnieuw zodra je het opslaat, ook terwijl je speelt. Een server stuurt het naar elke
 *   speler die binnenkomt, zodat het menu en de HUD dezelfde getallen laten zien.
 *   - LET OP: bestaat het .toml-bestand al, dan houdt het zijn eigen waarden. Verander je hieronder een
 *     standaardwaarde, pas dan ook run/config/starktech.toml aan (of gooi het weg: dan maakt het spel
 *     een nieuw bestand met de nieuwe standaardwaarden). Boven elke regel in het .toml-bestand staat
 *     "Default: ...": daar zie je de standaard uit dit bestand.
 *   - Eén wereld anders? Kopieer het bestand naar de map serverconfig in die wereld
 *     (saves/<naam van de wereld>/serverconfig/) en pas het daar aan: die kopie gaat voor.
 *   - Persoonlijke dingen (je volume, de stem, Spotify, de rand van zones) staan hier niet, maar in
 *     config/starktech-client.toml (EdithClientConfig): die kiest elke speler voor zichzelf.
 *
 * EENHEDEN
 *   tijd      in ticks: 20 ticks = 1 seconde (600 = een halve minuut, 1200 = een minuut)
 *   energie   in SE (Stark Energy). Bij de bril gaat het uit de batterij van de bril; bij blokken
 *             (hoofdstuk Power) uit het stroomnet (de Stark-kabels).
 *   afstand   in blokken
 *   schade    in halve hartjes: 2 = 1 hartje, 20 = een volle levensbalk
 *   delen     0.25 = 25%
 *
 * KOMMAGETALLEN
 *   Elk getal dat je kunt instellen is een kommagetal (float), dus 0.25 mag overal. Energie telt
 *   precies mee: 0.25 is echt 0.25, ook in de batterij van de bril. Tijden in ticks en aantallen (drones,
 *   tekens, niveaus) rondt de mod af naar het dichtstbijzijnde hele getal (12.6 ticks = 13), want een
 *   halve tick of een halve drone bestaat niet. Seconden en minuten rekent hij eerst om naar ticks, dus
 *   0.5 seconde is echt 10 ticks. Alleen de getallen met "final" (VAST, zie hieronder) zijn hele getallen.
 *   In de schermen van de generator, de koepel en de Arc Lamp zie je stroom per tick, seconde, minuut of
 *   uur: klik op de knop "/tick" (zie client/PowerUnit).
 *
 * PER NIVEAU
 *   Sommige getallen zijn een rijtje: één getal per niveau. Bijvoorbeeld de wachttijd van de
 *   satelliet voor upgrade "Recharge" niveau 0 tot en met 10. In het .toml-bestand ziet dat er zo uit:
 *       cooldown = [1800, 1692, 1584, ...]
 *   Het aantal getallen moet blijven kloppen. Klopt het niet, dan zet NeoForge het standaardrijtje terug.
 *
 * VAST
 *   Een getal met "final" ervoor staat NIET in het .toml-bestand. Het spel heeft het al nodig
 *   voordat er een wereld laadt (de levens van een mob, het pantser van het harnas), of het bepaalt
 *   hoe een scherm eruitziet. Zo'n getal verander je alleen hier, en daarna bouw je de mod opnieuw.
 *
 * EEN NIEUW GETAL
 *   Zet het in het juiste hoofdstuk, zonder "final", met @Tunable("Engelse uitleg") erboven. Meer
 *   hoeft niet: het staat dan vanzelf in het .toml-bestand (dat regelt StarkConfigLoader).
 *
 * INHOUD
 *   Rules ........ wat mag er in deze wereld (hacken, zakkenrollen, raids)
 *   Glasses ...... de bril: batterij, upgrades en verbruik per seconde
 *   Scanner ...... ertsen-scanner en vijanden door muren
 *   Power ........ STROOM: wat elk blok opwekt, opslaat en van het net gebruikt
 *   Drones ....... je eigen drones: zwerm, losse drone, escorte, arsenaal
 *   Satellite .... de satelliet, met alles per upgrade-niveau
 *   Recon ........ drone-camera en spionagedrone
 *   Transport .... vervoer door drones
 *   Illusions .... illusies en de E.M.P.
 *   Shields ...... persoonlijk schild en laserwanden
 *   Dome ......... de schildkoepel
 *   Hack ......... andere spelers hacken (berichten onderscheppen)
 *   Relays ....... redstone op afstand schakelen
 *   Comms ........ berichten en pings
 *   Navigation ... waypoints
 *   Holo ......... de holotafel
 *   Suit ......... het harnas: bescherming, vliegen, repulsor, raketten
 *   Workbench .... de harnas-werkbank en de upgrades van het harnas
 *   Turrets ...... torentjes (Stark, Hydra, beveiliging)
 *   Security ..... de beveiligingskern: zone, drones, systeem
 *   Logic ........ serverkasten en het datacenter: snelheid, warmte, brand, backups
 *   C4 ........... C4, en TNT en de satelliet tegen een beschermde zone
 *   Repair ....... barsten herstellen (Crusher, Compactor, Repair Paste)
 *   Bypass ....... inbreken met de Bypass Kit, en zakkenrollen
 *   Lights ....... de Arc Lamp
 *   Robots ....... guard robots: wat elk onderdeel doet (hoofdstuk 21)
 *   Hydra ........ Hydra-soldaten
 *   Raids ........ vijandige raiddrones
 */
public final class StarkConfig {
    private StarkConfig() {}

    // =============================================================================================== Rules

    @Section("What is allowed in this world (on a server: for everyone)")
    public static final class Rules {
        private Rules() {}

        /** Mag E.D.I.T.H. privéberichten van andere spelers onderscheppen ("hack player")? */
        @Tunable("May E.D.I.T.H. intercept other players' private messages (hack player)? "
                + "Set this to false on a server where players do not want that.")
        public static boolean HACK_ALLOWED = true;

        /** Mogen spelers keycards uit de zakken van anderen pikken (erachter sluipen, lege hand, rechtsklik)? */
        @Tunable("May players pick keycards out of other players' pockets (sneak behind them, empty hand, "
                + "right-click)? Set this to false on a server where players do not want that.")
        public static boolean PICKPOCKET_ALLOWED = true;

        /** Komen er vijandige raiddrones op spelers met de bril af? Hoe vaak en hoeveel: zie Raids. */
        @Tunable("Do hostile raid drones come after players wearing the glasses? (How often and how many: see [raids])")
        public static boolean RAIDS_ENABLED = true;
    }

    // =============================================================================================== Glasses

    @Section("The E.D.I.T.H. glasses: battery, upgrades and what each function costs per second")
    public static final class Glasses {
        private Glasses() {}

        // ------------------------------------------------------------------------- Batterij
        /** Hoeveel energie de bril kan opslaan zonder batterij-upgrades. */
        @Tunable("Energy the glasses hold without battery upgrades")
        public static float BASE_CAPACITY = 10_000.0f;
        /** Hoeveel extra opslag elke batterij-upgrade geeft. */
        @Tunable("Extra energy per battery upgrade")
        public static float CAPACITY_PER_BATTERY_UPGRADE = 10_000.0f;
        /** Hoeveel batterij-upgrades er hooguit in één bril passen. */
        @Tunable(value = "How many battery upgrades fit in one pair of glasses", max = 16)
        public static float MAX_BATTERY_UPGRADES = 3.0f;
        /** Hoe lang het installeren van een upgrade in het laadstation duurt (100 = 5 seconden). */
        @Tunable(value = "How long installing an upgrade in the charging station takes (ticks)", min = 1)
        public static float UPGRADE_INSTALL_TIME = 60.0f;
        /** Onder dit deel van de batterij waarschuwt E.D.I.T.H. (0.10 = 10%). */
        @Tunable(value = "Battery share (0-1) below which E.D.I.T.H. warns you", max = 1)
        public static float LOW_BATTERY_WARNING = 0.10f;

        // ------------------------------------------------------------------------- Verbruik per seconde
        /** Wat de HUD zelf kost zolang je de bril draagt. */
        @Tunable("Energy per second while the HUD is on")
        public static float DRAIN_HUD = 1.0f;
        /** Extra zolang nachtzicht aan staat. */
        @Tunable("Energy per second for night vision")
        public static float DRAIN_NIGHT_VISION = 4.0f;
        /** Extra zolang "vijanden door muren" aan staat. */
        @Tunable("Energy per second for the threat scan")
        public static float DRAIN_THREAT_SCAN = 3.0f;
        /** Extra zolang doelwit-analyse aan staat. */
        @Tunable("Energy per second for target analysis")
        public static float DRAIN_TARGET_ANALYSIS = 1.0f;

        // ------------------------------------------------------------------------- Doelwitten en zoom
        /** Tot hoe ver je een doelwit kunt analyseren en markeren. */
        @Tunable("How far away you can analyse and mark targets (blocks)")
        public static double TARGET_RANGE = 64.0;
        /** Hoeveel doelwitten je tegelijk kunt markeren. */
        @Tunable("How many targets you can mark at once")
        public static float MAX_MARKED_TARGETS = 10.0f;
        /** Hoeveel keer de bril inzoomt (bij het richten van de satelliet dubbel zoveel). */
        @Tunable(value = "How many times the glasses zoom in (twice this while aiming the satellite)", min = 1, max = 16)
        public static float ZOOM_FACTOR = 4.0f;
    }

    // =============================================================================================== Scanner

    @Section("Ore scanner and threat scan (seeing enemies through walls). Lists: scanner upgrade level 0, 1, 2 and 3")
    public static final class Scanner {
        private Scanner() {}

        /** Kosten van één ertsen-scan. */
        @Tunable("Energy cost of an ore scan")
        public static float ORE_SCAN_COST = 400.0f;
        /** Wachttijd tussen twee scans (200 = 10 seconden). */
        @Tunable("Wait between two ore scans (ticks)")
        public static float ORE_SCAN_COOLDOWN = 200.0f;
        /** Hoe lang de gevonden ertsen zichtbaar blijven (300 = 15 seconden). */
        @Tunable("How long ore markers stay visible (ticks)")
        public static float ORE_SCAN_DURATION = 300.0f;
        /** Scan-straal in blokken, per scanner-niveau 0, 1, 2 en 3. */
        @Tunable(value = "Ore scan radius (blocks) for scanner level 0, 1, 2 and 3", max = 128)
        public static float[] ORE_SCAN_RADIUS = {12.0f, 18.0f, 24.0f, 32.0f};
        /** Tot hoe ver vijanden door muren heen oplichten, per scanner-niveau 0, 1, 2 en 3. */
        @Tunable(value = "How far (blocks) enemies light up through walls, for scanner level 0, 1, 2 and 3", max = 256)
        public static float[] THREAT_RADIUS = {24.0f, 32.0f, 48.0f, 64.0f};

        /** VAST: hoeveel scanner-upgrades er in een bril passen (de rijtjes hierboven hebben er één meer). */
        public static final int MAX_SCANNER_UPGRADES = 3;
    }

    // =============================================================================================== Power

    /*
     * Alles wat met het stroomnet (de Stark-kabels) te maken heeft, voor elk blok: wat het opwekt, wat
     * het opslaat, en wat het gebruikt. Let op de tijd bij elk getal: "per tick" (20 keer per seconde),
     * "per seconde", of "elke twee seconden".
     *
     * Alle energiegetallen hier zijn kommagetallen (float), dus ook heel kleine waarden werken: 0.3 per
     * tick = 6 per seconde, 0.05 per tick = 1 per seconde. Wat nog geen hele stroom is, wordt bewaard en
     * telt de volgende tick mee (zie EnergyCarry en StarkGrid.pay), zodat het gemiddeld precies klopt.
     */
    @Section("Power on the Stark grid: what every block makes, stores and uses. "
            + "Watch the time in each line: per tick (20 times a second), per second, or every two seconds. "
            + "Decimals work: 0.3 per tick = 6 per second, 0.05 per tick = 1 per second.")
    public static final class Power {
        private Power() {}

        // ------------------------------------------------------------------------- Zonnepaneel
        /** Energie per tick met de zon recht boven je (minder als de zon laag staat, 's nachts niets). */
        @Tunable("Energy per tick a solar panel makes with the sun straight above (less when the sun is low, nothing at night)")
        public static float SOLAR_RATE = 2f;
        /** Welk deel van de zon een zonnepaneel nog krijgt als het regent (0.4 = 40%). */
        @Tunable(value = "Share of the sun (0-1) a solar panel still gets when it rains", max = 1)
        public static float SOLAR_RAIN = 1f;
        /** ...en bij onweer. */
        @Tunable(value = "Share of the sun (0-1) a solar panel still gets in a thunderstorm", max = 1)
        public static float SOLAR_THUNDER = 0.5f;
        /** Hoeveel een zonnepaneel zelf kan opslaan. */
        @Tunable("Energy a solar panel can store")
        public static float SOLAR_CAPACITY = 5_000.0f;

        // ------------------------------------------------------------------------- Warmtegenerator
        /** Energie per tick zolang er brandstof brandt (kolen, hout, ...). */
        @Tunable("Energy per tick a heat generator makes while it burns fuel")
        public static float HEAT_RATE_FUEL = 10f;
        /** Energie per tick per heet blok dat ertegenaan staat (lava, vuur, magma...). */
        @Tunable("Energy per tick per hot block (lava, fire, magma) next to a heat generator")
        public static float HEAT_RATE_AMBIENT = 5f;
        /** Meer hete buren dan dit tellen niet meer mee. */
        @Tunable(value = "Hot blocks above this many no longer count", max = 26)
        public static float HEAT_MAX_NEIGHBOURS = 8.0f;
        /** Hoeveel een warmtegenerator zelf kan opslaan. */
        @Tunable("Energy a heat generator can store")
        public static float HEAT_CAPACITY = 5_000.0f;

        // ------------------------------------------------------------------------- Laadstation (voor de bril)
        /**
         * Energie uit één stuk redstone. Bewust weinig: een volle bril kost zo ongeveer twee stapels.
         * Redstone is een noodvoorraad in je zak; voor gewone stroom zet je een generator neer.
         */
        @Tunable("Energy from one redstone dust in a charging station")
        public static float ENERGY_PER_REDSTONE = 50.0f;
        /** Energie uit één redstone-blok (9 redstone en een beetje bonus). */
        @Tunable("Energy from one redstone block in a charging station")
        public static float ENERGY_PER_REDSTONE_BLOCK = 480.0f;
        /** Hoeveel het laadstation zelf kan opslaan (hooguit 32000, anders klopt de balk in het scherm niet). */
        @Tunable(value = "Energy a charging station can store", min = 1, max = 32_000)
        public static float STATION_CAPACITY = 30_000.0f;
        /** Hoeveel energie het station per tick in de bril stopt. */
        @Tunable("Energy per tick a charging station puts into the glasses")
        public static float STATION_CHARGE_RATE = 50.0f;
        /** Hoeveel het station per tick uit het net mag halen om de bril te laden (ruim meer dan één paneel maakt). */
        @Tunable("Energy per tick a charging station draws from the grid to charge the glasses")
        public static float STATION_GRID_RATE = 20.0f;
        /**
         * Zijn eigen voorraad vult het station ook uit het net, maar alleen met wat over is: dit deel van
         * wat in de generatoren past, blijft altijd voor de rest van het net (holotafel, laserwanden, koepel).
         */
        @Tunable(value = "Share (0-1) of the generators' storage a charging station always leaves for the rest of the grid "
                + "when it fills its own storage", max = 1)
        public static float STATION_GRID_RESERVE = 0.5f;
        /** ...en dat gaat langzaam: zoveel per tick. */
        @Tunable("Energy per tick a charging station puts into its own storage from spare grid energy")
        public static float STATION_STORE_RATE = 10.0f;

        // ------------------------------------------------------------------------- Landingsplatform (voor het harnas)
        /** Zoveel energie per seconde laadt een landingsplatform in je bril, uit het net. */
        @Tunable("Energy per second a landing pad puts into your glasses")
        public static float PAD_CHARGE_PER_SECOND = 400.0f;
        /** Hoeveel de eigen batterij van een platform opslaat zonder batterijcellen. */
        @Tunable("How much a landing pad's own battery holds without battery cells")
        public static float PAD_BATTERY_BASE = 10_000.0f;
        /** Extra per Battery Upgrade in het batterijvak van het platform (hooguit 4). */
        @Tunable("Extra landing pad battery per Battery Upgrade in its battery slot (up to 4)")
        public static float PAD_BATTERY_PER_CELL = 15_000.0f;
        /** Zo snel vult het platform zijn batterij met wat het net over heeft (per seconde). */
        @Tunable("How fast a landing pad fills its battery from the grid's spare energy (per second)")
        public static float PAD_BATTERY_FILL = 200.0f;
        /** Zo snel laadt het platform je bril uit zijn eigen batterij (per seconde). */
        @Tunable("How fast a landing pad charges your glasses from its own battery (per second)")
        public static float PAD_BATTERY_CHARGE = 1600.0f;
        /** Zoveel duurzaamheid per seconde repareert een platform aan elk stuk harnas... */
        @Tunable("Durability per second a landing pad repairs on each suit piece")
        public static float PAD_REPAIR_PER_SECOND = 2.0f;
        /** ...en dat kost zoveel stroom per punt. */
        @Tunable("Energy per point of durability a landing pad repairs")
        public static float PAD_REPAIR_ENERGY = 10.0f;


        // ------------------------------------------------------------------------- Verbruik: verdediging
        /** Stroom per tick van één laserzender zolang zijn wand aan staat (een paar zenders = 2 per tick). */
        @Tunable("Energy per tick a laser wall emitter uses while its wall is on")
        public static float LASER_WALL_UPKEEP = 1.0f;
        /** Zoveel seconden moet het net stroom hebben voordat een laserwand aangaat (anders knippert hij). */
        @Tunable("A laser wall only turns on when the grid has had power for this many seconds (so it doesn't flicker)")
        public static float LASER_WALL_START_SECONDS = 5.0f;
        /**
         * Stroom per tick van een schildkoepel, per sterkte-niveau, voor elk van zijn 5 maten (zie Dome.RADII:
         * 8, 12, 16, 24 en 32 blokken). Een koepel van 16 blokken op sterkte 2 kost dus 4 x 2 = 8 per tick.
         */
        @Tunable(value = "Energy per tick a shield dome uses PER STRENGTH LEVEL, for each of its 5 sizes (see dome.radii). "
                + "A 16-block dome on strength 2 uses 4 x 2 = 8 per tick", max = 10_000)
        public static float[] DOME_UPKEEP = {2.0f, 3.0f, 4.0f, 6.0f, 8.0f};
        /** Stroom die één laserschot van een Stark-torentje van het net haalt. */
        @Tunable("Grid energy one Stark turret laser shot uses")
        public static float TURRET_LASER_SHOT = 60.0f;
        /** Stroom per raket die een Stark-torentje afvuurt. */
        @Tunable("Grid energy per rocket a Stark turret fires")
        public static float TURRET_ROCKET = 20.0f;
        /** Stroom die de beveiligingskern elke twee seconden van het net haalt (zonder staan hij en zijn drones uit). */
        @Tunable("Grid energy the security core uses every two seconds (without it, the core and its drones are off)")
        public static float SECURITY_CORE_UPKEEP = 20.0f;
        /** Een zone kost elke twee seconden 1 extra per zoveel blokken (30 x 20 x 30 kost 9 extra). */
        @Tunable(value = "A protected zone costs 1 extra grid energy every two seconds per this many blocks", min = 0.001)
        public static float ZONE_BLOCKS_PER_POWER = 2000.0f;
        /** Stroom die een bewakingscamera elke twee seconden haalt (zonder: "NO SIGNAL"). */
        @Tunable("Grid energy a security camera uses every two seconds (without it, it shows NO SIGNAL)")
        public static float CAMERA_UPKEEP = 2.0f;

        // ------------------------------------------------------------------------- Verbruik: machines en de rest
        /** Stroom per tick van de harnas-werkbank zolang hij een stuk bouwt... */
        @Tunable("Grid energy per tick while the suit workbench builds a suit piece")
        public static float WORKBENCH_BUILD = 10.0f;
        /** ...en zolang hij een upgrade installeert. */
        @Tunable("Grid energy per tick while the suit workbench installs an upgrade")
        public static float WORKBENCH_INSTALL = 8.0f;
        /** Stroom per tick van de Crusher en de Compactor zolang ze werken. */
        @Tunable("Grid energy per tick the Crusher and the Compactor use while they work")
        public static float CRUSHER_COMPACTOR = 4.0f;
        /** Stroom die één serverkast elke twee seconden haalt (zonder tellen zijn onderdelen niet mee). */
        @Tunable("Grid energy one server rack uses every two seconds (without it, its parts do not count)")
        public static float SERVER_RACK_UPKEEP = 10.0f;
        /** Stroom per tick van een holotafel zolang hij aan staat. */
        @Tunable("Grid energy per tick a holo table uses while it is on")
        public static float HOLO_TABLE_UPKEEP = 0.5f;
        /**
         * Stroom per tick van een Arc Lamp op lichtniveau 100 (2 per tick = 40 per seconde). Zachter gezet
         * gebruikt hij naar verhouding minder: op 50 de helft.
         */
        @Tunable("Grid energy per tick an Arc Lamp at light level 100 uses while it is on. "
                + "A lamp set lower uses less: at level 50, half of this.")
        public static float ARC_LAMP_POWER = 10.0f;
        /** Een lamp die uit is, gaat pas aan als het net zoveel ticks stroom heeft (100 = 5 seconden). */
        @Tunable("An Arc Lamp that is off only turns on when the grid has had power for this many ticks "
                + "(so it doesn't flicker on a weak generator)")
        public static float ARC_LAMP_START_TICKS = 100.0f;
    }

    // =============================================================================================== Drones

    @Section("Your own drones: how many, the swarm, a single strike drone, escort drones, and the arsenal")
    public static final class Drones {
        private Drones() {}

        // ------------------------------------------------------------------------- Herstellen (hoofdstuk 25)
        /** Levenspunten per seconde die een drone in zijn baan herstelt (even snel als vroeger op een dock). */
        @Tunable("Health an arsenal drone repairs per second while it waits in orbit")
        public static float ORBIT_REPAIR_PER_SECOND = 0.25f;

        // ------------------------------------------------------------------------- Hoeveel
        // Op een server met veel spelers kunnen honderden drones het spel laten haperen: hier kies je het maximum.
        /** Aanvalsdrones in een zwerm zonder drone-upgrades. */
        @Tunable(value = "Number of attack drones in a swarm without drone upgrades", min = 1, max = 64)
        public static float SWARM_BASE = 4.0f;
        /** Zoveel aanvalsdrones komen erbij per drone-upgrade... */
        @Tunable(value = "How many extra attack drones each drone upgrade adds", max = 8)
        public static float SWARM_PER_UPGRADE = 2.0f;
        /** ...maar nooit meer dan dit. */
        @Tunable(value = "Maximum number of attack drones in a swarm (with all upgrades)", min = 1, max = 64)
        public static float SWARM_MAX = 16.0f;
        /** Beschermende drones (escorte) zonder drone-upgrades. */
        @Tunable(value = "Number of guard drones (escort) without drone upgrades", max = 32)
        public static float GUARD_BASE = 2.0f;
        /** Zoveel beschermende drones komen erbij per drone-upgrade... */
        @Tunable(value = "How many extra guard drones each drone upgrade adds", max = 8)
        public static float GUARDS_PER_UPGRADE = 1.0f;
        /** ...maar nooit meer dan dit. */
        @Tunable(value = "Maximum number of guard drones (with all upgrades)", max = 32)
        public static float GUARD_MAX = 8.0f;
        /** Hoeveel drone-upgrades er in één bril passen. */
        @Tunable(value = "How many drone upgrades fit in one pair of glasses", max = 16)
        public static float MAX_UPGRADES = 6.0f;

        // ------------------------------------------------------------------------- De zwerm
        /** Kosten om een zwerm te lanceren. */
        @Tunable("Energy cost of launching a drone swarm")
        public static float SWARM_COST = 1_500.0f;
        /** Wachttijd tussen twee zwermen (600 = 30 seconden). */
        @Tunable("Wait between two swarms (ticks)")
        public static float SWARM_COOLDOWN = 600.0f;
        /** Hoe lang een zwerm blijft voordat hij naar huis vliegt (1200 = 1 minuut). */
        @Tunable("How long a swarm stays before flying home (ticks)")
        public static float SWARM_LIFETIME = 1_200.0f;

        // ------------------------------------------------------------------------- Eén drone op één doelwit
        /** Kosten om één drone op één doelwit af te sturen. */
        @Tunable("Energy cost of sending a single drone")
        public static float STRIKE_COST = 400.0f;
        /** Wachttijd tussen twee losse drones (160 = 8 seconden). */
        @Tunable("Wait between two single drones (ticks)")
        public static float STRIKE_COOLDOWN = 160.0f;
        /** Hoe lang die ene drone blijft jagen (1800 = 90 seconden). */
        @Tunable("How long a single drone hunts before flying home (ticks)")
        public static float STRIKE_LIFETIME = 1_800.0f;
        /** Tot hoe ver je een los doelwit kunt aanwijzen. */
        @Tunable("How far away you can send a single drone (blocks)")
        public static double STRIKE_RANGE = 96.0;

        // ------------------------------------------------------------------------- Beschermende drones (escorte)
        /** Extra energie per seconde zolang de beschermende drones aan staan. */
        @Tunable("Energy per second while escort drones are on")
        public static float GUARD_DRAIN = 6.0f;
        /** Tot hoe ver van jou ze vijanden aanvallen. */
        @Tunable("How far from you escort drones attack threats (blocks)")
        public static double GUARD_RANGE = 12.0;

        // ------------------------------------------------------------------------- Vechten
        /** Schade van één laserschot (4 = 2 hartjes). */
        @Tunable("Damage of one drone laser shot")
        public static float LASER_DAMAGE = 4.0f;
        /** Tijd tussen twee schoten van dezelfde drone. */
        @Tunable("Ticks between two drone laser shots")
        public static float FIRE_INTERVAL = 15.0f;
        /** Tot hoe ver een drone schiet. */
        @Tunable("How far a drone shoots (blocks)")
        public static double ATTACK_RANGE = 16.0;

        // ------------------------------------------------------------------------- Het arsenaal
        /** Welk deel van de bouwkosten je terugkrijgt als een drone wordt neergehaald (de rest is weg). */
        @Tunable(value = "Share of a drone's build cost you get back as scrap when it is shot down (0-1)", max = 1)
        public static double WRECK_RECOVERY = 0.10;
        /** Levenspunten die één ijzerstaaf aan een drone herstelt. */
        @Tunable(value = "Health one iron ingot repairs on a drone", min = 0.1)
        public static double REPAIR_PER_INGOT = 3.0;
        // (Repareren op een drone-dock staat bij Power.)

        // ------------------------------------------------------------------------- Aankomen en vertrekken
        /**
         * Hoe hoog boven je de drones vandaan komen, en hoe hoog ze weer verdwijnen. Ze vallen echt uit
         * de lucht. Zit er een plafond boven je (een grot), dan komen ze zo hoog als er ruimte is.
         */
        @Tunable(value = "How high above you drones dive in from, and climb away to (blocks)", max = 320)
        public static float ORBIT_HEIGHT = 96.0f;
        /** Een drone duikt in een boog: hij begint dit deel van de valhoogte opzij... */
        @Tunable(value = "A diving drone starts this share (0-1) of its fall height to the side, so it dives in an arc", max = 1)
        public static double DIVE_ARC_RATIO = 0.3;
        /** ...maar nooit meer dan zoveel blokken. */
        @Tunable("...but never more than this many blocks to the side")
        public static double DIVE_ARC_MAX = 24.0;
        /** Zo hoog boven zijn doel remt een aanvallende drone af na de duik. */
        @Tunable("How high above its target an attacking drone brakes after the dive (blocks)")
        public static double DIVE_BRAKE_HEIGHT = 3.0;
        /** Hoe snel een vertrekkende drone omhoog gaat (blokken per tick). */
        @Tunable("How fast a leaving drone climbs (blocks per tick)")
        public static double LAUNCH_SPEED = 2.2;
        /** Na zoveel ticks is een vertrekkende drone hoe dan ook weg, ook onder een plafond. */
        @Tunable("After this many ticks a leaving drone is gone, even when it bumps into a ceiling")
        public static float LAUNCH_TICKS = 80.0f;

        /** VAST: levenspunten van een drone (12 = 6 hartjes). Het spel heeft dit nodig bij het opstarten. */
        public static final double HEALTH = 12.0;
    }

    // =============================================================================================== Satellite

    /*
     * De satelliet heeft zes upgrades (G > Satellite), elk van niveau 0 tot en met 10:
     *   Recharge, Power, Targeting    gelden voor alles: rijtjes van 11 getallen (niveau 0 t/m 10)
     *   Precision, Bombardment, Sweep de drie schietstanden: niveau 0 = op slot, dus rijtjes van
     *                                 10 getallen (niveau 1 t/m 10). Precision begint op niveau 1.
     */
    @Section("The satellite. Lists for Recharge, Power and Targeting have 11 numbers (upgrade level 0 to 10). "
            + "Lists for the three firing modes (Precision, Bombardment, Sweep) have 10 numbers (level 1 to 10; level 0 = locked).")
    public static final class Satellite {
        private Satellite() {}

        // ------------------------------------------------------------------------- Kosten per schot
        /** Kosten van een precisie-straal (geen blokschade). */
        @Tunable("Energy cost of a precision beam")
        public static float COST_PRECISION = 2_000.0f;
        /** Kosten van een bombardement (meerdere inslagen, krater). */
        @Tunable("Energy cost of a bombardment")
        public static float COST_BOMBARDMENT = 5_000.0f;
        /** Kosten van de schuivende laser. */
        @Tunable("Energy cost of a sweeping laser")
        public static float COST_SWEEP = 3_500.0f;

        // ------------------------------------------------------------------------- Recharge, Power, Targeting (niveau 0 t/m 10)
        /** Wachttijd (herlaadtijd) tussen twee aanvallen, per Recharge-niveau: 1800 = 90 seconden, 720 = 36 seconden. */
        @Tunable(value = "Reload time between two satellite strikes for Recharge level 0 to 10 (ticks; 1800 = 90 seconds)", max = 72_000)
        public static float[] COOLDOWN = {1_800.0f, 1_692.0f, 1_584.0f, 1_476.0f, 1_368.0f, 1_260.0f, 1_152.0f, 1_044.0f, 936.0f, 828.0f, 720.0f};
        /** Schade maal dit, voor alle standen, per Power-niveau (1.08 = 8% meer). */
        @Tunable(value = "Damage multiplier for all modes for Power level 0 to 10 (1.08 = 8% more)", max = 100)
        public static float[] DAMAGE_MULTIPLIER = {1.0f, 1.08f, 1.16f, 1.24f, 1.32f, 1.4f, 1.48f, 1.56f, 1.64f, 1.72f, 1.8f};
        /** Hoe lang de satelliet richt voordat hij inslaat, per Targeting-niveau (60 = 3 seconden). */
        @Tunable(value = "How long the satellite aims before it hits, for Targeting level 0 to 10 (ticks)", max = 1_200)
        public static float[] CHARGE_TIME = {60.0f, 57.0f, 54.0f, 51.0f, 48.0f, 45.0f, 42.0f, 39.0f, 36.0f, 33.0f, 30.0f};
        /** Hoe ver weg je kunt richten, per Targeting-niveau. */
        @Tunable(value = "How far away the satellite can aim for Targeting level 0 to 10 (blocks)", max = 100_000)
        public static double[] RANGE = {500, 550, 600, 650, 700, 750, 800, 850, 900, 950, 1_000};

        /** Zo dicht bij jezelf kun je hooguit richten, zodat je jezelf niet opblaast. */
        @Tunable("Closest distance to yourself you can aim the satellite (blocks)")
        public static double MIN_DISTANCE = 10.0;
        /** Mag de satelliet blokken kapotmaken? Volgt ook de spelregel mobGriefing (uit = blokken blijven heel). */
        @Tunable("Do satellite explosions break blocks? (also follows the mobGriefing game rule)")
        public static boolean BREAKS_BLOCKS = true;

        // ------------------------------------------------------------------------- Precision (niveau 1 t/m 10)
        /** Schade van de precisie-straal per niveau (30 = 15 hartjes), vóór Power. */
        @Tunable(value = "Damage of the precision beam for Precision level 1 to 10 (before the Power multiplier)", max = 10_000)
        public static float[] PRECISION_DAMAGE = {30.0f, 33.6f, 37.2f, 40.8f, 44.4f, 48.0f, 51.6f, 55.2f, 58.8f, 62.4f};
        /** Binnen deze straal raakt de precisie-straal alles, per niveau. */
        @Tunable(value = "Radius of the precision beam for Precision level 1 to 10 (blocks)", max = 64)
        public static double[] PRECISION_RADIUS = {3.5, 3.7, 3.9, 4.1, 4.3, 4.5, 4.7, 4.9, 5.1, 5.3};

        // ------------------------------------------------------------------------- Bombardment (niveau 1 t/m 10)
        /** Hoeveel inslagen een bombardement heeft, per niveau. */
        @Tunable(value = "Number of impacts in a bombardment for Bombardment level 1 to 10", min = 1, max = 64)
        public static float[] BOMBARDMENT_SHOTS = {4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f};
        /** Hoe ver de inslagen rond het doel verspreid liggen, per niveau. */
        @Tunable(value = "How far the impacts spread around the target for Bombardment level 1 to 10 (blocks)", max = 64)
        public static double[] BOMBARDMENT_SPREAD = {6.0, 6.3, 6.6, 6.9, 7.2, 7.5, 7.8, 8.1, 8.4, 8.7};
        /** Kracht van één explosie per niveau (TNT = 4, een creeper = 3)... */
        @Tunable(value = "Explosion power of one impact for Bombardment level 1 to 10 (TNT is 4, a creeper 3)", max = 20)
        public static float[] BOMBARDMENT_EXPLOSION = {4.7f, 4.9f, 5.1f, 5.3f, 5.5f, 5.7f, 5.9f, 6.1f, 6.3f, 6.5f};
        /** ...plus dit per Power-niveau. */
        @Tunable(value = "Extra explosion power per Power level", max = 2)
        public static float EXPLOSION_PER_POWER_LEVEL = 0.1f;
        /** Ticks tussen twee inslagen. */
        @Tunable(value = "Ticks between two bombardment impacts", min = 1)
        public static float BOMBARDMENT_INTERVAL = 12.0f;

        // ------------------------------------------------------------------------- Sweep (niveau 1 t/m 10)
        /** Hoe lang de laser over de grond schuift, per niveau (56 = 2,8 seconde). */
        @Tunable(value = "How long the sweeping laser moves over the ground for Sweep level 1 to 10 (ticks)", min = 1, max = 1_200)
        public static float[] SWEEP_TICKS = {56.0f, 62.0f, 68.0f, 74.0f, 80.0f, 86.0f, 92.0f, 98.0f, 104.0f, 110.0f};
        /** Schade per treffer van de schuivende laser per niveau, vóór Power. */
        @Tunable(value = "Damage per hit of the sweeping laser for Sweep level 1 to 10 (before the Power multiplier)", max = 1_000)
        public static float[] SWEEP_DAMAGE = {6.96f, 7.52f, 8.08f, 8.64f, 9.2f, 9.76f, 10.32f, 10.88f, 11.44f, 12.0f};
        /** Hoe breed de laser raakt. */
        @Tunable("How wide the sweeping laser hits (blocks)")
        public static double SWEEP_RADIUS = 2.5;
        /** Het beginpunt dat je met de eerste K kiest, blijft zo lang bewaard (600 = een halve minuut). */
        @Tunable("How long the start point of a sweep (your first K) is remembered (ticks)")
        public static float SWEEP_START_TIMEOUT = 600.0f;

        // ------------------------------------------------------------------------- Schokgolf (bij elke inslag)
        /** Tot hoe ver de schokgolf wezens wegduwt. */
        @Tunable("How far the shockwave pushes mobs and players away (blocks)")
        public static double SHOCKWAVE_RADIUS = 10.0;
        /** Hoe hard de schokgolf duwt. */
        @Tunable("How hard the shockwave pushes")
        public static double SHOCKWAVE_POWER = 1.2;

        // ------------------------------------------------------------------------- Upgrades naar boven brengen
        /** Hoeveel drones een upgrade naar de satelliet brengen, per niveau 1 t/m 10 (ze komen daarna terug). */
        @Tunable(value = "How many drones it takes to bring upgrade level 1 to 10 up to the satellite (they come back)", min = 1, max = 64)
        public static float[] DELIVERY_DRONES = {2.0f, 2.0f, 3.0f, 3.0f, 4.0f, 4.0f, 5.0f, 5.0f, 6.0f, 6.0f};
        /** Zo lang doen de drones erover om boven te komen. */
        @Tunable("How long the drones take to fly an upgrade up (ticks)")
        public static float DELIVERY_TICKS = 70.0f;
    }

    // =============================================================================================== Recon

    @Section("The drone camera (fly a drone yourself) and the spy drone (follows someone)")
    public static final class Recon {
        private Recon() {}

        // ------------------------------------------------------------------------- Drone-camera
        /** Kosten om de verkenningsdrone te lanceren. */
        @Tunable("Energy cost of launching the drone camera")
        public static float SCOUT_COST = 200.0f;
        /** Extra energie per seconde zolang je door de drone kijkt. */
        @Tunable("Energy per second while you look through the drone camera")
        public static float SCOUT_DRAIN = 8.0f;
        /** Hoe ver de drone van je lichaam mag vliegen. */
        @Tunable(value = "How far the drone camera may fly from you (blocks)", min = 1)
        public static double SCOUT_RANGE = 64.0;
        /** Vliegsnelheid (blokken per tick; 0.35 = 7 blokken per seconde). */
        @Tunable("Drone camera flying speed (blocks per tick; 0.35 = 7 blocks per second)")
        public static double SCOUT_SPEED = 0.35;
        /** Vliegsnelheid met sprinten. */
        @Tunable("Drone camera flying speed while sprinting (blocks per tick)")
        public static double SCOUT_BOOST_SPEED = 0.7;

        // ------------------------------------------------------------------------- Spionagedrone
        /** Kosten om een spionagedrone te sturen. */
        @Tunable("Energy cost of a spy drone")
        public static float SPY_COST = 800.0f;
        /** Wachttijd tussen twee spionagedrones (400 = 20 seconden). */
        @Tunable("Wait between two spy drones (ticks)")
        public static float SPY_COOLDOWN = 400.0f;
        /** Hoe lang hij iemand volgt (12000 = 10 minuten). */
        @Tunable("How long a spy drone follows its target (ticks)")
        public static float SPY_DURATION = 12_000.0f;
        /** Hoe vaak hij een rapport stuurt (600 = elke 30 seconden). */
        @Tunable(value = "How often a spy drone sends a report (ticks)", min = 1)
        public static float SPY_REPORT_INTERVAL = 600.0f;
        /** Tot hoe ver je een doelwit kunt aanwijzen. */
        @Tunable("How far away you can send a spy drone (blocks)")
        public static double SPY_RANGE = 96.0;
        /** Hoe ver achter het doelwit de drone blijft hangen... */
        @Tunable("How far behind its target the spy drone stays (blocks)")
        public static double SPY_FOLLOW_DISTANCE = 4.0;
        /** ...en hoe hoog erboven. */
        @Tunable("How high above its target the spy drone stays (blocks)")
        public static double SPY_FOLLOW_HEIGHT = 5.0;
        /** Extra energie per seconde zolang je door de spionagedrone meekijkt. */
        @Tunable("Energy per second while you watch through the spy drone")
        public static float SPY_VIEW_DRAIN = 4.0f;
        /** Tot hoe ver je nog live kunt meekijken. */
        @Tunable("How far away you can still watch through the spy drone (blocks)")
        public static double SPY_VIEW_RANGE = 110.0;
    }

    // =============================================================================================== Transport

    @Section("Transport drones: a ride, the autopilot, cargo and carrying you")
    public static final class Transport {
        private Transport() {}

        /** Kosten om je te laten oppikken (of om een vracht te versturen). */
        @Tunable("Energy cost of a transport (ride, autopilot, cargo, carry)")
        public static float COST = 600.0f;
        /** Wachttijd tussen twee ritten (200 = 10 seconden). */
        @Tunable("Wait between two transports (ticks)")
        public static float COOLDOWN = 200.0f;
        /** Energie per seconde zolang drones je dragen. */
        @Tunable("Energy per second while drones carry you")
        public static float DRAIN = 14.0f;
        /** Hoe lang een rit of levering hooguit duurt (6000 = 5 minuten). */
        @Tunable("Longest a transport may take (ticks)")
        public static float MAX_TICKS = 6_000.0f;
        /** Vliegsnelheid als je zelf stuurt (0.45 = 9 blokken per seconde). */
        @Tunable("Flying speed when you steer yourself (blocks per tick)")
        public static double SPEED = 0.45;
        /** Vliegsnelheid met sprinten. */
        @Tunable("Flying speed while sprinting (blocks per tick)")
        public static double BOOST_SPEED = 0.85;
        /** Hoe snel de drone met je meezakt als je landt. */
        @Tunable("How fast the drone sinks with you when you land (blocks per tick)")
        public static double LAND_SPEED = 0.35;
        /** Hoe hoog boven de grond de autopiloot vliegt. */
        @Tunable("How high above the ground the autopilot flies (blocks)")
        public static float CRUISE_HEIGHT = 14.0f;

        /** VAST: hoeveel stapels een vrachtdrone meeneemt (zoveel vakjes heeft het scherm). */
        public static final int CARGO_SLOTS = 27;
    }

    // =============================================================================================== Illusions

    @Section("Illusions (cloak fields, fake walls, a fake army) and the E.M.P. that jams them")
    public static final class Illusions {
        private Illusions() {}

        /** Kosten om een illusie te starten. */
        @Tunable("Energy cost of an illusion")
        public static float COST = 1_200.0f;
        /** Hoeveel illusies één speler tegelijk kan hebben. */
        @Tunable("How many illusions one player can run at once")
        public static float MAX_ACTIVE = 4.0f;
        /** Hoe lang een illusie blijft (6000 = 5 minuten). */
        @Tunable("How long an illusion lasts (ticks)")
        public static float LIFETIME = 6_000.0f;
        /** Hoe groot het veld van één cloakingdrone is. */
        @Tunable("Radius of a cloak field (blocks)")
        public static double RADIUS = 16.0;
        /** Zo ver van je af kun je een illusie neerzetten. */
        @Tunable("How far away from you you can place an illusion (blocks)")
        public static double AIM_RANGE = 48.0;
        /** De maat van een valse muur: breedte... */
        @Tunable(value = "Width of a fake wall (blocks)", min = 1, max = 64)
        public static float WALL_WIDTH = 9.0f;
        /** ...en hoogte. */
        @Tunable(value = "Height of a fake wall (blocks)", min = 1, max = 64)
        public static float WALL_HEIGHT = 5.0f;
        /** Hoeveel nep-wezens er in een nep-leger staan. */
        @Tunable(value = "How many fake mobs a fake army has", max = 64)
        public static float ARMY_SIZE = 8.0f;

        // ------------------------------------------------------------------------- E.M.P.
        /** Hoe ver een E.M.P.-puls reikt. */
        @Tunable("Radius of an E.M.P. pulse (blocks)")
        public static double EMP_RADIUS = 24.0;
        /** Hoe lang illusies plat liggen na een E.M.P. (600 = 30 seconden). */
        @Tunable("How long an E.M.P. jams illusions (ticks)")
        public static float EMP_JAM_TICKS = 600.0f;
        /** Wachttijd van de E.M.P.-zender (300 = 15 seconden). */
        @Tunable("Wait between two E.M.P. pulses (ticks)")
        public static float EMP_COOLDOWN = 300.0f;
    }

    // =============================================================================================== Shields

    @Section("The personal rayshield and laser walls (their power use is under [power])")
    public static final class Shields {
        private Shields() {}

        /** Extra energie per seconde zolang je persoonlijke schild aan staat. */
        @Tunable("Energy per second while the personal rayshield is on")
        public static float PERSONAL_DRAIN = 5.0f;
        /** Wat één punt tegengehouden schade kost. */
        @Tunable("Energy the personal shield uses per point of damage it stops")
        public static float PERSONAL_COST_PER_DAMAGE = 120.0f;
        /** Schade als je een laserwand aanraakt (4 = 2 hartjes). */
        @Tunable("Damage from touching a laser wall")
        public static float LASER_WALL_DAMAGE = 4.0f;
        /** Hoe lang een laserwand hooguit wordt. */
        @Tunable(value = "Longest a laser wall can be (blocks between two emitters)", min = 1, max = 64)
        public static float LASER_MAX_LENGTH = 16.0f;
    }

    // =============================================================================================== Dome

    /*
     * Een koepel heeft twee knoppen in zijn scherm: de MAAT (een van de 5 stralen hieronder) en de
     * STERKTE (1 tot 5). Elke treffer die hij tegenhoudt kost schildkracht; is die op, dan valt hij
     * even uit (DOWN_TICKS) en komt daarna vanzelf terug. Wat hij aan stroom kost: Power.DOME_UPKEEP.
     */
    @Section("The shield dome. It has 5 sizes and strength 1 to 5; every hit it stops costs shield strength, "
            + "and at 0 it goes down for a while. Its power use is power.domeUpkeep.")
    public static final class Dome {
        private Dome() {}

        /** De 5 maten die je kunt kiezen (straal in blokken). */
        @Tunable(value = "The 5 sizes you can choose (radius in blocks)", min = 1, max = 64)
        public static float[] RADII = {8.0f, 12.0f, 16.0f, 24.0f, 32.0f};
        /** Met welke maat een nieuwe koepel begint (0 = de kleinste, 4 = de grootste). */
        @Tunable(value = "Size a new dome starts with (0 = smallest, 4 = largest)", max = 4)
        public static float DEFAULT_SIZE = 2.0f;
        /** Met welke sterkte een nieuwe koepel begint. */
        @Tunable(value = "Strength a new dome starts with (1-5)", min = 1, max = 5)
        public static float DEFAULT_STRENGTH = 2.0f;
        /** Hoeveel schildkracht hij heeft, per sterkte 1 t/m 5. */
        @Tunable(value = "Shield strength a dome has for strength 1 to 5", min = 1)
        public static float[] MAX_SHIELD = {400.0f, 800.0f, 1_200.0f, 1_600.0f, 2_000.0f};
        /** Hoeveel schildkracht er per tick bijkomt, per sterkte 1 t/m 5. */
        @Tunable("Shield strength a dome gets back per tick for strength 1 to 5")
        public static float[] RECHARGE = {2.0f, 4.0f, 6.0f, 8.0f, 10.0f};
        /** Hoe lang hij uitvalt als zijn schildkracht op is (200 = 10 seconden). */
        @Tunable("How long a broken dome stays down before it comes back (ticks)")
        public static float DOWN_TICKS = 200.0f;
        /** Na het uitvallen komt hij terug met dit deel van zijn schildkracht. */
        @Tunable(value = "Share (0-1) of its shield strength a dome comes back with after it was down", max = 1)
        public static float COMEBACK_SHIELD = 0.25f;

        // ------------------------------------------------------------------------- Wat een treffer kost
        /** Een pijl, sneeuwbal, drietand of ander klein projectiel. */
        @Tunable("Dome strength lost per arrow or projectile it stops")
        public static float COST_PROJECTILE = 15.0f;
        /** Een vuurbal (ghast, blaze, wither-schedel). */
        @Tunable("Dome strength lost per fireball it stops")
        public static float COST_FIREBALL = 60.0f;
        /** Een monster of raiddrone naar buiten duwen (elke halve seconde). */
        @Tunable("Dome strength lost per push of a mob or raid drone out of the dome (every half second)")
        public static float COST_PUSH = 5.0f;
        /** Een laserschot van een raiddrone of van de drone van iemand anders. */
        @Tunable("Dome strength lost per laser shot it stops")
        public static float COST_LASER = 30.0f;
        /** Een precisiestraal van een satelliet. */
        @Tunable("Dome strength lost when it stops a satellite precision beam")
        public static float COST_SATELLITE_BEAM = 350.0f;
        /** Eén inslag van een satellietbombardement. */
        @Tunable("Dome strength lost per bombardment impact it stops")
        public static float COST_SATELLITE_IMPACT = 150.0f;
        /** Eén treffer van de schuivende satellietlaser. */
        @Tunable("Dome strength lost per hit of a sweeping satellite laser it stops")
        public static float COST_SATELLITE_SWEEP = 40.0f;

        /** VAST: de laagste en hoogste sterkte (de rijtjes hierboven hebben er 5). */
        public static final int MIN_STRENGTH = 1;
        public static final int MAX_STRENGTH = 5;
    }

    // =============================================================================================== Hack

    @Section("Hacking players: reading the private messages of players you look at (switch it off under [rules])")
    public static final class Hack {
        private Hack() {}

        /** Hoeveel spelers je tegelijk kunt hacken. */
        @Tunable("How many players you can hack at once")
        public static float MAX_TARGETS = 3.0f;
        /** Energie per seconde per gehackte speler. */
        @Tunable("Energy per second per hacked player")
        public static float DRAIN = 3.0f;
        /** Tot hoe ver je een speler kunt hacken. */
        @Tunable("How far away you can hack a player (blocks)")
        public static double RANGE = 64.0;
        /** Hoe recht je naar de speler moet kijken (1 = precies in het midden, 0.45 = "ergens in je beeld"). */
        @Tunable(value = "How straight you must look at a player to hack them (1 = dead centre, 0.45 = somewhere in view)", max = 1)
        public static double VIEW_DOT = 0.45;
        /** Hoe lang een onderschept bericht boven het hoofd van de speler blijft staan (in milliseconden: 9000 = 9 seconden). */
        @Tunable("How long an intercepted message stays above the player's head (milliseconds)")
        public static float BUBBLE_MILLIS = 9_000.0f;
    }

    // =============================================================================================== Relays

    @Section("Redstone relays and other devices you switch from a distance")
    public static final class Relays {
        private Relays() {}

        /** Hoeveel relais en schakelbare apparaten één speler kan aanmelden. */
        @Tunable("How many relays and switchable devices one player can register")
        public static float MAX_RELAYS = 64.0f;
        /** Hoe lang de naam van een relais (en van een groep) mag zijn. */
        @Tunable(value = "Longest name of a relay or relay group (letters)", min = 1, max = 64)
        public static float MAX_NAME = 24.0f;
        /** Hoeveel groepen één speler mag hebben (14 oktober: groepen in plaats van tags). */
        @Tunable(value = "Most relay groups one player can have", min = 1, max = 256)
        public static float MAX_GROUPS = 32.0f;
        /** Wat één schakeling kost. */
        @Tunable("Energy cost of switching a relay remotely")
        public static float COST = 5.0f;
        /** Hoe lang een puls duurt (20 = 1 seconde). */
        @Tunable(value = "How long a redstone pulse lasts (ticks)", min = 1)
        public static float PULSE_TICKS = 20.0f;
    }

    // =============================================================================================== Comms

    @Section("Messages and pings between E.D.I.T.H. wearers")
    public static final class Comms {
        private Comms() {}

        /** Hoe lang een bericht mag zijn. */
        @Tunable(value = "Longest message (letters)", min = 1, max = 256)
        public static float MAX_MESSAGE_LENGTH = 100.0f;
        /** Wachttijd tussen twee berichten, tegen spam. */
        @Tunable("Wait between two messages (ticks)")
        public static float MESSAGE_COOLDOWN = 20.0f;
        /** Wachttijd tussen twee pings. */
        @Tunable("Wait between two pings (ticks)")
        public static float PING_COOLDOWN = 40.0f;
        /** Hoe lang een ping zichtbaar blijft (300 = 15 seconden). */
        @Tunable("How long a ping stays visible (ticks)")
        public static float PING_DURATION = 300.0f;
        /** Tot hoe ver weg anderen je ping zien. */
        @Tunable("How far away other E.D.I.T.H. wearers see your pings (blocks)")
        public static double PING_RANGE = 512.0;
        /** Hoe ver weg je een ping kunt plaatsen (waar je naar kijkt). */
        @Tunable("How far away you can place a ping where you look (blocks)")
        public static double PING_PLACE_RANGE = 128.0;
    }

    // =============================================================================================== Navigation

    @Section("Waypoints and navigation")
    public static final class Navigation {
        private Navigation() {}

        /** Hoeveel waypoints je kunt opslaan. */
        @Tunable("How many waypoints one player can save")
        public static float MAX_WAYPOINTS = 20.0f;
        /** Hoe lang de naam van een waypoint mag zijn. */
        @Tunable(value = "Longest waypoint name (letters)", min = 1, max = 64)
        public static float MAX_WAYPOINT_NAME = 24.0f;
        /** Binnen deze afstand ben je "aangekomen". */
        @Tunable("Within this distance of a waypoint you have arrived (blocks)")
        public static double ARRIVAL_DISTANCE = 4.0;
    }

    // =============================================================================================== Holo

    @Section("The holo table (its power use is under [power])")
    public static final class Holo {
        private Holo() {}

        /** De 4 afstanden die je in het scherm van de holotafel kunt kiezen: zo ver reikt de kaart. */
        @Tunable(value = "The 4 map ranges you can choose on a holo table (blocks)", min = 8, max = 512)
        public static float[] RANGES = {24.0f, 48.0f, 96.0f, 160.0f};
    }

    // =============================================================================================== Suit

    @Section("The suit: protection, flying, landing, repulsor and mini rockets. Everything it does is paid by the glasses")
    public static final class Suit {
        private Suit() {}

        // ------------------------------------------------------------------------- Bescherming met stroom
        /** Welk deel van de schade elk stuk met stroom wegneemt; drie stukken = 0.60 = zestig procent eraf. */
        @Tunable(value = "Share (0-1) of damage each powered suit piece stops (three pieces = three times this)", max = 1)
        public static float POWERED_REDUCTION_PER_PIECE = 0.20f;
        /** Wat één punt tegengehouden schade kost. */
        @Tunable("Energy per point of damage the suit stops")
        public static float REDUCTION_COST_PER_DAMAGE = 60.0f;

        // ------------------------------------------------------------------------- Vliegen: verbruik
        /** Energie per seconde als je stilhangt. */
        @Tunable("Energy per second while hovering")
        public static float DRAIN_HOVER = 12.0f;
        /** Energie per seconde als je vooruit vliegt. */
        @Tunable("Energy per second while flying")
        public static float DRAIN_FLY = 26.0f;
        /** Energie per seconde met de naverbrander (sprinttoets). */
        @Tunable("Energy per second with the afterburner")
        public static float DRAIN_BOOST = 45.0f;

        // ------------------------------------------------------------------------- Vliegen: hoe het voelt
        // Snelheden in blokken per tick: 1 blok per tick = 20 blokken per seconde = 72 km/u.
        /** Vanaf deze snelheid "vlieg" je in plaats van te zweven (dat kost DRAIN_FLY). */
        @Tunable("From this speed you fly instead of hover (blocks per tick)")
        public static double CRUISE_SPEED = 0.25;
        /** Vanaf deze snelheid gaat de naverbrander mee (dat kost DRAIN_BOOST). */
        @Tunable(value = "From this speed the afterburner counts (blocks per tick)", min = 0.01)
        public static double BOOST_SPEED = 1.3;
        /** Stuwkracht per tick in de richting waarin je kijkt (W/S) of opzij (A/D). */
        @Tunable("Thrust per tick forward, back and sideways")
        public static double THRUST = 0.045;
        /** Zoveel keer sterker met de naverbrander. */
        @Tunable("How many times stronger the thrust is with the afterburner")
        public static double BOOST = 2.3;
        /** Stuwkracht omhoog (spatie). */
        @Tunable("Thrust per tick up (space)")
        public static double THRUST_UP = 0.04;
        /** Stuwkracht omlaag (shift). */
        @Tunable("Thrust per tick down (shift)")
        public static double THRUST_DOWN = 0.035;
        /** Hoeveel vaart je elke tick houdt (0.96 = 96%; lopend in de lucht is dat 91%). */
        @Tunable(value = "Share (0-1) of your speed you keep every tick (higher = glide longer)", max = 1)
        public static double KEEP_SPEED = 0.96;
        /** Zonder op/neer te sturen zakt je verticale vaart zo snel weg: het harnas houdt je op hoogte. */
        @Tunable(value = "Share (0-1) of your up/down speed you keep every tick when you don't steer up or down", max = 1)
        public static double HOVER_DAMPING = 0.8;
        /** Sneller dan dit gaat het niet... */
        @Tunable("Top speed (blocks per tick)")
        public static double MAX_SPEED = 1.3;
        /** ...en met de naverbrander niet sneller dan dit. */
        @Tunable("Top speed with the afterburner (blocks per tick)")
        public static double MAX_BOOST_SPEED = 2.6;

        // ------------------------------------------------------------------------- Landen
        /** Vlak boven de grond remt het harnas zelf af tot hooguit deze valsnelheid... */
        @Tunable("Close to the ground the suit brakes to this falling speed (blocks per tick)")
        public static double LANDING_SPEED = 0.3;
        /** ...vanaf zoveel blokken boven de grond. */
        @Tunable("How high above the ground the suit starts braking (blocks)")
        public static double LANDING_HEIGHT = 5.0;
        /** Valschade tot deze hoogte vangen de laarzen helemaal op. */
        @Tunable("Falls up to this height do no damage with the boots on (blocks)")
        public static float SAFE_FALL = 40.0f;
        /** Wat het opvangen van een val kost. */
        @Tunable("Energy cost of catching a fall")
        public static float LANDING_COST = 200.0f;

        // ------------------------------------------------------------------------- Repulsor
        /** Schade van één repulsorstoot (7 = 3,5 hartje). */
        @Tunable("Damage of one repulsor blast")
        public static float REPULSOR_DAMAGE = 7.0f;
        /** Wat één stoot kost. */
        @Tunable("Energy cost of one repulsor blast")
        public static float REPULSOR_COST = 200.0f;
        /** Wachttijd tussen twee stoten (20 = 1 seconde). */
        @Tunable(value = "Ticks between two repulsor blasts", min = 1)
        public static float REPULSOR_COOLDOWN = 20.0f;
        /** Hoe ver de stoot reikt. */
        @Tunable("How far a repulsor blast reaches (blocks)")
        public static double REPULSOR_RANGE = 24.0;
        /** Hoe ver naast de straal je nog geraakt wordt. */
        @Tunable("How far next to the beam you still get hit (blocks)")
        public static double REPULSOR_WIDTH = 1.2;
        /** Hoe hard hij wegduwt wat hij raakt. */
        @Tunable("How hard a repulsor blast pushes away what it hits")
        public static double REPULSOR_KNOCKBACK = 0.7;
        /** Hoe hard de stoot jou zelf naar achteren duwt (klein houden, anders schiet je jezelf weg). */
        @Tunable("How hard a repulsor blast pushes yourself back")
        public static double REPULSOR_RECOIL = 0.04;

        // ------------------------------------------------------------------------- Mini-raketten
        /** Hoeveel raketten er in één salvo zitten. */
        @Tunable(value = "Number of mini rockets in a salvo", min = 1, max = 64)
        public static float ROCKET_SALVO = 6.0f;
        /** Wat een heel salvo kost. */
        @Tunable("Energy cost of a mini rocket salvo")
        public static float ROCKET_COST = 900.0f;
        /** Herlaadtijd van het raketpak (240 = 12 seconden). */
        @Tunable("Reload time of the rocket pack (ticks)")
        public static float ROCKET_COOLDOWN = 240.0f;
        /** Zoveel ticks tussen twee raketten van hetzelfde salvo. */
        @Tunable("Ticks between two rockets of the same salvo")
        public static float ROCKET_INTERVAL = 2.0f;
        /** Hoe ver de raketten doelwitten zoeken (en vliegen zonder doelwit). */
        @Tunable("How far away mini rockets pick targets (blocks)")
        public static double ROCKET_RANGE = 48.0;
        /** Schade voor wat de raket raakt (5 = 2,5 hartje). */
        @Tunable("Damage of one mini rocket hit")
        public static float ROCKET_DAMAGE = 5.0f;
        /** Schade voor wie vlak naast de inslag staat... */
        @Tunable("Damage to others right next to a mini rocket impact")
        public static float ROCKET_SPLASH_DAMAGE = 2.0f;
        /** ...tot zover van de inslag. */
        @Tunable("How far from a mini rocket impact the splash damage reaches (blocks)")
        public static double ROCKET_SPLASH_RADIUS = 1.8;
        /** Snelheid bij het wegschieten. */
        @Tunable("Speed of a mini rocket when it leaves the pack (blocks per tick)")
        public static double ROCKET_START_SPEED = 0.45;
        /** Hoogste snelheid. */
        @Tunable("Top speed of a mini rocket (blocks per tick)")
        public static double ROCKET_MAX_SPEED = 1.6;
        /** Zoveel snelheid komt er elke tick bij. */
        @Tunable("Speed a mini rocket gains every tick")
        public static double ROCKET_ACCELERATION = 0.08;
        /** Zo lang vliegt een raket eerst rechtdoor voor hij gaat bijsturen: het salvo waaiert uit. */
        @Tunable("Ticks a mini rocket flies straight before it starts steering (so the salvo fans out)")
        public static float ROCKET_ARM_TICKS = 5.0f;
        /** Hoe scherp een raket bijstuurt (0 = niet, 1 = meteen recht op het doel). */
        @Tunable(value = "How sharp a mini rocket steers (0 = not at all, 1 = straight at the target at once)", max = 1)
        public static double ROCKET_TURN = 0.28;
        /** Dichter dan dit bij zijn doel stuurt hij steeds scherper, tot recht erop. */
        @Tunable(value = "Closer than this to its target a mini rocket steers sharper and sharper (blocks)", min = 0.1)
        public static double ROCKET_SHARP_TURN_DISTANCE = 6.0;
        /** Na zoveel ticks ontploft een raket vanzelf (100 = 5 seconden). */
        @Tunable("A mini rocket explodes by itself after this many ticks")
        public static float ROCKET_LIFETIME = 100.0f;

        // ------------------------------------------------------------------------- Vast (pantser)
        // VAST: het spel maakt het pantser bij het opstarten, dus deze staan niet in het .toml-bestand.
        /** Bescherming zonder stroom (goed metaal, meer niet): borststuk, broek, laarzen. */
        public static final int ARMOR_CHEST = 6;
        public static final int ARMOR_LEGS = 4;
        public static final int ARMOR_BOOTS = 3;
        /** Hoe goed het harnas zware klappen opvangt (net als netherite). */
        public static final float TOUGHNESS = 2.0f;
        /** Hoe goed het je op de been houdt bij een klap. */
        public static final float KNOCKBACK_RESISTANCE = 0.1f;
    }

    // =============================================================================================== Workbench

    @Section("The suit workbench, and what each suit upgrade does (its power use is under [power])")
    public static final class Workbench {
        private Workbench() {}

        /** Hoe lang het bouwen van een stuk harnas duurt (200 = 10 seconden). */
        @Tunable(value = "How long building a suit piece takes (ticks)", min = 1)
        public static float BUILD_TICKS = 200.0f;
        /** Hoe lang het installeren van een upgrade duurt. */
        @Tunable(value = "How long installing an upgrade takes (ticks)", min = 1)
        public static float INSTALL_TICKS = 100.0f;

        // ------------------------------------------------------------------------- Wat elke upgrade doet
        /** Extra vliegsnelheid per straalmotor (0.12 = 12%). */
        @Tunable("Extra flight speed per thruster upgrade (0.12 = 12%)")
        public static double THRUSTER_SPEED = 0.12;
        /** Minder vliegenergie per straalmotor (0.15 = 15%). */
        @Tunable(value = "Less flight energy per thruster upgrade (0.15 = 15%)", max = 1)
        public static double THRUSTER_SAVING = 0.15;
        /** Extra deel van de schade dat het harnas met stroom tegenhoudt, per pantserplaat. */
        @Tunable(value = "Extra share of damage the powered suit stops per armor plating", max = 1)
        public static double PLATING_REDUCTION = 0.05;
        /** Extra repulsorschade per condensator (0.35 = 35%). */
        @Tunable("Extra repulsor damage per capacitor (0.35 = 35%)")
        public static double CAPACITOR_DAMAGE = 0.35;
        /** Extra raketschade (raak en ernaast) per kernkop (0.4 = 40%). */
        @Tunable("Extra mini rocket damage (direct hit and splash) per warhead (0.4 = 40%)")
        public static double WARHEAD_DAMAGE = 0.4;
        /** Extra raketten per salvo per magazijn. */
        @Tunable("Extra mini rockets per salvo per rocket magazine")
        public static float MAGAZINE_ROCKETS = 2.0f;

        // ------------------------------------------------------------------------- Hoeveel er in één stuk passen
        @Tunable(value = "How many thruster upgrades fit in the boots", max = 16)
        public static float MAX_THRUSTERS = 3.0f;
        @Tunable(value = "How many armor platings fit in one suit piece", max = 16)
        public static float MAX_PLATING = 2.0f;
        @Tunable(value = "How many capacitors fit in the chest piece", max = 16)
        public static float MAX_CAPACITORS = 2.0f;
        @Tunable(value = "How many warheads fit in the rocket pack", max = 16)
        public static float MAX_WARHEADS = 3.0f;
        @Tunable(value = "How many rocket magazines fit in the rocket pack", max = 16)
        public static float MAX_MAGAZINES = 3.0f;
    }

    // =============================================================================================== Turrets

    @Section("Turrets: the Stark turret you build, the Hydra turret (bunker) and the security turret (Stark Tower). "
            + "The Stark turret's power use is under [power]")
    public static final class Turrets {
        private Turrets() {}

        // ------------------------------------------------------------------------- Stark-torentje
        /** Tot hoe ver hij doelwitten ziet en met de laser raakt. */
        @Tunable("How far a Stark turret's laser reaches (blocks)")
        public static double RANGE = 16.0;
        /** Laserschade per schot (4 = 2 hartjes). */
        @Tunable("Damage of one Stark turret laser shot")
        public static float LASER_DAMAGE = 4.0f;
        /** Ticks tussen twee laserschoten (20 = 1 seconde). */
        @Tunable("Ticks between two Stark turret laser shots")
        public static float LASER_INTERVAL = 20.0f;
        /** Raketten (munitie in het torentje) reiken iets verder dan de laser. */
        @Tunable("How far a Stark turret fires rockets (blocks)")
        public static double ROCKET_RANGE = 24.0;
        /** Ticks tussen twee raketten. */
        @Tunable("Ticks between two turret rockets")
        public static float ROCKET_INTERVAL = 50.0f;
        /** Zoveel raketten passen er in het torentje. */
        @Tunable(value = "How many rockets fit in a Stark turret", min = 1, max = 64)
        public static float MAX_AMMO = 64.0f;

        // ------------------------------------------------------------------------- Hydra-torentje (geen stroom nodig)
        @Tunable("Damage of one Hydra turret laser shot")
        public static float HYDRA_LASER_DAMAGE = 3.0f;
        @Tunable("Ticks between two Hydra turret laser shots")
        public static float HYDRA_LASER_INTERVAL = 25.0f;
        @Tunable("How far a Hydra turret reaches (blocks)")
        public static double HYDRA_RANGE = 14.0;

        // ------------------------------------------------------------------------- Beveiligingstorentje (Stark Tower)
        @Tunable("Damage of one Stark Tower security turret shot")
        public static float SECURITY_LASER_DAMAGE = 3.0f;
        @Tunable("Ticks between two security turret shots")
        public static float SECURITY_LASER_INTERVAL = 20.0f;
        @Tunable("How far a security turret reaches (blocks)")
        public static double SECURITY_RANGE = 16.0;

        // ------------------------------------------------------------------------- Draaien en zoeken (alle torentjes)
        /** Hoe vaak hij rondkijkt naar een nieuw doelwit als hij er geen heeft. */
        @Tunable(value = "How often a turret without a target looks for a new one (ticks)", min = 1)
        public static float SCAN_INTERVAL = 10.0f;
        /** Zoveel graden per tick draait de kop hooguit (alleen voor het beeld). */
        @Tunable("Degrees per tick a turret head turns at most (only how it looks)")
        public static float TURN_SPEED = 14.0f;
        /** Zo snel draait hij rond als hij niemand ziet. */
        @Tunable("Degrees per tick a turret turns around when it sees nobody")
        public static float IDLE_TURN_SPEED = 1.2f;
    }

    // =============================================================================================== Security

    @Section("The security core (Stark Tower or your own): its reach and zone, its drones and its computer system. "
            + "Its power use is under [power]")
    public static final class Security {
        private Security() {}

        /**
         * Zover reikt een beveiligingskern, in elke richting (ook omhoog en omlaag): zo ver mag zijn zone
         * liggen, en zo ver horen zijn apparaten bij hem (torentjes, camera's, lezers, codepanelen,
         * serverkasten, laserwanden, het landingsplatform). Twee kernen staan minstens 2 x dit + 1 uit elkaar.
         */
        @Tunable(value = "How far (blocks, in every direction) a security core reaches: its protected zone and all its devices "
                + "(turrets, cameras, keycard readers, code panels, server racks, laser walls). "
                + "Two cores must stand at least twice this far apart.", min = 1, max = 1_000)
        public static float ZONE_REACH = 200.0f;

        // ------------------------------------------------------------------------- De oude drones van de kern
        // Tot hoofdstuk 25 maakte een kern zelf drones; die maakt hij niet meer, maar oude drones uit een bestaande
        // wereld vliegen nog (zie SecurityDroneEntity). Een security drone die je zelf bouwt, staat onder [robots].
        /** Schade van één laserschot. */
        @Tunable("Damage of one laser shot of an old patrol drone (from before you built your own security drones)")
        public static float DRONE_LASER_DAMAGE = 3.0f;
        /** Ticks tussen twee schoten. */
        @Tunable("Ticks between two security drone shots")
        public static float DRONE_FIRE_INTERVAL = 30.0f;
        /** Tot hoe ver een drone indringers ziet en raakt. */
        @Tunable("How far a security drone sees and shoots intruders (blocks)")
        public static double DRONE_RANGE = 16.0;
        /** Zover van de kern patrouilleert een drone als hij niemand ziet. */
        @Tunable("How far from the core a security drone patrols when it sees nobody (blocks)")
        public static double PATROL_RADIUS = 10.0;
        /** Hoe hard hij bijstuurt (zoals een raiddrone, iets rustiger). */
        @Tunable("How hard a security drone steers (higher = more agile)")
        public static double ACCELERATION = 0.05;

        // ------------------------------------------------------------------------- Het systeem (Stark OS)
        /** Zo lang mag wie een valstrik-bestand opende niet meer op dat systeem (600 = 30 seconden). */
        @Tunable("How long a trap file locks its reader out of that system (ticks)")
        public static float TRAP_LOCKOUT = 600.0f;
        /** Zo vaak mag je een fout wachtwoord proberen (bij inbreken via SSH)... */
        @Tunable(value = "How many wrong passwords you may try when you break into a system", min = 1)
        public static float PASSWORD_ATTEMPTS = 5.0f;
        /** ...en zo lang ben je daarna geblokkeerd (milliseconden: 4000 = 4 seconden). */
        @Tunable("How long you are locked out after too many wrong passwords (milliseconds)")
        public static float PASSWORD_LOCKOUT_MILLIS = 4_000.0f;

        /** VAST: levens van een beveiligingsdrone (8 = 4 hartjes). Het spel heeft dit nodig bij het opstarten. */
        public static final double DRONE_HEALTH = 8.0;
        /** VAST: zoveel kubussen kan één zone hebben (dit gaat mee in de berichten naar de spelers). */
        public static final int ZONE_MAX_BOXES = 16;
    }

    // =============================================================================================== Logic

    @Section("Server racks and the datacenter: how fast the redstone programs run, E.D.I.T.H.'s brain, heat, wear, fire "
            + "and backups. A rack's power use is under [power]")
    public static final class Logic {
        private Logic() {}

        // ------------------------------------------------------------------------- Snelheid en ruimte
        /** Zo lang wacht een server met kracht 1 (één CPU) voordat hij reageert; 2 x de kracht = de helft. */
        @Tunable("How long a server with Logic power 1 (one CPU) waits before it reacts to a trigger (ticks); "
                + "twice the power = half the time, never below 1 tick")
        public static float REACTION_TICKS = 10.0f;
        /** Hoeveel één GPU telt voor Logic (een CPU telt 1). */
        @Tunable("How much one GPU counts for Logic (a CPU counts 1)")
        public static double GPU_LOGIC_POWER = 0.5;
        /** Hoeveel één GPU telt voor het brein van E.D.I.T.H. (een CPU telt 1). */
        @Tunable("How much one GPU counts for E.D.I.T.H.'s brain (a CPU counts 1)")
        public static double GPU_BRAIN_POWER = 2.0;
        /** Zo lang denkt E.D.I.T.H. extra na op een datacenter met breinkracht 1 (160 = 8 seconden). */
        @Tunable("How long E.D.I.T.H. thinks extra on a datacenter with brain power 1 (one CPU), in ticks; "
                + "twice the power = half the time")
        public static float BRAIN_DELAY_TICKS = 160.0f;
        /** Is het nadenken zo kort of korter, dan valt het helemaal weg: het datacenter is sterk genoeg. */
        @Tunable("A brain delay this short or shorter (ticks) falls away completely: the datacenter is strong enough")
        public static float BRAIN_DELAY_GONE_TICKS = 10.0f;
        /** Zoveel blokken per seconde kan één CPU afwerken voordat hij op 100% zit (meer werk = meer warmte). */
        @Tunable(value = "How many blocks per second one Logic power (one CPU) can work through before the CPU use is 100% "
                + "(more work = more heat)", min = 1)
        public static float LOGIC_STEPS_PER_SECOND = 100.0f;
        /** Hoeveel diagrammen tegelijk kunnen lopen per RAM-latje. */
        @Tunable("How many diagrams can be busy at the same time per RAM stick")
        public static float RUNS_PER_RAM = 2.0f;
        /** Hoeveel blokken (triggers, ifs, acties) één SSD kan bewaren. */
        @Tunable("How many blocks (triggers, ifs, actions) one SSD can hold")
        public static float BLOCKS_PER_SSD = 24.0f;
        /** Zoveel backups van één netwerk blijven op één datacenter staan (de oudste gaat eraf). */
        @Tunable(value = "How many backups of one network a datacenter keeps (the oldest goes first)", min = 1, max = 64)
        public static float BACKUPS_KEPT = 3.0f;

        // ------------------------------------------------------------------------- Warmte
        /** Warmte (watt) van één CPU die niets doet... */
        @Tunable("Heat (watts) one CPU makes when it does nothing")
        public static double CPU_HEAT = 2.0;
        /** ...en extra als hij zo hard werkt als hij kan. */
        @Tunable("Extra heat (watts) one CPU makes when it works as hard as it can")
        public static double CPU_BUSY_HEAT = 2.0;
        /** Warmte (watt) van één GPU die niets doet... */
        @Tunable("Heat (watts) one GPU makes when it does nothing")
        public static double GPU_HEAT = 3.0;
        /** ...en extra als hij zo hard werkt als hij kan. */
        @Tunable("Extra heat (watts) one GPU makes when it works as hard as it can")
        public static double GPU_BUSY_HEAT = 4.0;
        /** Warmte (watt) van één RAM-latje of SSD. */
        @Tunable("Heat (watts) one RAM stick or SSD makes")
        public static double MEMORY_HEAT = 0.5;
        /** Zoveel warmte (watt) voert één fan af (alleen in zijn eigen kast). */
        @Tunable(value = "Heat (watts) one fan takes away (only in its own server rack)", min = 0.1)
        public static double FAN_COOLING = 6.0;
        /** Een kast ZONDER fan wordt elke twee seconden zoveel graden warmer per watt: elke kast met onderdelen heeft een fan nodig. */
        @Tunable("Degrees a server rack WITHOUT a fan gets warmer every two seconds, for every watt its parts make: "
                + "the heat cannot get out, so every rack with parts in it needs a fan")
        public static double NO_FAN_HEATING = 0.25;
        /** Temperatuur van een kast die niet meer warmte maakt dan zijn fans afvoeren. */
        @Tunable("Temperature (degrees) of a server rack that makes no more heat than its fans cool away")
        public static double AMBIENT_TEMPERATURE = 25.0;
        /** Zoveel graden warmer per watt die de fans niet afvoeren. */
        @Tunable(value = "Degrees warmer for every watt of heat that the fans of a rack do not cool away", min = 0.1)
        public static double DEGREES_PER_WATT = 8.0;
        /** Hoe snel de temperatuur meegaat, elke twee seconden (0.08 = ongeveer een minuut om er te komen). */
        @Tunable(value = "How fast the temperature of a rack with fans follows the heat, every two seconds "
                + "(0.08 = about a minute to get there)", max = 1)
        public static double HEAT_RATE = 0.08;
        /** Vanaf deze temperatuur is een kast warm: de hele server werkt op halve snelheid en E.D.I.T.H. waarschuwt. */
        @Tunable("From this temperature a rack is warm: the whole server works at half speed and E.D.I.T.H. warns you")
        public static double WARM_TEMPERATURE = 70.0;
        /** Zoveel trager werkt de server zolang een van zijn kasten warm is (2 = halve snelheid). */
        @Tunable(value = "How much slower the server works while one of its racks is warm (2 = half speed)", min = 1)
        public static double WARM_SLOWDOWN = 2.0;
        /** Vanaf deze temperatuur is een kast heet: zijn onderdelen slijten en gaan kapot. */
        @Tunable("From this temperature a rack is hot: its parts wear out and break")
        public static double HOT_TEMPERATURE = 90.0;
        /** Zoveel slijtage kan een onderdeel hebben; boven heet krijgt elk onderdeel er 1 of meer bij elke twee seconden. */
        @Tunable(value = "How much wear a part can take before it breaks; above the hot temperature every part gets 1 or more "
                + "every two seconds (half of the time)", min = 1)
        public static float MAX_WEAR = 20.0f;
        /** Vanaf deze temperatuur kan een kast in brand vliegen... */
        @Tunable("From this temperature a server rack can catch fire")
        public static double FIRE_TEMPERATURE = 110.0;
        /** ...als hij zo lang zo heet blijft (200 = 10 seconden)... */
        @Tunable("How long (ticks) a server rack must stay at the fire temperature before it catches fire")
        public static float FIRE_DELAY_TICKS = 200.0f;
        /** ...en dan brandt hij zo lang voordat hij weg is, met alles erin (tijd om je onderdelen te redden). */
        @Tunable("How long (ticks) a burning server rack burns before it is gone, with everything in it "
                + "(time to take your parts out)")
        public static float BURN_TICKS = 200.0f;

        /** VAST: zoveel vakjes heeft één serverkast (twee kolommen van drie): zo ziet het scherm eruit. */
        public static final int RACK_SLOTS = 6;
        /** VAST: zoveel vakjes staan er onder elkaar in één kolom. */
        public static final int RACK_ROWS = 3;
    }

    // =============================================================================================== C4

    /*
     * Hoe de klap werkt: één C4 doet POWER "breekpunten" vlak bij de lading, en dat loopt af tot 0 op
     * RADIUS blokken. Elk blok kan een aantal breekpunten hebben voor het breekt: zwak (hout, glas,
     * aarde) WEAK, gewoon (steen, baksteen) NORMAL, sterk (obsidiaan) STRONG, de beveiligingskern CORE.
     * Wat niet breekt krijgt barsten: de volgende klap telt erbij op, tot je ze herstelt (zie Repair).
     * Zo maakt één C4 een gat in een stenen muur van één dik, en heb je voor obsidiaan er drie nodig.
     */
    @Section("C4, and TNT and the satellite against the blocks of a protected zone. A blast does 'breach points' that "
            + "fall to 0 at the blast radius; a block breaks when it took its breach points, otherwise it gets cracks")
    public static final class C4 {
        private C4() {}

        // ------------------------------------------------------------------------- De klap
        /** Breekpunten van één C4 vlak bij de lading. */
        @Tunable("Breach points of one C4 right at the charge (falling to 0 at the blast radius)")
        public static float POWER = 10.0f;
        /** Hoe ver de klap reikt. */
        @Tunable("How far a C4 blast reaches (blocks)")
        public static float RADIUS = 3.5f;
        /** Hoe hard de klap spelers en wezens raakt (TNT = 4). */
        @Tunable("How hard a C4 blast hits players and mobs (TNT is 4)")
        public static float ENTITY_POWER = 4.0f;
        /** Andere C4 in de klap gaat zoveel ticks later ook af (een kettingreactie). */
        @Tunable("Other C4 in the blast goes off this many ticks later (a chain reaction)")
        public static float CHAIN_DELAY = 3.0f;

        // ------------------------------------------------------------------------- Hoe sterk blokken zijn
        @Tunable("Breach points a weak block (wood, glass, dirt, wool, sand...) takes before it breaks")
        public static float WEAK = 2.0f;
        @Tunable("Breach points a normal block (stone, bricks, deepslate, iron...) takes before it breaks")
        public static float NORMAL = 6.0f;
        @Tunable("Breach points a strong block (obsidian, netherite, the laser emitter...) takes before it breaks")
        public static float STRONG = 20.0f;
        @Tunable("Breach points the security core takes before it breaks (about 6 C4)")
        public static float CORE = 48.0f;
        /** Vanaf deze explosieweerstand (zoals in Minecraft) is een blok "gewoon"; daaronder zwak. */
        @Tunable("From this explosion resistance (as in Minecraft) a block counts as normal; below it, weak")
        public static float NORMAL_RESISTANCE = 4.0f;
        /** Vanaf deze explosieweerstand is een blok "sterk" (obsidiaan heeft 1200, steen 6). */
        @Tunable("From this explosion resistance a block counts as strong (obsidian has 1200, stone 6)")
        public static float STRONG_RESISTANCE = 100.0f;

        // ------------------------------------------------------------------------- TNT en de satelliet in een zone
        // In een zone werken ze met breekpunten, net als C4, maar zwakker ("TNT doet heel weinig aan de muur").
        @Tunable("Breach points of one TNT on the blocks of a protected zone (C4 is 10)")
        public static float TNT_BREACH = 3.0f;
        @Tunable("Breach points of one satellite bombardment impact on the blocks of a protected zone (C4 is 10)")
        public static float SATELLITE_BREACH = 6.0f;
        /** Zo ver reikt de klap van TNT of een satellietinslag in een zone. */
        @Tunable("How far the blast of TNT or a satellite impact reaches in a protected zone (blocks)")
        public static float EXPLOSION_BREACH_RADIUS = 3.0f;

        // ------------------------------------------------------------------------- Detonator en timer
        /** Hoe ver het signaal van een detonator reikt. */
        @Tunable("How far a detonator's signal reaches (blocks)")
        public static float DETONATOR_RANGE = 128.0f;
        /** De kortste tijd die je op een C4 (Timer) kunt zetten (seconden)... */
        @Tunable(value = "Shortest time you can set on a C4 (Timer) (seconds)", min = 1)
        public static float TIMER_MIN = 5.0f;
        /** ...de langste... */
        @Tunable(value = "Longest time you can set on a C4 (Timer) (seconds)", min = 1)
        public static float TIMER_MAX = 120.0f;
        /** ...en waar een nieuwe C4 (Timer) op staat. */
        @Tunable(value = "Time a new C4 (Timer) starts with (seconds)", min = 1)
        public static float TIMER_DEFAULT = 10.0f;
    }

    // =============================================================================================== Repair

    @Section("Repairing cracks: the Crusher and the Compactor, and Repair Paste (their power use is under [power])")
    public static final class Repair {
        private Repair() {}

        /** Hoe lang de Crusher en de Compactor over één item doen (100 = 5 seconden). */
        @Tunable(value = "How long the Crusher and the Compactor take for one item (ticks)", min = 1)
        public static float MACHINE_TICKS = 100.0f;
        /** Barsten genezen niet vanzelf. Elke keer opvullen met Repair Paste maakt het blok zoveel zwakker (0.15 = 15%)... */
        @Tunable(value = "How much weaker a block gets each time its cracks are filled with Repair Paste (0.15 = 15%)", max = 1)
        public static float PATCH_WEAKER = 0.15f;
        /** ...maar nooit zwakker dan dit deel van zijn normale sterkte (0.4 = 40%). */
        @Tunable(value = "Filling cracks never makes a block weaker than this (0.4 = 40% of its normal strength)", max = 1)
        public static float PATCH_WEAKEST = 0.40f;
    }

    // =============================================================================================== Bypass

    @Section("Breaking in with the Bypass Kit (keycard readers and code panels), and picking pockets. "
            + "Lists: the keycard level 1 to 5 you fake")
    public static final class Bypass {
        private Bypass() {}

        /** Zo dichtbij moet je blijven tijdens een hack; verder weg = de hack stopt. */
        @Tunable("How close you must stay to a reader or panel while you hack it (blocks)")
        public static double REACH = 6.0;

        // ------------------------------------------------------------------------- Het stroomcircuit (keycard-lezer), per kaartniveau 1 t/m 5
        /** Hoeveel seconden je krijgt, per niveau van de kaart die je namaakt. */
        @Tunable(value = "Seconds you get to rewire a keycard reader for card level 1 to 5", min = 10, max = 600)
        public static float[] CIRCUIT_SECONDS = {60.0f, 55.0f, 50.0f, 45.0f, 40.0f};
        /** Hoe breed het raster is, per niveau... */
        @Tunable(value = "Circuit width (columns) for card level 1 to 5", min = 2, max = 12)
        public static float[] CIRCUIT_COLUMNS = {5.0f, 6.0f, 6.0f, 7.0f, 8.0f};
        /** ...hoe hoog... */
        @Tunable(value = "Circuit height (rows) for card level 1 to 5", min = 2, max = 12)
        public static float[] CIRCUIT_ROWS = {4.0f, 4.0f, 5.0f, 5.0f, 6.0f};
        /** ...en hoeveel ALARM-punten erlangs zitten. */
        @Tunable(value = "ALARM points along the circuit for card level 1 to 5", max = 10)
        public static float[] CIRCUIT_ALARMS = {1.0f, 1.0f, 2.0f, 2.0f, 3.0f};
        /** Zo lang mag het signaal ALARM raken voordat het alarm afgaat (20 = 1 seconde). */
        @Tunable("How long the signal may touch ALARM before the alarm goes off (ticks)")
        public static float ALARM_GRACE_TICKS = 20.0f;

        // ------------------------------------------------------------------------- Vingerafdrukken (codepaneel)
        /** Pogingen om de volgorde te vinden: de lengte van de code plus zoveel. */
        @Tunable("Tries to find the order of a code panel's digits: the code length plus this many")
        public static float ORDER_EXTRA_TRIES = 2.0f;

        // ------------------------------------------------------------------------- Na een mislukte hack
        /** Zo lang negeert die lezer of dat paneel de hacker. */
        @Tunable("After a failed hack, that reader or panel ignores the hacker for this long (seconds)")
        public static float LOCKOUT_SECONDS = 60.0f;

        // ------------------------------------------------------------------------- Zakkenrollen
        /** Zo dichtbij moet je zijn. */
        @Tunable("How close you must be to pick someone's pocket (blocks)")
        public static double PICKPOCKET_REACH = 2.5;
        /** Loopt het slachtoffer verder weg dan dit, dan houdt het op (zonder betrapt te zijn). */
        @Tunable("If the victim walks further away than this, the pickpocketing stops (blocks)")
        public static double POCKET_LEAVE = 3.5;
        /** Zoveel keycards liggen er hooguit in een zak (heeft iemand er meer, dan zie je er zoveel). */
        @Tunable(value = "Most keycards you see in someone's pocket", min = 1, max = 8)
        public static float POCKET_MAX_CARDS = 6.0f;
        /** Zo lang voordat je dezelfde speler opnieuw mag proberen, wat er ook gebeurde. */
        @Tunable("How long before you may try the same player's pockets again (seconds), whatever happened")
        public static float PICKPOCKET_COOLDOWN_SECONDS = 30.0f;
        /** Zo lang licht een betrapte zakkenroller op, zodat het slachtoffer de dief kan vinden. */
        @Tunable("How long a caught pickpocket glows, so the victim can find them (seconds)")
        public static float PICKPOCKET_GLOW_SECONDS = 10.0f;

        /** VAST: zo vaak gaat een Bypass Kit mee (elke hack kost er één). Het spel heeft dit nodig bij het opstarten. */
        public static final int KIT_USES = 16;
    }

    // =============================================================================================== Lights

    @Section("The Arc Lamp (its power use is under [power])")
    public static final class Lights {
        private Lights() {}

        /**
         * Het hoogste lichtniveau dat je een lamp kunt geven, en waar een nieuwe lamp mee begint. Een gewone
         * lamp geeft hooguit 15. Een Arc Lamp op 100 geeft tot 85 blokken ver vol licht, dat daarna in 15
         * blokken uitdooft.
         */
        @Tunable(value = "The highest light level you can set an Arc Lamp to (new lamps start here). Normal lamps stop at 15. "
                + "Full light up to this minus 15 blocks away, then it fades out like normal light.", min = 15, max = 128)
        public static float ARC_LAMP_MAX_LEVEL = 100.0f;
    }

    // =============================================================================================== Robots

    @Section("Guard robots (chapter 21): what each part does. Every list has 4 numbers, one per style, in this "
            + "order: Commando, Trainer, Battle, Super Battle")
    public static final class Robots {
        private Robots() {}

        /** Levenspunten, naar de stijl van het lijf (20 = 10 hartjes). */
        @Tunable(value = "Health of a robot, by the style of its body (Commando, Trainer, Battle, Super Battle)",
                min = 1, max = 1_000)
        public static float[] BODY_HEALTH = {30.0f, 24.0f, 20.0f, 44.0f};
        /** Pantser, naar de stijl van het lijf. */
        @Tunable(value = "Armor of a robot, by the style of its body", max = 30)
        public static float[] BODY_ARMOR = {6.0f, 4.0f, 2.0f, 10.0f};
        /** Hoeveel energie de batterij hoogstens heeft, naar de stijl van het lijf. */
        @Tunable(value = "Battery of a robot (energy), by the style of its body", min = 1, max = 1_000_000)
        public static float[] BODY_BATTERY = {4000.0f, 6000.0f, 3000.0f, 8000.0f};
        /** Hoe snel hij loopt (0,25 is zo snel als een zombie), naar de stijl van de benen. */
        @Tunable(value = "Walking speed of a robot (0.25 = a zombie), by the style of its legs", max = 1)
        public static float[] LEGS_SPEED = {0.30f, 0.27f, 0.25f, 0.20f};
        /** Ticks tussen twee schoten, naar de stijl van de armen (hoofdstuk 24). */
        @Tunable(value = "Ticks between two shots of a robot, by the style of its arms", min = 1, max = 1_200)
        public static float[] ARMS_FIRE_INTERVAL = {24.0f, 30.0f, 36.0f, 18.0f};
        /** Hoe ver hij ziet (blokken), naar de stijl van het hoofd (hoofdstuk 24). */
        @Tunable(value = "How far a robot sees (blocks), by the style of its head", min = 1, max = 64)
        public static float[] HEAD_SCAN_RANGE = {14.0f, 12.0f, 10.0f, 12.0f};
        /** Hoe breed hij kijkt (graden; 360 = rondom), naar de stijl van het hoofd (hoofdstuk 24). */
        @Tunable(value = "How wide a robot looks (degrees, 360 = all around), by the style of its head", min = 10, max = 360)
        public static float[] HEAD_SCAN_ANGLE = {120.0f, 360.0f, 90.0f, 70.0f};
        /** De kans dat een onderdeel heel blijft als je een robot echt kapotmaakt (0 = nooit, 1 = altijd). */
        @Tunable(value = "Chance that each part survives when a robot is destroyed (0 = never, 1 = always)", max = 1)
        public static float PART_SURVIVE_CHANCE = 0.5f;
        /** Hoeveel stroom per tick een Robot Dock uit het net in de robot op hem stopt. */
        @Tunable(value = "Energy per tick a robot dock takes from the grid and puts into the robot standing on it", max = 1_000)
        public static float DOCK_CHARGE_RATE = 5.0f;
        /** Hoeveel van zijn batterij een robot per tick gebruikt als hij aan staat. */
        @Tunable(value = "Battery a robot uses per tick while it is switched on", max = 100)
        public static float DRAIN_ON = 0.25f;
        /** Hoeveel er per tick bij komt terwijl hij loopt. */
        @Tunable(value = "Extra battery a robot uses per tick while it walks", max = 100)
        public static float DRAIN_WALKING = 0.5f;
        /** Hoeveel blokken een robot uit zijn dock stapt als je hem aanzet. */
        @Tunable(value = "How many blocks a robot walks out of its dock when you switch it on", max = 16)
        public static float STEP_OUT = 2.0f;

        // Programma's (hoofdstuk 23): het brein en het lijf van een robot.
        /** Zoveel blokjes doet één when van een robotprogramma hooguit per tick (de rest de volgende tick). */
        @Tunable(value = "Robot program: blocks one when may do per tick (the rest waits for the next tick)", min = 1, max = 1_000)
        public static float STEPS_PER_TICK = 32.0f;
        /** Zoveel whens mogen er tegelijk bezig zijn op één robot. */
        @Tunable(value = "Robot program: how many whens may run at the same time on one robot", min = 1, max = 64)
        public static float MAX_RUNS = 8.0f;
        /** Hoe snel zijn programma hem laat lopen: keer de snelheid van zijn benen (0,45 x 0,25 = 2,25 blokken per seconde). */
        @Tunable(value = "How fast a robot walks for its program, times the speed of its legs (0.45 x 0.25 = 2.25 blocks a second)",
                min = 0.05, max = 4)
        public static float WALK_FACTOR = 0.45f;
        /** Zolang (seconden) probeert hij een stap voor hij geblokkeerd is. */
        @Tunable(value = "Seconds a robot keeps trying one step before it counts as blocked", min = 0.25, max = 60)
        public static float STUCK_SECONDS = 2.0f;
        /** Zolang duurt een kwartslag. */
        @Tunable(value = "Seconds a quarter turn of a robot takes", max = 5)
        public static float TURN_SECONDS = 0.25f;
        /** Zolang mag goto() (of de weg naar het dock en terug bij charge()) duren voor hij het opgeeft. */
        @Tunable(value = "Seconds goto() (or the walk to the dock and back for charge()) may take before the robot gives up",
                min = 1, max = 600)
        public static float GOTO_SECONDS = 60.0f;

        // Wapens (hoofdstuk 24): de Laser- en Gun-module. Hoe snel hij schiet, hangt aan zijn armen (ARMS_FIRE_INTERVAL).
        /** Schade van één laserschot van een robot (5 = 2,5 hartjes). */
        @Tunable(value = "Damage of one robot laser shot", max = 100)
        public static float LASER_DAMAGE = 5.0f;
        /** Hoeveel van zijn batterij één laserschot kost. */
        @Tunable(value = "Battery one robot laser shot costs", max = 10_000)
        public static float LASER_ENERGY = 60.0f;
        /** Schade van één kogel uit de Gun-module (een kogel is één Robot Ammo). */
        @Tunable(value = "Damage of one robot gun shot (each shot uses one Robot Ammo)", max = 100)
        public static float GUN_DAMAGE = 6.0f;
        /** Hoe ver de laser en het geweer van een robot reiken. */
        @Tunable(value = "How far a robot laser or gun reaches (blocks)", min = 1, max = 64)
        public static float SHOT_RANGE = 24.0f;
        /** Zoveel Robot Ammo past er in een robot. */
        @Tunable(value = "How much Robot Ammo fits in a robot", min = 1, max = 1_000)
        public static float MAX_AMMO = 64.0f;
        // De Ion Blaster (hoofdstuk 24): robots neerleggen zonder ze kapot te maken.
        /** Zolang (seconden) ligt een robot neer na een treffer van de Ion Blaster. */
        @Tunable(value = "Seconds a robot stays down after an Ion Blaster hit", min = 1, max = 600)
        public static float ION_DOWN_SECONDS = 60.0f;
        /** Hoe ver de Ion Blaster reikt (blokken). */
        @Tunable(value = "How far the Ion Blaster reaches (blocks)", min = 1, max = 64)
        public static float ION_RANGE = 24.0f;
        /** Zoveel ticks tussen twee schoten van de Ion Blaster. */
        @Tunable(value = "Ticks between two Ion Blaster shots", min = 1, max = 1_200)
        public static float ION_COOLDOWN = 30.0f;
        /** Zoveel stroom per tick stopt de Solar Panel-module in zijn batterij, in de volle middagzon. */
        @Tunable(value = "Energy per tick the Solar Panel module puts into a robot's battery in full midday sun", max = 100)
        public static float SOLAR_CHARGE = 0.5f;

        // Solderen (hoofdstuk 22): de hittebalk en wat er misgaat.
        /** Zoveel ticks duurt het tot de hittebalk vol is. */
        @Tunable(value = "Soldering: ticks the heat bar takes to fill up", min = 5, max = 400)
        public static float SOLDER_FULL_TICKS = 40.0f;
        /** Laat je los voor dit deel van de balk, dan pakt het soldeer niet. */
        @Tunable(value = "Soldering: let go below this share of the heat bar (0-1) and the solder does not hold", max = 1)
        public static float SOLDER_COLD = 0.45f;
        /** Boven dit deel van de balk is het te heet: het soldeer loopt vaak naar de pin ernaast. */
        @Tunable(value = "Soldering: above this share of the heat bar (0-1) the solder may run to the next pin", max = 2)
        public static float SOLDER_HOT = 0.75f;
        /** De kans op een brug als je in het groene vak loslaat. */
        @Tunable(value = "Soldering: chance of a solder bridge when you let go in the green zone", max = 1)
        public static float BRIDGE_CHANCE_GOOD = 0.03f;
        /** De kans op een brug als je te lang vasthield. */
        @Tunable(value = "Soldering: chance of a solder bridge when you held too long", max = 1)
        public static float BRIDGE_CHANCE_HOT = 0.7f;
        /** De kans dat een kortsluiting de hele robot laat afbranden (anders alleen de modules). */
        @Tunable(value = "Chance that a short circuit burns down the whole robot (otherwise only the modules on those pins)", max = 1)
        public static float BURN_CHANCE = 0.25f;

        // Security drones (hoofdstuk 25): solderen, schieten, de Ion Blaster en hun programma werken zoals bij een
        // robot (de getallen hierboven); dit zijn de getallen van de drone zelf.
        /** Zoveel leven heeft een security drone. */
        @Tunable(value = "Health of a security drone", min = 1, max = 1_000)
        public static float DRONE_HEALTH = 16.0f;
        /** Zoveel stroom past er in zijn batterij. */
        @Tunable(value = "Battery of a security drone (energy)", min = 1, max = 1_000_000)
        public static float DRONE_BATTERY = 3000.0f;
        /** Zoveel stroom per tick haalt een Drone Dock uit het net, voor de drone die erop staat. */
        @Tunable(value = "Energy per tick a drone dock takes from the grid and puts into the security drone on it", max = 1_000)
        public static float DRONE_DOCK_CHARGE_RATE = 5.0f;
        /** Zoveel batterij per tick als hij aan staat. */
        @Tunable(value = "Battery a security drone uses per tick while it is switched on", max = 100)
        public static float DRONE_DRAIN_ON = 0.25f;
        /** Zoveel extra per tick als hij vliegt. */
        @Tunable(value = "Extra battery a security drone uses per tick while it flies", max = 100)
        public static float DRONE_DRAIN_FLYING = 0.6f;
        /** Zo snel vliegt hij (blokken per tick). */
        @Tunable(value = "How fast a security drone flies (blocks per tick; 0.15 = 3 blocks a second)", min = 0.01, max = 2)
        public static float DRONE_SPEED = 0.15f;
        /** Zo ver kijkt zijn Scan-module. */
        @Tunable(value = "How far a security drone sees (blocks)", min = 1, max = 64)
        public static float DRONE_SCAN_RANGE = 16.0f;
        /** Zo breed kijkt hij (graden; 360 = rondom, een drone heeft camera's aan alle kanten). */
        @Tunable(value = "How wide a security drone looks (degrees, 360 = all around)", min = 10, max = 360)
        public static float DRONE_SCAN_ANGLE = 360.0f;
        /** Zoveel ticks tussen twee schoten. */
        @Tunable(value = "Ticks between two shots of a security drone", min = 1, max = 1_200)
        public static float DRONE_FIRE_INTERVAL = 30.0f;
        /** Zo hoog stijgt hij op van zijn dock als hij aangezet wordt. */
        @Tunable(value = "How many blocks a security drone rises from its dock when you switch it on", min = 1, max = 16)
        public static float DRONE_TAKE_OFF = 1.0f;
    }

    // =============================================================================================== Hydra

    @Section("Hydra soldiers (in the bunker)")
    public static final class Hydra {
        private Hydra() {}

        /** Schade van één laserschot (4 = 2 hartjes). */
        @Tunable("Damage of one Hydra soldier rifle shot")
        public static float LASER_DAMAGE = 4.0f;
        /** Ticks tussen twee schoten (35 = bijna 2 seconden). */
        @Tunable("Ticks between two Hydra soldier shots")
        public static float FIRE_INTERVAL = 35.0f;
        /** Tot hoe ver hij schiet. */
        @Tunable("How far a Hydra soldier shoots (blocks)")
        public static float ATTACK_RANGE = 18.0f;
        /** Hoeveel ervaring hij laat vallen. */
        @Tunable("Experience a Hydra soldier drops")
        public static float XP_REWARD = 10.0f;

        // VAST: het spel heeft deze nodig bij het opstarten.
        /** Levenspunten (24 = 12 hartjes): iets taaier dan een zombie. */
        public static final double SOLDIER_HEALTH = 24.0;
        /** Pantser (een beetje, van het vest). */
        public static final double SOLDIER_ARMOR = 4.0;
        /** Hoe snel hij loopt. */
        public static final double SOLDIER_SPEED = 0.27;
    }

    // =============================================================================================== Raids

    @Section("Hostile raid drones (switch them off under [rules])")
    public static final class Raids {
        private Raids() {}

        // ------------------------------------------------------------------------- Wanneer en hoeveel
        /** De kortste wachttijd tussen twee raids (minuten)... */
        @Tunable(value = "Shortest wait between two raids (minutes)", min = 1, max = 1_440)
        public static float MIN_MINUTES = 20.0f;
        /** ...en de langste. */
        @Tunable(value = "Longest wait between two raids (minutes)", min = 1, max = 1_440)
        public static float MAX_MINUTES = 45.0f;
        /** Hoeveel drones er in één raid komen. */
        @Tunable(value = "How many drones come in one raid", min = 1, max = 32)
        public static float SIZE = 4.0f;
        /** Hoeveel "aandacht" je trekt door een blok weg te halen in een Stark Tower... */
        @Tunable("Attention you draw by breaking one block of a Stark Tower")
        public static float HEAT_PER_TOWER_BLOCK = 4.0f;
        /** ...bij zoveel aandacht komen ze meteen... */
        @Tunable(value = "How much attention it takes before a raid comes at once", min = 1)
        public static float HEAT_FOR_RAID = 100.0f;
        /** ...en zoveel aandacht gaat er per minuut vanzelf weer af. */
        @Tunable("Attention that wears off by itself every minute")
        public static float HEAT_COOLDOWN_PER_MINUTE = 10.0f;
        /** Na een golf bij het wrak van een satelliet duurt het zo lang voordat het wrak ze opnieuw roept (12000 = 10 minuten). */
        @Tunable("Shortest wait between two raids at the same crashed satellite (ticks)")
        public static float CRASH_SITE_COOLDOWN = 12_000.0f;

        // ------------------------------------------------------------------------- De drone zelf
        /** Schade van één laserschot (3 = 1,5 hartje). */
        @Tunable("Damage of one raid drone laser shot")
        public static float LASER_DAMAGE = 3.0f;
        /** Ticks tussen twee schoten (40 = 2 seconden). */
        @Tunable("Ticks between two raid drone shots")
        public static float FIRE_INTERVAL = 40.0f;
        /** Hoeveel ervaring hij laat vallen. */
        @Tunable("Experience a raid drone drops")
        public static float XP_REWARD = 8.0f;

        // ------------------------------------------------------------------------- Jagen
        /** Tot hoe ver hij schiet. */
        @Tunable("How far a raid drone shoots (blocks)")
        public static double ATTACK_RANGE = 18.0;
        /** Tot hoe ver hij een doelwit zoekt... */
        @Tunable("How far a raid drone looks for a target (blocks)")
        public static double FIND_RANGE = 48.0;
        /** ...en verder dan dit geeft hij zijn doelwit op. */
        @Tunable("Further away than this a raid drone gives up its target (blocks)")
        public static double GIVE_UP_RANGE = 80.0;
        /** Hoe hoog boven zijn doelwit hij blijft hangen. */
        @Tunable("How high above its target a raid drone hovers (blocks)")
        public static double HOVER_ABOVE = 4.0;
        /** Hoe wijd hij om zijn doelwit cirkelt. */
        @Tunable("How wide a raid drone circles its target (blocks)")
        public static double CIRCLE_RADIUS = 6.0;
        /** Hoe hard hij bijstuurt (hoger = wendbaarder). */
        @Tunable("How hard a raid drone steers (higher = more agile)")
        public static double ACCELERATION = 0.06;
        /** Ticks tussen twee blokken die hij wegschiet als hij je achternazit (30 = anderhalve seconde). */
        @Tunable("Ticks between two blocks a raid drone breaks while hunting you")
        public static float BREAK_INTERVAL = 30.0f;
        /** Hoe ver vooruit hij blokken weghaalt. */
        @Tunable("How far ahead a raid drone breaks blocks (blocks)")
        public static double BREAK_REACH = 6.0;

        // ------------------------------------------------------------------------- Aankomst
        /** Hoe hoog boven je ze verschijnen. */
        @Tunable("How high above you raid drones appear (blocks)")
        public static float ARRIVAL_HEIGHT = 80.0f;
        /** Hoe ver de drones van een golf uit elkaar liggen als ze aankomen. */
        @Tunable("How far apart the drones of one raid arrive (blocks)")
        public static double SPREAD = 8.0;

        /** VAST: levenspunten (10 = 5 hartjes). Het spel heeft dit nodig bij het opstarten. */
        public static final double HEALTH = 10.0;
    }
}
