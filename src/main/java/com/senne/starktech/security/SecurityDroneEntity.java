package com.senne.starktech.security;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.network.BeamEffectPayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.registry.ModDamageTypes;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.shield.EdithDomes;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Een beveiligingsdrone van de Stark Tower.
 *
 * Hij hoort bij een beveiligingskern (zijn "thuis") en patrouilleert daaromheen. Ziet hij een
 * indringer (zie SecurityCores), dan gaat hij erop af en schiet hij lasers, net als een
 * raiddrone. Maar hij sloopt geen blokken (het is zijn eigen toren), en hij verlaat de toren niet.
 *
 * Het is geen "monster": zo schieten je eigen torentjes en escortedrones hem niet uit zichzelf
 * neer als de toren eenmaal van jou is. Valt hij jou aan (voor je de toren hebt), dan vechten
 * je escortedrones wel terug.
 *
 * Zijn lampjes zijn rood zolang de toren van niemand is, en blauw als hij van iemand is.
 */
public class SecurityDroneEntity extends PathfinderMob {
    /** Is de toren van iemand? (Voor de kleur van de lampjes in je spel.) */
    private static final EntityDataAccessor<Boolean> CLAIMED =
            SynchedEntityData.defineId(SecurityDroneEntity.class, EntityDataSerializers.BOOLEAN);
    /** Heeft zijn kern stroom? Zonder stroom zakt hij naar de grond en gaan zijn lampjes uit. */
    private static final EntityDataAccessor<Boolean> OFFLINE =
            SynchedEntityData.defineId(SecurityDroneEntity.class, EntityDataSerializers.BOOLEAN);

    private @Nullable BlockPos home;
    private int fireCooldown;
    private int patrolCooldown;
    /** Waar hij tijdens het patrouilleren heen vliegt. */
    private @Nullable Vec3 patrolPoint;

    public SecurityDroneEntity(EntityType<? extends SecurityDroneEntity> type, Level level) {
        super(type, level);
        this.moveControl = new SecurityMoveControl(this);
        this.setNoGravity(true);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, StarkConfig.Security.DRONE_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CLAIMED, false);
        builder.define(OFFLINE, false);
    }

    /** Staat hij uit (geen stroom bij zijn kern)? */
    public boolean isOffline() {
        return entityData.get(OFFLINE);
    }

    public boolean isClaimed() {
        return entityData.get(CLAIMED);
    }

    public @Nullable BlockPos home() {
        return home;
    }

    public void setHome(BlockPos home) {
        this.home = home.immutable();
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        com.senne.starktech.drone.DroneDamage.particles(this);
    }

    // ================================================================ Denken

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (fireCooldown > 0) {
            fireCooldown--;
        }
        SecurityCoreBlockEntity core = home == null ? null : SecurityCores.coreAt(level, home);
        entityData.set(CLAIMED, core != null && core.ownerId() != null);
        // Geen stroom bij de kern: niets doen, langzaam naar de grond zakken.
        boolean offline = core != null && !core.isOnline();
        // De eigenaar zette de drones uit (op de desktop): naar de kern vliegen en daar landen.
        parked = !offline && core != null && !core.dronesEnabled();
        entityData.set(OFFLINE, offline || parked);
        if (parked) {
            setTarget(null);
            land();
            return;
        }
        if (offline) {
            setTarget(null);
            getNavigation().stop();
            if (!onGround()) {
                setDeltaMovement(getDeltaMovement().multiply(0.5, 0, 0.5).add(0, -0.06, 0));
            }
            return;
        }

        LivingEntity target = getTarget();
        if (target == null || !stillATarget(level, core, target)) {
            target = findTarget(level, core);
            setTarget(target);
        }
        if (target != null) {
            attack(level, target);
        } else {
            patrol();
        }
    }

    /** Staan de drones van zijn kern uit (dan landt hij naast de kern)? Alleen op de server. */
    private boolean parked;

    /** Naar een eigen plekje naast de kern vliegen, en daar zachtjes landen. */
    private void land() {
        double angle = Math.floorMod(getId(), 8) * Math.PI / 4;
        double x = home.getX() + 0.5 + Math.cos(angle) * 2.5;
        double z = home.getZ() + 0.5 + Math.sin(angle) * 2.5;
        double dx = x - getX();
        double dz = z - getZ();
        if (dx * dx + dz * dz > 1.0) {
            moveControl.setWantedPosition(x, home.getY() + 1.5, z, 0.5);
        } else if (!onGround()) {
            setDeltaMovement(getDeltaMovement().multiply(0.5, 0, 0.5).add(0, -0.06, 0));
        }
    }

    /** Wat hij nu doet, in een paar woorden (Engels; voor de Devices-app op de desktop). */
    public String statusText() {
        if (isOffline()) {
            return parked ? "landed (switched off)" : "no power";
        }
        return getTarget() != null ? "attacking" : "patrolling";
    }

    private boolean stillATarget(ServerLevel level, @Nullable SecurityCoreBlockEntity core, LivingEntity target) {
        double range = StarkConfig.Security.DRONE_RANGE * 1.5;
        return target.distanceToSqr(this) <= range * range && SecurityCores.isIntruder(level, core, target)
                && (home == null || SecurityCores.inArea(home, target.blockPosition()));
    }

    /** De dichtstbijzijnde indringer die hij kan zien. */
    private @Nullable LivingEntity findTarget(ServerLevel level, @Nullable SecurityCoreBlockEntity core) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(StarkConfig.Security.DRONE_RANGE),
                        entity -> entity != this && SecurityCores.isIntruder(level, core, entity)
                                && (home == null || SecurityCores.inArea(home, entity.blockPosition()))
                                && getSensing().hasLineOfSight(entity))
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    /** Boven en naast zijn doelwit hangen, eromheen cirkelen, en schieten als hij hem ziet. */
    private void attack(ServerLevel level, LivingEntity target) {
        Vec3 eye = target.getEyePosition();
        double angle = tickCount * 0.05;
        moveControl.setWantedPosition(eye.x + Math.cos(angle) * 4, eye.y + 1.5, eye.z + Math.sin(angle) * 4, 0.9);
        lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, eye);
        if (fireCooldown <= 0 && getSensing().hasLineOfSight(target)
                && distanceToSqr(target) <= StarkConfig.Security.DRONE_RANGE * StarkConfig.Security.DRONE_RANGE) {
            fireLaser(level, target);
            fireCooldown = Math.round(StarkConfig.Security.DRONE_FIRE_INTERVAL);
        }
    }

    private void fireLaser(ServerLevel level, LivingEntity target) {
        Vec3 from = position().add(0, getBbHeight() * 0.3, 0);
        Vec3 to = target.getBoundingBox().getCenter();
        Vec3 shield = EdithDomes.blockedAt(level, to, from, null, Math.round(StarkConfig.Dome.COST_LASER));
        playSound(ModSounds.DRONE_LASER.get(), 0.7f, 1.3f + random.nextFloat() * 0.1f);
        if (shield != null) {
            ModNetwork.sendNear(level, position(), 64, new BeamEffectPayload(from, shield, BeamEffectPayload.Kind.DRONE_LASER));
            return;
        }
        com.senne.starktech.training.Shots.hit(level, target, ModDamageTypes.droneLaser(level, this, this),
                StarkConfig.Security.DRONE_LASER_DAMAGE, "Patrol drone");
        ModNetwork.sendNear(level, position(), 64, new BeamEffectPayload(from, to, BeamEffectPayload.Kind.DRONE_LASER));
    }

    /** Niemand te zien: rustig van plek naar plek rond de kern vliegen. */
    private void patrol() {
        if (home == null) {
            moveControl.setWantedPosition(getX(), getY(), getZ(), 0.2);
            return;
        }
        if (--patrolCooldown <= 0 || patrolPoint == null) {
            patrolCooldown = 60 + random.nextInt(60);
            double r = StarkConfig.Security.PATROL_RADIUS;
            patrolPoint = new Vec3(home.getX() + 0.5 + (random.nextDouble() * 2 - 1) * r,
                    home.getY() + 1.5 + random.nextDouble() * 2,
                    home.getZ() + 0.5 + (random.nextDouble() * 2 - 1) * r);
        }
        // Elke tick opnieuw: de besturing geeft alleen een duwtje als je zegt waar hij heen moet.
        moveControl.setWantedPosition(patrolPoint.x, patrolPoint.y, patrolPoint.z, 0.4);
    }

    // ================================================================ Als wezen

    /** Geen schade van elkaar, van de laserwanden van zijn eigen toren, of van klem zitten. */
    @Override
    public boolean hurt(DamageSource source, float damage) {
        if (source.getEntity() instanceof SecurityDroneEntity || source.is(DamageTypes.IN_WALL)
                || source.is(ModDamageTypes.LASER_WALL)) {
            return false;
        }
        return super.hurt(source, damage);
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.DRONE_HURT.get();
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return ModSounds.DRONE_DESTROYED.get();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) {
            tag.put("Home", NbtUtils.writeBlockPos(home));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        home = NbtUtils.readBlockPos(tag, "Home").orElse(null);
    }

    /** Vliegen zonder paden te zoeken, zoals de raiddrone. */
    private static class SecurityMoveControl extends MoveControl {
        private final SecurityDroneEntity drone;

        SecurityMoveControl(SecurityDroneEntity drone) {
            super(drone);
            this.drone = drone;
        }

        @Override
        public void tick() {
            if (operation != Operation.MOVE_TO) {
                return;
            }
            operation = Operation.WAIT;
            Vec3 delta = new Vec3(wantedX - drone.getX(), wantedY - drone.getY(), wantedZ - drone.getZ());
            double length = delta.length();
            if (length < 0.3) {
                drone.setDeltaMovement(drone.getDeltaMovement().scale(0.5));
                return;
            }
            double speed = speedModifier * StarkConfig.Security.ACCELERATION * Math.min(1.0, length / 2.0);
            drone.setDeltaMovement(drone.getDeltaMovement().scale(0.9).add(delta.scale(speed / length)));
        }
    }
}
