package com.senne.imttech.security.zone;

import com.senne.imttech.network.HiddenNamesPayload;
import com.senne.imttech.network.ModNetwork;
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
import org.jetbrains.annotations.Nullable;

/**
 * Hoofdstuk 99 (jouw wens: "vanaf je in een protected zone bent staat je nametag uit, dit maakt dat je niet zo makkelijk
 * iemand zijn bunker vindt doordat je de nametag ziet").
 *
 * Sta je in een <b>actieve</b> zone (een security core met stroom, zie Zones.activeAt), dan ziet niemand je naam boven je
 * hoofd, behalve wie in die zone mag bouwen: de eigenaar, zijn team en zijn vertrouwde spelers (en wie in creative speelt).
 * Jouw keuze: "team + vertrouwden". Zij zien dus ook de naam van een indringer in hun basis; een vreemde ziet geen naam, ook
 * niet van de eigenaar, en vindt de basis dus niet meer aan een naam die door de muren schijnt. Buiten de zone, of zonder
 * stroom, ziet iedereen je naam weer.
 *
 * Twee keer per seconde rekent de server per speler uit van wie hij de naam niet ziet, en stuurt dat (alleen als het
 * veranderde). Zijn spel tekent die namen dan niet (client/zone/HiddenNames). De tablijst blijft zoals hij is.
 */
public final class ZoneNames {
    private ZoneNames() {}

    /** Om de zoveel ticks kijken we opnieuw. */
    private static final int EVERY = 10;

    /** Wat elke speler het laatst kreeg, zodat we niet steeds hetzelfde sturen. */
    private static final Map<UUID, List<Integer>> LAST_SENT = new HashMap<>();

    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % EVERY != 0) {
            return;
        }
        List<ServerPlayer> players = event.getServer().getPlayerList().getPlayers();
        // Eerst: wie staat er in welke zone (de meeste spelers staan in geen enkele).
        Map<ServerPlayer, ZoneData.Zone> inZone = new HashMap<>();
        for (ServerPlayer player : players) {
            ZoneData.Zone zone = zoneOf(player);
            if (zone != null) {
                inZone.put(player, zone);
            }
        }
        for (ServerPlayer viewer : players) {
            List<Integer> hidden = new ArrayList<>();
            for (Map.Entry<ServerPlayer, ZoneData.Zone> entry : inZone.entrySet()) {
                if (hides(entry.getKey(), entry.getValue(), viewer)) {
                    hidden.add(entry.getKey().getId());
                }
            }
            hidden.sort(Integer::compare);
            if (!hidden.equals(LAST_SENT.get(viewer.getUUID()))) {
                LAST_SENT.put(viewer.getUUID(), hidden);
                ModNetwork.sendTo(viewer, new HiddenNamesPayload(hidden));
            }
        }
    }

    /** De actieve zone waar deze speler nu in staat (met zijn voeten), of null. Een toeschouwer telt niet. */
    public static @Nullable ZoneData.Zone zoneOf(ServerPlayer player) {
        if (player.isSpectator()) {
            return null;
        }
        return Zones.activeAt(player.serverLevel(), player.blockPosition());
    }

    /** Ziet {@code viewer} de naam van {@code player} nu niet? (Ook voor de tests.) */
    public static boolean hiddenFor(ServerPlayer player, ServerPlayer viewer) {
        ZoneData.Zone zone = zoneOf(player);
        return zone != null && hides(player, zone, viewer);
    }

    private static boolean hides(ServerPlayer player, ZoneData.Zone zone, ServerPlayer viewer) {
        if (player == viewer || player.level() != viewer.level()) {
            return false;
        }
        ServerLevel level = viewer.serverLevel();
        return !Zones.mayBuild(level, zone, viewer);
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_SENT.clear();
    }
}
