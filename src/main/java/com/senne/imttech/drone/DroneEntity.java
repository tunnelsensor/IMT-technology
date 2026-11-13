package com.senne.imttech.drone;

import com.senne.imttech.arsenal.ArsenalCosts;
import com.senne.imttech.arsenal.ArsenalServer;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtFeature;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.imt.ImtTargeting;
import com.senne.imttech.illusion.ImtIllusions;
import com.senne.imttech.network.BeamEffectPayload;
import com.senne.imttech.network.DroneControlPayload;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.network.DroneControlPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.registry.ModDamageTypes;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.shield.ImtDomes;
import com.senne.imttech.spy.ImtSpy;
import com.senne.imttech.transport.ImtTransport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Een drone. Er zijn drie soorten (zie Mode):
 * - Aanvalsdrones (de "zwerm"): vallen gemarkeerde doelwitten aan en verdwijnen na een minuut.
 * - Beschermende drones: vliegen rond hun eigenaar en schieten op vijanden in de buurt.
 * - Verkenningsdrone: de speler bestuurt hem zelf en kijkt door zijn camera.
 *
 * Een drone denkt alleen op de server. Hij heeft altijd een eigenaar; zonder eigenaar
 * (of als de bril af gaat of leeg is) vliegt hij weg.
 */
public class DroneEntity extends PathfinderMob implements com.senne.imttech.weapons.EmpTarget {
    public enum Mode {
        ATTACK, GUARD, SCOUT,
        /** Fase 6: volgt stiekem iemand en rapporteert (onzichtbaar, valt niet aan). */
        SPY,
        /** Fase 7: vervoert jou, een vracht of iemand anders. */
        TRANSPORT,
        /** Fase 12: onzichtbare drone die een illusie projecteert. */
        ILLUSION,
        /** Fase 15: op weg naar een baan om de planeet (net gebouwd, of klaar met zijn werk). */
        LAUNCH,
        /** Fase 15: teruggeroepen en aan de grond; rechtsklik om hem te repareren. */
        GROUNDED;

        public static Mode byId(int id) {
            Mode[] all = values();
            return all[Math.floorMod(id, all.length)];
        }
    }

    /** Wordt naar de client gestuurd, zodat die de juiste kleur lichtjes kan tekenen. */
    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BYTE);

    /**
     * Het soort "wakker blijven"-kaartje dat een drone aan Minecraft geeft (zie keepWorldAwake). Het verloopt vanzelf
     * 100 ticks (5 seconden) nadat de drone het voor het laatst vernieuwde: is de drone weg, dan gaat dat stuk wereld
     * weer slapen.
     */
    private static final TicketType<ChunkPos> AWAKE = TicketType.create("imttech_drone",
            Comparator.comparingLong(ChunkPos::toLong), 100);

    private @Nullable UUID ownerId;
    /**
     * Als dit gevuld is, jaagt de drone op dit ene wezen en niets anders (de "1 drone op 1
     * doelwit"-aanval). Is het doelwit dood of verdwenen, dan gaat de drone naar huis.
     */
    private @Nullable UUID lockedTargetId;
    /** Welke drone uit het arsenaal dit is, of null voor een drone zonder arsenaal. */
    private @Nullable UUID arsenalId;
    /** De duik uit de lucht (in een boog naar zijn plek), of null als hij niet duikt. */
    private @Nullable SkyDive dive;
    /** Hoe lang hij al aan het opstijgen is (alleen in LAUNCH). */
    private int launchTicks;
    /** Nummer van de drone binnen zijn groep (voor de plek in de formatie). */
    private int slot;
    /** Hoe lang de drone nog leeft; -1 = onbeperkt (beschermende drones). */
    private int lifeTicks = -1;
    private int fireCooldown;
    private @Nullable LivingEntity currentTarget;
    /**
     * Is de rit van deze drone al afgeboekt in het arsenaal (hij hangt weer in zijn baan, of hij is neergehaald)?
     * Dan boekt hij niets meer, wat er daarna ook met hem gebeurt.
     */
    private boolean settled;
    /**
     * Een oude kopie van een drone waarvan de rit al afgeboekt is. Bijvoorbeeld een drone die in een stuk wereld
     * achterbleef dat opgeslagen werd, en die terugkomt als je daar weer komt: in je arsenaal hangt hij al lang weer in
     * zijn baan (zie handBack), of hij is intussen met iets anders bezig. Zo'n kopie vliegt meteen weg, zonder iets te
     * boeken.
     */
    private boolean stale;
    /** Wanneer (speltijd) deze drone voor het laatst iets deed; zo ziet ArsenalServer een drone die stilstaat. */
    private long lastActiveTick;
    /** In welk stuk wereld (chunk) hij zijn laatste "wakker blijven"-kaartje gaf (zie keepWorldAwake). */
    private @Nullable ChunkPos awakeAt;

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
        this.moveControl = new DroneMoveControl(this);
        this.setNoGravity(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, ImtConfig.Drones.HEALTH)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    /** Stelt een nieuwe drone in. Roep dit aan voordat je hem in de wereld zet. */
    public void setup(ServerPlayer owner, Mode mode, int slot, int lifeTicks) {
        this.ownerId = owner.getUUID();
        this.entityData.set(DATA_MODE, (byte) mode.ordinal());
        this.slot = slot;
        this.lifeTicks = lifeTicks;
        this.fireCooldown = 10 + slot * 3; // niet allemaal tegelijk schieten
    }

    /**
     * Een spion en een cloakingdrone vallen niet op: ze worden niet getekend (maar ze zijn er wel
     * echt, dus wie weet waar ze hangen, kan ze uit de lucht slaan).
     *
     * Dit hangt aan hun taak en niet aan setInvisible(): dat vlagje zet Minecraft bij de eerste
     * tick weer uit, omdat er geen onzichtbaarheidsdrankje werkt. Daardoor waren ze vroeger
     * gewoon te zien. De taak (DATA_MODE) kent ook jouw spel, dus daar klopt het ook.
     */
    @Override
    public boolean isInvisible() {
        Mode mode = getMode();
        return mode == Mode.SPY || mode == Mode.ILLUSION || super.isInvisible();
    }

    /** Hoe lang hij nog blijft (ticks); -1 = tot iemand hem terugroept. */
    public void setLifeTicks(int lifeTicks) {
        this.lifeTicks = lifeTicks;
    }

    /** Koppelt deze drone aan een drone uit het arsenaal van zijn eigenaar. */
    public void setArsenalId(@Nullable UUID id) {
        this.arsenalId = id;
        if (!level().isClientSide && isOnAJob()) {
            // Vanaf nu is hij "onderweg"; ArsenalServer houdt in de gaten of hij dat echt blijft (zie onServerTick).
            this.lastActiveTick = level().getGameTime();
            ArsenalServer.seen(this);
        }
    }

    public @Nullable UUID getArsenalId() {
        return arsenalId;
    }

    /**
     * Staat deze drone in het arsenaal als "On a job", en moet hij zich dus nog terugmelden? Niet als hij dat al deed
     * (of neergehaald is), niet als hij een oude kopie is (zie stale), en niet als hij aan de grond op je wacht
     * (GROUNDED: dan staat hij in je arsenaal als "Grounded").
     */
    public boolean isOnAJob() {
        return arsenalId != null && !settled && !stale && getMode() != Mode.GROUNDED;
    }

    /** Wanneer (speltijd) hij voor het laatst iets deed (zie ArsenalServer.onServerTick). */
    public long lastActiveTick() {
        return lastActiveTick;
    }

    /**
     * Deze drone is een oude kopie geworden (zie stale): hij boekt niets meer en vliegt bij zijn volgende tick weg.
     * Dat gebeurt als zijn eigenaar inlogt (ArsenalServer.onLogin): wat er nog als "On a job" stond, hangt dan
     * al weer in zijn baan.
     */
    public void markStale() {
        stale = true;
        ArsenalServer.forget(this);
    }

    /** Deze drone jaagt alleen nog op dit wezen. */
    public void lockOn(LivingEntity target) {
        this.lockedTargetId = target.getUUID();
        this.currentTarget = target;
    }

    public boolean hasLockedTarget() {
        return lockedTargetId != null;
    }

    /**
     * De drone komt uit de lucht: vanaf waar hij nu is duikt hij in een boog naar
     * {@code landing}, snel vanuit de hoogte en rustig afremmend bij aankomst (zie SkyDive). Zo
     * zie je hem echt uit de wolken komen.
     */
    public void startDive(Vec3 landing) {
        this.dive = new SkyDive(position(), landing);
        // Tijdens het duiken schiet hij nog niet.
        this.fireCooldown = Math.max(this.fireCooldown, 15);
    }

    public boolean isDiving() {
        return dive != null;
    }

    /** Waar hij neerkomt als hij nog aan het duiken is, anders waar hij nu is. */
    public Vec3 landingSpot() {
        return dive != null ? dive.to() : position();
    }

    /**
     * Klaar hier: de drone stijgt op en verdwijnt hoog in de lucht, in plaats van ter plekke
     * op te lossen. Pas als hij boven is, meldt hij zich terug in je arsenaal.
     */
    public void departToOrbit(ServerLevel level) {
        if (getMode() == Mode.LAUNCH) {
            return; // al onderweg naar boven
        }
        if (isTransport() && isVehicle()) {
            // Hangt er nog iemand onder? Dan eerst netjes neerzetten, en pas daarna vertrekken
            // (descend roept ons opnieuw aan zodra hij leeg is). Vroeger steeg hij gewoon op,
            // met jou eronder, en liet hij je pas hoog in de lucht los.
            beginLanding();
            return;
        }
        dropCargo(level);
        // Zolang hij stijgt, staat hij als "onderweg" geboekt: je kunt hem nog niet opnieuw sturen. (Een oude kopie,
        // zie stale, boekt niets: die drone hangt in je arsenaal al weer in zijn baan.)
        ServerPlayer owner = ownerAnywhere(level);
        if (arsenalId != null && !settled && !stale && owner != null) {
            ArsenalServer.markDeployed(owner, arsenalId);
        }
        entityData.set(DATA_MODE, (byte) Mode.LAUNCH.ordinal());
        dive = null;
        launchTicks = 0;
        launchFrom = getY();
        currentTarget = null;
        lockedTargetId = null;
        setInvisible(false);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.DRONE_RECALL.get(), SoundSource.NEUTRAL, 0.7f, 1.3f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODE, (byte) Mode.ATTACK.ordinal());
    }

    public Mode getMode() {
        return Mode.byId(this.entityData.get(DATA_MODE));
    }

    public boolean isGuard() {
        return getMode() == Mode.GUARD;
    }

    public boolean isScout() {
        return getMode() == Mode.SCOUT;
    }

    public boolean isSpy() {
        return getMode() == Mode.SPY;
    }

    public boolean isTransport() {
        return getMode() == Mode.TRANSPORT;
    }

    public boolean isIllusion() {
        return getMode() == Mode.ILLUSION;
    }

    /** Wat een transportdrone doet: jou dragen, vracht brengen, iemand oppakken of meevliegen. */
    public enum TransportRole {
        /** Draagt de speler (jij hangt eronder). */
        CARRIER,
        /** Vliegt alleen mee, voor de show. */
        FOLLOWER,
        /** Brengt spullen naar een plek. */
        CARGO,
        /** Pakt een speler/dier/monster op en zet het elders neer. */
        GRABBER
    }

    private TransportRole transportRole = TransportRole.FOLLOWER;
    /** Waar een transportdrone naartoe moet (autopiloot, vracht, oppakken), of null. */
    private @Nullable BlockPos destination;
    /** De vracht van een vrachtdrone. */
    private final List<ItemStack> cargo = new ArrayList<>();
    /** Bezig met landen: de drone zakt met zijn passagier naar de grond. */
    private boolean landing;
    /** Landt hij omdat hij bij zijn bestemming is (dan meldt hij "afgeleverd")? */
    private boolean arrived;
    /** Waar de drone hing bij de vorige landingstick, om te zien of het zakken nog opschiet. */
    private double lastLandingY = Double.NaN;
    /** Hoeveel ticks het zakken al stilstaat (een vangnet; zie descend). */
    private int stuckTicks;
    /** Vanaf wanneer hij weer mag zeggen dat er geen grond onder je is (niet elke tick). */
    private long noGroundWarnAgainAt;

    /** Is hij al aan het zakken om je af te zetten? */
    public boolean isLanding() {
        return landing;
    }

    /**
     * Begin te landen: met de passagier eronder recht naar beneden zakken, en hem pas loslaten
     * als zijn voeten vlak boven de grond zijn (zie descend). Zo landt ELKE rit, hoe die ook
     * eindigt: sluipen, "Land" in het menu, een lege batterij, je bril af, de tijd om, een EMP,
     * of aangekomen bij je bestemming.
     */
    public void beginLanding() {
        if (landing) {
            return; // al aan het landen: niet opnieuw beginnen
        }
        landing = true;
        lastLandingY = Double.NaN;
        stuckTicks = 0;
        holdStill();
    }

    /** Begin te landen en zeg de speler die eronder hangt waarom (alleen de eerste keer). */
    public void landPassenger(String messageKey, ImtMessagePayload.Level level) {
        if (landing || !isVehicle()) {
            return;
        }
        beginLanding();
        if (getFirstPassenger() instanceof ServerPlayer rider) {
            ImtServer.send(rider, Component.translatable(messageKey), level);
        }
    }

    /** Geen oude vliegopdracht meer laten doorduwen: vanaf nu stuurt alleen het landen. */
    private void holdStill() {
        getNavigation().stop();
        ((DroneMoveControl) moveControl).hold();
    }

    public TransportRole transportRole() {
        return transportRole;
    }

    public @Nullable BlockPos destination() {
        return destination;
    }

    /** Stelt een transportdrone in: welke rol, waar naartoe en met welke vracht. */
    public void setupTransport(TransportRole role, @Nullable BlockPos destination, List<ItemStack> cargo) {
        this.landing = false;
        this.arrived = false;
        this.transportRole = role;
        this.destination = destination;
        this.cargo.clear();
        this.cargo.addAll(cargo);
    }

    public boolean hasCargo() {
        return !cargo.isEmpty();
    }

    /** Haalt de vracht uit de drone (en maakt hem leeg). */
    public List<ItemStack> takeCargo() {
        List<ItemStack> copy = List.copyOf(cargo);
        cargo.clear();
        return copy;
    }

    public int getSlot() {
        return slot;
    }

    /** Van wie deze drone is (of null). */
    public @Nullable UUID getOwnerId() {
        return ownerId;
    }

    public boolean isOwnedBy(Player player) {
        return player.getUUID().equals(ownerId);
    }

    private @Nullable ServerPlayer getOwner(ServerLevel level) {
        if (ownerId == null) {
            return null;
        }
        // Een spionagedrone volgt zijn doelwit, desnoods naar een andere wereld. Dan is zijn
        // eigenaar niet in dezelfde wereld, dus zoeken we hem op de hele server.
        if (isSpy()) {
            return level.getServer().getPlayerList().getPlayer(ownerId);
        }
        return level.getPlayerByUUID(ownerId) instanceof ServerPlayer player ? player : null;
    }

    /**
     * De eigenaar waar hij ook is, ook in een andere wereld. Alleen voor het arsenaal (terugmelden, neergehaald):
     * om bij je te blijven moet hij in jouw wereld zijn (zie getOwner).
     *
     * Vroeger zocht een drone die zich terugmeldde je alleen in zijn eigen wereld. Was je intussen door een portaal
     * gegaan, dan vond hij je niet, en bleef hij in je arsenaal op "On a job" staan.
     */
    private @Nullable ServerPlayer ownerAnywhere(ServerLevel level) {
        return ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
    }

    // ================================================================ Denken (elke tick, alleen op de server)

    @Override
    public void tick() {
        if (!level().isClientSide && isTransport()) {
            catchSneakingPassenger();
        }
        // Landen met iemand eronder: recht naar beneden, ook als zijn rand langs een richel of
        // door een bladerdak schuurt. Wie eronder hangt, komt zo altijd tot vlak boven de grond.
        // (Zoals de Vex van Minecraft: alleen tijdens deze tick door blokken heen.)
        noPhysics = !level().isClientSide && isTransport() && landing && isVehicle();
        super.tick();
        noPhysics = false;
        setNoGravity(true);
        if (level() instanceof ServerLevel level && !isRemoved()) {
            keepWorldAwake(level);
            if (isOnAJob()) {
                // Hij is nog echt bezig: dat ziet ArsenalServer (zie onServerTick).
                lastActiveTick = level.getGameTime();
                ArsenalServer.seen(this);
            }
        }
        // Beschadigd? Dan rookt en vonkt hij (alleen te zien in je eigen spel).
        DroneDamage.particles(this);
    }

    /**
     * Een "wakker blijven"-kaartje voor Minecraft, zoals een speler er ook een heeft: het stukje wereld rond deze
     * drone (3 bij 3 chunks) blijft doorlopen, ook als hij ver van elke speler weg is. Zo vliegt een vrachtdrone gewoon
     * door naar een verre kist, en volgt een spionagedrone zijn doelwit tot ver weg.
     *
     * Vroeger viel een drone die verder van je weg vloog dan je "simulation distance" stil in de lucht (daar laat
     * Minecraft niets bewegen). Hij kon zich dan nooit meer terugmelden, en stond in je arsenaal voor altijd op
     * "On a job". Een drone die aan de grond op je wacht (GROUNDED) vraagt geen kaartje: die mag gerust stilstaan.
     */
    private void keepWorldAwake(ServerLevel level) {
        if (getMode() == Mode.GROUNDED) {
            return;
        }
        ChunkPos here = chunkPosition();
        // Opnieuw zodra hij in een andere chunk komt, en anders elke seconde (het kaartje geldt 5 seconden).
        // Afstand 3: zijn eigen chunk en de 8 eromheen blijven helemaal doorlopen, dus ook als hij de grens over
        // vliegt, loopt de wereld daar al.
        if (tickCount % 20 == 0 || !here.equals(awakeAt)) {
            level.getChunkSource().addRegionTicket(AWAKE, here, 3, here);
            awakeAt = here;
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) {
            return; // denken doet hij alleen op de server
        }
        if (fireCooldown > 0) {
            fireCooldown--;
        }
        // Uitgeschakeld door een EMP-granaat (hoofdstuk 35): motor uit, naar de grond zakken en daar blijven liggen.
        // Hangt er nog iemand onder, dan zet hij die eerst neer (een noodlanding, zie onEmp).
        if (com.senne.imttech.weapons.Emp.isJammed(this)) {
            if (isTransport() && landing && isVehicle()) {
                descend(level);
            } else {
                fallDead();
            }
            return;
        }
        if (getMode() == Mode.LAUNCH) {
            climbToOrbit(level);
            return;
        }
        // Nog aan het invallen? Dan doet hij verder even niets: eerst landen.
        if (isDiving()) {
            dive(level);
            return;
        }
        // Iemand aan het neerzetten? Dat maakt een transportdrone altijd af, wat er ook gebeurt
        // (bril af of leeg, eigenaar weg, tijd om): pas vlak boven de grond laat hij los.
        if (isTransport() && landing) {
            descend(level);
            return;
        }
        // Een oude kopie (zie stale): niets meer doen, gewoon terug naar boven. Hangt er iemand onder, dan zet hij die
        // eerst neer (dat doet departToOrbit).
        if (stale) {
            recall(level);
            return;
        }

        ServerPlayer owner = getOwner(level);
        if (owner == null || !owner.isAlive() || owner.isSpectator() || !stillLinked(owner)) {
            if (owner != null && isTransport() && getFirstPassenger() == owner) {
                // Je hangt er zelf onder en je bril is leeg (of af): eerst landen, dan pas weg.
                ItemStack glasses = ImtData.getWornGlasses(owner);
                boolean empty = !glasses.isEmpty() && ImtData.getEnergy(glasses) <= 0;
                landPassenger(empty ? "imt.imttech.msg.transport_no_energy" : "imt.imttech.msg.transport_landing",
                        empty ? ImtMessagePayload.Level.ALERT : ImtMessagePayload.Level.INFO);
            }
            recall(level); // met iemand eronder: eerst landen (zie departToOrbit)
            return;
        }
        if (getMode() == Mode.GROUNDED) {
            restOnTheGround(level);
            return;
        }
        if (lifeTicks > 0 && --lifeTicks == 0) {
            if (isTransport()) {
                // Een rit duurt hooguit Transport.MAX_TICKS (5 minuten): dan land je.
                landPassenger("imt.imttech.msg.transport_time_up", ImtMessagePayload.Level.WARNING);
            }
            recall(level);
            return;
        }
        if (isScout()) {
            flyAsScout(owner);
            return;
        }
        if (isSpy()) {
            flyAsSpy(level, owner);
            return;
        }
        if (isTransport()) {
            flyAsTransport(level, owner);
            return;
        }
        if (isIllusion()) {
            // Wordt er nog mee gericht? Dan vliegt hij zoals de speler hem bestuurt.
            DroneControlPayload aim = ImtIllusions.aimControl(this);
            if (aim != null) {
                flyByControl(owner, aim, ImtConfig.Illusions.AIM_RANGE);
                return;
            }
            // Een cloakingdrone blijft stil hangen waar je hem liet: hij is de "projector".
            moveControl.setWantedPosition(getX(), getY(), getZ(), 0.2);
            return;
        }
        // Jaagt de drone op één vastgezet doelwit? Dan stopt hij zodra dat doelwit weg is.
        if (lockedTargetId != null) {
            LivingEntity locked = level.getEntity(lockedTargetId) instanceof LivingEntity living ? living : null;
            if (locked == null || !locked.isAlive()) {
                ImtServer.send(owner, Component.translatable("imt.imttech.msg.strike_done"), ImtMessagePayload.Level.INFO);
                recall(level);
                return;
            }
        }

        // Te ver weg (bv. eigenaar teleporteerde)? Dan springt de drone terug.
        double leash = lockedTargetId != null ? ImtConfig.Drones.STRIKE_RANGE + 32 : 48;
        if (distanceToSqr(owner) > leash * leash) {
            moveTo(owner.getX(), owner.getY() + owner.getBbHeight() + 1, owner.getZ());
            setDeltaMovement(Vec3.ZERO);
        }

        // Elke halve seconde opnieuw kiezen wie we aanvallen.
        if (currentTarget == null || !isValidTarget(owner, currentTarget) || tickCount % 10 == 0) {
            currentTarget = pickTarget(level, owner);
        }

        if (currentTarget != null) {
            engage(level, owner, currentTarget);
        } else {
            followOwner(owner);
        }
    }

    /** Door de rook van een smokebom (hoofdstuk 35) ziet hij niemand. */
    @Override
    public boolean hasLineOfSight(net.minecraft.world.entity.Entity entity) {
        return super.hasLineOfSight(entity)
                && !com.senne.imttech.weapons.Smoke.blocks(level(), getEyePosition(), entity.getEyePosition());
    }

    /**
     * Geraakt door een EMP-granaat: hij vergeet zijn doelwit en valt stil.
     *
     * Hangt er iemand onder (vervoer), dan laat hij die niet zomaar vallen: met zijn laatste
     * stroom zakt hij mee tot vlak boven de grond (een noodlanding), en pas daarna ligt hij stil.
     * Vroeger liet hij je hoog in de lucht los.
     */
    @Override
    public void onEmp(ServerLevel level, int ticks) {
        currentTarget = null;
        getNavigation().stop();
        if (isTransport() && isVehicle()) {
            // (Ook een opgepakt dier of monster zet hij zo neer; alleen een speler krijgt het bericht.)
            landPassenger("imt.imttech.msg.transport_emp", ImtMessagePayload.Level.ALERT);
            return;
        }
        ejectPassengers();
    }

    /** Uit door een EMP: geen motor, dus vallen (iets afgeremd door de lucht) tot op de grond. */
    private void fallDead() {
        getNavigation().stop();
        if (onGround()) {
            setDeltaMovement(0, 0, 0);
        } else {
            setDeltaMovement(getDeltaMovement().multiply(0.7, 0.98, 0.7).add(0, -0.08, 0));
        }
    }

    /** Is de bril van de eigenaar nog verbonden met deze drone? */
    private boolean stillLinked(ServerPlayer owner) {
        ItemStack glasses = ImtData.getWornGlasses(owner);
        if (glasses.isEmpty() || !ImtData.isAuthorized(glasses, owner)) {
            return false;
        }
        return switch (getMode()) {
            case ATTACK -> true;
            case GUARD -> ImtData.isEnabled(glasses, ImtFeature.GUARD_DRONES) && ImtData.getEnergy(glasses) > 0;
            case SCOUT -> ImtData.getEnergy(glasses) > 0 && ImtServer.isActiveScout(owner, this);
            case SPY -> ImtData.getEnergy(glasses) > 0 && ImtSpy.isActiveSpy(owner, this);
            case TRANSPORT -> ImtData.getEnergy(glasses) > 0;
            case ILLUSION -> ImtIllusions.isActive(this);
            case LAUNCH, GROUNDED -> true;
        };
    }

    // ---------------------------------------------------------------- Verkenningsdrone

    /**
     * De verkenningsdrone vliegt zoals de speler hem bestuurt (WASD, spatie, shift) en kijkt
     * waar de speler kijkt. Hij mag niet te ver van het lichaam van de speler weg.
     */
    private void flyAsScout(ServerPlayer owner) {
        flyByControl(owner, ImtServer.getScoutControl(owner), ImtConfig.Recon.SCOUT_RANGE);
    }

    /**
     * Vliegen zoals de speler stuurt: vooruit, opzij, omhoog/omlaag in de richting waarin hij
     * kijkt. Hij mag niet verder dan {@code range} blokken van het lichaam van de speler weg.
     * (Voor de verkenner en voor een cloakingdrone waarmee je richt.)
     */
    private void flyByControl(ServerPlayer owner, DroneControlPayload control, double range) {
        setYRot(control.yaw());
        setXRot(control.pitch());
        yBodyRot = control.yaw();
        yHeadRot = control.yaw();

        double yawRad = control.yaw() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vec3 left = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad));
        Vec3 wanted = forward.scale(control.forward()).add(left.scale(control.strafe())).add(0, control.vertical(), 0);
        if (wanted.lengthSqr() > 1) {
            wanted = wanted.normalize();
        }
        wanted = wanted.scale(control.boost() ? ImtConfig.Recon.SCOUT_BOOST_SPEED : ImtConfig.Recon.SCOUT_SPEED);

        // Aan een elastiekje: te ver weg? Dan trekt de verbinding hem terug.
        Vec3 toOwner = owner.getEyePosition().subtract(position());
        double distance = toOwner.length();
        if (distance > range) {
            wanted = wanted.add(toOwner.normalize().scale((distance - range) * 0.1 + 0.2));
        }
        setDeltaMovement(getDeltaMovement().lerp(wanted, 0.4));
    }

    // ---------------------------------------------------------------- Spionagedrone

    /**
     * De spionagedrone hangt achter en boven zijn doelwit: zo ziet hij alles, en het doelwit
     * kijkt er net niet tegenaan. Hij vecht nooit.
     */
    private void flyAsSpy(ServerLevel level, ServerPlayer owner) {
        LivingEntity target = ImtSpy.targetOf(this, owner);
        if (target == null || target.level() != level) {
            // Geen contact: stil blijven hangen tot I.M.T de opdracht stopt.
            moveControl.setWantedPosition(getX(), getY(), getZ(), 0.2);
            return;
        }
        Vec3 back = Vec3.directionFromRotation(0.0f, target.getYRot()).scale(-ImtConfig.Recon.SPY_FOLLOW_DISTANCE);
        double wantedX = target.getX() + back.x;
        double wantedY = target.getY() + ImtConfig.Recon.SPY_FOLLOW_HEIGHT;
        double wantedZ = target.getZ() + back.z;
        // Hij mag stevig doorvliegen, anders raakt hij een rennende speler kwijt.
        moveControl.setWantedPosition(wantedX, wantedY, wantedZ, 1.8);
        getLookControl().setLookAt(target, 30.0f, 30.0f);
        // Te veel achterstand (portaal, teleport, elytra)? Dan springt hij mee.
        if (distanceToSqr(target) > 48 * 48) {
            moveTo(wantedX, wantedY, wantedZ);
            setDeltaMovement(Vec3.ZERO);
        }
    }

    // ---------------------------------------------------------------- Transportdrones (fase 7)

    /** Wat een transportdrone doet, hangt af van zijn rol. */
    private void flyAsTransport(ServerLevel level, ServerPlayer owner) {
        switch (transportRole) {
            case CARRIER -> {
                if (!(getFirstPassenger() instanceof ServerPlayer rider)) {
                    // Je hangt er niet meer onder (gestorven, weggeteleporteerd): de rit is voorbij.
                    recall(level);
                    return;
                }
                if (destination != null) {
                    flyToDestination(level, owner);
                } else {
                    flyByRider(rider);
                }
            }
            case CARGO -> flyToDestination(level, owner);
            case GRABBER -> {
                if (getFirstPassenger() == null) {
                    recall(level); // wie we vasthielden is weg (gestorven of weggeteleporteerd)
                    return;
                }
                flyToDestination(level, owner);
            }
            case FOLLOWER -> {
                // Escortedrones vliegen alleen mee zolang de speler echt vliegt.
                if (ImtTransport.carrierOf(owner) == null) {
                    recall(level);
                    return;
                }
                // Toch ver achtergeraakt (je teleporteerde, of ging nog sneller)? Dan springt hij bij, net als de
                // andere drones (zie customServerAiStep). Vroeger bleef hij achter: met de autopiloot of al sprintend
                // vlieg je sneller dan hij vloog. Ver weg viel hij stil, en na de rit stond hij nog op "On a job".
                if (distanceToSqr(owner) > 48 * 48) {
                    Vec3 spot = circleSpot(owner);
                    moveTo(spot.x, spot.y, spot.z);
                    setDeltaMovement(Vec3.ZERO);
                }
                followOwner(owner);
            }
        }
    }

    /**
     * Jij stuurt: je vliegt de kant op waar je KIJKT (W/S) en zijwaarts met A/D. Spatie geeft
     * extra lift, sprinten is sneller, en met shift stop je (de drone zet je neer; dat
     * vangt catchSneakingPassenger op, nog voor deze methode).
     */
    private void flyByRider(ServerPlayer rider) {
        // Wat de speler indrukt, stuurt zijn eigen spel elke tick naar de server
        // (zie DroneCamera/ImtClientEvents en ImtServer.handleDroneControl).
        DroneControlPayload input = ImtServer.getTransportControl(rider);
        Vec3 look = rider.getLookAngle();
        double yawRad = rider.getYRot() * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad));
        Vec3 wanted = look.scale(input.forward()).add(left.scale(input.strafe()));
        if (input.vertical() != 0) {
            wanted = wanted.add(0, input.vertical(), 0);
        }
        if (wanted.lengthSqr() > 1) {
            wanted = wanted.normalize();
        }
        double speed = input.boost() ? ImtConfig.Transport.BOOST_SPEED : ImtConfig.Transport.SPEED;
        // Zonder invoer blijft hij stil hangen (hij zakt niet: hij heeft geen zwaartekracht).
        setDeltaMovement(getDeltaMovement().lerp(wanted.scale(speed), 0.25));
        setYRot(rider.getYRot());
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    /**
     * Sluipen terwijl je onder een transportdrone hangt = "zet me neer".
     *
     * Minecraft zelf laat je bij sluipen meteen los, hoe hoog je ook hangt (in Player.rideTick,
     * vlak NA de tick van de drone). Daarom vangt de drone het sluipen eerst op: hij zet de
     * sluiptoets terug op "los" voordat Minecraft hem ziet, en begint zelf te landen. Zo blijf
     * je eronder hangen tot je vlak boven de grond bent. Je eigen spel stuurt de toets elke
     * tick opnieuw zolang je hem vasthoudt, dus dit gebeurt dan ook elke tick.
     *
     * Dit geldt ook voor een speler die door iemands drone is opgepakt: sluipen zet hem neer
     * waar hij is (zoals vroeger kon hij zich zo losmaken, maar nu zonder te vallen).
     */
    private void catchSneakingPassenger() {
        if (!(getFirstPassenger() instanceof ServerPlayer passenger) || !passenger.isShiftKeyDown()) {
            return;
        }
        passenger.setShiftKeyDown(false);
        if (landing) {
            return; // hij is al aan het landen
        }
        if (!ImtTransport.hasSafeGroundBelow(passenger)) {
            // Boven de leegte (het End) of boven lava zet hij je niet neer: vlieg eerst ergens anders heen.
            long now = level().getGameTime();
            if (now >= noGroundWarnAgainAt) {
                noGroundWarnAgainAt = now + 40;
                ImtServer.send(passenger, Component.translatable("imt.imttech.msg.transport_no_ground"),
                        ImtMessagePayload.Level.WARNING);
            }
            return;
        }
        landPassenger("imt.imttech.msg.transport_landing", ImtMessagePayload.Level.INFO);
    }

    /**
     * Landen: de drone zakt recht naar beneden met zijn passagier eronder, en laat hem pas los
     * als zijn voeten vlak boven de grond (of het water) zijn. Je valt dus hooguit een paar
     * centimeter: geen zweefval, geen val, je staat gewoon (zie ImtTransport.setDown).
     *
     * Zo landt elke rit, hoe hij ook eindigt (zie beginLanding). Vroeger liet de drone je soms
     * hoog in de lucht los en zakte je daarna langzaam. Een server ziet dat als vliegen, en
     * gooit je na 4 seconden in de lucht van de server ("Flying is not enabled on this server").
     */
    private void descend(ServerLevel level) {
        Entity passenger = getFirstPassenger();
        if (passenger == null) {
            // Niemand meer om neer te zetten (gestorven, weggeteleporteerd): dan vertrekt hij.
            landing = false;
            recall(level);
            return;
        }
        holdStill();
        double drop = ImtTransport.distanceToGround(passenger);
        // Vangnet: zakt hij een hele seconde niet meer, dan laat hij toch los. Dat hoort niet
        // te gebeuren, want tijdens het landen gaat hij overal doorheen (zie tick).
        if (!Double.isNaN(lastLandingY) && lastLandingY - getY() < 0.01) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lastLandingY = getY();
        boolean belowWorld = passenger.getY() < level.getMinBuildHeight();

        if (drop > ImtTransport.RELEASE_HEIGHT && stuckTicks < 20 && !belowWorld) {
            // Hoog boven de grond op volle snelheid, vlak erboven rustig afremmen.
            double speed = Math.min(ImtConfig.Transport.LAND_SPEED, 0.1 + drop * 0.2);
            Vec3 sideways = Vec3.ZERO;
            if (arrived && destination != null) {
                // Bij je bestemming: onderweg naar beneden nog netjes boven de plek gaan hangen.
                sideways = new Vec3(destination.getX() + 0.5 - getX(), 0, destination.getZ() + 0.5 - getZ()).scale(0.2);
                if (sideways.length() > 0.3) {
                    sideways = sideways.normalize().scale(0.3);
                }
            }
            setDeltaMovement(sideways.x, -speed, sideways.z);
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        landing = false;
        ImtTransport.setDown(level, this, passenger, arrived);
    }

    /**
     * Vliegt naar zijn bestemming: eerst hoog genoeg klimmen (over de bomen en heuvels),
     * dan er naartoe, en boven de plek dalen. Draagt hij iemand (jij met de autopiloot, of wie
     * hij opgepakt heeft), dan landt hij daar zoals elke rit landt (zie descend). Een
     * vrachtdrone daalt tot bij de kist en levert af.
     */
    private void flyToDestination(ServerLevel level, ServerPlayer owner) {
        BlockPos target = destination;
        if (target == null) {
            recall(level);
            return;
        }
        double dx = target.getX() + 0.5 - getX();
        double dz = target.getZ() + 0.5 - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        int groundHere = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(getX()), (int) Math.floor(getZ()));
        int groundThere = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ());
        double cruiseY = Math.max(groundHere, groundThere) + Math.round(ImtConfig.Transport.CRUISE_HEIGHT);

        if (horizontal > 2.5) {
            if (getY() < cruiseY - 3) {
                // Eerst rechtop klimmen, anders vlieg je door een berg.
                moveControl.setWantedPosition(getX(), cruiseY, getZ(), 1.2);
            } else {
                moveControl.setWantedPosition(target.getX() + 0.5, cruiseY, target.getZ() + 0.5, 1.5);
            }
            return;
        }
        if (transportRole != TransportRole.CARGO) {
            // Boven de bestemming, met iemand eronder: recht naar beneden en loslaten vlak boven
            // de grond. Ligt de plek onder een dak, dan zet hij je op het dak (vroeger bleef hij
            // dan eeuwig boven het dak hangen, tot de tijd om was).
            arrived = true;
            beginLanding();
            return;
        }
        // Een vrachtdrone: boven de plek dalen en afleveren.
        double landY = target.getY() + 1.4;
        moveControl.setWantedPosition(target.getX() + 0.5, landY, target.getZ() + 0.5, 0.7);
        if (Math.abs(getY() - landY) < 1.6) {
            ImtTransport.arrive(level, this, owner);
        }
    }

    /** De speler (of het opgepakte wezen) hangt ONDER de drone. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0, -passenger.getBbHeight() - 0.3, 0.0);
    }

    /**
     * Waar je terechtkomt als de drone je loslaat: precies waar je hing, onder de drone.
     *
     * Minecraft zet een passagier standaard BOVENOP zijn voertuig. Bij een drone waar je onder
     * hangt, was dat bijna drie blokken hoger dan je hing, en die viel je daarna weer naar
     * beneden. Hang je half in een blok (een bladerdak), dan zoeken we een stukje hoger een
     * plek waar je wel past.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        for (double up = 0; up <= 3.0; up += 0.5) {
            if (level().noCollision(passenger, passenger.getBoundingBox().move(0, up, 0))) {
                return passenger.position().add(0, up, 0);
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    /** Vracht mag nooit verdwijnen: als de drone weggaat, valt de vracht op de grond. */
    private void dropCargo(ServerLevel level) {
        for (ItemStack stack : takeCargo()) {
            level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), stack));
        }
    }

    // ---------------------------------------------------------------- Doelwit kiezen

    private boolean isValidTarget(ServerPlayer owner, LivingEntity target) {
        double range = lockedTargetId != null ? ImtConfig.Drones.STRIKE_RANGE + 32 : 48;
        return !target.isRemoved() && target.level() == level() && ImtTargeting.canTarget(owner, target)
                && target.distanceToSqr(owner) < range * range;
    }

    private @Nullable LivingEntity pickTarget(ServerLevel level, ServerPlayer owner) {
        // Eén vastgezet doelwit? Dan kijken we niet naar iets anders.
        if (lockedTargetId != null) {
            return level.getEntity(lockedTargetId) instanceof LivingEntity locked && locked.isAlive() ? locked : null;
        }
        // Aanvalsdrones: eerst de gemarkeerde doelwitten, verdeeld over de drones.
        if (!isGuard()) {
            List<LivingEntity> marked = ImtServer.getMarkedTargets(owner);
            if (!marked.isEmpty()) {
                return marked.get(slot % marked.size());
            }
        }
        // Anders (en voor beschermende drones): vijanden rond de eigenaar.
        double range = isGuard() ? ImtConfig.Drones.GUARD_RANGE : ImtConfig.Drones.ATTACK_RANGE;
        List<LivingEntity> threats = level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(range),
                e -> ImtTargeting.isThreatTo(owner, e));
        if (threats.isEmpty()) {
            return null;
        }
        threats.sort(Comparator.comparingDouble(e -> e.distanceToSqr(owner)));
        // Wie de eigenaar net aanviel, gaat voor.
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null && threats.contains(attacker)) {
            return attacker;
        }
        return threats.get(slot % Math.min(threats.size(), 3));
    }

    // ---------------------------------------------------------------- Vliegen en schieten

    /**
     * Rond het doelwit cirkelen en erop schieten. Escortedrones in een formatie die blijft
     * staan (schild, pijl) gaan niet naar het doelwit toe: ze schieten vanaf hun plek.
     */
    private void engage(ServerLevel level, ServerPlayer owner, LivingEntity target) {
        if (isGuard() && formationOf(owner).holdsPosition()) {
            followOwner(owner);
        } else {
            double angle = tickCount * 0.06 + slot * (Math.PI * 2 / 5);
            double radius = 3.5;
            moveControl.setWantedPosition(
                    target.getX() + Math.cos(angle) * radius,
                    target.getY() + target.getBbHeight() + 1.5,
                    target.getZ() + Math.sin(angle) * radius,
                    1.0);
        }
        getLookControl().setLookAt(target, 30.0f, 30.0f);

        double range = ImtConfig.Drones.ATTACK_RANGE;
        if (fireCooldown <= 0 && distanceToSqr(target) <= range * range && hasLineOfSight(target)) {
            fireLaser(level, owner, target);
            fireCooldown = Math.round(ImtConfig.Drones.FIRE_INTERVAL) + random.nextInt(5);
        }
    }

    /** De vorm waarin de escorte van deze eigenaar vliegt (staat op zijn bril). */
    private static DroneFormation formationOf(ServerPlayer owner) {
        return ImtData.getFormation(ImtData.getWornGlasses(owner));
    }

    /**
     * Bij de eigenaar blijven. Escortedrones nemen hun plek in de formatie in (zie
     * DroneFormation), met de speltijd als klok: dan draaien ze samen rond en blijven ze
     * netjes verdeeld. Andere drones cirkelen gewoon om de eigenaar.
     */
    private void followOwner(ServerPlayer owner) {
        if (isGuard()) {
            ItemStack glasses = ImtData.getWornGlasses(owner);
            DroneFormation formation = ImtData.getFormation(glasses);
            Vec3 spot = formation.spot(owner, slot, ImtData.getGuardCount(glasses), level().getGameTime());
            moveControl.setWantedPosition(spot.x, spot.y, spot.z, formation.speed());
            return;
        }
        Vec3 spot = circleSpot(owner);
        double speed = 0.8;
        if (isTransport()) {
            // Een escortedrone bij een rit: raakt hij achterop (met zijn gewone snelheid haalt hij de autopiloot en
            // sprinten niet), dan geeft hij gas, hoe verder hoe meer. Zo blijft hij bij je in plaats van achter te blijven.
            speed = Math.min(3.0, 0.8 + Math.max(0.0, position().distanceTo(spot) - 3.0) * 0.25);
        }
        moveControl.setWantedPosition(spot.x, spot.y, spot.z, speed);
    }

    /** Waar een drone die met je meevliegt (zonder escorteformatie) nu hoort te hangen: in een kringetje om je heen. */
    private Vec3 circleSpot(ServerPlayer owner) {
        double angle = tickCount * 0.05 + slot * (Math.PI * 2 / 5);
        double radius = 2.8;
        double height = owner.getBbHeight() + 1.4;
        return new Vec3(owner.getX() + Math.cos(angle) * radius, owner.getY() + height,
                owner.getZ() + Math.sin(angle) * radius);
    }

    private void fireLaser(ServerLevel level, ServerPlayer owner, LivingEntity target) {
        Vec3 from = position().add(0, getBbHeight() * 0.3, 0);
        Vec3 to = target.getBoundingBox().getCenter();
        // Staat het doelwit onder de schildkoepel van iemand anders? Dan stuit het schot daarop.
        Vec3 shield = ImtDomes.blockedAt(level, to, from, owner.getUUID(), Math.round(ImtConfig.Dome.COST_LASER));
        if (shield != null) {
            playSound(ModSounds.DRONE_LASER.get(), 0.6f, 1.5f);
            ModNetwork.sendNear(level, position(), 64, new BeamEffectPayload(from, shield, BeamEffectPayload.Kind.DRONE_LASER));
            return;
        }
        DamageSource source = ModDamageTypes.droneLaser(level, this, owner);
        target.hurt(source, ImtConfig.Drones.LASER_DAMAGE);
        playSound(ModSounds.DRONE_LASER.get(), 0.6f, 1.5f + random.nextFloat() * 0.3f);
        ModNetwork.sendNear(level, position(), 64, new BeamEffectPayload(from, to, BeamEffectPayload.Kind.DRONE_LASER));
    }

    /**
     * Terugroepen: de drone stijgt op en verdwijnt hoog in de lucht. Zijn schade gaat mee, dus
     * hij komt beschadigd terug in je arsenaal en blijft zo tot je hem repareert.
     *
     * Is er geen weg omhoog (je staat in een grot), dan lost hij ter plekke op; anders zou hij
     * tegen het plafond blijven duwen.
     */
    public void recall(ServerLevel level) {
        departToOrbit(level);
    }

    /** Vliegt recht omhoog tot hij hoog genoeg is; daarna hangt hij in zijn baan. */
    private void climbToOrbit(ServerLevel level) {
        launchTicks++;
        setDeltaMovement(0, ImtConfig.Drones.LAUNCH_SPEED, 0);
        hasImpulse = true;
        if (launchTicks % 2 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() - 0.2, getZ(), 2, 0.08, 0.05, 0.08, 0.02);
        }
        boolean highEnough = getY() >= launchFrom + Math.round(ImtConfig.Drones.ORBIT_HEIGHT);
        boolean stuck = horizontalCollision || verticalCollision;
        if (highEnough || stuck || launchTicks >= Math.round(ImtConfig.Drones.LAUNCH_TICKS)
                || getY() > level.getMaxBuildHeight() + 16) {
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 8, 0.2, 0.2, 0.2, 0.02);
            returnToArsenal(level);
            discard();
        }
    }

    /** Vanaf welke hoogte hij begon met stijgen; zo weet hij wanneer hij hoog genoeg is. */
    private double launchFrom = Double.NaN;

    /** Valt met hoge snelheid naar beneden en vangt vlak boven zijn doel op. */
    /** Eén tick van de duik: naar de volgende plek op de boog, met zijn neus in de vliegrichting. */
    private void dive(ServerLevel level) {
        SkyDive current = dive;
        Vec3 step = current.next().subtract(position());
        setDeltaMovement(step);
        hasImpulse = true;
        if (step.horizontalDistanceSqr() > 0.0025) {
            float yaw = (float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG) - 90.0f;
            setYRot(yaw);
            yBodyRot = yaw;
        }
        // Een streep vonken achter hem aan, zodat je hem uit de lucht ziet komen.
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.4, getZ(), 2, 0.05, 0.1, 0.05, 0.01);
        // Aangekomen, of ergens tegenaan gevlogen? Dan is de duik klaar.
        if (current.done() || horizontalCollision || verticalCollision) {
            dive = null;
            setDeltaMovement(0, 0, 0);
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 6, 0.2, 0.1, 0.2, 0.02);
        }
    }

    /**
     * Een teruggeroepen drone: hij zakt naar de grond en blijft daar staan wachten tot je hem
     * met een rechtsklik repareert.
     */
    private void restOnTheGround(ServerLevel level) {
        if (onGround() || !level.getBlockState(blockPosition().below()).getCollisionShape(level, blockPosition().below()).isEmpty()) {
            setDeltaMovement(0, 0, 0);
            setNoGravity(true);
        } else {
            setDeltaMovement(0, -0.25, 0);
            hasImpulse = true;
        }
        // Een zacht knipperlicht, zodat je ziet dat hij op je wacht.
        if (tickCount % 20 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 1, 0.1, 0.05, 0.1, 0.0);
        }
    }

    /**
     * Rechtsklikken op een drone die op je wacht: repareren met ijzer uit je rugzak.
     * Is hij weer helemaal heel (of hou je shift ingedrukt), dan vertrekt hij naar zijn baan.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (getMode() != Mode.GROUNDED || !(player instanceof ServerPlayer serverPlayer)
                || !(level() instanceof ServerLevel level)) {
            return super.mobInteract(player, hand);
        }
        if (!isOwnedBy(player)) {
            return InteractionResult.FAIL; // niet jouw drone
        }
        boolean full = getHealth() >= getMaxHealth();
        if (player.isShiftKeyDown() || full) {
            departToOrbit(level);
            ImtServer.send(serverPlayer, Component.translatable("arsenal.imttech.msg.sent_up"),
                    ImtMessagePayload.Level.INFO);
            return InteractionResult.SUCCESS;
        }
        int wanted = (int) Math.ceil((getMaxHealth() - getHealth()) / ImtConfig.Drones.REPAIR_PER_INGOT);
        int used = ArsenalCosts.takeRepairParts(player, wanted);
        if (used <= 0) {
            ImtServer.send(serverPlayer, Component.translatable("arsenal.imttech.msg.no_parts",
                    ArsenalCosts.repairPartName(), wanted), ImtMessagePayload.Level.WARNING);
            return InteractionResult.CONSUME;
        }
        setHealth((float) Math.min(getMaxHealth(), getHealth() + used * ImtConfig.Drones.REPAIR_PER_INGOT));
        if (arsenalId != null) {
            ArsenalServer.setHealth(serverPlayer, arsenalId, getHealth());
        }
        level.playSound(null, getX(), getY(), getZ(), ModSounds.STATION_UPGRADE.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.4, getZ(), 8, 0.2, 0.2, 0.2, 0.05);
        if (getHealth() >= getMaxHealth()) {
            ImtServer.send(serverPlayer, Component.translatable("arsenal.imttech.msg.repaired"),
                    ImtMessagePayload.Level.INFO);
        } else {
            ImtServer.send(serverPlayer, Component.translatable("arsenal.imttech.msg.repairing",
                    Math.round(getHealth()), Math.round(getMaxHealth())), ImtMessagePayload.Level.INFO);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Meldt deze drone terug in het arsenaal van zijn eigenaar, met de schade die hij opliep. Dat gebeurt één keer per
     * rit; daarna is hij afgeboekt (settled). Is de eigenaar niet online, dan zet zijn arsenaal hem zelf terug in zijn
     * baan als hij weer inlogt (ArsenalServer.onLogin).
     */
    private void returnToArsenal(ServerLevel level) {
        if (arsenalId == null || settled || stale) {
            return; // al afgeboekt, of een oude kopie (zie stale)
        }
        ServerPlayer owner = ownerAnywhere(level);
        if (owner != null) {
            ArsenalServer.returnToOrbit(owner, arsenalId, getHealth());
        }
        settle();
    }

    /** Deze rit is afgeboekt: hij boekt niets meer, en ArsenalServer hoeft hem niet meer in de gaten te houden. */
    private void settle() {
        settled = true;
        ArsenalServer.forget(this);
    }

    /**
     * Deze drone verdwijnt uit de wereld zonder dat hij zich terugmeldde: iets haalde hem weg (zoals de spionagedrone
     * die zijn doelwit door een portaal volgt), of hij bleef achter in een stuk wereld dat niet meer bijgehouden wordt.
     * Dan zet zijn arsenaal hem meteen terug in zijn baan, met de schade die hij had.
     *
     * Vroeger gebeurde er dan niets, en bleef hij in je arsenaal op "On a job" staan tot je opnieuw inlogde.
     *
     * Hier wordt alleen geboekt. Minecraft roept dit soms aan terwijl het een stuk wereld opruimt, en dan mag er in de
     * wereld niets bijkomen of verdwijnen (dus geen vracht laten vallen). Komt de drone toch nog terug (je komt weer in
     * de buurt), dan is hij een oude kopie (stale) en vliegt hij weg zonder iets te boeken.
     *
     * Een teruggeroepen drone die aan de grond op je wacht (GROUNDED) blijft in je arsenaal "Grounded": hij staat er
     * nog als je terugkomt.
     */
    public void handBack(ServerLevel level) {
        if (!isOnAJob()) {
            return;
        }
        ServerPlayer owner = ownerAnywhere(level);
        if (owner != null) {
            ArsenalServer.returnToOrbit(owner, arsenalId, getHealth());
        }
        settle();
        stale = true;
    }

    /**
     * Wat ArsenalServer doet met een drone die al even niets meer doet (hij staat stil in een stuk wereld waar Minecraft
     * niets laat bewegen, of hij kwam nooit echt in de wereld): zijn rit afmaken en terug in zijn baan. Vracht gaat
     * alsnog naar de kist bij de bestemming, en wie hij vasthield laat hij los. Dit is een vangnet: met
     * keepWorldAwake hoort een drone niet stil te vallen.
     */
    public void abandon(ServerLevel level) {
        ejectPassengers();
        if (hasCargo()) {
            ImtTransport.deliverCargo(level, this, ownerAnywhere(level));
        }
        handBack(level);
        discard();
    }

    /**
     * Weggehaald door iets anders dan zijn eigen vertrek (een commando, de spionagedrone die door een portaal volgt...):
     * zijn vracht valt op de grond, en hij gaat terug in zijn baan (zie handBack).
     */
    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level && reason == RemovalReason.DISCARDED && isOnAJob()) {
            dropCargo(level);
        }
        super.remove(reason);
        // Meestal deed onRemovedFromLevel dit net al. Maar was hij nog niet echt in de wereld opgenomen (dan roept
        // Minecraft dat niet aan), dan gebeurt het hier.
        if (level() instanceof ServerLevel level) {
            handBack(level);
        }
    }

    /**
     * Minecraft haalt deze drone uit de wereld: weggehaald, of zijn stuk wereld wordt niet meer bijgehouden (niemand in
     * de buurt). Heeft hij zich nog niet teruggemeld, dan gaat hij nu terug in zijn baan (zie handBack).
     */
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (level() instanceof ServerLevel level) {
            handBack(level);
        }
    }

    /**
     * Een drone gaat nooit door een portaal: hij hoort bij jou, in jouw wereld. Ga jij door een portaal, dan vliegen je
     * drones terug naar hun baan (zie customServerAiStep: hij vindt je niet meer).
     */
    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    // ================================================================ Gedrag als wezen

    /** De eigenaar (en zijn andere drones) kunnen deze drone geen pijn doen. */
    @Override
    public boolean hurt(DamageSource source, float damage) {
        // Een drone ademt niet: raakt hij klem in een blok, dan stikt hij daar niet in.
        if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker != null && ownerId != null
                && (ownerId.equals(attacker.getUUID()) || attacker instanceof DroneEntity)) {
            return false;
        }
        return super.hurt(source, damage);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            // Neergehaald met iemand eronder: die valt meteen, vanaf waar hij hing (zie
            // getDismountLocationForPassenger). Een kapotte drone kan niemand meer neerzetten.
            ejectPassengers();
        }
        super.die(source);
        if (level() instanceof ServerLevel level) {
            dropCargo(level);
            // Neergehaald: uit het arsenaal, en een beetje schroot valt op de grond. (Ook als je in een andere wereld
            // bent. Een oude kopie, zie stale, kost je niets: die drone hangt in je arsenaal al weer in zijn baan.)
            ServerPlayer owner = ownerAnywhere(level);
            if (arsenalId != null && !settled && !stale && owner != null) {
                ArsenalServer.destroyed(owner, arsenalId, level, position());
            }
            settle();
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 8, 0.2, 0.2, 0.2, 0.02);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.2, getZ(), 12, 0.3, 0.3, 0.3, 0.2);
        }
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.DRONE_HURT.get();
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return ModSounds.DRONE_DESTROYED.get();
    }

    // ---------------------------------------------------------------- Opslaan en terugladen

    /**
     * Wat een drone moet onthouden als de wereld wordt opgeslagen: wie zijn eigenaar is, welke
     * drone uit het arsenaal hij is, en waar hij mee bezig was.
     *
     * Zonder dit was een drone na een herstart zijn eigenaar kwijt, en kon hij zichzelf niet
     * meer terugmelden in het arsenaal.
     */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) {
            tag.putUUID("DroneOwner", ownerId);
        }
        if (arsenalId != null) {
            tag.putUUID("DroneArsenal", arsenalId);
        }
        tag.putByte("DroneMode", (byte) getMode().ordinal());
        tag.putInt("DroneSlot", slot);
        tag.putInt("DroneLife", lifeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("DroneOwner") ? tag.getUUID("DroneOwner") : null;
        arsenalId = tag.hasUUID("DroneArsenal") ? tag.getUUID("DroneArsenal") : null;
        if (tag.contains("DroneMode")) {
            entityData.set(DATA_MODE, tag.getByte("DroneMode"));
        }
        slot = tag.getInt("DroneSlot");
        lifeTicks = tag.contains("DroneLife") ? tag.getInt("DroneLife") : -1;
        // Een drone die zo terugkomt (zijn stuk wereld wordt weer geladen, of hij droeg je toen je uitlogde), was bezig
        // met een rit die al afgeboekt is: toen hij uit de wereld ging, zette zijn arsenaal hem terug in zijn baan (zie
        // handBack, en ArsenalServer.onLogin). Dus is hij een oude kopie en vliegt hij weg, zonder iets te boeken.
        // Alleen een drone die aan de grond op je wacht (GROUNDED), blijft gewoon staan.
        stale = getMode() != Mode.GROUNDED;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
        // Drones duwen niemand opzij.
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    // Niet verdrinken regelen we met een tag:
    // data/minecraft/tags/entity_type/can_breathe_under_water.json

    @Override
    protected void checkFallDamage(double distance, boolean onGround, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
        // Drones vliegen; ze vallen niet.
    }

    // ================================================================ Besturing

    /**
     * Vliegt in een rechte lijn naar de gewenste plek (zoals de Vex uit Minecraft,
     * maar dan zonder door muren te gaan).
     */
    private static class DroneMoveControl extends MoveControl {
        /** Dezelfde drone als "mob", maar met het juiste type (MoveControl kent alleen Mob). */
        private final DroneEntity drone;

        DroneMoveControl(DroneEntity drone) {
            super(drone);
            this.drone = drone;
        }

        /** Stoppen met ergens naartoe vliegen: een oude bestemming duwt dan niet meer mee. */
        void hold() {
            operation = Operation.WAIT;
        }

        @Override
        public void tick() {
            if (drone.isScout() || com.senne.imttech.weapons.Emp.isJammed(drone)) {
                return; // de verkenner wordt rechtstreeks bestuurd; na een EMP valt hij gewoon
            }
            if (operation != Operation.MOVE_TO) {
                // Stilhangen: langzaam afremmen.
                drone.setDeltaMovement(drone.getDeltaMovement().scale(0.8));
                return;
            }
            Vec3 delta = new Vec3(wantedX - drone.getX(), wantedY - drone.getY(), wantedZ - drone.getZ());
            double length = delta.length();
            if (length < 0.3) {
                operation = Operation.WAIT;
                drone.setDeltaMovement(drone.getDeltaMovement().scale(0.5));
                return;
            }
            // Versnellen richting het doel; dichtbij rustiger aan.
            double speed = speedModifier * 0.06 * Math.min(1.0, length / 2.0);
            drone.setDeltaMovement(drone.getDeltaMovement().add(delta.scale(speed / length)));
            if (drone.currentTarget == null) {
                Vec3 motion = drone.getDeltaMovement();
                if (motion.horizontalDistanceSqr() > 0.001) {
                    drone.setYRot(-((float) Mth.atan2(motion.x, motion.z)) * Mth.RAD_TO_DEG);
                    drone.yBodyRot = drone.getYRot();
                }
            } else {
                double dx = drone.currentTarget.getX() - drone.getX();
                double dz = drone.currentTarget.getZ() - drone.getZ();
                drone.setYRot(-((float) Mth.atan2(dx, dz)) * Mth.RAD_TO_DEG);
                drone.yBodyRot = drone.getYRot();
            }
        }
    }
}
