package com.senne.starktech.robot;

import com.senne.starktech.arsenal.DroneDockBlock;
import com.senne.starktech.arsenal.DroneDockBlockEntity;
import com.senne.starktech.bot.BotBody;
import com.senne.starktech.bot.BotEntity;
import com.senne.starktech.bot.DroneMove;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.logic.program.ProgramKind;
import com.senne.starktech.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Een security drone (hoofdstuk 25, jouw antwoord: "een security drone bouw je zelf, met een printplaat"). Hij is
 * een vliegende guard robot: dezelfde printplaat met 8 pinnen en dezelfde modules, dezelfde batterij, aan en uit,
 * neer na de Ion Blaster, en een programma uit de app Logic (een droneprogramma, met {@code up(n)} en
 * {@code down(n)} erbij; zie DroneMove). Wat hij deelt met een robot staat in BotEntity.
 *
 * Zijn thuis is een Drone Dock: daar laadt hij via het stroomnet. Zet je hem aan terwijl hij op zijn dock staat,
 * dan stijgt hij op ({@code DRONE_TAKE_OFF} blokken); zet je hem uit, dan vliegt hij terug en landt hij erop.
 *
 * Hij hangt altijd in het midden van een blok, een stukje boven de bodem ({@link #HOVER}): zo vliegt hij blok voor
 * blok, net zoals een robot blok voor blok loopt. Is zijn batterij leeg, of ligt hij neer (de Ion Blaster), dan
 * valt hij naar de grond (zonder schade: hij is ervoor gebouwd).
 */
public class GuardDroneEntity extends BotEntity {
    /** Zo hoog boven de bodem van een blok hangt hij (blokken). */
    public static final double HOVER = 0.3;
    /** Hoe hoog de plaat van een Drone Dock is: daar landt hij op. */
    private static final double DOCK_FLOOR = 4.0 / 16.0;

    /** Waar hij heen stijgt als hij van zijn dock opstijgt (daarna null). */
    private @Nullable BlockPos takeOff;
    private int takeOffTicks;

    public GuardDroneEntity(EntityType<? extends GuardDroneEntity> type, Level level) {
        super(type, level);
        moveControl = new DroneMoveControl(this);
        setNoGravity(true);
        battery = maxBattery();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, StarkConfig.Robots.DRONE_HEALTH)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    /** Een drone vliegt (zie DroneMove). */
    @Override
    protected BotBody createBody() {
        return new DroneMove(this);
    }

    /** De padzoeker voor vliegers: om muren heen, ook omhoog en omlaag. */
    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    // ================================================================ Wat hij is

    @Override
    public ProgramKind programKind() {
        return ProgramKind.DRONE;
    }

    @Override
    protected String noun() {
        return "a security drone";
    }

    @Override
    protected String kindWord() {
        return "drone";
    }

    /** Opgepakt: een Security Drone-item met alles erop en eraan (zie GuardDroneItem). */
    @Override
    public ItemStack toItem() {
        return GuardDroneItem.create(toData());
    }

    /** Neem over wat er in een Security Drone-item zit: printplaat, naam, batterij, programma en kogels. */
    public void applyData(DroneData data) {
        setHealth(getMaxHealth());
        setBoard(data.board());
        setCustomName(data.name().isEmpty() ? null : Component.literal(data.name()));
        battery = Math.max(0, Math.min(1, data.battery())) * maxBattery();
        brain.setProgram(data.program().isEmpty() ? null : LogicProgram.load(data.program()));
        setAmmo(data.ammo());
        updateState(false);
    }

    /** Wat er in zijn item komt als je hem oppakt. */
    public DroneData toData() {
        return new DroneData(board(), hasCustomName() ? getCustomName().getString() : "",
                (float) (battery / Math.max(1.0, maxBattery())), brain.program() == null ? new CompoundTag() : brain.program().save(),
                ammo());
    }

    /** Opgepakt: zijn Drone Dock is weer vrij. */
    @Override
    public void leaveHome() {
        if (home != null && level().getBlockEntity(home) instanceof DroneDockBlockEntity dock && getUUID().equals(dock.bot())) {
            dock.setBot(null);
        }
    }

    @Override
    public double maxBattery() {
        return StarkConfig.Robots.DRONE_BATTERY;
    }

    @Override
    public double scanRange() {
        return StarkConfig.Robots.DRONE_SCAN_RANGE;
    }

    @Override
    public double scanAngle() {
        return StarkConfig.Robots.DRONE_SCAN_ANGLE;
    }

    @Override
    public double fireInterval() {
        return StarkConfig.Robots.DRONE_FIRE_INTERVAL;
    }

    /** De laser hangt rechts onder zijn buik, het geweer links. */
    @Override
    public Vec3 muzzle(boolean laser, Vec3 forward) {
        Vec3 side = new Vec3(-forward.z, 0, forward.x); // naar rechts, gezien vanuit de drone
        return position().add(0, 0.05, 0).add(forward.scale(0.4)).add(side.scale(laser ? 0.15 : -0.15));
    }

    /** Zijn afstandssensor zit vooraan, midden op zijn romp. */
    @Override
    public double sensorHeight() {
        return 0.25;
    }

    // ================================================================ Vliegen

    /** Waar hij hangt als hij in dit blok is: in het midden, {@link #HOVER} boven de bodem. */
    public Vec3 hoverSpot(BlockPos block) {
        return new Vec3(block.getX() + 0.5, block.getY() + HOVER, block.getZ() + 0.5);
    }

    /** Past hij in dit blok (zonder ergens tegenaan te zitten)? */
    public boolean fits(BlockPos block) {
        AABB box = getDimensions(getPose()).makeBoundingBox(hoverSpot(block)).deflate(0.02);
        return level().noCollision(this, box);
    }

    /**
     * Eén tick richting deze plek, met hooguit deze snelheid (blokken per tick). Geeft true als hij er is: dan hangt
     * hij er precies, stil.
     */
    public boolean flyTo(Vec3 spot, double speed) {
        Vec3 delta = spot.subtract(position());
        double distance = delta.length();
        if (distance < 0.06) {
            setPos(spot);
            setDeltaMovement(Vec3.ZERO);
            return true;
        }
        setDeltaMovement(delta.scale(Math.min(distance, speed) / distance));
        return false;
    }

    /** Zijn neus (en lijf) precies deze kant op. */
    public void setYaw(float yaw) {
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    // ================================================================ Zijn dock

    /** Aangezet op zijn dock: hij stijgt op. Uitgezet: hij vliegt terug (zie {@link #dockTick}). */
    @Override
    protected void switched(boolean on) {
        takeOff = null;
        if (on) {
            takeOff();
        }
    }

    /** Staat hij op zijn dock, dan stijgt hij op (als hij aangezet wordt, of klaar is met laden). */
    public void takeOff() {
        if (isOnDock()) {
            BlockPos up = home.above(Math.max(1, Math.round(StarkConfig.Robots.DRONE_TAKE_OFF)));
            takeOff = up;
            takeOffTicks = 0;
        }
    }

    /** Stijgt hij nog op van zijn dock? Dan begint zijn programma pas daarna. */
    @Override
    public boolean isLeavingDock() {
        return takeOff != null;
    }

    /** Neergeschoten: hij stijgt niet meer op (hij valt). */
    @Override
    protected void knockedDown() {
        takeOff = null;
    }

    /** Staat hij netjes op zijn dock (na het landen)? */
    public boolean isSettled() {
        return isOnDock() && flatDistance(home) <= 0.01 && Math.abs(getY() - (home.getY() + DOCK_FLOOR)) <= 0.01;
    }

    /** Is zijn Drone Dock er nog, en is het nog het zijne? */
    @Override
    protected boolean homeIsMine(ServerLevel level) {
        return level.getBlockEntity(home) instanceof DroneDockBlockEntity dock && getUUID().equals(dock.bot());
    }

    @Override
    protected double drainOn() {
        return StarkConfig.Robots.DRONE_DRAIN_ON;
    }

    @Override
    protected double drainMoving() {
        return StarkConfig.Robots.DRONE_DRAIN_FLYING;
    }

    @Override
    protected double dockChargeRate() {
        return StarkConfig.Robots.DRONE_DOCK_CHARGE_RATE;
    }

    /** Vliegt hij (dat kost extra stroom)? */
    @Override
    protected boolean isMoving() {
        return getDeltaMovement().lengthSqr() > 1.0E-4 || !getNavigation().isDone();
    }

    /**
     * Elke tick: zonder stroom of neergeschoten valt hij; anders stijgt hij op van zijn dock (net aangezet), of
     * vliegt hij terug en landt hij erop (uitgezet, of zijn programma zegt {@code charge()}).
     */
    @Override
    protected void dockTick(ServerLevel level) {
        boolean flying = battery > 0 && downUntil == 0;
        if (isNoGravity() != flying) {
            setNoGravity(flying);
        }
        if (!flying) {
            takeOff = null;
            getNavigation().stop();
            return;
        }
        double speed = StarkConfig.Robots.DRONE_SPEED;
        if (takeOff != null) {
            if (flyTo(hoverSpot(takeOff), speed) || ++takeOffTicks > 80) {
                takeOff = null;
            }
            return;
        }
        if ((active && !brain.wantsDock()) || home == null) {
            return;
        }
        if (isSettled()) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 above = hoverSpot(home.above());
        if (flatDistance(home) < 0.08 && getY() <= above.y + 0.1) {
            // Recht boven zijn dock: zakken tot hij erop staat, met zijn neus naar de open kant.
            getNavigation().stop();
            if (flyTo(new Vec3(home.getX() + 0.5, home.getY() + DOCK_FLOOR, home.getZ() + 0.5), speed * 0.6)) {
                BlockState dock = level.getBlockState(home);
                if (dock.getBlock() instanceof DroneDockBlock) {
                    setYaw(dock.getValue(DroneDockBlock.FACING).toYRot());
                }
            }
        } else if (position().distanceTo(above) < 2.5) {
            // Vlakbij: zelf naar het blok boven het dock (voor de padzoeker is "ernaast" al aangekomen).
            getNavigation().stop();
            flyTo(above, speed);
        } else {
            if (getNavigation().isDone() && tickCount % 20 == 0) {
                getNavigation().moveTo(above.x, above.y, above.z, 1.0);
            }
            if (getNavigation().isDone()) {
                flyTo(above, speed); // (nog) geen weg: dan rechtdoor
            }
        }
    }

    /** Hoe ver (in het platte vlak) hij van het midden van dit blok hangt. */
    private double flatDistance(BlockPos pos) {
        double dx = getX() - (pos.getX() + 0.5);
        double dz = getZ() - (pos.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ================================================================ Als wezen

    @Override
    public void tick() {
        super.tick();
        com.senne.starktech.drone.DroneDamage.particles(this);
    }

    /** Vallen doet hem niets (zonder stroom zakt hij naar de grond). */
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    /** Klem tegen een muur doet hem niets (hij hangt soms vlak tegen een plafond). */
    @Override
    public boolean hurt(DamageSource source, float damage) {
        return !source.is(DamageTypes.IN_WALL) && super.hurt(source, damage);
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.DRONE_HURT.get();
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return ModSounds.DRONE_DESTROYED.get();
    }

    // ================================================================ Opslaan

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (takeOff != null) {
            tag.putLong("TakeOff", takeOff.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        takeOff = tag.contains("TakeOff") ? BlockPos.of(tag.getLong("TakeOff")) : null;
    }

    /**
     * Vliegen zonder paden te zoeken (de padzoeker geeft de punten, deze besturing vliegt er recht naartoe), met zijn
     * neus naar waar hij heen vliegt.
     */
    private static class DroneMoveControl extends MoveControl {
        private final GuardDroneEntity drone;

        DroneMoveControl(GuardDroneEntity drone) {
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
            if (length < 1.0e-3) {
                drone.setDeltaMovement(Vec3.ZERO);
                return;
            }
            double speed = Math.min(length, speedModifier * StarkConfig.Robots.DRONE_SPEED);
            drone.setDeltaMovement(delta.scale(speed / length));
            if (delta.horizontalDistanceSqr() > 0.01) {
                float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
                drone.setYaw(rotlerp(drone.getYRot(), yaw, 20.0f));
            }
        }
    }
}
