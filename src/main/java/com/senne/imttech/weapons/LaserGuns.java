package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.Firearms;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunSkill;
import com.senne.imttech.guns.GunSkillHooks;
import com.senne.imttech.guns.GunSkillsServer;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.network.GunReloadPayload;
import com.senne.imttech.network.GunShotPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.registry.ModDamageTypes;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.shield.ImtDomes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * De server-kant van de lasergeweren (hoofdstuk 35): schieten, raken en herladen. Jouw spel tekent alles en stuurt alleen
 * "ik schiet" (met waar je keek) en "ik herlaad"; hier controleren we of dat mag (houd je een geweer vast, is het niet
 * te snel, zit er genoeg in de cel) en wat er geraakt wordt.
 *
 * <b>Laden en lossen zoals de Glock</b> (hoofdstuk 57, jouw wens: "niet meer herladen zoals vroeger, ze moeten werken
 * zoals de Glock"): R met een magazijnpouch wisselt met een cel uit de pouch (de oude komt op zijn plek); zonder pouch
 * haalt R de cel eruit (vroeger pakte R de volste cel uit je inventaris, dat is weg), en een cel erin doe je met
 * <b>rechtsklik met de cel in je hand</b>: in het lasergeweer in je werkbalk dat er geen heeft ({@link #insertCell}).
 * <b>U</b> haalt de cel eruit: in je magazijnpouch als daar plek is, anders in je inventaris ({@link #eject}).
 *
 * Een schot is meteen raak ("hitscan"): een rechte straal vanaf je ogen, met een beetje spreiding (minder als je
 * richt). Muren houden hem tegen, een schildkoepel ook (zoals andere lasers). Een schot in het hoofd doet 1,5 keer zoveel.
 * Spelers raak je alleen als PvP aan staat (dat regelt Minecraft zelf). De laserbouten die je ziet, tekent elk spel zelf
 * (zie GunShotPayload).
 *
 * Sinds hoofdstuk 55 bestaat een geweer uit onderdelen (zie {@link LaserParts}), en die doen mee: het geweer schiet
 * alleen compleet, met een kern en zonder kapot onderdeel; de emitter bepaalt de schade en het bereik, de optics de
 * spreiding, de capacitor de lading per schot, de trigger hoe snel je schiet, de receiver het herladen (en de terugslag,
 * in je eigen spel), het frame het trekken. Elk schot slijt elk onderdeel een beetje (jouw keuze: minder dan de Glock,
 * en geen storingen: een laser heeft geen hulzen); versleten onderdelen maken hun eigen cijfer slechter (mijn
 * invulling), kapot = het geweer doet niets tot je het vervangt op de Laser Workbench. En de skills (hoofdstuk 52, stap
 * 3) tellen nu ook voor lasergeweren: elk geweer heeft zijn eigen rijtje naast de globale skills.
 */
public final class LaserGuns {
    private LaserGuns() {}

    /** Hoe ver (graden) je eigen kijkrichting mag afwijken van wat de server denkt (anders nemen we die van de server). */
    private static final double MAX_AIM_DIFFERENCE = 25;
    /** Tot hoe ver andere spelers je schoten zien en horen (blokken). */
    private static final double VISIBLE = 96;

    /** Wat er gebeurde met een schot (voor de tests). */
    public enum Result { FIRED, NO_GUN, TOO_SOON, EMPTY, RELOADING, INCOMPLETE, BROKEN, BUSY }

    /** Wanneer (speltijd) elke speler weer mag schieten. */
    private static final Map<UUID, Long> NEXT_SHOT = new HashMap<>();

    /** Het "vakje" dat zegt: niet wisselen maar alleen de cel eruit (R zonder pouch, of U; hoofdstuk 57). */
    public static final int EJECT = -1;
    /** Het "vakje" dat zegt: de cel komt uit een Ammo Box (hoofdstuk 67, een lasergeweer op een doos cellen). */
    public static final int FROM_BOX = -2;

    /**
     * Iemand die aan het herladen is: in welk vakje zijn geweer zit, uit welk vakje van zijn magazijnpouch de cel komt
     * ({@code pouch}, of {@link #EJECT}: alleen de cel eruit), en wanneer het klaar is.
     */
    private record Reload(int slot, GunType type, int pouch, long done) {}

    private static final Map<UUID, Reload> RELOADS = new HashMap<>();

    /** Iemand die een cel in een geweer steekt (rechtsklik, hoofdstuk 57): de cel in zijn hand, het geweer in dit vakje. */
    private record Insert(int slot, int gunSlot, long done) {}

    private static final Map<UUID, Insert> INSERTS = new HashMap<>();

    // ================================================================ De onderdelen in de rekensommen (hoofdstuk 55)

    /** Zoveel procent kans per schot dat een laseronderdeel van dit materiaal een punt slijt (minder dan bij de Glock). */
    public static float wearChance(GunMaterial material) {
        return material.wearChance() * Math.max(0, ImtConfig.Guns.LASER_WEAR_FACTOR);
    }

    /** Zoveel procent van een cel kost één schot met dit geweer: de capacitor, versleten, kost meer. */
    public static float shotCost(ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        return type == null ? 0 : type.cost() * (1 + LaserGunItem.parts(gun).penalty(LaserPart.CAPACITOR));
    }

    /** Zoveel ticks minstens tussen twee schoten: een versleten trigger is trager. */
    public static int interval(ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        return type == null ? 1 : Math.max(1, Math.round(type.interval() * (1 + LaserGunItem.parts(gun).penalty(LaserPart.TRIGGER))));
    }

    /**
     * Een laseronderdeel van titanium is de maat: de getallen onder [weapons] gelden voor een titanium geweer, en
     * netherite doet het een beetje beter (zoals [guns] zegt, naast titanium).
     */
    private static float relative(float value, float titanium) {
        return value / Math.max(0.01f, titanium);
    }

    /** Hoe lang trekken duurt, maal dit: het materiaal van het frame, en hoe versleten het is. */
    public static float handlingFactor(ItemStack gun) {
        LaserParts parts = LaserGunItem.parts(gun);
        return relative(parts.materialOf(LaserPart.FRAME).handlingFactor(), GunMaterial.TITANIUM.handlingFactor())
                * (1 + parts.penalty(LaserPart.FRAME));
    }

    /** De terugslag, maal dit: het materiaal van de receiver, en hoe versleten hij is (ook in je eigen spel). */
    public static float recoilFactor(ItemStack gun) {
        LaserParts parts = LaserGunItem.parts(gun);
        return relative(parts.materialOf(LaserPart.RECEIVER).recoilFactor(), GunMaterial.TITANIUM.recoilFactor())
                * (1 + parts.penalty(LaserPart.RECEIVER));
    }

    /** Zo lang duurt herladen (ticks) voor deze speler met dit geweer: de receiver, en de skill Reload. */
    public static int reloadTicks(ServerPlayer player, ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        if (type == null) {
            return 1;
        }
        float ticks = type.reloadTicks() * (1 + LaserGunItem.parts(gun).penalty(LaserPart.RECEIVER));
        ticks *= GunSkillsServer.of(player).factor(GunSkill.RELOAD, type.id);
        return Math.max(5, Math.round(ticks));
    }

    // ================================================================ Schieten

    /** Een schot met het geweer in je hand, waar je naar keek ({@code yaw}, {@code pitch}). */
    public static Result fire(ServerPlayer player, float yaw, float pitch, boolean aiming) {
        ItemStack gun = player.getMainHandItem();
        GunType type = LaserGunItem.typeOf(gun);
        if (type == null || player.isSpectator()) {
            return Result.NO_GUN;
        }
        if (RELOADS.containsKey(player.getUUID())) {
            return Result.RELOADING;
        }
        if (Firearms.isBusy(player)) {
            return Result.BUSY; // je trekt het nog (uit de holster of van je sling)
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        // De onderdelen (hoofdstuk 55): compleet, met een kern, en niets kapot.
        LaserParts parts = LaserGunItem.parts(gun);
        if (!parts.complete()) {
            List<LaserPart> missing = parts.missing();
            tell(player, missing.size() == 1 && missing.getFirst() == LaserPart.CORE
                    ? Component.translatable("weapon.imttech.gun.no_core")
                    : Component.translatable("weapon.imttech.gun.incomplete", missing.size()));
            return Result.INCOMPLETE;
        }
        List<LaserPart> broken = parts.broken();
        if (!broken.isEmpty()) {
            tell(player, Component.translatable("weapon.imttech.gun.broken", broken.getFirst().text()));
            level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), ModSounds.LASER_GUN_EMPTY.get(),
                    SoundSource.PLAYERS, 0.6f, 0.8f);
            return Result.BROKEN;
        }
        Long next = NEXT_SHOT.get(player.getUUID());
        if (next != null && now < next - 1) {
            return Result.TOO_SOON; // één tick speling: je spel en de server lopen niet precies gelijk
        }
        float cell = LaserGunItem.cell(gun);
        float cost = shotCost(gun);
        int interval = interval(gun);
        if (cell < cost - 1.0E-4f) {
            level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), ModSounds.LASER_GUN_EMPTY.get(),
                    SoundSource.PLAYERS, 0.6f, 1.0f);
            NEXT_SHOT.put(player.getUUID(), now + interval);
            return Result.EMPTY;
        }
        LaserGunItem.setCell(gun, cell - cost);
        NEXT_SHOT.put(player.getUUID(), now + interval);

        Vec3 eye = player.getEyePosition();
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 server = player.getViewVector(1.0f);
        if (Math.toDegrees(Math.acos(Mth.clamp(look.dot(server), -1, 1))) > MAX_AIM_DIFFERENCE) {
            look = server;
        }
        RandomSource random = player.getRandom();
        // De optics: versleten strooit meer; de skill Steady aim (globaal en van dit geweer) strooit minder.
        double spread = Math.toRadians(type.spread(aiming) * (aiming ? com.senne.imttech.guns.FirearmItem.aimSpreadFactor(gun) : 1)
                * (1 + parts.penalty(LaserPart.OPTICS))
                * GunSkillsServer.of(player).factor(GunSkill.AIM, type.id));
        // De emitter: netherite doet iets meer, versleten minder (en reikt minder ver).
        float shotDamage = type.damage() * relative(parts.materialOf(LaserPart.EMITTER).damageFactor(), GunMaterial.TITANIUM.damageFactor())
                * (1 - parts.penalty(LaserPart.EMITTER));
        double range = type.range() * (1 - 0.5 * parts.penalty(LaserPart.EMITTER));
        List<Vec3> ends = new ArrayList<>();
        int hits = 0;
        Map<LivingEntity, Float> damage = new LinkedHashMap<>();
        // Hoofdstuk 109: hoeveel schade elk doelwit in zijn hoofd kreeg (de plate van zijn vest vangt alleen de rest op).
        Map<LivingEntity, Float> headDamage = new java.util.HashMap<>();
        for (int ray = 0; ray < type.rays(); ray++) {
            Vec3 dir = scatter(look, spread, random);
            Vec3 end = eye.add(dir.scale(range));
            HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (wall.getType() != HitResult.Type.MISS) {
                end = wall.getLocation();
            }
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, new AABB(eye, end).inflate(1.0),
                    entity -> entity.isAlive() && entity.isPickable() && !entity.isSpectator() && entity != player
                            && !(entity instanceof GrenadeEntity) // hoofdstuk 69: een granaat is aanklikbaar, maar je schiet erdoor
                            && !player.isPassengerOfSameVehicle(entity), 0.1f);
            LivingEntity target = null;
            if (hit != null) {
                // Waar de straal het wezen raakt (Minecraft geeft hier zijn voeten, en dan was een headshot onmogelijk).
                Entity entity = hit.getEntity();
                end = entity.getBoundingBox().inflate(0.1).clip(eye, end).orElse(entity.getBoundingBox().getCenter());
                target = entity instanceof LivingEntity living ? living : null;
            }
            // Een schildkoepel om het doelwit houdt de straal tegen (de eigen koepel niet).
            Vec3 shield = ImtDomes.blockedAt(level, end, eye, player.getUUID(), Math.round(ImtConfig.Dome.COST_LASER));
            if (shield != null) {
                end = shield;
                target = null;
            }
            if (target != null) {
                float amount = shotDamage;
                // In het hoofd: alles boven de ogen min een klein stukje.
                if (end.y >= target.getEyeY() - 0.25) {
                    amount *= ImtConfig.Weapons.HEADSHOT;
                    headDamage.merge(target, amount, Float::sum);
                }
                damage.merge(target, amount, Float::sum);
                hits |= 1 << ray;
            }
            ends.add(end);
        }
        // De schade per wezen in één keer (anders gooien de 8 stralen van de shotgun hem 8 keer weg), en meteen: geen
        // "even onkwetsbaar na een klap", anders deed het snelle geweer de helft van zijn schoten niets.
        for (Map.Entry<LivingEntity, Float> entry : damage.entrySet()) {
            LivingEntity target = entry.getKey();
            target.invulnerableTime = 0;
            float total = entry.getValue();
            float body = total <= 0 ? 0 : 1.0f - headDamage.getOrDefault(target, 0.0f) / total;
            target.hurt(com.senne.imttech.armor.HitSource.withBodyShare(ModDamageTypes.laserGun(level, player), body), total);
            GunSkillsServer.hit(player);
        }
        GunSkillHooks.fired(player);
        // Het geluid voor de anderen (jouw spel speelt het zelf al, meteen bij je klik).
        level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), type.fireSound(), SoundSource.PLAYERS,
                1.0f, 0.95f + random.nextFloat() * 0.1f);
        ModNetwork.sendNear(level, eye, VISIBLE, new GunShotPayload(player.getId(), type.ordinal(), ends, hits));
        // Hoofdstuk 60: de bewakers horen het (een pistool zacht, de rest wat verder).
        com.senne.imttech.raven.Noises.make(level, player.position(), type == GunType.PISTOL
                ? com.senne.imttech.config.ImtConfig.Guards.NOISE_LASER_PISTOL : com.senne.imttech.config.ImtConfig.Guards.NOISE_LASER_RIFLE,
                com.senne.imttech.raven.Noises.Kind.SHOT, player);
        wear(player, gun, parts, random);
        return Result.FIRED;
    }

    /** Slijtage (hoofdstuk 55): elk onderdeel een kans (van zijn materiaal, maal laserWearFactor) op een punt minder. */
    private static void wear(ServerPlayer player, ItemStack gun, LaserParts parts, RandomSource random) {
        LaserParts worn = parts;
        for (LaserPart part : LaserPart.wearing()) {
            PartState state = worn.get(part);
            if (state != null && random.nextFloat() * 100 < wearChance(state.material())) {
                worn = worn.with(part, state.worn1());
            }
        }
        if (worn != parts) {
            LaserGunItem.setParts(gun, worn);
            List<LaserPart> nowBroken = worn.broken();
            if (!nowBroken.isEmpty()) {
                tell(player, Component.translatable("weapon.imttech.gun.just_broke", nowBroken.getFirst().text()));
            }
        }
    }

    /**
     * Een schot van een wezen dat geen speler is (hoofdstuk 54: de Raven-soldaten): dezelfde stralen, treffers, koepels en
     * effecten als bij jou, maar zonder cel, zonder onderdelen en zonder de controle op je kijkrichting. {@code extraSpread}
     * (radialen) komt bij de spreiding van het geweer.
     */
    public static void fireFrom(ServerLevel level, LivingEntity shooter, GunType type, Vec3 from, Vec3 look, float damage,
                                double extraSpread) {
        RandomSource random = shooter.getRandom();
        double spread = Math.toRadians(type.spread(false)) + extraSpread;
        List<Vec3> ends = new ArrayList<>();
        int hits = 0;
        Map<LivingEntity, Float> damages = new LinkedHashMap<>();
        for (int ray = 0; ray < type.rays(); ray++) {
            Vec3 dir = scatter(look, spread, random);
            Vec3 end = from.add(dir.scale(type.range()));
            HitResult wall = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            if (wall.getType() != HitResult.Type.MISS) {
                end = wall.getLocation();
            }
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, shooter, from, end, new AABB(from, end).inflate(1.0),
                    entity -> entity.isAlive() && entity.isPickable() && !entity.isSpectator() && entity != shooter
                            && !(entity instanceof GrenadeEntity), 0.1f);
            LivingEntity target = null;
            if (hit != null) {
                Entity entity = hit.getEntity();
                end = entity.getBoundingBox().inflate(0.1).clip(from, end).orElse(entity.getBoundingBox().getCenter());
                target = entity instanceof LivingEntity living ? living : null;
            }
            Vec3 shield = ImtDomes.blockedAt(level, end, from, null, Math.round(ImtConfig.Dome.COST_LASER));
            if (shield != null) {
                end = shield;
                target = null;
            }
            if (target != null) {
                damages.merge(target, damage, Float::sum);
                hits |= 1 << ray;
            }
            ends.add(end);
        }
        for (Map.Entry<LivingEntity, Float> entry : damages.entrySet()) {
            entry.getKey().hurt(ModDamageTypes.laserGun(level, shooter), entry.getValue());
        }
        level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), type.fireSound(), SoundSource.HOSTILE,
                1.0f, 0.95f + random.nextFloat() * 0.1f);
        ModNetwork.sendNear(level, from, VISIBLE, new GunShotPayload(shooter.getId(), type.ordinal(), ends, hits));
    }

    /** Een richting een stukje willekeurig opzij (hooguit {@code spread} radialen), in een kegel rond {@code look}. */
    public static Vec3 scatter(Vec3 look, double spread, RandomSource random) {
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

    // ================================================================ Herladen, laden en lossen (zoals de Glock, hoofdstuk 57)

    /** Is deze speler met zijn cel bezig (wisselen, eruit halen, of er een in steken)? */
    public static boolean isReloading(ServerPlayer player) {
        return RELOADS.containsKey(player.getUUID()) || INSERTS.containsKey(player.getUUID());
    }

    /** R zonder vollere cel in je pouch, of U: de cel eruit (zie {@link #eject}). */
    public static boolean startReload(ServerPlayer player) {
        return startReload(player, EJECT);
    }

    /**
     * R met een magazijnpouch om (hoofdstuk 55: cellen passen in de magazijnpouch, zoals de magazijnen van de Glock): de
     * cel uit dit vakje ({@code pouch}) van de pouch erin; de oude cel komt op zijn plek. {@code pouch} = {@link #EJECT}:
     * alleen de cel eruit (hoofdstuk 57; vroeger kwam dan de volste cel uit je inventaris, dat is weg).
     */
    public static boolean startReload(ServerPlayer player, int pouch) {
        if (pouch < 0) {
            return eject(player);
        }
        ItemStack gun = player.getMainHandItem();
        GunType type = LaserGunItem.typeOf(gun);
        if (type == null || isReloading(player) || Firearms.isBusy(player)) {
            return false;
        }
        float current = LaserGunItem.cell(gun);
        List<ItemStack> contents = com.senne.imttech.backpack.Molle.magazines(player);
        if (pouch >= contents.size() || !contents.get(pouch).is(ModItems.ENERGY_CELL.get())
                || EnergyCellItem.charge(contents.get(pouch)) < Math.max(0.001f, current + 0.001f)) {
            tell(player, Component.translatable("gear.imttech.no_cell_there"));
            ModNetwork.sendTo(player, new GunReloadPayload(false, -1));
            return false;
        }
        begin(player, type, pouch, reloadTicks(player, gun));
        return true;
    }

    /**
     * De cel eruit (R zonder een vollere cel in je pouch, of U): na een deel van de herlaadtijd ({@code gunEjectPart})
     * komt hij in je magazijnpouch als daar plek is, anders in je inventaris. Zonder cel in het geweer: een melding.
     */
    public static boolean eject(ServerPlayer player) {
        ItemStack gun = player.getMainHandItem();
        GunType type = LaserGunItem.typeOf(gun);
        if (type == null || isReloading(player) || Firearms.isBusy(player)) {
            return false;
        }
        if (LaserGunItem.cell(gun) < 0) {
            tell(player, Component.translatable("weapon.imttech.gun.no_cell_hint"));
            ModNetwork.sendTo(player, new GunReloadPayload(false, -1));
            return false;
        }
        begin(player, type, EJECT, Math.max(1, Math.round(reloadTicks(player, gun) * ImtConfig.Weapons.GUN_EJECT_PART)));
        return true;
    }

    /**
     * Hoofdstuk 67: een lasergeweer op een Ammo Box met cellen. Net zo lang als herladen uit je pouch; daarna komt de
     * volste cel uit de doos in je geweer en je oude cel in de doos (armory/AmmoBoxes.finishCellSwap). Of er een
     * vollere cel in de doos ligt, keek AmmoBoxes al na.
     */
    public static boolean startReloadFromBox(ServerPlayer player) {
        ItemStack gun = player.getMainHandItem();
        GunType type = LaserGunItem.typeOf(gun);
        if (type == null || isReloading(player) || Firearms.isBusy(player)) {
            return false;
        }
        begin(player, type, FROM_BOX, reloadTicks(player, gun));
        return true;
    }

    /** Het wisselen of lossen begint: onthouden, het geluid van de cel eruit, en jouw spel hoort hoe lang het duurt. */
    private static void begin(ServerPlayer player, GunType type, int pouch, int ticks) {
        ServerLevel level = player.serverLevel();
        RELOADS.put(player.getUUID(), new Reload(player.getInventory().selected, type, pouch, level.getGameTime() + ticks));
        level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), ModSounds.LASER_GUN_CELL_OUT.get(),
                SoundSource.PLAYERS, 0.8f, 1.0f);
        ModNetwork.sendTo(player, new GunReloadPayload(true, ticks));
    }

    /**
     * Rechtsklik met een energiecel in je hand (hoofdstuk 57, zoals een magazijn in de Glock): in het lasergeweer in je
     * werkbalk dat geen cel heeft (dan de rest van je inventaris). Na een deel van de herlaadtijd ({@code gunInsertPart})
     * zit hij erin. Geeft false (met een melding) als er zo een geweer niet is.
     */
    public static boolean insertCell(ServerPlayer player, InteractionHand hand) {
        ItemStack cell = player.getItemInHand(hand);
        if (!cell.is(ModItems.ENERGY_CELL.get()) || hand != InteractionHand.MAIN_HAND || isReloading(player) || Firearms.isBusy(player)) {
            return false;
        }
        int gunSlot = gunWithoutCell(player.getInventory());
        if (gunSlot < 0) {
            tell(player, Component.translatable("weapon.imttech.gun.no_gun_for_cell"));
            return false;
        }
        ItemStack gun = player.getInventory().getItem(gunSlot);
        int ticks = Math.max(1, Math.round(reloadTicks(player, gun) * ImtConfig.Weapons.GUN_INSERT_PART));
        INSERTS.put(player.getUUID(), new Insert(player.getInventory().selected, gunSlot, player.serverLevel().getGameTime() + ticks));
        return true;
    }

    /** Het vakje met een lasergeweer zonder cel: eerst je werkbalk, dan de rest van je inventaris; of -1. */
    private static int gunWithoutCell(Inventory inventory) {
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (LaserGunItem.typeOf(stack) != null && LaserGunItem.cell(stack) < 0) {
                return slot;
            }
        }
        return -1;
    }

    /** Elke tick: wisselen, lossen en laden afmaken (of afbreken als je iets anders vastpakte). */
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Iterator<Map.Entry<UUID, Reload>> it = RELOADS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Reload> entry = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            Reload reload = entry.getValue();
            if (player == null) {
                it.remove();
                continue;
            }
            if (player.getInventory().selected != reload.slot() || LaserGunItem.typeOf(player.getMainHandItem()) != reload.type()) {
                it.remove();
                ModNetwork.sendTo(player, new GunReloadPayload(false, -1));
                continue;
            }
            if (player.serverLevel().getGameTime() >= reload.done()) {
                it.remove();
                finishReload(player, reload.pouch());
            }
        }
        for (Iterator<Map.Entry<UUID, Insert>> it = INSERTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Insert> entry = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            Insert insert = entry.getValue();
            if (player == null || !insertStillValid(player, insert)) {
                it.remove();
                continue;
            }
            if (player.serverLevel().getGameTime() >= insert.done()) {
                it.remove();
                finishInsert(player, insert);
            }
        }
    }

    /** Houd je de cel nog vast, en wacht het geweer nog op een cel? */
    private static boolean insertStillValid(ServerPlayer player, Insert insert) {
        Inventory inventory = player.getInventory();
        ItemStack gun = inventory.getItem(insert.gunSlot());
        return inventory.selected == insert.slot() && player.getMainHandItem().is(ModItems.ENERGY_CELL.get())
                && LaserGunItem.typeOf(gun) != null && LaserGunItem.cell(gun) < 0;
    }

    /**
     * Wisselen of lossen is klaar. Wisselen: de nieuwe cel uit de pouch gaat in het geweer en de oude komt in dat vakje.
     * Lossen: de cel gaat in je magazijnpouch als daar plek is, anders in je inventaris (of op de grond als die vol is).
     */
    private static void finishReload(ServerPlayer player, int pouch) {
        ItemStack gun = player.getMainHandItem();
        if (pouch == FROM_BOX) {
            com.senne.imttech.armory.AmmoBoxes.finishCellSwap(player, gun);
            return;
        }
        float old = LaserGunItem.cell(gun);
        if (pouch < 0) {
            if (old >= 0) {
                LaserGunItem.setCell(gun, -1);
                ItemStack cell = EnergyCellItem.withCharge(old);
                if (!Gear.storeMagazine(player, cell)) {
                    Firearms.give(player, cell);
                }
            }
            return;
        }
        List<ItemStack> contents = com.senne.imttech.backpack.Molle.magazines(player);
        if (pouch >= contents.size() || !contents.get(pouch).is(ModItems.ENERGY_CELL.get())) {
            ModNetwork.sendTo(player, new GunReloadPayload(false, -1));
            return;
        }
        float fresh = EnergyCellItem.charge(contents.get(pouch));
        contents.set(pouch, old >= 0 ? EnergyCellItem.withCharge(old) : ItemStack.EMPTY);
        com.senne.imttech.backpack.Molle.setMagazines(player, contents);
        LaserGunItem.setCell(gun, fresh);
        player.serverLevel().playSound(player, player.getX(), player.getEyeY(), player.getZ(),
                ModSounds.LASER_GUN_CELL_IN.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        GunSkillHooks.reloaded(player, gun);
    }

    /** De cel in je hand zit in het geweer: de klik (voor iedereen, jouw spel speelt hier niets zelf), en het telt als herladen. */
    private static void finishInsert(ServerPlayer player, Insert insert) {
        Inventory inventory = player.getInventory();
        ItemStack gun = inventory.getItem(insert.gunSlot());
        LaserGunItem.setCell(gun, EnergyCellItem.charge(player.getMainHandItem()));
        inventory.setItem(insert.slot(), ItemStack.EMPTY);
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                ModSounds.LASER_GUN_CELL_IN.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        GunSkillHooks.reloaded(player, gun);
    }

    // ================================================================ Opruimen

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        NEXT_SHOT.remove(event.getEntity().getUUID());
        RELOADS.remove(event.getEntity().getUUID());
        INSERTS.remove(event.getEntity().getUUID());
    }

    public static void clear() {
        NEXT_SHOT.clear();
        RELOADS.clear();
        INSERTS.clear();
    }

    /** Voor de tests: alsof er al lang niet geschoten is. */
    public static void forgetCooldown(Entity player) {
        NEXT_SHOT.remove(player.getUUID());
    }

    /** Voor de tests: het herladen (of lossen) meteen afmaken. */
    public static void finishReloadNow(ServerPlayer player) {
        Reload reload = RELOADS.remove(player.getUUID());
        if (reload != null) {
            finishReload(player, reload.pouch());
        }
    }

    /** Voor de tests: de cel in je hand meteen in het geweer. */
    public static void finishInsertNow(ServerPlayer player) {
        Insert insert = INSERTS.remove(player.getUUID());
        if (insert != null && insertStillValid(player, insert)) {
            finishInsert(player, insert);
        }
    }

    private static void tell(ServerPlayer player, Component text) {
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.RED), true);
    }
}
