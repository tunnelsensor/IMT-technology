package com.senne.starktech.security;

import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.security.zone.ZoneBox;
import com.senne.starktech.security.zone.ZoneData;
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
 * Een kern beschermt alles om zich heen tot SecurityBalance.ZONE_REACH blokken ver: zijn gebied.
 * Torentjes, beveiligingsdrones en laserwanden in dat gebied vragen hier: "is dit een indringer?"
 * En camera's, keycard-lezers, codepanelen en serverkasten vragen hier bij welke kern ze horen.
 *
 * - Heeft de kern nog GEEN eigenaar (niemand heeft hem gehackt), dan is elke speler een
 *   indringer (behalve in creative), net als monsters.
 * - Heeft hij WEL een eigenaar, dan zijn de eigenaar, zijn team en de spelers die hij vertrouwt
 *   (de Access-app op de desktop) vrienden. Andere spelers zijn alleen een indringer als de
 *   eigenaar dat aanzette (en PvP aan staat), monsters alleen als "attack mobs" aan staat.
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
        // Liggen er (bij uitzondering) twee gebieden over deze plek, dan telt de dichtstbijzijnde kern;
        // zo is het elke keer dezelfde (niet wie toevallig eerst in de lijst staat).
        SecurityCoreBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos corePos : List.copyOf(set)) {
            if (inArea(corePos, pos) && level.getBlockEntity(corePos) instanceof SecurityCoreBlockEntity core) {
                double distance = corePos.distSqr(pos);
                if (distance < bestDistance) {
                    best = core;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /**
     * Ligt deze plek in het gebied van de kern op {@code corePos}: tot ZONE_REACH blokken ver, in elke
     * richting? Dat getal staat in de config; verandert het, dan geldt het meteen.
     */
    public static boolean inArea(BlockPos corePos, BlockPos pos) {
        int reach = SecurityBalance.ZONE_REACH;
        return Math.abs(pos.getX() - corePos.getX()) <= reach
                && Math.abs(pos.getY() - corePos.getY()) <= reach
                && Math.abs(pos.getZ() - corePos.getZ()) <= reach;
    }

    /** Het gebied van de kern op {@code corePos} als één kubus (dezelfde blokken als {@link #inArea}). */
    public static ZoneBox areaOf(BlockPos corePos) {
        int reach = SecurityBalance.ZONE_REACH;
        return ZoneBox.of(corePos.offset(-reach, -reach, -reach), corePos.offset(reach, reach, reach));
    }

    /**
     * Hoort het apparaat op {@code pos} (een camera, lezer, serverkast...) bij deze kern? Het ligt in
     * zijn gebied, en geen andere kern staat dichterbij (net als bij {@link #coreAt}). Normaal liggen
     * twee gebieden nooit over elkaar, dan is het hetzelfde als {@link #inArea}; alleen in de tests
     * staan kernen dichter bij elkaar. (Niet gewoon {@code coreAt(...) == core}: een kern die net is
     * neergezet, meldt zich pas een tick later aan.)
     */
    public static boolean ownedBy(Level level, SecurityCoreBlockEntity core, BlockPos pos) {
        BlockPos own = core.getBlockPos();
        if (!inArea(own, pos)) {
            return false;
        }
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set == null) {
            return true;
        }
        double distance = own.distSqr(pos);
        for (BlockPos other : List.copyOf(set)) {
            if (!other.equals(own) && other.distSqr(pos) < distance && inArea(other, pos)
                    && level.getBlockEntity(other) instanceof SecurityCoreBlockEntity) {
                return false;
            }
        }
        return true;
    }

    /**
     * Zit deze kubus voor de zone van de kern op {@code corePos} een ander in de weg? Een zone mag tot
     * ZONE_REACH blokken ver (het hele gebied van de kern), maar niet over de zone van een andere kern
     * (zones liggen nooit over elkaar) en niet in het gebied van een andere kern (anders trok je je
     * zone over de basis van een ander, en schoten zijn torentjes in jouw zone).
     *
     * Normaal kan dat geen van beide: twee gebieden liggen nooit over elkaar (zie {@link #overlaps}),
     * en een zone blijft in zijn eigen gebied. Alleen kernen die te dicht bij elkaar kwamen (met
     * commando's, of in de tests) hebben dit nodig; in het eigen gebied mag het dan altijd.
     */
    public static boolean zoneBlocked(ServerLevel level, BlockPos corePos, ZoneBox box) {
        for (ZoneData.Zone zone : ZoneData.get(level).in(level.dimension())) {
            if (!zone.core().pos().equals(corePos) && zone.touches(box)) {
                return true;
            }
        }
        ZoneBox own = areaOf(corePos);
        for (BlockPos other : otherCores(level, corePos)) {
            ZoneBox shared = box.overlap(areaOf(other));
            if (shared != null && !own.contains(shared)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Alle andere kernen in deze dimensie: uit het netwerk van de wereld (ook in stukken wereld die
     * niet geladen zijn) en wat er geladen is (ook een kern die net is neergezet en nog geen IP heeft).
     */
    private static Set<BlockPos> otherCores(ServerLevel level, BlockPos corePos) {
        Set<BlockPos> cores = new HashSet<>();
        for (net.minecraft.core.GlobalPos other : com.senne.starktech.security.net.StarkNetworkData.get(level).all().values()) {
            if (other.dimension().equals(level.dimension())) {
                cores.add(other.pos());
            }
        }
        Set<BlockPos> loaded = LOADED.get(level.dimension());
        if (loaded != null) {
            cores.addAll(loaded);
        }
        cores.remove(corePos);
        // Een kern die nog in het netwerk staat maar in de wereld weg is, telt niet.
        cores.removeIf(pos -> level.isLoaded(pos) && !(level.getBlockEntity(pos) instanceof SecurityCoreBlockEntity));
        return cores;
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

    /**
     * Zou een kern op deze plek een gebied krijgen dat het gebied van een andere kern overlapt? Dan
     * mag hij er niet staan ("too close to another security network"). We kijken in het netwerk van
     * de wereld (StarkNetworkData), zodat ook kernen meetellen in stukken wereld die niet geladen
     * zijn. Een kern die daar nog staat maar in de wereld weg is, ruimen we meteen op.
     *
     * Ook over de zone van een andere kern mag zijn gebied niet liggen (een zone reikt tot ZONE_REACH
     * blokken ver): anders zette je vlak naast iemands zone een kern, en schoten jouw torentjes en
     * drones in zijn zone.
     */
    public static boolean overlaps(ServerLevel level, BlockPos pos) {
        ZoneBox area = areaOf(pos);
        ZoneData zones = ZoneData.get(level);
        for (ZoneData.Zone zone : List.copyOf(zones.in(level.dimension()))) {
            BlockPos core = zone.core().pos();
            if (core.equals(pos) || !zone.touches(area)) {
                continue;
            }
            if (level.isLoaded(core) && !(level.getBlockEntity(core) instanceof SecurityCoreBlockEntity)) {
                zones.forget(zone.core()); // de kern is weg (bijvoorbeeld met /setblock): zijn zone ook
                continue;
            }
            return true;
        }
        var network = com.senne.starktech.security.net.StarkNetworkData.get(level);
        for (net.minecraft.core.GlobalPos other : network.all().values()) {
            if (!other.dimension().equals(level.dimension()) || other.pos().equals(pos)
                    || !areasOverlap(other.pos(), pos)) {
                continue;
            }
            if (level.isLoaded(other.pos()) && !(level.getBlockEntity(other.pos()) instanceof SecurityCoreBlockEntity)) {
                network.release(other);
                continue;
            }
            return true;
        }
        // Ook een kern die net is neergezet en nog geen IP heeft.
        Set<BlockPos> loaded = LOADED.get(level.dimension());
        return loaded != null && loaded.stream().anyMatch(other -> !other.equals(pos) && areasOverlap(other, pos));
    }

    /** Overlappen de gebieden van kernen op deze twee plekken (minder dan 2 x ZONE_REACH + 1 uit elkaar)? */
    public static boolean areasOverlap(BlockPos a, BlockPos b) {
        int both = 2 * SecurityBalance.ZONE_REACH;
        return Math.abs(a.getX() - b.getX()) <= both
                && Math.abs(a.getZ() - b.getZ()) <= both
                && Math.abs(a.getY() - b.getY()) <= both;
    }

    /** Bij het afsluiten van de server: alles vergeten. */
    public static void clear() {
        LOADED.clear();
    }
}
