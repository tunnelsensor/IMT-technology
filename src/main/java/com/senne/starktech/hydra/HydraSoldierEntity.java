package com.senne.starktech.hydra;

import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.network.BeamEffectPayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.shield.DomeSettings;
import com.senne.starktech.shield.EdithDomes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Een Hydra-soldaat: bewaakt de Hydra-bunker (zie tools/generate_bunker.py).
 *
 * Hij loopt rond tot hij iemand ziet. Dan legt hij zijn lasergeweer aan (twee handen, zoals een
 * kruisboog) en schiet om de zoveel tijd een rode laserstraal: meteen raak, als hij je kan zien.
 * Hij schiet op spelers en op Stark-drones (jouw escorte krijgt het dus ook te verduren). Raak je
 * er één, dan komen de soldaten in de buurt ook op je af.
 *
 * Staat zijn doelwit onder een schildkoepel, dan stuit het schot op de koepel, net als bij de
 * raiddrones. Soms laat hij een Hydra-onderdeel vallen (zie de loot table
 * entities/hydra_soldier), zijn geweer nooit.
 */
public class HydraSoldierEntity extends Monster implements RangedAttackMob {
    public HydraSoldierEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = HydraBalance.XP_REWARD;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HydraBalance.SOLDIER_HEALTH)
                .add(Attributes.ARMOR, HydraBalance.SOLDIER_ARMOR)
                .add(Attributes.MOVEMENT_SPEED, HydraBalance.SOLDIER_SPEED)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ATTACK_DAMAGE, HydraBalance.LASER_DAMAGE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        // Op afstand blijven en schieten: dezelfde aanpak als een skelet met een boog.
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, HydraBalance.FIRE_INTERVAL,
                HydraBalance.FIRE_INTERVAL + 10, HydraBalance.ATTACK_RANGE));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, HydraSoldierEntity.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, StarkDroneEntity.class, true));
    }

    /** Iedereen krijgt een lasergeweer. Dat valt nooit: het werkt alleen met Hydra's eigen spullen. */
    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.HYDRA_RIFLE.get()));
        setDropChance(EquipmentSlot.MAINHAND, 0.0f);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        populateDefaultEquipmentSlots(level.getRandom(), difficulty);
        return result;
    }

    /** Eén laserschot: meteen raak (het is licht), tenzij er een schildkoepel tussen zit. */
    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 from = getEyePosition().add(getViewVector(1.0f).scale(0.6)).add(0, -0.25, 0);
        Vec3 to = target.getBoundingBox().getCenter();
        playSound(ModSounds.DRONE_LASER.get(), 0.9f, 0.7f + random.nextFloat() * 0.1f);
        Vec3 shield = EdithDomes.blockedAt(level, to, from, null, DomeSettings.COST_LASER);
        if (shield != null) {
            ModNetwork.sendNear(level, position(), 64, new BeamEffectPayload(from, shield, BeamEffectPayload.Kind.HYDRA_LASER));
            return;
        }
        target.hurt(damageSources().mobAttack(this), HydraBalance.LASER_DAMAGE);
        ModNetwork.sendNear(level, position(), 64, new BeamEffectPayload(from, to, BeamEffectPayload.Kind.HYDRA_LASER));
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }
}
