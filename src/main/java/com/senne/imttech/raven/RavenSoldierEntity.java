package com.senne.imttech.raven;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.drone.DroneEntity;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * Een Raven-soldaat: bewaakt de Biebia's Industries-bunker (zie tools/generate_bunker.py).
 *
 * Hij loopt rond tot hij iemand ziet. Sinds hoofdstuk 54 (jouw notitie: "zorg dat ze de wapens die er nu aan de game
 * zijn toegevoegd kunnen gebruiken, en het gun dat ze nu hebben moet volledig weg") draagt hij een wapen van de mod:
 * de helft een <b>Glock 19</b> (echte kogels, met een vliegtijd), de rest een <b>laser rifle</b> of <b>laser pistol</b>
 * (dezelfde stralen als die van jou). Een deel heeft ook <b>granaten</b> bij zich (frag of EMP): verstop je je achter een
 * muur, dan gooit hij er een; op een drone gooit hij een EMP. Hij schiet op spelers en op drones (jouw escorte krijgt
 * het dus ook te verduren). Hoe hij vecht staat in RavenCombatGoal: als ploeg, ze omsingelen je, zoeken een plek van
 * waaruit ze je zien als je je verstopt, en doen deuren open om bij je te komen.
 *
 * Staat zijn doelwit onder een schildkoepel, dan stuit het schot op de koepel, net als bij de
 * raiddrones. Soms laat hij een Raven-onderdeel vallen (zie de loot table
 * entities/raven_soldier), en soms (8%) zijn wapen.
 */
public class RavenSoldierEntity extends Monster implements RangedAttackMob {
    /** Hoofdstuk 54: een bewaker van de toren met een keycard van die toren op zak (zie structures/TowerGuards). */
    private boolean keycardGuard;
    /** Hoeveel granaten hij nog heeft, en of het EMP's zijn (anders frags). */
    private int grenades;
    private boolean empGrenades;
    private int grenadeCooldown;

    public RavenSoldierEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = Math.round(ImtConfig.Raven.XP_REWARD);
        // Deuren mogen in zijn route zitten: hij doet ze open (zie OpenDoorGoal hieronder).
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, ImtConfig.Raven.SOLDIER_HEALTH)
                .add(Attributes.ARMOR, ImtConfig.Raven.SOLDIER_ARMOR)
                .add(Attributes.MOVEMENT_SPEED, ImtConfig.Raven.SOLDIER_SPEED)
                // Ver vooruit denken: zo vindt hij ook een lange weg om een gebouw heen.
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, ImtConfig.Raven.LASER_DAMAGE);
    }

    /** Geboeid met handboeien (hoofdstuk 48): hij staat stil en doet niets, tot hij los is (of zich loswurmt). */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || com.senne.imttech.cuffs.Handcuffs.isCuffed(this);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        // Vechten als ploeg (omsingelen, een schietplek zoeken, een weg naar binnen).
        goalSelector.addGoal(2, new RavenCombatGoal(this));
        // Een deur op zijn route doet hij open (en weer dicht).
        goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, RavenSoldierEntity.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, DroneEntity.class, true));
    }

    /** Zijn wapen (hoofdstuk 54): de helft een Glock 19, verder een laser rifle of laser pistol; soms granaten erbij. */
    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, DifficultyInstance difficulty) {
        float roll = random.nextFloat();
        ItemStack weapon;
        if (roll < 0.5f) {
            weapon = com.senne.imttech.guns.FirearmItem.loaded(com.senne.imttech.guns.FirearmType.GLOCK_19,
                    com.senne.imttech.guns.GunMaterial.STEEL);
        } else {
            // Hoofdstuk 55: een compleet titanium geweer, maar zonder Nether Star (die haal je niet bij een soldaat): wie het
            // opraapt moet er zelf een insteken op de Laser Workbench. De soldaat zelf schiet ook zonder (fireFrom).
            com.senne.imttech.weapons.GunType kind = roll < 0.8f ? com.senne.imttech.weapons.GunType.RIFLE : com.senne.imttech.weapons.GunType.PISTOL;
            weapon = com.senne.imttech.weapons.LaserGunItem.withParts(kind, com.senne.imttech.weapons.LaserParts
                    .complete(com.senne.imttech.guns.GunMaterial.TITANIUM).without(com.senne.imttech.weapons.LaserPart.CORE));
            com.senne.imttech.weapons.LaserGunItem.setCell(weapon, com.senne.imttech.weapons.EnergyCellItem.FULL);
        }
        setItemSlot(EquipmentSlot.MAINHAND, weapon);
        setDropChance(EquipmentSlot.MAINHAND, ImtConfig.Raven.WEAPON_DROP_CHANCE);
        grenades = random.nextFloat() < ImtConfig.Raven.GRENADE_CHANCE ? 1 + random.nextInt(2) : 0;
        empGrenades = random.nextFloat() < 0.4f;
    }

    public boolean hasGrenades() {
        return grenades > 0 && grenadeCooldown <= 0;
    }

    public void setKeycardGuard(boolean guard) {
        this.keycardGuard = guard;
    }

    public boolean isKeycardGuard() {
        return keycardGuard;
    }

    /** Een bewaker laat zijn keycard vallen (geschreven op de kern van de toren, groep "staff"). */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (keycardGuard) {
            ItemStack card = com.senne.imttech.structures.TowerGuards.staffCard(level, blockPosition());
            if (!card.isEmpty()) {
                spawnAtLocation(card);
            }
        }
    }

    public boolean hasEmpGrenades() {
        return grenades > 0 && empGrenades;
    }

    /** Een granaat in een boogje naar {@code at} (frag, of een EMP). */
    public void throwGrenade(Vec3 at) {
        if (grenades <= 0 || !(level() instanceof ServerLevel level)) {
            return;
        }
        com.senne.imttech.weapons.RollingGrenade grenade = com.senne.imttech.weapons.RollingGrenade.thrown(
                empGrenades ? com.senne.imttech.weapons.RollingGrenade.Kind.EMP : com.senne.imttech.weapons.RollingGrenade.Kind.FRAG,
                level, this);
        Vec3 d = at.subtract(grenade.position());
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        grenade.shoot(d.x, d.y + flat * 0.22, d.z, (float) Mth.clamp(0.55 + flat * 0.05, 0.6, 1.3), 2.0f);
        level.addFreshEntity(grenade);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 0.6f, 0.6f);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        grenades--;
        grenadeCooldown = Math.round(ImtConfig.Raven.GRENADE_INTERVAL);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (grenadeCooldown > 0) {
            grenadeCooldown--;
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Grenades", grenades);
        tag.putBoolean("EmpGrenades", empGrenades);
        tag.putBoolean("KeycardGuard", keycardGuard);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        grenades = tag.getInt("Grenades");
        empGrenades = tag.getBoolean("EmpGrenades");
        keycardGuard = tag.getBoolean("KeycardGuard");
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        populateDefaultEquipmentSlots(level.getRandom(), difficulty);
        return result;
    }

    /** Eén laserschot: meteen raak (het is licht), tenzij er een schildkoepel tussen zit. */
    /** Door de rook van een smokebom (hoofdstuk 35) ziet hij niemand. */
    @Override
    public boolean hasLineOfSight(net.minecraft.world.entity.Entity entity) {
        return super.hasLineOfSight(entity)
                && !com.senne.imttech.weapons.Smoke.blocks(level(), getEyePosition(), entity.getEyePosition());
    }

    /** Eén schot met wat hij in zijn hand heeft: een kogel uit de Glock, of een straal uit zijn lasergeweer. */
    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 from = getEyePosition().add(getViewVector(1.0f).scale(0.6)).add(0, -0.25, 0);
        Vec3 to = target.getBoundingBox().getCenter();
        Vec3 look = to.subtract(from).normalize();
        ItemStack weapon = getMainHandItem();
        if (com.senne.imttech.guns.FirearmItem.typeOf(weapon) != null) {
            // Een echte kogel (hoofdstuk 52): die vliegt, zakt, en stuit op een koepel (zie BulletEntity).
            Vec3 direction = com.senne.imttech.weapons.LaserGuns.scatter(look, Math.toRadians(ImtConfig.Raven.SPREAD), random);
            com.senne.imttech.guns.BulletEntity.shoot(level, this, com.senne.imttech.guns.FirearmType.GLOCK_19,
                    ImtConfig.Raven.LASER_DAMAGE, direction);
            level.playSound(null, getX(), getEyeY(), getZ(), ModSounds.GLOCK_FIRE.get(), SoundSource.HOSTILE, 1.0f,
                    0.9f + random.nextFloat() * 0.1f);
            com.senne.imttech.guns.CasingEntity.eject(level, this);
            return;
        }
        com.senne.imttech.weapons.GunType type = com.senne.imttech.weapons.LaserGunItem.typeOf(weapon);
        com.senne.imttech.weapons.LaserGuns.fireFrom(level, this, type == null ? com.senne.imttech.weapons.GunType.RIFLE : type,
                from, look, ImtConfig.Raven.LASER_DAMAGE, Math.toRadians(ImtConfig.Raven.SPREAD));
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
