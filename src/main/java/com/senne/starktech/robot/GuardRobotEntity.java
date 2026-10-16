package com.senne.starktech.robot;

import com.senne.starktech.security.BaseTrust;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

/**
 * Een guard robot (hoofdstuk 21): een robot die je zelf samenstelt uit een hoofd, een lijf, armen en
 * benen, elk in een van vier stijlen (zie RobotParts). De onderdelen bepalen hoe hij eruitziet en een
 * beetje wat hij kan: het lijf zijn leven en pantser, de benen hoe snel hij loopt.
 *
 * Hij is geen monster (dus je torentjes laten hem met rust) en verdwijnt nooit vanzelf. Zolang hij nog
 * geen programma heeft (hoofdstuk 23) staat hij stil en kijkt hij rond.
 *
 * Wie hem neerzette is zijn eigenaar; samen met wie in die basis vertrouwd is mag die hem instellen
 * (dezelfde regel als bij de laserzender, zie BaseTrust).
 */
public class GuardRobotEntity extends PathfinderMob implements BaseTrust.Placed {
    /** De onderdelen, ingepakt in één getal (zie RobotParts.pack): je spel heeft ze nodig om hem te tekenen. */
    private static final EntityDataAccessor<Integer> PARTS =
            SynchedEntityData.defineId(GuardRobotEntity.class, EntityDataSerializers.INT);

    private @Nullable UUID owner;
    private String ownerName = "";

    public GuardRobotEntity(EntityType<? extends GuardRobotEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        applyParts();
        setHealth(getMaxHealth());
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

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PARTS, RobotParts.DEFAULT.pack());
    }

    @Override
    protected void registerGoals() {
        // Nog geen programma: hij blijft staan en kijkt wie er langskomt.
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
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

    /** Uit een spawn-ei (creative) komt een robot met willekeurige onderdelen. */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        if (spawnType == MobSpawnType.SPAWN_EGG) {
            setParts(RobotParts.random(level.getRandom()));
            setHealth(getMaxHealth());
        }
        return result;
    }

    // ================================================================ Eigenaar (BaseTrust)

    @Override
    public @Nullable UUID owner() {
        return owner;
    }

    @Override
    public String ownerName() {
        return ownerName;
    }

    @Override
    public void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
    }

    /** Voor BaseTrust: in welke basis hij staat, hangt af van waar hij nu is. */
    @Override
    public BlockPos getBlockPos() {
        return blockPosition();
    }

    // ================================================================ Opslaan

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Parts", parts().pack());
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Parts")) {
            entityData.set(PARTS, tag.getInt("Parts"));
        }
        // Na het laden van leven en eigenschappen: de config kan intussen andere getallen hebben.
        applyParts();
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
    }

    // ================================================================ Een machine, geen dier

    /** Hij ademt niet: onder water verdrinkt hij niet. */
    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    /** Je kunt hem niet aan een touw meenemen. */
    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    /** Lichte metalen stapjes (niet zo zwaar als een ijzergolem). */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 0.25f, 1.6f);
    }
}
