package com.senne.imttech.guns;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.FirearmActionPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De server-kant van de vuurwapens (hoofdstuk 52): de trekker, de kamer en het magazijn, slijtage en storingen, en
 * alle handelingen die even duren (herladen, een tap-rack, een patroon in de kamer, een magazijn vullen). Jouw spel
 * stuurt alleen "ik haal de trekker over" en "ik wil dit doen"; hier beslissen we of dat kan, en we zeggen terug hoe
 * lang het duurt (zie FirearmActionPayload), zodat de animatie even lang loopt.
 *
 * <b>Een schot</b>: zit er een patroon in de kamer, dan gaat hij af (soms niet: een ketser, dan staat de slagpin op
 * een slechte patroon en moet je een tap-rack doen). De huls springt er rechts uit ({@link CasingEntity}), het
 * magazijn duwt de volgende patroon in de kamer. Soms blijft de huls klem zitten (een "stovepipe"): ook een tap-rack.
 * Elk schot heeft per onderdeel een kans om een punt te slijten (van zijn materiaal); versleten onderdelen geven meer
 * storingen, en een kapot onderdeel legt het pistool stil tot je het vervangt op de Gunsmith Table.
 *
 * <b>Herladen</b> (jouw keuze): met een magazijnpouch kies je een magazijn uit de pouch (hoofdstuk 52, stap 2). Zonder
 * pouch: R gooit het magazijn eruit (het komt in je inventaris), je scrolt naar een magazijn, rechtsklik duwt het in
 * het pistool, en als je het pistool weer pakt, laat je de slede los. Dat duurt langer, en zo hoort het.
 */
public final class Firearms {
    private Firearms() {}

    /** Hoe ver (graden) je eigen kijkrichting mag afwijken van wat de server denkt. */
    private static final double MAX_AIM_DIFFERENCE = 25;

    /** Wat er gebeurde toen je de trekker overhaalde (voor de tests). */
    public enum Result { FIRED, NO_GUN, INCOMPLETE, BROKEN, JAMMED, TOO_SOON, EMPTY, BUSY, MISFIRE }

    /** De handelingen die even duren. Dezelfde volgorde als in jouw spel (client/guns/FirearmControl). */
    public enum ActionKind {
        /** R zonder pouch: het magazijn eruit, in je inventaris. */
        EJECT_MAG,
        /** Rechtsklik met een magazijn: in het pistool in je werkbalk dat er geen heeft. */
        INSERT_MAG,
        /** De slede loslaten: de eerste patroon uit het magazijn gaat in de kamer (vanzelf, als dat nodig is). */
        SLIDE_RELEASE,
        /** R bij een storing: op het magazijn tikken en de slede rakken. */
        TAP_RACK,
        /** Sneak + R: met de hand één patroon in de kamer steken (ook zonder magazijn). */
        CHAMBER_ROUND,
        /** R met een magazijn in je hand: één patroon erin duwen. */
        LOAD_ROUND,
        /** R met een magazijnpouch: dit magazijn uit de pouch erin (stap 2). */
        RELOAD_POUCH,
        /** Trekken uit de holster (stap 2). */
        DRAW
    }

    /** Een handeling die bezig is: wat, in welk vakje het item zit waarmee je bezig bent, iets extra, en wanneer klaar. */
    private record Action(ActionKind kind, int slot, int arg, long done) {}

    /** Wanneer (speltijd) elke speler weer mag schieten. */
    private static final Map<UUID, Long> NEXT_SHOT = new HashMap<>();
    private static final Map<UUID, Action> ACTIONS = new HashMap<>();
    /** In welk vakje elke speler het laatst een pistool vasthield (daar gaat een magazijn bij voorkeur in). */
    private static final Map<UUID, Integer> LAST_GUN_SLOT = new HashMap<>();

    // ================================================================ De trekker

    /** De trekker overhalen van het pistool in je hand, kijkend naar ({@code yaw}, {@code pitch}). */
    public static Result fire(ServerPlayer player, float yaw, float pitch, boolean aiming) {
        ItemStack gun = player.getMainHandItem();
        FirearmType type = FirearmItem.typeOf(gun);
        if (type == null || player.isSpectator()) {
            return Result.NO_GUN;
        }
        if (ACTIONS.containsKey(player.getUUID())) {
            return Result.BUSY;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        GunParts parts = FirearmItem.parts(gun);
        if (!parts.complete()) {
            tell(player, Component.translatable("gun.imttech.incomplete", parts.missing().size()));
            return Result.INCOMPLETE;
        }
        List<GunPart> broken = parts.broken();
        if (!broken.isEmpty()) {
            tell(player, Component.translatable("gun.imttech.broken", broken.getFirst().text()));
            click(player);
            return Result.BROKEN;
        }
        if (FirearmItem.jam(gun) != FirearmItem.JAM_NONE) {
            tell(player, Component.translatable("gun.imttech.jammed"));
            click(player);
            return Result.JAMMED;
        }
        Long next = NEXT_SHOT.get(player.getUUID());
        if (next != null && now < next - 1) {
            return Result.TOO_SOON; // één tick speling: je spel en de server lopen niet precies gelijk
        }
        NEXT_SHOT.put(player.getUUID(), now + type.interval());
        if (!FirearmItem.chambered(gun)) {
            click(player);
            return Result.EMPTY;
        }
        RandomSource random = player.getRandom();
        // Een ketser: de slagpin slaat, de patroon gaat niet af. Hij blijft in de kamer tot je een tap-rack doet.
        if (random.nextFloat() * 100 < misfireChance(parts)) {
            FirearmItem.setJam(gun, FirearmItem.JAM_MISFIRE);
            click(player);
            tell(player, Component.translatable("gun.imttech.misfire"));
            return Result.MISFIRE;
        }

        // Het schot.
        FirearmItem.setChambered(gun, false);
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 server = player.getViewVector(1.0f);
        if (Math.toDegrees(Math.acos(Mth.clamp(look.dot(server), -1, 1))) > MAX_AIM_DIFFERENCE) {
            look = server;
        }
        double spread = Math.toRadians(type.spread(aiming) * (1 + 0.5f * parts.wornFactor(GunPart.BARREL)));
        Vec3 direction = scatter(look, spread, random);
        float damage = type.damage() * parts.materialOf(GunPart.BARREL).damageFactor();
        damage *= 1 - 0.2f * Math.min(1, parts.wornFactor(GunPart.BARREL) / Math.max(0.01f, ImtConfig.Guns.WORN_JAM_FACTOR));
        BulletEntity.shoot(level, player, type, damage, direction);
        level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), ModSounds.GLOCK_FIRE.get(), SoundSource.PLAYERS,
                1.0f, 0.95f + random.nextFloat() * 0.1f);
        if (player.isSprinting()) {
            player.setSprinting(false);
        }

        // Slijtage: elk onderdeel een kans (van zijn materiaal) op een punt minder.
        GunParts worn = parts;
        for (GunPart part : GunPart.values()) {
            PartState state = worn.get(part);
            if (state != null && random.nextFloat() * 100 < state.material().wearChance()) {
                worn = worn.with(part, state.worn1());
            }
        }
        if (worn != parts) {
            FirearmItem.setParts(gun, worn);
            List<GunPart> nowBroken = worn.broken();
            if (!nowBroken.isEmpty()) {
                tell(player, Component.translatable("gun.imttech.just_broke", nowBroken.getFirst().text()));
            }
        }
        // De huls eruit en de volgende patroon erin; of een storing: de huls blijft klem zitten.
        if (random.nextFloat() * 100 < jamChance(worn)) {
            FirearmItem.setJam(gun, FirearmItem.JAM_STOVEPIPE);
            tell(player, Component.translatable("gun.imttech.stovepipe"));
        } else {
            CasingEntity.eject(level, player);
            if (FirearmItem.takeRoundFromMagazine(gun)) {
                FirearmItem.setChambered(gun, true);
            }
        }
        return Result.FIRED;
    }

    /** Zoveel procent kans per schot op een ketser: de slagpin en de trekker. */
    public static float misfireChance(GunParts parts) {
        return ImtConfig.Guns.MISFIRE_CHANCE * parts.materialOf(GunPart.STRIKER).reliability()
                * (1 + parts.wornFactor(GunPart.STRIKER) + parts.wornFactor(GunPart.TRIGGER));
    }

    /** Zoveel procent kans per schot dat de huls klem blijft zitten: de veren en de slede. */
    public static float jamChance(GunParts parts) {
        return ImtConfig.Guns.JAM_CHANCE * parts.materialOf(GunPart.SPRINGS).reliability()
                * (1 + parts.wornFactor(GunPart.SPRINGS) + parts.wornFactor(GunPart.SLIDE));
    }

    /** Een richting een stukje willekeurig opzij (hooguit {@code spread} radialen). */
    private static Vec3 scatter(Vec3 look, double spread, RandomSource random) {
        if (spread <= 0) {
            return look;
        }
        Vec3 up = Math.abs(look.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = look.cross(up).normalize();
        Vec3 down = look.cross(right).normalize();
        double angle = random.nextDouble() * Math.PI * 2;
        double off = spread * Math.sqrt(random.nextDouble());
        Vec3 side = right.scale(Math.cos(angle)).add(down.scale(Math.sin(angle)));
        return look.scale(Math.cos(off)).add(side.scale(Math.sin(off))).normalize();
    }

    private static void click(ServerPlayer player) {
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.GLOCK_DRY.get(),
                SoundSource.PLAYERS, 0.6f, 1.0f);
    }

    private static void tell(ServerPlayer player, Component text) {
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.RED), true);
    }

    // ================================================================ De handelingen

    /** Is deze speler met iets bezig (dan kan hij niet schieten)? */
    public static boolean isBusy(ServerPlayer player) {
        return ACTIONS.containsKey(player.getUUID());
    }

    public static @Nullable ActionKind currentAction(ServerPlayer player) {
        Action action = ACTIONS.get(player.getUUID());
        return action == null ? null : action.kind();
    }

    /** Zo lang (ticks) duurt deze handeling voor deze speler met dit pistool (het materiaal van het frame telt mee). */
    public static int ticksFor(ServerPlayer player, ActionKind kind, @Nullable ItemStack gun) {
        float base = switch (kind) {
            case EJECT_MAG -> ImtConfig.Guns.EJECT_TICKS;
            case INSERT_MAG -> ImtConfig.Guns.INSERT_TICKS;
            case SLIDE_RELEASE -> ImtConfig.Guns.SLIDE_TICKS;
            case TAP_RACK -> ImtConfig.Guns.TAP_RACK_TICKS;
            case CHAMBER_ROUND -> ImtConfig.Guns.CHAMBER_TICKS;
            case LOAD_ROUND -> ImtConfig.Guns.LOAD_ROUND_TICKS;
            case RELOAD_POUCH -> ImtConfig.Guns.POUCH_RELOAD_TICKS;
            case DRAW -> ImtConfig.Guns.DRAW_TICKS;
        };
        float factor = 1;
        if (gun != null && FirearmItem.typeOf(gun) != null && kind != ActionKind.LOAD_ROUND) {
            factor *= FirearmItem.parts(gun).materialOf(GunPart.FRAME).handlingFactor();
        }
        factor *= GunSkillHooks.timeFactor(player, kind, gun);
        return Math.max(1, Math.round(base * factor));
    }

    /**
     * Jouw spel vraagt een handeling (R, sneak + R, of een keuze uit de pouch). Geeft true als hij begint; dan krijgt
     * jouw spel ook een bericht met hoe lang hij duurt. Anders een bericht met 0 ticks (en meestal een reden onderaan
     * je scherm).
     */
    public static boolean request(ServerPlayer player, ActionKind kind, int arg) {
        boolean started = switch (kind) {
            case EJECT_MAG -> ejectMagazine(player);
            case TAP_RACK -> tapRack(player);
            case CHAMBER_ROUND -> chamberRound(player);
            case LOAD_ROUND -> loadRound(player);
            case RELOAD_POUCH -> GunGearHooks.reloadFromPouch(player, arg);
            case INSERT_MAG, SLIDE_RELEASE, DRAW -> false; // die beginnen niet op verzoek
        };
        if (!started) {
            ModNetwork.sendTo(player, new FirearmActionPayload(kind.ordinal(), arg, 0));
        }
        return started;
    }

    /** Een handeling beginnen met het item in het gekozen vakje; jouw spel hoort hoe lang het duurt. */
    static boolean start(ServerPlayer player, ActionKind kind, int slot, int arg, @Nullable ItemStack gun) {
        if (ACTIONS.containsKey(player.getUUID())) {
            return false;
        }
        int ticks = ticksFor(player, kind, gun);
        ACTIONS.put(player.getUUID(), new Action(kind, slot, arg, player.serverLevel().getGameTime() + ticks));
        ModNetwork.sendTo(player, new FirearmActionPayload(kind.ordinal(), arg, ticks));
        return true;
    }

    /** Het pistool in je hand, of null (met een melding als je er geen vasthoudt). */
    private static @Nullable ItemStack heldGun(ServerPlayer player) {
        ItemStack gun = player.getMainHandItem();
        return FirearmItem.typeOf(gun) == null ? null : gun;
    }

    /** R zonder pouch: het magazijn eruit. */
    private static boolean ejectMagazine(ServerPlayer player) {
        ItemStack gun = heldGun(player);
        if (gun == null || isBusy(player)) {
            return false;
        }
        if (!FirearmItem.hasMagazine(gun)) {
            tell(player, Component.translatable("gun.imttech.no_magazine_hint"));
            return false;
        }
        sound(player, ModSounds.GLOCK_MAG_OUT.get(), 0.8f);
        return start(player, ActionKind.EJECT_MAG, player.getInventory().selected, 0, gun);
    }

    /** Rechtsklik met een magazijn in je hand: in het pistool in je werkbalk dat er geen heeft. */
    public static boolean insertMagazine(ServerPlayer player, InteractionHand hand) {
        ItemStack magazine = player.getItemInHand(hand);
        FirearmType type = MagazineItem.typeOf(magazine);
        if (type == null || isBusy(player) || hand != InteractionHand.MAIN_HAND) {
            return false;
        }
        int gunSlot = gunWithoutMagazine(player, type);
        if (gunSlot < 0) {
            tell(player, Component.translatable("gun.imttech.no_gun_for_magazine"));
            return false;
        }
        ItemStack gun = player.getInventory().getItem(gunSlot);
        sound(player, ModSounds.GLOCK_MAG_IN.get(), 0.8f);
        return start(player, ActionKind.INSERT_MAG, player.getInventory().selected, gunSlot, gun);
    }

    /** Het vakje met een pistool van dit soort zonder magazijn: eerst waar je het laatst een pistool vasthield, dan de werkbalk, dan de rest. */
    private static int gunWithoutMagazine(ServerPlayer player, FirearmType type) {
        Inventory inventory = player.getInventory();
        Integer last = LAST_GUN_SLOT.get(player.getUUID());
        if (last != null && fitsMagazine(inventory.getItem(last), type)) {
            return last;
        }
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (fitsMagazine(inventory.getItem(slot), type)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean fitsMagazine(ItemStack gun, FirearmType type) {
        return FirearmItem.typeOf(gun) == type && !FirearmItem.hasMagazine(gun);
    }

    /** R bij een storing: een tap-rack. */
    private static boolean tapRack(ServerPlayer player) {
        ItemStack gun = heldGun(player);
        if (gun == null || isBusy(player) || FirearmItem.jam(gun) == FirearmItem.JAM_NONE) {
            return false;
        }
        sound(player, ModSounds.TAP_RACK.get(), 0.9f);
        return start(player, ActionKind.TAP_RACK, player.getInventory().selected, 0, gun);
    }

    /** Sneak + R: met de hand een patroon in de kamer. */
    private static boolean chamberRound(ServerPlayer player) {
        ItemStack gun = heldGun(player);
        if (gun == null || isBusy(player)) {
            return false;
        }
        if (FirearmItem.chambered(gun) || FirearmItem.jam(gun) != FirearmItem.JAM_NONE) {
            return false;
        }
        if (!FirearmItem.parts(gun).complete()) {
            tell(player, Component.translatable("gun.imttech.incomplete", FirearmItem.parts(gun).missing().size()));
            return false;
        }
        if (findRound(player) < 0) {
            tell(player, Component.translatable("gun.imttech.no_rounds"));
            return false;
        }
        sound(player, ModSounds.GLOCK_SLIDE.get(), 0.7f);
        return start(player, ActionKind.CHAMBER_ROUND, player.getInventory().selected, 0, gun);
    }

    /** R met een magazijn in je hand: één patroon erin. */
    private static boolean loadRound(ServerPlayer player) {
        ItemStack magazine = player.getMainHandItem();
        if (MagazineItem.typeOf(magazine) == null || isBusy(player)) {
            return false;
        }
        if (MagazineItem.isFull(magazine)) {
            tell(player, Component.translatable("gun.imttech.magazine_full"));
            return false;
        }
        if (findRound(player) < 0) {
            tell(player, Component.translatable("gun.imttech.no_rounds"));
            return false;
        }
        return start(player, ActionKind.LOAD_ROUND, player.getInventory().selected, 0, null);
    }

    /** Het vakje met patronen (niet je hand), of -1. */
    private static int findRound(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (slot != inventory.selected && inventory.getItem(slot).is(ModItems.ROUND_9MM.get())) {
                return slot;
            }
        }
        return -1;
    }

    /** Eén patroon uit je inventaris halen (geeft false als je er geen hebt). */
    private static boolean takeRound(ServerPlayer player) {
        int slot = findRound(player);
        if (slot < 0) {
            return false;
        }
        player.getInventory().getItem(slot).shrink(1);
        return true;
    }

    /** Zoveel patronen heb je bij je (los). */
    public static int roundsCarried(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.ROUND_9MM.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    // ================================================================ Elke tick

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ItemStack held = player.getMainHandItem();
            if (FirearmItem.typeOf(held) != null) {
                LAST_GUN_SLOT.put(player.getUUID(), player.getInventory().selected);
            }
            Action action = ACTIONS.get(player.getUUID());
            if (action != null) {
                tickAction(player, action);
            } else if (FirearmItem.typeOf(held) != null && needsSlideRelease(held)) {
                // De slede staat open en er zit een vol magazijn in: loslaten (de eerste patroon gaat in de kamer).
                sound(player, ModSounds.GLOCK_SLIDE.get(), 0.8f);
                start(player, ActionKind.SLIDE_RELEASE, player.getInventory().selected, 0, held);
            }
        }
        // Spelers die weg zijn vergeten.
        for (Iterator<UUID> it = ACTIONS.keySet().iterator(); it.hasNext(); ) {
            if (event.getServer().getPlayerList().getPlayer(it.next()) == null) {
                it.remove();
            }
        }
    }

    /** Moet de slede nog losgelaten worden: kamer leeg, patronen in het magazijn, geen storing, alles heel? */
    private static boolean needsSlideRelease(ItemStack gun) {
        return !FirearmItem.chambered(gun) && FirearmItem.roundsInMagazine(gun) > 0
                && FirearmItem.jam(gun) == FirearmItem.JAM_NONE && FirearmItem.works(gun);
    }

    private static void tickAction(ServerPlayer player, Action action) {
        Inventory inventory = player.getInventory();
        ItemStack held = player.getMainHandItem();
        boolean sameSlot = inventory.selected == action.slot();
        boolean stillValid = sameSlot && switch (action.kind()) {
            case LOAD_ROUND -> MagazineItem.typeOf(held) != null;
            case INSERT_MAG -> MagazineItem.typeOf(held) != null && FirearmItem.typeOf(inventory.getItem(action.arg())) != null;
            default -> FirearmItem.typeOf(held) != null;
        };
        if (!stillValid) {
            ACTIONS.remove(player.getUUID());
            ModNetwork.sendTo(player, new FirearmActionPayload(action.kind().ordinal(), action.arg(), 0));
            return;
        }
        if (player.serverLevel().getGameTime() < action.done()) {
            return;
        }
        ACTIONS.remove(player.getUUID());
        finish(player, action, held);
    }

    /** De handeling is klaar: nu gebeurt het echt. */
    private static void finish(ServerPlayer player, Action action, ItemStack held) {
        ServerLevel level = player.serverLevel();
        switch (action.kind()) {
            case EJECT_MAG -> {
                ItemStack magazine = FirearmItem.magazine(held);
                FirearmItem.setMagazine(held, ItemStack.EMPTY);
                if (!magazine.isEmpty() && !GunGearHooks.storeMagazine(player, magazine)) {
                    give(player, magazine);
                }
            }
            case INSERT_MAG -> {
                ItemStack gun = player.getInventory().getItem(action.arg());
                if (FirearmItem.typeOf(gun) == MagazineItem.typeOf(held) && !FirearmItem.hasMagazine(gun)) {
                    FirearmItem.setMagazine(gun, held);
                    player.getInventory().setItem(action.slot(), ItemStack.EMPTY);
                    GunSkillHooks.reloaded(player, gun);
                }
            }
            case SLIDE_RELEASE -> {
                if (needsSlideRelease(held) && FirearmItem.takeRoundFromMagazine(held)) {
                    FirearmItem.setChambered(held, true);
                }
            }
            case TAP_RACK -> {
                int jam = FirearmItem.jam(held);
                FirearmItem.setJam(held, FirearmItem.JAM_NONE);
                if (jam == FirearmItem.JAM_STOVEPIPE) {
                    CasingEntity.eject(level, player);
                }
                // Een ketser gaat verloren (de slechte patroon valt eruit); de volgende komt uit het magazijn.
                FirearmItem.setChambered(held, false);
                if (FirearmItem.takeRoundFromMagazine(held)) {
                    FirearmItem.setChambered(held, true);
                }
                GunSkillHooks.cleared(player, held);
            }
            case CHAMBER_ROUND -> {
                if (!FirearmItem.chambered(held) && takeRound(player)) {
                    FirearmItem.setChambered(held, true);
                    sound(player, ModSounds.GLOCK_SLIDE.get(), 0.8f);
                }
            }
            case LOAD_ROUND -> {
                if (!MagazineItem.isFull(held) && takeRound(player)) {
                    MagazineItem.setRounds(held, MagazineItem.rounds(held) + 1);
                    sound(player, ModSounds.GLOCK_ROUND_LOAD.get(), 0.7f);
                    GunSkillHooks.roundLoaded(player);
                }
            }
            case RELOAD_POUCH -> GunGearHooks.finishPouchReload(player, held, action.arg());
            case DRAW -> GunSkillHooks.drawn(player, held);
        }
    }

    /** Iets in je inventaris stoppen, of laten vallen als die vol is. */
    public static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume) {
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, volume,
                0.95f + player.getRandom().nextFloat() * 0.1f);
    }

    // ================================================================ Hulzen oprapen

    /** Rechtsklik op de grond vlak bij een huls (met een lege hand, of met hulzen in je hand): oprapen. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty() && !held.is(ModItems.CASING_9MM.get())) {
            return;
        }
        int picked = pickUpCasings(player, event.getHitVec().getLocation());
        if (picked > 0) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** Alle hulzen binnen {@code [guns] casingPickupRadius} van dit punt oprapen; geeft hoeveel. */
    public static int pickUpCasings(ServerPlayer player, Vec3 at) {
        double radius = ImtConfig.Guns.CASING_PICKUP_RADIUS;
        List<CasingEntity> casings = player.serverLevel().getEntitiesOfClass(CasingEntity.class,
                new AABB(at, at).inflate(radius), casing -> casing.position().distanceTo(at) <= radius + 0.3);
        if (casings.isEmpty()) {
            return 0;
        }
        for (Entity casing : casings) {
            casing.discard();
        }
        give(player, new ItemStack(ModItems.CASING_9MM.get(), casings.size()));
        sound(player, ModSounds.CASING_TINK.get(), 0.6f);
        player.displayClientMessage(Component.translatable("gun.imttech.casings_picked", casings.size())
                .withStyle(ChatFormatting.GOLD), true);
        return casings.size();
    }

    // ================================================================ Opruimen

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        NEXT_SHOT.remove(event.getEntity().getUUID());
        ACTIONS.remove(event.getEntity().getUUID());
        LAST_GUN_SLOT.remove(event.getEntity().getUUID());
    }

    public static void clear() {
        NEXT_SHOT.clear();
        ACTIONS.clear();
        LAST_GUN_SLOT.clear();
    }

    /** Voor de tests: alsof er al lang niet geschoten is. */
    public static void forgetCooldown(Entity player) {
        NEXT_SHOT.remove(player.getUUID());
    }

    /** Voor de tests: een handeling meteen afmaken. */
    public static void finishNow(ServerPlayer player) {
        Action action = ACTIONS.remove(player.getUUID());
        if (action != null) {
            finish(player, action, player.getMainHandItem());
        }
    }
}
