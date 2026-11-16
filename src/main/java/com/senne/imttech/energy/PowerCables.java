package com.senne.imttech.energy;

import com.senne.imttech.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De stroomkabels, en wat alle kabels samen hebben: een route tekenen, en oude kabelblokken omzetten.
 *
 * Een kabel ligt op een blokplek (zie CableCells). Op die plek mag ook een blok staan: dan zit de
 * kabel in dat blok (in een muur, een vloer). Kabels op plekken die naast elkaar liggen, zitten
 * aan elkaar vast; een apparaat dat naast een kabel staat (of er bovenop is gezet), zit eraan
 * vast. De stroom zelf regelt PowerGrid.
 *
 * Sinds hoofdstuk 38 zijn er drie soorten kabels (stroom, netwerk en PoE, zie CableKind). Het leggen en opvragen voor
 * alle soorten staat in Cables; de methodes hieronder zijn die van de stroomkabel, zodat alles wat er al naar vroeg
 * gewoon blijft werken.
 */
public final class PowerCables {
    private PowerCables() {}

    /**
     * Zo ver mag het eind van je kabel van de plek waar je kijkt liggen om er in één keer een stuk tussen te tekenen
     * (blokken, langs de assen geteld). Verder weg: dan begint de rol een nieuwe lijn (zie route).
     */
    private static final int MAX_ROUTE = 24;
    /**
     * Hoofdstuk 132: zoveel stukjes kabel legt de rol hooguit in één keer bij om aan te sluiten. Een omweg die langer is
     * (de route volgt de oppervlakken, en kan dus om iets heen lopen), telt niet: dan begint de rol een nieuwe lijn.
     */
    private static final int MAX_CATCH_UP = MAX_ROUTE * 2;

    // ================================================================ De stroomkabel (zie Cables voor alle soorten)

    /** Ligt hier een stroomkabel? (Een chunk die niet geladen is: nee.) */
    public static boolean has(Level level, BlockPos pos) {
        return Cables.has(CableKind.POWER, level, pos);
    }

    /** Alle stroomkabels in deze chunk (leeg als er geen zijn). */
    public static CableCells in(LevelChunk chunk) {
        return Cables.in(CableKind.POWER, chunk);
    }

    /** Legt een stuk stroomkabel op deze plek. Geeft false als er al een lag. */
    public static boolean add(ServerLevel level, BlockPos pos) {
        return Cables.add(CableKind.POWER, level, pos);
    }

    /** Haalt het stuk stroomkabel op deze plek weg. Geeft false als er geen lag. */
    public static boolean remove(ServerLevel level, BlockPos pos) {
        return Cables.remove(CableKind.POWER, level, pos);
    }

    // ================================================================ Tekenen

    /**
     * De plekken van {@code from} naar {@code to} (allebei meegerekend), langs een route over
     * oppervlakken: over de vloer, tegen een muur op, onder een plafond. Waar geen oppervlak is,
     * mag hij ook door blokken heen (dan zit hij in de muur). We zoeken de kortste route met zo
     * weinig mogelijk bochten.
     *
     * <p>Hoofdstuk 132: geeft null als dat niet gaat: {@code to} ligt meer dan MAX_ROUTE blokken van {@code from}, de
     * zoektocht vindt geen route, of de route zou meer dan MAX_CATCH_UP nieuwe stukjes kosten. De kabelrol begint dan een
     * nieuwe lijn waar je kijkt. Vroeger trok hij in dat geval een rechte lijn per as (eerst x, dan z, dan y), dwars door
     * alles heen: keek je even in de lucht terwijl je doorliep (of werd je geteleporteerd), dan legde hij in één tick een
     * lange kabel door de muren, tot je hele rol op was.
     */
    public static @Nullable List<BlockPos> route(Level level, BlockPos from, BlockPos to) {
        if (from.equals(to)) {
            return List.of(to);
        }
        if (from.distManhattan(to) > MAX_ROUTE) {
            return null;
        }
        List<BlockPos> found = search(level, from, to);
        // Het eerste stukje (from) ligt er al: de rest kost elk een kabel.
        return found != null && found.size() - 1 <= MAX_CATCH_UP ? found : null;
    }

    /**
     * Zoekt stap voor stap (van dichtbij naar ver) een route. Een stap kost 1; door een blok heen
     * kost 3 (liever erlangs dan erdoor), een stap door de lucht zonder muur of vloer ernaast
     * kost 4 (een kabel hangt niet graag in de lucht), en een bocht kost 1 extra.
     */
    private static @Nullable List<BlockPos> search(Level level, BlockPos from, BlockPos to) {
        record Node(BlockPos pos, @Nullable Direction dir, int cost) {}
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Integer> best = new HashMap<>();
        java.util.PriorityQueue<Node> open = new java.util.PriorityQueue<>(
                java.util.Comparator.comparingInt(node -> node.cost() + node.pos().distManhattan(to)));
        open.add(new Node(from, null, 0));
        best.put(from, 0);
        int visited = 0;
        while (!open.isEmpty() && visited++ < 4000) {
            Node node = open.poll();
            if (node.pos().equals(to)) {
                List<BlockPos> path = new ArrayList<>();
                for (BlockPos at = to; at != null; at = cameFrom.get(at)) {
                    path.add(0, at);
                }
                return path;
            }
            if (node.cost() > best.getOrDefault(node.pos(), Integer.MAX_VALUE)) {
                continue;
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = node.pos().relative(dir);
                if (next.distManhattan(from) > MAX_ROUTE * 2 || !level.isLoaded(next)) {
                    continue;
                }
                int cost = node.cost() + stepCost(level, next) + (node.dir() != null && node.dir() != dir ? 1 : 0);
                if (cost < best.getOrDefault(next, Integer.MAX_VALUE)) {
                    best.put(next, cost);
                    cameFrom.put(next, node.pos());
                    open.add(new Node(next, dir, cost));
                }
            }
        }
        return null;
    }

    private static int stepCost(Level level, BlockPos pos) {
        if (isSolid(level, pos)) {
            return 3;
        }
        for (Direction dir : Direction.values()) {
            if (isSolid(level, pos.relative(dir))) {
                return 1;
            }
        }
        return 4;
    }

    /** Een volledig, stevig blok (waar een kabel "in" zit in plaats van "op" ligt). */
    public static boolean isSolid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isCollisionShapeFullBlock(level, pos);
    }

    // ================================================================ Oude kabelblokken omzetten

    /**
     * Zet een oud kabelblok om in een kabel (op dezelfde plek). Zat het blok ingesloten (in een
     * muur of plafond: minstens vier stevige buren), dan komt er het blok van de muur voor in de
     * plaats en zit de kabel in de muur; anders lucht, en ligt de kabel op de vloer of tegen de muur.
     */
    public static void convertLegacy(ServerLevel level, BlockPos pos) {
        BlockState old = level.getBlockState(pos);
        if (!old.is(ModBlocks.CABLE.get())) {
            return;
        }
        Map<BlockState, Integer> walls = new HashMap<>();
        boolean[] solidSide = new boolean[6];
        for (Direction dir : Direction.values()) {
            BlockPos next = pos.relative(dir);
            BlockState state = level.getBlockState(next);
            if (state.isCollisionShapeFullBlock(level, next) && !(state.getBlock() instanceof EntityBlock)
                    && !(state.getBlock() instanceof PowerConnectable) && !state.is(ModBlocks.CABLE.get())) {
                solidSide[dir.ordinal()] = true;
                walls.merge(state, 1, Integer::sum);
            }
        }
        boolean embedded = switch (old.getValue(PowerCableBlock.PLACEMENT)) {
            case EMBEDDED -> true;
            case OPEN -> false;
            // Raden (een oude wereld): zit er langs een van de drie richtingen aan BEIDE kanten een
            // stevig blok, dan zat hij in een muur, vloer of plafond. Een kabel op de vloer naast
            // een muur heeft dat niet (boven hem is lucht, naast hem aan één kant ook).
            case UNKNOWN -> (solidSide[Direction.UP.ordinal()] && solidSide[Direction.DOWN.ordinal()])
                    || (solidSide[Direction.NORTH.ordinal()] && solidSide[Direction.SOUTH.ordinal()])
                    || (solidSide[Direction.EAST.ordinal()] && solidSide[Direction.WEST.ordinal()]);
        };
        BlockState fill = Blocks.AIR.defaultBlockState();
        if (embedded) {
            fill = walls.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                    .orElse(Blocks.STONE.defaultBlockState());
        }
        add(level, pos);
        level.setBlock(pos, fill, Block.UPDATE_ALL);
    }

    /** Chunks waarin nog oude kabelblokken liggen; die zetten we om zodra de server kan. */
    private static final Map<Level, Set<ChunkPos>> TO_CONVERT = new HashMap<>();
    /** Losse oude kabelblokken die net neergezet zijn (door een gebouw of /setblock). */
    private static final Map<Level, Set<BlockPos>> PLACED = new HashMap<>();

    /** Dit oude kabelblok straks omzetten (na deze servertik). */
    public static void queue(ServerLevel level, BlockPos pos) {
        synchronized (TO_CONVERT) {
            PLACED.computeIfAbsent(level, key -> new HashSet<>()).add(pos.immutable());
        }
    }

    /**
     * Een chunk laadt (uit een oude wereld, of net gemaakt met een gebouw erin): liggen er oude
     * kabelblokken in? Kijken kan snel per stuk van 16 hoog ("zit dit blok er misschien in?").
     */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        for (LevelChunkSection section : chunk.getSections()) {
            if (!section.hasOnlyAir() && section.maybeHas(state -> state.is(ModBlocks.CABLE.get()))) {
                synchronized (TO_CONVERT) {
                    TO_CONVERT.computeIfAbsent(level, key -> new HashSet<>()).add(chunk.getPos());
                }
                return;
            }
        }
    }

    /** Na elke servertik: oude kabelblokken omzetten, dan de veranderde kabels (van elke soort) doorsturen. */
    public static void onServerTick(ServerTickEvent.Post event) {
        convertQueued();
        Cables.syncChanged();
    }

    /** De chunks uit de wachtrij omzetten (niet tijdens het laden zelf). */
    private static void convertQueued() {
        List<Map.Entry<Level, ChunkPos>> work = new ArrayList<>();
        List<Map.Entry<Level, BlockPos>> single = new ArrayList<>();
        synchronized (TO_CONVERT) {
            if (TO_CONVERT.isEmpty() && PLACED.isEmpty()) {
                return;
            }
            TO_CONVERT.forEach((level, chunks) -> chunks.forEach(chunk -> work.add(Map.entry(level, chunk))));
            TO_CONVERT.clear();
            PLACED.forEach((level, positions) -> positions.forEach(pos -> single.add(Map.entry(level, pos))));
            PLACED.clear();
        }
        for (Map.Entry<Level, BlockPos> entry : single) {
            if (entry.getKey() instanceof ServerLevel level && level.isLoaded(entry.getValue())) {
                convertLegacy(level, entry.getValue());
            }
        }
        for (Map.Entry<Level, ChunkPos> entry : work) {
            if (!(entry.getKey() instanceof ServerLevel level) || !level.hasChunk(entry.getValue().x, entry.getValue().z)) {
                continue;
            }
            LevelChunk chunk = level.getChunk(entry.getValue().x, entry.getValue().z);
            List<BlockPos> found = new ArrayList<>();
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(ModBlocks.CABLE.get()))) {
                    continue;
                }
                int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (section.getBlockState(x, y, z).is(ModBlocks.CABLE.get())) {
                                found.add(new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y,
                                        chunk.getPos().getMinBlockZ() + z));
                            }
                        }
                    }
                }
            }
            for (BlockPos pos : found) {
                convertLegacy(level, pos);
            }
        }
    }

    /**
     * De gebouwen (Biebia's Industries Tower, Biebia's Industries-bunker) hebben een onzichtbare "marker" op elke plek waar
     * een kabel hoort (zie cable() in tools/generate_structure.py). Komt zo'n marker in de wereld,
     * dan leggen we daar een kabel en laten we de marker zelf niet toe. Zo blijft het blok op die
     * plek (een koperen zuil, een vloer, een plafond) gewoon staan, met de kabel erin.
     *
     * Elke soort kabel heeft zijn eigen labeltje (zie CableKind.marker): "imttech_cable" voor stroom,
     * "imttech_net_cable" voor netwerk en "imttech_poe_cable" voor PoE. Eén marker kan er meer hebben.
     */
    public static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof net.minecraft.world.entity.Marker marker)) {
            return;
        }
        net.minecraft.nbt.CompoundTag data = marker.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getCompound("data");
        boolean any = false;
        for (CableKind kind : CableKind.values()) {
            if (data.getBoolean(kind.marker())) {
                Cables.add(kind, level, marker.blockPosition());
                any = true;
            }
        }
        if (any) {
            event.setCanceled(true);
        }
    }

    /** Bij het afsluiten: de wachtrijen vergeten. */
    public static void clear() {
        synchronized (TO_CONVERT) {
            TO_CONVERT.clear();
            PLACED.clear();
        }
        Cables.clear();
    }

    /** De plekken naast {@code pos} met een kabel (voor het tekenen en de tests). */
    public static List<BlockPos> neighbours(Level level, BlockPos pos) {
        List<BlockPos> result = new ArrayList<>(6);
        for (Direction dir : Direction.values()) {
            BlockPos next = pos.relative(dir);
            if (has(level, next)) {
                result.add(next);
            }
        }
        return result;
    }

    /** Alle kabelplekken die aan deze vastzitten (voor de tests), hooguit {@code max}. */
    public static Set<BlockPos> network(Level level, BlockPos start, int max) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        if (has(level, start)) {
            todo.add(start);
            seen.add(start);
        }
        while (!todo.isEmpty() && seen.size() < max) {
            for (BlockPos next : neighbours(level, todo.poll())) {
                if (seen.add(next)) {
                    todo.add(next);
                }
            }
        }
        return seen;
    }
}
