package com.senne.starktech.shield;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.DomeActionPayload;
import com.senne.starktech.network.DomeOpenPayload;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.raid.RaidDroneEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De spelregels van de schildkoepel.
 *
 * De koepel houdt alles tegen wat NIET van zijn eigenaar (of diens team) komt:
 * - projectielen die van buiten naar binnen vliegen (pijlen, vuurballen...);
 * - monsters en raiddrones: die worden naar buiten geduwd;
 * - laserschoten van raiddrones en van de drones van anderen, als het doelwit binnen staat;
 * - satellietaanvallen van anderen op iets binnen de koepel.
 * Spelers en dieren kunnen er gewoon door (anders kwam je zelf niet meer thuis).
 *
 * Elke tegengehouden treffer kost schildkracht (zie DomeSettings); is die op, dan valt de
 * koepel even uit. Jouw eigen pijlen, drones en satelliet gaan er gewoon doorheen.
 */
public final class EdithDomes {
    private EdithDomes() {}

    /** Welke koepels op dit moment staan, per wereld. Zo hoeven we niet de hele wereld af te zoeken. */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE = new HashMap<>();

    static void setActive(ServerLevel level, BlockPos pos, boolean up) {
        Set<BlockPos> domes = ACTIVE.computeIfAbsent(level.dimension(), key -> new HashSet<>());
        if (up) {
            domes.add(pos.immutable());
        } else {
            domes.remove(pos);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    // ================================================================ Wie is vriend?

    /**
     * Hoort deze aanvaller bij de eigenaar van de koepel? Dat is zo als hij (of wie hem
     * stuurde: de schutter van een pijl, de eigenaar van een drone) de eigenaar zelf is, of in
     * hetzelfde team zit.
     */
    static boolean friendly(ServerLevel level, DomeGeneratorBlockEntity dome, @Nullable UUID attackerId) {
        if (attackerId == null || dome.ownerId() == null) {
            return false;
        }
        if (attackerId.equals(dome.ownerId())) {
            return true;
        }
        ServerPlayer attacker = level.getServer().getPlayerList().getPlayer(attackerId);
        PlayerTeam ownerTeam = level.getScoreboard().getPlayersTeam(dome.ownerName());
        return attacker != null && ownerTeam != null && ownerTeam.isAlliedTo(attacker.getTeam());
    }

    /** Wie er achter dit ding zit: de schutter van een projectiel, de eigenaar van een drone, of hijzelf. */
    private static @Nullable UUID responsible(@Nullable Entity entity) {
        if (entity instanceof Projectile projectile) {
            Entity shooter = projectile.getOwner();
            return shooter == null ? null : responsible(shooter);
        }
        if (entity instanceof StarkDroneEntity drone) {
            return drone.getOwnerId();
        }
        return entity instanceof Player player ? player.getUUID() : null;
    }

    // ================================================================ Tegenhouden

    /**
     * Wordt een treffer op {@code target} tegengehouden door een koepel? Alleen als het doelwit
     * binnen een koepel ligt, de aanval van buiten komt ({@code from}; null = uit de ruimte, zoals
     * de satelliet) en de aanvaller geen vriend is. Dan kost het schildkracht en zie je een flits
     * op de koepel.
     *
     * @return de plek waar de treffer op de koepel botst (daar laat je de straal eindigen), of
     *         null als er geen koepel in de weg zit: dan gaat de treffer gewoon door
     */
    public static @Nullable Vec3 blockedAt(ServerLevel level, Vec3 target, @Nullable Vec3 from,
                                           @Nullable UUID attackerId, int cost) {
        Set<BlockPos> domes = ACTIVE.get(level.dimension());
        if (domes == null || domes.isEmpty()) {
            return null;
        }
        for (BlockPos pos : List.copyOf(domes)) {
            if (!(level.getBlockEntity(pos) instanceof DomeGeneratorBlockEntity dome) || !dome.isUp()) {
                continue;
            }
            if (!dome.contains(target) || (from != null && dome.contains(from)) || friendly(level, dome, attackerId)) {
                continue;
            }
            Vec3 source = from != null ? from : target.add(0, dome.radius() * 2.0, 0);
            if (cost <= 0) {
                return shellPoint(dome, source, target); // tegengehouden, maar deze keer zonder flits
            }
            Vec3 hit = impact(level, dome, source, target);
            dome.absorb(level, cost);
            return hit;
        }
        return null;
    }

    /** Een treffer tegen de koepel: een flits en een zoemende klap op de plek waar hij de koepel raakt. */
    private static Vec3 impact(ServerLevel level, DomeGeneratorBlockEntity dome, Vec3 from, Vec3 towards) {
        Vec3 hit = shellPoint(dome, from, towards);
        level.sendParticles(ParticleTypes.FLASH, hit.x, hit.y, hit.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.x, hit.y, hit.z, 16, 0.4, 0.4, 0.4, 0.25);
        level.sendParticles(ParticleTypes.END_ROD, hit.x, hit.y, hit.z, 8, 0.3, 0.3, 0.3, 0.08);
        level.playSound(null, hit.x, hit.y, hit.z, com.senne.starktech.registry.ModSounds.RAYSHIELD_HIT.get(),
                SoundSource.BLOCKS, 1.5f, 0.75f);
        return hit;
    }

    /**
     * Waar een lijn van {@code from} naar {@code towards} de koepel raakt: het eerste punt op de
     * bol. Ligt {@code from} er niet buiten, dan het punt recht boven het doelwit.
     */
    static Vec3 shellPoint(DomeGeneratorBlockEntity dome, Vec3 from, Vec3 towards) {
        Vec3 center = dome.center();
        double r = dome.radius();
        Vec3 d = towards.subtract(from);
        Vec3 f = from.subtract(center);
        double a = d.dot(d);
        double b = 2 * f.dot(d);
        double c = f.dot(f) - r * r;
        double disc = b * b - 4 * a * c;
        if (a > 1.0E-6 && disc >= 0 && c > 0) {
            double t = (-b - Math.sqrt(disc)) / (2 * a);
            if (t >= 0 && t <= 1) {
                return from.add(d.scale(t));
            }
        }
        double dx = towards.x - center.x;
        double dz = towards.z - center.z;
        double up = Math.sqrt(Math.max(0, r * r - dx * dx - dz * dz));
        return new Vec3(towards.x, center.y + up, towards.z);
    }

    /**
     * Elke tick, voor een koepel die staat: projectielen die van buiten naar binnen vliegen
     * verdampen op de rand, en (elke halve seconde) worden monsters en raiddrones naar buiten
     * geduwd. Wat van de eigenaar of zijn team komt, laten we met rust.
     */
    static void defend(ServerLevel level, DomeGeneratorBlockEntity dome, long now) {
        Vec3 center = dome.center();
        double radius = dome.radius();
        AABB area = new AABB(center, center).inflate(radius + 4);

        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area)) {
            double distance = projectile.position().distanceTo(center);
            Vec3 motion = projectile.getDeltaMovement();
            boolean inward = motion.dot(center.subtract(projectile.position())) > 0;
            boolean atShell = distance <= radius + 2.5 && distance + motion.length() >= radius - 1.0;
            if (!inward || !atShell || projectile.getY() < center.y - 1.5
                    || friendly(level, dome, responsible(projectile))) {
                continue;
            }
            impact(level, dome, projectile.position().subtract(motion), projectile.position());
            projectile.discard();
            dome.absorb(level, projectile instanceof AbstractHurtingProjectile
                    ? StarkConfig.Dome.COST_FIREBALL : StarkConfig.Dome.COST_PROJECTILE);
            if (!dome.isUp()) {
                return;
            }
        }

        if (now % 10 != 0) {
            return;
        }
        for (LivingEntity intruder : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity.isAlive() && (entity instanceof Enemy || entity instanceof RaidDroneEntity))) {
            if (!dome.contains(intruder.position())) {
                continue;
            }
            Vec3 away = intruder.position().subtract(center);
            Vec3 push = (away.horizontalDistanceSqr() < 0.01 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z))
                    .normalize().scale(0.9);
            intruder.push(push.x, intruder instanceof RaidDroneEntity ? 0.4 : 0.2, push.z);
            intruder.hurtMarked = true;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, intruder.getX(), intruder.getY() + 1, intruder.getZ(),
                    4, 0.2, 0.3, 0.2, 0.05);
            dome.absorb(level, StarkConfig.Dome.COST_PUSH);
            if (!dome.isUp()) {
                return;
            }
        }
    }

    // ================================================================ Het scherm

    /** Rechtsklik op de generator: alleen de eigenaar mag hem instellen. */
    public static void open(ServerPlayer player, BlockPos pos) {
        DomeGeneratorBlockEntity dome = dome(player, pos);
        if (dome != null) {
            ModNetwork.sendTo(player, new DomeOpenPayload(pos));
        }
    }

    /** Een knop in het scherm. */
    public static void handleAction(ServerPlayer player, DomeActionPayload payload) {
        DomeGeneratorBlockEntity dome = dome(player, payload.pos());
        if (dome == null) {
            return;
        }
        switch (payload.op()) {
            case ENABLE -> dome.setEnabled(payload.value() != 0);
            case COLOUR -> {
                ShieldColor[] all = ShieldColor.values();
                dome.setColour(all[Math.floorMod(payload.value(), all.length)]);
            }
            case RADIUS -> dome.setRadius(payload.value());
            case STRENGTH -> dome.setStrength(payload.value());
        }
        player.serverLevel().playSound(null, payload.pos(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.4f, 1.6f);
    }

    /**
     * De koepel waar deze speler aan mag zitten, of null (met een melding). Hij moet er dichtbij
     * staan, en het moet zijn eigen koepel zijn. Een koepel zonder eigenaar (bijvoorbeeld in een
     * gebouw) wordt van wie hem het eerst instelt.
     */
    private static @Nullable DomeGeneratorBlockEntity dome(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 64
                || !(player.serverLevel().getBlockEntity(pos) instanceof DomeGeneratorBlockEntity dome)) {
            return null;
        }
        if (dome.ownerId() == null) {
            dome.setOwner(player);
        }
        if (!player.getUUID().equals(dome.ownerId())) {
            EdithServer.send(player, Component.translatable("screen.starktech.dome.not_yours", dome.ownerName()),
                    EdithMessagePayload.Level.WARNING);
            return null;
        }
        return dome;
    }
}
