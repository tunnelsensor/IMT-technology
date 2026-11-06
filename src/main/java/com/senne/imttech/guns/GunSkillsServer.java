package com.senne.imttech.guns;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModDamageTypes;
import com.senne.imttech.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * De server-kant van de skills (hoofdstuk 52, stap 3): ervaring geven voor alles wat je met een pistool doet (jouw
 * keuze: "skillpoints krijg je door alles wat met een gun te maken heeft te doen"), en punten uitgeven op een skill.
 */
public final class GunSkillsServer {
    private GunSkillsServer() {}

    public static GunSkills of(ServerPlayer player) {
        return player.getData(ModAttachments.GUN_SKILLS);
    }

    /** Ervaring erbij; bij een nieuw punt een melding en een geluidje. */
    public static void award(ServerPlayer player, float amount) {
        int xp = Math.round(amount);
        if (xp <= 0 || player.isSpectator()) {
            return;
        }
        GunSkills before = of(player);
        GunSkills after = before.withXp(xp);
        player.setData(ModAttachments.GUN_SKILLS, after);
        if (after.points() > before.points()) {
            player.displayClientMessage(Component.translatable("skill.imttech.point_earned", after.points())
                    .withStyle(ChatFormatting.GOLD), true);
            player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.IMT_CONFIRM.get(),
                    SoundSource.PLAYERS, 0.6f, 1.3f);
        }
    }

    /** Een punt (of meer) uitgeven op deze skill, globaal of voor dit pistool. Geeft true als het lukte. */
    public static boolean learn(ServerPlayer player, String scope, GunSkill skill) {
        boolean valid = GunSkills.GLOBAL.equals(scope);
        for (FirearmType type : FirearmType.values()) {
            valid |= type.id.equals(scope);
        }
        if (!valid) {
            return false;
        }
        GunSkills before = of(player);
        GunSkills after = before.learn(scope, skill);
        if (after == before) {
            player.displayClientMessage(Component.translatable("skill.imttech.cannot_learn").withStyle(ChatFormatting.RED), true);
            return false;
        }
        player.setData(ModAttachments.GUN_SKILLS, after);
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.IMT_SELECT.get(),
                SoundSource.PLAYERS, 0.7f, 1.0f);
        return true;
    }

    /** Iemand doodgeschoten met een pistool: ervaring voor de schutter. */
    public static void onDeath(LivingDeathEvent event) {
        Entity killer = event.getSource().getEntity();
        if (event.getSource().is(ModDamageTypes.BULLET) && killer instanceof ServerPlayer player && event.getEntity() != player) {
            award(player, ImtConfig.Guns.XP_KILL);
        }
    }

    /** Een kogel raakte iets: ervaring voor de schutter. */
    public static void hit(Entity shooter) {
        if (shooter instanceof ServerPlayer player) {
            award(player, ImtConfig.Guns.XP_HIT);
        }
    }
}
