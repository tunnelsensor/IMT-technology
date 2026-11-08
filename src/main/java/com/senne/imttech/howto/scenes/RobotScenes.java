package com.senne.imttech.howto.scenes;

import com.senne.imttech.arsenal.DroneDockBlock;
import com.senne.imttech.bot.BotBoard;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.logic.RackBay;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.robot.RobotAssemblerBlock;
import com.senne.imttech.robot.RobotDockBlock;
import com.senne.imttech.robot.RobotParts;
import com.senne.imttech.robot.RobotStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * De animaties van het tabblad Robots (hoofdstuk 63, jouw vraag van 8 november: "ga verder met alles over de robots
 * maken/bouwen/coden"): een guard robot bouwen, hem programmeren, modules solderen, wat de modules doen, en de security
 * drone. De training staat in {@link TrainingScenes}. Hoe het echt werkt staat in LEESMIJ ("Guard robots", "Modules
 * solderen", "Robots programmeren", "Zien en schieten", "Security drones").
 *
 * De robots en drones zijn echte robots van het spel: hun onderdelen, printplaat en of ze aan staan gaan mee als
 * gegevens (zie {@link #robot}), zodat je hun lichtjes en modules ziet zoals in je wereld.
 */
public final class RobotScenes {
    private RobotScenes() {}

    /** Zo hoog staat een robot op de plaat van een Robot Dock (zie GuardRobotEntity). */
    static final double DOCK_FLOOR = 3.0 / 16.0;
    /** Zo hoog staat een security drone op zijn Drone Dock. */
    static final double DRONE_FLOOR = 4.0 / 16.0;

    static final int LASER = 0xFF5A3C;
    static final int GUN = 0xFFE15A;
    static final int ION = 0x4F8BFF;

    /** De robot van de animaties over bouwen: een hoofd en benen van een Commando, een lijf en armen van een Super Battle. */
    public static final RobotParts MIX = new RobotParts(RobotStyle.COMMANDO, RobotStyle.SUPER_BATTLE, RobotStyle.SUPER_BATTLE,
            RobotStyle.COMMANDO);

    // ================================================================ Gegevens van een robot

    /** Een robot zoals het spel hem bewaart: zijn onderdelen, zijn printplaat en of hij aan staat (de batterij is vol). */
    static CompoundTag robot(RobotParts parts, BotBoard board, boolean on) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Parts", parts.pack());
        tag.put("Board", BotBoard.CODEC.encodeStart(NbtOps.INSTANCE, board).getOrThrow());
        tag.putBoolean("Active", on);
        return tag;
    }

    /** Een robot van één stijl. */
    static CompoundTag robot(RobotStyle style, BotBoard board, boolean on) {
        return robot(new RobotParts(style, style, style, style), board, on);
    }

    /** Dezelfde robot, neergelegd door de Ion Blaster (hij ligt op zijn rug). */
    static CompoundTag down(CompoundTag robot) {
        CompoundTag tag = robot.copy();
        tag.putLong("DownTicks", 20L * 60);
        return tag;
    }

    /** Een security drone met deze printplaat, aan of uit. */
    static CompoundTag drone(BotBoard board, boolean on) {
        CompoundTag tag = new CompoundTag();
        tag.put("Board", BotBoard.CODEC.encodeStart(NbtOps.INSTANCE, board).getOrThrow());
        tag.putBoolean("Active", on);
        return tag;
    }

    /** Een printplaat met deze modules op pin 1, 2, 3... ("scan", "laser", ...; "" = een lege pin). */
    static BotBoard board(String... modules) {
        BotBoard board = BotBoard.EMPTY;
        for (int i = 0; i < modules.length; i++) {
            if (!modules[i].isEmpty()) {
                board = board.with(i + 1, modules[i]);
            }
        }
        return board;
    }

    /** Alle acht modules, van pin 1 tot 8. */
    static BotBoard allModules() {
        return board("scan", "distance", "face_recognition", "pathfinding", "laser", "gun", "solar_panel", "camera");
    }

    static BlockState dock(Direction facing) {
        return ModBlocks.ROBOT_DOCK.get().defaultBlockState().setValue(RobotDockBlock.FACING, facing);
    }

    static BlockState droneDock(Direction facing) {
        return ModBlocks.DRONE_DOCK.get().defaultBlockState().setValue(DroneDockBlock.FACING, facing);
    }

    static BlockState solar() {
        return ModBlocks.SOLAR_PANEL.get().defaultBlockState();
    }

    /** Met je gezicht van {@code from} naar {@code to} (0 = zuid, 90 = west, zoals bij een wezen). */
    static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    /** Waar een schot uit zijn arm komt: de laser rechts, het geweer links (een robot die op {@code feet} staat). */
    static Vec3 arm(Vec3 feet, float yaw, boolean right) {
        double rad = Math.toRadians(yaw);
        Vec3 ahead = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
        Vec3 rightSide = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
        return feet.add(ahead.scale(0.45)).add(rightSide.scale(right ? 0.42 : -0.42)).add(0, 1.2, 0);
    }

    // ================================================================ Bouwen

    /**
     * Een guard robot bouwen: vier stijlen, wat elk onderdeel doet, de Robot Assembler (zijn scherm als plaatje), zijn
     * dock met stroom, zijn scherm, aan (hij stapt eruit) en uit (hij loopt terug), en herstellen en kogels.
     */
    public static List<HowToScene> build() {
        HowToSceneBuilder s = new HowToSceneBuilder("robot_build", "Building a guard robot").plate(7, 6).height(3);
        BlockPos assembler = new BlockPos(1, 1, 1);
        BlockPos dock = new BlockPos(4, 1, 1);
        BlockPos solar = new BlockPos(5, 1, 1);
        Vec3 onDock = new Vec3(4.5, 1 + DOCK_FLOOR, 1.5);
        Vec3 out = new Vec3(4.5, 1.0, 1.5 + ImtConfig.Robots.STEP_OUT);
        s.idle(6);
        s.chapter("Four styles");
        RobotStyle[] styles = RobotStyle.values();
        // Naast elkaar in beeld: een lijn dwars op de kijkrichting van de camera.
        Vec3[] row = {new Vec3(1.7, 1.0, 2.05), new Vec3(2.7, 1.0, 2.9), new Vec3(3.7, 1.0, 3.7), new Vec3(4.7, 1.0, 4.55)};
        for (int i = 0; i < styles.length; i++) {
            s.actor("style" + i, ModEntities.GUARD_ROBOT.get(), 200 - i * 4, 0, 40.0f, robot(styles[i], BotBoard.EMPTY, true),
                    row[i]);
            s.idle(4);
        }
        s.text(90, null, Side.RIGHT,
                "A *guard robot* is a head, a body, arms and legs, each in one of four styles: *Commando*, *Trainer*,"
                        + " *Battle* and *Super Battle*. You can mix them.");
        s.idle(95);
        s.chapter("What each part does");
        s.text(85, null, Side.LEFT,
                "The *legs* set its speed; the *body* its health, armor and battery; the *arms* how fast it shoots; the *head*"
                        + " how far and how wide it sees. Hover over a part to see its numbers.");
        s.idle(95);

        s.chapter("The Robot Assembler");
        s.place(assembler, ModBlocks.ROBOT_ASSEMBLER.get().defaultBlockState().setValue(RobotAssemblerBlock.FACING, Direction.SOUTH));
        s.idle(10);
        s.text(70, HowToSceneBuilder.top(assembler), Side.RIGHT,
                "You build it at a *Robot Assembler*, with a *Robot Circuit Board*: its brain, with 8 pins for modules.");
        s.idle(75);
        s.rightClick(HowToSceneBuilder.face(assembler, Direction.SOUTH), ItemStack.EMPTY);
        s.idle(14);
        s.picture(140, "robot_assembler", 176, 186,
                s.arrow(0.09f, 0.27f, "Head, body, arms and legs: each slot takes its own kind"),
                s.arrow(0.36f, 0.28f, "The robot as it will be, turning"),
                s.arrow(0.66f, 0.17f, "The *Robot Circuit Board*"),
                s.arrow(0.78f, 0.27f, "*Assemble*: a *Guard Robot* item"));
        s.idle(145);

        s.chapter("Its home: the Robot Dock");
        s.place(dock, dock(Direction.SOUTH));
        s.idle(8);
        s.place(solar, solar());
        s.idle(10);
        s.rightClick(HowToSceneBuilder.top(dock), new ItemStack(ModItems.GUARD_ROBOT.get()));
        s.idle(8);
        CompoundTag off = robot(MIX, BotBoard.EMPTY, false);
        CompoundTag on = robot(MIX, BotBoard.EMPTY, true);
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, 0.0f, off, onDock);
        s.idle(6);
        s.meter(100, onDock.add(0, 2.5, 0), "BATTERY", 0.35f, 1.0f, HowToSceneBuilder.GREEN);
        s.text(100, HowToSceneBuilder.top(solar), Side.RIGHT,
                "Right-click a *Robot Dock* with your *Guard Robot*: that dock is its home. With power (a generator against"
                        + " it, or a cable) it charges there. A new robot is switched *off*.");
        s.idle(105);

        s.chapter("Its screen");
        s.sneakRightClick(onDock.add(0, 1.3, 0), ItemStack.EMPTY);
        s.idle(14);
        s.picture(140, "robot_screen", 300, 176,
                s.arrow(0.1f, 0.06f, "Its name: your code calls it by this"),
                s.arrow(0.9f, 0.06f, "*Standby*, *Active* or *No power*"),
                s.arrow(0.55f, 0.42f, "Health and battery"),
                s.arrow(0.16f, 0.9f, "*Switch on* and off"),
                s.arrow(0.65f, 0.9f, "*Board*: solder modules on it"));
        s.idle(145);

        s.chapter("Switch it on");
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 20, 0.0f, on, onDock, out);
        s.sound(ModSounds.ROBOT_POWER_ON.get(), 0.6f, 1.0f);
        s.idle(4);
        s.text(90, out.add(0, 2.0, 0), Side.LEFT,
                "Switched on, it beeps twice, its lights come on and it steps %s blocks out of its dock. Without a program it"
                        + " just stands there and looks around.", HowToSceneBuilder.number(ImtConfig.Robots.STEP_OUT));
        s.idle(95);

        s.chapter("Switch it off");
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 26, 0.0f, off, out,
                new Vec3(4.5, 1.0, 2.5), onDock);
        s.sound(ModSounds.ROBOT_POWER_OFF.get(), 0.6f, 1.0f);
        s.idle(4);
        s.text(85, onDock.add(0, 2.0, 0), Side.RIGHT,
                "Switched off, it walks back into its dock and charges. An empty battery stops it until it's back on its"
                        + " dock.");
        s.idle(90);

        s.chapter("Repairs and ammo");
        s.rightClick(onDock.add(0, 1.2, 0), new ItemStack(Items.IRON_INGOT, 3));
        s.text(70, onDock.add(0, 1.6, 0), Side.RIGHT,
                "A robot never heals by itself: right-click it with *iron* (%s health per ingot).",
                HowToSceneBuilder.number(ImtConfig.Robots.REPAIR_PER_INGOT));
        s.idle(75);
        s.rightClick(onDock.add(0, 1.2, 0), new ItemStack(ModItems.ROBOT_AMMO.get(), 16));
        s.text(80, onDock.add(0, 1.6, 0), Side.RIGHT,
                "With a *Gun* module it fires *Robot Ammo*: right-click it with some (up to %s). *Pick up* in its screen"
                        + " turns it back into an item, with everything on it.",
                HowToSceneBuilder.number(ImtConfig.Robots.MAX_AMMO));
        s.idle(85);
        return List.of(s.build());
    }

    // ================================================================ Programmeren

    /**
     * Een robot programmeren: een robotprogramma in de app Logic, naar de robot sturen met de app Robots, hij loopt zijn
     * vierkantje, hij meldt zich bij de server (send), de server geeft hem een opdracht, en laden met charge().
     */
    public static List<HowToScene> program() {
        HowToSceneBuilder s = new HowToSceneBuilder("robot_program", "Programming a robot").plate(8, 7).height(3);
        BlockPos gen = new BlockPos(0, 1, 0);
        BlockPos core = new BlockPos(1, 1, 0);
        BlockPos rack = new BlockPos(2, 1, 0);
        BlockPos dock = new BlockPos(5, 1, 0);
        BlockPos solar = new BlockPos(6, 1, 0);
        Vec3 onDock = new Vec3(5.5, 1 + DOCK_FLOOR, 0.5);
        Vec3 a = new Vec3(5.5, 1.0, 2.5);
        Vec3 b = new Vec3(5.5, 1.0, 5.5);
        Vec3 c = new Vec3(2.5, 1.0, 5.5);
        Vec3 d = new Vec3(2.5, 1.0, 2.5);
        Vec3 coreTop = HowToSceneBuilder.top(core).add(0, 0.3, 0);
        BotBoard board = board("scan", "pathfinding");
        CompoundTag off = robot(RobotStyle.COMMANDO, board, false);
        CompoundTag on = robot(RobotStyle.COMMANDO, board, true);
        s.idle(6);
        s.chapter("A robot program");
        Map<BlockPos, BlockState> base = new LinkedHashMap<>();
        base.put(gen, SecurityScenes.heat(Direction.SOUTH));
        base.put(core, SecurityScenes.core(Direction.SOUTH, true));
        base.put(rack, DatacenterScenes.rack(true, RackBay.CPU, RackBay.CPU, RackBay.RAM, RackBay.SSD, RackBay.FAN, RackBay.FAN,
                RackBay.FAN, RackBay.FAN));
        base.put(dock, dock(Direction.SOUTH));
        base.put(solar, solar());
        s.place(base, HowToSceneBuilder.DROP);
        s.idle(10);
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, 0.0f, off, onDock);
        s.idle(6);
        s.text(80, coreTop, Side.RIGHT,
                "A robot does what its *program* says. You write it in the *Logic* app on the desktop of your *Security"
                        + " Core*, in code or in blocks.");
        s.idle(85);
        s.picture(150, "logic_robot", 426, 240,
                s.arrow(0.22f, 0.16f, "Press *+* and pick *Robot program* (a yellow R in the list)"),
                s.arrow(0.47f, 0.226f, "Runs when the robot switches on"),
                s.arrow(0.52f, 0.415f, "*forward(3)*, *turn_right()*: block by block"),
                s.arrow(0.57f, 0.478f, "*send(\"corner\")*: tells your server"),
                s.arrow(0.59f, 0.561f, "Runs when your server gives it this order"));
        s.idle(155);

        s.chapter("Send it to the robot");
        s.picture(130, "robots_program", 426, 240,
                s.arrow(0.16f, 0.282f, "The *Robots* app: your robots and drones"),
                s.arrow(0.375f, 0.283f, "Pick a robot program: your core sends it a copy"),
                s.arrow(0.53f, 0.865f, "*Program*"));
        s.idle(135);

        s.chapter("It runs on the robot");
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), 22, 20, 0.0f, on, onDock, a);
        s.sound(ModSounds.ROBOT_POWER_ON.get(), 0.6f, 1.0f);
        s.idle(22);
        int side = 30;
        int round = 4 * side;
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), round, round, -90.0f, on, a, b, c, d, a);
        s.text(round - 10, a.add(0, 2.2, 0), Side.RIGHT,
                "*when started:* begins when it switches on. It keeps its own copy and runs it itself: *forward(3)* walks"
                        + " three blocks, center to center, and *turn_right()* turns a quarter.");
        s.idle(round);

        s.chapter("Talking to the server");
        // Nog een rondje, en op elke hoek send("corner"): een berichtje door de lucht naar de kern.
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), round, round, -90.0f, on, a, b, c, d, a);
        s.text(round - 5, coreTop, Side.RIGHT,
                "With a *connection* (in your core's area, your core powered, your server running) it talks to your server:"
                        + " *send(\"corner\")* starts *when message(\"corner\"):* there, and *alarm*, *say* and *log* go through"
                        + " it too.");
        for (Vec3 corner : new Vec3[] {b, c, d, a}) {
            s.idle(side);
            s.signal(16, HowToSceneBuilder.CYAN, corner.add(0, 2.0, 0), coreTop);
        }
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), 20, 0, -90.0f, on, a);
        s.idle(4);

        s.chapter("Orders");
        Vec3 target = new Vec3(3.5, 1.0, 4.5);
        s.signal(16, HowToSceneBuilder.ORANGE, coreTop, a.add(0, 2.0, 0));
        s.idle(16);
        s.outline(110, new BlockPos(3, 0, 4), new BlockPos(3, 0, 4), HowToSceneBuilder.RED);
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), 60, 30, 0.0f, on, a, new Vec3(3.5, 1.0, 2.5), target);
        s.text(110, target.add(0, 2.0, 0), Side.RIGHT,
                "Your server gives orders: *order(\"Guard 1\", \"attack\", x, y, z)* interrupts it and starts its *when"
                        + " ordered(\"attack\"):*. *goto(x, y, z)* needs a *Pathfinding* module. Afterwards it carries on.");
        s.idle(60);
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), 50, 30, -90.0f, on, target, new Vec3(3.5, 1.0, 2.5), a);
        s.idle(50);

        s.chapter("Charging");
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), 24, 20, 0.0f, on, a, onDock);
        s.idle(24);
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), 70, 0, 0.0f, on, onDock);
        s.meter(65, onDock.add(0, 2.5, 0), "BATTERY", 0.2f, 0.8f, HowToSceneBuilder.GREEN);
        s.text(90, onDock.add(0, 2.0, 0), Side.RIGHT,
                "*charge(80)* walks it to its dock, charges it to 80%% and walks it back to where it was. *robot.battery*"
                        + " says how full it is.");
        s.idle(70);
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 20, 0.0f, on, onDock, a);
        s.idle(40);
        return List.of(s.build());
    }

    // ================================================================ Solderen

    /**
     * Modules solderen: de acht modules, het bordscherm (de hittebalk als plaatje), een brug zien met Inspect, kortsluiting
     * als je hem dan aanzet, en de modules op de robot. Een kleine plaat, zodat de robot groot in beeld staat.
     */
    public static List<HowToScene> soldering() {
        HowToSceneBuilder s = new HowToSceneBuilder("soldering", "Soldering modules").plate(3, 3).height(3);
        BlockPos dock = new BlockPos(1, 1, 1);
        Vec3 onDock = new Vec3(1.5, 1 + DOCK_FLOOR, 1.5);
        Vec3 head = onDock.add(0, 2.0, 0);
        Vec3 chest = onDock.add(0, 1.3, 0);
        s.idle(6);
        s.place(dock, dock(Direction.SOUTH));
        s.idle(8);
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, 40.0f,
                robot(RobotStyle.COMMANDO, BotBoard.EMPTY, false), onDock);
        s.idle(6);
        s.chapter("Eight modules");
        s.text(110, head, Side.RIGHT,
                "What a robot can do, you solder onto its *circuit board*: 8 pins. There are eight *modules*: *Scan*,"
                        + " *Distance*, *Face Recognition*, *Pathfinding*, *Laser*, *Gun*, *Solar Panel* and *Camera*.");
        s.idle(115);

        s.chapter("The board");
        s.text(70, chest, Side.LEFT,
                "Switch the robot *off*, open its screen (sneak + right-click) and press *Board*. You need a *Soldering"
                        + " Iron* somewhere in your bag.");
        s.idle(75);
        s.sneakRightClick(chest, ItemStack.EMPTY);
        s.idle(14);
        s.picture(150, "robot_board", 300, 196,
                s.arrow(0.23f, 0.225f, "Hold the mouse: the heat bar fills. Let go in the *green*"),
                s.arrow(0.57f, 0.38f, "Its 8 pins"),
                s.arrow(0.35f, 0.715f, "Your modules: drag one onto a pin"),
                s.arrow(0.87f, 0.06f, "*Inspect*: shows bridges"));
        s.idle(155);

        s.chapter("Let go in the green");
        s.meter(56, head.add(0, 0.7, 0), "HEAT", 0.0f, 0.6f, HowToSceneBuilder.GREEN);
        s.text(105, chest, Side.LEFT,
                "Too early: the solder doesn't stick (try again). In the green: a good joint. Too late: the module is on, but"
                        + " the solder often runs to the next pin: a *solder bridge*.");
        s.idle(52);
        // Pin 1 is gesoldeerd: het schoteltje van de Scan staat nu op zijn hoofd.
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, 40.0f,
                robot(RobotStyle.COMMANDO, board("scan"), false), onDock);
        s.sound(SoundEvents.FIRE_EXTINGUISH, 0.3f, 1.6f);
        s.idle(58);

        s.chapter("Solder bridges");
        s.picture(130, "robot_board_inspect", 300, 196,
                s.arrow(0.4f, 0.38f, "A *bridge*: without Inspect only a thin silver line"),
                s.arrow(0.5f, 0.17f, "Switching on now would short!"),
                s.arrow(0.117f, 0.38f, "*Right-click* a pin to desolder it"));
        s.idle(135);

        s.chapter("Short circuit");
        CompoundTag bridged = robot(RobotStyle.COMMANDO, board("scan", "", "laser").withBridge(3), false);
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, 40.0f, bridged, onDock);
        s.idle(10);
        s.flash(8, onDock, 1.0, 2.1, HowToSceneBuilder.RED);
        s.sound(SoundEvents.GENERIC_EXPLODE.value(), 0.25f, 1.8f);
        s.idle(4);
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, 40.0f,
                robot(RobotStyle.COMMANDO, board("scan", "", "burnt"), false), onDock);
        s.text(105, chest, Side.LEFT,
                "Switch it on with a bridge at a module and you get a *short circuit*: it stays off, those modules become"
                        + " *Burnt Modules*, and there is a %s chance the whole robot burns out.",
                Math.round(ImtConfig.Robots.BURN_CHANCE * 100) + "%");
        s.idle(110);

        s.chapter("On the robot");
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, 40.0f,
                robot(RobotStyle.COMMANDO, allModules(), true), onDock);
        s.sound(ModSounds.ROBOT_POWER_ON.get(), 0.6f, 1.0f);
        s.text(110, head, Side.RIGHT,
                "You see the modules on the robot: a dish on its head (Scan), a lens strip (Face Recognition), a laser on its"
                        + " right arm, a gun on its left, a solar panel on its back, a camera at its side.");
        s.idle(115);
        return List.of(s.build());
    }

    // ================================================================ Zien en schieten

    /**
     * Wat de modules doen: read en write (het programma als plaatje), de Scan met Face Recognition (jij mag erlangs, een
     * vreemde niet), de laser, het geweer, de Ion Blaster, en de rest in een zin. De robot staat bovenaan in het midden van
     * het beeld, wie langskomt onderaan: zo staan de tekstballonnen aan de zijkanten nergens voor.
     */
    public static List<HowToScene> modules() {
        HowToSceneBuilder s = new HowToSceneBuilder("robot_modules", "Seeing and shooting").plate(7, 6).height(3);
        Vec3 spot = new Vec3(5.0, 1.0, 1.5);
        Vec3 above = spot.add(0, 2.6, 0);
        BotBoard board = board("scan", "face_recognition", "laser", "gun");
        CompoundTag guard = robot(RobotStyle.COMMANDO, board, true);
        s.idle(6);
        s.chapter("read and write");
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, 40.0f, guard, spot);
        s.idle(10);
        s.text(80, above, Side.RIGHT,
                "In its program, give a pin a name (*scanPin = pin(1)*). Then *read(scanPin)* asks the module something and"
                        + " *write(laserPin, true)* switches it on.");
        s.idle(85);
        s.picture(140, "logic_guard", 426, 240,
                s.arrow(0.5f, 0.268f, "Pin 1, 2 and 3: what you soldered on them"),
                s.arrow(0.66f, 0.436f, "Only people who don't belong to your base"),
                s.arrow(0.58f, 0.478f, "Fire as soon as it sees one"),
                s.arrow(0.54f, 0.603f, "*read(scanPin)*: does it see someone?"));
        s.idle(145);

        s.chapter("Friend or stranger");
        s.player("you", 100, 100, 0.0f, Map.of(), new Vec3(6.8, 1.0, 5.6), new Vec3(2.5, 1.0, 5.2), new Vec3(0.3, 1.0, 4.5));
        s.text(95, above, Side.RIGHT,
                "*Scan* sees players in front of it, not through walls. With *write(facePin, \"strangers\")* it only counts"
                        + " people who don't belong to your base: you can walk by.");
        s.idle(100);

        s.chapter("The laser");
        Vec3 stop = new Vec3(2.4, 1.0, 4.3);
        s.stranger("intruder", "Intruder", 110, 30, 0.0f, Map.of(), new Vec3(0.3, 1.0, 2.2), stop);
        s.idle(30);
        float aim = yawTo(spot, stop);
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, aim, guard, spot);
        s.text(90, above, Side.RIGHT,
                "A weapon that is on fires as soon as it's ready, at whoever its Scan sees. The *Laser* (right arm) costs"
                        + " some battery per shot.");
        for (int i = 0; i < 3; i++) {
            s.beam(5, arm(spot, aim, true), stop.add(0, 1.2, 0), LASER);
            s.sound(ModSounds.ROBOT_LASER.get(), 0.35f, 1.0f);
            s.idle(24);
        }
        s.stranger("intruder", "Intruder", 30, 30, 0.0f, Map.of(), stop, new Vec3(0.2, 1.0, 2.4));
        s.idle(30);

        s.chapter("The gun");
        Vec3 stop2 = new Vec3(4.2, 1.0, 4.6);
        s.stranger("intruder", "Intruder", 100, 30, 0.0f, Map.of(), new Vec3(6.9, 1.0, 5.5), stop2);
        s.idle(30);
        float aim2 = yawTo(spot, stop2);
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, aim2, guard, spot);
        s.text(80, above, Side.LEFT,
                "The *Gun* (left arm) fires *Robot Ammo*: right-click the robot with it. *read(gunPin)* says how many rounds"
                        + " are left.");
        for (int i = 0; i < 3; i++) {
            s.beam(3, arm(spot, aim2, false), stop2.add(0, 1.2, 0), GUN);
            s.sound(ModSounds.ROBOT_GUN.get(), 0.35f, 1.0f);
            s.idle(24);
        }
        s.stranger("intruder", "Intruder", 30, 30, 0.0f, Map.of(), stop2, new Vec3(6.9, 1.0, 5.7));
        s.idle(30);

        s.chapter("The Ion Blaster");
        Vec3 you = new Vec3(5.8, 1.0, 4.6);
        float youAim = yawTo(you, spot);
        ItemStack blaster = new ItemStack(ModItems.ION_BLASTER.get());
        s.player("you2", HowToSceneBuilder.UNTIL_END, 30, youAim, Map.of(EquipmentSlot.MAINHAND, blaster),
                new Vec3(6.9, 1.0, 5.9), you);
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, yawTo(spot, you), guard, spot);
        s.idle(36);
        s.beam(8, you.add(0, 1.3, 0), spot.add(0, 1.1, 0), ION);
        s.sound(ModSounds.ION_BLASTER_FIRE.get(), 0.4f, 1.0f);
        s.idle(4);
        s.actor("guard", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, yawTo(spot, you), down(guard), spot);
        s.sound(ModSounds.ROBOT_POWER_OFF.get(), 0.6f, 1.0f);
        s.text(100, spot.add(0, 1.0, 0), Side.LEFT,
                "Your *Ion Blaster* knocks robots and drones down without breaking them. After %s seconds they get up and"
                        + " start their program again. On people and animals it does nothing.",
                HowToSceneBuilder.number(ImtConfig.Robots.ION_DOWN_SECONDS));
        s.idle(105);

        s.chapter("The other modules");
        s.text(120, null, Side.RIGHT,
                "*Distance*: how many blocks to what's in front of it. *Pathfinding*: *goto(x, y, z)*. *Solar Panel*: it"
                        + " charges in the sun. *Camera*: look through its eyes in the *Cameras* app.");
        s.idle(125);
        return List.of(s.build());
    }

    // ================================================================ Security drones

    /**
     * De security drone: een vliegende robot, op zijn Drone Dock (thuis en lader), aan (opstijgen), een droneprogramma als
     * plaatje, een vierkantje vliegen, en terug naar zijn dock. Een kleine plaat: een drone is maar klein.
     */
    public static List<HowToScene> drones() {
        HowToSceneBuilder s = new HowToSceneBuilder("security_drones", "Security drones").plate(5, 5).height(4);
        BlockPos dock = new BlockPos(3, 1, 1);
        BlockPos solar = new BlockPos(4, 1, 1);
        Vec3 onDock = new Vec3(3.5, 1 + DRONE_FLOOR, 1.5);
        double lift = ImtConfig.Robots.DRONE_TAKE_OFF;
        Vec3 hover = onDock.add(0, lift, 0);
        double high = onDock.y + lift + 1;
        Vec3 a = new Vec3(3.5, high, 1.5);
        Vec3 b = new Vec3(3.5, high, 4.5);
        Vec3 c = new Vec3(0.5, high, 4.5);
        Vec3 d = new Vec3(0.5, high, 1.5);
        BotBoard board = board("scan", "face_recognition", "laser", "pathfinding");
        CompoundTag off = drone(board, false);
        CompoundTag on = drone(board, true);
        s.idle(6);
        s.chapter("A flying robot");
        Vec3 show = new Vec3(2.5, 2.4, 2.5);
        s.actor("show", ModEntities.GUARD_DRONE.get(), 110, 0, 40.0f, on, show);
        s.text(105, show.add(0, 0.5, 0), Side.RIGHT,
                "A *Security Drone* is a guard robot that flies: the same circuit board, modules and soldering, a battery, on"
                        + " and off, and a program. Craft it from iron, an amethyst, redstone, a *Robot Circuit Board* and a"
                        + " *Computer Chip*.");
        s.idle(112);

        s.chapter("Its dock");
        s.place(dock, droneDock(Direction.SOUTH));
        s.idle(8);
        s.place(solar, solar());
        s.idle(10);
        s.rightClick(HowToSceneBuilder.top(dock), new ItemStack(ModItems.GUARD_DRONE.get()));
        s.idle(8);
        s.actor("drone", ModEntities.GUARD_DRONE.get(), HowToSceneBuilder.UNTIL_END, 0, 0.0f, off, onDock);
        s.idle(6);
        s.meter(90, onDock.add(0, 1.2, 0), "BATTERY", 0.3f, 0.56f, HowToSceneBuilder.GREEN);
        s.text(95, HowToSceneBuilder.top(dock), Side.LEFT,
                "Right-click a *Drone Dock* with it: that dock is its home and its charger. Right-click the dock to hear how"
                        + " it's doing: *\"Drone 1 is charging (56%%)\"*.");
        s.idle(100);

        s.chapter("Switch it on");
        s.actor("drone", ModEntities.GUARD_DRONE.get(), 30, 14, 0.0f, on, onDock, hover);
        s.sound(ModSounds.ROBOT_POWER_ON.get(), 0.6f, 1.2f);
        s.text(85, hover.add(0, 0.8, 0), Side.RIGHT,
                "Switched on, it takes off; then its program starts. Its screen is the same as a robot's: sneak + right-click"
                        + " it (*Board* to solder).");
        s.idle(30);
        s.actor("drone", ModEntities.GUARD_DRONE.get(), 60, 0, 0.0f, on, hover);
        s.idle(60);

        s.chapter("A drone program");
        s.actor("drone", ModEntities.GUARD_DRONE.get(), 140, 0, 0.0f, on, hover);
        s.picture(135, "logic_drone", 426, 240,
                s.arrow(0.22f, 0.16f, "Press *+* and pick *Drone program* (a light blue D)"),
                s.arrow(0.41f, 0.268f, "*up(1)*: straight up, not through a ceiling"),
                s.arrow(0.65f, 0.352f, "*drone.battery*: drone instead of robot"),
                s.arrow(0.53f, 0.436f, "*forward(3)* at the same height"));
        s.idle(140);

        s.chapter("Flying");
        s.actor("drone", ModEntities.GUARD_DRONE.get(), 20, 20, 0.0f, on, hover, a);
        s.text(70, a.add(0, 0.6, 0), Side.RIGHT,
                "*up(1)*, then *forward(3)* and *turn_right()* at the same height, block by block.");
        s.idle(20);
        int side = 22;
        s.actor("drone", ModEntities.GUARD_DRONE.get(), 4 * side, 4 * side, -90.0f, on, a, b, c, d, a);
        s.idle(4 * side);
        s.text(80, a.add(0, 0.6, 0), Side.RIGHT,
                "*goto(x, y, z)* (with a *Pathfinding* module) flies around walls. Its Scan looks all around, %s blocks.",
                HowToSceneBuilder.number(ImtConfig.Robots.DRONE_SCAN_RANGE));
        s.actor("drone", ModEntities.GUARD_DRONE.get(), 80, 0, 0.0f, on, a);
        s.idle(80);

        s.chapter("Back to its dock");
        s.actor("drone", ModEntities.GUARD_DRONE.get(), 30, 30, 0.0f, on, a, onDock);
        s.text(100, onDock.add(0, 1.0, 0), Side.RIGHT,
                "*charge()* flies it home: it lands, charges, takes off and flies back. The *Drones* switch (apps *Access* and"
                        + " *Devices*) sends all your drones home.");
        s.idle(30);
        s.actor("drone", ModEntities.GUARD_DRONE.get(), HowToSceneBuilder.UNTIL_END, 0, 0.0f, off, onDock);
        s.idle(80);
        return List.of(s.build());
    }
}
