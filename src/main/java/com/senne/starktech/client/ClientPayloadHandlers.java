package com.senne.starktech.client;

import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.network.BeamEffectPayload;
import com.senne.starktech.network.DroneCameraPayload;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.EdithStatusPayload;
import com.senne.starktech.network.OreScanPayload;
import com.senne.starktech.network.PingMarkerPayload;
import com.senne.starktech.network.SpyReportPayload;
import com.senne.starktech.network.SpyStatusPayload;
import net.minecraft.client.Minecraft;
import com.senne.starktech.registry.ModSounds;
import java.util.HashSet;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Wat jouw spel doet als het een bericht van de server krijgt.
 */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {}

    /** Een straal tekenen (drone-laser of satelliet). */
    public static void onBeam(BeamEffectPayload payload, IPayloadContext context) {
        BeamEffects.play(payload);
    }

    /** "Kijk door deze drone" (of stop ermee). */
    public static void onDroneCamera(DroneCameraPayload payload, IPayloadContext context) {
        DroneCamera.onServerMessage(payload.droneId(), payload.control());
    }

    public static void onMessage(EdithMessagePayload payload, IPayloadContext context) {
        EdithClientState.addNotification(payload.message(), payload.level());
        switch (payload.level()) {
            case BOOT -> EdithClientEvents.playUi(ModSounds.EDITH_BOOT.get(), 1.0f);
            case INFO -> EdithClientEvents.playUi(ModSounds.EDITH_NOTIFY.get(), 1.0f);
            case WARNING -> EdithClientEvents.playUi(ModSounds.EDITH_ALERT.get(), 1.2f);
            case ALERT -> EdithClientEvents.playUi(ModSounds.EDITH_DENIED.get(), 1.0f);
            case COMMS -> EdithClientEvents.playUi(ModSounds.COMMS_MESSAGE.get(), 1.0f);
        }
    }

    public static void onStatus(EdithStatusPayload payload, IPayloadContext context) {
        long now = Util.getMillis();
        EdithClientState.statusMillis = now;
        EdithClientState.swarmDrones = payload.swarmDrones();
        EdithClientState.guardDrones = payload.guardDrones();
        EdithClientState.cooldowns = payload.cooldowns().stream().mapToInt(Integer::intValue).toArray();
        EdithClientState.markedIds = new HashSet<>(payload.marked());
        EdithClientState.strikeTarget = payload.strikeTarget().orElse(null);
        EdithClientState.strikeAtMillis = now + payload.strikeTicks() * 50L;
    }

    public static void onPing(PingMarkerPayload payload, IPayloadContext context) {
        long now = Util.getMillis();
        EdithClientState.pings.removeIf(ping -> ping.sender().equals(payload.sender()));
        EdithClientState.pings.add(new EdithClientState.Ping(payload.sender(), payload.pos(), now,
                now + EdithBalance.PING_DURATION * 50L));
        var player = context.player();
        boolean own = player.getGameProfile().getName().equals(payload.sender());
        int distance = (int) Math.round(Math.sqrt(player.distanceToSqr(payload.pos())));
        EdithClientState.addNotification(own
                ? Component.translatable("edith.starktech.msg.ping_placed")
                : Component.translatable("edith.starktech.msg.ping_received", payload.sender(), distance), EdithMessagePayload.Level.COMMS);
        EdithClientEvents.playUi(ModSounds.COMMS_PING.get(), own ? 1.4f : 1.0f);
    }

    /** Wat de spionagedrone nu ziet (twee keer per seconde). */
    public static void onSpyStatus(SpyStatusPayload payload, IPayloadContext context) {
        EdithClientState.spyActive = payload.active();
        EdithClientState.spyTarget = payload.targetName();
        EdithClientState.spyPos = payload.pos().orElse(null);
        EdithClientState.spyDimension = payload.dimension();
        EdithClientState.spyTicksLeft = payload.ticksLeft();
        EdithClientState.spyHealth = payload.health();
        EdithClientState.spyMaxHealth = payload.maxHealth();
        EdithClientState.spyLogLines = payload.logLines();
    }

    /** Het rapport van de spionagedrone: bewaren en het scherm openen. */
    public static void onSpyReport(SpyReportPayload payload, IPayloadContext context) {
        EdithClientState.spyReportTarget = payload.targetName();
        EdithClientState.spyReport = payload.lines();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new SpyReportScreen(payload.targetName(), payload.lines()));
        }
        EdithClientEvents.playUi(ModSounds.COMMS_MESSAGE.get(), 0.9f);
    }

    public static void onOreScan(OreScanPayload payload, IPayloadContext context) {
        EdithClientEvents.playUi(ModSounds.EDITH_SCAN.get(), 1.0f);
        int found = OreScanner.scan(payload.radius());
        EdithClientState.addNotification(Component.translatable("edith.starktech.msg.scan_complete", found, payload.radius()),
                EdithMessagePayload.Level.INFO);
    }
}
