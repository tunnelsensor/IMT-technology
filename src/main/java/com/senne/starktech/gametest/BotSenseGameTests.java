package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.bot.BotBoard;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.robot.GuardRobotEntity;
import com.senne.starktech.robot.RobotParts;
import com.senne.starktech.robot.RobotStyle;
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
}
