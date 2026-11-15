package com.senne.imttech.logic;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.imt.ImtUplink;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.security.net.CoreNetworkData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * De bril draait op je datacenter (hoofdstuk 106, jouw wens van 13 november: "IMT is te OP ... je hebt een datacenter
 * nodig voor de IMT glasses zijn data. Elke satelliet request, waypoint gebruikt wat van de server: waypoint neemt enkel
 * ssd en ram, cpu en/of gpu als je navigeert. Andere dingen die soort van nadenken gebruiken dus altijd cpu en/of gpu en
 * ram. Data opslag moet op een ssd.").
 *
 * Hoe het werkt (alle getallen staan in {@link ImtConfig.Uplink}):
 * <ul>
 *   <li><b>Welk datacenter</b> (hoofdstuk 122 en 123, jouw wensen van 15 november: "je moet je bril connecten via het ip
 *       van je security core met password voor dat die bril aan de serverroom hangt", en daarna: "zorg dat de bril
 *       connectie niet aan de speler hangt maar echt aan de bril; als iemand het wachtwoord kent kan die ook connecten
 *       zonder trusted te zijn"): het datacenter van de kern waarmee je je bril verbond, in het G-menu (Systems,
 *       Datacenter) met het IP en het wachtwoord van die kern (zie {@link GlassesUplink}). De verbinding staat op de bril
 *       zelf ({@link ImtUplink}). Wie het wachtwoord kent, mag verbinden; zet de eigenaar een nieuw wachtwoord (of neemt
 *       iemand de kern over, wat het wachtwoord wist), dan moet elke bril opnieuw verbinden. Het brein draait op hetzelfde
 *       datacenter (zie {@link DatacenterBrain#best}). Niet verbonden: de bril doet niets meer dat nadenkt of iets
 *       bewaart. In creative heb je er geen nodig.</li>
 *   <li><b>Rekenkracht</b> (CPU's en GPU's): een CPU telt 1, een GPU 2, maal wat het RAM voedt, en warm de helft (de
 *       "power" van de tegel BRAIN in de app Monitor). Elk verzoek neemt daar een stukje van, zolang het bezig is.</li>
 *   <li><b>Eenmalige verzoeken</b> (een satellietaanval, een ertsen-scan, een opdracht aan je drones, een hack starten,
 *       een illusie, relais schakelen) houden het datacenter een paar seconden bezig. Past het er niet meer bij, dan
 *       zegt I.M.T "Datacenter busy" en gebeurt er niets: je raakt geen energie kwijt en er begint geen wachttijd.</li>
 *   <li><b>Wat doorloopt</b> (navigeren, de dreigingsscan, de doelwit-analyse, elke speler die je hackt, een
 *       spionagedrone) neemt zijn stuk zolang het aan staat.</li>
 *   <li><b>RAM</b>: elk verzoek neemt ook RAM zolang het bezig is, en elk waypoint een klein beetje zolang je bril op
 *       staat.</li>
 *   <li><b>SSD</b>: wat de bril bewaart (je waypoints) staat op de SSD's. Een nieuw waypoint heeft daar plaats nodig. Wat
 *       je al had, blijft staan, ook zonder datacenter ("de dingen die spelers nu al in IMT glasses hebben bewaard zonder
 *       datacenter blijven"); het telt wel mee zodra je een datacenter hebt.</li>
 * </ul>
 * Wat hier NIET onder valt (dat is de bril of het harnas zelf, geen nadenken of data): het beeld, nachtzicht, je schild,
 * de laser cannon en de raketten, de camera van een drone, berichten en pings, en stoppen of landen (dat kan altijd).
 *
 * Alles hier staat in het geheugen van de server (zoals wachttijden): wie waar mee bezig is, vergeten we als de wereld
 * sluit. Alleen hoeveel de bril van een speler bewaart (en op welk datacenter), staat in {@link Datacenters}: zo telt het
 * ook mee op de SSD's als die speler niet online is.
 */
public final class DatacenterGlasses {
    private DatacenterGlasses() {}

    /** Wat de bril van zijn datacenter kan vragen. */
    public enum Kind {
        /** Een satellietaanval (K, P of C). */
        SATELLITE("satellite strike", false),
        /** Een ertsen-scan. */
        SCAN("ore scan", false),
        /** Al het andere eenmalige: een opdracht aan je drones, een hack starten, een illusie, relais schakelen. */
        ORDER("order", false),
        /** Navigeren naar een waypoint of je sterfplek (zolang het aan staat). */
        NAVIGATION("navigation", true),
        /** De dreigingsscan (zolang hij aan staat). */
        THREAT_SCAN("threat scan", true),
        /** De doelwit-analyse (zolang ze aan staat). */
        TARGET_ANALYSIS("target analysis", true),
        /** Een speler die je hackt (per speler, zolang de hack loopt). */
        HACK("hack", true),
        /** Een spionagedrone (zolang hij volgt). */
        SPY("spy drone", true);

        private final String label;
        private final boolean standing;

        Kind(String label, boolean standing) {
            this.label = label;
            this.standing = standing;
        }

        /** Hoe dit in de app Monitor heet (Engels: je ziet het in het spel). */
        public String label() {
            return label;
        }

        /** Loopt dit door zolang het aan staat (true), of is het een eenmalig verzoek (false)? */
        public boolean standing() {
            return standing;
        }

        /** Hoeveel rekenkracht dit neemt (een CPU is 1, een GPU 2). */
        public double power() {
            return Math.max(0.0, switch (this) {
                case SATELLITE -> ImtConfig.Uplink.SATELLITE_POWER;
                case SCAN -> ImtConfig.Uplink.SCAN_POWER;
                case ORDER -> ImtConfig.Uplink.ORDER_POWER;
                case NAVIGATION -> ImtConfig.Uplink.NAVIGATION_POWER;
                case THREAT_SCAN, TARGET_ANALYSIS -> ImtConfig.Uplink.SENSOR_POWER;
                case HACK, SPY -> ImtConfig.Uplink.TRACKING_POWER;
            });
        }

        /** Hoeveel MB RAM dit neemt zolang het bezig is. */
        public double ramMb() {
            return Math.max(0.0, switch (this) {
                case SATELLITE -> ImtConfig.Uplink.SATELLITE_RAM_MB;
                case SCAN -> ImtConfig.Uplink.SCAN_RAM_MB;
                case ORDER -> ImtConfig.Uplink.ORDER_RAM_MB;
                case NAVIGATION -> ImtConfig.Uplink.NAVIGATION_RAM_MB;
                case THREAT_SCAN, TARGET_ANALYSIS -> ImtConfig.Uplink.SENSOR_RAM_MB;
                case HACK, SPY -> ImtConfig.Uplink.TRACKING_RAM_MB;
            });
        }

        /** Hoe lang (ticks) het datacenter met een eenmalig verzoek bezig is. */
        public int ticks() {
            return Math.max(1, Math.round(switch (this) {
                case SATELLITE -> ImtConfig.Uplink.SATELLITE_TICKS;
                case SCAN -> ImtConfig.Uplink.SCAN_TICKS;
                default -> ImtConfig.Uplink.ORDER_TICKS;
            }));
        }
    }

    /**
     * Hoe de bril van een speler met zijn datacenter verbonden is. Het nummer (de volgorde hier) gaat naar je spel, voor de
     * HUD en het G-menu: voeg iets nieuws dus achteraan toe.
     */
    public enum Link {
        /** Hij heeft er geen nodig: creative, of de regel staat uit in de config. */
        NOT_NEEDED,
        /** Verbonden met een datacenter dat draait, en hij mag erop (de eigenaar of vertrouwd). */
        OK,
        /** Op het IP waarmee zijn bril verbonden is, antwoordt geen datacenter meer (de kern is weg, of van niemand). */
        NONE,
        /** Verbonden, maar dat datacenter draait niet (geen stroom, geen racks, of er mist een onderdeel). */
        OFFLINE,
        /** Hoofdstuk 122: zijn bril is met geen enkele kern verbonden. */
        NOT_CONNECTED,
        /**
         * Hoofdstuk 123: verbonden met een wachtwoord dat de kern niet meer heeft (de eigenaar zette een nieuw, of iemand nam
         * de kern over): opnieuw verbinden met het nieuwe wachtwoord.
         */
        PASSWORD_CHANGED;

        /** Kan de bril nu nadenken en bewaren? */
        public boolean works() {
            return this == NOT_NEEDED || this == OK;
        }
    }

    // ================================================================ Geldt de regel?

    /** Voor de tests en de rondleidingen: de regel aan (true) of uit (false), of null = de config beslist. */
    private static @Nullable Boolean requiredOverride;

    /**
     * Heeft de bril een datacenter nodig ({@code [uplink] required} in de config)?
     *
     * Tijdens de automatische tests ({@code gradlew runGameTestServer}) geldt de oude regel, zodat alle tests van de bril
     * van vóór hoofdstuk 106 geen datacenter hoeven te bouwen; de tests van deze regel zetten hem zelf aan (zoals het
     * netwerk in LanGrid.required).
     */
    public static boolean required() {
        Boolean override = requiredOverride;
        if (override != null) {
            return override;
        }
        if (ServerLifecycleHooks.getCurrentServer() instanceof GameTestServer) {
            return false;
        }
        return ImtConfig.Uplink.REQUIRED;
    }

    /** Alleen voor de tests en de rondleidingen: de regel aan of uit zetten, of met null weer de config laten beslissen. */
    public static void overrideRequired(@Nullable Boolean required) {
        requiredOverride = required;
    }

    // ================================================================ Welk datacenter?

    /** Het datacenter waar de bril van deze speler op draait: waar het staat, en hoe het ervoor staat. */
    private record Server(GlobalPos pos, Datacenters.Summary summary) {
        /** Rekenkracht voor de bril: een CPU 1, een GPU 2, maal wat het RAM voedt, warm de helft. */
        double power() {
            if (summary.power() >= 0) {
                return summary.power();
            }
            // Een wereld van voor hoofdstuk 106: het datacenter meldde zijn kracht nog niet (dat doet het zodra zijn
            // stuk wereld weer geladen is). Tot dan schatten we ze uit de vertraging van het brein.
            return LogicStats.brainPowerFor(summary.brainDelay());
        }

        /** Zoveel MB RAM heeft het (oneindig als het dat nog niet meldde, zie power). */
        double ramMb() {
            return summary.ramMb() >= 0 ? summary.ramMb() : Double.POSITIVE_INFINITY;
        }
    }

    /**
     * Waar de bril van een speler aan hangt (hoofdstuk 122): hoe het ervoor staat, het datacenter als het werkt, en de naam
     * van de kern (voor de meldingen, "" als hij met geen enkele verbonden is).
     */
    private record Resolved(Link link, @Nullable Server server, String host) {}

    /**
     * Zoekt op waar deze verbinding (van een bril) aan hangt: de kern met dat IP in het netwerk van de wereld
     * (CoreNetworkData), hoe zijn datacenter ervoor staat ({@link Datacenters#summary}, ook als de kern niet geladen is), en
     * of het wachtwoord van toen nog geldt (hoofdstuk 123). Zo laden we nooit een stuk wereld, ook niet als dit elke seconde
     * gebeurt.
     */
    private static Resolved resolve(MinecraftServer server, @Nullable ImtUplink uplink) {
        if (uplink == null) {
            return new Resolved(Link.NOT_CONNECTED, null, "");
        }
        GlobalPos pos = CoreNetworkData.get(server).find(uplink.ip());
        Datacenters.Summary summary = pos == null ? null : Datacenters.get(server).summary(pos);
        if (pos == null || summary == null || summary.owner() == null) {
            return new Resolved(Link.NONE, null, uplink.host());
        }
        // Hoofdstuk 123: verbonden met een wachtwoord dat de kern niet meer heeft (een nieuw, of geen meer na een overname).
        if (uplink.stamp().isEmpty() || !uplink.stamp().equals(summary.passwordStamp())) {
            return new Resolved(Link.PASSWORD_CHANGED, null, summary.host());
        }
        if (!summary.online() || summary.brainDelay() < 0) {
            return new Resolved(Link.OFFLINE, null, summary.host());
        }
        return new Resolved(Link.OK, new Server(pos, summary), summary.host());
    }

    /**
     * De verbinding op de bril die deze speler op heeft (hoofdstuk 123: die staat op de bril zelf, zie {@link ImtUplink}).
     * Alleen zijn eigen bril telt: een bril van een ander werkt toch niet voor hem.
     */
    private static @Nullable ImtUplink uplinkOf(ServerPlayer player) {
        ItemStack glasses = ImtData.getWornGlasses(player);
        return glasses.isEmpty() || !ImtData.isAuthorized(glasses, player) ? null : ImtData.getUplink(glasses);
    }

    private static Resolved resolve(ServerPlayer player) {
        return resolve(player.server, uplinkOf(player));
    }

    /** Is de bril die deze speler op heeft met een kern verbonden (of het daar nu werkt of niet)? */
    public static boolean connected(ServerPlayer player) {
        return uplinkOf(player) != null;
    }

    private static @Nullable Server serving(ServerPlayer player) {
        return resolve(player).server();
    }

    /** Hoe de bril van deze speler met zijn datacenter verbonden is. */
    public static Link link(ServerPlayer player) {
        if (!required() || player.isCreative()) {
            return Link.NOT_NEEDED;
        }
        return resolve(player).link();
    }

    /**
     * Hoofdstuk 122: het datacenter waar de bril van deze speler aan hangt, als het draait en hij erop mag; anders null.
     * Daar draait ook zijn brein (zie {@link DatacenterBrain#best}), ook in creative en als de regel uit staat.
     */
    public static @Nullable Map.Entry<GlobalPos, Datacenters.Summary> linkedDatacenter(ServerPlayer player) {
        Server server = serving(player);
        return server == null ? null : Map.entry(server.pos(), server.summary());
    }

    /**
     * Voor de HUD en het G-menu (hoofdstuk 122): de naam en het IP van de kern waar de bril van deze speler mee verbonden
     * is (de naam van nu als we die kennen, anders die van toen hij verbond), of twee keer "" als met geen enkele.
     */
    public static String[] uplinkLabel(ServerPlayer player) {
        ImtUplink uplink = uplinkOf(player);
        if (uplink == null) {
            return new String[] {"", ""};
        }
        String host = resolve(player).host();
        return new String[] {host.isEmpty() ? uplink.host() : host, uplink.ip()};
    }

    /** Kan de bril van deze speler nu nadenken en bewaren? (Zonder melding.) */
    public static boolean linked(ServerPlayer player) {
        return link(player).works();
    }

    /** Hetzelfde, maar zegt de speler waarom niet (niet verbonden, een nieuw wachtwoord, geen datacenter, of het draait niet). */
    public static boolean requireLink(ServerPlayer player) {
        Link link = link(player);
        if (link.works()) {
            return true;
        }
        tellNoLink(player, link);
        return false;
    }

    /** Zegt de speler waarom zijn bril nu geen datacenter heeft (met de naam van de kern, als hij verbonden is). */
    private static void tellNoLink(ServerPlayer player, Link link) {
        String host = resolve(player).host();
        Component message = switch (link) {
            case NOT_CONNECTED -> Component.translatable("imt.imttech.msg.datacenter_not_connected");
            case PASSWORD_CHANGED -> Component.translatable("imt.imttech.msg.datacenter_password_changed", host);
            case OFFLINE -> Component.translatable("imt.imttech.msg.datacenter_offline", host);
            default -> Component.translatable("imt.imttech.msg.datacenter_none", host);
        };
        ImtServer.send(player, message, ImtMessagePayload.Level.WARNING);
    }

    // ================================================================ Wat er bezig is

    /** Een eenmalig verzoek waar een datacenter nog mee bezig is: van wie, wat, en tot wanneer. */
    private record Job(UUID player, String name, Kind kind, long until) {}

    /** Wat er van één speler doorloopt (elke seconde opnieuw gemeld, zie {@link #standing}). */
    private record Standing(GlobalPos datacenter, String name, List<Kind> kinds, int waypoints, long until) {
        double power() {
            double total = 0;
            for (Kind kind : kinds) {
                total += kind.power();
            }
            return total;
        }

        double ramMb() {
            double total = waypoints * Math.max(0.0, ImtConfig.Uplink.WAYPOINT_RAM_MB);
            for (Kind kind : kinds) {
                total += kind.ramMb();
            }
            return total;
        }
    }

    private static final Map<GlobalPos, List<Job>> JOBS = new HashMap<>();
    private static final Map<UUID, Standing> STANDING = new HashMap<>();

    /** Zo lang (ticks) geldt wat een speler meldde dat doorloopt; zijn bril meldt het elke seconde opnieuw. */
    private static final int STANDING_TICKS = 40;

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** De verzoeken waar dit datacenter nog mee bezig is (wat klaar is, gaat eruit). */
    private static List<Job> jobs(GlobalPos datacenter, long now) {
        List<Job> jobs = JOBS.get(datacenter);
        if (jobs == null) {
            return List.of();
        }
        // Ook weg als de tijd terug sprong (een ander getal dan we ooit konden zetten): dan klopt "tot wanneer" niet meer.
        jobs.removeIf(job -> now >= job.until() || job.until() - now > 20L * 60 * 10);
        if (jobs.isEmpty()) {
            JOBS.remove(datacenter);
            return List.of();
        }
        return jobs;
    }

    /** Wat er op dit datacenter doorloopt (van spelers wier bril het nog meldt). */
    private static List<Standing> standingOn(GlobalPos datacenter, long now) {
        List<Standing> found = new ArrayList<>();
        Iterator<Standing> it = STANDING.values().iterator();
        while (it.hasNext()) {
            Standing standing = it.next();
            if (now >= standing.until() || standing.until() - now > STANDING_TICKS) {
                it.remove(); // de bril ging af, de speler is weg, of de tijd sprong
            } else if (standing.datacenter().equals(datacenter)) {
                found.add(standing);
            }
        }
        return found;
    }

    /** Hoeveel rekenkracht de brillen nu van dit datacenter gebruiken (een CPU is 1, een GPU 2). */
    public static double usedPower(MinecraftServer server, GlobalPos datacenter) {
        long now = now(server);
        double total = 0;
        for (Job job : jobs(datacenter, now)) {
            total += job.kind().power();
        }
        for (Standing standing : standingOn(datacenter, now)) {
            total += standing.power();
        }
        return total;
    }

    /** Hoeveel MB RAM de brillen nu van dit datacenter gebruiken. */
    public static double usedRamMb(MinecraftServer server, GlobalPos datacenter) {
        long now = now(server);
        double total = 0;
        for (Job job : jobs(datacenter, now)) {
            total += job.kind().ramMb();
        }
        for (Standing standing : standingOn(datacenter, now)) {
            total += standing.ramMb();
        }
        return total;
    }

    /**
     * Wat de brillen nu op dit datacenter doen, voor de lijst RUNNING van de app Monitor: "satellite strike (Senne)",
     * "navigation (Senne)"...
     */
    public static List<String> running(MinecraftServer server, GlobalPos datacenter) {
        long now = now(server);
        List<String> lines = new ArrayList<>();
        for (Job job : jobs(datacenter, now)) {
            lines.add(job.kind().label() + " (" + job.name() + ")");
        }
        for (Standing standing : standingOn(datacenter, now)) {
            for (Kind kind : standing.kinds()) {
                lines.add(kind.label() + " (" + standing.name() + ")");
            }
        }
        return lines;
    }

    /**
     * Hoeveel MB de brillen op de SSD's van dit datacenter bewaren: van iedereen wiens bril de laatste keer verbonden met
     * deze kern werkte, ook als hij nu niet online is, en ook als het datacenter nu niet draait (de SSD's zijn er nog).
     */
    public static double storedMb(MinecraftServer server, GlobalPos datacenter) {
        return storedMb(server, datacenter, null);
    }

    /** Hetzelfde, zonder wat de bril van {@code except} bewaart (die telt een verzoek zelf mee, zie {@link #roomToStore}). */
    private static double storedMb(MinecraftServer server, GlobalPos datacenter, @Nullable UUID except) {
        if (!required()) {
            return 0;
        }
        double total = 0;
        for (Map.Entry<UUID, Datacenters.GlassesData> entry : Datacenters.get(server).glassesData().entrySet()) {
            if (!entry.getKey().equals(except) && storesOn(server, entry.getValue(), datacenter)) {
                total += entry.getValue().mb();
            }
        }
        return total;
    }

    /**
     * Staat wat deze bril bewaart op dit datacenter? Dat is het datacenter waar hij de laatste keer verbonden mee werkte
     * (hoofdstuk 123: zijn speler kan offline zijn, of de bril afhebben; dan geldt wat we het laatst zagen).
     */
    private static boolean storesOn(MinecraftServer server, Datacenters.GlassesData data, GlobalPos datacenter) {
        return !data.ip().isEmpty() && datacenter.equals(CoreNetworkData.get(server).find(data.ip()));
    }

    /**
     * Voor de app Monitor (hoofdstuk 122): wiens bril aan dit datacenter hangt, op naam (de laatste keer dat we hem zagen).
     * Zo ziet de eigenaar wie zijn wachtwoord kent en er gebruik van maakt.
     */
    public static List<String> connectedNames(MinecraftServer server, GlobalPos datacenter) {
        List<String> names = new ArrayList<>();
        for (Datacenters.GlassesData data : Datacenters.get(server).glassesData().values()) {
            if (storesOn(server, data, datacenter) && !data.name().isEmpty()) {
                names.add(data.name());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    // ================================================================ Een verzoek

    /**
     * Kan de bril van deze speler dit nu vragen? Zo niet, dan zegt I.M.T waarom (niet verbonden, een nieuw wachtwoord, geen
     * datacenter, het draait niet, het is te zwak, het is bezig, of het RAM is vol) en geeft dit false: er gebeurt dan
     * niets, en dat kost niets.
     *
     * Dit reserveert nog niets: gaat het verzoek echt door, roep dan {@link #started} aan.
     */
    public static boolean ready(ServerPlayer player, Kind kind) {
        Link link = link(player);
        if (link == Link.NOT_NEEDED) {
            return true;
        }
        Server serving = serving(player);
        if (serving == null) {
            tellNoLink(player, link);
            return false;
        }
        String host = serving.summary().host();
        double power = serving.power();
        double need = kind.power();
        if (need > power + 1.0e-6) {
            ImtServer.send(player, Component.translatable("imt.imttech.msg.datacenter_weak", host, plain(need), plain(power)),
                    ImtMessagePayload.Level.WARNING);
            return false;
        }
        // Wat van dit soort van deze speler al doorloopt, telt niet nog eens (hij zet het aan terwijl het al gemeld is).
        double used = usedPower(player.server, serving.pos()) - alreadyStanding(player, kind, serving.pos()) * need;
        if (used + need > power + 1.0e-6) {
            ImtServer.send(player, Component.translatable("imt.imttech.msg.datacenter_busy", host,
                    percent(used, power), plain(need), plain(Math.max(0.0, power - used))), ImtMessagePayload.Level.WARNING);
            return false;
        }
        double ram = serving.ramMb();
        double ramUsed = usedRamMb(player.server, serving.pos()) - alreadyStanding(player, kind, serving.pos()) * kind.ramMb();
        if (ramUsed + kind.ramMb() > ram + 1.0e-6) {
            ImtServer.send(player, Component.translatable("imt.imttech.msg.datacenter_no_ram", host,
                    LogicStats.bytes(kind.ramMb()), LogicStats.bytes(Math.max(0.0, ram - ramUsed))), ImtMessagePayload.Level.WARNING);
            return false;
        }
        return true;
    }

    /** Hoe vaak dit soort al doorloopt voor deze speler op dit datacenter (0 als het daar niet gemeld is). */
    private static int alreadyStanding(ServerPlayer player, Kind kind, GlobalPos datacenter) {
        Standing standing = STANDING.get(player.getUUID());
        if (!kind.standing() || standing == null || !standing.datacenter().equals(datacenter)
                || now(player.server) >= standing.until()) {
            return 0;
        }
        int count = 0;
        for (Kind running : standing.kinds()) {
            if (running == kind) {
                count++;
            }
        }
        return count;
    }

    /**
     * Een eenmalig verzoek gaat echt door (het kostte energie): het datacenter van deze speler is er nu {@link Kind#ticks}
     * mee bezig. Je ziet het in de app Monitor (de tegel BRAIN en de lijst RUNNING), en de racks worden er warmer van.
     */
    public static void started(ServerPlayer player, Kind kind) {
        if (kind.standing() || link(player) != Link.OK) {
            return;
        }
        Server serving = serving(player);
        if (serving == null) {
            return;
        }
        JOBS.computeIfAbsent(serving.pos(), pos -> new ArrayList<>()).add(new Job(player.getUUID(),
                player.getGameProfile().getName(), kind, now(player.server) + kind.ticks()));
    }

    /**
     * Elke seconde, vanuit de bril van een speler: dit loopt er nu door (navigeren, de scans, hacks, een spion), en
     * zoveel waypoints bewaart hij. Zo lang het gemeld wordt, neemt het zijn stuk van het datacenter.
     */
    public static void standing(ServerPlayer player, List<Kind> kinds, int waypoints) {
        if (link(player) != Link.OK) {
            STANDING.remove(player.getUUID());
            if (required() && !player.isCreative()) {
                remember(player, waypoints, ""); // ook zonder datacenter onthouden we hoeveel hij al bewaart (op geen enkel)
            }
            return;
        }
        Server serving = serving(player);
        if (serving == null) {
            STANDING.remove(player.getUUID());
            return;
        }
        STANDING.put(player.getUUID(), new Standing(serving.pos(), player.getGameProfile().getName(), List.copyOf(kinds),
                waypoints, now(player.server) + STANDING_TICKS));
        remember(player, waypoints, serving.summary().ip());
    }

    /** Deze speler is weg (of zijn bril is af): niets van hem loopt nog door. */
    public static void forget(UUID player) {
        STANDING.remove(player);
    }

    /** De wereld sluit: niemand is nog ergens mee bezig. */
    public static void clear() {
        JOBS.clear();
        STANDING.clear();
    }

    // ================================================================ Bewaren (op de SSD's)

    /** Zoveel MB nemen zoveel waypoints in op de SSD's. */
    public static double waypointsMb(int waypoints) {
        return Math.max(0, waypoints) * Math.max(0.0, ImtConfig.Uplink.WAYPOINT_MB);
    }

    /**
     * Onthoudt (in de wereld) hoeveel de bril van deze speler bewaart, en op welk datacenter (het IP van de kern, "" = op
     * geen enkel), zodat het ook meetelt als hij niet online is.
     */
    private static void remember(ServerPlayer player, int waypoints, String ip) {
        Datacenters.get(player.server).setGlassesData(player.getUUID(), waypointsMb(waypoints), ip,
                player.getGameProfile().getName());
    }

    /**
     * Is er plaats om nog {@code mb} MB te bewaren voor de bril van deze speler? Zo niet, dan zegt I.M.T waarom (niet
     * verbonden, een nieuw wachtwoord, geen datacenter, het draait niet, of de SSD's zijn vol) en geeft dit false.
     *
     * @param stored hoeveel waypoints hij nu al bewaart (die tellen mee, ook die van voor hij een datacenter had)
     */
    public static boolean roomToStore(ServerPlayer player, int stored, double mb) {
        Link link = link(player);
        if (link == Link.NOT_NEEDED) {
            return true;
        }
        Server serving = serving(player);
        if (serving == null) {
            tellNoLink(player, link);
            return false;
        }
        double free = freeSsdMb(player.server, serving, player.getUUID(), waypointsMb(stored));
        if (mb > free + 1.0e-6) {
            ImtServer.send(player, Component.translatable("imt.imttech.msg.datacenter_full", serving.summary().host(),
                    LogicStats.bytes(mb), LogicStats.bytes(Math.max(0.0, free))), ImtMessagePayload.Level.WARNING);
            return false;
        }
        return true;
    }

    /**
     * Zoveel MB is er nog vrij op de SSD's: wat erop past, min de diagrammen, de backups die er staan, wat de brillen van
     * de anderen erop bewaren (iedereen die het wachtwoord kent, kan eraan hangen), en wat de bril van {@code player} al
     * bewaart ({@code glassesMb}).
     */
    private static double freeSsdMb(MinecraftServer server, Server serving, UUID player, double glassesMb) {
        Datacenters datacenters = Datacenters.get(server);
        double used = LogicStats.blocksMb(serving.summary().ownBlocks() + datacenters.storedAt(serving.pos()))
                + storedMb(server, serving.pos(), player) + glassesMb;
        return LogicStats.blocksMb(serving.summary().capacity()) - used;
    }

    // ================================================================ Voor de HUD

    /** Hoeveel procent van de rekenkracht van zijn datacenter nu in gebruik is door brillen (0 als hij er geen heeft). */
    public static int loadPercent(ServerPlayer player) {
        if (link(player) != Link.OK) {
            return 0;
        }
        Server serving = serving(player);
        if (serving == null || serving.power() <= 0) {
            return 0;
        }
        return (int) Math.round(Math.min(1.0, usedPower(player.server, serving.pos()) / serving.power()) * 100);
    }

    // ================================================================ Tekst

    /** 0.5 blijft "0.5", 1.0 wordt "1", 0.25 blijft "0.25". */
    private static String plain(double value) {
        double rounded = Math.round(value * 100) / 100.0;
        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) rounded);
        }
        String text = String.format(Locale.ROOT, "%.2f", rounded);
        return text.endsWith("0") ? text.substring(0, text.length() - 1) : text;
    }

    private static int percent(double used, double total) {
        return total <= 0 ? 100 : (int) Math.round(Math.max(0.0, Math.min(1.0, used / total)) * 100);
    }
}
