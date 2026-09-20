package com.senne.starktech.arsenal;

import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.ArsenalActionPayload;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModSounds;
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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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

    public static EdithArsenal get(ServerPlayer player) {
        return player.getData(ModAttachments.ARSENAL);
    }

    private static void set(ServerPlayer player, EdithArsenal arsenal) {
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
        EdithArsenal arsenal = get(player);
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
        EdithArsenal arsenal = get(player);
        ArsenalDrone drone = arsenal.find(id);
        if (drone == null) {
            return;
        }
        set(player, arsenal.replace(new ArsenalDrone(id, health, ArsenalDrone.State.ORBIT)));
    }

    /**
     * Een drone is neergehaald: hij gaat uit het arsenaal en een klein deel van de
     * bouwkosten valt als schroot op de plek waar hij uit elkaar viel.
     */
    public static void destroyed(ServerPlayer player, UUID id, ServerLevel level, Vec3 where) {
        EdithArsenal arsenal = get(player);
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
        message(player, Component.translatable("arsenal.starktech.msg.lost", arsenal.size() - 1),
                EdithMessagePayload.Level.ALERT);
    }

    // ---------------------------------------------------------------- Terugroepen, repareren, lanceren

    /** Haalt een drone uit zijn baan naar de grond, zodat je hem kunt repareren. */
    public static boolean recall(ServerPlayer player, UUID id) {
        EdithArsenal arsenal = get(player);
        ArsenalDrone drone = arsenal.find(id);
        if (drone == null || drone.state() != ArsenalDrone.State.ORBIT) {
            return false;
        }
        set(player, arsenal.replace(drone.withState(ArsenalDrone.State.GROUNDED)));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.DRONE_RECALL.get(), SoundSource.PLAYERS, 0.7f, 1.1f);
        return true;
    }

    /** Stuurt een drone die aan de grond staat weer de ruimte in. */
    public static boolean launch(ServerPlayer player, UUID id) {
        EdithArsenal arsenal = get(player);
        ArsenalDrone drone = arsenal.find(id);
        if (drone == null || drone.state() != ArsenalDrone.State.GROUNDED || drone.health() <= 0.0f) {
            return false;
        }
        set(player, arsenal.replace(drone.withState(ArsenalDrone.State.ORBIT)));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.DRONE_DEPLOY.get(), SoundSource.PLAYERS, 0.7f, 1.3f);
        return true;
    }

    /**
     * Repareert een drone die aan de grond staat, met ijzer uit je eigen rugzak.
     *
     * Je hoeft niet genoeg ijzer te hebben: hij herstelt zover als het ijzer reikt, en de rest
     * doe je later. Geeft terug hoeveel staven er zijn verbruikt (0 = er gebeurde niets).
     */
    public static int repair(ServerPlayer player, UUID id) {
        EdithArsenal arsenal = get(player);
        ArsenalDrone drone = arsenal.find(id);
        if (drone == null || drone.state() != ArsenalDrone.State.GROUNDED || drone.isFullyRepaired()) {
            return 0;
        }
        float missing = (float) ArsenalBalance.DRONE_MAX_HEALTH - drone.health();
        int wanted = (int) Math.ceil(missing / ArsenalBalance.REPAIR_PER_INGOT);
        int used = ArsenalCosts.takeRepairParts(player, wanted);
        if (used <= 0) {
            message(player, Component.translatable("arsenal.starktech.msg.no_parts",
                    ArsenalCosts.repairPartName(), wanted), EdithMessagePayload.Level.WARNING);
            return 0;
        }
        float healed = (float) (used * ArsenalBalance.REPAIR_PER_INGOT);
        set(player, arsenal.replace(drone.withHealth(drone.health() + healed)));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.STATION_UPGRADE.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
        return used;
    }

    /** Wat het arsenaal-scherm vraagt: terugroepen, repareren of weer lanceren. */
    public static void handleAction(ServerPlayer player, ArsenalActionPayload payload) {
        // Zonder bril geen arsenaal: E.D.I.T.H. houdt je drones in hun baan.
        if (EdithServer.authorizedGlasses(player) == null) {
            return;
        }
        switch (payload.op()) {
            case RECALL -> recall(player, payload.drone());
            case REPAIR -> repair(player, payload.drone());
            case LAUNCH -> launch(player, payload.drone());
        }
    }

    // ---------------------------------------------------------------- Bij het inloggen

    /**
     * Je drones verdwijnen uit de wereld zodra jij weg bent. Wat nog als "bezig" geboekt
     * stond, hangt dus gewoon weer in een baan.
     */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EdithArsenal arsenal = get(player);
        EdithArsenal cleaned = arsenal.groundDeployed();
        if (cleaned != arsenal) {
            set(player, cleaned);
        }
    }

    private static void message(ServerPlayer player, Component text, EdithMessagePayload.Level level) {
        EdithServer.send(player, text, level);
    }

}
