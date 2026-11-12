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
 * </ul>
 * Het zoeken kost wat werk, dus we onthouden het resultaat. Komt er ergens een kabel bij of gaat er een weg (of iets
 * anders dat het net verandert), dan verhogen we {@link #changed()} en zoeken we de volgende keer opnieuw.
 */
public final class LanGrid {
    private LanGrid() {}

    /** Zoveel stukjes kabel (en racks en zenders) volgen we hooguit vanaf één plek (tegen eindeloos lange netten). */
    public static final int MAX_NODES = 512;
    /** Na zoveel ticks zoeken we sowieso opnieuw welke kern een apparaat heeft (bijvoorbeeld als er een stuk wereld laadde). */
    private static final int CORE_TICKS = 200;
    /** Zo lang (ticks) onthouden we welke apparaten aan een kern hangen (net zo lang als SecurityDevices). */
    private static final int LIST_TICKS = 40;

    /** Telt elke verandering aan een net; staat hij anders dan bij het zoeken, dan zoeken we opnieuw. */
    private static int version;

    /** Welke kern een apparaat had bij het zoeken (null = geen). */
    private record Link(int version, long gameTime, @Nullable BlockPos core) {}

    /** Welke apparaten aan een kern hingen bij het zoeken. */
    private record Reach(int version, long gameTime, Set<BlockPos> devices) {}

    private static final Map<ResourceKey<Level>, Map<BlockPos, Link>> CORE_OF = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, Reach>> DEVICES_OF = new HashMap<>();

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

    /** Waar de kern van dit apparaat staat (uit het geheugen als dat nog klopt), of null. */
    private static @Nullable BlockPos corePosOf(Level level, BlockPos device) {
        Map<BlockPos, Link> perLevel = CORE_OF.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        Link link = perLevel.get(device);
        long now = level.getGameTime();
        if (link == null || link.version() != version || now - link.gameTime() > CORE_TICKS || now < link.gameTime()) {
            link = new Link(version, now, searchCore(level, device, null));
            perLevel.put(device.immutable(), link);
        }
        return link.core();
    }

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
    private static @Nullable BlockPos searchCore(Level level, BlockPos device, @Nullable Map<Node, Node> parents) {
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
        }
        return null;
    }

    /**
     * Zoekt de apparaten van een kern: vanaf de kern langs alle kabels. Een apparaat dat ook bij een andere kern kan (twee
     * netten die elkaar raken), houden we alleen als deze kern de dichtstbijzijnde is. Een switch telt zelf ook als
     * apparaat (hij staat in Devices) zodra je hem via een poort die aan staat bereikt, ook zonder stroom.
     */
    private static Set<BlockPos> searchDevices(Level level, BlockPos core) {
        Set<BlockPos> devices = new LinkedHashSet<>();
        Set<Node> seen = new HashSet<>();
        ArrayDeque<Node> todo = new ArrayDeque<>();
        Node start = new Node(core.immutable(), null);
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
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (closedPort(state, direction.getOpposite())) {
                    continue;
                }
                if (state.getBlock() instanceof SecurityCoreBlock) {
                    continue; // een andere kern: daar gaan we niet doorheen
                }
                followCables(level, at, next, state, seen, todo, null);
                Node block = new Node(next.immutable(), null);
                if (!seen.add(block)) {
                    continue;
                }
                if (state.getBlock() instanceof NetworkConnectable) {
                    devices.add(next.immutable());
                }
                if (conducts(level, next, state)) {
                    todo.add(block);
                }
            }
        }
        devices.removeIf(device -> !core.equals(corePosOf(level, device)));
        return Set.copyOf(devices);
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
        BlockPos core = searchCore(level, device, parents);
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
     * oktober: een heel scherm aan één kabel), of een switch met stroom (zie NetworkSwitchBlock.passes; de poorten kijkt de
     * zoektocht zelf na). De kabels zelf volgt {@link #followCables}.
     */
    private static boolean conducts(Level level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.SERVER_RACK.get()) || state.is(ModBlocks.LASER_EMITTER.get())
                || state.is(ModBlocks.LASER_BRIDGE.get()) || state.is(ModBlocks.CAMERA_MONITOR.get())) {
            return true;
        }
        return state.getBlock() instanceof NetworkSwitchBlock && NetworkSwitchBlock.passes(level, pos, state);
    }

    /** Bij het afsluiten van de wereld: alles vergeten. */
    public static void clear() {
        CORE_OF.clear();
        DEVICES_OF.clear();
    }
}
