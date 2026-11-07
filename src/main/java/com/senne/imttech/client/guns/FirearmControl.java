package com.senne.imttech.client.guns;

import com.senne.imttech.client.ImtKeys;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Firearms;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.network.FirearmActionPayload;
import com.senne.imttech.network.FirearmFirePayload;
import com.senne.imttech.registry.ModSounds;
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
 * De bediening van de vuurwapens in je eigen spel (hoofdstuk 52), zoals GunControl voor de lasergeweren:
 * <ul>
 *   <li><b>linkerklik</b> haalt de trekker over (één klik = één schot; inhouden doet niets, een Glock is semi-automatisch);</li>
 *   <li><b>rechterklik</b> (inhouden) richt over de korrel en de keep;</li>
 *   <li><b>R</b>: bij een storing een tap-rack; anders herladen (met een magazijnpouch kies je een magazijn, zonder gooit
 *       het pistool zijn magazijn eruit); <b>sneak + R</b>: met de hand een patroon in de kamer;</li>
 *   <li><b>R met een magazijn in je hand</b>: een patroon erin (inhouden = doorgaan);</li>
 *   <li><b>U</b> (hoofdstuk 54, jouw wens: unload): met een pistool het magazijn eruit (in je pouch als er plek is), zonder
 *       magazijn de patroon uit de kamer; met een magazijn in je hand één patroon eruit (inhouden = doorgaan);</li>
 *   <li><b>H</b> bekijkt het pistool; sprinten laat het zakken.</li>
 * </ul>
 * Een handeling vraag je aan de server (FirearmActionPayload); die zegt hoe lang hij duurt, en zo lang loopt hier de
 * animatie (FirearmRenderer tekent hem).
 */
public final class FirearmControl {
    private FirearmControl() {}

    /** Zo lang (ticks) komt het pistool omhoog als je het pakt; tot dan schiet je niet. */
    public static final float SWITCH_TICKS = 7;
    public static final float INSPECT_TICKS = 50;
    private static final float AIM_SPEED = 0.25f;

    private static @Nullable FirearmType held;
    private static boolean heldMagazine;
    private static int heldSlot = -1;

    private static float aim;
    private static float aimO;
    private static float sprint;
    private static float sprintO;
    private static float drawnAt = -100;
    private static float shotAt = -100;
    private static float clickAt = -100;
    private static float inspectAt = -1;
    private static long nextShot;
    private static boolean triggerWasDown;
    private static boolean wasAiming;

    // De handeling die bezig is (van de server), en of we op een antwoord wachten.
    private static @Nullable Firearms.ActionKind action;
    private static int actionArg;
    private static float actionAt;
    private static int actionTicks = 1;
    private static boolean waiting;
    private static long waitingSince;
    private static long repeatAt;

    // ================================================================ Wat de tekenaar wil weten

    /** Het pistool dat je nu vasthoudt, of null. */
    public static @Nullable FirearmType held() {
        return held;
    }

    /** Houd je een magazijn vast? */
    public static boolean heldMagazine() {
        return heldMagazine;
    }

    public static float time(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partialTick;
    }

    public static float aim(float partialTick) {
        float t = Mth.lerp(partialTick, aimO, aim);
        return t * t * (3 - 2 * t);
    }

    public static float sprint(float partialTick) {
        float t = Mth.lerp(partialTick, sprintO, sprint);
        return t * t * (3 - 2 * t);
    }

    /** Hoe ver het pistool al omhoog is na het pakken (0 tot 1). */
    public static float drawn(float partialTick) {
        return Mth.clamp((time(partialTick) - drawnAt) / SWITCH_TICKS, 0, 1);
    }

    public static float inspect(float partialTick) {
        return inspectAt < 0 ? -1 : Mth.clamp((time(partialTick) - inspectAt) / INSPECT_TICKS, 0, 1);
    }

    public static float sinceShot(float partialTick) {
        return time(partialTick) - shotAt;
    }

    public static float sinceClick(float partialTick) {
        return time(partialTick) - clickAt;
    }

    /** De handeling die bezig is, of null. */
    public static @Nullable Firearms.ActionKind action() {
        return action;
    }

    public static int actionArg() {
        return actionArg;
    }

    /** Hoe ver de handeling is (0 tot 1), of -1 als er geen bezig is. */
    public static float action(float partialTick) {
        return action == null ? -1 : Mth.clamp((time(partialTick) - actionAt) / actionTicks, 0, 1);
    }

    public static boolean busy() {
        return action != null || waiting;
    }

    // ================================================================ Elke tick

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            held = null;
            heldMagazine = false;
            return;
        }
        ItemStack main = player.getMainHandItem();
        FirearmType type = FirearmItem.typeOf(main);
        boolean magazine = MagazineItem.typeOf(main) != null;
        long now = mc.level.getGameTime();
        if (type != held || magazine != heldMagazine || player.getInventory().selected != heldSlot) {
            held = type;
            heldMagazine = magazine;
            heldSlot = player.getInventory().selected;
            drawnAt = now;
            inspectAt = -1;
            aim = aimO = 0;
            // Een handeling met het vorige item is voorbij (behalve een magazijn erin: dan pak je het pistool erna).
            if (action != Firearms.ActionKind.INSERT_MAG && action != Firearms.ActionKind.DRAW) {
                action = null;
            }
            waiting = false;
        }
        aimO = aim;
        sprintO = sprint;
        if (action != null && now - actionAt > actionTicks + 1) {
            action = null;
        }
        if (waiting && now - waitingSince > 40) {
            waiting = false;
        }
        if (type == null && !magazine) {
            aim = 0;
            sprint = 0;
            // R en H vergeten, behalve als ze van een lasergeweer zijn (hoofdstuk 35: GunControl).
            if (com.senne.imttech.client.weapons.GunControl.held() == null) {
                drainKeys();
            }
            return;
        }
        boolean free = mc.screen == null;
        if (magazine) {
            aim = 0;
            sprint = Mth.approach(sprint, player.isSprinting() ? 1 : 0, 0.2f);
            // R: een patroon erin; inhouden = doorgaan zodra de vorige erin zit.
            boolean pressed = false;
            while (ImtKeys.GUN_RELOAD.consumeClick()) {
                pressed = true;
            }
            if (pressed || (ImtKeys.GUN_RELOAD.isDown() && now >= repeatAt)) {
                if (!busy()) {
                    request(Firearms.ActionKind.LOAD_ROUND, 0);
                    repeatAt = now + 4;
                }
            }
            // U: een patroon eruit; inhouden = doorgaan.
            boolean unload = false;
            while (ImtKeys.GUN_UNLOAD.consumeClick()) {
                unload = true;
            }
            if (unload || (ImtKeys.GUN_UNLOAD.isDown() && now >= repeatAt)) {
                if (!busy()) {
                    request(Firearms.ActionKind.UNLOAD_ROUND, 0);
                    repeatAt = now + 4;
                }
            }
            drainInspect();
            return;
        }

        boolean wantAim = free && mc.options.keyUse.isDown() && action == null && drawn(0) >= 1;
        if (wantAim && player.isSprinting()) {
            player.setSprinting(false);
        }
        if (wantAim && !wasAiming) {
            inspectAt = -1;
        }
        wasAiming = wantAim;
        aim = Mth.approach(aim, wantAim ? 1 : 0, AIM_SPEED);
        sprint = Mth.approach(sprint, player.isSprinting() && aim < 0.1f ? 1 : 0, 0.2f);

        while (ImtKeys.GUN_RELOAD.consumeClick()) {
            onReloadKey(player, main);
        }
        while (ImtKeys.GUN_UNLOAD.consumeClick()) {
            onUnloadKey(player, main);
        }
        while (ImtKeys.GUN_INSPECT.consumeClick()) {
            if (action == null && aim < 0.1f) {
                inspectAt = now;
            }
        }
        if (inspectAt >= 0 && now - inspectAt > INSPECT_TICKS) {
            inspectAt = -1;
        }

        boolean down = free && mc.options.keyAttack.isDown();
        boolean pressed = down && !triggerWasDown;
        triggerWasDown = down;
        if (pressed) {
            tryFire(player, main, type, now);
        }
    }

    private static void drainKeys() {
        while (ImtKeys.GUN_RELOAD.consumeClick()) {
            // niets
        }
        while (ImtKeys.GUN_UNLOAD.consumeClick()) {
            // niets
        }
        drainInspect();
    }

    private static void drainInspect() {
        while (ImtKeys.GUN_INSPECT.consumeClick()) {
            // niets
        }
    }

    /** R met een pistool: tap-rack, een patroon in de kamer, of herladen. */
    private static void onReloadKey(LocalPlayer player, ItemStack gun) {
        if (busy() || drawn(0) < 1) {
            return;
        }
        inspectAt = -1;
        if (FirearmItem.jam(gun) != FirearmItem.JAM_NONE) {
            request(Firearms.ActionKind.TAP_RACK, 0);
            return;
        }
        if (player.isShiftKeyDown()) {
            if (FirearmItem.chambered(gun)) {
                player.displayClientMessage(Component.translatable("gun.imttech.already_chambered").withStyle(ChatFormatting.RED), true);
                return;
            }
            request(Firearms.ActionKind.CHAMBER_ROUND, 0);
            return;
        }
        if (GunGearClient.openMagazinePicker(player, gun)) {
            return;
        }
        if (!FirearmItem.hasMagazine(gun)) {
            player.displayClientMessage(Component.translatable("gun.imttech.no_magazine_hint").withStyle(ChatFormatting.RED), true);
            play(player, ModSounds.GLOCK_DRY.get(), 0.4f);
            return;
        }
        request(Firearms.ActionKind.EJECT_MAG, 0);
    }

    /** U met een pistool (hoofdstuk 54): het magazijn eruit, of zonder magazijn de patroon uit de kamer. */
    private static void onUnloadKey(LocalPlayer player, ItemStack gun) {
        if (busy() || drawn(0) < 1) {
            return;
        }
        inspectAt = -1;
        if (FirearmItem.jam(gun) != FirearmItem.JAM_NONE) {
            player.displayClientMessage(Component.translatable("gun.imttech.jammed_unload").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (FirearmItem.hasMagazine(gun)) {
            request(Firearms.ActionKind.EJECT_MAG, 0);
            return;
        }
        if (FirearmItem.chambered(gun)) {
            request(Firearms.ActionKind.UNLOAD_CHAMBER, 0);
            return;
        }
        player.displayClientMessage(Component.translatable("gun.imttech.nothing_to_unload").withStyle(ChatFormatting.RED), true);
    }

    /** Een handeling aan de server vragen; zodra hij antwoordt, begint de animatie. */
    public static void request(Firearms.ActionKind kind, int arg) {
        waiting = true;
        waitingSince = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        aim = 0;
        PacketDistributor.sendToServer(new FirearmActionPayload(kind.ordinal(), arg, 0));
    }

    /** De server zegt: deze handeling begint (ticks > 0) of gaat niet / is afgebroken (0). */
    public static void onAction(FirearmActionPayload payload) {
        Firearms.ActionKind[] kinds = Firearms.ActionKind.values();
        if (payload.kind() < 0 || payload.kind() >= kinds.length) {
            return;
        }
        Firearms.ActionKind kind = kinds[payload.kind()];
        LocalPlayer me = Minecraft.getInstance().player;
        if (kind == Firearms.ActionKind.DRAW && me != null
                && com.senne.imttech.weapons.LaserGunItem.typeOf(me.getMainHandItem()) != null) {
            // Een lasergeweer trekken (hoofdstuk 55): dat tekent GunControl.
            waiting = false;
            if (payload.ticks() > 0) {
                com.senne.imttech.client.weapons.GunControl.onDraw(payload.ticks());
            }
            return;
        }
        waiting = false;
        if (payload.ticks() <= 0) {
            if (action == kind) {
                action = null;
            }
            return;
        }
        action = kind;
        actionArg = payload.arg();
        actionAt = time(0);
        actionTicks = payload.ticks();
        inspectAt = -1;
        aim = 0;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && (kind == Firearms.ActionKind.LOAD_ROUND || kind == Firearms.ActionKind.UNLOAD_ROUND)) {
            play(player, ModSounds.GLOCK_ROUND_LOAD.get(), 0.5f);
        }
    }

    private static void tryFire(LocalPlayer player, ItemStack gun, FirearmType type, long now) {
        if (busy() || drawn(0) < 1 || now < nextShot) {
            return;
        }
        nextShot = now + type.interval();
        inspectAt = -1;
        boolean aiming = aim > 0.5f;
        if (!FirearmItem.works(gun) || FirearmItem.jam(gun) != FirearmItem.JAM_NONE || !FirearmItem.chambered(gun)) {
            // Een klik: de server zegt waarom, en speelt de klik voor iedereen.
            clickAt = now;
            PacketDistributor.sendToServer(new FirearmFirePayload(player.getYRot(), player.getXRot(), aiming));
            return;
        }
        if (player.isSprinting()) {
            player.setSprinting(false);
        }
        shotAt = now;
        PacketDistributor.sendToServer(new FirearmFirePayload(player.getYRot(), player.getXRot(), aiming));
        play(player, ModSounds.GLOCK_FIRE.get(), 1.0f);
        // De terugslag: omhoog (minder als je richt, en naar het materiaal van de slede), en een beetje opzij.
        float kick = type.recoil(aiming) * FirearmItem.parts(gun).materialOf(GunPart.SLIDE).recoilFactor()
                * GunSkillsClient.recoilFactor(type);
        player.setXRot(Mth.clamp(player.getXRot() - kick, -90, 90));
        player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5f) * kick * 0.5f);
    }

    // ================================================================ Wat Minecraft zelf zou doen

    /** Met een pistool in je hand: linkerklik slaat niet, rechterklik gebruikt niets (het is richten). */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (held == null || event.isPickBlock()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    /** Richten: langzamer lopen. */
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (held == null || aim <= 0) {
            return;
        }
        float slow = 1 - 0.35f * aim;
        event.getInput().forwardImpulse *= slow;
        event.getInput().leftImpulse *= slow;
    }

    /** Richten: een beetje inzoomen. */
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov() || held == null) {
            return;
        }
        float zoom = 1 + (held.zoom() - 1) * aim((float) event.getPartialTick());
        if (zoom > 1.001f) {
            event.setFOV(event.getFOV() / zoom);
        }
    }

    public static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        if (held == null) {
            return;
        }
        float zoom = 1 + (held.zoom() - 1) * aim(1);
        if (zoom > 1.001f) {
            event.setMouseSensitivity(event.getMouseSensitivity() / Math.sqrt(zoom));
        }
    }

    // ================================================================ Hulpjes

    static void play(LocalPlayer player, SoundEvent sound, float volume) {
        player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS,
                volume, 0.95f + player.getRandom().nextFloat() * 0.1f, false);
    }

    /** Uit de wereld: alles terug. */
    public static void reset() {
        held = null;
        heldMagazine = false;
        heldSlot = -1;
        aim = aimO = sprint = sprintO = 0;
        inspectAt = -1;
        action = null;
        waiting = false;
    }
}
