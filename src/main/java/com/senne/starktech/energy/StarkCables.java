package com.senne.starktech.energy;

import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModBlocks;
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
 * Waar de kabels liggen, en alles wat je ermee doet: leggen, weghalen, een route tekenen, en
 * oude kabelblokken omzetten.
 *
 * Een kabel ligt op een blokplek (zie CableCells). Op die plek mag ook een blok staan: dan zit de
 * kabel in dat blok (in een muur, een vloer). Kabels op plekken die naast elkaar liggen, zitten
 * aan elkaar vast; een apparaat dat naast een kabel staat (of er bovenop is gezet), zit eraan
 * vast. De stroom zelf regelt StarkGrid.
 */
public final class StarkCables {
    private StarkCables() {}

    /** Zo ver mag één stuk dat je in één keer tekent, hooguit zoeken (blokken). */
    private static final int MAX_ROUTE = 24;

    // ================================================================ Opvragen

    /** Ligt hier een kabel? (Een chunk die niet geladen is: nee.) */
    public static boolean has(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        return chunk.hasData(ModAttachments.CABLES) && chunk.getData(ModAttachments.CABLES).contains(pos);
    }

    /** Alle kabels in deze chunk (leeg als er geen zijn). */
    public static CableCells in(LevelChunk chunk) {
        return chunk.hasData(ModAttachments.CABLES) ? chunk.getData(ModAttachments.CABLES) : CableCells.EMPTY;
    }

    // ================================================================ Leggen en weghalen

    /** Legt een stuk kabel op deze plek. Geeft false als er al een lag. */
    public static boolean add(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        CableCells cells = in(chunk);
        if (cells.contains(pos)) {
            return false;
        }
        store(chunk, cells.with(pos.immutable()));
        return true;
    }

    /** Haalt het stuk kabel op deze plek weg. Geeft false als er geen lag. */
    public static boolean remove(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        CableCells cells = in(chunk);
        if (!cells.contains(pos)) {
            return false;
        }
        store(chunk, cells.without(pos));
        return true;
    }

    private static void store(LevelChunk chunk, CableCells cells) {
        if (cells.isEmpty()) {
            chunk.removeData(ModAttachments.CABLES);
        } else {
            chunk.setData(ModAttachments.CABLES, cells);
        }
        chunk.setUnsaved(true);
        // Naar iedereen die deze chunk ziet, zodat ze de kabel zien verschijnen of verdwijnen.
        chunk.syncData(ModAttachments.CABLES);
        StarkGrid.changed();
    }

    // ================================================================ Tekenen

    /**
     * De plekken van {@code from} naar {@code to} (allebei meegerekend), langs een route over
     * oppervlakken: over de vloer, tegen een muur op, onder een plafond. Waar geen oppervlak is,
     * mag hij ook door blokken heen (dan zit hij in de muur). We zoeken de kortste route met zo
     * weinig mogelijk bochten; lukt dat niet binnen MAX_ROUTE stappen, dan een rechte lijn per as.
     */
    public static List<BlockPos> route(Level level, BlockPos from, BlockPos to) {
        if (from.equals(to)) {
            return List.of(to);
        }
        if (from.distManhattan(to) <= MAX_ROUTE) {
            List<BlockPos> found = search(level, from, to);
            if (found != null) {
                return found;
            }
        }
        return straight(from, to);
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

    /** Een rechte lijn: eerst langs x, dan z, dan y. */
    private static List<BlockPos> straight(BlockPos from, BlockPos to) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos.MutableBlockPos at = from.mutable();
        path.add(from.immutable());
        while (at.getX() != to.getX()) {
            at.move(Integer.signum(to.getX() - at.getX()), 0, 0);
            path.add(at.immutable());
        }
        while (at.getZ() != to.getZ()) {
            at.move(0, 0, Integer.signum(to.getZ() - at.getZ()));
            path.add(at.immutable());
        }
        while (at.getY() != to.getY()) {
            at.move(0, Integer.signum(to.getY() - at.getY()), 0);
            path.add(at.immutable());
        }
        return path;
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
        boolean embedded = switch (old.getValue(StarkCableBlock.PLACEMENT)) {
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

    /** Na elke servertik: de chunks uit de wachtrij omzetten (niet tijdens het laden zelf). */
    public static void onServerTick(ServerTickEvent.Post event) {
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
     * De gebouwen (Stark Tower, Hydra-bunker) hebben een onzichtbare "marker" op elke plek waar
     * een kabel hoort (zie cable() in tools/generate_structure.py). Komt zo'n marker in de wereld,
     * dan leggen we daar een kabel en laten we de marker zelf niet toe. Zo blijft het blok op die
     * plek (een koperen zuil, een vloer, een plafond) gewoon staan, met de kabel erin.
     */
    public static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof net.minecraft.world.entity.Marker marker)) {
            return;
        }
        net.minecraft.nbt.CompoundTag tag = marker.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        if (tag.getCompound("data").getBoolean("starktech_cable")) {
            add(level, marker.blockPosition());
            event.setCanceled(true);
        }
    }

    /** Bij het afsluiten: de wachtrij vergeten. */
    public static void clear() {
        synchronized (TO_CONVERT) {
            TO_CONVERT.clear();
            PLACED.clear();
        }
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
