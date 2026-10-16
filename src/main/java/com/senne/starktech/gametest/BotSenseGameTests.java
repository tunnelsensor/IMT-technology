package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.bot.BotBoard;
import com.senne.starktech.logic.LogicServer;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.robot.GuardRobotEntity;
import com.senne.starktech.robot.RobotParts;
import com.senne.starktech.robot.RobotStyle;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de zintuigen en wapens van een robot (hoofdstuk 24): wat de modules op zijn pinnen zeggen
 * ({@code read}) en doen ({@code write}).
 */
@GameTestHolder(StarkTech.MODID)
@PrefixGameTestTemplate(false)
public final class BotSenseGameTests {
    private BotSenseGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Een robot met zijn verstand aan, die naar het noorden kijkt, met dit hoofd, deze modules en dit programma. */
    static GuardRobotEntity robot(GameTestHelper helper, BlockPos pos, RobotStyle head, BotBoard board, String code) {
        GuardRobotEntity robot = helper.spawn(ModEntities.GUARD_ROBOT.get(), pos);
        robot.setParts(RobotParts.DEFAULT.with(com.senne.starktech.robot.RobotSlot.HEAD, head));
        robot.setHealth(robot.getMaxHealth());
        robot.setBoard(board);
        robot.brain().body().setFacing(Direction.NORTH);
        robot.setProgram(BotProgramGameTests.program(code));
        robot.setActive(true);
        return robot;
    }

    /** Kijkt elke paar ticks wat zijn Scan (pin 1) en Distance (pin 2) zeggen. */
    private static final String LOOK = """
            seen = false
            far = 0
            when started:
                forever:
                    seen = read(pin(1))
                    far = read(pin(2))
                    wait(0.1)
            """;

    /**
     * Scan en Distance: een speler 5 blokken voor hem ziet hij (afstand 5); een muur ertussen en hij ziet hem niet meer
     * (afstand 3, de muur). Een speler achter hem ziet een Commando-hoofd (120 graden) niet, een Trainer-hoofd
     * (rondom) wel. Een lege pin zegt false.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void robot_scan_sees_players_in_sight(GameTestHelper helper) {
        BotBoard board = BotBoard.EMPTY.with(1, "scan").with(2, "distance");
        GuardRobotEntity commando = robot(helper, new BlockPos(6, 2, 12), RobotStyle.COMMANDO, board, LOOK);
        GuardRobotEntity trainer = robot(helper, new BlockPos(14, 2, 12), RobotStyle.TRAINER, board, LOOK);
        ServerPlayer front = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 2, 7), "scan-front");
        helper.runAfterDelay(10, () -> {
            check(Boolean.TRUE.equals(commando.brain().global("seen")), "De scan zag de speler voor hem niet");
            check(Double.valueOf(5).equals(commando.brain().global("far")), "De afstand tot de speler was niet 5 maar "
                    + commando.brain().global("far"));
            check(Boolean.FALSE.equals(commando.brain().pins().read(3)), "Een lege pin zei geen false");
            // Een muur ertussen (twee hoog), 3 blokken voor hem.
            helper.setBlock(new BlockPos(6, 2, 9), Blocks.STONE);
            helper.setBlock(new BlockPos(6, 3, 9), Blocks.STONE);
        });
        helper.runAfterDelay(20, () -> {
            check(Boolean.FALSE.equals(commando.brain().global("seen")), "De scan zag door de muur");
            check(Double.valueOf(3).equals(commando.brain().global("far")), "De afstand tot de muur was niet 3 maar "
                    + commando.brain().global("far"));
            // De speler achter de robots.
            front.teleportTo(helper.absolutePos(new BlockPos(10, 2, 16)).getX() + 0.5, front.getY(),
                    helper.absolutePos(new BlockPos(10, 2, 16)).getZ() + 0.5);
        });
        helper.runAfterDelay(30, () -> {
            check(Boolean.FALSE.equals(commando.brain().global("seen")), "Een Commando-hoofd zag iemand achter hem");
            check(Boolean.TRUE.equals(trainer.brain().global("seen")), "Een Trainer-hoofd (rondom) zag de speler achter hem niet");
            helper.succeed();
        });
    }

    /** Kijkt met een filter op zijn Face Recognition (pin 2): ziet zijn Scan (pin 1) iemand, en wie? */
    private static String faceProgram(String filter) {
        return "seen = false\nwho = \"\"\nwhen started:\n    write(pin(2), \"" + filter + "\")\n    forever:\n"
                + "        seen = read(pin(1))\n        who = read(pin(2))\n        wait(0.1)\n";
    }

    /**
     * Face Recognition: met "strangers" ziet hij de vreemde en niet de eigenaar, met "trusted" juist de eigenaar,
     * met een naam alleen die speler. Wie een vriend is, vraagt hij aan de server: zonder werkende server ziet hij met
     * "strangers" niemand meer, maar een naam herkent hij nog wel.
     */
    @GameTest(template = ARENA, timeoutTicks = 160, batch = "robot_face")
    public static void robot_face_recognition_filters(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 2, 7), "face-owner");
        ServerPlayer stranger = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(10, 2, 6), "face-stranger");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos rack = new BlockPos(12, 3, 2);
        StarkTechGameTests.placeServer(helper, rack, core);
        BotBoard board = BotBoard.EMPTY.with(1, "scan").with(2, "face_recognition");
        GuardRobotEntity strangers = robot(helper, new BlockPos(6, 2, 12), RobotStyle.COMMANDO, board, faceProgram("strangers"));
        GuardRobotEntity trusted = robot(helper, new BlockPos(9, 2, 12), RobotStyle.COMMANDO, board, faceProgram("trusted"));
        GuardRobotEntity named = robot(helper, new BlockPos(12, 2, 12), RobotStyle.COMMANDO, board, faceProgram("Face-Stranger"));
        for (GuardRobotEntity robot : java.util.List.of(strangers, trusted, named)) {
            robot.setOwner(owner);
        }
        helper.runAfterDelay(2, () -> StarkTechGameTests.startServer(helper, rack, core));
        helper.runAfterDelay(20, () -> {
            check("face-stranger".equals(strangers.brain().global("who")) && Boolean.TRUE.equals(strangers.brain().global("seen")),
                    "Met \"strangers\" zag hij " + strangers.brain().global("who"));
            check("face-owner".equals(trusted.brain().global("who")), "Met \"trusted\" zag hij " + trusted.brain().global("who"));
            check("face-stranger".equals(named.brain().global("who")), "Met een naam zag hij " + named.brain().global("who"));
            check(strangers.brain().pins().filter().equals("strangers"), "Het filter is " + strangers.brain().pins().filter());
            // Geen werkende server meer: geen verbinding.
            helper.setBlock(rack, Blocks.AIR);
            LogicServer.forgetAll();
        });
        helper.runAfterDelay(35, () -> {
            check(Boolean.FALSE.equals(strangers.brain().global("seen")) && "".equals(strangers.brain().global("who")),
                    "Zonder verbinding zag hij met \"strangers\" toch " + strangers.brain().global("who"));
            check("face-stranger".equals(named.brain().global("who")), "Zonder verbinding herkende hij de naam niet meer");
            helper.succeed();
        });
    }
}
