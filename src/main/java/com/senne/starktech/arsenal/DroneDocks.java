package com.senne.starktech.arsenal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.GlobalPos;

/**
 * Welke drone op welke drone-dock staat, voor alle docks die nu geladen zijn.
 *
 * Een drone mag maar op één dock tegelijk staan. Een dock die een vrije drone zoekt, kijkt hier
 * welke al een plek hebben. Net als SecurityCores onthouden we alleen wat geladen is: een dock
 * in een stuk wereld dat niet geladen is, meldt zijn drone weer aan zodra hij laadt (en is die
 * intussen door een andere dock geclaimd, dan laat hij hem los).
 */
public final class DroneDocks {
    private DroneDocks() {}

    private static final Map<UUID, GlobalPos> CLAIMS = new HashMap<>();

    /** Deze dock wil deze drone. Lukt niet als een andere dock hem al heeft. */
    static boolean claim(UUID drone, GlobalPos dock) {
        GlobalPos holder = CLAIMS.putIfAbsent(drone, dock);
        return holder == null || holder.equals(dock);
    }

    /** Heeft deze dock deze drone (nog)? */
    static boolean holds(UUID drone, GlobalPos dock) {
        return dock.equals(CLAIMS.get(drone));
    }

    /** Staat deze drone al op een (geladen) dock? */
    static boolean isClaimed(UUID drone) {
        return CLAIMS.containsKey(drone);
    }

    /** De dock laat zijn drone los (afgebroken, uitgeladen, of de drone is er niet meer). */
    static void release(UUID drone, GlobalPos dock) {
        CLAIMS.remove(drone, dock);
    }

    /** Bij het afsluiten van de server: alles vergeten. */
    public static void clear() {
        CLAIMS.clear();
    }
}
