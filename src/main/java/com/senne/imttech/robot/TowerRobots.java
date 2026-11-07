package com.senne.imttech.robot;

import com.senne.imttech.bot.BotBoard;
import com.senne.imttech.bot.BotModule;
import com.senne.imttech.bot.BotNetwork;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * De bewakersrobots van Biebia in een Biebia's Industries Tower (hoofdstuk 60, stap 7, jouw antwoord 6: "in de toren
 * zijn het robots en drones van Biebia met een programma"). Zoals TowerDrones voor de hangar: een kern van niemand zet
 * één keer een guard robot op de Robot Docks van de toren (twee in de lobby bij de lift, twee op de
 * beveiligingsverdieping voor de schermenwand), met Scan, Face Recognition, Laser en Pathfinding, in de squad
 * {@link #SQUAD} en met het programma <b>Tower guard</b> (hieronder): rondjes lopen, gaan kijken bij lawaai, "contact"
 * melden aan Biebia's kern als hij iemand heeft (die geeft de hele squad en de drones de opdracht "alarm", zie
 * TowerGuards.SECURITY_SOURCE), "man down" bij een lichaam. Ze zijn van niemand (iedereen is een vreemde, ze schieten
 * ook zonder PvP), en wie de toren hackt, krijgt ze (TowerHack). Een robot die kapot gaat, komt niet terug.
 */
public final class TowerRobots {
    private TowerRobots() {}

    /** Hoe Biebia zijn programma noemde (zo staat het in de app Robots). */
    public static final String PROGRAM_NAME = "Tower guard";
    /** De squad van de bewakersrobots (de drones heten "Tower drones"). */
    public static final String SQUAD = "Tower guards";

    /** Biebia's programma: zoals een bewaker in Hitman, in zijn eigen taal. Hack de toren en lees het in de app Logic. */
    public static final String PROGRAM = """
            # Biebia's guards: patrol the floor, go and look when you hear something,
            # call the others when you have someone, and report a body.
            facePin = pin(2)
            laserPin = pin(3)

            when started:
                write(facePin, "strangers")
                while true:
                    if robot.battery < 20:
                        charge()
                    forward(4)
                    if robot.blocked:
                        turn_right()
                    turn_right()
                    wait(2)

            when heard:
                goto(sound.x, sound.y, sound.z)
                wait(4)

            when spotted:
                send("contact")
                aim(target.x, target.y, target.z)
                write(laserPin, true)
                wait(3)
                aim()

            when body_seen:
                send("man down")
                goto(body.x, body.y, body.z)
                wait(3)

            when ordered("alarm"):
                write(laserPin, true)
                goto(order.x, order.y, order.z)
            """;

    /** Zo ver (blokken) van de kern zoeken we docks: de lobby ligt ruim vijftig blokken onder de beveiligingsverdieping. */
    private static final int REACH_SIDE = 40;
    private static final int REACH_DOWN = 80;
    private static final int REACH_UP = 16;

    /** Wat Biebia op hun printplaat soldeerde (zoals bij de drones): Scan 1, Face Recognition 2, Laser 3, Pathfinding 4. */
    public static BotBoard board() {
        return BotBoard.EMPTY.with(1, BotModule.SCAN.id()).with(2, BotModule.FACE_RECOGNITION.id())
                .with(3, BotModule.LASER.id()).with(4, BotModule.PATHFINDING.id());
    }

    /** Biebia's programma, als robotprogramma. */
    public static LogicProgram program() {
        return new LogicProgram(PROGRAM_NAME, true, GraphConverter.toGraph(ScriptParser.parse(PROGRAM, ProgramKind.ROBOT), null),
                PROGRAM, ProgramKind.ROBOT);
    }

    /**
     * Zet Biebia's robots op de lege Robot Docks rond deze kern (van onder naar boven), hooguit {@code towerRobots}.
     * Alleen docks in een stuk wereld dat nu geladen is. Geeft hoeveel robots er kwamen.
     */
    public static int fill(ServerLevel level, SecurityCoreBlockEntity core) {
        BlockPos center = core.getBlockPos();
        List<RobotDockBlockEntity> docks = new ArrayList<>();
        for (int cx = (center.getX() - REACH_SIDE) >> 4; cx <= (center.getX() + REACH_SIDE) >> 4; cx++) {
            for (int cz = (center.getZ() - REACH_SIDE) >> 4; cz <= (center.getZ() + REACH_SIDE) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    BlockPos pos = entity.getBlockPos();
                    if (entity instanceof RobotDockBlockEntity dock && dock.robot() == null
                            && Math.abs(pos.getX() - center.getX()) <= REACH_SIDE && Math.abs(pos.getZ() - center.getZ()) <= REACH_SIDE
                            && pos.getY() >= center.getY() - REACH_DOWN && pos.getY() <= center.getY() + REACH_UP
                            && SecurityCores.coreAt(level, pos) == core) {
                        docks.add(dock);
                    }
                }
            }
        }
        docks.sort(Comparator.comparingInt((RobotDockBlockEntity dock) -> dock.getBlockPos().getY())
                .thenComparingInt(dock -> dock.getBlockPos().getZ()).thenComparingInt(dock -> dock.getBlockPos().getX()));
        int wanted = Math.round(ImtConfig.Security.TOWER_ROBOTS);
        int placed = 0;
        for (RobotDockBlockEntity dock : docks) {
            if (placed >= wanted) {
                break;
            }
            if (place(level, dock) != null) {
                placed++;
            }
        }
        return placed;
    }

    /** Eén robot van Biebia op dit dock: aan, met een volle batterij, zijn printplaat, zijn squad en zijn programma. */
    private static GuardRobotEntity place(ServerLevel level, RobotDockBlockEntity dock) {
        GuardRobotEntity robot = ModEntities.GUARD_ROBOT.get().create(level);
        if (robot == null) {
            return null;
        }
        BlockPos pos = dock.getBlockPos();
        BlockState state = level.getBlockState(pos);
        Direction facing = state.getBlock() instanceof RobotDockBlock ? state.getValue(RobotDockBlock.FACING) : Direction.SOUTH;
        robot.moveTo(pos.getX() + 0.5, pos.getY() + 3.0 / 16.0, pos.getZ() + 0.5, facing.toYRot(), 0);
        robot.setYHeadRot(facing.toYRot());
        robot.yBodyRot = facing.toYRot();
        robot.brain().body().setFacing(facing);
        robot.setParts(RobotParts.DEFAULT);
        robot.setBoard(board());
        robot.setProgram(program());
        robot.setSquad(SQUAD);
        robot.setBattery(robot.maxBattery());
        BotNetwork network = BotNetwork.get(level);
        int number = 1;
        while (network.nameTaken(null, "Tower guard " + number, null)) {
            number++;
        }
        robot.setCustomName(Component.literal("Tower guard " + number));
        robot.setHome(pos);
        dock.setRobot(robot.getUUID());
        level.addFreshEntity(robot);
        robot.setActive(true);
        network.update(robot);
        return robot;
    }

    /** Biebia's robots in het gebied van deze kern (die van niemand zijn). */
    private static List<GuardRobotEntity> of(ServerLevel level, SecurityCoreBlockEntity core) {
        return level.getEntitiesOfClass(GuardRobotEntity.class, core.area(),
                robot -> robot.isAlive() && robot.owner() == null && SecurityCores.coreAt(level, robot.blockPosition()) == core);
    }

    /** Het alarm: iemand begint de kern te hacken. Biebia's robots krijgen de opdracht "alarm", met zijn plek. */
    public static void alarm(ServerLevel level, SecurityCoreBlockEntity core, ServerPlayer intruder) {
        for (GuardRobotEntity robot : of(level, core)) {
            robot.brain().order("alarm", intruder.blockPosition());
        }
    }

    /** De toren is gehackt: Biebia's robots zijn nu van de hacker (zoals de drones, zie TowerDrones.handOver). */
    public static void handOver(ServerLevel level, SecurityCoreBlockEntity core, ServerPlayer player) {
        BotNetwork network = BotNetwork.get(level);
        for (GuardRobotEntity robot : of(level, core)) {
            robot.setOwner(player);
            if (network.nameTaken(player.getUUID(), robot.getName().getString(), robot.getUUID())) {
                robot.setCustomName(Component.literal(network.freeName(player.getUUID(), ProgramKind.ROBOT)));
            }
            network.update(robot);
        }
    }
}
