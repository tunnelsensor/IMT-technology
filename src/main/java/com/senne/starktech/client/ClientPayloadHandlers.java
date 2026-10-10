package com.senne.starktech.client;

import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.network.BeamEffectPayload;
import com.senne.starktech.network.DroneCameraPayload;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.EdithStatusPayload;
import com.senne.starktech.network.OreScanPayload;
import com.senne.starktech.client.illusion.IllusionRenderer;
import com.senne.starktech.network.HackMessagePayload;
import com.senne.starktech.network.IllusionAimPayload;
import com.senne.starktech.network.IllusionScenePayload;
import com.senne.starktech.network.PingMarkerPayload;
import com.senne.starktech.network.HoloOpenPayload;
import com.senne.starktech.network.SuitFlightPayload;
import com.senne.starktech.network.RelayEditPayload;
import com.senne.starktech.network.SpyReportPayload;
import com.senne.starktech.network.SpyStatusPayload;
import net.minecraft.client.Minecraft;
import com.senne.starktech.registry.ModSounds;
import java.util.HashSet;
import net.minecraft.network.chat.Component;
import com.senne.starktech.client.hud.BootOverlay;
import com.senne.starktech.client.suit.SuitEffects;
import com.senne.starktech.network.DomeOpenPayload;
import com.senne.starktech.network.ThrusterPayload;
import net.minecraft.Util;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Wat jouw spel doet als het een bericht van de server krijgt.
 */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {}

    /** Fase 12: een illusie van een cloakingdrone (of "vergeet deze illusie"). */
    public static void onIllusionScene(IllusionScenePayload payload, IPayloadContext context) {
        IllusionRenderer.onServerMessage(payload.droneId(), payload.owner(), payload.radius(), payload.jammed(),
                payload.anchor(), payload.scene().orElse(null));
    }

    /** Je richt een illusie met een drone (of je bent klaar met richten). */
    public static void onIllusionAim(IllusionAimPayload payload, IPayloadContext context) {
        IllusionRenderer.onAim(payload.droneId(), payload.scene().orElse(null));
    }

    /** Fase 10: een onderschept privébericht boven het hoofd van een gehackte speler. */
    public static void onHackMessage(HackMessagePayload payload, IPayloadContext context) {
        EdithClientState.addHackBubble(payload.entityId(), payload.text(), payload.outgoing(),
                EdithBalance.HACK_BUBBLE_MILLIS);
        EdithClientEvents.playUi(ModSounds.COMMS_MESSAGE.get(), 1.4f);
    }

    /** Fase 9: open het scherm om een relais een naam en groep te geven. */
    public static void onRelayEdit(RelayEditPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new RelayEditScreen(payload.index()));
        }
    }

    /** De terminal van de laptop openen (met alles wat je al typte), of weer sluiten. */
    public static void onHackScreen(com.senne.starktech.network.HackScreenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.open()) {
            mc.setScreen(new HackScreen(payload.pos(), payload.seed(), payload.commands()));
        } else if (mc.screen instanceof HackScreen hack && hack.laptop().equals(payload.pos())) {
            hack.closeFromServer();
        }
    }

    /** De toetsen van een codepaneel openen (als er nog geen ander scherm openstaat). */
    public static void onCodePanel(com.senne.starktech.network.CodePanelPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new CodePanelScreen(payload.pos(), payload.text()));
        }
    }

    /**
     * Een hack met de Bypass Kit (hoofdstuk 12): het stroomcircuit van een lezer of de vingerafdrukken van
     * een paneel openen, een poging beantwoorden, de uitslag laten zien, of het scherm sluiten (je liep weg).
     */
    public static void onBypass(com.senne.starktech.network.BypassPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        var args = payload.args();
        switch (payload.op()) {
            case "reader" -> mc.setScreen(new com.senne.starktech.client.bypass.CircuitScreen(payload.pos(),
                    args.getString("name"), args.getLong("seed")));
            case "panel" -> mc.setScreen(new com.senne.starktech.client.bypass.FingerprintScreen(payload.pos(),
                    args.getString("name"), args.getInt("length"), args.getIntArray("prints"), args.getInt("tries")));
            case "guess" -> {
                if (mc.screen instanceof com.senne.starktech.client.bypass.FingerprintScreen panel
                        && panel.device().equals(payload.pos())) {
                    panel.onGuess(args.getString("code"), args.getInt("right"), args.getInt("tries"));
                }
            }
            case "result" -> {
                if (mc.screen instanceof com.senne.starktech.client.bypass.BypassScreen screen
                        && screen.device().equals(payload.pos())) {
                    screen.onResult(args);
                }
            }
            case "close" -> {
                if (mc.screen instanceof com.senne.starktech.client.bypass.BypassScreen screen
                        && screen.device().equals(payload.pos())) {
                    screen.closeFromServer();
                }
            }
            default -> { }
        }
    }

    /** Zakkenrollen (hoofdstuk 12): de zak van een speler openen, of laten zien hoe het afliep. */
    public static void onPocket(com.senne.starktech.network.PocketPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        var args = payload.args();
        if (payload.op().equals("open")) {
            mc.setScreen(new com.senne.starktech.client.pickpocket.PocketScreen(payload.victim(), args.getString("name"),
                    args.getInt("cards"), args.getLong("seed")));
        } else if (payload.op().equals("result")
                && mc.screen instanceof com.senne.starktech.client.pickpocket.PocketScreen pocket
                && pocket.victim() == payload.victim()) {
            pocket.onResult(args);
        }
    }

    /** De Stark OS-desktop openen (eigenaar of op afstand), of weer sluiten. */
    public static void onOsOpen(com.senne.starktech.network.OsOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (!payload.open()) {
            if (mc.screen instanceof com.senne.starktech.client.os.StarkOsScreen desk && desk.core().equals(payload.pos())) {
                desk.onClose();
            }
            return;
        }
        mc.setScreen(new com.senne.starktech.client.os.StarkOsScreen(payload.pos(), payload.remote()));
    }

    /** Een nieuwe momentopname van de desktop: het scherm leest hieruit. */
    public static void onOsSnapshot(com.senne.starktech.network.OsSnapshotPayload payload, IPayloadContext context) {
        if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen desk
                && desk.core().equals(payload.pos())) {
            desk.update(payload.data());
        }
    }

    public static void onCabinetOpen(com.senne.starktech.network.CabinetOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new PowerCabinetScreen(payload.pos(), payload.owner()));
        }
    }

    public static void onDomeOpen(DomeOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new DomeScreen(payload.pos()));
        }
    }

    /** Het instelscherm van een holotafel openen. */
    public static void onHoloOpen(HoloOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new HoloTableScreen(payload.pos()));
        }
    }

    /** Het instelscherm van een Arc Lamp openen (helderheid en gloed). */
    public static void onLampOpen(com.senne.starktech.network.LampOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new com.senne.starktech.client.light.ArcLampScreen(payload.pos()));
        }
    }

    /** De server zet je stuwraketten uit (energie op, harnas uit...). */
    public static void onSuitFlight(SuitFlightPayload payload, IPayloadContext context) {
        if (!payload.engaged()) {
            SuitFlight.serverStopped();
        }
    }

    /** Een straal tekenen (drone-laser of satelliet). */
    public static void onBeam(BeamEffectPayload payload, IPayloadContext context) {
        BeamEffects.play(payload);
    }

    public static void onThruster(ThrusterPayload payload, IPayloadContext context) {
        SuitEffects.onThruster(payload);
    }

    /** "Kijk door deze drone" (of stop ermee). */
    public static void onDroneCamera(DroneCameraPayload payload, IPayloadContext context) {
        DroneCamera.onServerMessage(payload.droneId(), payload.control());
    }

    /** C4 op je zone (hoofdstuk 10): de rode markering aan of uit. */
    public static void onC4Alert(com.senne.starktech.network.C4AlertPayload payload, IPayloadContext context) {
        com.senne.starktech.client.c4.C4Alerts.onPayload(payload);
    }

    /** De zones die jij mag zien (hoofdstuk 10): de rand tekent ZoneBorderRenderer. */
    public static void onZoneSync(com.senne.starktech.network.ZoneSyncPayload payload, IPayloadContext context) {
        com.senne.starktech.client.zone.ZoneClientState.set(payload.zones());
    }

    public static void onMessage(EdithMessagePayload payload, IPayloadContext context) {
        // De welkomstzin bij het opzetten: die zegt de opstart-animatie zelf, op het moment dat
        // je identiteit bevestigd is. Is die animatie al voorbij, dan gewoon meteen.
        // Let op: bij het inloggen komt de zin vaak binnen voordat jouw spel zelf gezien heeft dat
        // je de bril draagt (of terwijl het laadscherm er nog staat). Ook dan bewaren we hem,
        // anders klonk hij twee keer: nu meteen, en straks nog eens in de animatie.
        long bootAge = Util.getMillis() - EdithClientState.bootStartMillis;
        boolean bootNotStarted = EdithClientState.bootPending || !EdithClientState.wearing;
        if (payload.level() == EdithMessagePayload.Level.BOOT && (bootNotStarted || bootAge < BootOverlay.CONFIRMED_AT)) {
            EdithClientState.bootGreeting = payload.message();
            if (!bootNotStarted) {
                EdithClientEvents.playUi(ModSounds.EDITH_BOOT.get(), 1.0f);
            }
            return;
        }
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
        EdithClientState.hackedCount = payload.hackedCount();
        EdithClientState.threatIds = payload.threats();
        EdithClientState.brainDelayTicks = payload.brainDelay();
        EdithClientState.brainSource = payload.brainSource();
    }

    public static void onPing(PingMarkerPayload payload, IPayloadContext context) {
        long now = Util.getMillis();
        EdithClientState.pings.removeIf(ping -> ping.sender().equals(payload.sender()));
        EdithClientState.pings.add(new EdithClientState.Ping(payload.sender(), payload.pos(), now,
                now + EdithBalance.PING_DURATION * 50L));
        if (payload.sender().equals(PingMarkerPayload.SWEEP_START)) {
            return; // alleen de markering, geen melding
        }
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
