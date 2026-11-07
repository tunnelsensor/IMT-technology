package com.senne.imttech.config;

/*
 * =================================================================================================
 *  ALLE GETALLEN VAN I.M.T TECHNOLOGY, OP ÉÉN PLEK
 * =================================================================================================
 *
 * Hier staat elk getal dat bepaalt hoe de mod speelt: wat iets kost, hoeveel schade het doet, hoe
 * ver het reikt, hoe lang je moet wachten, en hoeveel stroom elk blok maakt of gebruikt. De rest van
 * de mod leest de getallen rechtstreeks hier, bijvoorbeeld ImtConfig.Satellite.COST_SWEEP.
 *
 * HET CONFIGBESTAND
 *   Uit dit bestand maakt de mod vanzelf config/imttech.toml (in .minecraft, of op een server in de
 *   map van de server). Daar staat alles in, met dezelfde hoofdstukken; de getallen hieronder zijn de
 *   standaardwaarden. Pas je het .toml-bestand aan, dan hoef je niets opnieuw te bouwen: het spel
 *   leest het opnieuw zodra je het opslaat, ook terwijl je speelt. Een server stuurt het naar elke
 *   speler die binnenkomt, zodat het menu en de HUD dezelfde getallen laten zien.
 *   - LET OP: bestaat het .toml-bestand al, dan houdt het zijn eigen waarden. Verander je hieronder een
 *     standaardwaarde, pas dan ook run/config/imttech.toml aan (of gooi het weg: dan maakt het spel
 *     een nieuw bestand met de nieuwe standaardwaarden). Boven elke regel in het .toml-bestand staat
 *     "Default: ...": daar zie je de standaard uit dit bestand.
 *   - Eén wereld anders? Kopieer het bestand naar de map serverconfig in die wereld
 *     (saves/<naam van de wereld>/serverconfig/) en pas het daar aan: die kopie gaat voor.
 *   - Persoonlijke dingen (je volume, de stem, Spotify, de rand van zones) staan hier niet, maar in
 *     config/imttech-client.toml (ImtClientConfig): die kiest elke speler voor zichzelf.
 *
 * EENHEDEN
 *   tijd      in ticks: 20 ticks = 1 seconde (600 = een halve minuut, 1200 = een minuut)
 *   energie   in FE (Forge Energy, sinds 4 november: dezelfde eenheid als Create Crafts & Additions en Mekanism; tot
 *             dan heette het SE en was alles tien keer kleiner). Bij de bril gaat het uit de batterij van de bril; bij
 *             blokken (hoofdstuk Power) uit het stroomnet (de stroomkabels).
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
 *   hoeft niet: het staat dan vanzelf in het .toml-bestand (dat regelt ImtConfigLoader).
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
 *   Illusions .... illusies en de Illusion Jammer
 *   Shields ...... persoonlijk schild, laserwanden en laserbruggen
 *   Dome ......... de schildkoepel
 *   Hack ......... andere spelers hacken (berichten onderscheppen)
 *   Relays ....... redstone op afstand schakelen
 *   Comms ........ berichten en pings
 *   Navigation ... waypoints
 *   Holo ......... de holotafel
 *   Suit ......... het harnas: bescherming, vliegen, laser cannon, raketten
 *   Workbench .... de harnas-werkbank en de upgrades van het harnas
 *   Turrets ...... torentjes (raket, Raven, beveiliging)
 *   Security ..... de beveiligingskern: zone, drones, systeem
 *   Network ...... netwerkkabels: welke apparaten er een nodig hebben (hoofdstuk 38)
 *   Logic ........ serverkasten en het datacenter: snelheid, warmte, brand, backups
 *   Breach ....... hoe sterk blokken zijn tegen frags, en TNT en de satelliet tegen een beschermde zone
 *   Repair ....... barsten herstellen (Crusher, Compactor, Repair Paste)
 *   Bypass ....... inbreken met de Bypass Kit, en zakkenrollen
 *   Lights ....... de Arc Lamp
 *   Robots ....... guard robots: wat elk onderdeel doet (hoofdstuk 21)
 *   Raven ........ Raven-soldaten
 *   Guards ....... bewakers zoals in Hitman: zien, horen, de radio, zoeken, lichamen (hoofdstuk 60)
 *   Raids ........ vijandige raiddrones
 *   Weapons ...... granaten, de Remote EMP en Remote Frag, lasergeweren en energiecellen (hoofdstuk 35)
 *   Guns ......... vuurwapens: de Glock 19, onderdelen en materialen, magazijnen, kogels, hulzen, storingen (hoofdstuk 52)
 */
public final class ImtConfig {
    private ImtConfig() {}

    // =============================================================================================== Rules

    @Section("What is allowed in this world (on a server: for everyone)")
    public static final class Rules {
        private Rules() {}

        /** Mag I.M.T privéberichten van andere spelers onderscheppen ("hack player")? */
        @Tunable("May I.M.T intercept other players' private messages (hack player)? "
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

    @Section("The I.M.T glasses: battery, upgrades and what each function costs per second")
    public static final class Glasses {
        private Glasses() {}

        // ------------------------------------------------------------------------- Batterij
        /** Hoeveel energie de bril kan opslaan zonder batterij-upgrades. */
        @Tunable(value = "Energy the glasses hold without battery upgrades", energy = true)
        public static float BASE_CAPACITY = 100_000.0f;
        /** Hoeveel extra opslag elke batterij-upgrade geeft. */
        @Tunable(value = "Extra energy per battery upgrade", energy = true)
        public static float CAPACITY_PER_BATTERY_UPGRADE = 100_000.0f;
        /** Hoeveel batterij-upgrades er hooguit in één bril passen. */
        @Tunable(value = "How many battery upgrades fit in one pair of glasses", max = 16)
        public static float MAX_BATTERY_UPGRADES = 3.0f;
        /** Hoe lang het installeren van een upgrade in het laadstation duurt (100 = 5 seconden). */
        @Tunable(value = "How long installing an upgrade in the charging station takes (ticks)", min = 1)
        public static float UPGRADE_INSTALL_TIME = 60.0f;
        /** Onder dit deel van de batterij waarschuwt I.M.T (0.10 = 10%). */
        @Tunable(value = "Battery share (0-1) below which I.M.T warns you", max = 1)
        public static float LOW_BATTERY_WARNING = 0.10f;

        // ------------------------------------------------------------------------- Verbruik per seconde
        /** Wat de HUD zelf kost zolang je de bril draagt. */
        @Tunable(value = "Energy per second while the HUD is on", energy = true)
        public static float DRAIN_HUD = 10.0f;
        /** Extra zolang nachtzicht aan staat. */
        @Tunable(value = "Energy per second for night vision", energy = true)
        public static float DRAIN_NIGHT_VISION = 40.0f;
        /** Extra zolang "vijanden door muren" aan staat. */
        @Tunable(value = "Energy per second for the threat scan", energy = true)
        public static float DRAIN_THREAT_SCAN = 30.0f;
        /** Extra zolang doelwit-analyse aan staat. */
        @Tunable(value = "Energy per second for target analysis", energy = true)
        public static float DRAIN_TARGET_ANALYSIS = 10.0f;

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
        @Tunable(value = "Energy cost of an ore scan", energy = true)
        public static float ORE_SCAN_COST = 4_000.0f;
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
     * Alles wat met het stroomnet (de stroomkabels) te maken heeft, voor elk blok: wat het opwekt, wat
     * het opslaat, en wat het gebruikt. Let op de tijd bij elk getal: "per tick" (20 keer per seconde),
     * "per seconde", of "elke twee seconden".
     *
     * Alle energiegetallen hier zijn kommagetallen (float), dus ook heel kleine waarden werken: 0.3 per
     * tick = 6 per seconde, 0.05 per tick = 1 per seconde. Wat nog geen hele stroom is, wordt bewaard en
     * telt de volgende tick mee (zie EnergyCarry en PowerGrid.pay), zodat het gemiddeld precies klopt.
     */
    @Section("Power on the grid: what every block makes, stores and uses. "
            + "Watch the time in each line: per tick (20 times a second), per second, or every two seconds. "
            + "Decimals work: 0.3 per tick = 6 per second, 0.05 per tick = 1 per second.")
    public static final class Power {
        private Power() {}

        // ------------------------------------------------------------------------- Zonnepaneel
        /** Energie per tick met de zon recht boven je (minder als de zon laag staat, 's nachts niets). */
        @Tunable(value = "Energy per tick a solar panel makes with the sun straight above (less when the sun is low, nothing at night)", energy = true)
        public static float SOLAR_RATE = 24.0f;
        /** Welk deel van de zon een zonnepaneel nog krijgt als het regent (0.4 = 40%). */
        @Tunable(value = "Share of the sun (0-1) a solar panel still gets when it rains", max = 1)
        public static float SOLAR_RAIN = 1f;
        /** ...en bij onweer. */
        @Tunable(value = "Share of the sun (0-1) a solar panel still gets in a thunderstorm", max = 1)
        public static float SOLAR_THUNDER = 0.5f;
        /** Hoeveel een zonnepaneel zelf kan opslaan. */
        @Tunable(value = "Energy a solar panel can store", energy = true)
        public static float SOLAR_CAPACITY = 50_000.0f;

        // ------------------------------------------------------------------------- Warmtegenerator
        /** Energie per tick zolang er brandstof brandt (kolen, hout, ...). */
        @Tunable(value = "Energy per tick a heat generator makes while it burns fuel", energy = true)
        public static float HEAT_RATE_FUEL = 100.0f;
        /** Energie per tick per heet blok dat ertegenaan staat (lava, vuur, magma...). */
        @Tunable(value = "Energy per tick per hot block (lava, fire, magma) next to a heat generator", energy = true)
        public static float HEAT_RATE_AMBIENT = 50.0f;
        /** Meer hete buren dan dit tellen niet meer mee. */
        @Tunable(value = "Hot blocks above this many no longer count", max = 26)
        public static float HEAT_MAX_NEIGHBOURS = 8.0f;
        /** Hoeveel een warmtegenerator zelf kan opslaan. */
        @Tunable(value = "Energy a heat generator can store", energy = true)
        public static float HEAT_CAPACITY = 50_000.0f;

        // ------------------------------------------------------------------------- Laadstation (voor de bril)
        // (Sinds hoofdstuk 54 gaat er geen redstone meer in het station: alleen stroom van het net.)
        /** Hoeveel het laadstation zelf kan opslaan (hooguit 32000, anders klopt de balk in het scherm niet). */
        @Tunable(value = "Energy a charging station can store", min = 1, max = 32_000, energy = true)
        public static float STATION_CAPACITY = 300_000.0f;
        /** Hoeveel energie het station per tick in de bril stopt. */
        @Tunable(value = "Energy per tick a charging station puts into the glasses", energy = true)
        public static float STATION_CHARGE_RATE = 500.0f;
        /** Hoeveel het station per tick uit het net mag halen om de bril te laden (ruim meer dan één paneel maakt). */
        @Tunable(value = "Energy per tick a charging station draws from the grid to charge the glasses", energy = true)
        public static float STATION_GRID_RATE = 200.0f;
        /**
         * Zijn eigen voorraad vult het station ook uit het net, maar alleen met wat over is: dit deel van
         * wat in de generatoren past, blijft altijd voor de rest van het net (holotafel, laserwanden, koepel).
         */
        @Tunable(value = "Share (0-1) of the generators' storage a charging station always leaves for the rest of the grid "
                + "when it fills its own storage", max = 1)
        public static float STATION_GRID_RESERVE = 0.5f;
        /** ...en dat gaat langzaam: zoveel per tick. */
        @Tunable(value = "Energy per tick a charging station puts into its own storage from spare grid energy", energy = true)
        public static float STATION_STORE_RATE = 100.0f;

        // ------------------------------------------------------------------------- Landingsplatform (voor het harnas)
        /** Zoveel energie per seconde laadt een landingsplatform in je bril, uit het net. */
        @Tunable(value = "Energy per second a landing pad puts into your glasses", energy = true)
        public static float PAD_CHARGE_PER_SECOND = 4_000.0f;
        /** Hoeveel de eigen batterij van een platform opslaat zonder batterijcellen. */
        @Tunable(value = "How much a landing pad's own battery holds without battery cells", energy = true)
        public static float PAD_BATTERY_BASE = 100_000.0f;
        /** Extra per Battery Upgrade in het batterijvak van het platform (hooguit 4). */
        @Tunable(value = "Extra landing pad battery per Battery Upgrade in its battery slot (up to 4)", energy = true)
        public static float PAD_BATTERY_PER_CELL = 150_000.0f;
        /** Zo snel vult het platform zijn batterij met wat het net over heeft (per seconde). */
        @Tunable(value = "How fast a landing pad fills its battery from the grid's spare energy (per second)", energy = true)
        public static float PAD_BATTERY_FILL = 2_000.0f;
        /** Zo snel laadt het platform je bril uit zijn eigen batterij (per seconde). */
        @Tunable(value = "How fast a landing pad charges your glasses from its own battery (per second)", energy = true)
        public static float PAD_BATTERY_CHARGE = 16_000.0f;
        /** Zoveel duurzaamheid per seconde repareert een platform aan elk stuk harnas... */
        @Tunable("Durability per second a landing pad repairs on each suit piece")
        public static float PAD_REPAIR_PER_SECOND = 2.0f;
        /** ...en dat kost zoveel stroom per punt. */
        @Tunable(value = "Energy per point of durability a landing pad repairs", energy = true)
        public static float PAD_REPAIR_ENERGY = 100.0f;


        // ------------------------------------------------------------------------- Verbruik: verdediging
        /** Stroom per tick van één laserzender zolang zijn wand aan staat (een paar zenders = 2 per tick). */
        @Tunable(value = "Energy per tick a laser wall emitter uses while its wall is on", energy = true)
        public static float LASER_WALL_UPKEEP = 10.0f;
        /** Zoveel seconden moet het net stroom hebben voordat een laserwand aangaat (anders knippert hij). */
        @Tunable("A laser wall only turns on when the grid has had power for this many seconds (so it doesn't flicker)")
        public static float LASER_WALL_START_SECONDS = 5.0f;
        /** Stroom per tick van één brugzender zolang zijn laserbrug er ligt (hoofdstuk 34). */
        @Tunable(value = "Energy per tick a laser bridge emitter uses while its bridge is out", energy = true)
        public static float LASER_BRIDGE_UPKEEP = 10.0f;
        /** Zoveel seconden moet het net stroom hebben voordat een laserbrug uitschuift (anders knippert hij). */
        @Tunable("A laser bridge only comes out when the grid has had power for this many seconds (so it doesn't flicker)")
        public static float LASER_BRIDGE_START_SECONDS = 5.0f;
        /**
         * Stroom per tick van een schildkoepel, per sterkte-niveau, voor elk van zijn 5 maten (zie Dome.RADII:
         * 8, 12, 16, 24 en 32 blokken). Een koepel van 16 blokken op sterkte 2 kost dus 4 x 2 = 8 per tick.
         */
        @Tunable(value = "Energy per tick a shield dome uses PER STRENGTH LEVEL, for each of its 5 sizes (see dome.radii). "
                + "A 16-block dome on strength 2 uses 4 x 2 = 8 per tick", max = 10_000, energy = true)
        public static float[] DOME_UPKEEP = {20.0f, 30.0f, 40.0f, 60.0f, 80.0f};
        /** Stroom die één laserschot van een Biebia's Industries-torentje van het net haalt. */
        @Tunable(value = "Grid energy one rocket turret laser shot uses", energy = true)
        public static float TURRET_LASER_SHOT = 600.0f;
        /** Stroom per raket die een Biebia's Industries-torentje afvuurt. */
        @Tunable(value = "Grid energy per rocket a rocket turret fires", energy = true)
        public static float TURRET_ROCKET = 200.0f;
        /** Stroom die de beveiligingskern elke twee seconden van het net haalt (zonder staan hij en zijn drones uit). */
        @Tunable(value = "Grid energy the security core uses every two seconds (without it, the core and its drones are off)", energy = true)
        public static float SECURITY_CORE_UPKEEP = 200.0f;
        /** Een zone kost elke twee seconden 1 extra per zoveel blokken (30 x 20 x 30 kost 9 extra). */
        @Tunable(value = "A protected zone costs 1 extra grid energy every two seconds per this many blocks", min = 0.001, inverseEnergy = true)
        public static float ZONE_BLOCKS_PER_POWER = 200.0f;
        /** Stroom die een bewakingscamera elke twee seconden haalt (zonder: "NO SIGNAL"). */
        @Tunable(value = "Grid energy a security camera uses every two seconds (without it, it shows NO SIGNAL)", energy = true)
        public static float CAMERA_UPKEEP = 20.0f;
        /**
         * Hoofdstuk 38: stroom die een keycardlezer elke twee seconden haalt (de kern waar hij met een netwerkkabel aan
         * hangt, betaalt dat van het stroomnet van de lezer). Zonder stroom leest hij geen kaarten.
         */
        @Tunable(value = "Grid energy a keycard reader uses every two seconds (without it, it reads no cards)", energy = true)
        public static float READER_UPKEEP = 10.0f;
        /** Hetzelfde voor een codepaneel: zonder stroom doen de toetsen niets. */
        @Tunable(value = "Grid energy a code panel uses every two seconds (without it, its keys do nothing)", energy = true)
        public static float CODE_PANEL_UPKEEP = 10.0f;
        /** Hetzelfde voor een Auth Console (hoofdstuk 49). */
        @Tunable(value = "Grid energy an Auth Console uses every two seconds (without it, its button does nothing)", energy = true)
        public static float AUTH_CONSOLE_UPKEEP = 10.0f;
        /** Stroom die een Network Switch elke twee seconden haalt (zonder: geen netwerk door de switch). */
        @Tunable(value = "Grid energy a network switch uses every two seconds (without it, no network passes through it)", energy = true)
        public static float NETWORK_SWITCH_UPKEEP = 30.0f;
        /** Stroom die een netwerkknop elke twee seconden haalt (zonder: de server hoort hem niet). */
        @Tunable(value = "Grid energy a network button uses every two seconds (without it, the server does not hear it)", energy = true)
        public static float BUTTON_UPKEEP = 5.0f;
        /** Hetzelfde voor een netwerkhendel. */
        @Tunable(value = "Grid energy a network lever uses every two seconds (without it, the server does not hear it)", energy = true)
        public static float LEVER_UPKEEP = 5.0f;
        /** Hoofdstuk 40: stroom die een Computer elke twee seconden haalt (zonder: zijn monitor blijft zwart). */
        @Tunable(value = "Grid energy a computer uses every two seconds (without it, its monitor stays dark)", energy = true)
        public static float COMPUTER_UPKEEP = 20.0f;
        /** 19 oktober: stroom die één Camera Monitor elke twee seconden haalt (een groot scherm gebruikt dus meer). */
        @Tunable(value = "Grid energy one camera monitor uses every two seconds (a big screen uses this per monitor; without it, it stays dark)", energy = true)
        public static float CAMERA_MONITOR_UPKEEP = 10.0f;

        // ------------------------------------------------------------------------- Verbruik: machines en de rest
        /** Stroom per tick van de harnas-werkbank zolang hij een stuk bouwt... */
        @Tunable(value = "Grid energy per tick while the suit workbench builds a suit piece", energy = true)
        public static float WORKBENCH_BUILD = 100.0f;
        /** ...en zolang hij een upgrade installeert. */
        @Tunable(value = "Grid energy per tick while the suit workbench installs an upgrade", energy = true)
        public static float WORKBENCH_INSTALL = 80.0f;
        /** Stroom per tick van de Crusher en de Compactor zolang ze werken. */
        @Tunable(value = "Grid energy per tick the Crusher and the Compactor use while they work", energy = true)
        public static float CRUSHER_COMPACTOR = 40.0f;
        /** Stroom die één serverkast elke twee seconden haalt (zonder tellen zijn onderdelen niet mee). */
        @Tunable(value = "Grid energy one server rack uses every two seconds (without it, its parts do not count)", energy = true)
        public static float SERVER_RACK_UPKEEP = 100.0f;
        /** Stroom per tick van een holotafel zolang hij aan staat. */
        @Tunable(value = "Grid energy per tick a holo table uses while it is on", energy = true)
        public static float HOLO_TABLE_UPKEEP = 5.0f;
        /**
         * Stroom per tick van een Arc Lamp op lichtniveau 100 (2 per tick = 40 per seconde). Zachter gezet
         * gebruikt hij naar verhouding minder: op 50 de helft.
         */
        @Tunable(value = "Grid energy per tick an Arc Lamp at light level 100 uses while it is on. "
                + "A lamp set lower uses less: at level 50, half of this.", energy = true)
        public static float ARC_LAMP_POWER = 100.0f;
        /** Een lamp die uit is, gaat pas aan als het net zoveel ticks stroom heeft (100 = 5 seconden). */
        @Tunable("An Arc Lamp that is off only turns on when the grid has had power for this many ticks "
                + "(so it doesn't flicker on a weak generator)")
        public static float ARC_LAMP_START_TICKS = 100.0f;

        // ------------------------------------------------------------------------- Forge Energy (hoofdstuk 46)
        @Tunable(value = "The energy units this file uses: 1 = before 4 November 2026 (every energy number was ten times"
                + " smaller), 2 = Forge Energy (FE). An old file is converted when it loads. Do not change this yourself.",
                min = 1, max = 2)
        public static int ENERGY_UNITS = 2;
        @Tunable("At most this much FE per tick a solar panel, heat generator or battery pushes into a machine or cable of"
                + " another mod (Create Crafts & Additions, Mekanism...) standing against it")
        public static float FE_PUSH_RATE = 1_000.0f;
        @Tunable("At most this much FE a device of this mod keeps from another mod's cable (its own small buffer, used"
                + " before the grid)")
        public static float FE_DEVICE_BUFFER = 4_000.0f;

        // ------------------------------------------------------------------------- De Power Battery (hoofdstuk 47)
        @Tunable(value = "FE one Battery Cell inside a Power Battery stores", max = 100_000_000)
        public static float BATTERY_CELL_CAPACITY = 1_000_000.0f;
        @Tunable("At most this much FE per tick a Power Battery takes from the generators on its grid (what they have above"
                + " their reserve, see stationGridReserve)")
        public static float BATTERY_CHARGE_RATE = 2_000.0f;
        /** VAST: zo groot mag een batterij per kant zijn (de zoektocht naar de doos rekent hiermee). */
        public static final int BATTERY_MAX_SIZE = 18;
    }

    // =============================================================================================== Drones

    @Section("Your own drones: how many, the swarm, a single strike drone, escort drones, and the arsenal")
    public static final class Drones {
        private Drones() {}

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
        @Tunable(value = "Energy cost of launching a drone swarm", energy = true)
        public static float SWARM_COST = 15_000.0f;
        /** Wachttijd tussen twee zwermen (600 = 30 seconden). */
        @Tunable("Wait between two swarms (ticks)")
        public static float SWARM_COOLDOWN = 600.0f;
        /** Hoe lang een zwerm blijft voordat hij naar huis vliegt (1200 = 1 minuut). */
        @Tunable("How long a swarm stays before flying home (ticks)")
        public static float SWARM_LIFETIME = 1_200.0f;

        // ------------------------------------------------------------------------- Eén drone op één doelwit
        /** Kosten om één drone op één doelwit af te sturen. */
        @Tunable(value = "Energy cost of sending a single drone", energy = true)
        public static float STRIKE_COST = 4_000.0f;
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
        @Tunable(value = "Energy per second while escort drones are on", energy = true)
        public static float GUARD_DRAIN = 60.0f;
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
        /**
         * Levenspunten die één ijzerstaaf aan een drone herstelt. Dat is de enige manier: een drone herstelt nergens
         * vanzelf, ook niet in zijn baan (26 oktober).
         */
        @Tunable(value = "Health one iron ingot repairs on a drone (the only way to repair one: drones never heal "
                + "by themselves, not even in orbit)", min = 0.1)
        public static double REPAIR_PER_INGOT = 3.0;

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
        @Tunable(value = "Energy cost of a precision beam", energy = true)
        public static float COST_PRECISION = 20_000.0f;
        /** Kosten van een bombardement (meerdere inslagen, krater). */
        @Tunable(value = "Energy cost of a bombardment", energy = true)
        public static float COST_BOMBARDMENT = 50_000.0f;
        /** Kosten van de schuivende laser. */
        @Tunable(value = "Energy cost of a sweeping laser", energy = true)
        public static float COST_SWEEP = 35_000.0f;

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
        @Tunable(value = "Energy cost of launching the drone camera", energy = true)
        public static float SCOUT_COST = 2_000.0f;
        /** Extra energie per seconde zolang je door de drone kijkt. */
        @Tunable(value = "Energy per second while you look through the drone camera", energy = true)
        public static float SCOUT_DRAIN = 80.0f;
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
        @Tunable(value = "Energy cost of a spy drone", energy = true)
        public static float SPY_COST = 8_000.0f;
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
        @Tunable(value = "Energy per second while you watch through the spy drone", energy = true)
        public static float SPY_VIEW_DRAIN = 40.0f;
        /** Tot hoe ver je nog live kunt meekijken. */
        @Tunable("How far away you can still watch through the spy drone (blocks)")
        public static double SPY_VIEW_RANGE = 110.0;
    }

    // =============================================================================================== Transport

    @Section("Transport drones: a ride, the autopilot, cargo and carrying you")
    public static final class Transport {
        private Transport() {}

        /** Kosten om je te laten oppikken (of om een vracht te versturen). */
        @Tunable(value = "Energy cost of a transport (ride, autopilot, cargo, carry)", energy = true)
        public static float COST = 6_000.0f;
        /** Wachttijd tussen twee ritten (200 = 10 seconden). */
        @Tunable("Wait between two transports (ticks)")
        public static float COOLDOWN = 200.0f;
        /** Energie per seconde zolang drones je dragen. */
        @Tunable(value = "Energy per second while drones carry you", energy = true)
        public static float DRAIN = 140.0f;
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

    @Section("Illusions (cloak fields, fake walls, a fake army) and the Illusion Jammer that jams them")
    public static final class Illusions {
        private Illusions() {}

        /** Kosten om een illusie te starten. */
        @Tunable(value = "Energy cost of an illusion", energy = true)
        public static float COST = 12_000.0f;
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

        // ------------------------------------------------------------------------- Illusion Jammer (heette vroeger E.M.P.)
        /** Hoe ver een puls van de Illusion Jammer reikt. */
        @Tunable("Radius of an Illusion Jammer pulse (blocks)")
        public static double EMP_RADIUS = 24.0;
        /** Hoe lang illusies plat liggen na een puls (600 = 30 seconden). */
        @Tunable("How long the Illusion Jammer jams illusions (ticks)")
        public static float EMP_JAM_TICKS = 600.0f;
        /** Wachttijd van de Illusion Jammer (300 = 15 seconden). */
        @Tunable("Wait between two Illusion Jammer pulses (ticks)")
        public static float EMP_COOLDOWN = 300.0f;
    }

    // =============================================================================================== Shields

    @Section("The personal rayshield, laser walls and laser bridges (their power use is under [power])")
    public static final class Shields {
        private Shields() {}

        /** Extra energie per seconde zolang je persoonlijke schild aan staat. */
        @Tunable(value = "Energy per second while the personal rayshield is on", energy = true)
        public static float PERSONAL_DRAIN = 50.0f;
        /** Wat één punt tegengehouden schade kost. */
        @Tunable(value = "Energy the personal shield uses per point of damage it stops", energy = true)
        public static float PERSONAL_COST_PER_DAMAGE = 1_200.0f;
        /** Schade als je een laserwand aanraakt (4 = 2 hartjes). */
        @Tunable("Damage from touching a laser wall")
        public static float LASER_WALL_DAMAGE = 4.0f;
        /** Hoe lang een laserwand hooguit wordt. */
        @Tunable(value = "Longest a laser wall can be (blocks between two emitters)", min = 1, max = 64)
        public static float LASER_MAX_LENGTH = 16.0f;
        /** Hoe lang een laserbrug hooguit wordt (hoofdstuk 34): zoveel blokken vanaf de zender. */
        @Tunable(value = "Longest a laser bridge can be (blocks from its emitter)", min = 1, max = 64)
        public static float LASER_BRIDGE_MAX_LENGTH = 16.0f;
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
        public static float[] RECHARGE = {2.0f, 3.0f, 4.0f, 6.0f, 8.0f};
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
        @Tunable(value = "Energy per second per hacked player", energy = true)
        public static float DRAIN = 30.0f;
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
        @Tunable(value = "Energy cost of switching a relay remotely", energy = true)
        public static float COST = 50.0f;
        /** Hoe lang een puls duurt (20 = 1 seconde). */
        @Tunable(value = "How long a redstone pulse lasts (ticks)", min = 1)
        public static float PULSE_TICKS = 20.0f;
    }

    // =============================================================================================== Comms

    @Section("Messages and pings between I.M.T wearers")
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
        @Tunable("How far away other I.M.T wearers see your pings (blocks)")
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

    @Section("The suit: protection, flying, landing, laser cannon and mini rockets. Everything it does is paid by the glasses")
    public static final class Suit {
        private Suit() {}

        // ------------------------------------------------------------------------- Bescherming met stroom
        /** Welk deel van de schade elk stuk met stroom wegneemt; drie stukken = 0.60 = zestig procent eraf. */
        @Tunable(value = "Share (0-1) of damage each powered suit piece stops (three pieces = three times this)", max = 1)
        public static float POWERED_REDUCTION_PER_PIECE = 0.20f;
        /** Wat één punt tegengehouden schade kost. */
        @Tunable(value = "Energy per point of damage the suit stops", energy = true)
        public static float REDUCTION_COST_PER_DAMAGE = 600.0f;

        // ------------------------------------------------------------------------- Vliegen: verbruik
        /** Energie per seconde als je stilhangt. */
        @Tunable(value = "Energy per second while hovering", energy = true)
        public static float DRAIN_HOVER = 120.0f;
        /** Energie per seconde als je vooruit vliegt. */
        @Tunable(value = "Energy per second while flying", energy = true)
        public static float DRAIN_FLY = 260.0f;
        /** Energie per seconde met de naverbrander (sprinttoets). */
        @Tunable(value = "Energy per second with the afterburner", energy = true)
        public static float DRAIN_BOOST = 450.0f;

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
        @Tunable(value = "Energy cost of catching a fall", energy = true)
        public static float LANDING_COST = 2_000.0f;

        // ------------------------------------------------------------------------- Laser cannon
        /** Schade van één laserkanonschot (7 = 3,5 hartje). */
        @Tunable("Damage of one laser cannon blast")
        public static float LASER_CANNON_DAMAGE = 7.0f;
        /** Wat één stoot kost. */
        @Tunable(value = "Energy cost of one laser cannon blast", energy = true)
        public static float LASER_CANNON_COST = 2_000.0f;
        /** Wachttijd tussen twee stoten (20 = 1 seconde). */
        @Tunable(value = "Ticks between two laser cannon blasts", min = 1)
        public static float LASER_CANNON_COOLDOWN = 20.0f;
        /** Hoe ver de stoot reikt. */
        @Tunable("How far a laser cannon blast reaches (blocks)")
        public static double LASER_CANNON_RANGE = 24.0;
        /** Hoe ver naast de straal je nog geraakt wordt. */
        @Tunable("How far next to the beam you still get hit (blocks)")
        public static double LASER_CANNON_WIDTH = 1.2;
        /** Hoe hard hij wegduwt wat hij raakt. */
        @Tunable("How hard a laser cannon blast pushes away what it hits")
        public static double LASER_CANNON_KNOCKBACK = 0.7;
        /** Hoe hard de stoot jou zelf naar achteren duwt (klein houden, anders schiet je jezelf weg). */
        @Tunable("How hard a laser cannon blast pushes yourself back")
        public static double LASER_CANNON_RECOIL = 0.04;

        // ------------------------------------------------------------------------- Mini-raketten
        /** Hoeveel raketten er in één salvo zitten. */
        @Tunable(value = "Number of mini rockets in a salvo", min = 1, max = 64)
        public static float ROCKET_SALVO = 6.0f;
        /** Wat een heel salvo kost. */
        @Tunable(value = "Energy cost of a mini rocket salvo", energy = true)
        public static float ROCKET_COST = 9_000.0f;
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
        /** Extra laserkanonschade per condensator (0.35 = 35%). */
        @Tunable("Extra laser cannon damage per capacitor (0.35 = 35%)")
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

    @Section("Turrets: the rocket turret and the laser turret (security turret; in the Biebia's Industries Tower, the bunker, "
            + "and yours). The rocket turret's power use is under [power]")
    public static final class Turrets {
        private Turrets() {}

        // ------------------------------------------------------------------------- Biebia's Industries-torentje
        /** Tot hoe ver hij doelwitten ziet en met de laser raakt. */
        @Tunable("How far a rocket turret's laser reaches (blocks)")
        public static double RANGE = 16.0;
        /** Laserschade per schot (4 = 2 hartjes). */
        @Tunable("Damage of one rocket turret laser shot")
        public static float LASER_DAMAGE = 4.0f;
        /** Ticks tussen twee laserschoten (20 = 1 seconde). */
        @Tunable("Ticks between two rocket turret laser shots")
        public static float LASER_INTERVAL = 20.0f;
        /** Raketten (munitie in het torentje) reiken iets verder dan de laser. */
        @Tunable("How far a rocket turret fires rockets (blocks)")
        public static double ROCKET_RANGE = 24.0;
        /** Ticks tussen twee raketten. */
        @Tunable("Ticks between two turret rockets")
        public static float ROCKET_INTERVAL = 50.0f;
        /** Zoveel raketten passen er in het torentje. */
        @Tunable(value = "How many rockets fit in a rocket turret", min = 1, max = 64)
        public static float MAX_AMMO = 64.0f;

        // ------------------------------------------------------------------------- Laserturret (de beveiliging: toren, bunker, jij)
        @Tunable("Damage of one laser turret shot")
        public static float SECURITY_LASER_DAMAGE = 3.0f;
        @Tunable("Ticks between two laser turret shots")
        public static float SECURITY_LASER_INTERVAL = 20.0f;
        @Tunable("How far a laser turret reaches (blocks)")
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

    @Section("The security core (Biebia's Industries Tower or your own): its reach and zone, its drones and its computer system. "
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

        // ------------------------------------------------------------------------- Autorisatiecodes (hoofdstuk 49)
        // Per kern in te stellen in Settings op de desktop; dit zijn de standaardwaarden voor een nieuwe kern.
        @Tunable(value = "How many digits the authorization code of a new core has (4-8; change it per core in Settings)", min = 4, max = 8)
        public static float AUTH_CODE_LENGTH = 6.0f;
        @Tunable(value = "How many minutes an authorization code lasts before a new one comes (per core in Settings)", min = 1, max = 60)
        public static float AUTH_CODE_MINUTES = 5.0f;
        @Tunable("Does the previous authorization code still work on a new core? It is always written in the log (per core in Settings)")
        public static boolean AUTH_OLD_CODES = false;
        @Tunable(value = "How long a request at an Auth Console waits for an answer (seconds); no answer = denied", min = 5, max = 300)
        public static float AUTH_REQUEST_SECONDS = 30.0f;

        // ------------------------------------------------------------------------- De I.M.T Phone (hoofdstuk 50)
        @Tunable(value = "How many wrong PINs in a row lock an I.M.T Phone (the owner is told every time)", min = 1, max = 10)
        public static float PHONE_PIN_ATTEMPTS = 3.0f;
        @Tunable(value = "How long a phone stays locked after too many wrong PINs (seconds)", min = 1, max = 3600)
        public static float PHONE_PIN_LOCK_SECONDS = 60.0f;

        // ------------------------------------------------------------------------- Biebia's drones (hoofdstuk 25)
        /** Zoveel geprogrammeerde security drones zette Biebia in een Biebia's Industries Tower (op de docks in de hangar). */
        @Tunable(value = "How many programmed security drones Biebia left in a Biebia's Industries Tower (on the drone docks of its hangar)", max = 16)
        public static float TOWER_DRONES = 4.0f;

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

        // ------------------------------------------------------------------------- Het systeem (Security OS)
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
        /**
         * VAST: zoveel kubussen kan één zone hebben (dit gaat mee in de berichten naar de spelers). Sinds hoofdstuk 45
         * (de Autoscan maakt er al snel een stuk of tien) 48 in plaats van 16.
         */
        public static final int ZONE_MAX_BOXES = 48;

        // ------------------------------------------------------------------------- De Autoscan (hoofdstuk 45)
        @Tunable("Autoscan: how many blocks of wall around the rooms of a bunker belong to the zone (so hidden redstone counts too),"
                + " and the margin around an open building")
        public static float ZONE_SCAN_WALL = 3.0f;
        @Tunable("Autoscan: at most this many blocks of rooms (or of a building) are scanned; more is not a closed bunker")
        public static float ZONE_SCAN_MAX = 60_000.0f;
    }

    // =============================================================================================== Network

    @Section("Network cables (chapter 38): which security devices need a network cable to the desktop of a security core. "
            + "The power the devices use is under [power]")
    public static final class Network {
        private Network() {}

        /**
         * Moeten beveiligingsapparaten met een netwerkkabel aan de desktop van een kern hangen (jouw wens, 29 oktober)?
         * Aan: een keycardlezer, codepaneel, camera, beveiligingstorentje of serverrek zonder netwerkkabel doet niets en
         * staat niet in Devices, en lezers en panelen hebben stroom nodig. Uit: de oude regel, ze horen bij de
         * dichtstbijzijnde kern in wiens gebied ze staan, en lezers en panelen werken zonder stroom (handig voor een oude
         * wereld). Laserpoorten, de laserbrug en je eigen raketturretje werken altijd los.
         */
        @Tunable("Do security devices (keycard readers, code panels, cameras, security turrets, server racks) need a network "
                + "cable to the desktop of a security core, and readers and code panels power? false = the old rule: they "
                + "belong to the nearest core within its reach, and readers and panels need no power (handy for an old world)")
        public static boolean REQUIRED = true;

        /**
         * Hoe ver weg (blokken) je de signalen en de stroom over de kabels ziet lopen (het witte of groene vakje, de gele
         * streepjes). 0 = nooit: dan stuurt de server ze ook niet.
         */
        @Tunable("How far away (blocks) players see signals and power move along cables (the white or green box, the "
                + "yellow dashes). 0 = never")
        public static float SIGNAL_RANGE = 48.0f;

        /**
         * Hoe vaak (seconden) een apparaat hooguit laat zien dat er stroom naar hem toe loopt. Kleiner = vaker een nieuw
         * pakketje naar de spelers in de buurt. 0 = nooit.
         */
        @Tunable("How often (seconds) a device at most shows its power flowing towards it along the cables. 0 = never")
        public static float FLOW_SECONDS = 2.0f;
    }

    // =============================================================================================== Logic

    @Section("Server racks and the datacenter: how fast the redstone programs run, I.M.T's brain, heat, wear, fire "
            + "and backups. A rack's power use is under [power]")
    public static final class Logic {
        private Logic() {}

        // ------------------------------------------------------------------------- Snelheid en ruimte
        /** Zo lang wacht een server met kracht 1 (één CPU) voordat hij reageert; 2 x de kracht = de helft. */
        @Tunable("How long a server with Logic power 1 (one CPU) waits before it reacts to a trigger (ticks); "
                + "twice the power = half the time, never below 1 tick")
        public static float REACTION_TICKS = 10.0f;
        /**
         * Zoveel CPU's samen zijn één "breinkracht" voor I.M.T (hoofdstuk 54, jouw notitie: "een data center moet veel groter
         * zijn om goed te werken, minstens 30 racks in plaats van 5"): 6 CPU's = 1, dus 30 racks vol CPU's = 30 kracht (geen
         * vertraging meer), 5 racks = 5 kracht (32 ticks). De snelheid van je diagrammen (Logic) blijft 1 CPU = 1.
         */
        @Tunable(value = "How many CPUs together count as one brain power for I.M.T; 6 = a datacenter needs six times as"
                + " many racks as before 6 November to think without delay (Logic itself stays 1 CPU = 1)", min = 0.1, max = 100)
        public static double CPUS_PER_POWER = 6.0;
        /** Hoeveel één GPU telt voor Logic (een CPU telt 1). */
        @Tunable("How much one GPU counts for Logic (a CPU counts 1)")
        public static double GPU_LOGIC_POWER = 0.5;
        /** Hoeveel één GPU telt voor het brein van I.M.T (een CPU telt 1). */
        @Tunable("How much one GPU counts for I.M.T's brain (a CPU counts 1)")
        public static double GPU_BRAIN_POWER = 2.0;
        /** Zo lang denkt I.M.T extra na op een datacenter met breinkracht 1 (160 = 8 seconden). */
        @Tunable("How long I.M.T thinks extra on a datacenter with brain power 1 (one CPU), in ticks; "
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
        /** Vanaf deze temperatuur is een kast warm: de hele server werkt op halve snelheid en I.M.T waarschuwt. */
        @Tunable("From this temperature a rack is warm: the whole server works at half speed and I.M.T warns you")
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

    // =============================================================================================== Breach

    /*
     * Hoe een klap werkt (eerst van C4, sinds 26 oktober van de frags: zie [weapons]): een frag doet "breekpunten" vlak
     * bij de lading, en dat loopt af tot 0 aan de rand van de klap. Elk blok kan een aantal breekpunten hebben voor het
     * breekt: zwak (hout, glas, aarde) WEAK, gewoon (steen, baksteen) NORMAL, sterk (obsidiaan) STRONG, de
     * beveiligingskern CORE. Wat niet breekt krijgt barsten: de volgende klap telt erbij op, tot je ze herstelt (zie
     * Repair). Zo blaast één frag een blok steen uit een muur, en heb je voor obsidiaan er vier nodig.
     */
    @Section("How strong blocks are against frags (and TNT and the satellite against the blocks of a protected zone). A "
            + "blast does 'breach points' that fall to 0 at the blast radius; a block breaks when it took its breach points, "
            + "otherwise it gets cracks")
    public static final class Breach {
        private Breach() {}

        // ------------------------------------------------------------------------- Hoe sterk blokken zijn
        @Tunable("Breach points a weak block (wood, glass, dirt, wool, sand...) takes before it breaks")
        public static float WEAK = 2.0f;
        @Tunable("Breach points a normal block (stone, bricks, deepslate, iron...) takes before it breaks")
        public static float NORMAL = 6.0f;
        @Tunable("Breach points a strong block (obsidian, netherite, the laser emitter...) takes before it breaks")
        public static float STRONG = 20.0f;
        @Tunable("Breach points the security core takes before it breaks (about 8 Remote Frags)")
        public static float CORE = 48.0f;
        /** Vanaf deze explosieweerstand (zoals in Minecraft) is een blok "gewoon"; daaronder zwak. */
        @Tunable("From this explosion resistance (as in Minecraft) a block counts as normal; below it, weak")
        public static float NORMAL_RESISTANCE = 4.0f;
        /** Vanaf deze explosieweerstand is een blok "sterk" (obsidiaan heeft 1200, steen 6). */
        @Tunable("From this explosion resistance a block counts as strong (obsidian has 1200, stone 6)")
        public static float STRONG_RESISTANCE = 100.0f;

        // ------------------------------------------------------------------------- De Block Reinforcer (hoofdstuk 44)
        @Tunable("Breach points a block reinforced with a Reinforcement Plate takes at least (20 = as strong as obsidian)")
        public static float REINFORCED = 20.0f;
        @Tunable("How many blocks of the same kind the Block Reinforcer does in one go when you sneak (the whole wall)")
        public static float REINFORCER_WALL = 64.0f;

        // ------------------------------------------------------------------------- TNT en de satelliet in een zone
        // In een zone werken ze met breekpunten, net als een frag, maar zwakker ("TNT doet heel weinig aan de muur").
        @Tunable("Breach points of one TNT on the blocks of a protected zone (a frag is 8)")
        public static float TNT_BREACH = 3.0f;
        @Tunable("Breach points of one satellite bombardment impact on the blocks of a protected zone (a frag is 8)")
        public static float SATELLITE_BREACH = 6.0f;
        /** Zo ver reikt de klap van TNT of een satellietinslag in een zone. */
        @Tunable("How far the blast of TNT or a satellite impact reaches in a protected zone (blocks)")
        public static float EXPLOSION_BREACH_RADIUS = 3.0f;
    }

    // =============================================================================================== Handcuffs

    @Section("Handcuffs (chapter 48): catching players, Raven soldiers and guard robots, and dragging them along")
    public static final class Handcuffs {
        private Handcuffs() {}

        /** Zo lang houd je rechtsklik vast om iemand te boeien (meteen als hij weerloos is, zie Handcuffs.instant). */
        @Tunable(value = "How long you hold right-click on someone to handcuff them (seconds); instant when they are flashbanged, "
                + "asleep, nearly dead, or a robot that is down", max = 60)
        public static float HOLD_SECONDS = 2.0f;
        /** Zo dichtbij moet je zijn. */
        @Tunable(value = "How close you must be to handcuff someone (blocks)", min = 1, max = 8)
        public static float REACH = 3.0f;
        /** Onder dit deel van zijn leven is iemand weerloos: dan gaan de boeien meteen om. */
        @Tunable(value = "Below this part of their health someone is handcuffed instantly (0.25 = a quarter)", max = 1)
        public static float WEAK_HEALTH = 0.25f;
        /** Zo lang moet een geboeide speler sluipen om los te komen (een losgelaten soldaat wurmt zich even snel los). */
        @Tunable(value = "How long a handcuffed player must hold sneak to struggle free (seconds); a Raven soldier nobody "
                + "holds wriggles free after the same time", min = 1, max = 3600)
        public static float STRUGGLE_SECONDS = 30.0f;
        /** Hoe snel een geboeide nog loopt (0.5 = half zo snel). */
        @Tunable(value = "How fast a handcuffed player still walks (0.5 = half speed)", min = 0.05, max = 1)
        public static float SLOW = 0.5f;
        /** Zo ver achter je staat wie je vasthoudt (jouw wens van 4 november: geen lijn, maar echt vast). */
        @Tunable(value = "How far in front of you a handcuffed person is held (blocks); they stay there, facing forward, while you walk and turn",
                min = 0.5, max = 8)
        public static float HOLD_DISTANCE = 1.4f;
        /** Verder weg dan dit, en je bent hem kwijt (hij blijft geboeid). */
        @Tunable(value = "Further away than this and you lose your hold on them; they stay handcuffed (blocks)", min = 2, max = 64)
        public static float LET_GO = 10.0f;
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

    @Section("Breaking in with the Bypass Kit (keycard readers, code panels and laser emitters), and picking pockets")
    public static final class Bypass {
        private Bypass() {}

        /** Zo dichtbij moet je blijven tijdens een hack; verder weg = de hack stopt. */
        @Tunable("How close you must stay to a reader, panel or laser emitter while you hack it (blocks)")
        public static double REACH = 6.0;

        // ------------------------------------------------------------------------- Het stroomcircuit (keycard-lezer)
        // Sinds 25 oktober kiest de hacker een groep in plaats van een niveau, en is het circuit altijd zo moeilijk als
        // het oude niveau 5 (jouw keuze): daarom één getal per instelling in plaats van een rijtje van vijf.
        /** Hoeveel seconden je krijgt. */
        @Tunable(value = "Seconds you get to rewire a keycard reader", min = 10, max = 600)
        public static float CIRCUIT_SECONDS = 40.0f;
        /** Hoe breed het raster is... */
        @Tunable(value = "Circuit width (columns)", min = 2, max = 12)
        public static float CIRCUIT_COLUMNS = 8.0f;
        /** ...hoe hoog... */
        @Tunable(value = "Circuit height (rows)", min = 2, max = 12)
        public static float CIRCUIT_ROWS = 6.0f;
        /** ...en hoeveel ALARM-punten erlangs zitten. */
        @Tunable(value = "ALARM points along the circuit", max = 10)
        public static float CIRCUIT_ALARMS = 3.0f;
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

        // ------------------------------------------------------------------------- Draden knippen (laserzender, hoofdstuk 33)
        // Jouw keuze: hoe groter de laserpoort, hoe meer draden (het aantal zenders plus 3) en hoe meer tijd.
        /** Zoveel draden heeft de kluwen van een poort van één zender... */
        @Tunable(value = "Wires inside a laser gate of one emitter (a bigger gate has more, up to the most below)", min = 2, max = 8)
        public static float LASER_WIRES_MIN = 10.0f;
        /** ...en zoveel hooguit, bij een grote poort. */
        @Tunable(value = "Most wires inside a laser gate, however big it is", min = 2, max = 8)
        public static float LASER_WIRES_MAX = 8.0f;
        /** Zoveel seconden krijg je bij de minste draden... */
        @Tunable(value = "Seconds you get to cut the wires of a laser gate with the fewest wires", min = 5, max = 600)
        public static float LASER_SECONDS_MIN = 10.0f;
        /** ...en zoveel bij de meeste draden (daartussen loopt het gelijk op). */
        @Tunable(value = "Seconds you get to cut the wires of a laser gate with the most wires", min = 5, max = 600)
        public static float LASER_SECONDS_MAX = 30.0f;
        /** Verkeerde draad of te laat: zo lang laat de poort niemand meer knippen. */
        @Tunable("After a wrong cut (or running out of time), the laser gate can't be hacked by anyone for this long (seconds)")
        public static float LASER_LOCK_SECONDS = 300.0f;
        /** De schok van een verkeerde draad (4 = 2 hartjes). */
        @Tunable(value = "Damage of the shock from cutting the wrong wire (2 = one heart)", max = 100)
        public static float LASER_SHOCK_DAMAGE = 4.0f;
        /** Zoveel Computer Chips kost het om de draden van een doorgeknipte poort te herstellen (in creative niets). */
        @Tunable(value = "Computer Chips it costs to repair the cut wires of a laser gate (free in creative)", max = 64)
        public static float LASER_REPAIR_CHIPS = 1.0f;

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
        @Tunable(value = "Battery of a robot (energy), by the style of its body", min = 1, max = 1_000_000, energy = true)
        public static float[] BODY_BATTERY = {40_000.0f, 60_000.0f, 30_000.0f, 80_000.0f};
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
        /**
         * Levenspunten die één ijzerstaaf aan een robot of security drone herstelt (rechtsklik met ijzer). Dat is de
         * enige manier: ze herstellen nergens vanzelf, ook niet op hun dock (26 oktober). Een Battle-robot (20) is met
         * 5 staven weer heel, een security drone (16) met 4.
         */
        @Tunable(value = "Health one iron ingot repairs on a robot or a security drone (right-click it with iron). "
                + "This is the only way to repair them: they never heal by themselves, not even on their dock",
                min = 0.1, max = 1_000)
        public static float REPAIR_PER_INGOT = 4.0f;
        /** Hoeveel stroom per tick een Robot Dock uit het net in de robot op hem stopt. */
        @Tunable(value = "Energy per tick a robot dock takes from the grid and puts into the robot standing on it", max = 1_000, energy = true)
        public static float DOCK_CHARGE_RATE = 50.0f;
        /** Hoeveel van zijn batterij een robot per tick gebruikt als hij aan staat. */
        @Tunable(value = "Battery a robot uses per tick while it is switched on", max = 100, energy = true)
        public static float DRAIN_ON = 2.5f;
        /** Hoeveel er per tick bij komt terwijl hij loopt. */
        @Tunable(value = "Extra battery a robot uses per tick while it walks", max = 100, energy = true)
        public static float DRAIN_WALKING = 5.0f;
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
        @Tunable(value = "Battery one robot laser shot costs", max = 10_000, energy = true)
        public static float LASER_ENERGY = 600.0f;
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
        @Tunable(value = "Energy per tick the Solar Panel module puts into a robot's battery in full midday sun", max = 100, energy = true)
        public static float SOLAR_CHARGE = 5.0f;

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
        @Tunable(value = "Battery of a security drone (energy)", min = 1, max = 1_000_000, energy = true)
        public static float DRONE_BATTERY = 30_000.0f;
        /** Zoveel stroom per tick haalt een Drone Dock uit het net, voor de drone die erop staat. */
        @Tunable(value = "Energy per tick a drone dock takes from the grid and puts into the security drone on it", max = 1_000, energy = true)
        public static float DRONE_DOCK_CHARGE_RATE = 50.0f;
        /** Zoveel batterij per tick als hij aan staat. */
        @Tunable(value = "Battery a security drone uses per tick while it is switched on", max = 100, energy = true)
        public static float DRONE_DRAIN_ON = 2.5f;
        /** Zoveel extra per tick als hij vliegt. */
        @Tunable(value = "Extra battery a security drone uses per tick while it flies", max = 100, energy = true)
        public static float DRONE_DRAIN_FLYING = 6.0f;
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

    // =============================================================================================== Training

    @Section("Training like Rookie squad (chapter 26): the countdown, what a hit does, and the reports")
    public static final class Training {
        private Training() {}

        /** Zoveel seconden telt I.M.T af voordat een training begint. */
        @Tunable(value = "Seconds I.M.T counts down before a training starts", max = 30)
        public static float COUNTDOWN_SECONDS = 3.0f;
        /** Zo lang (ticks) na een treffer telt een volgende niet mee: anders kostte een laserwand elke tick een leven. */
        @Tunable(value = "Ticks after a hit during which the trainee cannot be hit again (so one shot is one life)", min = 1, max = 200)
        public static float HIT_COOLDOWN = 20.0f;
        /** Zo lang (ticks) is de trainee trager na een treffer met trainingsschoten. */
        @Tunable(value = "Ticks the trainee is slower after a hit with training shots", max = 200)
        public static float SLOW_TICKS = 20.0f;
        /** Zoveel rapporten bewaart een kern (de app Training). */
        @Tunable(value = "How many training reports a security core keeps (the Training app)", min = 1, max = 100)
        public static float MAX_REPORTS = 10.0f;
        /** Zo lang (ticks) geeft een Training End-blok redstone na een treffer. */
        @Tunable(value = "Ticks a Training End block gives a redstone signal after each hit", min = 1, max = 200)
        public static float END_PULSE = 10.0f;
    }

    // =============================================================================================== Raven

    @Section("Raven soldiers (in the bunker)")
    public static final class Raven {
        private Raven() {}

        /** Schade van één laserschot (4 = 2 hartjes). */
        @Tunable("Damage of one Raven soldier shot (a bullet from his Glock 19, or a laser)")
        public static float LASER_DAMAGE = 4.0f;
        /** Hoofdstuk 54: hoe ver een soldaat ernaast schiet (graden), welke kans op granaten, en of zijn wapen valt. */
        @Tunable(value = "Extra spread of a Raven soldier's shots (degrees)", max = 45)
        public static float SPREAD = 4.0f;
        @Tunable(value = "Chance that a Raven soldier carries grenades (frag or EMP)", max = 1)
        public static float GRENADE_CHANCE = 0.35f;
        @Tunable(value = "Ticks between two grenades of one soldier", min = 20)
        public static float GRENADE_INTERVAL = 200.0f;
        @Tunable(value = "Chance that a dead Raven soldier drops his weapon (Glock 19 or laser gun)", max = 1)
        public static float WEAPON_DROP_CHANCE = 0.08f;
        /** Ticks tussen twee schoten (35 = bijna 2 seconden). */
        @Tunable("Ticks between two Raven soldier shots")
        public static float FIRE_INTERVAL = 35.0f;
        /** Tot hoe ver hij schiet. */
        @Tunable("How far a Raven soldier shoots (blocks)")
        public static float ATTACK_RANGE = 18.0f;
        /** Hoeveel ervaring hij laat vallen. */
        @Tunable("Experience a Raven soldier drops")
        public static float XP_REWARD = 10.0f;

        // VAST: het spel heeft deze nodig bij het opstarten.
        /** Levenspunten (24 = 12 hartjes): iets taaier dan een zombie. */
        public static final double SOLDIER_HEALTH = 24.0;
        /** Pantser (een beetje, van het vest). */
        public static final double SOLDIER_ARMOR = 4.0;
        /** Hoe snel hij loopt. */
        public static final double SOLDIER_SPEED = 0.27;
    }


    // =============================================================================================== Guards

    @Section("Guards like in Hitman (chapter 60): how Raven soldiers (and your robots) notice you, hear you, search and call backup")
    public static final class Guards {
        private Guards() {}

        // ------------------------------------------------------------------------- Zien
        /** Zo ver kijkt een bewaker. */
        @Tunable(value = "How far a guard sees (blocks)", min = 4)
        public static float VIEW_RANGE = 28.0f;
        /** Zo breed is zijn blik (de hele kegel). */
        @Tunable(value = "How wide a guard sees (degrees, the whole cone)", min = 10, max = 360)
        public static float VIEW_ANGLE = 140.0f;
        /** Zo lang duurt het voor hij je heeft als je vlak voor hem staat... */
        @Tunable(value = "Seconds in sight before a guard has you, right in front of him", min = 0.1)
        public static float SPOT_SECONDS_NEAR = 1.0f;
        /** ...en aan de rand van zijn bereik. */
        @Tunable(value = "Seconds in sight before a guard has you, at the edge of his range", min = 0.1)
        public static float SPOT_SECONDS_FAR = 8.0f;
        @Tunable(value = "How much slower a guard notices you while you sneak (0.5 = half as fast)", min = 0.05, max = 1)
        public static float SNEAK_FACTOR = 0.5f;
        @Tunable(value = "How much faster a guard notices you while you sprint", min = 1)
        public static float SPRINT_FACTOR = 1.6f;
        @Tunable(value = "How much slower a guard notices you in the corner of his eye (the outer half of the cone)", min = 0.05, max = 1)
        public static float PERIPHERAL_FACTOR = 0.5f;
        @Tunable(value = "How much faster a searching guard notices you", min = 1)
        public static float SEARCH_FACTOR = 3.0f;
        @Tunable(value = "Seconds for a full meter to drop to nothing once you are out of sight", min = 0.5)
        public static float DECAY_SECONDS = 6.0f;
        /** Bij zoveel van de meter wordt hij achterdochtig en komt hij kijken. */
        @Tunable(value = "At this much of the meter (0-100) a guard gets suspicious and comes to look", min = 1, max = 99)
        public static float SUSPICIOUS_AT = 50.0f;
        @Tunable(value = "Seconds a guard looks around at a spot before he goes back to his route", min = 1)
        public static float LOOK_SECONDS = 4.0f;

        // ------------------------------------------------------------------------- Zoeken en onthouden
        @Tunable(value = "Seconds without sight before a fighting guard loses you and starts searching", min = 1)
        public static float LOST_SECONDS = 10.0f;
        @Tunable(value = "Seconds a guard searches around the spot where he lost you", min = 5)
        public static float SEARCH_SECONDS = 120.0f;
        @Tunable(value = "How far around that spot he searches (blocks)", min = 2)
        public static float SEARCH_RADIUS = 12.0f;
        @Tunable(value = "Minutes after an alarm during which the guards of that base notice you faster")
        public static float MEMORY_MINUTES = 5.0f;
        @Tunable(value = "How much faster they notice you then", min = 1)
        public static float MEMORY_FACTOR = 2.0f;

        // ------------------------------------------------------------------------- Routes en posten
        @Tunable(value = "How fast a guard walks his route (1 = his normal walking speed)", min = 0.2, max = 2)
        public static float PATROL_SPEED = 0.75f;
        @Tunable(value = "Shortest time a guard stops to look around at a point of his route (seconds)")
        public static float PATROL_WAIT_MIN = 2.0f;
        @Tunable(value = "Longest time he stops there (seconds)")
        public static float PATROL_WAIT_MAX = 5.0f;

        // ------------------------------------------------------------------------- Horen (stap 3)
        @Tunable(value = "How far a Glock shot is heard (blocks); sneaking and walking make no noise at all")
        public static float NOISE_GLOCK = 48.0f;
        @Tunable(value = "How far a laser pistol shot is heard (blocks)")
        public static float NOISE_LASER_PISTOL = 12.0f;
        @Tunable(value = "How far a laser rifle, shotgun or sniper shot is heard (blocks)")
        public static float NOISE_LASER_RIFLE = 16.0f;
        @Tunable(value = "How far an explosion is heard (blocks): frag grenades, the Remote Frag, TNT")
        public static float NOISE_EXPLOSION = 56.0f;
        @Tunable(value = "How far a flashbang is heard (blocks)")
        public static float NOISE_FLASHBANG = 24.0f;
        @Tunable(value = "How far an EMP is heard (blocks)")
        public static float NOISE_EMP = 12.0f;
        @Tunable(value = "How far breaking a block is heard (blocks)")
        public static float NOISE_BLOCK = 14.0f;
        @Tunable(value = "How far sprinting is heard (blocks)")
        public static float NOISE_STEPS = 8.0f;
        @Tunable(value = "How far a door, trapdoor or gate is heard (blocks)")
        public static float NOISE_DOOR = 8.0f;
        @Tunable(value = "How far a fall of more than three blocks is heard (blocks)")
        public static float NOISE_FALL = 12.0f;
        @Tunable(value = "Through a wall a noise carries this part of its distance (0.5 = half)", min = 0.05, max = 1)
        public static float NOISE_WALL_FACTOR = 0.5f;

        // ------------------------------------------------------------------------- De radio en de versterking (stap 4)
        @Tunable(value = "Seconds a guard needs to grab his radio once he has you: stop him before that and nobody heard a thing", min = 0.05)
        public static float RADIO_GRAB_SECONDS = 1.0f;
        @Tunable(value = "Seconds the whole radio call takes (grabbing included): stop him in between and his mates only get suspicious", min = 0.1)
        public static float RADIO_CALL_SECONDS = 3.0f;
        @Tunable(value = "How far players hear the radio chatter of the guards (blocks)", min = 8)
        public static float RADIO_RANGE = 48.0f;
        @Tunable(value = "How far from its entrance a Raven base reaches: the guards within it share one alarm (blocks)", min = 16)
        public static float SITE_RANGE = 160.0f;
        @Tunable(value = "Seconds an alarm lasts after the last contact; then the guards search a bit and go back to their routes", min = 10)
        public static float ALARM_SECONDS = 120.0f;
        @Tunable(value = "Seconds after a finished radio call before the backup team comes in at the entrance", min = 1)
        public static float BACKUP_DELAY_SECONDS = 10.0f;
        @Tunable(value = "How many soldiers come in one backup team", min = 1, max = 16)
        public static float BACKUP_SIZE = 4.0f;
        @Tunable(value = "How many backup teams come per alarm at most (0 = none)", max = 10)
        public static float BACKUP_WAVES = 2.0f;

        // ------------------------------------------------------------------------- Lichamen (stap 5)
        @Tunable(value = "Minutes the body of a dead soldier stays (then it vanishes and what was in it drops)", min = 0.05)
        public static float BODY_MINUTES = 5.0f;
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
        /** Hoeveel "aandacht" je trekt door een blok weg te halen in een Biebia's Industries Tower... */
        @Tunable("Attention you draw by breaking one block of a Biebia's Industries Tower")
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

        // ------------------------------------------------------------------------- Een basis (hoofdstuk 54)
        /** Een blok van een zone breekt hij niet: daar gooit hij een sticky frag op, hooguit om de zoveel ticks. */
        @Tunable(value = "Ticks between two sticky frags a raid drone throws at a protected zone", min = 20)
        public static float BREACH_INTERVAL = 120.0f;
        /** Zit zijn doelwit in een zone, dan gooit hij EMP's op de torentjes en laserzenders van die kern. */
        @Tunable(value = "Ticks between two EMP grenades a raid drone throws at a base's turrets and laser emitters", min = 20)
        public static float EMP_INTERVAL = 240.0f;
        @Tunable("How far a raid drone looks for a turret or laser emitter to EMP (blocks)")
        public static double DEVICE_REACH = 24.0;
        /** Een raid begint buiten een basis: zoveel blokken buiten de zone (of het stuk rondom de kern). */
        @Tunable(value = "How far outside a base (a security core with its zone) a raid starts (blocks)", min = 1)
        public static double BASE_MARGIN = 12.0;
        /** Een kern zonder zone telt zoveel blokken rondom als basis. */
        @Tunable(value = "Blocks around a security core without a zone that count as its base", min = 1)
        public static double BASE_SIZE = 16.0;

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

    // =============================================================================================== Weapons

    @Section("Weapons (chapter 35): EMP and frag grenades, smoke grenades, flashbangs, laser guns and energy cells")
    public static final class Weapons {
        private Weapons() {}

        // ------------------------------------------------------------------------- De EMP-granaten
        /** Hoe ver de puls van een EMP-granaat reikt: een bol met deze straal. */
        @Tunable(value = "Radius of the pulse of an EMP grenade (blocks)", min = 1, max = 32)
        public static double EMP_RADIUS = 5.0;
        /** Hoe lang alles uit blijft na een EMP-granaat (seconden). */
        @Tunable(value = "How long everything stays off after an EMP grenade (seconds)", min = 1, max = 600)
        public static float EMP_SECONDS = 20.0f;
        /** Tot hoe ver het signaal van een EMP Trigger of Frag Trigger reikt (blokken), naar zijn ladingen. */
        @Tunable(value = "How far the signal of an EMP Trigger or Frag Trigger reaches its Remote EMPs or Remote Frags (blocks)",
                min = 1, max = 1_000)
        public static float TRIGGER_RANGE = 128.0f;
        /** Na hoeveel seconden een sticky (EMP of frag) afgaat, vanaf het vastplakken. */
        @Tunable(value = "Seconds from sticking until a sticky grenade (EMP or frag) goes off", max = 30)
        public static float STICKY_FUSE_SECONDS = 2.0f;
        /** Na hoeveel seconden een EMP Grenade of Frag Grenade (de bollen) afgaat, vanaf het gooien. */
        @Tunable(value = "Seconds from throwing until an EMP Grenade or Frag Grenade (the balls) goes off", max = 30)
        public static float ROLLING_FUSE_SECONDS = 3.0f;
        /** Hoe hard je een granaat gooit (1,5 = zoals een sneeuwbal). */
        @Tunable(value = "How hard you throw a grenade (1.5 = like a snowball)", max = 5)
        public static float THROW_SPEED = 1.1f;
        /** Hoe hard je hem onderhands gooit (met sneak). */
        @Tunable(value = "How hard you toss a grenade underhand, while sneaking", max = 5)
        public static float TOSS_SPEED = 0.45f;
        /**
         * De klap van een frag-granaat en van de Remote Frag (jouw wens: een extra bom die geen EMP is maar wel schade
         * aan de map doet, "zoals de C4 maar net iets minder sterk"; de Remote Frag "zoals een Sticky Frag"): breekpunten
         * vlak bij de lading (de oude C4 deed 10), aflopend tot 0 op de straal van de klap (C4: 3,5). Zie [breach] voor
         * hoe sterk blokken zijn. De EMP-granaten doen geen schade aan de map.
         */
        @Tunable(value = "Breach points of a frag grenade or Remote Frag right where it goes off", max = 1_000)
        public static float GRENADE_BREACH = 8.0f;
        @Tunable(value = "How far the blast of a frag grenade or Remote Frag reaches (blocks)", max = 16)
        public static float GRENADE_BREACH_RADIUS = 3.0f;
        /** Hoe hard de klap van een frag spelers en wezens raakt (TNT = 4). */
        @Tunable(value = "How hard the blast of a frag grenade or Remote Frag hits players and mobs (TNT is 4)", max = 16)
        public static float GRENADE_ENTITY_POWER = 3.0f;
        /** Hoeveel van zijn vaart de bol overhoudt als hij stuitert (0 = blijft liggen, 1 = stuitert eeuwig). */
        @Tunable(value = "How much speed the rolling grenade keeps when it bounces (0 = none, 1 = all)", max = 0.95)
        public static float BOUNCE = 0.45f;

        // ------------------------------------------------------------------------- De smokebom en de flashbang
        /** Na hoeveel seconden een Smoke Grenade begint te roken, vanaf het gooien. */
        @Tunable(value = "Seconds from throwing until a Smoke Grenade starts smoking", max = 30)
        public static float SMOKE_FUSE_SECONDS = 2.0f;
        /** Hoe lang de rook blijft hangen (seconden). */
        @Tunable(value = "How long the smoke of a Smoke Grenade lasts (seconds)", min = 1, max = 600)
        public static float SMOKE_SECONDS = 18.0f;
        /** Hoe ver de rook stroomt (blokken, om muren heen): in de open lucht een bol met deze straal. */
        @Tunable(value = "How far the smoke of a Smoke Grenade spreads (blocks, around walls; in open air a ball this big)",
                min = 1, max = 16)
        public static double SMOKE_RADIUS = 4.5;
        /** Door zoveel blokken rook kunnen robots, turrets en drones nog net kijken (van heel dichtbij). */
        @Tunable(value = "Through this many blocks of smoke robots, turrets and drones can still see", max = 16)
        public static double SMOKE_SEE_THROUGH = 1.5;
        /** Na hoeveel seconden een Flashbang afgaat, vanaf het gooien. */
        @Tunable(value = "Seconds from throwing until a Flashbang goes off", max = 30)
        public static float FLASH_FUSE_SECONDS = 1.6f;
        /** Tot hoe ver een flashbang je verblindt (blokken). */
        @Tunable(value = "How far away a Flashbang can blind you (blocks)", min = 1, max = 64)
        public static double FLASH_RADIUS = 20.0;
        /** Hoe lang je hoogstens verblind bent (seconden: als je er vlakbij recht naar keek). */
        @Tunable(value = "How long a Flashbang blinds you at most (seconds, when you looked straight at it up close)",
                max = 60)
        public static float FLASH_SECONDS = 5.0f;

        // ------------------------------------------------------------------------- Energiecellen
        /** Zoveel stroom kost het om een lege cel helemaal op te laden in het Charging Station. */
        @Tunable(value = "Energy the Charging Station uses to fill one empty energy cell", min = 1, energy = true)
        public static float CELL_CHARGE_ENERGY = 20_000.0f;

        // ------------------------------------------------------------------------- De lasergeweren
        // Elk rijtje: Laser Pistol, Laser Rifle, Laser Sniper, Laser Shotgun.
        /** Schade van één schot (bij de shotgun: van één straal uit de waaier). */
        @Tunable(value = "Damage of one shot (shotgun: of one ray of the spread), per gun: pistol, rifle, sniper, shotgun",
                max = 1_000)
        public static float[] GUN_DAMAGE = {6.0f, 4.5f, 24.0f, 3.5f};
        /** Zoveel ticks minstens tussen twee schoten. */
        @Tunable(value = "Ticks between two shots, per gun", min = 1, max = 200)
        public static float[] GUN_INTERVAL = {3.0f, 3.0f, 25.0f, 14.0f};
        /** Zoveel procent van een cel kost één schot (de waaier van de shotgun telt als één schot). */
        @Tunable(value = "Percent of an energy cell one shot uses, per gun", min = 0.1, max = 100)
        public static float[] GUN_COST = {5.0f, 3.0f, 20.0f, 12.5f};
        /** Zo ver reikt een schot (blokken). */
        @Tunable(value = "How far a shot reaches (blocks), per gun", min = 1, max = 512)
        public static float[] GUN_RANGE = {64.0f, 96.0f, 192.0f, 24.0f};
        /** Zo lang duurt herladen (ticks): de animatie in je spel duurt even lang. */
        @Tunable(value = "How long reloading takes (ticks), per gun", min = 5, max = 200)
        public static float[] GUN_RELOAD = {30.0f, 44.0f, 60.0f, 52.0f};
        /** Hoofdstuk 57: alleen de cel eruit (R zonder pouch, of U) duurt dit deel van de herlaadtijd. */
        @Tunable(value = "Taking the cell out on its own (R without a pouch, or U), as a part of the reload time", min = 0.05, max = 2)
        public static float GUN_EJECT_PART = 0.5f;
        /** Een cel er met de hand in (rechtsklik met de cel) duurt dit deel van de herlaadtijd. */
        @Tunable(value = "Putting a cell in by hand (right-click with the cell), as a part of the reload time", min = 0.05, max = 2)
        public static float GUN_INSERT_PART = 0.75f;
        /** Hoe ver een schot van het midden af kan gaan als je niet richt (graden). */
        @Tunable(value = "How far a shot can stray when you shoot from the hip (degrees), per gun", max = 45)
        public static float[] GUN_SPREAD = {2.0f, 3.0f, 6.0f, 8.0f};
        /** Hoe ver een schot van het midden af kan gaan als je door het vizier richt (graden). */
        @Tunable(value = "How far a shot can stray while aiming down the sights (degrees), per gun", max = 45)
        public static float[] GUN_SPREAD_AIMING = {0.5f, 0.8f, 0.0f, 6.0f};
        /** Zoveel stralen schiet de shotgun in één keer. */
        @Tunable(value = "How many rays the laser shotgun fires at once", min = 1, max = 32)
        public static float SHOTGUN_RAYS = 8.0f;
        /** Zoveel keer meer schade bij een schot in het hoofd. */
        @Tunable(value = "Damage multiplier for a headshot", min = 1, max = 10)
        public static float HEADSHOT = 1.5f;
    }

    // =============================================================================================== Guns

    @Section("Firearms (chapter 52): the Glock 19, its parts and materials, magazines, bullets, casings, jams and the handling times")
    public static final class Guns {
        private Guns() {}

        // ------------------------------------------------------------------------- Per pistool (volgorde: Glock 19)
        /** Schade van één kogel van dichtbij (halve hartjes). */
        @Tunable(value = "Damage of one bullet up close (half hearts), per firearm: Glock 19", max = 1_000)
        public static float[] DAMAGE = {5.0f};
        /** Zoveel ticks minstens tussen twee schoten (een Glock schiet zo snel als je klikt, tot deze grens). */
        @Tunable(value = "Ticks between two shots at least (a Glock fires as fast as you click, up to this), per firearm", min = 1, max = 200)
        public static float[] INTERVAL = {3.0f};
        /** Tot zo ver doet een kogel zijn volle schade (blokken). */
        @Tunable(value = "Up to this distance a bullet does its full damage (blocks), per firearm", min = 1, max = 512)
        public static float[] FULL_RANGE = {24.0f};
        /** Zo ver vliegt een kogel hoogstens (blokken); op het eind doet hij nog farDamage van zijn schade. */
        @Tunable(value = "How far a bullet flies at most (blocks), per firearm", min = 1, max = 512)
        public static float[] RANGE = {80.0f};
        /** Hoe snel een kogel vliegt (blokken per tick; 16 = ongeveer 300 meter per seconde). */
        @Tunable(value = "Bullet speed (blocks per tick; 16 is about 300 m/s), per firearm", min = 1, max = 64)
        public static float[] BULLET_SPEED = {16.0f};
        /** Zoveel patronen gaan er in een magazijn (een echte Glock 19: 15). */
        @Tunable(value = "Rounds in a magazine (a real Glock 19 holds 15), per firearm", min = 1, max = 100)
        public static float[] MAGAZINE_CAPACITY = {15.0f};
        /** Hoe ver een schot van het midden af kan gaan als je vanuit de heup schiet (graden). */
        @Tunable(value = "How far a shot can stray when you shoot from the hip (degrees), per firearm", max = 45)
        public static float[] SPREAD = {3.5f};
        /** Hetzelfde als je door het vizier richt. */
        @Tunable(value = "How far a shot can stray while aiming down the sights (degrees), per firearm", max = 45)
        public static float[] SPREAD_AIMING = {0.8f};
        /** Zo hard duwt een schot je zicht omhoog (graden), vanuit de heup... */
        @Tunable(value = "How hard a shot kicks your view up when shooting from the hip (degrees), per firearm", max = 30)
        public static float[] RECOIL = {1.8f};
        /** ...en als je richt. */
        @Tunable(value = "How hard a shot kicks your view up while aiming (degrees), per firearm", max = 30)
        public static float[] RECOIL_AIMING = {1.1f};

        // ------------------------------------------------------------------------- Kogels en hulzen
        /** Hoeveel een kogel per tick zakt (blokken per tick per tick): een heel klein beetje. */
        @Tunable(value = "How much a bullet drops per tick (blocks per tick per tick)", max = 1)
        public static float BULLET_GRAVITY = 0.02f;
        /** Zoveel keer meer schade bij een schot in het hoofd. */
        @Tunable(value = "Damage multiplier for a headshot", min = 1, max = 10)
        public static float HEADSHOT = 2.0f;
        /** Zoveel van zijn schade doet een kogel nog op zijn verste punt (0,5 = de helft). */
        @Tunable(value = "Fraction of the damage a bullet still does at its farthest point (0.5 = half)", max = 1)
        public static float FAR_DAMAGE = 0.5f;
        /** Breken kogels glas? (Nooit in een beschermde zone, nooit verstevigd glas.) */
        @Tunable("Do bullets break glass? (Never in a protected zone, never reinforced glass)")
        public static boolean BULLETS_BREAK_GLASS = true;
        /** Na zoveel seconden verdwijnt een huls die op de grond ligt. */
        @Tunable(value = "Seconds before a casing on the ground disappears", min = 10, max = 86_400)
        public static float CASING_SECONDS = 600.0f;
        /** Tot zo ver van waar je klikt raap je hulzen op (blokken). */
        @Tunable(value = "How far from where you right-click casings are picked up (blocks)", min = 0.5, max = 8)
        public static float CASING_PICKUP_RADIUS = 1.5f;

        // ------------------------------------------------------------------------- De materialen (volgorde: polymeer, staal, titanium, netherite)
        /** Zoveel procent kans per schot dat een onderdeel van dit materiaal één punt slijt (van de 100). Netherite: alleen laseronderdelen (hoofdstuk 55). */
        @Tunable(value = "Percent chance per shot that a part wears one point (of 100), per material: polymer, steel, titanium, netherite (laser parts only)", max = 100)
        public static float[] WEAR_CHANCE = {4.0f, 2.0f, 0.8f, 0.3f};
        /** Hoeveel vaker een onderdeel van dit materiaal een storing geeft (1 = gewoon, 0,6 = veel minder). */
        @Tunable(value = "How much more often a part of this material jams or misfires (1 = normal), per material", max = 10)
        public static float[] MATERIAL_RELIABILITY = {1.5f, 1.0f, 0.6f, 0.5f};
        /** De schade, maal dit, naar het materiaal van de loop (of de emitter van een lasergeweer). */
        @Tunable(value = "Damage multiplier by the material of the barrel (or a laser gun's emitter), per material", min = 0.1, max = 5)
        public static float[] MATERIAL_DAMAGE = {1.0f, 1.0f, 1.1f, 1.15f};
        /** De terugslag, maal dit, naar het materiaal van de slede (of de receiver van een lasergeweer). */
        @Tunable(value = "Recoil multiplier by the material of the slide (or a laser gun's receiver), per material", min = 0.1, max = 5)
        public static float[] MATERIAL_RECOIL = {1.1f, 1.0f, 0.85f, 0.8f};
        /** Hoe lang trekken, richten en herladen duren, maal dit, naar het materiaal van het frame (kleiner = sneller). */
        @Tunable(value = "Handling time multiplier (drawing, reloading) by the material of the frame, per material", min = 0.1, max = 5)
        public static float[] MATERIAL_HANDLING = {0.9f, 1.0f, 1.05f, 1.0f};

        // ------------------------------------------------------------------------- Slijtage en storingen
        /** Onder deze conditie is een onderdeel versleten en geeft het steeds meer storingen. */
        @Tunable(value = "Below this condition (of 100) a part counts as worn and causes more and more jams", max = 100)
        public static float WORN_BELOW = 40.0f;
        /** Zoveel keer meer storingen geeft een onderdeel dat bijna kapot is. */
        @Tunable(value = "How many times more jams a nearly broken part causes", max = 50)
        public static float WORN_JAM_FACTOR = 3.0f;
        /** Zoveel procent kans per schot dat de huls klem blijft zitten (een stovepipe), met nieuwe stalen onderdelen. */
        @Tunable(value = "Percent chance per shot that the casing gets stuck (stovepipe), with new steel parts", max = 100)
        public static float JAM_CHANCE = 0.4f;
        /** Zoveel procent kans per schot op een ketser (de patroon gaat niet af), met nieuwe stalen onderdelen. */
        @Tunable(value = "Percent chance per shot of a misfire (the round does not go off), with new steel parts", max = 100)
        public static float MISFIRE_CHANCE = 0.15f;

        // ------------------------------------------------------------------------- Hoe lang alles duurt (ticks)
        @Tunable(value = "Ticks to drop the magazine out of the pistol (R without a magazine pouch, or U)", min = 1, max = 200)
        public static float EJECT_TICKS = 8.0f;
        @Tunable(value = "Ticks to push a magazine into the pistol (right-click with a magazine)", min = 1, max = 200)
        public static float INSERT_TICKS = 12.0f;
        @Tunable(value = "Ticks to release the slide (the first round goes into the chamber)", min = 1, max = 200)
        public static float SLIDE_TICKS = 7.0f;
        @Tunable(value = "Ticks for a tap-rack after a jam (R)", min = 1, max = 200)
        public static float TAP_RACK_TICKS = 26.0f;
        @Tunable(value = "Ticks to put one round into the chamber by hand (sneak + R)", min = 1, max = 200)
        public static float CHAMBER_TICKS = 22.0f;
        @Tunable(value = "Ticks to push one round into a magazine you hold (R)", min = 1, max = 200)
        public static float LOAD_ROUND_TICKS = 6.0f;
        @Tunable(value = "Ticks to take one round out of a magazine you hold (U)", min = 1, max = 200)
        public static float UNLOAD_ROUND_TICKS = 6.0f;
        @Tunable(value = "Ticks to rack the round out of the chamber by hand (U with a pistol without a magazine)", min = 1, max = 200)
        public static float UNLOAD_CHAMBER_TICKS = 16.0f;
        @Tunable(value = "Ticks to reload from a magazine pouch (R, then pick a magazine)", min = 1, max = 200)
        public static float POUCH_RELOAD_TICKS = 30.0f;
        @Tunable(value = "Ticks to draw the pistol from its holster", min = 1, max = 200)
        public static float DRAW_TICKS = 12.0f;

        // ------------------------------------------------------------------------- De skills (stap 3)
        /** Zoveel ervaring geeft elk ding dat je met een pistool doet. */
        @Tunable(value = "Skill experience for one shot", max = 1_000)
        public static float XP_SHOT = 1.0f;
        @Tunable(value = "Skill experience for hitting something with a bullet", max = 1_000)
        public static float XP_HIT = 2.0f;
        @Tunable(value = "Skill experience for killing something with a bullet", max = 1_000)
        public static float XP_KILL = 10.0f;
        @Tunable(value = "Skill experience for reloading (a magazine in)", max = 1_000)
        public static float XP_RELOAD = 3.0f;
        @Tunable(value = "Skill experience for clearing a jam (tap-rack)", max = 1_000)
        public static float XP_TAP_RACK = 2.0f;
        @Tunable(value = "Skill experience for loading one round into a magazine", max = 1_000)
        public static float XP_ROUND_LOADED = 1.0f;
        @Tunable(value = "Skill experience for drawing from the holster", max = 1_000)
        public static float XP_DRAW = 1.0f;
        /** Zoveel ervaring kost het eerste skillpunt; elk volgende punt maal xpGrowth meer. */
        @Tunable(value = "Experience for the first skill point", min = 1, max = 1_000_000)
        public static float XP_FIRST_POINT = 60.0f;
        @Tunable(value = "Each next skill point costs this much more (1.2 = 20% more than the one before)", min = 1, max = 5)
        public static float XP_GROWTH = 1.2f;
        /** Het hoogste niveau van een skill. */
        @Tunable(value = "Highest level of a skill", min = 1, max = 20)
        public static float SKILL_MAX_LEVEL = 5.0f;
        /** Hoeveel elk niveau eraf haalt: globaal (geldt voor elk vuurwapen) en per wapen. */
        @Tunable(value = "How much each level of a GLOBAL skill takes off (0.05 = 5% faster or more accurate)", max = 1)
        public static float SKILL_GLOBAL_STEP = 0.05f;
        @Tunable(value = "How much each level of a PER-WEAPON skill takes off (0.08 = 8%)", max = 1)
        public static float SKILL_WEAPON_STEP = 0.08f;
        /** Zo kort of klein wordt iets hoogstens (0,3 = 70% eraf). */
        @Tunable(value = "The lowest the skills can bring a time or spread (0.3 = 70% off at most)", min = 0.05, max = 1)
        public static float SKILL_MIN_FACTOR = 0.3f;

        // ------------------------------------------------------------------------- De lasergeweren uit onderdelen (hoofdstuk 55)
        /** Laseronderdelen slijten zoveel keer zo snel als Glock-onderdelen van hetzelfde materiaal (jouw keuze: iets minder). */
        @Tunable(value = "Laser gun parts wear this many times as fast as Glock parts of the same material (0.5 = half the chance per shot)", max = 10)
        public static float LASER_WEAR_FACTOR = 0.5f;
        /** Hoeveel slechter een bijna kapot laseronderdeel zijn eigen cijfer maakt (0,5 = tot 50% minder schade, meer spreiding, trager...). */
        @Tunable(value = "How much worse a nearly broken laser part makes its own number (0.5 = up to 50% less damage, more spread, slower)", max = 5)
        public static float LASER_WORN_PENALTY = 0.5f;
        /** Zo lang duurt het om een geweer van je sling (rug of borst) te trekken. */
        @Tunable(value = "Ticks to pull a laser gun off your sling (back or chest)", min = 1, max = 200)
        public static float SLING_DRAW_TICKS = 18.0f;

        // ------------------------------------------------------------------------- Holster en pouches (stap 2)
        @Tunable(value = "Grenades that fit in a Grenade Pouch", min = 1, max = 27)
        public static float GRENADE_POUCH_SIZE = 4.0f;
        /** Hoofdstuk 58 (jouw wens): zes (was drie). Let op: een bestaand configbestand houdt zijn eigen getal. */
        @Tunable(value = "Magazines (or energy cells) that fit in a Magazine Pouch", min = 1, max = 9)
        public static float MAG_POUCH_SIZE = 6.0f;
    }
}
