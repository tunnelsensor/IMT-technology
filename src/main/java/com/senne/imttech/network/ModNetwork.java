package com.senne.imttech.network;

import com.senne.imttech.client.ClientPayloadHandlers;
import com.senne.imttech.comms.ImtComms;
import com.senne.imttech.arsenal.ArsenalServer;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.illusion.ImtIllusions;
import com.senne.imttech.holo.HoloTables;
import com.senne.imttech.nav.ImtNavigation;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.satellite.SatelliteWorkshop;
import com.senne.imttech.shield.ImtDomes;
import com.senne.imttech.suit.ImtSuit;
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
    private static final String PROTOCOL_VERSION = "23";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // Speler -> server
        registrar.playToServer(ImtActionPayload.TYPE, ImtActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ImtServer.handleAction(player, payload.action());
            }
        });
        registrar.playToServer(MarkTargetPayload.TYPE, MarkTargetPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ImtServer.handleMark(player, payload.entityId());
            }
        });
        // Fase 3
        registrar.playToServer(WaypointActionPayload.TYPE, WaypointActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ImtNavigation.handleWaypointAction(player, payload);
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
                    ImtIllusions.place(player);
                } else {
                    ImtIllusions.cancelAim(player, "imt.imttech.msg.illusion_aim_cancelled");
                }
            }
        });
        registrar.playToServer(DroneControlPayload.TYPE, DroneControlPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ImtServer.handleDroneControl(player, payload);
            }
        });
        registrar.playToServer(CommsPayload.TYPE, CommsPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ImtComms.handleMessage(player, payload);
            }
        });
        registrar.playToServer(PingPayload.TYPE, PingPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ImtComms.handlePing(player, payload);
            }
        });
        // Fase 9: redstone-relais
        registrar.playToServer(RelayActionPayload.TYPE, RelayActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ImtRedstone.handleAction(player, payload);
            }
        });
        // De relay emitter: "Save" in zijn scherm.
        registrar.playToServer(EmitterSavePayload.TYPE, EmitterSavePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.senne.imttech.redstone.RelayEmitters.handleSave(player, payload);
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
                com.senne.imttech.energy.PowerCabinets.handleAction(player, payload);
            }
        });
        // De harnas-werkbank: een klik op een stuk van het hologram.
        // Hoofdstuk 10: de tijd op een C4 (Timer) instellen.
        registrar.playToServer(C4TimerPayload.TYPE, C4TimerPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                C4TimerPayload.handle(player, payload);
            }
        });
        // Hoofdstuk 10: linksklik met de Zone Marker (hoek 1, of met sluipen een kubus weghalen).
        registrar.playToServer(ZoneMarkerClickPayload.TYPE, ZoneMarkerClickPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.senne.imttech.security.zone.ZoneMarkerItem.leftClick(player, payload.pos());
            }
        });
        registrar.playToServer(WorkbenchClickPayload.TYPE, WorkbenchClickPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                WorkbenchClickPayload.handle(player, payload);
            }
        });
        // De schildkoepel: een knop in zijn scherm.
        registrar.playToServer(DomeActionPayload.TYPE, DomeActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ImtDomes.handleAction(player, payload);
            }
        });
        // De laptop-hack: een commando getypt in de terminal van de laptop.
        registrar.playToServer(HackCommandPayload.TYPE, HackCommandPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.senne.imttech.security.TowerHack.handleCommand(player, payload);
            }
        });
        // De Security OS-desktop: een opdracht van het scherm (wachtwoord zetten, apparaat aan/uit...).
        registrar.playToServer(OsActionPayload.TYPE, OsActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.senne.imttech.security.os.SecurityOs.handleAction(player, payload);
            }
        });
        registrar.playToServer(HoloActionPayload.TYPE, HoloActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                HoloTables.handleAction(player, payload);
            }
        });
        // De Arc Lamp: het schuifje uit zijn instelscherm (helderheid).
        registrar.playToServer(LampActionPayload.TYPE, LampActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.senne.imttech.light.ArcLamps.handleAction(player, payload);
            }
        });
        // De laserzender: kleur, omkeren en liggend/staand uit zijn instelscherm (13 oktober).
        registrar.playToServer(LaserActionPayload.TYPE, LaserActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.senne.imttech.shield.LaserEmitters.handleAction(player, payload);
            }
        });
        // De trainingsblokken (hoofdstuk 26): de instellingen van een Training Start-blok bewaren.
        registrar.playToServer(TrainingActionPayload.TYPE, TrainingActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.senne.imttech.training.TrainingBlocks.handleAction(player, payload);
            }
        });
        // Het robotscherm (hoofdstuk 21): aan/uit, hernoemen en oppakken.
        registrar.playToServer(RobotActionPayload.TYPE, RobotActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.senne.imttech.robot.Robots.handleAction(player, payload);
            }
        });
        // Solderen op het bord van een robot (hoofdstuk 22).
        registrar.playToServer(SolderPayload.TYPE, SolderPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.senne.imttech.robot.Robots.handleSolder(player, payload);
            }
        });
        // De keycardlezer: Save in zijn naamscherm (13 oktober).
        registrar.playToServer(InputRenamePayload.TYPE, InputRenamePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.senne.imttech.logic.InputDevices.handleRename(player, payload);
            }
        });

        // Server -> speler. In 1.21.1 geef je de afhandeling hier meteen mee. We doen dat met
        // lambda's die pas op het moment van uitvoeren naar de client-code kijken; zo laadt een
        // server (die geen schermen heeft) die code nooit.
        registrar.playToClient(ImtMessagePayload.TYPE, ImtMessagePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onMessage(payload, context));
        registrar.playToClient(OreScanPayload.TYPE, OreScanPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onOreScan(payload, context));
        registrar.playToClient(ImtStatusPayload.TYPE, ImtStatusPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onStatus(payload, context));
        registrar.playToClient(BeamEffectPayload.TYPE, BeamEffectPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onBeam(payload, context));
        registrar.playToClient(DroneCameraPayload.TYPE, DroneCameraPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onDroneCamera(payload, context));
        registrar.playToClient(PingMarkerPayload.TYPE, PingMarkerPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onPing(payload, context));
        registrar.playToClient(HackScreenPayload.TYPE, HackScreenPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onHackScreen(payload, context));
        // De Security OS-desktop: openen/sluiten, en de momentopname van zijn toestand.
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
        registrar.playToClient(LampOpenPayload.TYPE, LampOpenPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onLampOpen(payload, context));
        registrar.playToClient(LaserOpenPayload.TYPE, LaserOpenPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onLaserOpen(payload, context));
        registrar.playToClient(RobotOpenPayload.TYPE, RobotOpenPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onRobotOpen(payload, context));
        registrar.playToClient(TrainingOpenPayload.TYPE, TrainingOpenPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onTrainingOpen(payload, context));
        registrar.playToClient(InputNameOpenPayload.TYPE, InputNameOpenPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onInputNameOpen(payload, context));
        // Het harnas: stuwraketten aan/uit. Beide kanten op: jouw spel zet ze aan, de server
        // zet ze uit (bijvoorbeeld als je energie op is).
        registrar.playBidirectional(SuitFlightPayload.TYPE, SuitFlightPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> ClientPayloadHandlers.onSuitFlight(payload, context),
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                ImtSuit.setEngaged(player, payload.engaged());
                            }
                        }));
        // Het codepaneel: de server opent de toetsen, jouw spel stuurt de ingetypte code terug.
        registrar.playBidirectional(CodePanelPayload.TYPE, CodePanelPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> ClientPayloadHandlers.onCodePanel(payload, context),
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.senne.imttech.logic.LogicEvents.codePanel(player.serverLevel(), payload.pos(),
                                        player, payload.text());
                            }
                        }));
        // Hoofdstuk 12: een hack met de Bypass Kit. De server opent het scherm en zegt hoe het afliep;
        // jouw spel stuurt het gekozen niveau, de stand van de stukjes, of dat het mislukte.
        registrar.playBidirectional(BypassPayload.TYPE, BypassPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> ClientPayloadHandlers.onBypass(payload, context),
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.senne.imttech.bypass.Bypass.handle(player, payload);
                            }
                        }));
        // Hoofdstuk 12: zakkenrollen. De server opent de zak en zegt hoe het afliep; jouw spel zegt
        // welke kaart je eruit trok, of dat de meter vol is.
        registrar.playBidirectional(PocketPayload.TYPE, PocketPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> ClientPayloadHandlers.onPocket(payload, context),
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.senne.imttech.pickpocket.Pickpocket.handle(player, payload);
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
        // Het scherm van de relay emitter (13 oktober)
        registrar.playToClient(EmitterOpenPayload.TYPE, EmitterOpenPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onEmitterOpen(payload, context));
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
        // Hoofdstuk 10: C4 op je zone (de rode markering aan of uit)
        registrar.playToClient(C4AlertPayload.TYPE, C4AlertPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onC4Alert(payload, context));
        // Hoofdstuk 10: de zones die jij mag zien (jouw eigen zones en die van je vrienden)
        registrar.playToClient(ZoneSyncPayload.TYPE, ZoneSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.onZoneSync(payload, context));
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
