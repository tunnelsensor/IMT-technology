package com.senne.starktech.bot;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.energy.StarkGrid;
import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.robot.RobotState;
import com.senne.starktech.robot.Robots;
import com.senne.starktech.security.BaseTrust;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

/**
 * Wat een guard robot en een security drone (hoofdstuk 25) als wezen delen: een printplaat, een batterij die op
 * zijn dock laadt (en met een Solar Panel-module in de zon), aan en uit, neer na de Ion Blaster, kogels voor zijn
 * Gun-module, een eigenaar, een brein dat zijn programma draait, en zijn plek in BotNetwork. Wat verschilt (lopen
 * of vliegen, hoe hij eruitziet, zijn dock) staat in GuardRobotEntity en GuardDroneEntity.
 *
 * Hij is geen monster (dus je torentjes laten hem met rust) en verdwijnt nooit vanzelf. Staat hij aan, dan
 * gebruikt hij stroom (meer als hij beweegt); is de batterij leeg, dan valt hij stil ({@link RobotState#NO_POWER}).
 *
 * Wie hem neerzette is zijn eigenaar; samen met wie in die basis vertrouwd is mag die hem instellen (dezelfde
 * regel als bij de laserzender, zie BaseTrust).
 */
public abstract class BotEntity extends PathfinderMob implements Bot, BaseTrust.Placed {
    /** Hoe het met hem gaat (RobotState): je spel doet zijn lichtjes uit als hij niet aan staat. */
    private static final EntityDataAccessor<Byte> STATE =
            SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.BYTE);
    /** Staat hij aan (ook met een lege batterij), voor de knop in zijn scherm. */
    private static final EntityDataAccessor<Boolean> SWITCHED_ON =
            SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.BOOLEAN);
    /** Laadt hij nu op zijn dock? Dan zoemt hij zachtjes in je spel (zie client/robot/RobotChargeHum). */
    private static final EntityDataAccessor<Boolean> CHARGING =
            SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.BOOLEAN);
    /** Zijn printplaat (wat er op de pinnen zit en de bruggen), voor het bordscherm en het model in je spel. */
    private static final EntityDataAccessor<CompoundTag> BOARD =
            SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** Hoe vol zijn batterij is (0 tot 1), voor de schermen. */
    private static final EntityDataAccessor<Float> BATTERY =
            SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.FLOAT);
    /** Hoeveel Robot Ammo er in zijn geweer zit (hoofdstuk 24), voor zijn scherm. */
    private static final EntityDataAccessor<Integer> AMMO =
            SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.INT);

    protected @Nullable UUID owner;
    protected String ownerName = "";
    /** Zijn printplaat zoals hij het laatst uit BOARD gelezen werd (zodat we dat niet elke keer opnieuw doen). */
    private BotBoard board = BotBoard.EMPTY;
    private @Nullable CompoundTag boardTag;
    /** Staat hij aan (zijn eigenaar zette hem aan)? Een nieuwe staat uit. */
    protected boolean active;
    /** Hoeveel stroom er in zijn batterij zit (hoogstens {@link #maxBattery()}). */
    protected double battery;
    /** Zijn dock, of null als hij (nog) geen thuis heeft. */
    protected @Nullable BlockPos home;
    /** Zijn brein (hoofdstuk 23): hier draait zijn programma, alleen op de server. */
    protected final BotBrain brain;
    /** Tot wanneer (speltijd) hij neerligt na een treffer van de Ion Blaster (hoofdstuk 24); 0 = hij staat. */
    protected long downUntil;

    protected BotEntity(EntityType<? extends BotEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        brain = new BotBrain(this, createBody());
    }

    /** Zijn lijf: hoe zijn programma hem laat bewegen (lopen of vliegen). */
    protected abstract BotBody createBody();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, (byte) RobotState.STANDBY.ordinal());
        builder.define(BATTERY, 1.0f);
        builder.define(SWITCHED_ON, false);
        builder.define(CHARGING, false);
        builder.define(BOARD, new CompoundTag());
        builder.define(AMMO, 0);
    }

    // ================================================================ Wat hij is

    /** "a guard robot" of "a security drone", zoals in een zin (voor meldingen). */
    protected abstract String noun();

    /** "robot" of "drone": het korte woord. */
    protected abstract String kindWord();

    /** Het item waarin hij verandert als je hem oppakt (met alles erop en eraan). */
    public abstract ItemStack toItem();

    /** Hij wordt opgepakt: zijn dock is weer vrij. */
    public abstract void leaveHome();

    /** Zijn printplaat: wat er op zijn 8 pinnen gesoldeerd is (ook in je spel). */
    @Override
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
    @Override
    public void setBoard(BotBoard board) {
        entityData.set(BOARD, (CompoundTag) BotBoard.CODEC.encodeStart(NbtOps.INSTANCE, board).getOrThrow());
    }

    // ================================================================ Kogels (hoofdstuk 24)

    /** Hoeveel Robot Ammo er in zijn geweer zit (ook in je spel). */
    @Override
    public int ammo() {
        return entityData.get(AMMO);
    }

    /** Zoveel kogels (hooguit MAX_AMMO). */
    @Override
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
        String name = displayName();
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

    /** Zijn naam ("Guard 1"), of wat hij is als hij er (nog) geen heeft ("The robot"). */
    public String displayName() {
        return hasCustomName() ? getCustomName().getString() : "The " + kindWord();
    }

    // ================================================================ Batterij, aan en uit

    /** Hoe het met hem gaat. Op de server uitgerekend, in je spel meegestuurd. */
    @Override
    public RobotState state() {
        return RobotState.byIndex(entityData.get(STATE));
    }

    /** Hoeveel stroom er in zijn batterij zit. */
    @Override
    public double battery() {
        return battery;
    }

    /** Hoe vol zijn batterij is, van 0 tot 1 (ook in je spel). */
    @Override
    public float batteryFraction() {
        return entityData.get(BATTERY);
    }

    /** Zet de batterij op zoveel stroom (voor tests en het oppakken). */
    @Override
    public void setBattery(double energy) {
        battery = Math.max(0, Math.min(maxBattery(), energy));
        updateState();
    }

    /** Laadt hij nu op zijn dock? (Ook in je spel: dan zoemt hij.) */
    public boolean isCharging() {
        return entityData.get(CHARGING);
    }

    /** Staat hij aan? (Ook in je spel.) */
    @Override
    public boolean isActive() {
        return level().isClientSide ? entityData.get(SWITCHED_ON) : active;
    }

    /**
     * Zet hem aan of uit. Aan: hij gaat van zijn dock af (zie {@link #switched}). Uit: hij gaat terug naar zijn dock
     * en laadt daar. Aanzetten met een soldeerbrug aan een module geeft kortsluiting (zie {@link #shortCircuit}): dan
     * blijft hij uit en geeft dit false.
     */
    @Override
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
        switched(on);
        updateState();
        return true;
    }

    /** Net aan- of uitgezet: een robot stapt uit zijn dock, een drone stijgt op. */
    protected abstract void switched(boolean on);

    // ================================================================ Neer (hoofdstuk 24)

    /**
     * Geraakt door de Ion Blaster: hij valt neer ({@link RobotState#DOWN}). Zijn programma stopt, hij beweegt niet en
     * gebruikt geen stroom, en na {@code ticks} staat hij weer op (aan: dan begint zijn programma weer bij when
     * started). Nog eens raken terwijl hij ligt, begint de tijd opnieuw.
     */
    public void knockDown(int ticks) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        downUntil = level.getGameTime() + Math.max(1, ticks);
        // In een training (hoofdstuk 26) blijft hij liggen tot die voorbij is (zie TrainingBots).
        com.senne.starktech.security.SecurityCoreBlockEntity core = com.senne.starktech.security.SecurityCores.coreAt(level, blockPosition());
        com.senne.starktech.training.Training.Session session = core == null ? null
                : com.senne.starktech.training.Training.at(level, core.getBlockPos());
        if (session != null && session.running()) {
            downUntil = Long.MAX_VALUE;
            session.addDowned(getUUID());
        }
        brain.stop();
        getNavigation().stop();
        knockedDown();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.5, getZ(), 30, 0.3, 0.6, 0.3, 0.2);
        playSound(ModSounds.ROBOT_HURT.get(), 0.8f, 1.4f);
        updateState();
    }

    /** Net neergeschoten (een robot stapt dan niet meer uit zijn dock; een drone valt). */
    protected void knockedDown() {}

    /** Hij staat weer op (na een training waarin hij neerging, hoofdstuk 26). */
    public void standUp() {
        if (downUntil > 0) {
            downUntil = 0;
            updateState();
        }
    }

    /** Ligt hij neer (na de Ion Blaster)? (Ook in je spel.) */
    @Override
    public boolean isDown() {
        return level().isClientSide ? state() == RobotState.DOWN : downUntil > 0;
    }

    // ================================================================ Zijn programma (hoofdstuk 23)

    /** Zijn brein: het programma dat hij draait (zie BotBrain). Alleen op de server bezig. */
    @Override
    public BotBrain brain() {
        return brain;
    }

    /** Het programma dat hij draait, of null. */
    @Override
    public @Nullable LogicProgram program() {
        return brain.program();
    }

    /** Een ander programma (of geen). Staat hij aan, dan begint het meteen bij when started. */
    @Override
    public void setProgram(@Nullable LogicProgram program) {
        brain.setProgram(program);
    }

    /**
     * Kortsluiting (hoofdstuk 22, jouw antwoord: "heb je het niet gezien en je gebruikt de robot, dan kortsluiting:
     * de robot of de onderdelen kunnen afbranden"). Elke soldeerbrug aan een module brandt door: de modules op die
     * pinnen worden Burnt Modules en de brug is weg. Vonken, rook en een knal; met de kans {@code burnChance} brandt
     * hij helemaal af (hij is dan echt kapot). De eigenaar hoort het van E.D.I.T.H., en het logboek van de kern waar
     * hij staat krijgt een regel.
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
        double middle = getBbHeight() * 0.63;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + middle, getZ(), 40, 0.35, 0.5, 0.35, 0.25);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + middle + 0.2, getZ(), 12, 0.25, 0.4, 0.25, 0.02);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.6f);
        playSound(ModSounds.ROBOT_HURT.get(), 1.0f, 0.8f);
        boolean burnsDown = random.nextFloat() < StarkConfig.Robots.BURN_CHANCE;
        String noun = noun();
        String name = hasCustomName() ? getCustomName().getString() : Character.toUpperCase(noun.charAt(0)) + noun.substring(1);
        StringBuilder pins = new StringBuilder();
        for (int pin : burnt) {
            pins.append(pins.isEmpty() ? "" : burnt.last() == pin ? " and " : ", ").append(pin);
        }
        String text = "Short circuit in " + name + ": " + (burnt.size() == 1 ? "pin " : "pins ") + pins + " burnt out"
                + (burnsDown ? ", and the " + kindWord() + " burnt down" : "");
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

    // ================================================================ Zijn dock

    /** Zijn dock, of null. */
    @Override
    public @Nullable BlockPos home() {
        return home;
    }

    public void setHome(@Nullable BlockPos home) {
        this.home = home == null ? null : home.immutable();
    }

    /** Hoeveel zon er op zijn rug valt, van 0 tot 1 (voor zijn Solar Panel-module). */
    @Override
    public float sunlight(ServerLevel level) {
        return com.senne.starktech.station.SolarMath.sunlight(level, blockPosition().above());
    }

    /** Staat hij op zijn dock? */
    public boolean isOnDock() {
        return home != null && blockPosition().equals(home);
    }

    /** Is zijn dock er nog, en is het nog van hem? (Om de twee seconden nagekeken.) */
    protected abstract boolean homeIsMine(ServerLevel level);

    /** Zoveel stroom per tick als hij aan staat, en er nog bij als hij beweegt. */
    protected abstract double drainOn();

    protected abstract double drainMoving();

    /** Zoveel laadt zijn dock per tick. */
    protected abstract double dockChargeRate();

    /** Beweegt hij nu (dat kost extra stroom)? */
    protected abstract boolean isMoving();

    /** Elke tick na het laden: naar zijn dock toe als hij uit staat (netjes gaan staan, of landen). */
    protected abstract void dockTick(ServerLevel level);

    // ================================================================ Elke tick

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            serverTick(level);
        }
    }

    /** Elke tick op de server: is zijn dock er nog, laden, stroom gebruiken, naar zijn dock, en zijn programma. */
    private void serverTick(ServerLevel level) {
        if (downUntil > 0) {
            if (level.getGameTime() >= downUntil) {
                downUntil = 0; // hij staat weer op (updateState hieronder laat hem weer opstarten)
            } else {
                getNavigation().stop();
                if (tickCount % 20 == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 4, 0.4, 0.1, 0.4, 0.05);
                }
            }
        }
        if (home != null && tickCount % 40 == 0 && level.isLoaded(home) && !homeIsMine(level)) {
            home = null; // zijn dock is weg, of er woont een ander
        }
        double max = maxBattery();
        boolean charging = false;
        if (isOnDock() && battery < max) {
            double charge = Math.min(dockChargeRate(), max - battery);
            if (StarkGrid.pay(level, home, charge)) {
                battery += charge;
                charging = true;
            }
        }
        if (entityData.get(CHARGING) != charging) {
            entityData.set(CHARGING, charging);
        }
        // De Solar Panel-module (hoofdstuk 24): laadt in de zon, ook als hij uit staat (één keer per seconde).
        if (tickCount % 20 == 0 && battery < max && board().has(BotModule.SOLAR_PANEL)) {
            battery = Math.min(max, battery + 20 * StarkConfig.Robots.SOLAR_CHARGE * sunlight(level));
        }
        boolean moving = isMoving();
        if (battery > 0 && (active || moving) && downUntil == 0) {
            battery = Math.max(0, battery - (active ? drainOn() : 0) - (moving ? drainMoving() : 0));
        }
        if (battery <= 0) {
            getNavigation().stop();
        }
        dockTick(level);
        // Zijn programma (hoofdstuk 23): alleen als hij aan staat, stroom heeft en niet neerligt. Anders stopt het, en
        // de volgende keer begint het weer bij when started.
        if (active && battery > 0 && downUntil == 0 && brain.hasProgram()) {
            brain.tick(level);
        } else {
            brain.stop();
        }
        updateState();
        // Om de seconde melden bij BotNetwork (de app Robots leest daaruit). Nog geen naam? Dan krijgt hij er een.
        if (tickCount % 20 == 1 && !isDeadOrDying()) {
            BotNetwork network = BotNetwork.get(level);
            if (!hasCustomName()) {
                setCustomName(Component.literal(network.freeName(owner, programKind())));
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
        if (held.getItem() instanceof RobotAmmoItem && !player.isSecondaryUseActive()) {
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

    /** Zet wat je spel moet weten (toestand en batterij) klaar om mee te sturen, als het veranderd is. */
    protected void updateState() {
        updateState(true);
    }

    /** Hetzelfde; {@code sounds} = false bij het laden en neerzetten (dan geen opstartgeluid). */
    protected void updateState(boolean sounds) {
        RobotState state = battery <= 0 ? RobotState.NO_POWER : downUntil > 0 ? RobotState.DOWN
                : active ? RobotState.ACTIVE : RobotState.STANDBY;
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

    /** Op zijn dock duwt niemand hem eraf. */
    @Override
    public boolean isPushable() {
        return !isOnDock() && super.isPushable();
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
        if (downUntil > 0) {
            // Neer tot het einde van een training: die bestaat na opnieuw laden niet meer, dus dan gewoon de gewone tijd.
            long left = downUntil == Long.MAX_VALUE ? Math.round(StarkConfig.Robots.ION_DOWN_SECONDS * 20)
                    : downUntil - level().getGameTime();
            tag.putLong("DownTicks", Math.max(1, left));
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setBoard(tag.contains("Board") ? BotBoard.CODEC.parse(NbtOps.INSTANCE, tag.get("Board")).result().orElse(BotBoard.EMPTY)
                : BotBoard.EMPTY);
        // Na het laden van leven en eigenschappen: wat van zijn lijf komt (de config kan intussen andere getallen hebben).
        readBody(tag);
        active = tag.getBoolean("Active");
        battery = tag.contains("Battery") ? Math.min(tag.getDouble("Battery"), maxBattery()) : maxBattery();
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
        brain.setProgram(tag.contains("Program") ? LogicProgram.load(tag.getCompound("Program")) : null);
        brain.body().setFacing(tag.contains("Facing") ? Direction.from2DDataValue(tag.getByte("Facing")) : null);
        setAmmo(tag.getInt("Ammo"));
        downUntil = tag.contains("DownTicks") ? level().getGameTime() + tag.getLong("DownTicks") : 0;
        updateState(false);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
    }

    /** Wat zijn eigen soort bewaart en eerst terug moet zijn (bij een robot: zijn onderdelen). */
    protected void readBody(CompoundTag tag) {}

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
}
