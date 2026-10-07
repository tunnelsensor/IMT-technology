package com.senne.starktech.edith;

import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.drone.EdithDrones;
import com.senne.starktech.nav.EdithNavigation;
import com.senne.starktech.network.DroneCameraPayload;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.network.DroneControlPayload;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.EdithStatusPayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.network.OreScanPayload;
import com.senne.starktech.network.PingMarkerPayload;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.comms.EdithComms;
import com.senne.starktech.nav.EdithWaypoints;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.hack.EdithHack;
import com.senne.starktech.illusion.EdithIllusions;
import com.senne.starktech.illusion.IllusionScene;
import com.senne.starktech.satellite.SatelliteMode;
import com.senne.starktech.satellite.SatelliteStats;
import com.senne.starktech.satellite.SatelliteUpgrades;
import com.senne.starktech.satellite.SatelliteWorkshop;
import com.senne.starktech.satellite.SatelliteStrikes;
import com.senne.starktech.spy.EdithSpy;
import com.senne.starktech.suit.EdithSuit;
import com.senne.starktech.transport.EdithTransport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Het "brein" van E.D.I.T.H. aan de serverkant. Hier wordt beslist wat mag en wat niet:
 * eigenaar controleren, energie verbruiken, nachtzicht geven, scans goedkeuren,
 * doelwitten markeren, drones lanceren en de satelliet richten.
 *
 * Alles wat met eerlijkheid te maken heeft (energie, cooldowns) gebeurt hier op de server,
 * zodat spelers op een server niet kunnen valsspelen.
 */
public final class EdithServer {
    private EdithServer() {}

    /** Hoe lang ons nachtzicht-effect duurt. We verversen het elke seconde, dus het loopt nooit af. */
    private static final int NIGHT_VISION_DURATION = 400;

    /** Tijdelijke gegevens per speler die niet in de bril zelf hoeven (worden niet opgeslagen). */
    private static final class PlayerState {
        boolean wasWearing;
        /** Van wie de bril was die de speler vorige tick op had (null = van niemand, of geen bril). */
        @Nullable UUID wornOwner;
        boolean warnedLowBattery;
        boolean warnedEmptyBattery;
        /** Wanneer elke functie weer mag (speltijd in ticks), op de volgorde van EdithCooldown. */
        final long[] readyAt = new long[EdithCooldown.COUNT];
        /** Gemarkeerde doelwitten (op volgorde van markeren). */
        final List<UUID> marked = new ArrayList<>();
        /** De verkenningsdrone waar de speler nu door kijkt, of null. */
        @Nullable UUID scoutId;
        DroneControlPayload scoutControl = DroneControlPayload.IDLE;
        long lastControlTick;
        /** De laatste besturing voor een transportdrone (zelf vliegen). */
        DroneControlPayload transportControl = DroneControlPayload.IDLE;
        long lastTransportTick;
        /** De drone waar de speler alleen door MEEKIJKT (spionagedrone), of null. */
        @Nullable UUID viewDroneId;
        /** Schuivende laser: het beginpunt dat de speler al koos (en wanneer), of null. */
        @Nullable Vec3 sweepStart;
        long sweepStartAt;
    }

    private static final Map<UUID, PlayerState> STATES = new HashMap<>();

    private static PlayerState state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState());
    }

    // ---------------------------------------------------------------- Events

    /** Wordt elke tick (20x per seconde) voor elke speler aangeroepen. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            EdithThreats.forget(player);
            EdithTransport.forgetSoftLandings();
        }
    }

    /** Wereld gesloten: alles vergeten (cooldowns horen bij de tijd van die ene wereld). */
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
        EdithThreats.clear();
        EdithTransport.forgetSoftLandings();
    }

    // ---------------------------------------------------------------- Elke tick

    private static void tick(ServerPlayer player) {
        PlayerState state = state(player);
        ItemStack glasses = EdithData.getWornGlasses(player);
        boolean wearing = !glasses.isEmpty();

        // Opzetten telt ook als je de ene bril direct voor de andere verwisselt (rechtsklik met een
        // bril terwijl je er al een op hebt): dan zat er geen tick zonder bril tussen. Een andere
        // bril herkennen we aan zijn eigenaar.
        UUID owner = wearing ? ownerId(glasses) : null;
        boolean swapped = wearing && state.wasWearing && !Objects.equals(owner, state.wornOwner);
        if (wearing && (!state.wasWearing || swapped)) {
            onEquip(player, glasses);
            owner = ownerId(glasses); // een nieuwe bril heeft nu misschien net een eigenaar
        } else if (!wearing && state.wasWearing) {
            removeOurNightVision(player);
        }
        state.wasWearing = wearing;
        state.wornOwner = owner;

        // Verkenningsdrone kwijt (neergeschoten, bril af...)? Dan terug naar je lichaam.
        if (state.scoutId != null && (!wearing || !EdithData.isAuthorized(glasses, player) || getScout(player, state) == null)) {
            endScout(player, "edith.starktech.msg.scout_lost", EdithMessagePayload.Level.WARNING);
        }

        // Kijk je mee door een spionagedrone die er niet meer is (of is je bril af)? Dan stoppen.
        if (state.viewDroneId != null) {
            boolean droneGone = !(player.serverLevel().getEntity(state.viewDroneId) instanceof StarkDroneEntity drone)
                    || !drone.isAlive()
                    || drone.distanceToSqr(player) > (EdithBalance.SPY_VIEW_RANGE + 40) * (EdithBalance.SPY_VIEW_RANGE + 40);
            if (!wearing || droneGone) {
                stopWatching(player);
            }
        }

        if (!wearing) {
            return;
        }
        if (!EdithData.isAuthorized(glasses, player)) {
            removeOurNightVision(player);
            return;
        }
        // Eén keer per seconde energie verbruiken en effecten verversen.
        if (player.tickCount % 20 == 0) {
            secondTick(player, glasses, state);
        }
        // Zakken naar de grond nadat een drone je heeft losgelaten.
        EdithTransport.tickSoftLanding(player);
        // Af en toe: relais die er niet meer staan uit je redstone-lijst gooien.
        if (player.tickCount % 100 == 0) {
            EdithRedstone.pruneGhosts(player);
        }
        // Twee keer per seconde de HUD bijwerken.
        if (player.tickCount % 10 == 0) {
            sendStatus(player, state);
        }
    }

    private static @Nullable UUID ownerId(ItemStack glasses) {
        EdithOwner owner = EdithData.getOwner(glasses);
        return owner == null ? null : owner.id();
    }

    /** De speler heeft net een bril opgezet. */
    private static void onEquip(ServerPlayer player, ItemStack glasses) {
        EdithOwner owner = EdithData.getOwner(glasses);
        String name = player.getGameProfile().getName();
        if (owner == null || owner.id().equals(player.getUUID()) || owner.transferPending()) {
            StarkAdvancements.award(player, StarkAdvancements.GLASSES);
        }

        if (owner == null) {
            // Nieuwe bril: de eerste drager wordt de eigenaar.
            EdithData.setOwner(glasses, new EdithOwner(player.getUUID(), name, false));
            send(player, Component.translatable("edith.starktech.msg.registered", name), EdithMessagePayload.Level.BOOT);
        } else if (owner.id().equals(player.getUUID())) {
            // Update de naam, voor het geval de speler van naam veranderd is.
            if (!owner.name().equals(name)) {
                EdithData.setOwner(glasses, new EdithOwner(player.getUUID(), name, owner.transferPending()));
            }
            send(player, Component.translatable("edith.starktech.msg.welcome_back", name), EdithMessagePayload.Level.BOOT);
            if (owner.transferPending()) {
                send(player, Component.translatable("edith.starktech.msg.transfer_still_pending"), EdithMessagePayload.Level.INFO);
            }
        } else if (owner.transferPending()) {
            // De vorige eigenaar heeft de bril vrijgegeven: deze speler wordt de nieuwe eigenaar.
            EdithData.setOwner(glasses, new EdithOwner(player.getUUID(), name, false));
            send(player, Component.translatable("edith.starktech.msg.transfer_complete", owner.name(), name), EdithMessagePayload.Level.BOOT);
        } else {
            send(player, Component.translatable("edith.starktech.msg.access_denied", owner.name()), EdithMessagePayload.Level.ALERT);
            return;
        }

        if (EdithData.getEnergy(glasses) <= 0) {
            send(player, Component.translatable("edith.starktech.msg.battery_empty"), EdithMessagePayload.Level.WARNING);
        }
    }

    /** Eén keer per seconde: energie verbruiken, waarschuwingen, nachtzicht, drones. */
    private static void secondTick(ServerPlayer player, ItemStack glasses, PlayerState state) {
        int drain = EdithBalance.DRAIN_HUD;
        for (EdithFeature feature : EdithFeature.values()) {
            if (EdithData.isEnabled(glasses, feature)) {
                drain += feature.drainPerSecond();
            }
        }
        if (state.scoutId != null) {
            drain += EdithBalance.DRAIN_SCOUT;
        }
        if (state.viewDroneId != null) {
            drain += EdithBalance.DRAIN_SPY_VIEW;
        }

        int energy = EdithData.getEnergy(glasses);
        if (energy > 0) {
            energy = Math.max(0, energy - drain);
            EdithData.setEnergy(glasses, energy);
        }

        // Waarschuwing: batterij leeg.
        if (energy <= 0) {
            if (!state.warnedEmptyBattery) {
                send(player, Component.translatable("edith.starktech.msg.battery_empty"), EdithMessagePayload.Level.ALERT);
                state.warnedEmptyBattery = true;
            }
        } else {
            state.warnedEmptyBattery = false;
        }

        // Waarschuwing: batterij bijna leeg.
        float fraction = EdithData.getEnergyFraction(glasses);
        if (energy > 0 && fraction < EdithBalance.LOW_BATTERY_WARNING) {
            if (!state.warnedLowBattery) {
                int percent = Math.round(fraction * 100);
                send(player, Component.translatable("edith.starktech.msg.battery_low", percent), EdithMessagePayload.Level.WARNING);
                state.warnedLowBattery = true;
            }
        } else if (fraction > EdithBalance.LOW_BATTERY_WARNING + 0.05f) {
            state.warnedLowBattery = false;
        }

        // Nachtzicht geven of weghalen.
        if (energy > 0 && EdithData.isEnabled(glasses, EdithFeature.NIGHT_VISION)) {
            applyNightVision(player);
        } else {
            removeOurNightVision(player);
        }

        // Beschermende drones bijroepen als er te weinig zijn.
        // (Wegsturen gaat vanzelf: drones controleren zelf of ze nog nodig zijn.)
        if (energy > 0 && EdithData.isEnabled(glasses, EdithFeature.GUARD_DRONES)) {
            EdithDrones.maintainGuards(player, glasses);
        }

        cleanUpMarks(player, state);
        EdithNavigation.tick(player);
        // Vliegen met drones kost energie; is de bril leeg, dan zet hij je neer.
        EdithTransport.tickRider(player, glasses);
        // Hacken kost energie per gehackte speler.
        EdithHack.tick(player, glasses);
    }

    // ---------------------------------------------------------------- Nachtzicht

    /**
     * Is dit nachtzicht-effect van E.D.I.T.H. (en niet van een drankje)? Ons effect is
     * "ambient", onzichtbaar en duurt nooit langer dan NIGHT_VISION_DURATION.
     */
    private static boolean isOurNightVision(MobEffectInstance effect) {
        return effect.isAmbient() && !effect.isVisible() && effect.getDuration() <= NIGHT_VISION_DURATION;
    }

    private static void applyNightVision(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        // Een nachtzicht-drankje laten we met rust.
        if (current == null || isOurNightVision(current)) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_DURATION, 0, true, false, false));
        }
    }

    private static void removeOurNightVision(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (current != null && isOurNightVision(current)) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    // ---------------------------------------------------------------- Acties van de speler

    /**
     * Geeft de bril terug als de speler hem draagt en de eigenaar is. Zo niet, dan krijgt
     * hij een melding en geeft dit null terug.
     */
    public static @Nullable ItemStack authorizedGlasses(ServerPlayer player) {
        ItemStack glasses = EdithData.getWornGlasses(player);
        if (glasses.isEmpty()) {
            return null;
        }
        if (!EdithData.isAuthorized(glasses, player)) {
            EdithOwner owner = EdithData.getOwner(glasses);
            send(player, Component.translatable("edith.starktech.msg.access_denied", owner == null ? "?" : owner.name()), EdithMessagePayload.Level.ALERT);
            return null;
        }
        return glasses;
    }

    /** Een speler vraagt E.D.I.T.H. iets te doen (via het menu of een sneltoets). */
    public static void handleAction(ServerPlayer player, int actionId) {
        EdithAction action = EdithAction.byId(actionId);
        if (action == null) {
            return;
        }
        ItemStack glasses = authorizedGlasses(player);
        if (glasses == null) {
            return;
        }

        EdithFeature feature = action.toggles();
        if (feature != null) {
            boolean on = EdithData.toggle(glasses, feature);
            String key = on ? "edith.starktech.msg.feature_on" : "edith.starktech.msg.feature_off";
            send(player, Component.translatable(key, Component.translatable(feature.translationKey())), EdithMessagePayload.Level.INFO);
            if (feature == EdithFeature.NIGHT_VISION && !on) {
                removeOurNightVision(player);
            }
            if (feature == EdithFeature.SHIELD) {
                // Je persoonlijke rayshield: "vwoem" aan, wegzakkend zoemen uit (7 oktober).
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                        on ? com.senne.starktech.registry.ModSounds.RAYSHIELD_ON.get()
                                : com.senne.starktech.registry.ModSounds.RAYSHIELD_OFF.get(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
            }
            return;
        }

        switch (action) {
            case ORE_SCAN -> oreScan(player, glasses);
            case AUTHORIZE_TRANSFER -> toggleTransfer(player, glasses);
            case LAUNCH_DRONES -> launchDrones(player, glasses);
            case SATELLITE_STRIKE -> satelliteStrike(player, glasses, SatelliteMode.PRECISION);
            case CLEAR_MARKS -> clearMarks(player);
            case DRONE_CAMERA -> toggleScout(player, glasses);
            // Fase 5
            case DRONE_STRIKE -> droneStrike(player, glasses);
            // Fase 6
            case SPY_DRONE -> spyDrone(player, glasses);
            case SPY_VIEW -> toggleSpyView(player);
            case SPY_REPORT -> EdithSpy.sendReport(player);
            case STAND_DOWN -> standDown(player, glasses);
            // Fase 7
            case TRANSPORT_RIDE -> transport(player, glasses, false);
            case TRANSPORT_AUTOPILOT -> transport(player, glasses, true);
            case CARGO_DRONE -> cargo(player, glasses);
            case CARRY_ENTITY -> carry(player, glasses);
            // Fase 10
            case HACK_PLAYER -> hackPlayer(player);
            case HACK_STOP -> EdithHack.stopAll(player, true);
            // Fase 12
            case ILLUSION_WALL -> EdithIllusions.start(player, glasses, IllusionScene.Kind.WALL);
            case ILLUSION_ARMY -> EdithIllusions.start(player, glasses, IllusionScene.Kind.ARMY);
            case ILLUSION_CLOAK -> EdithIllusions.start(player, glasses, IllusionScene.Kind.CLOAK);
            case ILLUSION_STOP -> EdithIllusions.stopAll(player);
            // Fase 15
            case REPULSOR -> EdithSuit.repulsor(player);
            case ROCKETS -> com.senne.starktech.suit.SuitRockets.fire(player);
            case STRIKE_PRECISION -> satelliteStrike(player, glasses, SatelliteMode.PRECISION);
            case STRIKE_BOMBARDMENT -> satelliteStrike(player, glasses, SatelliteMode.BOMBARDMENT);
            case STRIKE_SWEEP -> satelliteStrike(player, glasses, SatelliteMode.SWEEP);
            case DRONE_FORMATION -> {
                com.senne.starktech.drone.DroneFormation next = EdithData.getFormation(glasses).next();
                EdithData.setFormation(glasses, next);
                send(player, Component.translatable("edith.starktech.msg.formation", Component.translatable(next.translationKey())),
                        EdithMessagePayload.Level.INFO);
            }
            default -> { }
        }
    }

    private static void oreScan(ServerPlayer player, ItemStack glasses) {
        PlayerState state = state(player);
        if (!checkCooldown(player, state, EdithCooldown.ORE_SCAN)
                || !payEnergy(player, glasses, EdithBalance.ORE_SCAN_COST)) {
            return;
        }
        startCooldown(player, state, EdithCooldown.ORE_SCAN, EdithBalance.ORE_SCAN_COOLDOWN);
        ModNetwork.sendTo(player, new OreScanPayload(EdithData.getOreScanRadius(glasses)));
    }

    private static void toggleTransfer(ServerPlayer player, ItemStack glasses) {
        EdithOwner owner = EdithData.getOwner(glasses);
        if (owner == null) {
            return;
        }
        boolean pending = !owner.transferPending();
        EdithData.setOwner(glasses, owner.withTransferPending(pending));
        String key = pending ? "edith.starktech.msg.transfer_authorized" : "edith.starktech.msg.transfer_cancelled";
        send(player, Component.translatable(key), EdithMessagePayload.Level.INFO);
    }

    // ---------------------------------------------------------------- Drones

    /**
     * Hangt er nog minstens één drone klaar in je baan?
     *
     * Zo niet, dan zeggen we dat en gebeurt er verder niets: je raakt geen energie kwijt en er
     * begint geen wachttijd. Nieuwe drones bouw je met een recept; zie het arsenaal-scherm.
     */
    private static boolean requireDrone(ServerPlayer player) {
        if (ArsenalServer.get(player).availableCount() > 0) {
            return true;
        }
        send(player, Component.translatable("arsenal.starktech.msg.empty"), EdithMessagePayload.Level.WARNING);
        return false;
    }

    private static void launchDrones(ServerPlayer player, ItemStack glasses) {
        PlayerState state = state(player);
        if (!requireDrone(player)
                || !checkCooldown(player, state, EdithCooldown.SWARM)
                || !payEnergy(player, glasses, EdithBalance.SWARM_COST)) {
            return;
        }
        // Niets gemarkeerd? Dan nemen we wat de speler aankijkt.
        if (getMarkedTargets(player).isEmpty()) {
            LivingEntity looked = findLookTarget(player, EdithBalance.TARGET_RANGE);
            if (looked != null && EdithTargeting.canTarget(player, looked)) {
                state.marked.add(looked.getUUID());
            }
        }
        startCooldown(player, state, EdithCooldown.SWARM, EdithBalance.SWARM_COOLDOWN);
        int launched = EdithDrones.launchSwarm(player, glasses);
        if (launched > 0) {
            StarkAdvancements.award(player, StarkAdvancements.SWARM);
        }
        int targets = getMarkedTargets(player).size();
        Component message = targets > 0
                ? Component.translatable("edith.starktech.msg.swarm_launched", launched, targets)
                : Component.translatable("edith.starktech.msg.swarm_launched_defend", launched);
        send(player, message, EdithMessagePayload.Level.INFO);
    }

    /**
     * Stuurt EEN drone op het wezen waar je naar kijkt (of op je eerste gemarkeerde doelwit).
     * Veel goedkoper dan een hele zwerm, en hij blijft op dat ene doelwit jagen tot het dood is.
     */
    private static void droneStrike(ServerPlayer player, ItemStack glasses) {
        PlayerState state = state(player);
        if (!checkCooldown(player, state, EdithCooldown.STRIKE)) {
            return;
        }
        // Eerst kijken waar je naar kijkt; zie je niets, dan het eerste gemarkeerde doelwit.
        LivingEntity target = findLookTarget(player, EdithBalance.STRIKE_DRONE_RANGE);
        if (target == null) {
            List<LivingEntity> marked = getMarkedTargets(player);
            target = marked.isEmpty() ? null : marked.getFirst();
        }
        if (target == null) {
            send(player, Component.translatable("edith.starktech.msg.no_target_in_sight"), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (!EdithTargeting.canTarget(player, target)) {
            send(player, Component.translatable("edith.starktech.msg.cannot_mark", target.getDisplayName()), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (!requireDrone(player) || !payEnergy(player, glasses, EdithBalance.STRIKE_DRONE_COST)) {
            return;
        }
        startCooldown(player, state, EdithCooldown.STRIKE, EdithBalance.STRIKE_DRONE_COOLDOWN);
        if (EdithDrones.launchStrike(player, target) != null) {
            send(player, Component.translatable("edith.starktech.msg.strike_launched", target.getDisplayName()),
                    EdithMessagePayload.Level.INFO);
        }
        sendStatus(player, state);
    }

    /**
     * "Stand down": alles wat aanvalt stopt meteen.
     * - de zwerm en losse aanvalsdrones vliegen weg
     * - de escorte gaat uit (die drones vertrekken dan zelf)
     * - een satelliet-aanval die aan het opladen is wordt afgebroken
     * - alle markeringen gaan weg, zodat er niets opnieuw begint
     *
     * De spionagedrone blijft wel: die valt niemand aan.
     */
    private static void standDown(ServerPlayer player, ItemStack glasses) {
        PlayerState state = state(player);
        int attackers = EdithDrones.recallAll(player, StarkDroneEntity.Mode.ATTACK);
        int guards = 0;
        if (EdithData.isEnabled(glasses, EdithFeature.GUARD_DRONES)) {
            EdithData.toggle(glasses, EdithFeature.GUARD_DRONES);
            guards = EdithDrones.recallAll(player, StarkDroneEntity.Mode.GUARD);
        }
        boolean satellite = SatelliteStrikes.cancel(player.getUUID());
        state.marked.clear();
        send(player, Component.translatable("edith.starktech.msg.stand_down", attackers + guards),
                EdithMessagePayload.Level.WARNING);
        if (satellite) {
            send(player, Component.translatable("edith.starktech.msg.satellite_cancelled"), EdithMessagePayload.Level.INFO);
        }
        sendStatus(player, state);
    }

    /** Hackt de speler waar je naar kijkt (of stopt die hack). */
    private static void hackPlayer(ServerPlayer player) {
        LivingEntity looked = findLookTarget(player, EdithBalance.HACK_RANGE);
        if (!(looked instanceof ServerPlayer target)) {
            send(player, Component.translatable("edith.starktech.msg.hack_no_player"), EdithMessagePayload.Level.WARNING);
            return;
        }
        EdithHack.toggle(player, target);
        sendStatus(player, state(player));
    }

    // ---------------------------------------------------------------- Transport (fase 7)

    /** Laat je oppikken door drones: zelf sturen of met de autopiloot. */
    private static void transport(ServerPlayer player, ItemStack glasses, boolean autopilot) {
        // Hang je er al aan? Dan zetten we je neer (geen kosten, geen wachttijd).
        if (EdithTransport.carrierOf(player) != null) {
            EdithTransport.startRide(player, glasses, autopilot);
            return;
        }
        PlayerState state = state(player);
        if (!requireDrone(player) || !checkCooldown(player, state, EdithCooldown.TRANSPORT)
                || !payEnergy(player, glasses, EdithBalance.TRANSPORT_COST)) {
            return;
        }
        startCooldown(player, state, EdithCooldown.TRANSPORT, EdithBalance.TRANSPORT_COOLDOWN);
        EdithTransport.startRide(player, glasses, autopilot);
        sendStatus(player, state);
    }

    /** Stuurt de spullen uit de kist waar je naar kijkt naar je bestemming. */
    private static void cargo(ServerPlayer player, ItemStack glasses) {
        PlayerState state = state(player);
        if (!requireDrone(player) || !checkCooldown(player, state, EdithCooldown.TRANSPORT)
                || !payEnergy(player, glasses, EdithBalance.TRANSPORT_COST)) {
            return;
        }
        startCooldown(player, state, EdithCooldown.TRANSPORT, EdithBalance.TRANSPORT_COOLDOWN);
        EdithTransport.sendCargo(player, glasses);
        sendStatus(player, state);
    }

    /** Pakt het wezen waar je naar kijkt op en zet het bij je bestemming neer. */
    private static void carry(ServerPlayer player, ItemStack glasses) {
        PlayerState state = state(player);
        LivingEntity target = findLookTarget(player, EdithBalance.TARGET_RANGE);
        if (target == null) {
            send(player, Component.translatable("edith.starktech.msg.no_target_in_sight"), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (!requireDrone(player) || !checkCooldown(player, state, EdithCooldown.TRANSPORT)
                || !payEnergy(player, glasses, EdithBalance.TRANSPORT_COST)) {
            return;
        }
        startCooldown(player, state, EdithCooldown.TRANSPORT, EdithBalance.TRANSPORT_COOLDOWN);
        EdithTransport.carry(player, target);
        sendStatus(player, state);
    }

    // ---------------------------------------------------------------- Spionagedrone (fase 6)

    /** Spionagedrone sturen naar wie je aankijkt, of de huidige opdracht stoppen. */
    private static void spyDrone(ServerPlayer player, ItemStack glasses) {
        if (EdithSpy.of(player) != null) {
            EdithSpy.stop(player, "edith.starktech.spy.log.end_recalled");
            return;
        }
        PlayerState state = state(player);
        if (!checkCooldown(player, state, EdithCooldown.SPY)) {
            return;
        }
        // Spioneren mag op iedereen en alles (het doet geen pijn), behalve op je eigen drones.
        LivingEntity target = findLookTarget(player, EdithBalance.SPY_RANGE);
        if (target == null) {
            List<LivingEntity> marked = getMarkedTargets(player);
            target = marked.isEmpty() ? null : marked.getFirst();
        }
        if (target == null) {
            send(player, Component.translatable("edith.starktech.msg.no_target_in_sight"), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (!requireDrone(player) || !payEnergy(player, glasses, EdithBalance.SPY_COST)) {
            return;
        }
        startCooldown(player, state, EdithCooldown.SPY, EdithBalance.SPY_COOLDOWN);
        EdithSpy.start(player, target);
        sendStatus(player, state);
    }

    /**
     * Live meekijken door de spionagedrone (of stoppen). Dit kan alleen als de drone in
     * jouw wereld en niet te ver weg is: jouw spel kent alleen de wezens in de buurt.
     */
    private static void toggleSpyView(ServerPlayer player) {
        PlayerState state = state(player);
        if (state.viewDroneId != null) {
            stopWatching(player);
            return;
        }
        EdithSpy.Mission mission = EdithSpy.of(player);
        UUID droneId = mission == null ? null : mission.droneId();
        if (droneId == null) {
            send(player, Component.translatable("edith.starktech.msg.spy_none"), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (!(player.serverLevel().getEntity(droneId) instanceof StarkDroneEntity drone)
                || drone.distanceToSqr(player) > EdithBalance.SPY_VIEW_RANGE * EdithBalance.SPY_VIEW_RANGE) {
            send(player, Component.translatable("edith.starktech.msg.spy_signal_weak"), EdithMessagePayload.Level.WARNING);
            return;
        }
        // Je eigen verkenningsdrone kan niet tegelijk: eerst die terugroepen.
        if (state.scoutId != null) {
            endScout(player, "edith.starktech.msg.scout_returned", EdithMessagePayload.Level.INFO);
        }
        state.viewDroneId = droneId;
        EdithSpy.setWatching(player, true);
        ModNetwork.sendTo(player, new DroneCameraPayload(drone.getId(), false));
        send(player, Component.translatable("edith.starktech.msg.spy_view_on"), EdithMessagePayload.Level.INFO);
    }

    /** Stopt het meekijken: terug naar je eigen ogen. */
    public static void stopWatching(ServerPlayer player) {
        PlayerState state = state(player);
        if (state.viewDroneId == null) {
            return;
        }
        state.viewDroneId = null;
        EdithSpy.setWatching(player, false);
        ModNetwork.sendTo(player, DroneCameraPayload.OFF);
        send(player, Component.translatable("edith.starktech.msg.spy_view_off"), EdithMessagePayload.Level.INFO);
    }

    /** Kijkt deze speler nu mee door een spionagedrone? */
    private static boolean isWatching(ServerPlayer player) {
        PlayerState state = STATES.get(player.getUUID());
        return state != null && state.viewDroneId != null;
    }

    // ---------------------------------------------------------------- Doelwitten markeren

    /** De speler wil een wezen markeren (of de markering weghalen). */
    public static void handleMark(ServerPlayer player, int entityId) {
        if (authorizedGlasses(player) == null) {
            return;
        }
        Entity entity = player.serverLevel().getEntity(entityId);
        double maxRange = EdithBalance.TARGET_RANGE + 8;
        if (!(entity instanceof LivingEntity target)) {
            return;
        }
        // Je kunt markeren vanaf je lichaam, of vanaf je verkenningsdrone.
        StarkDroneEntity scout = getScout(player, state(player));
        boolean inRange = target.distanceToSqr(player) <= maxRange * maxRange
                || (scout != null && target.distanceToSqr(scout) <= maxRange * maxRange);
        if (!inRange) {
            return;
        }
        PlayerState state = state(player);
        if (state.marked.remove(target.getUUID())) {
            send(player, Component.translatable("edith.starktech.msg.unmarked", target.getDisplayName()), EdithMessagePayload.Level.INFO);
            return;
        }
        if (!EdithTargeting.canTarget(player, target)) {
            send(player, Component.translatable("edith.starktech.msg.cannot_mark", target.getDisplayName()), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (state.marked.size() >= EdithBalance.MAX_MARKED_TARGETS) {
            state.marked.removeFirst();
        }
        state.marked.add(target.getUUID());
        send(player, Component.translatable("edith.starktech.msg.marked", target.getDisplayName(), state.marked.size()), EdithMessagePayload.Level.INFO);
        sendStatus(player, state);
    }

    private static void clearMarks(ServerPlayer player) {
        state(player).marked.clear();
        send(player, Component.translatable("edith.starktech.msg.marks_cleared"), EdithMessagePayload.Level.INFO);
    }

    /** De gemarkeerde doelwitten die nog leven en in de buurt zijn. Wordt ook door drones gebruikt. */
    public static List<LivingEntity> getMarkedTargets(ServerPlayer player) {
        PlayerState state = STATES.get(player.getUUID());
        if (state == null || state.marked.isEmpty()) {
            return List.of();
        }
        List<LivingEntity> result = new ArrayList<>();
        ServerLevel level = player.serverLevel();
        for (UUID id : state.marked) {
            if (level.getEntity(id) instanceof LivingEntity living && isMarkStillValid(player, living)) {
                result.add(living);
            }
        }
        return result;
    }

    private static boolean isMarkStillValid(ServerPlayer player, LivingEntity target) {
        return target.isAlive() && EdithTargeting.canTarget(player, target) && target.distanceToSqr(player) < 128 * 128;
    }

    /** Haalt dode, verdwenen of te verre doelwitten uit de lijst. */
    private static void cleanUpMarks(ServerPlayer player, PlayerState state) {
        ServerLevel level = player.serverLevel();
        Iterator<UUID> it = state.marked.iterator();
        while (it.hasNext()) {
            if (!(level.getEntity(it.next()) instanceof LivingEntity living) || !isMarkStillValid(player, living)) {
                it.remove();
            }
        }
    }

    /** Het wezen waar de speler naar kijkt, voor functies buiten deze klasse (zoals de raketten). */
    public static @Nullable LivingEntity lookTarget(ServerPlayer player, double range) {
        return findLookTarget(player, range);
    }

    /**
     * Het wezen waar de speler naar kijkt (zelfde idee als de doelwit-analyse, maar op de server).
     * Kijk je door je verkenningsdrone, dan kijken we vanaf die drone.
     */
    private static @Nullable LivingEntity findLookTarget(ServerPlayer player, double range) {
        StarkDroneEntity scout = getScout(player, state(player));
        Vec3 eye = scout != null ? scout.getEyePosition() : player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        // Zit er een blok in de weg? Dan kijken we niet verder dan dat blok.
        HitResult blockHit = player.serverLevel().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            range = blockHit.getLocation().distanceTo(eye);
            end = eye.add(look.scale(range));
        }
        AABB searchBox = new AABB(eye, end).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, searchBox,
                e -> e instanceof LivingEntity && !(e instanceof StarkDroneEntity) && !e.isSpectator() && e.isPickable(), range * range);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    // ---------------------------------------------------------------- Satelliet

    /**
     * Een satellietaanval: K = precisieraket, P = bombardement, C = schuivende laser. Er zijn geen
     * standen om in te stellen; welke aanval het is, zit in de toets. (De bril onthoudt wel welke
     * aanval er onderweg is, voor de HUD.)
     *
     * Waar hij op richt, in deze volgorde:
     * 1. het WEZEN waar je naar kijkt (dan volgt de raket het, ook als het wegloopt);
     * 2. het blok waar je naar kijkt (tot 500 blokken ver);
     * 3. je bestemming uit de navigatie;
     * 4. je laatste ping.
     */
    private static void satelliteStrike(ServerPlayer player, ItemStack glasses, SatelliteMode mode) {
        if (!SatelliteWorkshop.get(player).unlocked(mode)) {
            send(player, Component.translatable("satellite.starktech.msg.locked", Component.translatable(mode.translationKey())),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        PlayerState state = state(player);
        // Een half gekozen laserlijn (alleen het beginpunt) vervalt als je iets anders afvuurt.
        if (mode != SatelliteMode.SWEEP) {
            state.sweepStart = null;
        }
        if (SatelliteStrikes.pendingFor(player.getUUID()) != null || !checkCooldown(player, state, EdithCooldown.SATELLITE)) {
            return;
        }
        SatelliteUpgrades upgrades = SatelliteWorkshop.get(player);
        double range = SatelliteStats.range(upgrades.targeting());
        UUID locked = null;
        Vec3 target = null;

        LivingEntity looked = findLookTarget(player, range);
        if (looked != null && EdithTargeting.canTarget(player, looked)) {
            locked = looked.getUUID();
            target = looked.position();
        }
        if (target == null) {
            HitResult hit = player.pick(range, 1.0f, true);
            if (hit.getType() != HitResult.Type.MISS) {
                target = hit.getLocation();
            }
        }
        if (target == null) {
            target = navigationPoint(player);
        }
        if (target == null) {
            target = EdithComms.lastPing(player);
        }
        if (target == null) {
            send(player, Component.translatable("edith.starktech.msg.satellite_no_target"), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (target.distanceTo(player.position()) < EdithBalance.SATELLITE_MIN_DISTANCE) {
            send(player, Component.translatable("edith.starktech.msg.satellite_too_close"), EdithMessagePayload.Level.WARNING);
            return;
        }
        // De satelliet hangt in de ruimte: het doel moet de lucht kunnen zien.
        if (!player.serverLevel().canSeeSky(BlockPos.containing(target).above())) {
            send(player, Component.translatable("edith.starktech.msg.satellite_no_sky"), EdithMessagePayload.Level.WARNING);
            return;
        }
        // De schuivende laser kies je in twee keer: eerst het beginpunt (dat kost nog niets), dan
        // het eindpunt. Pas bij het tweede punt vuurt hij, van het begin naar het eind.
        Vec3 sweepTo = null;
        if (mode == SatelliteMode.SWEEP) {
            long now = player.level().getGameTime();
            if (state.sweepStart == null || now - state.sweepStartAt > EdithBalance.SWEEP_START_TIMEOUT) {
                state.sweepStart = target;
                state.sweepStartAt = now;
                ModNetwork.sendTo(player, new PingMarkerPayload(PingMarkerPayload.SWEEP_START, target));
                send(player, Component.translatable("edith.starktech.msg.sweep_start_set"), EdithMessagePayload.Level.INFO);
                return;
            }
            sweepTo = target;
            target = state.sweepStart;
            locked = null; // een lijn over de grond, niet achter een wezen aan
        }
        if (!payEnergy(player, glasses, mode.cost())) {
            return;
        }
        state.sweepStart = null;
        EdithData.setSatelliteMode(glasses, mode); // voor de HUD: welke aanval er onderweg is
        startCooldown(player, state, EdithCooldown.SATELLITE, SatelliteStats.cooldownTicks(upgrades.recharge()));
        SatelliteStrikes.start(player.serverLevel(), target, player.getUUID(), mode, locked, sweepTo);
        StarkAdvancements.award(player, StarkAdvancements.SATELLITE);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SATELLITE_LOCK.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        send(player, Component.translatable("edith.starktech.msg.satellite_locked_mode",
                Component.translatable(mode.translationKey()), SatelliteStats.chargeTicks(upgrades.targeting()) / 20),
                EdithMessagePayload.Level.WARNING);
        sendStatus(player, state);
    }

    /** Waar de speler naartoe navigeert (als dat in deze wereld is), of null. */
    private static @Nullable Vec3 navigationPoint(ServerPlayer player) {
        EdithWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        if (data.active() == EdithWaypoints.NONE) {
            return null;
        }
        return EdithNavigation.targetPos(player, data)
                .filter(pos -> pos.dimension() == player.level().dimension())
                .map(pos -> Vec3.atCenterOf(pos.pos()))
                .orElse(null);
    }

    // ---------------------------------------------------------------- Verkenningsdrone (drone-camera)

    /** Drone-camera aan of uit. */
    private static void toggleScout(ServerPlayer player, ItemStack glasses) {
        PlayerState state = state(player);
        if (state.scoutId != null) {
            endScout(player, "edith.starktech.msg.scout_returned", EdithMessagePayload.Level.INFO);
            return;
        }
        if (EdithData.getEnergy(glasses) < EdithBalance.SCOUT_COST) {
            send(player, Component.translatable("edith.starktech.msg.not_enough_energy", EdithBalance.SCOUT_COST), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (!requireDrone(player)) {
            return;
        }
        StarkDroneEntity drone = EdithDrones.launchScout(player);
        if (drone == null) {
            return;
        }
        // Pas betalen als de drone echt in de lucht is.
        EdithData.tryUseEnergy(glasses, EdithBalance.SCOUT_COST);
        state.scoutId = drone.getUUID();
        state.scoutControl = new DroneControlPayload(0, 0, 0, false, player.getYRot(), player.getXRot());
        state.lastControlTick = player.level().getGameTime();
        ModNetwork.sendTo(player, new DroneCameraPayload(drone.getId(), true));
        send(player, Component.translatable("edith.starktech.msg.scout_online"), EdithMessagePayload.Level.INFO);
    }

    /** Stopt de drone-camera: de drone vliegt weg en de speler kijkt weer door zijn eigen ogen. */
    private static void endScout(ServerPlayer player, String messageKey, EdithMessagePayload.Level level) {
        PlayerState state = state(player);
        StarkDroneEntity scout = getScout(player, state);
        state.scoutId = null;
        if (scout != null) {
            scout.recall(player.serverLevel());
        }
        ModNetwork.sendTo(player, DroneCameraPayload.OFF);
        send(player, Component.translatable(messageKey), level);
    }

    private static @Nullable StarkDroneEntity getScout(ServerPlayer player, PlayerState state) {
        if (state.scoutId == null) {
            return null;
        }
        return player.serverLevel().getEntity(state.scoutId) instanceof StarkDroneEntity drone && drone.isAlive() ? drone : null;
    }

    /** Is dit de verkenningsdrone waar de speler nu door kijkt? (Wordt door de drone zelf gevraagd.) */
    public static boolean isActiveScout(ServerPlayer player, StarkDroneEntity drone) {
        PlayerState state = STATES.get(player.getUUID());
        return state != null && drone.getUUID().equals(state.scoutId);
    }

    /** Hoe de speler de verkenningsdrone op dit moment bestuurt. */
    public static DroneControlPayload getScoutControl(ServerPlayer player) {
        PlayerState state = STATES.get(player.getUUID());
        if (state == null) {
            return DroneControlPayload.IDLE;
        }
        DroneControlPayload control = state.scoutControl;
        // Al een halve seconde niets gehoord (bv. haperende verbinding)? Dan blijft hij hangen.
        if (player.level().getGameTime() - state.lastControlTick > 10) {
            return new DroneControlPayload(0, 0, 0, false, control.yaw(), control.pitch());
        }
        return control;
    }

    /**
     * Hoe de speler zijn transportdrone bestuurt (zelf vliegen). Hoort er al een halve seconde
     * niets binnen te komen, dan blijft de drone stil hangen.
     */
    public static DroneControlPayload getTransportControl(ServerPlayer player) {
        PlayerState state = STATES.get(player.getUUID());
        if (state == null || player.level().getGameTime() - state.lastTransportTick > 10) {
            return DroneControlPayload.IDLE;
        }
        return state.transportControl;
    }

    /**
     * Besturing van een drone, elke tick gestuurd door het spel van de speler. Hetzelfde bericht
     * gebruiken we voor de verkenningsdrone (waar je door kijkt) en voor de transportdrone
     * (waar je onder hangt).
     */
    public static void handleDroneControl(ServerPlayer player, DroneControlPayload payload) {
        // state() in plaats van STATES.get(): het eerste besturingsbericht kan binnenkomen
        // voordat de speler ooit getickt is, en dan mag het niet verloren gaan.
        PlayerState state = state(player);
        // Waarden netjes binnen de grenzen houden (een gehackte client kan rare getallen sturen).
        DroneControlPayload safe = new DroneControlPayload(
                Mth.clamp(payload.forward(), -1, 1),
                Mth.clamp(payload.strafe(), -1, 1),
                Mth.clamp(payload.vertical(), -1, 1),
                payload.boost(),
                Mth.wrapDegrees(payload.yaw()),
                Mth.clamp(payload.pitch(), -90, 90));
        // Hang je onder een transportdrone? Dan is dit de besturing daarvan.
        if (EdithTransport.carrierOf(player) != null) {
            state.transportControl = safe;
            state.lastTransportTick = player.level().getGameTime();
            return;
        }
        // Richt je een illusie? Dan bestuur je die drone.
        if (EdithIllusions.steer(player, safe)) {
            return;
        }
        if (state.scoutId == null) {
            return;
        }
        state.scoutControl = safe;
        state.lastControlTick = player.level().getGameTime();
    }



    /** Wordt de speler geraakt terwijl hij door de drone kijkt? Dan meteen terug naar zijn lichaam. */
    public static void onLivingDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0) {
            PlayerState state = STATES.get(player.getUUID());
            if (state != null && state.scoutId != null) {
                endScout(player, "edith.starktech.msg.scout_body_attacked", EdithMessagePayload.Level.ALERT);
            }
            if (isWatching(player)) {
                stopWatching(player);
            }
            EdithIllusions.cancelAim(player, "edith.starktech.msg.illusion_aim_attacked");
        }
    }

    // ---------------------------------------------------------------- Hulpjes

    /**
     * Is deze functie weer beschikbaar? Zonder melding: voor dingen die je ingedrukt houdt,
     * zoals de repulsor, zou je anders elke tick "nog 1 seconde" in beeld krijgen.
     */
    public static boolean isReady(ServerPlayer player, EdithCooldown cooldown) {
        return player.level().getGameTime() >= state(player).readyAt[cooldown.ordinal()];
    }

    /** Zet de wachttijd van een functie op "nu + ticks" (voor functies buiten deze klasse). */
    public static void startCooldown(ServerPlayer player, EdithCooldown cooldown, int ticks) {
        startCooldown(player, state(player), cooldown, ticks);
    }

    /**
     * Alle wachttijden en markeringen weg, en geen drone-camera meer. Alleen voor de showcase
     * (client/dev/Showcase), die elke functie vanaf een schone lei wil laten zien.
     */
    public static void resetForShowcase(ServerPlayer player) {
        PlayerState state = state(player);
        java.util.Arrays.fill(state.readyAt, 0L);
        state.marked.clear();
        if (state.scoutId != null || state.viewDroneId != null) {
            ModNetwork.sendTo(player, DroneCameraPayload.OFF);
        }
        state.scoutId = null;
        state.viewDroneId = null;
        state.sweepStart = null;
        sendStatus(player, state);
    }

    /** Is deze functie weer beschikbaar? Zo niet, dan krijgt de speler een melding. */
    private static boolean checkCooldown(ServerPlayer player, PlayerState state, EdithCooldown cooldown) {
        return checkCooldown(player, state.readyAt[cooldown.ordinal()]);
    }

    /** Zet de wachttijd van een functie op "nu + ticks". */
    private static void startCooldown(ServerPlayer player, PlayerState state, EdithCooldown cooldown, int ticks) {
        state.readyAt[cooldown.ordinal()] = player.level().getGameTime() + ticks;
    }

    /** Is de cooldown voorbij? Zo niet, dan krijgt de speler een melding. */
    private static boolean checkCooldown(ServerPlayer player, long readyAtTick) {
        long now = player.level().getGameTime();
        if (now < readyAtTick) {
            long secondsLeft = (readyAtTick - now + 19) / 20;
            send(player, Component.translatable("edith.starktech.msg.cooldown", secondsLeft), EdithMessagePayload.Level.WARNING);
            return false;
        }
        return true;
    }

    /** Probeert energie te betalen. Zo niet, dan krijgt de speler een melding. */
    private static boolean payEnergy(ServerPlayer player, ItemStack glasses, int cost) {
        if (!EdithData.tryUseEnergy(glasses, cost)) {
            send(player, Component.translatable("edith.starktech.msg.not_enough_energy", cost), EdithMessagePayload.Level.WARNING);
            return false;
        }
        return true;
    }

    private static int ticksLeft(ServerPlayer player, long readyAtTick) {
        return (int) Math.max(0, readyAtTick - player.level().getGameTime());
    }

    /** Stuurt de HUD-status (drones, cooldowns, markeringen, satelliet) naar de speler. */
    private static void sendStatus(ServerPlayer player, PlayerState state) {
        int swarm = EdithDrones.find(player, StarkDroneEntity.Mode.ATTACK).size();
        int guards = EdithDrones.find(player, StarkDroneEntity.Mode.GUARD).size();
        List<Integer> markedIds = getMarkedTargets(player).stream().map(Entity::getId).toList();
        SatelliteStrikes.Strike strike = SatelliteStrikes.pendingFor(player.getUUID());
        List<Integer> cooldowns = new ArrayList<>(EdithCooldown.COUNT);
        for (EdithCooldown cooldown : EdithCooldown.values()) {
            cooldowns.add(ticksLeft(player, state.readyAt[cooldown.ordinal()]));
        }
        // Wie valt je aan? Alleen dat markeren we; zie EdithThreats.
        List<Integer> threatIds = List.of();
        ItemStack worn = EdithData.getWornGlasses(player);
        if (EdithData.isEnabled(worn, EdithFeature.THREAT_SCAN)) {
            int radius = EdithData.getThreatRadius(worn);
            threatIds = new ArrayList<>();
            for (LivingEntity threat : EdithThreats.of(player, radius)) {
                threatIds.add(threat.getId());
            }
            threatIds.addAll(EdithThreats.incomingProjectiles(player, radius));
        }
        ModNetwork.sendTo(player, new EdithStatusPayload(
                swarm, guards,
                cooldowns,
                markedIds,
                strike == null ? Optional.empty() : Optional.of(strike.target()),
                strike == null ? 0 : strike.ticksLeft(),
                EdithHack.targetsOf(player).size(),
                threatIds));
    }

    public static void send(ServerPlayer player, Component message, EdithMessagePayload.Level level) {
        ModNetwork.sendTo(player, new EdithMessagePayload(message, level));
    }
}
