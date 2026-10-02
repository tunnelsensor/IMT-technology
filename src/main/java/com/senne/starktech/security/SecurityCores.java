package com.senne.starktech.security;

import com.senne.starktech.drone.StarkDroneEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

/**
 * Welke beveiligingskernen er geladen zijn, en wie ze als vriend zien.
 *
 * Een kern beschermt de toren om zich heen (zie SecurityBalance.AREA_...). Torentjes,
 * beveiligingsdrones en laserwanden in dat gebied vragen hier: "is dit een indringer?"
 *
 * - Heeft de kern nog GEEN eigenaar (niemand heeft hem gehackt), dan is elke speler een
 *   indringer (behalve in creative), net als monsters.
 * - Heeft hij WEL een eigenaar, dan zijn de eigenaar en zijn team vrienden. Andere spelers zijn
 *   alleen een indringer als PvP aan staat; monsters blijven altijd een doelwit.
 */
public final class SecurityCores {
    private SecurityCores() {}

    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

    static void loaded(Level level, BlockPos pos) {
        LOADED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
    }

    static void unloaded(Level level, BlockPos pos) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    /** De kern die deze plek beschermt, of null als hij buiten elke toren ligt. */
    public static @Nullable SecurityCoreBlockEntity coreAt(Level level, BlockPos pos) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set == null) {
            return null;
        }
        for (BlockPos corePos : List.copyOf(set)) {
            if (inArea(corePos, pos) && level.getBlockEntity(corePos) instanceof SecurityCoreBlockEntity core) {
                return core;
            }
        }
        return null;
    }

    /** Ligt deze plek in het gebied van de kern op {@code corePos}? */
    public static boolean inArea(BlockPos corePos, BlockPos pos) {
        return Math.abs(pos.getX() - corePos.getX()) <= SecurityBalance.AREA_RADIUS
                && Math.abs(pos.getZ() - corePos.getZ()) <= SecurityBalance.AREA_RADIUS
                && pos.getY() >= corePos.getY() - SecurityBalance.AREA_DOWN
                && pos.getY() <= corePos.getY() + SecurityBalance.AREA_UP;
    }

    /**
     * Moet de beveiliging hierop schieten? {@code core} mag null zijn (een torentje zonder kern
     * gedraagt zich als een kern zonder eigenaar).
     */
    public static boolean isIntruder(ServerLevel level, @Nullable SecurityCoreBlockEntity core, LivingEntity entity) {
        if (!entity.isAlive() || entity.isInvulnerable() || entity instanceof SecurityDroneEntity) {
            return false;
        }
        boolean unclaimed = core == null || core.ownerId() == null;
        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator() || isFriend(level, core, player)) {
                return false;
            }
            // Van niemand: iedereen is een indringer. Van jou: andere spelers alleen als de
            // eigenaar dat op de desktop heeft aangezet, of als ze net een valstrik openden (en
            // dan nog alleen met PvP aan).
            return unclaimed || (core.attackPlayers() || core.isHunted(player.getUUID()))
                    && level.getServer().isPvpAllowed();
        }
        if (entity instanceof StarkDroneEntity drone) {
            ServerPlayer owner = drone.getOwnerId() == null ? null
                    : level.getServer().getPlayerList().getPlayer(drone.getOwnerId());
            return owner != null && isIntruder(level, core, owner);
        }
        // Monsters: van niemand altijd, van jou alleen als "attack mobs" aan staat (standaard aan).
        return entity instanceof Enemy && (unclaimed || core.attackMobs());
    }

    /** Is deze speler de eigenaar van de kern, zit hij in zijn team, of is hij vertrouwd? */
    public static boolean isFriend(ServerLevel level, @Nullable SecurityCoreBlockEntity core, Player player) {
        if (core == null || core.ownerId() == null) {
            return false;
        }
        if (core.ownerId().equals(player.getUUID()) || core.isTrusted(player.getUUID())) {
            return true;
        }
        PlayerTeam ownerTeam = level.getScoreboard().getPlayersTeam(core.ownerName());
        return ownerTeam != null && ownerTeam.isAlliedTo(player.getTeam());
    }

    /** Bij het afsluiten van de server: alles vergeten. */
    public static void clear() {
        LOADED.clear();
    }
}
