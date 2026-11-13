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
 * Hoofdstuk 103 (de grote update), voor de nieuwe wapens:
 * <ul>
 *   <li><b>X</b>: de keuzeschakelaar één stand verder (safe, één schot, drie schoten, automatisch). Op automatisch
 *       schiet het wapen zolang je de knop inhoudt; op drie schoten geeft één klik er drie;</li>
 *   <li><b>R met een grendel of een pomp</b> (de AWP, de Remington 870; jouw keuze): doorladen na elk schot. Is er niets
 *       door te laden, dan doet R wat hij altijd doet, of duwt hij een patroon in de buis van de 870 (inhouden =
 *       doorgaan); <b>U</b> haalt er daar een uit.</li>
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
    /** Hoofdstuk 103: zoveel schoten van een salvo van drie moeten er nog komen. */
    private static int burstLeft;
    /** Hoofdstuk 103: houd je R in om de buis van een shotgun te vullen (dan gaat hij door met de volgende patroon)? */
    private static boolean tubeLoading;

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
            // Geen wereld (het titelscherm, of tussen twee werelden): alles terug, ook de tijden (zie reset).
            reset();
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
            bipod = bipodO = 0;
            burstLeft = 0;
            tubeLoading = false;
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

        boolean wantAim = free && mc.options.keyUse.isDown() && action == null && drawn(0) >= 1
                && !com.senne.imttech.client.armory.ArmoryClient.usesArmory() // hoofdstuk 67: ophangen of wisselen, niet richten
                && !com.senne.imttech.client.weapons.GrenadeGrab.wouldGrab(); // hoofdstuk 69: een granaat oprapen
        if (wantAim && player.isSprinting()) {
            player.setSprinting(false);
        }
        if (wantAim && !wasAiming) {
            inspectAt = -1;
        }
        wasAiming = wantAim;
        // Hoofdstuk 103, stap 5: een red dot brengt je sneller op je doel, een zware kijker trager.
        com.senne.imttech.guns.Attachment optic = type == null ? null : FirearmItem.sight(main);
        aim = Mth.approach(aim, wantAim ? 1 : 0, AIM_SPEED * (type == null ? 1 : FirearmItem.aimSpeedFactor(main)));
        // Stap 6: de tweepoot klapt uit als je sneakt en stilstaat, en weer in als je beweegt of opstaat.
        bipodO = bipod;
        boolean legsDown = type != null && FirearmItem.attachment(main, com.senne.imttech.guns.AttachmentSlot.UNDERBARREL)
                == com.senne.imttech.guns.Attachment.BIPOD && braced(player);
        bipod = Mth.approach(bipod, legsDown ? 1 : 0, 0.14f);
        scoped = optic != null && optic.zooms() ? optic : null;
        if (scoped != null) {
            scopeZoom = Mth.clamp(scopeZoom, 1, scoped.maxZoom());
        }
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
        // Hoofdstuk 103: X = de keuzeschakelaar.
        while (ImtKeys.GUN_FIRE_MODE.consumeClick()) {
            if (!busy() && drawn(0) >= 1) {
                burstLeft = 0;
                requestQuiet(Firearms.ActionKind.SELECT_FIRE, 0);
            }
        }
        // Hoofdstuk 103: R inhouden met een shotgun = de buis blijven vullen.
        if (tubeLoading) {
            if (!ImtKeys.GUN_RELOAD.isDown() || !type.tube()) {
                tubeLoading = false;
            } else if (!busy() && now >= repeatAt && FirearmItem.roundsInMagazine(main) < FirearmItem.capacity(main)) {
                request(Firearms.ActionKind.LOAD_TUBE, 0);
                repeatAt = now + 4;
            }
        }

        boolean down = free && mc.options.keyAttack.isDown();
        boolean pressed = down && !triggerWasDown;
        triggerWasDown = down;
        FirearmType.FireMode mode = FirearmItem.fireMode(main);
        if (pressed) {
            burstLeft = 0;
            if (mode == FirearmType.FireMode.SAFE) {
                // De schakelaar staat op safe: de trekker doet niets (geen klik, geen bericht naar de server).
                if (!busy() && drawn(0) >= 1) {
                    player.displayClientMessage(Component.translatable("gun.imttech.safe").withStyle(ChatFormatting.RED), true);
                }
            } else if (tryFire(player, main, type, now) && mode == FirearmType.FireMode.BURST) {
                burstLeft = Math.max(0, Math.round(com.senne.imttech.config.ImtConfig.Guns.BURST_SHOTS) - 1);
            }
        } else if (mode == FirearmType.FireMode.AUTO && down && now >= nextShot) {
            // Automatisch: zolang je de knop inhoudt, haalt je spel de trekker telkens opnieuw over.
            tryFire(player, main, type, now);
        } else if (burstLeft > 0 && now >= nextShot) {
            // Een salvo van drie: de rest komt vanzelf, ook als je de knop al losliet.
            burstLeft = tryFire(player, main, type, now) ? burstLeft - 1 : 0;
        }
    }

    private static void drainKeys() {
        while (ImtKeys.GUN_RELOAD.consumeClick()) {
            // niets
        }
        while (ImtKeys.GUN_UNLOAD.consumeClick()) {
            // niets
        }
        while (ImtKeys.GUN_FIRE_MODE.consumeClick()) {
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
        FirearmType type = FirearmItem.typeOf(gun);
        // Hoofdstuk 103 (jouw keuze): een grendel of een pomp laad je zelf door. Is dat nodig, dan doet R dat eerst.
        if (type != null && Firearms.needsCycle(gun)) {
            request(Firearms.ActionKind.CYCLE, 0);
            return;
        }
        if (type != null && type.tube()) {
            // De Remington 870 heeft geen magazijn: R duwt een patroon in de buis (inhouden = doorgaan).
            tubeLoading = true;
            request(Firearms.ActionKind.LOAD_TUBE, 0);
            repeatAt = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + 4;
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
        FirearmType type = FirearmItem.typeOf(gun);
        if (type != null && type.tube()) {
            // Hoofdstuk 103: de buis van de Remington 870: U haalt er een patroon uit; is hij leeg, dan de kamer.
            if (FirearmItem.roundsInMagazine(gun) > 0) {
                request(Firearms.ActionKind.UNLOAD_TUBE, 0);
                return;
            }
        } else if (FirearmItem.hasMagazine(gun)) {
            request(Firearms.ActionKind.EJECT_MAG, 0);
            return;
        }
        if (FirearmItem.chambered(gun) || FirearmItem.spent(gun)) {
            request(Firearms.ActionKind.UNLOAD_CHAMBER, 0);
            return;
        }
        player.displayClientMessage(Component.translatable("gun.imttech.nothing_to_unload").withStyle(ChatFormatting.RED), true);
    }

    /** Kan er nog een patroon in de buis van het wapen in je hand (hij is niet vol, en je hebt er nog een bij)? */
    private static boolean canLoadTube(LocalPlayer player) {
        ItemStack gun = player.getMainHandItem();
        FirearmType type = FirearmItem.typeOf(gun);
        if (type == null || !type.tube() || FirearmItem.roundsInMagazine(gun) >= FirearmItem.capacity(gun)) {
            return false;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(type.caliber.round())) {
                return true;
            }
        }
        return false;
    }

    /** Een handeling aan de server vragen; zodra hij antwoordt, begint de animatie. */
    public static void request(Firearms.ActionKind kind, int arg) {
        waiting = true;
        waitingSince = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        aim = 0;
        PacketDistributor.sendToServer(new FirearmActionPayload(kind.ordinal(), arg, 0));
    }

    /** Hetzelfde voor iets wat niet duurt (de keuzeschakelaar): je blijft richten, en je spel wacht nergens op. */
    private static void requestQuiet(Firearms.ActionKind kind, int arg) {
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
        if (kind == Firearms.ActionKind.SELECT_FIRE) {
            return; // de keuzeschakelaar duurt niet: niets te tekenen, en een andere handeling loopt gewoon door
        }
        waiting = false;
        if (payload.ticks() <= 0) {
            if (action == kind) {
                action = null;
            }
            if (kind == Firearms.ActionKind.LOAD_TUBE && tubeLoading) {
                // Geweigerd. Is de buis vol of zijn je patronen op, dan stopt het laden. Maar meestal was de server gewoon
                // nog een tikje bezig met de vorige patroon (jouw spel loopt daar net op voor): dan proberen we het zo
                // meteen opnieuw, anders stopte het laden na elke patroon terwijl je R nog inhoudt.
                tubeLoading = me != null && canLoadTube(me);
                repeatAt = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + 2;
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

    /** De trekker overhalen. Geeft true als er (volgens je eigen spel) een schot afging. */
    private static boolean tryFire(LocalPlayer player, ItemStack gun, FirearmType type, long now) {
        if (busy() || drawn(0) < 1 || now < nextShot) {
            return false;
        }
        nextShot = now + type.interval();
        inspectAt = -1;
        boolean aiming = aim > 0.5f;
        if (!FirearmItem.works(gun) || FirearmItem.jam(gun) != FirearmItem.JAM_NONE || !FirearmItem.chambered(gun)) {
            // Een klik: de server zegt waarom, en speelt de klik voor iedereen.
            clickAt = now;
            PacketDistributor.sendToServer(new FirearmFirePayload(player.getYRot(), player.getXRot(), aiming));
            return false;
        }
        if (player.isSprinting()) {
            player.setSprinting(false);
        }
        shotAt = now;
        PacketDistributor.sendToServer(new FirearmFirePayload(player.getYRot(), player.getXRot(), aiming));
        // Stap 6: met een demper een doffe plof in plaats van de knal.
        boolean quiet = FirearmItem.suppressed(gun);
        play(player, quiet ? type.suppressedSound() : type.fireSound(), 1.0f);
        // Hoofdstuk 103: je spel doet alvast wat de server ook doet (de kamer leeg, de volgende patroon erin), zodat
        // automatisch vuur niet hoeft te wachten tot de server het wapen terugstuurt. De server stuurt daarna het echte
        // wapen, ook als het anders liep (een storing).
        FirearmItem.setChambered(gun, false);
        if (type.manual()) {
            FirearmItem.setSpent(gun, true);
        } else if (FirearmItem.takeRoundFromMagazine(gun)) {
            FirearmItem.setChambered(gun, true);
        }
        // De terugslag: omhoog (minder als je richt, en naar het materiaal van de slede), en een beetje opzij.
        // Stap 6: een rechte greep dempt hem, en de tweepoot bijna helemaal als je sneakt en stilstaat.
        float kick = type.recoil(aiming) * FirearmItem.parts(gun).materialOf(GunPart.SLIDE).recoilFactor()
                * GunSkillsClient.recoilFactor(type) * FirearmItem.recoilFactor(gun, braced(player));
        player.setXRot(Mth.clamp(player.getXRot() - kick, -90, 90));
        player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5f) * kick * 0.5f);
        return true;
    }

    // ================================================================ Wat Minecraft zelf zou doen

    /** Met een pistool in je hand: linkerklik slaat niet, rechterklik gebruikt niets (het is richten). */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (held == null || event.isPickBlock()) {
            return;
        }
        // Hoofdstuk 67: rechtsklik op een Pegboard (ophangen) of een Ammo Box (wisselen) mag door.
        if (event.isUseItem() && com.senne.imttech.client.armory.ArmoryClient.usesArmory()) {
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

    /** Hoofdstuk 103, stap 6: hoe ver de tweepoot van het wapen in je hand uitgeklapt is (0 tot 1). */
    private static float bipod;
    private static float bipodO;

    public static float bipod(float partialTick) {
        float t = Mth.lerp(partialTick, bipodO, bipod);
        return t * t * (3 - 2 * t);
    }

    /**
     * Steun je het wapen: sneak je, sta je op de grond en sta je (bijna) stil? Dan staat een tweepoot op zijn pootjes
     * (jouw keuze: "een tweepoot voor de AWP: sneak je, dan staat hij op zijn pootjes").
     */
    public static boolean braced(net.minecraft.world.entity.player.Player player) {
        net.minecraft.world.phys.Vec3 move = player.getDeltaMovement();
        return player.isCrouching() && player.onGround() && move.x * move.x + move.z * move.z < 0.0009;
    }

    /**
     * Hoofdstuk 103, stap 5: de Sniper Scope op het wapen in je hand (of null), en hoe ver hij nu staat. Eén kijker "van
     * 1x tot 10x en alles ertussenin" (jouw keuze): je verstelt hem met het scrollwiel terwijl je richt. De stand blijft
     * staan zolang het spel open is.
     */
    private static com.senne.imttech.guns.Attachment scoped;
    private static float scopeZoom = 4.0f;

    /** Staat er een kijker op je wapen waarmee je zoomt, en richt je (of ben je ermee bezig)? */
    public static boolean scoping() {
        return held != null && scoped != null && aim > 0.05f;
    }

    /** Zo ver staat de kijker nu (1 tot 10). */
    public static float scopeZoom() {
        return scopeZoom;
    }

    /** Voor de rondleiding: de kijker op deze stand zetten. */
    public static void setScopeZoom(float zoom) {
        scopeZoom = Math.max(1, zoom);
    }

    /** Zo ver zoom je nu in (1 = niet): het ijzeren vizier of de red dot een beetje, de Sniper Scope zo ver als hij staat. */
    private static float zoom(float partialTick) {
        if (held == null) {
            return 1;
        }
        float a = aim(partialTick);
        if (scoped != null) {
            // Vloeiend van 1 naar de stand van de kijker: in gelijke verhoudingen, niet in gelijke stappen.
            return (float) Math.pow(scopeZoom, a);
        }
        return 1 + (held.zoom() - 1) * a;
    }

    /**
     * Richten: inzoomen. Over een ijzeren vizier of door een red dot een beetje, zoals altijd (de kijkhoek gedeeld door
     * de zoom). Door de Sniper Scope een echte "4x": de tangens van de kijkhoek gedeeld door vier, niet de hoek zelf,
     * zodat alles in het midden van je beeld precies vier keer zo groot is.
     */
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov() || held == null) {
            return;
        }
        float zoom = zoom((float) event.getPartialTick());
        if (zoom > 1.001f) {
            if (scoped == null) {
                event.setFOV(event.getFOV() / zoom);
                return;
            }
            double half = Math.toRadians(event.getFOV() / 2);
            event.setFOV(Math.toDegrees(2 * Math.atan(Math.tan(half) / zoom)));
        }
    }

    public static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        if (held == null) {
            return;
        }
        float zoom = zoom(1);
        if (zoom > 1.001f) {
            // Ingezoomd beweegt het beeld sneller: de muis gaat evenredig trager (10x ingezoomd = 10x trager), zodat
            // een beweging van je hand op je scherm even ver gaat. Minecraft rekent met (0,6 x gevoeligheid + 0,2) tot
            // de derde macht: daarom delen we dat getal door de derdemachtswortel van de zoom, en rekenen we terug.
            double base = event.getMouseSensitivity() * 0.6 + 0.2;
            event.setMouseSensitivity((base / Math.cbrt(zoom) - 0.2) / 0.6);
        }
    }

    /**
     * Het scrollwiel terwijl je door de Sniper Scope richt: in- en uitzoomen, een vijfde per tikje (in plaats van een
     * ander vakje van je werkbalk kiezen).
     */
    public static void onMouseScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || held == null || scoped == null || aim < 0.5f || event.getScrollDeltaY() == 0) {
            return;
        }
        float before = scopeZoom;
        scopeZoom = Mth.clamp((float) (scopeZoom * Math.pow(1.2, event.getScrollDeltaY())), 1, scoped.maxZoom());
        if (scopeZoom != before && mc.player != null) {
            play(mc.player, net.minecraft.sounds.SoundEvents.SPYGLASS_USE, 0.25f);
        }
        event.setCanceled(true);
    }

    // ================================================================ Hulpjes

    static void play(LocalPlayer player, SoundEvent sound, float volume) {
        player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS,
                volume, 0.95f + player.getRandom().nextFloat() * 0.1f, false);
    }

    /**
     * Uit de wereld: alles terug, ook alle tijden. Die zijn speltijd van de wereld waar je in zat, en elke wereld heeft
     * zijn eigen klok: een server die al weken draait staat op miljoenen ticks, een nieuwe singleplayerwereld op een paar
     * duizend. Bleef nextShot staan, dan wachtte je na een schot op de server in je eigen wereld op een tijd die daar pas
     * over dagen komt: je kon er niet meer schieten, zonder klik of melding (hoofdstuk 91).
     */
    public static void reset() {
        held = null;
        heldMagazine = false;
        heldSlot = -1;
        aim = aimO = sprint = sprintO = 0;
        inspectAt = -1;
        action = null;
        waiting = false;
        nextShot = 0;
        burstLeft = 0;
        tubeLoading = false;
        repeatAt = 0;
        waitingSince = 0;
        drawnAt = shotAt = clickAt = -100;
        triggerWasDown = false;
        wasAiming = false;
    }
}
