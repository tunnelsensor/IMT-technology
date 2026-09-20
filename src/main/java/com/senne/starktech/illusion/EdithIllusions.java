package com.senne.starktech.illusion;

import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.edith.StarkAdvancements;
import com.senne.starktech.drone.EdithDrones;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.IllusionScenePayload;
import com.senne.starktech.network.ModNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De cloakingdrones en hun illusies (zoals de drones van Mysterio).
 *
 * Hoe het werkt:
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
        /** Zolang dit > 0 is, ligt de illusie plat door een E.M.P. */
        private int jammedTicks;
        /** Spelers die de scène al gekregen hebben. */
        private final Set<UUID> informed = new HashSet<>();

        Projector(UUID ownerId, UUID droneId, int droneEntityId, IllusionScene scene, double radius) {
            this.ownerId = ownerId;
            this.droneId = droneId;
            this.droneEntityId = droneEntityId;
            this.scene = scene;
            this.radius = radius;
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

    // ================================================================ Starten

    /** Hoeveel illusies deze speler nu projecteert. */
    public static int countOf(ServerPlayer owner) {
        return (int) PROJECTORS.values().stream().filter(projector -> projector.ownerId.equals(owner.getUUID())).count();
    }

    /**
     * Start een illusie. Wat je krijgt, hangt af van de soort:
     * - WALL:  een valse muur van het blok dat je in je andere hand hebt (of steen);
     * - ARMY:  een nep-leger van het wezen uit het spawn-ei in je hand (of zombies);
     * - COPY:  een kopie van het stuk wereld waar je naar kijkt;
     * - CLOAK: verbergt alles in het veld.
     */
    public static void start(ServerPlayer owner, ItemStack glasses, IllusionScene.Kind kind) {
        if (countOf(owner) >= EdithBalance.MAX_ILLUSIONS) {
            EdithServer.send(owner, Component.translatable("edith.starktech.msg.illusion_too_many",
                    EdithBalance.MAX_ILLUSIONS), EdithMessagePayload.Level.WARNING);
            return;
        }
        IllusionScene scene = switch (kind) {
            case WALL -> IllusionScene.wall(blockFromHand(owner), EdithBalance.ILLUSION_WALL_WIDTH,
                    EdithBalance.ILLUSION_WALL_HEIGHT);
            case ARMY -> IllusionScene.army(mobFromHand(owner), EdithBalance.ILLUSION_ARMY_SIZE, 3.0);
            case COPY -> copyLookedAtRegion(owner);
            case CLOAK -> IllusionScene.CLOAK_ONLY;
        };
        if (scene == null) {
            EdithServer.send(owner, Component.translatable("edith.starktech.msg.illusion_no_copy"),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        // Een illusie is ook gewoon een drone: zonder drone in een baan gebeurt er niets.
        if (ArsenalServer.get(owner).availableCount() <= 0) {
            EdithServer.send(owner, Component.translatable("arsenal.starktech.msg.empty"),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        if (!EdithData.tryUseEnergy(glasses, EdithBalance.ILLUSION_COST)) {
            EdithServer.send(owner, Component.translatable("edith.starktech.msg.not_enough_energy",
                    EdithBalance.ILLUSION_COST), EdithMessagePayload.Level.WARNING);
            return;
        }
        StarkDroneEntity drone = EdithDrones.launchIllusion(owner);
        if (drone == null) {
            return;
        }
        Projector projector = new Projector(owner.getUUID(), drone.getUUID(), drone.getId(), scene,
                EdithBalance.ILLUSION_RADIUS);
        PROJECTORS.put(drone.getUUID(), projector);
        StarkAdvancements.award(owner, StarkAdvancements.ILLUSION);
        EdithServer.send(owner, Component.translatable("edith.starktech.msg.illusion_started",
                Component.translatable("edith.starktech.illusion.kind." + kind.name().toLowerCase(java.util.Locale.ROOT)),
                scene.size()), EdithMessagePayload.Level.INFO);
    }

    /** Roept alle cloakingdrones van deze speler terug. */
    public static void stopAll(ServerPlayer owner) {
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

    /** Hoort deze drone bij een actieve illusie? (De drone vraagt dit zelf.) */
    public static boolean isActive(StarkDroneEntity drone) {
        return PROJECTORS.containsKey(drone.getUUID());
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

    /** Kopieert het stuk wereld waar je naar kijkt (een kubus van ILLUSION_COPY_SIZE blokken). */
    private static @Nullable IllusionScene copyLookedAtRegion(ServerPlayer owner) {
        HitResult hit = owner.pick(32.0, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        BlockPos center = blockHit.getBlockPos();
        int half = EdithBalance.ILLUSION_COPY_SIZE / 2;
        List<IllusionScene.BlockElement> blocks = new ArrayList<>();
        ServerLevel level = owner.serverLevel();
        for (int x = -half; x <= half; x++) {
            for (int y = -half; y <= half; y++) {
                for (int z = -half; z <= half; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        // De plek slaan we op ten opzichte van het midden van de kopie.
                        blocks.add(new IllusionScene.BlockElement(new BlockPos(x, y, z), state));
                    }
                }
            }
        }
        return blocks.isEmpty() ? null : IllusionScene.copy(blocks);
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
                projector.jammedTicks = EdithBalance.EMP_JAM_TICKS;
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
                ModNetwork.sendTo(player, new IllusionScenePayload(drone.getId(), projector.radius,
                        projector.jammed(), java.util.Optional.of(projector.scene)));
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
                new AABB(drone.position(), drone.position()).inflate(EdithBalance.ILLUSION_RADIUS));
    }
}
