package com.senne.starktech.spy;

import com.senne.starktech.drone.EdithDrones;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.nav.EdithNavigation;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.network.SpyReportPayload;
import com.senne.starktech.network.SpyStatusPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * De spionagedrone: hij volgt 10 minuten lang stiekem iemand (of iets) en rapporteert
 * alles aan de speler die hem stuurde.
 *
 * Wat je krijgt:
 * - live positie van het doelwit in je HUD en op je kompas (ook door muren en van heel ver);
 * - elke 30 seconden een kort rapport (waar, hoeveel leven, wat hij vasthoudt, wie erbij is);
 * - een logboek van wat hij doet (blokken breken/plaatsen, kisten openen, vechten, chatten...);
 * - en je kunt live door de camera van de drone meekijken.
 *
 * De drone is onzichtbaar (hij "cloakt"), maar hij is er wel echt: wie weet waar hij hangt,
 * kan hem uit de lucht slaan. Dan is de opdracht voorbij.
 *
 * Eén opdracht per speler. Alle gegevens staan hier in het geheugen van de server; als de
 * wereld sluit, stoppen de opdrachten (net als bij cooldowns).
 */
public final class EdithSpy {
    private EdithSpy() {}

    /** Hoe lang het doelwit weg mag zijn (bv. chunk niet geladen) voordat we opgeven, in ticks. */
    private static final int LOST_GRACE_TICKS = 100;
    /** Hoeveel regels er maximaal in het logboek komen. */
    private static final int MAX_LOG = 80;

    /** Eén spionage-opdracht van één speler. */
    public static final class Mission {
        private final UUID ownerId;
        private final UUID targetId;
        private final Component targetName;
        private @Nullable UUID droneId;
        private ResourceKey<Level> dimension;
        private int ticksLeft;
        private int ticksToReport;
        private int lostTicks;
        /** Hoe lang de drone zelf al onvindbaar is (neergehaald?). */
        private int droneLostTicks;
        /** Kijkt de eigenaar op dit moment door de drone? */
        private boolean watching;
        private final List<Component> log = new ArrayList<>();
        /** Laatst bekende plek van het doelwit (ook als het doelwit even niet geladen is). */
        private Vec3 lastPos = Vec3.ZERO;
        private float lastHealth;
        private float lastMaxHealth = 20.0f;

        Mission(UUID ownerId, LivingEntity target) {
            this.ownerId = ownerId;
            this.targetId = target.getUUID();
            this.targetName = target.getDisplayName().copy();
            this.dimension = target.level().dimension();
            this.ticksLeft = EdithBalance.SPY_DURATION;
            this.ticksToReport = EdithBalance.SPY_REPORT_INTERVAL;
            this.lastPos = target.position();
            this.lastHealth = target.getHealth();
            this.lastMaxHealth = target.getMaxHealth();
        }

        public Component targetName() {
            return targetName;
        }

        public int ticksLeft() {
            return ticksLeft;
        }

        public List<Component> log() {
            return log;
        }

        public boolean watching() {
            return watching;
        }

        public @Nullable UUID droneId() {
            return droneId;
        }

        /** Hoeveel van de 10 minuten er al om is, als tekst "3:07". */
        private String elapsed() {
            int seconds = (EdithBalance.SPY_DURATION - ticksLeft) / 20;
            return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
        }
    }

    /** Eén opdracht per speler (de sleutel is de eigenaar). */
    private static final Map<UUID, Mission> MISSIONS = new HashMap<>();

    // ================================================================ Starten en stoppen

    /** De opdracht van deze speler, of null. */
    public static @Nullable Mission of(Player player) {
        return MISSIONS.get(player.getUUID());
    }

    /** Start een nieuwe opdracht. Geeft false als het niet lukte. */
    public static boolean start(ServerPlayer owner, LivingEntity target) {
        Mission mission = new Mission(owner.getUUID(), target);
        StarkDroneEntity drone = EdithDrones.launchSpy(owner, target);
        if (drone == null) {
            return false;
        }
        mission.droneId = drone.getUUID();
        MISSIONS.put(owner.getUUID(), mission);
        add(mission, Component.translatable("edith.starktech.spy.log.start", mission.targetName));
        EdithServer.send(owner, Component.translatable("edith.starktech.msg.spy_started",
                mission.targetName, EdithBalance.SPY_DURATION / 1200), EdithMessagePayload.Level.INFO);
        return true;
    }

    /**
     * Stopt de opdracht: de drone vliegt weg, de speler krijgt het eindrapport.
     *
     * @param reasonKey vertaalsleutel voor de reden (bv. "voorbij", "drone neergehaald")
     */
    public static void stop(ServerPlayer owner, String reasonKey) {
        Mission mission = MISSIONS.remove(owner.getUUID());
        if (mission == null) {
            return;
        }
        ServerLevel level = owner.level().getServer().getLevel(mission.dimension);
        StarkDroneEntity drone = findDrone(level, mission);
        if (drone != null) {
            drone.recall(drone.level() instanceof ServerLevel droneLevel ? droneLevel : level);
        }
        if (mission.watching) {
            EdithServer.stopWatching(owner);
        }
        add(mission, Component.translatable(reasonKey));
        EdithServer.send(owner, Component.translatable("edith.starktech.msg.spy_finished", mission.targetName),
                EdithMessagePayload.Level.BOOT);
        // Het eindrapport: de client bewaart het en opent het scherm.
        ModNetwork.sendTo(owner, new SpyReportPayload(mission.targetName, List.copyOf(mission.log), true));
        // En nog één statusbericht, zodat de HUD weet dat er geen opdracht meer is.
        ModNetwork.sendTo(owner, SpyStatusPayload.NONE);
    }

    /** Stuurt het huidige rapport nog eens naar de speler (menu-optie "Rapport"). */
    public static void sendReport(ServerPlayer owner) {
        Mission mission = of(owner);
        if (mission == null) {
            EdithServer.send(owner, Component.translatable("edith.starktech.msg.spy_none"), EdithMessagePayload.Level.WARNING);
            return;
        }
        ModNetwork.sendTo(owner, new SpyReportPayload(mission.targetName, List.copyOf(mission.log), false));
    }

    /** Hoort deze drone bij de opdracht van deze speler? (De drone vraagt dit zelf.) */
    public static boolean isActiveSpy(ServerPlayer owner, StarkDroneEntity drone) {
        Mission mission = MISSIONS.get(owner.getUUID());
        return mission != null && drone.getUUID().equals(mission.droneId);
    }

    /** Het wezen dat een spionagedrone volgt, of null. */
    public static @Nullable LivingEntity targetOf(StarkDroneEntity drone, ServerPlayer owner) {
        Mission mission = MISSIONS.get(owner.getUUID());
        if (mission == null || !drone.getUUID().equals(mission.droneId)) {
            return null;
        }
        return drone.level().getEntity(mission.targetId) instanceof LivingEntity living ? living : null;
    }

    /** Onthoudt of de speler nu door de spionagedrone kijkt (voor de HUD en het stoppen). */
    public static void setWatching(ServerPlayer owner, boolean watching) {
        Mission mission = MISSIONS.get(owner.getUUID());
        if (mission != null) {
            mission.watching = watching;
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        MISSIONS.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        MISSIONS.clear();
    }

    // ================================================================ Elke tick

    public static void onServerTick(ServerTickEvent.Post event) {
        if (MISSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Mission>> it = MISSIONS.entrySet().iterator();
        List<Runnable> endings = new ArrayList<>();
        while (it.hasNext()) {
            Mission mission = it.next().getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(mission.ownerId);
            // Eigenaar weg, bril af of leeg? Dan is het voorbij.
            if (owner == null || !EdithData.isOperational(owner)) {
                it.remove();
                continue;
            }
            String ending = tickMission(server, owner, mission);
            if (ending != null) {
                // Niet tijdens het doorlopen van de lijst stoppen: dat doen we er net na.
                endings.add(() -> stop(owner, ending));
            }
        }
        endings.forEach(Runnable::run);
    }

    /** Eén opdracht bijwerken. Geeft een vertaalsleutel terug als de opdracht moet stoppen. */
    private static @Nullable String tickMission(MinecraftServer server, ServerPlayer owner, Mission mission) {
        ServerLevel level = server.getLevel(mission.dimension);
        if (level == null) {
            return "edith.starktech.spy.log.end_lost";
        }

        // Is het doelwit er nog? (Een monster in een niet-geladen stuk wereld "bestaat" even niet.)
        LivingEntity target = level.getEntity(mission.targetId) instanceof LivingEntity living ? living : null;
        if (target == null) {
            // Misschien is het doelwit naar een andere wereld gegaan: overal zoeken.
            target = findInOtherLevel(server, mission);
        }
        if (target == null || !target.isAlive()) {
            mission.lostTicks++;
            if (mission.lostTicks > LOST_GRACE_TICKS) {
                return "edith.starktech.spy.log.end_lost";
            }
        } else {
            mission.lostTicks = 0;
            mission.lastPos = target.position();
            mission.lastHealth = target.getHealth();
            mission.lastMaxHealth = target.getMaxHealth();
            // Doelwit in een andere wereld? Dan stuurt E.D.I.T.H. daar een nieuwe drone heen.
            if (target.level().dimension() != mission.dimension) {
                followToOtherLevel(owner, mission, target);
            }
        }

        // Is de drone er nog? Zo niet: neergehaald (of verdwenen).
        if (findDrone(server.getLevel(mission.dimension), mission) == null) {
            // Nog even geduld: net na het spawnen kan hij een tick "onvindbaar" zijn.
            if (++mission.droneLostTicks > 10) {
                return "edith.starktech.spy.log.end_destroyed";
            }
        } else {
            mission.droneLostTicks = 0;
        }

        // Tijd: aftellen en elke 30 seconden een rapport.
        if (--mission.ticksLeft <= 0) {
            return "edith.starktech.spy.log.end_time";
        }
        if (--mission.ticksToReport <= 0) {
            mission.ticksToReport = EdithBalance.SPY_REPORT_INTERVAL;
            report(owner, mission, target);
        }
        // Twee keer per seconde de HUD bijwerken.
        if (mission.ticksLeft % 10 == 0) {
            sendStatus(owner, mission);
        }
        return null;
    }

    /** Zoekt het doelwit in de andere werelden (het kan door een portaal zijn gegaan). */
    private static @Nullable LivingEntity findInOtherLevel(MinecraftServer server, Mission mission) {
        for (ServerLevel other : server.getAllLevels()) {
            if (other.dimension() != mission.dimension
                    && other.getEntity(mission.targetId) instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    /** Doelwit is naar een andere wereld: oude drone weg, nieuwe drone daar. */
    private static void followToOtherLevel(ServerPlayer owner, Mission mission, LivingEntity target) {
        ServerLevel oldLevel = owner.level().getServer().getLevel(mission.dimension);
        StarkDroneEntity old = oldLevel == null ? null : findDrone(oldLevel, mission);
        if (old != null) {
            old.discard();
        }
        mission.dimension = target.level().dimension();
        StarkDroneEntity fresh = EdithDrones.launchSpy(owner, target);
        mission.droneId = fresh == null ? null : fresh.getUUID();
        mission.lostTicks = 0;
        mission.droneLostTicks = 0;
        add(mission, Component.translatable("edith.starktech.spy.log.dimension",
                EdithNavigation.dimensionName(mission.dimension)));
        EdithServer.send(owner, Component.translatable("edith.starktech.msg.spy_dimension", mission.targetName,
                EdithNavigation.dimensionName(mission.dimension)), EdithMessagePayload.Level.INFO);
    }

    private static @Nullable StarkDroneEntity findDrone(@Nullable ServerLevel level, Mission mission) {
        if (level == null || mission.droneId == null) {
            return null;
        }
        return level.getEntity(mission.droneId) instanceof StarkDroneEntity drone && drone.isAlive() ? drone : null;
    }

    // ================================================================ Rapporteren

    /** Het rapport van elke 30 seconden: waar, hoeveel leven, wat hij vasthoudt, wie erbij is. */
    private static void report(ServerPlayer owner, Mission mission, @Nullable LivingEntity target) {
        if (target == null) {
            add(mission, Component.translatable("edith.starktech.spy.log.no_contact"));
            EdithServer.send(owner, Component.translatable("edith.starktech.spy.log.no_contact"), EdithMessagePayload.Level.WARNING);
            return;
        }
        BlockPos pos = target.blockPosition();
        int hearts = Math.max(1, Math.round(target.getHealth() / 2));
        Component held = target.getMainHandItem().isEmpty()
                ? Component.translatable("edith.starktech.spy.empty_hands")
                : target.getMainHandItem().getHoverName();
        // Wie is er in de buurt (binnen 12 blokken)?
        List<LivingEntity> near = target.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(12),
                e -> e != target && !(e instanceof StarkDroneEntity) && e.isAlive());
        Component company = near.isEmpty()
                ? Component.translatable("edith.starktech.spy.alone")
                : Component.translatable("edith.starktech.spy.company", near.size(), near.getFirst().getDisplayName());

        Component line = Component.translatable("edith.starktech.spy.report", mission.targetName,
                pos.getX(), pos.getY(), pos.getZ(), hearts, held, company);
        add(mission, line);
        EdithServer.send(owner, line, EdithMessagePayload.Level.COMMS);
    }

    /** Een regel in het logboek, met de tijd erbij. */
    private static void add(Mission mission, Component line) {
        mission.log.add(Component.literal("[" + mission.elapsed() + "] ").withStyle(ChatFormatting.DARK_AQUA).append(line));
        while (mission.log.size() > MAX_LOG) {
            mission.log.removeFirst();
        }
    }

    private static void sendStatus(ServerPlayer owner, Mission mission) {
        ModNetwork.sendTo(owner, new SpyStatusPayload(true, mission.targetName, Optional.of(mission.lastPos),
                mission.dimension, mission.ticksLeft, mission.lastHealth, mission.lastMaxHealth, mission.log.size()));
    }

    // ================================================================ Logboek: wat doet het doelwit?

    /** De opdracht die dit wezen volgt, of null. Zo weten we of we iets moeten opschrijven. */
    private static @Nullable Mission watching(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        for (Mission mission : MISSIONS.values()) {
            if (mission.targetId.equals(entity.getUUID())) {
                return mission;
            }
        }
        return null;
    }

    /** Schrijft iets in het logboek van het doelwit (en waarschuwt de eigenaar bij belangrijk nieuws). */
    private static void note(Entity subject, Component line, boolean important) {
        Mission mission = watching(subject);
        if (mission == null) {
            return;
        }
        add(mission, line);
        if (important && subject.level().getServer() != null) {
            ServerPlayer owner = subject.level().getServer().getPlayerList().getPlayer(mission.ownerId);
            if (owner != null) {
                EdithServer.send(owner, line, EdithMessagePayload.Level.COMMS);
            }
        }
    }

    public static void onBlockBreak(BreakBlockEvent event) {
        note(event.getPlayer(), Component.translatable("edith.starktech.spy.log.break",
                event.getState().getBlock().getName(), event.getPos().getX(), event.getPos().getY(), event.getPos().getZ()), false);
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        note(event.getEntity(), Component.translatable("edith.starktech.spy.log.place",
                event.getPlacedBlock().getBlock().getName(), event.getPos().getX(), event.getPos().getY(), event.getPos().getZ()), false);
    }

    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        note(event.getEntity(), Component.translatable("edith.starktech.spy.log.container"), false);
    }

    public static void onAttack(AttackEntityEvent event) {
        note(event.getEntity(), Component.translatable("edith.starktech.spy.log.attack",
                event.getTarget().getDisplayName()), true);
    }

    public static void onDeath(LivingDeathEvent event) {
        note(event.getEntity(), Component.translatable("edith.starktech.spy.log.death"), true);
    }

    public static void onChat(ServerChatEvent event) {
        note(event.getPlayer(), Component.translatable("edith.starktech.spy.log.chat", event.getMessage()), true);
    }

    /** Een speler die we volgen gaat naar een andere wereld. */
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Mission mission = watching(event.getEntity());
        if (mission != null && event.getEntity() instanceof ServerPlayer target) {
            // De drone volgt in de volgende tick; hier schrijven we alleen wat er gebeurde.
            add(mission, Component.translatable("edith.starktech.spy.log.portal",
                    EdithNavigation.dimensionName(event.getTo())));
            mission.lastPos = target.position();
        }
    }
}
