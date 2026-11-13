package com.senne.imttech.imt;

import com.senne.imttech.arsenal.ArsenalServer;
import com.senne.imttech.arsenal.Orbit;
import com.senne.imttech.config.Numbers;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.drone.ImtDrones;
import com.senne.imttech.nav.ImtNavigation;
import com.senne.imttech.network.DroneCameraPayload;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.network.DroneControlPayload;
import com.senne.imttech.drone.DroneEntity;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.network.ImtStatusPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.network.OreScanPayload;
import com.senne.imttech.network.PingMarkerPayload;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.comms.ImtComms;
import com.senne.imttech.nav.ImtWaypoints;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.hack.ImtHack;
import com.senne.imttech.logic.DatacenterGlasses;
import com.senne.imttech.illusion.ImtIllusions;
import com.senne.imttech.illusion.IllusionScene;
import com.senne.imttech.satellite.SatelliteMode;
import com.senne.imttech.satellite.SatelliteStats;
import com.senne.imttech.satellite.SatelliteUpgrades;
import com.senne.imttech.satellite.SatelliteWorkshop;
import com.senne.imttech.satellite.SatelliteStrikes;
import com.senne.imttech.spy.ImtSpy;
import com.senne.imttech.suit.ImtSuit;
import com.senne.imttech.transport.ImtTransport;
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
 * Het "brein" van I.M.T aan de serverkant. Hier wordt beslist wat mag en wat niet:
 * eigenaar controleren, energie verbruiken, nachtzicht geven, scans goedkeuren,
 * doelwitten markeren, drones lanceren en de satelliet richten.
 *
 * Alles wat met eerlijkheid te maken heeft (energie, cooldowns) gebeurt hier op de server,
 * zodat spelers op een server niet kunnen valsspelen.
 */
public final class ImtServer {
    private ImtServer() {}

    /** Hoe lang ons nachtzicht-effect duurt. We verversen het elke seconde, dus het loopt nooit af. */
    private static final int NIGHT_VISION_DURATION = 400;

    /** Tijdelijke gegevens per speler die niet in de bril zelf hoeven (worden niet opgeslagen). */
    private static final class PlayerState {
        boolean wasWearing;
        /** Van wie de bril was die de speler vorige tick op had (null = van niemand, of geen bril). */
        @Nullable UUID wornOwner;
        boolean warnedLowBattery;
        /** Wanneer elke functie weer mag (speltijd in ticks), op de volgorde van ImtCooldown. */
        final long[] readyAt = new long[ImtCooldown.COUNT];
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
            ImtThreats.forget(player);
        }
    }

    /** Wereld gesloten: alles vergeten (cooldowns horen bij de tijd van die ene wereld). */
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
        ImtThreats.clear();
    }

    // ---------------------------------------------------------------- Elke tick

    private static void tick(ServerPlayer player) {
        PlayerState state = state(player);
        ItemStack glasses = ImtData.getWornGlasses(player);
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

        // Verkenningsdrone kwijt (neergeschoten, bril af...)? Dan terug naar je lichaam. Hoofdstuk 96: ook als de batterij
        // leeg is, want dan is de bril uit (je keek anders zonder HUD door de drone, en kon niet meer terug).
        boolean batteryDead = wearing && ImtData.getEnergy(glasses) <= 0;
        if (state.scoutId != null && (!wearing || !ImtData.isAuthorized(glasses, player) || getScout(player, state) == null
                || batteryDead)) {
            endScout(player, "imt.imttech.msg.scout_lost", ImtMessagePayload.Level.WARNING);
        }

        // Kijk je mee door een spionagedrone die er niet meer is (of is je bril af)? Dan stoppen.
        if (state.viewDroneId != null) {
            boolean droneGone = !(player.serverLevel().getEntity(state.viewDroneId) instanceof DroneEntity drone)
                    || !drone.isAlive()
                    || drone.distanceToSqr(player) > (ImtConfig.Recon.SPY_VIEW_RANGE + 40) * (ImtConfig.Recon.SPY_VIEW_RANGE + 40);
            if (!wearing || droneGone || batteryDead) {
                stopWatching(player);
            }
        }

        if (!wearing) {
            return;
        }
        if (!ImtData.isAuthorized(glasses, player)) {
            removeOurNightVision(player);
            return;
        }
        // Eén keer per seconde energie verbruiken en effecten verversen.
        if (player.tickCount % 20 == 0) {
            secondTick(player, glasses, state);
        }
        // Af en toe: relais die er niet meer staan uit je redstone-lijst gooien.
        if (player.tickCount % 100 == 0) {
            ImtRedstone.pruneGhosts(player);
        }
        // Twee keer per seconde de HUD bijwerken.
        if (player.tickCount % 10 == 0) {
            sendStatus(player, state);
        }
    }

    private static @Nullable UUID ownerId(ItemStack glasses) {
        ImtOwner owner = ImtData.getOwner(glasses);
        return owner == null ? null : owner.id();
    }

    /** De speler heeft net een bril opgezet. */
    private static void onEquip(ServerPlayer player, ItemStack glasses) {
        ImtOwner owner = ImtData.getOwner(glasses);
        String name = player.getGameProfile().getName();
        if (owner == null || owner.id().equals(player.getUUID()) || owner.transferPending()) {
            ImtAdvancements.award(player, ImtAdvancements.GLASSES);
        }

        if (owner == null) {
            // Nieuwe bril: de eerste drager wordt de eigenaar.
            ImtData.setOwner(glasses, new ImtOwner(player.getUUID(), name, false));
            send(player, Component.translatable("imt.imttech.msg.registered", name), ImtMessagePayload.Level.BOOT);
        } else if (owner.id().equals(player.getUUID())) {
            // Update de naam, voor het geval de speler van naam veranderd is.
            if (!owner.name().equals(name)) {
                ImtData.setOwner(glasses, new ImtOwner(player.getUUID(), name, owner.transferPending()));
            }
            send(player, Component.translatable("imt.imttech.msg.welcome_back", name), ImtMessagePayload.Level.BOOT);
            if (owner.transferPending()) {
                send(player, Component.translatable("imt.imttech.msg.transfer_still_pending"), ImtMessagePayload.Level.INFO);
            }
        } else if (owner.transferPending()) {
            // De vorige eigenaar heeft de bril vrijgegeven: deze speler wordt de nieuwe eigenaar.
            ImtData.setOwner(glasses, new ImtOwner(player.getUUID(), name, false));
            send(player, Component.translatable("imt.imttech.msg.transfer_complete", owner.name(), name), ImtMessagePayload.Level.BOOT);
        } else {
            send(player, Component.translatable("imt.imttech.msg.access_denied", owner.name()), ImtMessagePayload.Level.ALERT);
        }
        // (Een lege bril opzetten: hoofdstuk 96, dan zegt hij niets, ook niet "Battery depleted": send() zwijgt dan.)
    }

    /** Eén keer per seconde: energie verbruiken, waarschuwingen, nachtzicht, drones. */
    private static void secondTick(ServerPlayer player, ItemStack glasses, PlayerState state) {
        double drain = ImtConfig.Glasses.DRAIN_HUD;
        for (ImtFeature feature : ImtFeature.values()) {
            if (ImtData.isEnabled(glasses, feature)) {
                drain += feature.drainPerSecond();
            }
        }
        if (state.scoutId != null) {
            drain += ImtConfig.Recon.SCOUT_DRAIN;
        }
        if (state.viewDroneId != null) {
            drain += ImtConfig.Recon.SPY_VIEW_DRAIN;
        }

        double energy = ImtData.getExactEnergy(glasses);
        if (energy > 0) {
            energy = Math.max(0, energy - drain);
            ImtData.setEnergy(glasses, energy);
        }

        // Batterij leeg: dat zegt je spel zelf, met het uitvalbeeld, als laatste zin (hoofdstuk 96, zie
        // ImtClientEvents.trackBattery). Vanaf dan zegt de server niets meer (zie send).

        // Waarschuwing: batterij bijna leeg.
        float fraction = ImtData.getEnergyFraction(glasses);
        if (energy > 0 && fraction < ImtConfig.Glasses.LOW_BATTERY_WARNING) {
            if (!state.warnedLowBattery) {
                int percent = Math.round(fraction * 100);
                send(player, Component.translatable("imt.imttech.msg.battery_low", percent), ImtMessagePayload.Level.WARNING);
                state.warnedLowBattery = true;
            }
        } else if (fraction > ImtConfig.Glasses.LOW_BATTERY_WARNING + 0.05f) {
            state.warnedLowBattery = false;
        }

        // Nachtzicht geven of weghalen.
        if (energy > 0 && ImtData.isEnabled(glasses, ImtFeature.NIGHT_VISION)) {
            applyNightVision(player);
        } else {
            removeOurNightVision(player);
        }

        // Beschermende drones bijroepen als er te weinig zijn.
        // (Wegsturen gaat vanzelf: drones controleren zelf of ze nog nodig zijn.)
        if (energy > 0 && ImtData.isEnabled(glasses, ImtFeature.GUARD_DRONES)) {
            ImtDrones.maintainGuards(player, glasses);
        }

        cleanUpMarks(player, state);
        // Hoofdstuk 106: is je datacenter weg of draait het niet meer, dan stopt wat erop doorliep. En elke seconde hoort
        // je datacenter wat er van jouw bril doorloopt (zie DatacenterGlasses).
        datacenterSecond(player, glasses);
        ImtNavigation.tick(player);
        // Vliegen met drones kost energie; is de bril leeg, dan zet hij je neer.
        ImtTransport.tickRider(player, glasses);
        // Hacken kost energie per gehackte speler.
        ImtHack.tick(player, glasses);
    }

    // ---------------------------------------------------------------- Nachtzicht

    /**
     * Is dit nachtzicht-effect van I.M.T (en niet van een drankje)? Ons effect is
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
        ItemStack glasses = ImtData.getWornGlasses(player);
        if (glasses.isEmpty()) {
            return null;
        }
        if (!ImtData.isAuthorized(glasses, player)) {
            ImtOwner owner = ImtData.getOwner(glasses);
            send(player, Component.translatable("imt.imttech.msg.access_denied", owner == null ? "?" : owner.name()), ImtMessagePayload.Level.ALERT);
            return null;
        }
        return glasses;
    }

    /** Een speler vraagt I.M.T iets te doen (via het menu of een sneltoets). */
    public static void handleAction(ServerPlayer player, int actionId) {
        ImtAction action = ImtAction.byId(actionId);
        if (action == null) {
            return;
        }
        ItemStack glasses = authorizedGlasses(player);
        if (glasses == null) {
            return;
        }
        // Hoofdstuk 96: een lege bril is uit. Hij doet niets en zegt niets (ook geen "Not enough energy"); je spel stuurt dan
        // zelf al niets meer, dit is het vangnet. Alleen "het brein is klaar met nadenken" komt nog door.
        if (ImtData.getEnergy(glasses) <= 0 && action != ImtAction.BRAIN_DONE) {
            return;
        }
        // Na een EMP-granaat (hoofdstuk 35) start de bril opnieuw op: tot dan doet hij niets.
        if (com.senne.imttech.weapons.Emp.isJammed(player)) {
            player.displayClientMessage(Component.translatable("hud.imttech.emp_reboot",
                    net.minecraft.util.Mth.ceil(com.senne.imttech.weapons.Emp.ticksLeft(player) / 20f))
                    .withStyle(net.minecraft.ChatFormatting.AQUA), true);
            return;
        }

        // Hoofdstuk 107 (jouw wens: "zorg dat de satelliet niet in nether en end werkt"): je satelliet hangt in dezelfde
        // baan als je drones, om de gewone wereld. Boven de Nether en het End hangt niets (zie arsenal/Orbit), dus daar
        // kan hij niet inslaan. Dat zeggen we meteen, nog voor het richten: geen energie weg, geen wachttijd, en een half
        // gekozen laserlijn blijft staan. (Vroeger hield alleen "het doel moet open lucht zien" het tegen, omdat die
        // werelden geen hemellicht hebben; nu is het een eigen regel met een eigen melding.)
        if (action.isSatelliteStrike() && !Orbit.reachable(player.level())) {
            send(player, Component.translatable("imt.imttech.msg.satellite_no_orbit"), ImtMessagePayload.Level.WARNING);
            return;
        }

        // Hoofdstuk 106 (jouw wens: "je hebt een datacenter nodig voor de IMT glasses"): alles wat nadenkt, draait op je
        // datacenter. Heb je er geen, draait het niet, of past dit er nu niet bij, dan zegt I.M.T dat en gebeurt er niets:
        // geen energie weg, en er begint geen wachttijd. Uitzetten, terugroepen en landen kan altijd.
        DatacenterGlasses.Kind need = datacenterNeed(player, glasses, action);
        if (need != null && !DatacenterGlasses.ready(player, need)) {
            return;
        }
        double energyBefore = ImtData.getExactEnergy(glasses);

        ImtFeature feature = action.toggles();
        if (feature != null) {
            boolean on = ImtData.toggle(glasses, feature);
            String key = on ? "imt.imttech.msg.feature_on" : "imt.imttech.msg.feature_off";
            send(player, Component.translatable(key, Component.translatable(feature.translationKey())), ImtMessagePayload.Level.INFO);
            // Wat doorloopt (de scans) neemt meteen zijn stuk van je datacenter, niet pas bij de volgende seconde.
            reportStanding(player, glasses);
            if (feature == ImtFeature.GUARD_DRONES && on && ArsenalServer.readyCount(player) <= 0
                    && ImtDrones.find(player, DroneEntity.Mode.GUARD).isEmpty()) {
                // Hoofdstuk 106: je zet je escorte aan, maar er is hier geen drone om te komen (in de Nether of het End
                // alleen wat met je meekwam): zeg dat, anders gebeurt er stil niets.
                ArsenalServer.tellNoneReady(player);
            }
            if (feature == ImtFeature.NIGHT_VISION && !on) {
                removeOurNightVision(player);
            }
            if (feature == ImtFeature.SHIELD) {
                // Je persoonlijke rayshield: "vwoem" aan, wegzakkend zoemen uit (7 oktober).
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                        on ? com.senne.imttech.registry.ModSounds.RAYSHIELD_ON.get()
                                : com.senne.imttech.registry.ModSounds.RAYSHIELD_OFF.get(),
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
            case SPY_REPORT -> ImtSpy.sendReport(player);
            case STAND_DOWN -> standDown(player, glasses);
            // Het brein op het datacenter (8 oktober)
            case BRAIN_THINKING -> com.senne.imttech.logic.DatacenterBrain.start(player);
            case BRAIN_DONE -> com.senne.imttech.logic.DatacenterBrain.stop(player);
            // Fase 7
            case TRANSPORT_RIDE -> transport(player, glasses, false);
            case TRANSPORT_AUTOPILOT -> transport(player, glasses, true);
            case CARGO_DRONE -> cargo(player, glasses);
            case CARRY_ENTITY -> carry(player, glasses);
            // Fase 10
            case HACK_PLAYER -> hackPlayer(player);
            case HACK_STOP -> ImtHack.stopAll(player, true);
            // Fase 12
            case ILLUSION_WALL -> ImtIllusions.start(player, glasses, IllusionScene.Kind.WALL);
            case ILLUSION_ARMY -> ImtIllusions.start(player, glasses, IllusionScene.Kind.ARMY);
            case ILLUSION_CLOAK -> ImtIllusions.start(player, glasses, IllusionScene.Kind.CLOAK);
            case ILLUSION_STOP -> ImtIllusions.stopAll(player);
            // Fase 15
            case LASER_CANNON -> ImtSuit.laserCannon(player);
            case ROCKETS -> com.senne.imttech.suit.SuitRockets.fire(player);
            case STRIKE_PRECISION -> satelliteStrike(player, glasses, SatelliteMode.PRECISION);
            case STRIKE_BOMBARDMENT -> satelliteStrike(player, glasses, SatelliteMode.BOMBARDMENT);
            case STRIKE_SWEEP -> satelliteStrike(player, glasses, SatelliteMode.SWEEP);
            case DRONE_FORMATION -> {
                com.senne.imttech.drone.DroneFormation next = ImtData.getFormation(glasses).next();
                ImtData.setFormation(glasses, next);
                send(player, Component.translatable("imt.imttech.msg.formation", Component.translatable(next.translationKey())),
                        ImtMessagePayload.Level.INFO);
            }
            default -> { }
        }
        if (need != null) {
            if (!need.standing() && ImtData.getExactEnergy(glasses) < energyBefore) {
                // Het kostte energie, dus het verzoek ging echt door: je datacenter is er nu even mee bezig (hoofdstuk 106).
                DatacenterGlasses.started(player, need);
            }
            // En wat doorloopt (een spion, een hack) neemt meteen zijn stuk.
            reportStanding(player, glasses);
        }
    }

    /**
     * Hoofdstuk 106: wat deze actie van je datacenter vraagt, of null als ze er geen nodig heeft. Nodig heeft: alles wat
     * nadenkt (de satelliet, de scans, opdrachten aan je drones, hacken, een spion, illusies). Niet nodig: wat de bril of
     * het harnas zelf doet (nachtzicht, je schild, de laser cannon, de raketten, de formatie kiezen), en alles wat iets
     * STOPT: uitzetten, terugroepen, landen en "stand down" werken altijd, ook zonder datacenter.
     */
    private static @Nullable DatacenterGlasses.Kind datacenterNeed(ServerPlayer player, ItemStack glasses, ImtAction action) {
        return switch (action) {
            case ORE_SCAN -> DatacenterGlasses.Kind.SCAN;
            case SATELLITE_STRIKE, STRIKE_PRECISION, STRIKE_BOMBARDMENT, STRIKE_SWEEP -> DatacenterGlasses.Kind.SATELLITE;
            case LAUNCH_DRONES, DRONE_STRIKE, CARGO_DRONE, CARRY_ENTITY, ILLUSION_WALL, ILLUSION_ARMY, ILLUSION_CLOAK ->
                    DatacenterGlasses.Kind.ORDER;
            // Aanzetten vraagt je datacenter; nog eens drukken is stoppen, en dat kan altijd.
            case DRONE_CAMERA -> state(player).scoutId == null ? DatacenterGlasses.Kind.ORDER : null;
            case SPY_DRONE -> ImtSpy.of(player) == null ? DatacenterGlasses.Kind.SPY : null;
            case TRANSPORT_RIDE, TRANSPORT_AUTOPILOT ->
                    ImtTransport.carrierOf(player) == null ? DatacenterGlasses.Kind.ORDER : null;
            case HACK_PLAYER -> findLookTarget(player, ImtConfig.Hack.RANGE) instanceof ServerPlayer target
                    && ImtHack.isHacking(player, target.getUUID()) ? null : DatacenterGlasses.Kind.HACK;
            case TOGGLE_THREAT_SCAN ->
                    ImtData.isEnabled(glasses, ImtFeature.THREAT_SCAN) ? null : DatacenterGlasses.Kind.THREAT_SCAN;
            case TOGGLE_TARGET_ANALYSIS ->
                    ImtData.isEnabled(glasses, ImtFeature.TARGET_ANALYSIS) ? null : DatacenterGlasses.Kind.TARGET_ANALYSIS;
            case TOGGLE_GUARD_DRONES ->
                    ImtData.isEnabled(glasses, ImtFeature.GUARD_DRONES) ? null : DatacenterGlasses.Kind.ORDER;
            default -> null;
        };
    }

    /**
     * Hoofdstuk 106: zegt je datacenter wat er van jouw bril doorloopt (navigeren, de dreigingsscan, de doelwit-analyse,
     * elke speler die je hackt, een spionagedrone) en hoeveel waypoints hij bewaart. Elke seconde, en meteen als je iets
     * aan- of uitzet. Zie DatacenterGlasses.standing.
     */
    /**
     * Elke seconde voor wie zijn bril op heeft: is je datacenter weg of draait het niet meer, dan stopt wat erop doorliep;
     * en je datacenter hoort wat er van jouw bril doorloopt.
     */
    private static void datacenterSecond(ServerPlayer player, ItemStack glasses) {
        if (!DatacenterGlasses.linked(player)) {
            stopWhatNeedsTheDatacenter(player);
        }
        reportStanding(player, glasses);
    }

    /** Hetzelfde, voor de bril die deze speler op heeft (voor de automatische tests: een testspeler tikt niet vanzelf). */
    public static void reportToDatacenter(ServerPlayer player) {
        ItemStack glasses = ImtData.getWornGlasses(player);
        if (!glasses.isEmpty() && ImtData.isAuthorized(glasses, player)) {
            datacenterSecond(player, glasses);
        }
    }

    private static void reportStanding(ServerPlayer player, ItemStack glasses) {
        List<DatacenterGlasses.Kind> running = new ArrayList<>();
        ImtWaypoints waypoints = player.getData(ModAttachments.WAYPOINTS);
        if (ImtData.getEnergy(glasses) > 0) {
            if (waypoints.active() != ImtWaypoints.NONE) {
                running.add(DatacenterGlasses.Kind.NAVIGATION);
            }
            if (ImtData.isEnabled(glasses, ImtFeature.THREAT_SCAN)) {
                running.add(DatacenterGlasses.Kind.THREAT_SCAN);
            }
            if (ImtData.isEnabled(glasses, ImtFeature.TARGET_ANALYSIS)) {
                running.add(DatacenterGlasses.Kind.TARGET_ANALYSIS);
            }
            for (int i = 0; i < ImtHack.targetsOf(player).size(); i++) {
                running.add(DatacenterGlasses.Kind.HACK);
            }
            if (ImtSpy.of(player) != null) {
                running.add(DatacenterGlasses.Kind.SPY);
            }
        }
        DatacenterGlasses.standing(player, running, waypoints.waypoints().size());
    }

    /**
     * Hoofdstuk 106: je datacenter is weg of draait niet meer. Wat erop doorliep, stopt: je navigatie, je hacks en je
     * spionagedrone. (De scans blijven "aan" staan in je bril, maar tonen niets tot je datacenter er weer is.)
     */
    private static void stopWhatNeedsTheDatacenter(ServerPlayer player) {
        boolean stopped = false;
        ImtWaypoints waypoints = player.getData(ModAttachments.WAYPOINTS);
        if (waypoints.active() != ImtWaypoints.NONE) {
            player.setData(ModAttachments.WAYPOINTS, waypoints.withActive(ImtWaypoints.NONE));
            stopped = true;
        }
        if (!ImtHack.targetsOf(player).isEmpty()) {
            ImtHack.stopAll(player, false);
            stopped = true;
        }
        if (ImtSpy.of(player) != null) {
            ImtSpy.stop(player, "imt.imttech.spy.log.end_no_datacenter");
            stopped = true;
        }
        if (stopped) {
            send(player, Component.translatable("imt.imttech.msg.datacenter_lost"), ImtMessagePayload.Level.ALERT);
        }
    }

    private static void oreScan(ServerPlayer player, ItemStack glasses) {
        PlayerState state = state(player);
        if (!checkCooldown(player, state, ImtCooldown.ORE_SCAN)
                || !payEnergy(player, glasses, ImtConfig.Scanner.ORE_SCAN_COST)) {
            return;
        }
        startCooldown(player, state, ImtCooldown.ORE_SCAN, Math.round(ImtConfig.Scanner.ORE_SCAN_WAIT));
        ModNetwork.sendTo(player, new OreScanPayload(ImtData.getOreScanRadius(glasses)));
    }

    private static void toggleTransfer(ServerPlayer player, ItemStack glasses) {
        ImtOwner owner = ImtData.getOwner(glasses);
        if (owner == null) {
            return;
        }
        boolean pending = !owner.transferPending();
        ImtData.setOwner(glasses, owner.withTransferPending(pending));
        String key = pending ? "imt.imttech.msg.transfer_authorized" : "imt.imttech.msg.transfer_cancelled";
        send(player, Component.translatable(key), ImtMessagePayload.Level.INFO);
    }

    // ---------------------------------------------------------------- Drones

    /**
     * Hangt er nog minstens één drone klaar in je baan?
     *
     * Zo niet, dan zeggen we dat en gebeurt er verder niets: je raakt geen energie kwijt en er
     * begint geen wachttijd. Nieuwe drones bouw je met een recept; zie het arsenaal-scherm.
     */
    private static boolean requireDrone(ServerPlayer player) {
        // Hoofdstuk 106: in de Nether en het End tellen alleen de drones die met je meekwamen (zie arsenal/Orbit).
        if (ArsenalServer.readyCount(player) > 0) {
            return true;
        }
        ArsenalServer.tellNoneReady(player);
        return false;
    }

    private static void launchDrones(ServerPlayer player, ItemStack glasses) {
        PlayerState state = state(player);
        if (!requireDrone(player)
                || !checkCooldown(player, state, ImtCooldown.SWARM)
                || !payEnergy(player, glasses, ImtConfig.Drones.SWARM_COST)) {
            return;
        }
        // Niets gemarkeerd? Dan nemen we wat de speler aankijkt.
        if (getMarkedTargets(player).isEmpty()) {
            LivingEntity looked = findLookTarget(player, ImtConfig.Glasses.TARGET_RANGE);
            if (looked != null && ImtTargeting.canTarget(player, looked)) {
                state.marked.add(looked.getUUID());
            }
        }
        startCooldown(player, state, ImtCooldown.SWARM, Math.round(ImtConfig.Drones.SWARM_COOLDOWN));
        int launched = ImtDrones.launchSwarm(player, glasses);
        if (launched > 0) {
            ImtAdvancements.award(player, ImtAdvancements.SWARM);
        }
        int targets = getMarkedTargets(player).size();
        Component message = targets > 0
                ? Component.translatable("imt.imttech.msg.swarm_launched", launched, targets)
                : Component.translatable("imt.imttech.msg.swarm_launched_defend", launched);
        send(player, message, ImtMessagePayload.Level.INFO);
    }

    /**
     * Stuurt EEN drone op het wezen waar je naar kijkt (of op je eerste gemarkeerde doelwit).
     * Veel goedkoper dan een hele zwerm, en hij blijft op dat ene doelwit jagen tot het dood is.
     */
    private static void droneStrike(ServerPlayer player, ItemStack glasses) {
        PlayerState state = state(player);
        if (!checkCooldown(player, state, ImtCooldown.STRIKE)) {
            return;
        }
        // Eerst kijken waar je naar kijkt; zie je niets, dan je laatst gemarkeerde doelwit (hoofdstuk 97; was het eerste).
        LivingEntity target = lookedOrMarked(player, ImtConfig.Drones.STRIKE_RANGE);
        if (target == null) {
            return;
        }
        if (!ImtTargeting.canTarget(player, target)) {
            send(player, Component.translatable("imt.imttech.msg.cannot_mark", target.getDisplayName()), ImtMessagePayload.Level.WARNING);
            return;
        }
        if (!requireDrone(player) || !payEnergy(player, glasses, ImtConfig.Drones.STRIKE_COST)) {
            return;
        }
        startCooldown(player, state, ImtCooldown.STRIKE, Math.round(ImtConfig.Drones.STRIKE_COOLDOWN));
        if (ImtDrones.launchStrike(player, target) != null) {
            send(player, Component.translatable("imt.imttech.msg.strike_launched", target.getDisplayName()),
                    ImtMessagePayload.Level.INFO);
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
        int attackers = ImtDrones.recallAll(player, DroneEntity.Mode.ATTACK);
        int guards = 0;
        if (ImtData.isEnabled(glasses, ImtFeature.GUARD_DRONES)) {
            ImtData.toggle(glasses, ImtFeature.GUARD_DRONES);
            guards = ImtDrones.recallAll(player, DroneEntity.Mode.GUARD);
        }
        boolean satellite = SatelliteStrikes.cancel(player.getUUID());
        state.marked.clear();
        send(player, Component.translatable("imt.imttech.msg.stand_down", attackers + guards),
                ImtMessagePayload.Level.WARNING);
        if (satellite) {
            send(player, Component.translatable("imt.imttech.msg.satellite_cancelled"), ImtMessagePayload.Level.INFO);
        }
        sendStatus(player, state);
    }

    /** Hackt de speler waar je naar kijkt (of stopt die hack). */
    private static void hackPlayer(ServerPlayer player) {
        LivingEntity looked = findLookTarget(player, ImtConfig.Hack.RANGE);
        if (!(looked instanceof ServerPlayer target)) {
            send(player, Component.translatable("imt.imttech.msg.hack_no_player"), ImtMessagePayload.Level.WARNING);
            return;
        }
        ImtHack.toggle(player, target);
        sendStatus(player, state(player));
    }

    // ---------------------------------------------------------------- Transport (fase 7)

    /** Laat je oppikken door drones: zelf sturen of met de autopiloot. */
    private static void transport(ServerPlayer player, ItemStack glasses, boolean autopilot) {
        // Hang je er al aan? Dan zetten we je neer (geen kosten, geen wachttijd).
        if (ImtTransport.carrierOf(player) != null) {
            ImtTransport.startRide(player, glasses, autopilot);
            return;
        }
        PlayerState state = state(player);
        if (!requireDrone(player) || !checkCooldown(player, state, ImtCooldown.TRANSPORT)
                || !payEnergy(player, glasses, ImtConfig.Transport.COST)) {
            return;
        }
        startCooldown(player, state, ImtCooldown.TRANSPORT, Math.round(ImtConfig.Transport.COOLDOWN));
        ImtTransport.startRide(player, glasses, autopilot);
        sendStatus(player, state);
    }

    /** Stuurt de spullen uit de kist waar je naar kijkt naar je bestemming. */
    private static void cargo(ServerPlayer player, ItemStack glasses) {
        PlayerState state = state(player);
        if (!requireDrone(player) || !checkCooldown(player, state, ImtCooldown.TRANSPORT)
                || !payEnergy(player, glasses, ImtConfig.Transport.COST)) {
            return;
        }
        startCooldown(player, state, ImtCooldown.TRANSPORT, Math.round(ImtConfig.Transport.COOLDOWN));
        ImtTransport.sendCargo(player, glasses);
        sendStatus(player, state);
    }

    /** Pakt het wezen waar je naar kijkt op en zet het bij je bestemming neer. */
    private static void carry(ServerPlayer player, ItemStack glasses) {
        PlayerState state = state(player);
        // Wie je aankijkt, of anders je laatst gemarkeerde doelwit binnen 64 blokken (hoofdstuk 97).
        LivingEntity target = lookedOrMarked(player, ImtConfig.Glasses.TARGET_RANGE);
        if (target == null) {
            return;
        }
        if (!requireDrone(player) || !checkCooldown(player, state, ImtCooldown.TRANSPORT)
                || !payEnergy(player, glasses, ImtConfig.Transport.COST)) {
            return;
        }
        startCooldown(player, state, ImtCooldown.TRANSPORT, Math.round(ImtConfig.Transport.COOLDOWN));
        ImtTransport.carry(player, target);
        sendStatus(player, state);
    }

    // ---------------------------------------------------------------- Spionagedrone (fase 6)

    /** Spionagedrone sturen naar wie je aankijkt, of de huidige opdracht stoppen. */
    private static void spyDrone(ServerPlayer player, ItemStack glasses) {
        if (ImtSpy.of(player) != null) {
            ImtSpy.stop(player, "imt.imttech.spy.log.end_recalled");
            return;
        }
        PlayerState state = state(player);
        if (!checkCooldown(player, state, ImtCooldown.SPY)) {
            return;
        }
        // Spioneren mag op iedereen en alles (het doet geen pijn), behalve op je eigen drones. Zie je niemand, dan je laatst
        // gemarkeerde doelwit (hoofdstuk 97; was het eerste).
        LivingEntity target = lookedOrMarked(player, ImtConfig.Recon.SPY_RANGE);
        if (target == null) {
            return;
        }
        if (!requireDrone(player) || !payEnergy(player, glasses, ImtConfig.Recon.SPY_COST)) {
            return;
        }
        startCooldown(player, state, ImtCooldown.SPY, Math.round(ImtConfig.Recon.SPY_COOLDOWN));
        ImtSpy.start(player, target);
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
        ImtSpy.Mission mission = ImtSpy.of(player);
        UUID droneId = mission == null ? null : mission.droneId();
        if (droneId == null) {
            send(player, Component.translatable("imt.imttech.msg.spy_none"), ImtMessagePayload.Level.WARNING);
            return;
        }
        if (!(player.serverLevel().getEntity(droneId) instanceof DroneEntity drone)
                || drone.distanceToSqr(player) > ImtConfig.Recon.SPY_VIEW_RANGE * ImtConfig.Recon.SPY_VIEW_RANGE) {
            send(player, Component.translatable("imt.imttech.msg.spy_signal_weak"), ImtMessagePayload.Level.WARNING);
            return;
        }
        // Je eigen verkenningsdrone kan niet tegelijk: eerst die terugroepen.
        if (state.scoutId != null) {
            endScout(player, "imt.imttech.msg.scout_returned", ImtMessagePayload.Level.INFO);
        }
        state.viewDroneId = droneId;
        ImtSpy.setWatching(player, true);
        ModNetwork.sendTo(player, new DroneCameraPayload(drone.getId(), false));
        send(player, Component.translatable("imt.imttech.msg.spy_view_on"), ImtMessagePayload.Level.INFO);
    }

    /** Stopt het meekijken: terug naar je eigen ogen. */
    public static void stopWatching(ServerPlayer player) {
        PlayerState state = state(player);
        if (state.viewDroneId == null) {
            return;
        }
        state.viewDroneId = null;
        ImtSpy.setWatching(player, false);
        ModNetwork.sendTo(player, DroneCameraPayload.OFF);
        send(player, Component.translatable("imt.imttech.msg.spy_view_off"), ImtMessagePayload.Level.INFO);
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
        double maxRange = ImtConfig.Glasses.TARGET_RANGE + 8;
        if (!(entity instanceof LivingEntity target)) {
            return;
        }
        // Je kunt markeren vanaf je lichaam, of vanaf je verkenningsdrone.
        DroneEntity scout = getScout(player, state(player));
        boolean inRange = target.distanceToSqr(player) <= maxRange * maxRange
                || (scout != null && target.distanceToSqr(scout) <= maxRange * maxRange);
        if (!inRange) {
            return;
        }
        PlayerState state = state(player);
        if (state.marked.remove(target.getUUID())) {
            send(player, Component.translatable("imt.imttech.msg.unmarked", target.getDisplayName()), ImtMessagePayload.Level.INFO);
            return;
        }
        if (!ImtTargeting.canTarget(player, target)) {
            send(player, Component.translatable("imt.imttech.msg.cannot_mark", target.getDisplayName()), ImtMessagePayload.Level.WARNING);
            return;
        }
        // Hoofdstuk 106: een doelwit onthouden doet je datacenter. (Een markering weghalen, hierboven, kan altijd.)
        if (!DatacenterGlasses.requireLink(player)) {
            return;
        }
        if (state.marked.size() >= Math.round(ImtConfig.Glasses.MAX_MARKED_TARGETS)) {
            state.marked.removeFirst();
        }
        state.marked.add(target.getUUID());
        send(player, Component.translatable("imt.imttech.msg.marked", target.getDisplayName(), state.marked.size()), ImtMessagePayload.Level.INFO);
        sendStatus(player, state);
    }

    private static void clearMarks(ServerPlayer player) {
        state(player).marked.clear();
        send(player, Component.translatable("imt.imttech.msg.marks_cleared"), ImtMessagePayload.Level.INFO);
    }

    /** Zo ver gaan je zwerm en je raketten achter een markering aan (blokken); verder weg blijft hij wel staan. */
    private static final double NEAR_MARK = 128;

    /**
     * De gemarkeerde doelwitten die nog leven en in de buurt zijn (128 blokken). Wordt ook door drones gebruikt: een zwerm
     * vliegt niet achter een markering aan die ver weg is (zie {@link #allMarkedTargets} voor alles).
     */
    public static List<LivingEntity> getMarkedTargets(ServerPlayer player) {
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity living : allMarkedTargets(player)) {
            if (living.distanceToSqr(player) < NEAR_MARK * NEAR_MARK) {
                result.add(living);
            }
        }
        return result;
    }

    /**
     * Hoofdstuk 97: al je markeringen die nog gelden, op elke afstand in jouw wereld, op volgorde van markeren. Een markering
     * blijft staan tot het doelwit doodgaat, uitlogt of naar een andere wereld gaat, of tot jij "Clear marks" of "Stand down"
     * doet (jouw keuze; vroeger was hij weg zodra het doelwit 128 blokken van je weg was).
     */
    public static List<LivingEntity> allMarkedTargets(ServerPlayer player) {
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

    /** Hoofdstuk 97: het doelwit dat je het laatst markeerde (en dat nog geldt), of null. */
    public static @Nullable LivingEntity lastMarked(ServerPlayer player) {
        List<LivingEntity> marked = allMarkedTargets(player);
        return marked.isEmpty() ? null : marked.getLast();
    }

    /**
     * Hoofdstuk 97 (jouw wens: "de dingen die 'no target in sight' zeggen moeten ook op een target kunnen gaan als je maar 1
     * target hebt gemarkt met r, zo kun je makkelijk iemand marken en later pas bv een raket op sturen"): het wezen waar je
     * naar kijkt, en kijk je naar niets, dan je laatst gemarkeerde doelwit (met meer markeringen: het laatste, jouw keuze).
     * Elke actie houdt haar eigen bereik: is je markering verder weg dan {@code range}, dan zegt I.M.T hoe ver, en gebeurt
     * er niets. Geen wezen en geen markering: "No target in sight.". Geeft dan null.
     */
    private static @Nullable LivingEntity lookedOrMarked(ServerPlayer player, double range) {
        LivingEntity looked = findLookTarget(player, range);
        if (looked != null) {
            return looked;
        }
        LivingEntity marked = lastMarked(player);
        if (marked == null) {
            send(player, Component.translatable("imt.imttech.msg.no_target_in_sight"), ImtMessagePayload.Level.WARNING);
            return null;
        }
        if (!inReach(player, marked, range)) {
            return null;
        }
        return marked;
    }

    /** Is dit gemarkeerde doelwit binnen {@code range}? Zo niet, dan zegt I.M.T hoe ver het is (hoofdstuk 97). */
    private static boolean inReach(ServerPlayer player, LivingEntity marked, double range) {
        double distance = marked.distanceTo(player);
        if (distance <= range) {
            return true;
        }
        send(player, Component.translatable("imt.imttech.msg.mark_out_of_range", marked.getDisplayName(),
                Math.round(distance), Math.round(range)), ImtMessagePayload.Level.WARNING);
        return false;
    }

    /** Hoofdstuk 97: wie doodgaat, is voor niemand meer gemarkeerd (ook niet als hij meteen weer opstaat). */
    public static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        UUID dead = event.getEntity().getUUID();
        for (PlayerState state : STATES.values()) {
            state.marked.remove(dead);
        }
    }

    private static boolean isMarkStillValid(ServerPlayer player, LivingEntity target) {
        return target.isAlive() && ImtTargeting.canTarget(player, target);
    }

    /**
     * Haalt dode en verdwenen doelwitten uit de lijst (uitgelogd, in een andere wereld, of in een stuk wereld dat niet meer
     * geladen is). Hoe ver ze zijn, maakt sinds hoofdstuk 97 niet meer uit.
     */
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
        DroneEntity scout = getScout(player, state(player));
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
                e -> e instanceof LivingEntity && !(e instanceof DroneEntity) && !e.isSpectator() && e.isPickable(), range * range);
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
     * 2. hoofdstuk 97: je laatst gemarkeerde doelwit (ook als je naar een blok kijkt, jouw keuze), als het binnen het bereik
     *    van je satelliet is; is het verder, dan zegt I.M.T hoe ver en vuurt hij niet (haal je markering weg met "Clear
     *    marks" om een plek te raken). Niet voor de laserlijn (C): die trek je van punt naar punt;
     * 3. het blok waar je naar kijkt (tot 500 blokken ver);
     * 4. je bestemming uit de navigatie;
     * 5. je laatste ping.
     */
    private static void satelliteStrike(ServerPlayer player, ItemStack glasses, SatelliteMode mode) {
        if (!SatelliteWorkshop.get(player).unlocked(mode)) {
            send(player, Component.translatable("satellite.imttech.msg.locked", Component.translatable(mode.translationKey())),
                    ImtMessagePayload.Level.WARNING);
            return;
        }
        PlayerState state = state(player);
        // Een half gekozen laserlijn (alleen het beginpunt) vervalt als je iets anders afvuurt.
        if (mode != SatelliteMode.SWEEP) {
            state.sweepStart = null;
        }
        if (SatelliteStrikes.pendingFor(player.getUUID()) != null || !checkCooldown(player, state, ImtCooldown.SATELLITE)) {
            return;
        }
        SatelliteUpgrades upgrades = SatelliteWorkshop.get(player);
        double range = SatelliteStats.range(upgrades.targeting());
        UUID locked = null;
        Vec3 target = null;

        LivingEntity looked = findLookTarget(player, range);
        if (looked != null && ImtTargeting.canTarget(player, looked)) {
            locked = looked.getUUID();
            target = looked.position();
        }
        if (target == null && mode != SatelliteMode.SWEEP) {
            LivingEntity marked = lastMarked(player);
            if (marked != null) {
                if (!inReach(player, marked, range)) {
                    return;
                }
                locked = marked.getUUID();
                target = marked.position();
            }
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
            target = ImtComms.lastPing(player);
        }
        if (target == null) {
            send(player, Component.translatable("imt.imttech.msg.satellite_no_target"), ImtMessagePayload.Level.WARNING);
            return;
        }
        if (target.distanceTo(player.position()) < ImtConfig.Satellite.MIN_DISTANCE) {
            send(player, Component.translatable("imt.imttech.msg.satellite_too_close"), ImtMessagePayload.Level.WARNING);
            return;
        }
        // De satelliet hangt in de ruimte: het doel moet de lucht kunnen zien.
        if (!player.serverLevel().canSeeSky(BlockPos.containing(target).above())) {
            send(player, Component.translatable("imt.imttech.msg.satellite_no_sky"), ImtMessagePayload.Level.WARNING);
            return;
        }
        // De schuivende laser kies je in twee keer: eerst het beginpunt (dat kost nog niets), dan
        // het eindpunt. Pas bij het tweede punt vuurt hij, van het begin naar het eind.
        Vec3 sweepTo = null;
        if (mode == SatelliteMode.SWEEP) {
            long now = player.level().getGameTime();
            if (state.sweepStart == null || now - state.sweepStartAt > Math.round(ImtConfig.Satellite.SWEEP_START_TIMEOUT)) {
                state.sweepStart = target;
                state.sweepStartAt = now;
                ModNetwork.sendTo(player, new PingMarkerPayload(PingMarkerPayload.SWEEP_START, target));
                send(player, Component.translatable("imt.imttech.msg.sweep_start_set"), ImtMessagePayload.Level.INFO);
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
        ImtData.setSatelliteMode(glasses, mode); // voor de HUD: welke aanval er onderweg is
        startCooldown(player, state, ImtCooldown.SATELLITE, SatelliteStats.cooldownTicks(upgrades.recharge()));
        SatelliteStrikes.start(player.serverLevel(), target, player.getUUID(), mode, locked, sweepTo);
        ImtAdvancements.award(player, ImtAdvancements.SATELLITE);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SATELLITE_LOCK.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        send(player, Component.translatable("imt.imttech.msg.satellite_locked_mode",
                Component.translatable(mode.translationKey()), SatelliteStats.chargeTicks(upgrades.targeting()) / 20),
                ImtMessagePayload.Level.WARNING);
        sendStatus(player, state);
    }

    /** Waar de speler naartoe navigeert (als dat in deze wereld is), of null. */
    private static @Nullable Vec3 navigationPoint(ServerPlayer player) {
        ImtWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        if (data.active() == ImtWaypoints.NONE) {
            return null;
        }
        return ImtNavigation.targetPos(player, data)
                .filter(pos -> pos.dimension() == player.level().dimension())
                .map(pos -> Vec3.atCenterOf(pos.pos()))
                .orElse(null);
    }

    // ---------------------------------------------------------------- Verkenningsdrone (drone-camera)

    /** Drone-camera aan of uit. */
    private static void toggleScout(ServerPlayer player, ItemStack glasses) {
        PlayerState state = state(player);
        if (state.scoutId != null) {
            endScout(player, "imt.imttech.msg.scout_returned", ImtMessagePayload.Level.INFO);
            return;
        }
        if (ImtData.getExactEnergy(glasses) < ImtConfig.Recon.SCOUT_COST) {
            send(player, Component.translatable("imt.imttech.msg.not_enough_energy",
                    Numbers.amount(player, ImtConfig.Recon.SCOUT_COST)), ImtMessagePayload.Level.WARNING);
            return;
        }
        if (!requireDrone(player)) {
            return;
        }
        DroneEntity drone = ImtDrones.launchScout(player);
        if (drone == null) {
            return;
        }
        // Pas betalen als de drone echt in de lucht is.
        ImtData.tryUseEnergy(glasses, ImtConfig.Recon.SCOUT_COST);
        state.scoutId = drone.getUUID();
        state.scoutControl = new DroneControlPayload(0, 0, 0, false, player.getYRot(), player.getXRot());
        state.lastControlTick = player.level().getGameTime();
        ModNetwork.sendTo(player, new DroneCameraPayload(drone.getId(), true));
        send(player, Component.translatable("imt.imttech.msg.scout_online"), ImtMessagePayload.Level.INFO);
    }

    /** Stopt de drone-camera: de drone vliegt weg en de speler kijkt weer door zijn eigen ogen. */
    private static void endScout(ServerPlayer player, String messageKey, ImtMessagePayload.Level level) {
        PlayerState state = state(player);
        DroneEntity scout = getScout(player, state);
        state.scoutId = null;
        if (scout != null) {
            scout.recall(player.serverLevel());
        }
        ModNetwork.sendTo(player, DroneCameraPayload.OFF);
        send(player, Component.translatable(messageKey), level);
    }

    private static @Nullable DroneEntity getScout(ServerPlayer player, PlayerState state) {
        if (state.scoutId == null) {
            return null;
        }
        return player.serverLevel().getEntity(state.scoutId) instanceof DroneEntity drone && drone.isAlive() ? drone : null;
    }

    /** Is dit de verkenningsdrone waar de speler nu door kijkt? (Wordt door de drone zelf gevraagd.) */
    public static boolean isActiveScout(ServerPlayer player, DroneEntity drone) {
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
        if (ImtTransport.carrierOf(player) != null) {
            state.transportControl = safe;
            state.lastTransportTick = player.level().getGameTime();
            return;
        }
        // Richt je een illusie? Dan bestuur je die drone.
        if (ImtIllusions.steer(player, safe)) {
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
                endScout(player, "imt.imttech.msg.scout_body_attacked", ImtMessagePayload.Level.ALERT);
            }
            if (isWatching(player)) {
                stopWatching(player);
            }
            ImtIllusions.cancelAim(player, "imt.imttech.msg.illusion_aim_attacked");
        }
    }

    // ---------------------------------------------------------------- Hulpjes

    /**
     * Is deze functie weer beschikbaar? Zonder melding: voor dingen die je ingedrukt houdt,
     * zoals de laser cannon, zou je anders elke tick "nog 1 seconde" in beeld krijgen.
     */
    public static boolean isReady(ServerPlayer player, ImtCooldown cooldown) {
        return player.level().getGameTime() >= state(player).readyAt[cooldown.ordinal()];
    }

    /** Zet de wachttijd van een functie op "nu + ticks" (voor functies buiten deze klasse). */
    public static void startCooldown(ServerPlayer player, ImtCooldown cooldown, int ticks) {
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
    private static boolean checkCooldown(ServerPlayer player, PlayerState state, ImtCooldown cooldown) {
        return checkCooldown(player, state.readyAt[cooldown.ordinal()]);
    }

    /** Zet de wachttijd van een functie op "nu + ticks". */
    private static void startCooldown(ServerPlayer player, PlayerState state, ImtCooldown cooldown, int ticks) {
        state.readyAt[cooldown.ordinal()] = player.level().getGameTime() + ticks;
    }

    /** Is de cooldown voorbij? Zo niet, dan krijgt de speler een melding. */
    private static boolean checkCooldown(ServerPlayer player, long readyAtTick) {
        long now = player.level().getGameTime();
        if (now < readyAtTick) {
            long secondsLeft = (readyAtTick - now + 19) / 20;
            send(player, Component.translatable("imt.imttech.msg.cooldown", secondsLeft), ImtMessagePayload.Level.WARNING);
            return false;
        }
        return true;
    }

    /** Probeert energie te betalen. Zo niet, dan krijgt de speler een melding. */
    private static boolean payEnergy(ServerPlayer player, ItemStack glasses, double cost) {
        if (!ImtData.tryUseEnergy(glasses, cost)) {
            send(player, Component.translatable("imt.imttech.msg.not_enough_energy", Numbers.amount(player, cost)),
                    ImtMessagePayload.Level.WARNING);
            return false;
        }
        return true;
    }

    private static int ticksLeft(ServerPlayer player, long readyAtTick) {
        return (int) Math.max(0, readyAtTick - player.level().getGameTime());
    }

    /** Stuurt de HUD-status (drones, cooldowns, markeringen, satelliet) naar de speler. */
    private static void sendStatus(ServerPlayer player, PlayerState state) {
        int swarm = ImtDrones.find(player, DroneEntity.Mode.ATTACK).size();
        int guards = ImtDrones.find(player, DroneEntity.Mode.GUARD).size();
        // Hoofdstuk 97: alle markeringen, ook die ver weg (je HUD telt ze; markeren kan je spel alleen wat het kent).
        List<Integer> markedIds = allMarkedTargets(player).stream().map(Entity::getId).toList();
        SatelliteStrikes.Strike strike = SatelliteStrikes.pendingFor(player.getUUID());
        List<Integer> cooldowns = new ArrayList<>(ImtCooldown.COUNT);
        for (ImtCooldown cooldown : ImtCooldown.values()) {
            cooldowns.add(ticksLeft(player, state.readyAt[cooldown.ordinal()]));
        }
        // Wie valt je aan? Alleen dat markeren we; zie ImtThreats.
        List<Integer> threatIds = List.of();
        ItemStack worn = ImtData.getWornGlasses(player);
        // Hoofdstuk 106: de dreigingsscan rekent op je datacenter. Zonder (of als het niet draait) ziet hij niets.
        DatacenterGlasses.Link link = DatacenterGlasses.link(player);
        if (ImtData.isEnabled(worn, ImtFeature.THREAT_SCAN) && link.works()) {
            int radius = ImtData.getThreatRadius(worn);
            threatIds = new ArrayList<>();
            for (LivingEntity threat : ImtThreats.of(player, radius)) {
                threatIds.add(threat.getId());
            }
            threatIds.addAll(ImtThreats.incomingProjectiles(player, radius));
        }
        ModNetwork.sendTo(player, new ImtStatusPayload(
                swarm, guards,
                cooldowns,
                markedIds,
                strike == null ? Optional.empty() : Optional.of(strike.target()),
                strike == null ? 0 : strike.ticksLeft(),
                ImtHack.targetsOf(player).size(),
                threatIds,
                com.senne.imttech.logic.DatacenterBrain.delayFor(player),
                com.senne.imttech.logic.DatacenterBrain.sourceFor(player),
                link.ordinal(),
                DatacenterGlasses.loadPercent(player)));
    }

    public static void send(ServerPlayer player, Component message, ImtMessagePayload.Level level) {
        // Hoofdstuk 96: een lege bril zegt niets meer, ook niet over je basis of je drones. (Haar laatste zin, "Battery
        // depleted", zegt je spel zelf op het moment dat hij leegloopt.) Zonder bril gaan meldingen door, zoals altijd.
        if (ImtData.isBatteryDead(player)) {
            return;
        }
        ModNetwork.sendTo(player, new ImtMessagePayload(message, level));
    }
}
