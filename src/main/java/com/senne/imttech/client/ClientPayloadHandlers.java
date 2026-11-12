package com.senne.imttech.client;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.BeamEffectPayload;
import com.senne.imttech.network.DroneCameraPayload;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.network.ImtStatusPayload;
import com.senne.imttech.network.OreScanPayload;
import com.senne.imttech.client.illusion.IllusionRenderer;
import com.senne.imttech.network.HackMessagePayload;
import com.senne.imttech.network.IllusionAimPayload;
import com.senne.imttech.network.IllusionScenePayload;
import com.senne.imttech.network.PingMarkerPayload;
import com.senne.imttech.network.HoloOpenPayload;
import com.senne.imttech.network.SuitFlightPayload;
import com.senne.imttech.network.RelayEditPayload;
import com.senne.imttech.network.SpyReportPayload;
import com.senne.imttech.network.SpyStatusPayload;
import net.minecraft.client.Minecraft;
import com.senne.imttech.registry.ModSounds;
import java.util.HashSet;
import net.minecraft.network.chat.Component;
import com.senne.imttech.client.hud.BootOverlay;
import com.senne.imttech.client.suit.SuitEffects;
import com.senne.imttech.network.DomeOpenPayload;
import com.senne.imttech.network.ThrusterPayload;
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
        ImtClientState.addHackBubble(payload.entityId(), payload.text(), payload.outgoing(),
                Math.round(ImtConfig.Hack.BUBBLE_MILLIS));
        ImtClientEvents.playUi(ModSounds.COMMS_MESSAGE.get(), 1.4f);
    }

    /** Fase 9: open het scherm om een relais een naam en groep te geven. */
    public static void onRelayEdit(RelayEditPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new RelayEditScreen(payload.index()));
        }
    }

    /** De terminal van de laptop openen (met alles wat je al typte), of weer sluiten. */
    public static void onHackScreen(com.senne.imttech.network.HackScreenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.open()) {
            mc.setScreen(new HackScreen(payload.pos(), payload.seed(), payload.commands()));
        } else if (mc.screen instanceof HackScreen hack && hack.laptop().equals(payload.pos())) {
            hack.closeFromServer();
        }
    }

    /** De toetsen van een codepaneel openen (als er nog geen ander scherm openstaat). */
    public static void onCodePanel(com.senne.imttech.network.CodePanelPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new CodePanelScreen(payload.pos(), payload.text()));
        }
    }

    /**
     * Een hack met de Bypass Kit (hoofdstuk 12): het stroomcircuit van een lezer of de vingerafdrukken van
     * een paneel openen, een poging beantwoorden, de uitslag laten zien, of het scherm sluiten (je liep weg).
     */
    public static void onBypass(com.senne.imttech.network.BypassPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        var args = payload.args();
        switch (payload.op()) {
            case "reader" -> {
                // De groepen van het netwerk van de lezer (25 oktober), als een boom.
                java.util.List<com.senne.imttech.client.bypass.CircuitScreen.Group> groups = new java.util.ArrayList<>();
                for (net.minecraft.nbt.Tag element : args.getList("groups", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    net.minecraft.nbt.CompoundTag row = (net.minecraft.nbt.CompoundTag) element;
                    groups.add(new com.senne.imttech.client.bypass.CircuitScreen.Group(row.getString("n"), row.getInt("d")));
                }
                mc.setScreen(new com.senne.imttech.client.bypass.CircuitScreen(payload.pos(), args.getString("name"),
                        args.getLong("seed"), groups));
            }
            case "panel" -> mc.setScreen(new com.senne.imttech.client.bypass.FingerprintScreen(payload.pos(),
                    args.getString("name"), args.getInt("length"), args.getIntArray("prints"), args.getInt("tries")));
            // Een laserzender (hoofdstuk 33): de kluwen van draden.
            case "laser" -> mc.setScreen(new com.senne.imttech.client.bypass.WireScreen(payload.pos(), args.getString("name"),
                    args.getLong("seed"), args.getInt("wires"), args.getInt("ticks"), args.getInt("lock")));
            case "guess" -> {
                if (mc.screen instanceof com.senne.imttech.client.bypass.FingerprintScreen panel
                        && panel.device().equals(payload.pos())) {
                    panel.onGuess(args.getString("code"), args.getInt("right"), args.getInt("tries"));
                }
            }
            case "result" -> {
                if (mc.screen instanceof com.senne.imttech.client.bypass.BypassScreen screen
                        && screen.device().equals(payload.pos())) {
                    screen.onResult(args);
                }
            }
            case "close" -> {
                if (mc.screen instanceof com.senne.imttech.client.bypass.BypassScreen screen
                        && screen.device().equals(payload.pos())) {
                    screen.closeFromServer();
                }
            }
            default -> { }
        }
    }

    /** Zakkenrollen (hoofdstuk 12): de zak van een speler openen, of laten zien hoe het afliep. */
    public static void onPocket(com.senne.imttech.network.PocketPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        var args = payload.args();
        if (payload.op().equals("open")) {
            mc.setScreen(new com.senne.imttech.client.pickpocket.PocketScreen(payload.victim(), args.getString("name"),
                    args.getInt("cards"), args.getLong("seed")));
        } else if (payload.op().equals("result")
                && mc.screen instanceof com.senne.imttech.client.pickpocket.PocketScreen pocket
                && pocket.victim() == payload.victim()) {
            pocket.onResult(args);
        }
    }

    /** De Security OS-desktop openen (eigenaar of op afstand), of weer sluiten. */
    public static void onOsOpen(com.senne.imttech.network.OsOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (!payload.open()) {
            if (mc.screen instanceof com.senne.imttech.client.os.SecurityOsScreen desk && desk.core().equals(payload.pos())) {
                desk.onClose();
            }
            return;
        }
        mc.setScreen(new com.senne.imttech.client.os.SecurityOsScreen(payload.pos(), payload.remote()));
    }

    /**
     * Hoofdstuk 40: het inlogscherm van de Hacking Laptop openen (met het IP van de vorige keer), of het antwoord van de
     * server laten zien als het al open staat (waarom inloggen niet lukte).
     */
    public static void onOsLoginPrompt(com.senne.imttech.network.OsLoginPromptPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.open()) {
            mc.setScreen(new com.senne.imttech.client.os.OsLoginScreen(payload.ip()));
        } else if (mc.screen instanceof com.senne.imttech.client.os.OsLoginScreen login) {
            login.reply(payload.message());
        }
    }

    /** Hoofdstuk 50: een scherm van de I.M.T Phone openen (verbinden, de PIN, een PIN kiezen), of het antwoord laten zien. */
    public static void onPhonePrompt(com.senne.imttech.network.PhonePromptPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.mode() >= 0) {
            com.senne.imttech.client.os.OsLoginScreen.Mode mode = switch (payload.mode()) {
                case com.senne.imttech.network.PhonePromptPayload.PIN -> com.senne.imttech.client.os.OsLoginScreen.Mode.PHONE_PIN;
                case com.senne.imttech.network.PhonePromptPayload.SET_PIN -> com.senne.imttech.client.os.OsLoginScreen.Mode.PHONE_SET_PIN;
                default -> com.senne.imttech.client.os.OsLoginScreen.Mode.PHONE_LOGIN;
            };
            mc.setScreen(new com.senne.imttech.client.os.OsLoginScreen(mode, payload.label()));
        } else if (mc.screen instanceof com.senne.imttech.client.os.OsLoginScreen login) {
            login.reply(payload.message());
        }
    }

    /** Hoofdstuk 40: het codescherm van een Computer openen, of het antwoord van de server laten zien. */
    public static void onComputerCodePrompt(com.senne.imttech.network.ComputerCodePromptPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.open()) {
            mc.setScreen(new com.senne.imttech.client.os.OsLoginScreen(payload.monitor(), payload.name()));
        } else if (mc.screen instanceof com.senne.imttech.client.os.OsLoginScreen login) {
            login.reply(payload.message());
        }
    }

    /** Een nieuwe momentopname van de desktop: het scherm leest hieruit. */
    public static void onOsSnapshot(com.senne.imttech.network.OsSnapshotPayload payload, IPayloadContext context) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen desk
                && desk.core().equals(payload.pos())) {
            desk.update(payload.data());
        }
    }

    public static void onCabinetOpen(com.senne.imttech.network.CabinetOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new PowerCabinetScreen(payload.pos(), payload.owner()));
        }
    }

    /** 19 oktober: het instelscherm van een schermmuur. */
    public static void onCameraWallOpen(com.senne.imttech.network.CameraWallOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new com.senne.imttech.client.os.CameraWallScreen(payload.pos(), payload.width(), payload.height(), payload.data()));
        }
    }

    /** Hoofdstuk 38: het deurtje van een Network Switch. */
    public static void onSwitchOpen(com.senne.imttech.network.SwitchOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new NetworkSwitchScreen(payload.pos(), payload.name()));
        }
    }

    /** Hoofdstuk 38: er loopt een signaal of stroom over de kabels (zie CableTraffic). */
    public static void onCableTraffic(com.senne.imttech.network.CableTrafficPayload payload, IPayloadContext context) {
        CableTraffic.receive(payload);
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

    /** Het scherm van een relay emitter openen (welke relais, en wat hij doet). */
    public static void onEmitterOpen(com.senne.imttech.network.EmitterOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new RelayEmitterScreen(payload));
        }
    }

    /** Het instelscherm van een Arc Lamp openen (helderheid). */
    public static void onLampOpen(com.senne.imttech.network.LampOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new com.senne.imttech.client.light.ArcLampScreen(payload.pos()));
        }
    }

    /** Het instelscherm van een laserzender openen (kleur, omkeren, liggend of staand), of van een laserbrug (hoofdstuk 34). */
    public static void onLaserOpen(com.senne.imttech.network.LaserOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new com.senne.imttech.client.shield.LaserEmitterScreen(payload.pos(), payload.owner(), payload.name()));
        }
    }

    /** Het scherm van een trainingsblok openen (sneak + rechtsklik, hoofdstuk 26): de instellingen van een Start-blok. */
    public static void onTrainingOpen(com.senne.imttech.network.TrainingOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && !payload.end()) {
            mc.setScreen(new com.senne.imttech.client.training.TrainingStartScreen(payload.pos(),
                    com.senne.imttech.training.Training.Settings.load(payload.data())));
        } else if (mc.screen == null) {
            mc.setScreen(new com.senne.imttech.client.os.TrainingEndScreen(payload.pos(), payload.data()));
        }
    }

    /** Het scherm van een guard robot openen (sneak + rechtsklik, hoofdstuk 21). */
    public static void onRobotOpen(com.senne.imttech.network.RobotOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new com.senne.imttech.client.robot.RobotScreen(payload.entityId(), payload.owner(),
                    payload.home().orElse(null)));
        }
    }

    /** Het naamscherm van een keycardlezer, codepaneel of (sinds hoofdstuk 64) serverrack openen (sneak + rechtsklik). */
    public static void onInputNameOpen(com.senne.imttech.network.InputNameOpenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new InputNameScreen(payload.pos(), payload.name(), payload.owner()));
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

    /** Iemand schoot met een lasergeweer (hoofdstuk 35): de laserbouten tekenen. */
    public static void onGunShot(com.senne.imttech.network.GunShotPayload payload, IPayloadContext context) {
        com.senne.imttech.client.weapons.GunEffects.shot(payload);
    }

    /** De server zegt hoe lang herladen duurt, of dat het niet kan (hoofdstuk 35): de animatie loopt mee of stopt. */
    public static void onGunReload(com.senne.imttech.network.GunReloadPayload payload, IPayloadContext context) {
        com.senne.imttech.client.weapons.GunControl.onReload(payload.start(), payload.value());
    }

    /** Een handeling met een pistool of magazijn begint, of gaat niet (hoofdstuk 52). */
    public static void onFirearmAction(com.senne.imttech.network.FirearmActionPayload payload, IPayloadContext context) {
        com.senne.imttech.client.guns.FirearmControl.onAction(payload);
    }

    /** Een flashbang verblindt je (hoofdstuk 35). */
    public static void onFlash(com.senne.imttech.network.FlashPayload payload, IPayloadContext context) {
        com.senne.imttech.client.weapons.FlashOverlay.flash(payload.strength(), payload.ticks());
    }

    /** Een EMP-granaat ging af (hoofdstuk 35). */
    public static void onEmpBlast(com.senne.imttech.network.EmpBlastPayload payload, IPayloadContext context) {
        com.senne.imttech.client.weapons.EmpEffects.blast(payload.center(), payload.radius());
    }

    public static void onThruster(ThrusterPayload payload, IPayloadContext context) {
        SuitEffects.onThruster(payload);
    }

    /** "Kijk door deze drone" (of stop ermee). */
    public static void onDroneCamera(DroneCameraPayload payload, IPayloadContext context) {
        DroneCamera.onServerMessage(payload.droneId(), payload.control());
    }

    /** Een Remote Frag op je zone (hoofdstuk 10 en 35): de rode markering aan of uit. */
    public static void onChargeAlert(com.senne.imttech.network.ChargeAlertPayload payload, IPayloadContext context) {
        com.senne.imttech.client.zone.ChargeAlerts.onPayload(payload);
    }

    /** De zones die jij mag zien (hoofdstuk 10): de rand tekent ZoneBorderRenderer. */
    public static void onZoneSync(com.senne.imttech.network.ZoneSyncPayload payload, IPayloadContext context) {
        com.senne.imttech.client.zone.ZoneClientState.set(payload.zones());
    }

    /** Hoofdstuk 99: van wie je de naam niet ziet (ze staan in een zone waar jij niet mag bouwen). */
    public static void onHiddenNames(com.senne.imttech.network.HiddenNamesPayload payload, IPayloadContext context) {
        com.senne.imttech.client.zone.HiddenNames.set(payload.players());
    }

    /** Hoofdstuk 44: de verstevigde blokken in je buurt (je houdt de Block Reinforcer vast). */
    public static void onReinforcedBlocks(com.senne.imttech.network.ReinforcedBlocksPayload payload, IPayloadContext context) {
        com.senne.imttech.client.repair.ReinforcedRenderer.set(payload.blocks());
    }

    /** Hoofdstuk 47: het scherm van de Power Battery openen, of de cijfers van een open scherm verversen. */
    public static void onBatteryStats(com.senne.imttech.network.BatteryStatsPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BatteryScreen screen) {
            screen.update(payload);
        } else if (payload.open() && mc.screen == null) {
            mc.setScreen(new BatteryScreen(payload));
        }
    }

    public static void onMessage(ImtMessagePayload payload, IPayloadContext context) {
        // Hoofdstuk 96: een lege bril toont en zegt niets, en piept ook niet. (De server stuurt dan al niets meer; dit vangt
        // wat er nog onderweg was op het moment dat hij leegliep.)
        if (ImtClientState.batteryDead) {
            return;
        }
        // De welkomstzin bij het opzetten: die zegt de opstart-animatie zelf, op het moment dat
        // je identiteit bevestigd is. Is die animatie al voorbij, dan gewoon meteen.
        // Let op: bij het inloggen komt de zin vaak binnen voordat jouw spel zelf gezien heeft dat
        // je de bril draagt (of terwijl het laadscherm er nog staat). Ook dan bewaren we hem,
        // anders klonk hij twee keer: nu meteen, en straks nog eens in de animatie.
        long bootAge = Util.getMillis() - ImtClientState.bootStartMillis;
        boolean bootNotStarted = ImtClientState.bootPending || !ImtClientState.wearing;
        if (payload.level() == ImtMessagePayload.Level.BOOT && (bootNotStarted || bootAge < BootOverlay.CONFIRMED_AT)) {
            ImtClientState.bootGreeting = payload.message();
            if (!bootNotStarted) {
                ImtClientEvents.playUi(ModSounds.IMT_BOOT.get(), 1.0f);
            }
            return;
        }
        ImtClientState.addNotification(payload.message(), payload.level());
        switch (payload.level()) {
            case BOOT -> ImtClientEvents.playUi(ModSounds.IMT_BOOT.get(), 1.0f);
            case INFO -> ImtClientEvents.playUi(ModSounds.IMT_NOTIFY.get(), 1.0f);
            case WARNING -> ImtClientEvents.playUi(ModSounds.IMT_ALERT.get(), 1.2f);
            case ALERT -> ImtClientEvents.playUi(ModSounds.IMT_DENIED.get(), 1.0f);
            case COMMS -> ImtClientEvents.playUi(ModSounds.COMMS_MESSAGE.get(), 1.0f);
        }
    }

    public static void onStatus(ImtStatusPayload payload, IPayloadContext context) {
        long now = Util.getMillis();
        ImtClientState.statusMillis = now;
        ImtClientState.swarmDrones = payload.swarmDrones();
        ImtClientState.guardDrones = payload.guardDrones();
        ImtClientState.cooldowns = payload.cooldowns().stream().mapToInt(Integer::intValue).toArray();
        ImtClientState.markedIds = new HashSet<>(payload.marked());
        ImtClientState.strikeTarget = payload.strikeTarget().orElse(null);
        ImtClientState.strikeAtMillis = now + payload.strikeTicks() * 50L;
        ImtClientState.hackedCount = payload.hackedCount();
        ImtClientState.threatIds = payload.threats();
        ImtClientState.brainDelayTicks = payload.brainDelay();
        ImtClientState.brainSource = payload.brainSource();
    }

    public static void onPing(PingMarkerPayload payload, IPayloadContext context) {
        // Hoofdstuk 96: een lege bril ziet en hoort geen pings.
        if (ImtClientState.batteryDead) {
            return;
        }
        long now = Util.getMillis();
        ImtClientState.pings.removeIf(ping -> ping.sender().equals(payload.sender()));
        ImtClientState.pings.add(new ImtClientState.Ping(payload.sender(), payload.pos(), now,
                now + Math.round(ImtConfig.Comms.PING_DURATION) * 50L));
        if (payload.sender().equals(PingMarkerPayload.SWEEP_START)) {
            return; // alleen de markering, geen melding
        }
        var player = context.player();
        boolean own = player.getGameProfile().getName().equals(payload.sender());
        int distance = (int) Math.round(Math.sqrt(player.distanceToSqr(payload.pos())));
        ImtClientState.addNotification(own
                ? Component.translatable("imt.imttech.msg.ping_placed")
                : Component.translatable("imt.imttech.msg.ping_received", payload.sender(), distance), ImtMessagePayload.Level.COMMS);
        ImtClientEvents.playUi(ModSounds.COMMS_PING.get(), own ? 1.4f : 1.0f);
    }

    /** Wat de spionagedrone nu ziet (twee keer per seconde). */
    public static void onSpyStatus(SpyStatusPayload payload, IPayloadContext context) {
        ImtClientState.spyActive = payload.active();
        ImtClientState.spyTarget = payload.targetName();
        ImtClientState.spyPos = payload.pos().orElse(null);
        ImtClientState.spyDimension = payload.dimension();
        ImtClientState.spyTicksLeft = payload.ticksLeft();
        ImtClientState.spyHealth = payload.health();
        ImtClientState.spyMaxHealth = payload.maxHealth();
        ImtClientState.spyLogLines = payload.logLines();
    }

    /** Het rapport van de spionagedrone: bewaren en het scherm openen. */
    public static void onSpyReport(SpyReportPayload payload, IPayloadContext context) {
        ImtClientState.spyReportTarget = payload.targetName();
        ImtClientState.spyReport = payload.lines();
        Minecraft mc = Minecraft.getInstance();
        // Hoofdstuk 96: met een lege bril bewaren we het rapport wel, maar het scherm gaat niet open.
        if (ImtClientState.batteryDead) {
            return;
        }
        if (mc.screen == null) {
            mc.setScreen(new SpyReportScreen(payload.targetName(), payload.lines()));
        }
        ImtClientEvents.playUi(ModSounds.COMMS_MESSAGE.get(), 0.9f);
    }

    public static void onOreScan(OreScanPayload payload, IPayloadContext context) {
        if (ImtClientState.batteryDead) {
            return; // hoofdstuk 96: een lege bril scant niet
        }
        ImtClientEvents.playUi(ModSounds.IMT_SCAN.get(), 1.0f);
        int found = OreScanner.scan(payload.radius());
        ImtClientState.addNotification(Component.translatable("imt.imttech.msg.scan_complete", found, payload.radius()),
                ImtMessagePayload.Level.INFO);
    }
}
