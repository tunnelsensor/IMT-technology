package com.senne.imttech.guns;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Een lege huls (hoofdstuk 52, jouw wens: "elke keer als je schiet vliegt je casing er ook uit en kun je die door
 * rechts te klikken op de grond waar een casing ligt oppakken"). Hij vliegt rechts uit het pistool, stuitert met een
 * tinkje en blijft liggen. Na {@code [guns] casingSeconds} (10 minuten) verdwijnt hij, zodat een schietbaan niet
 * vol komt te liggen. Oprapen: rechtsklik op de grond vlak bij hem (zie Firearms.pickUpCasings).
 */
public class CasingEntity extends Entity {
    private static final double GRAVITY = 0.05;
    private int age;
    /** Alleen in je spel: hoe hij ligt (draait als hij vliegt). */
    public float spin;
    public float spinO;

    public CasingEntity(EntityType<? extends CasingEntity> type, Level level) {
        super(type, level);
        noCulling = false;
    }

    /** Een huls die rechts uit het pistool van {@code shooter} springt. */
    public static CasingEntity eject(ServerLevel level, LivingEntity shooter) {
        CasingEntity casing = new CasingEntity(ModEntities.CASING.get(), level);
        Vec3 look = shooter.getViewVector(1.0f);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 at = shooter.getEyePosition().add(look.scale(0.45)).add(right.scale(0.22)).subtract(up.scale(0.08));
        casing.setPos(at.x, at.y, at.z);
        var random = shooter.getRandom();
        casing.setDeltaMovement(right.scale(0.14 + random.nextDouble() * 0.05)
                .add(up.scale(0.12 + random.nextDouble() * 0.05))
                .add(look.scale(0.02))
                .add(shooter.getDeltaMovement().scale(0.5)));
        casing.spin = random.nextFloat() * 360;
        level.addFreshEntity(casing);
        return casing;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        spinO = spin;
        // Altijd zwaartekracht (ook als hij ligt: zo blijft hij "op de grond", net als een item), dan bewegen.
        Vec3 velocity = getDeltaMovement().add(0, -GRAVITY, 0);
        if (!onGround()) {
            spin += 35;
        }
        move(MoverType.SELF, velocity);
        if (verticalCollisionBelow) {
            if (velocity.y < -0.1) {
                // Stuiteren, met een tinkje.
                velocity = new Vec3(velocity.x * 0.6, -velocity.y * 0.35, velocity.z * 0.6);
                if (!level().isClientSide) {
                    level().playSound(null, getX(), getY(), getZ(), ModSounds.CASING_TINK.get(), SoundSource.NEUTRAL, 0.35f,
                            1.2f + random.nextFloat() * 0.4f);
                }
            } else {
                velocity = velocity.multiply(0.55, 0, 0.55);
                if (velocity.lengthSqr() < 1.0E-5) {
                    velocity = Vec3.ZERO;
                }
            }
        } else if (horizontalCollision) {
            velocity = velocity.multiply(-0.3, 1, -0.3);
        }
        setDeltaMovement(velocity);
        if (!level().isClientSide) {
            age++;
            if (age > ImtConfig.Guns.CASING_SECONDS * 20) {
                discard();
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("age", age);
    }
}
