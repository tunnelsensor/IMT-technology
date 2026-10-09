package com.senne.starktech.c4;

import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.C4AlertPayload;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.os.EventLog;
import com.senne.starktech.security.zone.ZoneData;
import com.senne.starktech.security.zone.Zones;
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
 * Het alarm van C4 (hoofdstuk 10, jouw keuze: "plakt iemand C4 op of in je zone, dan zegt E.D.I.T.H.
 * waar, aan jou en je vertrouwde spelers").
 *
 * <ul>
 *   <li><b>Geplakt</b> in een zone of tot 3 blokken ervan (door iemand die er niet mag bouwen):
 *       E.D.I.T.H. zegt waar, aan de eigenaar en zijn vrienden die online zijn, en ze zien een rode
 *       markering "C4!" tot hij weg is. Niet wie het was: in het echt weet je dat ook niet.</li>
 *   <li><b>Ontploft</b> (C4, of TNT of de satelliet die in de zone iets kapotmaken): hoeveel blokken van
 *       de zone er weg zijn. Is de eigenaar offline, dan hoort
 *       hij het als hij weer inlogt ("While you were away: ...").</li>
 *   <li><b>Onschadelijk gemaakt</b> (met een schaar): de markering gaat weg, en wie hem plakte hoort het.</li>
 * </ul>
 * Alles staat ook in het logboek van de kern (rode regels, ALARM).
 */
public final class C4Alarm {
    private C4Alarm() {}

    /** Zo ver van een zone telt een C4 nog als "op je zone" (blokken). */
    public static final double NEAR = 3.0;
    /** Zo lang na het inloggen wacht E.D.I.T.H. met het verslag (ticks), zodat je bril eerst opstart. */
    private static final int REPORT_DELAY = 100;

    /** Wie net inlogde en nog een verslag moet horen: speler -> wanneer (servertick). */
    private static final Map<UUID, Integer> REPORT_AT = new HashMap<>();

    // ================================================================ Geplakt

    /** Net geplakt: ligt hij op een zone van een ander, dan gaat het alarm af. */
    public static void planted(ServerLevel level, BlockPos pos, C4BlockEntity c4) {
        ZoneData.Zone zone = Zones.activeNear(level, pos, NEAR);
        if (zone == null || zone.isBuilder(level, c4.placer(), c4.placerName())) {
            return; // geen zone, of het is je eigen C4 in je eigen zone
        }
        c4.setAlerted();
        String what = c4.kind() == C4Block.Kind.TIMED && c4.explodeAt() >= 0
                ? "timer " + C4Item.clock((int) Math.ceil(c4.remaining(level.getGameTime()) / 20.0)) : "remote";
        log(level, zone, "C4 planted at " + xyz(pos) + " (" + what + ")");
        tellBuilders(level, zone, Component.translatable("zone.starktech.c4.planted", pos.getX(), pos.getY(), pos.getZ(), what),
                EdithMessagePayload.Level.ALERT);
        marker(level, zone, pos, true);
    }

    // ================================================================ Ontploft of onschadelijk

    /**
     * Ontploft. {@code zone} is de zone die de klap raakte (of null), {@code blocks} hoeveel blokken
     * van die zone er weg zijn.
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
        tellBuilders(level, zone, Component.translatable("zone.starktech.c4.blast", what, blocks), EdithMessagePayload.Level.ALERT);
        UUID owner = zone.owner();
        if (owner != null && level.getServer().getPlayerList().getPlayer(owner) == null) {
            ZoneData.get(level).addAwayReport(owner, blocks);
        }
    }

    /** Met een schaar onschadelijk gemaakt: de markering weg, en wie hem plakte hoort het. */
    public static void defused(ServerLevel level, BlockPos pos, C4BlockEntity c4, ServerPlayer by) {
        ZoneData.Zone zone = Zones.activeNear(level, pos, NEAR);
        if (zone != null) {
            if (c4.alerted()) {
                marker(level, zone, pos, false);
            }
            log(level, zone, "C4 at " + xyz(pos) + " defused");
        }
        if (c4.placer() != null && !c4.placer().equals(by.getUUID())) {
            ServerPlayer placer = level.getServer().getPlayerList().getPlayer(c4.placer());
            if (placer != null) {
                EdithServer.send(placer, Component.translatable("zone.starktech.c4.your_defused", pos.getX(), pos.getY(), pos.getZ()),
                        EdithMessagePayload.Level.WARNING);
            }
        }
    }

    // ================================================================ Wie het hoort

    /** E.D.I.T.H. zegt het tegen de eigenaar en zijn vrienden die online zijn. */
    private static void tellBuilders(ServerLevel level, ZoneData.Zone zone, Component message, EdithMessagePayload.Level kind) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (zone.isBuilder(level, player)) {
                EdithServer.send(player, message, kind);
            }
        }
    }

    /** De rode markering "C4!" aan of uit, bij de eigenaar en zijn vrienden. */
    private static void marker(ServerLevel level, ZoneData.Zone zone, BlockPos pos, boolean on) {
        C4AlertPayload payload = new C4AlertPayload(GlobalPos.of(level.dimension(), pos.immutable()), on);
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
                EdithServer.send(player, Component.translatable("zone.starktech.c4.report", report[0], report[1]),
                        EdithMessagePayload.Level.ALERT);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        REPORT_AT.clear();
    }
}
