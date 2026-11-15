package com.senne.imttech.security.zone;

import com.senne.imttech.config.ImtConfig;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * De Autoscan van een zone (hoofdstuk 45, jouw wens: "autoscan zodat je makkelijk je bunker kunt scannen en protecten,
 * doet altijd 3 blokken in de muur zodat eventuele redstone ook wordt meegenomen; als het geen bunker is, is het
 * moeilijker, maar zoek daar ook iets goeds voor").
 * <ul>
 *   <li><b>Een bunker</b> (dicht): vanaf het startpunt vullen we alle kamers die open met elkaar verbonden zijn (lucht en
 *       alles zonder botsing: tapijt en een deur, ook een open deur, houden het tegen; een fakkel, knop of open hek niet).
 *       Daar maken we kubussen van, en elke kubus groeit {@link #margin()} blokken (de muur, en wat erin zit).</li>
 *   <li><b>Een open basis</b> (de kamer is niet dicht en je staat bij je gebouw, of je klikt een blok van het gebouw
 *       aan): we volgen alle aaneengesloten gebouwde blokken (alles wat geen natuur is, zie de tag {@code imttech:natural})
 *       en leggen er één doos omheen met dezelfde marge (jouw keuze).</li>
 * </ul>
 *
 * Hoofdstuk 116 (jouw wens: "de autoscan zegt dat er een mogelijke cave is, deze tekst komt uit de GUI ... ook wil ik
 * dat je een ping krijgt van waar die autoscan dat ziet. De 'er is een cave dus autoscan werkt niet' moet je kunnen
 * overriden door te zeggen bevestigen dat cave er bij moet. Als dit lag zou veroorzaken door dat het een giga cave is
 * mag het toch niet"):
 * <ul>
 *   <li>Het vullen laadt nooit een stuk wereld (zie {@link Reader}) en stopt meteen bij de open lucht.</li>
 *   <li>Is de kamer niet dicht, dan zoeken we waar hij openligt ({@link #leak}): daar komt de ping, en de weg die de scan
 *       nam gaat mee als stippellijn.</li>
 *   <li>Komt die opening in een grot uit, dan mag je de grot erbij nemen ("Include cave"): dezelfde scan met de grens
 *       {@link ImtConfig.Security#ZONE_SCAN_CAVE_MAX} en een tijdslimiet, waarbij de open lucht en de rand van het bereik
 *       gewoon de grens van de grot zijn. Is de grot groter (een "giga cave"), dan mag het niet.</li>
 *   <li>Wat er vroeger gebeurde (een doos om je gebouw als je ernaast staat) gebeurt nog steeds; de grot is een keuze
 *       erbovenop.</li>
 * </ul>
 * Alles blijft binnen het bereik van de kern ({@code limit}).
 */
public final class ZoneScan {
    private ZoneScan() {}

    /** Blokken die de natuur maakt (steen, aarde, zand, erts, bomen...): die horen niet bij een gebouw. */
    public static final TagKey<Block> NATURAL = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("imttech", "natural"));
    /**
     * Hoofdstuk 116: wat een grot of rots maakt (steen, aarde, erts...). Kleiner dan NATURAL: obsidiaan, stammen,
     * glowstone, zeelantaarns, terracotta en zandsteen bouw je ook vaak zelf, dus die tellen bij het zoeken naar de
     * opening als muur van je bunker.
     */
    public static final TagKey<Block> TERRAIN = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("imttech", "terrain"));

    /** De zes richtingen, in de volgorde van Minecraft (zo slaat het vullen op waar elk blok vandaan kwam). */
    private static final Direction[] DIRECTIONS = Direction.values();
    /** Zo ver (blokken) kijkt een "straal" of er een gebouwde muur staat (zie {@link #inside}). */
    private static final int RAY = 48;
    /** Ligt de open lucht meer dan zoveel stappen achter de opening, dan zat er een grot tussen. */
    private static final int CAVE_STEPS = 6;
    /** Geeft het maken van kubussen er meer dan zoveel, dan maken we ze op een grover raster (zie {@link #boxes}). */
    private static final int MESH_LIMIT = 400;
    /** Het grofste raster: 2^5 = 32 blokken per vakje. */
    private static final int MAX_SHIFT = 5;
    /** Tot zoveel blokken maken we de kubussen eerst precies (daarboven meteen grover: dat scheelt veel rekenwerk). */
    private static final int EXACT_CELLS = 30_000;
    /** Hooguit zoveel punten van de weg gaan mee naar de speler (de stippellijn). */
    private static final int PATH_POINTS = 96;
    /** Hooguit zoveel pings voor plekken waar een grot de open lucht raakt. */
    private static final int MAX_MOUTHS = 3;

    /** Hoe het vullen afliep (hoofdstuk 116). */
    public enum Stop {
        /** Dicht: alles gevonden, binnen de grenzen. */
        CLOSED,
        /** Bij de open lucht gekomen: de kamers zijn open naar buiten. */
        SKY,
        /** Tegen de rand van het bereik van de kern gekomen. */
        EDGE,
        /** Meer blokken dan mocht: een grote grot (of een heel groot gebouw). */
        BIG,
        /** Bij een stuk wereld dat niet geladen is (dat laden we niet: dat laat de server haperen). */
        UNLOADED,
        /** Het duurde te lang (alleen bij "Include cave": anders zou de server haperen). */
        SLOW
    }

    /** Wat een ping laat zien: de opening naar een grot, de opening naar buiten, of waar een grot zelf de open lucht raakt. */
    public enum Kind {
        CAVE("Opening: cave"),
        OUTSIDE("Opening: outside"),
        CAVE_EXIT("Cave exit");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        /** Het tekstje bij de ping (in het Engels, zoals alles in het spel). */
        public String label() {
            return label;
        }
    }

    /** Een plek die de scan je laat zien: een ping (hoofdstuk 116). */
    public record Mark(BlockPos pos, Kind kind) {}

    /**
     * Wat de scan vond.
     *
     * @param boxes     de kubussen met de marge erbij (leeg als het niet lukte)
     * @param roomBoxes dezelfde kubussen zonder marge: de kamers zelf, of de doos om het gebouw (zie {@link #apply})
     * @param enclosed  true: dichte kamers; false: een open basis (een doos om het gebouw)
     * @param cells     hoeveel blokken de kamers (of het gebouw) telden
     * @param problem   waarom het niet lukte (in het Engels, voor in het spel), of null
     * @param marks     waar de kamers openliggen (de pings; leeg = geen ping)
     * @param path      de weg die de scan nam (een paar punten, voor de stippellijn), of leeg
     * @param offer     wat de knop doet die je nu kunt kiezen: null = geen knop, anders de tekst ("Include cave")
     * @param cave      de kamers nemen een grot mee (meer dan ZONE_SCAN_MAX blokken)
     * @param headline  een kopje voor het venster van de app Zone (bijvoorbeeld "OPEN TO A CAVE"), of null
     * @param note      een uitleg bij een scan die wel lukte maar iets te melden heeft, of null
     */
    public record Result(List<ZoneBox> boxes, List<ZoneBox> roomBoxes, boolean enclosed, int cells, @Nullable String problem,
                         List<Mark> marks, List<BlockPos> path, @Nullable String offer, boolean cave, @Nullable String headline,
                         @Nullable String note) {
        static Result failed(String problem) {
            return failed(null, problem, Leak.NONE);
        }

        static Result failed(@Nullable String headline, String problem, Leak leak) {
            return new Result(List.of(), List.of(), false, 0, problem, leak.marks(), leak.path(), leak.offer(), false, headline, null);
        }

        public boolean ok() {
            return problem == null;
        }

        /**
         * Deze uitkomst (de doos om een gebouw), met de pings, de weg en het kopje van een eerdere scan die niet dicht was:
         * zo zie je toch waar de kamer openlag, en kun je de grot nog erbij nemen.
         */
        public Result after(Result earlier) {
            if (earlier.marks().isEmpty() && earlier.headline() == null) {
                return this;
            }
            String why = ok() ? "Not closed, so a box around your building instead" : note;
            return new Result(boxes, roomBoxes, enclosed, cells, problem, earlier.marks(), earlier.path(), earlier.offer(), cave,
                    earlier.headline(), why);
        }
    }

    /**
     * Wat {@link #leak} vond: de pings, de weg (een paar punten), en of de opening in een grot uitkomt (dan mag je hem erbij
     * nemen) of je kamers gewoon heel groot zijn.
     */
    record Leak(List<Mark> marks, List<BlockPos> path, @Nullable String offer, boolean cave, boolean tooBig) {
        static final Leak NONE = new Leak(List.of(), List.of(), null, false, false);
    }

    // ================================================================ De instellingen

    /** Hoeveel blokken muur (en marge om een gebouw) er bij de zone komt. */
    public static int margin() {
        return Math.max(0, Math.round(ImtConfig.Security.ZONE_SCAN_WALL));
    }

    /** Zoveel blokken mogen de kamers (of het gebouw) hooguit tellen bij een gewone scan. */
    public static int roomMax() {
        return Math.max(1000, Math.round(ImtConfig.Security.ZONE_SCAN_MAX));
    }

    /** Zoveel blokken mogen kamers en grot samen tellen bij "Include cave". */
    public static int caveMax() {
        return Math.max(roomMax(), Math.round(ImtConfig.Security.ZONE_SCAN_CAVE_MAX));
    }

    // ================================================================ Scannen

    /** Een gewone scan vanaf {@code start} (zie {@link #scan(ServerLevel, BlockPos, ZoneBox, int, boolean)}). */
    public static Result scan(ServerLevel level, BlockPos start, ZoneBox limit, int maxBoxes) {
        return scan(level, start, limit, maxBoxes, false);
    }

    /**
     * Scant vanaf {@code start}: een blok in een kamer (lucht), of een blok van het gebouw. {@code limit} is hoe ver de
     * zone mag komen (het bereik van de kern); {@code maxBoxes} hoeveel kubussen er hooguit uit mogen komen.
     * {@code includeCave}: de knop "Include cave" (hoofdstuk 116), met een grotere grens en een tijdslimiet.
     */
    public static Result scan(ServerLevel level, BlockPos start, ZoneBox limit, int maxBoxes, boolean includeCave) {
        int rooms = roomMax();
        return scan(level, start, limit, maxBoxes, rooms, includeCave ? caveMax() : rooms,
                includeCave ? Math.max(1, Math.round(ImtConfig.Security.ZONE_SCAN_CAVE_MILLIS)) : 0);
    }

    /**
     * De scan met eigen grenzen (de tests gebruiken dit met kleine getallen). {@code roomMax}: tot hier is het een
     * gewone kamer; {@code max}: zoveel blokken mag het vullen hooguit (groter dan {@code roomMax} = de grot erbij
     * nemen); {@code millis}: na zoveel milliseconden stopt het vullen (0 = geen limiet).
     */
    public static Result scan(ServerLevel level, BlockPos start, ZoneBox limit, int maxBoxes, int roomMax, int max, int millis) {
        if (!limit.contains(start)) {
            return Result.failed("That is outside the reach of your core");
        }
        Reader reader = new Reader(level);
        BlockState state = reader.state(start);
        if (state == null) {
            return Result.failed("That part of the world is not loaded");
        }
        boolean includeCave = max > roomMax;
        Leak leak = Leak.NONE;
        String headline = null;
        if (passable(level, start, state)) {
            long deadline = millis > 0 ? System.nanoTime() + millis * 1_000_000L : Long.MAX_VALUE;
            // Bij "Include cave" zijn de open lucht en de rand van het bereik gewoon de grens van de grot.
            Fill rooms = fill(reader, start, limit, max, deadline, includeCave ? Sky.BOUNDARY : Sky.STOP,
                    (pos, s) -> passable(level, pos, s));
            if (rooms.stop == Stop.CLOSED) {
                // Dichte kamers (eventueel met de grot erbij): kubussen, en elke kubus de marge groter.
                List<ZoneBox> roomBoxes = boxes(rooms.cells(), maxBoxes);
                boolean cave = rooms.size() > roomMax;
                List<Mark> mouths = rooms.mouths;
                String note = mouths.isEmpty() ? (rooms.edge ? "The cave goes on past the reach of your core" : null)
                        : "The cave touches the open air (see the ping): it is in your zone, but anyone can walk in there";
                return new Result(grow(roomBoxes, margin(), limit), roomBoxes, true, rooms.size(), null, mouths, List.of(), null,
                        cave, note == null ? null : "ROOMS + CAVE", note);
            }
            leak = leak(level, reader, rooms, limit, includeCave);
            String ping = leak.marks().isEmpty() ? "" : " (see the ping)";
            if (includeCave) {
                // "Include cave" lukte niet: zeggen waarom (zonder een doos als vervanging: je vroeg om de grot).
                return switch (rooms.stop) {
                    case UNLOADED -> Result.failed("NOT LOADED", "Part of the cave is not loaded: go there and scan again", leak);
                    case SLOW -> Result.failed("CAVE TOO BIG", "The cave is too big to scan without lag: close the opening" + ping, leak);
                    default -> Result.failed("CAVE TOO BIG", "The cave is too big (over " + String.format("%,d", max)
                            + " blocks): close the opening" + ping, leak);
                };
            }
            String why;
            if (leak.cave()) {
                headline = "OPEN TO A CAVE";
                why = "Your rooms open into a cave" + ping;
            } else if (leak.tooBig()) {
                headline = "ROOMS TOO BIG";
                why = "Your rooms are bigger than " + String.format("%,d", roomMax) + " blocks";
            } else {
                headline = switch (rooms.stop) {
                    case SKY -> "OPEN TO THE OUTSIDE";
                    case EDGE -> "TOO FAR";
                    case UNLOADED -> "NOT LOADED";
                    default -> "NOT CLOSED";
                };
                why = switch (rooms.stop) {
                    case SKY -> "Not a closed room: it is open to the outside" + ping;
                    case EDGE -> "Not a closed room: it goes on past the reach of your core";
                    case UNLOADED -> "Not a closed room, and part of it is not loaded";
                    default -> "Not a closed room: it goes on and on" + ping;
                };
            }
            // Zoals altijd: staat er een gebouwd blok naast het startpunt, dan een doos om dat gebouw (de grot blijft een keuze).
            BlockPos solid = null;
            for (Direction direction : DIRECTIONS) {
                BlockPos next = start.relative(direction);
                BlockState nextState = reader.state(next);
                if (nextState != null && isBuilt(nextState)) {
                    solid = next;
                    break;
                }
            }
            if (solid == null) {
                return Result.failed(headline, why, leak);
            }
            start = solid;
        } else if (!isBuilt(state)) {
            return Result.failed("That is a natural block: click a block of your building, or stand in a closed room");
        }
        // Een open basis: alle gebouwde blokken die aan elkaar vastzitten, met een doos eromheen.
        Fill built = fill(reader, start, limit, roomMax, Long.MAX_VALUE, Sky.IGNORE, (pos, s) -> isBuilt(s));
        if (built.stop != Stop.CLOSED) {
            return Result.failed(headline, built.stop == Stop.UNLOADED ? "Part of your building is not loaded: go there and scan again"
                    : "Your building is too big to scan in one go: scan a part with the Zone Marker", leak);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long cell : built.cells()) {
            minX = Math.min(minX, BlockPos.getX(cell));
            minY = Math.min(minY, BlockPos.getY(cell));
            minZ = Math.min(minZ, BlockPos.getZ(cell));
            maxX = Math.max(maxX, BlockPos.getX(cell));
            maxY = Math.max(maxY, BlockPos.getY(cell));
            maxZ = Math.max(maxZ, BlockPos.getZ(cell));
        }
        List<ZoneBox> roomBoxes = List.of(ZoneBox.of(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ)));
        String note = headline == null ? null : "Not closed, so a box around your building instead";
        return new Result(grow(roomBoxes, margin(), limit), roomBoxes, false, built.size(), null, leak.marks(), leak.path(),
                leak.offer(), false, headline, note);
    }

    /**
     * Waar een scan vanaf je voeten begint: jouw blok, of het blok erboven als je op iets met een randje staat (tapijt, een
     * halve plaat, zielenzand), zodat de scan in de kamer begint en niet in de vloer.
     */
    public static BlockPos roomStart(ServerLevel level, BlockPos feet) {
        if (!passable(level, feet, level.getBlockState(feet)) && passable(level, feet.above(), level.getBlockState(feet.above()))) {
            return feet.above();
        }
        return feet;
    }

    /** Kun je hier staan of doorheen: lucht, of een blok zonder botsing (tapijt is wel een vloer, een knop niet)? */
    static boolean passable(ServerLevel level, BlockPos pos, BlockState state) {
        return state.isAir() || !(state.getBlock() instanceof LiquidBlock) && state.getCollisionShape(level, pos).isEmpty();
    }

    /** Hoort dit blok bij een gebouw: vast, en niet iets dat de natuur maakt? */
    static boolean isBuilt(BlockState state) {
        return !state.isAir() && !(state.getBlock() instanceof LiquidBlock) && !state.is(NATURAL);
    }

    // ================================================================ Blokken lezen zonder te laden

    /**
     * Leest blokken zonder een stuk wereld te laden (hoofdstuk 116). {@code ServerLevel.getBlockState} laadt een stuk dat
     * niet geladen is (en maakt het zelfs, als het er nog nooit was): bij een grote grot of een bereik van honderden
     * blokken laat dat de server lang haperen. Hier geeft een niet-geladen stuk null, en dan stopt de scan.
     */
    static final class Reader {
        final ServerLevel level;
        private long key = Long.MIN_VALUE;
        private @Nullable LevelChunk chunk;

        Reader(ServerLevel level) {
            this.level = level;
        }

        private @Nullable LevelChunk chunk(int x, int z) {
            long wanted = ChunkPos.asLong(x >> 4, z >> 4);
            if (wanted != key) {
                key = wanted;
                chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            }
            return chunk;
        }

        /** Het blok op deze plek, of null als dat stuk wereld niet geladen is. */
        @Nullable BlockState state(BlockPos pos) {
            LevelChunk here = chunk(pos.getX(), pos.getZ());
            return here == null ? null : here.getBlockState(pos);
        }

        /**
         * Staat deze plek in de open lucht: niets erboven dat tegenhoudt? Bladeren tellen niet mee (onder een bos sta je
         * buiten), glas en water wel (een glazen dak of een basis onder water is dicht).
         */
        boolean openSky(BlockPos pos) {
            LevelChunk here = chunk(pos.getX(), pos.getZ());
            return here != null
                    && pos.getY() > here.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX() & 15, pos.getZ() & 15);
        }
    }

    // ================================================================ Vullen

    private interface Cell {
        boolean matches(BlockPos pos, BlockState state);
    }

    /** Wat het vullen doet met de open lucht: stoppen (een kamer), negeren (een gebouw) of als grens zien (een grot erbij). */
    private enum Sky { STOP, IGNORE, BOUNDARY }

    /** Wat het vullen vond: elk blok met de richting waar het vandaan kwam (zo vinden we de weg terug), en hoe het afliep. */
    private static final class Fill {
        final long start;
        /** Elk gevonden blok, met de richting (0 tot 5, zie DIRECTIONS) waarin we het vanaf zijn buur bereikten; -1 = het begin. */
        final Long2ByteOpenHashMap from = new Long2ByteOpenHashMap();
        /** De blokken die nog bekeken moesten worden toen het vullen stopte (de buitenste laag; zie {@link #leak}). */
        @Nullable LongArrayFIFOQueue todo;
        Stop stop = Stop.CLOSED;
        /** Waar het vullen ophield (bij CLOSED: het begin). */
        long escape;
        /** Bij een grot erbij: waar hij de open lucht raakt (hooguit MAX_MOUTHS, een eindje uit elkaar). */
        final List<Mark> mouths = new ArrayList<>();
        /** Bij een grot erbij: raakte hij de rand van het bereik? */
        boolean edge;

        Fill(long start) {
            this.start = start;
            this.escape = start;
            from.defaultReturnValue((byte) -2);
            from.put(start, (byte) -1);
        }

        int size() {
            return from.size();
        }

        LongSet cells() {
            return from.keySet();
        }

        Fill stopped(Stop how, long where) {
            stop = how;
            escape = where;
            return this;
        }

        /** De weg van het begin naar {@code end} (de kortste: het vullen gaat laag voor laag). */
        long[] path(long end) {
            LongArrayList back = new LongArrayList();
            long here = end;
            for (int guard = 0; guard <= from.size(); guard++) {
                back.add(here);
                byte direction = from.get(here);
                if (direction < 0) {
                    break; // het begin (of een blok dat er niet in zit)
                }
                Direction step = DIRECTIONS[direction];
                here = BlockPos.offset(here, -step.getStepX(), -step.getStepY(), -step.getStepZ());
            }
            long[] path = new long[back.size()];
            for (int i = 0; i < path.length; i++) {
                path[i] = back.getLong(path.length - 1 - i);
            }
            return path;
        }

        void mouth(BlockPos pos) {
            if (mouths.size() >= MAX_MOUTHS) {
                return;
            }
            for (Mark mark : mouths) {
                if (mark.pos().distSqr(pos) < 64) {
                    return; // vlak bij een ping die er al is
                }
            }
            mouths.add(new Mark(pos.immutable(), Kind.CAVE_EXIT));
        }
    }

    /**
     * Vult vanaf {@code start} alle buren (zes kanten) waar {@code cell} ja op zegt, laag voor laag. Stopt bij de rand van
     * {@code limit} (EDGE), bij meer dan {@code max} blokken (BIG), bij een stuk wereld dat niet geladen is (UNLOADED) en
     * na de {@code deadline} (SLOW). Met {@link Sky#STOP} stopt het bij het eerste blok in de open lucht (SKY); met
     * {@link Sky#BOUNDARY} zijn de open lucht en de rand van het bereik gewoon de grens (een grot erbij nemen).
     */
    private static Fill fill(Reader reader, BlockPos start, ZoneBox limit, int max, long deadline, Sky sky, Cell cell) {
        Fill fill = new Fill(start.asLong());
        if (sky == Sky.STOP && reader.openSky(start)) {
            return fill.stopped(Stop.SKY, fill.start);
        }
        LongArrayFIFOQueue todo = new LongArrayFIFOQueue();
        todo.enqueue(fill.start);
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int minY = reader.level.getMinBuildHeight();
        int maxY = reader.level.getMaxBuildHeight();
        int steps = 0;
        while (!todo.isEmpty()) {
            long here = todo.dequeueLong();
            if ((++steps & 1023) == 0 && System.nanoTime() > deadline) {
                return fill.stopped(Stop.SLOW, here);
            }
            int x = BlockPos.getX(here);
            int y = BlockPos.getY(here);
            int z = BlockPos.getZ(here);
            for (int d = 0; d < DIRECTIONS.length; d++) {
                Direction direction = DIRECTIONS[d];
                probe.set(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
                if (!limit.contains(probe)) {
                    if (sky == Sky.BOUNDARY) {
                        fill.edge = true;
                        continue;
                    }
                    fill.todo = todo;
                    return fill.stopped(Stop.EDGE, here); // tot aan het bereik: dit is niet dicht (of te groot)
                }
                if (probe.getY() < minY || probe.getY() >= maxY) {
                    continue;
                }
                long key = probe.asLong();
                if (fill.from.containsKey(key)) {
                    continue;
                }
                BlockState state = reader.state(probe);
                if (state == null) {
                    fill.todo = todo;
                    return fill.stopped(Stop.UNLOADED, here);
                }
                if (!cell.matches(probe, state)) {
                    continue;
                }
                if (sky == Sky.BOUNDARY && reader.openSky(probe)) {
                    fill.mouth(probe); // de grot raakt hier de open lucht: dat is zijn rand
                    continue;
                }
                fill.from.put(key, (byte) d);
                if (fill.from.size() > max) {
                    fill.todo = todo;
                    return fill.stopped(Stop.BIG, key);
                }
                if (sky == Sky.STOP && reader.openSky(probe)) {
                    return fill.stopped(Stop.SKY, key);
                }
                todo.enqueue(key);
            }
        }
        return fill;
    }

    // ================================================================ Waar ligt de opening (de ping)

    /**
     * Waar de kamers openliggen (hoofdstuk 116, jouw wens: "een ping van waar die autoscan dat ziet"). We volgen de weg
     * van het begin naar waar het vullen ophield; die gaat door de opening. Langs die weg kijken we per blok of het nog
     * "binnen" is ({@link #inside}: aan minstens vier kanten een gebouwde muur). De opening is het laatste blok binnen,
     * vlak voor de weg echt naar buiten gaat (drie blokken op rij buiten, of de open lucht). Een gat in je muur heeft
     * muur aan vier kanten en telt dus als binnen; het eerste blok van de grot erachter niet.
     * <p>
     * Stopte het vullen omdat het te veel blokken werden, dan zoeken we in de buitenste laag een blok dat niet binnen is
     * (de grot) en volgen de weg daarheen; zijn ze allemaal binnen, dan zijn je kamers gewoon heel groot. Is de bunker
     * zelf uit natuursteen gehakt (dan is niets binnen), dan wijzen we alleen een korte deuropening naar de open lucht
     * aan: een gok verderop zou je de verkeerde kant op sturen. De stippellijn laat dan toch zien waar de scan heen ging.
     */
    private static Leak leak(ServerLevel level, Reader reader, Fill fill, ZoneBox limit, boolean includeCave) {
        long end = fill.escape;
        boolean tooBig = false;
        if (fill.stop == Stop.BIG || fill.stop == Stop.SLOW) {
            long outside = outsideInFrontier(level, reader, fill, limit);
            if (outside == Long.MIN_VALUE) {
                tooBig = fill.stop == Stop.BIG && !includeCave;
            } else {
                end = outside;
            }
        }
        long[] path = fill.path(end);
        int n = path.length - 1;
        if (n < 1) {
            return Leak.NONE; // het begin zelf is al buiten (je staat in de open lucht): geen ping op je eigen voeten
        }
        byte[] seen = new byte[n + 1]; // 0 = nog niet bekeken, 1 = binnen, 2 = buiten
        int opening = -1;
        boolean anyInside = false;
        for (int i = 0; i <= n && opening < 0; i++) {
            if (insideAt(level, reader, path, i, limit, seen)) {
                anyInside = true;
                continue;
            }
            if (!anyInside) {
                continue;
            }
            boolean out = true;
            for (int k = i + 1; k <= Math.min(n, i + 2); k++) {
                if (insideAt(level, reader, path, k, limit, seen)) {
                    out = false;
                    break;
                }
            }
            if (out) {
                opening = i - 1;
            }
        }
        List<Mark> marks = new ArrayList<>();
        boolean cave = false;
        if (!anyInside) {
            // Uit natuursteen gehakt: alleen een korte overdekte gang naar de open lucht (een deuropening) wijzen we aan.
            if (fill.stop == Stop.SKY && covered(reader, path, n) <= 8) {
                opening = n - 1;
                marks.add(new Mark(BlockPos.of(path[opening]), Kind.OUTSIDE));
            }
        } else if (opening >= 0) {
            BlockPos at = BlockPos.of(path[opening]);
            if (fill.stop == Stop.SKY && n - opening <= CAVE_STEPS) {
                marks.add(new Mark(at, Kind.OUTSIDE)); // een deuropening naar buiten
            } else {
                marks.add(new Mark(at, Kind.CAVE));
                cave = fill.stop != Stop.UNLOADED;
                if (fill.stop == Stop.SKY) {
                    marks.add(new Mark(BlockPos.of(path[n - 1]), Kind.CAVE_EXIT)); // eerst een grot, en die gaat naar buiten
                }
            }
        } else if (fill.stop == Stop.BIG && !includeCave) {
            tooBig = true; // alles langs de weg is binnen: een heel grote bunker, geen grot
        }
        // Na een mislukte "Include cave" geen knop meer (dan is het een giga cave); anders de grot erbij, of groter scannen.
        String offer = includeCave ? null : cave ? "Include cave" : tooBig ? "Scan bigger" : null;
        return new Leak(List.copyOf(marks), samplePath(path, opening), offer, cave, tooBig && !cave);
    }

    /**
     * In de buitenste laag van een vulling die te groot werd: een blok dat niet "binnen" is (bij voorkeur zo ver mogelijk
     * weg), of Long.MIN_VALUE als we er geen vinden. We bekijken hooguit 128 blokken, verspreid over die laag.
     */
    private static long outsideInFrontier(ServerLevel level, Reader reader, Fill fill, ZoneBox limit) {
        if (!inside(level, reader, BlockPos.of(fill.escape), limit)) {
            return fill.escape;
        }
        LongArrayFIFOQueue todo = fill.todo;
        if (todo == null || todo.isEmpty()) {
            return Long.MIN_VALUE;
        }
        long[] layer = new long[todo.size()];
        for (int i = 0; i < layer.length; i++) {
            layer[i] = todo.dequeueLong();
        }
        int step = Math.max(1, layer.length / 128);
        for (int i = layer.length - 1; i >= 0; i -= step) {
            if (!inside(level, reader, BlockPos.of(layer[i]), limit)) {
                return layer[i];
            }
        }
        return Long.MIN_VALUE;
    }

    /** Hoeveel overdekte blokken (geen open lucht) er vlak voor het einde van de weg liggen. */
    private static int covered(Reader reader, long[] path, int n) {
        int count = 0;
        for (int i = n - 1; i >= 0 && !reader.openSky(BlockPos.of(path[i])); i--) {
            count++;
        }
        return count;
    }

    /** Een paar punten van de weg voor de stippellijn: dicht bij de opening alles, verderop om de zoveel. */
    private static List<BlockPos> samplePath(long[] path, int opening) {
        List<BlockPos> points = new ArrayList<>();
        int step = Math.max(1, (int) Math.ceil(path.length / (double) PATH_POINTS));
        for (int i = 0; i < path.length; i++) {
            boolean near = opening >= 0 && Math.abs(i - opening) <= 6;
            if (near || i % step == 0 || i == path.length - 1) {
                points.add(BlockPos.of(path[i]));
            }
        }
        // Nooit meer dan het bericht aankan: dan om de twee.
        while (points.size() > PATH_POINTS + 13) {
            List<BlockPos> half = new ArrayList<>();
            for (int i = 0; i < points.size(); i += 2) {
                half.add(points.get(i));
            }
            points = half;
        }
        return List.copyOf(points);
    }

    private static boolean insideAt(ServerLevel level, Reader reader, long[] path, int i, ZoneBox limit, byte[] seen) {
        if (seen[i] == 0) {
            seen[i] = (byte) (inside(level, reader, BlockPos.of(path[i]), limit) ? 1 : 2);
        }
        return seen[i] == 1;
    }

    /**
     * Is dit blok "binnen" een gebouw: niet in de open lucht, en in minstens vier van de zes richtingen (recht omhoog,
     * omlaag en naar de vier kanten) is het eerste vaste blok een gebouwd blok (geen rots, zie {@link #TERRAIN})?
     */
    private static boolean inside(ServerLevel level, Reader reader, BlockPos pos, ZoneBox limit) {
        if (reader.openSky(pos)) {
            return false;
        }
        int walls = 0;
        int open = 0;
        BlockPos.MutableBlockPos ray = new BlockPos.MutableBlockPos();
        for (Direction direction : DIRECTIONS) {
            if (wallHit(level, reader, pos, direction, limit, ray)) {
                walls++;
            } else if (++open > 2) {
                return false; // al drie richtingen zonder muur: vier halen we niet meer
            }
        }
        return walls >= 4;
    }

    /** Raakt een rechte lijn vanaf {@code pos} in deze richting (hooguit RAY blokken) eerst een gebouwd blok? */
    private static boolean wallHit(ServerLevel level, Reader reader, BlockPos pos, Direction direction, ZoneBox limit,
                                   BlockPos.MutableBlockPos ray) {
        for (int k = 1; k <= RAY; k++) {
            ray.set(pos.getX() + direction.getStepX() * k, pos.getY() + direction.getStepY() * k,
                    pos.getZ() + direction.getStepZ() * k);
            if (!limit.contains(ray) || ray.getY() < level.getMinBuildHeight() || ray.getY() >= level.getMaxBuildHeight()) {
                return false;
            }
            BlockState state = reader.state(ray);
            if (state == null) {
                return false;
            }
            if (!passable(level, ray, state)) {
                return !(state.getBlock() instanceof LiquidBlock) && !state.is(TERRAIN);
            }
        }
        return false;
    }

    // ================================================================ Blokken naar kubussen

    /**
     * Maakt hooguit {@code maxBoxes} kubussen van een stel blokken. Eerst steeds vanaf een blok dat nog nergens in zit een
     * zo groot mogelijke kubus (eerst naar het oosten, dan naar het zuiden, dan omhoog). Geeft dat er heel veel (een
     * grillige grot), dan doen we dat op een grover raster van 2, 4, 8... blokken per vakje (een vakje telt als er één
     * blok in zit); bij heel veel blokken beginnen we meteen grover. Daarna voegen we kubussen samen tot het past (zie
     * {@link #mergeDown}). Een kubus mag iets te groot zijn: een zone die een blok meer beschermt, is geen ramp.
     */
    public static List<ZoneBox> boxes(LongSet cells, int maxBoxes) {
        int first = cells.size() <= EXACT_CELLS ? 0
                : Math.min(MAX_SHIFT, (int) Math.ceil(Math.log(cells.size() / 2000.0) / Math.log(8)));
        List<ZoneBox> boxes = List.of();
        for (int shift = first; shift <= MAX_SHIFT; shift++) {
            boxes = mesh(shift == 0 ? cells : coarse(cells, shift), shift);
            if (boxes.size() <= MESH_LIMIT) {
                break;
            }
        }
        List<ZoneBox> merged = new ArrayList<>(boxes);
        mergeDown(merged, Math.max(1, maxBoxes), box -> true);
        return merged;
    }

    /** De blokken op een raster van 2^shift blokken per vakje. */
    private static LongOpenHashSet coarse(LongSet cells, int shift) {
        LongOpenHashSet coarse = new LongOpenHashSet();
        for (long cell : cells) {
            coarse.add(BlockPos.asLong(BlockPos.getX(cell) >> shift, BlockPos.getY(cell) >> shift, BlockPos.getZ(cell) >> shift));
        }
        return coarse;
    }

    /** Kubussen die samen precies deze vakjes dekken (op een raster van 2^shift blokken), in blokken van de wereld. */
    private static List<ZoneBox> mesh(LongSet cells, int shift) {
        List<ZoneBox> boxes = new ArrayList<>();
        LongOpenHashSet left = new LongOpenHashSet(cells);
        long[] sorted = left.toLongArray();
        it.unimi.dsi.fastutil.longs.LongArrays.quickSort(sorted, (a, b) -> {
            int c = Integer.compare(BlockPos.getY(a), BlockPos.getY(b));
            if (c == 0) {
                c = Integer.compare(BlockPos.getZ(a), BlockPos.getZ(b));
            }
            return c == 0 ? Integer.compare(BlockPos.getX(a), BlockPos.getX(b)) : c;
        });
        for (long seed : sorted) {
            if (!left.contains(seed)) {
                continue;
            }
            int x0 = BlockPos.getX(seed);
            int y0 = BlockPos.getY(seed);
            int z0 = BlockPos.getZ(seed);
            int x1 = x0;
            while (left.contains(BlockPos.asLong(x1 + 1, y0, z0))) {
                x1++;
            }
            int z1 = z0;
            while (rowFree(left, x0, x1, y0, z1 + 1)) {
                z1++;
            }
            int y1 = y0;
            while (slabFree(left, x0, x1, y1 + 1, z0, z1)) {
                y1++;
            }
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        left.remove(BlockPos.asLong(x, y, z));
                    }
                }
            }
            boxes.add(ZoneBox.of(new BlockPos(x0 << shift, y0 << shift, z0 << shift),
                    new BlockPos(((x1 + 1) << shift) - 1, ((y1 + 1) << shift) - 1, ((z1 + 1) << shift) - 1)));
        }
        return boxes;
    }

    private static boolean rowFree(LongOpenHashSet left, int x0, int x1, int y, int z) {
        for (int x = x0; x <= x1; x++) {
            if (!left.contains(BlockPos.asLong(x, y, z))) {
                return false;
            }
        }
        return true;
    }

    private static boolean slabFree(LongOpenHashSet left, int x0, int x1, int y, int z0, int z1) {
        for (int z = z0; z <= z1; z++) {
            if (!rowFree(left, x0, x1, y, z)) {
                return false;
            }
        }
        return true;
    }

    /** Elke kubus {@code margin} blokken groter naar alle kanten, en binnen {@code limit} (wat erbuiten valt, gaat weg). */
    public static List<ZoneBox> grow(List<ZoneBox> boxes, int margin, ZoneBox limit) {
        List<ZoneBox> grown = new ArrayList<>();
        for (ZoneBox box : boxes) {
            ZoneBox fitted = grown(box, margin).clamp(limit.min(), limit.max());
            if (fitted != null) {
                grown.add(fitted);
            }
        }
        return grown;
    }

    private static ZoneBox grown(ZoneBox box, int margin) {
        return ZoneBox.of(box.min().offset(-margin, -margin, -margin), box.max().offset(margin, margin, margin));
    }

    /**
     * Voegt kubussen samen tot er hooguit {@code max} zijn: steeds de twee waarvan de doos eromheen het minste erbij neemt
     * (en die {@code allowed} goedkeurt). Snel, ook met honderden kubussen: elke kubus onthoudt met welke andere hij het
     * goedkoopst samengaat, en na een samenvoeging rekenen we alleen opnieuw wat met die twee te maken had. Geeft false
     * als het niet lukte (geen toegestane samenvoeging meer); dan staan er nog meer dan {@code max} in de lijst.
     */
    public static boolean mergeDown(List<ZoneBox> boxes, int max, Predicate<ZoneBox> allowed) {
        int n = boxes.size();
        if (n <= max) {
            return true;
        }
        ZoneBox[] box = boxes.toArray(new ZoneBox[0]);
        boolean[] alive = new boolean[n];
        long[] bestCost = new long[n];
        int[] bestWith = new int[n];
        java.util.Arrays.fill(alive, true);
        // Paren die allowed afkeurde: we vragen het pas als een paar echt aan de beurt is (dat scheelt veel vragen).
        LongOpenHashSet forbidden = new LongOpenHashSet();
        for (int a = 0; a < n; a++) {
            best(a, box, alive, bestCost, bestWith, forbidden);
        }
        int count = n;
        while (count > max) {
            int a = -1;
            for (int i = 0; i < n; i++) {
                if (alive[i] && bestWith[i] >= 0 && (a < 0 || bestCost[i] < bestCost[a])) {
                    a = i;
                }
            }
            if (a < 0) {
                break; // niets mag nog samen
            }
            int b = bestWith[a];
            ZoneBox joined = union(box[a], box[b]);
            if (!allowed.test(joined)) {
                forbidden.add(pair(a, b));
                best(a, box, alive, bestCost, bestWith, forbidden);
                if (bestWith[b] == a) {
                    best(b, box, alive, bestCost, bestWith, forbidden);
                }
                continue;
            }
            int keep = Math.min(a, b);
            int drop = Math.max(a, b);
            box[keep] = joined;
            alive[drop] = false;
            count--;
            // Wat met de twee oude kubussen afgekeurd was, geldt niet meer (de nieuwe is anders): opnieuw vragen als het moet.
            for (it.unimi.dsi.fastutil.longs.LongIterator iterator = forbidden.iterator(); iterator.hasNext(); ) {
                long key = iterator.nextLong();
                int low = (int) (key >>> 32);
                int high = (int) key;
                if (low == keep || high == keep || low == drop || high == drop) {
                    iterator.remove();
                }
            }
            best(keep, box, alive, bestCost, bestWith, forbidden);
            for (int i = 0; i < n; i++) {
                if (!alive[i] || i == keep) {
                    continue;
                }
                if (bestWith[i] == keep || bestWith[i] == drop || bestWith[i] < 0) {
                    best(i, box, alive, bestCost, bestWith, forbidden);
                } else {
                    long cost = union(box[i], box[keep]).volume() - box[i].volume() - box[keep].volume();
                    if (cost < bestCost[i] || cost == bestCost[i] && keep < bestWith[i]) {
                        bestCost[i] = cost;
                        bestWith[i] = keep;
                    }
                }
            }
        }
        boxes.clear();
        for (int i = 0; i < n; i++) {
            if (alive[i]) {
                boxes.add(box[i]);
            }
        }
        return count <= max;
    }

    /** Een paar kubussen als één getal (de kleinste eerst), voor de lijst met afgekeurde paren. */
    private static long pair(int a, int b) {
        return ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xFFFFFFFFL);
    }

    /** Met welke andere kubus gaat kubus {@code a} het goedkoopst samen (geen afgekeurd paar)? -1 als met geen enkele. */
    private static void best(int a, ZoneBox[] box, boolean[] alive, long[] bestCost, int[] bestWith, LongOpenHashSet forbidden) {
        bestWith[a] = -1;
        bestCost[a] = Long.MAX_VALUE;
        for (int b = 0; b < box.length; b++) {
            if (b == a || !alive[b] || !forbidden.isEmpty() && forbidden.contains(pair(a, b))) {
                continue;
            }
            long cost = union(box[a], box[b]).volume() - box[a].volume() - box[b].volume();
            if (cost < bestCost[a]) {
                bestCost[a] = cost;
                bestWith[a] = b;
            }
        }
    }

    /** De kleinste kubus om twee kubussen heen. */
    static ZoneBox union(ZoneBox a, ZoneBox b) {
        return ZoneBox.of(new BlockPos(Math.min(a.min().getX(), b.min().getX()), Math.min(a.min().getY(), b.min().getY()),
                Math.min(a.min().getZ(), b.min().getZ())), new BlockPos(Math.max(a.max().getX(), b.max().getX()),
                Math.max(a.max().getY(), b.max().getY()), Math.max(a.max().getZ(), b.max().getZ())));
    }

    // ================================================================ In de zone zetten

    /**
     * Wat {@link #apply} deed: een korte zin, en hoe je zone er nu bij staat. Samen passen ze meestal op één regel; de
     * actiebalk van de Zone Marker laat de stand weg als het te lang wordt (hoofdstuk 116: geen tekst buiten je scherm).
     *
     * @param changed veranderde de zone (dan kan Undo scan)
     */
    public record Applied(String summary, String zone, boolean changed) {
        /** Zo lang mag een regel in de actiebalk hooguit zijn (bij GUI-schaal 4 op 1080p past dat net). */
        private static final int SHORT = 75;

        public String text() {
            return summary + " · " + zone;
        }

        public String shortText() {
            return text().length() <= SHORT ? text() : summary;
        }
    }

    /**
     * Zet de gevonden kubussen in de zone van {@code core}. Elke kubus komt van een kubus zonder marge ({@code roomBoxes})
     * met de marge eromheen; raakt hij zo een andere zone, dan proberen we de halve marge en daarna geen marge (een
     * vraag van hoofdstuk 117: kernen mogen dicht bij elkaar staan). Een kubus die al helemaal in de zone ligt slaan we
     * over, een oude kubus die helemaal in een nieuwe ligt gaat weg (zo kun je gerust nog eens scannen). Worden het te
     * veel kubussen, dan voegen we samen, maar nooit tot een kubus die een andere zone raakt; lukt dat niet, dan vallen
     * de nieuwste kubussen af.
     */
    public static Applied apply(com.senne.imttech.security.SecurityCoreBlockEntity core, Result result, int maxBoxes) {
        List<ZoneBox> before = List.copyOf(core.zoneBoxes());
        List<ZoneBox> boxes = new ArrayList<>(before);
        int margin = margin();
        int added = 0;
        int blocked = 0;
        int replaced = 0;
        for (ZoneBox room : result.roomBoxes()) {
            ZoneBox fitted = null;
            boolean anywhere = false;
            for (int m : new int[] {margin, margin / 2, 0}) {
                ZoneBox candidate = grown(room, m).clamp(core.zoneLow(), core.zoneHigh());
                if (candidate == null) {
                    continue;
                }
                anywhere = true;
                if (!core.zoneBoxBlocked(candidate)) {
                    fitted = candidate;
                    break;
                }
            }
            if (fitted == null) {
                if (anywhere) {
                    blocked++;
                }
                continue;
            }
            ZoneBox found = fitted;
            if (boxes.stream().anyMatch(old -> old.contains(found))) {
                continue;
            }
            int count = boxes.size();
            boxes.removeIf(found::contains);
            replaced += count - boxes.size();
            boxes.add(found);
            added++;
        }
        if (!mergeDown(boxes, maxBoxes, box -> !core.zoneBoxBlocked(box))) {
            while (boxes.size() > maxBoxes) {
                boxes.removeLast();
                added = Math.max(0, added - 1);
                blocked++;
            }
        }
        List<ZoneBox> fitted = added > 0 || replaced > 0 ? core.setZoneBoxes(boxes) : before;
        long volume = ZoneData.unionVolume(fitted);
        String kind = result.cave() ? "rooms + cave" : result.enclosed() ? "rooms" : "building";
        StringBuilder summary = new StringBuilder("Autoscan (" + kind + "): ");
        if (added == 0) {
            summary.append(blocked == 0 ? "nothing new, your zone already covers it" : "nothing added");
        } else {
            summary.append(added).append(added == 1 ? " box added" : " boxes added");
        }
        if (replaced > 0) {
            summary.append(", ").append(replaced).append(" replaced");
        }
        if (blocked > 0) {
            summary.append(", ").append(blocked).append(" too close to another core");
        }
        String zone = "zone: " + fitted.size() + (fitted.size() == 1 ? " box, " : " boxes, ") + String.format("%,d", volume) + " blocks";
        core.logEvent(com.senne.imttech.security.os.EventLog.Type.ZONE, "Zone: autoscan, " + added + " added, " + replaced + " replaced");
        return new Applied(summary.toString(), zone, !fitted.equals(before));
    }
}
