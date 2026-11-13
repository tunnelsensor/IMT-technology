package com.senne.imttech.arsenal;

import com.senne.imttech.drone.ImtDrones;
import com.senne.imttech.drone.DroneEntity;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.imt.ImtAdvancements;
import com.senne.imttech.network.ArsenalActionPayload;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Het arsenaal aan de serverkant: hier worden drones bijgeschreven, uitgeleend en afgeschreven.
 *
 * De regel is simpel: een drone die in de wereld vliegt, staat in het arsenaal als DEPLOYED.
 * Komt hij terug, dan schrijven we zijn schade weg en staat hij weer klaar. Gaat hij kapot,
 * dan is hij uit het arsenaal verdwenen en valt er een beetje schroot op de grond.
 */
public final class ArsenalServer {
    private ArsenalServer() {}

    public static ImtArsenal get(ServerPlayer player) {
        return player.getData(ModAttachments.ARSENAL);
    }

    private static void set(ServerPlayer player, ImtArsenal arsenal) {
        player.setData(ModAttachments.ARSENAL, arsenal);
    }

    // ---------------------------------------------------------------- Bijbouwen

    /** Een nieuwe drone bijschrijven; geeft zijn nummer terug. */
    public static UUID add(ServerPlayer player) {
        UUID id = UUID.randomUUID();
        set(player, get(player).with(ArsenalDrone.fresh(id)));
        return id;
    }

    // ---------------------------------------------------------------- Uitlenen en terugbrengen

    /**
     * Zoveel drones uit hun baan halen als er klaar hangen (maximaal "wanted").
     * Ze staan daarna als DEPLOYED geboekt; de beller zet ze in de wereld.
     */
    public static List<ArsenalDrone> checkOut(ServerPlayer player, int wanted) {
        if (wanted <= 0) {
            return List.of();
        }
        ImtArsenal arsenal = get(player);
        List<ArsenalDrone> picked = arsenal.pickAvailable(wanted);
        List<ArsenalDrone> out = new ArrayList<>(picked.size());
        for (ArsenalDrone drone : picked) {
            ArsenalDrone deployed = drone.withState(ArsenalDrone.State.DEPLOYED);
            arsenal = arsenal.replace(deployed);
            out.add(deployed);
        }
        set(player, arsenal);
        return out;
    }

    /** Eén drone uitlenen, of null als er geen klaar hangt. */
    public static @Nullable ArsenalDrone checkOutOne(ServerPlayer player) {
        List<ArsenalDrone> one = checkOut(player, 1);
        return one.isEmpty() ? null : one.getFirst();
    }

    /** Een drone die heelhuids terugkomt: schade bijwerken en weer in een baan zetten. */
    public static void returnToOrbit(ServerPlayer player, UUID id, float health) {
        ImtArsenal arsenal = get(player);
        ArsenalDrone drone = arsenal.find(id);
        if (drone == null) {
            return;
        }
        set(player, arsenal.replace(drone.withState(ArsenalDrone.State.ORBIT).withHealth(health)));
    }

    /** Boekt een drone als "onderweg", bijvoorbeeld zolang hij naar boven klimt. */
    public static void markDeployed(ServerPlayer player, UUID id) {
        ImtArsenal arsenal = get(player);
        ArsenalDrone drone = arsenal.find(id);
        if (drone != null && drone.state() != ArsenalDrone.State.DEPLOYED) {
            set(player, arsenal.replace(drone.withState(ArsenalDrone.State.DEPLOYED)));
        }
    }

    /** Schrijft de schade van een drone weg zonder verder iets te veranderen. */
    public static void setHealth(ServerPlayer player, UUID id, float health) {
        ImtArsenal arsenal = get(player);
        ArsenalDrone drone = arsenal.find(id);
        if (drone != null) {
            set(player, arsenal.replace(drone.withHealth(health)));
        }
    }

    // Een drone herstelt nergens vanzelf, ook niet in zijn baan (26 oktober, jouw wens: "drones mogen niet zelf
    // herstellen, dit kan enkel met repair van iron"). Repareren doe je met ijzer: roep hem terug en rechtsklik op hem
    // (zie DroneEntity.mobInteract).

    /**
     * Een drone is neergehaald: hij gaat uit het arsenaal en een klein deel van de
     * bouwkosten valt als schroot op de plek waar hij uit elkaar viel.
     */
    public static void destroyed(ServerPlayer player, UUID id, ServerLevel level, Vec3 where) {
        ImtArsenal arsenal = get(player);
        if (arsenal.find(id) == null) {
            return;
        }
        set(player, arsenal.without(id));
        for (ItemStack scrap : ArsenalCosts.wreckage(level.getRandom())) {
            ItemEntity item = new ItemEntity(level, where.x, where.y, where.z, scrap);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, where.x, where.y, where.z, 12, 0.3, 0.3, 0.3, 0.02);
        message(player, Component.translatable("arsenal.imttech.msg.lost", arsenal.size() - 1),
                ImtMessagePayload.Level.ALERT);
        ImtAdvancements.award(player, ImtAdvancements.DRONE_DOWN);
    }

    // ---------------------------------------------------------------- Terugroepen, repareren, lanceren

    /**
     * Haalt een drone uit zijn baan en laat hem naast je landen.
     *
     * Hij komt echt uit de lucht vallen en blijft dan naast je staan wachten. Repareren doe je
     * met een rechtsklik op de drone zelf; shift-rechtsklik stuurt hem weer omhoog.
     */
    public static boolean recall(ServerPlayer player, UUID id) {
        ImtArsenal arsenal = get(player);
        ArsenalDrone drone = arsenal.find(id);
        if (drone == null || drone.state() != ArsenalDrone.State.ORBIT) {
            return false;
        }
        ArsenalDrone grounded = drone.withState(ArsenalDrone.State.GROUNDED);
        set(player, arsenal.replace(grounded));
        if (ImtDrones.landRecalled(player, grounded) == null) {
            // Er kon geen drone neergezet worden: dan blijft hij gewoon in zijn baan hangen.
            set(player, get(player).replace(drone));
            return false;
        }
        message(player, Component.translatable("arsenal.imttech.msg.coming_down"),
                ImtMessagePayload.Level.INFO);
        return true;
    }

    /**
     * Stuurt een drone die aan de grond staat weer de ruimte in, vanuit het arsenaal-scherm.
     * De drone die naast je staat stijgt dan echt op.
     */
    public static boolean launch(ServerPlayer player, UUID id) {
        ArsenalDrone drone = get(player).find(id);
        if (drone == null || drone.state() != ArsenalDrone.State.GROUNDED) {
            return false;
        }
        DroneEntity landed = ImtDrones.findGrounded(player, id);
        if (landed != null) {
            landed.departToOrbit(player.serverLevel());
            return true;
        }
        // Zijn drone is er niet meer (te ver weg gelopen, wereld opnieuw geladen): dan zetten
        // we hem gewoon administratief terug in zijn baan.
        set(player, get(player).replace(drone.withState(ArsenalDrone.State.ORBIT)));
        return true;
    }

    /** Wat het arsenaal-scherm vraagt: terugroepen, repareren of weer lanceren. */
    public static void handleAction(ServerPlayer player, ArsenalActionPayload payload) {
        // Zonder bril geen arsenaal: I.M.T houdt je drones in hun baan.
        if (ImtServer.authorizedGlasses(player) == null) {
            return;
        }
        switch (payload.op()) {
            case RECALL -> recall(player, payload.drone());
            case LAUNCH -> launch(player, payload.drone());
        }
    }

    // ---------------------------------------------------------------- Wie is er echt nog onderweg?

    /**
     * De drones die nu in de wereld een opdracht doen, per drone uit een arsenaal. Een drone meldt zich hier elke tick
     * (DroneEntity.tick) zolang hij in je arsenaal op "On a job" staat. Zo zien we een drone die stilvalt of verdwijnt
     * zonder zich terug te melden.
     */
    private static final Map<UUID, DroneEntity> IN_THE_WORLD = new HashMap<>();

    /** Zo lang (in ticks) mag een drone op een opdracht niets doen voor we zeggen dat hij stilstaat: 2 seconden. */
    private static final int STANDING_STILL_TICKS = 40;

    /** Deze drone is bezig met een opdracht (zie DroneEntity.tick). */
    public static void seen(DroneEntity drone) {
        if (drone.getArsenalId() != null) {
            IN_THE_WORLD.put(drone.getArsenalId(), drone);
        }
    }

    /** Deze drone heeft zijn rit afgeboekt (of is een oude kopie): niet meer in de gaten houden. */
    public static void forget(DroneEntity drone) {
        if (drone.getArsenalId() != null) {
            IN_THE_WORLD.remove(drone.getArsenalId(), drone);
        }
    }

    /**
     * Elke seconde: klopt "On a job" nog met wat de drones echt doen?
     *
     * Normaal meldt elke drone zich zelf terug als hij klaar is. Dit is het vangnet voor wat daar tussendoor glipt:
     * een drone die al 2 seconden niets meer doet, staat stil in een stuk wereld waar Minecraft niets laat bewegen (of
     * kwam nooit echt in de wereld). Die maakt zijn rit af en gaat terug in zijn baan (DroneEntity.abandon). En een
     * drone die al uit de wereld is zonder zich terug te melden, wordt alsnog teruggeboekt.
     *
     * Vroeger bleven zulke drones in je arsenaal op "On a job" staan, ook als ze allang niets meer deden.
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (IN_THE_WORLD.isEmpty() || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (DroneEntity drone : List.copyOf(IN_THE_WORLD.values())) {
            if (!(drone.level() instanceof ServerLevel level) || !drone.isOnAJob()) {
                forget(drone);
            } else if (drone.isRemoved()) {
                drone.handBack(level);
                forget(drone);
            } else if (level.getGameTime() - drone.lastActiveTick() > STANDING_STILL_TICKS) {
                drone.abandon(level);
                forget(drone);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        IN_THE_WORLD.clear();
    }

    // ---------------------------------------------------------------- Bij het inloggen

    /**
     * Je drones verdwijnen uit de wereld zodra jij weg bent. Wat nog als "bezig" geboekt
     * stond, hangt dus gewoon weer in een baan. (En een oude "in de hangar"-vlag gaat weg: drones
     * staan sinds hoofdstuk 25 niet meer op een dock.)
     *
     * Hing er toch nog een drone van jou ergens in de wereld (hij viel stil terwijl je weg was), dan is die vanaf nu een
     * oude kopie: komt hij weer in beweging, dan vliegt hij weg zonder iets te boeken (zie DroneEntity.stale).
     */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ImtArsenal arsenal = get(player);
        ImtArsenal cleaned = arsenal.groundDeployed().undocked();
        if (cleaned != arsenal) {
            set(player, cleaned);
        }
        for (DroneEntity drone : List.copyOf(IN_THE_WORLD.values())) {
            if (player.getUUID().equals(drone.getOwnerId())) {
                drone.markStale();
            }
        }
    }

    private static void message(ServerPlayer player, Component text, ImtMessagePayload.Level level) {
        ImtServer.send(player, text, level);
    }

}
