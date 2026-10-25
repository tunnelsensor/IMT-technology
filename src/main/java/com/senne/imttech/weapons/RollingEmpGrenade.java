package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * De EMP Grenade (hoofdstuk 35, jouw wens: "de bol die rolt zoals bij Star Wars detonators"). Hij gaat 3 seconden na
 * het gooien af (ROLLING_FUSE_SECONDS), waar hij dan ook ligt. Onderweg stuitert hij tegen muren, de vloer en wezens,
 * en op de grond rolt hij verder tot hij stil ligt. Zo kun je hem om een hoek gooien, of met sneak over de vloer laten
 * rollen.
 */
public class RollingEmpGrenade extends EmpGrenadeEntity {
    /** Zo hard trekt de zwaartekracht (blokken per tick per tick), zoals bij een sneeuwbal. */
    private static final double GRAVITY = 0.04;
    /** Hoeveel vaart hij per tick houdt als hij over de grond rolt, en in de lucht. */
    private static final double ROLLING = 0.94;
    private static final double AIR = 0.99;
    /** Langzamer dan dit stuitert hij niet meer op de vloer, maar blijft hij liggen (en rolt hij). */
    private static final double MIN_BOUNCE = 0.12;
    /** De straal van de bol (blokken), om te weten hoe ver hij draait als hij rolt. */
    public static final float RADIUS = 3 / 16f;

    // Alleen in je spel: hoe de bol nu gedraaid is (door het rollen), en hoe de vorige tick.
    public final Quaternionf spin = new Quaternionf();
    public final Quaternionf spinO = new Quaternionf();

    public RollingEmpGrenade(EntityType<? extends RollingEmpGrenade> type, Level level) {
        super(type, level);
    }

    /** Een nieuwe granaat in de hand van {@code thrower}, die meteen op scherp staat. */
    public RollingEmpGrenade(Level level, LivingEntity thrower) {
        this(ModEntities.ROLLING_EMP_GRENADE.get(), level);
        setOwner(thrower);
        setPos(thrower.getX(), thrower.getEyeY() - 0.2, thrower.getZ());
        arm(Math.round(ImtConfig.Weapons.ROLLING_FUSE_SECONDS * 20));
    }

    @Override
    protected void fly() {
        Vec3 wanted = getDeltaMovement().add(0, -GRAVITY, 0);
        Vec3 before = position();
        // Eerst kijken of hij een wezen raakt (niet zijn gooier, die hij net loslaat).
        EntityHitResult hitEntity = ProjectileUtil.getEntityHitResult(level(), this, before, before.add(wanted),
                getBoundingBox().expandTowards(wanted).inflate(0.1), this::canHitEntity);
        if (hitEntity != null && wanted.length() > 0.15) {
            // Tegen iemand aan: hij kaatst terug, een stuk trager.
            wanted = wanted.scale(-0.3);
            bounceSound(wanted.length());
        }
        move(MoverType.SELF, wanted);
        Vec3 moved = position().subtract(before);
        double bounce = ImtConfig.Weapons.BOUNCE;
        double vx = wanted.x;
        double vy = wanted.y;
        double vz = wanted.z;
        boolean hit = false;
        // Is hij in een richting minder ver gekomen dan hij wilde? Dan zat daar een blok: kaats terug.
        if (Math.abs(moved.x - wanted.x) > 1.0E-4) {
            hit |= Math.abs(vx) > MIN_BOUNCE;
            vx = -vx * bounce;
        }
        if (Math.abs(moved.z - wanted.z) > 1.0E-4) {
            hit |= Math.abs(vz) > MIN_BOUNCE;
            vz = -vz * bounce;
        }
        if (Math.abs(moved.y - wanted.y) > 1.0E-4) {
            if (Math.abs(vy) > MIN_BOUNCE + GRAVITY) {
                hit = true;
                vy = -vy * bounce;
            } else {
                vy = 0; // te traag om te stuiteren: hij ligt (en rolt)
            }
        }
        if (hit) {
            bounceSound(Math.sqrt(vx * vx + vy * vy + vz * vz));
        }
        double keep = onGround() ? ROLLING : AIR;
        setDeltaMovement(vx * keep, vy * AIR, vz * keep);
        if (level().isClientSide) {
            roll(moved);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target instanceof LivingEntity && !target.isSpectator();
    }

    private void bounceSound(double speed) {
        if (!level().isClientSide && speed > 0.05) {
            level().playSound(null, getX(), getY(), getZ(), ModSounds.EMP_GRENADE_BOUNCE.get(), SoundSource.PLAYERS,
                    (float) Mth.clamp(speed * 2, 0.2, 0.8), 0.9f + random.nextFloat() * 0.2f);
        }
    }

    /** In je spel: de bol draait mee met hoe ver hij over de grond ging (om de as dwars op zijn richting). */
    private void roll(Vec3 moved) {
        spinO.set(spin);
        double distance = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
        if (distance < 1.0E-4) {
            return;
        }
        Vector3f axis = new Vector3f((float) moved.z, 0, (float) -moved.x).normalize();
        float angle = (float) (distance / RADIUS);
        spin.premul(new Quaternionf().rotationAxis(angle, axis));
    }
}
