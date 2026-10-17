package com.senne.imttech.turret;

import com.senne.imttech.drone.DroneEntity;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

/**
 * Op wie een torentje schiet.
 *
 * ROCKET-torentje: monsters (ook raiddrones en Hydra-soldaten, dat zijn allemaal "vijanden").
 * Spelers alleen als PvP aan staat op de server, en nooit de eigenaar of iemand uit zijn team.
 * Dieren, dorpelingen, je eigen huisdieren en I.M.T-drones laat hij met rust.
 *
 * HYDRA-torentje (in de bunker): spelers (niet in creative) en I.M.T-drones.
 *
 * BEVEILIGINGSTORENTJE (in de Bibie's Industries Tower): wat de beveiligingskern een indringer noemt.
 *
 * Staat een Bibie's Industries-torentje in het gebied van een beveiligingskern van ZIJN eigenaar, dan volgt
 * het de regels van die kern (de Access-app op de desktop): vertrouwde spelers niet, monsters en
 * andere spelers alleen als dat aan staat.
 *
 * We kijken naar de eigenaar via zijn UUID en naam, niet naar de speler zelf: een torentje
 * moet ook werken als de eigenaar niet online is.
 */
public final class TurretTargeting {
    private TurretTargeting() {}

    public static boolean isTarget(ServerLevel level, TurretKind kind, @Nullable UUID ownerId, String ownerName,
                                   LivingEntity entity, net.minecraft.core.BlockPos turretPos) {
        if (!entity.isAlive() || entity.isInvulnerable() || entity instanceof ArmorStand) {
            return false;
        }
        if (kind == TurretKind.SECURITY) {
            // Wie een indringer is, zegt de beveiligingskern van de toren.
            return com.senne.imttech.security.SecurityCores.isIntruder(level,
                    com.senne.imttech.security.SecurityCores.coreAt(level, turretPos), entity);
        }
        if (kind == TurretKind.HYDRA) {
            return entity instanceof DroneEntity || entity instanceof Player player && canHurt(player);
        }
        if (ownerId != null && entity instanceof OwnableEntity pet && ownerId.equals(pet.getOwnerUUID())) {
            return false;
        }
        var core = com.senne.imttech.security.SecurityCores.coreAt(level, turretPos);
        if (core != null && ownerId != null && ownerId.equals(core.ownerId())) {
            return com.senne.imttech.security.SecurityCores.isIntruder(level, core, entity);
        }
        if (entity instanceof DroneEntity) {
            return false;
        }
        if (entity instanceof Player player) {
            return canHurt(player) && level.getServer().isPvpAllowed()
                    && !player.getUUID().equals(ownerId) && !sameTeam(level, ownerName, player);
        }
        return entity instanceof Enemy;
    }

    /** Spelers in creative of die alleen toekijken, kun je niet raken. */
    private static boolean canHurt(Player player) {
        return !player.isCreative() && !player.isSpectator();
    }

    /** Zit deze speler in hetzelfde team (of een bevriend team) als de eigenaar? */
    private static boolean sameTeam(ServerLevel level, String ownerName, Player player) {
        if (ownerName.isEmpty()) {
            return false;
        }
        PlayerTeam ownerTeam = level.getScoreboard().getPlayersTeam(ownerName);
        return ownerTeam != null && ownerTeam.isAlliedTo(player.getTeam());
    }
}
