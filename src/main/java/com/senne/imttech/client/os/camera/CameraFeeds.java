package com.senne.imttech.client.os.camera;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.senne.imttech.ImtTech;
import com.senne.imttech.client.ImtClientConfig;
import com.senne.imttech.network.CameraWatchPayload;
import com.senne.imttech.security.CameraBlockEntity;
import com.senne.imttech.security.feed.FeedEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * De camerabeelden van de bewakingsmuur (jouw wens, 28 september: live beeldjes zoals de mod
 * Vista, zonder lag).
 *
 * Elk beeld is een klein plaatje: 192 x 108 pixels, of 384 x 216 als je erop klikte (het grote
 * beeld). De schermen vragen elk frame om de beelden die ze laten zien ({@link #camera}, {@link #robot}); vlak
 * vóór het volgende frame tekenen we de beelden die aan de beurt zijn (de FeedPlanner kiest: een
 * paar keer per seconde, om de beurt, en per frame niet te lang). Wat 5 seconden niet gevraagd is,
 * ruimen we op.
 *
 * Het tekenen zelf staat in FeedRenderer. Gaat daar iets mis (een wezen of blok van een andere mod
 * dat zich niet laat tekenen), dan vangen we dat op: dat beeld laat dan "FEED ERROR" zien, en het
 * spel loopt gewoon door.
 *
 * Sinds hoofdstuk 24 kan een beeld ook van een robot met een Camera-module komen ({@link #robot}): dan
 * kijkt het mee met zijn hoofd, en tekenen we de robot zelf niet (anders keek je in zijn eigen hoofd).
 *
 * <h2>Camerabeelden van overal (hoofdstuk 94)</h2>
 * Jouw wens: "camera range moet evenveel zijn als de cabel lang is dus een verbonde camera moet altijd zicht hebben". Een
 * paar keer per seconde zeggen we de server welke beelden er op je scherm staan ({@link #onClientTick}); die stuurt de
 * wereld rond die camera's en wie daar rondloopt (FeedWorld, FeedPuppets), en daaruit tekenen we. Zo heeft een camera aan
 * het netwerk altijd beeld, hoe ver je ook weg bent, en ook met Sodium. Tot de server het stukje wereld van de lens
 * stuurde, staat er "CONNECTING...". Mag je een camera niet zien (de server kijkt dat na), dan "NO ACCESS".
 */
public final class CameraFeeds {
    private CameraFeeds() {}

    public static final int WIDTH = 192;
    public static final int HEIGHT = 108;

    /** Zo vaak (ticks) zeggen we de server welke beelden er op je scherm staan. */
    private static final int WATCH_EVERY = 10;
    /** Zo lang (ms) na de laatste keer dat een beeld gevraagd werd, telt het nog als "op je scherm". */
    private static final long WATCH_GRACE_MS = 1500;

    /** Eén camerabeeld: zijn plaatje, en waar de camera staat en heen kijkt. */
    private static final class Feed {
        TextureTarget target;
        Vec3 eye = Vec3.ZERO;
        float yaw;
        float pitch;
        boolean big;
        /** Is er al minstens één keer in getekend? */
        boolean ready;
        /** Ging het tekenen mis? Dan proberen we het niet opnieuw. */
        boolean broken;
        /** Het wezen dat we niet tekenen (de robot zelf), of -1. */
        int skip = -1;
    }

    /**
     * Wat een scherm laat zien: een beeld ({@code texture} is het nummer van het plaatje), of wat er mis is
     * ({@code problem}: "NO ACCESS", "FEED ERROR"), of geen van beide (nog aan het verbinden: "CONNECTING...").
     */
    public record Picture(int texture, @Nullable String problem) {
        static final Picture CONNECTING = new Picture(-1, null);

        public boolean live() {
            return texture >= 0;
        }
    }

    private static final Map<Long, Feed> FEEDS = new HashMap<>();
    private static final FeedPlanner PLANNER = new FeedPlanner();

    /** Welke camera's (hun plek) en robots (hun nummer) er op je scherm staan, en wanneer voor het laatst (ms). */
    private static final Map<Long, Long> WANT_CAMERAS = new HashMap<>();
    private static final Map<Integer, Long> WANT_ROBOTS = new HashMap<>();
    /** Kwam er net een beeld bij? Dan meteen de server inlichten (niet pas bij de volgende ronde). */
    private static boolean wantChanged;
    /** Stuurden we de server al een lege lijst (niets meer op je scherm)? */
    private static boolean sentEmpty = true;
    private static int ticks;
    /** De wereld waar de stukjes van de server bij horen (een andere dimensie: alles opnieuw). */
    private static @Nullable ClientLevel knownLevel;

    // ================================================================ Wat de schermen vragen

    /**
     * Het beeld van deze camera, voor een scherm dat hem nu laat zien. Waar zijn lens zit en waar hij heen kijkt: uit de
     * camera zelf als je spel hem kent (dichtbij), anders wat de server zei, anders wat het scherm al wist
     * ({@code eyeHint}, {@code yawHint}, {@code pitchHint}; de app Cameras krijgt dat van de kern).
     */
    public static Picture camera(BlockPos camera, @Nullable Vec3 eyeHint, float yawHint, float pitchHint, boolean big) {
        Minecraft mc = Minecraft.getInstance();
        if (WANT_CAMERAS.put(camera.asLong(), Util.getMillis()) == null) {
            wantChanged = true;
        }
        FeedWorld.State state = FeedWorld.camera(camera);
        if (state != null && state.status() == FeedEntities.NO_ACCESS) {
            return new Picture(-1, "NO ACCESS");
        }
        Vec3 eye;
        float yaw;
        float pitch;
        if (mc.level != null && mc.level.getBlockEntity(camera) instanceof CameraBlockEntity entity) {
            eye = CameraBlockEntity.eye(camera, entity.getBlockState());
            yaw = entity.yaw();
            pitch = entity.pitch();
        } else if (state != null && state.eye() != null) {
            eye = state.eye();
            yaw = state.yaw();
            pitch = state.pitch();
        } else if (eyeHint != null) {
            eye = eyeHint;
            yaw = yawHint;
            pitch = pitchHint;
        } else {
            return Picture.CONNECTING;
        }
        if (broken(camera.asLong())) {
            return new Picture(-1, "FEED ERROR");
        }
        return new Picture(request(camera.asLong(), eye, yaw, pitch, big, -1), null);
    }

    /**
     * Het beeld door de ogen van deze robot of drone (zijn nummer in de wereld): vanaf zijn hoofd, een stukje naar voren
     * (anders kijk je tegen de binnenkant van zijn hoofd); een drone kijkt een beetje naar beneden. Kent je spel hem (hij is
     * in de buurt), dan volgt het beeld zijn hoofd vloeiend; anders wat de server zei.
     */
    public static Picture robot(int entityId, boolean big, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (WANT_ROBOTS.put(entityId, Util.getMillis()) == null) {
            wantChanged = true;
        }
        FeedWorld.State state = FeedWorld.robot(entityId);
        if (state != null && state.status() == FeedEntities.NO_ACCESS) {
            return new Picture(-1, "NO ACCESS");
        }
        Entity robot = mc.level == null ? null : mc.level.getEntity(entityId);
        Vec3 eye;
        float yaw;
        float pitch;
        if (robot != null) {
            yaw = robot instanceof LivingEntity living ? Mth.rotLerp(partialTick, living.yHeadRotO, living.yHeadRot) : robot.getYRot();
            eye = robot.getEyePosition(partialTick).add(Vec3.directionFromRotation(0, yaw).scale(0.35));
            pitch = robot.getXRot() + (robot instanceof com.senne.imttech.robot.GuardDroneEntity ? 20.0f : 0.0f);
        } else if (state != null && state.eye() != null) {
            eye = state.eye();
            yaw = state.yaw();
            pitch = state.pitch();
        } else {
            return Picture.CONNECTING;
        }
        long key = robotKey(entityId);
        if (broken(key)) {
            return new Picture(-1, "FEED ERROR");
        }
        return new Picture(request(key, eye, yaw, pitch, big, entityId), null);
    }

    private static int request(long key, Vec3 eye, float yaw, float pitch, boolean big, int skip) {
        Feed feed = FEEDS.computeIfAbsent(key, k -> new Feed());
        feed.eye = eye;
        feed.yaw = yaw;
        feed.pitch = pitch;
        feed.big = big;
        feed.skip = skip;
        PLANNER.want(key, Util.getMillis(), big);
        return feed.ready && !feed.broken && feed.target != null ? feed.target.getColorTextureId() : -1;
    }

    /**
     * Welk stuk van een beeld je laat zien op een vlak met deze verhouding (breedte / hoogte): {u0, v0, u1, v1}. Een beeld
     * wordt niet uitgerekt; wat niet past, snijden we links en rechts of boven en onder af.
     */
    public static float[] crop(float aspect) {
        float feed = (float) WIDTH / HEIGHT;
        if (aspect < feed) {
            float keep = aspect / feed;
            return new float[] {(1 - keep) / 2, 0, (1 + keep) / 2, 1};
        }
        float keep = feed / aspect;
        return new float[] {0, (1 - keep) / 2, 1, (1 + keep) / 2};
    }

    /**
     * Het nummer van het beeld van een robot. Een camerablok gebruikt zijn plek ({@code BlockPos.asLong}); een robot
     * een "plek" op hoogte -2048, waar nooit een blok staat, zodat ze nooit hetzelfde nummer krijgen.
     */
    private static long robotKey(int entityId) {
        return BlockPos.asLong(entityId & 0x1FFFFFF, -2048, entityId >>> 25);
    }

    /** Ging het tekenen van dit beeld mis? */
    private static boolean broken(long key) {
        Feed feed = FEEDS.get(key);
        return feed != null && feed.broken;
    }

    // ================================================================ De server inlichten (elke tick)

    /**
     * Elke tick in je spel: de poppen laten lopen, en om de halve seconde (of meteen als er een beeld bij kwam) de server
     * zeggen welke beelden er op je scherm staan. Staat er niets meer, dan één keer een lege lijst: dan stopt de server.
     */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != knownLevel) {
            // Een andere wereld of dimensie: wat we hadden, hoort daar niet bij.
            FeedWorld.clear();
            knownLevel = mc.level;
        }
        if (mc.level == null || mc.getConnection() == null) {
            return;
        }
        FeedPuppets.tick();
        long now = Util.getMillis();
        if (++ticks % WATCH_EVERY != 0 && !wantChanged) {
            return;
        }
        wantChanged = false;
        FeedWorld.forgetOldStates(now);
        WANT_CAMERAS.values().removeIf(time -> now - time > WATCH_GRACE_MS);
        WANT_ROBOTS.values().removeIf(time -> now - time > WATCH_GRACE_MS);
        if (WANT_CAMERAS.isEmpty() && WANT_ROBOTS.isEmpty()) {
            if (!sentEmpty) {
                PacketDistributor.sendToServer(new CameraWatchPayload(List.of(), List.of(), distance()));
                sentEmpty = true;
            }
            return;
        }
        List<Long> cameras = new ArrayList<>(WANT_CAMERAS.keySet());
        List<Integer> robots = new ArrayList<>(WANT_ROBOTS.keySet());
        int room = CameraWatchPayload.MAX;
        cameras = cameras.subList(0, Math.min(cameras.size(), room));
        robots = robots.subList(0, Math.min(robots.size(), room));
        PacketDistributor.sendToServer(new CameraWatchPayload(cameras, robots, distance()));
        sentEmpty = false;
    }

    /** Hoe ver de beelden kijken (config/imttech-client.toml). */
    private static int distance() {
        return ImtClientConfig.SPEC.isLoaded() ? ImtClientConfig.CAMERA_DISTANCE.get() : 48;
    }

    // ================================================================ Vóór elk frame

    /**
     * Vlak voor het frame: de beelden die aan de beurt zijn, tekenen; met de tijd die er dan nog is, de modellen van de
     * andere beelden alvast bouwen; en wat niet meer gevraagd wordt, opruimen.
     */
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        if (FEEDS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        for (long key : PLANNER.expired(now)) {
            Feed feed = FEEDS.remove(key);
            if (feed != null && feed.target != null) {
                feed.target.destroyBuffers();
            }
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (ImtClientConfig.SPEC.isLoaded()) {
            PLANNER.configure(ImtClientConfig.CAMERA_FPS.get(), ImtClientConfig.CAMERA_MIN_FPS.get(),
                    ImtClientConfig.CAMERA_BUDGET_MS.get(), 5000);
        }
        int distance = distance();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        FeedMesher.beginFrame();
        long started = System.nanoTime();
        int rendered = 0;
        for (long key : PLANNER.due(now, mc.getFps())) {
            if (!PLANNER.mayRenderMore(rendered, (System.nanoTime() - started) / 1_000_000.0)) {
                break;
            }
            Feed feed = FEEDS.get(key);
            if (feed == null || feed.broken) {
                PLANNER.rendered(key, now);
                continue;
            }
            if (!FeedRenderer.ready(feed.eye)) {
                continue; // de server stuurde de plek van de lens nog niet: volgende frame weer
            }
            int width = feed.big ? WIDTH * 2 : WIDTH;
            int height = feed.big ? HEIGHT * 2 : HEIGHT;
            if (feed.target == null) {
                feed.target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            } else if (feed.target.width != width || feed.target.height != height) {
                feed.target.resize(width, height, Minecraft.ON_OSX);
            }
            try {
                FeedRenderer.render(feed.target, feed.eye, feed.yaw, feed.pitch, partialTick, distance, feed.skip);
                feed.ready = true;
            } catch (RuntimeException e) {
                feed.broken = true;
                ImtTech.LOGGER.warn("[cameras] could not draw a camera image", e);
            }
            PLANNER.rendered(key, now);
            rendered++;
        }
        // Tijd over en nog iets te bouwen? Dan de modellen van de beelden die nu niet aan de beurt waren alvast bouwen.
        for (Feed feed : FEEDS.values()) {
            if (!FeedMesher.hasWork()) {
                break;
            }
            if (!feed.broken && FeedRenderer.ready(feed.eye)) {
                FeedRenderer.prepare(feed.eye, feed.yaw, feed.pitch, distance, (float) WIDTH / HEIGHT);
            }
        }
    }

    /** Uit de wereld: alle plaatjes en alles van de server opruimen. */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void clear() {
        for (Feed feed : FEEDS.values()) {
            if (feed.target != null) {
                feed.target.destroyBuffers();
            }
        }
        FEEDS.clear();
        PLANNER.clear();
        WANT_CAMERAS.clear();
        WANT_ROBOTS.clear();
        sentEmpty = true;
        wantChanged = false;
        FeedWorld.clear();
    }
}
