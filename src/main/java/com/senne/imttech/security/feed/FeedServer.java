package com.senne.imttech.security.feed;

import com.senne.imttech.bot.BotEntity;
import com.senne.imttech.bot.BotLink;
import com.senne.imttech.bot.BotModule;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.CameraFeedPayload;
import com.senne.imttech.network.CameraWatchPayload;
import com.senne.imttech.network.CameraWorldPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.robot.GuardDroneEntity;
import com.senne.imttech.security.CameraBlock;
import com.senne.imttech.security.CameraBlockEntity;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import com.senne.imttech.security.os.SecurityOs;
import com.senne.imttech.security.wall.CameraMonitorBlockEntity;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Camerabeelden van overal (hoofdstuk 94), aan de kant van de server. Jouw wens van 12 november: "camera range moet evenveel
 * zijn als de cabel lang is dus een verbonde camera moet altijd zicht hebben".
 *
 * <h2>Hoe het was</h2>
 * Je spel tekende een camerabeeld met de stukken wereld die het zelf al had klaargezet om jou de wereld te laten zien. Een
 * camera verder weg dan je kijkafstand gaf daarom "OUT OF RANGE", ook al hing hij aan de kabel. En met Sodium (dat het
 * tekenen van de wereld helemaal overneemt) gaf <b>elke</b> camera "OUT OF RANGE".
 *
 * <h2>Hoe het nu gaat</h2>
 * <ol>
 *   <li>Je spel zegt een paar keer per seconde welke camera's er op je scherm staan (CameraWatchPayload).</li>
 *   <li>Hier kijken we per camera na of je hem mag zien ({@link #mayWatch}): je hebt de desktop van zijn kern open (op de
 *       kern, op een Computer met de app Cameras, of met de laptop), of je staat bij een schermmuur van die kern waar hij
 *       op staat. En of hij beeld geeft (stroom, aan).</li>
 *   <li>Dan sturen we de wereld rond die camera, tot {@code [network] cameraFeedMax} blokken ver (en zo ver als je spel vraagt,
 *       {@code cameraFeedDistance} in imttech-client.toml): de blokken, het
 *       licht en de blokken met een eigen model (zie FeedFormat), een stukje per keer, het dichtstbijzijnde eerst
 *       (CameraWorldPayload). Verandert er daar iets (een deur gaat open, iemand bouwt), dan sturen we dat stukje opnieuw.</li>
 *   <li>Tien keer per seconde sturen we wie er rondloopt rond die camera's, en waar de camera's heen kijken
 *       (CameraFeedPayload, zie FeedEntities).</li>
 *   <li>Zolang je kijkt, houden we dat stuk wereld geladen (een "ticket", net als bij de laptop), maar het tikt niet: daar
 *       gebeurt niets extra's omdat jij kijkt.</li>
 * </ol>
 * Je spel tekent het beeld zelf uit wat het zo krijgt (client/os/camera/FeedWorld en FeedMesher), los van wat het voor jou
 * tekent: dus ook met Sodium, en hoe ver je ook weg bent.
 */
public final class FeedServer {
    private FeedServer() {}

    /** Zo vaak (ticks) kijken we opnieuw na of je de camera's mag zien, en vernieuwen we de tickets. */
    private static final int CHECK_EVERY = 20;
    /** Zo lang (ticks) geldt een lijst van je spel; daarna vergeten we hem (je spel stuurt hem twee keer per seconde). */
    private static final int WATCH_TIMEOUT = 60;
    /** Elke zoveel ticks: de camera's en de wezens. */
    private static final int FEED_EVERY = 2;
    /** Een wezen krijgt elke zoveel ticks een volledige beschrijving (hoe het eruitziet). */
    private static final int FULL_EVERY = 20;
    /** Zoveel stukjes wereld kijken we elke tick na of hun licht veranderde. */
    private static final int LIGHT_CHECKS_PER_TICK = 24;
    /** Zoveel stukjes wereld (en ongeveer zoveel bytes, voor het inpakken) sturen we hooguit per tick naar één speler. */
    private static final int MAX_SECTIONS_PER_TICK = 32;
    private static final int MAX_BYTES_PER_TICK = 384 * 1024;
    /** Zoveel wezens hooguit per camera (de dichtstbijzijnde). */
    private static final int MAX_ENTITIES = 48;
    /** Zo dicht (blokken) moet je bij een schermmuur staan om de camera's erop te mogen zien. */
    private static final double WALL_RANGE = 96.0;
    /** Zo lang (ticks) na een verandering kijken we het licht rond die plek nog eens na (het licht rekent even na). */
    private static final int LIGHT_SETTLE = 10;

    /** Het ticket dat de wereld rond een camera geladen houdt zolang je kijkt (verloopt vanzelf na 5 seconden). */
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("imttech_camera_feed", Comparator.comparingLong(ChunkPos::toLong), 100);

    /** Een camerablok ({@code robot} = false, {@code id} = zijn plek) of een robot ({@code id} = zijn nummer in de wereld). */
    record Source(boolean robot, long id) {}

    /** Wat we bij het nakijken van een camera vonden. */
    static final class Checked {
        byte status = FeedEntities.GONE;
        @Nullable Vec3 eye;
        float yaw;
        float pitch;
    }

    /** Wat we per speler bijhouden. */
    static final class Watcher {
        ResourceKey<Level> dimension;
        List<Source> requested = List.of();
        long requestedAt;
        int distance = 48;
        boolean listChanged = true;
        final Map<Source, Checked> sources = new LinkedHashMap<>();
        /** De stukjes wereld die je spel nu nodig heeft. */
        final LongOpenHashSet needed = new LongOpenHashSet();
        /** De stukjes die je spel al heeft, met hoe hun licht was toen we ze stuurden. */
        final Long2IntOpenHashMap sent = new Long2IntOpenHashMap();
        /** Wat nog naar je spel moet, het belangrijkste eerst. */
        final LongLinkedOpenHashSet queue = new LongLinkedOpenHashSet();
        /** Wat je spel mag vergeten. */
        final LongOpenHashSet forget = new LongOpenHashSet();
        /** Stukjes waarvan we het licht nog eens nakijken, en wanneer. */
        final Long2LongOpenHashMap lightCheck = new Long2LongOpenHashMap();
        /** Wanneer een wezen zijn laatste volledige beschrijving kreeg. */
        final Int2LongOpenHashMap fullSent = new Int2LongOpenHashMap();
        long nextCheck;
        int lightCursor;
        /** Hoeveel stukjes wereld we deze speler in totaal stuurden (ook opnieuw; voor de tests). */
        int sectionsSent;

        Watcher(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }

    private static final Map<UUID, Watcher> WATCHERS = new HashMap<>();
    /** De stukjes wereld waar deze tick een blok veranderde (zie {@link #blockChanged}), per dimensie. */
    private static final Map<ResourceKey<Level>, LongOpenHashSet> CHANGED = new HashMap<>();
    /** Kijkt er iemand? Anders hoeft {@link #blockChanged} niets te onthouden. */
    private static volatile boolean active;

    // ================================================================ Van je spel

    /** Je spel zegt welke camerabeelden er op je scherm staan. */
    public static void handleWatch(ServerPlayer player, CameraWatchPayload payload) {
        List<Source> sources = new ArrayList<>();
        for (long camera : payload.cameras()) {
            sources.add(new Source(false, camera));
        }
        for (int robot : payload.robots()) {
            sources.add(new Source(true, robot));
        }
        if (sources.isEmpty()) {
            Watcher old = WATCHERS.remove(player.getUUID());
            if (old != null) {
                forgetAll(player, old);
            }
            active = !WATCHERS.isEmpty();
            return;
        }
        Watcher watcher = WATCHERS.get(player.getUUID());
        if (watcher == null || watcher.dimension != player.level().dimension()) {
            if (watcher != null) {
                forgetAll(player, watcher);
            }
            watcher = new Watcher(player.level().dimension());
            WATCHERS.put(player.getUUID(), watcher);
        }
        int max = Math.round(ImtConfig.Network.CAMERA_FEED_MAX);
        int distance = Mth.clamp(payload.distance(), 16, Math.max(16, max));
        if (!sources.equals(watcher.requested) || distance != watcher.distance) {
            watcher.listChanged = true;
            watcher.nextCheck = 0; // meteen nakijken
        }
        watcher.requested = List.copyOf(sources);
        watcher.distance = distance;
        watcher.requestedAt = player.server.getTickCount();
        active = true;
    }

    /**
     * Er veranderde een blok in een geladen stuk wereld (vanuit LevelChunkMixin, alleen op de server). Kijkt er iemand naar
     * dat stukje, dan sturen we het opnieuw.
     */
    public static void blockChanged(Level level, BlockPos pos) {
        if (!active) {
            return;
        }
        CHANGED.computeIfAbsent(level.dimension(), key -> new LongOpenHashSet()).add(SectionPos.asLong(pos));
    }

    // ================================================================ Elke tick

    public static void onServerTick(ServerTickEvent.Post event) {
        if (WATCHERS.isEmpty()) {
            CHANGED.clear();
            active = false;
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, Watcher>> iterator = WATCHERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Watcher> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Watcher watcher = entry.getValue();
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (now - watcher.requestedAt > WATCH_TIMEOUT || player.level().dimension() != watcher.dimension) {
                forgetAll(player, watcher);
                iterator.remove();
                continue;
            }
            LongOpenHashSet changed = CHANGED.get(watcher.dimension);
            if (changed != null) {
                markChanged(watcher, changed, now);
            }
            try {
                tick(player, watcher, now);
            } catch (RuntimeException e) {
                com.senne.imttech.ImtTech.LOGGER.warn("[cameras] could not send camera feeds to {}", player.getGameProfile().getName(), e);
                forgetAll(player, watcher);
                iterator.remove();
            }
        }
        CHANGED.clear();
        active = !WATCHERS.isEmpty();
    }

    /** Wie uitlogt, kijkt niet meer. */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        WATCHERS.remove(event.getEntity().getUUID());
        active = !WATCHERS.isEmpty();
    }

    /** De server stopt: alles vergeten. */
    public static void clear() {
        WATCHERS.clear();
        CHANGED.clear();
        active = false;
    }

    /** Stukjes die je spel heeft en waar deze tick een blok veranderde: opnieuw sturen, en straks het licht eromheen nakijken. */
    private static void markChanged(Watcher watcher, LongOpenHashSet changed, long now) {
        LongIterator it = changed.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            if (watcher.sent.containsKey(key)) {
                watcher.queue.addAndMoveToFirst(key);
            }
            for (Direction direction : Direction.values()) {
                long next = SectionPos.offset(key, direction);
                if (watcher.sent.containsKey(next)) {
                    watcher.lightCheck.put(next, now + LIGHT_SETTLE);
                }
            }
            if (watcher.sent.containsKey(key)) {
                watcher.lightCheck.put(key, now + LIGHT_SETTLE);
            }
        }
    }

    private static void tick(ServerPlayer player, Watcher watcher, long now) {
        ServerLevel level = player.serverLevel();
        boolean robots = watcher.requested.stream().anyMatch(Source::robot);
        // 1. Nakijken (elke seconde, of meteen na een nieuwe lijst); robots lopen rond, dus hun stukjes vaker.
        if (now >= watcher.nextCheck) {
            check(player, level, watcher);
            watcher.nextCheck = now + CHECK_EVERY;
            updateNeeded(level, watcher);
            keepLoaded(level, watcher);
        } else if (robots && now % 10 == 0) {
            refreshRobots(level, watcher);
            updateNeeded(level, watcher);
        }
        // 2. Het licht nakijken: een paar stukjes per tick, en wat net veranderde.
        checkLight(level, watcher, now);
        // 3. Stukjes wereld sturen.
        sendSections(player, level, watcher);
        // 4. De camera's en de wezens.
        if (now % FEED_EVERY == 0) {
            if (robots) {
                refreshRobots(level, watcher);
            }
            sendFeed(player, level, watcher, now);
        }
    }

    // ================================================================ Mag het? Waar kijkt hij?

    private static void check(ServerPlayer player, ServerLevel level, Watcher watcher) {
        watcher.sources.clear();
        for (Source source : watcher.requested) {
            watcher.sources.put(source, check(player, level, source));
        }
    }

    /** Kijkt één camera (of robot) na: mag deze speler hem zien, en waar kijkt hij? */
    static Checked check(ServerPlayer player, ServerLevel level, Source source) {
        Checked result = new Checked();
        if (!source.robot()) {
            BlockPos pos = BlockPos.of(source.id());
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof CameraBlockEntity camera)) {
                return result;
            }
            BlockState state = level.getBlockState(pos);
            SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
            if (core == null || !mayWatch(player, level, core, pos, null)) {
                result.status = FeedEntities.NO_ACCESS;
                return result;
            }
            result.eye = CameraBlockEntity.eye(pos, state);
            result.yaw = camera.yaw();
            result.pitch = camera.pitch();
            result.status = state.getValue(CameraBlock.ONLINE) ? FeedEntities.OK : FeedEntities.OFFLINE;
            return result;
        }
        if (!(level.getEntity((int) source.id()) instanceof BotEntity robot) || !robot.isAlive()
                || !robot.board().has(BotModule.CAMERA)) {
            return result;
        }
        SecurityCoreBlockEntity core = BotLink.core(robot);
        if (core == null || !mayWatch(player, level, core, null, robot.getUUID())) {
            result.status = FeedEntities.NO_ACCESS;
            return result;
        }
        aimRobot(result, robot);
        result.status = robot.brain().pins().cameraOn() && !robot.isDown() ? FeedEntities.OK : FeedEntities.OFFLINE;
        return result;
    }

    /** Waar een robot heen kijkt: vanaf zijn hoofd, een stukje naar voren; een drone een beetje naar beneden. */
    private static void aimRobot(Checked result, BotEntity robot) {
        float yaw = robot.getYHeadRot();
        result.eye = robot.getEyePosition().add(Vec3.directionFromRotation(0, yaw).scale(0.35));
        result.yaw = yaw;
        result.pitch = robot.getXRot() + (robot instanceof GuardDroneEntity ? 20.0f : 0.0f);
    }

    /** Robots lopen rond: hun lens tussendoor bijwerken (zonder alles opnieuw na te kijken). */
    private static void refreshRobots(ServerLevel level, Watcher watcher) {
        for (Map.Entry<Source, Checked> entry : watcher.sources.entrySet()) {
            Checked checked = entry.getValue();
            if (entry.getKey().robot() && checked.status <= FeedEntities.OFFLINE
                    && level.getEntity((int) entry.getKey().id()) instanceof BotEntity robot && robot.isAlive()) {
                aimRobot(checked, robot);
            }
        }
    }

    /**
     * Mag deze speler de camera's van deze kern zien? Ja als hij de desktop van die kern open heeft met de app Cameras (op de
     * kern, op een Computer, met de laptop of zijn telefoon), of als hij bij een schermmuur van die kern staat (binnen
     * {@link #WALL_RANGE} blokken) waar precies deze camera (of robot) op staat.
     */
    public static boolean mayWatch(ServerPlayer player, ServerLevel level, SecurityCoreBlockEntity core, @Nullable BlockPos camera,
                                   @Nullable UUID robot) {
        SecurityOs.Session session = SecurityOs.session(player);
        if (session != null && session.core().equals(core.getBlockPos()) && session.rights(level).shows("CAMERAS")) {
            return true;
        }
        for (BlockPos monitor : SecurityDevices.scan(level, core).monitors()) {
            if (monitor.distToCenterSqr(player.position()) > WALL_RANGE * WALL_RANGE) {
                continue;
            }
            if (level.getBlockEntity(monitor) instanceof CameraMonitorBlockEntity entity && entity.tile() != null) {
                CameraMonitorBlockEntity.Tile tile = entity.tile();
                if (camera != null && camera.equals(tile.camera()) || robot != null && robot.equals(tile.robot())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ================================================================ Welke stukjes wereld

    /**
     * De stukjes wereld rond de camera's die je mag zien: elk stukje waarvan het midden binnen het bereik ligt (plus een
     * marge voor de hoeken, zoals client/os/camera/FeedRenderer). Wat je spel niet meer nodig heeft, mag het vergeten; wat
     * erbij komt, zetten we in de rij (het dichtstbijzijnde eerst).
     */
    private static void updateNeeded(ServerLevel level, Watcher watcher) {
        LongOpenHashSet needed = new LongOpenHashSet();
        List<Vec3> eyes = new ArrayList<>();
        for (Checked checked : watcher.sources.values()) {
            if (checked.status <= FeedEntities.OFFLINE && checked.eye != null) {
                addNeeded(needed, level, checked.eye, watcher.distance);
                eyes.add(checked.eye);
            }
        }
        // Weg: wat je spel had maar niet meer nodig heeft.
        LongIterator sent = watcher.sent.keySet().iterator();
        while (sent.hasNext()) {
            long key = sent.nextLong();
            if (!needed.contains(key)) {
                sent.remove();
                watcher.forget.add(key);
                watcher.lightCheck.remove(key);
            }
        }
        LongIterator queued = watcher.queue.iterator();
        while (queued.hasNext()) {
            if (!needed.contains(queued.nextLong())) {
                queued.remove();
            }
        }
        // Erbij: wat je spel nog niet heeft, het dichtstbijzijnde eerst.
        LongArrayList fresh = new LongArrayList();
        LongIterator it = needed.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            if (!watcher.sent.containsKey(key) && !watcher.queue.contains(key)) {
                fresh.add(key);
                watcher.forget.remove(key);
            }
        }
        fresh.sort((a, b) -> Double.compare(nearest(a, eyes), nearest(b, eyes)));
        for (int i = 0; i < fresh.size(); i++) {
            watcher.queue.add(fresh.getLong(i));
        }
        watcher.needed.clear();
        watcher.needed.addAll(needed);
    }

    /** Alle stukjes wereld waarvan het midden binnen {@code distance} + 14 blokken van de lens ligt. */
    static void addNeeded(LongOpenHashSet into, Level level, Vec3 eye, int distance) {
        double reach = distance + 14.0;
        double reach2 = reach * reach;
        int radius = (int) Math.ceil(reach / 16.0);
        int cx = SectionPos.posToSectionCoord(eye.x);
        int cy = SectionPos.posToSectionCoord(eye.y);
        int cz = SectionPos.posToSectionCoord(eye.z);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int sy = cy + dy;
                if (sy < level.getMinSection() || sy >= level.getMaxSection()) {
                    continue;
                }
                for (int dz = -radius; dz <= radius; dz++) {
                    int sx = cx + dx;
                    int sz = cz + dz;
                    double ox = (sx << 4) + 8 - eye.x;
                    double oy = (sy << 4) + 8 - eye.y;
                    double oz = (sz << 4) + 8 - eye.z;
                    if (ox * ox + oy * oy + oz * oz <= reach2) {
                        into.add(SectionPos.asLong(sx, sy, sz));
                    }
                }
            }
        }
    }

    /** Hoe ver (in het kwadraat) het midden van dit stukje van de dichtstbijzijnde lens ligt. */
    private static double nearest(long key, List<Vec3> eyes) {
        double x = (SectionPos.x(key) << 4) + 8;
        double y = (SectionPos.y(key) << 4) + 8;
        double z = (SectionPos.z(key) << 4) + 8;
        double best = Double.MAX_VALUE;
        for (Vec3 eye : eyes) {
            best = Math.min(best, eye.distanceToSqr(x, y, z));
        }
        return best;
    }

    /**
     * Houdt de stukken wereld rond de camera's geladen zolang je kijkt (alleen geladen, niet levend: blokken en wezens daar
     * tikken niet extra). Het ticket verloopt vanzelf als je ophoudt met kijken.
     */
    private static void keepLoaded(ServerLevel level, Watcher watcher) {
        LongOpenHashSet chunks = new LongOpenHashSet();
        LongIterator it = watcher.needed.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            chunks.add(ChunkPos.asLong(SectionPos.x(key), SectionPos.z(key)));
        }
        LongIterator chunk = chunks.iterator();
        while (chunk.hasNext()) {
            ChunkPos pos = new ChunkPos(chunk.nextLong());
            level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
        }
    }

    // ================================================================ Licht

    /** Een paar stukjes per tick: is het licht veranderd sinds we ze stuurden? Dan opnieuw sturen. */
    private static void checkLight(ServerLevel level, Watcher watcher, long now) {
        if (!watcher.lightCheck.isEmpty()) {
            LongArrayList due = new LongArrayList();
            for (Long2LongOpenHashMap.Entry entry : watcher.lightCheck.long2LongEntrySet()) {
                if (entry.getLongValue() <= now) {
                    due.add(entry.getLongKey());
                }
            }
            for (int i = 0; i < due.size(); i++) {
                long key = due.getLong(i);
                watcher.lightCheck.remove(key);
                recheckLight(level, watcher, key);
            }
        }
        if (watcher.sent.isEmpty()) {
            return;
        }
        long[] keys = watcher.sent.keySet().toLongArray();
        for (int i = 0; i < LIGHT_CHECKS_PER_TICK && i < keys.length; i++) {
            watcher.lightCursor = (watcher.lightCursor + 1) % keys.length;
            recheckLight(level, watcher, keys[watcher.lightCursor]);
        }
    }

    private static void recheckLight(ServerLevel level, Watcher watcher, long key) {
        if (!watcher.sent.containsKey(key)) {
            return;
        }
        if (FeedFormat.lightHash(level, SectionPos.of(key)) != watcher.sent.get(key)) {
            watcher.queue.add(key);
        }
    }

    // ================================================================ Versturen

    /** Stuurt wat in de rij staat, tot het budget van deze tick op is. Een stukje wereld dat nog laadt, wacht. */
    private static void sendSections(ServerPlayer player, ServerLevel level, Watcher watcher) {
        if (watcher.queue.isEmpty() && watcher.forget.isEmpty()) {
            return;
        }
        FriendlyByteBuf body = new FriendlyByteBuf(Unpooled.buffer());
        int count = 0;
        int bytes = 0;
        LongIterator it = watcher.queue.iterator();
        while (it.hasNext() && count < MAX_SECTIONS_PER_TICK && bytes < MAX_BYTES_PER_TICK) {
            long key = it.nextLong();
            if (!watcher.needed.contains(key)) {
                it.remove();
                continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.x(key), SectionPos.z(key));
            if (chunk == null) {
                continue; // nog niet geladen: volgende keer
            }
            it.remove();
            bytes += FeedFormat.writeSection(body, level, chunk, key);
            watcher.sent.put(key, FeedFormat.lightHash(level, SectionPos.of(key)));
            watcher.sectionsSent++;
            count++;
        }
        if (count == 0 && watcher.forget.isEmpty()) {
            body.release();
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(bytes + 64));
        buf.writeVarInt(FeedFormat.VERSION);
        buf.writeVarInt(watcher.forget.size());
        LongIterator forget = watcher.forget.iterator();
        while (forget.hasNext()) {
            buf.writeLong(forget.nextLong());
        }
        watcher.forget.clear();
        buf.writeVarInt(count);
        buf.writeBytes(body);
        body.release();
        byte[] packed = FeedFormat.pack(buf);
        buf.release();
        ModNetwork.sendTo(player, new CameraWorldPayload(packed));
    }

    /** Alles wat je spel van ons heeft, mag het vergeten (je kijkt niet meer, of je ging naar een andere dimensie). */
    private static void forgetAll(ServerPlayer player, Watcher watcher) {
        if (watcher.sent.isEmpty() && watcher.forget.isEmpty()) {
            return;
        }
        watcher.forget.addAll(watcher.sent.keySet());
        watcher.sent.clear();
        watcher.queue.clear();
        watcher.needed.clear();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(FeedFormat.VERSION);
        buf.writeVarInt(watcher.forget.size());
        LongIterator forget = watcher.forget.iterator();
        while (forget.hasNext()) {
            buf.writeLong(forget.nextLong());
        }
        watcher.forget.clear();
        buf.writeVarInt(0);
        byte[] packed = FeedFormat.pack(buf);
        buf.release();
        ModNetwork.sendTo(player, new CameraWorldPayload(packed));
    }

    /** De camera's (mag het, waar kijken ze) en de wezens rond de camera's die beeld geven. */
    private static void sendFeed(ServerPlayer player, ServerLevel level, Watcher watcher, long now) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        buf.writeVarInt(watcher.sources.size());
        Map<Integer, Entity> seen = new LinkedHashMap<>();
        double reach = watcher.distance;
        for (Map.Entry<Source, Checked> entry : watcher.sources.entrySet()) {
            Source source = entry.getKey();
            Checked checked = entry.getValue();
            boolean live = checked.status <= FeedEntities.OFFLINE && checked.eye != null;
            FeedEntities.writeSource(buf, source.robot(), source.id(), checked.status, live ? checked.eye : null,
                    checked.yaw, checked.pitch);
            if (checked.status != FeedEntities.OK || checked.eye == null) {
                continue;
            }
            Vec3 eye = checked.eye;
            List<Entity> near = level.getEntities((Entity) null, new AABB(eye, eye).inflate(reach),
                    entity -> entity.isAlive() && !entity.isSpectator() && entity.distanceToSqr(eye) <= reach * reach);
            near.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(eye)));
            for (int i = 0; i < near.size() && i < MAX_ENTITIES; i++) {
                seen.putIfAbsent(near.get(i).getId(), near.get(i));
            }
        }
        // Wie niet meer in beeld is, krijgt de volgende keer weer een volledige beschrijving.
        it.unimi.dsi.fastutil.ints.IntIterator known = watcher.fullSent.keySet().iterator();
        while (known.hasNext()) {
            if (!seen.containsKey(known.nextInt())) {
                known.remove();
            }
        }
        RegistryFriendlyByteBuf entities = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        int written = 0;
        for (Entity entity : seen.values()) {
            long last = watcher.fullSent.getOrDefault(entity.getId(), Long.MIN_VALUE / 2);
            boolean full = now - last >= FULL_EVERY;
            if (FeedEntities.writeEntity(entities, entity, full)) {
                written++;
                if (full) {
                    watcher.fullSent.put(entity.getId(), now);
                }
            }
        }
        buf.writeVarInt(written);
        buf.writeBytes(entities);
        entities.release();
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        buf.release();
        ModNetwork.sendTo(player, new CameraFeedPayload(data));
    }

    // ================================================================ Voor de tests

    /** Hoeveel stukjes wereld deze speler van ons heeft (voor de tests). */
    public static int sentCount(ServerPlayer player) {
        Watcher watcher = WATCHERS.get(player.getUUID());
        return watcher == null ? 0 : watcher.sent.size();
    }

    /** Hoeveel stukjes wereld we deze speler in totaal stuurden, ook opnieuw (voor de tests). */
    public static int totalSent(ServerPlayer player) {
        Watcher watcher = WATCHERS.get(player.getUUID());
        return watcher == null ? 0 : watcher.sectionsSent;
    }

    /** Hoeveel stukjes wereld deze speler nodig heeft (voor de tests). */
    public static int neededCount(ServerPlayer player) {
        Watcher watcher = WATCHERS.get(player.getUUID());
        return watcher == null ? 0 : watcher.needed.size();
    }

    /** Wat de server over deze camera zegt tegen deze speler (voor de tests), of -1 als hij hem niet bekijkt. */
    public static int status(ServerPlayer player, BlockPos camera) {
        Watcher watcher = WATCHERS.get(player.getUUID());
        Checked checked = watcher == null ? null : watcher.sources.get(new Source(false, camera.asLong()));
        return checked == null ? -1 : checked.status;
    }

    /** Staat dit stukje wereld klaar om (opnieuw) naar deze speler te gaan? (Voor de tests.) */
    public static boolean queued(ServerPlayer player, long section) {
        Watcher watcher = WATCHERS.get(player.getUUID());
        return watcher != null && watcher.queue.contains(section);
    }

    /** Eén tick voor alleen deze speler (voor de tests: dan hoeven ze niet op de server te wachten). */
    public static void tickFor(ServerPlayer player) {
        Watcher watcher = WATCHERS.get(player.getUUID());
        if (watcher == null) {
            return;
        }
        long now = player.server.getTickCount();
        LongOpenHashSet changed = CHANGED.get(watcher.dimension);
        if (changed != null) {
            markChanged(watcher, changed, now);
        }
        tick(player, watcher, now);
    }
}
