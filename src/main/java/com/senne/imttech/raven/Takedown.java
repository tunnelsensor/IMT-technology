package com.senne.imttech.raven;

import com.senne.imttech.config.ImtConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Een sluipaanval met een zwaard (8 november, jouw wens: "zorg dat je met een zwaard een guard kunt neersteken langs
 * achter zodat je die stealth kunt killen"), zoals in Hitman.
 *
 * Sla je een Raven-soldaat met een <b>zwaard</b> terwijl je <b>achter hem</b> staat (in de zone achter zijn rug,
 * {@code [guards] takedownAngle}, gemeten vanaf waar zijn hoofd heen kijkt) en hij nog niet met iemand vecht of via de
 * radio roept, dan is hij in één keer neer. Dat is stil: de andere bewakers horen niets (er gaat geen lawaai naar
 * {@link Noises}), je hoort alleen een zachte steek. Zijn lichaam blijft liggen zoals altijd, met zijn buit erin:
 * verstop het, anders roept wie het ziet "Man down!".
 *
 * Kijkt hij naar je, of heeft hij al een doelwit, dan is het een gewone slag (en hij heeft je meteen). Op je scherm
 * staat "Takedown" onder je vizier zodra het kan (zie GuardLayer); dat werkt ook zonder bril.
 */
public final class Takedown {
    private Takedown() {}

    /** Een zwaard (ook een zwaard uit een andere mod, als die het in de tag {@code minecraft:swords} zet). */
    public static boolean isBlade(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.getItem() instanceof SwordItem;
    }

    /**
     * Staat {@code attacker} in de zone achter de rug van {@code guard}? Recht achter hem is 180 graden van waar hij
     * heen kijkt; met de standaardzone van 180 graden telt alles achter zijn schouders.
     */
    public static boolean behind(Entity attacker, LivingEntity guard) {
        double zone = ImtConfig.Guards.TAKEDOWN_ANGLE;
        if (zone <= 0) {
            return false;
        }
        Vec3 facing = Vec3.directionFromRotation(0, guard.getYHeadRot());
        double angle = RavenSenses.flatAngle(facing, attacker.position().subtract(guard.position()));
        return angle >= 180.0 - zone / 2.0;
    }

    /**
     * Kan {@code player} deze soldaat nu neersteken? {@code state} is wat de soldaat doet: op de server zijn echte toestand,
     * in je spel wat de server meestuurt (zo klopt de hint onder je vizier met wat er gebeurt).
     */
    public static boolean possible(Player player, RavenSoldierEntity guard, RavenAlert state) {
        return guard.isAlive() && state != RavenAlert.COMBAT && state != RavenAlert.CALLING
                && isBlade(player.getMainHandItem()) && behind(player, guard);
    }

    /** Een speler slaat een wezen: is het een soldaat die hem niet ziet, en heeft hij een zwaard? Dan is het een sluipaanval. */
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!(event.getTarget() instanceof RavenSoldierEntity guard) || !(player.level() instanceof ServerLevel level)
                || player.isSpectator()) {
            return;
        }
        if (guard.getTarget() != null || !possible(player, guard, guard.senses().state())) {
            return;
        }
        event.setCanceled(true); // geen gewone slag (en dus ook geen zwaai die een tweede soldaat raakt)
        takeDown(level, player, guard);
    }

    /** Hij is neer: een zachte steek, wat vonkjes, en het zwaard slijt zoals bij een gewone slag. */
    static void takeDown(ServerLevel level, Player player, RavenSoldierEntity guard) {
        Vec3 back = guard.getEyePosition().add(Vec3.directionFromRotation(0, guard.getYHeadRot()).scale(-0.3)).add(0, -0.4, 0);
        level.sendParticles(ParticleTypes.CRIT, back.x, back.y, back.z, 8, 0.15, 0.2, 0.15, 0.15);
        level.playSound(null, guard.getX(), guard.getEyeY(), guard.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.45f, 0.7f);
        guard.silenceDeath();
        guard.hurt(level.damageSources().playerAttack(player), Math.max(1000.0f, guard.getMaxHealth() * 50.0f));
        player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        player.resetAttackStrengthTicker();
    }
}
