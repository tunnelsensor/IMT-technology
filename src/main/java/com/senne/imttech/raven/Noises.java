package com.senne.imttech.raven;

import com.senne.imttech.config.ImtConfig;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Lawaai (hoofdstuk 60, stap 3, jouw antwoord 3: "schoten, ontploffingen, blokken kapotslaan, rennen, deuren, vallen;
 * sluipen is stil; de Glock luid, een laserpistool zacht"). Alles wat lawaai maakt, meldt zich hier met een plek en hoe
 * ver het te horen is (blokken, uit {@code [guards]}):
 * <ul>
 *   <li>de Glock ({@code noiseGlock} 48, Firearms), een laserpistool ({@code noiseLaserPistol} 12) en de andere
 *       lasergeweren ({@code noiseLaserRifle} 16, LaserGuns), en de wapens van jouw robots (BotShot);</li>
 *   <li>elke ontploffing ({@code noiseExplosion} 56: frags, de Remote Frag, TNT), een flashbang ({@code noiseFlashbang}
 *       24) en een EMP ({@code noiseEmp} 12);</li>
 *   <li>een blok kapotslaan ({@code noiseBlock} 14), rennen ({@code noiseSteps} 8, om de zes ticks), een deur, luik of
 *       hek ({@code noiseDoor} 8) en een val van meer dan drie blokken ({@code noiseFall} 12).</li>
 * </ul>
 * Elke Raven-soldaat binnen die afstand hoort het ({@link RavenSenses#heard}); zit er een muur tussen, dan draagt het
 * maar {@code noiseWallFactor} (de helft) zo ver. Lawaai van een soldaat zelf telt niet (zijn maten weten al dat hij
 * schiet), en sinds stap 6 horen ook robots en drones het ({@code when heard:}). Sluipen en gewoon lopen maken geen
 * lawaai: zo sluip je zoals in Hitman langs een bewaker.
 */
public final class Noises {
    private Noises() {}

    /** Wat voor lawaai (voor {@code sound.kind} in een robotprogramma, stap 6). */
    public enum Kind {
        SHOT, EXPLOSION, BLOCK, STEPS, DOOR, FALL;

        /** Het woord in een programma: "shot", "explosion"... */
        public String word() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Wie er nog meer wil horen (stap 6: de robots en drones). */
    public interface Listener {
        void hear(ServerLevel level, Vec3 at, double radius, Kind kind, @Nullable Entity source);
    }

    private static final java.util.List<Listener> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void listen(Listener listener) {
        LISTENERS.add(listener);
    }

    /**
     * Lawaai op {@code at}, tot {@code radius} blokken te horen, gemaakt door {@code source} (een speler, een granaat...
     * of null). Door een muur heen telt {@code noiseWallFactor} van de afstand.
     */
    public static void make(ServerLevel level, Vec3 at, double radius, Kind kind, @Nullable Entity source) {
        if (radius <= 0 || source instanceof RavenSoldierEntity) {
            return;
        }
        AABB box = new AABB(at, at).inflate(radius);
        for (RavenSoldierEntity soldier : level.getEntitiesOfClass(RavenSoldierEntity.class, box, Entity::isAlive)) {
            if (hears(level, soldier.getEyePosition(), at, radius)) {
                soldier.senses().heard(at, kind);
            }
        }
        for (Listener listener : LISTENERS) {
            listener.hear(level, at, radius, kind, source);
        }
    }

    /** Hoort iemand met zijn oren op {@code ear} lawaai op {@code at} dat {@code radius} ver draagt (een muur halveert)? */
    public static boolean hears(ServerLevel level, Vec3 ear, Vec3 at, double radius) {
        double reach = radius;
        HitResult wall = level.clip(new ClipContext(ear, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (wall.getType() != HitResult.Type.MISS) {
            reach *= ImtConfig.Guards.NOISE_WALL_FACTOR;
        }
        return ear.distanceToSqr(at) <= reach * reach;
    }

    // ================================================================ Wat er vanzelf lawaai maakt

    /** Rennen: om de zes ticks een stap die te horen is (gewoon lopen en sluipen zijn stil). */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isSprinting() || !player.onGround()
                || player.tickCount % 6 != 0 || player.isSpectator()) {
            return;
        }
        Vec3 v = player.getDeltaMovement();
        if (v.x * v.x + v.z * v.z < 0.0025) {
            return;
        }
        make(player.serverLevel(), player.position(), ImtConfig.Guards.NOISE_STEPS, Kind.STEPS, player);
    }

    /** Een blok kapotslaan. */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getPlayer() != null) {
            make(level, Vec3.atCenterOf(event.getPos()), ImtConfig.Guards.NOISE_BLOCK, Kind.BLOCK, event.getPlayer());
        }
    }

    /** Een deur, luik of hek open- of dichtdoen. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        var block = level.getBlockState(event.getPos()).getBlock();
        boolean door = block instanceof DoorBlock d && d.type().canOpenByHand()
                || block instanceof TrapDoorBlock && !level.getBlockState(event.getPos()).is(net.minecraft.world.level.block.Blocks.IRON_TRAPDOOR)
                || block instanceof FenceGateBlock;
        if (door) {
            make(level, Vec3.atCenterOf(event.getPos()), ImtConfig.Guards.NOISE_DOOR, Kind.DOOR, event.getEntity());
        }
    }

    /** Een val van meer dan drie blokken. */
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getDistance() > 3.0f && !player.isSpectator()) {
            make(player.serverLevel(), player.position(), ImtConfig.Guards.NOISE_FALL, Kind.FALL, player);
        }
    }

    /** Elke ontploffing: granaten, de Remote Frag, TNT. */
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            net.minecraft.world.level.Explosion explosion = event.getExplosion();
            make(level, explosion.center(), ImtConfig.Guards.NOISE_EXPLOSION, Kind.EXPLOSION, explosion.getIndirectSourceEntity());
        }
    }
}
