package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.bot.BotBoard;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.LogicServer;
import com.senne.imttech.network.OsActionPayload;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.robot.GuardRobotEntity;
import com.senne.imttech.robot.IonBlasterItem;
import com.senne.imttech.robot.RobotParts;
import com.senne.imttech.robot.RobotState;
import com.senne.imttech.robot.RobotStyle;
import com.senne.imttech.robot.Robots;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.SecurityOs;
import com.senne.imttech.shield.DomeGeneratorBlockEntity;
import com.senne.imttech.station.ChargerBlockEntity;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de zintuigen en wapens van een robot (hoofdstuk 24): wat de modules op zijn pinnen zeggen
 * ({@code read}) en doen ({@code write}).
 */
@GameTestHolder(ImtTech.MODID)
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
        robot.setParts(RobotParts.DEFAULT.with(com.senne.imttech.robot.RobotSlot.HEAD, head));
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
        ServerPlayer front = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 2, 7), "scan-front");
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
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 2, 7), "face-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(10, 2, 6), "face-stranger");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos rack = new BlockPos(12, 3, 2);
        ImtTechGameTests.placeServer(helper, rack, core);
        BotBoard board = BotBoard.EMPTY.with(1, "scan").with(2, "face_recognition");
        GuardRobotEntity strangers = robot(helper, new BlockPos(6, 2, 12), RobotStyle.COMMANDO, board, faceProgram("strangers"));
        GuardRobotEntity trusted = robot(helper, new BlockPos(9, 2, 12), RobotStyle.COMMANDO, board, faceProgram("trusted"));
        GuardRobotEntity named = robot(helper, new BlockPos(12, 2, 12), RobotStyle.COMMANDO, board, faceProgram("Face-Stranger"));
        for (GuardRobotEntity robot : java.util.List.of(strangers, trusted, named)) {
            robot.setOwner(owner);
        }
        helper.runAfterDelay(2, () -> ImtTechGameTests.startServer(helper, rack, core));
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

    // ================================================================ Stap 3: laser en geweer

    /** Schiet met de laser (pin 3) zodra hij klaar is; telt de schoten, en of hij vlak na een schot weer klaar was. */
    private static final String LASER = """
            shots = 0
            readyAfter = true
            when started:
                forever:
                    if read(pin(3)):
                        write(pin(3), true)
                        shots = shots + 1
                        readyAfter = read(pin(3))
                    wait(0.1)
            """;

    /** Schiet met het geweer (pin 4) zolang er kogels zijn; een schot telt als er een kogel minder is. */
    private static final String GUN = """
            shots = 0
            when started:
                forever:
                    before = read(pin(4))
                    if before > 0:
                        write(pin(4), true)
                        if read(pin(4)) < before:
                            shots = shots + 1
                    wait(0.1)
            """;

    /**
     * De laser schiet recht vooruit (hij ziet geen speler) en raakt de koe: dat kost batterij, en vlak na een schot is
     * hij niet klaar. Het geweer schiet niet zonder kogels; de eigenaar laadt er drie (rechtsklik met Robot Ammo), een
     * vreemde mag dat niet, en dan schiet hij ze alle drie op.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "robot_weapons")
    public static void robot_laser_and_gun_shoot(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(20, 2, 20), "gun-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(21, 2, 21), "gun-stranger");
        owner.getAbilities().instabuild = false; // testspelers houden anders creative-handen: dan raakt de stapel niet op
        stranger.getAbilities().instabuild = false;
        Cow laserCow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 2, 9));
        Cow gunCow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(12, 2, 9));
        GuardRobotEntity laser = robot(helper, new BlockPos(4, 2, 14), RobotStyle.BATTLE, BotBoard.EMPTY.with(3, "laser"), LASER);
        GuardRobotEntity gun = robot(helper, new BlockPos(12, 2, 14), RobotStyle.BATTLE, BotBoard.EMPTY.with(4, "gun"), GUN);
        gun.setOwner(owner);
        double full = laser.maxBattery();
        helper.runAfterDelay(20, () -> {
            check((Double) laser.brain().global("shots") >= 1, "De laser schoot niet");
            check(Boolean.FALSE.equals(laser.brain().global("readyAfter")), "Vlak na een schot was de laser al weer klaar");
            check(laserCow.getHealth() < laserCow.getMaxHealth(), "De laser raakte de koe niet");
            check(laser.battery() <= full - ImtConfig.Robots.LASER_ENERGY, "Een laserschot kostte geen batterij: " + laser.battery());
            check(Double.valueOf(0).equals(gun.brain().global("shots")) && gunCow.getHealth() == gunCow.getMaxHealth(),
                    "Het geweer schoot zonder kogels");
            // Laden: de eigenaar wel, een vreemde niet.
            stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROBOT_AMMO.get(), 5));
            stranger.interactOn(gun, InteractionHand.MAIN_HAND);
            check(gun.ammo() == 0 && stranger.getMainHandItem().getCount() == 5, "Een vreemde kon kogels laden");
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROBOT_AMMO.get(), 3));
            owner.interactOn(gun, InteractionHand.MAIN_HAND);
            check(gun.ammo() == 3 && owner.getMainHandItem().isEmpty(), "Laden lukte niet: " + gun.ammo() + " kogels");
        });
        helper.runAfterDelay(140, () -> {
            check(gun.ammo() == 0 && Double.valueOf(3).equals(gun.brain().global("shots")),
                    "Het geweer schoot niet drie keer: " + gun.brain().global("shots") + " schoten, " + gun.ammo() + " kogels over");
            check(!gunCow.isAlive() || gunCow.getHealth() < gunCow.getMaxHealth(), "Het geweer raakte de koe niet");
            helper.succeed();
        });
    }

    /**
     * Wat een schot tegenhoudt: een muur en een schildkoepel (de koe erachter blijft heel). En zonder PvP (zoals in de testwereld) schiet hij wel op een speler die hij ziet, maar raakt hij
     * hem niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "robot_weapons")
    public static void robot_shots_stop_at_walls_domes_and_pvp(GameTestHelper helper) {
        ServerPlayer target = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 9), "pvp-target");
        GuardRobotEntity sees = robot(helper, new BlockPos(4, 2, 14), RobotStyle.COMMANDO,
                BotBoard.EMPTY.with(1, "scan").with(3, "laser"), LASER);
        // Een muur tussen de robot en de koe.
        helper.setBlock(new BlockPos(20, 2, 12), Blocks.STONE);
        helper.setBlock(new BlockPos(20, 3, 12), Blocks.STONE);
        Cow walled = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(20, 2, 9));
        robot(helper, new BlockPos(20, 2, 14), RobotStyle.BATTLE, BotBoard.EMPTY.with(3, "laser"), LASER);
        // Een koepel (8 blokken) rond de koe in het midden; de robot staat erbuiten.
        BlockPos domePos = new BlockPos(12, 3, 12);
        helper.setBlock(domePos, ModBlocks.DOME_GENERATOR.get());
        helper.setBlock(domePos.east(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(domePos.east())).fill(20_000);
        DomeGeneratorBlockEntity dome = (DomeGeneratorBlockEntity) helper.getBlockEntity(domePos);
        dome.setRadius(0);
        Cow shielded = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(12, 2, 14));
        GuardRobotEntity[] outside = new GuardRobotEntity[1];
        helper.runAfterDelay(25, () -> {
            check(dome.isUp(), "De koepel ging niet op: " + dome.status());
            outside[0] = robot(helper, new BlockPos(12, 2, 22), RobotStyle.BATTLE, BotBoard.EMPTY.with(3, "laser"), LASER);
        });
        helper.runAfterDelay(50, () -> {
            check((Double) sees.brain().global("shots") >= 1, "De robot schoot niet op de speler die hij zag");
            check(target.getHealth() == target.getMaxHealth(), "Zonder PvP raakte hij toch een speler");
            check(walled.getHealth() == walled.getMaxHealth(), "Het schot ging door de muur");
            check((Double) outside[0].brain().global("shots") >= 1, "De robot buiten de koepel schoot niet");
            check(shielded.getHealth() == shielded.getMaxHealth(), "Het schot ging door de koepel");
            check(dome.isUp(), "De koepel ging neer");
            helper.succeed();
        });
    }

    // ================================================================ Stap 4: het zonnepaneel

    /**
     * De Solar Panel-module laadt in de zon, ook als hij uit staat: om 12 uur 's middags (dat blijft dag, dus andere
     * tests merken het niet) zegt hij bijna 100 zon. Onder een dak, of zonder de module, laadt hij niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void robot_solar_panel_charges_in_the_sun(GameTestHelper helper) {
        helper.getLevel().setDayTime(6000);
        BotBoard solar = BotBoard.EMPTY.with(5, "solar_panel");
        GuardRobotEntity sunny = still(helper, new BlockPos(6, 2, 6), solar);
        GuardRobotEntity covered = still(helper, new BlockPos(14, 2, 6), solar);
        helper.setBlock(new BlockPos(14, 5, 6), Blocks.STONE); // een dak boven zijn hoofd
        GuardRobotEntity plain = still(helper, new BlockPos(10, 2, 6), BotBoard.EMPTY);
        double half = sunny.maxBattery() / 2;
        for (GuardRobotEntity robot : java.util.List.of(sunny, covered, plain)) {
            robot.setBattery(half);
        }
        helper.runAfterDelay(45, () -> {
            check((Double) sunny.brain().pins().read(5) >= 90, "Om 12 uur ving hij maar " + sunny.brain().pins().read(5) + " zon");
            check(sunny.battery() >= half + 20 * ImtConfig.Robots.SOLAR_CHARGE * 0.9, "In de zon laadde hij niet: " + sunny.battery());
            check(Double.valueOf(0).equals(covered.brain().pins().read(5)) && covered.battery() == half,
                    "Onder een dak laadde hij toch: " + covered.battery());
            check(plain.battery() == half, "Zonder module laadde hij toch: " + plain.battery());
            helper.succeed();
        });
    }

    /** Een robot met zijn verstand aan, maar uit en zonder programma, met deze modules. */
    private static GuardRobotEntity still(GameTestHelper helper, BlockPos pos, BotBoard board) {
        GuardRobotEntity robot = helper.spawn(ModEntities.GUARD_ROBOT.get(), pos);
        robot.setParts(RobotParts.DEFAULT);
        robot.setBoard(board);
        return robot;
    }

    // ================================================================ Stap 5: de camera

    /**
     * Een robot met een Camera-module en verbinding staat in de app Cameras (met zijn nummer, zodat je spel zijn hoofd
     * volgt); zet zijn programma de camera uit, dan staat hij er "uit" bij. Zonder module of zonder verbinding staat
     * hij er niet. Kijkt iemand door zijn ogen (de app zegt het), dan is {@code read(cameraPin)} true.
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "robot_camera")
    public static void robot_camera_in_the_cameras_app(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "camera-owner");
        BlockPos corePos = new BlockPos(2, 3, 2);
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, corePos, owner);
        BlockPos rack = new BlockPos(12, 3, 2);
        ImtTechGameTests.placeServer(helper, rack, core);
        BotBoard camera = BotBoard.EMPTY.with(6, "camera");
        GuardRobotEntity watcher = robot(helper, new BlockPos(6, 2, 12), RobotStyle.COMMANDO, camera,
                "watched = false\nwhen started:\n    forever:\n        watched = read(pin(6))\n        wait(0.1)\n");
        GuardRobotEntity shy = robot(helper, new BlockPos(9, 2, 12), RobotStyle.COMMANDO, camera,
                "when started:\n    write(pin(6), false)\n");
        GuardRobotEntity blind = robot(helper, new BlockPos(12, 2, 12), RobotStyle.COMMANDO, BotBoard.EMPTY, "when started:\n    pass\n");
        GuardRobotEntity stranger = robot(helper, new BlockPos(15, 2, 12), RobotStyle.COMMANDO, camera, "when started:\n    pass\n");
        for (GuardRobotEntity robot : java.util.List.of(watcher, shy, blind)) {
            robot.setOwner(owner); // "stranger" heeft geen eigenaar: geen verbinding
        }
        helper.runAfterDelay(25, () -> {
            ImtTechGameTests.startServer(helper, rack, core);
            java.util.Map<Integer, CompoundTag> listed = new java.util.HashMap<>();
            for (Tag element : Robots.cameras(helper.getLevel(), core)) {
                CompoundTag tag = (CompoundTag) element;
                listed.put(tag.getInt("robot"), tag);
            }
            check(listed.containsKey(watcher.getId()) && listed.get(watcher.getId()).getBoolean("on"),
                    "De robot met een camera staat niet (aan) in de app Cameras: " + listed);
            check(listed.containsKey(shy.getId()) && !listed.get(shy.getId()).getBoolean("on"), "De uitgezette camera staat er niet uit bij");
            check(!listed.containsKey(blind.getId()), "Een robot zonder Camera-module staat erin");
            check(!listed.containsKey(stranger.getId()), "Een robot zonder verbinding staat erin");
            check(Boolean.FALSE.equals(watcher.brain().global("watched")), "Er keek al iemand mee");
            SecurityOs.open(owner, helper.absolutePos(corePos));
            CompoundTag args = new CompoundTag();
            args.putInt("robot", watcher.getId());
            SecurityOs.handleAction(owner, new OsActionPayload(helper.absolutePos(corePos), "cam_watch", args));
        });
        helper.runAfterDelay(30, () -> {
            check(Boolean.TRUE.equals(watcher.brain().global("watched")), "read(cameraPin) zag niet dat iemand meekeek");
            helper.succeed();
        });
    }

    // ================================================================ Stap 6: de Ion Blaster

    /**
     * De Ion Blaster legt een robot neer (DOWN) zonder hem te verwonden: zijn programma stopt, en na de tijd (hier 2
     * seconden) staat hij weer op en begint zijn programma opnieuw bij when started. Een koe en een speler raakt de
     * straal wel, maar er gebeurt niets mee.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "robot_ion")
    public static void ion_blaster_downs_robots_only(GameTestHelper helper) {
        ServerPlayer shooter = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 2, 2), "ion-shooter");
        GuardRobotEntity robot = robot(helper, new BlockPos(6, 2, 8), RobotStyle.COMMANDO, BotBoard.EMPTY,
                "boots = 0\nwhen started:\n    boots = boots + 1\n");
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(12, 2, 8));
        ServerPlayer victim = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(18, 2, 8), "ion-victim");
        helper.runAfterDelay(5, () -> {
            check(Double.valueOf(1).equals(robot.brain().global("boots")), "Zijn programma begon niet");
            float before = ImtConfig.Robots.ION_DOWN_SECONDS;
            ImtConfig.Robots.ION_DOWN_SECONDS = 2;
            Entity hit;
            try {
                shooter.lookAt(EntityAnchorArgument.Anchor.EYES, robot.getBoundingBox().getCenter());
                hit = IonBlasterItem.fire(helper.getLevel(), shooter);
            } finally {
                ImtConfig.Robots.ION_DOWN_SECONDS = before;
            }
            check(hit == robot && robot.state() == RobotState.DOWN, "De robot ging niet neer: " + hit + ", " + robot.state());
            check(!robot.brain().started(), "Neer liep zijn programma door");
            check(robot.getHealth() == robot.getMaxHealth(), "De Ion Blaster verwondde de robot");
            shooter.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
            check(IonBlasterItem.fire(helper.getLevel(), shooter) == cow && cow.getHealth() == cow.getMaxHealth(),
                    "De straal deed de koe iets (of raakte hem niet)");
            shooter.lookAt(EntityAnchorArgument.Anchor.EYES, victim.getBoundingBox().getCenter());
            check(IonBlasterItem.fire(helper.getLevel(), shooter) == victim && victim.getHealth() == victim.getMaxHealth(),
                    "De straal deed een speler iets (of raakte hem niet)");
        });
        helper.runAfterDelay(5 + 40 + 10, () -> {
            check(robot.state() == RobotState.ACTIVE && robot.brain().started(), "Na 2 seconden stond hij niet weer op: " + robot.state());
            check(Double.valueOf(1).equals(robot.brain().global("boots")), "Opgestaan begon zijn programma niet opnieuw (vers)");
            helper.succeed();
        });
    }
}
