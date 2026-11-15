package com.senne.imttech.car;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.program.LogicException;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.security.BaseTrust;
import com.senne.imttech.weapons.EmpTarget;
import com.senne.imttech.weapons.EnergyCellItem;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

/**
 * De RC Car (hoofdstuk 108, jouw wens: "je programmeert een nieuw soort robot (rc car); dit autootje kan rijden door
 * wat jij programmeert, bijvoorbeeld: herhaal breek voor jou, stap naar voor, breek boven jou... dit maakt dan een
 * lange strip mine").
 *
 * Een klein autootje met een boor vooraan en een kofferbak. Het doet niets uit zichzelf, en een nieuwe auto heeft geen
 * programma (jouw wens: "ik wil dat je de rc car vanaf 0 moet programmeren"): jij schrijft het (sneak + rechtsklik met
 * een lege hand, Program), en start dan het hoofdprogramma of een van zijn functies, op zijn scherm of van ver met de RC
 * Controller. Zie {@link CarProgram} (de taal), {@link CarBrain} (wat er loopt),
 * {@link CarMove} (rijden, draaien, breken, plaatsen) en {@link CarWork} (wat hij in de wereld doet).
 *
 * <ul>
 *   <li><b>Stroom</b>: hij rijdt op een Energy Cell (dezelfde als van de lasergeweren; die laad je in het Charging
 *       Station). Rechtsklik op de auto met een cel: die gaat erin, en de oude krijg je terug. Elke stap, elk blok
 *       breken en plaatsen kost wat (zie [rcCar] in de config). Zonder cel, of met een lege, doet hij niets.</li>
 *   <li><b>Boorkop</b> (jouw wens: "zorg dat je de drill head kunt upgraden"): een nieuwe auto heeft de ijzeren kop, en
 *       die is traag. Rechtsklik op de auto met een betere kop (steel, titanium, netherite): die zit erop, en de oude
 *       krijg je terug. Zie {@link DrillHead}.</li>
 *   <li><b>Kofferbak</b>: 18 vakjes. Wat hij breekt gaat erin; wat hij plaatst komt eruit. Rechtsklik op de auto:
 *       de kofferbak gaat open (voor iedereen, zoals een kist).</li>
 *   <li><b>Oppakken</b> (jouw wens: "je kunt deze rc car ook oppakken en in je inventory zetten"): de knop Pick up in
 *       zijn scherm, of sneak + slaan. Zijn programma, zijn naam, zijn cel en zijn kofferbak gaan mee in het item.</li>
 *   <li><b>Van wie</b>: wie hem neerzette is zijn eigenaar; samen met wie in die basis vertrouwd is, mag die hem
 *       instellen (dezelfde regel als bij een guard robot, zie BaseTrust). In een beschermde zone breekt en plaatst
 *       hij alleen als zijn eigenaar dat daar zelf mag.</li>
 * </ul>
 * Hij kan niet kapot: het is een stevig stuk gereedschap, en je programma zit erin. Een EMP-granaat legt hem wel
 * stil (zijn klus stopt).
 */
public class RcCarEntity extends Mob implements BaseTrust.Placed, EmpTarget {
    /** Zoveel vakjes heeft zijn kofferbak (twee rijen, zoals een kleine kist). */
    public static final int TRUNK_SIZE = 18;

    /** Wat hij nu doet ("" = niets, CarBrain.MAIN, CarBrain.MANUAL of de naam van een functie), voor zijn scherm. */
    private static final EntityDataAccessor<String> JOB = SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.STRING);
    /** Hoe vol zijn energiecel is, van 0 tot 1; -1 = hij heeft er geen. */
    private static final EntityDataAccessor<Float> BATTERY = SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.FLOAT);
    /** De functies van zijn programma (de defs zonder parameters), met een nieuwe regel ertussen: de knoppen. */
    private static final EntityDataAccessor<String> FUNCTIONS = SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.STRING);
    /** Heeft zijn programma een hoofdprogramma (when started)? Dan doet de knop Start iets. */
    private static final EntityDataAccessor<Boolean> HAS_MAIN = SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.BOOLEAN);
    /** Het laatste dat niet lukte (of wat er mis is met zijn programma), voor zijn scherm; leeg = niets. */
    private static final EntityDataAccessor<String> NOTE = SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.STRING);
    /** Boort hij nu? Dan draait zijn boor in je spel. */
    private static final EntityDataAccessor<Boolean> DRILLING = SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.BOOLEAN);
    /** Hoeveel voorwerpen er in zijn kofferbak liggen, voor zijn scherm. */
    private static final EntityDataAccessor<Integer> ITEMS = SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.INT);
    /** Zijn boorkop (het nummer van de DrillHead), voor zijn model en zijn scherm. */
    private static final EntityDataAccessor<Integer> DRILL = SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.INT);
    /** Hoe hoog zijn arm staat (hoofdstuk 113): 0 = het blok vóór hem, 1 of 2 blokken hoger. Voor zijn model. */
    private static final EntityDataAccessor<Integer> ARM = SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.INT);

    private @Nullable UUID owner;
    private String ownerName = "";
    /** Wie de klus startte die nu loopt (of het laatst liep); null = niemand in het bijzonder: dan telt zijn eigenaar. */
    private @Nullable UUID driver;
    private String driverName = "";
    /** Zijn vaste nummer: daaraan herkent een RC Controller hem, ook nadat je hem oppakte en weer neerzette. */
    private UUID link = UUID.randomUUID();
    /** De code van zijn programma, zoals jij hem schreef (een nieuwe auto heeft er geen). */
    private String source = CarProgram.EMPTY;
    private final SimpleContainer trunk = new SimpleContainer(TRUNK_SIZE) {
        /** De kofferbak gaat dicht als de auto weg is of te ver van je wegrijdt. */
        @Override
        public boolean stillValid(Player player) {
            return isAlive() && player.distanceToSqr(RcCarEntity.this) <= 64.0;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return fitsInTrunk(stack);
        }
    };
    /** Zijn energiecel (of leeg). */
    private ItemStack cell = ItemStack.EMPTY;
    private final CarMove body;
    private final CarBrain brain;
    /** Tot wanneer (speltijd) hij stilligt na een EMP-granaat; 0 = hij doet het. */
    private long jammedUntil;
    /** De Storage Interface waar hij aan hangt (hoofdstuk 113), of null: die geeft hem zijn bestellingen. */
    private @Nullable net.minecraft.core.GlobalPos station;
    /**
     * Alleen in je spel: hoe hoog zijn arm nu getekend wordt, en de vorige tick. Hij schuift vloeiend naar zijn stand
     * ({@link #arm}), zodat je de arm ziet bewegen (zie RcCarModel).
     */
    public float armShown;
    public float armShownO;

    public RcCarEntity(EntityType<? extends RcCarEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        body = new CarMove(this);
        brain = new CarBrain(this, body);
        if (!level.isClientSide) {
            applyProgram();
        }
    }

    /** Hij rijdt een heel blok op zonder te springen, en niets duwt hem weg. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(JOB, "");
        builder.define(BATTERY, -1.0f);
        builder.define(FUNCTIONS, "");
        builder.define(HAS_MAIN, false);
        builder.define(NOTE, "");
        builder.define(DRILLING, false);
        builder.define(ITEMS, 0);
        builder.define(DRILL, DrillHead.IRON.ordinal());
        builder.define(ARM, 0);
    }

    // ================================================================ Zijn programma

    public CarBrain brain() {
        return brain;
    }

    public CarMove body() {
        return body;
    }

    /** De code van zijn programma. */
    public String source() {
        return source;
    }

    /**
     * Nieuwe code (uit de editor). Klopt hij niet, dan blijft de oude en krijg je de fout (Engels, met het regelnummer)
     * terug; anders null. Wat hij deed, stopt.
     */
    public @Nullable String setSource(String code) {
        LogicProgram compiled;
        try {
            compiled = CarProgram.compile(code);
        } catch (LogicException e) {
            return e.describe();
        }
        source = code;
        brain.setProgram(compiled);
        showProgram(compiled, "");
        return null;
    }

    /** Maakt van de bewaarde code zijn programma (na het laden, of uit zijn item). Klopt de code niet meer, dan zegt zijn scherm dat. */
    private void applyProgram() {
        String problem = CarProgram.problem(source);
        LogicProgram compiled = problem == null ? CarProgram.compileOrNull(source) : null;
        brain.setProgram(compiled);
        showProgram(compiled, problem == null ? "" : "Program: " + problem);
    }

    /** Zet de knoppen van dit programma (zijn functies, en of Start iets doet) klaar voor je spel. */
    private void showProgram(@Nullable LogicProgram program, String note) {
        entityData.set(FUNCTIONS, String.join("\n", CarProgram.functions(program)));
        entityData.set(HAS_MAIN, CarProgram.hasMain(program));
        entityData.set(NOTE, note);
    }

    /** De functies van zijn programma: de knoppen op zijn scherm en op de RC Controller (ook in je spel). */
    public List<String> functions() {
        String names = entityData.get(FUNCTIONS);
        return names.isEmpty() ? List.of() : List.of(names.split("\n"));
    }

    /** Heeft zijn programma een hoofdprogramma (when started)? (Ook in je spel.) */
    public boolean hasMain() {
        return entityData.get(HAS_MAIN);
    }

    /** Wat hij nu doet: "" = niets, CarBrain.MAIN, CarBrain.MANUAL of de naam van een functie. (Ook in je spel.) */
    public String job() {
        return entityData.get(JOB);
    }

    /** Het laatste dat niet lukte, of wat er mis is met zijn programma ("" = niets). (Ook in je spel.) */
    public String note() {
        return entityData.get(NOTE);
    }

    /** Onthoud wat er niet lukte, voor zijn scherm. */
    public void setNote(String note) {
        String kept = note.length() > 120 ? note.substring(0, 120) : note;
        if (!entityData.get(NOTE).equals(kept)) {
            entityData.set(NOTE, kept);
        }
    }

    /** Boort hij nu? (Ook in je spel: dan draait zijn boor.) */
    public boolean isDrilling() {
        return entityData.get(DRILLING);
    }

    // ================================================================ Zijn energiecel

    /** Zijn energiecel (leeg als hij er geen heeft). */
    public ItemStack cell() {
        return cell;
    }

    public boolean hasCell() {
        return !cell.isEmpty();
    }

    /** Een andere cel erin (of geen); de oude krijg je terug. */
    public ItemStack swapCell(ItemStack fresh) {
        ItemStack old = cell;
        cell = fresh;
        syncBattery();
        return old;
    }

    /** Hoe vol zijn cel is, van 0 tot 100 (0 zonder cel). */
    public float cellPercent() {
        return cell.isEmpty() ? 0 : EnergyCellItem.charge(cell);
    }

    /** Hoe vol zijn cel is, van 0 tot 1, of -1 als hij er geen heeft. (Ook in je spel.) */
    public float batteryFraction() {
        return entityData.get(BATTERY);
    }

    /** Hoeveel stroom (FE) er nog in zijn cel zit. */
    public double energy() {
        return cellPercent() / EnergyCellItem.FULL * ImtConfig.Weapons.CELL_CHARGE_ENERGY;
    }

    /** Heeft hij nog zoveel stroom? */
    public boolean hasEnergy(double amount) {
        return !cell.isEmpty() && energy() + 1.0e-6 >= amount;
    }

    /** Betaalt zoveel stroom uit zijn cel. Geeft false (en betaalt niets) als er niet genoeg in zit. */
    public boolean spend(double amount) {
        if (!hasEnergy(amount)) {
            return false;
        }
        if (amount > 0) {
            EnergyCellItem.setCharge(cell, (float) (EnergyCellItem.charge(cell)
                    - amount / Math.max(1.0, ImtConfig.Weapons.CELL_CHARGE_ENERGY) * EnergyCellItem.FULL));
        }
        return true;
    }

    /**
     * Stuurt naar je spel hoe vol de cel is, in stapjes van een tiende procent (zo gaat er niet elke tick een bericht
     * weg). Een cel waar minder dan een twintigste procent in zit (te weinig voor nog één blok rijden), is voor je spel
     * gewoon leeg.
     */
    private void syncBattery() {
        float fraction = cell.isEmpty() ? -1.0f : Math.round(cellPercent() / EnergyCellItem.FULL * 1000.0f) / 1000.0f;
        if (entityData.get(BATTERY) != fraction) {
            entityData.set(BATTERY, fraction);
        }
    }

    // ================================================================ Zijn boorkop

    /** Zijn boorkop. (Ook in je spel: zijn model en zijn scherm tonen hem.) */
    public DrillHead drill() {
        return DrillHead.byIndex(entityData.get(DRILL));
    }

    /** Een andere boorkop erop. */
    public void setDrill(DrillHead head) {
        entityData.set(DRILL, head.ordinal());
    }

    // ================================================================ Zijn arm (hoofdstuk 113)

    /**
     * Hoe hoog zijn arm staat: 0 = het blok vóór hem, 1 of 2 blokken hoger (jouw keuze: een arm die je programmeert, tot
     * drie kisten hoog). Op die hoogte breekt, plaatst en kijkt hij, en opent hij een kist. (Ook in je spel: zijn model.)
     */
    public int arm() {
        return entityData.get(ARM);
    }

    public void setArm(int level) {
        entityData.set(ARM, Math.max(0, Math.min(com.senne.imttech.logic.program.Op.MAX_ARM, level)));
    }

    // ================================================================ Zijn Storage Interface (hoofdstuk 113)

    /**
     * De Storage Interface waar hij aan hangt, of null. Die interface onthoudt wat hij in de kisten ziet, en geeft hem
     * zijn bestellingen. De interface zelf houdt de lijst van zijn auto's bij (op hun vaste nummer); zet je de auto
     * opnieuw neer, dan zegt de interface hem binnen een halve seconde weer bij wie hij hoort.
     */
    public @Nullable net.minecraft.core.GlobalPos station() {
        return station;
    }

    public void setStation(@Nullable net.minecraft.core.GlobalPos station) {
        this.station = station;
    }

    // ================================================================ Zijn kofferbak

    public SimpleContainer trunk() {
        return trunk;
    }

    /** Hoeveel voorwerpen er in zijn kofferbak liggen. (Ook in je spel.) */
    public int trunkItems() {
        return entityData.get(ITEMS);
    }

    /**
     * Mag dit in de kofferbak? Geen RC Car (anders zit een auto in een auto in een auto) en niets dat zelf een kist is
     * (een shulker box, een volle Ammo Box of pouch), net zoals een shulker box en een rugzak dat niet aannemen.
     */
    public static boolean fitsInTrunk(ItemStack stack) {
        // Ook de regel van de rugzakken (BackpackItem.mayHold): geen volle Ammo Box of pouch, niets dat zelf spullen
        // bewaart. Als je de auto oppakt, is hij zelf een doos: anders zaten er dozen in dozen in dozen.
        return !(stack.getItem() instanceof RcCarItem) && stack.canFitInsideContainerItems()
                && com.senne.imttech.backpack.BackpackItem.mayHold(stack);
    }

    // ================================================================ Het item (oppakken en neerzetten)

    /** Neem over wat er in een RC Car-item zit: naam, programma, cel, kofferbak en boorkop. */
    public void applyData(RcCarData data) {
        setCustomName(data.name().isEmpty() ? null : Component.literal(data.name()));
        source = data.source();
        setDrill(data.drill());
        link = data.link().orElseGet(UUID::randomUUID);
        cell = data.cell() < 0 ? ItemStack.EMPTY : EnergyCellItem.withCharge(data.cell());
        data.trunk().copyInto(trunk.getItems()); // elk voorwerp weer in zijn eigen vakje
        applyProgram();
        syncBattery();
    }

    /** Wat er in zijn item komt als je hem oppakt. */
    public RcCarData toData() {
        return new RcCarData(hasCustomName() ? getCustomName().getString() : "", source, java.util.Optional.of(link),
                cell.isEmpty() ? RcCarData.NO_CELL : EnergyCellItem.charge(cell), ItemContainerContents.fromItems(trunk.getItems()),
                drill());
    }

    /** Het item waarin hij verandert als je hem oppakt (met alles erin). */
    public ItemStack toItem() {
        return RcCarItem.create(toData());
    }

    /** Zijn vaste nummer (waaraan een RC Controller hem herkent). */
    public UUID link() {
        return link;
    }

    /** Een nieuw nummer (als er al een auto met dit nummer rijdt: een kopie uit creative). */
    public void newLink() {
        link = UUID.randomUUID();
    }

    /** Zijn naam ("Digger"), of "RC Car" als hij er geen heeft. */
    public String displayName() {
        return hasCustomName() ? getCustomName().getString() : "RC Car";
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

    /**
     * Zegt iets tegen wie zijn klus startte (als die op de server is; anders tegen zijn eigenaar): in de chat, of
     * ({@code bar}) klein boven je werkbalk.
     */
    public void tell(Component message, boolean bar) {
        ServerPlayer player = actorPlayer();
        if (player == null) {
            player = ownerPlayer();
        }
        if (player != null) {
            player.displayClientMessage(message, bar);
        }
    }

    // ================================================================ Namens wie hij werkt

    /** Onthoud wie deze klus startte (null = niemand in het bijzonder: dan telt zijn eigenaar). */
    void setDriver(@Nullable ServerPlayer player) {
        driver = player == null ? null : player.getUUID();
        driverName = player == null ? "" : player.getGameProfile().getName();
    }

    /**
     * Zoals hierboven, met het nummer en de naam van de speler: wie iets bestelde bij een Storage Interface (hoofdstuk
     * 113) hoeft niet meer op de server te zijn als de auto eraan begint.
     */
    void setDriver(@Nullable UUID player, String name) {
        driver = player;
        driverName = player == null ? "" : name;
    }

    /**
     * Namens wie hij nu blokken breekt en plaatst: wie zijn klus startte, anders zijn eigenaar (null = niemand). Niet
     * altijd zijn eigenaar: wie op de knop drukt, is verantwoordelijk. Anders kon iemand die jouw auto mag besturen ermee
     * door de muur van jouw beschermde zone boren. (Gevonden door de review.)
     */
    public @Nullable UUID actorId() {
        return driver != null ? driver : owner;
    }

    /** De naam van die speler ("" als er niemand is). */
    public String actorName() {
        return driver != null ? driverName : ownerName;
    }

    /** Die speler, als hij nu op de server is; anders null. */
    public @Nullable ServerPlayer actorPlayer() {
        UUID id = actorId();
        return id != null && level() instanceof ServerLevel level ? level.getServer().getPlayerList().getPlayer(id) : null;
    }

    /** Zijn eigenaar, als die nu op de server is; anders null. */
    public @Nullable ServerPlayer ownerPlayer() {
        return owner != null && level() instanceof ServerLevel level ? level.getServer().getPlayerList().getPlayer(owner) : null;
    }

    // ================================================================ Elke tick

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            serverTick(level);
        } else {
            // In je spel: de arm schuift naar zijn stand (een derde van de rest per tick).
            armShownO = armShown;
            armShown += (arm() - armShown) * 0.34f;
            if (Math.abs(arm() - armShown) < 0.01f) {
                armShown = arm();
            }
        }
    }

    private void serverTick(ServerLevel level) {
        if (jammedUntil > 0 && level.getGameTime() >= jammedUntil) {
            jammedUntil = 0;
        }
        if (jammedUntil > 0) {
            if (tickCount % 20 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 3, 0.3, 0.1, 0.3, 0.05);
            }
        } else {
            brain.tick(level);
        }
        // Wat je spel moet weten, als het veranderd is.
        String job = brain.job();
        if (!entityData.get(JOB).equals(job)) {
            entityData.set(JOB, job);
        }
        if (entityData.get(DRILLING) != body.drilling()) {
            entityData.set(DRILLING, body.drilling());
        }
        syncBattery();
        if (tickCount % 10 == 0) {
            int items = CarWork.itemsIn(trunk);
            if (entityData.get(ITEMS) != items) {
                entityData.set(ITEMS, items);
            }
        }
        if (tickCount % 20 == 1 && isAlive()) {
            RcCars.register(this); // zo vindt een RC Controller hem
        }
    }

    /** Een EMP-granaat (hoofdstuk 35): zijn klus stopt, en hij ligt even stil. */
    @Override
    public void onEmp(ServerLevel level, int ticks) {
        brain.stop();
        jammedUntil = level.getGameTime() + Math.max(1, ticks);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 20, 0.3, 0.2, 0.3, 0.2);
    }

    /** Ligt hij stil na een EMP-granaat? */
    public boolean isJammed() {
        return jammedUntil > 0;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            brain.stop(); // de barstjes van het boren gaan weg
            RcCars.unregister(this);
        }
        super.remove(reason);
    }

    // ================================================================ Klikken

    /**
     * Rechtsklik met een RC Controller: koppelen. Met een Energy Cell: de cel erin. Met een boorkop: die erop. Sneak +
     * rechtsklik met een lege hand: zijn scherm (als je hem mag instellen). Anders: de kofferbak gaat open.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof RcControllerItem) {
            if (player instanceof ServerPlayer serverPlayer) {
                RcCars.link(serverPlayer, this, held);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (held.getItem() instanceof EnergyCellItem && !player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer serverPlayer) {
                RcCars.loadCell(serverPlayer, this, hand);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (held.getItem() instanceof DrillHeadItem && !player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer serverPlayer) {
                RcCars.swapDrill(serverPlayer, this, hand);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            if (!held.isEmpty()) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                RcCars.open(serverPlayer, this, false);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            RcCars.openTrunk(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /**
     * Hij kan niet kapot (alleen /kill en de leegte). Sneak + slaan door wie hem mag oppakken (zie RcCars.mayRemove):
     * oppakken, zoals je een minecart oppakt.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (source.getDirectEntity() instanceof ServerPlayer player && player.isShiftKeyDown() && isAlive()
                && RcCars.mayRemove(player, this)) {
            RcCars.pickUp(player, this);
        }
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /**
     * Echt weg (/kill, de leegte): de auto valt als item op de grond, met alles erin. Dit staat in dropEquipment en niet
     * in dropCustomDeathLoot: dat laatste slaat Minecraft over als de spelregel doMobLoot uit staat, en dan was de auto
     * met je programma en je spullen gewoon verdwenen. (Gevonden door de review.)
     */
    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        spawnAtLocation(toItem());
        trunk.clearContent();
        cell = ItemStack.EMPTY;
    }

    // ================================================================ Een machine, geen dier

    /** Niets duwt hem van zijn plek (hij moet precies in het midden van zijn blok blijven). */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
        // Hij duwt ook niemand weg.
    }

    /**
     * Stromend water duwt hem niet van zijn plek: terwijl hij boort of stilstaat, stuurt niets hem bij, en dan dreef hij
     * een half blok weg en klopte zijn gang niet meer. (Gevonden door de review.)
     */
    @SuppressWarnings("deprecation") // de versie met het soort vloeistof vraagt het aan deze
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** Vallen doet hem niets (na mine_down() zakt hij in het gat). */
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    /** Rijden: een zacht, hoog motortje (het servo-geluid van een robot, maar kleiner). */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.ROBOT_STEP.get(), 0.12f, 1.7f + random.nextFloat() * 0.2f);
    }

    // ================================================================ Opslaan

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.putUUID("Link", link);
        tag.putString("Source", source);
        if (!cell.isEmpty()) {
            tag.putFloat("Cell", EnergyCellItem.charge(cell)); // hoe vol (0 tot 100); geen cel = niets
        }
        tag.put("Trunk", ContainerHelper.saveAllItems(new CompoundTag(), trunk.getItems(), registryAccess()));
        tag.putByte("Facing", (byte) body.facing().get2DDataValue());
        tag.putString("DrillHead", drill().getSerializedName());
        tag.putByte("Arm", (byte) arm());
        if (station != null) {
            net.minecraft.core.GlobalPos.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, station)
                    .result().ifPresent(where -> tag.put("Station", where));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        link = tag.hasUUID("Link") ? tag.getUUID("Link") : UUID.randomUUID();
        source = tag.contains("Source") ? tag.getString("Source") : CarProgram.EMPTY;
        setDrill(DrillHead.byId(tag.getString("DrillHead"))); // een auto zonder dit gegeven heeft de ijzeren kop
        cell = tag.contains("Cell") ? EnergyCellItem.withCharge(tag.getFloat("Cell")) : ItemStack.EMPTY;
        trunk.clearContent();
        ContainerHelper.loadAllItems(tag.getCompound("Trunk"), trunk.getItems(), registryAccess());
        body.setFacing(tag.contains("Facing") ? Direction.from2DDataValue(tag.getByte("Facing")) : null);
        setArm(tag.getByte("Arm"));
        armShown = arm(); // net geladen (of een auto in een animatie van How it works): de arm staat er meteen
        armShownO = armShown;
        station = tag.contains("Station") ? net.minecraft.core.GlobalPos.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,
                tag.get("Station")).result().orElse(null) : null;
        applyProgram();
        syncBattery();
        // Alleen voor de animaties van How it works (howto/scenes/CarScenes): een auto die boort. In de wereld zegt zijn
        // lijf dat elke tick zelf (zie serverTick).
        if (tag.contains("Drilling")) {
            entityData.set(DRILLING, tag.getBoolean("Drilling"));
        }
    }
}
