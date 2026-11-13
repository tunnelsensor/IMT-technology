package com.senne.imttech.raven;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.drone.DroneEntity;
import com.senne.imttech.registry.ModItems;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
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
 * een <b>Glock 19</b> (echte kogels, met een vliegtijd), een <b>laser rifle</b> of <b>laser pistol</b> (dezelfde stralen
 * als die van jou), en sinds hoofdstuk 103 (stap 8, jouw keuze: "Raven-soldaten met de nieuwe wapens") ook een
 * <b>MP5</b>, een <b>AK-47</b>, soms een <b>AWP</b> (een sluipschutter) of een <b>Remington 870</b>. Wat hij draagt en
 * wat het kan, staat in {@link RavenArms}. Een deel heeft ook <b>granaten</b> bij zich (frag of EMP): verstop je je achter een
 * muur, dan gooit hij er een; op een drone gooit hij een EMP. Hij schiet op spelers en op drones (jouw escorte krijgt
 * het dus ook te verduren). Hoe hij vecht staat in RavenCombatGoal: als ploeg, ze omsingelen je, zoeken een plek van
 * waaruit ze je zien als je je verstopt, en doen deuren open om bij je te komen.
 *
 * Staat zijn doelwit onder een schildkoepel, dan stuit het schot op de koepel, net als bij de
 * raiddrones. Soms laat hij een Raven-onderdeel of een attachment vallen (zie de loot table
 * entities/raven_soldier), en soms (8%) zijn wapen, met wat erop zit.
 *
 * Sinds hoofdstuk 60 is hij een bewaker zoals in Hitman: hij kijkt in een kegel en het duurt even voor hij je heeft
 * (RavenSenses: een meter die volloopt), hij komt kijken als hij iets ziet of hoort en zoekt waar hij je kwijtraakte
 * (RavenInvestigateGoal). Wat je spel daarvan ziet, staat in {@link Watcher}.
 */
public class RavenSoldierEntity extends Monster implements RangedAttackMob, Watcher {
    /** Hoofdstuk 60: hoe alert hij is, zijn meter en wie hem vult, voor het "?"/"!" en het oogje op je scherm. */
    private static final EntityDataAccessor<Byte> ALERT = SynchedEntityData.defineId(RavenSoldierEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> SUSPICION = SynchedEntityData.defineId(RavenSoldierEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> SUSPECT = SynchedEntityData.defineId(RavenSoldierEntity.class, EntityDataSerializers.INT);
    /** Hoofdstuk 72, stap 5: de rugzak op zijn rug (soms een antracietgrijze Assault Pack), ook in jouw spel om hem te tekenen. */
    private static final EntityDataAccessor<ItemStack> PACK = SynchedEntityData.defineId(RavenSoldierEntity.class, EntityDataSerializers.ITEM_STACK);
    /**
     * 8 november: een sluipaanval (Takedown) is een korte worsteling. Wie hem vastheeft (het nummer van de speler, -1 =
     * niemand) en hoe lang het duurt (ticks, 0 = niemand heeft hem vast); je spel tekent er de animatie mee
     * (RavenSoldierRenderer en TakedownPose).
     */
    private static final EntityDataAccessor<Integer> TAKEDOWN_BY = SynchedEntityData.defineId(RavenSoldierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TAKEDOWN_LENGTH = SynchedEntityData.defineId(RavenSoldierEntity.class, EntityDataSerializers.INT);

    /** Zijn zintuigen (hoofdstuk 60). */
    private final RavenSenses senses = new RavenSenses(this);
    /** Zijn roepnaam op de radio (hoofdstuk 60, stap 4): "BB-2358". */
    private String callsign = "";
    /** Hoofdstuk 60: zijn post (waar hij verscheen) en zijn route (leeg = hij staat op zijn post), zie RavenPatrolGoal. */
    private @Nullable net.minecraft.core.BlockPos post;
    private java.util.List<net.minecraft.core.BlockPos> route = java.util.List.of();
    /** Hoofdstuk 54: een bewaker van de toren met een keycard van die toren op zak (zie structures/TowerGuards). */
    private boolean keycardGuard;
    /** Hoeveel granaten hij nog heeft, en of het EMP's zijn (anders frags). */
    private int grenades;
    private boolean empGrenades;
    private int grenadeCooldown;
    /**
     * Hoofdstuk 103, stap 8: een grendel (AWP) of een pomp (Remington 870) laadt hij na elk schot zelf door. Zoveel ticks
     * nog tot hij dat doet (je hoort het, en dan pas springt de lege huls eruit); 0 = er valt niets door te laden.
     */
    private int cycleIn;
    /** Zo lang na het schot laadt hij door (ticks): een halve seconde, zoals jij dat doet. */
    private static final int CYCLE_DELAY = 10;
    /**
     * 8 november: een soldaat van een versterkingsteam (RavenSites). Na "stand down" loopt hij terug naar de ingang en is
     * hij weg ({@code leaving}), zodat een bunker na een alarm niet voorgoed voller wordt.
     */
    private boolean backup;
    private boolean leaving;
    /** Neergestoken met een sluipaanval (Takedown): hij sterft zonder geluid. */
    private boolean silentDeath;
    /**
     * De sluipaanval: bij welke tick van dit wezen hij begon (aan beide kanten: in je spel loopt de animatie daarvandaan),
     * en op de server wie hem vastheeft en hoeveel ticks al. Niet opgeslagen: na opnieuw laden is hij gewoon weer vrij.
     */
    private int takedownStart;
    private @Nullable java.util.UUID takedownHolder;
    private int takedownTicks;

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

    /**
     * Geboeid met handboeien (hoofdstuk 48): hij staat stil en doet niets, tot hij los is (of zich loswurmt). En sinds
     * 8 november ook als iemand hem vastheeft voor een sluipaanval.
     */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || com.senne.imttech.cuffs.Handcuffs.isCuffed(this) || isBeingTakenDown();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        // Vechten als ploeg (omsingelen, een schietplek zoeken, een weg naar binnen).
        goalSelector.addGoal(2, new RavenCombatGoal(this));
        // Een deur op zijn route doet hij open (en weer dicht).
        goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, true));
        // Hoofdstuk 60: achterdochtig gaan kijken, of zoeken waar hij je kwijtraakte.
        goalSelector.addGoal(4, new RavenInvestigateGoal(this));
        // Rustig: zijn route lopen, of op zijn post staan (hoofdstuk 60; vroeger slenterde hij rond).
        goalSelector.addGoal(5, new RavenPatrolGoal(this));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, RavenSoldierEntity.class).setAlertOthers());
        // Spelers ziet hij sinds hoofdstuk 60 met zijn eigen zintuigen (RavenSenses: een kegel, en een meter die volloopt),
        // niet meer meteen en rondom; jouw drones schiet hij nog wel meteen neer als hij ze ziet.
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, DroneEntity.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ALERT, (byte) 0);
        builder.define(SUSPICION, (byte) 0);
        builder.define(SUSPECT, -1);
        builder.define(TAKEDOWN_BY, -1);
        builder.define(TAKEDOWN_LENGTH, 0);
        builder.define(PACK, ItemStack.EMPTY);
    }

    /** Hoofdstuk 72, stap 5: de rugzak op zijn rug, of leeg. */
    public ItemStack pack() {
        return entityData.get(PACK);
    }

    public void setPack(ItemStack pack) {
        entityData.set(PACK, pack.copy());
    }

    // ================================================================ Zijn zintuigen (hoofdstuk 60)

    public RavenSenses senses() {
        return senses;
    }

    /** Zijn roepnaam op de radio ("BB-2358"), zie RavenRadio. */
    public String callsign() {
        return callsign.isEmpty() ? "BB-0000" : callsign;
    }

    /** Dood terwijl hij riep: zijn maten hoorden een half gesprek (zie RavenSenses.interrupted). */
    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level) {
            senses.interrupted(level);
        }
        super.die(source);
    }

    /** Zijn post: waar hij verscheen (daar staat hij als hij geen route heeft). */
    public @Nullable net.minecraft.core.BlockPos post() {
        return post;
    }

    public void setPost(@Nullable net.minecraft.core.BlockPos post) {
        this.post = post == null ? null : post.immutable();
    }

    /** Een soldaat van een versterkingsteam (RavenSites.spawnTeam). */
    public void markBackup() {
        backup = true;
    }

    public boolean isBackup() {
        return backup;
    }

    /** Is hij op weg naar buiten (na "stand down")? */
    public boolean isLeaving() {
        return leaving;
    }

    /**
     * "Stand down" (RavenSites): een soldaat van een versterkingsteam gaat terug naar de ingang (zijn post) en verdwijnt
     * daar zodra hij rustig is. Komt hij onderweg iemand tegen, dan vecht hij eerst nog.
     */
    public void leave() {
        if (backup) {
            leaving = true;
            route = java.util.List.of();
        }
    }

    /** Een sluipaanval (Takedown): zijn dood maakt geen geluid. */
    public void silenceDeath() {
        silentDeath = true;
    }

    // ================================================================ De sluipaanval (8 november)

    /** Heeft iemand hem nu vast voor een sluipaanval? (aan beide kanten) */
    public boolean isBeingTakenDown() {
        return entityData.get(TAKEDOWN_LENGTH) > 0;
    }

    /** Het nummer (entity id) van de speler die hem vastheeft, of -1. */
    public int takedownBy() {
        return entityData.get(TAKEDOWN_BY);
    }

    /** Hoe lang de sluipaanval duurt (ticks), of 0. */
    public int takedownLength() {
        return entityData.get(TAKEDOWN_LENGTH);
    }

    /** Hoe ver de sluipaanval is, van 0 tot 1; {@code age} is zijn tickCount plus de partial tick (voor een vloeiende animatie). */
    public float takedownProgress(float age) {
        int length = takedownLength();
        return length <= 0 ? 0.0f : Mth.clamp((age - takedownStart) / length, 0.0f, 1.0f);
    }

    /** Op de server: wie hem vastheeft (null = niemand). */
    public @Nullable java.util.UUID takedownHolder() {
        return takedownHolder;
    }

    /** Op de server: weer een tick verder met worstelen; geeft hoeveel ticks het nu al duurt. */
    int nextTakedownTick() {
        return ++takedownTicks;
    }

    /** Een speler grijpt hem van achteren (Takedown): hij staat stil en worstelt {@code length} ticks. */
    void grab(Player player, int length) {
        takedownHolder = player.getUUID();
        takedownTicks = 0;
        getNavigation().stop();
        entityData.set(TAKEDOWN_BY, player.getId());
        entityData.set(TAKEDOWN_LENGTH, length);
    }

    /** De sluipaanval is voorbij: af, of hij is losgekomen. */
    void releaseTakedown() {
        takedownHolder = null;
        takedownTicks = 0;
        entityData.set(TAKEDOWN_LENGTH, 0);
        entityData.set(TAKEDOWN_BY, -1);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (TAKEDOWN_LENGTH.equals(key)) {
            takedownStart = tickCount; // ook in je spel: daar begint de animatie nu
        }
    }

    /** Verdwijnt hij terwijl iemand hem vastheeft (gedood door iets anders, weggehaald), dan is die speler weer vrij. */
    @Override
    public void remove(RemovalReason reason) {
        if (takedownHolder != null && level() instanceof ServerLevel level) {
            Takedown.released(level, this);
        }
        super.remove(reason);
    }

    /** Zijn route (de plekken uit zijn markering, zie RavenRoutes), leeg als hij op zijn post staat. */
    public java.util.List<net.minecraft.core.BlockPos> route() {
        return route;
    }

    public void setRoute(java.util.List<net.minecraft.core.BlockPos> route) {
        this.route = java.util.List.copyOf(route);
    }

    /** Van zijn zintuigen naar je spel (alleen als er iets veranderde). */
    void syncAlert(RavenAlert alert, int suspicion, int suspectId) {
        byte state = (byte) alert.ordinal();
        if (entityData.get(ALERT) != state) {
            entityData.set(ALERT, state);
        }
        byte meter = (byte) Mth.clamp(suspicion, 0, 100);
        if (entityData.get(SUSPICION) != meter) {
            entityData.set(SUSPICION, meter);
        }
        if (entityData.get(SUSPECT) != suspectId) {
            entityData.set(SUSPECT, suspectId);
        }
    }

    @Override
    public RavenAlert alertState() {
        return RavenAlert.byOrdinal(entityData.get(ALERT));
    }

    @Override
    public int suspicion() {
        return entityData.get(SUSPICION);
    }

    @Override
    public int suspectId() {
        return entityData.get(SUSPECT);
    }

    /**
     * Zijn wapen (hoofdstuk 54, en sinds hoofdstuk 103 ook de nieuwe wapens): een Glock 19, een MP5, een AK-47, een AWP,
     * een Remington 870, een laser rifle of een laser pistol (zie RavenArms); soms granaten erbij.
     */
    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, DifficultyInstance difficulty) {
        arm(RavenArms.random(random));
        grenades = random.nextFloat() < ImtConfig.Raven.GRENADE_CHANCE ? 1 + random.nextInt(2) : 0;
        empGrenades = random.nextFloat() < 0.4f;
    }

    /** Geeft hem dit wapen in zijn hand (ook voor de tests en de rondleiding: een soldaat met een wapen naar keuze). */
    public void arm(ItemStack weapon) {
        setItemSlot(EquipmentSlot.MAINHAND, weapon);
        setDropChance(EquipmentSlot.MAINHAND, ImtConfig.Raven.WEAPON_DROP_CHANCE);
        cycleIn = 0;
    }

    /** Tot hoe ver hij schiet met wat hij in zijn hand heeft (blokken): een sluipschutter veel verder dan de rest. */
    public double attackRange() {
        return RavenArms.range(getMainHandItem());
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
        // Eerst onthouden wat hij droeg: laat hij zijn wapen vallen (super), dan is zijn hand daarna leeg.
        ItemStack weapon = getMainHandItem().copy();
        super.dropCustomDeathLoot(level, source, recentlyHit);
        // Hoofdstuk 72, stap 5: zijn rugzak, vol buit (die komt met de rest in zijn lichaam, zie Bodies). Sinds hoofdstuk
        // 103 met munitie voor het wapen dat hij droeg.
        if (!pack().isEmpty()) {
            spawnAtLocation(com.senne.imttech.backpack.RavenPacks.filled(pack(), getRandom(), weapon));
            setPack(ItemStack.EMPTY);
        }
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
        if (level() instanceof ServerLevel level) {
            if (cycleIn > 0 && --cycleIn == 0) {
                cycle(level);
            }
            if (isBeingTakenDown()) {
                // 8 november: iemand heeft hem vast voor een sluipaanval. Hij ziet en hoort niets meer, en roept niet: hij worstelt.
                setAggressive(false);
                Takedown.tick(level, this);
                return;
            }
            // Hoofdstuk 60: kijken (en sinds stap 3 luisteren), en zijn houding: het geweer aangelegd zodra hij een doelwit heeft.
            senses.tick(level);
            setAggressive(getTarget() != null);
            // 8 november: een versterkingsteam dat naar buiten loopt, is weg zodra hij rustig bij de ingang staat.
            if (leaving && post != null && getTarget() == null && senses.state() == RavenAlert.CALM
                    && !com.senne.imttech.cuffs.Handcuffs.isCuffed(this)
                    && position().distanceTo(Vec3.atBottomCenterOf(post)) < 2.0) {
                discard();
            }
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Grenades", grenades);
        tag.putBoolean("EmpGrenades", empGrenades);
        tag.putBoolean("KeycardGuard", keycardGuard);
        tag.putLong("MemoryUntil", senses.memoryUntil());
        tag.putString("Callsign", callsign);
        tag.putBoolean("Backup", backup);
        tag.putBoolean("Leaving", leaving);
        if (!pack().isEmpty()) {
            tag.put("Pack", pack().save(registryAccess()));
        }
        if (post != null) {
            tag.putLong("Post", post.asLong());
        }
        if (!route.isEmpty()) {
            tag.putLongArray("Route", route.stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
        }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        grenades = tag.getInt("Grenades");
        empGrenades = tag.getBoolean("EmpGrenades");
        keycardGuard = tag.getBoolean("KeycardGuard");
        senses.setMemoryUntil(tag.getLong("MemoryUntil"));
        callsign = tag.getString("Callsign");
        backup = tag.getBoolean("Backup");
        leaving = tag.getBoolean("Leaving");
        setPack(tag.contains("Pack") ? ItemStack.parseOptional(registryAccess(), tag.getCompound("Pack")) : ItemStack.EMPTY);
        post = tag.contains("Post") ? net.minecraft.core.BlockPos.of(tag.getLong("Post")) : null;
        route = tag.contains("Route")
                ? java.util.Arrays.stream(tag.getLongArray("Route")).mapToObj(net.minecraft.core.BlockPos::of).toList()
                : java.util.List.of();
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        populateDefaultEquipmentSlots(level.getRandom(), difficulty);
        if (post == null) {
            post = blockPosition(); // hoofdstuk 60: waar hij verschijnt, is zijn post
        }
        if (callsign.isEmpty()) {
            callsign = "BB-" + (1000 + level.getRandom().nextInt(9000));
        }
        // Hoofdstuk 72, stap 5 (jouw keuze): soms een antracietgrijze Assault Pack op zijn rug.
        if (pack().isEmpty() && level.getRandom().nextFloat() < ImtConfig.Backpacks.RAVEN_PACK_CHANCE) {
            setPack(com.senne.imttech.backpack.RavenPacks.pack());
        }
        return result;
    }

    /** Eén laserschot: meteen raak (het is licht), tenzij er een schildkoepel tussen zit. */
    /** Door de rook van een smokebom (hoofdstuk 35) ziet hij niemand. */
    @Override
    public boolean hasLineOfSight(net.minecraft.world.entity.Entity entity) {
        return super.hasLineOfSight(entity)
                && !com.senne.imttech.weapons.Smoke.blocks(level(), getEyePosition(), entity.getEyePosition());
    }

    /**
     * Eén schot met wat hij in zijn hand heeft: een kogel uit zijn vuurwapen (bij de Remington 870 acht korrels hagel),
     * of een straal uit zijn lasergeweer. Een salvo (MP5, AK-47) is een paar van deze schoten vlak na elkaar: dat regelt
     * RavenCombatGoal.
     */
    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 from = getEyePosition().add(getViewVector(1.0f).scale(0.6)).add(0, -0.25, 0);
        Vec3 to = target.getBoundingBox().getCenter();
        Vec3 look = to.subtract(from).normalize();
        ItemStack weapon = getMainHandItem();
        com.senne.imttech.guns.FirearmType firearm = com.senne.imttech.guns.FirearmItem.typeOf(weapon);
        if (firearm != null) {
            // Een echte kogel (hoofdstuk 52): die vliegt, zakt, en stuit op een koepel (zie BulletEntity). Sinds hoofdstuk
            // 103 de kogel van zijn eigen wapen (zo ver, zo snel), met de schade en de spreiding uit [raven] (RavenArms).
            Vec3 aim = com.senne.imttech.weapons.LaserGuns.scatter(look, Math.toRadians(RavenArms.spread(weapon)), random);
            float damage = RavenArms.damage(weapon);
            // Hagel is een bundel korrels: één richting voor het schot, en elke korrel waaiert er nog wat af (zoals bij jou).
            int pellets = firearm.pellets();
            double fan = Math.toRadians(Math.max(0, ImtConfig.Guns.PELLET_SPREAD));
            for (int i = 0; i < pellets; i++) {
                com.senne.imttech.guns.BulletEntity.shoot(level, this, firearm, damage,
                        pellets == 1 ? aim : com.senne.imttech.weapons.LaserGuns.scatter(aim, fan, random));
            }
            // Hoofdstuk 74: dichtbij het schot, verder weg het doffe schot van ver; met een demper erop een doffe plof
            // (zie Firearms.shotSound).
            com.senne.imttech.guns.Firearms.shotSound(level, this, firearm, com.senne.imttech.guns.FirearmItem.suppressed(weapon),
                    getX(), getEyeY(), getZ(), SoundSource.HOSTILE, 0.9f + random.nextFloat() * 0.1f);
            if (firearm.manual()) {
                // Een grendel of een pomp: de lege huls blijft in de kamer tot hij doorlaadt, een halve seconde later.
                cycleIn = CYCLE_DELAY;
            } else {
                com.senne.imttech.guns.CasingEntity.eject(level, this, firearm.caliber);
            }
            return;
        }
        com.senne.imttech.weapons.GunType type = com.senne.imttech.weapons.LaserGunItem.typeOf(weapon);
        com.senne.imttech.weapons.LaserGuns.fireFrom(level, this, type == null ? com.senne.imttech.weapons.GunType.RIFLE : type,
                from, look, RavenArms.damage(weapon), Math.toRadians(RavenArms.spread(weapon)));
    }

    /** Doorladen na een schot met een grendel of een pomp: je hoort het, en de lege huls springt eruit. */
    private void cycle(ServerLevel level) {
        com.senne.imttech.guns.FirearmType firearm = com.senne.imttech.guns.FirearmItem.typeOf(getMainHandItem());
        if (firearm == null || !firearm.manual() || !isAlive()) {
            return;
        }
        level.playSound(null, getX(), getEyeY(), getZ(), firearm.rackSound(), SoundSource.HOSTILE, 0.8f, 0.95f + random.nextFloat() * 0.1f);
        com.senne.imttech.guns.CasingEntity.eject(level, this, firearm.caliber);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return silentDeath ? null : SoundEvents.PLAYER_DEATH;
    }
}
