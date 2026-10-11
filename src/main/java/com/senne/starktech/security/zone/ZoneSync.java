package com.senne.starktech.security.zone;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.network.ZoneSyncPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Stuurt elke speler de zones die hij mag zien, zodat zijn spel de rand kan tekenen (zie
 * client/zone/ZoneBorderRenderer).
 *
 * Jouw keuze (8 oktober): de rand zien alleen de eigenaar en zijn vrienden (team en vertrouwde
 * spelers). Een vreemde krijgt de zone dus nooit te zien; hij merkt pas iets als een blok niet breekt.
 *
 * Twee keer per seconde kijken we wat elke speler zou moeten zien. Is dat hetzelfde als de vorige
 * keer, dan sturen we niets.
 */
public final class ZoneSync {
    private ZoneSync() {}

    /** Om de zoveel ticks kijken we opnieuw. */
    private static final int EVERY = 10;
    /**
     * Zones waarvan de kern verder weg staat dan dit (blokken) plus ZONE_REACH, sturen we niet. (Een
     * zone kan tot ZONE_REACH van zijn kern liggen: dan sta je soms aan zijn rand, ver van de kern.)
     */
    private static final double RANGE = 256.0;

    /** Wat elke speler het laatst kreeg (als één getal, de "hash"), zodat we niet steeds hetzelfde sturen. */
    private static final Map<UUID, Integer> LAST_SENT = new HashMap<>();

    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % EVERY != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            send(player, false);
        }
    }

    /** Stuurt de speler zijn zones, als die anders zijn dan de vorige keer (of altijd, met {@code force}). */
    public static void send(ServerPlayer player, boolean force) {
        List<ZoneSyncPayload.View> views = visibleTo(player);
        int signature = views.hashCode();
        Integer last = LAST_SENT.get(player.getUUID());
        if (!force && last != null && last == signature) {
            return;
        }
        LAST_SENT.put(player.getUUID(), signature);
        ModNetwork.sendTo(player, new ZoneSyncPayload(views));
    }

    /** De zones in de buurt waar deze speler mag bouwen (en dus de rand van mag zien). */
    public static List<ZoneSyncPayload.View> visibleTo(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<ZoneSyncPayload.View> views = new ArrayList<>();
        double range = RANGE + Math.round(StarkConfig.Security.ZONE_REACH);
        for (ZoneData.Zone zone : ZoneData.get(level).in(level.dimension())) {
            if (views.size() >= ZoneSyncPayload.MAX_ZONES) {
                break;
            }
            if (zone.owner() == null || !zone.isBuilder(level, player)
                    || zone.core().pos().distToCenterSqr(player.position()) > range * range) {
                continue;
            }
            // Zoals hij nu beschermt (is het bereik in de config kleiner gemaakt, dan is de rand dat ook).
            views.add(new ZoneSyncPayload.View(zone.core().pos(), zone.active(), zone.boxesInReach()));
        }
        return views;
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_SENT.clear();
    }
}
