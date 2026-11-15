package com.senne.imttech.security.zone;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Een zone blijft geladen, ook als er niemand in de buurt is (hoofdstuk 120, jouw wens van 15 november): "zorg dat een
 * zone ook chunkloaded is behalve de laatste range van 10 omdat dat toch geen base is, dit maakt dat als de securitycore
 * ver weg is het nog steeds werkt doordat alles loaded is".
 *
 * Minecraft laat alleen de wereld rond de spelers draaien. Ben je ver weg (of offline), dan staat je basis stil: je
 * torentjes, generatoren, machines, robots en drones doen niets meer. En staat je kern ver van je zone (een zone mag tot
 * 500 blokken van de kern liggen), dan vond een apparaat in je zone zijn kern niet meer: die kern lag in een stuk wereld
 * dat niet geladen was. Daarom houden we nu geladen:
 * <ul>
 *   <li><b>de zone</b>, behalve de buitenste rand van elke kubus: {@link ZoneScan#margin()} blokken (standaard 10, de rand
 *       die de Autoscan om je kamers legt: muur en marge, geen basis), of minder als de kubus daar te smal voor is (dan
 *       blijft haar midden geladen). Een kolom van een kubus telt als het vierkant van die rand naar alle kanten eromheen
 *       helemaal in de zone ligt (alle kubussen samen, van bovenaf gezien: zo blijft de naad tussen twee kamers gewoon
 *       geladen). Een stuk wereld (chunk, 16 x 16 blokken, de hele hoogte) blijft geladen als er zo'n kolom in ligt. Elk
 *       blok van de zone ligt zo in een geladen stuk of er direct naast (hooguit 10 blokken van een geladen kolom), en
 *       een stuk naast een geladen stuk draait half mee: blokken zoals torentjes en generatoren werken daar, alleen
 *       wezens (robots, drones) staan er stil. Ook een zone die de Autoscan vóór hoofdstuk 117 maakte (met 3 blokken rand)
 *       houdt zo haar kamers;</li>
 *   <li><b>het stuk wereld van de kern zelf</b> (ook als die buiten zijn zone staat), als eerste: zonder kern werkt niets
 *       in je zone. Pas als dat vastzit (en de kern er dus nog staat), komen de andere stukken;</li>
 *   <li><b>het netwerk van de kern</b>: de stukken waar zijn netwerkkabels door lopen (geladen, niet draaiend). Dat doet
 *       lan/NetworkKeeper, voor elke kern die hier in {@link #keptCores} staat.</li>
 * </ul>
 * Hooguit {@code zoneChunkLoadingMax} stukken per zone (standaard 256), die het dichtst bij de kern eerst.
 *
 * <b>Hoe.</b> Met de "ticket controller" van NeoForge: een ticket per stuk wereld, met de plek van de kern als eigenaar.
 * Minecraft bewaart die tickets in de wereld en zet ze bij het opstarten zelf terug, nog voor er een speler is; dan
 * vraagt het eerst aan ons welke nog mogen ({@link #validate}). Een ticket zonder "ticking" (zo maken wij ze): de
 * blokken en wezens in dat stuk draaien gewoon (net als met /forceload), maar gewassen groeien er niet harder van en
 * er komen geen monsters door (dat kostte veel rekenwerk voor niets).
 *
 * <b>Wanneer.</b> Een zone blijft geladen zolang ze een eigenaar en minstens één kubus heeft, ook zonder stroom (zo laden
 * je zonnepanelen een lege kern weer op terwijl je weg bent) en ook als de eigenaar offline is. Kern weg (gehakt), zone
 * leeg, of {@code zoneChunkLoading = false} in de config: binnen een seconde laten we alles los. Is de kern verdwenen
 * zonder dat hij afgebroken werd (een stuk wereld dat met een ander programma gewist werd), dan vergeten we zijn zone,
 * net als Zones.activeAt dat doet.
 *
 * <b>Zonder haperen.</b> Elke tick pakken we hooguit {@link #PER_TICK} stukken op. Een stuk dat nog niet geladen is,
 * laten we eerst op de achtergrond laden (met een tijdelijk ticket, zoals een speler die aankomt); pas als het er is,
 * zetten we het vast. Vastzetten van een stuk dat er nog niet is, laat de server namelijk wachten tot het van de schijf
 * komt (of, als het nog nooit bestond, tot het gemaakt is). En welke stukken bij een zone horen, rekenen we per zone
 * hooguit één keer per seconde opnieuw uit (wie snel op + en - klikt in de app Zone, laat de server zo niet haperen).
 */
public final class ZoneChunks {
    private ZoneChunks() {}

    /** De "ticket controller" waarmee we zones geladen houden (ImtTech meldt hem aan bij NeoForge). */
    public static final TicketController CONTROLLER =
            new TicketController(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "zone"), ZoneChunks::validate);

    /** Zo vaak (ticks) kijken we sowieso of alles klopt, en zo vaak rekenen we een zone hooguit opnieuw uit. */
    private static final int EVERY = 20;
    /** Zoveel stukken wereld pakken we hooguit per tick op, zodat de server nooit hapert. */
    static final int PER_TICK = 64;

    /** Het tijdelijke ticket dat een stuk wereld op de achtergrond laat laden voor we het vastzetten (5 seconden geldig). */
    private static final TicketType<ChunkPos> LOADING =
            TicketType.create("imttech_zone_loading", Comparator.comparingLong(ChunkPos::toLong), 100);

    /** Voor welke server de lijsten hieronder gelden (in singleplayer kan de volgende wereld een andere server zijn). */
    private static @Nullable MinecraftServer server;
    /** Per dimensie, per kern: welke stukken wereld we nu vasthouden (met een ticket van {@link #CONTROLLER}). */
    private static final Map<ResourceKey<Level>, Map<BlockPos, LongLinkedOpenHashSet>> HELD = new HashMap<>();
    /** Per kern de laatst uitgerekende lijst: alleen opnieuw rekenen als de kubussen, de rand of het maximum veranderden. */
    private static final Map<GlobalPos, Wanted> WANTED = new HashMap<>();
    /** {@link ZoneData#changes()} bij de vorige ronde. */
    private static int lastChanges = -1;
    /** Lag er na de vorige ronde nog werk (stukken die nog geladen moesten worden)? Dan meteen de volgende tick verder. */
    private static boolean busy;
    /** Moest er in deze ronde een zone wachten met opnieuw rekenen (zie {@link #wantedFor})? Dan straks nog een ronde. */
    private static boolean deferred;

    /** Wat we voor een zone uitrekenden, waarmee, en wanneer (de tick van de server, zie {@link #wantedFor}). */
    private record Wanted(List<ZoneBox> boxes, int margin, int max, LongList chunks, int tick) {}

    // ================================================================ Elke tick

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer current = event.getServer();
        forServer(current);
        // Niet tijdens de automatische tests: daar bleven anders de zones van alle oude tests geladen, en dat verandert de
        // wereld van latere tests (zoals bij NetworkKeeper: een raiddrone die op het dak van een andere testarena landt).
        // De tests van dit hoofdstuk roepen syncNow zelf aan.
        if (current instanceof GameTestServer) {
            return;
        }
        ZoneData data = ZoneData.get(current);
        if (!busy && data.changes() == lastChanges && current.getTickCount() % EVERY != 0) {
            return;
        }
        lastChanges = data.changes();
        busy = step(current, data, PER_TICK);
    }

    /**
     * Eén ronde: wat niet meer geladen hoeft te blijven, laten we los (dat kost niets); wat erbij moet, pakken we op, de
     * belangrijkste eerst en hooguit {@code budget} stukken. Geeft terug of er nog werk ligt.
     */
    private static boolean step(MinecraftServer server, ZoneData data, int budget) {
        deferred = false;
        Set<GlobalPos> seen = new HashSet<>();
        Map<ServerLevel, Map<BlockPos, LongList>> wantedPerLevel = new LinkedHashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            Map<BlockPos, LongLinkedOpenHashSet> held = HELD.computeIfAbsent(level.dimension(), key -> new HashMap<>());
            Map<BlockPos, LongList> wanted = new LinkedHashMap<>();
            // Een kopie: keeps() kan een zone vergeten (een kern die weg is), en dat verandert de lijst van ZoneData.
            for (ZoneData.Zone zone : List.copyOf(data.in(level.dimension()))) {
                seen.add(zone.core());
                if (keeps(level, data, zone)) {
                    wanted.put(zone.core().pos(), wantedFor(zone, server.getTickCount(), false));
                }
            }
            wantedPerLevel.put(level, wanted);
            // Eerst loslaten, in alle dimensies (dat kost niets): kernen zonder zone (of waarvan de zone niet meer geladen
            // hoort te blijven), en stukken die er niet meer bij horen (een kleinere zone, een lager maximum).
            for (Iterator<Map.Entry<BlockPos, LongLinkedOpenHashSet>> it = held.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<BlockPos, LongLinkedOpenHashSet> entry = it.next();
                LongList want = wanted.get(entry.getKey());
                LongSet keep = want == null ? LongSet.of() : new LongOpenHashSet(want);
                release(level, entry.getKey(), entry.getValue(), keep);
                if (entry.getValue().isEmpty()) {
                    it.remove();
                }
            }
        }
        WANTED.keySet().retainAll(seen);
        // Dan erbij, per zone de belangrijkste eerst (de kern, dan wat er het dichtst bij ligt), hooguit budget stukken.
        boolean more = deferred;
        for (Map.Entry<ServerLevel, Map<BlockPos, LongList>> perLevel : wantedPerLevel.entrySet()) {
            ServerLevel level = perLevel.getKey();
            Map<BlockPos, LongLinkedOpenHashSet> held = HELD.computeIfAbsent(level.dimension(), key -> new HashMap<>());
            for (Map.Entry<BlockPos, LongList> entry : perLevel.getValue().entrySet()) {
                BlockPos core = entry.getKey();
                LongList want = entry.getValue();
                LongLinkedOpenHashSet have = held.get(core);
                for (int i = 0; i < want.size(); i++) {
                    long chunk = want.getLong(i);
                    if (have != null && have.contains(chunk)) {
                        continue;
                    }
                    // Eerst het stuk van de kern (dat staat altijd vooraan): pas als dat vastzit, en keeps() dus zag dat de
                    // kern er nog staat, de rest. Anders laadden we bij een verdwenen kern eerst nog zijn hele zone.
                    if (i > 0 && (have == null || !have.contains(want.getLong(0)))) {
                        break;
                    }
                    if (budget <= 0) {
                        return true;
                    }
                    budget--;
                    int x = ChunkPos.getX(chunk);
                    int z = ChunkPos.getZ(chunk);
                    if (level.getChunkSource().getChunkNow(x, z) == null) {
                        // Nog niet geladen: eerst op de achtergrond laten laden. Volgende tick kijken we weer.
                        ChunkPos pos = new ChunkPos(x, z);
                        level.getChunkSource().addRegionTicket(LOADING, pos, 0, pos);
                        more = true;
                        continue;
                    }
                    CONTROLLER.forceChunk(level, core, x, z, true, false);
                    if (have == null) {
                        have = new LongLinkedOpenHashSet();
                        held.put(core, have);
                    }
                    have.add(chunk);
                }
            }
        }
        return more;
    }

    /** Laat de stukken van deze kern los die niet in {@code keep} staan. */
    private static void release(ServerLevel level, BlockPos core, LongLinkedOpenHashSet have, LongSet keep) {
        LongIterator chunks = have.iterator();
        while (chunks.hasNext()) {
            long chunk = chunks.nextLong();
            if (!keep.contains(chunk)) {
                CONTROLLER.forceChunk(level, core, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false, false);
                chunks.remove();
            }
        }
    }

    /**
     * Moet deze zone nu geladen blijven? Ja, zolang ze een eigenaar en een kubus heeft en de config het wil. Is het stuk
     * wereld van de kern geladen, dan kijken we ook of de kern er nog staat. Weggehaald zonder afbreken (een stuk wereld
     * dat met een ander programma gewist werd, een andere mod): dan vergeten we de zone, net als Zones.activeAt. Anders
     * laadden we haar elke paar seconden opnieuw om te zien dat de kern weg is. Niet geladen = dat weten we nog niet.
     */
    private static boolean keeps(ServerLevel level, ZoneData data, ZoneData.Zone zone) {
        if (!worthKeeping(zone)) {
            return false;
        }
        BlockPos core = zone.core().pos();
        LevelChunk chunk = level.getChunkSource().getChunkNow(core.getX() >> 4, core.getZ() >> 4);
        if (chunk == null || chunk.getBlockEntity(core) instanceof SecurityCoreBlockEntity) {
            return true;
        }
        data.forget(zone.core());
        return false;
    }

    /** Hoort deze zone geladen te blijven (de config staat aan, een eigenaar, minstens één kubus)? */
    public static boolean worthKeeping(@Nullable ZoneData.Zone zone) {
        return ImtConfig.Security.ZONE_CHUNK_LOADING && zone != null && zone.owner() != null && zone.hasBoxes();
    }

    /**
     * De stukken voor deze zone, uit het geheugen als de kubussen, de rand en het maximum nog dezelfde zijn. Veranderden
     * ze, dan rekenen we opnieuw, maar per zone hooguit één keer per {@link #EVERY} ticks (behalve met {@code now}, voor
     * de tests): tot dan geldt de vorige lijst, en komt er straks nog een ronde ({@link #deferred}).
     */
    private static LongList wantedFor(ZoneData.Zone zone, int tick, boolean now) {
        List<ZoneBox> boxes = zone.boxesInReach();
        int margin = ZoneScan.margin();
        int max = max();
        Wanted cached = WANTED.get(zone.core());
        if (cached != null && cached.margin() == margin && cached.max() == max && cached.boxes().equals(boxes)) {
            return cached.chunks();
        }
        if (!now && cached != null && tick >= cached.tick() && tick - cached.tick() < EVERY) {
            deferred = true;
            return cached.chunks();
        }
        cached = new Wanted(boxes, margin, max, wanted(boxes, zone.core().pos(), margin, max), tick);
        WANTED.put(zone.core(), cached);
        return cached.chunks();
    }

    /** Hoeveel stukken wereld een zone hooguit geladen houdt (uit de config). */
    public static int max() {
        return Math.max(1, Math.round(ImtConfig.Security.ZONE_CHUNK_LOADING_MAX));
    }

    // ================================================================ Opstarten en afsluiten

    /**
     * Bij het opstarten, per dimensie en voor er een speler is: Minecraft vond onze tickets in de wereld terug en vraagt
     * welke er mogen blijven. Die van een zone die nog geladen hoort te blijven, houden we (en onthouden we: dan hoeft de
     * eerste ronde ze niet opnieuw te zetten); de rest gaat weg. Zo laadt een grote basis na een herstart op de achtergrond,
     * zoals bij een speler die aankomt, in plaats van in één keer bij de eerste tick.
     */
    private static void validate(ServerLevel level, TicketHelper tickets) {
        forServer(level.getServer());
        Map<BlockPos, LongLinkedOpenHashSet> held = HELD.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        ZoneData data = ZoneData.get(level.getServer());
        boolean tests = level.getServer() instanceof GameTestServer;
        for (Map.Entry<BlockPos, TicketSet> entry : tickets.getBlockTickets().entrySet()) {
            BlockPos core = entry.getKey();
            // We maken nooit tickets die "ticken" (zie de uitleg bovenaan); staat er toch een, dan weg ermee.
            for (long chunk : entry.getValue().ticking()) {
                tickets.removeTicket(core, chunk, true);
            }
            if (tests || !worthKeeping(data.zone(GlobalPos.of(level.dimension(), core)))) {
                tickets.removeAllTickets(core);
                continue;
            }
            held.put(core.immutable(), new LongLinkedOpenHashSet(entry.getValue().nonTicking()));
        }
        // Tickets op naam van een wezen maken we niet.
        for (UUID owner : tickets.getEntityTickets().keySet()) {
            tickets.removeAllTickets(owner);
        }
    }

    /** Hoort wat we onthielden bij deze server? Zo niet (een volgende wereld), dan beginnen we opnieuw. */
    private static void forServer(MinecraftServer current) {
        if (server != current) {
            clear();
            server = current;
        }
    }

    /** De server stopt: alles vergeten (de tickets zelf staan in de wereld). */
    public static void clear() {
        HELD.clear();
        WANTED.clear();
        lastChanges = -1;
        busy = false;
        deferred = false;
        server = null;
    }

    // ================================================================ Voor anderen

    /** De stukken wereld (als ChunkPos.toLong) die we nu voor de zone van deze kern geladen houden (de app Zone, de tests). */
    public static long[] kept(ServerLevel level, BlockPos core) {
        if (server != level.getServer()) {
            return new long[0];
        }
        Map<BlockPos, LongLinkedOpenHashSet> held = HELD.get(level.dimension());
        LongLinkedOpenHashSet chunks = held == null ? null : held.get(core);
        return chunks == null ? new long[0] : chunks.toLongArray();
    }

    /** De kernen in deze dimensie waarvan we de zone nu geladen houden (hun netwerk houdt NetworkKeeper dan ook geladen). */
    public static Set<BlockPos> keptCores(ServerLevel level) {
        if (server != level.getServer()) {
            return Set.of();
        }
        Map<BlockPos, LongLinkedOpenHashSet> held = HELD.get(level.dimension());
        if (held == null || held.isEmpty()) {
            return Set.of();
        }
        Set<BlockPos> cores = new HashSet<>();
        for (Map.Entry<BlockPos, LongLinkedOpenHashSet> entry : held.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                cores.add(entry.getKey());
            }
        }
        return cores;
    }

    /**
     * Voor de tests (daar staat de ronde van elke tick uit, zie {@link #onServerTick}): de stukken van deze ene zone meteen
     * goed zetten, zonder budget en zonder te wachten. Zonder zone (of met de config uit) laat dit alles van die kern los;
     * staat de kern er niet meer, dan vergeet het ook de zone (zie {@link #keeps}).
     */
    public static void syncNow(ServerLevel level, BlockPos core) {
        forServer(level.getServer());
        GlobalPos key = GlobalPos.of(level.dimension(), core);
        ZoneData data = ZoneData.get(level);
        ZoneData.Zone zone = data.zone(key);
        LongList want = zone != null && keeps(level, data, zone)
                ? wantedFor(zone, level.getServer().getTickCount(), true) : LongList.of();
        Map<BlockPos, LongLinkedOpenHashSet> held = HELD.computeIfAbsent(level.dimension(), dim -> new HashMap<>());
        LongLinkedOpenHashSet have = held.computeIfAbsent(core.immutable(), pos -> new LongLinkedOpenHashSet());
        release(level, core, have, new LongOpenHashSet(want));
        for (int i = 0; i < want.size(); i++) {
            long chunk = want.getLong(i);
            if (have.add(chunk)) {
                CONTROLLER.forceChunk(level, core, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true, false);
            }
        }
        if (have.isEmpty()) {
            held.remove(core);
        }
        if (data.zone(key) == null) {
            WANTED.remove(key);
        }
    }

    // ================================================================ Rekenen: welke stukken wereld

    /**
     * Welke stukken wereld (chunks, als ChunkPos.toLong) van een zone met deze kubussen geladen blijven: de belangrijkste
     * eerst (het stuk van de kern, dan wat er het dichtst bij ligt), hooguit {@code max}. Zonder kubussen niets.
     *
     * <ol>
     *   <li>We kijken van bovenaf: elke kubus is een rechthoek (x en z; de hoogte doet er voor een stuk wereld niet toe).</li>
     *   <li>Elke kubus heeft haar eigen rand: {@code margin} blokken, maar nooit meer dan de helft van haar breedte of
     *       diepte (een kubus van 9 breed heeft een rand van 4: dan blijft haar middelste rij over).</li>
     *   <li>Een stuk wereld blijft geladen als een kubus erin een kolom heeft waarvan het vierkant van haar rand naar alle
     *       kanten helemaal in de zone ligt (alle kubussen samen, zie {@link #keepsChunk}). We proberen de stukken van
     *       dichtbij naar ver van de kern, en stoppen bij {@code max}.</li>
     * </ol>
     */
    public static LongList wanted(List<ZoneBox> boxes, BlockPos core, int margin, int max) {
        LongArrayList result = new LongArrayList();
        if (boxes.isEmpty() || max <= 0) {
            return result;
        }
        int coreX = core.getX() >> 4;
        int coreZ = core.getZ() >> 4;
        long coreChunk = ChunkPos.asLong(coreX, coreZ);
        result.add(coreChunk);
        int[][] rects = new int[boxes.size()][];
        int[] margins = new int[boxes.size()];
        LongOpenHashSet candidates = new LongOpenHashSet();
        for (int i = 0; i < boxes.size(); i++) {
            ZoneBox box = boxes.get(i);
            int[] rect = {box.min().getX(), box.min().getZ(), box.max().getX(), box.max().getZ()};
            rects[i] = rect;
            margins[i] = Math.min(Math.max(0, margin), Math.min((rect[2] - rect[0]) / 2, (rect[3] - rect[1]) / 2));
            for (int cx = rect[0] >> 4; cx <= rect[2] >> 4; cx++) {
                for (int cz = rect[1] >> 4; cz <= rect[3] >> 4; cz++) {
                    candidates.add(ChunkPos.asLong(cx, cz));
                }
            }
        }
        long[] order = candidates.toLongArray();
        LongArrays.quickSort(order, (LongComparator) (a, b) -> {
            long da = distance(a, coreX, coreZ);
            long db = distance(b, coreX, coreZ);
            return da != db ? Long.compare(da, db) : Long.compare(a, b);
        });
        for (long chunk : order) {
            if (result.size() >= max) {
                break;
            }
            if (chunk != coreChunk && keepsChunk(rects, margins, ChunkPos.getX(chunk), ChunkPos.getZ(chunk))) {
                result.add(chunk);
            }
        }
        return result;
    }

    /** Hoe ver dit stuk wereld van dat van de kern ligt (in het kwadraat, in stukken). */
    private static long distance(long chunk, int coreX, int coreZ) {
        long dx = ChunkPos.getX(chunk) - coreX;
        long dz = ChunkPos.getZ(chunk) - coreZ;
        return dx * dx + dz * dz;
    }

    /** Ligt deze rechthoek ({x1, z1, x2, z2}, beide randen erbij) in het gebied van x0,z0 tot en met x1,z1? */
    private static boolean overlaps(int[] rect, int x0, int z0, int x1, int z1) {
        return rect[0] <= x1 && rect[2] >= x0 && rect[1] <= z1 && rect[3] >= z0;
    }

    /**
     * Blijft dit stuk wereld geladen? Ja als een kubus die erin ligt hier een kolom heeft waarvan het vierkant van haar
     * rand ({@code margins}) naar alle kanten helemaal in de zone ligt (de rechthoeken samen).
     *
     * Eerst het snelle en precieze geval: het midden van een kubus (de kubus zonder haar rand) ligt in dit stuk. Lukt dat
     * met geen enkele kubus, dan kan het alleen nog met een vierkant dat over meer kubussen ligt (de naad tussen twee
     * kamers). Dat rekenen we uit met een tabel over dit stuk plus de rand: per plek de zijde van het grootste vierkant
     * dat helemaal in de zone ligt en daar zijn hoek met de grootste x en z heeft. Een vierkant van 2m+1 rond kolom (x, z)
     * past als die tabel op (x + m, z + m) minstens 2m+1 zegt.
     */
    static boolean keepsChunk(int[][] rects, int[] margins, int chunkX, int chunkZ) {
        int bx0 = chunkX << 4;
        int bz0 = chunkZ << 4;
        int bx1 = bx0 + 15;
        int bz1 = bz0 + 15;
        int most = -1;
        for (int i = 0; i < rects.length; i++) {
            int[] rect = rects[i];
            if (!overlaps(rect, bx0, bz0, bx1, bz1)) {
                continue;
            }
            int m = margins[i];
            if (Math.max(rect[0] + m, bx0) <= Math.min(rect[2] - m, bx1)
                    && Math.max(rect[1] + m, bz0) <= Math.min(rect[3] - m, bz1)) {
                return true;
            }
            most = Math.max(most, m);
        }
        if (most < 0) {
            return false; // geen kubus in dit stuk
        }
        int x0 = bx0 - most;
        int z0 = bz0 - most;
        int x1 = bx1 + most;
        int z1 = bz1 + most;
        // Raakt maar één kubus dit gebied, dan kan een vierkant alleen in die ene liggen, en dat keken we net al na.
        int near = 0;
        for (int[] rect : rects) {
            if (overlaps(rect, x0, z0, x1, z1)) {
                near++;
            }
        }
        if (near < 2) {
            return false;
        }
        int size = 16 + 2 * most;
        boolean[] inside = new boolean[size * size];
        for (int[] rect : rects) {
            int ax = Math.max(rect[0], x0);
            int bx = Math.min(rect[2], x1);
            int az = Math.max(rect[1], z0);
            int bz = Math.min(rect[3], z1);
            for (int z = az; z <= bz; z++) {
                int row = (z - z0) * size;
                for (int x = ax; x <= bx; x++) {
                    inside[row + x - x0] = true;
                }
            }
        }
        // square[z * size + x]: de zijde van het grootste vierkant in de zone met (x, z) als hoek met de grootste x en z.
        int[] square = new int[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int k = z * size + x;
                if (inside[k]) {
                    square[k] = x == 0 || z == 0 ? 1
                            : 1 + Math.min(square[k - 1], Math.min(square[k - size], square[k - size - 1]));
                }
            }
        }
        for (int i = 0; i < rects.length; i++) {
            int[] rect = rects[i];
            if (!overlaps(rect, bx0, bz0, bx1, bz1)) {
                continue;
            }
            int m = margins[i];
            int span = 2 * m + 1;
            for (int z = Math.max(rect[1], bz0); z <= Math.min(rect[3], bz1); z++) {
                for (int x = Math.max(rect[0], bx0); x <= Math.min(rect[2], bx1); x++) {
                    if (square[(z + m - z0) * size + (x + m - x0)] >= span) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
