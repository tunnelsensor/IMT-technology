package com.senne.imttech.energy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Waar de kabels van elke soort liggen (hoofdstuk 38): opvragen, leggen en weghalen. Wat PowerCables vroeger voor de
 * stroomkabel deed, doet deze klasse voor alle drie de soorten (zie CableKind); PowerCables gebruikt hem voor de
 * stroomkabel, zodat alles wat daar al naar vroeg gewoon blijft werken.
 *
 * Een kabel ligt op een blokplek (zie CableCells). Op die plek mag ook een blok staan: dan zit de kabel in dat blok (in een
 * muur, een vloer). Elke soort heeft zijn eigen lijst per chunk, dus op één plek kunnen een stroomkabel, een netwerkkabel
 * en een PoE-kabel tegelijk liggen.
 */
public final class Cables {
    private Cables() {}

    /** Een chunk waarvan de kabels van deze soort deze tik veranderd zijn (zie store). */
    private record Pending(LevelChunk chunk, CableKind kind) {}

    /** Dit slot beschermt de wachtrij: kabels kunnen ook gelegd worden terwijl een gebouw in de wereld komt. */
    private static final Object LOCK = new Object();
    private static final Set<Pending> TO_SYNC = new LinkedHashSet<>();

    // ================================================================ Opvragen

    /** Ligt hier een kabel van deze soort? (Een chunk die niet geladen is: nee.) */
    public static boolean has(CableKind kind, Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        // Eerst hasData: getData op een chunk zonder lijst maakt er een (lege) aan, en die gaat dan naar iedereen.
        return chunk.hasData(kind.attachment()) && chunk.getData(kind.attachment()).contains(pos);
    }

    /** Ligt hier een kabel die stroom doorgeeft (een stroomkabel of een PoE-kabel)? */
    public static boolean carriesPower(Level level, BlockPos pos) {
        return has(CableKind.POWER, level, pos) || has(CableKind.POE, level, pos);
    }

    /** Ligt hier een kabel die netwerk doorgeeft (een netwerkkabel of een PoE-kabel)? */
    public static boolean carriesNetwork(Level level, BlockPos pos) {
        return has(CableKind.NETWORK, level, pos) || has(CableKind.POE, level, pos);
    }

    /** Ligt hier een kabel, van welke soort ook? */
    public static boolean any(Level level, BlockPos pos) {
        for (CableKind kind : CableKind.values()) {
            if (has(kind, level, pos)) {
                return true;
            }
        }
        return false;
    }

    /** De soorten die hier liggen, altijd in dezelfde volgorde: stroom, netwerk, PoE. */
    public static List<CableKind> at(Level level, BlockPos pos) {
        List<CableKind> kinds = new ArrayList<>(3);
        for (CableKind kind : CableKind.values()) {
            if (has(kind, level, pos)) {
                kinds.add(kind);
            }
        }
        return kinds;
    }

    /** Alle kabels van deze soort in deze chunk (leeg als er geen zijn). */
    public static CableCells in(CableKind kind, LevelChunk chunk) {
        return chunk.hasData(kind.attachment()) ? chunk.getData(kind.attachment()) : CableCells.EMPTY;
    }

    // ================================================================ Leggen en weghalen

    /** Legt een stuk kabel van deze soort op deze plek. Geeft false als er al een lag. */
    public static boolean add(CableKind kind, ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        CableCells cells = in(kind, chunk);
        if (cells.contains(pos)) {
            return false;
        }
        store(kind, chunk, cells.with(pos.immutable()));
        return true;
    }

    /** Haalt het stuk kabel van deze soort op deze plek weg. Geeft false als er geen lag. */
    public static boolean remove(CableKind kind, ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        CableCells cells = in(kind, chunk);
        if (!cells.contains(pos)) {
            return false;
        }
        store(kind, chunk, cells.without(pos));
        return true;
    }

    private static void store(CableKind kind, LevelChunk chunk, CableCells cells) {
        if (cells.isEmpty()) {
            chunk.removeData(kind.attachment());
        } else {
            chunk.setData(kind.attachment(), cells);
        }
        chunk.setUnsaved(true);
        // Naar iedereen die deze chunk ziet, zodat ze de kabel zien verschijnen of verdwijnen. Dat doen we aan het eind
        // van de servertik, één keer per chunk en soort: een gebouw legt honderden kabels tegelijk, en anders ging de hele
        // kabellijst per stukje kabel opnieuw de lijn over.
        synchronized (LOCK) {
            TO_SYNC.add(new Pending(chunk, kind));
        }
        // Een stroomnet (en straks een netwerk) is anders geworden: opnieuw zoeken.
        PowerGrid.changed();
    }

    // ================================================================ Doorsturen

    /** Na elke servertik (zie PowerCables.onServerTick): de chunks met nieuwe of weggehaalde kabels doorsturen. */
    static void syncChanged() {
        List<Pending> pending;
        synchronized (LOCK) {
            if (TO_SYNC.isEmpty()) {
                return;
            }
            pending = new ArrayList<>(TO_SYNC);
            TO_SYNC.clear();
        }
        for (Pending entry : pending) {
            if (entry.chunk().getLevel() instanceof ServerLevel level && anyoneHas(level, entry.chunk())) {
                entry.chunk().syncData(entry.kind().attachment());
            }
        }
    }

    /**
     * Heeft een speler deze chunk al binnen? Een chunk die net gemaakt is (met een gebouw erin) staat meestal nog in de
     * wachtrij om verstuurd te worden: die krijgt de kabels vanzelf mee als hij aankomt. Stuur je ze eerder, dan gooit de
     * speler ze weg ("unknown chunk").
     */
    private static boolean anyoneHas(ServerLevel level, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        if (level.getChunkSource().getChunkNow(pos.x, pos.z) != chunk) {
            return false; // al weer uit het geheugen
        }
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(pos, false)) {
            if (player.connection != null && !player.connection.chunkSender.isPending(pos.toLong())) {
                return true;
            }
        }
        return false;
    }

    /** Bij het afsluiten: de wachtrij vergeten. */
    static void clear() {
        synchronized (LOCK) {
            TO_SYNC.clear();
        }
    }
}
