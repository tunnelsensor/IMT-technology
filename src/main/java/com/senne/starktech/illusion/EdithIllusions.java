package com.senne.starktech.illusion;

import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.edith.StarkAdvancements;
import com.senne.starktech.drone.EdithDrones;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.DroneCameraPayload;
import com.senne.starktech.network.DroneControlPayload;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.IllusionAimPayload;
import com.senne.starktech.network.IllusionScenePayload;
import com.senne.starktech.network.ModNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De cloakingdrones en hun illusies (zoals de drones van Mysterio).
 *
 * Hoe het werkt:
 * - Een valse muur of nep-leger RICHT je eerst: een drone vliegt van je weg en jij kijkt door
 *   hem heen (zoals de drone-camera). Onder de drone zie je een voorvertoning op de grond.
 *   Linkermuisknop zet het beeld daar neer, rechtermuisknop annuleert (zie {@link #place}).
 * - Je stuurt een cloakingdrone de lucht in. Die drone is onzichtbaar, maar hij is er ECHT:
 *   hij heeft een hitbox, dus wie weet waar hij hangt kan hem uit de lucht slaan.
 * - Rond de drone hangt een "veld" (een bol met straal ILLUSION_RADIUS). Iedereen die in dat
 *   veld kijkt, ziet het beeld dat de drone projecteert: nep-blokken, nep-wezens, of niets
 *   (dan verbergt hij wat er echt staat).
 * - Alles is nep en alleen beeld: in de wereld verandert niets. Haal je de drone neer, dan is
 *   het beeld meteen weg.
 *
 * Zwakke plekken (net als in de film):
 * - overlappen twee velden elkaar, dan gaat het beeld in dat stuk flikkeren (zie de client);
 * - de drone zelf blijft raakbaar;
 * - een E.M.P.-puls legt alle illusies in de buurt tijdelijk plat;
 * - de drone zoemt zachtjes en laat af en toe vonkjes zien, dus goed kijken (of luisteren) helpt.
 */
public final class EdithIllusions {
    private EdithIllusions() {}

    /** Eén actieve projectie: een drone met zijn scène. */
    public static final class Projector {
        private final UUID ownerId;
        private final UUID droneId;
        /** Het entity-nummer van de drone: dat gebruiken de clients om hem te herkennen. */
        private final int droneEntityId;
        private final IllusionScene scene;
        private final double radius;
        /** Het punt op de grond waar het beeld staat (zie IllusionScene). */
        private final BlockPos anchor;
        /** Zolang dit > 0 is, ligt de illusie plat door een E.M.P. */
        private int jammedTicks;
        /** Spelers die de scène al gekregen hebben. */
        private final Set<UUID> informed = new HashSet<>();

        Projector(UUID ownerId, UUID droneId, int droneEntityId, IllusionScene scene, double radius, BlockPos anchor) {
            this.ownerId = ownerId;
            this.droneId = droneId;
            this.droneEntityId = droneEntityId;
            this.scene = scene;
            this.radius = radius;
            this.anchor = anchor;
        }

        public BlockPos anchor() {
            return anchor;
        }

        public IllusionScene scene() {
            return scene;
        }

        public boolean jammed() {
            return jammedTicks > 0;
        }
    }

    /** Alle actieve projecties (per drone). */
    private static final Map<UUID, Projector> PROJECTORS = new HashMap<>();

    /**
     * Iemand is een illusie aan het richten: welke drone, wat voor beeld, en hoe hij die drone
     * nu bestuurt (de toetsen en kijkrichting die zijn spel elke tick stuurt).
     */
    private static final class Aim {
        final UUID droneId;
        final IllusionScene.Kind kind;
        final IllusionScene scene;
        final long startTick;
        DroneControlPayload control;

        Aim(UUID droneId, IllusionScene.Kind kind, IllusionScene scene, long startTick, DroneControlPayload control) {
            this.droneId = droneId;
            this.kind = kind;
            this.scene = scene;
            this.startTick = startTick;
            this.control = control;
        }
    }

    /** Wie er nu aan het richten is (per speler). */
    private static final Map<UUID, Aim> AIMS = new HashMap<>();
    /** Na zo lang richten (ticks) geeft de drone het op. 1800 ticks = anderhalve minuut. */
    private static final int AIM_TIMEOUT = 1_800;

    // ================================================================ Starten

    /** De illusies die deze speler nu projecteert (voor de tests). */
    public static List<Projector> projectorsOf(ServerPlayer owner) {
        return PROJECTORS.values().stream().filter(projector -> projector.ownerId.equals(owner.getUUID())).toList();
    }

    /** Hoeveel illusies deze speler nu projecteert. */
    public static int countOf(ServerPlayer owner) {
        return (int) PROJECTORS.values().stream().filter(projector -> projector.ownerId.equals(owner.getUUID())).count();
    }

    /**
     * Start een illusie. Wat je krijgt, hangt af van de soort:
     * - WALL:  een valse muur van het blok dat je in je andere hand hebt (of steen);
     * - ARMY:  een nep-leger van het wezen uit het spawn-ei in je hand (of zombies);
     * - CLOAK: verbergt alles in het veld.
     *
     * Muur en leger richt je eerst met de drone; het onzichtbare veld komt meteen waar je kijkt.
     * Ben je al aan het richten, dan betekent nog eens kiezen: zet hem hier neer.
     */
    public static void start(ServerPlayer owner, ItemStack glasses, IllusionScene.Kind kind) {
        if (AIMS.containsKey(owner.getUUID())) {
            place(owner);
            return;
        }
        if (countOf(owner) >= StarkConfig.Illusions.MAX_ACTIVE) {
            EdithServer.send(owner, Component.translatable("edith.starktech.msg.illusion_too_many",
                    StarkConfig.Illusions.MAX_ACTIVE), EdithMessagePayload.Level.WARNING);
            return;
        }
        IllusionScene scene = switch (kind) {
            case WALL -> IllusionScene.wall(blockFromHand(owner), StarkConfig.Illusions.WALL_WIDTH,
                    StarkConfig.Illusions.WALL_HEIGHT);
            case ARMY -> IllusionScene.army(mobFromHand(owner), StarkConfig.Illusions.ARMY_SIZE, 3.0);
            case CLOAK -> IllusionScene.CLOAK_ONLY;
        };
        // Een illusie is ook gewoon een drone: zonder drone in een baan gebeurt er niets.
        if (ArsenalServer.get(owner).availableCount() <= 0) {
            EdithServer.send(owner, Component.translatable("arsenal.starktech.msg.empty"),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        // Betalen doen we pas bij het neerzetten, maar genoeg energie moet er nu al zijn.
        if (EdithData.getEnergy(glasses) < StarkConfig.Illusions.COST) {
            EdithServer.send(owner, Component.translatable("edith.starktech.msg.not_enough_energy",
                    StarkConfig.Illusions.COST), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (kind == IllusionScene.Kind.CLOAK) {
            StarkDroneEntity drone = EdithDrones.launchIllusion(owner);
            if (drone != null && EdithData.tryUseEnergy(glasses, StarkConfig.Illusions.COST)) {
                project(owner, drone, scene, IllusionScene.groundBelow(drone.level(), drone.position()), kind);
            }
            return;
        }
        // Muur of leger: eerst richten. De drone vliegt van je weg en jij kijkt door hem heen.
        StarkDroneEntity drone = EdithDrones.launchIllusionAim(owner);
        if (drone == null) {
            return;
        }
        AIMS.put(owner.getUUID(), new Aim(drone.getUUID(), kind, scene, owner.level().getGameTime(),
                new DroneControlPayload(0, 0, 0, false, owner.getYRot(), owner.getXRot())));
        ModNetwork.sendTo(owner, new DroneCameraPayload(drone.getId(), true));
        ModNetwork.sendTo(owner, new IllusionAimPayload(drone.getId(), Optional.of(scene)));
        EdithServer.send(owner, Component.translatable("edith.starktech.msg.illusion_aim"), EdithMessagePayload.Level.INFO);
    }

    /** De illusie gaat echt aan: deze drone projecteert dit beeld op dit punt. */
    private static void project(ServerPlayer owner, StarkDroneEntity drone, IllusionScene scene, BlockPos anchor,
                                IllusionScene.Kind kind) {
        Projector projector = new Projector(owner.getUUID(), drone.getUUID(), drone.getId(), scene,
                StarkConfig.Illusions.RADIUS, anchor);
        PROJECTORS.put(drone.getUUID(), projector);
        StarkAdvancements.award(owner, StarkAdvancements.ILLUSION);
        EdithServer.send(owner, Component.translatable("edith.starktech.msg.illusion_started",
                Component.translatable("edith.starktech.illusion.kind." + kind.name().toLowerCase(java.util.Locale.ROOT)),
                scene.size()), EdithMessagePayload.Level.INFO);
    }

    // ================================================================ Richten

    /**
     * Neerzetten (linkermuisknop tijdens het richten): het beeld komt op de grond waar de drone
     * naar kijkt, de muur dwars op die kijkrichting, en de drone gaat er recht boven hangen.
     */
    public static void place(ServerPlayer owner) {
        Aim aim = AIMS.remove(owner.getUUID());
        if (aim == null) {
            return;
        }
        endAimView(owner);
        StarkDroneEntity drone = findDrone(owner.server, aim.droneId);
        if (drone == null) {
            EdithServer.send(owner, Component.translatable("edith.starktech.msg.illusion_lost"), EdithMessagePayload.Level.WARNING);
            return;
        }
        ItemStack glasses = EdithServer.authorizedGlasses(owner);
        if (glasses == null || countOf(owner) >= StarkConfig.Illusions.MAX_ACTIVE
                || !EdithData.tryUseEnergy(glasses, StarkConfig.Illusions.COST)) {
            EdithServer.send(owner, Component.translatable("edith.starktech.msg.not_enough_energy",
                    StarkConfig.Illusions.COST), EdithMessagePayload.Level.WARNING);
            drone.recall(owner.serverLevel());
            return;
        }
        BlockPos anchor = IllusionScene.aimAnchor(drone.level(), drone.getEyePosition(),
                aim.control.yaw(), aim.control.pitch());
        IllusionScene scene = aim.scene.facing(aim.control.yaw());
        // Boven het beeld hangen: daar zit het midden van zijn veld (tenzij daar iets in de weg zit).
        BlockPos above = anchor.above(Math.max(3, scene.height() + 2));
        if (drone.level().getBlockState(above).getCollisionShape(drone.level(), above).isEmpty()) {
            drone.moveTo(above.getX() + 0.5, above.getY(), above.getZ() + 0.5, aim.control.yaw(), 0.0f);
            drone.setDeltaMovement(Vec3.ZERO);
        }
        drone.setLifeTicks(StarkConfig.Illusions.LIFETIME);
        project(owner, drone, scene, anchor, aim.kind);
    }

    /** Annuleren (rechtermuisknop, of er ging iets mis): de drone gaat terug naar zijn baan. */
    public static void cancelAim(ServerPlayer owner, String messageKey) {
        Aim aim = AIMS.remove(owner.getUUID());
        if (aim == null) {
            return;
        }
        endAimView(owner);
        StarkDroneEntity drone = findDrone(owner.server, aim.droneId);
        if (drone != null) {
            drone.recall(owner.serverLevel());
        }
        EdithServer.send(owner, Component.translatable(messageKey), EdithMessagePayload.Level.INFO);
    }

    /** Terug naar je eigen ogen, en de voorvertoning weg. */
    private static void endAimView(ServerPlayer owner) {
        ModNetwork.sendTo(owner, DroneCameraPayload.OFF);
        ModNetwork.sendTo(owner, IllusionAimPayload.DONE);
    }

    /** Is deze speler een illusie aan het richten? */
    public static boolean isAiming(ServerPlayer owner) {
        return AIMS.containsKey(owner.getUUID());
    }

    /**
     * Wordt er met deze drone gericht? Dan de besturing die hij moet volgen, anders null.
     * (De drone vraagt dit zelf, elke tick.)
     */
    public static @Nullable DroneControlPayload aimControl(StarkDroneEntity drone) {
        for (Aim aim : AIMS.values()) {
            if (aim.droneId.equals(drone.getUUID())) {
                return aim.control;
            }
        }
        return null;
    }

    /** De besturing van de speler, terwijl hij richt. Geeft false als hij niet aan het richten is. */
    public static boolean steer(ServerPlayer owner, DroneControlPayload control) {
        Aim aim = AIMS.get(owner.getUUID());
        if (aim == null) {
            return false;
        }
        aim.control = control;
        return true;
    }

    /** Roept alle cloakingdrones van deze speler terug (en stopt het richten). */
    public static void stopAll(ServerPlayer owner) {
        cancelAim(owner, "edith.starktech.msg.illusion_aim_cancelled");
        int stopped = 0;
        for (StarkDroneEntity drone : EdithDrones.find(owner, StarkDroneEntity.Mode.ILLUSION)) {
            Projector projector = PROJECTORS.remove(drone.getUUID());
            // Belangrijk: iedereen die de illusie ziet moet horen dat hij weg is. Deden we dat
            // niet, dan bleef de nep-muur (of het nep-leger) gewoon in beeld staan.
            if (projector != null) {
                broadcastStop(owner.server, projector);
            }
            drone.recall(owner.serverLevel());
            stopped++;
        }
        // Ook projecties waarvan de drone al weg is opruimen.
        PROJECTORS.entrySet().removeIf(entry -> {
            if (!entry.getValue().ownerId.equals(owner.getUUID())) {
                return false;
            }
            broadcastStop(owner.server, entry.getValue());
            return true;
        });
        EdithServer.send(owner, stopped > 0
                ? Component.translatable("edith.starktech.msg.illusion_stopped", stopped)
                : Component.translatable("edith.starktech.msg.illusion_none"), EdithMessagePayload.Level.INFO);
    }

    /** Hoort deze drone bij een actieve illusie, of wordt er nog mee gericht? (De drone vraagt dit zelf.) */
    public static boolean isActive(StarkDroneEntity drone) {
        return PROJECTORS.containsKey(drone.getUUID()) || aimControl(drone) != null;
    }

    // ================================================================ Wat je in je hand hebt

    /** Het blok voor een valse muur: uit je andere hand, anders steen. */
    private static BlockState blockFromHand(ServerPlayer owner) {
        for (ItemStack stack : List.of(owner.getOffhandItem(), owner.getMainHandItem())) {
            if (stack.getItem() instanceof BlockItem blockItem) {
                return blockItem.getBlock().defaultBlockState();
            }
        }
        return net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
    }

    /** Het wezen voor een nep-leger: uit een spawn-ei in je hand, anders een zombie. */
    private static EntityType<?> mobFromHand(ServerPlayer owner) {
        for (ItemStack stack : List.of(owner.getOffhandItem(), owner.getMainHandItem())) {
            if (stack.getItem() instanceof SpawnEggItem egg) {
                return egg.getType(stack);
            }
        }
        return EntityType.ZOMBIE;
    }

    // ================================================================ E.M.P.

    /**
     * Een E.M.P.-puls: alle illusies binnen de straal liggen even plat en de drones krijgen
     * schade. Zo kun je je verdedigen tegen iemand die jou voor de gek houdt.
     */
    public static int emp(ServerLevel level, Vec3 center, double radius) {
        int hit = 0;
        for (StarkDroneEntity drone : level.getEntitiesOfClass(StarkDroneEntity.class,
                new AABB(center, center).inflate(radius), StarkDroneEntity::isIllusion)) {
            Projector projector = PROJECTORS.get(drone.getUUID());
            if (projector != null) {
                projector.jammedTicks = StarkConfig.Illusions.EMP_JAM_TICKS;
                projector.informed.clear(); // iedereen moet de nieuwe stand weten
                hit++;
            }
            drone.hurt(level.damageSources().magic(), 6.0f);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, drone.getX(), drone.getY(), drone.getZ(),
                    20, 0.5, 0.5, 0.5, 0.3);
        }
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 0.5f);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
        return hit;
    }

    // ================================================================ Elke tick

    public static void onServerTick(ServerTickEvent.Post event) {
        tickAims(event.getServer());
        if (PROJECTORS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Projector>> it = PROJECTORS.entrySet().iterator();
        List<Runnable> sends = new ArrayList<>();
        while (it.hasNext()) {
            Projector projector = it.next().getValue();
            if (projector.jammedTicks > 0) {
                projector.jammedTicks--;
                if (projector.jammedTicks == 0) {
                    projector.informed.clear();
                }
            }
            // Drone weg (neergehaald of teruggeroepen)? Dan is de illusie voorbij.
            StarkDroneEntity drone = findDrone(event.getServer(), projector.droneId);
            if (drone == null) {
                it.remove();
                sends.add(() -> broadcastStop(event.getServer(), projector));
                continue;
            }
            // Twee keer per seconde: nieuwe kijkers de scène sturen.
            if (event.getServer().getTickCount() % 10 == 0 && drone.level() instanceof ServerLevel level) {
                informNearby(level, drone, projector);
                // Een klein beetje verraad: af en toe een vonkje en een zacht zoempje.
                if (level.random.nextInt(6) == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, drone.getX(), drone.getY(), drone.getZ(),
                            1, 0.1, 0.1, 0.1, 0.01);
                }
                if (level.random.nextInt(20) == 0) {
                    level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.BEACON_AMBIENT,
                            SoundSource.HOSTILE, 0.25f, 1.8f);
                }
            }
        }
        sends.forEach(Runnable::run);
    }

    /**
     * Wie richt, maar zijn drone kwijt is (neergehaald), is uitgelogd, of er te lang over doet:
     * dan stopt het richten.
     */
    private static void tickAims(net.minecraft.server.MinecraftServer server) {
        if (AIMS.isEmpty()) {
            return;
        }
        for (UUID ownerId : List.copyOf(AIMS.keySet())) {
            Aim aim = AIMS.get(ownerId);
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner == null) {
                AIMS.remove(ownerId); // de drone merkt zelf dat zijn eigenaar weg is
            } else if (findDrone(server, aim.droneId) == null) {
                AIMS.remove(ownerId);
                endAimView(owner);
                EdithServer.send(owner, Component.translatable("edith.starktech.msg.illusion_lost"),
                        EdithMessagePayload.Level.WARNING);
            } else if (owner.level().getGameTime() - aim.startTick > AIM_TIMEOUT) {
                cancelAim(owner, "edith.starktech.msg.illusion_aim_timeout");
            }
        }
    }

    private static @Nullable StarkDroneEntity findDrone(net.minecraft.server.MinecraftServer server, UUID droneId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(droneId) instanceof StarkDroneEntity drone && drone.isAlive()) {
                return drone;
            }
        }
        return null;
    }

    /** Stuurt de scène naar spelers die in de buurt komen en hem nog niet hebben. */
    private static void informNearby(ServerLevel level, StarkDroneEntity drone, Projector projector) {
        double range = projector.radius + 48;
        for (ServerPlayer player : level.players()) {
            boolean near = player.distanceToSqr(drone) <= range * range;
            if (near && projector.informed.add(player.getUUID())) {
                ModNetwork.sendTo(player, new IllusionScenePayload(drone.getId(), projector.ownerId, projector.radius,
                        projector.jammed(), projector.anchor, Optional.of(projector.scene)));
            } else if (!near && projector.informed.remove(player.getUUID())) {
                ModNetwork.sendTo(player, IllusionScenePayload.stop(drone.getId()));
            }
        }
    }

    /** De illusie is voorbij: iedereen die hem kende, mag hem vergeten. */
    private static void broadcastStop(net.minecraft.server.MinecraftServer server, Projector projector) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (projector.informed.contains(player.getUUID())) {
                ModNetwork.sendTo(player, IllusionScenePayload.stop(projector.droneEntityId));
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        PROJECTORS.clear();
        AIMS.clear();
    }

    /** Hulpje voor de HUD: hoeveel illusies staan er in de buurt van dit punt? */
    public static int nearbyCount(ServerLevel level, Vec3 point, double range) {
        int count = 0;
        for (StarkDroneEntity drone : level.getEntitiesOfClass(StarkDroneEntity.class,
                new AABB(point, point).inflate(range), StarkDroneEntity::isIllusion)) {
            if (PROJECTORS.containsKey(drone.getUUID())) {
                count++;
            }
        }
        return count;
    }

    /** Hulpje voor de tests: welke wezens staan er in het veld van deze drone? */
    public static List<LivingEntity> inField(ServerLevel level, StarkDroneEntity drone) {
        return level.getEntitiesOfClass(LivingEntity.class,
                new AABB(drone.position(), drone.position()).inflate(StarkConfig.Illusions.RADIUS));
    }
}
