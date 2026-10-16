package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.bot.BotBoard;
import com.senne.starktech.bot.BotModule;
import com.senne.starktech.bot.BotNetwork;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.logic.LogicServer;
import com.senne.starktech.logic.program.GraphConverter;
import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.logic.program.ProgramKind;
import com.senne.starktech.logic.program.ScriptParser;
import com.senne.starktech.network.OsActionPayload;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.robot.GuardRobotEntity;
import com.senne.starktech.robot.GuardRobotItem;
import com.senne.starktech.robot.RobotData;
import com.senne.starktech.robot.RobotParts;
import com.senne.starktech.robot.Robots;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.os.StarkOs;
import com.senne.starktech.station.ChargerBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
 * Tests voor robotprogramma's (hoofdstuk 23): het brein van een robot (BotBrain) voert zijn programma uit, en
 * zijn lijf (BotMove) loopt blok voor blok, draait, loopt naar een plek, en gaat laden en weer terug.
 */
@GameTestHolder(StarkTech.MODID)
@PrefixGameTestTemplate(false)
public final class BotProgramGameTests {
    private BotProgramGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Een robotprogramma uit deze code (de code moet kloppen). */
    static LogicProgram program(String code) {
        return new LogicProgram("Test", true, GraphConverter.toGraph(ScriptParser.parse(code, ProgramKind.ROBOT), null), code,
                ProgramKind.ROBOT);
    }

    /** Een robot met zijn verstand aan (anders beweegt hij niet), die naar het noorden kijkt, met dit programma. */
    private static GuardRobotEntity robot(GameTestHelper helper, BlockPos pos, String code) {
        GuardRobotEntity robot = helper.spawn(ModEntities.GUARD_ROBOT.get(), pos);
        robot.setParts(RobotParts.DEFAULT);
        robot.setHealth(robot.getMaxHealth());
        robot.brain().body().setFacing(Direction.NORTH);
        robot.setProgram(program(code));
        return robot;
    }

    /**
     * Waar dit is, gezien vanuit de test (zoals {@code absolutePos} het bedoelt; {@code relativePos} van Minecraft
     * spiegelt x en z). In deze arena is y = 1 de vloer: wie loopt, staat op y = 2.
     */
    private static BlockPos rel(GameTestHelper helper, BlockPos abs) {
        return abs.subtract(helper.absolutePos(BlockPos.ZERO));
    }

    /** Staat hij precies in het midden van dit blok (relatief in de test)? */
    private static void checkAt(GameTestHelper helper, GuardRobotEntity robot, BlockPos relative, String what) {
        BlockPos abs = helper.absolutePos(relative);
        check(robot.blockPosition().getX() == abs.getX() && robot.blockPosition().getZ() == abs.getZ(),
                what + ": hij staat op " + rel(helper, robot.blockPosition()) + ", niet op " + relative);
        check(Math.abs(robot.getX() - (abs.getX() + 0.5)) < 0.02 && Math.abs(robot.getZ() - (abs.getZ() + 0.5)) < 0.02,
                what + ": hij staat niet in het midden van het blok (" + robot.getX() + ", " + robot.getZ() + ")");
    }

    /**
     * Een vierkantje: vier keer {@code forward(2)} en {@code turn_right()}. Na de eerste stap staat hij precies 2
     * blokken noordelijker en kijkt hij daarna naar het oosten; aan het eind staat hij weer precies waar hij
     * begon, met zijn gezicht weer naar het noorden.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void robot_program_walks_a_square(GameTestHelper helper) {
        BlockPos start = new BlockPos(4, 2, 6);
        GuardRobotEntity robot = robot(helper, start, """
                done = false
                firstZ = 0
                turned = ""
                when started:
                    forward(2)
                    firstZ = robot.z
                    turn_right()
                    turned = robot.facing
                    repeat(3):
                        forward(2)
                        turn_right()
                    done = true
                """);
        int startZ = helper.absolutePos(start).getZ();
        robot.setActive(true);
        helper.succeedWhen(() -> {
            check(Boolean.TRUE.equals(robot.brain().global("done")), "Het vierkantje is nog niet klaar");
            check(Double.valueOf(startZ - 2).equals(robot.brain().global("firstZ")),
                    "Na forward(2) stond hij niet 2 blokken noordelijker: z = " + robot.brain().global("firstZ") + ", begon op " + startZ);
            check("east".equals(robot.brain().global("turned")), "Na turn_right() keek hij niet naar het oosten maar "
                    + robot.brain().global("turned"));
            checkAt(helper, robot, start, "Na het vierkantje");
            check(Math.abs(Mth.wrapDegrees(robot.getYRot() - Direction.NORTH.toYRot())) < 1, "Hij kijkt niet weer naar het noorden");
            check(robot.brain().idle(), "Zijn programma is klaar, maar er is nog iets bezig");
        });
    }

    /**
     * Een muur van twee hoog: {@code forward(3)} komt één blok ver, dan is {@code robot.blocked} waar en staat
     * hij in het midden van het blok vóór de muur.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void robot_program_blocked_by_a_wall(GameTestHelper helper) {
        BlockPos start = new BlockPos(4, 2, 6);
        helper.setBlock(start.north(2), Blocks.STONE);
        helper.setBlock(start.north(2).above(), Blocks.STONE);
        GuardRobotEntity robot = robot(helper, start, """
                hit = false
                done = false
                when started:
                    forward(3)
                    hit = robot.blocked
                    done = true
                """);
        robot.setActive(true);
        helper.succeedWhen(() -> {
            check(Boolean.TRUE.equals(robot.brain().global("done")), "Nog niet klaar");
            check(Boolean.TRUE.equals(robot.brain().global("hit")), "Tegen de muur was robot.blocked niet waar");
            checkAt(helper, robot, start.north(), "Voor de muur");
        });
    }

    /**
     * {@code goto} werkt alleen met de Pathfinding-module: zonder blijft hij staan (robot.blocked), met loopt hij
     * er precies heen.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void robot_program_goto_needs_pathfinding(GameTestHelper helper) {
        String code = """
                hit = false
                done = false
                when started:
                    goto(robot.x + 3, robot.y, robot.z - 1)
                    hit = robot.blocked
                    done = true
                """;
        BlockPos plainStart = new BlockPos(3, 2, 3);
        BlockPos smartStart = new BlockPos(3, 2, 9);
        GuardRobotEntity plain = robot(helper, plainStart, code);
        GuardRobotEntity smart = robot(helper, smartStart, code);
        smart.setBoard(BotBoard.EMPTY.with(4, BotModule.PATHFINDING.id()));
        plain.setActive(true);
        smart.setActive(true);
        helper.succeedWhen(() -> {
            check(Boolean.TRUE.equals(plain.brain().global("done")) && Boolean.TRUE.equals(smart.brain().global("done")), "Nog niet klaar");
            check(Boolean.TRUE.equals(plain.brain().global("hit")), "Zonder Pathfinding-module was robot.blocked niet waar");
            checkAt(helper, plain, plainStart, "Zonder Pathfinding-module");
            check(Boolean.FALSE.equals(smart.brain().global("hit")), "Met de module was hij toch geblokkeerd");
            checkAt(helper, smart, smartStart.east(3).north(), "Met de Pathfinding-module");
        });
    }

    /**
     * Een lus zonder wachten laat de wereld niet hangen: elke when doet hooguit zijn budget per tick, en een
     * andere when ({@code every(1)}) loopt ondertussen gewoon door.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void robot_program_endless_loop_is_safe(GameTestHelper helper) {
        GuardRobotEntity robot = robot(helper, new BlockPos(4, 2, 4), """
                count = 0
                ticks = 0
                when started:
                    forever:
                        count = count + 1
                when every(1):
                    ticks = ticks + 1
                """);
        robot.setActive(true);
        helper.runAfterDelay(55, () -> {
            double count = (Double) robot.brain().global("count");
            double ticks = (Double) robot.brain().global("ticks");
            check(count > 100, "De lus deed bijna niets: " + count);
            check(count <= 55 * StarkConfig.Robots.STEPS_PER_TICK, "De lus deed meer dan zijn budget: " + count);
            check(ticks >= 2, "every(1) liep niet naast de lus: " + ticks);
            check(robot.brain().running() == 1, "Er zijn " + robot.brain().running() + " whens bezig (de lus hoort te blijven)");
            helper.succeed();
        });
    }

    /**
     * {@code charge(60)}: hij loopt naar zijn dock, laadt tot 60%, stapt eruit en loopt terug naar waar hij was,
     * met zijn gezicht dezelfde kant op. Daarna gaat zijn programma verder.
     */
    @GameTest(template = ARENA, timeoutTicks = 900)
    public static void robot_program_charges_and_comes_back(GameTestHelper helper) {
        ServerPlayer player = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 14), "charge-owner");
        BlockPos dock = new BlockPos(6, 2, 9);
        helper.setBlock(dock.west(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(dock.west())).fill(20_000);
        GuardRobotEntity robot = dockedRobot(helper, player, dock);
        robot.setProgram(program("""
                done = false
                before = 0
                after = 0
                where = ""
                when started:
                    before = robot.battery
                    where = robot.facing
                    charge(60)
                    after = robot.battery
                    done = true
                """));
        robot.setBattery(robot.maxBattery() * 0.5);
        robot.setActive(true);
        BlockPos[] outside = new BlockPos[1];
        helper.succeedWhen(() -> {
            if (outside[0] == null && robot.brain().started()) {
                outside[0] = robot.blockPosition(); // waar hij stond toen zijn programma begon (na het uitstappen)
            }
            check(Boolean.TRUE.equals(robot.brain().global("done")), "Nog niet klaar met laden");
            check((Double) robot.brain().global("before") < 55, "Hij begon al vol: " + robot.brain().global("before"));
            check((Double) robot.brain().global("after") >= 59, "Na charge(60) was zijn batterij maar " + robot.brain().global("after"));
            check(outside[0] != null && robot.blockPosition().equals(outside[0]), "Hij liep niet terug naar waar hij was: hij staat op "
                    + rel(helper, robot.blockPosition()) + (outside[0] == null ? "" : ", was " + rel(helper, outside[0])));
            Direction facing = Direction.byName((String) robot.brain().global("where"));
            check(facing != null && robot.brain().body().facing() == facing, "Hij kijkt niet meer dezelfde kant op");
            check(!robot.brain().body().blocked(), "Laden gaf robot.blocked");
        });
    }

    /** {@code stop()}: hij zet zichzelf uit en loopt naar zijn dock. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void robot_program_stop_switches_it_off(GameTestHelper helper) {
        ServerPlayer player = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 14), "stop-owner");
        BlockPos dock = new BlockPos(6, 2, 9);
        GuardRobotEntity robot = dockedRobot(helper, player, dock);
        robot.setProgram(program("""
                when started:
                    forward(1)
                    stop()
                    forward(5)
                """));
        robot.setActive(true);
        helper.runAfterDelay(30, () -> check(robot.isActive(), "Hij ging te vroeg uit"));
        helper.runAfterDelay(60, () -> helper.succeedWhen(() -> {
            check(!robot.isActive(), "stop() zette hem niet uit");
            check(robot.isOnDock(), "Uitgezet liep hij niet terug naar zijn dock");
        }));
    }

    /**
     * Uit en weer aan: zijn programma begint opnieuw bij when started, met verse namen (zoals een computer die
     * opnieuw opstart). Zijn programma en de kant waar hij naar kijkt, blijven bewaard als de wereld opslaat.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void robot_program_restarts_fresh(GameTestHelper helper) {
        GuardRobotEntity robot = robot(helper, new BlockPos(4, 2, 4), """
                boots = 0
                when started:
                    boots = boots + 1
                """);
        robot.brain().body().setFacing(Direction.WEST);
        robot.setActive(true);
        helper.runAfterDelay(5, () -> {
            check(Double.valueOf(1).equals(robot.brain().global("boots")), "when started liep niet één keer: " + robot.brain().global("boots"));
            robot.setActive(false);
        });
        helper.runAfterDelay(8, () -> {
            check(!robot.brain().started(), "Uitgezet bleef zijn programma bezig");
            robot.setActive(true);
        });
        helper.runAfterDelay(14, () -> {
            check(Double.valueOf(1).equals(robot.brain().global("boots")), "Opnieuw aan begon hij niet met verse namen: "
                    + robot.brain().global("boots"));
            CompoundTag tag = new CompoundTag();
            robot.saveWithoutId(tag);
            GuardRobotEntity copy = ModEntities.GUARD_ROBOT.get().create(helper.getLevel());
            copy.load(tag);
            check(copy.program() != null && copy.program().source().equals(robot.program().source())
                    && copy.program().kind() == ProgramKind.ROBOT, "Zijn programma werd niet bewaard");
            check(copy.brain().body().facing() == Direction.WEST, "Hij vergat welke kant hij op keek: " + copy.brain().body().facing());
            helper.succeed();
        });
    }

    // ================================================================ Stap 4: programma's naar de robot

    /**
     * De app Robots stuurt een robotprogramma naar een robot (een serverdiagram kan niet). De robot bewaart zijn eigen
     * kopie: verander je het programma op de server, dan houdt hij de oude (en zegt de app "older"), en opgepakt zit
     * het in zijn item. Zonder werkende server heeft hij geen verbinding: dan lukt het niet. Een robot die niet
     * geladen is, krijgt zijn programma als hij terug is.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "robot_upload")
    public static void robot_program_upload_from_the_desktop(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "upload-owner");
        BlockPos corePos = new BlockPos(2, 3, 2);
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, corePos, owner);
        BlockPos coreAbs = helper.absolutePos(corePos);
        BlockPos rack = new BlockPos(12, 3, 12);
        StarkTechGameTests.placeServer(helper, rack, core);
        GuardRobotEntity robot = helper.spawn(ModEntities.GUARD_ROBOT.get(), new BlockPos(8, 2, 8));
        robot.setOwner(owner);
        String patrol = "when started:\n    forward(1)\n";
        helper.runAfterDelay(25, () -> {
            StarkTechGameTests.startServer(helper, rack, core);
            StarkOs.open(owner, coreAbs);
            saveProgram(owner, coreAbs, "", "Patrol", patrol, ProgramKind.ROBOT);
            saveProgram(owner, coreAbs, "", "Doors", "when every(5):\n    log(\"x\")\n", ProgramKind.SERVER);
            check(core.logic().program("Patrol") != null && core.logic().program("Doors") != null, "De programma's werden niet bewaard");

            Robots.Desktop wrong = upload(owner, core, robot, "Doors");
            check(wrong.error() && wrong.text().contains("not a robot program"), "Een serverdiagram naar een robot: " + wrong.text());
            Robots.Desktop ok = upload(owner, core, robot, "Patrol");
            check(!ok.error() && robot.program() != null && robot.program().name().equals("Patrol"), "Uploaden lukte niet: " + ok.text());
            CompoundTag row = row(helper, core, robot);
            check(row.getString("prog").equals("Patrol") && !row.getBoolean("old") && row.getBoolean("link"),
                    "De app Robots ziet het programma niet goed: " + row);

            // Op de server veranderd: de robot houdt zijn kopie, de app zegt dat die ouder is.
            saveProgram(owner, coreAbs, "Patrol", "Patrol", "when started:\n    forward(2)\n", ProgramKind.ROBOT);
            check(robot.program().source().equals(patrol), "De robot kreeg vanzelf de nieuwe versie");
            check(row(helper, core, robot).getBoolean("old"), "De app zegt niet dat zijn kopie ouder is");

            // Opgepakt: zijn programma zit in het item.
            RobotData data = robot.toData();
            GuardRobotEntity copy = ModEntities.GUARD_ROBOT.get().create(helper.getLevel());
            copy.applyData(data);
            check(copy.program() != null && copy.program().source().equals(patrol), "Het programma zat niet in zijn item");

            // Zonder werkende server: geen verbinding.
            helper.setBlock(rack, Blocks.AIR);
            LogicServer.forgetAll();
            Robots.Desktop offline = upload(owner, core, robot, "");
            check(offline.error() && offline.text().contains("no connection") && robot.program() != null,
                    "Zonder server kon hij toch een programma krijgen: " + offline.text());
            check(!row(helper, core, robot).getBoolean("link"), "Zonder server zegt de app dat hij verbinding heeft");
            StarkTechGameTests.placeServer(helper, rack, core);
            StarkTechGameTests.startServer(helper, rack, core);

            // Ver weg (niet geladen): het wacht in BotNetwork tot hij terug is.
            CompoundTag saved = new CompoundTag();
            robot.saveWithoutId(saved);
            robot.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            Robots.Desktop far = upload(owner, core, robot, "");
            check(!far.error() && far.text().contains("far away"), "Voor een robot ver weg: " + far.text());
            check(BotNetwork.get(helper.getLevel()).pending(robot.getUUID()) != null, "Er wacht niets op de robot");
            GuardRobotEntity back = ModEntities.GUARD_ROBOT.get().create(helper.getLevel());
            back.load(saved);
            helper.getLevel().addFreshEntity(back);
            check(back.program() != null, "Terug had hij zijn eigen programma niet meer");
            helper.runAfterDelay(25, () -> {
                check(back.program() == null, "Terug kreeg hij het wachtende programma (geen) niet");
                check(BotNetwork.get(helper.getLevel()).pending(back.getUUID()) == null, "Het programma bleef wachten");
                helper.succeed();
            });
        });
    }

    /** Een programma bewaren zoals de app Logic dat doet. */
    private static void saveProgram(ServerPlayer owner, BlockPos coreAbs, String old, String name, String code, ProgramKind kind) {
        CompoundTag args = new CompoundTag();
        args.putString("old", old);
        args.putString("name", name);
        args.put("graph", GraphConverter.toGraph(ScriptParser.parse(code, kind), null).save());
        args.putString("source", code);
        args.putString("kind", kind.name());
        StarkOs.handleAction(owner, new OsActionPayload(coreAbs, "logic_save", args));
    }

    /** De knop Program in de app Robots, met dit programma ("" = geen). */
    private static Robots.Desktop upload(ServerPlayer owner, SecurityCoreBlockEntity core, GuardRobotEntity robot, String program) {
        CompoundTag args = new CompoundTag();
        args.putString("id", robot.getUUID().toString());
        args.putString("program", program);
        return Robots.desktop(owner, core, "robot_program", args);
    }

    /** De regel van deze robot in de app Robots. */
    private static CompoundTag row(GameTestHelper helper, SecurityCoreBlockEntity core, GuardRobotEntity robot) {
        for (Tag element : Robots.snapshot(helper.getLevel(), core)) {
            CompoundTag row = (CompoundTag) element;
            if (row.getString("id").equals(robot.getUUID().toString())) {
                return row;
            }
        }
        throw new GameTestAssertException("De robot staat niet in de app Robots");
    }

    /** Een robot die je met het item op een dock zet (gekoppeld aan dat dock). */
    private static GuardRobotEntity dockedRobot(GameTestHelper helper, ServerPlayer player, BlockPos dock) {
        helper.setBlock(dock, ModBlocks.ROBOT_DOCK.get());
        ItemStack stack = GuardRobotItem.create(new RobotData(RobotParts.DEFAULT, BotBoard.EMPTY, ""));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(dock);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.UP, 0.5), Direction.UP, abs, false);
        check(stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "Neerzetten op het dock lukte niet");
        List<GuardRobotEntity> robots = helper.getEntities(ModEntities.GUARD_ROBOT.get(), dock, 1.0);
        check(robots.size() == 1, "Er staan " + robots.size() + " robots op het dock");
        return robots.get(0);
    }
}
