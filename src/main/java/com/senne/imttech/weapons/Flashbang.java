package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.FlashPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.registry.ModSounds;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * De flashbang (hoofdstuk 35). Jouw keuze: "zoals in CS" en alleen voor spelers (robots, turrets, drones en monsters
 * merken er niets van). Een felle flits en een harde knal: elke speler die de flits kan zien (geen muur ertussen, binnen
 * FLASH_RADIUS) krijgt een wit scherm en een piep in zijn oren, ook wie hem gooide. Hoe erg, hangt af van waar je naar
 * kijkt ({@link #strength}): recht ernaar = helemaal, opzij = ongeveer de helft, met je rug ernaartoe = een beetje. En
 * van de afstand: tot 4 blokken helemaal, verder minder.
 *
 * Het witte scherm zelf tekent je spel (zie client/weapons/FlashOverlay).
 */
public final class Flashbang {
    private Flashbang() {}

    /** Zo dichtbij is de flits altijd op zijn felst (blokken). */
    private static final double FULL_DISTANCE = 4;

    /** Een flashbang gaat af op {@code at}. Geeft per speler hoe erg hij verblind is (0 tot 1), voor de tests. */
    public static Map<ServerPlayer, Float> bang(ServerLevel level, Vec3 at, @Nullable Entity thrower) {
        level.playSound(null, at.x, at.y, at.z, ModSounds.FLASHBANG_BANG.get(), SoundSource.PLAYERS, 3.0f, 1.0f);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 2, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 24, 0.2, 0.2, 0.2, 0.25);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.02);
        double radius = ImtConfig.Weapons.FLASH_RADIUS;
        Map<ServerPlayer, Float> blinded = new LinkedHashMap<>();
        for (ServerPlayer player : level.players()) {
            Vec3 eye = player.getEyePosition();
            if (player.isSpectator() || eye.distanceToSqr(at) > radius * radius || !canSee(level, at, eye)) {
                continue;
            }
            float strength = strength(player.getViewVector(1.0f), eye, at, radius);
            if (strength < 0.05f) {
                continue;
            }
            int ticks = Math.max(1, Math.round(ImtConfig.Weapons.FLASH_SECONDS * 20 * strength));
            blinded.put(player, strength);
            markBlinded(player, ticks);
            if (player.connection != null) {
                ModNetwork.sendTo(player, new FlashPayload(strength, ticks));
            }
        }
        return blinded;
    }

    /** Tot wanneer (speltijd) elke speler verblind is: een verblinde speler boei je meteen (hoofdstuk 48). */
    private static final Map<UUID, Long> BLINDED_UNTIL = new HashMap<>();

    public static void markBlinded(ServerPlayer player, int ticks) {
        BLINDED_UNTIL.put(player.getUUID(), player.serverLevel().getGameTime() + ticks);
    }

    /** Is hij nu nog verblind door een flashbang? */
    public static boolean isBlinded(ServerPlayer player) {
        Long until = BLINDED_UNTIL.get(player.getUUID());
        return until != null && player.serverLevel().getGameTime() < until;
    }

    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        BLINDED_UNTIL.clear();
    }

    /** Zit er geen blok tussen de flits en deze ogen? */
    private static boolean canSee(ServerLevel level, Vec3 flash, Vec3 eye) {
        return level.clip(new ClipContext(flash, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    /**
     * Hoe erg een flits je verblindt (0 tot 1), als je zo kijkt ({@code look}) en de flits daar is. Recht ernaar
     * kijken (tot 60 graden opzij) = 1; tot 100 graden opzij zakt het naar 0,35; daarachter 0,15. Verder weg dan 4
     * blokken wordt het minder, tot 0,3 keer aan de rand van FLASH_RADIUS.
     */
    public static float strength(Vec3 look, Vec3 eye, Vec3 flash, double radius) {
        Vec3 to = flash.subtract(eye);
        double distance = to.length();
        double facing = distance < 1.0E-3 ? 1 : look.normalize().dot(to.scale(1 / distance));
        double view;
        if (facing >= 0.5) {
            view = 1;
        } else if (facing >= -0.17) {
            view = 0.35 + 0.65 * (facing + 0.17) / 0.67;
        } else {
            view = 0.15;
        }
        double near = distance <= FULL_DISTANCE ? 1
                : Math.max(0.3, 1 - 0.7 * (distance - FULL_DISTANCE) / Math.max(1, radius - FULL_DISTANCE));
        return (float) (view * near);
    }
}
