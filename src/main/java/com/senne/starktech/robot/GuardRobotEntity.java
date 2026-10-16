package com.senne.starktech.robot;

import com.senne.starktech.bot.BotBody;
import com.senne.starktech.bot.BotEntity;
import com.senne.starktech.bot.BotMove;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.logic.program.ProgramKind;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.registry.ModSounds;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Een guard robot (hoofdstuk 21): een robot die je zelf samenstelt uit een hoofd, een lijf, armen en
 * benen, elk in een van vier stijlen (zie RobotParts). De onderdelen bepalen hoe hij eruitziet en een
 * beetje wat hij kan: het lijf zijn leven en pantser, de benen hoe snel hij loopt.
 *
 * Zolang hij geen programma heeft staat hij stil en kijkt hij rond; met een programma (hoofdstuk 23) doet hij
 * wat dat zegt (zie BotBrain), en kijkt hij recht voor zich uit.
 *
 * Hij heeft een batterij (hoe groot: zijn lijf). Zijn thuis is een Robot Dock: daar laadt hij via het
 * stroomnet. Zet je hem uit, dan loopt hij naar zijn dock en blijft hij daar staan laden; zet je hem aan
 * terwijl hij in zijn dock staat, dan stapt hij er naar voren uit. Wat hij deelt met een security drone
 * (batterij, printplaat, aan en uit, neer, eigenaar, opslaan) staat in BotEntity.
 */
public class GuardRobotEntity extends BotEntity {
    /** De onderdelen, ingepakt in één getal (zie RobotParts.pack): je spel heeft ze nodig om hem te tekenen. */
    private static final EntityDataAccessor<Integer> PARTS =
            SynchedEntityData.defineId(GuardRobotEntity.class, EntityDataSerializers.INT);
    /** Hoe hoog de plaat van een Robot Dock is: daar staat hij op. */
    private static final double DOCK_FLOOR = 3.0 / 16.0;

    /**
     * Alleen in je spel, voor het voorbeeld in de Robot Assembler: welke onderdelen we tekenen (bit per
     * RobotSlot). Een onderdeel dat nog niet in de assembler ligt, tekenen we niet.
     */
    private int shownSlots = 0b1111;
    /** Waar hij naartoe stapt als hij in zijn dock aangezet wordt (daarna null). */
    private @Nullable BlockPos stepOut;

    public GuardRobotEntity(EntityType<? extends GuardRobotEntity> type, Level level) {
        super(type, level);
        applyParts();
        setHealth(getMaxHealth());
        battery = maxBattery();
    }

    /**
     * De standaardwaarden; de echte komen van zijn onderdelen (zie {@link #applyParts}). Een stap omhoog
     * van één blok kan hij zonder te springen.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    /** Een robot loopt (zie BotMove). */
    @Override
    protected BotBody createBody() {
        return new BotMove(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PARTS, RobotParts.DEFAULT.pack());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new ReturnToDockGoal());
        goalSelector.addGoal(1, new StepOutGoal());
        // Geen programma: als hij aan staat, blijft hij staan en kijkt hij wie er langskomt. (Met een programma
        // kijkt hij recht voor zich uit: dat is de kant waar forward() heen gaat.)
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f) {
            @Override
            public boolean canUse() {
                return state() == RobotState.ACTIVE && !brain.hasProgram() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return state() == RobotState.ACTIVE && !brain.hasProgram() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return state() == RobotState.ACTIVE && !brain.hasProgram() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return state() == RobotState.ACTIVE && !brain.hasProgram() && super.canContinueToUse();
            }
        });
    }

    // ================================================================ Onderdelen

    /** Waaruit hij bestaat. */
    public RobotParts parts() {
        return RobotParts.unpack(entityData.get(PARTS));
    }

    /** Andere onderdelen: zijn leven, pantser en snelheid veranderen mee (zijn leven blijft even vol als het was). */
    public void setParts(RobotParts parts) {
        float fraction = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 1.0f;
        entityData.set(PARTS, parts.pack());
        applyParts();
        setHealth(fraction * getMaxHealth());
    }

    /** Zet leven, pantser en snelheid op wat zijn onderdelen geven (de getallen uit de config). */
    private void applyParts() {
        RobotParts parts = parts();
        setBase(Attributes.MAX_HEALTH, parts.health());
        setBase(Attributes.ARMOR, parts.armor());
        setBase(Attributes.MOVEMENT_SPEED, parts.speed());
        if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** Welke onderdelen je spel tekent (zie {@link #shownSlots}). */
    public boolean shows(RobotSlot slot) {
        return (shownSlots & 1 << slot.ordinal()) != 0;
    }

    /** Voor het voorbeeld in de Robot Assembler: teken alleen deze onderdelen (bit per RobotSlot). */
    public void setShownSlots(int mask) {
        shownSlots = mask;
    }

    /** Neem over wat er in een Guard Robot-item zit: onderdelen, printplaat, naam, batterij en programma (zie GuardRobotItem). */
    public void applyData(RobotData data) {
        setParts(data.robotParts());
        setHealth(getMaxHealth());
        setBoard(data.board());
        setCustomName(data.name().isEmpty() ? null : Component.literal(data.name()));
        battery = Math.max(0, Math.min(1, data.battery())) * maxBattery();
        brain.setProgram(data.program().isEmpty() ? null : LogicProgram.load(data.program()));
        setAmmo(data.ammo());
        updateState(false);
    }

    /** Wat er in zijn item komt als je hem oppakt (zijn programma en kogels gaan mee). */
    public RobotData toData() {
        return new RobotData(parts().pack(), board(), hasCustomName() ? getCustomName().getString() : "",
                (float) (battery / Math.max(1.0, maxBattery())), brain.program() == null ? new CompoundTag() : brain.program().save(),
                ammo());
    }

    // ================================================================ Batterij en zijn dock

    /** Hoeveel stroom er hoogstens in zijn batterij past (van zijn lijf). */
    @Override
    public double maxBattery() {
        return parts().battery();
    }

    /** Aangezet in zijn dock: hij stapt er naar voren uit. Uitgezet: hij loopt terug (zie ReturnToDockGoal). */
    @Override
    protected void switched(boolean on) {
        stepOut = null;
        if (on) {
            stepOutOfDock();
        }
    }

    /** Staat hij in zijn dock, dan stapt hij er naar voren uit (als hij aangezet wordt, of klaar is met laden). */
    public void stepOutOfDock() {
        if (isOnDock() && level().getBlockState(home).getBlock() instanceof RobotDockBlock) {
            Direction front = level().getBlockState(home).getValue(RobotDockBlock.FACING);
            stepOut = home.relative(front, Math.max(1, Math.round(StarkConfig.Robots.STEP_OUT)));
        }
    }

    /** Is hij nog uit zijn dock aan het stappen? */
    public boolean isSteppingOut() {
        return stepOut != null;
    }

    // ================================================================ Wat elke Bot heeft (hoofdstuk 25)

    @Override
    public ProgramKind programKind() {
        return ProgramKind.ROBOT;
    }

    @Override
    protected String noun() {
        return "a guard robot";
    }

    @Override
    protected String kindWord() {
        return "robot";
    }

    /** Opgepakt: een Guard Robot-item met alles erop en eraan (zie GuardRobotItem). */
    @Override
    public ItemStack toItem() {
        return GuardRobotItem.create(toData());
    }

    /** Opgepakt: zijn Robot Dock is weer vrij. */
    @Override
    public void leaveHome() {
        if (home != null && level().getBlockEntity(home) instanceof RobotDockBlockEntity dock && getUUID().equals(dock.robot())) {
            dock.setRobot(null);
        }
    }

    @Override
    public boolean isLeavingDock() {
        return isSteppingOut();
    }

    /** Hoe ver hij kijkt: van zijn hoofd. */
    @Override
    public double scanRange() {
        return parts().scanRange();
    }

    @Override
    public double scanAngle() {
        return parts().scanAngle();
    }

    /** Hoe snel hij opnieuw schiet: van zijn armen. */
    @Override
    public double fireInterval() {
        return parts().fireInterval();
    }

    /** De laser zit aan zijn rechterarm, het geweer aan zijn linker. */
    @Override
    public Vec3 muzzle(boolean laser, Vec3 forward) {
        Vec3 side = new Vec3(-forward.z, 0, forward.x); // naar rechts, gezien vanuit de robot
        return position().add(0, 1.15, 0).add(forward.scale(0.45)).add(side.scale(laser ? 0.38 : -0.38));
    }

    /** Neergeschoten: hij stapt niet meer uit zijn dock. */
    @Override
    protected void knockedDown() {
        stepOut = null;
    }

    /** Staat hij precies in het midden van zijn dock (na het netjes gaan staan)? */
    public boolean isSettled() {
        return isOnDock() && flatDistance(home) <= 0.001;
    }

    /** Is zijn Robot Dock er nog, en woont hij daar nog? */
    @Override
    protected boolean homeIsMine(ServerLevel level) {
        return level.getBlockEntity(home) instanceof RobotDockBlockEntity dock && getUUID().equals(dock.robot());
    }

    @Override
    protected double drainOn() {
        return StarkConfig.Robots.DRAIN_ON;
    }

    @Override
    protected double drainMoving() {
        return StarkConfig.Robots.DRAIN_WALKING;
    }

    @Override
    protected double dockChargeRate() {
        return StarkConfig.Robots.DOCK_CHARGE_RATE;
    }

    /** Loopt hij (dat kost extra stroom)? */
    @Override
    protected boolean isMoving() {
        return getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 || !getNavigation().isDone() || getMoveControl().hasWanted();
    }

    /** Uit (of hij wil laden) en vlak bij zijn dock: ga er netjes in staan. */
    @Override
    protected void dockTick(ServerLevel level) {
        if ((!active || brain.wantsDock()) && downUntil == 0 && home != null && closeToHome() && flatDistance(home) > 0.001) {
            settleInDock(level);
        }
    }

    /** Staat hij vlak bij het midden van zijn dock (om er netjes in te gaan staan)? */
    private boolean closeToHome() {
        return home != null && Math.abs(getY() - (home.getY() + DOCK_FLOOR)) < 0.6
                && Vec3.atBottomCenterOf(home).distanceToSqr(getX(), home.getY(), getZ()) < 0.6 * 0.6;
    }

    /** Ga precies in het midden van het dock staan, met je gezicht naar de open kant. */
    private void settleInDock(ServerLevel level) {
        BlockState dock = level.getBlockState(home);
        float yaw = dock.getBlock() instanceof RobotDockBlock ? dock.getValue(RobotDockBlock.FACING).toYRot() : getYRot();
        getNavigation().stop();
        moveTo(home.getX() + 0.5, home.getY() + DOCK_FLOOR, home.getZ() + 0.5, yaw, 0);
        setYHeadRot(yaw);
        yBodyRot = yaw;
        setDeltaMovement(Vec3.ZERO);
    }

    /** Het blok vóór zijn dock (aan de open kant), of het dock zelf als daar geen dock meer staat. */
    private BlockPos frontOfHome() {
        BlockState dock = level().getBlockState(home);
        return dock.getBlock() instanceof RobotDockBlock ? home.relative(dock.getValue(RobotDockBlock.FACING)) : home;
    }

    /** Hoe ver (in het platte vlak) hij van het midden van dit blok staat. */
    private double flatDistance(BlockPos pos) {
        double dx = getX() - (pos.getX() + 0.5);
        double dz = getZ() - (pos.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Uitgezet (of zijn programma zegt {@code charge()}): loop terug naar je dock (geen dock of lege batterij: blijf
     * staan). Eerst met de gewone padzoeker naar het blok vóór het dock, dan het laatste stukje rechtdoor erin: van
     * en naar een plek op de lage plaat van het dock vindt de padzoeker geen weg.
     */
    private class ReturnToDockGoal extends Goal {
        ReturnToDockGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return (!active || brain.wantsDock()) && downUntil == 0 && home != null && battery > 0 && !closeToHome() && onGround();
        }

        @Override
        public boolean canContinueToUse() {
            return (!active || brain.wantsDock()) && downUntil == 0 && home != null && battery > 0 && !closeToHome();
        }

        @Override
        public void start() {
            walkToFront();
        }

        @Override
        public void tick() {
            BlockPos front = frontOfHome();
            boolean level = Math.abs(getY() - front.getY()) < 1.2;
            if (level && (flatDistance(front) < 0.9 || flatDistance(home) < 1.2 && !front.equals(home))) {
                // Het laatste stukje: rechtdoor het dock in.
                getNavigation().stop();
                getMoveControl().setWantedPosition(home.getX() + 0.5, home.getY() + DOCK_FLOOR, home.getZ() + 0.5, 0.7);
            } else if (level && flatDistance(front) < 2.5 && getNavigation().isDone()) {
                // Vlakbij: voor de padzoeker is een blok ernaast al "aangekomen". Dan zelf naar het blok vóór het dock.
                getMoveControl().setWantedPosition(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, 1.0);
            } else if (getNavigation().isDone() && tickCount % 20 == 0) {
                walkToFront(); // de weg was op of werd niet gevonden: opnieuw proberen
            }
        }

        private void walkToFront() {
            BlockPos front = frontOfHome();
            getNavigation().moveTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, 1.0);
        }
    }

    /** Aangezet in zijn dock: stap er rechtdoor naar voren uit (hooguit 4 seconden, dan blijft hij staan). */
    private class StepOutGoal extends Goal {
        private int ticks;

        StepOutGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return active && stepOut != null && battery > 0 && downUntil == 0 && onGround();
        }

        @Override
        public boolean canContinueToUse() {
            return active && stepOut != null && battery > 0 && downUntil == 0 && ticks < 80 && flatDistance(stepOut) > 0.25;
        }

        @Override
        public void start() {
            ticks = 0;
        }

        @Override
        public void tick() {
            ticks++;
            getMoveControl().setWantedPosition(stepOut.getX() + 0.5, stepOut.getY(), stepOut.getZ() + 0.5, 1.0);
        }

        @Override
        public void stop() {
            stepOut = null;
        }
    }

    /** Uit een spawn-ei (creative) komt een robot met willekeurige onderdelen, en hij staat meteen aan. */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        if (spawnType == MobSpawnType.SPAWN_EGG) {
            setParts(RobotParts.random(level.getRandom()));
            setHealth(getMaxHealth());
            battery = maxBattery();
            setActive(true);
        }
        return result;
    }

    /**
     * Echt kapot (zijn leven is op): elk onderdeel blijft met een kans heel en valt op de grond (zie
     * PART_SURVIVE_CHANCE in de config). Wat kapot gaat, blijft kapot.
     */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        RobotParts parts = parts();
        for (RobotSlot slot : RobotSlot.values()) {
            if (random.nextFloat() < StarkConfig.Robots.PART_SURVIVE_CHANCE) {
                spawnAtLocation(new ItemStack(ModItems.robotPart(slot, parts.get(slot))));
            }
        }
    }

    // ================================================================ Opslaan

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Parts", parts().pack());
    }

    /** Zijn onderdelen eerst: daar hangen zijn leven, pantser, snelheid en batterij aan. */
    @Override
    protected void readBody(CompoundTag tag) {
        if (tag.contains("Parts")) {
            entityData.set(PARTS, tag.getInt("Parts"));
        }
        applyParts();
    }

    // ================================================================ Geluiden

    /** Geraakt: een metalen klang en vonkjes. */
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ROBOT_HURT.get();
    }

    /** Kapot: hij zakt uit (de zoem zakt weg, een doffe klonk). */
    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ROBOT_POWER_OFF.get();
    }

    /** Een stap: een servo-ritje en een licht metalen tikje (niet zo zwaar als een ijzergolem). */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.ROBOT_STEP.get(), 0.35f, 0.95f + random.nextFloat() * 0.1f);
    }
}
