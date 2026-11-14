package com.senne.imttech.guns;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModDamageTypes;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.security.zone.Zones;
import com.senne.imttech.shield.ImtDomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Een kogel (hoofdstuk 52, jouw keuze: "de kogels moeten een vliegtijd hebben, kogels vliegen heel snel maar moeten
 * niet instant hitten of missen"). Hij vliegt {@code [guns] bulletSpeed} blokken per tick (16: ongeveer 300 meter per
 * seconde), zakt een heel klein beetje, en kijkt elke tick langs zijn hele stukje baan wat hij raakt (muren en wezens),
 * dus hij vliegt nooit door iets heen. Dichtbij doet hij zijn volle schade; voorbij {@code fullRange} loopt die af
 * tot {@code farDamage} op zijn verste punt. In het hoofd: twee keer zoveel. Een schildkoepel houdt hem tegen, net
 * als de lasers. Glas breekt hij (jouw mod: alleen buiten een beschermde zone).
 */
public class BulletEntity extends Projectile {
    private float damage;
    private double fullRange;
    private double range;
    private double travelled;
    private int life;

    public BulletEntity(EntityType<? extends BulletEntity> type, Level level) {
        super(type, level);
        noCulling = true;
    }

    /** Een kogel uit dit pistool, vanaf de ogen van de schutter. */
    /** Zoveel schade doet deze kogel van dichtbij (voor de tests: een demper remt hem een tikje af). */
    public float damage() {
        return damage;
    }

    public static BulletEntity shoot(ServerLevel level, LivingEntity shooter, FirearmType type, float damage, Vec3 direction) {
        BulletEntity bullet = new BulletEntity(ModEntities.BULLET.get(), level);
        bullet.setOwner(shooter);
        bullet.damage = damage;
        bullet.fullRange = type.fullRange();
        bullet.range = type.range();
        Vec3 eye = shooter.getEyePosition();
        // Een stukje voor de ogen (de loop), zodat de kogel niet in je eigen hoofd begint.
        Vec3 start = eye.add(direction.scale(0.6));
        bullet.setPos(start.x, start.y, start.z);
        bullet.setDeltaMovement(direction.normalize().scale(type.bulletSpeed()));
        bullet.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        bullet.setXRot((float) Math.toDegrees(-Math.asin(Mth.clamp(direction.y, -1, 1))));
        bullet.yRotO = bullet.getYRot();
        bullet.xRotO = bullet.getXRot();
        level.addFreshEntity(bullet);
        return bullet;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // Niets te delen: hoe hij vliegt, zie je aan zijn beweging.
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 velocity = getDeltaMovement();
        Vec3 from = position();
        Vec3 to = from.add(velocity);
        if (level() instanceof ServerLevel level) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
            // Een schildkoepel om het doel (niet die van de schutter zelf) houdt de kogel tegen.
            Vec3 shield = ImtDomes.blockedAt(level, end, from, getOwner() == null ? null : getOwner().getUUID(),
                    Math.round(ImtConfig.Dome.COST_LASER));
            if (shield != null) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, shield.x, shield.y, shield.z, 6, 0.1, 0.1, 0.1, 0.05);
                discard();
                return;
            }
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                return;
            }
        }
        setPos(to.x, to.y, to.z);
        travelled += velocity.length();
        life++;
        // Een heel klein beetje zwaartekracht en luchtweerstand.
        setDeltaMovement(velocity.add(0, -ImtConfig.Guns.BULLET_GRAVITY, 0).scale(0.995));
        updateRotation(); // van Projectile: de kogel wijst in zijn vliegrichting
        if (life > 100 || travelled > range) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity.isAlive() && !entity.isSpectator() && entity != getOwner();
    }

    /** Hoeveel van de volle schade er nog over is op deze afstand. */
    private float falloff() {
        if (travelled <= fullRange || range <= fullRange) {
            return 1;
        }
        float t = (float) Mth.clamp((travelled - fullRange) / (range - fullRange), 0, 1);
        return Mth.lerp(t, 1, ImtConfig.Guns.FAR_DAMAGE);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = hit.getEntity();
        float amount = damage * falloff();
        if (entity instanceof LivingEntity target) {
            // Waar de kogel het wezen raakt: alles boven de ogen min een klein stukje telt als het hoofd.
            Vec3 at = entity.getBoundingBox().inflate(0.1).clip(position(), position().add(getDeltaMovement()))
                    .orElse(hit.getLocation());
            boolean head = at.y >= target.getEyeY() - 0.25;
            if (head) {
                amount *= ImtConfig.Guns.HEADSHOT;
            }
            // Meteen: geen "even onkwetsbaar", anders doet een snel tweede schot niets.
            target.invulnerableTime = 0;
            // Hoofdstuk 109: de schade weet of hij in het hoofd zat, want een headshot gaat langs de plates van een vest.
            target.hurt(com.senne.imttech.armor.HitSource.of(ModDamageTypes.bullet(level, getOwner()), head), amount);
            GunSkillsServer.hit(getOwner());
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.1);
        } else {
            entity.hurt(ModDamageTypes.bullet(level, getOwner()), amount);
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        Vec3 at = hit.getLocation();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 8, 0.05, 0.05, 0.05, 0.1);
        level.playSound(null, at.x, at.y, at.z, ModSounds.BULLET_IMPACT.get(), SoundSource.NEUTRAL, 0.7f,
                0.9f + random.nextFloat() * 0.2f);
        // Glas gaat kapot (jouw mod: nooit in een zone die het tegenhoudt, en nooit verstevigd glas).
        if (ImtConfig.Guns.BULLETS_BREAK_GLASS && (state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES))
                && !Zones.denies(level, pos, getOwner()) && !com.senne.imttech.breach.Cracks.isReinforced(level, pos)) {
            level.destroyBlock(pos, false, getOwner());
        }
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("damage", damage);
        tag.putDouble("fullRange", fullRange);
        tag.putDouble("range", range);
        tag.putDouble("travelled", travelled);
        tag.putInt("life", life);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("damage");
        fullRange = tag.getDouble("fullRange");
        range = tag.getDouble("range");
        travelled = tag.getDouble("travelled");
        life = tag.getInt("life");
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }
}
