package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.arsenal.DroneDockBlockEntity;
import com.senne.imttech.bot.BotNetwork;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.LogicException;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.ScriptChecker;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.robot.DroneData;
import com.senne.imttech.robot.GuardDroneEntity;
import com.senne.imttech.robot.GuardDroneItem;
import com.senne.imttech.robot.IonBlasterItem;
import com.senne.imttech.robot.RobotState;
import com.senne.imttech.robot.TowerDrones;
import com.senne.imttech.bot.BotModule;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityDevices;
import com.senne.imttech.security.os.SecurityOs;
import com.senne.imttech.station.ChargerBlockEntity;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de security drone (hoofdstuk 25): zijn programma (een droneprogramma, met up en down), hoe hij vliegt
 * (DroneMove: blok voor blok, niet door een plafond of de grond), laden op zijn Drone Dock, en de Ion Blaster.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class DroneGameTests {
    private DroneGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Een droneprogramma uit deze code (de code moet kloppen). */
    static LogicProgram program(String code) {
        return new LogicProgram("Test", true, GraphConverter.toGraph(ScriptParser.parse(code, ProgramKind.DRONE), null), code,
                ProgramKind.DRONE);
    }

    /** Een drone die in dit blok hangt (in het midden, een stukje boven de bodem), met zijn neus naar het noorden. */
    private static GuardDroneEntity drone(GameTestHelper helper, BlockPos pos, String code) {
        GuardDroneEntity drone = helper.spawn(ModEntities.GUARD_DRONE.get(), pos);
        Vec3 spot = drone.hoverSpot(helper.absolutePos(pos));
        drone.moveTo(spot.x, spot.y, spot.z, Direction.NORTH.toYRot(), 0);
        drone.setYaw(Direction.NORTH.toYRot());
        drone.brain().body().setFacing(Direction.NORTH);
        drone.setProgram(program(code));
        return drone;
    }

    /** Waar dit is, gezien vanuit de test (in deze arena is y = 1 de vloer). */
    private static BlockPos rel(GameTestHelper helper, BlockPos abs) {
        return abs.subtract(helper.absolutePos(BlockPos.ZERO));
    }

    /** Hangt hij precies in het midden van dit blok (relatief in de test)? */
    private static void checkAt(GameTestHelper helper, GuardDroneEntity drone, BlockPos relative, String what) {
        BlockPos abs = helper.absolutePos(relative);
        check(drone.blockPosition().equals(abs), what + ": hij hangt in " + rel(helper, drone.blockPosition()) + ", niet in " + relative);
        check(drone.position().distanceTo(drone.hoverSpot(abs)) < 0.03,
                what + ": hij hangt niet in het midden van het blok (" + drone.position() + ")");
    }

    /**
     * {@code up(3)}, {@code forward(2)}, {@code turn_right()} en {@code back(1)}: hij stijgt precies 3 blokken, vliegt
     * 2 blokken naar het noorden, draait naar het oosten en vliegt 1 blok achteruit (naar het westen).
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void drone_program_flies_up_and_around(GameTestHelper helper) {
        BlockPos start = new BlockPos(6, 2, 8);
        GuardDroneEntity drone = drone(helper, start, """
                done = false
                startY = 0
                upY = 0
                turned = ""
                when started:
                    startY = drone.y
                    up(3)
                    upY = drone.y
                    forward(2)
                    turn_right()
                    turned = drone.facing
                    back(1)
                    done = true
                """);
        drone.setActive(true);
        helper.succeedWhen(() -> {
            check(Boolean.TRUE.equals(drone.brain().global("done")), "Nog niet klaar met vliegen");
            double startY = (Double) drone.brain().global("startY");
            check(Double.valueOf(startY + 3).equals(drone.brain().global("upY")),
                    "up(3) bracht hem niet 3 blokken hoger: van " + startY + " naar " + drone.brain().global("upY"));
            check("east".equals(drone.brain().global("turned")), "Na turn_right() keek hij niet naar het oosten");
            checkAt(helper, drone, start.above(3).north(2).west(), "Aan het eind");
            check(!drone.brain().body().blocked(), "drone.blocked was waar, maar er stond niets in de weg");
        });
    }

    /**
     * Een plafond: {@code up(5)} komt maar 2 blokken ver (drone.blocked), en {@code down(9)} stopt op de grond (weer
     * drone.blocked), waar {@code drone.height} 0 is.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void drone_program_stops_at_ceiling_and_ground(GameTestHelper helper) {
        BlockPos start = new BlockPos(6, 3, 8);
        helper.setBlock(start.above(3), Blocks.STONE);
        GuardDroneEntity drone = drone(helper, start, """
                done = false
                upHit = false
                upY = 0
                downHit = false
                height = 9
                when started:
                    up(5)
                    upHit = drone.blocked
                    upY = drone.y
                    down(9)
                    downHit = drone.blocked
                    height = drone.height
                    done = true
                """);
        int startY = helper.absolutePos(start).getY();
        drone.setActive(true);
        helper.succeedWhen(() -> {
            check(Boolean.TRUE.equals(drone.brain().global("done")), "Nog niet klaar");
            check(Boolean.TRUE.equals(drone.brain().global("upHit")), "Tegen het plafond was drone.blocked niet waar");
            check(Double.valueOf(startY + 2).equals(drone.brain().global("upY")), "Onder het plafond hing hij niet op y "
                    + (startY + 2) + " maar op " + drone.brain().global("upY"));
            check(Boolean.TRUE.equals(drone.brain().global("downHit")), "Op de grond was drone.blocked niet waar");
            checkAt(helper, drone, new BlockPos(6, 2, 8), "Op de grond");
            check(Double.valueOf(0).equals(drone.brain().global("height")), "Vlak boven de vloer was drone.height niet 0 maar "
                    + drone.brain().global("height"));
        });
    }

    /**
     * Op zijn Drone Dock (neergezet met het item): aan stijgt hij op, {@code charge(60)} laat hem terugvliegen, landen,
     * laden tot 60%, opstijgen en terugvliegen naar waar hij was. Uitgezet landt hij weer op zijn dock.
     */
    @GameTest(template = ARENA, timeoutTicks = 1000)
    public static void drone_charges_on_its_dock(GameTestHelper helper) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 14), "drone-owner");
        BlockPos dockPos = new BlockPos(6, 2, 9);
        GuardDroneEntity drone = dockedDrone(helper, player, dockPos);
        BlockPos abs = helper.absolutePos(dockPos);
        DroneDockBlockEntity dock = (DroneDockBlockEntity) helper.getBlockEntity(dockPos);
        check(abs.equals(drone.home()) && drone.getUUID().equals(dock.bot()), "Het dock is niet zijn thuis");
        check(drone.isSettled(), "Hij staat niet netjes op het dock");
        check(drone.getName().getString().equals("Drone 1"), "Hij heet niet Drone 1 maar " + drone.getName().getString());
        drone.setProgram(program("""
                done = false
                before = 0
                after = 0
                when started:
                    before = drone.battery
                    charge(60)
                    after = drone.battery
                    done = true
                """));
        drone.setBattery(drone.maxBattery() * 0.5);
        drone.setActive(true);
        BlockPos[] outside = new BlockPos[1];
        boolean[] switchedOff = new boolean[1];
        helper.succeedWhen(() -> {
            if (outside[0] == null && drone.brain().started()) {
                outside[0] = drone.blockPosition(); // waar hij hing toen zijn programma begon (na het opstijgen)
            }
            if (!switchedOff[0]) {
                check(Boolean.TRUE.equals(drone.brain().global("done")), "Nog niet klaar met laden");
                check((Double) drone.brain().global("before") < 55, "Hij begon al vol: " + drone.brain().global("before"));
                check((Double) drone.brain().global("after") >= 59, "Na charge(60) was zijn batterij maar " + drone.brain().global("after"));
                check(outside[0] != null && !outside[0].equals(abs), "Hij steeg niet op van zijn dock");
                check(drone.blockPosition().equals(outside[0]), "Hij vloog niet terug naar waar hij was: hij hangt in "
                        + rel(helper, drone.blockPosition()) + ", was " + rel(helper, outside[0]));
                check(!drone.brain().body().blocked(), "Laden gaf drone.blocked");
                drone.setActive(false);
                switchedOff[0] = true;
            }
            check(drone.isSettled(), "Uitgezet landde hij niet op zijn dock");
            check(BotNetwork.get(helper.getLevel()).get(drone.getUUID()) != null
                    && BotNetwork.get(helper.getLevel()).get(drone.getUUID()).drone(), "BotNetwork kent hem niet als drone");
        });
    }

    /**
     * Een drone die je met het item op een nieuw Drone Dock zet (met een volle warmtegenerator ernaast). Het dock is van
     * jou, zoals wanneer je het zelf neerzet (anders is het van de kern in wiens gebied het staat: in de testwereld
     * kan dat de kern van een andere test zijn).
     */
    private static GuardDroneEntity dockedDrone(GameTestHelper helper, ServerPlayer player, BlockPos dockPos) {
        helper.setBlock(dockPos, ModBlocks.DRONE_DOCK.get());
        ((DroneDockBlockEntity) helper.getBlockEntity(dockPos)).setOwner(player.getUUID());
        helper.setBlock(dockPos.west(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(dockPos.west())).fill(20_000);
        ItemStack stack = GuardDroneItem.create(DroneData.DEFAULT);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(dockPos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.UP, 0.5), Direction.UP, abs, false);
        check(stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "Neerzetten op het dock lukte niet");
        List<GuardDroneEntity> drones = helper.getEntities(ModEntities.GUARD_DRONE.get(), dockPos, 1.0);
        check(drones.size() == 1, "Er staan " + drones.size() + " drones op het dock");
        return drones.get(0);
    }

    /**
     * De schakelaar "Drones" van de kern (hoofdstuk 25): uit = je security drones vliegen naar hun dock en landen erop,
     * en de kern maakt zelf geen drones. In de app Devices staat de drone met zijn batterij.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "drone_switch")
    public static void core_drones_switch_sends_drones_home(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 4), "switch-owner"); // vlak bij de kern
        BlockPos corePos = new BlockPos(2, 3, 2);
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, corePos, owner);
        BlockPos coreAbs = helper.absolutePos(corePos);
        BlockPos rack = new BlockPos(14, 3, 3);
        ImtTechGameTests.placeServer(helper, rack, core);
        GuardDroneEntity drone = dockedDrone(helper, owner, new BlockPos(8, 2, 9));
        helper.runAfterDelay(25, () -> {
            ImtTechGameTests.startServer(helper, rack, core);
            drone.setActive(true);
        });
        helper.runAfterDelay(80, () -> {
            check(drone.isActive() && !drone.isOnDock(), "Aangezet steeg hij niet op van zijn dock");
            SecurityOs.open(owner, coreAbs);
            ImtTechGameTests.osAction(owner, coreAbs, "flag", "name", "drones", "on", "false");
            check(!core.dronesEnabled(), "De schakelaar Drones ging niet uit (de desktop deed niets)");
            check(!drone.isActive(), "De schakelaar Drones zette de drone niet uit");
            boolean listed = false;
            for (Tag element : SecurityDevices.snapshot(helper.getLevel(), core)) {
                CompoundTag device = (CompoundTag) element;
                if (device.getString("name").equals(drone.getName().getString())) {
                    listed = device.contains("bat");
                }
            }
            check(listed, "De app Devices toont de drone (met zijn batterij) niet");
            helper.succeedWhen(() -> {
                check(drone.isSettled(), "Uitgezet landde hij niet op zijn dock");
                int old = helper.getLevel().getEntitiesOfClass(com.senne.imttech.security.SecurityDroneEntity.class,
                        new net.minecraft.world.phys.AABB(coreAbs).inflate(40)).size();
                check(old == 0, "De kern maakte toch " + old + " drones");
            });
        });
    }

    /**
     * Zonder stroom valt hij (hij zakt naar de grond), en de Ion Blaster legt hem neer (zonder schade): zijn programma
     * stopt, en na de tijd staat hij weer op en begint het opnieuw.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void drone_falls_when_down_or_empty(GameTestHelper helper) {
        ServerPlayer shooter = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 2, 2), "drone-shooter");
        GuardDroneEntity downed = drone(helper, new BlockPos(6, 5, 9), "boots = 0\nwhen started:\n    boots = boots + 1\n");
        GuardDroneEntity empty = drone(helper, new BlockPos(10, 5, 9), "when started:\n    pass\n");
        downed.setActive(true);
        empty.setActive(true);
        helper.runAfterDelay(5, () -> {
            check(Double.valueOf(1).equals(downed.brain().global("boots")), "Zijn programma begon niet");
            float before = ImtConfig.Robots.ION_DOWN_SECONDS;
            ImtConfig.Robots.ION_DOWN_SECONDS = 3;
            Entity hit;
            try {
                shooter.lookAt(EntityAnchorArgument.Anchor.EYES, downed.getBoundingBox().getCenter());
                hit = IonBlasterItem.fire(helper.getLevel(), shooter);
            } finally {
                ImtConfig.Robots.ION_DOWN_SECONDS = before;
            }
            check(hit == downed && downed.state() == RobotState.DOWN, "De drone ging niet neer: " + hit + ", " + downed.state());
            check(!downed.brain().started(), "Neer liep zijn programma door");
            empty.setBattery(0);
        });
        helper.runAfterDelay(40, () -> {
            check(downed.getHealth() == downed.getMaxHealth(), "Neervallen verwondde de drone");
            check(downed.getY() < helper.absolutePos(new BlockPos(6, 2, 9)).getY() + 0.1, "Neer viel hij niet op de grond: y = " + downed.getY());
            check(empty.state() == RobotState.NO_POWER && empty.getY() < helper.absolutePos(new BlockPos(10, 2, 9)).getY() + 0.1,
                    "Zonder stroom zakte hij niet naar de grond: " + empty.state() + ", y = " + empty.getY());
        });
        helper.runAfterDelay(5 + 60 + 10, () -> {
            check(downed.state() == RobotState.ACTIVE && downed.brain().started(), "Na 3 seconden stond hij niet weer op: " + downed.state());
            check(Double.valueOf(1).equals(downed.brain().global("boots")), "Opgestaan begon zijn programma niet opnieuw (vers)");
            helper.succeed();
        });
    }

    /**
     * De taal van een droneprogramma: up en down en drone.* mogen daar (en alleen daar), robot.* niet; de woorden van
     * een robot (forward, read, when started) mogen in allebei, en niet op de server.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void drone_program_language(GameTestHelper helper) {
        ScriptParser.parse("when started:\n    up(2)\n    forward(3)\n    if drone.height > 2 and read(1):\n        down(1)\n",
                ProgramKind.DRONE);
        expectProblem("when started:\n    up(2)\n", ProgramKind.ROBOT, "up() only works in a drone program, not in a robot program");
        expectProblem("when started:\n    if drone.battery < 10:\n        charge()\n", ProgramKind.ROBOT,
                "drone.battery only works in a drone program, not in a robot program");
        expectProblem("when started:\n    if robot.battery < 10:\n        charge()\n", ProgramKind.DRONE,
                "robot.battery only works in a robot program, not in a drone program");
        expectProblem("when every(5):\n    forward(3)\n", ProgramKind.SERVER,
                "forward() only works in a robot program or a drone program, not in a server diagram");
        expectProblem("when started:\n    activate(\"Drone 1\")\n", ProgramKind.DRONE,
                "activate() only works in a server diagram or a Training End program, not in a drone program");
        helper.succeed();
    }

    /**
     * Biebia's drones (hoofdstuk 25): een Biebia's Industries Tower van niemand zet op vier van zijn zes hangar-docks een security drone,
     * aan, met Scan, Face Recognition, Laser en Pathfinding, en het programma "Tower patrol".
     */
    @GameTest(template = "biebia_tower", timeoutTicks = 400, batch = "tower_drones")
    public static void biebia_tower_has_biebia_drones(GameTestHelper helper) {
        // Welke drones al eens 2 blokken (plat gemeten) van hun dock vlogen: dan patrouilleren ze echt.
        java.util.Set<java.util.UUID> patrolled = new java.util.HashSet<>();
        helper.onEachTick(() -> {
            for (GuardDroneEntity drone : helper.getLevel().getEntitiesOfClass(GuardDroneEntity.class, helper.getBounds())) {
                if (drone.home() != null && Math.abs(drone.getX() - (drone.home().getX() + 0.5))
                        + Math.abs(drone.getZ() - (drone.home().getZ() + 0.5)) >= 2) {
                    patrolled.add(drone.getUUID());
                }
            }
        });
        helper.runAfterDelay(360, () -> check(patrolled.size() == Math.round(ImtConfig.Security.TOWER_DRONES),
                "Maar " + patrolled.size() + " drones vlogen hun rondje door de hangar"));
        helper.runAfterDelay(380, helper::succeed);
        helper.runAfterDelay(80, () -> {
            List<GuardDroneEntity> drones = helper.getLevel().getEntitiesOfClass(GuardDroneEntity.class, helper.getBounds());
            check(drones.size() == Math.round(ImtConfig.Security.TOWER_DRONES),
                    "In de toren zijn er " + drones.size() + " drones in plaats van " + Math.round(ImtConfig.Security.TOWER_DRONES));
            for (GuardDroneEntity drone : drones) {
                String name = drone.getName().getString();
                check(drone.owner() == null && drone.isActive(), name + " is van iemand, of staat uit");
                check(drone.program() != null && drone.program().name().equals(TowerDrones.PROGRAM_NAME), name + " heeft Biebia's programma niet");
                check(drone.board().has(BotModule.SCAN) && drone.board().has(BotModule.LASER) && drone.board().has(BotModule.PATHFINDING),
                        name + " mist modules: " + drone.board());
                check(drone.home() != null && helper.getLevel().getBlockState(drone.home()).is(ModBlocks.DRONE_DOCK.get()),
                        name + " heeft geen Drone Dock als thuis");
            }
        });
    }

    /**
     * Een drone van Biebia schiet op een vreemde, ook zonder PvP (het is de toren die schiet). Het alarm (iemand hackt de
     * kern) stuurt hem naar de indringer. Na de hack is hij van de hacker, en dan gelden diens regels: zonder PvP
     * schiet hij niet meer op spelers.
     */
    @GameTest(template = ARENA, timeoutTicks = 500, batch = "tower_drones")
    public static void biebia_drone_shoots_strangers_until_hacked(GameTestHelper helper) {
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), null);
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 2, 13), "biebia-stranger");
        GuardDroneEntity drone = drone(helper, new BlockPos(6, 3, 6), "when started:\n    pass\n");
        drone.setBoard(TowerDrones.board());
        drone.setProgram(TowerDrones.program());
        drone.setActive(true);
        boolean[] alarmed = new boolean[1];
        boolean[] came = new boolean[1];
        helper.onEachTick(() -> {
            if (alarmed[0] && drone.position().distanceTo(stranger.position().add(0, 2, 0)) < 1.5) {
                came[0] = true;
            }
        });
        helper.runAfterDelay(80, () -> {
            check(stranger.getHealth() < stranger.getMaxHealth(), "Biebia's drone schoot niet op een vreemde (zonder PvP)");
            stranger.setHealth(stranger.getMaxHealth());
            TowerDrones.alarm(helper.getLevel(), core, stranger);
            alarmed[0] = true;
        });
        helper.runAfterDelay(260, () -> {
            check(came[0], "Na het alarm kwam hij niet naar de indringer (hij hangt op " + drone.position() + ")");
            ServerPlayer hacker = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 2, 3), "biebia-hacker");
            core.setOwner(hacker);
            TowerDrones.handOver(helper.getLevel(), core, hacker);
            check(hacker.getUUID().equals(drone.owner()), "Na de hack is de drone niet van de hacker");
            BotNetwork.Entry entry = BotNetwork.get(helper.getLevel()).get(drone.getUUID());
            check(entry != null && hacker.getUUID().equals(entry.owner()), "BotNetwork weet niet dat hij van de hacker is");
            stranger.setHealth(stranger.getMaxHealth());
        });
        helper.runAfterDelay(380, () -> {
            check(stranger.getHealth() == stranger.getMaxHealth(), "Na de hack schoot hij toch op een speler (zonder PvP)");
            helper.succeed();
        });
    }

    /** Geeft deze code (van deze soort) een fout met deze tekst, bij het lezen of bij de controle? */
    private static void expectProblem(String code, ProgramKind kind, String part) {
        String found;
        try {
            List<ScriptChecker.Problem> problems = ScriptChecker.check(ScriptParser.parse(code, kind), kind);
            found = problems.isEmpty() ? "geen fout" : problems.getFirst().message();
        } catch (LogicException e) {
            found = e.getMessage();
        }
        check(found.contains(part), "Voor:\n" + code + "\nverwachtte ik \"" + part + "\", maar kreeg: " + found);
    }
}
