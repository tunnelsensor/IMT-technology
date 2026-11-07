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

    /**
     * Iemand die aan het herladen is: in welk vakje zijn geweer zit, uit welk vakje van zijn magazijnpouch de cel komt
     * ({@code pouch}, of -1: de volste cel uit zijn inventaris), en wanneer het klaar is.
     */
    private record Reload(int slot, GunType type, int pouch, long done) {}

    private static final Map<UUID, Reload> RELOADS = new HashMap<>();

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
        double spread = Math.toRadians(type.spread(aiming) * (1 + parts.penalty(LaserPart.OPTICS))
                * GunSkillsServer.of(player).factor(GunSkill.AIM, type.id));
        // De emitter: netherite doet iets meer, versleten minder (en reikt minder ver).
        float shotDamage = type.damage() * relative(parts.materialOf(LaserPart.EMITTER).damageFactor(), GunMaterial.TITANIUM.damageFactor())
                * (1 - parts.penalty(LaserPart.EMITTER));
        double range = type.range() * (1 - 0.5 * parts.penalty(LaserPart.EMITTER));
        List<Vec3> ends = new ArrayList<>();
        int hits = 0;
        Map<LivingEntity, Float> damage = new LinkedHashMap<>();
        for (int ray = 0; ray < type.rays(); ray++) {
            Vec3 dir = scatter(look, spread, random);
            Vec3 end = eye.add(dir.scale(range));
            HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (wall.getType() != HitResult.Type.MISS) {
                end = wall.getLocation();
            }
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, new AABB(eye, end).inflate(1.0),
                    entity -> entity.isAlive() && entity.isPickable() && !entity.isSpectator() && entity != player
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
            target.hurt(ModDamageTypes.laserGun(level, player), entry.getValue());
            GunSkillsServer.hit(player);
        }
        GunSkillHooks.fired(player);
        // Het geluid voor de anderen (jouw spel speelt het zelf al, meteen bij je klik).
        level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), type.fireSound(), SoundSource.PLAYERS,
                1.0f, 0.95f + random.nextFloat() * 0.1f);
        ModNetwork.sendNear(level, eye, VISIBLE, new GunShotPayload(player.getId(), type.ordinal(), ends, hits));
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
                    entity -> entity.isAlive() && entity.isPickable() && !entity.isSpectator() && entity != shooter, 0.1f);
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

    // ================================================================ Herladen

    /** Is deze speler aan het herladen? */
    public static boolean isReloading(ServerPlayer player) {
        return RELOADS.containsKey(player.getUUID());
    }

    /**
     * R: begin met herladen met de volste cel uit je inventaris. Dat kan als je een geweer vasthoudt en er in je
     * inventaris een cel is die voller is dan die in het geweer. Geeft false (en zegt waarom) als het niet kan.
     */
    public static boolean startReload(ServerPlayer player) {
        return startReload(player, -1);
    }

    /**
     * R met een magazijnpouch om (hoofdstuk 55: cellen passen in de magazijnpouch, zoals de magazijnen van de Glock): de
     * cel uit dit vakje ({@code pouch}) van de pouch erin; de oude cel komt op zijn plek. {@code pouch} = -1: de volste
     * cel uit je inventaris, zoals altijd.
     */
    public static boolean startReload(ServerPlayer player, int pouch) {
        ItemStack gun = player.getMainHandItem();
        GunType type = LaserGunItem.typeOf(gun);
        if (type == null || RELOADS.containsKey(player.getUUID()) || Firearms.isBusy(player)) {
            return false;
        }
        float current = LaserGunItem.cell(gun);
        if (pouch >= 0) {
            List<ItemStack> contents = Gear.contents(player, GearItem.Kind.MAG_POUCH);
            if (pouch >= contents.size() || !contents.get(pouch).is(ModItems.ENERGY_CELL.get())
                    || EnergyCellItem.charge(contents.get(pouch)) < Math.max(0.001f, current + 0.001f)) {
                player.displayClientMessage(Component.translatable("gear.imttech.no_cell_there").withStyle(ChatFormatting.RED), true);
                ModNetwork.sendTo(player, new GunReloadPayload(false, -1));
                return false;
            }
        } else if (bestCell(player.getInventory(), current) < 0) {
            player.displayClientMessage(Component.translatable("weapon.imttech.gun.no_cell").withStyle(ChatFormatting.RED), true);
            ModNetwork.sendTo(player, new GunReloadPayload(false, -1));
            return false;
        }
        ServerLevel level = player.serverLevel();
        int ticks = reloadTicks(player, gun);
        RELOADS.put(player.getUUID(), new Reload(player.getInventory().selected, type, pouch, level.getGameTime() + ticks));
        level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), ModSounds.LASER_GUN_CELL_OUT.get(),
                SoundSource.PLAYERS, 0.8f, 1.0f);
        ModNetwork.sendTo(player, new GunReloadPayload(true, ticks));
        return true;
    }

    /**
     * Het vakje met de volste cel die voller is dan {@code current} (de cel in het geweer, -1 = geen), of -1 als die er
     * niet is. Een lege cel telt nooit.
     */
    private static int bestCell(Inventory inventory, float current) {
        int best = -1;
        float most = Math.max(0.001f, current + 0.001f);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.ENERGY_CELL.get()) && EnergyCellItem.charge(stack) >= most) {
                most = EnergyCellItem.charge(stack);
                best = slot;
            }
        }
        return best;
    }

    /** Elke tick: herladen afmaken (of afbreken als je iets anders vastpakte). */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (RELOADS.isEmpty()) {
            return;
        }
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
    }

    /**
     * De cellen wisselen: de nieuwe cel gaat in het geweer, de oude komt op zijn plek (in je inventaris, of in het vakje
     * van de pouch waar de nieuwe uit kwam).
     */
    private static void finishReload(ServerPlayer player, int pouch) {
        ItemStack gun = player.getMainHandItem();
        float old = LaserGunItem.cell(gun);
        if (pouch >= 0) {
            List<ItemStack> contents = Gear.contents(player, GearItem.Kind.MAG_POUCH);
            if (pouch >= contents.size() || !contents.get(pouch).is(ModItems.ENERGY_CELL.get())) {
                ModNetwork.sendTo(player, new GunReloadPayload(false, -1));
                return;
            }
            float fresh = EnergyCellItem.charge(contents.get(pouch));
            contents.set(pouch, old >= 0 ? EnergyCellItem.withCharge(old) : ItemStack.EMPTY);
            Gear.setContents(player, GearItem.Kind.MAG_POUCH, contents);
            LaserGunItem.setCell(gun, fresh);
        } else {
            Inventory inventory = player.getInventory();
            int slot = bestCell(inventory, old);
            if (slot < 0) {
                ModNetwork.sendTo(player, new GunReloadPayload(false, -1));
                return;
            }
            float fresh = EnergyCellItem.charge(inventory.getItem(slot));
            inventory.setItem(slot, old >= 0 ? EnergyCellItem.withCharge(old) : ItemStack.EMPTY);
            LaserGunItem.setCell(gun, fresh);
        }
        player.serverLevel().playSound(player, player.getX(), player.getEyeY(), player.getZ(),
                ModSounds.LASER_GUN_CELL_IN.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        GunSkillHooks.reloaded(player, gun);
    }

    // ================================================================ Opruimen

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        NEXT_SHOT.remove(event.getEntity().getUUID());
        RELOADS.remove(event.getEntity().getUUID());
    }

    public static void clear() {
        NEXT_SHOT.clear();
        RELOADS.clear();
    }

    /** Voor de tests: alsof er al lang niet geschoten is. */
    public static void forgetCooldown(Entity player) {
        NEXT_SHOT.remove(player.getUUID());
    }

    /** Voor de tests: het herladen meteen afmaken. */
    public static void finishReloadNow(ServerPlayer player) {
        Reload reload = RELOADS.remove(player.getUUID());
        if (reload != null) {
            finishReload(player, reload.pouch());
        }
    }

    private static void tell(ServerPlayer player, Component text) {
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.RED), true);
    }
}
