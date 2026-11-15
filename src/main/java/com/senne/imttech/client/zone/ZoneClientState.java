package com.senne.imttech.client.zone;

import com.senne.imttech.network.ZoneSyncPayload;
import java.util.List;

/**
 * Wat jouw spel weet over zones: alleen die waar jij mag bouwen (je eigen zones en die van wie jou
 * vertrouwt), zoals de server ze stuurt (zie ZoneSync). Daarmee tekent ZoneBorderRenderer de rand.
 */
public final class ZoneClientState {
    private ZoneClientState() {}

    private static List<ZoneSyncPayload.View> zones = List.of();
    /** Telt elke nieuwe lijst; de tekenaar rekent de wanden opnieuw uit als dit verandert. */
    private static int version;

    public static void set(List<ZoneSyncPayload.View> newZones) {
        zones = List.copyOf(newZones);
        version++;
    }

    public static List<ZoneSyncPayload.View> zones() {
        return zones;
    }

    public static int version() {
        return version;
    }

    // ================================================================ Hoofdstuk 116: de pings van de Autoscan

    /** Wat de laatste Autoscan zag (de pings, de weg, of je de grot erbij kunt nemen); null = niets. */
    private static @org.jetbrains.annotations.Nullable com.senne.imttech.network.ZoneScanMarksPayload scan;
    /** Wanneer de pings kwamen (voor het "uitdijen" bij het begin) en tot wanneer ze blijven staan. */
    private static long scanSince;
    private static long scanUntil;
    /** In welke wereld (Overworld, Nether...) de pings liggen: in een andere wereld tekenen we ze niet. */
    private static @org.jetbrains.annotations.Nullable net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> scanLevel;

    /** Wat de server stuurde na een Autoscan (zonder pings en weg: alles weg). */
    public static void setScanMarks(com.senne.imttech.network.ZoneScanMarksPayload payload) {
        boolean empty = payload.marks().isEmpty() && payload.path().isEmpty();
        scan = empty ? null : payload;
        scanSince = net.minecraft.Util.getMillis();
        scanUntil = scanSince + Math.max(1, payload.seconds()) * 1000L;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        scanLevel = level == null ? null : level.dimension();
    }

    /** Wat de laatste Autoscan zag, als het er nog staat en in de wereld waar je nu bent; anders null. */
    public static @org.jetbrains.annotations.Nullable com.senne.imttech.network.ZoneScanMarksPayload scan() {
        if (scan != null && net.minecraft.Util.getMillis() > scanUntil) {
            scan = null;
        }
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return level != null && level.dimension().equals(scanLevel) ? scan : null;
    }

    public static long scanSince() {
        return scanSince;
    }

    public static long scanUntil() {
        return scanUntil;
    }

    /** Uitgelogd: niets meer onthouden (op een andere server liggen de zones ergens anders). */
    public static void clear() {
        set(List.of());
        scan = null;
    }
}
