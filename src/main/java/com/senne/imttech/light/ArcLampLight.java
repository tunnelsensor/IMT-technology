package com.senne.imttech.light;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LightEngine;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Het licht van de Arc Lamp: lichtniveau 100, in een spel dat bij 15 ophoudt (jouw wens, 9 oktober).
 *
 * <h2>Hoe licht in Minecraft werkt</h2>
 * Elke plek in de wereld heeft een lichtgetal van 0 tot 15. Een lichtbron geeft zijn eigen plek een
 * getal (een fakkel 14, gloeisteen 15), en elke stap verder is het 1 minder; door water of bladeren iets
 * meer, door een muur helemaal niets. Meer dan 15 kan niet: het spel bewaart het getal in 4 bits per blok.
 * Na 15 stappen is het dus altijd donker.
 *
 * <h2>Hoe we er 100 van maken</h2>
 * <ol>
 *   <li>We meten zelf waar het licht van de lamp zou komen als hij 100 kon geven (zie {@link LampSurvey}):
 *       elke plek die de lamp in een rechte lijn kan zien, tot 85 stappen ver. Om hoeken en door muren
 *       of dichte deuren gaat het niet: elke kamer heeft zijn eigen lamp nodig (13 oktober). Die plekken
 *       onthouden we.</li>
 *   <li>Op al die plekken doen we alsof er een onzichtbare lichtbron van 15 staat. Het spel vraagt bij
 *       het rekenen aan elk blok "hoeveel licht geef jij?"; via een mixin (mixin/BlockLightEngineMixin)
 *       zeggen wij dan 15 voor die plekken ({@link #emission}).</li>
 *   <li>Na die 85 stappen doet het spel de rest zelf: 14, 13, ... tot 0 op 100 stappen. Zo dooft het licht
 *       zacht uit, precies zoals een lamp van 100 zou doen.</li>
 * </ol>
 * Omdat het "gewoon" licht van het spel is, werkt alles vanzelf: je ziet het (ook je vrienden op de
 * server), monsters spawnen er niet, en planten groeien erbij.
 *
 * <h2>Wat erbij komt kijken</h2>
 * <ul>
 *   <li><b>Aan en uit.</b> Gaat een lamp aan of uit, dan moet het spel al die plekken opnieuw uitrekenen.
 *       Dat zijn er honderdduizenden: die geven we in één keer door aan het licht-rekenwerk van het spel
 *       ({@link #drainChecks}, via mixin/LightEngineMixin), dat op een eigen thread draait.</li>
 *   <li><b>Bouwen.</b> Bouw je een muur in het licht, dan moet erachter schaduw komen. Daarom meten we na
 *       elke blokverandering in de buurt opnieuw ({@link #blockChanged}), als het even stil is.</li>
 *   <li><b>Onthouden.</b> De laatste meting bewaren we. Zet je de lamp met de schakelaar weer aan en is er
 *       niets veranderd, dan is het licht er meteen. De eerste keer duurt het een halve seconde (de lamp
 *       "warmt op", net als een echte booglamp).</li>
 *   <li><b>Ver weg.</b> Licht wordt met de chunk bewaard. Gaat de lamp uit terwijl een deel van zijn licht
 *       in een niet-geladen chunk ligt, dan ruimen we dat op zodra die chunk weer laadt (zie {@link LightDebt}).</li>
 *   <li><b>Je eigen spel.</b> De server stuurt lichtveranderingen normaal niet naar je spel (dat rekent
 *       bij een blokverandering zelf), en ons licht verandert zonder blokverandering. Daarom sturen we het
 *       licht van de lamp zelf naar iedereen die het gebied ziet, zodra de server het heeft uitgerekend
 *       ({@link #sendWhenDone}). Rekent je spel daarna zelf iets na (je zet een fakkel in het licht), dan
 *       houdt het een plek met 15 op 15 zolang er een brandende lamp in de buurt is; anders zou je spel
 *       een donker vlekje tekenen dat de server niet heeft.</li>
 * </ul>
 */
public final class ArcLampLight {
    private ArcLampLight() {}

    /** Na een blokverandering wachten we zo lang of er nog meer komt, voordat we opnieuw meten (ticks). */
    private static final int QUIET = 10;
    /** ... maar langer dan dit wachten we niet (voor iemand die blijft bouwen). */
    private static final int MAX_WAIT = 60;
    /** Een chunk van de schuldlijst kijken we pas na als hij zo lang geladen is (dan staat zijn licht klaar). */
    private static final int SWEEP_DELAY = 60;
    /** Zoveel schuld-chunks per keer nakijken (om de 5 ticks). */
    private static final int SWEEPS_PER_ROUND = 4;

    private static final Long2ObjectMap<long[]> NOTHING = Long2ObjectMaps.emptyMap();

    /**
     * Per wereld (overworld, nether... en de wereld van je eigen spel): alles hieronder. Deze lijst wordt
     * nooit aangepast, alleen vervangen (de licht-thread leest hem tegelijk, zie {@link #emission}).
     */
    private static volatile Map<Level, LevelLight> worlds = new IdentityHashMap<>();

    /** Alles van één wereld. */
    private static final class LevelLight {
        final Level level;
        /** De lampen in deze wereld (alleen op de server). */
        final Map<BlockPos, Lamp> lamps = new HashMap<>();
        /**
         * Waar de lampen nu licht geven: per sectie (16x16x16 blokken, zie SectionPos) een rij van 4096
         * bits, 1 = hier staat een "onzichtbare lichtbron". Wordt nooit aangepast, alleen vervangen: de
         * licht-thread van het spel leest hem terwijl de server rekent.
         */
        volatile Long2ObjectMap<long[]> lit = NOTHING;
        /** Metingen die klaar zijn (van de achtergrond-thread), nog toe te passen op de server. */
        final ConcurrentLinkedQueue<Survey> done = new ConcurrentLinkedQueue<>();
        /** Plekken die het licht van het spel opnieuw moet uitrekenen (de licht-thread haalt ze hier op). */
        final ConcurrentLinkedQueue<long[]> checks = new ConcurrentLinkedQueue<>();
        /** Alleen in je eigen spel: de lampen die branden (zie {@link #clientLamp}). */
        volatile List<BlockPos> litLamps = List.of();

        LevelLight(Level level) {
            this.level = level;
        }

        boolean isLit(long pos) {
            long[] bits = lit.get(SectionPos.blockToSection(pos));
            if (bits == null) {
                return false;
            }
            int index = LampSurvey.index(pos);
            return (bits[index >>> 6] & 1L << (index & 63)) != 0;
        }

        /** Je eigen spel: een plek met 15, dicht bij een lamp die brandt (zie de uitleg bovenaan). */
        boolean clientLit(BlockPos pos) {
            List<BlockPos> lamps = litLamps;
            if (lamps.isEmpty()) {
                return false;
            }
            int reach = maxReach();
            for (BlockPos lamp : lamps) {
                if (lamp.distManhattan(pos) <= reach) {
                    return level.getBrightness(LightLayer.BLOCK, pos) >= LightEngine.MAX_LEVEL;
                }
            }
            return false;
        }
    }

    /** Eén lamp, zoals wij hem bijhouden. */
    private static final class Lamp {
        final BlockPos pos;
        /** Wil hij licht geven (heeft hij stroom)? */
        boolean on;
        /** Hoe ver zijn licht vol is: zijn lichtniveau min 15 (0 = een gewone lamp, zonder meting). */
        int reach;
        /** Zijn bereik is veranderd: de vorige meting telt niet meer, ook niet in chunks die niet geladen zijn. */
        boolean fresh;
        /** De plekken van zijn laatste meting (bits per sectie), of null als hij nog nooit gemeten is. */
        @Nullable Long2ObjectMap<long[]> region;
        /** Staan zijn plekken nu in {@link LevelLight#lit}? */
        boolean shown;
        /** Er veranderde iets binnen bereik sinds de laatste meting. */
        boolean dirty = true;
        /** De laatste verandering, en de eerste sinds de vorige meting (in ticks van de wereld). */
        long changedAt;
        long dirtyAt;
        /** Telt de metingen; komt er een oude binnen, dan gooien we die weg. */
        int generation;
        boolean measuring;

        Lamp(BlockPos pos) {
            this.pos = pos;
        }
    }

    /** Een meting die klaar is: wat hij vond, of wat er misging. */
    private record Survey(BlockPos pos, int generation, @Nullable Long2ObjectMap<long[]> region, @Nullable Throwable error) {}

    /** Hoe ver het licht van een lamp op dit lichtniveau vol is: het lichtniveau min 15 (85 bij 100, 0 tot 15). */
    public static int reachOf(int lightLevel) {
        return Math.max(0, Mth.clamp(lightLevel, 1, Math.round(ImtConfig.Lights.ARC_LAMP_MAX_LEVEL)) - LightEngine.MAX_LEVEL);
    }

    /** Het bereik van een lamp op het hoogste lichtniveau dat de config toestaat. */
    public static int maxReach() {
        return reachOf(Math.round(ImtConfig.Lights.ARC_LAMP_MAX_LEVEL));
    }

    // ================================================================ Voor het licht-rekenwerk (mixins)

    /**
     * Hoeveel licht geeft deze plek extra, van een Arc Lamp? 15 of 0. Het spel vraagt dit voor elk blok
     * dat het narekent, op zijn eigen licht-thread: daarom alleen lezen, en snel (zonder lampen meteen 0).
     */
    public static int emission(BlockGetter getter, BlockPos pos) {
        Map<Level, LevelLight> all = worlds;
        if (all.isEmpty()) {
            return 0;
        }
        LevelLight light = all.get(getter);
        if (light == null) {
            return 0;
        }
        boolean lit = light.level.isClientSide() ? light.clientLit(pos) : light.isLit(pos.asLong());
        return lit ? LightEngine.MAX_LEVEL : 0;
    }

    /** Het licht-rekenwerk begint een ronde: geef het de plekken die het opnieuw moet bekijken. */
    public static void drainChecks(BlockGetter getter, LongSet into) {
        Map<Level, LevelLight> all = worlds;
        if (all.isEmpty()) {
            return;
        }
        LevelLight light = all.get(getter);
        if (light == null) {
            return;
        }
        long[] batch;
        while ((batch = light.checks.poll()) != null) {
            for (long pos : batch) {
                into.add(pos);
            }
        }
    }

    // ================================================================ De lamp meldt zich (server)

    /**
     * Hoe staat de lamp ervoor (elke tick, zie ArcLampBlockEntity): brandt hij, en hoe ver reikt zijn licht
     * (zie {@link #reachOf}). Er gebeurt alleen iets als er iets veranderde, of als hij zich voor het eerst
     * meldt (na het neerzetten of het laden).
     */
    public static void update(ServerLevel level, BlockPos pos, boolean on, int reach) {
        LevelLight light = light(level);
        Lamp lamp = light.lamps.get(pos);
        long now = level.getGameTime();
        if (lamp == null) {
            lamp = new Lamp(pos.immutable());
            lamp.changedAt = lamp.dirtyAt = now;
            lamp.reach = reach;
            lamp.on = !on; // zodat hieronder "aan" of "uit" gebeurt
            light.lamps.put(lamp.pos, lamp);
        }
        if (lamp.reach != reach) {
            // Je zette hem feller of zachter: opnieuw meten (zodra je even stopt met schuiven). Brandt hij,
            // dan blijft tot dan zijn oude licht staan; brandt hij niet, dan is zijn oude meting niets meer waard.
            lamp.reach = reach;
            lamp.fresh = true;
            if (!lamp.shown) {
                lamp.region = null;
            }
            dirty(lamp, now);
        }
        if (lamp.on != on) {
            lamp.on = on;
            if (on) {
                // Het vorige licht meteen (de schakelaar weer aan); is er intussen iets veranderd, dan meten
                // we zo meteen opnieuw (zie onServerTick).
                show(light, lamp);
            } else {
                hide(level, light, lamp);
            }
        }
    }

    /** De lamp is weg (afgebroken, opgeblazen): zijn licht ook. */
    public static void removed(ServerLevel level, BlockPos pos) {
        LevelLight light = worlds.get(level);
        if (light == null) {
            return;
        }
        Lamp lamp = light.lamps.get(pos);
        if (lamp == null) {
            return;
        }
        lamp.generation++; // een meting die nog loopt, telt niet meer
        hide(level, light, lamp);
        light.lamps.remove(pos);
    }

    /** Is deze lamp aan het meten (de eerste keer, of na een verbouwing)? Voor de statusregel. */
    public static boolean measuring(Level level, BlockPos pos) {
        LevelLight light = worlds.get(level);
        Lamp lamp = light == null ? null : light.lamps.get(pos);
        return lamp != null && lamp.on && (lamp.measuring || !lamp.shown);
    }

    // ================================================================ Je eigen spel

    /** In je eigen spel: deze lamp brandt (of niet meer). Zie {@link LevelLight#clientLit}. */
    public static void clientLamp(Level level, BlockPos pos, boolean lit) {
        LevelLight light = lit ? light(level) : worlds.get(level);
        if (light == null || light.litLamps.contains(pos) == lit) {
            return;
        }
        List<BlockPos> next = new ArrayList<>(light.litLamps);
        if (lit) {
            next.add(pos.immutable());
        } else {
            next.remove(pos);
        }
        light.litLamps = List.copyOf(next);
    }

    // ================================================================ Veranderingen in de wereld

    /**
     * Een blok veranderde (zie mixin/LevelChunkMixin). Houdt het nieuwe blok licht anders tegen dan het
     * oude (een muur waar lucht was, glas waar een muur was, een deur die opengaat), dan meten we de
     * lampen in de buurt opnieuw.
     */
    public static void blockChanged(Level level, BlockPos pos, BlockState before, BlockState after) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        LevelLight light = worlds.get(level);
        if (light == null || light.lamps.isEmpty() || !blocksDifferently(level, pos, before, after)) {
            return;
        }
        long now = level.getGameTime();
        for (Lamp lamp : light.lamps.values()) {
            if (lamp.reach > 0 && !lamp.pos.equals(pos) && lamp.pos.distManhattan(pos) <= lamp.reach + 1) {
                dirty(lamp, now);
            }
        }
    }

    /**
     * Houdt het nieuwe blok licht anders tegen dan het oude? (Of is een van de twee een plaat of trap, die
     * maar aan één kant dicht is.) Een deur of luik dat open- of dichtgaat telt ook: voor de lamp is een
     * dichte deur een muur (zie LampSurvey.stopsBeam). Alleen dan kan de meting anders uitkomen. Een fakkel
     * neerzetten of een andere lamp die aangaat verandert alleen hoeveel licht een blok zelf geeft: dat
     * maakt voor de meting niets uit.
     */
    private static boolean blocksDifferently(Level level, BlockPos pos, BlockState before, BlockState after) {
        return before.getLightBlock(level, pos) != after.getLightBlock(level, pos)
                || before.useShapeForLightOcclusion() || after.useShapeForLightOcclusion()
                || LampSurvey.closedDoor(before) != LampSurvey.closedDoor(after);
    }

    /** Een chunk is geladen: de lampen die erbij kunnen, meten opnieuw (nu zien ze meer). */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LevelLight light = worlds.get(level);
        if (light == null || light.lamps.isEmpty()) {
            return;
        }
        ChunkPos chunk = event.getChunk().getPos();
        long now = level.getGameTime();
        for (Lamp lamp : light.lamps.values()) {
            int dx = Math.abs(chunk.getMiddleBlockX() - lamp.pos.getX());
            int dz = Math.abs(chunk.getMiddleBlockZ() - lamp.pos.getZ());
            if (lamp.reach > 0 && dx + dz <= lamp.reach + 16) {
                dirty(lamp, now);
            }
        }
    }

    private static void dirty(Lamp lamp, long now) {
        if (!lamp.dirty) {
            lamp.dirty = true;
            lamp.dirtyAt = now;
        }
        lamp.changedAt = now;
    }

    // ================================================================ Elke tick (server)

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LevelLight light = worlds.get(level);
            if (light != null) {
                tick(level, light);
            }
            if (level.getGameTime() % 5 == 0) {
                sweepDebts(level);
            }
        }
    }

    private static void tick(ServerLevel level, LevelLight light) {
        Survey survey;
        while ((survey = light.done.poll()) != null) {
            apply(level, light, survey);
        }
        long now = level.getGameTime();
        for (Lamp lamp : light.lamps.values()) {
            if (lamp.on && lamp.dirty && !lamp.measuring && level.isLoaded(lamp.pos)
                    && (now - lamp.changedAt >= QUIET || now - lamp.dirtyAt >= MAX_WAIT)) {
                startSurvey(level, light, lamp);
            }
        }
    }

    /** Begin een meting: klaarzetten op de server, rekenen op een achtergrond-thread. */
    private static void startSurvey(ServerLevel level, LevelLight light, Lamp lamp) {
        lamp.dirty = false;
        lamp.measuring = true;
        int generation = ++lamp.generation;
        BlockPos pos = lamp.pos;
        Long2ObjectMap<long[]> previous = lamp.fresh ? null : lamp.region;
        lamp.fresh = false;
        if (lamp.reach <= 0) {
            // Tot lichtniveau 15 is het een gewone lamp: het blok geeft zelf licht, en er hoeven geen
            // onzichtbare lichtbronnen bij. Deze "meting" is dus meteen klaar: niets.
            light.done.add(new Survey(pos, generation, new Long2ObjectOpenHashMap<>(), null));
            return;
        }
        LampSurvey survey = LampSurvey.prepare(level, pos, lamp.reach, previous);
        CompletableFuture.supplyAsync(survey::run, Util.backgroundExecutor())
                .whenComplete((region, error) -> light.done.add(new Survey(pos, generation, region, error)));
    }

    /** Een meting is klaar: onthouden, en brandt de lamp, dan het licht bijwerken. */
    private static void apply(ServerLevel level, LevelLight light, Survey survey) {
        Lamp lamp = light.lamps.get(survey.pos());
        if (lamp == null || lamp.generation != survey.generation()) {
            return; // de lamp is weg, of er loopt al een nieuwere meting
        }
        lamp.measuring = false;
        if (survey.error() != null || survey.region() == null) {
            ImtTech.LOGGER.warn("[arc lamp] Meting bij {} mislukt; zo meteen opnieuw", survey.pos(), survey.error());
            dirty(lamp, level.getGameTime());
            return;
        }
        Long2ObjectMap<long[]> before = lamp.shown ? lamp.region : null;
        lamp.region = survey.region();
        if (lamp.on) {
            LongOpenHashSet sections = new LongOpenHashSet(lamp.region.keySet());
            if (before != null) {
                sections.addAll(before.keySet());
            }
            lamp.shown = true;
            republish(light, sections, lamp.pos);
        }
    }

    /** Zet de plekken van deze lamp aan (als hij er al een meting van heeft). */
    private static void show(LevelLight light, Lamp lamp) {
        if (lamp.shown || lamp.region == null) {
            return;
        }
        lamp.shown = true;
        republish(light, lamp.region.keySet(), lamp.pos);
    }

    /** Zet de plekken van deze lamp uit, en zet zijn chunks op de schuldlijst (zie LightDebt). */
    private static void hide(ServerLevel level, LevelLight light, Lamp lamp) {
        if (!lamp.shown || lamp.region == null) {
            lamp.shown = false;
            return;
        }
        lamp.shown = false;
        republish(light, lamp.region.keySet(), lamp.pos);
        LightDebt debt = LightDebt.get(level);
        for (long section : lamp.region.keySet()) {
            debt.add(ChunkPos.asLong(SectionPos.x(section), SectionPos.z(section)));
        }
    }

    /**
     * Rekent opnieuw uit welke plekken licht geven in deze secties (van alle lampen samen), zet dat klaar
     * voor de licht-thread, en geeft het spel de plekken door die veranderden.
     *
     * @param trigger een plek in een geladen chunk: daar "porren" we het licht-rekenwerk, zodat het
     *                meteen een ronde doet (en dan onze plekken ophaalt)
     */
    private static void republish(LevelLight light, LongSet sections, BlockPos trigger) {
        Long2ObjectMap<long[]> before = light.lit;
        Long2ObjectOpenHashMap<long[]> after = new Long2ObjectOpenHashMap<>(before);
        LongArrayList changed = new LongArrayList();
        LongOpenHashSet changedSections = new LongOpenHashSet();
        LayerLightEventListener stored = light.level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        for (long section : sections) {
            long[] now = null;
            for (Lamp lamp : light.lamps.values()) {
                long[] bits = lamp.shown && lamp.region != null ? lamp.region.get(section) : null;
                if (bits != null) {
                    now = or(now, bits);
                }
            }
            if (now == null) {
                after.remove(section);
            } else {
                after.put(section, now);
            }
            int count = changed.size();
            long[] was = before.get(section);
            DataLayer layer = stored.getDataLayerData(SectionPos.of(section));
            collectChanges(layer, section, was, now, changed);
            if (changed.size() > count) {
                changedSections.add(section);
            }
            if (layer == null && was != null && light.level instanceof ServerLevel level && lostSome(was, now)) {
                // Hier gaan plekken uit, maar de chunk is niet geladen: daar ruimen we op als hij laadt.
                LightDebt.get(level).add(ChunkPos.asLong(SectionPos.x(section), SectionPos.z(section)));
            }
        }
        light.lit = after; // vanaf nu ziet de licht-thread het nieuwe licht
        ImtTech.LOGGER.debug("[arc lamp] {} secties bijgewerkt, {} plekken opnieuw na te rekenen", sections.size(), changed.size());
        if (!changed.isEmpty() && light.level instanceof ServerLevel level) {
            light.checks.add(changed.toLongArray());
            level.getChunkSource().getLightEngine().checkBlock(trigger);
            sendWhenDone(level, changedSections, trigger);
        }
    }

    /**
     * Stuurt het nieuwe licht naar de spelers, zodra het licht-rekenwerk ermee klaar is.
     *
     * Waarom dat nodig is: sinds Minecraft 1.20 rekent je eigen spel het licht zelf uit als er een blok
     * verandert, en stuurt de server lichtveranderingen alleen nog naar spelers voor wie die chunk aan de
     * rand van hun zicht ligt. Ons licht verandert zonder dat er een blok verandert: je spel zou het dus
     * nooit te zien krijgen (de server wist het wel). Daarom sturen we het hier zelf: voor elke chunk met
     * een veranderde sectie het licht van die secties en de secties eromheen (daar dooft het licht uit),
     * naar iedereen die die chunk ziet.
     */
    private static void sendWhenDone(ServerLevel level, LongSet sections, BlockPos trigger) {
        int minSection = level.getLightEngine().getMinLightSection();
        int count = level.getLightEngine().getLightSectionCount();
        Long2ObjectOpenHashMap<java.util.BitSet> perChunk = new Long2ObjectOpenHashMap<>();
        for (long section : sections) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    java.util.BitSet bits = perChunk.computeIfAbsent(
                            ChunkPos.asLong(SectionPos.x(section) + dx, SectionPos.z(section) + dz), key -> new java.util.BitSet());
                    for (int dy = -1; dy <= 1; dy++) {
                        int index = SectionPos.y(section) + dy - minSection;
                        if (index >= 0 && index < count) {
                            bits.set(index);
                        }
                    }
                }
            }
        }
        // "Klaar" = een klusje dat het licht-rekenwerk pas doet ná onze plekken (zelfde chunk als het porren).
        ChunkPos chunk = new ChunkPos(trigger);
        level.getChunkSource().getLightEngine().waitForPendingTasks(chunk.x, chunk.z)
                .thenRun(() -> level.getServer().execute(() -> send(level, perChunk)));
    }

    private static void send(ServerLevel level, Long2ObjectMap<java.util.BitSet> perChunk) {
        for (Long2ObjectMap.Entry<java.util.BitSet> entry : perChunk.long2ObjectEntrySet()) {
            ChunkPos pos = new ChunkPos(entry.getLongKey());
            if (!level.hasChunk(pos.x, pos.z)) {
                continue;
            }
            List<net.minecraft.server.level.ServerPlayer> players = level.getChunkSource().chunkMap.getPlayers(pos, false);
            if (players.isEmpty()) {
                continue;
            }
            net.minecraft.network.protocol.game.ClientboundLightUpdatePacket packet =
                    new net.minecraft.network.protocol.game.ClientboundLightUpdatePacket(
                            pos, level.getLightEngine(), new java.util.BitSet(), entry.getValue());
            for (net.minecraft.server.level.ServerPlayer player : players) {
                player.connection.send(packet);
            }
        }
    }

    /**
     * De plekken in deze sectie die veranderden. Alleen wat er echt toe doet: een plek die erbij komt en
     * nog geen 15 heeft, of een plek die eraf gaat en nu 15 heeft. (Na het laden van de wereld staat het
     * licht er bijvoorbeeld al: dan hoeft het spel niets na te rekenen.)
     */
    private static void collectChanges(@Nullable DataLayer layer, long section, @Nullable long[] was,
                                       @Nullable long[] now, LongArrayList out) {
        if (layer == null || was == now) {
            return; // hier bewaart het spel geen licht (of de chunk is niet geladen), of er veranderde niets
        }
        int baseX = SectionPos.sectionToBlockCoord(SectionPos.x(section));
        int baseY = SectionPos.sectionToBlockCoord(SectionPos.y(section));
        int baseZ = SectionPos.sectionToBlockCoord(SectionPos.z(section));
        for (int word = 0; word < 64; word++) {
            long a = was == null ? 0L : was[word];
            long b = now == null ? 0L : now[word];
            long diff = a ^ b;
            while (diff != 0L) {
                int bit = Long.numberOfTrailingZeros(diff);
                diff &= diff - 1;
                int index = word << 6 | bit;
                int x = index & 15;
                int z = index >> 4 & 15;
                int y = index >> 8;
                boolean litNow = (b & 1L << bit) != 0L;
                int level = layer.get(x, y, z);
                if (litNow ? level < LightEngine.MAX_LEVEL : level >= LightEngine.MAX_LEVEL) {
                    out.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                }
            }
        }
    }

    /** Staat er in {@code was} een plek die niet in {@code now} staat? */
    private static boolean lostSome(long[] was, @Nullable long[] now) {
        for (int i = 0; i < 64; i++) {
            if ((was[i] & ~(now == null ? 0L : now[i])) != 0L) {
                return true;
            }
        }
        return false;
    }

    /** Twee rijen bits samen (zonder er een aan te passen: die zijn misschien gedeeld). */
    private static long[] or(@Nullable long[] a, long[] b) {
        if (a == null || a == b) {
            return b;
        }
        if (a == LampSurvey.FULL || b == LampSurvey.FULL) {
            return LampSurvey.FULL;
        }
        long[] both = new long[64];
        boolean full = true;
        for (int i = 0; i < 64; i++) {
            both[i] = a[i] | b[i];
            full &= both[i] == -1L;
        }
        return full ? LampSurvey.FULL : both;
    }

    // ================================================================ Opruimen (zie LightDebt)

    /**
     * Kijkt chunks van de schuldlijst na die al even geladen zijn: hangt er licht van 15 zonder dat daar een
     * lichtbron staat (geen lamp, geen gloeisteen), dan laat het spel die plekken opnieuw uitrekenen.
     */
    private static void sweepDebts(ServerLevel level) {
        LightDebt debt = LightDebt.get(level);
        if (debt.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        LongArrayList finished = new LongArrayList();
        for (long chunk : debt.chunks()) {
            int x = ChunkPos.getX(chunk);
            int z = ChunkPos.getZ(chunk);
            if (!level.hasChunk(x, z)) {
                debt.loadedSince.remove(chunk);
                continue;
            }
            long since = debt.loadedSince.containsKey(chunk) ? debt.loadedSince.get(chunk) : now;
            debt.loadedSince.put(chunk, since);
            if (now - since >= SWEEP_DELAY && finished.size() < SWEEPS_PER_ROUND) {
                sweep(level, x, z);
                finished.add(chunk);
            }
        }
        for (long chunk : finished) {
            debt.remove(chunk);
            debt.loadedSince.remove(chunk);
        }
    }

    private static void sweep(ServerLevel level, int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return;
        }
        LevelLight light = worlds.get(level);
        LayerLightEventListener stored = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        LongArrayList stale = new LongArrayList();
        for (int i = 0; i < sections.length; i++) {
            int sectionY = chunk.getSectionYFromSectionIndex(i);
            DataLayer layer = stored.getDataLayerData(SectionPos.of(chunkX, sectionY, chunkZ));
            if (layer == null || layer.isEmpty()) {
                continue;
            }
            for (int index = 0; index < 4096; index++) {
                int x = index & 15;
                int z = index >> 4 & 15;
                int y = index >> 8;
                if (layer.get(x, y, z) < LightEngine.MAX_LEVEL) {
                    continue;
                }
                long pos = BlockPos.asLong((chunkX << 4) + x, (sectionY << 4) + y, (chunkZ << 4) + z);
                if ((light != null && light.isLit(pos))
                        || sections[i].getBlockState(x, y, z).getLightEmission(level, at.set(pos)) >= LightEngine.MAX_LEVEL) {
                    continue; // hier hoort het licht: een brandende lamp, of een echte lichtbron
                }
                stale.add(pos);
            }
        }
        if (!stale.isEmpty()) {
            light(level).checks.add(stale.toLongArray());
            BlockPos trigger = chunk.getPos().getMiddleBlockPosition(level.getMinBuildHeight());
            level.getChunkSource().getLightEngine().checkBlock(trigger);
            LongOpenHashSet staleSections = new LongOpenHashSet();
            stale.forEach((long pos) -> staleSections.add(SectionPos.blockToSection(pos)));
            sendWhenDone(level, staleSections, trigger);
            ImtTech.LOGGER.debug("[arc lamp] {} plekken oud licht opgeruimd in chunk {} {}", stale.size(), chunkX, chunkZ);
        }
    }

    // ================================================================ Wereld dicht

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            forget(level);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            forget(level);
        }
    }

    private static void forget(Level level) {
        synchronized (ArcLampLight.class) {
            if (worlds.containsKey(level)) {
                Map<Level, LevelLight> copy = new IdentityHashMap<>(worlds);
                copy.remove(level);
                worlds = copy;
            }
        }
    }

    private static LevelLight light(Level level) {
        LevelLight light = worlds.get(level);
        if (light != null) {
            return light;
        }
        synchronized (ArcLampLight.class) {
            light = worlds.get(level);
            if (light == null) {
                Map<Level, LevelLight> copy = new IdentityHashMap<>(worlds);
                light = new LevelLight(level);
                copy.put(level, light);
                worlds = copy;
            }
            return light;
        }
    }

    // ================================================================ Voor de tests

    /** Staat hier een "onzichtbare lichtbron" van een brandende lamp? */
    public static boolean isLit(Level level, BlockPos pos) {
        LevelLight light = worlds.get(level);
        return light != null && light.isLit(pos.asLong());
    }

    /** Is alles klaar: geen metingen meer bezig, en niets meer voor het licht-rekenwerk? */
    public static boolean settled(Level level) {
        LevelLight light = worlds.get(level);
        if (light == null) {
            return true;
        }
        if (!light.checks.isEmpty() || !light.done.isEmpty()) {
            return false;
        }
        for (Lamp lamp : light.lamps.values()) {
            if (lamp.measuring || lamp.on && lamp.dirty) {
                return false;
            }
        }
        return true;
    }
}
