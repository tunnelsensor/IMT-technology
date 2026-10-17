package com.senne.imttech.robot;

import com.senne.imttech.arsenal.DroneDockBlock;
import com.senne.imttech.arsenal.DroneDockBlockEntity;
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
 * De drones van Biebia in een Biebia's Industries Tower (hoofdstuk 25, jouw antwoord van 14 oktober: "de Biebia's Industries Tower krijgt docks
 * met drones die Biebia al geprogrammeerd heeft"). Een kern van niemand (Biebia's systeem) zet één keer een security
 * drone op de eerste {@code TOWER_DRONES} Drone Docks van de toren (die staan in de hangar): met Scan, Face
 * Recognition, Laser en Pathfinding gesoldeerd, en het programma "Tower patrol" (hieronder).
 * <ul>
 *   <li>Ze zijn van niemand: voor hen is iedereen een vreemde, dus ze schieten op elke speler die ze zien (niet in
 *       creative). Ook zonder PvP: het is de toren die schiet, niet een speler (zie BotShot).</li>
 *   <li>Begint iemand de kern te hacken (het alarm), dan krijgen ze de opdracht "alarm" met zijn plek: ze vliegen
 *       ernaartoe, als ze er kunnen komen.</li>
 *   <li>Wie de toren hackt, krijgt ze: dan zijn ze van hem (zie TowerHack), met zijn regels voor vrienden.</li>
 *   <li>Een drone die kapot gaat, komt niet terug (de kern maakt geen gratis drones meer).</li>
 * </ul>
 */
public final class TowerDrones {
    private TowerDrones() {}

    /** Hoe Biebia zijn programma noemde (zo staat het in de app Robots). */
    public static final String PROGRAM_NAME = "Tower patrol";

    /** Biebia's programma: de hangar rondvliegen, op vreemden schieten, en komen kijken als het alarm gaat. */
    public static final String PROGRAM = """
            # Biebia's drones: patrol the hangar, shoot strangers, and come when the alarm goes off.
            scanPin = pin(1)
            facePin = pin(2)
            laserPin = pin(3)

            when started:
                write(facePin, "strangers")
                up(2)
                forever:
                    if drone.battery < 25:
                        charge()
                    forward(3)
                    turn_right()

            when every(1):
                if read(scanPin) and read(laserPin):
                    write(laserPin, true)

            when ordered("alarm"):
                goto(order.x, order.y + 2, order.z)
            """;

    /** Zo ver (blokken) van de kern zoeken we docks: de toren is 41 breed, en de hangar ligt 22 blokken lager. */
    private static final int REACH_SIDE = 40;
    private static final int REACH_DOWN = 40;
    private static final int REACH_UP = 16;

    /** Wat Biebia op hun printplaat soldeerde: Scan op pin 1, Face Recognition op 2, Laser op 3, Pathfinding op 4. */
    public static BotBoard board() {
        return BotBoard.EMPTY.with(1, BotModule.SCAN.id()).with(2, BotModule.FACE_RECOGNITION.id())
                .with(3, BotModule.LASER.id()).with(4, BotModule.PATHFINDING.id());
    }

    /** Biebia's programma, als droneprogramma. */
    public static LogicProgram program() {
        return new LogicProgram(PROGRAM_NAME, true, GraphConverter.toGraph(ScriptParser.parse(PROGRAM, ProgramKind.DRONE), null),
                PROGRAM, ProgramKind.DRONE);
    }

    /**
     * Zet Biebia's drones op de lege Drone Docks rond deze kern (van west naar oost, van noord naar zuid), hooguit
     * {@code TOWER_DRONES}. De kern doet dit één keer (zie SecurityCoreBlockEntity). Alleen docks in een stuk wereld
     * dat nu geladen is. Geeft hoeveel drones er kwamen.
     */
    public static int fill(ServerLevel level, SecurityCoreBlockEntity core) {
        BlockPos center = core.getBlockPos();
        List<DroneDockBlockEntity> docks = new ArrayList<>();
        for (int cx = (center.getX() - REACH_SIDE) >> 4; cx <= (center.getX() + REACH_SIDE) >> 4; cx++) {
            for (int cz = (center.getZ() - REACH_SIDE) >> 4; cz <= (center.getZ() + REACH_SIDE) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    BlockPos pos = entity.getBlockPos();
                    if (entity instanceof DroneDockBlockEntity dock && dock.bot() == null
                            && Math.abs(pos.getX() - center.getX()) <= REACH_SIDE && Math.abs(pos.getZ() - center.getZ()) <= REACH_SIDE
                            && pos.getY() >= center.getY() - REACH_DOWN && pos.getY() <= center.getY() + REACH_UP
                            && SecurityCores.coreAt(level, pos) == core) { // alleen de docks van deze toren
                        docks.add(dock);
                    }
                }
            }
        }
        docks.sort(Comparator.comparingInt((DroneDockBlockEntity dock) -> dock.getBlockPos().getY())
                .thenComparingInt(dock -> dock.getBlockPos().getZ()).thenComparingInt(dock -> dock.getBlockPos().getX()));
        int wanted = Math.round(ImtConfig.Security.TOWER_DRONES);
        int placed = 0;
        for (DroneDockBlockEntity dock : docks) {
            if (placed >= wanted) {
                break;
            }
            if (place(level, dock) != null) {
                placed++;
            }
        }
        return placed;
    }

    /** Eén drone van Biebia op dit dock: aan, met een volle batterij, zijn printplaat en zijn programma. */
    private static GuardDroneEntity place(ServerLevel level, DroneDockBlockEntity dock) {
        GuardDroneEntity drone = ModEntities.GUARD_DRONE.get().create(level);
        if (drone == null) {
            return null;
        }
        BlockPos pos = dock.getBlockPos();
        BlockState state = level.getBlockState(pos);
        Direction facing = state.getBlock() instanceof DroneDockBlock ? state.getValue(DroneDockBlock.FACING) : Direction.SOUTH;
        drone.moveTo(pos.getX() + 0.5, pos.getY() + 4.0 / 16.0, pos.getZ() + 0.5, facing.toYRot(), 0);
        drone.setYaw(facing.toYRot());
        drone.brain().body().setFacing(facing);
        drone.setBoard(board());
        drone.setProgram(program());
        BotNetwork network = BotNetwork.get(level);
        int number = 1;
        while (network.nameTaken(null, "Tower drone " + number, null)) {
            number++;
        }
        drone.setCustomName(Component.literal("Tower drone " + number));
        drone.setHome(pos);
        dock.setBot(drone.getUUID());
        level.addFreshEntity(drone);
        drone.setActive(true);
        network.update(drone);
        return drone;
    }

    /** Biebia's drones in het gebied van deze kern (die van niemand zijn). */
    private static List<GuardDroneEntity> of(ServerLevel level, SecurityCoreBlockEntity core) {
        return level.getEntitiesOfClass(GuardDroneEntity.class, core.area(),
                drone -> drone.isAlive() && drone.owner() == null && SecurityCores.coreAt(level, drone.blockPosition()) == core);
    }

    /** Het alarm: iemand begint de kern te hacken. Biebia's drones krijgen de opdracht "alarm", met zijn plek. */
    public static void alarm(ServerLevel level, SecurityCoreBlockEntity core, ServerPlayer intruder) {
        for (GuardDroneEntity drone : of(level, core)) {
            drone.brain().order("alarm", intruder.blockPosition());
        }
    }

    /**
     * De toren is gehackt: Biebia's drones zijn nu van de hacker (en volgen zijn regels voor vrienden). Heeft hij al
     * een robot of drone met dezelfde naam, dan krijgt de drone een nieuwe ("Drone 3").
     */
    public static void handOver(ServerLevel level, SecurityCoreBlockEntity core, ServerPlayer player) {
        BotNetwork network = BotNetwork.get(level);
        for (GuardDroneEntity drone : of(level, core)) {
            drone.setOwner(player);
            if (network.nameTaken(player.getUUID(), drone.getName().getString(), drone.getUUID())) {
                drone.setCustomName(Component.literal(network.freeName(player.getUUID(), ProgramKind.DRONE)));
            }
            network.update(drone);
        }
    }
}
