package com.senne.starktech.client.zone;

import com.senne.starktech.network.ZoneSyncPayload;
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

    /** Uitgelogd: niets meer onthouden (op een andere server liggen de zones ergens anders). */
    public static void clear() {
        set(List.of());
    }
}
