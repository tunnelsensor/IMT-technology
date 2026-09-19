package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.drone.EdithDrones;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.edith.EdithAction;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.nav.EdithNavigation;
import com.senne.starktech.nav.EdithWaypoints;
import com.senne.starktech.nav.Waypoint;
import com.senne.starktech.network.DroneControlPayload;
import com.senne.starktech.network.WaypointActionPayload;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithOwner;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.satellite.SatelliteMode;
import com.senne.starktech.satellite.SatelliteStrikes;
import com.senne.starktech.spy.EdithSpy;
import com.senne.starktech.station.ChargingStationBlockEntity;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Automatische tests. Ze draaien alleen als je "gradlew runGameTestServer" uitvoert,
 * of in het spel met het commando /test runall.
 *
 * - outpost_and_station: bouwt het Stark Industries-gebouw na, controleert de kist en kijkt of
 *   het laadstation een bril echt oplaadt en een upgrade inbouwt.
 * - drone_swarm: een zwerm drones schakelt een vijand uit.
 * - satellite_strike: de satelliet wacht eerst en slaat dan in.
 * - navigation: waypoints opslaan, laden, navigeren en aankomen.
 * - scout_drone: de verkenningsdrone vliegt zoals de speler hem bestuurt.
 * - spy_drone: de spionagedrone volgt zijn doelwit, ook als dat wegloopt.
 */
public final class StarkTechGameTests {
    private StarkTechGameTests() {}

    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, StarkTech.MODID);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> OUTPOST_AND_STATION =
            TEST_FUNCTIONS.register("outpost_and_station", () -> StarkTechGameTests::outpostAndStation);

    // Posities in het gebouw (zie tools/generate_structure.py).
    private static final BlockPos STATION_POS = new BlockPos(5, 1, 7);
    private static final BlockPos CHEST_POS = new BlockPos(1, 1, 7);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> DRONE_SWARM =
            TEST_FUNCTIONS.register("drone_swarm", () -> StarkTechGameTests::droneSwarm);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SATELLITE_STRIKE =
            TEST_FUNCTIONS.register("satellite_strike", () -> StarkTechGameTests::satelliteStrike);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> NAVIGATION =
            TEST_FUNCTIONS.register("navigation", () -> StarkTechGameTests::navigation);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SCOUT_DRONE =
            TEST_FUNCTIONS.register("scout_drone", () -> StarkTechGameTests::scoutDrone);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SPY_DRONE =
            TEST_FUNCTIONS.register("spy_drone", () -> StarkTechGameTests::spyDrone);

    public static void registerTests(RegisterGameTestsEvent event) {
        // Elke test een eigen "omgeving": tests met een andere omgeving draaien na elkaar,
        // zodat de explosie of de drones van de ene test de andere niet kunnen raken.
        Identifier empty = Identifier.withDefaultNamespace("empty");
        event.registerTest(id("outpost_and_station"), new FunctionGameTestInstance(OUTPOST_AND_STATION.getKey(),
                new TestData<>(environment(event, "station"), id("stark_outpost"), 200, 0, true)));
        event.registerTest(id("drone_swarm"), new FunctionGameTestInstance(DRONE_SWARM.getKey(),
                // Ruim de tijd: de testserver tikt heel snel, en het laden van de chunks
                // rondom (nodig voor de vliegende drones) kost echte tijd.
                new TestData<>(environment(event, "drones"), empty, 2400, 0, true)));
        event.registerTest(id("satellite_strike"), new FunctionGameTestInstance(SATELLITE_STRIKE.getKey(),
                new TestData<>(environment(event, "satellite"), empty, 300, 0, true)));
        event.registerTest(id("navigation"), new FunctionGameTestInstance(NAVIGATION.getKey(),
                new TestData<>(environment(event, "navigation"), empty, 100, 0, true)));
        event.registerTest(id("scout_drone"), new FunctionGameTestInstance(SCOUT_DRONE.getKey(),
                new TestData<>(environment(event, "scout"), empty, 2400, 0, true)));
        event.registerTest(id("spy_drone"), new FunctionGameTestInstance(SPY_DRONE.getKey(),
                new TestData<>(environment(event, "spy"), empty, 2400, 0, true)));
    }

    private static Holder<TestEnvironmentDefinition<?>> environment(RegisterGameTestsEvent event, String name) {
        return event.registerEnvironment(id(name), new TestEnvironmentDefinition.AllOf());
    }

    /** Een nep-speler met een opgeladen bril op, die eigenaar is van die bril. */
    @SuppressWarnings("removal")
    private static ServerPlayer makeEdithOwner(GameTestHelper helper, BlockPos relativePos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(relativePos));
        // Een nep-speler houdt de chunks rond zich niet "levend". Zonder dit zouden drones
        // die buiten de chunk van de test vliegen stilvallen.
        int chunkX = SectionPos.blockToSectionCoord(pos.x);
        int chunkZ = SectionPos.blockToSectionCoord(pos.z);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                helper.getLevel().setChunkForced(chunkX + dx, chunkZ + dz, true);
                helper.getLevel().getChunk(chunkX + dx, chunkZ + dz); // meteen inladen
            }
        }
        player.snapTo(pos.x, pos.y, pos.z);
        player.setNoGravity(true);
        ItemStack glasses = new ItemStack(ModItems.EDITH_GLASSES.get());
        EdithData.setEnergy(glasses, EdithData.getMaxEnergy(glasses));
        EdithData.setOwner(glasses, new EdithOwner(player.getUUID(), player.getGameProfile().name(), false));
        player.setItemSlot(EquipmentSlot.HEAD, glasses);
        return player;
    }

    /** Een vijand die stil blijft staan (geen AI) en niet in de zon verbrandt. */
    private static Husk spawnDummyEnemy(GameTestHelper helper, BlockPos relativePos) {
        Husk husk = helper.spawn(EntityTypes.HUSK, relativePos);
        husk.setNoAi(true);
        husk.setNoGravity(true);
        return husk;
    }

    /**
     * Voert iets uit zodra alle posities in een actieve ("entity ticking") chunk liggen.
     * In de testwereld kan het even duren voordat geforceerde chunks actief worden.
     */
    private static void whenEntityTicking(GameTestHelper helper, List<BlockPos> positions, Runnable action) {
        if (positions.stream().allMatch(helper.getLevel()::isPositionEntityTicking)) {
            action.run();
        } else {
            helper.runAfterDelay(5, () -> whenEntityTicking(helper, positions, action));
        }
    }

    /**
     * Navigatie: een plek opslaan, ernaartoe navigeren, en bij aankomst stopt de navigatie.
     * Controleert ook dat de waypoints goed opgeslagen en weer ingelezen worden.
     */
    private static void navigation(GameTestHelper helper) {
        ServerPlayer player = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        EdithNavigation.handleWaypointAction(player,
                new WaypointActionPayload(WaypointActionPayload.Op.ADD, 0, "  Thuis\u00a7c  ", Waypoint.COLORS[2]));
        EdithWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        if (data.waypoints().size() != 1 || !data.waypoints().getFirst().name().equals("Thuis")) {
            throw helper.assertionException(Component.literal("Waypoint niet (goed) opgeslagen: " + data));
        }

        // Opslaan en weer inlezen (zoals bij het afsluiten en opnieuw laden van de wereld).
        var saved = EdithWaypoints.CODEC.codec().encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        EdithWaypoints loaded = EdithWaypoints.CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
        if (!loaded.equals(data)) {
            throw helper.assertionException(Component.literal("Waypoints anders na opslaan: " + loaded));
        }

        EdithNavigation.handleWaypointAction(player, new WaypointActionPayload(WaypointActionPayload.Op.NAVIGATE, 0, "", 0));
        if (player.getData(ModAttachments.WAYPOINTS).active() != 0) {
            throw helper.assertionException(Component.literal("Navigatie niet gestart"));
        }
        // De speler staat al op de plek, dus de volgende controle moet "aangekomen" zeggen.
        EdithNavigation.tick(player);
        if (player.getData(ModAttachments.WAYPOINTS).active() != EdithWaypoints.NONE) {
            throw helper.assertionException(Component.literal("Aankomst niet herkend"));
        }

        EdithNavigation.handleWaypointAction(player, new WaypointActionPayload(WaypointActionPayload.Op.REMOVE, 0, "", 0));
        if (!player.getData(ModAttachments.WAYPOINTS).waypoints().isEmpty()) {
            throw helper.assertionException(Component.literal("Waypoint niet verwijderd"));
        }
        helper.succeed();
    }

    /**
     * De spionagedrone: sturen, laten volgen terwijl het doelwit zich verplaatst, en kijken
     * of het logboek bijgehouden wordt.
     */
    private static void spyDrone(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        Husk target = spawnDummyEnemy(helper, new BlockPos(3, 3, 1));
        AtomicBoolean started = new AtomicBoolean();
        whenEntityTicking(helper, List.of(owner.blockPosition(), target.blockPosition().east(12)), () -> {
            if (started.getAndSet(true)) {
                return;
            }
            // De speler kijkt niet naar het doelwit, dus markeren we het eerst: de spion pakt dan
            // het eerste gemarkeerde doelwit.
            EdithServer.handleMark(owner, target.getId());
            int marked = EdithServer.getMarkedTargets(owner).size();
            EdithServer.handleAction(owner, EdithAction.SPY_DRONE.ordinal());
            if (EdithSpy.of(owner) == null) {
                throw helper.assertionException(Component.literal("Geen spionage-opdracht gestart (gemarkeerd: " + marked
                        + ", energie: " + EdithData.getEnergy(EdithData.getWornGlasses(owner))
                        + ", drones: " + EdithDrones.find(owner, StarkDroneEntity.Mode.SPY).size() + ")"));
            }
            if (EdithSpy.of(owner).log().isEmpty()) {
                throw helper.assertionException(Component.literal("Logboek blijft leeg"));
            }
            if (EdithDrones.find(owner, StarkDroneEntity.Mode.SPY).isEmpty()) {
                throw helper.assertionException(Component.literal("Geen spionagedrone in de lucht"));
            }
            // Het doelwit verplaatst zich: de drone moet erachteraan.
            target.snapTo(helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(14, 3, 1))));
        });
        helper.succeedWhen(() -> {
            List<StarkDroneEntity> spies = EdithDrones.find(owner, StarkDroneEntity.Mode.SPY);
            if (spies.isEmpty()) {
                throw helper.assertionException(Component.literal("Spionagedrone verdwenen"));
            }
            float distance = spies.getFirst().distanceTo(target);
            if (distance > 10) {
                throw helper.assertionException(Component.literal("Spion volgt zijn doelwit niet: " + distance));
            }
        });
    }

    /** De verkenningsdrone vliegt de kant op die de speler hem "stuurt", en stopt weer netjes. */
    private static void scoutDrone(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        owner.setYRot(-90); // naar het oosten kijken (+X)
        // Let op: de testomgeving kan een ingeplande taak soms twee keer uitvoeren. De drone-camera
        // is een aan/uit-knop, dus we zorgen er zelf voor dat we hem maar één keer aanzetten.
        AtomicBoolean launched = new AtomicBoolean();
        whenEntityTicking(helper, List.of(owner.blockPosition(), owner.blockPosition().east(12)), () -> {
            if (launched.getAndSet(true)) {
                return;
            }
            EdithServer.handleAction(owner, EdithAction.DRONE_CAMERA.ordinal());
            StarkDroneEntity scout = EdithDrones.findScout(owner);
            if (scout == null) {
                throw helper.assertionException(Component.literal("Geen verkenningsdrone gelanceerd"));
            }
            UUID scoutId = scout.getUUID();
            double startX = scout.getX();
            helper.succeedWhen(() -> {
                // Elke tick "vooruit" sturen (naar het oosten), zoals het spel van de speler zou doen.
                EdithServer.handleDroneControl(owner, new DroneControlPayload(1, 0, 0, false, -90, 0));
                if (!(helper.getLevel().getEntity(scoutId) instanceof StarkDroneEntity drone)) {
                    throw helper.assertionException(Component.literal("Verkenningsdrone verdwenen"));
                }
                if (drone.getX() - startX < 5) {
                    throw helper.assertionException(Component.literal("Drone vloog niet vooruit: " + (drone.getX() - startX)));
                }
            });
        });
    }

    /** Een zwerm aanvalsdrones schakelt een vijand in de buurt uit. */
    private static void droneSwarm(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        Husk enemy = spawnDummyEnemy(helper, new BlockPos(7, 3, 1));
        UUID enemyId = enemy.getUUID();
        // Wachten tot de chunks van eigenaar en vijand echt "actief" zijn, dan pas lanceren.
        whenEntityTicking(helper, List.of(owner.blockPosition(), enemy.blockPosition()), () -> {
            // Hoeveel drones er in een zwerm zitten staat in de config (EdithConfig), dus vragen we het op.
            int expected = EdithData.getSwarmSize(EdithData.getWornGlasses(owner));
            int launched = EdithDrones.launchSwarm(owner, EdithData.getWornGlasses(owner));
            if (launched != expected) {
                throw helper.assertionException(Component.literal("Verwacht " + expected + " drones, kreeg er " + launched));
            }
        });
        helper.succeedWhen(() -> {
            if (findAlive(helper, enemyId) instanceof LivingEntity alive) {
                throw helper.assertionException(Component.literal("Vijand leeft nog: " + alive.getHealth() + " levens"));
            }
        });
    }

    /** De satelliet slaat na het opladen in en schakelt een vijand op het doel uit. */
    private static void satelliteStrike(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(0, 3, 0));
        Husk enemy = spawnDummyEnemy(helper, new BlockPos(14, 3, 0));
        // Pas na 5 seconden starten: de tests draaien tegelijk, en zo is de drone-test
        // klaar voordat deze explosie zijn drones zou kunnen raken.
        int startDelay = 100;
        // Bombardement: meerdere inslagen, dus de vijand gaat er zeker aan.
        helper.runAfterDelay(startDelay, () -> SatelliteStrikes.start(helper.getLevel(), enemy.position(), owner.getUUID(),
                SatelliteMode.BOMBARDMENT, null, new Vec3(1, 0, 0)));
        // Tijdens het opladen mag er nog niets gebeuren...
        UUID enemyId = enemy.getUUID();
        helper.runAfterDelay(startDelay + EdithBalance.SATELLITE_CHARGE_TIME - 10, () -> {
            if (!(findAlive(helper, enemyId) instanceof LivingEntity alive) || alive.getHealth() < alive.getMaxHealth()) {
                throw helper.assertionException(Component.literal("De satelliet sloeg te vroeg in"));
            }
        });
        // ...en daarna is de vijand weg.
        helper.succeedWhen(() -> {
            if (findAlive(helper, enemyId) != null) {
                throw helper.assertionException(Component.literal("Vijand leeft nog na de inslag"));
            }
        });
    }

    /**
     * Zoekt een wezen op via zijn UUID. Let op: een wezen kan tijdens een test opnieuw
     * ingeladen worden als nieuw object, dus we bewaren nooit het object zelf.
     */
    private static @Nullable LivingEntity findAlive(GameTestHelper helper, UUID id) {
        return helper.getLevel().getEntity(id) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private static void outpostAndStation(GameTestHelper helper) {
        // 1. Het gebouw is goed geplaatst.
        helper.assertBlockPresent(ModBlocks.CHARGING_STATION.get(), STATION_POS);
        helper.assertBlockPresent(Blocks.CHEST, CHEST_POS);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST_POS, ChestBlockEntity.class);
        if (chest.getLootTable() == null) {
            throw helper.assertionException(CHEST_POS, Component.literal("De kist heeft geen loot table"));
        }

        // 2. Het laadstation laadt op en bouwt een upgrade in.
        ChargingStationBlockEntity station = helper.getBlockEntity(STATION_POS, ChargingStationBlockEntity.class);
        station.setItem(ChargingStationBlockEntity.SLOT_GLASSES, new ItemStack(ModItems.EDITH_GLASSES.get()));
        station.setItem(ChargingStationBlockEntity.SLOT_FUEL, new ItemStack(Items.REDSTONE, 4));
        station.setItem(ChargingStationBlockEntity.SLOT_UPGRADE, new ItemStack(ModItems.BATTERY_UPGRADE.get()));

        helper.succeedWhen(() -> {
            ItemStack glasses = station.getItem(ChargingStationBlockEntity.SLOT_GLASSES);
            if (EdithData.getUpgrades(glasses).battery() != 1) {
                throw helper.assertionException(STATION_POS, Component.literal("Upgrade nog niet ingebouwd"));
            }
            if (EdithData.getEnergy(glasses) < 4 * 500) {
                throw helper.assertionException(STATION_POS, Component.literal("Bril nog niet opgeladen: " + EdithData.getEnergy(glasses)));
            }
        });
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(StarkTech.MODID, path);
    }
}
