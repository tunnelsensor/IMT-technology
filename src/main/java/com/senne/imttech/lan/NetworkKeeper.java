package com.senne.imttech.lan;

import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import com.senne.imttech.security.os.SecurityOs;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Houdt het netwerk van een kern geladen zolang iemand het gebruikt (hoofdstuk 94, jouw wens van 12 november: "camera range
 * moet evenveel zijn als de cabel lang is dus een verbonde camera moet altijd zicht hebben").
 *
 * Het netwerk zoekt zijn apparaten langs de kabels (LanGrid), maar alleen door stukken wereld die de server geladen heeft:
 * die rond de spelers. Een camera aan het eind van een lange kabel, waar niemand in de buurt is, viel zo uit het net (niet
 * in de app Cameras, "NO SIGNAL" op een schermmuur). Daarom onthoudt elke kern in welke stukken wereld zijn net ligt, en
 * waar het verder gaat (SecurityCoreBlockEntity.networkChunks). Zolang iemand het net gebruikt, houden we die stukken
 * geladen, met een "ticket" zoals de laptop dat doet voor de kern:
 * <ul>
 *   <li>iemand heeft de desktop van die kern open (op de kern, op een Computer, met de laptop of zijn telefoon);</li>
 *   <li>iemand staat bij een schermmuur van die kern (binnen 96 blokken van een Camera Monitor).</li>
 * </ul>
 * Alleen geladen, niet levend: daar tikt niets extra's (een boerderij ver weg draait er niet van). Komt het net in een stuk
 * dat nu geladen werd verder, dan vindt de volgende zoektocht (om de twee seconden) dat ook, en laden we het volgende stuk:
 * zo volgt het de kabel tot het eind (hooguit LanGrid.MAX_NETWORK_CHUNKS stukken).
 */
public final class NetworkKeeper {
    private NetworkKeeper() {}

    /** Zo vaak (ticks) kijken we welke netten in gebruik zijn. */
    private static final int EVERY = 20;
    /** Zo dichtbij een Camera Monitor telt als "gebruikt het net" (blokken). */
    private static final double MONITOR_RANGE = 96.0;

    /** Het ticket dat een stuk van het net geladen houdt (verloopt vanzelf na 5 seconden; elke seconde vernieuwd). */
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("imttech_network", Comparator.comparingLong(ChunkPos::toLong), 100);

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        // Niet tijdens de automatische tests: daar blijven desktops van testspelers open staan, en dan bleven de netten van
        // oude tests geladen. Dat veranderde de wereld van latere tests (een raiddrone landde op het dak van een andere
        // testarena, want in een geladen stuk wereld ziet hij de grond, in een niet geladen stuk niet).
        if (server.getTickCount() % EVERY != 0 || !LanGrid.required()
                || server instanceof net.minecraft.gametest.framework.GameTestServer) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (BlockPos core : inUse(level)) {
                if (level.isLoaded(core) && level.getBlockEntity(core) instanceof SecurityCoreBlockEntity entity) {
                    keepLoaded(level, entity);
                }
            }
        }
    }

    /** De kernen in deze wereld waarvan iemand nu het net gebruikt. */
    static Set<BlockPos> inUse(ServerLevel level) {
        Set<BlockPos> cores = new HashSet<>();
        for (SecurityOs.Session session : SecurityOs.sessions()) {
            if (session.dimension() == level.dimension()) {
                cores.add(session.core());
            }
        }
        if (level.players().isEmpty()) {
            return cores;
        }
        for (BlockPos pos : SecurityCores.loadedCores(level)) {
            if (cores.contains(pos) || !(level.getBlockEntity(pos) instanceof SecurityCoreBlockEntity core)) {
                continue;
            }
            for (BlockPos monitor : SecurityDevices.knownMonitors(level, core)) {
                if (level.getNearestPlayer(monitor.getX() + 0.5, monitor.getY() + 0.5, monitor.getZ() + 0.5, MONITOR_RANGE,
                        false) != null) {
                    cores.add(pos);
                    break;
                }
            }
        }
        return cores;
    }

    /** De stukken wereld van het net van deze kern geladen houden (de volgende 5 seconden). */
    public static void keepLoaded(ServerLevel level, SecurityCoreBlockEntity core) {
        for (long chunk : core.networkChunks()) {
            ChunkPos pos = new ChunkPos(chunk);
            level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
        }
    }
}
