package com.senne.starktech.client;

import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.network.DroneControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.Util;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * De drone-camera aan jouw kant: je kijkt door de verkenningsdrone en bestuurt hem met
 * dezelfde toetsen waarmee je normaal loopt.
 *
 * Hoe het werkt:
 * - De camera van het spel wordt op de drone gezet (setCameraEntity).
 * - Je eigen lichaam krijgt een "lege" besturing, zodat het blijft staan.
 * - Elke tick sturen we je toetsen en kijkrichting naar de server, die de drone laat vliegen.
 * - De muis draait gewoon je speler; de camera neemt die kijkrichting over.
 */
public final class DroneCamera {
    private DroneCamera() {}

    /** De drone waar we door kijken, of -1. */
    private static int droneId = -1;
    /** De server zei "kijk door drone X", maar die drone is hier nog niet aangekomen. */
    private static int pendingId = -1;
    private static long pendingUntilMillis;
    /** De echte besturing van de speler, om later terug te zetten. */
    private static @Nullable Input savedInput;
    /** true = je bestuurt de drone zelf (verkenner); false = je kijkt alleen mee (spion). */
    private static boolean control = true;

    /** Kijk je alleen mee (spionagedrone) in plaats van zelf te vliegen? */
    public static boolean isWatchingOnly() {
        return isActive() && !control;
    }

    public static boolean isActive() {
        return droneId >= 0;
    }

    public static @Nullable StarkDroneEntity drone() {
        Minecraft mc = Minecraft.getInstance();
        return isActive() && mc.level != null && mc.level.getEntity(droneId) instanceof StarkDroneEntity drone ? drone : null;
    }

    /**
     * Bericht van de server: kijk door deze drone (of stop, bij een negatief nummer).
     *
     * @param controlDrone true = jij vliegt hem (verkenner); false = je kijkt alleen mee (spion)
     */
    public static void onServerMessage(int id, boolean controlDrone) {
        if (id < 0) {
            exit();
        } else {
            control = controlDrone;
            pendingId = id;
            pendingUntilMillis = Util.getMillis() + 3000;
        }
    }

    /** Elke tick: camera aanzetten als de drone er is, en de besturing doorsturen. */
    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            exit();
            return;
        }
        if (pendingId >= 0) {
            if (mc.level.getEntity(pendingId) instanceof StarkDroneEntity drone) {
                enter(mc, player, drone);
                pendingId = -1;
            } else if (Util.getMillis() > pendingUntilMillis) {
                pendingId = -1;
            }
        }
        if (!isActive()) {
            return;
        }
        StarkDroneEntity drone = drone();
        if (drone == null || !drone.isAlive()) {
            exit();
            return;
        }

        // Kijk je alleen mee? Dan sturen we de drone niet: hij doet zijn eigen werk.
        if (!control) {
            return;
        }
        Options options = mc.options;
        boolean inGame = mc.screen == null;
        float forward = inGame ? axis(options.keyUp.isDown(), options.keyDown.isDown()) : 0;
        float strafe = inGame ? axis(options.keyLeft.isDown(), options.keyRight.isDown()) : 0;
        float vertical = inGame ? axis(options.keyJump.isDown(), options.keyShift.isDown()) : 0;
        boolean boost = inGame && options.keySprint.isDown();
        PacketDistributor.sendToServer(new DroneControlPayload(forward, strafe, vertical, boost, player.getYRot(), player.getXRot()));
    }

    private static float axis(boolean positive, boolean negative) {
        return (positive ? 1 : 0) - (negative ? 1 : 0);
    }

    private static void enter(Minecraft mc, LocalPlayer player, StarkDroneEntity drone) {
        droneId = drone.getId();
        if (savedInput == null) {
            savedInput = player.input;
            // Een lege besturing: je lichaam loopt, springt en sluipt niet meer.
            player.input = new Input();
        }
        mc.setCameraEntity(drone);
    }

    /** Terug naar je eigen ogen. */
    public static void exit() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (mc.getCameraEntity() != mc.player) {
                mc.setCameraEntity(mc.player);
            }
            if (savedInput != null) {
                mc.player.input = savedInput;
            }
        }
        savedInput = null;
        droneId = -1;
        pendingId = -1;
    }

    // ---------------------------------------------------------------- Events

    /**
     * De camera kijkt waar jij kijkt (vloeiend, met je muis). Kijk je alleen mee met een
     * spionagedrone, dan kijk je waar die drone kijkt (dus naar het doelwit).
     */
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (isActive() && control && player != null) {
            float partialTick = (float) event.getPartialTick();
            event.setYaw(player.getViewYRot(partialTick));
            event.setPitch(player.getViewXRot(partialTick));
        }
    }

    /** Geen hand of item in beeld terwijl je door de drone kijkt. */
    public static void onRenderHand(RenderHandEvent event) {
        if (isActive()) {
            event.setCanceled(true);
        }
    }

    /**
     * Klikken doet niets met je lichaam. Linkermuisknop markeert het doelwit waar de drone
     * naar kijkt (zo kun je vanuit de lucht doelwitten aanwijzen voor je zwerm).
     */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!isActive()) {
            return;
        }
        if (!control) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        // Ingedrukt houden stuurt dit event elke tick; we markeren hooguit 4x per seconde.
        long now = Util.getMillis();
        if (event.isAttack() && now - lastMarkMillis > 250) {
            lastMarkMillis = now;
            EdithClientEvents.markLookTarget();
        }
    }

    private static long lastMarkMillis;
}
