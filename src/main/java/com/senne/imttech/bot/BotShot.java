package com.senne.imttech.bot;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.BeamEffectPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.registry.ModDamageTypes;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.shield.ImtDomes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Eén schot van een guard robot (hoofdstuk 24): met de Laser-module (kost batterij) of de Gun-module (kost één
 * Robot Ammo). Wie BotPins aanwijst (wie de robot ziet, door het filter van zijn Face Recognition) wordt het
 * doelwit; zonder doelwit schiet hij recht vooruit en raakt hij wat daar staat, en met {@code aim(x, y, z)} (zie
 * {@link #fireAt}) richting die plek.
 * <ul>
 *   <li>Een muur houdt het schot tegen, en een schildkoepel ook (zoals bij de Raven-soldaat; de koepels van zijn
 *       eigenaar niet).</li>
 *   <li>Spelers raakt hij alleen als PvP aan staat, en niet in creative of als toeschouwer (net als torentjes). Een bot
 *       van niemand (Biebia's drones in een Biebia's Industries Tower) raakt ze ook zonder PvP: dan schiet de toren, geen speler.</li>
 *   <li>De laser komt uit zijn rechterarm, het geweer uit zijn linker (daar zitten ze op het model).</li>
 * </ul>
 */
public final class BotShot {
    private BotShot() {}

    public enum Weapon { LASER, GUN }

    /** Schiet. Geeft het wezen dat geraakt (en verwond) werd, of null. */
    public static @Nullable LivingEntity fire(Bot bot, Weapon weapon, @Nullable LivingEntity target) {
        if (!(bot.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 forward = Vec3.directionFromRotation(0, bot.getYRot());
        Vec3 from = bot.muzzle(weapon == Weapon.LASER, forward);
        if (target != null && target.isAlive() && target.getBoundingBox().getCenter().distanceTo(from) <= ImtConfig.Robots.SHOT_RANGE) {
            return shoot(level, bot, weapon, from, target.getBoundingBox().getCenter(), target);
        }
        // Niemand in zicht: recht vooruit, en raak wat daar staat.
        return along(level, bot, weapon, from, forward);
    }

    /**
     * {@code aim(x, y, z)}: schiet richting deze plek en raak wat er op die lijn staat. Het schot gaat zo ver als een wapen
     * schiet (ook voorbij de plek), tot een muur of iets levends het tegenhoudt. Geeft het wezen dat geraakt werd, of null.
     */
    public static @Nullable LivingEntity fireAt(Bot bot, Weapon weapon, Vec3 place) {
        if (!(bot.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 flat = new Vec3(place.x - bot.position().x, 0, place.z - bot.position().z);
        Vec3 side = flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, bot.getYRot()) : flat.normalize();
        Vec3 from = bot.muzzle(weapon == Weapon.LASER, side);
        Vec3 way = place.subtract(from);
        return along(level, bot, weapon, from, way.lengthSqr() < 1.0e-4 ? side : way.normalize());
    }

    /** Schiet vanaf {@code from} in deze richting, en raak het eerste levende wezen op die lijn. */
    private static @Nullable LivingEntity along(ServerLevel level, Bot bot, Weapon weapon, Vec3 from, Vec3 way) {
        Vec3 to = from.add(way.scale(ImtConfig.Robots.SHOT_RANGE));
        EntityHitResult first = ProjectileUtil.getEntityHitResult(level, bot.entity(), from, to, new AABB(from, to).inflate(1.0),
                entity -> entity instanceof LivingEntity living && living.isAlive() && entity != bot.entity(), 0.3f);
        if (first == null) {
            return shoot(level, bot, weapon, from, to, null);
        }
        // Waar de straal hem raakt (Minecraft geeft hier zijn voeten, en dan loopt het schot door de vloer).
        Entity entity = first.getEntity();
        Vec3 end = entity.getBoundingBox().inflate(0.3).clip(from, to).orElse(entity.getBoundingBox().getCenter());
        return shoot(level, bot, weapon, from, end, (LivingEntity) entity);
    }

    /** Het schot zelf, van {@code from} naar {@code to}: wat ertussen staat, het geluid, de straal, en de schade aan {@code hit}. */
    private static @Nullable LivingEntity shoot(ServerLevel level, Bot bot, Weapon weapon, Vec3 from, Vec3 to, @Nullable LivingEntity hit) {
        boolean laser = weapon == Weapon.LASER;
        // Een muur ertussen: daar houdt het op.
        HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bot.entity()));
        if (wall.getType() != HitResult.Type.MISS) {
            to = wall.getLocation();
            hit = null;
        }
        // Een schildkoepel ertussen: die vangt het op (dat kost hem schildkracht).
        Vec3 shield = ImtDomes.blockedAt(level, to, from, bot.owner(), Math.round(ImtConfig.Dome.COST_LASER));
        if (shield != null) {
            to = shield;
            hit = null;
        }
        bot.playSound(laser ? ModSounds.ROBOT_LASER.get() : ModSounds.ROBOT_GUN.get(), 1.0f, 0.95f + bot.getRandom().nextFloat() * 0.1f);
        ModNetwork.sendNear(level, bot.position(), 64, new BeamEffectPayload(from, to,
                laser ? BeamEffectPayload.Kind.ROBOT_LASER : BeamEffectPayload.Kind.ROBOT_BULLET));
        if (hit == null || !mayHurt(level, bot, hit)) {
            return null;
        }
        float damage = laser ? ImtConfig.Robots.LASER_DAMAGE : ImtConfig.Robots.GUN_DAMAGE;
        // Via Shots (hoofdstuk 26): de trainee van een training krijgt misschien geen schade, maar het telt wel.
        return com.senne.imttech.training.Shots.hit(level, hit, ModDamageTypes.robotShot(level, bot.entity(), laser), damage,
                bot.getName().getString()) ? hit : null;
    }

    /**
     * Spelers alleen met PvP aan (of als de bot van niemand is, of als de speler de trainee van een training is: die
     * koos er zelf voor, hoofdstuk 26), en niet in creative of als toeschouwer; de rest altijd.
     */
    private static boolean mayHurt(ServerLevel level, Bot bot, Entity entity) {
        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator()
                    && (bot.owner() == null || level.getServer().isPvpAllowed() || com.senne.imttech.training.Training.isTrainee(player));
        }
        return true;
    }

}
