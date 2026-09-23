package com.senne.starktech.network;

import com.senne.starktech.client.ClientPayloadHandlers;
import com.senne.starktech.comms.EdithComms;
import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.holo.HoloTables;
import com.senne.starktech.nav.EdithNavigation;
import com.senne.starktech.redstone.EdithRedstone;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registreert alle netwerkberichten. Berichten naar de speler (playToClient) worden aan de
 * client-kant afgehandeld in client/ClientPayloadHandlers.
 */
public final class ModNetwork {
    private ModNetwork() {}

    /** Verhoog dit getal als je de berichten verandert, zodat oude en nieuwe versies niet door elkaar lopen. */
    private static final String PROTOCOL_VERSION = "6";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // Speler -> server
        registrar.playToServer(EdithActionPayload.TYPE, EdithActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                EdithServer.handleAction(player, payload.action());
            }
        });
        registrar.playToServer(MarkTargetPayload.TYPE, MarkTargetPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                EdithServer.handleMark(player, payload.entityId());
            }
        });
        // Fase 3
        registrar.playToServer(WaypointActionPayload.TYPE, WaypointActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                EdithNavigation.handleWaypointAction(player, payload);
            }
        });
        registrar.playToServer(DroneControlPayload.TYPE, DroneControlPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                EdithServer.handleDroneControl(player, payload);
            }
        });
        registrar.playToServer(CommsPayload.TYPE, CommsPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                EdithComms.handleMessage(player, payload);
            }
        });
        registrar.playToServer(PingPayload.TYPE, PingPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                EdithComms.handlePing(player, payload);
            }
        });
        // Fase 9: redstone-relais
        registrar.playToServer(RelayActionPayload.TYPE, RelayActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                EdithRedstone.handleAction(player, payload);
            }
        });
        // Fase 15: het drone-arsenaal
        registrar.playToServer(ArsenalActionPayload.TYPE, ArsenalActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ArsenalServer.handleAction(player, payload);
            }
        });
        // Fase 15: de holotafel
        registrar.playToServer(HoloActionPayload.TYPE, HoloActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                HoloTables.handleAction(player, payload);
            }
        });

        // Server -> speler. In 1.21.1 geef je de afhandeling hier meteen mee. We doen dat met
        // lambda's die pas op het moment van uitvoeren naar de client-code kijken; zo laadt een
        // server (die geen schermen heeft) die code nooit.
        registrar.playToClient(EdithMessagePayload.TYPE, EdithMessagePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onMessage(payload, context));
        registrar.playToClient(OreScanPayload.TYPE, OreScanPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onOreScan(payload, context));
        registrar.playToClient(EdithStatusPayload.TYPE, EdithStatusPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onStatus(payload, context));
        registrar.playToClient(BeamEffectPayload.TYPE, BeamEffectPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onBeam(payload, context));
        registrar.playToClient(DroneCameraPayload.TYPE, DroneCameraPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onDroneCamera(payload, context));
        registrar.playToClient(PingMarkerPayload.TYPE, PingMarkerPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onPing(payload, context));
        registrar.playToClient(HoloOpenPayload.TYPE, HoloOpenPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onHoloOpen(payload, context));
        // Fase 6: spionagedrone
        registrar.playToClient(SpyStatusPayload.TYPE, SpyStatusPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onSpyStatus(payload, context));
        registrar.playToClient(SpyReportPayload.TYPE, SpyReportPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onSpyReport(payload, context));
        // Fase 9: het scherm om een relais een naam te geven
        registrar.playToClient(RelayEditPayload.TYPE, RelayEditPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onRelayEdit(payload, context));
        // Fase 12: illusies van cloakingdrones
        registrar.playToClient(IllusionScenePayload.TYPE, IllusionScenePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onIllusionScene(payload, context));
        // Fase 10: onderschept privébericht
        registrar.playToClient(HackMessagePayload.TYPE, HackMessagePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onHackMessage(payload, context));
    }

    /**
     * Stuurt een bericht naar één speler, maar alleen als die het kan ontvangen.
     * (Nep-spelers van andere mods of van tests hebben onze kanalen niet; zonder deze
     * controle zou de server crashen.)
     */
    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Stuurt een bericht naar alle spelers binnen een straal rond een punt. */
    public static void sendNear(ServerLevel level, Vec3 pos, double radius, CustomPacketPayload payload) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) <= radius * radius) {
                sendTo(player, payload);
            }
        }
    }
}
