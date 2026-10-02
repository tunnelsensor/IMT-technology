package com.senne.starktech.client.os.camera;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.senne.starktech.StarkTech;
import com.senne.starktech.client.EdithClientConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * De camerabeelden van de bewakingsmuur (jouw wens, 28 september: live beeldjes zoals de mod
 * Vista, zonder lag).
 *
 * Elk beeld is een klein plaatje: 192 x 108 pixels, of 384 x 216 als je erop klikte (het grote
 * beeld). De bewakingsmuur vraagt elk frame om de beelden die hij laat zien ({@link #request}); vlak
 * vóór het volgende frame tekenen we de beelden die aan de beurt zijn (de FeedPlanner kiest: een
 * paar keer per seconde, om de beurt, en per frame niet te lang). Wat 5 seconden niet gevraagd is,
 * ruimen we op.
 *
 * Het tekenen zelf staat in FeedRenderer. Gaat daar iets mis (een wezen of blok van een andere mod
 * dat zich niet laat tekenen), dan vangen we dat op: dat beeld laat dan "NO SIGNAL" zien, en het
 * spel loopt gewoon door.
 */
public final class CameraFeeds {
    private CameraFeeds() {}

    public static final int WIDTH = 192;
    public static final int HEIGHT = 108;

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
    }

    private static final Map<Long, Feed> FEEDS = new HashMap<>();
    private static final FeedPlanner PLANNER = new FeedPlanner();

    /**
     * De bewakingsmuur laat dit beeld zien (elk frame dat het op het scherm staat). Geeft het nummer
     * van het plaatje terug, of -1 als er nog geen beeld is (net gevraagd, of het tekenen ging mis).
     */
    public static int request(BlockPos camera, Vec3 eye, float yaw, float pitch, boolean big) {
        long key = camera.asLong();
        Feed feed = FEEDS.computeIfAbsent(key, k -> new Feed());
        feed.eye = eye;
        feed.yaw = yaw;
        feed.pitch = pitch;
        feed.big = big;
        PLANNER.want(key, Util.getMillis(), big);
        return feed.ready && !feed.broken && feed.target != null ? feed.target.getColorTextureId() : -1;
    }

    /** Ging het tekenen van dit beeld mis? */
    public static boolean broken(BlockPos camera) {
        Feed feed = FEEDS.get(camera.asLong());
        return feed != null && feed.broken;
    }

    /** Ligt de lens in het stuk wereld dat je spel nu kan tekenen? */
    public static boolean inRange(Vec3 eye) {
        return FeedRenderer.inRange(eye);
    }

    // ================================================================ Vóór elk frame

    /** Vlak voor het frame: de beelden die aan de beurt zijn, tekenen; en wat niet meer gevraagd wordt, opruimen. */
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
        if (EdithClientConfig.SPEC.isLoaded()) {
            PLANNER.configure(EdithClientConfig.CAMERA_FPS.get(), EdithClientConfig.CAMERA_MIN_FPS.get(),
                    EdithClientConfig.CAMERA_BUDGET_MS.get(), 5000);
        }
        int distance = EdithClientConfig.SPEC.isLoaded() ? EdithClientConfig.CAMERA_DISTANCE.get() : 48;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
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
            if (!inRange(feed.eye)) {
                PLANNER.rendered(key, now);
                continue;
            }
            int width = feed.big ? WIDTH * 2 : WIDTH;
            int height = feed.big ? HEIGHT * 2 : HEIGHT;
            if (feed.target == null) {
                feed.target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            } else if (feed.target.width != width || feed.target.height != height) {
                feed.target.resize(width, height, Minecraft.ON_OSX);
            }
            try {
                FeedRenderer.render(feed.target, feed.eye, feed.yaw, feed.pitch, partialTick, distance);
                feed.ready = true;
            } catch (RuntimeException e) {
                feed.broken = true;
                StarkTech.LOGGER.warn("[cameras] een camerabeeld kon niet getekend worden", e);
            }
            PLANNER.rendered(key, now);
            rendered++;
        }
    }

    /** Uit de wereld: alle plaatjes opruimen. */
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
    }
}
