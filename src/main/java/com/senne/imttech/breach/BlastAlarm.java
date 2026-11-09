package com.senne.imttech.breach;

import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.network.ChargeAlertPayload;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.EventLog;
import com.senne.imttech.security.zone.ZoneData;
import com.senne.imttech.security.zone.Zones;
import com.senne.imttech.weapons.RemoteChargeBlockEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Het alarm van een zone bij explosieven (hoofdstuk 10, jouw keuze: "plakt iemand een lading op of in je zone, dan zegt
 * I.M.T waar, aan jou en je vertrouwde spelers"). Eerst was dat voor C4; sinds 26 oktober voor de Remote Frag (jouw
 * keuze: "alarm en schaar, zoals C4").
 *
 * <ul>
 *   <li><b>Geplakt</b>: een Remote Frag in een zone of tot 3 blokken ervan (door iemand die er niet mag bouwen): I.M.T
 *       zegt waar, aan de eigenaar en zijn vrienden die online zijn, en ze zien een rode markering "FRAG!" tot hij weg
 *       is. Niet wie het was: in het echt weet je dat ook niet.</li>
 *   <li><b>Ontploft</b> (een frag, of TNT of de satelliet die in de zone iets kapotmaken): hoeveel blokken van de zone
 *       er weg zijn. Is de eigenaar offline, dan hoort hij het als hij weer inlogt ("While you were away: ...").</li>
 *   <li><b>Onschadelijk gemaakt</b> (met een schaar): de markering gaat weg, en wie hem plakte hoort het.</li>
 * </ul>
 * Alles staat ook in het logboek van de kern (rode regels, ALARM).
 */
public final class BlastAlarm {
    private BlastAlarm() {}

    /** Zo ver van een zone telt een lading nog als "op je zone" (blokken). */
    public static final double NEAR = 3.0;
    /** Zo lang na het inloggen wacht I.M.T met het verslag (ticks), zodat je bril eerst opstart. */
    private static final int REPORT_DELAY = 100;

    /** Wie net inlogde en nog een verslag moet horen: speler -> wanneer (servertick). */
    private static final Map<UUID, Integer> REPORT_AT = new HashMap<>();

    // ================================================================ Geplakt

    /** Een Remote Frag net geplakt: ligt hij op een zone van een ander, dan gaat het alarm af. */
    public static void planted(ServerLevel level, BlockPos pos, RemoteChargeBlockEntity charge) {
        ZoneData.Zone zone = Zones.activeNear(level, pos, NEAR);
        if (zone == null || zone.isBuilder(level, charge.owner(), charge.ownerName())) {
            return; // geen zone, of het is je eigen lading in je eigen zone
        }
        charge.setAlerted();
        log(level, zone, "Remote Frag planted at " + xyz(pos));
        tellBuilders(level, zone, Component.translatable("zone.imttech.frag.planted", pos.getX(), pos.getY(), pos.getZ()),
                ImtMessagePayload.Level.ALERT);
        marker(level, zone, pos, true);
    }

    // ================================================================ Ontploft of onschadelijk

    /**
     * Ontploft. {@code zone} is de zone die de klap raakte (of null), {@code blocks} hoeveel blokken van die zone er weg
     * zijn, {@code alerted} of er een markering stond (die gaat dan weg).
     */
    public static void blasted(ServerLevel level, BlockPos pos, @Nullable ZoneData.Zone zone, int blocks, boolean alerted, String what) {
        if (zone == null) {
            return;
        }
        if (alerted) {
            marker(level, zone, pos, false);
        }
        log(level, zone, what + " blast at " + xyz(pos) + ": " + blocks + (blocks == 1 ? " block" : " blocks") + " destroyed");
        if (blocks <= 0) {
            return;
        }
        tellBuilders(level, zone, Component.translatable("zone.imttech.blast", what, blocks), ImtMessagePayload.Level.ALERT);
        UUID owner = zone.owner();
        if (owner != null && level.getServer().getPlayerList().getPlayer(owner) == null) {
            ZoneData.get(level).addAwayReport(owner, blocks);
        }
    }

    /** Met een schaar onschadelijk gemaakt: de markering weg, en wie hem plakte hoort het. */
    public static void defused(ServerLevel level, BlockPos pos, RemoteChargeBlockEntity charge, ServerPlayer by) {
        ZoneData.Zone zone = Zones.activeNear(level, pos, NEAR);
        if (zone != null) {
            if (charge.alerted()) {
                marker(level, zone, pos, false);
            }
            log(level, zone, "Remote Frag at " + xyz(pos) + " defused");
        }
        if (charge.owner() != null && !charge.owner().equals(by.getUUID())) {
            ServerPlayer placer = level.getServer().getPlayerList().getPlayer(charge.owner());
            if (placer != null) {
                ImtServer.send(placer, Component.translatable("zone.imttech.frag.your_defused", pos.getX(), pos.getY(), pos.getZ()),
                        ImtMessagePayload.Level.WARNING);
            }
        }
    }

    /**
     * Hoofdstuk 69: met sneak + lege hand van de muur gehaald (nog scherp). De markering gaat weg (hij hangt daar niet
     * meer), het logboek zegt het, en de eigenaar hoort het (als iemand anders het deed).
     */
    public static void takenOff(ServerLevel level, BlockPos pos, RemoteChargeBlockEntity charge, ServerPlayer by) {
        ZoneData.Zone zone = Zones.activeNear(level, pos, NEAR);
        if (zone != null) {
            if (charge.alerted()) {
                marker(level, zone, pos, false);
            }
            log(level, zone, "Remote Frag at " + xyz(pos) + " taken off by " + by.getGameProfile().getName());
        }
        if (charge.owner() != null && !charge.owner().equals(by.getUUID())) {
            ServerPlayer placer = level.getServer().getPlayerList().getPlayer(charge.owner());
            if (placer != null) {
                ImtServer.send(placer, Component.translatable("zone.imttech.frag.your_taken", pos.getX(), pos.getY(), pos.getZ()),
                        ImtMessagePayload.Level.WARNING);
            }
        }
    }

    // ================================================================ Wie het hoort

    /** I.M.T zegt het tegen de eigenaar en zijn vrienden die online zijn. */
    private static void tellBuilders(ServerLevel level, ZoneData.Zone zone, Component message, ImtMessagePayload.Level kind) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (zone.isBuilder(level, player)) {
                ImtServer.send(player, message, kind);
            }
        }
    }

    /** De rode markering "FRAG!" aan of uit, bij de eigenaar en zijn vrienden. */
    private static void marker(ServerLevel level, ZoneData.Zone zone, BlockPos pos, boolean on) {
        ChargeAlertPayload payload = new ChargeAlertPayload(GlobalPos.of(level.dimension(), pos.immutable()), on);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (zone.isBuilder(level, player)) {
                ModNetwork.sendTo(player, payload);
            }
        }
    }

    /** Een rode regel in het logboek van de kern (als zijn chunk geladen is). */
    private static void log(ServerLevel level, ZoneData.Zone zone, String text) {
        ServerLevel coreLevel = level.getServer().getLevel(zone.core().dimension());
        if (coreLevel != null && coreLevel.isLoaded(zone.core().pos())
                && coreLevel.getBlockEntity(zone.core().pos()) instanceof SecurityCoreBlockEntity core) {
            core.logEvent(EventLog.Type.ALARM, text);
        }
    }

    private static String xyz(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    // ================================================================ Terwijl je weg was

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            REPORT_AT.put(player.getUUID(), player.getServer().getTickCount() + REPORT_DELAY);
        }
    }

    /** Een paar seconden na het inloggen: het verslag (als er iets gebeurde). */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (REPORT_AT.isEmpty()) {
            return;
        }
        int now = event.getServer().getTickCount();
        for (Iterator<Map.Entry<UUID, Integer>> it = REPORT_AT.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> entry = it.next();
            if (now < entry.getValue()) {
                continue;
            }
            it.remove();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            int[] report = ZoneData.get(event.getServer()).takeAwayReport(player.getUUID());
            if (report != null) {
                ImtServer.send(player, Component.translatable("zone.imttech.blast.report", report[0], report[1]),
                        ImtMessagePayload.Level.ALERT);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        REPORT_AT.clear();
    }
}
