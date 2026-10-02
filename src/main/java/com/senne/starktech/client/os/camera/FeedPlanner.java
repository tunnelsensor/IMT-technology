package com.senne.starktech.client.os.camera;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * De planner van de bewakingsmuur: welke camerabeelden dit frame opnieuw getekend worden, en welke
 * weg mogen. Het idee komt van de mod Vista (jouw voorbeeld, 28 september), zodat de muur geen lag
 * geeft:
 * <ul>
 *   <li>alleen beelden die echt op het scherm staan, worden getekend;</li>
 *   <li>niet elk frame, maar een paar keer per seconde (standaard 5; het grote beeld 2x zo vaak);</li>
 *   <li>loopt het spel onder de 60 beelden per seconde, dan nog minder vaak (tot hooguit 2 per
 *       seconde): het spel heeft het dan al zwaar;</li>
 *   <li>om de beurt (wie het langst wacht eerst), en per frame samen hooguit een paar milliseconden
 *       (maar altijd minstens één beeld, anders zou een traag beeld nooit meer ververst worden);</li>
 *   <li>een beeld dat 5 seconden niet op het scherm stond, gooien we weg (dat ruimt zijn plaatje op).</li>
 * </ul>
 *
 * Er zit hier bewust geen Minecraft-code in: zo kan een automatische test hem nakijken (op de
 * server, waar geen beeld is). Tijden zijn in milliseconden; een beeld heet hier een "key" (het
 * getal van de plek van de camera).
 */
public final class FeedPlanner {
    /** Zo lang (ms) telt een beeld nog als "op het scherm" na de laatste keer dat het getekend werd. */
    public static final long WANTED_GRACE_MS = 250;

    /** Eén camerabeeld: wanneer het voor het laatst getekend is, en wanneer het voor het laatst op het scherm stond. */
    private static final class Feed {
        long lastRendered = Long.MIN_VALUE / 4; // nog nooit
        long lastWanted;
        boolean big;
    }

    private final Map<Long, Feed> feeds = new HashMap<>();
    private int baseFps = 5;
    private int minFps = 2;
    private double budgetMs = 3.0;
    private long disposeAfterMs = 5000;

    /** De getallen uit de instellingen (config/starktech-client.toml). */
    public void configure(int baseFps, int minFps, double budgetMs, long disposeAfterMs) {
        this.baseFps = Math.max(1, baseFps);
        this.minFps = Math.max(1, Math.min(minFps, this.baseFps));
        this.budgetMs = Math.max(0.1, budgetMs);
        this.disposeAfterMs = Math.max(500, disposeAfterMs);
    }

    /** Dit beeld staat nu op het scherm ({@code big}: groot, omdat je erop klikte). */
    public void want(long key, long nowMs, boolean big) {
        Feed feed = feeds.computeIfAbsent(key, k -> new Feed());
        feed.lastWanted = nowMs;
        feed.big = big;
    }

    /**
     * Hoeveel ms er tussen twee keer tekenen van één beeld zit, als het hele spel {@code fps} beelden
     * per seconde haalt. Groot: twee keer zo vaak.
     */
    public long interval(double fps, boolean big) {
        double base = 1000.0 / baseFps;
        double slowest = 1000.0 / minFps;
        double interval = fps > 0 && fps < 60 ? base * (60.0 / fps) : base;
        interval = Math.min(slowest, interval);
        return Math.round(big ? interval / 2 : interval);
    }

    /** De beelden die dit frame aan de beurt zijn, het langst wachtende eerst. */
    public List<Long> due(long nowMs, double fps) {
        List<Map.Entry<Long, Feed>> due = new ArrayList<>();
        for (Map.Entry<Long, Feed> entry : feeds.entrySet()) {
            Feed feed = entry.getValue();
            if (nowMs - feed.lastWanted > WANTED_GRACE_MS) {
                continue; // staat niet (meer) op het scherm
            }
            if (nowMs - feed.lastRendered >= interval(fps, feed.big)) {
                due.add(entry);
            }
        }
        due.sort(Comparator.comparingLong(entry -> entry.getValue().lastRendered));
        List<Long> keys = new ArrayList<>(due.size());
        for (Map.Entry<Long, Feed> entry : due) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    /** Mag er dit frame nog een beeld bij? Het eerste altijd; daarna alleen binnen het tijdbudget. */
    public boolean mayRenderMore(int renderedThisFrame, double spentMs) {
        return renderedThisFrame == 0 || spentMs < budgetMs;
    }

    /** Dit beeld is net getekend. */
    public void rendered(long key, long nowMs) {
        Feed feed = feeds.get(key);
        if (feed != null) {
            feed.lastRendered = nowMs;
        }
    }

    /**
     * De beelden die al {@code disposeAfterMs} niet op het scherm stonden. Ze gaan uit de planner;
     * wie dit aanroept, ruimt hun plaatjes op.
     */
    public List<Long> expired(long nowMs) {
        List<Long> gone = new ArrayList<>();
        feeds.entrySet().removeIf(entry -> {
            if (nowMs - entry.getValue().lastWanted > disposeAfterMs) {
                gone.add(entry.getKey());
                return true;
            }
            return false;
        });
        return gone;
    }

    /** Staat er nog iets in de planner? */
    public boolean isEmpty() {
        return feeds.isEmpty();
    }

    /** Alles vergeten (bij het verlaten van een wereld). */
    public void clear() {
        feeds.clear();
    }
}
