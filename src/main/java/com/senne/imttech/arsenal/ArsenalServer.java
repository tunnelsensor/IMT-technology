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
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.senne.imttech.config.ImtConfig;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
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

    /**
     * Drones in hun baan herstellen zich daar (hoofdstuk 25, jouw antwoord: "arsenaal-drones gaan niet meer op een
     * dock, ze blijven in hun baan"): elke seconde {@code ORBIT_REPAIR_PER_SECOND} leven, even snel als vroeger op
     * een dock, en zonder stroom. Een drone die op pad is (DEPLOYED), herstelt niet.
     */
    public static void orbitRepair(ServerPlayer player) {
        ImtArsenal arsenal = get(player);
        ImtArsenal repaired = arsenal;
        for (ArsenalDrone drone : arsenal.drones()) {
            if (drone.state() == ArsenalDrone.State.ORBIT && drone.health() > 0 && !drone.isFullyRepaired()) {
                repaired = repaired.replace(drone.withHealth(drone.health() + ImtConfig.Drones.ORBIT_REPAIR_PER_SECOND));
            }
        }
        if (repaired != arsenal) {
            set(player, repaired);
        }
    }

    /** Elke seconde, voor elke speler: zijn drones in hun baan herstellen. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            orbitRepair(player);
        }
    }

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

    // ---------------------------------------------------------------- Bij het inloggen

    /**
     * Je drones verdwijnen uit de wereld zodra jij weg bent. Wat nog als "bezig" geboekt
     * stond, hangt dus gewoon weer in een baan. (En een oude "in de hangar"-vlag gaat weg: drones
     * staan sinds hoofdstuk 25 niet meer op een dock.)
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
    }

    private static void message(ServerPlayer player, Component text, ImtMessagePayload.Level level) {
        ImtServer.send(player, text, level);
    }

}
