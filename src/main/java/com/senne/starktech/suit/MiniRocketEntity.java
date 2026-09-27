package com.senne.starktech.suit;

import com.senne.starktech.edith.EdithTargeting;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Eén mini-raket uit het raketpakket.
 *
 * Hij vliegt eerst een paar ticks rechtdoor (zo waaiert het salvo mooi uit over je schouders),
 * gaat dan steeds sneller en stuurt elke tick een stukje bij naar zijn doelwit. Heeft hij
 * geen doelwit, dan vliegt hij naar de plek waar je naar keek. Raakt hij iets, dan ontploft
 * hij: flinke schade voor wat hij raakt, een beetje voor wie ernaast staat. Blokken gaan niet
 * stuk, en jou, je team en je eigen drones raakt hij nooit (EdithTargeting).
 *
 * Het is een "projectiel", net als een pijl. Daardoor houden rayshield-koepels en laserwanden
 * hem vanzelf tegen, zonder dat we daar hier iets voor hoeven te doen.
 */
public class MiniRocketEntity extends Projectile {
    /** Waar hij op jaagt (alleen op de server bekend). */
    private @Nullable UUID targetId;
    /** Waar hij heen vliegt als er geen doelwit (meer) is. */
    private @Nullable Vec3 aimPoint;
    private int age;
    /** Raketten van een torentje raken alleen monsters (en hun eigen doelwit), nooit iemand anders. */
    private boolean onlyEnemies;
    /** Hoeveel harder hij aankomt dan normaal (raketkoppen in het harnas; 1 = normaal). */
    private float damageScale = 1.0f;

    public MiniRocketEntity(EntityType<? extends MiniRocketEntity> type, Level level) {
        super(type, level);
    }

    /**
     * Zet een raket klaar: waar hij begint, in welke richting hij wegschiet, en waar hij op
     * jaagt (een wezen, of anders een plek).
     */
    public void launch(Entity owner, Vec3 from, Vec3 direction, @Nullable LivingEntity target, @Nullable Vec3 aim) {
        setOwner(owner);
        setPos(from);
        setDeltaMovement(direction.normalize().scale(SuitBalance.ROCKET_START_SPEED));
        faceMotion();
        this.targetId = target == null ? null : target.getUUID();
        this.aimPoint = target == null ? aim : target.getBoundingBox().getCenter();
    }

    /** Sterkere raket (raketkoppen): de schade keer dit getal. */
    public void setDamageScale(float scale) {
        this.damageScale = scale;
    }

    /** Voor torentjes: raak alleen monsters en je eigen doelwit. */
    public void onlyHitEnemies() {
        this.onlyEnemies = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // Niets extra's: positie en vaart stuurt het spel zelf door.
    }

    /** Raketten worden niet opgeslagen: na een paar seconden zijn ze toch weg. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        Level level = level();
        if (!level.isClientSide) {
            if (++age > SuitBalance.ROCKET_LIFETIME) {
                explode(position(), null);
                return;
            }
            steer((ServerLevel) level);
        }

        // Raakt hij op weg naar zijn volgende plek iets (een blok of een wezen)?
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS
                && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) {
            hitTargetOrDeflectSelf(hit);
            if (isRemoved()) {
                return;
            }
        }
        checkInsideBlocks();

        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        faceMotion();

        if (level.isClientSide) {
            // Het spoor: een vlammetje en rook uit de staart.
            Vec3 tail = position().subtract(motion.normalize().scale(0.3));
            level.addParticle(ParticleTypes.SMALL_FLAME, tail.x, tail.y, tail.z, 0, 0, 0);
            level.addParticle(ParticleTypes.SMOKE, tail.x, tail.y, tail.z, 0, 0.01, 0);
        }
    }

    /** Sneller gaan, en (na de eerste ticks) bijsturen naar het doelwit. */
    private void steer(ServerLevel level) {
        Vec3 motion = getDeltaMovement();
        double speed = Math.min(SuitBalance.ROCKET_MAX_SPEED, motion.length() + SuitBalance.ROCKET_ACCELERATION);
        Vec3 direction = motion.normalize();
        if (age > SuitBalance.ROCKET_ARM_TICKS) {
            if (targetId != null && level.getEntity(targetId) instanceof LivingEntity target && target.isAlive()) {
                aimPoint = target.getBoundingBox().getCenter();
            }
            if (aimPoint != null) {
                Vec3 toAim = aimPoint.subtract(position());
                // Dichtbij stuurt hij veel scherper bij; anders schiet hij er in een bocht langs.
                double near = Math.max(0, 1 - toAim.length() / SuitBalance.ROCKET_SHARP_TURN_DISTANCE);
                double turn = Mth.lerp(near, SuitBalance.ROCKET_TURN, 1.0);
                direction = direction.add(toAim.normalize().subtract(direction).scale(turn)).normalize();
            }
        }
        setDeltaMovement(direction.scale(speed));
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

    /** Alleen levende wezens die jij mag aanvallen; nooit jezelf of een andere raket. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !super.canHitEntity(entity)) {
            return false;
        }
        Entity owner = getOwner();
        if (entity == owner) {
            return false;
        }
        if (onlyEnemies) {
            return living instanceof net.minecraft.world.entity.monster.Enemy || living.getUUID().equals(targetId);
        }
        return !(owner instanceof ServerPlayer player) || EdithTargeting.canTarget(player, living);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        explode(result.getLocation(), result.getEntity());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        explode(result.getLocation(), null);
    }

    /**
     * Boem: schade voor {@code direct} (wat hij raakte) en een beetje voor wie ernaast staat.
     * Geen echte Minecraft-explosie: die zou blokken slopen en jou ook raken.
     */
    private void explode(Vec3 at, @Nullable Entity direct) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Entity owner = getOwner();
        if (direct instanceof LivingEntity living) {
            // Een salvo raakt hetzelfde doelwit vlak na elkaar. Normaal is een wezen na een klap
            // een halve seconde onkwetsbaar; dan zou alleen de eerste raket tellen.
            living.invulnerableTime = 0;
            living.hurt(damageSources().explosion(this, owner), SuitBalance.ROCKET_DAMAGE * damageScale);
        }
        AABB area = new AABB(at, at).inflate(SuitBalance.ROCKET_SPLASH_RADIUS);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != direct && e != owner && e.isAlive() && canHitEntity(e))) {
            if (nearby.getBoundingBox().getCenter().distanceTo(at) <= SuitBalance.ROCKET_SPLASH_RADIUS + 0.5) {
                nearby.hurt(damageSources().explosion(this, owner), SuitBalance.ROCKET_SPLASH_DAMAGE * damageScale);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.06);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                0.5f, 1.4f + level.random.nextFloat() * 0.3f);
        discard();
    }
}
