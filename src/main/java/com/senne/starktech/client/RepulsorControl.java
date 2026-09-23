package com.senne.starktech.client;

import com.senne.starktech.edith.EdithAction;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.suit.StarkSuitItem;
import com.senne.starktech.suit.SuitBalance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * De repulsor als aan/uit-knop.
 *
 * Toets I zet hem aan of uit. Staat hij aan, dan is je aanvalsknop (linkermuisknop) de
 * repulsor: klikken vuurt, ingedrukt houden vuurt telkens als hij weer geladen is. Slaan en
 * blokken breken doe je dan niet; zet hem uit om weer gewoon te hakken.
 *
 * Dit is allemaal jouw eigen spel. De server krijgt alleen "vuur" te horen en beslist zelf of
 * dat mag (harnas, bril, energie, wachttijd).
 */
public final class RepulsorControl {
    private RepulsorControl() {}

    /** Staat de repulsor aan? */
    private static boolean armed;
    /** Wanneer (in speltijd) hij weer mag vuren. */
    private static long readyAt;
    /**
     * Wanneer hij voor het laatst vuurde; de HUD tekent daarmee het opladen. Begint één
     * wachttijd in het verleden, dus "geladen". (Long.MIN_VALUE liep over bij het aftrekken,
     * en dan stond de ring altijd op leeg.)
     */
    private static long firedAt = -SuitBalance.REPULSOR_COOLDOWN;

    public static boolean isArmed() {
        return armed;
    }

    /** Hoe ver hij opgeladen is: 0 net gevuurd, 1 klaar. */
    public static float charge(float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 1.0f;
        }
        float since = player.level().getGameTime() - firedAt + partialTick;
        return Math.min(1.0f, since / SuitBalance.REPULSOR_COOLDOWN);
    }

    /** Toets I (of het G-menu): aan of uit. */
    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !EdithClientState.authorized) {
            return;
        }
        if (!armed && !hasChestplate(player)) {
            EdithClientState.addNotification(Component.translatable("suit.starktech.msg.no_chest"),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        setArmed(player, !armed);
    }

    private static void setArmed(LocalPlayer player, boolean on) {
        armed = on;
        play(player, on ? ModSounds.REPULSOR_ARM.get() : ModSounds.REPULSOR_DISARM.get());
        EdithClientState.addNotification(Component.translatable(on
                ? "suit.starktech.msg.repulsor_armed" : "suit.starktech.msg.repulsor_safe"),
                EdithMessagePayload.Level.INFO, false);
    }

    /** Elke tick: nog steeds een harnas aan? En vuren zolang je de aanvalsknop vasthoudt. */
    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (!armed || player == null) {
            return;
        }
        // Borstplaat uit, bril af of niet meer van jou: dan gaat hij vanzelf uit.
        if (!hasChestplate(player) || !EdithClientState.wearing || !EdithClientState.authorized) {
            setArmed(player, false);
            return;
        }
        if (mc.screen == null && mc.options.keyAttack.isDown() && !DroneCamera.isActive()) {
            tryFire(player);
        }
    }

    /**
     * De aanvalsknop: staat de repulsor aan, dan slaan we niet en breken we niets, maar vuren
     * we. Minecraft stuurt dit bij elke klik, en ook elke tick zolang je op een blok houdt.
     */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!armed || !event.isAttack() || DroneCamera.isActive()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            tryFire(player);
        }
    }

    private static void tryFire(LocalPlayer player) {
        long now = player.level().getGameTime();
        if (now < readyAt) {
            return;
        }
        readyAt = now + SuitBalance.REPULSOR_COOLDOWN;
        firedAt = now;
        EdithClientEvents.sendAction(EdithAction.REPULSOR);
        // Je arm gaat naar voren, alsof je je handpalm naar voren duwt.
        player.swing(InteractionHand.MAIN_HAND);
    }

    /** Bij uitloggen of een andere wereld: weer uit. */
    public static void reset() {
        armed = false;
        readyAt = 0;
        firedAt = -SuitBalance.REPULSOR_COOLDOWN;
    }

    private static boolean hasChestplate(LocalPlayer player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof StarkSuitItem;
    }

    /** Alleen jij hoort je handschoen aan- en uitgaan; het schot zelf hoort iedereen. */
    private static void play(LocalPlayer player, SoundEvent sound) {
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), sound,
                SoundSource.PLAYERS, 0.8f, 1.0f, false);
    }
}
