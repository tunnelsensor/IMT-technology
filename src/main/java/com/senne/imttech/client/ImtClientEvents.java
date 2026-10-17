package com.senne.imttech.client;

import com.senne.imttech.client.voice.VoiceControl;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.drone.DroneEntity;
import com.senne.imttech.imt.ImtAction;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtFeature;
import com.senne.imttech.network.ImtActionPayload;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.network.MarkTargetPayload;
import com.senne.imttech.network.PingPayload;
import com.senne.imttech.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Alles wat elke tick of elke frame aan de speler-kant gebeurt: toetsen afhandelen,
 * het doelwit zoeken, vijanden in de buurt verzamelen en inzoomen.
 */
public final class ImtClientEvents {
    private ImtClientEvents() {}

    // ---------------------------------------------------------------- Elke tick

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        ItemStack glasses = ImtData.getWornGlasses(player);
        boolean wasWearing = ImtClientState.wearing;
        ImtClientState.wearing = !glasses.isEmpty();
        ImtClientState.authorized = ImtClientState.wearing && ImtData.isAuthorized(glasses, player);
        ImtClientState.operational = ImtClientState.authorized && ImtData.getEnergy(glasses) > 0;

        if (ImtClientState.wearing && !wasWearing) {
            ImtClientState.bootPending = true;
        } else if (!ImtClientState.wearing && wasWearing) {
            ImtClientState.bootPending = false;
            ImtClientState.bootGreeting = null;
        }
        // De opstart-animatie pas laten beginnen als je de wereld ziet. Bij het inloggen staat er
        // eerst nog een laadscherm; begon hij toen al, dan zag je hem niet en zei I.M.T haar
        // welkomstzin nog voor je in de wereld was (en daarna nog een keer).
        if (ImtClientState.bootPending && !(mc.screen instanceof ReceivingLevelScreen)) {
            ImtClientState.bootPending = false;
            ImtClientState.bootStartMillis = Util.getMillis();
            ImtClientState.bootGreetingSaid = false;
        }

        ImtClientState.loadMenuTab();
        // Hang je onder een transportdrone? (Dan tonen we de vliegtips in de HUD.)
        ImtClientState.flying = player.getVehicle() instanceof DroneEntity drone && drone.isTransport();
        DroneCamera.tick(mc);
        // Zelf vliegen: de server weet pas welke kant je op wil als jouw spel het doorstuurt.
        // (Kijk je door een drone-camera, dan doet DroneCamera.tick dat al.)
        if (ImtClientState.flying && !DroneCamera.isActive()) {
            DroneCamera.sendControls(mc, player);
        }
        handleKeys(mc);
        LaserCannonControl.tick(mc);
        VoiceControl.tick(mc);
        ImtClientState.pings.removeIf(ping -> Util.getMillis() > ping.endMillis());

        boolean inGame = mc.screen == null;
        ImtClientState.zooming = ImtClientState.operational && inGame && ImtKeys.ZOOM.isDown();

        // Waar kijk je naar? (voor markeren en de doelwit-analyse)
        ImtClientState.lookTarget = ImtClientState.operational ? findTarget(player) : null;
        ImtClientState.target = ImtData.isEnabled(glasses, ImtFeature.TARGET_ANALYSIS) ? ImtClientState.lookTarget : null;

        // Wie valt je aan? Dat weet alleen de server (welk doelwit een wezen heeft, ziet jouw
        // spel niet), dus die stuurt de nummers door; wij zoeken ze hier op.
        if (ImtClientState.operational && ImtData.isEnabled(glasses, ImtFeature.THREAT_SCAN)) {
            ImtClientState.threatRadius = ImtData.getThreatRadius(glasses);
            List<Entity> threats = new ArrayList<>();
            for (int id : ImtClientState.threatIds) {
                Entity entity = mc.level.getEntity(id);
                if (entity != null && entity.isAlive()) {
                    threats.add(entity);
                }
            }
            threats.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
            ImtClientState.threats = threats;
        } else {
            ImtClientState.threats = List.of();
        }
    }

    /**
     * P is in gewoon Minecraft "sociale interacties" (op een server opent dat een scherm). Draag
     * je een werkende bril en zit het bombardement op dezelfde toets, dan is P van I.M.T:
     * dan slikken we de klik van Minecraft in, vóór Minecraft hem zelf verwerkt (deze "Pre"-tick
     * komt eerder dan de toetsen van Minecraft).
     */
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (ImtClientState.operational && mc.screen == null
                && ImtKeys.SATELLITE_BOMBARDMENT.getKey().equals(mc.options.keySocialInteractions.getKey())) {
            while (mc.options.keySocialInteractions.consumeClick()) {
                // de klik weggooien
            }
        }
    }

    private static void handleKeys(Minecraft mc) {
        // consumeClick() geeft true voor elke keer dat de toets is ingedrukt.
        while (ImtKeys.MENU.consumeClick()) {
            // Het ronde menu in het midden; de HUD links verdwijnt zolang het open is (zie ImtMenuScreen).
            openIfAuthorized(mc, new ImtMenuScreen());
        }
        while (ImtKeys.NIGHT_VISION.consumeClick()) {
            sendAction(ImtAction.TOGGLE_NIGHT_VISION);
        }
        while (ImtKeys.ORE_SCAN.consumeClick()) {
            sendAction(ImtAction.ORE_SCAN);
        }
        while (ImtKeys.THREAT_SCAN.consumeClick()) {
            sendAction(ImtAction.TOGGLE_THREAT_SCAN);
        }
        while (ImtKeys.MARK_TARGET.consumeClick()) {
            markLookTarget();
        }
        while (ImtKeys.LAUNCH_DRONES.consumeClick()) {
            sendAction(ImtAction.LAUNCH_DRONES);
        }
        while (ImtKeys.DRONE_STRIKE.consumeClick()) {
            sendAction(ImtAction.DRONE_STRIKE);
        }
        while (ImtKeys.SPY_DRONE.consumeClick()) {
            sendAction(ImtAction.SPY_DRONE);
        }
        while (ImtKeys.STAND_DOWN.consumeClick()) {
            sendAction(ImtAction.STAND_DOWN);
        }
        while (ImtKeys.SATELLITE.consumeClick()) {
            sendAction(ImtAction.STRIKE_PRECISION);
        }
        while (ImtKeys.SATELLITE_BOMBARDMENT.consumeClick()) {
            sendAction(ImtAction.STRIKE_BOMBARDMENT);
        }
        while (ImtKeys.SATELLITE_SWEEP.consumeClick()) {
            sendAction(ImtAction.STRIKE_SWEEP);
        }
        while (ImtKeys.DRONE_CAMERA.consumeClick()) {
            sendAction(ImtAction.DRONE_CAMERA);
        }
        while (ImtKeys.PING.consumeClick()) {
            placePing();
        }
        while (ImtKeys.NAVIGATION.consumeClick()) {
            openIfAuthorized(mc, new NavigationScreen());
        }
        while (ImtKeys.COMMS.consumeClick()) {
            openIfAuthorized(mc, new CommsScreen());
        }
        while (ImtKeys.REDSTONE.consumeClick()) {
            openIfAuthorized(mc, new RelayListScreen());
        }
        while (ImtKeys.HACK.consumeClick()) {
            sendAction(ImtAction.HACK_PLAYER);
        }
        while (ImtKeys.LASER_CANNON.consumeClick()) {
            // Aan of uit; vuren doe je daarna met de aanvalsknop (zie LaserCannonControl).
            LaserCannonControl.toggle();
        }
        while (ImtKeys.ROCKETS.consumeClick()) {
            sendAction(ImtAction.ROCKETS);
        }
        while (ImtKeys.CABLE_XRAY.consumeClick()) {
            toggleCableXray();
        }
        while (ImtKeys.ZONE_BORDER.consumeClick()) {
            toggleZoneBorder();
        }
    }

    /**
     * De rand van zones aan of uit (G > Information of de eigen toets). Je ziet de rand alleen door je
     * bril, dus zoals de kabel-xray alleen met jouw eigen bril op; I.M.T meldt het.
     */
    public static void toggleZoneBorder() {
        if (!ImtClientState.authorized) {
            return;
        }
        boolean on = ImtClientConfig.toggleZoneBorder();
        String key = on ? "imt.imttech.msg.feature_on" : "imt.imttech.msg.feature_off";
        ImtClientState.addNotification(Component.translatable(key, Component.translatable("screen.imttech.menu.zone_border")),
                ImtMessagePayload.Level.INFO);
    }

    /**
     * Kabel-xray aan of uit (via G > Information of de eigen toets). Alleen met jouw eigen bril op;
     * I.M.T meldt het net zoals bij de andere functies ("Cable X-ray enabled.").
     */
    public static void toggleCableXray() {
        if (!ImtClientState.authorized) {
            return;
        }
        boolean on = ImtClientConfig.toggleCableXray();
        String key = on ? "imt.imttech.msg.feature_on" : "imt.imttech.msg.feature_off";
        ImtClientState.addNotification(Component.translatable(key, Component.translatable("screen.imttech.menu.cable_xray")),
                ImtMessagePayload.Level.INFO);
    }

    /** Opent een I.M.T-scherm, maar alleen als je de bril draagt en de eigenaar bent. */
    public static void openIfAuthorized(Minecraft mc, net.minecraft.client.gui.screens.Screen screen) {
        if (!ImtClientState.wearing || mc.screen != null) {
            return;
        }
        if (ImtClientState.authorized) {
            playUi(ModSounds.IMT_MENU_OPEN.get(), 1.6f);
            mc.setScreen(screen);
        } else {
            playUi(ModSounds.IMT_DENIED.get(), 1.0f);
        }
    }

    /** Plaatst een ping op de plek (of het wezen) waar je naar kijkt. */
    public static void placePing() {
        if (!ImtClientState.operational) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity viewer = viewer(mc);
        Vec3 eye = viewer.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0f);
        double range = ImtConfig.Comms.PING_PLACE_RANGE;
        Vec3 end = eye.add(look.scale(range));
        HitResult blockHit = viewer.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, viewer));

        Vec3 target = null;
        LivingEntity entity = ImtClientState.lookTarget;
        if (entity != null) {
            target = new Vec3(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            target = blockHit.getLocation();
        }
        if (target == null) {
            ImtClientState.addNotification(Component.translatable("imt.imttech.msg.no_ping_target"), ImtMessagePayload.Level.WARNING);
            playUi(ModSounds.IMT_ALERT.get(), 1.2f);
            return;
        }
        PacketDistributor.sendToServer(new PingPayload(target));
    }

    /** Waar "kijk" je vandaan: je eigen ogen, of de camera van je verkenningsdrone. */
    private static Entity viewer(Minecraft mc) {
        Entity drone = DroneCamera.drone();
        return drone != null ? drone : mc.player;
    }

    /** Markeert het wezen waar je naar kijkt (of haalt de markering weg). */
    public static void markLookTarget() {
        if (!ImtClientState.operational) {
            return;
        }
        LivingEntity target = ImtClientState.lookTarget;
        if (target == null) {
            ImtClientState.addNotification(Component.translatable("imt.imttech.msg.no_target_in_sight"), ImtMessagePayload.Level.WARNING);
            playUi(ModSounds.IMT_ALERT.get(), 1.2f);
            return;
        }
        PacketDistributor.sendToServer(new MarkTargetPayload(target.getId()));
    }

    /** Vraagt de server om een actie uit te voeren (alleen als je de bril draagt). */
    public static void sendAction(ImtAction action) {
        if (ImtClientState.wearing) {
            PacketDistributor.sendToServer(new ImtActionPayload(action.ordinal()));
        }
    }

    public static void playUi(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.6f));
    }

    /**
     * Zoekt het levende wezen waar je naar kijkt, tot TARGET_RANGE blokken ver (niet door muren).
     * Kijk je door de drone-camera, dan zoeken we vanaf de drone.
     */
    private static LivingEntity findTarget(LocalPlayer player) {
        Entity viewer = viewer(Minecraft.getInstance());
        double range = ImtConfig.Glasses.TARGET_RANGE;
        Vec3 eye = viewer.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));

        // Eerst kijken of er een blok in de weg zit.
        HitResult blockHit = viewer.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        if (blockHit.getType() != HitResult.Type.MISS) {
            range = blockHit.getLocation().distanceTo(eye);
            end = eye.add(look.scale(range));
        }

        AABB searchBox = viewer.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(viewer, eye, end, searchBox,
                e -> e instanceof LivingEntity && e != player && !(e instanceof DroneEntity) && !e.isSpectator() && e.isPickable(),
                range * range);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    // ---------------------------------------------------------------- Zoom

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        // Snel vliegen met het harnas maakt het beeld iets breder: zo voel je de snelheid.
        event.setFOV(event.getFOV() * SuitFlight.fovFactor((float) event.getPartialTick()));
        // Onthouden voor de HUD-markeringen (zie WorldProjection).
        ImtClientState.fieldOfView = event.getFOV();
        if (ImtClientState.zooming) {
            // Terwijl de satelliet oplaadt zoom je twee keer zo ver in: zo kun je van heel
            // ver precies zien waar hij inslaat.
            float factor = ImtClientState.strikeTarget != null ? ImtConfig.Glasses.ZOOM_FACTOR * 2 : ImtConfig.Glasses.ZOOM_FACTOR;
            event.setFOV(event.getFOV() / factor);
            ImtClientState.fieldOfView = event.getFOV();
        }
    }

    /** Muis minder gevoelig maken tijdens inzoomen, anders schiet je alle kanten op. */
    public static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        if (ImtClientState.zooming) {
            event.setMouseSensitivity(event.getMouseSensitivity() / 2.5);
        }
    }

    // In Minecraft 1.21.1 kunnen we wezens niet zelf een gekleurde omlijning geven zoals in
    // nieuwere versies. In plaats daarvan tekent de HUD markeringen op je scherm (ook door
    // muren heen): zie hud/ThreatMarkerWidget en hud/MarkedTargetsWidget.

    // ---------------------------------------------------------------- Wereld verlaten

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DroneCamera.exit();
        LaserCannonControl.reset();
        SuitFlight.reset();
        com.senne.imttech.client.suit.SuitEffects.clear();
        com.senne.imttech.client.suit.LaserCannonHand.reset();
        com.senne.imttech.client.illusion.IllusionRenderer.clear();
        com.senne.imttech.client.shield.RayshieldHum.clear();
        com.senne.imttech.client.robot.RobotChargeHum.clear();
        ImtClientState.reset();
    }
}
