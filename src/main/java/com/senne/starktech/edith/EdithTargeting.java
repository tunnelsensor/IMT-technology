package com.senne.starktech.edith;

import com.senne.starktech.drone.StarkDroneEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Regels voor wie E.D.I.T.H. mag aanvallen.
 *
 * - Nooit jezelf, je eigen drones of je eigen tamme dieren.
 * - Nooit teamgenoten.
 * - Spelers alleen als PvP op de server aan staat (en nooit in creative/spectator).
 */
public final class EdithTargeting {
    private EdithTargeting() {}

    /** Mag de eigenaar dit wezen als doelwit kiezen (markeren, drones, satelliet)? */
    public static boolean canTarget(ServerPlayer owner, LivingEntity target) {
        if (!target.isAlive() || target == owner || target instanceof StarkDroneEntity) {
            return false;
        }
        if (target instanceof Player player) {
            // canHarmPlayer kijkt naar de PvP-instelling van de server én naar teams.
            return !player.isCreative() && !player.isSpectator() && owner.canHarmPlayer(player);
        }
        if (target instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID())) {
            return false; // je eigen hond, kat, papegaai...
        }
        return !owner.isAlliedTo(target);
    }

    /**
     * Moeten beschermende drones dit wezen uit zichzelf aanvallen? Alleen vijandige
     * monsters, of wie de eigenaar net pijn heeft gedaan.
     */
    public static boolean isThreatTo(ServerPlayer owner, LivingEntity entity) {
        if (!canTarget(owner, entity)) {
            return false;
        }
        if (entity instanceof Enemy) {
            return true;
        }
        return owner.getLastHurtByMob() == entity && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200;
    }
}
