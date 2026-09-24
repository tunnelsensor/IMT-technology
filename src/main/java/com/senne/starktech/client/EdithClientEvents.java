package com.senne.starktech.client;

import com.senne.starktech.client.voice.VoiceControl;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.edith.EdithAction;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithFeature;
import com.senne.starktech.network.EdithActionPayload;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.MarkTargetPayload;
import com.senne.starktech.network.PingPayload;
import com.senne.starktech.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
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
public final class EdithClientEvents {
    private EdithClientEvents() {}

    // ---------------------------------------------------------------- Elke tick

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        ItemStack glasses = EdithData.getWornGlasses(player);
        boolean wasWearing = EdithClientState.wearing;
        EdithClientState.wearing = !glasses.isEmpty();
        EdithClientState.authorized = EdithClientState.wearing && EdithData.isAuthorized(glasses, player);
        EdithClientState.operational = EdithClientState.authorized && EdithData.getEnergy(glasses) > 0;

        if (EdithClientState.wearing && !wasWearing) {
            EdithClientState.bootStartMillis = Util.getMillis();
            EdithClientState.bootGreetingSaid = false;
        }

        EdithClientState.loadMenuTab();
        // Hang je onder een transportdrone? (Dan tonen we de vliegtips in de HUD.)
        EdithClientState.flying = player.getVehicle() instanceof StarkDroneEntity drone && drone.isTransport();
        DroneCamera.tick(mc);
        // Zelf vliegen: de server weet pas welke kant je op wil als jouw spel het doorstuurt.
        // (Kijk je door een drone-camera, dan doet DroneCamera.tick dat al.)
        if (EdithClientState.flying && !DroneCamera.isActive()) {
            DroneCamera.sendControls(mc, player);
        }
        handleKeys(mc);
        RepulsorControl.tick(mc);
        VoiceControl.tick(mc);
        EdithClientState.pings.removeIf(ping -> Util.getMillis() > ping.endMillis());

        boolean inGame = mc.screen == null;
        EdithClientState.zooming = EdithClientState.operational && inGame && EdithKeys.ZOOM.isDown();

        // Waar kijk je naar? (voor markeren en de doelwit-analyse)
        EdithClientState.lookTarget = EdithClientState.operational ? findTarget(player) : null;
        EdithClientState.target = EdithData.isEnabled(glasses, EdithFeature.TARGET_ANALYSIS) ? EdithClientState.lookTarget : null;

        // Wie valt je aan? Dat weet alleen de server (welk doelwit een wezen heeft, ziet jouw
        // spel niet), dus die stuurt de nummers door; wij zoeken ze hier op.
        if (EdithClientState.operational && EdithData.isEnabled(glasses, EdithFeature.THREAT_SCAN)) {
            EdithClientState.threatRadius = EdithData.getThreatRadius(glasses);
            List<Entity> threats = new ArrayList<>();
            for (int id : EdithClientState.threatIds) {
                Entity entity = mc.level.getEntity(id);
                if (entity != null && entity.isAlive()) {
                    threats.add(entity);
                }
            }
            threats.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
            EdithClientState.threats = threats;
        } else {
            EdithClientState.threats = List.of();
        }
    }

    private static void handleKeys(Minecraft mc) {
        // consumeClick() geeft true voor elke keer dat de toets is ingedrukt.
        while (EdithKeys.MENU.consumeClick()) {
            // Het ronde menu in het midden; de HUD links verdwijnt zolang het open is (zie EdithMenuScreen).
            openIfAuthorized(mc, new EdithMenuScreen());
        }
        while (EdithKeys.NIGHT_VISION.consumeClick()) {
            sendAction(EdithAction.TOGGLE_NIGHT_VISION);
        }
        while (EdithKeys.ORE_SCAN.consumeClick()) {
            sendAction(EdithAction.ORE_SCAN);
        }
        while (EdithKeys.THREAT_SCAN.consumeClick()) {
            sendAction(EdithAction.TOGGLE_THREAT_SCAN);
        }
        while (EdithKeys.MARK_TARGET.consumeClick()) {
            markLookTarget();
        }
        while (EdithKeys.LAUNCH_DRONES.consumeClick()) {
            sendAction(EdithAction.LAUNCH_DRONES);
        }
        while (EdithKeys.DRONE_STRIKE.consumeClick()) {
            sendAction(EdithAction.DRONE_STRIKE);
        }
        while (EdithKeys.SPY_DRONE.consumeClick()) {
            sendAction(EdithAction.SPY_DRONE);
        }
        while (EdithKeys.STAND_DOWN.consumeClick()) {
            sendAction(EdithAction.STAND_DOWN);
        }
        while (EdithKeys.SATELLITE.consumeClick()) {
            sendAction(EdithAction.SATELLITE_STRIKE);
        }
        while (EdithKeys.DRONE_CAMERA.consumeClick()) {
            sendAction(EdithAction.DRONE_CAMERA);
        }
        while (EdithKeys.PING.consumeClick()) {
            placePing();
        }
        while (EdithKeys.NAVIGATION.consumeClick()) {
            openIfAuthorized(mc, new NavigationScreen());
        }
        while (EdithKeys.COMMS.consumeClick()) {
            openIfAuthorized(mc, new CommsScreen());
        }
        while (EdithKeys.REDSTONE.consumeClick()) {
            openIfAuthorized(mc, new RelayListScreen());
        }
        while (EdithKeys.HACK.consumeClick()) {
            sendAction(EdithAction.HACK_PLAYER);
        }
        while (EdithKeys.REPULSOR.consumeClick()) {
            // Aan of uit; vuren doe je daarna met de aanvalsknop (zie RepulsorControl).
            RepulsorControl.toggle();
        }
    }

    /** Opent een E.D.I.T.H.-scherm, maar alleen als je de bril draagt en de eigenaar bent. */
    public static void openIfAuthorized(Minecraft mc, net.minecraft.client.gui.screens.Screen screen) {
        if (!EdithClientState.wearing || mc.screen != null) {
            return;
        }
        if (EdithClientState.authorized) {
            playUi(ModSounds.EDITH_MENU_OPEN.get(), 1.6f);
            mc.setScreen(screen);
        } else {
            playUi(ModSounds.EDITH_DENIED.get(), 1.0f);
        }
    }

    /** Plaatst een ping op de plek (of het wezen) waar je naar kijkt. */
    public static void placePing() {
        if (!EdithClientState.operational) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity viewer = viewer(mc);
        Vec3 eye = viewer.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0f);
        double range = EdithBalance.PING_PLACE_RANGE;
        Vec3 end = eye.add(look.scale(range));
        HitResult blockHit = viewer.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, viewer));

        Vec3 target = null;
        LivingEntity entity = EdithClientState.lookTarget;
        if (entity != null) {
            target = new Vec3(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            target = blockHit.getLocation();
        }
        if (target == null) {
            EdithClientState.addNotification(Component.translatable("edith.starktech.msg.no_ping_target"), EdithMessagePayload.Level.WARNING);
            playUi(ModSounds.EDITH_ALERT.get(), 1.2f);
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
        if (!EdithClientState.operational) {
            return;
        }
        LivingEntity target = EdithClientState.lookTarget;
        if (target == null) {
            EdithClientState.addNotification(Component.translatable("edith.starktech.msg.no_target_in_sight"), EdithMessagePayload.Level.WARNING);
            playUi(ModSounds.EDITH_ALERT.get(), 1.2f);
            return;
        }
        PacketDistributor.sendToServer(new MarkTargetPayload(target.getId()));
    }

    /** Vraagt de server om een actie uit te voeren (alleen als je de bril draagt). */
    public static void sendAction(EdithAction action) {
        if (EdithClientState.wearing) {
            PacketDistributor.sendToServer(new EdithActionPayload(action.ordinal()));
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
        double range = EdithBalance.TARGET_RANGE;
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
                e -> e instanceof LivingEntity && e != player && !(e instanceof StarkDroneEntity) && !e.isSpectator() && e.isPickable(),
                range * range);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    // ---------------------------------------------------------------- Zoom

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        // Snel vliegen met het harnas maakt het beeld iets breder: zo voel je de snelheid.
        event.setFOV(event.getFOV() * SuitFlight.fovFactor((float) event.getPartialTick()));
        // Onthouden voor de HUD-markeringen (zie WorldProjection).
        EdithClientState.fieldOfView = event.getFOV();
        if (EdithClientState.zooming) {
            // Terwijl de satelliet oplaadt zoom je twee keer zo ver in: zo kun je van heel
            // ver precies zien waar hij inslaat.
            float factor = EdithClientState.strikeTarget != null ? EdithBalance.ZOOM_FACTOR * 2 : EdithBalance.ZOOM_FACTOR;
            event.setFOV(event.getFOV() / factor);
            EdithClientState.fieldOfView = event.getFOV();
        }
    }

    /** Muis minder gevoelig maken tijdens inzoomen, anders schiet je alle kanten op. */
    public static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        if (EdithClientState.zooming) {
            event.setMouseSensitivity(event.getMouseSensitivity() / 2.5);
        }
    }

    // In Minecraft 1.21.1 kunnen we wezens niet zelf een gekleurde omlijning geven zoals in
    // nieuwere versies. In plaats daarvan tekent de HUD markeringen op je scherm (ook door
    // muren heen): zie hud/ThreatMarkerWidget en hud/MarkedTargetsWidget.

    // ---------------------------------------------------------------- Wereld verlaten

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DroneCamera.exit();
        RepulsorControl.reset();
        SuitFlight.reset();
        com.senne.starktech.client.illusion.IllusionRenderer.clear();
        EdithClientState.reset();
    }
}
