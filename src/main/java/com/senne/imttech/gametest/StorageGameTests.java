package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.car.CarProgram;
import com.senne.imttech.car.DrillHead;
import com.senne.imttech.car.RcCarEntity;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.lan.RouterBlockEntity;
import com.senne.imttech.lan.Routers;
import com.senne.imttech.logic.program.LogicException;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.logic.program.ScriptPrinter;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.station.ChargerBlockEntity;
import com.senne.imttech.storage.ChargingPadBlock;
import com.senne.imttech.storage.ChestSensorBlock;
import com.senne.imttech.storage.ItemKind;
import com.senne.imttech.storage.StorageActionPayload;
import com.senne.imttech.storage.StorageInterfaceBlockEntity;
import com.senne.imttech.storage.StorageMenu;
import com.senne.imttech.storage.StorageOrder;
import com.senne.imttech.storage.StorageRegistry;
import com.senne.imttech.storage.StorageStatePayload;
import com.senne.imttech.storage.Storages;
import com.senne.imttech.weapons.EnergyCellItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor het opslagsysteem met de RC Car (hoofdstuk 113, jouw wens: "een storage system via de robots: je kunt een
 * item orderen via een storage interface, deze interface activeert een robot die je hebt geprogrammeerd; je moet elke
 * stap en kist die de robot moet checken in je code programmeren").
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class StorageGameTests {
    private StorageGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * Een auto met een volle cel, de netherite boorkop en een RC Robot Arm (hoofdstuk 118: zonder arm komt hij niet bij
     * kisten hoger dan het blok vóór hem), die naar het noorden kijkt, met dit programma.
     */
    private static RcCarEntity car(GameTestHelper helper, BlockPos pos, String code) {
        RcCarEntity car = helper.spawn(ModEntities.RC_CAR.get(), pos);
        car.setDrill(DrillHead.NETHERITE);
        car.setRobotArm(true);
        car.body().setFacing(Direction.NORTH);
        car.swapCell(EnergyCellItem.withCharge(EnergyCellItem.FULL));
        String problem = car.setSource(code);
        check(problem == null, "De code van de test klopt niet: " + problem);
        return car;
    }

    /** Een ton op deze plek met deze spullen erin. */
    private static Container barrel(GameTestHelper helper, BlockPos pos, ItemStack... stacks) {
        helper.setBlock(pos, Blocks.BARREL);
        Container barrel = (Container) helper.getBlockEntity(pos);
        for (int i = 0; i < stacks.length; i++) {
            barrel.setItem(i, stacks[i]);
        }
        return barrel;
    }

    private static int count(Container container, Item item) {
        return container.countItem(item);
    }

    /** De fout die deze code geeft (zonder "Line n: "), of "" als hij klopt. */
    private static String problem(String code, ProgramKind kind) {
        try {
            ScriptParser.parse(code, kind);
            return "";
        } catch (LogicException e) {
            return e.getMessage();
        }
    }

    private static void expectProblem(String code, ProgramKind kind, String part) {
        String problem = problem(code, kind);
        check(problem.contains(part), "Verwachtte een fout met \"" + part + "\", maar kreeg: \"" + problem + "\" bij:\n" + code);
    }

    // ================================================================ De taal

    /**
     * De nieuwe woorden van de RC Car (jouw wens: "kijken of het item in die chest zit en het eruit halen doe je gewoon
     * door if item("hetitem") in chest():"). Ze kloppen in een autoprogramma en komen precies zo terug uit de printer;
     * elders bestaan ze niet. Een auto krijgt alleen "fetch" en "store" als opdracht, en order.item bestaat alleen onder
     * when ordered. En de gewone woorden take, put, check en item blijven vrij in een serverdiagram of robotprogramma:
     * een diagram van vóór deze update met def check(): moet blijven kloppen.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void storage_language(GameTestHelper helper) {
        check(CarProgram.problem(CarProgram.EXAMPLE_STORAGE) == null, "Het voorbeeld van de opslag-auto klopt niet: "
                + CarProgram.problem(CarProgram.EXAMPLE_STORAGE));
        String code = """
                when ordered("fetch"):
                    if item(order.item) in chest() and chest.count(order.item) >= 1:
                        take(order.item, order.left)
                    if "diamond" in trunk() or not item("Iron Ingot") in chest():
                        put("diamond")
                    take()
                    take("iron_ingot")
                    put()
                    put(order.item, trunk.count(order.item) - 1)
                    check()
                    if order.known and car.arm < 2 and chest.free > 0 and chest.slots > chest.items:
                        say("At " + order.x + " " + order.y + " " + order.z + ": " + order.count + " " + order.name)
                """;
        String printed = ScriptPrinter.print(ScriptParser.parse(code, ProgramKind.CAR));
        check(printed.equals(code), "De code van de opslag kwam niet precies terug:\n" + printed);
        // Alleen in een autoprogramma.
        expectProblem("when started:\n    take()\n", ProgramKind.ROBOT, "take() only works in an RC program");
        expectProblem("when every(5):\n    check()\n", ProgramKind.SERVER, "check() only works in an RC program");
        expectProblem("when every(5):\n    if item(\"a\") in chest():\n        say(\"x\")\n", ProgramKind.SERVER,
                "item(...) only works in an RC program");
        expectProblem("when every(5):\n    if \"a\" in chest():\n        say(\"x\")\n", ProgramKind.SERVER, "in chest() only works in an RC program");
        expectProblem("when ordered(\"attack\"):\n    if order.item == \"a\":\n        say(\"x\")\n", ProgramKind.ROBOT,
                "order.item only works in an RC program");
        // Wat erbij hoort.
        expectProblem("def a():\n    take(3)\n", ProgramKind.CAR, "take: the item must be text");
        expectProblem("def a():\n    take(\"a\", \"b\")\n", ProgramKind.CAR, "take: the count must be a number");
        expectProblem("def a():\n    put(\"a\", 1, 2)\n", ProgramKind.CAR, "put needs at most 2 values");
        expectProblem("def a():\n    check(1)\n", ProgramKind.CAR, "check does not take a number");
        expectProblem("def a():\n    if item(\"a\") in box():\n        stop()\n", ProgramKind.CAR, "After in comes chest() or trunk()");
        expectProblem("def a():\n    if 3 in chest():\n        stop()\n", ProgramKind.CAR, "Before in comes the name of an item");
        expectProblem("def a():\n    if item(3) in chest():\n        stop()\n", ProgramKind.CAR, "item needs the name of an item between quotes");
        expectProblem("def a():\n    if in chest():\n        stop()\n", ProgramKind.CAR, "Something is missing before in");
        expectProblem("def a():\n    if chest.count() > 1:\n        stop()\n", ProgramKind.CAR, "chest.count needs the name of an item");
        // De opdrachten van een auto komen van zijn Storage Interface.
        expectProblem("when ordered(\"attack\"):\n    stop()\n", ProgramKind.CAR, "An RC car gets its orders from a Storage Interface");
        expectProblem("when started:\n    say(order.item)\n", ProgramKind.CAR, "order.item only works inside when ordered(...)");
        check(problem("when ordered(\"store\"):\n    take(order.item, order.count)\n", ProgramKind.CAR).isEmpty(),
                "when ordered(\"store\") klopt niet in een autoprogramma");
        // De nieuwe woorden zijn nergens bezet: een programma van vóór hoofdstuk 113 mag ze als eigen naam hebben, ook een
        // autoprogramma (dat bestaat sinds hoofdstuk 108). Zijn eigen def gaat dan vóór het woord van de taal; dat de auto
        // die def ook echt draait, test rc_car_arm_reaches_three_high. (Gevonden bij de nalezing.)
        String oldCar = "take = 0\ndef check():\n    take = take + 1\n\ndef lane(int put):\n    for arm_up in range(put):\n"
                + "        check()\n";
        check(problem(oldCar, ProgramKind.CAR).isEmpty(), "Een autoprogramma van vóór de update met de namen take, check, put en"
                + " arm_up klopt niet meer: " + problem(oldCar, ProgramKind.CAR));
        // mine_down(2) bestaat niet meer, en de uitleg wijst niet naar de arm (die kan niet naar beneden).
        expectProblem("def a():\n    mine_down(2)\n", ProgramKind.CAR, "To dig deeper, repeat it");
        // ...en elders blijven het gewone namen (een diagram van vóór hoofdstuk 113 blijft kloppen).
        check(problem("""
                item = 0
                when every(5):
                    check()
                    put = item + 1
                    item = put
                def check():
                    say("checked " + item)
                def take(int arm_up):
                    say("took " + arm_up)
                """, ProgramKind.SERVER).isEmpty(), "Een serverdiagram met de namen item, put, check en take klopt niet meer: "
                + problem("item = 0\nwhen every(5):\n    check()\ndef check():\n    say(\"x\")\n", ProgramKind.SERVER));
        check(problem("when started:\n    item = 3\n    forward(item)\n", ProgramKind.ROBOT).isEmpty(),
                "Een robotprogramma met de naam item klopt niet meer");
        // In een autoprogramma mag item ook nog een eigen naam zijn: als functie herkennen we het aan het haakje.
        check(problem("def a():\n    item = \"dirt\"\n    if item(item) in trunk():\n        stop()\n", ProgramKind.CAR).isEmpty(),
                "Een autoprogramma met een eigen naam item klopt niet");
        helper.succeed();
    }

    // ================================================================ De arm

    /**
     * Jouw keuze: een arm die je programmeert, "en deze gebruik je vanaf nu ook voor het minen van 1+ block range". Met
     * arm_up() doet de auto alles wat hij vóór zich doet een blok hoger: kijken (block()), breken (mine()) en plaatsen
     * (place()). Hoger dan twee blokken komt hij niet (car.blocked), en de arm kost een beetje stroom.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void rc_car_arm_reaches_three_high(GameTestHelper helper) {
        BlockPos start = new BlockPos(7, 2, 10);
        BlockPos front = start.north();
        helper.setBlock(front, Blocks.STONE);
        helper.setBlock(front.above(2), Blocks.STONE);
        helper.setBlock(front.above(3), Blocks.STONE);
        RcCarEntity car = car(helper, start, """
                stones = 0
                top = false
                take = 0
                def put():
                    take = take + 1
                def tower():
                    for i in range(3):
                        if block() == "stone":
                            stones = stones + 1
                        mine()
                        if i < 2:
                            arm_up()
                    arm_up()
                    top = car.blocked
                    arm_down()
                    place("cobblestone")
                    arm_down(2)
                    put()
                """);
        check(car.arm() == 0, "Een nieuwe auto heeft zijn arm niet beneden");
        check(car.brain().startFunction("tower"), "De functie tower begon niet");
        helper.succeedWhen(() -> {
            check(!car.brain().busy(), "De auto is nog bezig");
            check(Double.valueOf(2).equals(car.brain().global("stones")), "block() zag met de arm " + car.brain().global("stones")
                    + " stenen in plaats van 2 (beneden en twee hoger)");
            check(Boolean.TRUE.equals(car.brain().global("top")), "arm_up() boven de hoogste stand gaf geen car.blocked");
            helper.assertBlockPresent(Blocks.AIR, front);
            helper.assertBlockPresent(Blocks.COBBLESTONE, front.above());
            helper.assertBlockPresent(Blocks.AIR, front.above(2));
            helper.assertBlockPresent(Blocks.STONE, front.above(3));
            check(car.trunk().countItem(Items.COBBLESTONE) == 1, "In de kofferbak ligt " + car.trunk().countItem(Items.COBBLESTONE)
                    + " cobblestone in plaats van 1 (twee gebroken, één geplaatst)");
            check(car.arm() == 0, "Zijn arm staat niet weer beneden: " + car.arm());
            // Een programma van vóór de update met een eigen def put(): put() draait die def, niet het put van de taal.
            check(Double.valueOf(1).equals(car.brain().global("take")), "put() draaide niet de eigen def put() van het programma: take = "
                    + car.brain().global("take"));
            // arm_down(2) vanaf 1: hij kwam maar één blok lager, dus car.blocked.
            check(car.body().blocked(), "arm_down(2) vanaf stand 1 gaf geen car.blocked");
        });
    }

    // ================================================================ Kisten

    /**
     * Jouw wens: "kijken of het item in die chest zit en het eruit halen doe je gewoon door if item("hetitem") in
     * chest():". De auto neemt uit de kist vóór hem (take), legt in de ton erboven (arm_up, put), telt (chest.count,
     * trunk.count, chest.free), en zegt het als er niets is (car.blocked). Een kist met een slot opent hij niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void rc_car_takes_and_puts(GameTestHelper helper) {
        BlockPos start = new BlockPos(7, 2, 10);
        Container low = barrel(helper, start.north(), new ItemStack(Items.IRON_INGOT, 20), new ItemStack(Items.DIAMOND, 5));
        low.setItem(10, new ItemStack(Items.SHULKER_BOX)); // die past niet in zijn kofferbak
        Container high = barrel(helper, start.north().above());
        // Een dubbele kist verderop (hier komt de auto pas aan het eind): de rechterhelft met een slot en twee smaragden.
        BlockPos leftHalf = start.north().east(4);
        helper.setBlock(leftHalf, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH)
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT));
        helper.setBlock(leftHalf.west(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH)
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
        BaseContainerBlockEntity rightHalf = (BaseContainerBlockEntity) helper.getBlockEntity(leftHalf.west());
        rightHalf.setItem(0, new ItemStack(Items.EMERALD, 2));
        CompoundTag lockTag = rightHalf.saveWithoutMetadata(helper.getLevel().registryAccess());
        lockTag.putString("Lock", "secret");
        rightHalf.loadWithComponents(lockTag, helper.getLevel().registryAccess());
        RcCarEntity car = car(helper, start, """
                seen = 0
                free = 0
                missing = false
                nothing = ""
                blank = false
                def work():
                    take(nothing)
                    blank = item(nothing) in chest() or trunk.count(nothing) > 0 or chest.count(nothing) > 0
                    if item("Iron Ingot") in chest() and not item("gold_ingot") in chest():
                        seen = chest.count("iron_ingot")
                    take("iron_ingot", 8)
                    take("diamond")
                    take("gold_ingot")
                    missing = car.blocked
                    arm_up()
                    put("iron_ingot", 3)
                    put()
                    free = chest.free
                    arm_down()
                    check()
                """);
        float before = car.cellPercent();
        check(car.brain().startFunction("work"), "De functie work begon niet");
        helper.succeedWhen(() -> {
            check(!car.brain().busy(), "De auto is nog bezig");
            check(Double.valueOf(20).equals(car.brain().global("seen")), "chest.count zag " + car.brain().global("seen") + " ijzer in plaats van 20");
            check(Boolean.TRUE.equals(car.brain().global("missing")), "take van iets dat er niet ligt gaf geen car.blocked");
            check(count(low, Items.IRON_INGOT) == 12 && count(low, Items.DIAMOND) == 0, "In de onderste ton liggen "
                    + count(low, Items.IRON_INGOT) + " ijzer en " + count(low, Items.DIAMOND) + " diamant (verwacht 12 en 0)");
            check(count(high, Items.IRON_INGOT) == 8 && count(high, Items.DIAMOND) == 5, "In de bovenste ton liggen "
                    + count(high, Items.IRON_INGOT) + " ijzer en " + count(high, Items.DIAMOND) + " diamant (verwacht 8 en 5)");
            check(car.trunk().isEmpty(), "De kofferbak is niet leeg na put()");
            check(Double.valueOf(25).equals(car.brain().global("free")), "chest.free zei " + car.brain().global("free")
                    + " in plaats van 25 (27 vakjes, twee bezet)");
            check(car.cellPercent() < before, "Nemen en leggen kostte geen stroom");
            check(!car.body().blocked(), "car.blocked is waar na check() bij een ton");
            // Een lege naam (zoals order.item buiten een bestelling) is niets, niet alles (gevonden bij de nalezing).
            check(Boolean.FALSE.equals(car.brain().global("blank")), "Een lege naam telde als elk item");
            // Een shulkerdoos past niet in zijn kofferbak: "in chest()" zegt dan nee, en take zegt waarom.
            check(com.senne.imttech.car.CarWork.countInChest(helper.getLevel(), car, "shulker_box") == 0,
                    "chest.count telt een shulkerdoos die de auto niet kan meenemen");
            check(com.senne.imttech.car.CarWork.take(helper.getLevel(), car, "shulker_box", -1) == com.senne.imttech.car.CarWork.Result.REFUSED
                    && car.note().contains("cannot carry") && count(low, Items.SHULKER_BOX) == 1,
                    "take van een shulkerdoos zegt niet dat hij die niet kan dragen: " + car.note());
            // Een kist met een slot: hij opent hem niet, en zegt waarom.
            BaseContainerBlockEntity locked = (BaseContainerBlockEntity) low;
            CompoundTag tag = locked.saveWithoutMetadata(helper.getLevel().registryAccess());
            tag.putString("Lock", "secret");
            locked.loadWithComponents(tag, helper.getLevel().registryAccess());
            check(com.senne.imttech.car.CarWork.take(helper.getLevel(), car, "iron_ingot", -1) == com.senne.imttech.car.CarWork.Result.REFUSED
                    && count(low, Items.IRON_INGOT) == 12 && car.note().contains("locked"), "De auto opende een kist met een slot: " + car.note());
            // Een dubbele kist met een slot op de andere helft: Minecraft opent dan geen van beide, de auto ook niet
            // (gevonden bij de nalezing: hij bereikte via de open helft ook de vakjes van de helft met het slot).
            net.minecraft.world.phys.Vec3 there = net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(leftHalf.south()));
            car.moveTo(there.x, there.y, there.z);
            check(com.senne.imttech.car.CarWork.take(helper.getLevel(), car, "", -1) == com.senne.imttech.car.CarWork.Result.REFUSED
                    && rightHalf.countItem(Items.EMERALD) == 2 && car.note().contains("locked"),
                    "De auto nam uit een dubbele kist met een slot op de andere helft: " + car.note());
        });
    }

    /**
     * Een beschermde zone geldt ook voor kisten: de auto van een vreemde neemt daar niets uit een ton, legt er niets in en
     * ziet niet wat erin zit; de auto van de eigenaar van de zone wel. Zo is een RC Car geen manier om iemands kisten leeg
     * te halen of erin te kijken.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "zones")
    public static void rc_car_leaves_chests_in_a_zone_alone(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 7), "chest-zone-owner");
        ServerPlayer outsider = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(14, 3, 14), "chest-zone-outsider");
        SecurityCoreBlockEntity core = ZoneGameTests.zoneCore(helper, owner);
        String code = """
                seen = 0
                done = 0
                def grab():
                    seen = chest.count("diamond")
                    take("diamond")
                    put("cobblestone")
                    done = 1
                """;
        BlockPos strangerStart = new BlockPos(7, 2, 9);
        BlockPos ownerStart = new BlockPos(10, 2, 9);
        Container strangerBarrel = barrel(helper, strangerStart.north(), new ItemStack(Items.DIAMOND, 7));
        Container ownerBarrel = barrel(helper, ownerStart.north(), new ItemStack(Items.DIAMOND, 7));
        RcCarEntity strangerCar = car(helper, strangerStart, code);
        strangerCar.setOwner(outsider);
        strangerCar.trunk().addItem(new ItemStack(Items.COBBLESTONE, 4));
        RcCarEntity ownerCar = car(helper, ownerStart, code);
        ownerCar.setOwner(owner);
        ownerCar.trunk().addItem(new ItemStack(Items.COBBLESTONE, 4));
        check(strangerCar.brain().startFunction("grab") && ownerCar.brain().startFunction("grab"), "De functie grab begon niet");
        helper.succeedWhen(() -> {
            check(Double.valueOf(1).equals(strangerCar.brain().global("done")) && Double.valueOf(1).equals(ownerCar.brain().global("done")),
                    "Een auto is nog niet klaar");
            check(Double.valueOf(0).equals(strangerCar.brain().global("seen")), "De auto van een vreemde zag wat er in een ton in de zone zit");
            check(count(strangerBarrel, Items.DIAMOND) == 7 && count(strangerBarrel, Items.COBBLESTONE) == 0
                    && strangerCar.trunk().countItem(Items.DIAMOND) == 0 && strangerCar.trunk().countItem(Items.COBBLESTONE) == 4,
                    "De auto van een vreemde nam iets uit (of legde iets in) een ton in de zone");
            check(Double.valueOf(7).equals(ownerCar.brain().global("seen")), "De auto van de eigenaar zag de diamanten in zijn eigen ton niet");
            check(count(ownerBarrel, Items.DIAMOND) == 0 && count(ownerBarrel, Items.COBBLESTONE) == 4
                    && ownerCar.trunk().countItem(Items.DIAMOND) == 7, "De auto van de eigenaar kon niet bij zijn eigen ton in de zone");
            core.setZoneBoxes(List.of());
        });
    }

    // ================================================================ De Storage Interface

    /** Waar alles staat in de tests van de interface: de auto thuis (neus naar het noorden), met de interface vóór hem. */
    private static final BlockPos HOME = new BlockPos(4, 2, 10);
    private static final BlockPos STATION = HOME.north();

    /** De ton van stapel {@code stack} (1 tot 4, langs de gang rechts van de auto) op hoogte {@code level} (0 tot 2). */
    private static BlockPos barrelAt(int stack, int level) {
        return new BlockPos(HOME.getX() + stack, HOME.getY() + level, HOME.getZ() - 1);
    }

    /**
     * De opslagkamer van het voorbeeld (CarProgram.EXAMPLE_STORAGE): de interface, vier stapels van drie tonnen langs een
     * gang, en een auto van {@code owner} die aan de interface gekoppeld is. Geeft de interface.
     */
    private static StorageInterfaceBlockEntity room(GameTestHelper helper, ServerPlayer owner, RcCarEntity car) {
        helper.setBlock(STATION, StorageRegistry.STORAGE_INTERFACE.get());
        StorageInterfaceBlockEntity station = (StorageInterfaceBlockEntity) helper.getBlockEntity(STATION);
        station.setOwner(owner);
        for (int stack = 1; stack <= 4; stack++) {
            for (int level = 0; level < 3; level++) {
                barrel(helper, barrelAt(stack, level));
            }
        }
        car.setOwner(owner);
        check(station.link(car), "De auto koppelde niet aan de interface");
        car.setStation(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(STATION)));
        return station;
    }

    private static Container barrelIn(GameTestHelper helper, int stack, int level) {
        return (Container) helper.getBlockEntity(barrelAt(stack, level));
    }

    /**
     * Jouw wens: "je kunt een item orderen via een storage interface, deze interface activeert een robot die je hebt
     * geprogrammeerd; de robots doorzoeken dan je hele chestroom". Eerst weet de interface niets, en kun je niets
     * bestellen. Een Chest Sensor op één ton (met een netwerkkabel naar de interface) laat hem die ton live zien; de rest
     * leert hij doordat de auto erin kijkt (de functie scan met check()). Dan bestel je 36 ijzer: de auto (het voorbeeld
     * van How it works) rijdt de gang af, neemt 10 uit de ene ton en 26 uit de andere, en legt ze in de afhaalvakjes. De
     * bestelling is klaar, en de interface weet dat er nog 4 liggen.
     */
    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void storage_interface_fetches_with_a_car(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, HOME.south(2), "storage-fetch");
        RcCarEntity car = car(helper, HOME, CarProgram.EXAMPLE_STORAGE + """

                def scan():
                    turn_right()
                    for i in range(chests):
                        forward(1)
                        turn_left()
                        check()
                        arm_up()
                        check()
                        arm_up()
                        check()
                        arm_down(2)
                        turn_right()
                    back(chests)
                    turn_left()
                """);
        StorageInterfaceBlockEntity station = room(helper, owner, car);
        barrelIn(helper, 1, 0).setItem(0, new ItemStack(Items.COBBLESTONE, 12));
        barrelIn(helper, 2, 1).setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        barrelIn(helper, 4, 2).setItem(3, new ItemStack(Items.IRON_INGOT, 30));
        ItemKind iron = ItemKind.of(new ItemStack(Items.IRON_INGOT));
        check(car.brain().handles("fetch") && car.brain().handles("store") && !car.brain().handles("attack"),
                "De auto zegt niet dat zijn programma fetch en store aankan");
        // Nog niets bekend: bestellen kan niet.
        check(station.stock(level).totals().isEmpty() && station.fetch(level, iron, 8, owner) == null && station.orders().isEmpty(),
                "Een interface zonder sensors of gezien kisten nam een bestelling aan");
        // Een Chest Sensor op de ton met de 10 ijzer (aan de achterkant), met een netwerkkabel naar de interface.
        BlockPos sensor = barrelAt(2, 1).north();
        helper.setBlock(sensor, StorageRegistry.CHEST_SENSOR.get().defaultBlockState().setValue(ChestSensorBlock.FACING, Direction.NORTH));
        // In het spel is een sensor van wie hem neerzet. (Zonder eigenaar telt hij alleen op vrije grond, en in de testwereld
        // ligt dit stuk soms in het gebied van de kern van een test ernaast.)
        ((com.senne.imttech.storage.ChestSensorBlockEntity) helper.getBlockEntity(sensor)).setOwner(owner);
        for (BlockPos cable : List.of(STATION.north(), STATION.north().east(), STATION.north().east(2), sensor.below())) {
            Cables.add(CableKind.NETWORK, level, helper.absolutePos(cable));
        }
        // Een sensor van een vreemde op een ton met goud, aan dezelfde kabel: die leest de interface niet (gevonden bij de
        // nalezing: een netwerk houdt zich niet aan eigenaars, en zo las een vreemde jouw kisten).
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, HOME.south(4), "storage-stranger");
        barrelIn(helper, 3, 1).setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
        BlockPos strangerSensor = barrelAt(3, 1).north();
        helper.setBlock(strangerSensor, StorageRegistry.CHEST_SENSOR.get().defaultBlockState().setValue(ChestSensorBlock.FACING, Direction.NORTH));
        ((com.senne.imttech.storage.ChestSensorBlockEntity) helper.getBlockEntity(strangerSensor)).setOwner(stranger);
        Cables.add(CableKind.NETWORK, level, helper.absolutePos(strangerSensor.below()));
        ItemKind gold = ItemKind.of(new ItemStack(Items.GOLD_INGOT));
        helper.runAfterDelay(12, () -> {
            StorageInterfaceBlockEntity.Stock stock = station.stock(level);
            check(stock.sensors() == 1 && stock.count(iron) == 10 && stock.storages() == 1, "Met één sensor ziet de interface "
                    + stock.count(iron) + " ijzer in " + stock.storages() + " kisten (" + stock.sensors() + " sensors)");
            check(stock.count(gold) == 0, "De interface leest de sensor van een vreemde");
            check(Storages.stationOf(level, helper.absolutePos(strangerSensor)) == null, "De sensor van een vreemde zegt dat de interface hem leest");
            check(Storages.stationOf(level, helper.absolutePos(sensor)) == station, "De sensor zegt niet aan welke interface hij hangt");
            check(car.brain().startFunction("scan"), "De functie scan begon niet");
        });
        helper.startSequence()
                .thenWaitUntil(() -> check(helper.getTick() > 14 && !car.brain().busy(), "De auto is nog aan het kijken"))
                .thenExecute(() -> {
                    StorageInterfaceBlockEntity.Stock stock = station.stock(level);
                    check(stock.count(iron) == 40 && stock.count(ItemKind.of(new ItemStack(Items.COBBLESTONE))) == 12,
                            "Na het kijken weet de interface van " + stock.count(iron) + " ijzer (verwacht 40)");
                    check(station.seenStorages() == 12, "De auto keek in " + station.seenStorages() + " tonnen in plaats van 12");
                    // De dichtstbijzijnde ton met ijzer, gezien vanaf thuis: stapel 2.
                    StorageInterfaceBlockEntity.Place near = station.nearest(level, iron, helper.absolutePos(HOME));
                    check(near != null && near.pos().equals(helper.absolutePos(barrelAt(2, 1))) && near.live(),
                            "De dichtstbijzijnde ton met ijzer is niet die van de sensor: " + near);
                    StorageOrder order = station.fetch(level, iron, 36, owner);
                    check(order != null && order.count == 36 && order.state == StorageOrder.State.WAITING, "De bestelling van 36 ijzer kwam er niet");
                })
                .thenWaitUntil(() -> check(station.orders().getFirst().state != StorageOrder.State.WAITING, "De auto is nog niet begonnen"))
                .thenExecute(() -> {
                    // Alleen de auto die ermee bezig is, kan de bestelling beëindigen (niet een auto die er eerder mee bezig was).
                    StorageOrder order = station.orders().getFirst();
                    station.orderEnded(order.id, java.util.UUID.randomUUID(), 36, "x");
                    check(order.state == StorageOrder.State.RUNNING && car.link().equals(order.car),
                            "Een andere auto beëindigde de bestelling: " + order.state);
                })
                .thenWaitUntil(() -> check(station.orders().getFirst().state == StorageOrder.State.DONE, "De bestelling is nog niet klaar: "
                        + station.orders().getFirst().state + " " + station.orders().getFirst().moved + "/36 " + station.orders().getFirst().note
                        + " (" + car.note() + ")"))
                .thenExecute(() -> {
                    check(count(station.out(), Items.IRON_INGOT) == 36, "In de afhaalvakjes liggen " + count(station.out(), Items.IRON_INGOT)
                            + " ijzer in plaats van 36");
                    check(count(barrelIn(helper, 2, 1), Items.IRON_INGOT) == 0 && count(barrelIn(helper, 4, 2), Items.IRON_INGOT) == 4,
                            "De tonnen hebben niet 0 en 4 ijzer over");
                    check(car.trunk().isEmpty() && !car.brain().busy() && car.arm() == 0, "De auto is niet leeg en klaar na de bestelling");
                    // Bij de interface: chest.slots en chest.free gaan over PICK-UP (daar legt put() in), chest.items over de
                    // vakjes om op te bergen (daar neemt take() uit). Gevonden bij de nalezing.
                    int[] numbers = com.senne.imttech.car.CarWork.chestNumbers(level, car);
                    check(numbers[0] == 9 && numbers[1] == 8 && numbers[2] == 0, "Bij de interface zeggen chest.slots, chest.free en"
                            + " chest.items " + numbers[0] + ", " + numbers[1] + " en " + numbers[2] + " in plaats van 9, 8 en 0");
                    check(car.blockPosition().equals(helper.absolutePos(HOME)) && car.body().facing() == Direction.NORTH,
                            "De auto staat niet weer thuis met zijn neus naar de interface");
                    check(station.stock(level).count(iron) == 4, "De interface denkt dat er nog " + station.stock(level).count(iron) + " ijzer ligt");
                    // Tijdens de bestelling werkte hij namens wie bestelde.
                    check(owner.getUUID().equals(car.actorId()), "De auto werkte niet namens wie bestelde");
                })
                .thenSucceed();
    }

    /**
     * Opbergen (jouw keuze: halen én opbergen): leg spullen in de interface en druk op Store. Elke soort wordt één
     * bestelling. De auto pakt ze bij de interface en legt ze waar dat item al ligt (de cobblestone: de interface weet waar,
     * dus hij rijdt de lege tonnen voorbij), of anders in de eerste lege ton (de diamanten, die nog nergens liggen).
     */
    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void storage_interface_stores_with_a_car(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, HOME.south(2), "storage-store");
        RcCarEntity car = car(helper, HOME, CarProgram.EXAMPLE_STORAGE);
        StorageInterfaceBlockEntity station = room(helper, owner, car);
        barrelIn(helper, 2, 0).setItem(0, new ItemStack(Items.COBBLESTONE, 12));
        // De interface weet van die ton (een auto keek er eerder in).
        station.saw(helper.absolutePos(barrelAt(2, 0)), helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, helper.absolutePos(barrelAt(2, 0)), null),
                helper.getLevel().getGameTime());
        check(station.store(owner) == 0 && station.orders().isEmpty(), "Store zonder spullen maakte een bestelling");
        station.in().setItem(0, new ItemStack(Items.COBBLESTONE, 20));
        station.in().setItem(1, new ItemStack(Items.DIAMOND, 5));
        station.in().setItem(4, new ItemStack(Items.COBBLESTONE, 10));
        check(station.store(owner) == 2 && station.orders().size() == 2, "Store maakte niet één bestelling per soort: " + station.orders().size());
        check(station.orders().getFirst().count == 30 && station.orders().get(1).count == 5, "De aantallen van de bestellingen kloppen niet");
        // Nog eens drukken terwijl ze wachten: geen dubbele bestellingen.
        check(station.store(owner) == 2 && station.orders().size() == 2, "Twee keer Store gaf dubbele bestellingen: " + station.orders().size());
        helper.succeedWhen(() -> {
            for (StorageOrder order : station.orders()) {
                check(order.state == StorageOrder.State.DONE, "Een bestelling is nog niet klaar: " + order.kind + " " + order.item + " "
                        + order.state + " " + order.moved + "/" + order.count + " " + order.note + " (" + car.note() + ")");
            }
            check(station.in().isEmpty(), "Er ligt nog iets in de IN-vakjes");
            check(count(barrelIn(helper, 2, 0), Items.COBBLESTONE) == 42, "Bij de cobblestone liggen " + count(barrelIn(helper, 2, 0), Items.COBBLESTONE)
                    + " in plaats van 42");
            check(count(barrelIn(helper, 1, 0), Items.DIAMOND) == 5, "De diamanten liggen niet in de eerste lege ton");
            check(car.trunk().isEmpty() && car.blockPosition().equals(helper.absolutePos(HOME)), "De auto is niet leeg en thuis");
        });
    }

    /**
     * Wat de auto niet kwijt kan, brengt hij terug: de ton waar de cobblestone hoort is vol, dus de stenen die je inlegde
     * liggen daarna in de afhaalvakjes (niet vergeten in zijn kofferbak), en de bestelling zegt dat ze niet af is.
     */
    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void storage_interface_brings_back_what_does_not_fit(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, HOME.south(2), "storage-full");
        RcCarEntity car = car(helper, HOME, CarProgram.EXAMPLE_STORAGE);
        StorageInterfaceBlockEntity station = room(helper, owner, car);
        Container full = barrelIn(helper, 1, 0);
        for (int slot = 0; slot < full.getContainerSize(); slot++) {
            full.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        station.saw(helper.absolutePos(barrelAt(1, 0)), helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, helper.absolutePos(barrelAt(1, 0)), null),
                helper.getLevel().getGameTime());
        station.in().setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        check(station.store(owner) == 1, "Store maakte geen bestelling");
        helper.succeedWhen(() -> {
            StorageOrder order = station.orders().getFirst();
            check(order.ended(), "De bestelling loopt nog: " + order.state + " (" + car.note() + ")");
            check(order.state == StorageOrder.State.FAILED && order.moved == 0 && !order.note.isEmpty(),
                    "De bestelling zegt niet dat er niets opgeborgen is: " + order.state + " " + order.moved + " \"" + order.note + "\"");
            check(count(station.out(), Items.COBBLESTONE) == 10, "De stenen die niet pasten liggen niet in de afhaalvakjes: "
                    + count(station.out(), Items.COBBLESTONE));
            check(car.trunk().isEmpty() && station.in().isEmpty(), "Er ligt nog iets in de kofferbak of in de IN-vakjes");
            check(count(full, Items.COBBLESTONE) == 27 * 64, "De volle ton veranderde");
            check(car.blockPosition().equals(helper.absolutePos(HOME)), "De auto staat niet weer thuis");
        });
    }

    /**
     * Het scherm van de interface: alleen wie hem mag instellen krijgt het (het toont alles wat er in je kisten ligt), een
     * auto koppel je alleen als je hem mag besturen, en een wachtende bestelling haal je weg. Shift-klik legt alleen op het
     * tabblad Store iets in de IN-vakjes.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void storage_interface_screen_and_rights(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = ImtTechGameTests.makeSurvivalOwner(helper, HOME.south(2), "storage-alice");
        ServerPlayer bob = ImtTechGameTests.makeSurvivalOwner(helper, HOME.south(2), "storage-bob");
        RcCarEntity car = car(helper, HOME, CarProgram.EXAMPLE_STORAGE);
        StorageInterfaceBlockEntity station = room(helper, alice, car);
        RcCarEntity bobCar = car(helper, HOME.south(3), "");
        bobCar.setOwner(bob);
        // Even wachten: een auto meldt zich pas na zijn eerste tick aan (zo vindt de interface hem op zijn vaste nummer).
        helper.runAfterDelay(3, () -> screenAndRights(helper, level, alice, bob, car, bobCar, station));
    }

    private static void screenAndRights(GameTestHelper helper, ServerLevel level, ServerPlayer alice, ServerPlayer bob, RcCarEntity car,
                                        RcCarEntity bobCar, StorageInterfaceBlockEntity station) {
        Storages.handleAction(alice, station, StorageActionPayload.link(car.link(), false));
        check(!station.linked(car.link()) && car.station() == null, "Unlink maakte de auto niet los");
        // Bob: het scherm niet, en geen knoppen.
        Storages.open(bob, station);
        check(!(bob.containerMenu instanceof StorageMenu), "Bob kreeg het scherm van de interface van Alice");
        Storages.handleAction(bob, station, StorageActionPayload.link(car.link(), true));
        check(!station.linked(car.link()), "Bob koppelde een auto aan de interface van Alice");
        // De auto van Bob staat in de buurt: Alice ziet hem niet in haar lijst, en kan hem niet koppelen.
        StorageStatePayload state = Storages.state(alice, station);
        check(state.cars().stream().anyMatch(entry -> entry.link().equals(car.link()) && !entry.linked() && entry.fetch() && entry.store()),
                "Alice ziet haar eigen auto niet als te koppelen, met F en S");
        check(state.cars().stream().noneMatch(entry -> entry.link().equals(bobCar.link())), "Alice ziet de auto van Bob in haar lijst");
        Storages.handleAction(alice, station, StorageActionPayload.link(bobCar.link(), true));
        check(!station.linked(bobCar.link()), "Alice koppelde de auto van Bob");
        Storages.handleAction(alice, station, StorageActionPayload.link(car.link(), true));
        check(station.linked(car.link()) && car.station() != null, "Alice kon haar eigen auto niet koppelen");
        // Een bestelling die wacht (de auto heeft geen cel meer), haal je weg.
        car.swapCell(ItemStack.EMPTY);
        barrelIn(helper, 1, 0).setItem(0, new ItemStack(Items.IRON_INGOT, 9));
        station.saw(helper.absolutePos(barrelAt(1, 0)), level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(barrelAt(1, 0)), null), level.getGameTime());
        Storages.handleAction(alice, station, StorageActionPayload.fetch(new ItemStack(Items.IRON_INGOT), 99));
        check(station.orders().size() == 1 && station.orders().getFirst().count == 9, "Bestellen via het scherm gaf niet 9 ijzer (wat er ligt)");
        // Waarom de bestelling wacht (gevonden bij de nalezing). Geen cel, of een restje van 1%: te leeg.
        StorageOrder waiting = station.orders().getFirst();
        station.tickOrders(level);
        check(waiting.state == StorageOrder.State.WAITING && waiting.note.contains("almost empty"), "Een auto zonder cel: "
                + waiting.state + " \"" + waiting.note + "\"");
        car.swapCell(EnergyCellItem.withCharge(1.0f));
        station.tickOrders(level);
        check(waiting.state == StorageOrder.State.WAITING && waiting.note.contains("almost empty"), "Een auto met 1% in zijn cel kreeg"
                + " de bestelling: " + waiting.state + " \"" + waiting.note + "\"");
        // Een volle cel, maar hij staat een blok naast zijn thuisplek: wachten (zijn programma is voor die plek geschreven).
        car.swapCell(EnergyCellItem.withCharge(EnergyCellItem.FULL));
        car.moveTo(car.getX() + 1, car.getY(), car.getZ());
        station.tickOrders(level);
        check(waiting.state == StorageOrder.State.WAITING && waiting.note.contains("home spot") && !car.brain().busy(),
                "Een auto naast zijn thuisplek kreeg de bestelling: " + waiting.state + " \"" + waiting.note + "\"");
        car.moveTo(car.getX() - 1, car.getY(), car.getZ());
        check(station.atHome(car), "Terug op zijn plek staat de auto niet thuis");
        // Losmaken terwijl hij bezig is: de bestelling wacht weer op een auto (ze mislukt niet).
        waiting.state = StorageOrder.State.RUNNING;
        waiting.car = car.link();
        Storages.handleAction(alice, station, StorageActionPayload.link(car.link(), false));
        check(waiting.state == StorageOrder.State.WAITING && waiting.car == null, "Na losmaken wacht de bestelling niet weer: " + waiting.state);
        Storages.handleAction(alice, station, StorageActionPayload.link(car.link(), true));
        check(station.linked(car.link()), "Alice kon haar auto niet opnieuw koppelen");
        // De auto is intussen van Bob (opgepakt in diens zone en weer neergezet): de bestelling van Alice draait nooit het
        // programma van Bob met de rechten van Alice.
        car.setOwner(bob);
        station.tickOrders(level);
        check(waiting.state == StorageOrder.State.WAITING && waiting.note.contains("may not control") && !car.brain().busy(),
                "De auto van een ander kreeg de bestelling: " + waiting.state + " \"" + waiting.note + "\"");
        state = Storages.state(alice, station);
        check(state.stock().size() == 1 && state.stock().getFirst().count() == 9 && state.orders().size() == 1,
                "Het scherm toont niet de 9 ijzer en de bestelling");
        Storages.handleAction(bob, station, StorageActionPayload.cancel(station.orders().getFirst().id));
        check(station.orders().size() == 1, "Bob haalde een bestelling van Alice weg");
        Storages.handleAction(alice, station, StorageActionPayload.cancel(station.orders().getFirst().id));
        check(station.orders().isEmpty(), "Alice kon haar wachtende bestelling niet weghalen");
        // Shift-klik: alleen op het tabblad Store.
        StorageMenu menu = new StorageMenu(1, alice.getInventory(), station);
        alice.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 16));
        int first = StorageInterfaceBlockEntity.IN_SLOTS + StorageInterfaceBlockEntity.OUT_SLOTS;
        menu.quickMoveStack(alice, first);
        check(station.in().isEmpty(), "Shift-klik legde iets in de IN-vakjes terwijl je niet op het tabblad Store stond");
        menu.clickMenuButton(alice, StorageMenu.TAB_STORE);
        menu.quickMoveStack(alice, first);
        check(count(station.in(), Items.COBBLESTONE) == 16 && alice.getInventory().getItem(9).isEmpty(), "Shift-klik op Store legde de stenen niet in");
        // In de afhaalvakjes leg je zelf niets.
        check(!menu.getSlot(StorageInterfaceBlockEntity.IN_SLOTS).mayPlace(new ItemStack(Items.DIRT)), "Je kunt iets in een afhaalvakje leggen");
        // Een heel grote opslag: wat niet in één bericht past, laat de server weg (anders verbreekt Minecraft de verbinding).
        List<StorageStatePayload.Entry> many = new java.util.ArrayList<>();
        for (Item item : List.of(Items.STONE, Items.DIRT, Items.OAK_LOG, Items.GLASS, Items.SAND, Items.GRAVEL)) {
            many.add(new StorageStatePayload.Entry(new ItemStack(item), 64, 1));
        }
        int all = Storages.fitting(many, level.registryAccess(), Storages.MAX_STOCK_BYTES);
        int some = Storages.fitting(many, level.registryAccess(), 12);
        check(all == 6 && some > 0 && some < 6 && Storages.fitting(List.of(), level.registryAccess(), 12) == 0,
                "Van 6 soorten passen er " + all + " in een groot bericht en " + some + " in een bericht van 12 bytes");
        check(Storages.state(alice, station).more() == 0, "Een kleine opslag zegt dat er soorten ontbreken");
        helper.succeed();
    }

    // ================================================================ De Chest Sensor

    /**
     * Jouw wens: "een chest sensor die je op elke storage (hopper, barrel, chest...) kunt plaatsen om zo een live overzicht
     * te hebben; deze sensoren moeten geen stroom hebben". Hier hangt de sensor draadloos aan een Router, net als de
     * interface: zonder kabel en zonder stroom voor de sensor. Wat je in de kist verandert, ziet de interface. Op een blok
     * zonder binnenkant blijft een sensor niet zitten, en haal je de kist weg, dan valt hij eraf.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void chest_sensor_shows_a_chest_live(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stationPos = new BlockPos(4, 2, 4);
        helper.setBlock(stationPos, StorageRegistry.STORAGE_INTERFACE.get());
        StorageInterfaceBlockEntity station = (StorageInterfaceBlockEntity) helper.getBlockEntity(stationPos);
        BlockPos chest = new BlockPos(12, 2, 8);
        helper.setBlock(chest, Blocks.CHEST);
        ((Container) helper.getBlockEntity(chest)).setItem(5, new ItemStack(Items.GOLD_INGOT, 3));
        BlockPos hopper = new BlockPos(12, 2, 12);
        helper.setBlock(hopper, Blocks.HOPPER);
        ((Container) helper.getBlockEntity(hopper)).setItem(0, new ItemStack(Items.GOLD_INGOT, 2));
        // Sensors bovenop de kist en de trechter.
        helper.setBlock(chest.above(), StorageRegistry.CHEST_SENSOR.get().defaultBlockState().setValue(ChestSensorBlock.FACING, Direction.UP));
        helper.setBlock(hopper.above(), StorageRegistry.CHEST_SENSOR.get().defaultBlockState().setValue(ChestSensorBlock.FACING, Direction.UP));
        ItemKind gold = ItemKind.of(new ItemStack(Items.GOLD_INGOT));
        check(station.stock(level).count(gold) == 0, "Een sensor zonder netwerk telt mee");
        // Een Router in het midden: de interface en de twee sensors eraan (zonder kabels).
        BlockPos routerPos = new BlockPos(8, 2, 8);
        helper.setBlock(routerPos, ModBlocks.ROUTER.get());
        ((RouterBlockEntity) helper.getBlockEntity(routerPos)).updateOnline(level);
        BlockPos router = helper.absolutePos(routerPos);
        check(Routers.link(level, router, helper.absolutePos(stationPos)) && Routers.link(level, router, helper.absolutePos(chest.above()))
                && Routers.link(level, router, helper.absolutePos(hopper.above())), "De router verbond de interface en de sensors niet");
        helper.runAfterDelay(12, () -> {
            StorageInterfaceBlockEntity.Stock stock = station.stock(level);
            check(stock.count(gold) == 5 && stock.sensors() == 2 && stock.storages() == 2, "Via de router ziet de interface "
                    + stock.count(gold) + " goud in " + stock.storages() + " kisten (" + stock.sensors() + " sensors)");
            ((Container) helper.getBlockEntity(chest)).setItem(6, new ItemStack(Items.GOLD_INGOT, 10));
        });
        helper.runAfterDelay(26, () -> {
            check(station.stock(level).count(gold) == 15, "Wat er in de kist bij kwam, ziet de interface niet: " + station.stock(level).count(gold));
            // Op een blok zonder binnenkant blijft een sensor niet zitten.
            BlockPos stone = new BlockPos(6, 2, 12);
            helper.setBlock(stone, Blocks.STONE);
            check(!StorageRegistry.CHEST_SENSOR.get().defaultBlockState().setValue(ChestSensorBlock.FACING, Direction.UP)
                    .canSurvive(level, helper.absolutePos(stone.above())), "Een Chest Sensor blijft op een steen zitten");
            // De kist weg: de sensor valt eraf.
            helper.setBlock(chest, Blocks.AIR);
            helper.assertBlockPresent(Blocks.AIR, chest.above());
        });
        helper.runAfterDelay(40, () -> {
            check(station.stock(level).count(gold) == 2, "Na het weghalen van de kist telt de interface nog " + station.stock(level).count(gold)
                    + " goud (verwacht 2: de trechter)");
            helper.succeed();
        });
    }

    // ================================================================ De Charging Pad

    /**
     * Jouw keuze: een laadplaat, zodat de opslag-auto zichzelf oplaadt. De plaat laadt de cel van de auto die erop staat met
     * stroom van het net ({@code padRate} per tick), en zijn strepen gloeien dan. Zonder stroom, zonder auto of met een
     * auto zonder cel doet hij niets.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void charging_pad_charges_a_car(GameTestHelper helper) {
        BlockPos powered = new BlockPos(6, 2, 6);
        BlockPos dead = new BlockPos(10, 2, 6);
        helper.setBlock(powered, StorageRegistry.CHARGING_PAD.get());
        helper.setBlock(dead, StorageRegistry.CHARGING_PAD.get());
        helper.setBlock(powered.east(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(powered.east())).fill(20_000);
        RcCarEntity car = car(helper, powered, "");
        car.swapCell(EnergyCellItem.withCharge(10.0f));
        RcCarEntity unpowered = car(helper, dead, "");
        unpowered.swapCell(EnergyCellItem.withCharge(10.0f));
        helper.runAfterDelay(60, () -> {
            check(car.cellPercent() > 15.0f, "Na drie seconden op de plaat zit de cel op " + car.cellPercent() + "% (begon op 10%)");
            check(car.blockPosition().equals(helper.absolutePos(powered)), "De auto staat niet meer op de plaat");
            helper.assertBlockProperty(powered, ChargingPadBlock.CHARGING, true);
            check(Math.abs(unpowered.cellPercent() - 10.0f) < 0.01f, "Een plaat zonder stroom laadde de cel: " + unpowered.cellPercent());
            helper.assertBlockProperty(dead, ChargingPadBlock.CHARGING, false);
            helper.succeed();
        });
    }
}
