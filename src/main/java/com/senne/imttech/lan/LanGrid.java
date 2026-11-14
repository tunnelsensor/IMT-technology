package com.senne.imttech.lan;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.security.SecurityCoreBlock;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Het netwerk van een beveiligingskern (hoofdstuk 38, jouw wens van 29 oktober): "je hangt een witte netwerkkabel aan de
 * desktop, verbindt die met een keycardreader, en dan pas komt die bij de desktop devices."
 *
 * Hoe het werkt (net als het stroomnet, zie PowerGrid):
 * <ul>
 *   <li>Een apparaat hangt aan het netwerk als er een netwerkkabel (of PoE-kabel) naast ligt, of als het er bovenop
 *       staat. Kabels van dezelfde soort die tegen elkaar aan liggen, zitten aan elkaar vast; een netwerkkabel en een
 *       PoE-kabel niet (hoofdstuk 85).</li>
 *   <li>We zoeken vanaf het apparaat langs de kabels, net als water door buizen, tot we bij een Security Core komen
 *       (de desktop). Staat het apparaat er direct tegenaan, dan heeft het geen kabel nodig.</li>
 *   <li>Serverracks, laserzenders, brugzenders en Camera Monitors geven het netwerk ook door aan wie ertegenaan staat: een
 *       kast vol racks, een laserpoort, een laserbrug van meer zenders of een schermmuur heeft genoeg aan één kabel (zoals
 *       bij stroom).</li>
 *   <li>Een open kabel binnen een EMP-puls geeft even niets door (zie Emp.cutsCable); een kabel in een muur wel.</li>
 *   <li>Raakt een net twee kernen, dan hoort een apparaat bij de dichtstbijzijnde langs de kabel.</li>
 *   <li>Hoofdstuk 112 (jouw wens: "een router: hier kun je elk ding draadloos tot op 32 blokken op connecten"): een Router
 *       met stroom geeft het netwerk door aan de apparaten (en routers) waar hij draadloos mee verbonden is, en andersom.
 *       Zo'n sprong door de lucht telt in de zoektocht als één stap, net als een stukje kabel. Wie met wie verbonden is,
 *       staat in {@link RouterLinks}.</li>
 * </ul>
 * Het zoeken kost wat werk, dus we onthouden het resultaat. Komt er ergens een kabel bij of gaat er een weg (of iets
 * anders dat het net verandert), dan verhogen we {@link #changed()} en zoeken we de volgende keer opnieuw.
 */
public final class LanGrid {
    private LanGrid() {}

    /** Zoveel stukjes kabel (en racks en zenders) volgen we hooguit vanaf een apparaat dat zijn kern zoekt. */
    public static final int MAX_NODES = 512;
    /**
     * Zoveel vanaf een kern die al zijn apparaten zoekt (hoofdstuk 94, jouw wens: "camera range moet evenveel zijn als de
     * cabel lang is"): een kabel mag lang zijn. Dat zoeken gebeurt maar één keer per kern (om de twee seconden of als er
     * iets verandert), en vult meteen in welke kern elk apparaat heeft; zo hoeft een apparaat aan een lange kabel zelf niet
     * zo ver te zoeken.
     */
    public static final int MAX_NODES_FROM_CORE = 8192;
    /** Zoveel stukken wereld (chunks) onthoudt een kern hooguit voor zijn netwerk (zie NetworkKeeper). */
    public static final int MAX_NETWORK_CHUNKS = 256;
    /** Na zoveel ticks zoeken we sowieso opnieuw welke kern een apparaat heeft (bijvoorbeeld als er een stuk wereld laadde). */
    private static final int CORE_TICKS = 200;
    /** Zo lang (ticks) onthouden we welke apparaten aan een kern hangen (net zo lang als SecurityDevices). */
    private static final int LIST_TICKS = 40;

    /** Telt elke verandering aan een net; staat hij anders dan bij het zoeken, dan zoeken we opnieuw. */
    private static int version;

    /**
     * Welke kern een apparaat had bij het zoeken (null = geen), en of die zoektocht volledig was ({@code complete}): hij
     * vond een kern, of hij kwam nergens langs een stuk wereld dat niet geladen is (en het net was niet te groot). Is hij
     * niet volledig, dan betekent "geen kern" alleen "niet gevonden in wat nu geladen is" (zie {@link #searchComplete}).
     */
    private record Link(int version, long gameTime, @Nullable BlockPos core, boolean complete) {}

    /** Welke apparaten aan een kern hingen bij het zoeken. */
    private record Reach(int version, long gameTime, Set<BlockPos> devices) {}

    private static final Map<ResourceKey<Level>, Map<BlockPos, Link>> CORE_OF = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, Reach>> DEVICES_OF = new HashMap<>();
    /** Per plek: alle netwerkblokken in hetzelfde net (zie {@link #networkOf}). */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Reach>> NETWORK_OF = new HashMap<>();

    /** Voor de automatische tests: het netwerk aan (true) of uit (false) gezet, of null = zoals de config zegt. */
    private static @Nullable Boolean requiredOverride;

    // ================================================================ Moet er een kabel zijn?

    /**
     * Hebben beveiligingsapparaten een netwerkkabel nodig ({@code [network] required} in de config)? Zo niet (een oude
     * wereld), dan horen ze zoals vroeger bij de dichtstbijzijnde kern in wiens gebied ze staan (zie SecurityCores).
     *
     * Tijdens de automatische tests ({@code gradlew runGameTestServer}) geldt de oude regel, zodat alle tests van vóór de
     * netwerkkabels niet bekabeld hoeven te worden; de tests van het netwerk zetten het zelf aan (zie
     * NetworkGameTests).
     */
    public static boolean required() {
        Boolean override = requiredOverride;
        if (override != null) {
            return override;
        }
        if (ServerLifecycleHooks.getCurrentServer() instanceof GameTestServer) {
            return false;
        }
        return ImtConfig.Network.REQUIRED;
    }

    /** Alleen voor de tests: het netwerk aan of uit zetten, of met null weer de config laten beslissen. */
    public static void overrideRequired(@Nullable Boolean required) {
        requiredOverride = required;
        changed();
    }

    // ================================================================ Opvragen

    /** Er kwam een kabel, switch, rack, zender of kern bij, of er ging er een weg: alle netten opnieuw zoeken. */
    public static void changed() {
        version++;
    }

    /**
     * De stand van de teller van {@link #changed()}. Wie zelf iets over het netwerk onthoudt (Routers: welke apparaten al
     * een netwerk hebben), bewaart dit getal erbij: is het veranderd, dan klopt wat hij onthield niet meer.
     */
    public static int version() {
        return version;
    }

    /**
     * De kern waar dit apparaat via netwerkkabels aan hangt (de dichtstbijzijnde langs de kabel), of null als het nergens
     * aan hangt. (Ook als er geen kabel nodig is: dit kijkt alleen naar de kabels; zie SecurityCores.deviceCore.)
     */
    public static @Nullable SecurityCoreBlockEntity coreOf(Level level, BlockPos device) {
        BlockPos core = corePosOf(level, device);
        if (core == null || !level.isLoaded(core)) {
            return null;
        }
        return level.getBlockEntity(core) instanceof SecurityCoreBlockEntity entity ? entity : null;
    }

    /**
     * Waar de kern van dit apparaat staat (uit het geheugen als dat nog klopt), of null. Is het net te groot om vanaf het
     * apparaat te doorzoeken (meer dan {@link #MAX_NODES} stukjes, hoofdstuk 94), dan vragen we het de kernen: die zoeken
     * verder ({@link #MAX_NODES_FROM_CORE}).
     */
    private static @Nullable BlockPos corePosOf(Level level, BlockPos device) {
        return link(level, device).core();
    }

    /** Wat we van dit apparaat weten (uit het geheugen als dat nog klopt, anders opnieuw gezocht). */
    private static Link link(Level level, BlockPos device) {
        Map<BlockPos, Link> perLevel = CORE_OF.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        Link link = perLevel.get(device);
        long now = level.getGameTime();
        if (link == null || link.version() != version || now - link.gameTime() > CORE_TICKS || now < link.gameTime()) {
            boolean[] flags = {false, false}; // te groot, en: langs een stuk wereld dat niet geladen is
            BlockPos core = searchCore(level, device, null, flags);
            if (core == null && flags[0]) {
                core = coreByReach(level, device);
            }
            link = new Link(version, now, core, core != null || !flags[0] && !flags[1]);
            perLevel.put(device.immutable(), link);
        }
        return link;
    }

    /**
     * Loopt er vanaf dit apparaat een weg naar een kern (langs kabels, of via een Router)? Anders dan {@link #coreOf}
     * hoeft het stuk wereld van die kern nu niet geladen te zijn: de weg is gevonden toen dat wel zo was, en zolang er
     * niets aan het net verandert, klopt hij nog. (Gevonden door de nalezing: een relais dat je van ver schakelde, leek
     * "geen netwerk" te hebben als alleen het stuk wereld van zijn kern niet geladen was.)
     */
    public static boolean reachesCore(Level level, BlockPos device) {
        return link(level, device).core() != null;
    }

    /**
     * Was de laatste zoektocht naar de kern van dit apparaat volledig? Zo ja, dan is "geen kern" ({@link #coreOf} geeft
     * null) echt waar: er loopt geen weg naar een kern. Zo nee, dan kwam de zoektocht langs een stuk wereld dat niet
     * geladen is (of het net is te groot): de kern kan daarachter zitten. Hoofdstuk 112: een relais dat je van ver
     * schakelt, staat vaak in zo'n half geladen stuk; dan telt wat het zelf nog weet (zie ImtRedstone.offline).
     */
    public static boolean searchComplete(Level level, BlockPos device) {
        return link(level, device).complete();
    }

    /** Welke geladen kern dit apparaat bij zijn eigen zoektocht vond (voor een net dat te groot is om vanaf het apparaat te doorzoeken). */
    private static @Nullable BlockPos coreByReach(Level level, BlockPos device) {
        for (BlockPos core : com.senne.imttech.security.SecurityCores.loadedCores(level)) {
            if (!SEARCHING.contains(core) && devicesOf(level, core).contains(device)) {
                return core;
            }
        }
        return null;
    }

    /** De kernen die nu hun apparaten aan het zoeken zijn (zo vraagt een zoektocht nooit zichzelf om raad). */
    private static final Set<BlockPos> SEARCHING = new HashSet<>();

    /**
     * Alle apparaten (de plekken van blokken met een netwerkaansluiting, zie NetworkConnectable) die via netwerkkabels aan
     * deze kern hangen. Hooguit LIST_TICKS oud.
     */
    public static Set<BlockPos> devicesOf(Level level, BlockPos core) {
        Map<BlockPos, Reach> perLevel = DEVICES_OF.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        Reach reach = perLevel.get(core);
        long now = level.getGameTime();
        if (reach == null || reach.version() != version || now - reach.gameTime() >= LIST_TICKS || now < reach.gameTime()) {
            reach = new Reach(version, now, searchDevices(level, core));
            perLevel.put(core.immutable(), reach);
        }
        return reach.devices();
    }

    // ================================================================ Zoeken

    /**
     * Een plek in de zoektocht (hoofdstuk 85): een stukje kabel van één soort ({@code kind} is NETWORK of POE), of een blok
     * zelf ({@code kind} is null): het apparaat, de kern, of een blok dat het netwerk doorgeeft (een rack, een zender, een
     * switch...). Liggen een netwerkkabel en een PoE-kabel op dezelfde plek, dan zijn dat twee aparte plekken in de
     * zoektocht: ze zitten niet aan elkaar vast.
     */
    private record Node(BlockPos pos, @Nullable CableKind kind) {}

    /** De kabels die het netwerk doorgeven: de netwerkkabel en de PoE-kabel. */
    private static final CableKind[] NETWORK_KINDS = {CableKind.NETWORK, CableKind.POE};

    /**
     * Zoekt de kern: eerst de zes buren van het apparaat, en dan langs elke kabel verder, van dichtbij naar ver. De eerste
     * kern die we tegenkomen is de dichtstbijzijnde langs de kabel.
     *
     * Een Network Switch (stap 6): je komt er alleen in via een poort die aan staat, en je gaat er alleen door als hij
     * stroom heeft, en dan alleen naar poorten die aan staan. Is het apparaat zelf een switch, dan zoekt hij via zijn
     * eigen poorten die aan staan (ook zonder stroom: dan hangt hij er nog wel aan, maar laat hij niets door).
     *
     * Hoofdstuk 85 (jouw wens: "zorg ook dat power cable, netwerk en poe niet connecten aan elkaar"): een netwerkkabel loopt
     * alleen verder in een netwerkkabel, een PoE-kabel alleen in een PoE-kabel. Van de ene soort naar de andere kom je
     * alleen via een blok (de kern, een switch, een rack...). Elk netwerkblok neemt allebei de soorten aan.
     */
    private static @Nullable BlockPos searchCore(Level level, BlockPos device, @Nullable Map<Node, Node> parents,
                                                 @Nullable boolean[] flags) {
        Set<Node> seen = new HashSet<>();
        ArrayDeque<Node> todo = new ArrayDeque<>();
        Node start = new Node(device.immutable(), null);
        seen.add(start);
        todo.add(start);
        int followed = 0;
        while (!todo.isEmpty() && followed++ < MAX_NODES) {
            Node at = todo.poll();
            BlockState atState = level.getBlockState(at.pos());
            for (Direction direction : Direction.values()) {
                if (closedPort(atState, direction)) {
                    continue;
                }
                BlockPos next = at.pos().relative(direction);
                if (!level.isLoaded(next)) {
                    if (!level.isOutsideBuildHeight(next)) {
                        noteUnloaded(flags); // daar kan een kabel verder lopen: we weten het niet
                    }
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (closedPort(state, direction.getOpposite())) {
                    continue; // de poort aan deze kant staat uit: misschien komen we er via een andere kant wel in
                }
                followCables(level, at, next, state, seen, todo, parents);
                Node block = new Node(next.immutable(), null);
                if (!seen.add(block)) {
                    continue;
                }
                remember(parents, block, at);
                if (state.getBlock() instanceof SecurityCoreBlock) {
                    return next;
                }
                if (conducts(level, next, state)) {
                    todo.add(block);
                }
            }
            // Hoofdstuk 112: door de lucht, van een router naar zijn apparaten of van een apparaat naar zijn router.
            for (BlockPos next : wireless(level, at)) {
                Node block = new Node(next.immutable(), null);
                if (!level.isLoaded(next)) {
                    noteUnloaded(flags); // zijn router (of een apparaat van deze router) staat in een stuk dat niet geladen is
                    continue;
                }
                if (!seen.add(block)) {
                    continue;
                }
                remember(parents, block, at);
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof SecurityCoreBlock) {
                    return next;
                }
                if (conducts(level, next, state)) {
                    todo.add(block);
                }
            }
        }
        if (flags != null && !todo.isEmpty()) {
            flags[0] = true; // gestopt met nog werk over: het net is groter dan we vanaf hier zoeken
        }
        return null;
    }

    /** De zoektocht kwam langs een stuk wereld dat niet geladen is (zie {@link #searchComplete}). */
    private static void noteUnloaded(@Nullable boolean[] flags) {
        if (flags != null && flags.length > 1) {
            flags[1] = true;
        }
    }

    /**
     * Zoekt de apparaten van een kern: vanaf de kern langs alle kabels. Een apparaat dat ook bij een andere kern kan (twee
     * netten die elkaar raken), houden we alleen als deze kern de dichtstbijzijnde is. Een switch telt zelf ook als
     * apparaat (hij staat in Devices) zodra je hem via een poort die aan staat bereikt, ook zonder stroom.
     *
     * Hoofdstuk 94: dit mag ver gaan ({@link #MAX_NODES_FROM_CORE}). Raakt dit net geen andere kern, dan hoort elk gevonden
     * apparaat bij deze kern, en onthouden we dat meteen (dan hoeft een apparaat aan een lange kabel niet zelf de hele weg
     * te zoeken). En we onthouden in de kern in welke stukken wereld zijn net ligt, en waar het verder gaat in een stuk dat
     * niet geladen is: dat laadt NetworkKeeper zolang iemand het net gebruikt.
     */
    private static Set<BlockPos> searchDevices(Level level, BlockPos core) {
        Set<BlockPos> devices = new LinkedHashSet<>();
        Set<Node> seen = new HashSet<>();
        ArrayDeque<Node> todo = new ArrayDeque<>();
        it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet chunks = new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet();
        boolean otherCore = false;
        BlockPos self = core.immutable();
        Node start = new Node(self, null);
        seen.add(start);
        todo.add(start);
        int followed = 0;
        SEARCHING.add(self);
        try {
            while (!todo.isEmpty() && followed++ < MAX_NODES_FROM_CORE) {
                Node at = todo.poll();
                addChunk(chunks, at.pos());
                BlockState atState = level.getBlockState(at.pos());
                for (Direction direction : Direction.values()) {
                    if (closedPort(atState, direction)) {
                        continue;
                    }
                    BlockPos next = at.pos().relative(direction);
                    if (!level.isLoaded(next)) {
                        addChunk(chunks, next); // hier gaat het net misschien verder, in een stuk wereld dat niet geladen is
                        continue;
                    }
                    BlockState state = level.getBlockState(next);
                    if (closedPort(state, direction.getOpposite())) {
                        continue;
                    }
                    if (state.getBlock() instanceof SecurityCoreBlock) {
                        // Een andere kern: daar gaan we niet doorheen. (Onszelf komen we ook tegen, vanaf de kabel ernaast.)
                        otherCore |= !next.equals(self);
                        continue;
                    }
                    followCables(level, at, next, state, seen, todo, null);
                    Node block = new Node(next.immutable(), null);
                    if (!seen.add(block)) {
                        continue;
                    }
                    if (state.getBlock() instanceof NetworkConnectable) {
                        devices.add(next.immutable());
                        addChunk(chunks, next);
                    }
                    if (conducts(level, next, state)) {
                        todo.add(block);
                    }
                }
                // Hoofdstuk 112: door de lucht, van een router naar zijn apparaten (en van de kern naar zijn router).
                for (BlockPos next : wireless(level, at)) {
                    if (!level.isLoaded(next)) {
                        addChunk(chunks, next); // de router of het apparaat staat in een stuk wereld dat niet geladen is
                        continue;
                    }
                    BlockState state = level.getBlockState(next);
                    if (state.getBlock() instanceof SecurityCoreBlock) {
                        otherCore |= !next.equals(self);
                        continue;
                    }
                    Node block = new Node(next.immutable(), null);
                    if (!seen.add(block)) {
                        continue;
                    }
                    if (state.getBlock() instanceof NetworkConnectable) {
                        devices.add(next.immutable());
                        addChunk(chunks, next);
                    }
                    if (conducts(level, next, state)) {
                        todo.add(block);
                    }
                }
            }
            if (otherCore || !todo.isEmpty()) {
                // Twee netten raken elkaar (of dit net is te groot om helemaal te doorzoeken): elk apparaat kijkt zelf welke
                // kern het dichtstbij is.
                devices.removeIf(device -> !self.equals(corePosOf(level, device)));
            } else {
                Map<BlockPos, Link> perLevel = CORE_OF.computeIfAbsent(level.dimension(), key -> new HashMap<>());
                long now = level.getGameTime();
                for (BlockPos device : devices) {
                    perLevel.put(device, new Link(version, now, self, true));
                }
            }
        } finally {
            SEARCHING.remove(self);
        }
        if (level.getBlockEntity(self) instanceof SecurityCoreBlockEntity entity) {
            entity.rememberNetwork(chunks.toLongArray());
        }
        return Set.copyOf(devices);
    }

    // ================================================================ Het hele net (hoofdstuk 112)

    /**
     * Alle netwerkblokken (zie NetworkConnectable) in hetzelfde net als deze plek: alles wat je vanaf hier bereikt langs
     * netwerkkabels, blokken die het netwerk doorgeven, routers en kernen. Anders dan {@link #devicesOf} hoeft er geen kern
     * in dat net te zitten: een Storage Interface en zijn Chest Sensors aan één Router zijn ook een net. Zit er wel een
     * kern in, dan geeft die het net door (wat aan de ene kant van de desktop hangt, zit in hetzelfde net als wat aan de
     * andere kant hangt). De plek zelf zit erbij als daar een netwerkblok staat. Hooguit LIST_TICKS oud.
     */
    public static Set<BlockPos> networkOf(Level level, BlockPos from) {
        Map<BlockPos, Reach> perLevel = NETWORK_OF.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        Reach reach = perLevel.get(from);
        long now = level.getGameTime();
        if (reach == null || reach.version() != version || now - reach.gameTime() >= LIST_TICKS || now < reach.gameTime()) {
            if (perLevel.size() > 4096) {
                perLevel.clear(); // plekken waar al lang niemand meer naar vroeg
            }
            reach = new Reach(version, now, searchNetwork(level, from));
            perLevel.put(from.immutable(), reach);
        }
        return reach.devices();
    }

    /** Zoekt het hele net van deze plek (zie {@link #networkOf}). Een plek in een stuk wereld dat niet geladen is, heeft geen net. */
    private static Set<BlockPos> searchNetwork(Level level, BlockPos from) {
        if (!level.isLoaded(from)) {
            return Set.of(); // hiervoor laden we niets (level.getBlockState zou dat stuk wereld anders laden)
        }
        Set<BlockPos> found = new LinkedHashSet<>();
        Set<Node> seen = new HashSet<>();
        ArrayDeque<Node> todo = new ArrayDeque<>();
        Node start = new Node(from.immutable(), null);
        seen.add(start);
        todo.add(start);
        if (level.getBlockState(from).getBlock() instanceof NetworkConnectable) {
            found.add(from.immutable());
        }
        int followed = 0;
        while (!todo.isEmpty() && followed++ < MAX_NODES_FROM_CORE) {
            Node at = todo.poll();
            BlockState atState = level.getBlockState(at.pos());
            for (Direction direction : Direction.values()) {
                if (closedPort(atState, direction)) {
                    continue;
                }
                BlockPos next = at.pos().relative(direction);
                if (!level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (closedPort(state, direction.getOpposite())) {
                    continue;
                }
                followCables(level, at, next, state, seen, todo, null);
                visitNetwork(level, next, state, seen, todo, found);
            }
            for (BlockPos next : wireless(level, at)) {
                if (level.isLoaded(next)) {
                    visitNetwork(level, next, level.getBlockState(next), seen, todo, found);
                }
            }
        }
        return java.util.Collections.unmodifiableSet(found);
    }

    /** Eén blok in de zoektocht naar het hele net: een netwerkblok telt mee, en een kern of een doorgevend blok gaat verder. */
    private static void visitNetwork(Level level, BlockPos next, BlockState state, Set<Node> seen, ArrayDeque<Node> todo,
                                     Set<BlockPos> found) {
        Node block = new Node(next.immutable(), null);
        if (!seen.add(block)) {
            return;
        }
        if (state.getBlock() instanceof NetworkConnectable) {
            found.add(next.immutable());
        }
        if (state.getBlock() instanceof SecurityCoreBlock || conducts(level, next, state)) {
            todo.add(block);
        }
    }

    /**
     * De draadloze buren van deze plek in de zoektocht (hoofdstuk 112): van een router alles waar hij mee verbonden is,
     * van een apparaat zijn router. Alleen vanaf een blok (niet vanaf een stukje kabel), en alleen op de server.
     */
    private static List<BlockPos> wireless(Level level, Node at) {
        return at.kind() == null && level instanceof ServerLevel server ? Routers.partners(server, at.pos()) : List.of();
    }

    /** Onthoudt het stuk wereld (chunk) van deze plek, tot {@link #MAX_NETWORK_CHUNKS}. */
    private static void addChunk(it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet chunks, BlockPos pos) {
        if (chunks.size() < MAX_NETWORK_CHUNKS) {
            chunks.add(net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        }
    }

    /**
     * Volgt de kabels op {@code next} vanaf {@code at} (hoofdstuk 85): vanaf een stukje kabel alleen dezelfde soort, vanaf
     * een blok elke netwerksoort. Een open kabel in een EMP-puls geeft even niets door; een kabel door een switch heen ook
     * niet (de switch beslist zelf, met zijn poorten en zijn stroom).
     */
    private static void followCables(Level level, Node at, BlockPos next, BlockState state, Set<Node> seen,
                                     ArrayDeque<Node> todo, @Nullable Map<Node, Node> parents) {
        if (state.getBlock() instanceof NetworkSwitchBlock || com.senne.imttech.weapons.Emp.cutsCable(level, next)) {
            return;
        }
        for (CableKind kind : NETWORK_KINDS) {
            if (at.kind() != null && at.kind() != kind) {
                continue;
            }
            Node cable = new Node(next.immutable(), kind);
            if (!seen.contains(cable) && Cables.has(kind, level, next)) {
                seen.add(cable);
                remember(parents, cable, at);
                todo.add(cable);
            }
        }
    }

    /** Onthoudt (als we een weg zoeken) dat we {@code next} vanaf {@code from} bereikten. */
    private static void remember(@Nullable Map<Node, Node> parents, Node next, Node from) {
        if (parents != null) {
            parents.putIfAbsent(next, from);
        }
    }

    /**
     * De weg langs de kabels van dit apparaat naar zijn kern (de dichtstbijzijnde langs de kabel): elke plek, van het
     * apparaat tot en met de kern. Leeg als het nergens aan hangt. Voor de signalen die je over de kabel ziet lopen (zie
     * CableTraffic); dit wordt niet onthouden.
     */
    public static List<BlockPos> path(Level level, BlockPos device) {
        Map<Node, Node> parents = new HashMap<>();
        BlockPos core = searchCore(level, device, parents, null);
        if (core == null) {
            return List.of();
        }
        List<BlockPos> path = new ArrayList<>();
        for (Node at = new Node(core, null); at != null; at = parents.get(at)) {
            // Een blok op een kabel is twee plekken in de zoektocht maar één plek in de wereld: die maar één keer.
            if (path.isEmpty() || !path.getLast().equals(at.pos())) {
                path.add(at.pos());
            }
        }
        java.util.Collections.reverse(path);
        return path;
    }

    /** Is dit een Network Switch waarvan de poort aan deze kant uit staat? */
    private static boolean closedPort(BlockState state, Direction side) {
        return state.getBlock() instanceof NetworkSwitchBlock && !NetworkSwitchBlock.isOn(state, side);
    }

    /**
     * Geeft dit blok het netwerk door aan wie ertegen staat? Een serverrack, een laser- of brugzender, een Camera Monitor (19
     * oktober: een heel scherm aan één kabel), een switch met stroom (zie NetworkSwitchBlock.passes; de poorten kijkt de
     * zoektocht zelf na), of (hoofdstuk 112) een Router met stroom. De kabels zelf volgt {@link #followCables}.
     */
    private static boolean conducts(Level level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.SERVER_RACK.get()) || state.is(ModBlocks.LASER_EMITTER.get())
                || state.is(ModBlocks.LASER_BRIDGE.get()) || state.is(ModBlocks.CAMERA_MONITOR.get())) {
            return true;
        }
        if (state.getBlock() instanceof RouterBlock) {
            return RouterBlock.passes(level, pos, state); // hoofdstuk 112: een router met stroom
        }
        return state.getBlock() instanceof NetworkSwitchBlock && NetworkSwitchBlock.passes(level, pos, state);
    }

    /** Bij het afsluiten van de wereld: alles vergeten. */
    public static void clear() {
        CORE_OF.clear();
        DEVICES_OF.clear();
        NETWORK_OF.clear();
        Routers.clear();
    }
}
