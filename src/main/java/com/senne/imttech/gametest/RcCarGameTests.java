package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.car.CarMove;
import com.senne.imttech.car.CarProgram;
import com.senne.imttech.car.DrillHead;
import com.senne.imttech.car.RcCarData;
import com.senne.imttech.car.RcCarEntity;
import com.senne.imttech.car.RcCarItem;
import com.senne.imttech.car.RcCars;
import com.senne.imttech.car.RcControllerItem;
import com.senne.imttech.car.RcLink;
import com.senne.imttech.client.os.CodeCompletion;
import com.senne.imttech.logic.program.LogicException;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.logic.program.ScriptPrinter;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.weapons.EnergyCellItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de RC Car (hoofdstuk 108): de taal van zijn programma, en wat hij in de wereld doet: een gang mijnen, een
 * kamer uithakken, blokken plaatsen, zijn energiecel, zijn boorkop, zijn kofferbak, oppakken en neerzetten, en de RC
 * Controller.
 *
 * In deze arena is y = 1 de vloer: wie rijdt, staat op y = 2. De auto's kijken naar het noorden (naar kleinere z).
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class RcCarGameTests {
    private RcCarGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * Een auto met een volle energiecel, die naar het noorden kijkt, met dit programma (de code moet kloppen). Hij heeft
     * de netherite boorkop (de volle snelheid), tenzij een test hem een andere geeft: zo duren de tests niet onnodig lang.
     */
    private static RcCarEntity car(GameTestHelper helper, BlockPos pos, String code) {
        RcCarEntity car = helper.spawn(ModEntities.RC_CAR.get(), pos);
        car.setDrill(DrillHead.NETHERITE);
        car.body().setFacing(Direction.NORTH);
        car.swapCell(EnergyCellItem.withCharge(EnergyCellItem.FULL));
        String problem = car.setSource(code);
        check(problem == null, "De code van de test klopt niet: " + problem);
        return car;
    }

    /** Waar dit is, gezien vanuit de test. */
    private static BlockPos rel(GameTestHelper helper, BlockPos abs) {
        return abs.subtract(helper.absolutePos(BlockPos.ZERO));
    }

    /** Staat hij precies in het midden van dit blok (relatief in de test)? */
    private static void checkAt(GameTestHelper helper, RcCarEntity car, BlockPos relative, String what) {
        BlockPos abs = helper.absolutePos(relative);
        check(car.blockPosition().equals(abs), what + ": hij staat op " + rel(helper, car.blockPosition()) + ", niet op " + relative);
        check(Math.abs(car.getX() - (abs.getX() + 0.5)) < 0.02 && Math.abs(car.getZ() - (abs.getZ() + 0.5)) < 0.02,
                what + ": hij staat niet in het midden van het blok (" + car.getX() + ", " + car.getZ() + ")");
    }

    /** Vult een balk van de arena met dit blok. */
    private static void fill(GameTestHelper helper, BlockPos a, BlockPos b, net.minecraft.world.level.block.Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(a, b)) {
            helper.setBlock(pos, block);
        }
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
     * De taal van een RC Car. Een nieuwe auto heeft geen programma (jouw wens: "ik wil dat je de rc car vanaf 0 moet
     * programmeren; er mag geen voorbestaande code zijn"): een leeg programma klopt, en heeft geen functies en geen
     * Start. De twee voorbeelden van How it works (strip_mine en room, uit jouw eerste wens) kloppen. De woorden van een
     * auto werken alleen in een autoprogramma, en een auto kent geen relais, geen every en geen robot.*; mine neemt geen
     * getal meer (hoofdstuk 113: daar is de arm voor); place neemt niets of een soort blok; block() geeft een waarde.
     * Ctrl+Spatie stelt in een autoprogramma de woorden van de auto voor, en geen relais.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void rc_car_language(GameTestHelper helper) {
        check(RcCarData.DEFAULT.source().isEmpty(), "Een nieuwe auto heeft al code: " + RcCarData.DEFAULT.source());
        check(CarProgram.problem(CarProgram.EMPTY) == null, "Een leeg programma geeft een fout: " + CarProgram.problem(CarProgram.EMPTY));
        LogicProgram empty = CarProgram.compile(CarProgram.EMPTY);
        check(CarProgram.functions(empty).isEmpty() && !CarProgram.hasMain(empty), "Een leeg programma heeft functies of een Start");
        check(CarProgram.problem("# only a note\n\n") == null, "Een programma met alleen uitleg geeft een fout");
        LogicProgram examples = CarProgram.compile(CarProgram.EXAMPLE_STRIP_MINE + "\n" + CarProgram.EXAMPLE_ROOM);
        check(CarProgram.functions(examples).equals(List.of("strip_mine", "dig", "room")),
                "De voorbeelden hebben niet de functies strip_mine, dig en room, maar " + CarProgram.functions(examples));
        check(!CarProgram.hasMain(examples), "De voorbeelden hebben een when started, dat hoort niet");

        // Een def met parameters is een hulpje, geen functie op de controller; when started is het hoofdprogramma.
        LogicProgram mixed = CarProgram.compile("""
                steps = 3
                when started:
                    lane(steps)
                def lane(int length):
                    for i in range(length):
                        mine()
                        forward(1)
                def home():
                    unload()
                """);
        check(CarProgram.functions(mixed).equals(List.of("home")), "Alleen home is een functie, niet " + CarProgram.functions(mixed));
        check(CarProgram.hasMain(mixed), "when started telde niet als hoofdprogramma");

        // Heen en terug: wat de printer schrijft, leest de parser weer precies zo.
        String code = """
                def build():
                    mine()
                    mine_up()
                    mine_down()
                    arm_up()
                    arm_up(2)
                    arm_down()
                    place()
                    place_up("cobblestone")
                    place_down()
                    collect()
                    unload()
                    if block() == "stone" and car.battery > 10 and not car.full:
                        say("Stone, " + car.items + " items, " + car.blocks + " blocks")
                    while block_up() != "air" or block_down() == "air":
                        stop()
                """;
        String printed = ScriptPrinter.print(ScriptParser.parse(code, ProgramKind.CAR));
        check(printed.equals(code), "De code van een auto kwam niet precies terug:\n" + printed);

        // Woorden van een auto werken alleen in een autoprogramma...
        expectProblem("when started:\n    mine()\n", ProgramKind.ROBOT, "mine() only works in a car program, not in a robot program");
        expectProblem("when started:\n    place()\n", ProgramKind.DRONE, "place() only works in a car program");
        expectProblem("when every(5):\n    if block() == \"air\":\n        say(\"x\")\n", ProgramKind.SERVER,
                "block(...) only works in a car program");
        expectProblem("when every(5):\n    if car.blocked:\n        say(\"x\")\n", ProgramKind.SERVER, "car.blocked only works in a car program");
        // ...en een auto kent geen relais, geen logboek, geen every, geen pinnen en geen robot.*.
        expectProblem("def a():\n    on(\"Lamp\")\n", ProgramKind.CAR, "on() only works in");
        expectProblem("def a():\n    log(\"x\")\n", ProgramKind.CAR, "log() only works in");
        expectProblem("when every(5):\n    mine()\n", ProgramKind.CAR, "every(...) only works in");
        expectProblem("def a():\n    goto(1, 2, 3)\n", ProgramKind.CAR, "goto() only works in");
        expectProblem("def a():\n    up(1)\n", ProgramKind.CAR, "up() only works in a drone program");
        expectProblem("def a():\n    if robot.blocked:\n        mine()\n", ProgramKind.CAR, "robot.blocked only works in a robot program");
        expectProblem("def a():\n    if read(1):\n        mine()\n", ProgramKind.CAR, "read(...) only works in");
        // De getallen en waarden.
        expectProblem("def a():\n    mine(2)\n", ProgramKind.CAR, "mine() breaks one block now, without a number");
        expectProblem("def a():\n    mine_up(2)\n", ProgramKind.CAR, "raise the arm: arm_up() and then mine()");
        expectProblem("def a():\n    arm_up(3)\n", ProgramKind.CAR, "arm_up takes 1 to 2 blocks, not 3");
        expectProblem("def a():\n    arm_down(0)\n", ProgramKind.CAR, "arm_down takes 1 to 2 blocks");
        expectProblem("def a():\n    place(3)\n", ProgramKind.CAR, "place: the block must be text");
        expectProblem("def a():\n    place(\"a\", \"b\")\n", ProgramKind.CAR, "place needs 0 or 1 values");
        expectProblem("def a():\n    block()\n", ProgramKind.CAR, "block(...) gives a value");
        expectProblem("def a():\n    if block(1) == \"stone\":\n        mine()\n", ProgramKind.CAR, "block() takes nothing between the brackets");
        expectProblem("def a():\n    if block() == 3:\n        mine()\n", ProgramKind.CAR, "You cannot compare text with a number");
        expectProblem("def a():\n    block = 3\n", ProgramKind.CAR, "already a word of the language");
        check(problem("def a():\n    forward(64)\n    back(2)\n    turn_left()\n    turn_right()\n    wait(1)\n    say(\"hi\")\n", ProgramKind.CAR)
                .isEmpty(), "Rijden, wachten en say horen in een autoprogramma te werken");

        // Ctrl+Spatie op een ingesprongen regel: de woorden van de auto, en geen relais.
        CodeCompletion.Data data = new CodeCompletion.Data(List.of("Lamp"), List.of(), List.of(), ProgramKind.CAR);
        List<String> labels = CodeCompletion.complete(List.of("def a():", "    "), 1, 4, data).items().stream()
                .map(CodeCompletion.Item::label).toList();
        for (String word : List.of("mine()", "mine_up()", "mine_down()", "place(...)", "collect()", "unload()", "forward(3)", "turn_left()",
                "arm_up()", "arm_down()", "take(\"...\")", "put(\"...\")", "check()")) {
            check(labels.contains(word), "Ctrl+Spatie stelt " + word + " niet voor in een autoprogramma: " + labels);
        }
        check(labels.stream().noneMatch(label -> label.startsWith("on(") || label.startsWith("pulse(") || label.startsWith("goto(")),
                "Ctrl+Spatie stelt in een autoprogramma een relais of goto voor: " + labels);
        List<String> values = CodeCompletion.complete(List.of("def a():", "    if "), 1, 7, data).items().stream()
                .map(CodeCompletion.Item::label).toList();
        for (String word : List.of("block()", "block_up()", "block_down()", "car.battery", "car.blocked", "car.full", "car.arm",
                "chest.free", "item(\"...\")", "chest.count(\"...\")", "trunk.count(\"...\")", "in chest()", "in trunk()")) {
            check(values.contains(word), "Ctrl+Spatie stelt " + word + " niet voor na if: " + values);
        }
        check(values.stream().noneMatch(label -> label.startsWith("is_on") || label.startsWith("robot.")),
                "Ctrl+Spatie stelt in een autoprogramma is_on of robot.* voor: " + values);
        helper.succeed();
    }

    // ================================================================ Mijnen

    /**
     * Jouw voorbeeld: "herhaal: breek voor jou, stap naar voor, breek boven jou... dit maakt dan een lange strip mine".
     * Vier keer: de auto staat vier blokken verder, de gang is 1 breed en 2 hoog, niet langer, niet hoger en niet breder,
     * de acht stenen liggen als cobblestone in zijn kofferbak, en het kostte stroom.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void rc_car_strip_mines_a_tunnel(GameTestHelper helper) {
        BlockPos start = new BlockPos(7, 2, 12);
        fill(helper, new BlockPos(6, 2, 6), new BlockPos(8, 4, 11), Blocks.STONE);
        RcCarEntity car = car(helper, start, """
                def strip():
                    for i in range(4):
                        mine()
                        forward(1)
                        mine_up()
                """);
        float before = car.cellPercent();
        check(car.brain().startFunction("strip"), "De functie strip begon niet");
        check(car.brain().busy() && car.brain().job().equals("strip"), "De auto zegt niet dat hij met strip bezig is");
        helper.succeedWhen(() -> {
            check(!car.brain().busy(), "De auto is nog bezig");
            checkAt(helper, car, start.north(4), "Na vier stappen");
            for (int i = 1; i <= 4; i++) {
                helper.assertBlockPresent(Blocks.AIR, start.north(i));
                helper.assertBlockPresent(Blocks.AIR, start.north(i).above());
            }
            helper.assertBlockPresent(Blocks.STONE, start.north(5));
            helper.assertBlockPresent(Blocks.STONE, start.north(4).above(2));
            helper.assertBlockPresent(Blocks.STONE, start.north(2).east());
            helper.assertBlockPresent(Blocks.STONE, start.north(2).west());
            check(car.trunk().countItem(Items.COBBLESTONE) == 8, "In de kofferbak liggen " + car.trunk().countItem(Items.COBBLESTONE)
                    + " cobblestone in plaats van 8");
            check(car.cellPercent() < before - 1.0f, "Acht stenen breken en vier blokken rijden kostte bijna geen stroom: van "
                    + before + " naar " + car.cellPercent());
            check(!car.body().blocked(), "car.blocked is waar na een gang die gewoon lukte");
        });
    }

    /**
     * Jouw voorbeeld: "een functie die een kamer van 5x3x5 uithakt: als je die kiest, voert hij de code uit vanaf de plek
     * waar hij staat". De functie room zoals How it works hem als voorbeeld toont (CarProgram.EXAMPLE_ROOM), in een blok
     * steen: daarna is er een kamer van precies 5 breed, 3 hoog en 5 diep, de muren, de vloer en het plafond staan er nog,
     * de 75 stenen liggen in de kofferbak, en de auto staat weer waar hij begon met zijn neus dezelfde kant op. Sinds
     * hoofdstuk 113 begint hij vóór de kamer (in de deuropening) en breekt hij de drie blokken hoog met zijn arm.
     */
    @GameTest(template = ARENA, timeoutTicks = 6000)
    public static void rc_car_carves_a_room(GameTestHelper helper) {
        BlockPos start = new BlockPos(4, 2, 12);
        fill(helper, new BlockPos(3, 2, 6), new BlockPos(9, 5, 12), Blocks.STONE);
        helper.setBlock(start, Blocks.AIR);
        RcCarEntity car = car(helper, start, CarProgram.EXAMPLE_ROOM);
        check(car.functions().equals(List.of("dig", "room")), "De auto toont niet de functies dig en room, maar " + car.functions());
        check(car.brain().startFunction("room"), "De functie room begon niet");
        helper.succeedWhen(() -> {
            check(!car.brain().busy(), "De auto is nog bezig met de kamer");
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(4, 2, 7), new BlockPos(8, 4, 11))) {
                helper.assertBlockPresent(Blocks.AIR, pos);
            }
            // De muren (links, rechts, voor, achter), het plafond en de vloer staan er nog, ook boven de deuropening.
            for (BlockPos wall : List.of(new BlockPos(3, 3, 9), new BlockPos(9, 3, 9), new BlockPos(6, 3, 6), new BlockPos(6, 3, 12),
                    new BlockPos(6, 5, 9), new BlockPos(4, 5, 7), new BlockPos(8, 5, 11), new BlockPos(4, 3, 12), new BlockPos(5, 2, 12))) {
                helper.assertBlockPresent(Blocks.STONE, wall);
            }
            check(car.trunk().countItem(Items.COBBLESTONE) == 75, "In de kofferbak liggen " + car.trunk().countItem(Items.COBBLESTONE)
                    + " cobblestone in plaats van 75");
            check(car.arm() == 0, "Zijn arm staat na de kamer niet weer beneden: " + car.arm());
            checkAt(helper, car, start, "Na de kamer");
            check(Math.abs(Mth.wrapDegrees(car.getYRot() - Direction.NORTH.toYRot())) < 1, "Hij kijkt niet weer naar het noorden");
            check(!car.body().blocked(), "car.blocked is waar na een kamer die gewoon lukte");
        });
    }

    /**
     * Met de ijzeren boorkop (de kop van een nieuwe auto) is zijn boor ijzeren gereedschap: steen breekt hij, obsidiaan
     * (dat een diamanten pickaxe vraagt) en bedrock niet. Dan is car.blocked waar, staat het blok er nog, en zegt zijn
     * scherm welke kop hij nodig heeft. Lucht en water zijn geen probleem: daar valt niets te breken. Met de netherite kop
     * breekt hij obsidiaan wel (titanium ook; steel niet), bedrock nooit.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void rc_car_cannot_break_everything(GameTestHelper helper) {
        String code = """
                hit = false
                tried = 0
                def dig():
                    mine()
                    hit = car.blocked
                    tried = tried + 1
                """;
        BlockPos stoneStart = new BlockPos(3, 2, 8);
        BlockPos obsidianStart = new BlockPos(6, 2, 8);
        BlockPos bedrockStart = new BlockPos(9, 2, 8);
        BlockPos airStart = new BlockPos(12, 2, 8);
        helper.setBlock(stoneStart.north(), Blocks.STONE);
        helper.setBlock(obsidianStart.north(), Blocks.OBSIDIAN);
        helper.setBlock(bedrockStart.north(), Blocks.BEDROCK);
        RcCarEntity stone = car(helper, stoneStart, code);
        RcCarEntity obsidian = car(helper, obsidianStart, code);
        RcCarEntity bedrock = car(helper, bedrockStart, code);
        RcCarEntity air = car(helper, airStart, code);
        for (RcCarEntity car : List.of(stone, obsidian, bedrock, air)) {
            car.setDrill(DrillHead.IRON);
        }
        // De netherite kop: obsidiaan lukt (dat duurt een seconde of twaalf), bedrock niet.
        BlockPos strongStart = new BlockPos(6, 2, 13);
        BlockPos strongBedrockStart = new BlockPos(9, 2, 13);
        helper.setBlock(strongStart.north(), Blocks.OBSIDIAN);
        helper.setBlock(strongBedrockStart.north(), Blocks.BEDROCK);
        RcCarEntity strong = car(helper, strongStart, code);
        RcCarEntity strongBedrock = car(helper, strongBedrockStart, code);
        check(strong.drill() == DrillHead.NETHERITE, "De auto van de test heeft niet de netherite kop");
        net.minecraft.world.level.block.state.BlockState obsidianBlock = Blocks.OBSIDIAN.defaultBlockState();
        check(!DrillHead.IRON.canDrill(obsidianBlock) && !DrillHead.STEEL.canDrill(obsidianBlock)
                        && DrillHead.TITANIUM.canDrill(obsidianBlock) && DrillHead.NETHERITE.canDrill(obsidianBlock),
                "Obsidiaan hoort alleen met de titanium en de netherite kop te lukken");
        check(DrillHead.weakestFor(obsidianBlock) == DrillHead.TITANIUM && DrillHead.weakestFor(Blocks.STONE.defaultBlockState()) == DrillHead.IRON,
                "De zwakste kop voor obsidiaan hoort titanium te zijn, en voor steen ijzer");
        List<RcCarEntity> all = List.of(stone, obsidian, bedrock, air, strong, strongBedrock);
        for (RcCarEntity car : all) {
            check(car.brain().startFunction("dig"), "De functie dig begon niet");
        }
        helper.succeedWhen(() -> {
            for (RcCarEntity car : all) {
                check(Double.valueOf(1).equals(car.brain().global("tried")), "Een auto is nog niet klaar met dig");
            }
            check(Boolean.FALSE.equals(strong.brain().global("hit")), "Obsidiaan breken met de netherite kop gaf car.blocked");
            helper.assertBlockPresent(Blocks.AIR, strongStart.north());
            check(strong.trunk().countItem(Items.OBSIDIAN) == 1, "Het obsidiaan ligt niet in de kofferbak");
            check(Boolean.TRUE.equals(strongBedrock.brain().global("hit")), "Bedrock gaf met de netherite kop geen car.blocked");
            helper.assertBlockPresent(Blocks.BEDROCK, strongBedrockStart.north());
            check(Boolean.FALSE.equals(stone.brain().global("hit")), "Steen breken gaf car.blocked");
            helper.assertBlockPresent(Blocks.AIR, stoneStart.north());
            check(Boolean.TRUE.equals(obsidian.brain().global("hit")), "Obsidiaan gaf geen car.blocked");
            helper.assertBlockPresent(Blocks.OBSIDIAN, obsidianStart.north());
            check(obsidian.note().contains("too hard for its Iron Drill Head") && obsidian.note().contains("Titanium"),
                    "Zijn scherm zegt niet waarom obsidiaan niet lukt en welke kop hij nodig heeft: " + obsidian.note());
            check(Boolean.TRUE.equals(bedrock.brain().global("hit")), "Bedrock gaf geen car.blocked");
            helper.assertBlockPresent(Blocks.BEDROCK, bedrockStart.north());
            check(Boolean.FALSE.equals(air.brain().global("hit")), "Lucht 'breken' gaf car.blocked");
            check(obsidian.trunk().isEmpty() && bedrock.trunk().isEmpty(), "Er ligt iets in de kofferbak van een auto die niets brak");
        });
    }

    /**
     * Een beschermde zone geldt ook voor een RC Car: de auto van een vreemde breekt en plaatst daar niets (car.blocked),
     * de auto van de eigenaar van de zone wel. Zo is een auto geen manier om in te breken. Het telt wie de klus startte:
     * ook de auto van de eigenaar doet er niets als een vreemde hem bestuurt (gevonden door de review).
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "zones")
    public static void rc_car_respects_zones(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 7), "car-zone-owner");
        ServerPlayer outsider = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(14, 3, 14), "car-zone-outsider");
        SecurityCoreBlockEntity core = ZoneGameTests.zoneCore(helper, owner);
        String code = """
                hit = false
                tried = 0
                def dig():
                    mine()
                    hit = car.blocked
                    place_up()
                    tried = tried + 1
                """;
        BlockPos strangerStart = new BlockPos(7, 2, 9);
        BlockPos ownerStart = new BlockPos(10, 2, 9);
        helper.setBlock(strangerStart.north(), Blocks.STONE);
        helper.setBlock(ownerStart.north(), Blocks.STONE);
        RcCarEntity strangerCar = car(helper, strangerStart, code);
        strangerCar.setOwner(outsider);
        strangerCar.trunk().addItem(new ItemStack(Items.COBBLESTONE, 4));
        RcCarEntity ownerCar = car(helper, ownerStart, code);
        ownerCar.setOwner(owner);
        ownerCar.trunk().addItem(new ItemStack(Items.COBBLESTONE, 4));
        // De auto van de eigenaar van de zone, maar de vreemde startte de klus: het telt wie op de knop drukte.
        BlockPos drivenStart = new BlockPos(8, 2, 12);
        helper.setBlock(drivenStart.north(), Blocks.STONE);
        RcCarEntity drivenCar = car(helper, drivenStart, code);
        drivenCar.setOwner(owner);
        drivenCar.trunk().addItem(new ItemStack(Items.COBBLESTONE, 4));
        check(strangerCar.brain().startFunction("dig") && ownerCar.brain().startFunction("dig")
                && drivenCar.brain().startFunction("dig", outsider), "De functie dig begon niet");
        helper.succeedWhen(() -> {
            check(Double.valueOf(1).equals(strangerCar.brain().global("tried")) && Double.valueOf(1).equals(ownerCar.brain().global("tried"))
                            && Double.valueOf(1).equals(drivenCar.brain().global("tried")), "Een auto is nog niet klaar");
            check(Boolean.TRUE.equals(drivenCar.brain().global("hit")), "De auto van de eigenaar brak een blok in de zone terwijl een vreemde hem bestuurde");
            helper.assertBlockPresent(Blocks.STONE, drivenStart.north());
            helper.assertBlockPresent(Blocks.AIR, drivenStart.above());
            check(drivenCar.trunk().countItem(Items.COBBLESTONE) == 4, "De auto die een vreemde bestuurde, plaatste een blok in de zone");
            check(Boolean.TRUE.equals(strangerCar.brain().global("hit")), "De auto van een vreemde brak een blok in de zone");
            helper.assertBlockPresent(Blocks.STONE, strangerStart.north());
            helper.assertBlockPresent(Blocks.AIR, strangerStart.above());
            check(strangerCar.trunk().countItem(Items.COBBLESTONE) == 4, "De auto van een vreemde plaatste een blok in de zone");
            check(Boolean.FALSE.equals(ownerCar.brain().global("hit")), "De auto van de eigenaar mocht niet breken in zijn eigen zone");
            helper.assertBlockPresent(Blocks.AIR, ownerStart.north());
            helper.assertBlockPresent(Blocks.COBBLESTONE, ownerStart.above());
            core.setZoneBoxes(List.of());
        });
    }

    /**
     * Valt er grind in het gat, dan wacht de auto tot het ligt en breekt hij dat ook: na mine() is de plek echt vrij en
     * rijdt hij erin. Ook bij mine_up(): het grind valt dan tot op de auto zelf, en hij breekt zich vrij.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void rc_car_clears_falling_gravel(GameTestHelper helper) {
        BlockPos start = new BlockPos(4, 2, 9);
        helper.setBlock(start.north(), Blocks.STONE);
        helper.setBlock(start.north().above(), Blocks.GRAVEL);
        helper.setBlock(start.north().above(2), Blocks.GRAVEL);
        RcCarEntity forward = car(helper, start, """
                ok = false
                def dig():
                    mine()
                    forward(1)
                    ok = not car.blocked
                """);
        BlockPos under = new BlockPos(10, 2, 9);
        helper.setBlock(under.above(), Blocks.STONE);
        helper.setBlock(under.above(2), Blocks.GRAVEL);
        helper.setBlock(under.above(3), Blocks.GRAVEL);
        RcCarEntity up = car(helper, under, """
                done = false
                def dig():
                    mine_up()
                    done = true
                """);
        check(forward.brain().startFunction("dig") && up.brain().startFunction("dig"), "De functie dig begon niet");
        helper.succeedWhen(() -> {
            check(Boolean.TRUE.equals(forward.brain().global("ok")), "Na mine() kon de auto niet vooruit: er lag nog grind");
            checkAt(helper, forward, start.north(), "Na mine() en forward(1)");
            for (int y = 0; y <= 2; y++) {
                helper.assertBlockPresent(Blocks.AIR, start.north().above(y));
            }
            int got = forward.trunk().countItem(Items.COBBLESTONE) + forward.trunk().countItem(Items.GRAVEL)
                    + forward.trunk().countItem(Items.FLINT);
            check(got == 3, "In de kofferbak liggen " + got + " voorwerpen in plaats van 3 (de steen en twee keer grind of vuursteen)");

            check(Boolean.TRUE.equals(up.brain().global("done")), "mine_up() onder grind is nog niet klaar");
            for (int y = 0; y <= 3; y++) {
                helper.assertBlockPresent(Blocks.AIR, under.above(y));
            }
            checkAt(helper, up, under, "Na mine_up() onder grind");
            check(helper.getLevel().noCollision(up), "De auto zit nog vast in het grind dat op hem viel");
        });
    }

    /**
     * Rijden over oneffen grond: de auto rijdt een blok op en weer af (zoals een guard robot stapt), en stopt voor een
     * muur van twee hoog: dan is car.blocked waar en staat hij in het midden van het blok ervoor.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void rc_car_drives_up_and_down_a_step(GameTestHelper helper) {
        BlockPos start = new BlockPos(7, 2, 12);
        helper.setBlock(start.north(2), Blocks.STONE);
        helper.setBlock(start.north(3), Blocks.STONE);
        helper.setBlock(start.north(6), Blocks.STONE);
        helper.setBlock(start.north(6).above(), Blocks.STONE);
        RcCarEntity car = car(helper, start, """
                top = 0
                hit = false
                done = false
                def go():
                    forward(3)
                    top = car.y
                    forward(2)
                    hit = car.blocked
                    forward(3)
                    done = car.blocked
                """);
        check(car.brain().startFunction("go"), "De functie go begon niet");
        helper.succeedWhen(() -> {
            check(!car.brain().busy(), "De auto is nog bezig");
            check(Double.valueOf(helper.absolutePos(start.above()).getY()).equals(car.brain().global("top")),
                    "Na drie blokken stond hij niet boven op de steen: y = " + car.brain().global("top"));
            check(Boolean.FALSE.equals(car.brain().global("hit")), "Een blok op en af rijden gaf car.blocked");
            check(Boolean.TRUE.equals(car.brain().global("done")), "Voor de muur van twee hoog gaf hij geen car.blocked");
            checkAt(helper, car, start.north(5), "Voor de muur");
        });
    }

    // ================================================================ De boorkop

    /**
     * Jouw wens: "zorg dat je de drill head kunt upgraden, zodat een basic drill head wel even duurt om iets te hakken;
     * netherite is de snelheid dat die nu hakt". Vier auto's, elk met een andere kop, breken elk één blok steen: netherite
     * in 8 ticks (zo snel als jij met een ijzeren pickaxe: de snelheid van de auto vóór er koppen waren), titanium in 12,
     * steel in 19, ijzer in 30. Stroom kost het voor elke kop evenveel.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void rc_car_drill_heads_set_the_speed(GameTestHelper helper) {
        DrillHead[] heads = DrillHead.values();
        RcCarEntity[] cars = new RcCarEntity[heads.length];
        BlockPos[] blocks = new BlockPos[heads.length];
        long[] brokenAt = new long[heads.length];
        for (int i = 0; i < heads.length; i++) {
            BlockPos start = new BlockPos(3 + 3 * i, 2, 9);
            blocks[i] = start.north();
            helper.setBlock(blocks[i], Blocks.STONE);
            cars[i] = car(helper, start, "def dig():\n    mine()\n");
            cars[i].setDrill(heads[i]);
            brokenAt[i] = -1;
        }
        // Even wachten tot ze staan (dan telt alleen het boren zelf), en dan allemaal tegelijk beginnen.
        long[] startedAt = {-1};
        helper.runAfterDelay(10, () -> {
            startedAt[0] = helper.getTick();
            for (RcCarEntity car : cars) {
                check(car.brain().startFunction("dig"), "De functie dig begon niet");
            }
        });
        helper.onEachTick(() -> {
            for (int i = 0; i < heads.length; i++) {
                if (brokenAt[i] < 0 && startedAt[0] >= 0 && helper.getBlockState(blocks[i]).isAir()) {
                    brokenAt[i] = helper.getTick() - startedAt[0];
                }
            }
        });
        helper.succeedWhen(() -> {
            for (int i = 0; i < heads.length; i++) {
                check(brokenAt[i] >= 0 && !cars[i].brain().busy(), "De auto met de kop " + heads[i] + " is nog bezig");
            }
            long iron = brokenAt[DrillHead.IRON.ordinal()];
            long steel = brokenAt[DrillHead.STEEL.ordinal()];
            long titanium = brokenAt[DrillHead.TITANIUM.ordinal()];
            long netherite = brokenAt[DrillHead.NETHERITE.ordinal()];
            String times = "ijzer " + iron + ", steel " + steel + ", titanium " + titanium + ", netherite " + netherite + " ticks";
            check(netherite >= 7 && netherite <= 11, "Netherite hoort steen in 8 ticks te breken (de snelheid van voorheen): " + times);
            check(iron >= 28 && iron <= 34, "IJzer hoort steen in 30 ticks te breken (een kwart van de snelheid): " + times);
            check(netherite < titanium && titanium < steel && steel < iron, "Een betere kop hoort sneller te zijn: " + times);
            check(Math.abs(cars[0].cellPercent() - cars[3].cellPercent()) < 0.001f, "De kop veranderde wat een blok aan stroom kost");
            for (RcCarEntity car : cars) {
                check(car.trunk().countItem(Items.COBBLESTONE) == 1, "De steen ligt niet in de kofferbak");
            }
        });
    }

    // ================================================================ Plaatsen

    /**
     * place("dirt") zet aarde vóór de auto (ook al ligt er eerst cobblestone in de kofferbak), place_down() zet een blok
     * onder hem (hij staat dan één hoger), place_up() erboven. Met een lege kofferbak: car.blocked.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void rc_car_places_blocks(GameTestHelper helper) {
        BlockPos start = new BlockPos(7, 2, 7);
        RcCarEntity car = car(helper, start, """
                left = -1
                empty = false
                def build():
                    place("dirt")
                    place_down()
                    place_up()
                    left = car.blocks
                    place()
                    empty = car.blocked
                """);
        car.trunk().addItem(new ItemStack(Items.COBBLESTONE, 2));
        car.trunk().addItem(new ItemStack(Items.DIRT, 1));
        car.trunk().addItem(new ItemStack(Items.DIAMOND, 3)); // geen blok: dat plaatst hij niet
        float before = car.cellPercent();
        check(car.brain().startFunction("build"), "De functie build begon niet");
        helper.succeedWhen(() -> {
            check(!car.brain().busy(), "De auto is nog bezig");
            helper.assertBlockPresent(Blocks.DIRT, start.north());
            helper.assertBlockPresent(Blocks.COBBLESTONE, start);
            helper.assertBlockPresent(Blocks.COBBLESTONE, start.above(2));
            checkAt(helper, car, start.above(), "Na place_down()");
            check(Double.valueOf(0).equals(car.brain().global("left")), "car.blocks is " + car.brain().global("left") + " in plaats van 0");
            check(Boolean.TRUE.equals(car.brain().global("empty")), "place() zonder blokken gaf geen car.blocked");
            check(car.trunk().countItem(Items.DIAMOND) == 3, "De diamanten zijn uit de kofferbak verdwenen");
            check(car.cellPercent() < before, "Plaatsen kostte geen stroom");
        });
    }

    // ================================================================ Stroom

    /**
     * Zonder energiecel doet de auto niets (de klus stopt meteen, het blok staat er nog). Met een bijna lege cel rijdt
     * hij tot de cel op is: twee blokken van de vier, en dan stopt de klus.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void rc_car_needs_an_energy_cell(GameTestHelper helper) {
        BlockPos noCellStart = new BlockPos(4, 2, 10);
        helper.setBlock(noCellStart.north(), Blocks.STONE);
        RcCarEntity noCell = car(helper, noCellStart, """
                def dig():
                    mine()
                    forward(1)
                """);
        noCell.swapCell(ItemStack.EMPTY);
        check(noCell.batteryFraction() < 0, "Zonder cel zegt de auto niet dat hij er geen heeft");
        check(noCell.brain().startFunction("dig"), "De functie dig begon niet");

        BlockPos lowStart = new BlockPos(10, 2, 12);
        RcCarEntity low = car(helper, lowStart, """
                def drive():
                    forward(4)
                """);
        // Precies genoeg voor twee blokken rijden.
        double cellEnergy = com.senne.imttech.config.ImtConfig.Weapons.CELL_CHARGE_ENERGY;
        float twoSteps = (float) (2 * com.senne.imttech.config.ImtConfig.RcCar.MOVE_COST / cellEnergy * EnergyCellItem.FULL);
        low.swapCell(EnergyCellItem.withCharge(twoSteps + 0.0001f));
        check(low.brain().startFunction("drive"), "De functie drive begon niet");
        helper.succeedWhen(() -> {
            check(!noCell.brain().busy(), "De auto zonder cel is nog bezig");
            helper.assertBlockPresent(Blocks.STONE, noCellStart.north());
            checkAt(helper, noCell, noCellStart, "Zonder cel");
            check(!low.brain().busy(), "De auto met de bijna lege cel is nog bezig");
            checkAt(helper, low, lowStart.north(2), "Met stroom voor twee blokken");
            check(low.cellPercent() < 0.01f, "De cel is niet leeg: " + low.cellPercent());
            check(low.batteryFraction() >= 0 && low.batteryFraction() <= 0.001f, "Je spel hoort een lege cel te zien, geen " + low.batteryFraction());
        });
    }

    // ================================================================ Kofferbak

    /** collect() raapt op wat er rond de auto ligt, unload() legt alles in de kist voor hem. Zonder kist: op de grond. */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void rc_car_collects_and_unloads(GameTestHelper helper) {
        BlockPos start = new BlockPos(6, 2, 8);
        helper.setBlock(start.north(), Blocks.CHEST);
        RcCarEntity car = car(helper, start, """
                done = false
                def haul():
                    collect()
                    unload()
                    done = true
                """);
        car.trunk().addItem(new ItemStack(Items.COBBLESTONE, 40));
        car.trunk().addItem(new ItemStack(Items.DIAMOND, 3));
        helper.spawnItem(Items.EMERALD, start.east());
        BlockPos looseStart = new BlockPos(11, 2, 8);
        RcCarEntity loose = car(helper, looseStart, """
                done = false
                def haul():
                    unload()
                    done = true
                """);
        loose.trunk().addItem(new ItemStack(Items.GOLD_INGOT, 5));
        helper.runAfterDelay(10, () -> check(car.brain().startFunction("haul") && loose.brain().startFunction("haul"),
                "De functie haul begon niet"));
        helper.succeedWhen(() -> {
            check(Boolean.TRUE.equals(car.brain().global("done")) && Boolean.TRUE.equals(loose.brain().global("done")),
                    "Een auto is nog niet klaar met haul");
            check(car.trunk().isEmpty(), "De kofferbak is niet leeg na unload()");
            Container chest = (Container) helper.getBlockEntity(start.north());
            check(chest.countItem(Items.COBBLESTONE) == 40 && chest.countItem(Items.DIAMOND) == 3 && chest.countItem(Items.EMERALD) == 1,
                    "In de kist liggen niet de 40 cobblestone, 3 diamanten en de opgeraapte smaragd");
            check(loose.trunk().isEmpty(), "Zonder kist bleef er iets in de kofferbak");
            int onGround = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new AABB(helper.absolutePos(looseStart)).inflate(1.5), item -> item.getItem().is(Items.GOLD_INGOT)).stream()
                    .mapToInt(item -> item.getItem().getCount()).sum();
            check(onGround == 5, "Zonder kist liggen er " + onGround + " goudstaven op de grond in plaats van 5");
        });
    }

    // ================================================================ Het item en de controller

    /**
     * Jouw wens: "je kunt deze rc car ook oppakken en in je inventory zetten". Neerzetten met het item: de auto is van
     * jou en kijkt de kant op waar jij kijkt, hij heeft geen programma en de ijzeren boorkop, en een RC Controller in je
     * rugzak die nog nergens mee gekoppeld is, koppelt vanzelf. Een betere boorkop zet je erop met rechtsklik. Oppakken:
     * zijn naam, programma, cel, boorkop en kofferbak (elk in zijn eigen vakje) zitten in het item. Weer neerzetten:
     * dezelfde auto, en de controller vindt hem nog.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void rc_car_item_keeps_everything(GameTestHelper helper) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(7, 2, 10), "car-owner");
        player.getAbilities().instabuild = false;
        player.setYRot(Direction.EAST.toYRot());
        player.getInventory().setItem(5, new ItemStack(ModItems.RC_CONTROLLER.get()));
        RcCarEntity car = placeCar(helper, player, new ItemStack(ModItems.RC_CAR.get()), new BlockPos(7, 1, 7));
        check(player.getUUID().equals(car.owner()), "De auto is niet van wie hem neerzette");
        check(car.body().facing() == Direction.EAST, "De auto kijkt naar " + car.body().facing() + ", niet waar de speler kijkt (oost)");
        check(car.functions().isEmpty() && !car.hasMain() && car.source().isEmpty(),
                "Een nieuwe auto heeft al een programma: " + car.functions() + " / " + car.source());
        check(!car.hasCell(), "Een nieuwe auto heeft al een energiecel");
        check(car.drill() == DrillHead.IRON, "Een nieuwe auto heeft niet de ijzeren boorkop maar " + car.drill());
        RcLink link = RcControllerItem.link(player.getInventory().getItem(5));
        check(link != null && link.car().equals(car.link()), "De vrije RC Controller koppelde niet vanzelf met de nieuwe auto");
        check(RcCars.find(car.link()) == car, "RcCars vindt de auto niet op zijn nummer");

        // Een betere boorkop erop (rechtsklik met de kop): de oude krijg je terug in je hand. Dezelfde kop nog eens: niets.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.NETHERITE_DRILL_HEAD.get()));
        RcCars.swapDrill(player, car, InteractionHand.MAIN_HAND);
        check(car.drill() == DrillHead.NETHERITE, "De netherite kop zit niet op de auto maar " + car.drill());
        check(player.getMainHandItem().is(ModItems.IRON_DRILL_HEAD.get()) && player.getMainHandItem().getCount() == 1,
                "Je kreeg de ijzeren kop niet terug in je hand, maar " + player.getMainHandItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.NETHERITE_DRILL_HEAD.get(), 2));
        RcCars.swapDrill(player, car, InteractionHand.MAIN_HAND);
        check(car.drill() == DrillHead.NETHERITE && player.getMainHandItem().getCount() == 2,
                "Dezelfde kop er nog eens op zetten kostte een kop");

        // Een cel erin (rechtsklik met een cel), een naam, een programma en iets in vakje 7 van de kofferbak.
        player.setItemInHand(InteractionHand.MAIN_HAND, EnergyCellItem.withCharge(50.0f));
        RcCars.loadCell(player, car, InteractionHand.MAIN_HAND);
        check(car.hasCell() && Math.abs(car.cellPercent() - 50.0f) < 0.01f, "De cel van 50% zit niet in de auto");
        check(player.getMainHandItem().isEmpty(), "De cel is niet uit je hand");
        car.setCustomName(Component.literal("Digger"));
        String code = "def home():\n    unload()\n";
        check(car.setSource(code) == null, "Het programma van de test klopt niet");
        car.trunk().setItem(7, new ItemStack(Items.DIAMOND, 5));
        java.util.UUID number = car.link();

        // Oppakken.
        RcCars.pickUp(player, car);
        check(!car.isAlive(), "De auto staat er nog na het oppakken");
        check(RcCars.find(number) == null, "RcCars vindt de auto nog nadat hij opgepakt is");
        ItemStack stack = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).getItem() instanceof RcCarItem) {
                stack = player.getInventory().removeItemNoUpdate(slot);
            }
        }
        check(!stack.isEmpty(), "Het item van de auto zit niet in je rugzak");
        RcCarData data = RcCarItem.data(stack);
        check(data.name().equals("Digger") && data.source().equals(code), "De naam of het programma zit niet in het item");
        check(Math.abs(data.cell() - 50.0f) < 0.01f, "De cel zit niet in het item: " + data.cell());
        check(data.link().isPresent() && data.link().get().equals(number), "Zijn vaste nummer zit niet in het item");
        check(data.items() == 5, "De kofferbak zit niet in het item");
        check(data.drill() == DrillHead.NETHERITE, "De boorkop zit niet in het item: " + data.drill());

        // Weer neerzetten, ergens anders: dezelfde auto.
        RcCarEntity again = placeCar(helper, player, stack, new BlockPos(10, 1, 9));
        check(again != car && again.link().equals(number), "De auto die je weer neerzet heeft een ander nummer");
        check(again.displayName().equals("Digger") && again.functions().equals(List.of("home")), "Naam of programma kwam niet mee");
        check(again.trunk().getItem(7).is(Items.DIAMOND) && again.trunk().getItem(7).getCount() == 5,
                "De diamanten liggen niet meer in vakje 7 van de kofferbak");
        check(Math.abs(again.cellPercent() - 50.0f) < 0.01f, "De cel kwam niet mee");
        check(again.drill() == DrillHead.NETHERITE, "De boorkop kwam niet mee: " + again.drill());
        check(RcCars.find(number) == again, "De RC Controller vindt de auto niet meer na oppakken en neerzetten");

        // Een RC Car hoort niet in een kofferbak (dan zit een auto in een auto), een shulker box ook niet; en de auto zelf
        // past niet in een rugzak of shulkerdoos (hij is zelf een doos).
        ItemStack carItem = new ItemStack(ModItems.RC_CAR.get());
        check(!RcCarEntity.fitsInTrunk(carItem) && !RcCarEntity.fitsInTrunk(new ItemStack(Items.SHULKER_BOX))
                && RcCarEntity.fitsInTrunk(new ItemStack(Items.COBBLESTONE)), "Wat in de kofferbak mag, klopt niet");
        check(!carItem.canFitInsideContainerItems() && !com.senne.imttech.backpack.BackpackItem.mayHold(carItem),
                "Een RC Car past in een rugzak of shulkerdoos");

        // In creative blijft het item in je hand als je de auto neerzet. Oppakken en weer neerzetten houdt toch zijn nummer
        // (je RC Controller blijft werken); het item in je hand vergeet het, zodat de volgende auto een nieuw nummer krijgt.
        player.getAbilities().instabuild = true;
        RcCars.pickUp(player, again);
        ItemStack picked = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).getItem() instanceof RcCarItem) {
                picked = player.getInventory().removeItemNoUpdate(slot);
            }
        }
        check(!picked.isEmpty(), "Het item van de auto zit niet in je rugzak (creative)");
        player.setItemInHand(InteractionHand.MAIN_HAND, picked);
        BlockPos third = helper.absolutePos(new BlockPos(12, 1, 12));
        InteractionResult placed = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(third).relative(Direction.UP, 0.5), Direction.UP, third, false)));
        List<RcCarEntity> cars = helper.getLevel().getEntitiesOfClass(RcCarEntity.class, new AABB(third.above()));
        check(placed.consumesAction() && cars.size() == 1, "De auto neerzetten in creative lukte niet");
        RcCarEntity last = cars.getFirst();
        check(last.link().equals(number), "In creative kreeg de auto die je weer neerzette een ander nummer");
        check(!player.getMainHandItem().isEmpty() && RcCarItem.data(player.getMainHandItem()).link().isEmpty(),
                "Het item dat in creative in je hand blijft, hoort het nummer van de auto te vergeten");
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        // Echt weg (/kill) terwijl de spelregel doMobLoot uit staat: de auto valt toch als item op de grond, met alles erin.
        net.minecraft.world.level.GameRules rules = helper.getLevel().getGameRules();
        boolean loot = rules.getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT);
        rules.getRule(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT).set(false, helper.getLevel().getServer());
        last.kill();
        rules.getRule(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT).set(loot, helper.getLevel().getServer());
        List<net.minecraft.world.entity.item.ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new AABB(third.above()).inflate(2),
                item -> item.getItem().getItem() instanceof RcCarItem);
        check(dropped.size() == 1, "Na /kill met doMobLoot uit liggen er " + dropped.size() + " auto's op de grond in plaats van 1");
        RcCarData left = RcCarItem.data(dropped.getFirst().getItem());
        check(left.name().equals("Digger") && left.items() == 5 && left.drill() == DrillHead.NETHERITE,
                "De auto die op de grond viel, is niet meer compleet: " + left.name() + ", " + left.items() + " voorwerpen, " + left.drill());
        dropped.forEach(net.minecraft.world.entity.Entity::discard);
        helper.succeed();
    }

    /** Laat de speler een RC Car-item op de bovenkant van dit blok zetten, en geeft de auto die er dan staat. */
    private static RcCarEntity placeCar(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos floor) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(floor);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.UP, 0.5), Direction.UP, abs, false);
        InteractionResult result = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        check(result.consumesAction(), "De auto neerzetten lukte niet");
        check(player.getMainHandItem().isEmpty(), "Het item is niet uit je hand na het neerzetten");
        List<RcCarEntity> cars = helper.getLevel().getEntitiesOfClass(RcCarEntity.class, new AABB(abs.above()));
        check(cars.size() == 1, "Er staan " + cars.size() + " auto's op het blok in plaats van 1");
        return cars.getFirst();
    }

    // ================================================================ Zijn scherm van ver, en gebarsten blokken

    /**
     * De knoppen van zijn scherm (en van de RC Controller) werken tot controlRange blokken ver: de auto rijdt van je weg
     * terwijl je kijkt, en dan moet Stop het nog doen. Verder weg: niets. Oppakken kan alleen van dichtbij. Een vreemde
     * kan niets met jouw auto.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void rc_car_buttons_reach_as_far_as_the_controller(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(7, 2, 10), "car-far-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 2, 10), "car-far-stranger");
        BlockPos start = new BlockPos(7, 2, 8);
        RcCarEntity car = car(helper, start, "def nap():\n    wait(60)\n");
        car.setOwner(owner);
        Vec3 near = owner.position();
        double range = com.senne.imttech.config.ImtConfig.RcCar.CONTROL_RANGE;

        // Een vreemde: niets.
        RcCars.handleAction(stranger, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                com.senne.imttech.network.RcCarActionPayload.Op.RUN, "nap"));
        check(!car.brain().busy(), "Een vreemde kon een functie van andermans auto starten");

        // Twintig blokken weg (hoog in de lucht): starten en stoppen werkt, oppakken niet.
        owner.moveTo(near.x, near.y + 20, near.z);
        RcCars.handleAction(owner, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                com.senne.imttech.network.RcCarActionPayload.Op.RUN, "nap"));
        check(car.brain().busy() && car.brain().job().equals("nap"), "Van 20 blokken ver begon de functie niet");
        RcCars.handleAction(owner, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                com.senne.imttech.network.RcCarActionPayload.Op.STOP, ""));
        check(!car.brain().busy(), "Van 20 blokken ver werkte Stop niet");
        RcCars.handleAction(owner, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                com.senne.imttech.network.RcCarActionPayload.Op.PICK_UP, ""));
        check(car.isAlive(), "Van 20 blokken ver kon je de auto oppakken");

        // Verder dan controlRange: geen signaal.
        owner.moveTo(near.x, near.y + range + 5, near.z);
        RcCars.handleAction(owner, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                com.senne.imttech.network.RcCarActionPayload.Op.RUN, "nap"));
        check(!car.brain().busy(), "Verder dan controlRange begon de functie toch");

        // Een nieuwe naam en een nieuw programma van ver (binnen bereik), en dan van dichtbij oppakken.
        owner.moveTo(near.x, near.y + 20, near.z);
        RcCars.handleAction(owner, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                com.senne.imttech.network.RcCarActionPayload.Op.RENAME, "Mole"));
        RcCars.handleAction(owner, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                com.senne.imttech.network.RcCarActionPayload.Op.SAVE, "def dig():\n    mine()\n"));
        check(car.displayName().equals("Mole") && car.functions().equals(List.of("dig")), "Hernoemen of Save van ver werkte niet");
        RcCars.handleAction(owner, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                com.senne.imttech.network.RcCarActionPayload.Op.SAVE, "def broken(:\n"));
        check(car.functions().equals(List.of("dig")), "Code die niet klopt, kwam toch op de auto");
        owner.moveTo(near.x, near.y, near.z);
        RcCars.handleAction(owner, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                com.senne.imttech.network.RcCarActionPayload.Op.PICK_UP, ""));
        check(!car.isAlive(), "Van dichtbij kon je de auto niet oppakken");
        check(owner.getInventory().items.stream().anyMatch(stack -> stack.getItem() instanceof RcCarItem
                && RcCarItem.data(stack).name().equals("Mole")), "De opgepakte auto zit niet in je rugzak");
        helper.succeed();
    }

    /**
     * Een regel van de mod die ook voor een auto geldt: een blok met barsten (hoofdstuk 11) geeft een gebarsten blok, ook
     * als een RC Car het breekt. Anders maakte je met een auto van elk gebarsten blok weer een heel.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "cracks")
    public static void rc_car_gets_cracked_blocks(GameTestHelper helper) {
        BlockPos start = new BlockPos(6, 2, 8);
        helper.setBlock(start.north(), Blocks.STONE);
        com.senne.imttech.breach.CrackData.get(helper.getLevel()).put(helper.getLevel(), helper.absolutePos(start.north()),
                new com.senne.imttech.breach.CrackData.Crack(2.0f, Blocks.STONE, 0));
        RcCarEntity car = car(helper, start, "done = false\ndef dig():\n    mine()\n    done = true\n");
        check(car.brain().startFunction("dig"), "De functie dig begon niet");
        helper.succeedWhen(() -> {
            check(Boolean.TRUE.equals(car.brain().global("done")), "De auto is nog niet klaar");
            helper.assertBlockPresent(Blocks.AIR, start.north());
            ItemStack got = car.trunk().getItem(0);
            check(got.is(ModItems.CRACKED_BLOCK.get()) && com.senne.imttech.repair.FromBlockItem.block(got) == Blocks.STONE
                            && got.getCount() == 1 && car.trunk().countItem(Items.COBBLESTONE) == 0,
                    "In de kofferbak ligt geen gebarsten steen maar " + got);
        });
    }

    // ================================================================ Wat de review vond

    /**
     * Gevonden door de review: place() met een halve plaat op een halve plaat van hetzelfde soort liet Minecraft eindeloos
     * in zichzelf rondvragen: een servercrash die elke speler met twee halve platen kon uitlokken. Nu zet de auto een blok
     * alleen op een vrije plek: de tweede plaat blijft in de kofferbak en car.blocked is waar. Hetzelfde met sneeuw op
     * sneeuw, en met place_down() als hij op de plaat staat. Een deur (twee blokken hoog) plaatst hij helemaal niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void rc_car_does_not_stack_slabs(GameTestHelper helper) {
        String twice = """
                first = false
                second = false
                done = false
                def build():
                    place()
                    first = car.blocked
                    place()
                    second = car.blocked
                    done = true
                """;
        BlockPos slabStart = new BlockPos(4, 2, 8);
        RcCarEntity slabs = car(helper, slabStart, twice);
        slabs.trunk().addItem(new ItemStack(Items.STONE_SLAB, 2));
        BlockPos snowStart = new BlockPos(8, 2, 8);
        RcCarEntity snow = car(helper, snowStart, twice);
        snow.trunk().addItem(new ItemStack(Items.SNOW, 2));
        BlockPos downStart = new BlockPos(12, 2, 8);
        RcCarEntity down = car(helper, downStart, """
                first = false
                second = false
                done = false
                def build():
                    place_down()
                    first = car.blocked
                    place_down()
                    second = car.blocked
                    done = true
                """);
        down.trunk().addItem(new ItemStack(Items.STONE_SLAB, 2));
        BlockPos doorStart = new BlockPos(4, 2, 12);
        RcCarEntity door = car(helper, doorStart, """
                first = false
                second = false
                done = false
                def build():
                    place()
                    first = car.blocked
                    done = true
                """);
        door.trunk().addItem(new ItemStack(Items.OAK_DOOR, 1));
        List<RcCarEntity> all = List.of(slabs, snow, down, door);
        for (RcCarEntity car : all) {
            check(car.brain().startFunction("build"), "De functie build begon niet");
        }
        helper.succeedWhen(() -> {
            for (RcCarEntity car : all) {
                check(Boolean.TRUE.equals(car.brain().global("done")) && car.isAlive(), "Een auto is nog niet klaar (of weg)");
            }
            for (RcCarEntity car : List.of(slabs, snow, down)) {
                check(Boolean.FALSE.equals(car.brain().global("first")), "Het eerste blok plaatsen gaf car.blocked");
                check(Boolean.TRUE.equals(car.brain().global("second")), "Een tweede blok op dezelfde plek gaf geen car.blocked");
            }
            net.minecraft.world.level.block.state.BlockState slab = helper.getBlockState(slabStart.north());
            check(slab.is(Blocks.STONE_SLAB) && slab.getValue(net.minecraft.world.level.block.SlabBlock.TYPE)
                    == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM, "Voor de auto ligt geen enkele halve plaat maar " + slab);
            check(slabs.trunk().countItem(Items.STONE_SLAB) == 1, "De tweede halve plaat is uit de kofferbak");
            net.minecraft.world.level.block.state.BlockState layer = helper.getBlockState(snowStart.north());
            check(layer.is(Blocks.SNOW) && layer.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) == 1,
                    "Voor de auto ligt niet één laagje sneeuw maar " + layer);
            check(snow.trunk().countItem(Items.SNOW) == 1, "Het tweede laagje sneeuw is uit de kofferbak");
            net.minecraft.world.level.block.state.BlockState under = helper.getBlockState(downStart);
            check(under.is(Blocks.STONE_SLAB) && under.getValue(net.minecraft.world.level.block.SlabBlock.TYPE)
                    == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM, "Onder de auto ligt geen enkele halve plaat maar " + under);
            check(down.trunk().countItem(Items.STONE_SLAB) == 1, "De tweede halve plaat (place_down) is uit de kofferbak");
            check(Boolean.TRUE.equals(door.brain().global("first")), "Een deur plaatsen gaf geen car.blocked");
            helper.assertBlockPresent(Blocks.AIR, doorStart.north());
            check(door.trunk().countItem(Items.OAK_DOOR) == 1, "De deur is uit de kofferbak");
        });
    }

    /**
     * Gevonden door de review. Op een pad (15 pixels hoog) staan de voeten van de auto nog in het vloerblok: mine() hoort
     * dan toch het blok voor zijn neus te breken en niet de vloer voor hem, block_down() is het pad, en car.y is het blok
     * van zijn lijf. En stromend water duwt hem niet van zijn plek.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void rc_car_keeps_its_place_on_odd_ground(GameTestHelper helper) {
        BlockPos pathStart = new BlockPos(4, 2, 10);
        helper.setBlock(pathStart.below(), Blocks.DIRT_PATH);
        helper.setBlock(pathStart.north(), Blocks.STONE);
        RcCarEntity onPath = car(helper, pathStart, """
                under = false
                front = false
                height = 0
                done = false
                def dig():
                    under = block_down() == "dirt_path"
                    front = block() == "stone"
                    height = car.y
                    mine()
                    forward(1)
                    done = not car.blocked
                """);
        // Een gootje van steen met stromend water: de bron in het noorden, de auto drie blokken verder in de stroom.
        fill(helper, new BlockPos(9, 2, 5), new BlockPos(11, 2, 12), Blocks.STONE);
        fill(helper, new BlockPos(10, 2, 6), new BlockPos(10, 2, 11), Blocks.AIR);
        helper.setBlock(new BlockPos(10, 2, 6), Blocks.WATER);
        BlockPos streamStart = new BlockPos(10, 2, 9);
        RcCarEntity inStream = car(helper, streamStart, "def nap():\n    wait(1)\n");
        helper.runAfterDelay(10, () -> check(onPath.brain().startFunction("dig"), "De functie dig begon niet"));
        helper.succeedWhen(() -> {
            check(helper.getTick() >= 120, "Even wachten tot het water stroomt");
            check(Boolean.TRUE.equals(onPath.brain().global("done")), "De auto op het pad is nog niet klaar (of kon niet vooruit)");
            check(Boolean.TRUE.equals(onPath.brain().global("under")), "block_down() op een pad is niet dirt_path");
            check(Boolean.TRUE.equals(onPath.brain().global("front")), "block() op een pad zag het blok steen voor zijn neus niet");
            check(Double.valueOf(helper.absolutePos(pathStart).getY()).equals(onPath.brain().global("height")),
                    "car.y op een pad is " + onPath.brain().global("height") + ", niet het blok van zijn lijf");
            helper.assertBlockPresent(Blocks.AIR, pathStart.north());
            helper.assertBlockPresent(Blocks.DIRT_PATH, pathStart.below());
            check(!helper.getBlockState(pathStart.north().below()).isAir(), "De auto brak de vloer voor hem in plaats van het blok voor zijn neus");
            checkAt(helper, onPath, pathStart.north(), "Na mine() en forward(1) vanaf een pad");

            check(!helper.getBlockState(streamStart).getFluidState().isEmpty()
                            && !helper.getBlockState(streamStart).getFluidState().isSource(), "Bij de auto stroomt geen water");
            BlockPos abs = helper.absolutePos(streamStart);
            check(Math.abs(inStream.getX() - (abs.getX() + 0.5)) < 0.02 && Math.abs(inStream.getZ() - (abs.getZ() + 0.5)) < 0.02,
                    "Stromend water duwde de auto van zijn plek: " + inStream.getX() + ", " + inStream.getZ());
        });
    }

    /**
     * Gevonden door de review: wie mag wat met andermans auto? Staat de auto van een bezoeker in jouw beschermde zone,
     * dan bestuur je hem niet (geen functie starten, geen ander programma), maar je mag hem wel stilzetten en oppakken:
     * hij kan niet kapot, dus wie er een in jouw zone achterlaat, is hem kwijt. De bezoeker zelf bestuurt zijn auto nog
     * wel (maar breken kan hij er niet: zie rc_car_respects_zones).
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "zones")
    public static void rc_car_in_someone_elses_zone(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 8), "car-zone2-owner");
        ServerPlayer visitor = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(9, 3, 10), "car-zone2-visitor");
        SecurityCoreBlockEntity core = ZoneGameTests.zoneCore(helper, owner);
        BlockPos start = new BlockPos(7, 2, 9);
        RcCarEntity car = car(helper, start, "def nap():\n    wait(200)\n");
        car.setOwner(visitor);
        // Even wachten: de zone moet stroom hebben (dan pas beschermt hij).
        helper.runAfterDelay(20, () -> {
        check(com.senne.imttech.security.zone.Zones.activeAt(helper.getLevel(), helper.absolutePos(start)) != null,
                "De zone beschermt de plek van de auto niet");
        check(!RcCars.mayControl(owner, car), "De eigenaar van de zone mag de auto van een bezoeker besturen");
        check(RcCars.mayRemove(owner, car), "De eigenaar van de zone mag de auto van een bezoeker niet oppakken");
        check(RcCars.mayControl(visitor, car), "De bezoeker mag zijn eigen auto niet besturen");
        RcCars.handleAction(owner, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                com.senne.imttech.network.RcCarActionPayload.Op.RUN, "nap"));
        check(!car.brain().busy(), "De eigenaar van de zone startte een functie van andermans auto");
        RcCars.handleAction(owner, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                com.senne.imttech.network.RcCarActionPayload.Op.SAVE, "def dig():\n    mine()\n"));
        check(car.functions().equals(List.of("nap")), "De eigenaar van de zone zette een ander programma op andermans auto");
        RcCars.handleAction(visitor, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                com.senne.imttech.network.RcCarActionPayload.Op.RUN, "nap"));
        check(car.brain().busy(), "De bezoeker kon zijn eigen auto niet starten");
        check(visitor.getUUID().equals(car.actorId()), "De auto weet niet wie zijn klus startte");
        RcCars.handleAction(owner, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                com.senne.imttech.network.RcCarActionPayload.Op.STOP, ""));
        check(!car.brain().busy(), "De eigenaar van de zone kon de auto niet stilzetten");
        RcCars.handleAction(owner, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                com.senne.imttech.network.RcCarActionPayload.Op.PICK_UP, ""));
        check(!car.isAlive() && owner.getInventory().items.stream().anyMatch(stack -> stack.getItem() instanceof RcCarItem),
                "De eigenaar van de zone kon de auto niet oppakken");
        ZoneGameTests.finish(helper, core);
        });
    }

    /**
     * Gevonden door de review. Een functie mag "manual" of "program" heten (dat waren de interne namen van een klus: een
     * functie manual deed toen niets). En een naamkaartje: Minecraft gaf elke auto de naam van het kaartje, van wie hij
     * ook was; nu alleen wie hem mag besturen, en met de regels van Rename (niet te lang).
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void rc_car_names(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(7, 2, 10), "car-name-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 2, 10), "car-name-stranger");
        BlockPos start = new BlockPos(7, 2, 8);
        RcCarEntity car = car(helper, start, """
                a = 0
                b = 0
                def manual():
                    a = a + 1
                def program():
                    b = b + 1
                """);
        car.setOwner(owner);
        owner.getAbilities().instabuild = false;
        stranger.getAbilities().instabuild = false;
        check(car.functions().equals(List.of("manual", "program")), "De functies manual en program staan niet op de auto: " + car.functions());

        // Een vreemde met een naamkaartje: niets. De eigenaar: de naam staat erop en het kaartje is op. Te lang: niet.
        stranger.setItemInHand(InteractionHand.MAIN_HAND, nameTag("Stolen"));
        stranger.interactOn(car, InteractionHand.MAIN_HAND);
        check(!car.hasCustomName() && stranger.getMainHandItem().getCount() == 1, "Een vreemde hernoemde de auto met een naamkaartje");
        owner.setItemInHand(InteractionHand.MAIN_HAND, nameTag("An RC Car with a name that is much too long"));
        owner.interactOn(car, InteractionHand.MAIN_HAND);
        check(!car.hasCustomName() && owner.getMainHandItem().getCount() == 1, "Een veel te lange naam kwam toch op de auto");
        owner.setItemInHand(InteractionHand.MAIN_HAND, nameTag("Mole"));
        owner.interactOn(car, InteractionHand.MAIN_HAND);
        check(car.displayName().equals("Mole") && owner.getMainHandItem().isEmpty(), "Het naamkaartje van de eigenaar werkte niet: " + car.displayName());

        check(car.brain().startFunction("manual"), "De functie manual begon niet");
        boolean[] second = {false};
        helper.succeedWhen(() -> {
            check(!car.brain().busy(), "De auto is nog bezig");
            check(Double.valueOf(1).equals(car.brain().global("a")), "De functie manual deed niets: a = " + car.brain().global("a"));
            if (!second[0]) {
                second[0] = true;
                check(Double.valueOf(0).equals(car.brain().global("b")), "De functie program liep al mee met manual");
                check(car.brain().startFunction("program"), "De functie program begon niet");
            }
            check(Double.valueOf(1).equals(car.brain().global("b")), "De functie program is nog niet klaar: b = " + car.brain().global("b"));
        });
    }

    /** Een naamkaartje met deze naam erop. */
    private static ItemStack nameTag(String name) {
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name));
        return tag;
    }

    // ================================================================ Starten, stoppen en met de hand

    /**
     * Start loopt het hoofdprogramma (when started); is de auto bezig, dan begint er niets anders; Stop houdt alles tegen
     * en dan staat hij stil. Een stap met de hand (de pijltjes) rijdt één blok. Een functie die er niet is, begint niet.
     * Een EMP-granaat stopt zijn klus.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void rc_car_start_stop_and_steps(GameTestHelper helper) {
        BlockPos start = new BlockPos(7, 2, 12);
        RcCarEntity car = car(helper, start, """
                rounds = 0
                when started:
                    while true:
                        forward(1)
                        back(1)
                        rounds = rounds + 1
                def spin():
                    turn_right()
                """);
        check(car.hasMain(), "De auto zegt niet dat hij een hoofdprogramma heeft");
        check(!car.brain().startFunction("nope"), "Een functie die niet bestaat, begon toch");
        check(car.brain().startMain(), "Start begon niet");
        check(!car.brain().startFunction("spin") && !car.brain().startManual(new CarMove.Drive(1)) && !car.brain().startMain(),
                "Terwijl de auto bezig was, begon er iets anders");
        BlockPos stepStart = new BlockPos(11, 2, 12);
        RcCarEntity stepper = car(helper, stepStart, "def nothing():\n    wait(1)\n");
        check(!stepper.brain().startMain(), "Start begon zonder when started");
        check(stepper.brain().startManual(new CarMove.Drive(1)), "Een stap met de hand begon niet");
        BlockPos empStart = new BlockPos(3, 2, 12);
        RcCarEntity jammed = car(helper, empStart, "def far():\n    forward(8)\n");
        check(jammed.brain().startFunction("far"), "De functie far begon niet");
        helper.runAfterDelay(4, () -> jammed.onEmp(helper.getLevel(), 2000));
        int[] stoppedAt = {-1};
        double[] stoppedZ = new double[2];
        helper.succeedWhen(() -> {
            // De stap met de hand: één blok, en dan is hij weer vrij.
            check(!stepper.brain().busy(), "De stap met de hand is nog bezig");
            checkAt(helper, stepper, stepStart.north(), "Na één stap met de hand");
            // Het hoofdprogramma liep minstens twee rondjes; dan Stop.
            Object rounds = car.brain().global("rounds");
            check(rounds instanceof Double done && done >= 2, "Het hoofdprogramma liep nog geen twee rondjes: " + rounds);
            if (stoppedAt[0] < 0) {
                car.brain().stop();
                stoppedAt[0] = car.tickCount;
                stoppedZ[0] = car.getZ();
                stoppedZ[1] = jammed.getZ();
            }
            check(!car.brain().busy() && car.brain().job().isEmpty(), "Na Stop is de auto nog bezig");
            check(car.tickCount >= stoppedAt[0] + 30, "Even wachten of hij echt stilstaat");
            check(Math.abs(car.getZ() - stoppedZ[0]) < 0.01, "Na Stop reed de auto nog verder");
            // De EMP: zijn klus is gestopt, lang voor de acht blokken, en hij ligt stil.
            check(!jammed.brain().busy() && jammed.isJammed(), "Na de EMP-granaat is de auto nog bezig of niet stilgelegd");
            check(jammed.getZ() > helper.absolutePos(empStart.north(6)).getZ() && Math.abs(jammed.getZ() - stoppedZ[1]) < 0.01,
                    "Na de EMP-granaat reed de auto door");
        });
    }
}
