package com.senne.imttech.energy;

import com.senne.imttech.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Het stroomnet van I.M.T Technology: zonnepanelen en warmtegeneratoren leveren, en alles wat via
 * stroomkabels (of er direct tegenaan) met ze verbonden is, kan die stroom gebruiken. Kabels zijn
 * getekende lijnen (zie PowerCables): een apparaat hangt eraan als er een kabel naast ligt, of
 * als het er bovenop gezet is.
 *
 * Hoe het werkt:
 * - Een apparaat dat stroom wil, vraagt {@link #draw}. Wij zoeken vanaf dat apparaat alle
 *   leveranciers op: we kijken naar zijn zes buren, en volgen elke kabel die we tegenkomen
 *   (net als water dat door buizen loopt). Drone-docks geven de stroom ook door, zodat een rij
 *   docks in een hangar aan één kabel genoeg heeft; laserzenders ook, zodat een laserpoort van
 *   meerdere zenders aan één kabel genoeg heeft; en serverracks, zodat een kast vol racks aan één
 *   kabel genoeg heeft.
 * - We halen de stroom eerst bij de leverancier die het meest heeft, zodat ze ongeveer even vol
 *   blijven.
 * - Wat een apparaat gebruikt mag een kommagetal zijn (0.05 per tick): dat betaalt het met
 *   {@link #pay}, dat per apparaat onthoudt wat nog geen hele stroom was.
 * - Het zoeken kost wat werk, dus we onthouden het resultaat per apparaat. Komt er ergens een
 *   kabel of leverancier bij of gaat er een weg, dan verhogen we {@link #changed()} en zoeken we
 *   de volgende keer opnieuw.
 */
public final class PowerGrid {
    private PowerGrid() {}

    /** Zoveel kabels volgen we hooguit vanaf één apparaat (tegen eindeloos lange netten). */
    public static final int MAX_CABLES = 512;
    /** Na zo veel ticks zoeken we sowieso opnieuw (bijvoorbeeld als er een stuk wereld laadde). */
    private static final int CACHE_TICKS = 200;

    /** Telt elke verandering aan een net; staat hij anders dan bij het zoeken, dan zoeken we opnieuw. */
    private static int version;

    private record Found(int version, long gameTime, List<BlockPos> sources) {}

    private static final Map<ResourceKey<Level>, Map<BlockPos, Found>> CACHE = new HashMap<>();

    /** Per apparaat: wat het nog schuldig is, minder dan 1 (zie {@link #pay}). */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Double>> OWED = new HashMap<>();

    /**
     * Er kwam een kabel of leverancier bij, of er ging er een weg: alle netten opnieuw zoeken. Ook het netwerk van de
     * beveiligingskernen (hoofdstuk 38, zie LanGrid): wat het stroomnet verandert (een kabel, een rack, een laserzender,
     * een EMP-puls), verandert het netwerk vaak ook.
     */
    public static void changed() {
        version++;
        com.senne.imttech.lan.LanGrid.changed();
    }

    /** Alle leveranciers die met dit apparaat verbonden zijn. */
    public static List<EnergySource> sources(Level level, BlockPos device) {
        List<EnergySource> result = new ArrayList<>();
        for (BlockPos pos : sourcePositions(level, device)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof EnergySource source) {
                result.add(source);
            } else {
                // Hoofdstuk 46: een leverancier van een andere mod (een energiekubus, een accumulator) op ons net.
                EnergySource foreign = FeBridge.foreignSource(level, pos, null);
                if (foreign != null) {
                    result.add(foreign);
                }
            }
        }
        return result;
    }

    /** Hoeveel stroom er in totaal in de leveranciers van dit apparaat zit (en in zijn eigen FE-buffertje). */
    public static int available(Level level, BlockPos device) {
        long total = FeBridge.buffered(level, device);
        for (EnergySource source : sources(level, device)) {
            total += source.energy();
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * Haalt maximaal {@code amount} stroom uit het net van dit apparaat, bij de voortste
     * leverancier eerst. Geeft terug hoeveel het werd (kan minder zijn, of 0). Wat een vreemde kabel in het buffertje
     * van het apparaat duwde (hoofdstuk 46), gaat voor.
     */
    public static int draw(Level level, BlockPos device, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int taken = FeBridge.drainBuffer(level, device, amount);
        if (taken >= amount) {
            return taken;
        }
        List<EnergySource> sources = sources(level, device);
        sources.sort(Comparator.comparingInt(EnergySource::energy).reversed());
        for (EnergySource source : sources) {
            taken += source.drain(amount - taken);
            if (taken >= amount) {
                break;
            }
        }
        if (taken < amount) {
            missed(sources, amount - taken);
        }
        // Hoofdstuk 38: de stroom liep echt; spelers in de buurt zien gele streepjes over de kabel (zie lan/CableTraffic).
        if (taken > 0 && level instanceof net.minecraft.server.level.ServerLevel server) {
            com.senne.imttech.lan.CableTraffic.power(server, device);
            note(level, device, taken);
        }
        return taken;
    }

    // ================================================================ Wie gebruikt er stroom (hoofdstuk 47)

    /** Een apparaat dat de laatste tijd stroom haalde: waar, en gemiddeld hoeveel per tick (over een venster van 5 s). */
    public record DeviceUse(BlockPos device, float perTick) {}

    /** Per apparaat: {begin van het venster, wat het in dit venster haalde, het gemiddelde van het vorige venster, laatste tick}. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, double[]>> USES = new HashMap<>();
    private static final int USE_WINDOW = 100;

    private static void note(Level level, BlockPos device, int taken) {
        long now = level.getGameTime();
        Map<BlockPos, double[]> perLevel = USES.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        double[] use = perLevel.computeIfAbsent(device.immutable(), key -> new double[] {now, 0, 0, now});
        if (now - use[0] >= USE_WINDOW) {
            use[2] = use[1] / Math.max(1, now - use[0]);
            use[0] = now;
            use[1] = 0;
        }
        use[1] += taken;
        use[3] = now;
    }

    /** De apparaten die de laatste 10 seconden stroom haalden in deze wereld, met hun gemiddelde per tick. */
    public static List<DeviceUse> recentDevices(Level level) {
        List<DeviceUse> result = new ArrayList<>();
        Map<BlockPos, double[]> perLevel = USES.get(level.dimension());
        if (perLevel == null) {
            return result;
        }
        long now = level.getGameTime();
        perLevel.entrySet().removeIf(entry -> now - entry.getValue()[3] > 2L * USE_WINDOW);
        for (Map.Entry<BlockPos, double[]> entry : perLevel.entrySet()) {
            double[] use = entry.getValue();
            double perTick = use[2] > 0 ? use[2] : use[1] / Math.max(1, now - use[0]);
            result.add(new DeviceUse(entry.getKey(), (float) perTick));
        }
        return result;
    }

    /** Waar de leveranciers van dit apparaat staan (voor de batterij: hangt dit apparaat aan mij?). */
    public static List<BlockPos> sourcePositionsOf(Level level, BlockPos device) {
        return sourcePositions(level, device);
    }

    /**
     * Dit apparaat wilde zoveel stroom maar kreeg het niet (het net was te leeg). Dat vertellen we aan
     * zijn leveranciers, zodat het scherm van een generator kan zeggen dat er te weinig stroom is.
     */
    public static void missed(Level level, BlockPos device, double amount) {
        if (amount > 0) {
            missed(sources(level, device), amount);
        }
    }

    private static void missed(List<EnergySource> sources, double amount) {
        for (EnergySource source : sources) {
            source.missed(amount / sources.size());
        }
    }

    /**
     * Betaalt {@code amount} stroom uit het net van dit apparaat. Het mag een kommagetal zijn: wat nog
     * geen hele stroom is, onthouden we per apparaat en tellen we de volgende keer op. 0.25 per tick
     * wordt zo elke vierde tick 1 (net als {@link EnergyCarry}). Hele getallen gaan zoals altijd.
     *
     * Zit er minder in het net dan het bedrag (naar boven afgerond, dus bij 0.05 minstens 1), dan
     * betaalt het niets en geeft het false: het apparaat heeft dan geen stroom.
     */
    public static boolean pay(Level level, BlockPos device, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (available(level, device) < Math.max(1.0, Math.ceil(amount))) {
            missed(level, device, amount);
            return false;
        }
        Map<BlockPos, Double> perLevel = OWED.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        double owed = perLevel.getOrDefault(device, 0.0) + amount;
        int whole = (int) Math.min(Integer.MAX_VALUE, owed);
        if (whole > 0) {
            draw(level, device, whole);
        }
        owed -= whole;
        if (owed > 1.0E-9) {
            perLevel.put(device.immutable(), owed);
        } else {
            perLevel.remove(device);
        }
        return true;
    }

    /** Waar de leveranciers van dit apparaat staan (uit het geheugen als dat nog klopt). */
    private static List<BlockPos> sourcePositions(Level level, BlockPos device) {
        Map<BlockPos, Found> perLevel = CACHE.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        Found found = perLevel.get(device);
        long now = level.getGameTime();
        if (found == null || found.version != version || now - found.gameTime > CACHE_TICKS) {
            found = new Found(version, now, search(level, device, null));
            perLevel.put(device.immutable(), found);
        }
        return found.sources;
    }

    /**
     * Een plek in de zoektocht (hoofdstuk 85): een stukje kabel van één soort ({@code kind} is POWER of POE), of een blok
     * zelf ({@code kind} is null): het apparaat dat stroom zoekt, of een blok dat stroom doorgeeft (een drone-dock, een
     * laserzender, de kern...). Op één plek kunnen een stroomkabel en een PoE-kabel allebei liggen: dat zijn dan twee
     * aparte plekken in de zoektocht, want ze zitten niet aan elkaar vast.
     */
    private record Node(BlockPos pos, @Nullable CableKind kind) {}

    /** De kabels die stroom doorgeven: de stroomkabel en de PoE-kabel. */
    private static final CableKind[] POWER_KINDS = {CableKind.POWER, CableKind.POE};
    private static final CableKind[] NO_KINDS = {};

    /**
     * Zoekt de leveranciers: eerst de zes buren van het apparaat, en dan langs elke kabel verder.
     * (Door een leverancier of een ander apparaat heen lopen we niet: alleen kabels, drone-docks,
     * laserzenders, brugzenders (hoofdstuk 34), serverracks, Camera Monitors en lichtschakelaars die aan staan geleiden.
     * Staat een apparaat op een kabel, dan loopt de kabel eronder door. Een open kabel in een EMP-puls geleidt even
     * niet, zie Emp.cutsCable.)
     *
     * Hoofdstuk 38: een PoE-kabel geeft ook stroom door (stroom en netwerk in één kabel), en de Security Core (de
     * desktop) geeft zijn stroom door aan wat erop aangesloten is. Jouw wens: "PoE werkt als je een power cable aan de
     * desktop hebt hangen, en dan vertrekt die van de desktop": één stroomkabel op de kern voedt zo al zijn PoE-kabels.
     *
     * Hoofdstuk 85 (jouw wens: "zorg ook dat power cable, netwerk en poe niet connecten aan elkaar", en "iets dat niet in
     * een netwerk kan, kan enkel een powercable"):
     * <ul>
     *   <li>een stroomkabel loopt alleen verder in een stroomkabel, een PoE-kabel alleen in een PoE-kabel. Liggen ze tegen
     *       elkaar aan, dan gaat er niets over: van de ene soort naar de andere kom je alleen via een blok (de kern);</li>
     *   <li>een PoE-kabel geeft alleen stroom aan een blok dat in een netwerk kan ({@link #takesPoe}): de kern, een lezer,
     *       een camera, een rack... Een Crusher, een generator of een stroomkast sluit alleen op een stroomkabel aan. Is het
     *       apparaat dat stroom zoekt zelf geen netwerkblok, dan slaan we elke PoE-kabel over (ook verderop, achter een
     *       rack): zo krijgt het nooit stroom via PoE.</li>
     * </ul>
     */
    private static List<BlockPos> search(Level level, BlockPos device, @Nullable Map<Node, Node> parents) {
        List<BlockPos> sources = new ArrayList<>();
        Set<Node> seen = new HashSet<>();
        ArrayDeque<Node> todo = new ArrayDeque<>();
        Node start = new Node(device.immutable(), null);
        seen.add(start);
        boolean poe = takesPoe(level.getBlockState(device));
        // Staat het apparaat zelf op een kabel (erbovenop gebouwd)? Dan hangt het daar ook aan (aan een PoE-kabel alleen als
        // het een netwerkblok is).
        for (CableKind kind : POWER_KINDS) {
            if ((kind != CableKind.POE || poe) && Cables.has(kind, level, device)
                    && !com.senne.imttech.weapons.Emp.cutsCable(level, device)) {
                Node onIt = new Node(device.immutable(), kind);
                seen.add(onIt);
                remember(parents, onIt, start);
                todo.add(onIt);
            }
        }
        look(level, start, poe, sources, seen, todo, true, parents);
        int followed = 0;
        while (!todo.isEmpty() && followed < MAX_CABLES) {
            look(level, todo.poll(), poe, sources, seen, todo, false, parents);
            followed++;
        }
        return List.copyOf(sources);
    }

    /**
     * Mag dit blok stroom uit een PoE-kabel halen (hoofdstuk 85)? Alleen een blok dat in een netwerk kan: de Security Core en
     * alles waar een netwerkkabel op aansluit (zie NetworkConnectable). Zo'n blok kan een stroomkabel, een netwerkkabel en
     * een PoE-kabel hebben; elk ander blok alleen een stroomkabel.
     */
    public static boolean takesPoe(BlockState state) {
        return state.getBlock() instanceof com.senne.imttech.lan.NetworkConnectable;
    }

    /**
     * De weg langs de kabels van de dichtstbijzijnde leverancier van dit apparaat naar het apparaat: elke plek, van de
     * leverancier tot en met het apparaat. Leeg als het geen leverancier heeft. Voor de gele streepjes van de stroom die je
     * over een kabel ziet lopen (hoofdstuk 38, zie lan/CableTraffic); dit wordt niet onthouden.
     */
    public static List<BlockPos> path(Level level, BlockPos device) {
        Map<Node, Node> parents = new HashMap<>();
        List<BlockPos> sources = search(level, device, parents);
        if (sources.isEmpty()) {
            return List.of();
        }
        List<BlockPos> path = new ArrayList<>();
        for (Node at = new Node(sources.getFirst(), null); at != null; at = parents.get(at)) {
            // Een apparaat op een kabel is twee plekken in de zoektocht maar één plek in de wereld: die maar één keer.
            if (path.isEmpty() || !path.getLast().equals(at.pos())) {
                path.add(at.pos());
            }
        }
        return path;
    }

    /**
     * Bekijkt de zes buren van {@code from}: een leverancier onthouden we, een kabel of een blok dat stroom doorgeeft volgen
     * we. {@code start}: dit is het apparaat zelf (dat zoekt zijn stroom aan alle kanten, ook een switch). {@code poe}: mag
     * de zoektocht over PoE-kabels (is het apparaat een netwerkblok, zie {@link #takesPoe})?
     */
    private static void look(Level level, Node from, boolean poe, List<BlockPos> sources, Set<Node> seen,
                             ArrayDeque<Node> todo, boolean start, @Nullable Map<Node, Node> parents) {
        BlockPos at = from.pos();
        BlockState fromState = level.getBlockState(at);
        boolean fromCabinet = fromState.getBlock() instanceof PowerCabinetBlock;
        // Hoofdstuk 38: door een Network Switch gaat alleen PoE-stroom, van een PoE-kabel naar de PoE-kabels aan de andere
        // poorten die aan staan (niet naar een gewone stroomkabel of een apparaat ertegen).
        boolean fromSwitch = !start && from.kind() == null
                && fromState.getBlock() instanceof com.senne.imttech.lan.NetworkSwitchBlock;
        // Hoofdstuk 85: van een stukje PoE-kabel gaat de stroom alleen naar PoE-kabels en netwerkblokken.
        boolean fromPoe = from.kind() == CableKind.POE;
        for (Direction direction : Direction.values()) {
            // Uit een stroomkast gaat de stroom alleen naar de kanten waarvan de schakelaar aan staat.
            if (fromCabinet && !PowerCabinetBlock.isOn(fromState, direction)) {
                continue;
            }
            if (fromSwitch && !com.senne.imttech.lan.NetworkSwitchBlock.isOn(fromState, direction)) {
                continue;
            }
            BlockPos next = at.relative(direction);
            if (!level.isLoaded(next)) {
                continue;
            }
            BlockState state = level.getBlockState(next);
            // Een switch, een lichtschakelaar en een stroomkast beslissen zelf wat erdoor gaat (hieronder), ook als er een
            // getekende kabel door loopt.
            boolean gate = state.getBlock() instanceof com.senne.imttech.lan.NetworkSwitchBlock
                    || state.getBlock() instanceof com.senne.imttech.light.LightSwitchBlock
                    || state.getBlock() instanceof PowerCabinetBlock;
            // Eerst de kabels op de buurplek: alleen dezelfde soort als waar we op lopen (hoofdstuk 85). Komen we uit een
            // blok, dan elke soort waar dat blok op aansluit (een PoE-kabel alleen bij een netwerkblok, en uit een switch
            // alleen een PoE-kabel).
            for (CableKind kind : gate ? NO_KINDS : POWER_KINDS) {
                if (kind == CableKind.POE && !poe) {
                    continue;
                }
                boolean joins = from.kind() != null ? from.kind() == kind
                        : kind == CableKind.POWER ? !fromSwitch : takesPoe(fromState);
                Node cable = new Node(next.immutable(), kind);
                if (joins && !seen.contains(cable) && Cables.has(kind, level, next)
                        && !com.senne.imttech.weapons.Emp.cutsCable(level, next)) {
                    seen.add(cable);
                    remember(parents, cable, from);
                    todo.add(cable);
                }
            }
            if (fromSwitch) {
                continue; // uit een switch alleen in een PoE-kabel (hierboven), niet in een blok ertegen
            }
            Node block = new Node(next.immutable(), null);
            if (seen.contains(block)) {
                continue;
            }
            // In een switch kom je alleen vanaf een PoE-kabel, door een poort die aan staat.
            if (state.getBlock() instanceof com.senne.imttech.lan.NetworkSwitchBlock) {
                if (fromPoe && com.senne.imttech.lan.NetworkSwitchBlock.isOn(state, direction.getOpposite())) {
                    seen.add(block);
                    remember(parents, block, from);
                    todo.add(block);
                }
                continue;
            }
            // Hoofdstuk 85: een PoE-kabel sluit alleen op netwerkblokken aan. Een generator, een lichtschakelaar, een
            // stroomkast of een drone-dock ernaast doet niets.
            if (fromPoe && !takesPoe(state)) {
                continue;
            }
            // Een lichtschakelaar is een stukje kabel dat aan of uit kan: aan = de stroom gaat erdoor,
            // uit = hier houdt het op (ook als er een getekende kabel door de schakelaar loopt).
            if (state.getBlock() instanceof com.senne.imttech.light.LightSwitchBlock) {
                seen.add(block);
                remember(parents, block, from);
                if (com.senne.imttech.light.LightSwitchBlock.isOn(state)) {
                    todo.add(block);
                }
                continue;
            }
            // In een stroomkast kom je alleen via een kant die aan staat.
            if (state.getBlock() instanceof PowerCabinetBlock) {
                if (PowerCabinetBlock.isOn(state, direction.getOpposite())) {
                    seen.add(block);
                    remember(parents, block, from);
                    todo.add(block);
                }
                continue;
            }
            // Een leverancier kan ook OP een kabel staan: dan is de plek allebei. Hoofdstuk 46: een leverancier van een
            // andere mod (via de energie-capability) telt ook.
            BlockEntity entity = level.getBlockEntity(next);
            if (entity instanceof EnergySource || entity != null && FeBridge.foreignSource(level, next, direction.getOpposite()) != null) {
                seen.add(block);
                remember(parents, block, from);
                sources.add(next.immutable());
            }
            // Blokken die de stroom doorgeven aan wie ertegen staat. De Security Core geeft stroom door aan wat erop
            // aangesloten is (zijn PoE-kabels, hoofdstuk 38).
            if (state.is(ModBlocks.DRONE_DOCK.get())
                    || state.is(ModBlocks.LASER_EMITTER.get()) || state.is(ModBlocks.LASER_BRIDGE.get())
                    || state.is(ModBlocks.SERVER_RACK.get()) || state.is(ModBlocks.SECURITY_CORE.get())
                    || state.is(ModBlocks.CAMERA_MONITOR.get())) {
                seen.add(block);
                remember(parents, block, from);
                todo.add(block);
            }
        }
    }

    /** Onthoudt (als we een weg zoeken) dat we {@code next} vanaf {@code from} bereikten. */
    private static void remember(@Nullable Map<Node, Node> parents, Node next, Node from) {
        if (parents != null) {
            parents.putIfAbsent(next, from);
        }
    }

    /** Bij het afsluiten van de wereld: alles vergeten. */
    public static void clear() {
        CACHE.clear();
        OWED.clear();
        USES.clear();
        FeBridge.clear();
    }
}
