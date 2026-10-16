package com.senne.starktech.robot;

import com.senne.starktech.bot.BotBoard;
import com.senne.starktech.bot.BotBrain;
import com.senne.starktech.bot.BotModule;
import com.senne.starktech.bot.BotNetwork;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.energy.StarkGrid;
import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.security.BaseTrust;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

/**
 * Een guard robot (hoofdstuk 21): een robot die je zelf samenstelt uit een hoofd, een lijf, armen en
 * benen, elk in een van vier stijlen (zie RobotParts). De onderdelen bepalen hoe hij eruitziet en een
 * beetje wat hij kan: het lijf zijn leven en pantser, de benen hoe snel hij loopt.
 *
 * Hij is geen monster (dus je torentjes laten hem met rust) en verdwijnt nooit vanzelf. Zolang hij geen
 * programma heeft staat hij stil en kijkt hij rond; met een programma (hoofdstuk 23) doet hij wat dat zegt
 * (zie BotBrain), en kijkt hij recht voor zich uit.
 *
 * Hij heeft een batterij (hoe groot: zijn lijf). Staat hij aan, dan gebruikt hij stroom (meer als hij loopt);
 * is de batterij leeg, dan valt hij stil ({@link RobotState#NO_POWER}). Zijn thuis is een Robot Dock: daar
 * laadt hij via het stroomnet. Zet je hem uit, dan loopt hij naar zijn dock en blijft hij daar staan laden;
 * zet je hem aan terwijl hij in zijn dock staat, dan stapt hij er naar voren uit.
 *
 * Wie hem neerzette is zijn eigenaar; samen met wie in die basis vertrouwd is mag die hem instellen
 * (dezelfde regel als bij de laserzender, zie BaseTrust).
 */
public class GuardRobotEntity extends PathfinderMob implements BaseTrust.Placed {
    /** De onderdelen, ingepakt in één getal (zie RobotParts.pack): je spel heeft ze nodig om hem te tekenen. */
    private static final EntityDataAccessor<Integer> PARTS =
            SynchedEntityData.defineId(GuardRobotEntity.class, EntityDataSerializers.INT);
    /** Hoe het met hem gaat (RobotState): je spel doet zijn lichtjes uit als hij niet aan staat. */
    private static final EntityDataAccessor<Byte> STATE =
            SynchedEntityData.defineId(GuardRobotEntity.class, EntityDataSerializers.BYTE);
    /** Staat hij aan (ook met een lege batterij), voor de knop in zijn scherm. */
    private static final EntityDataAccessor<Boolean> SWITCHED_ON =
            SynchedEntityData.defineId(GuardRobotEntity.class, EntityDataSerializers.BOOLEAN);
    /** Laadt hij nu op zijn dock? Dan zoemt hij zachtjes in je spel (zie client/robot/RobotChargeHum). */
    private static final EntityDataAccessor<Boolean> CHARGING =
            SynchedEntityData.defineId(GuardRobotEntity.class, EntityDataSerializers.BOOLEAN);
    /** Zijn printplaat (wat er op de pinnen zit en de bruggen), voor het bordscherm en het model in je spel. */
    private static final EntityDataAccessor<CompoundTag> BOARD =
            SynchedEntityData.defineId(GuardRobotEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** Hoe vol zijn batterij is (0 tot 1), voor de schermen. */
    private static final EntityDataAccessor<Float> BATTERY =
            SynchedEntityData.defineId(GuardRobotEntity.class, EntityDataSerializers.FLOAT);
    /** Hoeveel Robot Ammo er in zijn geweer zit (hoofdstuk 24), voor zijn scherm. */
    private static final EntityDataAccessor<Integer> AMMO =
            SynchedEntityData.defineId(GuardRobotEntity.class, EntityDataSerializers.INT);
    /** Hoe hoog de plaat van een Robot Dock is: daar staat hij op. */
    private static final double DOCK_FLOOR = 3.0 / 16.0;

    private @Nullable UUID owner;
    private String ownerName = "";
    /** Zijn printplaat zoals hij het laatst uit BOARD gelezen werd (zodat we dat niet elke keer opnieuw doen). */
    private BotBoard board = BotBoard.EMPTY;
    private @Nullable CompoundTag boardTag;
    /**
     * Alleen in je spel, voor het voorbeeld in de Robot Assembler: welke onderdelen we tekenen (bit per
     * RobotSlot). Een onderdeel dat nog niet in de assembler ligt, tekenen we niet.
     */
    private int shownSlots = 0b1111;
    /** Staat hij aan (zijn eigenaar zette hem aan)? Een nieuwe robot staat uit. */
    private boolean active;
    /** Hoeveel stroom er in zijn batterij zit (hoogstens {@link #maxBattery()}). */
    private double battery;
    /** Zijn Robot Dock, of null als hij (nog) geen thuis heeft. */
    private @Nullable BlockPos home;
    /** Waar hij naartoe stapt als hij in zijn dock aangezet wordt (daarna null). */
    private @Nullable BlockPos stepOut;
    /** Zijn brein (hoofdstuk 23): hier draait zijn programma, alleen op de server. */
    private final BotBrain brain = new BotBrain(this);

    public GuardRobotEntity(EntityType<? extends GuardRobotEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
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

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PARTS, RobotParts.DEFAULT.pack());
        builder.define(STATE, (byte) RobotState.STANDBY.ordinal());
        builder.define(BATTERY, 1.0f);
        builder.define(SWITCHED_ON, false);
        builder.define(CHARGING, false);
        builder.define(BOARD, new CompoundTag());
        builder.define(AMMO, 0);
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

    /** Zijn printplaat: wat er op zijn 8 pinnen gesoldeerd is (ook in je spel). */
    public BotBoard board() {
        CompoundTag tag = entityData.get(BOARD);
        if (tag != boardTag) {
            boardTag = tag;
            board = tag.isEmpty() ? BotBoard.EMPTY
                    : BotBoard.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(BotBoard.EMPTY);
        }
        return board;
    }

    /** Een ander bord (na solderen of een kortsluiting). */
    public void setBoard(BotBoard board) {
        entityData.set(BOARD, (CompoundTag) BotBoard.CODEC.encodeStart(NbtOps.INSTANCE, board).getOrThrow());
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

    // ================================================================ Kogels (hoofdstuk 24)

    /** Hoeveel Robot Ammo er in zijn geweer zit (ook in je spel). */
    public int ammo() {
        return entityData.get(AMMO);
    }

    /** Zoveel kogels (hooguit MAX_AMMO). */
    public void setAmmo(int ammo) {
        entityData.set(AMMO, Math.max(0, Math.min(maxAmmo(), ammo)));
    }

    public static int maxAmmo() {
        return Math.max(1, Math.round(StarkConfig.Robots.MAX_AMMO));
    }

    /**
     * Rechtsklik met Robot Ammo (als je hem mag instellen): zoveel kogels als erin passen gaan uit je hand in zijn
     * geweer. Geeft true als dat gebeurde (of als hij al vol is: dan zegt hij dat).
     */
    private boolean loadAmmo(ServerPlayer player, ItemStack stack) {
        if (!Robots.mayEdit(player, this)) {
            Robots.open(player, this); // dezelfde melding als bij het scherm: "not yours"
            return false;
        }
        String name = hasCustomName() ? getCustomName().getString() : "The robot";
        int room = maxAmmo() - ammo();
        if (room <= 0) {
            player.displayClientMessage(Component.translatable("message.starktech.robot.full", name, ammo()), true);
            return true;
        }
        int moved = Math.min(room, stack.getCount());
        setAmmo(ammo() + moved);
        stack.consume(moved, player);
        playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.2f);
        player.displayClientMessage(Component.translatable("message.starktech.robot.loaded", name, ammo()), true);
        return true;
    }

    // ================================================================ Batterij, aan en uit, zijn dock

    /** Hoe het met hem gaat. Op de server uitgerekend, in je spel meegestuurd. */
    public RobotState state() {
        return RobotState.byIndex(entityData.get(STATE));
    }

    /** Hoeveel stroom er in zijn batterij zit. */
    public double battery() {
        return battery;
    }

    /** Hoeveel stroom er hoogstens in zijn batterij past (van zijn lijf). */
    public double maxBattery() {
        return parts().battery();
    }

    /** Hoe vol zijn batterij is, van 0 tot 1 (ook in je spel). */
    public float batteryFraction() {
        return entityData.get(BATTERY);
    }

    /** Zet de batterij op zoveel stroom (voor tests en het oppakken). */
    public void setBattery(double energy) {
        battery = Math.max(0, Math.min(maxBattery(), energy));
        updateState();
    }

    /** Laadt hij nu op zijn dock? (Ook in je spel: dan zoemt hij.) */
    public boolean isCharging() {
        return entityData.get(CHARGING);
    }

    /** Staat hij aan? (Ook in je spel.) */
    public boolean isActive() {
        return level().isClientSide ? entityData.get(SWITCHED_ON) : active;
    }

    /**
     * Zet hem aan of uit. Aan in zijn dock: hij stapt er naar voren uit. Uit: hij loopt naar zijn dock (zie
     * ReturnToDockGoal) en laadt daar. Aanzetten met een soldeerbrug aan een module geeft kortsluiting (zie
     * {@link #shortCircuit}): dan blijft hij uit en geeft dit false.
     */
    public boolean setActive(boolean on) {
        if (active == on) {
            return true;
        }
        if (on && level() instanceof ServerLevel level && !board().shorts().isEmpty()) {
            shortCircuit(level);
            return false;
        }
        active = on;
        getNavigation().stop();
        stepOut = null;
        if (on) {
            stepOutOfDock();
        }
        updateState();
        return true;
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

    // ================================================================ Zijn programma (hoofdstuk 23)

    /** Zijn brein: het programma dat hij draait (zie BotBrain). Alleen op de server bezig. */
    public BotBrain brain() {
        return brain;
    }

    /** Het programma dat hij draait, of null. */
    public @Nullable LogicProgram program() {
        return brain.program();
    }

    /** Een ander programma (of geen). Staat hij aan, dan begint het meteen bij when started. */
    public void setProgram(@Nullable LogicProgram program) {
        brain.setProgram(program);
    }

    /**
     * Kortsluiting (hoofdstuk 22, jouw antwoord: "heb je het niet gezien en je gebruikt de robot, dan kortsluiting:
     * de robot of de onderdelen kunnen afbranden"). Elke soldeerbrug aan een module brandt door: de modules op die
     * pinnen worden Burnt Modules en de brug is weg. Vonken, rook en een knal; met de kans {@code burnChance} brandt
     * de hele robot af (hij is dan echt kapot: zijn onderdelen vallen eruit met hun kans). De eigenaar hoort het
     * van E.D.I.T.H., en het logboek van de kern waar hij staat krijgt een regel.
     */
    private void shortCircuit(ServerLevel level) {
        BotBoard board = board();
        java.util.TreeSet<Integer> burnt = new java.util.TreeSet<>();
        int bridges = board.bridges();
        for (int left : board.shorts()) {
            for (int pin = left; pin <= left + 1; pin++) {
                if (!board.pin(pin).isEmpty()) {
                    board = board.with(pin, BotModule.BURNT.id());
                    burnt.add(pin);
                }
            }
            bridges &= ~(1 << (left - 1));
        }
        setBoard(new BotBoard(board.pins(), bridges));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 40, 0.35, 0.5, 0.35, 0.25);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.4, getZ(), 12, 0.25, 0.4, 0.25, 0.02);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.6f);
        playSound(ModSounds.ROBOT_HURT.get(), 1.0f, 0.8f);
        boolean burnsDown = random.nextFloat() < StarkConfig.Robots.BURN_CHANCE;
        String name = hasCustomName() ? getCustomName().getString() : "A guard robot";
        StringBuilder pins = new StringBuilder();
        for (int pin : burnt) {
            pins.append(pins.isEmpty() ? "" : burnt.last() == pin ? " and " : ", ").append(pin);
        }
        String text = "Short circuit in " + name + ": " + (burnt.size() == 1 ? "pin " : "pins ") + pins + " burnt out"
                + (burnsDown ? ", and the robot burnt down" : "");
        com.senne.starktech.security.SecurityCoreBlockEntity core = com.senne.starktech.security.SecurityCores.coreAt(level, blockPosition());
        if (core != null) {
            core.log().add(level.getGameTime(), com.senne.starktech.security.os.EventLog.Type.DEVICE, text);
            core.setChanged();
        }
        ServerPlayer owner = this.owner == null ? null : level.getServer().getPlayerList().getPlayer(this.owner);
        if (owner != null) {
            com.senne.starktech.edith.EdithServer.send(owner, Component.literal(text),
                    com.senne.starktech.network.EdithMessagePayload.Level.ALERT);
        }
        if (burnsDown) {
            igniteForSeconds(4);
            hurt(damageSources().inFire(), Float.MAX_VALUE);
        }
    }

    /** Zijn Robot Dock, of null. */
    public @Nullable BlockPos home() {
        return home;
    }

    public void setHome(@Nullable BlockPos home) {
        this.home = home == null ? null : home.immutable();
    }

    /** Staat hij in zijn dock? */
    public boolean isOnDock() {
        return home != null && blockPosition().equals(home);
    }

    /** Staat hij precies in het midden van zijn dock (na het netjes gaan staan)? */
    public boolean isSettled() {
        return isOnDock() && flatDistance(home) <= 0.001;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            serverTick(level);
        }
    }

    /** Elke tick op de server: is zijn dock er nog, laden, stroom gebruiken, en in zijn dock gaan staan. */
    private void serverTick(ServerLevel level) {
        if (home != null && tickCount % 40 == 0 && level.isLoaded(home)
                && !(level.getBlockEntity(home) instanceof RobotDockBlockEntity dock && getUUID().equals(dock.robot()))) {
            home = null; // zijn dock is weg, of er woont een andere robot
        }
        double max = maxBattery();
        boolean charging = false;
        if (isOnDock() && battery < max) {
            double charge = Math.min(StarkConfig.Robots.DOCK_CHARGE_RATE, max - battery);
            if (StarkGrid.pay(level, home, charge)) {
                battery += charge;
                charging = true;
            }
        }
        if (entityData.get(CHARGING) != charging) {
            entityData.set(CHARGING, charging);
        }
        boolean walking = getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 || !getNavigation().isDone()
                || getMoveControl().hasWanted();
        if (battery > 0 && (active || walking)) {
            battery = Math.max(0, battery - (active ? StarkConfig.Robots.DRAIN_ON : 0)
                    - (walking ? StarkConfig.Robots.DRAIN_WALKING : 0));
        }
        if (battery <= 0) {
            getNavigation().stop();
        }
        if ((!active || brain.wantsDock()) && home != null && closeToHome() && flatDistance(home) > 0.001) {
            settleInDock(level);
        }
        // Zijn programma (hoofdstuk 23): alleen als hij aan staat en stroom heeft. Anders stopt het, en de volgende
        // keer begint het weer bij when started.
        if (active && battery > 0 && brain.hasProgram()) {
            brain.tick(level);
        } else {
            brain.stop();
        }
        updateState();
        // Om de seconde melden bij BotNetwork (de app Robots leest daaruit). Nog geen naam? Dan krijgt hij er een.
        if (tickCount % 20 == 1 && !isDeadOrDying()) {
            BotNetwork network = BotNetwork.get(level);
            if (!hasCustomName()) {
                setCustomName(Component.literal(network.freeName(owner)));
            }
            network.update(this);
        }
    }

    /** Echt kapot: meteen weg uit BotNetwork (niet pas na het omvallen). */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level && isDeadOrDying()) {
            BotNetwork.get(level).remove(getUUID());
        }
    }

    /** Kapot of opgepakt: weg uit BotNetwork. (Een stuk wereld dat niet meer geladen is, telt niet.) */
    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level && reason.shouldDestroy()) {
            BotNetwork.get(level).remove(getUUID());
        }
        super.remove(reason);
    }

    /**
     * Sneak + rechtsklik met een lege hand: zijn scherm (als je hem mag instellen, zie Robots). Rechtsklik met Robot
     * Ammo: kogels laden (hoofdstuk 24).
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof com.senne.starktech.bot.RobotAmmoItem && !player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer serverPlayer) {
                loadAmmo(serverPlayer, held);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (hand != InteractionHand.MAIN_HAND || !player.isSecondaryUseActive() || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Robots.open(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
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

    /** Zet wat je spel moet weten (toestand en batterij) klaar om mee te sturen, als het veranderd is. */
    private void updateState() {
        updateState(true);
    }

    /** Hetzelfde; {@code sounds} = false bij het laden en neerzetten (dan geen opstartgeluid). */
    private void updateState(boolean sounds) {
        RobotState state = battery <= 0 ? RobotState.NO_POWER : active ? RobotState.ACTIVE : RobotState.STANDBY;
        if (state() != state) {
            // Aan (opstarten) of niet meer aan (uitgezet of de batterij is leeg): dat hoor je.
            if (sounds && (state == RobotState.ACTIVE || state() == RobotState.ACTIVE)) {
                playSound(state == RobotState.ACTIVE ? ModSounds.ROBOT_POWER_ON.get() : ModSounds.ROBOT_POWER_OFF.get(), 0.8f, 1.0f);
            }
            entityData.set(STATE, (byte) state.ordinal());
        }
        if (entityData.get(SWITCHED_ON) != active) {
            entityData.set(SWITCHED_ON, active);
        }
        float fraction = (float) (battery / Math.max(1.0, maxBattery()));
        if (Math.abs(batteryFraction() - fraction) >= 0.005f || (fraction == 0) != (batteryFraction() == 0)) {
            entityData.set(BATTERY, fraction);
        }
    }

    /** In zijn dock duwt niemand hem eruit. */
    @Override
    public boolean isPushable() {
        return !isOnDock() && super.isPushable();
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
            return (!active || brain.wantsDock()) && home != null && battery > 0 && !closeToHome() && onGround();
        }

        @Override
        public boolean canContinueToUse() {
            return (!active || brain.wantsDock()) && home != null && battery > 0 && !closeToHome();
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
            return active && stepOut != null && battery > 0 && onGround();
        }

        @Override
        public boolean canContinueToUse() {
            return active && stepOut != null && battery > 0 && ticks < 80 && flatDistance(stepOut) > 0.25;
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
        tag.putBoolean("Active", active);
        tag.putDouble("Battery", battery);
        if (home != null) {
            tag.putLong("Home", home.asLong());
        }
        tag.put("Board", BotBoard.CODEC.encodeStart(NbtOps.INSTANCE, board()).getOrThrow());
        if (brain.program() != null) {
            tag.put("Program", brain.program().save());
        }
        tag.putByte("Facing", (byte) brain.body().facing().get2DDataValue());
        tag.putInt("Ammo", ammo());
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
        setBoard(tag.contains("Board") ? BotBoard.CODEC.parse(NbtOps.INSTANCE, tag.get("Board")).result().orElse(BotBoard.EMPTY)
                : BotBoard.EMPTY);
        // Na het laden van leven en eigenschappen: de config kan intussen andere getallen hebben.
        applyParts();
        active = tag.getBoolean("Active");
        battery = tag.contains("Battery") ? Math.min(tag.getDouble("Battery"), maxBattery()) : maxBattery();
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
        brain.setProgram(tag.contains("Program") ? LogicProgram.load(tag.getCompound("Program")) : null);
        brain.body().setFacing(tag.contains("Facing") ? Direction.from2DDataValue(tag.getByte("Facing")) : null);
        setAmmo(tag.getInt("Ammo"));
        updateState(false);
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
