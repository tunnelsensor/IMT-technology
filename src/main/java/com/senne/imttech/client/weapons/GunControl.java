package com.senne.imttech.client.weapons;

import com.senne.imttech.client.ImtKeys;
import com.senne.imttech.network.GunFirePayload;
import com.senne.imttech.network.GunReloadPayload;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * De bediening van de lasergeweren in je eigen spel (hoofdstuk 35, jouw keuze: "zoals TACZ"):
 * <ul>
 *   <li><b>linkerklik</b> schiet (het geweer: knop inhouden), en zwaait of breekt niets;</li>
 *   <li><b>rechterklik</b> (inhouden) richt door het vizier: je zoomt in, schiet nauwkeuriger en loopt langzamer;</li>
 *   <li><b>R</b> herlaadt en <b>H</b> bekijkt het geweer (alleen met een geweer in je hand; anders doen R en H wat ze
 *       altijd deden);</li>
 *   <li>sprinten laat het geweer zakken, en schieten stopt je sprint.</li>
 * </ul>
 * Alles wat je ziet, gebeurt meteen (geluid, terugslag, de bout), de server beslist of het schot telt (zie
 * weapons/LaserGuns). Hier houden we ook bij hoe ver elke animatie is; GunRenderer tekent ze.
 */
public final class GunControl {
    private GunControl() {}

    /** Zo lang (ticks) komt het geweer omhoog als je het pakt; tot dan schiet je niet. */
    public static final float DRAW_TICKS = 7;
    /** Zo lang (ticks) duurt het bekijken. */
    public static final float INSPECT_TICKS = 50;
    /** Hoe snel (per tick) je naar het vizier gaat en terug. */
    private static final float AIM_SPEED = 0.22f;

    // Wat je vasthoudt.
    private static @Nullable GunType held;
    private static int heldSlot = -1;

    // De animaties: tijden in ticks (speltijd + deel van een tick), -1 = niet bezig.
    private static float aim;
    private static float aimO;
    private static float sprint;
    private static float sprintO;
    private static float drawnAt = -100;
    private static float reloadAt = -1;
    private static int reloadTicks = 1;
    private static boolean cellInPlayed;
    private static float inspectAt = -1;
    private static float shotAt = -100;
    private static float emptyAt = -100;
    private static boolean cyclePlayed = true;
    private static long nextShot;
    private static boolean triggerWasDown;
    private static boolean wasAiming;

    // ================================================================ Wat de tekenaar wil weten

    /** Het geweer dat je nu vasthoudt, of null. */
    public static @Nullable GunType held() {
        return held;
    }

    /** De tijd nu, in ticks met een deel van een tick erbij (voor vloeiende animaties). */
    public static float time(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partialTick;
    }

    /** Hoe ver je door het vizier richt (0 = vanuit de heup, 1 = helemaal), zacht begonnen en zacht gestopt. */
    public static float aim(float partialTick) {
        float t = Mth.lerp(partialTick, aimO, aim);
        return t * t * (3 - 2 * t);
    }

    /** Hoe ver het geweer gezakt is omdat je sprint (0 tot 1). */
    public static float sprint(float partialTick) {
        float t = Mth.lerp(partialTick, sprintO, sprint);
        return t * t * (3 - 2 * t);
    }

    /** Hoe ver het geweer al omhoog is na het pakken (0 tot 1). */
    public static float draw(float partialTick) {
        return Mth.clamp((time(partialTick) - drawnAt) / DRAW_TICKS, 0, 1);
    }

    /** Hoe ver het herladen is (0 tot 1), of -1 als je niet herlaadt. */
    public static float reload(float partialTick) {
        return reloadAt < 0 ? -1 : Mth.clamp((time(partialTick) - reloadAt) / reloadTicks, 0, 1);
    }

    /** Hoe ver het bekijken is (0 tot 1), of -1. */
    public static float inspect(float partialTick) {
        return inspectAt < 0 ? -1 : Mth.clamp((time(partialTick) - inspectAt) / INSPECT_TICKS, 0, 1);
    }

    /** Hoe lang geleden (ticks) je schoot. */
    public static float sinceShot(float partialTick) {
        return time(partialTick) - shotAt;
    }

    /** Hoe lang geleden (ticks) je met een leeg geweer klikte. */
    public static float sinceEmpty(float partialTick) {
        return time(partialTick) - emptyAt;
    }

    public static boolean reloading() {
        return reloadAt >= 0;
    }

    // ================================================================ Elke tick

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            held = null;
            return;
        }
        ItemStack main = player.getMainHandItem();
        GunType type = LaserGunItem.typeOf(main);
        long now = mc.level.getGameTime();
        if (type != held || player.getInventory().selected != heldSlot) {
            // Iets anders gepakt (of een ander geweer): het nieuwe geweer komt omhoog, wat bezig was stopt.
            held = type;
            heldSlot = player.getInventory().selected;
            drawnAt = now;
            reloadAt = -1;
            inspectAt = -1;
            aim = aimO = 0;
        }
        aimO = aim;
        sprintO = sprint;
        if (type == null) {
            aim = 0;
            sprint = 0;
            // R en H vergeten, behalve als ze van een pistool of magazijn zijn (hoofdstuk 52: FirearmControl).
            if (com.senne.imttech.client.guns.FirearmControl.held() == null
                    && !com.senne.imttech.client.guns.FirearmControl.heldMagazine()) {
                drainKeys();
            }
            return;
        }
        boolean free = mc.screen == null;
        boolean wantAim = free && mc.options.keyUse.isDown() && !reloading() && draw(0) >= 1;
        if (wantAim && player.isSprinting()) {
            player.setSprinting(false);
        }
        if (wantAim && !wasAiming) {
            play(player, ModSounds.LASER_GUN_AIM.get(), 0.6f);
            inspectAt = -1;
        }
        wasAiming = wantAim;
        aim = Mth.approach(aim, wantAim ? 1 : 0, AIM_SPEED);
        sprint = Mth.approach(sprint, player.isSprinting() && aim < 0.1f ? 1 : 0, 0.2f);

        while (ImtKeys.GUN_RELOAD.consumeClick()) {
            startReload(player, main, type);
        }
        while (ImtKeys.GUN_INSPECT.consumeClick()) {
            if (!reloading() && aim < 0.1f) {
                inspectAt = now;
            }
        }
        if (reloading()) {
            float done = (now - reloadAt) / reloadTicks;
            if (!cellInPlayed && done >= 0.58f) {
                cellInPlayed = true;
                play(player, ModSounds.LASER_GUN_CELL_IN.get(), 0.8f);
            }
            if (done >= 1.05f) {
                reloadAt = -1;
            }
        }
        if (inspectAt >= 0 && now - inspectAt > INSPECT_TICKS) {
            inspectAt = -1;
        }
        // De grendel (sniper) of de pomp (shotgun) na een schot.
        if (!cyclePlayed && now - shotAt >= 5) {
            cyclePlayed = true;
            play(player, ModSounds.LASER_GUN_CYCLE.get(), 0.7f);
        }

        boolean down = free && mc.options.keyAttack.isDown();
        boolean pressed = down && !triggerWasDown;
        triggerWasDown = down;
        if (type.automatic ? down : pressed) {
            tryFire(player, main, type, now);
        }
    }

    /** R en H horen nu niet bij een geweer: hun klikken vergeten (anders herlaadt hij later ineens). */
    private static void drainKeys() {
        while (ImtKeys.GUN_RELOAD.consumeClick()) {
            // niets
        }
        while (ImtKeys.GUN_INSPECT.consumeClick()) {
            // niets
        }
    }

    private static void tryFire(LocalPlayer player, ItemStack gun, GunType type, long now) {
        if (reloading() || draw(0) < 1 || now < nextShot) {
            return;
        }
        nextShot = now + type.interval();
        inspectAt = -1;
        if (LaserGunItem.shotsLeft(gun) <= 0) {
            emptyAt = now;
            play(player, ModSounds.LASER_GUN_EMPTY.get(), 0.7f);
            send(new GunFirePayload(player.getYRot(), player.getXRot(), aim > 0.5f));
            return;
        }
        if (player.isSprinting()) {
            player.setSprinting(false);
        }
        shotAt = now;
        cyclePlayed = type != GunType.SNIPER && type != GunType.SHOTGUN;
        send(new GunFirePayload(player.getYRot(), player.getXRot(), aim > 0.5f));
        play(player, type.fireSound(), 1.0f);
        // De terugslag: je kijkt een stukje omhoog (minder als je richt), en een heel klein beetje opzij.
        float kick = GunRig.of(type).cameraKick * (aim > 0.5f ? 0.6f : 1.0f);
        player.setXRot(Mth.clamp(player.getXRot() - kick, -90, 90));
        player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5f) * kick * 0.4f);
    }

    /** R: herladen, als er een cel is die voller is dan die in je geweer. De server doet het echte wisselen. */
    private static void startReload(LocalPlayer player, ItemStack gun, GunType type) {
        if (reloading() || draw(0) < 1) {
            return;
        }
        float current = LaserGunItem.cell(gun);
        boolean better = false;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.ENERGY_CELL.get()) && EnergyCellItem.charge(stack) >= Math.max(0.001f, current + 0.001f)) {
                better = true;
                break;
            }
        }
        if (!better) {
            emptyAt = player.level().getGameTime();
            play(player, ModSounds.LASER_GUN_EMPTY.get(), 0.5f);
            player.displayClientMessage(Component.translatable("weapon.imttech.gun.no_cell").withStyle(ChatFormatting.RED), true);
            return;
        }
        reloadAt = player.level().getGameTime();
        reloadTicks = type.reloadTicks();
        cellInPlayed = false;
        inspectAt = -1;
        aim = 0;
        send(new GunReloadPayload(true));
        play(player, ModSounds.LASER_GUN_CELL_OUT.get(), 0.8f);
    }

    /** De server zegt dat herladen niet kan: de animatie stoppen. */
    public static void reloadRefused() {
        reloadAt = -1;
    }

    // ================================================================ Wat Minecraft zelf zou doen

    /** Met een geweer in je hand: linkerklik slaat niet en breekt niets, rechterklik gebruikt niets (het is richten). */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (held == null || event.isPickBlock()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    /** Richten: langzamer lopen (zoals bij een boog). */
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (held == null || aim <= 0) {
            return;
        }
        float slow = 1 - 0.45f * aim;
        event.getInput().forwardImpulse *= slow;
        event.getInput().leftImpulse *= slow;
    }

    /** De kijkhoek van de wereld (graden) van het laatste beeld, na alle zoom (voor GunRenderer.ownMuzzle). */
    private static double worldFov = 70;

    public static double worldFov() {
        return worldFov;
    }

    /** Richten: inzoomen (alleen de wereld, niet het geweer zelf). Komt als laatste, na de andere zoom. */
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) {
            return;
        }
        if (held != null) {
            float zoom = 1 + (held.zoom - 1) * aim((float) event.getPartialTick());
            if (zoom > 1.001f) {
                event.setFOV(event.getFOV() / zoom);
            }
        }
        worldFov = event.getFOV();
    }

    /** Ingezoomd: de muis minder gevoelig, anders schiet je alle kanten op. */
    public static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        if (held == null) {
            return;
        }
        float zoom = 1 + (held.zoom - 1) * aim(1);
        if (zoom > 1.001f) {
            event.setMouseSensitivity(event.getMouseSensitivity() / Math.sqrt(zoom));
        }
    }

    // ================================================================ Hulpjes

    private static void play(LocalPlayer player, SoundEvent sound, float volume) {
        player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS,
                volume, 0.95f + player.getRandom().nextFloat() * 0.1f, false);
    }

    private static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** Uit de wereld: alles terug. */
    public static void reset() {
        held = null;
        heldSlot = -1;
        aim = aimO = sprint = sprintO = 0;
        reloadAt = -1;
        inspectAt = -1;
    }
}
