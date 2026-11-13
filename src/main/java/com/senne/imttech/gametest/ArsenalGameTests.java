package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.arsenal.ArsenalDrone;
import com.senne.imttech.arsenal.ArsenalServer;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.drone.DroneEntity;
import com.senne.imttech.drone.ImtDrones;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.transport.ImtTransport;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Hoofdstuk 88 (jouw melding: "wanneer elke drone in orbit moet zijn staat er toch on a job"): "On a job" in je arsenaal
 * klopt met wat je drones echt doen. Een drone die klaar is, verdwenen is of ergens stilstaat, hangt weer in zijn baan.
 *
 * Wat er mis was, en wat elke test hier vastlegt:
 * - een drone die uit de wereld verdween zonder zich terug te melden, bleef "On a job" (weggehaald);
 * - een drone die zich terugmeldde terwijl jij in een andere wereld was, vond je niet (andere wereld);
 * - een drone die niets meer deed, werd door niemand opgemerkt (stilstaan);
 * - een oude kopie van een drone (terug uit een opgeslagen stuk wereld) boekte zijn oude rit nog eens (oude kopie);
 * - de twee escortedrones van een rit bleven achter, want je vloog sneller dan zij (een rit);
 * - een drone die verder vloog dan het stuk wereld rond de spelers, viel daar stil in de lucht (ver weg).
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class ArsenalGameTests {
    private ArsenalGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Waar deze drone uit je arsenaal nu is: in zijn baan, op een opdracht, aan de grond (of null: niet meer van jou). */
    private static ArsenalDrone.State state(ServerPlayer owner, UUID id) {
        ArsenalDrone drone = ArsenalServer.get(owner).find(id);
        return drone == null ? null : drone.state();
    }

    /** Voert iets uit zodra alle posities in een actieve ("entity ticking") chunk liggen (de testwereld heeft even nodig). */
    private static void whenEntityTicking(GameTestHelper helper, List<BlockPos> positions, Runnable action) {
        if (positions.stream().allMatch(helper.getLevel()::isPositionEntityTicking)) {
            action.run();
        } else {
            helper.runAfterDelay(5, () -> whenEntityTicking(helper, positions, action));
        }
    }

    /**
     * Haalt een drone uit je arsenaal en zet hem op deze plek in de wereld, zoals ImtDrones dat doet (maar zonder dat
     * hij eerst uit de lucht komt invallen).
     */
    private static DroneEntity launch(ServerPlayer owner, ServerLevel level, DroneEntity.Mode mode, Vec3 pos) {
        ArsenalDrone picked = ArsenalServer.checkOutOne(owner);
        check(picked != null, "Er hangt geen drone klaar in het arsenaal");
        DroneEntity drone = ModEntities.DRONE.get().create(level);
        check(drone != null, "Kon geen drone maken");
        drone.setup(owner, mode, 0, Math.round(ImtConfig.Drones.SWARM_LIFETIME));
        drone.setArsenalId(picked.id());
        drone.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
        check(level.addFreshEntity(drone), "De drone kwam niet in de wereld");
        return drone;
    }

    /** Houdt de chunk waar deze plek in ligt geladen en actief (aan) of laat hem weer los (uit). */
    private static void forceChunk(ServerLevel level, BlockPos pos, boolean on) {
        level.setChunkForced(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), on);
        if (on) {
            level.getChunk(pos); // meteen inladen
        }
    }

    // ================================================================ Weggehaald

    /**
     * Een drone die uit de wereld verdwijnt zonder zich terug te melden (iets haalt hem weg, zoals de spionagedrone die
     * zijn doelwit door een portaal volgt: de oude drone wordt dan gewoon weggehaald), hangt meteen weer in zijn baan.
     * Vroeger bleef hij op "On a job" staan tot je opnieuw inlogde.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "arsenal_status")
    public static void a_drone_taken_away_goes_back_to_orbit(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 3, 1));
        AtomicBoolean started = new AtomicBoolean();
        whenEntityTicking(helper, List.of(owner.blockPosition()), () -> {
            if (started.getAndSet(true)) {
                return;
            }
            int ready = ArsenalServer.get(owner).availableCount();
            DroneEntity scout = ImtDrones.launchScout(owner);
            check(scout != null, "De verkenner vertrok niet");
            UUID id = scout.getArsenalId();
            check(state(owner, id) == ArsenalDrone.State.DEPLOYED, "Net vertrokken, maar hij staat op " + state(owner, id));
            scout.discard();
            check(state(owner, id) == ArsenalDrone.State.ORBIT, "Weggehaald, maar hij staat nog op " + state(owner, id));
            check(ArsenalServer.get(owner).availableCount() == ready,
                    "Er hangen er " + ArsenalServer.get(owner).availableCount() + " klaar in plaats van " + ready);
            helper.succeed();
        });
    }

    // ================================================================ Andere wereld

    /**
     * Ben je in een andere wereld als je drone zich terugmeldt (je ging door een portaal terwijl hij nog bezig was), dan
     * vindt hij je toch, en hangt hij daarna weer in je arsenaal. Vroeger zocht hij je alleen in zijn eigen wereld, en
     * bleef hij op "On a job" staan. Hier is de drone in de Nether en jij in de gewone wereld: hij vindt je daar niet om
     * bij je te blijven, dus vliegt hij terug naar zijn baan.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "arsenal_status")
    public static void a_drone_in_another_world_still_reports_back(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 3, 1));
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        check(nether != null, "Deze testserver heeft geen Nether");
        // Daar is geen speler, dus houden we dat stuk Nether zelf wakker.
        BlockPos spot = helper.absolutePos(new BlockPos(1, 0, 1)).atY(100);
        forceChunk(nether, spot, true);
        DroneEntity drone = launch(owner, nether, DroneEntity.Mode.ATTACK, Vec3.atBottomCenterOf(spot));
        UUID id = drone.getArsenalId();
        helper.succeedWhen(() -> {
            check(drone.isRemoved(), "De drone is nog in de Nether (" + drone.getMode() + ")");
            check(state(owner, id) == ArsenalDrone.State.ORBIT,
                    "De drone uit de Nether is weg, maar staat in je arsenaal op " + state(owner, id));
            forceChunk(nether, spot, false);
        });
    }

    // ================================================================ Stilstaan

    /**
     * Een drone die "On a job" staat maar niets doet (hij kwam nooit echt in de wereld, of hij staat stil in een stuk
     * wereld waar Minecraft niets laat bewegen), wordt na 2 seconden opgemerkt en hangt dan weer in zijn baan. Niet
     * meteen: een drone die net vertrekt, mag even de tijd hebben.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "arsenal_status")
    public static void a_drone_that_does_nothing_goes_back_to_orbit(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 3, 1));
        ArsenalDrone picked = ArsenalServer.checkOutOne(owner);
        check(picked != null, "Er hangt geen drone klaar in het arsenaal");
        // Geboekt als "On a job", maar hij komt nooit in de wereld.
        DroneEntity drone = ModEntities.DRONE.get().create(helper.getLevel());
        check(drone != null, "Kon geen drone maken");
        drone.setup(owner, DroneEntity.Mode.ATTACK, 0, Math.round(ImtConfig.Drones.SWARM_LIFETIME));
        drone.setArsenalId(picked.id());
        long start = helper.getLevel().getGameTime();
        helper.runAfterDelay(10, () -> {
            check(state(owner, picked.id()) == ArsenalDrone.State.DEPLOYED,
                    "Na een halve seconde staat hij al op " + state(owner, picked.id()) + " (te snel)");
            helper.succeedWhen(() -> check(state(owner, picked.id()) == ArsenalDrone.State.ORBIT,
                    "Na " + (helper.getLevel().getGameTime() - start) + " ticks staat hij nog op "
                            + state(owner, picked.id())));
        });
    }

    // ================================================================ Oude kopie

    /**
     * Een oude kopie van een drone (hij bleef achter in een stuk wereld dat opgeslagen werd, en komt terug als je daar
     * weer komt) vliegt meteen weg en verandert niets aan je arsenaal. Die drone hangt daar allang weer in zijn baan, of
     * is intussen op een nieuwe opdracht (zoals hier). Vroeger meldde de kopie zich nog eens terug, en stond de drone in
     * je arsenaal "In orbit" terwijl hij nog bezig was.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "arsenal_status")
    public static void an_old_copy_of_a_drone_does_not_touch_your_arsenal(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 3, 1));
        ServerLevel level = helper.getLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(8.5, 4, 8.5));
        AtomicBoolean started = new AtomicBoolean();
        whenEntityTicking(helper, List.of(BlockPos.containing(pos)), () -> {
            if (started.getAndSet(true)) {
                return;
            }
            DroneEntity first = launch(owner, level, DroneEntity.Mode.ATTACK, pos);
            UUID id = first.getArsenalId();
            // Zo slaat de wereld hem op als zijn stuk wereld niet meer geladen is...
            CompoundTag saved = first.saveWithoutId(new CompoundTag());
            // ...en dan is hij weg uit de wereld: terug in zijn baan.
            first.discard();
            check(state(owner, id) == ArsenalDrone.State.ORBIT, "Weggehaald, maar hij staat op " + state(owner, id));
            // Intussen gaat dezelfde drone op een nieuwe opdracht.
            ArsenalServer.markDeployed(owner, id);
            // Dan komt de oude kopie terug: je komt weer in de buurt en zijn stuk wereld wordt geladen.
            DroneEntity copy = ModEntities.DRONE.get().create(level);
            check(copy != null, "Kon geen drone maken");
            copy.load(saved);
            check(level.addFreshEntity(copy), "De oude kopie kwam niet in de wereld");
            helper.succeedWhen(() -> {
                check(state(owner, id) == ArsenalDrone.State.DEPLOYED,
                        "De oude kopie veranderde je arsenaal: de drone staat op " + state(owner, id));
                check(copy.isRemoved(), "De oude kopie hangt er nog (" + copy.getMode() + ")");
            });
        });
    }

    // ================================================================ Een rit

    /**
     * Een rit met de transportdrone: de twee escortedrones vliegen mee, en een escortedrone die ver achterop raakt
     * (64 blokken) springt meteen bij, zoals de andere drones. Na het landen hangen alle drie weer in je arsenaal.
     *
     * Vroeger kon een escortedrone de autopiloot of een sprintende rit niet bijhouden. Hij bleef achter, viel ver weg
     * stil (daar laat Minecraft niets bewegen), en stond na de rit nog op "On a job". Na een paar ritten stonden zo
     * steeds meer drones op "On a job".
     */
    @GameTest(template = ARENA, timeoutTicks = 600, batch = "arsenal_ride")
    public static void a_ride_ends_with_every_drone_back_in_orbit(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 3, 1));
        ServerLevel level = helper.getLevel();
        int ready = ArsenalServer.get(owner).availableCount();
        // Waar de escortedrone "achtergebleven" is: 64 blokken verder (dat stuk wereld houden we zelf wakker).
        BlockPos far = owner.blockPosition().above(4).east(64);
        forceChunk(level, far, true);
        AtomicBoolean started = new AtomicBoolean();
        AtomicReference<DroneEntity> straggler = new AtomicReference<>();
        AtomicLong movedAt = new AtomicLong();
        AtomicBoolean landing = new AtomicBoolean();
        whenEntityTicking(helper, List.of(owner.blockPosition(), far), () -> {
            if (started.getAndSet(true)) {
                return;
            }
            ImtTransport.startRide(owner, ImtData.getWornGlasses(owner), false);
            check(ImtTransport.carrierOf(owner) != null, "Er kwam geen transportdrone onder je hangen");
            helper.succeedWhen(() -> {
                List<DroneEntity> escorts = ImtDrones.find(owner, DroneEntity.Mode.TRANSPORT).stream()
                        .filter(drone -> drone.transportRole() == DroneEntity.TransportRole.FOLLOWER).toList();
                DroneEntity behind = straggler.get();
                if (behind == null) {
                    check(escorts.size() == 2, "Er vliegen " + escorts.size() + " escortedrones mee in plaats van 2");
                    check(escorts.stream().noneMatch(DroneEntity::isDiving), "De escortedrones komen nog aanvliegen");
                    behind = escorts.getFirst();
                    behind.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
                    straggler.set(behind);
                    movedAt.set(level.getGameTime());
                    throw new GameTestAssertException("Een escortedrone staat nu 64 blokken achter");
                }
                if (!landing.get()) {
                    check(behind.isAlive(), "De achtergebleven escortedrone is verdwenen");
                    long ticks = level.getGameTime() - movedAt.get();
                    check(behind.distanceTo(owner) < 12, "De escortedrone hangt na " + ticks + " ticks nog "
                            + Math.round(behind.distanceTo(owner)) + " blokken van je");
                    check(ticks <= 10, "De escortedrone kwam pas na " + ticks + " ticks terug (hij springt niet bij)");
                    check(state(owner, behind.getArsenalId()) == ArsenalDrone.State.DEPLOYED,
                            "De escortedrone is nog bezig, maar staat op " + state(owner, behind.getArsenalId()));
                    // Klaar met vliegen: landen.
                    landing.set(true);
                    DroneEntity carrier = ImtTransport.carrierOf(owner);
                    check(carrier != null, "Je hangt niet meer onder je transportdrone");
                    carrier.landPassenger("imt.imttech.msg.transport_landing", ImtMessagePayload.Level.INFO);
                    throw new GameTestAssertException("Aan het landen");
                }
                check(ImtTransport.carrierOf(owner) == null, "Je hangt nog onder je transportdrone");
                check(ArsenalServer.get(owner).deployedCount() == 0,
                        "Na de rit staan er nog " + ArsenalServer.get(owner).deployedCount() + " drones op \"On a job\"");
                check(ArsenalServer.get(owner).availableCount() == ready, "Na de rit hangen er "
                        + ArsenalServer.get(owner).availableCount() + " klaar in plaats van " + ready);
                forceChunk(level, far, false);
            });
        });
    }

    // ================================================================ Ver weg

    /**
     * Een vrachtdrone naar een kist 160 blokken verderop, ver buiten het stuk wereld dat rond de test wakker is (een
     * testspeler houdt de wereld rond zich niet wakker): hij vliegt er echt helemaal heen, levert af, en hangt daarna
     * weer in je arsenaal.
     *
     * Vroeger viel een drone stil in de lucht zodra hij verder vloog dan je "simulation distance": daar laat Minecraft
     * niets bewegen. De vracht kwam nooit aan, en de drone stond voor altijd op "On a job". Nu houdt elke drone die
     * bezig is, het stukje wereld rond zich wakker (zoals een speler).
     */
    @GameTest(template = ARENA, timeoutTicks = 1600, batch = "arsenal_far")
    public static void a_cargo_drone_flies_on_where_nobody_is(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 3, 1));
        ServerLevel level = helper.getLevel();
        // De kist: 160 blokken naar het westen, op de grond. Dat stuk wereld eerst even inladen: van een stuk dat niet
        // geladen is, zegt Minecraft dat de grond op de bodem van de wereld ligt (dan kwam de kist onder de aarde).
        BlockPos column = helper.absolutePos(new BlockPos(12, 0, 12)).west(160);
        level.getChunkAt(column);
        BlockPos chestPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        // De drone begint boven het plafond van de testarena (daar kan hij niet doorheen klimmen).
        Vec3 start = helper.absoluteVec(new Vec3(12.5, 18, 12.5));
        AtomicBoolean started = new AtomicBoolean();
        double[] farthest = {0};
        whenEntityTicking(helper, List.of(BlockPos.containing(start)), () -> {
            if (started.getAndSet(true)) {
                return;
            }
            DroneEntity drone = launch(owner, level, DroneEntity.Mode.TRANSPORT, start);
            UUID id = drone.getArsenalId();
            drone.setLifeTicks(Math.round(ImtConfig.Transport.MAX_TICKS));
            drone.setupTransport(DroneEntity.TransportRole.CARGO, chestPos, List.of(new ItemStack(Items.DIAMOND, 3)));
            helper.succeedWhen(() -> {
                if (!drone.isRemoved()) {
                    farthest[0] = Math.max(farthest[0], start.x - drone.getX());
                }
                check(!drone.hasCargo(), "Nog onderweg: " + Math.round(start.x - drone.getX()) + " van de "
                        + Math.round(start.x - chestPos.getX() - 0.5) + " blokken (t.o.v. de kist: "
                        + String.format("%.1f, %.1f, %.1f", drone.getX() - chestPos.getX() - 0.5,
                                drone.getY() - chestPos.getY(), drone.getZ() - chestPos.getZ() - 0.5)
                        + "; " + (drone.isRemoved() ? "weg" : "in de wereld")
                        + ", " + (drone.isOnAJob() ? "op een opdracht" : "afgeboekt/oude kopie")
                        + ", " + (level.isPositionEntityTicking(drone.blockPosition()) ? "wereld loopt" : "wereld staat stil")
                        + ", laatst actief " + (level.getGameTime() - drone.lastActiveTick()) + " ticks geleden)");
                check(farthest[0] >= start.x - chestPos.getX() - 4, "De vracht is er, maar de drone kwam maar "
                        + Math.round(farthest[0]) + " blokken ver: hij vloog er niet echt heen");
                check(level.getBlockEntity(chestPos) instanceof Container chest && chest.countItem(Items.DIAMOND) == 3,
                        "De drie diamanten liggen niet in de kist");
                check(state(owner, id) == ArsenalDrone.State.ORBIT,
                        "Afgeleverd, maar de vrachtdrone staat op " + state(owner, id));
            });
        });
    }
}
