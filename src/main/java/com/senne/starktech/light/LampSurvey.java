package com.senne.starktech.light;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 * <h2>Licht per kamer (13 oktober)</h2>
 * Jouw wens: "zorg dat het licht niet door muren kan, dat je echt licht per kamer moet hebben". Eerst
 * deden we wat het spel met licht doet: stap voor stap naar de buren, om alles heen. Bij een gewone lamp
 * (15 stappen) merk je dat niet, maar 85 stappen ver komt dat licht door elke deuropening en gang, om
 * elke hoek: één lamp verlichtte je hele huis. Nu gaat het licht zoals echt licht: <b>in rechte lijnen</b>.
 * Een plek krijgt vol licht als de lamp hem kan "zien". Achter een muur, om een hoek of in de kamer
 * ernaast is het dus donker; door een open deur of een raam valt alleen een bundel naar binnen. Een
 * <b>dichte deur of een dicht luik</b> houdt het licht tegen zoals een muur (in het gewone spel gaat
 * licht door een deur heen).
 *
 * <h2>Hoe we "zien" uitrekenen</h2>
 * Voor elke plek een echte lijn naar de lamp trekken zou honderdduizenden lijnen kosten. Het kan sneller,
 * schil voor schil vanaf de lamp (eerst alles op 1 stap, dan op 2, ...). Elke plek krijgt een getal van 0
 * tot 1, hoe goed de lamp hem ziet: het gemiddelde van de buren aan de kant van de lamp, gewogen naar de
 * richting. Een plek schuin rechtsboven de lamp (3 opzij, 1 omhoog) kijkt voor 3/4 naar zijn buur aan de
 * lamp-kant ernaast en voor 1/4 naar de buur eronder. Zit er tussen hem en zo'n buur een muur, dan telt
 * die buur als 0. Zo gaat het getal achter een muur naar 0 (schaduw), en in de open lucht blijft het 1.
 * Vanaf 0.5 krijgt een plek vol licht. Licht kan zo nooit terug naar de lamp toe: een dichte kamer blijft
 * altijd dicht, en om een hoek komt het nauwelijks.
 *
 * <b>Uit elke kant.</b> De lamp is een heel blok dat aan alle zes kanten licht geeft. Keken we vanuit zijn
 * midden, dan zou een lamp in het plafond schuin langs dat plafond niets zien (de lijn loopt dan door de
 * plafondblokken naast hem), en bleef een grote hal aan de randen donker. Daarom kijken we vanuit elk vrij
 * plekje naast de lamp (bij een lamp in het plafond: het plekje eronder), en krijgt alles licht wat een
 * van die plekjes ziet. Elk plekje kijkt alleen naar wat vóór zijn kant van de lamp ligt (de onderkant
 * naar alles lager dan de lamp, enzovoort): wat erachter ligt, doet een andere kant. Dat scheelt de helft.
 *
 * Tot hoe ver: tot {@code reach} stappen van de lamp (85 bij lichtniveau 100), geteld zoals het spel (opzij
 * plus omhoog plus vooruit). Daarna doet het spel de rest: in de schaduw en na die 85 stappen dooft het
 * licht zoals gewoon licht uit, 1 per blok.
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

    /** Zo goed moet de lamp een plek zien (0 tot 1) voor vol licht. */
    private static final float LIT_FROM = 0.5f;
    /** Ziet de lamp een plek slechter dan dit, dan rekenen we daarachter niet verder (daar wordt het nooit licht). */
    private static final float PASS_ON = 0.05f;
    /** In {@link #sight}: deze plek staat in de rij, maar is nog niet uitgerekend. */
    private static final float QUEUED = -1.0f;

    /** De plek van de lamp, als getal (zie BlockPos.asLong). */
    private final long lamp;
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

    /** De kant van de lamp waar we nu vanuit kijken, en het plekje daar (zie "Uit elke kant" bovenaan). */
    private Direction side = Direction.DOWN;
    private int sourceX;
    private int sourceY;
    private int sourceZ;
    /** Per sectie: hoe goed dat plekje elke plek ziet, van 0 tot 1 (0 = niet, of nog niet bekeken). */
    private final Long2ObjectOpenHashMap<float[]> sight = new Long2ObjectOpenHashMap<>();
    private long lastSection = Long.MIN_VALUE;
    private float[] lastSight;
    /** Twee kladplekken, zodat we niet voor elke plek een nieuwe BlockPos maken. */
    private final BlockPos.MutableBlockPos here = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos there = new BlockPos.MutableBlockPos();

    private LampSurvey(BlockPos lamp, int reach, int minChunkX, int minChunkZ, int size, int minSection,
                       LevelChunkSection[][] chunks, boolean[][] storing, @Nullable Long2ObjectMap<long[]> previous,
                       int minY, int height) {
        this.lamp = lamp.asLong();
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
     * Vanuit elke vrije kant van de lamp kijken we apart ({@link #shine}), en tellen alles op.
     */
    Long2ObjectMap<long[]> run() {
        Long2ObjectOpenHashMap<long[]> region = new Long2ObjectOpenHashMap<>();
        BlockState lampState = getter.getBlockState(here.set(lamp));
        for (Direction side : DIRECTIONS) {
            long first = BlockPos.offset(lamp, side);
            BlockState next = stateAt(first);
            if (next == null || stopsBeam(next, getter, there.set(first))
                    || occludes(here.set(lamp), lampState, there.set(first), next, side)) {
                continue; // aan deze kant zit een muur (of niets dat geladen is)
            }
            shine(side, region);
        }
        // De lamp zelf geeft zelf al licht, en hoort er niet bij.
        long[] own = region.get(SectionPos.blockToSection(lamp));
        if (own != null) {
            int i = index(lamp);
            own[i >>> 6] &= ~(1L << (i & 63));
        }
        Long2ObjectOpenHashMap<long[]> result = new Long2ObjectOpenHashMap<>();
        for (Long2ObjectMap.Entry<long[]> entry : region.long2ObjectEntrySet()) {
            int count = 0;
            for (long word : entry.getValue()) {
                count += Long.bitCount(word);
            }
            if (count == 4096) {
                result.put(entry.getLongKey(), FULL);
            } else if (count > 0) {
                result.put(entry.getLongKey(), entry.getValue());
            }
        }
        if (previous != null) {
            for (Long2ObjectMap.Entry<long[]> entry : previous.long2ObjectEntrySet()) {
                int cx = SectionPos.x(entry.getLongKey()) - minChunkX;
                int cz = SectionPos.z(entry.getLongKey()) - minChunkZ;
                if (cx >= 0 && cz >= 0 && cx < size && cz < size && chunks[cx + cz * size] == null) {
                    result.put(entry.getLongKey(), entry.getValue());
                }
            }
        }
        return result;
    }

    /**
     * Kijk vanuit het plekje aan deze kant van de lamp: alles wat het tot {@code reach} stappen van de lamp
     * ziet (en vóór deze kant ligt), komt in {@code region}.
     *
     * Hoe: schil voor schil (zie de uitleg bovenaan). Per schil zoeken we eerst de plekken die erbij
     * komen: de buren van de vorige schil, verder van het plekje af. Daarna rekenen we voor elk uit hoe
     * goed het plekje hem ziet ({@link #sightOf}). Zijn buren aan de kant van het plekje liggen allemaal
     * in de vorige schil, en die is al klaar.
     */
    private void shine(Direction side, Long2ObjectOpenHashMap<long[]> region) {
        long source = BlockPos.offset(lamp, side);
        this.side = side;
        sourceX = BlockPos.getX(source);
        sourceY = BlockPos.getY(source);
        sourceZ = BlockPos.getZ(source);
        sight.clear();
        lastSection = Long.MIN_VALUE;
        lastSight = null;
        setSight(source, 1.0f);
        LongArrayList shell = new LongArrayList();
        shell.add(source);
        // Het plekje ligt al 1 stap van de lamp: vanaf hier nog reach - 1 stappen.
        for (int step = 1; step < reach && !shell.isEmpty(); step++) {
            LongArrayList next = new LongArrayList();
            for (int i = 0; i < shell.size(); i++) {
                long pos = shell.getLong(i);
                if (sightAt(pos) >= PASS_ON) {
                    queueOutward(pos, next);
                }
            }
            for (int i = 0; i < next.size(); i++) {
                long pos = next.getLong(i);
                setSight(pos, sightOf(pos));
            }
            shell = next;
        }
        // Wat het plekje goed genoeg ziet, krijgt vol licht.
        for (Long2ObjectMap.Entry<float[]> entry : sight.long2ObjectEntrySet()) {
            float[] cells = entry.getValue();
            long[] bits = null;
            for (int i = 0; i < 4096; i++) {
                if (cells[i] >= LIT_FROM) {
                    if (bits == null) {
                        bits = region.computeIfAbsent(entry.getLongKey(), key -> new long[64]);
                    }
                    bits[i >>> 6] |= 1L << (i & 63);
                }
            }
        }
    }

    /** Zet de buren van deze plek die verder van het plekje af liggen in de volgende schil (elk één keer). */
    private void queueOutward(long pos, LongArrayList next) {
        int dx = BlockPos.getX(pos) - sourceX;
        int dy = BlockPos.getY(pos) - sourceY;
        int dz = BlockPos.getZ(pos) - sourceZ;
        for (Direction direction : DIRECTIONS) {
            int along = direction.getAxis().choose(dx, dy, dz);
            if (along != 0 && Integer.signum(along) != direction.getAxisDirection().getStep()) {
                continue; // die kant op is terug naar het plekje
            }
            long further = BlockPos.offset(pos, direction);
            int ahead = side.getAxis().choose(BlockPos.getX(further) - BlockPos.getX(lamp),
                    BlockPos.getY(further) - BlockPos.getY(lamp), BlockPos.getZ(further) - BlockPos.getZ(lamp));
            if (ahead * side.getAxisDirection().getStep() <= 0) {
                continue; // achter deze kant van de lamp: dat doet een andere kant
            }
            if (sightAt(further) == 0.0f) { // nog niet in de rij (de volgende schil is nog niet uitgerekend)
                setSight(further, QUEUED);
                next.add(further);
            }
        }
    }

    /**
     * Hoe goed het plekje deze plek ziet, van 0 tot 1. Niet als het blok zelf het licht tegenhoudt (zie
     * {@link #stopsBeam}). Anders: het gemiddelde van zijn buren aan de kant van het plekje, elk zo zwaar
     * als hij in die richting ervan af ligt (3 opzij en 1 omhoog: 3/4 de buur ernaast, 1/4 de buur eronder).
     * Zit de dichte kant van een plaat of trap tussen hem en een buur, dan telt die buur als 0.
     */
    private float sightOf(long pos) {
        BlockState to = stateAt(pos);
        if (to == null || stopsBeam(to, getter, there.set(pos))) {
            return 0.0f; // niet geladen, een sectie zonder licht, of een muur
        }
        int dx = BlockPos.getX(pos) - sourceX;
        int dy = BlockPos.getY(pos) - sourceY;
        int dz = BlockPos.getZ(pos) - sourceZ;
        float seen = 0.0f;
        for (Direction direction : DIRECTIONS) {
            int along = direction.getAxis().choose(dx, dy, dz);
            if (along == 0 || Integer.signum(along) != direction.getAxisDirection().getStep()) {
                continue; // het licht komt niet uit die richting
            }
            long back = BlockPos.offset(pos, direction.getOpposite());
            float behind = sightAt(back);
            if (behind <= 0.0f) {
                continue;
            }
            BlockState from = stateAt(back);
            if (from == null || occludes(here.set(back), from, there.set(pos), to, direction)) {
                continue;
            }
            seen += Math.abs(along) * behind;
        }
        return seen / (Math.abs(dx) + Math.abs(dy) + Math.abs(dz));
    }

    /**
     * Houdt dit blok het licht van de lamp helemaal tegen? Een muur (een blok dat al het licht tegenhoudt,
     * ook getint glas), en een dichte deur of een dicht luik: in het gewone spel gaat licht daar doorheen,
     * maar dan zou een dichte deur geen kamers scheiden. Glas, water en bladeren laten het door.
     */
    static boolean stopsBeam(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getLightBlock(level, pos) >= LightEngine.MAX_LEVEL || closedDoor(state);
    }

    /** Een dichte deur of een dicht luik (ook die van ijzer). */
    static boolean closedDoor(BlockState state) {
        return (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock)
                && state.hasProperty(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.OPEN);
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

    /** Hoe goed het plekje deze plek ziet (0 als het er nog niet naar keek, {@link #QUEUED} als hij in de rij staat). */
    private float sightAt(long pos) {
        float[] cells = cellsOf(SectionPos.blockToSection(pos), false);
        return cells == null ? 0.0f : cells[index(pos)];
    }

    private void setSight(long pos, float value) {
        cellsOf(SectionPos.blockToSection(pos), true)[index(pos)] = value;
    }

    private @Nullable float[] cellsOf(long section, boolean create) {
        if (section == lastSection) {
            return lastSight;
        }
        float[] cells = sight.get(section);
        if (cells == null) {
            if (!create) {
                return null;
            }
            cells = new float[4096];
            sight.put(section, cells);
        }
        lastSection = section;
        lastSight = cells;
        return cells;
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
