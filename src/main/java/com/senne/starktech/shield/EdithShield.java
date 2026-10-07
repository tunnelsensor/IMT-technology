package com.senne.starktech.shield;

import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithFeature;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.EdithMessagePayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Het rayshield: energieschilden, in drie soorten.
 *
 * 1. PERSOONLIJK SCHILD (functie SHIELD, aan/uit in het G-menu): een veld rond jou dat
 *    pijlen, vuurballen, explosies en drone-lasers tegenhoudt. Elke treffer kost energie;
 *    is je batterij leeg, dan valt het schild weg en voel je de klap wel.
 * 2. LASERPOORT: twee laserzenders tegenover elkaar maken een rode wand (zie LaserEmitterBlock).
 * 3. KOEPEL: een koepelgenerator maakt een koepel om je basis (zie DomeGeneratorBlock en
 *    EdithDomes).
 */
public final class EdithShield {
    private EdithShield() {}

    // ================================================================ 1. Persoonlijk schild

    /**
     * Wordt aangeroepen vlak voordat iemand schade krijgt. Draagt de speler een werkende bril
     * met het schild aan? Dan houdt het schild de klap tegen zolang er energie is.
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack glasses = EdithData.getWornGlasses(player);
        if (glasses.isEmpty() || !EdithData.isAuthorized(glasses, player)
                || !EdithData.isEnabled(glasses, EdithFeature.SHIELD)) {
            return;
        }
        DamageSource source = event.getSource();
        // Sommige dingen kan een schild niet tegenhouden (verdrinken, honger, de leegte...).
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }
        float damage = event.getAmount();
        int cost = (int) Math.ceil(damage * EdithBalance.SHIELD_COST_PER_DAMAGE);
        if (!EdithData.tryUseEnergy(glasses, cost)) {
            EdithServer.send(player, Component.translatable("edith.starktech.msg.shield_down"),
                    EdithMessagePayload.Level.ALERT);
            return; // geen energie meer: de klap komt door
        }
        event.setCanceled(true);
        flash(player.serverLevel(), player.position().add(0, player.getBbHeight() / 2, 0));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                com.senne.starktech.registry.ModSounds.RAYSHIELD_HIT.get(), SoundSource.PLAYERS, 0.8f, 1.15f);
    }

    /** Een flitsje van blauwe deeltjes rond de speler: het schild vangt de klap op. */
    private static void flash(ServerLevel level, Vec3 center) {
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 14, 0.5, 0.6, 0.5, 0.05);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 8, 0.6, 0.7, 0.6, 0.2);
    }
}
