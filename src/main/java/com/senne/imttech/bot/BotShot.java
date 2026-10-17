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
 * doelwit; ziet hij niemand, dan schiet hij recht vooruit en raakt hij wat daar staat.
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
        boolean laser = weapon == Weapon.LASER;
        Vec3 from = bot.muzzle(laser, Vec3.directionFromRotation(0, bot.getYRot()));
        double range = ImtConfig.Robots.SHOT_RANGE;
        LivingEntity hit = null;
        Vec3 to;
        if (target != null && target.isAlive() && target.getBoundingBox().getCenter().distanceTo(from) <= range) {
            hit = target;
            to = target.getBoundingBox().getCenter();
        } else {
            // Niemand in zicht: recht vooruit, en raak wat daar staat.
            to = from.add(Vec3.directionFromRotation(0, bot.getYRot()).scale(range));
            EntityHitResult first = ProjectileUtil.getEntityHitResult(level, bot.entity(), from, to, new AABB(from, to).inflate(1.0),
                    entity -> entity instanceof LivingEntity living && living.isAlive() && entity != bot.entity(), 0.3f);
            if (first != null) {
                hit = (LivingEntity) first.getEntity();
                to = first.getLocation();
            }
        }
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
