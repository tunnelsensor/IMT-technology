package com.senne.starktech.satellite;

import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModSounds;
import net.minecraft.core.BlockPos;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Een raket van de Stark-satelliet (in plaats van de bliksemschicht van vroeger).
 *
 * Als de satelliet klaar is met richten, laat hij de raket hoog boven het doel los. Die komt
 * schuin binnen (van een willekeurige kant, zo zie je hem beter), gaat steeds sneller en buigt
 * af naar zijn doel, zodat hij op het laatst bijna recht naar beneden duikt: dan raakt hij geen
 * muur naast het doelwit. Volgt de satelliet een wezen (lock-on), dan blijft de raket op dat
 * wezen gericht. Raakt hij onderweg toch een dak of muur, dan slaat hij daar in.
 *
 * Wat de inslag doet (schade, krater, schokgolf) staat niet hier maar in SatelliteStrikes: die
 * geeft de raket bij het lanceren mee wat er moet gebeuren ({@code onImpact}).
 *
 * Hij wordt niet opgeslagen: een raket die halverwege is als de wereld sluit, is gewoon weg.
 */
public class OrbitalMissileEntity extends Entity {
    /** Hoe hoog boven het doel hij losgelaten wordt, en hoever opzij. */
    private static final double DROP_HEIGHT = 150.0;
    private static final double DROP_SIDEWAYS = 35.0;
    /** Blokken per tick: begin, maximum, en hoeveel er elke tick bij komt. */
    private static final double START_SPEED = 2.0;
    private static final double MAX_SPEED = 7.0;
    private static final double ACCELERATION = 0.4;
    /** Na zo veel ticks is hij sowieso weg (nooit eindeloos rondvliegen). */
    private static final int MAX_AGE = 200;
    /**
     * Hoeveel ticks hij ongeveer valt (van het loslaten tot de inslag). De satelliet laat hem zo
     * lang voor het einde van het aftellen los. Uitgerekend met dezelfde stapjes als in tick().
     */
    public static final int FALL_TICKS = fallTicks();

    private static int fallTicks() {
        Vec3 target = Vec3.ZERO;
        Vec3 pos = new Vec3(DROP_SIDEWAYS, DROP_HEIGHT, 0);
        double speed = START_SPEED;
        int ticks = 0;
        while (ticks < MAX_AGE) {
            speed = Math.min(MAX_SPEED, speed + ACCELERATION);
            if (target.subtract(pos).length() <= speed) {
                break;
            }
            pos = pos.add(aimPoint(pos, target).subtract(pos).normalize().scale(speed));
            ticks++;
        }
        return ticks + 1;
    }

    /**
     * Waar de raket nu naartoe stuurt: niet recht op het doel af, maar op een punt recht boven
     * het doel, half zo hoog als hij zelf nog boven het doel hangt. Dat punt zakt mee terwijl hij
     * daalt; zo buigt hij af en valt hij het laatste stuk bijna loodrecht.
     */
    private static Vec3 aimPoint(Vec3 from, Vec3 target) {
        double above = Math.max(0, from.y - target.y);
        return target.add(0, above * 0.5, 0);
    }

    private Vec3 target = Vec3.ZERO;
    private @Nullable UUID lockedId;
    private @Nullable Consumer<Vec3> onImpact;
    private int age;
    /** Alleen in jouw spel: waar het spoor vorige tick ophield. */
    private @Nullable Vec3 lastTrail;

    public OrbitalMissileEntity(EntityType<? extends OrbitalMissileEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /**
     * Laat een raket los boven {@code target}. Volgt hij een wezen ({@code lockedId}), dan stuurt
     * hij daarheen bij. Bij de inslag roept hij {@code onImpact} aan met de plek waar hij insloeg.
     */
    public static void launch(ServerLevel level, Vec3 target, @Nullable UUID lockedId, Consumer<Vec3> onImpact) {
        OrbitalMissileEntity missile = ModEntities.ORBITAL_MISSILE.get().create(level);
        // Draait de wereld daar niet (te ver van elke speler)? Dan zou de raket blijven hangen:
        // dan meteen inslaan, zonder raket.
        if (missile == null || !level.isPositionEntityTicking(BlockPos.containing(target))) {
            onImpact.accept(target);
            return;
        }
        double angle = level.random.nextDouble() * Math.PI * 2;
        double y = Math.min(target.y + DROP_HEIGHT, level.getMaxBuildHeight() + 100);
        Vec3 start = new Vec3(target.x + Math.cos(angle) * DROP_SIDEWAYS, y, target.z + Math.sin(angle) * DROP_SIDEWAYS);
        if (!level.isPositionEntityTicking(BlockPos.containing(start.x, target.y, start.z))) {
            start = new Vec3(target.x, y, target.z); // opzij draait de wereld niet: recht van boven
        }
        missile.setPos(start);
        missile.target = target;
        missile.lockedId = lockedId;
        missile.onImpact = onImpact;
        missile.setDeltaMovement(target.subtract(start).normalize().scale(START_SPEED));
        missile.faceMotion();
        level.addFreshEntity(missile);
        // Het gefluit klinkt op het doel: wie daar staat, hoort hem aankomen.
        level.playSound(null, target.x, target.y, target.z, ModSounds.SATELLITE_MISSILE.get(), SoundSource.PLAYERS, 4.0f,
                0.95f + level.random.nextFloat() * 0.1f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // Niets extra's: positie en vaart stuurt het spel zelf door.
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            if (++age > MAX_AGE || onImpact == null) {
                discard();
                return;
            }
            // Lock-on: loopt het doelwit weg, dan gaat de raket mee.
            if (lockedId != null && level.getEntity(lockedId) instanceof LivingEntity locked && locked.isAlive()) {
                target = locked.position();
            }
            double speed = Math.min(MAX_SPEED, getDeltaMovement().length() + ACCELERATION);
            Vec3 toTarget = target.subtract(position());
            if (toTarget.length() <= speed) {
                impact(target);
                return;
            }
            Vec3 motion = aimPoint(position(), target).subtract(position()).normalize().scale(speed);
            // Een dak of muur op de weg? Dan slaat hij daar in.
            BlockHitResult hit = level.clip(new ClipContext(position(), position().add(motion),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                impact(hit.getLocation());
                return;
            }
            setDeltaMovement(motion);
        }
        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        faceMotion();
        if (level().isClientSide) {
            trail();
        }
    }

    /**
     * Het spoor: van waar de staart vorige tick was tot waar hij nu is (hij gaat tot 7 blokken
     * per tick), zodat het één doorlopende streep wordt. Een dikke, lang blijvende rookstreep
     * (kampvuurrook), daartussen donkere rook, en vuur vlak achter de staart. Alles "altijd
     * zichtbaar", zodat je het spoor ook van ver ziet.
     */
    private void trail() {
        Level level = level();
        Vec3 now = position();
        Vec3 previous = lastTrail != null ? lastTrail : now;
        lastTrail = now;
        Vec3 path = now.subtract(previous);
        double length = path.length();
        if (length < 1.0E-3) {
            return;
        }
        Vec3 back = path.normalize();
        int steps = Math.max(1, (int) Math.ceil(length / 0.6));
        for (int i = 0; i < steps; i++) {
            Vec3 p = previous.add(path.scale(i / (double) steps)).subtract(back.scale(1.4));
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, p.x, p.y, p.z,
                    (random.nextDouble() - 0.5) * 0.04, 0.01, (random.nextDouble() - 0.5) * 0.04);
            if (i % 3 == 0) {
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, p.x, p.y, p.z,
                        (random.nextDouble() - 0.5) * 0.02, 0.01, (random.nextDouble() - 0.5) * 0.02);
            }
        }
        for (int i = 0; i < 3; i++) {
            Vec3 p = now.subtract(back.scale(1.4 + i * 0.4));
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, p.x, p.y, p.z,
                    -back.x * 0.1, -back.y * 0.1, -back.z * 0.1);
        }
    }

    private void impact(Vec3 at) {
        Consumer<Vec3> callback = onImpact;
        onImpact = null;
        discard();
        if (callback != null) {
            callback.accept(at);
        }
    }

    /** Draai de neus in de vliegrichting (daar kijkt de renderer naar). */
    private void faceMotion() {
        Vec3 motion = getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-6) {
            return;
        }
        setYRot((float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(motion.y, motion.horizontalDistance()) * Mth.RAD_TO_DEG));
        if (tickCount <= 1) {
            yRotO = getYRot();
            xRotO = getXRot();
        }
    }

    /** Ook van heel ver te zien (normaal verdwijnt zo'n klein ding na een paar tientallen blokken). */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 320 * 320;
    }

    /** Niet te raken of kapot te maken: hij valt gewoon door. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
