package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.drone.EdithDrones;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.edith.EdithAction;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithOwner;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.nav.EdithNavigation;
import com.senne.starktech.nav.EdithWaypoints;
import com.senne.starktech.nav.Waypoint;
import com.senne.starktech.network.DroneControlPayload;
import com.senne.starktech.network.WaypointActionPayload;
import com.senne.starktech.registry.ModArmorMaterials;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.satellite.SatelliteMode;
import com.senne.starktech.satellite.SatelliteStrikes;
import com.senne.starktech.spy.EdithSpy;
import com.senne.starktech.client.voice.VoiceAction;
import com.senne.starktech.client.voice.VoiceDictionary;
import com.senne.starktech.client.voice.VoiceMatcher;
import com.senne.starktech.station.ChargingStationBlockEntity;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Automatische tests. Ze draaien met "gradlew runGameTestServer", of in het spel met /test runall.
 *
 * In Minecraft 1.21.1 meld je een test aan met @GameTest boven een methode. "template" is het
 * stukje wereld waarin de test draait (een structuur-bestand), "batch" bepaalt welke tests
 * tegelijk lopen: tests met dezelfde batch draaien samen, verschillende batches na elkaar.
 * Dat is belangrijk, want de explosies van de satelliettest mogen de drones van een andere
 * test niet raken.
 *
 * - stark_tower: bouwt de Stark Tower na, controleert de kist en kijkt of het laadstation
 *   een bril echt oplaadt en een upgrade inbouwt.
 * - drone_swarm: een zwerm drones schakelt een vijand uit.
 * - satellite_strike: de satelliet wacht eerst en slaat dan in.
 * - navigation: waypoints opslaan, laden, navigeren en aankomen.
 * - scout_drone: de verkenningsdrone vliegt zoals de speler hem bestuurt.
 * - spy_drone: de spionagedrone volgt zijn doelwit, ook als dat wegloopt.
 * - voice_dictionary: de spraakcommando's worden herkend (ook met een invulplek in de zin).
 */
@GameTestHolder(StarkTech.MODID)
@PrefixGameTestTemplate(false)
public final class StarkTechGameTests {
    private StarkTechGameTests() {}

    // Posities in de Stark Tower (zie tools/generate_structure.py). In een gametest ligt de
    // structuur één blok boven het "structuurblok", dus tellen we bij y altijd 1 op.
    private static final BlockPos STATION_POS = new BlockPos(7, 2, 12);
    private static final BlockPos CHEST_POS = new BlockPos(3, 2, 12);

    /** De lege test-arena (zie tools/generate_test_structure.py). */
    private static final String ARENA = "empty";

    // ================================================================ De tests

    @GameTest(template = "stark_tower", timeoutTicks = 200, batch = "station")
    public static void stark_tower(GameTestHelper helper) {
        towerAndStation(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 2400, batch = "drones")
    public static void drone_swarm(GameTestHelper helper) {
        droneSwarm(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 2400, batch = "scout")
    public static void scout_drone(GameTestHelper helper) {
        scoutDrone(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 2400, batch = "spy")
    public static void spy_drone(GameTestHelper helper) {
        spyDrone(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 300, batch = "satellite")
    public static void satellite_strike(GameTestHelper helper) {
        satelliteStrike(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = "navigation")
    public static void navigation(GameTestHelper helper) {
        navigationTest(helper);
    }

    /**
     * Blokken met een block entity zijn standaard onzichtbaar. Deze test bewaakt dat het
     * laadstation zijn model laat zien, zodat het niet als een onzichtbare muur in je wereld staat.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "voice")
    public static void charging_station_is_visible(GameTestHelper helper) {
        BlockState state = ModBlocks.CHARGING_STATION.get().defaultBlockState();
        if (state.getRenderShape() != RenderShape.MODEL) {
            throw new GameTestAssertException("Het laadstation wordt niet getekend: " + state.getRenderShape());
        }
        helper.succeed();
    }

    /**
     * De bril is een stuk armor, en armor heeft een materiaal nodig dat ook echt in het spel
     * geregistreerd is. Was dat niet zo, dan crashte het spel zodra iets de eigenschappen van
     * de bril opvroeg - bijvoorbeeld bij het tonen van de tooltip in de creative-tab.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "voice")
    public static void glasses_armor_material(GameTestHelper helper) {
        if (!ModArmorMaterials.EDITH.isBound()) {
            throw new GameTestAssertException("Het materiaal starktech:edith is niet geregistreerd.");
        }
        ItemStack glasses = new ItemStack(ModItems.EDITH_GLASSES.get());
        // Dit is precies wat de tooltip doet. Een niet-geregistreerd materiaal knalt hier.
        glasses.forEachModifier(EquipmentSlot.HEAD, (attribute, modifier) -> {});
        helper.succeed();
    }

    /**
     * Het woordenboek van de spraakbesturing: herkent E.D.I.T.H. de zinnen die erin staan,
     * en haalt ze de juiste waarde uit een zin met een invulplek?
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "voice")
    public static void voice_dictionary(GameTestHelper helper) {
        VoiceDictionary dictionary;
        try {
            dictionary = VoiceDictionary.loadDefault();
        } catch (java.io.IOException e) {
            throw new GameTestAssertException("Woordenboek kan niet geladen worden: " + e.getMessage());
        }
        VoiceMatcher matcher = new VoiceMatcher(dictionary);

        // Zin -> verwachte actie, en (optioneel) wat er ingevuld moet worden.
        checkVoice(matcher, "stuur de drones", VoiceAction.LAUNCH_DRONES, null);
        checkVoice(matcher, "stand down", VoiceAction.STAND_DOWN, null);
        checkVoice(matcher, "bespioneer hem", VoiceAction.SPY_DRONE, null);
        checkVoice(matcher, "til me op", VoiceAction.TRANSPORT_RIDE, null);
        // "naar thuis" is bewust een eigen commando (naar huis), dus nemen we een andere plek:
        checkVoice(matcher, "breng me naar de diamantmijn", VoiceAction.NAVIGATE_TO, "diamantmijn");
        // Invulplek in het MIDDEN van de zin (dit kon eerst niet). We nemen bewust namen die
        // niet op een bestaand commando lijken: "licht aan" is bijvoorbeeld al nachtzicht.
        checkVoice(matcher, "zet de voordeur aan", VoiceAction.REDSTONE_ON, "voordeur");
        checkVoice(matcher, "doe de voordeur uit", VoiceAction.REDSTONE_OFF, "voordeur");
        checkVoice(matcher, "turn on the front door", VoiceAction.REDSTONE_ON, "front door");
        checkVoice(matcher, "geef de val een puls", VoiceAction.REDSTONE_PULSE, "val");
        helper.succeed();
    }

    /** Controleert of een zin het juiste commando (en de juiste waarde) oplevert. */
    private static void checkVoice(VoiceMatcher matcher, String heard, VoiceAction expected, @Nullable String value) {
        VoiceMatcher.Match match = matcher.best(heard);
        if (match == null) {
            throw new GameTestAssertException("Niet herkend: \"" + heard + "\"");
        }
        boolean found = match.command().actions.stream().anyMatch(ref -> ref.action() == expected);
        if (!found) {
            throw new GameTestAssertException("\"" + heard + "\" werd " + match.command().name
                    + " in plaats van " + expected);
        }
        if (value != null && !match.captured().equalsIgnoreCase(value)) {
            throw new GameTestAssertException("\"" + heard + "\" vulde in: \"" + match.captured()
                    + "\" in plaats van \"" + value + "\"");
        }
    }

    // ================================================================ Hulpjes

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
        player.moveTo(pos.x, pos.y, pos.z);
        player.setNoGravity(true);
        ItemStack glasses = new ItemStack(ModItems.EDITH_GLASSES.get());
        EdithData.setEnergy(glasses, EdithData.getMaxEnergy(glasses));
        EdithData.setOwner(glasses, new EdithOwner(player.getUUID(), player.getGameProfile().getName(), false));
        player.setItemSlot(EquipmentSlot.HEAD, glasses);
        return player;
    }

    /** Een vijand die stil blijft staan (geen AI) en niet in de zon verbrandt. */
    private static Husk spawnDummyEnemy(GameTestHelper helper, BlockPos relativePos) {
        Husk husk = helper.spawn(EntityType.HUSK, relativePos);
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
    private static void navigationTest(GameTestHelper helper) {
        ServerPlayer player = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        EdithNavigation.handleWaypointAction(player,
                new WaypointActionPayload(WaypointActionPayload.Op.ADD, 0, "  Thuis\u00a7c  ", Waypoint.COLORS[2]));
        EdithWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        if (data.waypoints().size() != 1 || !data.waypoints().getFirst().name().equals("Thuis")) {
            throw new GameTestAssertException("Waypoint niet (goed) opgeslagen: " + data);
        }

        // Opslaan en weer inlezen (zoals bij het afsluiten en opnieuw laden van de wereld).
        var saved = EdithWaypoints.CODEC.codec().encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        EdithWaypoints loaded = EdithWaypoints.CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
        if (!loaded.equals(data)) {
            throw new GameTestAssertException("Waypoints anders na opslaan: " + loaded);
        }

        EdithNavigation.handleWaypointAction(player, new WaypointActionPayload(WaypointActionPayload.Op.NAVIGATE, 0, "", 0));
        if (player.getData(ModAttachments.WAYPOINTS).active() != 0) {
            throw new GameTestAssertException("Navigatie niet gestart");
        }
        // De speler staat al op de plek, dus de volgende controle moet "aangekomen" zeggen.
        EdithNavigation.tick(player);
        if (player.getData(ModAttachments.WAYPOINTS).active() != EdithWaypoints.NONE) {
            throw new GameTestAssertException("Aankomst niet herkend");
        }

        EdithNavigation.handleWaypointAction(player, new WaypointActionPayload(WaypointActionPayload.Op.REMOVE, 0, "", 0));
        if (!player.getData(ModAttachments.WAYPOINTS).waypoints().isEmpty()) {
            throw new GameTestAssertException("Waypoint niet verwijderd");
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
                throw new GameTestAssertException("Geen spionage-opdracht gestart (gemarkeerd: " + marked
                        + ", energie: " + EdithData.getEnergy(EdithData.getWornGlasses(owner))
                        + ", drones: " + EdithDrones.find(owner, StarkDroneEntity.Mode.SPY).size() + ")");
            }
            if (EdithSpy.of(owner).log().isEmpty()) {
                throw new GameTestAssertException("Logboek blijft leeg");
            }
            if (EdithDrones.find(owner, StarkDroneEntity.Mode.SPY).isEmpty()) {
                throw new GameTestAssertException("Geen spionagedrone in de lucht");
            }
            // Het doelwit verplaatst zich: de drone moet erachteraan.
            target.moveTo(helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(14, 3, 1))));
        });
        helper.succeedWhen(() -> {
            List<StarkDroneEntity> spies = EdithDrones.find(owner, StarkDroneEntity.Mode.SPY);
            if (spies.isEmpty()) {
                throw new GameTestAssertException("Spionagedrone verdwenen");
            }
            float distance = spies.getFirst().distanceTo(target);
            if (distance > 10) {
                throw new GameTestAssertException("Spion volgt zijn doelwit niet: " + distance);
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
                throw new GameTestAssertException("Geen verkenningsdrone gelanceerd");
            }
            UUID scoutId = scout.getUUID();
            double startX = scout.getX();
            helper.succeedWhen(() -> {
                // Elke tick "vooruit" sturen (naar het oosten), zoals het spel van de speler zou doen.
                EdithServer.handleDroneControl(owner, new DroneControlPayload(1, 0, 0, false, -90, 0));
                if (!(helper.getLevel().getEntity(scoutId) instanceof StarkDroneEntity drone)) {
                    throw new GameTestAssertException("Verkenningsdrone verdwenen");
                }
                if (drone.getX() - startX < 5) {
                    throw new GameTestAssertException("Drone vloog niet vooruit: " + (drone.getX() - startX));
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
                throw new GameTestAssertException("Verwacht " + expected + " drones, kreeg er " + launched);
            }
        });
        helper.succeedWhen(() -> {
            if (findAlive(helper, enemyId) instanceof LivingEntity alive) {
                throw new GameTestAssertException("Vijand leeft nog: " + alive.getHealth() + " levens");
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
                throw new GameTestAssertException("De satelliet sloeg te vroeg in");
            }
        });
        // ...en daarna is de vijand weg.
        helper.succeedWhen(() -> {
            if (findAlive(helper, enemyId) != null) {
                throw new GameTestAssertException("Vijand leeft nog na de inslag");
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

    private static void towerAndStation(GameTestHelper helper) {
        // 1. Het gebouw is goed geplaatst.
        helper.assertBlockPresent(ModBlocks.CHARGING_STATION.get(), STATION_POS);
        helper.assertBlockPresent(Blocks.CHEST, CHEST_POS);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(CHEST_POS);
        if (chest.getLootTable() == null) {
            throw new GameTestAssertException("De kist heeft geen loot table");
        }

        // 2. Het laadstation laadt op en bouwt een upgrade in.
        ChargingStationBlockEntity station = (ChargingStationBlockEntity) helper.getBlockEntity(STATION_POS);
        station.setItem(ChargingStationBlockEntity.SLOT_GLASSES, new ItemStack(ModItems.EDITH_GLASSES.get()));
        station.setItem(ChargingStationBlockEntity.SLOT_FUEL, new ItemStack(Items.REDSTONE, 4));
        station.setItem(ChargingStationBlockEntity.SLOT_UPGRADE, new ItemStack(ModItems.BATTERY_UPGRADE.get()));

        helper.succeedWhen(() -> {
            ItemStack glasses = station.getItem(ChargingStationBlockEntity.SLOT_GLASSES);
            if (EdithData.getUpgrades(glasses).battery() != 1) {
                throw new GameTestAssertException("Upgrade nog niet ingebouwd");
            }
            if (EdithData.getEnergy(glasses) < 4 * 500) {
                throw new GameTestAssertException("Bril nog niet opgeladen: " + EdithData.getEnergy(glasses));
            }
        });
    }
}
