package com.senne.starktech.drone;

import com.senne.starktech.arsenal.ArsenalCosts;
import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithFeature;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.edith.EdithTargeting;
import com.senne.starktech.illusion.EdithIllusions;
import com.senne.starktech.network.BeamEffectPayload;
import com.senne.starktech.network.DroneControlPayload;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.DroneControlPayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.registry.ModDamageTypes;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.shield.EdithDomes;
import com.senne.starktech.spy.EdithSpy;
import com.senne.starktech.transport.EdithTransport;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Een Stark-drone. Er zijn drie soorten (zie Mode):
 * - Aanvalsdrones (de "zwerm"): vallen gemarkeerde doelwitten aan en verdwijnen na een minuut.
 * - Beschermende drones: vliegen rond hun eigenaar en schieten op vijanden in de buurt.
 * - Verkenningsdrone: de speler bestuurt hem zelf en kijkt door zijn camera.
 *
 * Een drone denkt alleen op de server. Hij heeft altijd een eigenaar; zonder eigenaar
 * (of als de bril af gaat of leeg is) vliegt hij weg.
 */
public class StarkDroneEntity extends PathfinderMob {
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
    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(StarkDroneEntity.class, EntityDataSerializers.BYTE);

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

    public StarkDroneEntity(EntityType<? extends StarkDroneEntity> type, Level level) {
        super(type, level);
        this.moveControl = new DroneMoveControl(this);
        this.setNoGravity(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, StarkConfig.Drones.HEALTH)
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
    }

    public @Nullable UUID getArsenalId() {
        return arsenalId;
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
        dropCargo(level);
        // Zolang hij stijgt, staat hij als "onderweg" geboekt: je kunt hem nog niet opnieuw sturen.
        ServerPlayer owner = getOwner(level);
        if (arsenalId != null && owner != null) {
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
    /** Hoe hoog je hing bij de vorige landingstick, om te zien of het zakken nog opschiet. */
    private double lastDrop = -1;
    /** Hoeveel ticks het zakken al stilstaat (dan laten we je los; zie descend). */
    private int stuckTicks;

    /** Is hij al aan het zakken om je af te zetten? */
    public boolean isLanding() {
        return landing;
    }

    /** Begin te landen: zakken tot bij de grond en de passagier daar loslaten. */
    public void beginLanding() {
        landing = true;
        lastDrop = -1;
        stuckTicks = 0;
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

    // ================================================================ Denken (elke tick, alleen op de server)

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        // Beschadigd? Dan rookt en vonkt hij (alleen te zien in je eigen spel).
        DroneDamage.particles(this);
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
        if (getMode() == Mode.LAUNCH) {
            climbToOrbit(level);
            return;
        }
        // Nog aan het invallen? Dan doet hij verder even niets: eerst landen.
        if (isDiving()) {
            dive(level);
            return;
        }

        ServerPlayer owner = getOwner(level);
        if (owner == null || !owner.isAlive() || owner.isSpectator() || !stillLinked(owner)) {
            recall(level);
            return;
        }
        if (getMode() == Mode.GROUNDED) {
            restOnTheGround(level);
            return;
        }
        if (lifeTicks > 0 && --lifeTicks == 0) {
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
            DroneControlPayload aim = EdithIllusions.aimControl(this);
            if (aim != null) {
                flyByControl(owner, aim, StarkConfig.Illusions.AIM_RANGE);
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
                EdithServer.send(owner, Component.translatable("edith.starktech.msg.strike_done"), EdithMessagePayload.Level.INFO);
                recall(level);
                return;
            }
        }

        // Te ver weg (bv. eigenaar teleporteerde)? Dan springt de drone terug.
        double leash = lockedTargetId != null ? StarkConfig.Drones.STRIKE_RANGE + 32 : 48;
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

    /** Is de bril van de eigenaar nog verbonden met deze drone? */
    private boolean stillLinked(ServerPlayer owner) {
        ItemStack glasses = EdithData.getWornGlasses(owner);
        if (glasses.isEmpty() || !EdithData.isAuthorized(glasses, owner)) {
            return false;
        }
        return switch (getMode()) {
            case ATTACK -> true;
            case GUARD -> EdithData.isEnabled(glasses, EdithFeature.GUARD_DRONES) && EdithData.getEnergy(glasses) > 0;
            case SCOUT -> EdithData.getEnergy(glasses) > 0 && EdithServer.isActiveScout(owner, this);
            case SPY -> EdithData.getEnergy(glasses) > 0 && EdithSpy.isActiveSpy(owner, this);
            case TRANSPORT -> EdithData.getEnergy(glasses) > 0;
            case ILLUSION -> EdithIllusions.isActive(this);
            case LAUNCH, GROUNDED -> true;
        };
    }

    // ---------------------------------------------------------------- Verkenningsdrone

    /**
     * De verkenningsdrone vliegt zoals de speler hem bestuurt (WASD, spatie, shift) en kijkt
     * waar de speler kijkt. Hij mag niet te ver van het lichaam van de speler weg.
     */
    private void flyAsScout(ServerPlayer owner) {
        flyByControl(owner, EdithServer.getScoutControl(owner), StarkConfig.Recon.SCOUT_RANGE);
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
        wanted = wanted.scale(control.boost() ? StarkConfig.Recon.SCOUT_BOOST_SPEED : StarkConfig.Recon.SCOUT_SPEED);

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
        LivingEntity target = EdithSpy.targetOf(this, owner);
        if (target == null || target.level() != level) {
            // Geen contact: stil blijven hangen tot E.D.I.T.H. de opdracht stopt.
            moveControl.setWantedPosition(getX(), getY(), getZ(), 0.2);
            return;
        }
        Vec3 back = Vec3.directionFromRotation(0.0f, target.getYRot()).scale(-StarkConfig.Recon.SPY_FOLLOW_DISTANCE);
        double wantedX = target.getX() + back.x;
        double wantedY = target.getY() + StarkConfig.Recon.SPY_FOLLOW_HEIGHT;
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
                    // De speler is afgestapt (shift): dan is de rit voorbij.
                    recall(level);
                    return;
                }
                if (landing) {
                    descend(rider);
                } else if (destination != null) {
                    flyToDestination(level, owner);
                } else {
                    flyByRider(rider, level, owner);
                }
            }
            case CARGO -> flyToDestination(level, owner);
            case GRABBER -> {
                if (getFirstPassenger() == null) {
                    recall(level); // wie we vasthielden is weg (afgestapt of dood)
                    return;
                }
                flyToDestination(level, owner);
            }
            case FOLLOWER -> {
                // Escortedrones vliegen alleen mee zolang de speler echt vliegt.
                if (EdithTransport.carrierOf(owner) == null) {
                    recall(level);
                    return;
                }
                followOwner(owner);
            }
        }
    }

    /**
     * Jij stuurt: je vliegt de kant op waar je KIJKT (W/S) en zijwaarts met A/D. Spatie geeft
     * extra lift, sprinten is sneller, en met shift stop je (de drone zet je neer).
     */
    private void flyByRider(ServerPlayer rider, ServerLevel level, ServerPlayer owner) {
        // Wat de speler indrukt, stuurt zijn eigen spel elke tick naar de server
        // (zie DroneCamera/EdithClientEvents en EdithServer.handleDroneControl).
        DroneControlPayload input = EdithServer.getTransportControl(rider);
        if (rider.isShiftKeyDown()) {
            beginLanding(); // hij zakt nu met je mee; zie descend()
            return;
        }
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
        double speed = input.boost() ? StarkConfig.Transport.BOOST_SPEED : StarkConfig.Transport.SPEED;
        // Zonder invoer blijft hij stil hangen (hij zakt niet: hij heeft geen zwaartekracht).
        setDeltaMovement(getDeltaMovement().lerp(wanted.scale(speed), 0.25));
        setYRot(rider.getYRot());
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    /**
     * Landen: de drone zakt recht naar beneden tot je voeten bij de grond zijn, en laat je
     * daar los. Je houdt er niets aan over - geen zweefval, geen val: je staat gewoon.
     */
    private void descend(ServerPlayer rider) {
        double drop = EdithTransport.distanceToGround(rider);
        // Schiet het zakken nog op? Een drone met iemand eronder kan niet eindeloos laag
        // komen; zodra hij stil blijft hangen laten we los en zak jij het laatste stukje
        // zelf (rustig, zie EdithTransport.tickSoftLanding).
        if (lastDrop >= 0 && lastDrop - drop < 0.05) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lastDrop = drop;

        if (drop > 0.5 && drop < EdithTransport.NO_GROUND && stuckTicks < 10) {
            double speed = Math.min(StarkConfig.Transport.LAND_SPEED, 0.1 + drop * 0.2);
            setDeltaMovement(0, -speed, 0);
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        EdithTransport.land(rider, this, true);
    }

    /**
     * Vliegt naar zijn bestemming: eerst hoog genoeg klimmen (over de bomen en heuvels),
     * dan er naartoe, en boven de plek rustig dalen. Is hij er: afleveren.
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
        double cruiseY = Math.max(groundHere, groundThere) + Math.round(StarkConfig.Transport.CRUISE_HEIGHT);

        if (horizontal > 2.5) {
            if (getY() < cruiseY - 3) {
                // Eerst rechtop klimmen, anders vlieg je door een berg.
                moveControl.setWantedPosition(getX(), cruiseY, getZ(), 1.2);
            } else {
                moveControl.setWantedPosition(target.getX() + 0.5, cruiseY, target.getZ() + 0.5, 1.5);
            }
            return;
        }
        // Boven de bestemming: dalen.
        double landY = target.getY() + 1.4;
        moveControl.setWantedPosition(target.getX() + 0.5, landY, target.getZ() + 0.5, 0.7);
        if (Math.abs(getY() - landY) < 1.6) {
            EdithTransport.arrive(level, this, owner);
        }
    }

    /** De speler (of het opgepakte wezen) hangt ONDER de drone. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0, -passenger.getBbHeight() - 0.3, 0.0);
    }

    /** Vracht mag nooit verdwijnen: als de drone weggaat, valt de vracht op de grond. */
    private void dropCargo(ServerLevel level) {
        for (ItemStack stack : takeCargo()) {
            level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), stack));
        }
    }

    // ---------------------------------------------------------------- Doelwit kiezen

    private boolean isValidTarget(ServerPlayer owner, LivingEntity target) {
        double range = lockedTargetId != null ? StarkConfig.Drones.STRIKE_RANGE + 32 : 48;
        return !target.isRemoved() && target.level() == level() && EdithTargeting.canTarget(owner, target)
                && target.distanceToSqr(owner) < range * range;
    }

    private @Nullable LivingEntity pickTarget(ServerLevel level, ServerPlayer owner) {
        // Eén vastgezet doelwit? Dan kijken we niet naar iets anders.
        if (lockedTargetId != null) {
            return level.getEntity(lockedTargetId) instanceof LivingEntity locked && locked.isAlive() ? locked : null;
        }
        // Aanvalsdrones: eerst de gemarkeerde doelwitten, verdeeld over de drones.
        if (!isGuard()) {
            List<LivingEntity> marked = EdithServer.getMarkedTargets(owner);
            if (!marked.isEmpty()) {
                return marked.get(slot % marked.size());
            }
        }
        // Anders (en voor beschermende drones): vijanden rond de eigenaar.
        double range = isGuard() ? StarkConfig.Drones.GUARD_RANGE : StarkConfig.Drones.ATTACK_RANGE;
        List<LivingEntity> threats = level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(range),
                e -> EdithTargeting.isThreatTo(owner, e));
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

        double range = StarkConfig.Drones.ATTACK_RANGE;
        if (fireCooldown <= 0 && distanceToSqr(target) <= range * range && hasLineOfSight(target)) {
            fireLaser(level, owner, target);
            fireCooldown = Math.round(StarkConfig.Drones.FIRE_INTERVAL) + random.nextInt(5);
        }
    }

    /** De vorm waarin de escorte van deze eigenaar vliegt (staat op zijn bril). */
    private static DroneFormation formationOf(ServerPlayer owner) {
        return EdithData.getFormation(EdithData.getWornGlasses(owner));
    }

    /**
     * Bij de eigenaar blijven. Escortedrones nemen hun plek in de formatie in (zie
     * DroneFormation), met de speltijd als klok: dan draaien ze samen rond en blijven ze
     * netjes verdeeld. Andere drones cirkelen gewoon om de eigenaar.
     */
    private void followOwner(ServerPlayer owner) {
        if (isGuard()) {
            ItemStack glasses = EdithData.getWornGlasses(owner);
            DroneFormation formation = EdithData.getFormation(glasses);
            Vec3 spot = formation.spot(owner, slot, EdithData.getGuardCount(glasses), level().getGameTime());
            moveControl.setWantedPosition(spot.x, spot.y, spot.z, formation.speed());
            return;
        }
        double angle = tickCount * 0.05 + slot * (Math.PI * 2 / 5);
        double radius = isGuard() ? 1.8 : 2.8;
        double height = owner.getBbHeight() + (isGuard() ? 0.5 : 1.4);
        moveControl.setWantedPosition(
                owner.getX() + Math.cos(angle) * radius,
                owner.getY() + height,
                owner.getZ() + Math.sin(angle) * radius,
                0.8);
    }

    private void fireLaser(ServerLevel level, ServerPlayer owner, LivingEntity target) {
        Vec3 from = position().add(0, getBbHeight() * 0.3, 0);
        Vec3 to = target.getBoundingBox().getCenter();
        // Staat het doelwit onder de schildkoepel van iemand anders? Dan stuit het schot daarop.
        Vec3 shield = EdithDomes.blockedAt(level, to, from, owner.getUUID(), Math.round(StarkConfig.Dome.COST_LASER));
        if (shield != null) {
            playSound(ModSounds.DRONE_LASER.get(), 0.6f, 1.5f);
            ModNetwork.sendNear(level, position(), 64, new BeamEffectPayload(from, shield, BeamEffectPayload.Kind.DRONE_LASER));
            return;
        }
        DamageSource source = ModDamageTypes.droneLaser(level, this, owner);
        target.hurt(source, StarkConfig.Drones.LASER_DAMAGE);
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
        setDeltaMovement(0, StarkConfig.Drones.LAUNCH_SPEED, 0);
        hasImpulse = true;
        if (launchTicks % 2 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() - 0.2, getZ(), 2, 0.08, 0.05, 0.08, 0.02);
        }
        boolean highEnough = getY() >= launchFrom + Math.round(StarkConfig.Drones.ORBIT_HEIGHT);
        boolean stuck = horizontalCollision || verticalCollision;
        if (highEnough || stuck || launchTicks >= Math.round(StarkConfig.Drones.LAUNCH_TICKS)
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
            EdithServer.send(serverPlayer, Component.translatable("arsenal.starktech.msg.sent_up"),
                    EdithMessagePayload.Level.INFO);
            return InteractionResult.SUCCESS;
        }
        int wanted = (int) Math.ceil((getMaxHealth() - getHealth()) / StarkConfig.Drones.REPAIR_PER_INGOT);
        int used = ArsenalCosts.takeRepairParts(player, wanted);
        if (used <= 0) {
            EdithServer.send(serverPlayer, Component.translatable("arsenal.starktech.msg.no_parts",
                    ArsenalCosts.repairPartName(), wanted), EdithMessagePayload.Level.WARNING);
            return InteractionResult.CONSUME;
        }
        setHealth((float) Math.min(getMaxHealth(), getHealth() + used * StarkConfig.Drones.REPAIR_PER_INGOT));
        if (arsenalId != null) {
            ArsenalServer.setHealth(serverPlayer, arsenalId, getHealth());
        }
        level.playSound(null, getX(), getY(), getZ(), ModSounds.STATION_UPGRADE.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.4, getZ(), 8, 0.2, 0.2, 0.2, 0.05);
        if (getHealth() >= getMaxHealth()) {
            EdithServer.send(serverPlayer, Component.translatable("arsenal.starktech.msg.repaired"),
                    EdithMessagePayload.Level.INFO);
        } else {
            EdithServer.send(serverPlayer, Component.translatable("arsenal.starktech.msg.repairing",
                    Math.round(getHealth()), Math.round(getMaxHealth())), EdithMessagePayload.Level.INFO);
        }
        return InteractionResult.SUCCESS;
    }

    /** Meldt deze drone terug in het arsenaal van zijn eigenaar, met de schade die hij opliep. */
    private void returnToArsenal(ServerLevel level) {
        ServerPlayer owner = getOwner(level);
        if (arsenalId != null && owner != null) {
            ArsenalServer.returnToOrbit(owner, arsenalId, getHealth());
        }
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
                && (ownerId.equals(attacker.getUUID()) || attacker instanceof StarkDroneEntity)) {
            return false;
        }
        return super.hurt(source, damage);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            dropCargo(level);
            // Neergehaald: uit het arsenaal, en een beetje schroot valt op de grond.
            ServerPlayer owner = getOwner(level);
            if (arsenalId != null && owner != null) {
                ArsenalServer.destroyed(owner, arsenalId, level, position());
            }
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
            tag.putUUID("StarkOwner", ownerId);
        }
        if (arsenalId != null) {
            tag.putUUID("StarkArsenal", arsenalId);
        }
        tag.putByte("StarkMode", (byte) getMode().ordinal());
        tag.putInt("StarkSlot", slot);
        tag.putInt("StarkLife", lifeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("StarkOwner") ? tag.getUUID("StarkOwner") : null;
        arsenalId = tag.hasUUID("StarkArsenal") ? tag.getUUID("StarkArsenal") : null;
        if (tag.contains("StarkMode")) {
            entityData.set(DATA_MODE, tag.getByte("StarkMode"));
        }
        slot = tag.getInt("StarkSlot");
        lifeTicks = tag.contains("StarkLife") ? tag.getInt("StarkLife") : -1;
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
        private final StarkDroneEntity drone;

        DroneMoveControl(StarkDroneEntity drone) {
            super(drone);
            this.drone = drone;
        }

        @Override
        public void tick() {
            if (drone.isScout()) {
                return; // de verkenner wordt rechtstreeks bestuurd
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
