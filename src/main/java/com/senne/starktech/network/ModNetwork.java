package com.senne.starktech.network;

import com.senne.starktech.client.ClientPayloadHandlers;
import com.senne.starktech.comms.EdithComms;
import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.illusion.EdithIllusions;
import com.senne.starktech.holo.HoloTables;
import com.senne.starktech.nav.EdithNavigation;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.satellite.SatelliteWorkshop;
import com.senne.starktech.shield.EdithDomes;
import com.senne.starktech.suit.EdithSuit;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registreert alle netwerkberichten. Berichten naar de speler (playToClient) worden aan de
 * client-kant afgehandeld in client/ClientPayloadHandlers.
 */
public final class ModNetwork {
    private ModNetwork() {}

    /** Verhoog dit getal als je de berichten verandert, zodat oude en nieuwe versies niet door elkaar lopen. */
    private static final String PROTOCOL_VERSION = "13";

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
        registrar.playToServer(SatelliteUpgradePayload.TYPE, SatelliteUpgradePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.maxAll()) {
                    SatelliteWorkshop.maxAll(player);
                } else {
                    SatelliteWorkshop.upgrade(player, payload.upgrade());
                }
            }
        });
        // Illusie richten: "zet hem hier neer" of "laat maar".
        registrar.playToServer(IllusionPlacePayload.TYPE, IllusionPlacePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.place()) {
                    EdithIllusions.place(player);
                } else {
                    EdithIllusions.cancelAim(player, "edith.starktech.msg.illusion_aim_cancelled");
                }
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
        // De stroomkast: een schakelaar op zijn deurtje.
        registrar.playToServer(CabinetActionPayload.TYPE, CabinetActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                com.senne.starktech.energy.PowerCabinets.handleAction(player, payload);
            }
        });
        // De harnas-werkbank: een klik op een stuk van het hologram.
        registrar.playToServer(WorkbenchClickPayload.TYPE, WorkbenchClickPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                WorkbenchClickPayload.handle(player, payload);
            }
        });
        // De schildkoepel: een knop in zijn scherm.
        registrar.playToServer(DomeActionPayload.TYPE, DomeActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                EdithDomes.handleAction(player, payload);
            }
        });
        // De laptop-hack: een commando getypt in de terminal van de laptop.
        registrar.playToServer(HackCommandPayload.TYPE, HackCommandPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.senne.starktech.security.TowerHack.handleCommand(player, payload);
            }
        });
        // De Stark OS-desktop: een opdracht van het scherm (wachtwoord zetten, apparaat aan/uit...).
        registrar.playToServer(OsActionPayload.TYPE, OsActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.senne.starktech.security.os.StarkOs.handleAction(player, payload);
            }
        });
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
        registrar.playToClient(HackScreenPayload.TYPE, HackScreenPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onHackScreen(payload, context));
        // De Stark OS-desktop: openen/sluiten, en de momentopname van zijn toestand.
        registrar.playToClient(OsOpenPayload.TYPE, OsOpenPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onOsOpen(payload, context));
        registrar.playToClient(OsSnapshotPayload.TYPE, OsSnapshotPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onOsSnapshot(payload, context));
        registrar.playToClient(DomeOpenPayload.TYPE, DomeOpenPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onDomeOpen(payload, context));
        registrar.playToClient(CabinetOpenPayload.TYPE, CabinetOpenPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onCabinetOpen(payload, context));
        registrar.playToClient(HoloOpenPayload.TYPE, HoloOpenPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onHoloOpen(payload, context));
        // Het harnas: stuwraketten aan/uit. Beide kanten op: jouw spel zet ze aan, de server
        // zet ze uit (bijvoorbeeld als je energie op is).
        registrar.playBidirectional(SuitFlightPayload.TYPE, SuitFlightPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> ClientPayloadHandlers.onSuitFlight(payload, context),
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                EdithSuit.setEngaged(player, payload.engaged());
                            }
                        }));
        // Het codepaneel: de server opent de toetsen, jouw spel stuurt de ingetypte code terug.
        registrar.playBidirectional(CodePanelPayload.TYPE, CodePanelPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> ClientPayloadHandlers.onCodePanel(payload, context),
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.senne.starktech.logic.LogicEvents.codePanel(player.serverLevel(), payload.pos(),
                                        player, payload.text());
                            }
                        }));
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
        registrar.playToClient(IllusionAimPayload.TYPE, IllusionAimPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onIllusionAim(payload, context));
        // Fase 10: onderschept privébericht
        registrar.playToClient(HackMessagePayload.TYPE, HackMessagePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onHackMessage(payload, context));
        // Harnas: straalvlammen onder de laarzen van wie vliegt
        registrar.playToClient(ThrusterPayload.TYPE, ThrusterPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onThruster(payload, context));
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
