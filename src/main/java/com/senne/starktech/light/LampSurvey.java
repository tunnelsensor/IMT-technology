package com.senne.starktech.light;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Eén meting van een Arc Lamp: tot waar zou het licht vol (15) zijn, als een lamp lichtniveau 100 kon
 * geven? (Zie ArcLampLight voor het hele verhaal.)
 *
 * We doen precies wat het spel met licht doet, maar met een groter getal: vanaf de lamp stap voor stap
 * naar de buren (boven, onder, noord, oost, zuid, west). Elke stap kost 1; door water of bladeren iets
 * meer (zoveel als dat blok licht tegenhoudt). Door een muur (een blok dat al het licht tegenhoudt) of
 * door de dichte kant van een trap of plaat gaat het niet. Elke plek die we binnen
 * {@code reach} stappen halen (85 bij lichtniveau 100), krijgt vol licht.
 *
 * Omdat dat veel plekken zijn (honderdduizenden), gebeurt het rekenen niet op de server zelf maar
 * ernaast, op een achtergrond-thread: de server loopt gewoon door. Daarom kijken we niet naar de wereld
 * zelf, maar naar een kopie van de lijstjes met blokken ("secties") die {@link #prepare} op de server
 * klaarzet. Dat mag: het spel leest die lijstjes zelf ook vanaf een andere thread (bij het licht).
 *
 * Chunks die niet geladen zijn, kunnen we niet zien: daar houden we wat de vorige meting zei.
 */
final class LampSurvey {
    /** Een sectie waarin alle 4096 plekken licht krijgen: één gedeelde rij (scheelt geheugen). Nooit aanpassen! */
    static final long[] FULL = full();

    private static final Direction[] DIRECTIONS = Direction.values();

    private final BlockPos origin;
    private final int reach;
    private final int minChunkX;
    private final int minChunkZ;
    /** Hoeveel chunks het vierkant breed is. */
    private final int size;
    private final int minSection;
    /** Per chunk in het vierkant (zie {@link #chunkIndex}): zijn secties, of null als hij niet geladen was. */
    private final LevelChunkSection[][] chunks;
    /** Per chunk, per sectie: bewaart het spel daar licht? Alleen daar kan licht komen. */
    private final boolean[][] storing;
    /** Wat de vorige meting vond (voor chunks die nu niet geladen zijn), of null. */
    private final @Nullable Long2ObjectMap<long[]> previous;
    private final Snapshot getter;

    /** Per sectie: hoeveel stappen het licht nodig had naar elke plek, plus 1 (0 = nog niet bereikt). */
    private final Long2ObjectOpenHashMap<byte[]> costs = new Long2ObjectOpenHashMap<>();
    private long lastSection = Long.MIN_VALUE;
    private byte[] lastCosts;

    private LampSurvey(BlockPos origin, int reach, int minChunkX, int minChunkZ, int size, int minSection,
                       LevelChunkSection[][] chunks, boolean[][] storing, @Nullable Long2ObjectMap<long[]> previous,
                       int minY, int height) {
        this.origin = origin.immutable();
        this.reach = reach;
        this.minChunkX = minChunkX;
        this.minChunkZ = minChunkZ;
        this.size = size;
        this.minSection = minSection;
        this.chunks = chunks;
        this.storing = storing;
        this.previous = previous;
        this.getter = new Snapshot(minY, height);
    }

    /**
     * Zet alles klaar voor een meting (op de server zelf): welke chunks rond de lamp geladen zijn, hun
     * secties, en in welke secties het spel licht bewaart.
     *
     * @param reach    tot hoeveel stappen het licht vol is (lichtniveau min 15)
     * @param previous wat de vorige meting van deze lamp vond, of null
     */
    static LampSurvey prepare(Level level, BlockPos origin, int reach, @Nullable Long2ObjectMap<long[]> previous) {
        int radius = (reach >> 4) + 1;
        int size = radius * 2 + 1;
        int minChunkX = SectionPos.blockToSectionCoord(origin.getX()) - radius;
        int minChunkZ = SectionPos.blockToSectionCoord(origin.getZ()) - radius;
        int minSection = level.getMinSection();
        int count = level.getSectionsCount();
        LayerLightEventListener light = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        LevelChunkSection[][] chunks = new LevelChunkSection[size * size][];
        boolean[][] storing = new boolean[size * size][];
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                int cx = minChunkX + dx;
                int cz = minChunkZ + dz;
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                boolean[] store = new boolean[count];
                for (int i = 0; i < count; i++) {
                    store[i] = light.getDataLayerData(SectionPos.of(cx, minSection + i, cz)) != null;
                }
                chunks[dx + dz * size] = chunk.getSections().clone();
                storing[dx + dz * size] = store;
            }
        }
        return new LampSurvey(origin, reach, minChunkX, minChunkZ, size, minSection, chunks, storing, previous,
                level.getMinBuildHeight(), level.getHeight());
    }

    /**
     * De meting zelf (op een achtergrond-thread). Geeft per sectie (zie SectionPos.asLong) een rij van
     * 4096 bits terug: 1 = die plek krijgt vol licht. De lamp zelf zit er niet in (die geeft zelf licht).
     *
     * Hoe: een "emmer" per aantal stappen. We pakken steeds een plek uit de laagste emmer die nog iets
     * heeft, en zetten zijn buren in de emmer van zijn stappen plus wat die buur kost. Zo komt elke plek
     * eerst langs via de kortste weg (net als water dat zich verspreidt).
     */
    Long2ObjectMap<long[]> run() {
        LongArrayFIFOQueue[] buckets = new LongArrayFIFOQueue[reach + 1];
        long start = origin.asLong();
        setCost(start, 0);
        bucket(buckets, 0).enqueue(start);
        BlockPos.MutableBlockPos here = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos there = new BlockPos.MutableBlockPos();
        for (int cost = 0; cost <= reach; cost++) {
            LongArrayFIFOQueue queue = buckets[cost];
            if (queue == null) {
                continue;
            }
            while (!queue.isEmpty()) {
                long pos = queue.dequeueLong();
                if (costOf(pos) != cost) {
                    continue; // later via een kortere weg gevonden
                }
                BlockState from = pos == start ? getter.getBlockState(here.set(pos)) : stateAt(pos);
                if (from == null) {
                    continue;
                }
                here.set(pos);
                for (Direction direction : DIRECTIONS) {
                    long next = BlockPos.offset(pos, direction);
                    BlockState to = stateAt(next);
                    if (to == null) {
                        continue; // niet geladen, of een sectie zonder licht
                    }
                    there.set(next);
                    // Zoveel houdt dit blok tegen: lucht en glas 0, water en bladeren 1, een muur 15.
                    int blocked = to.getLightBlock(getter, there);
                    if (blocked >= LightEngine.MAX_LEVEL) {
                        continue;
                    }
                    int step = cost + Math.max(1, blocked);
                    if (step > reach) {
                        continue;
                    }
                    int known = costOf(next);
                    if (known >= 0 && known <= step) {
                        continue;
                    }
                    if (occludes(here, from, there, to, direction)) {
                        continue;
                    }
                    setCost(next, step);
                    bucket(buckets, step).enqueue(next);
                }
            }
            buckets[cost] = null; // klaar met deze emmer, mag weg
        }
        return finish();
    }

    /** Zit de dichte kant van een van de twee blokken (een plaat, een trap) tussen ze in? Net als het spel. */
    private boolean occludes(BlockPos fromPos, BlockState from, BlockPos toPos, BlockState to, Direction direction) {
        if (emptyShape(from) && emptyShape(to)) {
            return false; // het gewone geval: geen van beide heeft zo'n kant
        }
        VoxelShape a = LightEngine.getOcclusionShape(getter, fromPos, from, direction);
        VoxelShape b = LightEngine.getOcclusionShape(getter, toPos, to, direction.getOpposite());
        return Shapes.faceShapeOccludes(a, b);
    }

    /** Hetzelfde als LightEngine.isEmptyShape (die is niet openbaar). */
    private static boolean emptyShape(BlockState state) {
        return !state.canOcclude() || !state.useShapeForLightOcclusion();
    }

    /** Van de stappen per plek naar de bits per sectie, plus wat de vorige meting vond in chunks die we niet zagen. */
    private Long2ObjectMap<long[]> finish() {
        Long2ObjectOpenHashMap<long[]> region = new Long2ObjectOpenHashMap<>();
        long originSection = SectionPos.blockToSection(origin.asLong());
        for (Long2ObjectMap.Entry<byte[]> entry : costs.long2ObjectEntrySet()) {
            byte[] cells = entry.getValue();
            long[] bits = new long[64];
            int count = 0;
            for (int i = 0; i < 4096; i++) {
                if (cells[i] != 0) {
                    bits[i >>> 6] |= 1L << (i & 63);
                    count++;
                }
            }
            if (entry.getLongKey() == originSection) {
                int i = index(origin.asLong());
                bits[i >>> 6] &= ~(1L << (i & 63));
                count--;
            }
            if (count == 4096) {
                region.put(entry.getLongKey(), FULL);
            } else if (count > 0) {
                region.put(entry.getLongKey(), bits);
            }
        }
        if (previous != null) {
            for (Long2ObjectMap.Entry<long[]> entry : previous.long2ObjectEntrySet()) {
                int cx = SectionPos.x(entry.getLongKey()) - minChunkX;
                int cz = SectionPos.z(entry.getLongKey()) - minChunkZ;
                if (cx >= 0 && cz >= 0 && cx < size && cz < size && chunks[cx + cz * size] == null) {
                    region.put(entry.getLongKey(), entry.getValue());
                }
            }
        }
        return region;
    }

    // ================================================================ Hulpjes

    /** Het blok op deze plek, of null als we er niet kunnen komen (niet geladen, of geen licht in die sectie). */
    private @Nullable BlockState stateAt(long pos) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        int chunk = chunkIndex(x, z);
        if (chunk < 0 || chunks[chunk] == null) {
            return null;
        }
        int section = (y >> 4) - minSection;
        if (section < 0 || section >= chunks[chunk].length || !storing[chunk][section]) {
            return null;
        }
        return chunks[chunk][section].getBlockState(x & 15, y & 15, z & 15);
    }

    /** Welke chunk in het vierkant, of -1 als hij erbuiten ligt. */
    private int chunkIndex(int x, int z) {
        int cx = (x >> 4) - minChunkX;
        int cz = (z >> 4) - minChunkZ;
        return cx < 0 || cz < 0 || cx >= size || cz >= size ? -1 : cx + cz * size;
    }

    /** De plek binnen zijn sectie, zoals het spel licht opslaat (eerst x, dan z, dan y). */
    static int index(long pos) {
        return (BlockPos.getY(pos) & 15) << 8 | (BlockPos.getZ(pos) & 15) << 4 | (BlockPos.getX(pos) & 15);
    }

    /** Hoeveel stappen het licht naar deze plek nodig had, of -1 als het er (nog) niet kwam. */
    private int costOf(long pos) {
        byte[] cells = cellsOf(SectionPos.blockToSection(pos), false);
        return cells == null ? -1 : cells[index(pos)] - 1;
    }

    private void setCost(long pos, int cost) {
        cellsOf(SectionPos.blockToSection(pos), true)[index(pos)] = (byte) (cost + 1);
    }

    private @Nullable byte[] cellsOf(long section, boolean create) {
        if (section == lastSection) {
            return lastCosts;
        }
        byte[] cells = costs.get(section);
        if (cells == null) {
            if (!create) {
                return null;
            }
            cells = new byte[4096];
            costs.put(section, cells);
        }
        lastSection = section;
        lastCosts = cells;
        return cells;
    }

    private static LongArrayFIFOQueue bucket(LongArrayFIFOQueue[] buckets, int cost) {
        if (buckets[cost] == null) {
            buckets[cost] = new LongArrayFIFOQueue();
        }
        return buckets[cost];
    }

    private static long[] full() {
        long[] bits = new long[64];
        Arrays.fill(bits, -1L);
        return bits;
    }

    /**
     * De kopie van de wereld als "BlockGetter": sommige blokken willen bij "hoeveel licht hou je tegen?"
     * even rondkijken. Dat doen ze dan in onze kopie, niet in de echte wereld (die mag niet vanaf een
     * andere thread).
     */
    private final class Snapshot implements BlockGetter {
        private final int minY;
        private final int height;

        Snapshot(int minY, int height) {
            this.minY = minY;
            this.height = height;
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            int chunk = chunkIndex(pos.getX(), pos.getZ());
            int section = (pos.getY() >> 4) - minSection;
            if (chunk < 0 || chunks[chunk] == null || section < 0 || section >= chunks[chunk].length) {
                return Blocks.AIR.defaultBlockState();
            }
            return chunks[chunk][section].getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public int getMinBuildHeight() {
            return minY;
        }
    }
}
