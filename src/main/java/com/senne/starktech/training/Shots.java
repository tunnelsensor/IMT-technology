package com.senne.starktech.training;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Eén plek waar elk schot van je beveiliging terechtkomt (hoofdstuk 26): een torentje, een laserwand, een oude
 * patrouilledrone, een robot of een security drone. Is wie geraakt wordt de trainee van een training die bezig is,
 * dan beslist de training wat er gebeurt (zie Training.hit): met trainingsschoten geen schade (een blauwe flits en
 * een leven eraf), lethal wel schade, en het telt mee. Anders is het gewoon een schot.
 */
public final class Shots {
    private Shots() {}

    /**
     * Dit schot raakt dit wezen: {@code shooter} is wie schoot ("Security turret", "Guard 2"...). Geeft true als het
     * raakte (met trainingsschoten ook zonder schade).
     */
    public static boolean hit(ServerLevel level, LivingEntity target, DamageSource source, float damage, String shooter) {
        if (target instanceof ServerPlayer player) {
            Training.Session session = Training.of(player.getUUID());
            if (session != null && session.running()) {
                if (session.settings().mode() == Training.Mode.TRAINING_SHOTS) {
                    Training.hit(level, player, shooter);
                    return true;
                }
                boolean hurt = target.hurt(source, damage);
                if (hurt) {
                    Training.hit(level, player, shooter);
                }
                return hurt;
            }
        }
        return target.hurt(source, damage);
    }

    /**
     * Krijgt dit wezen nu trainingsschoten (de trainee van een training zonder echte schade)? Dan schieten torentjes
     * alleen hun laser, geen raketten.
     */
    public static boolean trainingShots(LivingEntity target) {
        if (!(target instanceof ServerPlayer player)) {
            return false;
        }
        Training.Session session = Training.of(player.getUUID());
        return session != null && session.running() && session.settings().mode() == Training.Mode.TRAINING_SHOTS;
    }
}
