package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.InputDeviceBlock;
import com.senne.imttech.logic.InputDeviceBlockEntity;
import com.senne.imttech.logic.InputDevices;
import com.senne.imttech.logic.InputLight;
import com.senne.imttech.logic.KeycardData;
import com.senne.imttech.logic.LogicEvents;
import com.senne.imttech.logic.LogicRunner;
import com.senne.imttech.logic.LogicServer;
import com.senne.imttech.logic.LogicStats;
import com.senne.imttech.logic.program.Expr;
import com.senne.imttech.logic.program.ExprCache;
import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.LogicException;
import com.senne.imttech.logic.program.LogicGraph;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.Op;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.Script;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.logic.program.ScriptPrinter;
import com.senne.imttech.network.InputRenamePayload;
import com.senne.imttech.network.OsActionPayload;
import com.senne.imttech.redstone.Relay;
import com.senne.imttech.redstone.RelayBlock;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.EventLog;
import com.senne.imttech.security.os.SecurityOs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de redstone-diagrammen op de server (hoofdstuk 8 van het plan): de taal (code lezen,
 * terugschrijven, foutmeldingen), code naar blokjes en terug, en de server die de diagrammen echt
 * uitvoert (keycard, codepaneel, timers, relais, RAM en SSD's).
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class LogicGameTests {
    private LogicGameTests() {}

    private static final String ARENA = "empty";

    /** Het voorbeeld uit het plan: alles wat de taal kan, netjes geschreven zoals de printer het doet. */
    private static final String EXAMPLE = """
            when keycard("Lab door"):
                if card.in_group("lab"):
                    accept()
                    pulse("Vault door", 3)
                elif card.valid:
                    say("Someone without lab access tried the lab")
                else:
                    deny()
                    alarm("Wrong card at the lab")
                log("Lab door used")

            when every(10):
                if is_night and not is_on("Lamp"):
                    on("Lamp")
            """;

    // ================================================================ De taal

    /** Code lezen en weer schrijven geeft precies dezelfde code; het telt 12 blokjes. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_code_round_trip(GameTestHelper helper) {
        Script script = ScriptParser.parse(EXAMPLE);
        if (script.blocks() != 12) {
            throw new GameTestAssertException("Het voorbeeld zou 12 blokjes moeten zijn, niet " + script.blocks());
        }
        String printed = ScriptPrinter.print(script);
        if (!printed.equals(EXAMPLE)) {
            throw new GameTestAssertException("Terugschrijven gaf andere code:\n" + printed);
        }
        // Slordig geschreven code wordt netjes: tabs, extra spaties, commentaar, enkele aanhalingstekens.
        String messy = "# mijn deur\nwhen   codepanel('Front door') :\n\tif code==\"4321\" :  # de code\n\t\taccept( )\n\telse:\n\t\tpass\n";
        String clean = ScriptPrinter.print(ScriptParser.parse(messy));
        String expected = "when codepanel(\"Front door\"):\n    if code == \"4321\":\n        accept()\n";
        if (!clean.equals(expected)) {
            throw new GameTestAssertException("Slordige code werd niet netjes:\n" + clean);
        }
        helper.succeed();
    }

    /** Fouten krijgen het goede regelnummer en uitleg die een beginner snapt. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_code_errors(GameTestHelper helper) {
        expectError("when keycard(\"Lab\"):\n    opn(\"Door\")", 2, "Did you mean on?");
        expectError("on(\"Lamp\")", 1, "must be inside a when");
        expectError("when keycard(\"Lab\")\n    accept()", 1, "must end with a :");
        expectError("when keycard(\"Lab\"):\n    if code == \"1\":\n        accept()", 2, "code only works inside when codepanel");
        expectError("when codepanel(\"P\"):\n    if code == 4321:\n        accept()", 2, "put the code between quotes");
        expectError("when every(10):\n    accept()", 2, "only works inside when keycard");
        expectError("when every(0.5):\n    on(\"Lamp\")", 1, "every takes 1 to 3600 seconds");
        expectError("when keycard(\"Lab\"):\n    on(Lamp)", 2, "If you meant a device, put it between quotes");
        expectError("when keycard(\"Lab\"):\n    accept()\n      deny()", 3, "indented more");
        expectError("when keycard(\"Lab\"):\n    if card.number = 3:\n        accept()", 2, "Use == to compare");
        expectError("when keycard(\"Lab\"):\n    if card.number >= \"3\":\n        accept()", 2, "cannot compare a number with text");
        expectError("when keycard(\"Lab\"):\nwhen every(5):\n    on(\"A\")", 1, "must be lines that are indented");
        expectError("when keycard(\"Lab\"):\n    else:\n        accept()", 2, "no if above it");
        expectError("when keycard(\"Lab\"):\n    pulse(\"Gate\", 99)", 2, "0.05 to 60 seconds");
        helper.succeed();
    }

    /**
     * Hoofdstuk 38: de netwerkknop en -hendel in de taal. {@code when button(...)} en {@code when lever(...)} lezen en
     * schrijven netjes terug; {@code lever.on} werkt alleen in een when lever, {@code player.name} ook na een knop of
     * hendel, en accept() niet (een knop heeft geen lampje). Een oude def die lever heette, krijgt een andere naam, en
     * Ctrl+Spatie kent de nieuwe triggers en lever.on.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_buttons_and_levers(GameTestHelper helper) {
        String code = """
                when button("Doorbell"):
                    say(player.name + " is at the door")

                when lever("Night mode"):
                    if lever.on:
                        on("Vault lasers")
                    else:
                        off("Vault lasers")
                """;
        String printed = ScriptPrinter.print(ScriptParser.parse(code));
        check(printed.equals(code), "Knop en hendel terugschrijven gaf andere code:\n" + printed);
        expectError("when button(\"Doorbell\"):\n    if lever.on:\n        on(\"Lamp\")", 2, "lever.on only works inside when lever(...)");
        expectError("when button(\"Doorbell\"):\n    accept()", 2, "only works inside when keycard");
        expectError("when every(5):\n    say(player.name)", 2, "lever(...) or request(...)");
        expectError("when lever(Night):\n    on(\"Lamp\")", 1, "Put names between quotes");
        // Een oude def die lever heette.
        LogicGraph old = new LogicGraph();
        LogicGraph.Node def = old.add(Op.DEF, 0, 0);
        def.text = "lever";
        LogicGraph.Node every = old.add(Op.EVERY, 0, 40);
        LogicGraph.Node call = old.add(Op.CALL, 100, 40);
        call.text = "lever";
        every.next = call.id;
        check(old.renameReservedDefs() && def.text.equals("lever_1") && call.text.equals("lever_1"),
                "De def lever kreeg geen andere naam: " + def.text + " / " + call.text);
        expectSuggestions("when |", List.of("button(\"...\"):", "lever(\"...\"):"), List.of());
        expectSuggestions("when lever(\"Night mode\"):\n    if |", List.of("lever.on", "player.name"), List.of("card.valid", "code"));
        expectSuggestions("when button(\"Doorbell\"):\n    if |", List.of("player.name"), List.of("lever.on"));
        helper.succeed();
    }

    // ================================================================ Hoofdstuk 43: typen, parameters en global scripts

    /** Een global script met een gedeelde naam en twee defs (een met parameters), zoals de printer het schrijft. */
    private static final String GLOBAL_SCRIPT = """
            global

            lockdown: bool = false
            total: int = 0

            def activate_alarm():
                alarm("Alarm!")

            def open_door(string door, float secs):
                log("Opening " + door)
                pulse(door, secs)
            """;

    /**
     * Hoofdstuk 43: namen met een soort-woord ({@code count: int = 0}), defs met parameters, aanroepen met waarden, een
     * apparaat als code, en global scripts. Lezen en terugschrijven geeft dezelfde code (ook via de blokjes); de
     * bibliotheek van een global script maakt zijn defs en gedeelde namen overal bruikbaar; en fouten krijgen uitleg.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_types_params_global(GameTestHelper helper) {
        // Een global script: lezen, terugschrijven, en via de blokjes.
        Script global = ScriptParser.parse(GLOBAL_SCRIPT);
        if (!global.global() || global.functions().size() != 2 || global.globals().size() != 2) {
            throw new GameTestAssertException("Het global script werd niet goed gelezen: " + global);
        }
        if (!ScriptPrinter.print(global).equals(GLOBAL_SCRIPT)) {
            throw new GameTestAssertException("Terugschrijven van het global script gaf andere code:\n" + ScriptPrinter.print(global));
        }
        LogicGraph graph = GraphConverter.toGraph(global, null);
        if (!graph.global() || !ScriptPrinter.print(GraphConverter.toScript(graph)).equals(GLOBAL_SCRIPT)) {
            throw new GameTestAssertException("Via de blokjes kwam het global script anders terug:\n"
                    + ScriptPrinter.print(GraphConverter.toScript(graph)));
        }
        LogicGraph loaded = LogicGraph.load(graph.save());
        if (!loaded.global() || !ScriptPrinter.print(GraphConverter.toScript(loaded)).equals(GLOBAL_SCRIPT)) {
            throw new GameTestAssertException("Na opslaan en laden kwam het global script anders terug");
        }
        var params = global.functions().get(1).params();
        if (params.size() != 2 || !params.get(0).name().equals("door") || params.get(0).varType() != com.senne.imttech.logic.program.VarType.STRING
                || params.get(1).varType() != com.senne.imttech.logic.program.VarType.FLOAT) {
            throw new GameTestAssertException("De parameters van open_door kloppen niet: " + params);
        }
        // Een gewoon diagram dat het global script gebruikt: alleen met de bibliotheek.
        var library = new com.senne.imttech.logic.program.Library(List.of(
                new com.senne.imttech.logic.program.Library.Source("Lib", global)));
        String uses = """
                when keycard("Lab door"):
                    if lockdown:
                        deny()
                        activate_alarm()
                    else:
                        accept()
                        open_door("Vault door", 3)

                when lever("Night"):
                    lockdown = lever.on
                """;
        Script script = ScriptParser.parse(uses, ProgramKind.SERVER, library);
        if (!ScriptPrinter.print(script).equals(uses)) {
            throw new GameTestAssertException("Terugschrijven met parameters gaf andere code:\n" + ScriptPrinter.print(script));
        }
        expectError(uses, 4, "I don't know \"activate_alarm\""); // zonder de bibliotheek bestaat de def niet
        // Een eigen def met parameters, en het apparaat als code.
        String own = """
                count: int = 0

                def open(string door):
                    pulse(door, 2)
                    count = count + 1

                when every(10):
                    open("Lamp")
                    say($"Opened {count} times")
                """;
        Script ownScript = ScriptParser.parse(own);
        if (!ScriptPrinter.print(ownScript).equals(own)) {
            throw new GameTestAssertException("Terugschrijven van een eigen def met parameter gaf andere code:\n" + ScriptPrinter.print(ownScript));
        }
        if (!ScriptPrinter.print(GraphConverter.toScript(GraphConverter.toGraph(ownScript, null))).equals(own)) {
            throw new GameTestAssertException("Via de blokjes kwam de def met parameter anders terug");
        }
        // Fouten met uitleg.
        expectError("count: int = 2.5\n\nwhen every(10):\n    pass", 1, "whole number");
        expectError("lockdown: bool = 0\n\nwhen every(10):\n    pass", 1, "cannot give it");
        expectError("def open(strng door):\n    pass\n\nwhen every(10):\n    open(\"a\")", 1, "I don't know the type");
        expectError("def open(string door):\n    pass\n\nwhen every(10):\n    open()", 5, "needs 1 value");
        expectError("def open(string door):\n    pass\n\nwhen every(10):\n    open(3)", 5, "must be text");
        expectError("when every(10):\n    global", 2, "first line");
        expectError("global\n\nwhen every(10):\n    pass", 3, "global script holds only defs");
        expectError("when every(10):\n    on(Lamp)", 2, "If you meant a device");
        expectError("when every(10):\n    count: int = 1\n    count = 2.5", 3, "whole number");
        expectError("when every(10):\n    name: string = \"a\"\n    name = 3", 3, "cannot give it");
        // Een gedeelde naam nog eens maken, of een def uit het global script nog eens maken, mag niet.
        expectErrorWith("lockdown = true\n\nwhen every(10):\n    pass", library, 1, "shared name from your global script Lib");
        expectErrorWith("def activate_alarm():\n    pass\n\nwhen every(10):\n    activate_alarm()", library, 1, "in your global script Lib");
        // accept() in een def uit het global script werkt alleen onder een lezer of paneel.
        var withAccept = new com.senne.imttech.logic.program.Library(List.of(new com.senne.imttech.logic.program.Library.Source("Lib",
                ScriptParser.parse("global\n\ndef ok():\n    accept()\n"))));
        expectErrorWith("when every(10):\n    ok()", withAccept, 2, "ok() uses accept()");
        // Ctrl+Spatie kent de soort-woorden, de gedeelde namen en de defs met hun parameters.
        List<String> types = complete("when every(10):\n    count: |", ProgramKind.SERVER);
        if (!types.contains("int") || !types.contains("bool")) {
            throw new GameTestAssertException("Na count: verwachtte ik de soort-woorden, maar kreeg: " + types);
        }
        List<String> calls = complete("def open(string door):\n    pass\n\nwhen every(10):\n    |", ProgramKind.SERVER);
        if (!calls.contains("open(string door)")) {
            throw new GameTestAssertException("Ctrl+Spatie toonde de def niet met zijn parameter: " + calls);
        }
        helper.succeed();
    }

    /**
     * Hoofdstuk 114 (jouw wens: "zoals in C#"): de parameters van een def met het soort-woord vóór de naam en zonder dubbele
     * punt, {@code def open(string door, int times):}. De oude vorm ({@code door: string}) geeft een fout die zegt hoe het
     * nu moet, in de code en in het vak Parameters van een DEF-blokje; Ctrl+Spatie stelt in een def eerst de soorten voor.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_params_type_first_like_csharp(GameTestHelper helper) {
        String code = """
                def open(string door, int times, x):
                    for i in range(times):
                        pulse(door, x)

                when every(10):
                    open("Lamp", 2, 1)
                """;
        Script script = ScriptParser.parse(code);
        var params = script.functions().getFirst().params();
        if (params.size() != 3 || !params.get(0).name().equals("door") || params.get(0).varType() != com.senne.imttech.logic.program.VarType.STRING
                || !params.get(1).name().equals("times") || params.get(1).varType() != com.senne.imttech.logic.program.VarType.INT
                || !params.get(2).name().equals("x") || params.get(2).varType() != null) {
            throw new GameTestAssertException("De parameters zoals in C# werden niet goed gelezen: " + params);
        }
        if (!ScriptPrinter.print(script).equals(code)) {
            throw new GameTestAssertException("Terugschrijven gaf andere code:\n" + ScriptPrinter.print(script));
        }
        if (!ScriptPrinter.print(GraphConverter.toScript(LogicGraph.load(GraphConverter.toGraph(script, null).save()))).equals(code)) {
            throw new GameTestAssertException("Via de blokjes (opslaan en laden) kwamen de parameters anders terug");
        }
        // De oude vorm, en andere fouten, met uitleg.
        expectError("def open(door: string):\n    pass\n\nwhen every(10):\n    open(\"a\")", 1,
                "Write the type before the name, like in C#: def open(string door):");
        expectError("def open(door: strng):\n    pass\n\nwhen every(10):\n    open(\"a\")", 1, "without a colon");
        expectError("def open(string):\n    pass\n\nwhen every(10):\n    open(\"a\")", 1, "After string comes the name of the parameter");
        expectError("def open(string door = \"a\"):\n    pass\n\nwhen every(10):\n    open(\"a\")", 1, "gets its value when you use the def");
        expectError("def open(string door int times):\n    pass\n\nwhen every(10):\n    open(\"a\", 1)", 1, "Separate the parameters with a comma");
        expectError("def open(string door,):\n    pass\n\nwhen every(10):\n    open(\"a\")", 1, "missing after the comma");
        // Het vak Parameters van een DEF-blokje leest hetzelfde.
        String old = com.senne.imttech.logic.program.Checks.exprProblem(Op.DEF, "door: string");
        if (old == null || !old.contains("Write the type before the name")) {
            throw new GameTestAssertException("Het DEF-blokje met door: string gaf niet de goede fout: " + old);
        }
        String fine = com.senne.imttech.logic.program.Checks.exprProblem(Op.DEF, "string door, int times");
        if (fine != null) {
            throw new GameTestAssertException("Het DEF-blokje met string door, int times gaf een fout: " + fine);
        }
        // Ctrl+Spatie: aan het begin van een parameter de soort-woorden.
        List<String> first = complete("def open(|", ProgramKind.SERVER);
        List<String> second = complete("def open(string door, |", ProgramKind.SERVER);
        if (!first.contains("int") || !first.contains("bool") || !second.contains("float") || !second.contains("string")) {
            throw new GameTestAssertException("In een def verwachtte ik de soort-woorden, maar kreeg: " + first + " / " + second);
        }
        helper.succeed();
    }

    /**
     * Hoofdstuk 114: code die nog met de oude parameters bewaard is ({@code def open(door: string):}), draait bij het laden
     * vanzelf om: in een diagram (de code en de blokjes, zodat je commentaar blijft staan), in een RC Car (in de wereld en
     * als item) en in een Training End-blok. De rest van de code (commentaar, {@code count: int = 0}, Windows-regels)
     * blijft staan.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_old_params_upgraded_on_load(GameTestHelper helper) {
        // De omzetter zelf.
        String oldCode = "# opens a door\ncount: int = 0\n\ndef open(door: string, secs:float, x):  # with a pulse\n"
                + "    pulse(door, secs)\n    count = count + 1\n\nwhen every(10):\n    open(\"Lamp\", 2, 1)\n";
        String newCode = "# opens a door\ncount: int = 0\n\ndef open(string door, float secs, x):  # with a pulse\n"
                + "    pulse(door, secs)\n    count = count + 1\n\nwhen every(10):\n    open(\"Lamp\", 2, 1)\n";
        String upgraded = com.senne.imttech.logic.program.CodeUpgrade.source(oldCode);
        if (!upgraded.equals(newCode)) {
            throw new GameTestAssertException("De oude code werd niet goed omgezet:\n" + upgraded);
        }
        if (!com.senne.imttech.logic.program.CodeUpgrade.source(newCode).equals(newCode)) {
            throw new GameTestAssertException("Code die al goed stond, veranderde toch");
        }
        String windows = com.senne.imttech.logic.program.CodeUpgrade.source("def a(n: int):\r\n    pass\r\n");
        if (!windows.equals("def a(int n):\r\n    pass\r\n")) {
            throw new GameTestAssertException("Code met Windows-regels werd niet goed omgezet: " + windows.replace("\r", "\\r"));
        }
        // Een diagram zoals een oude wereld het bewaarde: de oude code, en een DEF-blokje met door: string.
        LogicProgram fresh = new LogicProgram("Doors", true, GraphConverter.toGraph(ScriptParser.parse(newCode), null), newCode);
        CompoundTag tag = fresh.save();
        tag.putString("Source", oldCode);
        boolean foundDef = false;
        for (net.minecraft.nbt.Tag element : tag.getCompound("Graph").getList("Nodes", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            if (entry.getString("o").equals("DEF")) {
                entry.putString("e", "door: string, secs: float, x");
                foundDef = true;
            }
        }
        if (!foundDef) {
            throw new GameTestAssertException("De test vond geen DEF-blokje om oud te maken");
        }
        LogicProgram loaded = LogicProgram.load(tag);
        if (!loaded.source().equals(newCode)) {
            throw new GameTestAssertException("De code van het diagram werd bij het laden niet omgezet:\n" + loaded.source());
        }
        if (!loaded.graph().problems(ProgramKind.SERVER).isEmpty()) {
            throw new GameTestAssertException("De blokjes kloppen na het laden niet: " + loaded.graph().problems(ProgramKind.SERVER).getFirst().message());
        }
        String fromBlocks = ScriptPrinter.print(GraphConverter.toScript(loaded.graph()));
        if (!ScriptPrinter.print(ScriptParser.parse(loaded.source())).equals(fromBlocks)) {
            throw new GameTestAssertException("De omgezette code past niet meer bij de blokjes (dan viel je commentaar weg):\n" + fromBlocks);
        }
        // Een RC Car in de wereld, en een als item.
        String oldCar = "def lane(length: int):\n    forward(length)\n\ndef home():\n    lane(3)\n";
        String newCar = "def lane(int length):\n    forward(length)\n\ndef home():\n    lane(3)\n";
        com.senne.imttech.car.RcCarEntity car = helper.spawn(com.senne.imttech.registry.ModEntities.RC_CAR.get(), new BlockPos(4, 2, 4));
        CompoundTag carTag = car.saveWithoutId(new CompoundTag());
        carTag.putString("Source", oldCar);
        car.load(carTag);
        if (!car.source().equals(newCar) || !car.functions().equals(List.of("home"))) {
            throw new GameTestAssertException("De RC Car werd niet omgezet: " + car.source() + " / functies " + car.functions());
        }
        com.senne.imttech.car.RcCarData data = com.senne.imttech.car.RcCarData.DEFAULT;
        car.applyData(new com.senne.imttech.car.RcCarData("", oldCar, java.util.Optional.empty(), data.cell(), data.trunk(), data.drill(),
                data.robotArm(), data.program(), data.programServer()));
        if (!car.source().equals(newCar) || !car.functions().equals(List.of("home"))) {
            throw new GameTestAssertException("Een RC Car uit een oud item werd niet omgezet: " + car.source());
        }
        // Een Training End-blok.
        BlockPos endPos = new BlockPos(8, 2, 8);
        helper.setBlock(endPos, ModBlocks.TRAINING_END.get());
        var end = (com.senne.imttech.training.TrainingEndBlockEntity) helper.getBlockEntity(endPos);
        var registries = helper.getLevel().registryAccess();
        CompoundTag endTag = end.saveWithoutMetadata(registries);
        endTag.putString("Code", "def score(points: int):\n    log(\"hit \" + points)\n\nwhen trainee_hit:\n    score(hits)\n");
        end.loadWithComponents(endTag, registries);
        String endCode = "def score(int points):\n    log(\"hit \" + points)\n\nwhen trainee_hit:\n    score(hits)\n";
        if (!end.source().equals(endCode)) {
            throw new GameTestAssertException("Het Training End-blok werd niet omgezet: " + end.source());
        }
        ScriptParser.parse(end.source(), ProgramKind.TRAINING_END); // en de omgezette code klopt
        helper.succeed();
    }

    /**
     * Hoofdstuk 43 in een wereld: een global script met de gedeelde naam {@code lockdown}; een hendel zet hem om, en een
     * timer in een ander diagram ziet dezelfde waarde (de lamp volgt). De waarde blijft bewaard na opslaan en laden.
     */
    @GameTest(template = ARENA, timeoutTicks = 260, batch = "logic_runner_shared")
    public static void logic_shared_names_run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        BlockPos lamp = relay(helper, setup, new BlockPos(9, 3, 4), "Lamp");
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
            SecurityOs.open(setup.owner(), setup.coreAbs());
            save(setup, "Lib", "global\n\nlockdown: bool = false\n");
            saveWith(setup, "Doors", """
                    when lever("Night"):
                        lockdown = lever.on

                    when every(1):
                        if lockdown:
                            on("Lamp")
                        else:
                            off("Lamp")
                    """);
            if (setup.core().logic().program("Doors") == null) {
                throw new GameTestAssertException("Het diagram werd niet bewaard: " + lastLog(setup.core()));
            }
            if (!Boolean.FALSE.equals(setup.core().logic().sharedValues().get("lockdown"))) {
                throw new GameTestAssertException("De gedeelde naam begon niet op false: " + setup.core().logic().sharedValues());
            }
            // De hendel gaat om (alsof iemand hem omzette).
            LogicRunner.fire(level, setup.core(), Op.LEVER, "Night", new LogicRunner.Context(Op.LEVER, "Night", null,
                    setup.owner().getUUID(), "Senne", true, true, null, false, "", null, List.of(), true));
        });
        helper.runAfterDelay(60, () -> {
            check(Boolean.TRUE.equals(setup.core().logic().sharedValues().get("lockdown")),
                    "De hendel zette lockdown niet op true: " + setup.core().logic().sharedValues() + " / " + lastLog(setup.core()));
            check(level.getBlockState(lamp).getValue(RelayBlock.POWERED), "De timer in het andere diagram zag lockdown niet (lamp uit)");
            // Opslaan en laden houdt de waarde.
            var reloaded = com.senne.imttech.logic.LogicStore.load(setup.core().logic().save());
            check(Boolean.TRUE.equals(reloaded.sharedValues().get("lockdown")), "Na opslaan en laden was lockdown weg: " + reloaded.sharedValues());
            LogicRunner.fire(level, setup.core(), Op.LEVER, "Night", new LogicRunner.Context(Op.LEVER, "Night", null,
                    setup.owner().getUUID(), "Senne", true, true, null, false, "", null, List.of(), false));
        });
        helper.runAfterDelay(120, () -> {
            check(Boolean.FALSE.equals(setup.core().logic().sharedValues().get("lockdown")), "De hendel zette lockdown niet terug op false");
            check(!level.getBlockState(lamp).getValue(RelayBlock.POWERED), "De lamp ging niet uit toen lockdown false werd");
            helper.succeed();
        });
    }

    /**
     * Hoofdstuk 43 in een wereld: een def met parameters uit een global script ({@code open_door("Lamp", 2)}) pulst het
     * apparaat dat je meegeeft; een gedeelde {@code total: int} rondt af naar een heel getal (2,5 wordt 2, dan 4).
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "logic_runner_shared")
    public static void logic_def_params_run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        BlockPos lamp = relay(helper, setup, new BlockPos(9, 3, 4), "Lamp");
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
            SecurityOs.open(setup.owner(), setup.coreAbs());
            save(setup, "Lib", GLOBAL_SCRIPT);
            saveWith(setup, "Timer", """
                    when every(1):
                        total = total + 2.5
                        if total == 2:
                            open_door("Lamp", 2)
                    """);
            if (setup.core().logic().program("Timer") == null) {
                throw new GameTestAssertException("Het diagram werd niet bewaard: " + lastLog(setup.core()));
            }
        });
        // De timer gaat na 1 s af (sinds hoofdstuk 86 meteen: 1 apparaat op één CPU): dan is total 2 (2,5 afgerond) en pulst
        // open_door de lamp 2 s. De tweede keer is een seconde later.
        helper.runAfterDelay(35, () -> {
            check(Double.valueOf(2.0).equals(setup.core().logic().sharedValues().get("total")),
                    "total zou 2 moeten zijn (2,5 afgerond naar een heel getal), niet " + setup.core().logic().sharedValues().get("total")
                            + ". Logboek: " + lastLog(setup.core()));
            check(level.getBlockState(lamp).getValue(RelayBlock.POWERED), "open_door(\"Lamp\", 2) zette de lamp niet aan. Logboek: " + lastLog(setup.core()));
        });
        // De tweede keer (na 2 s): 4,5 wordt 4.
        helper.runAfterDelay(58, () -> check(Double.valueOf(4.0).equals(setup.core().logic().sharedValues().get("total")),
                "total zou na de tweede keer 4 moeten zijn, niet " + setup.core().logic().sharedValues().get("total")));
        helper.runAfterDelay(95, () -> {
            check(!level.getBlockState(lamp).getValue(RelayBlock.POWERED), "De puls van 2 s hield niet op");
            helper.succeed();
        });
    }

    /** Zoals {@link #expectError}, maar met de defs en gedeelde namen van global scripts erbij. */
    private static void expectErrorWith(String code, com.senne.imttech.logic.program.Library library, int line, String part) {
        try {
            ScriptParser.parse(code, ProgramKind.SERVER, library);
        } catch (LogicException e) {
            if (e.line() != line || !e.getMessage().contains(part)) {
                throw new GameTestAssertException("Voor:\n" + code + "\nverwachtte ik regel " + line + " met \"" + part
                        + "\", maar kreeg: " + e.describe());
            }
            return;
        }
        throw new GameTestAssertException("Deze code zou een fout moeten geven:\n" + code);
    }

    /** Zoals {@link #save}, maar de code mag de defs en gedeelde namen van de global scripts op die kern gebruiken. */
    private static void saveWith(Setup setup, String name, String code) {
        var library = com.senne.imttech.logic.program.Library.of(setup.core().logic().programs(), name);
        CompoundTag args = new CompoundTag();
        args.putString("old", "");
        args.putString("name", name);
        args.put("graph", GraphConverter.toGraph(ScriptParser.parse(code, ProgramKind.SERVER, library), null).save());
        args.putString("source", code);
        SecurityOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), "logic_save", args));
    }

    private static void expectError(String code, int line, String part) {
        try {
            ScriptParser.parse(code);
        } catch (LogicException e) {
            if (e.line() != line || !e.getMessage().contains(part)) {
                throw new GameTestAssertException("Voor:\n" + code + "\nverwachtte ik regel " + line + " met \"" + part
                        + "\", maar kreeg: " + e.describe());
            }
            return;
        }
        throw new GameTestAssertException("Deze code zou een fout moeten geven:\n" + code);
    }

    /**
     * Rekenen (hoofdstuk 20, stap 2): + - * / met de voorrang van school (keer en delen eerst),
     * haakjes, een minteken, tekst plakken met +, en delen door 0 geeft 0. Terugschrijven zet alleen
     * de haakjes die nodig zijn, en als blokjes is het gewoon een if.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_arithmetic(GameTestHelper helper) {
        String code = """
                when keycard("Lab door"):
                    if card.number * 2 - 1 >= 5 and card.name + "!" == "Boss!":
                        accept()
                    elif -card.number < -3 or (card.number + 1) * 2 == 8:
                        deny()
                """;
        String printed = ScriptPrinter.print(ScriptParser.parse(code));
        check(printed.equals(code), "Rekenen terugschrijven gaf andere code:\n" + printed);
        LogicGraph graph = GraphConverter.toGraph(ScriptParser.parse(code), null);
        check(graph.problems().isEmpty(), "De blokjes met rekenen hebben fouten: " + graph.problems());
        check(ScriptPrinter.print(GraphConverter.toScript(graph)).equals(code), "Van blokjes terug naar code ging mis");
        // Haakjes alleen waar ze nodig zijn: overbodige vallen weg, nodige blijven staan.
        String messy = "when every(5):\n    if ((2 + 3) * 4) == 20 and 10 - (4 - 1) == 7 and 2 + (3 * 4) == 14:\n        pass\n";
        String clean = ScriptPrinter.print(ScriptParser.parse(messy));
        check(clean.equals("when every(5):\n    if (2 + 3) * 4 == 20 and 10 - (4 - 1) == 7 and 2 + 3 * 4 == 14:\n        pass\n"),
                "De haakjes kloppen niet:\n" + clean);
        // Uitrekenen, met een kaart van niveau 3 met de naam "Boss".
        Expr.Facts card = new Expr.Facts() {
            @Override
            public Object value(String variable) {
                return switch (variable) {
                    case "card.number" -> 3.0;
                    case "card.name" -> "Boss";
                    default -> false;
                };
            }

            @Override
            public boolean isOn(String device) {
                return false;
            }
        };
        for (String yes : List.of("card.number * 2 - 1 >= 5", "2 + 3 * 4 == 14", "(2 + 3) * 4 == 20", "10 / 4 == 2.5",
                "1 / 0 == 0", "-card.number == -3", "card.name + \"!\" == \"Boss!\"", "\"Level \" + card.number == \"Level 3\"",
                "10 - 4 - 3 == 3", "-(card.number + 1) == -4")) {
            Expr expr = ExprCache.get(yes, Op.KEYCARD);
            check(expr != null && ExprCache.test(expr, card), "Dit zou waar moeten zijn: " + yes);
        }
        Expr no = ExprCache.get("card.number * 2 - 1 >= 6", Op.KEYCARD);
        check(no != null && !ExprCache.test(no, card), "3 * 2 - 1 is 5, niet 6 of meer");
        // Foutmeldingen die een beginner snapt.
        expectError("when keycard(\"Lab\"):\n    if card.name - 1 > 0:\n        pass", 2, "only subtract numbers");
        expectError("when keycard(\"Lab\"):\n    if card.number * \"2\" > 1:\n        pass", 2, "only multiply numbers");
        expectError("when keycard(\"Lab\"):\n    if card.valid + 1 > 0:\n        pass", 2, "only add numbers");
        expectError("when keycard(\"Lab\"):\n    if -card.name == \"x\":\n        pass", 2, "only works with a number");
        expectError("when keycard(\"Lab\"):\n    if card.number + 1:\n        pass", 2, "must be true or false");
        expectError("when keycard(\"Lab\"):\n    if card.number + :\n        pass", 2, "I did not expect");
        helper.succeed();
    }

    /**
     * Namen (variabelen, hoofdstuk 20, stap 3): helemaal links voor het hele programma, in een when of
     * def alleen voor die run. Terugschrijven, blokjes, opslaan en laden, en de foutmeldingen.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_variables(GameTestHelper helper) {
        String code = """
                count = 0
                limit = 3 * 2

                when keycard("Lab door"):
                    count = count + 1
                    label = "Card " + card.name
                    if count >= limit:
                        alarm("Too many cards")
                        count = 0

                def reset():
                    count = 0
                """;
        String printed = ScriptPrinter.print(ScriptParser.parse(code));
        check(printed.equals(code), "Namen terugschrijven gaf andere code:\n" + printed);
        LogicGraph graph = GraphConverter.toGraph(ScriptParser.parse(code), null);
        check(graph.problems().isEmpty(), "De blokjes met namen hebben fouten: " + graph.problems());
        String back = ScriptPrinter.print(GraphConverter.toScript(graph));
        check(back.equals(code), "Van blokjes terug naar code ging mis:\n" + back);
        // Opslaan en weer laden (de waarde staat onder "e"): hetzelfde diagram.
        LogicGraph loaded = LogicGraph.load(graph.save());
        check(ScriptPrinter.print(GraphConverter.toScript(loaded)).equals(code), "Na opslaan en laden was het anders");
        // Fouten die een beginner snapt.
        expectError("when every(5):\n    if count > 1:\n        pass", 2, "count has no value yet");
        expectError("count = 0\nwhen every(5):\n    count = \"x\"", 3, "count holds a number");
        expectError("when every(5):\n    on = 1", 2, "already a word of the language");
        expectError("when keycard(\"A\"):\n    card.number = 3", 2, "not give it a value");
        expectError("def go():\n    pass\nwhen every(5):\n    go = 1", 4, "one of your own blocks");
        expectError("x = card.number\nwhen every(5):\n    pass", 1, "only exists inside a when");
        expectError("when every(5):\n    if is_nigt:\n        pass", 2, "Did you mean is_night?");
        expectError("when every(5):\n    x =", 2, "Give x a value");
        // Een def ziet de namen van de when die hem gebruikt niet (zoals een functie in Python).
        expectError("when every(5):\n    x = 1\n    helper()\ndef helper():\n    if x > 0:\n        pass", 5, "x has no value yet");
        expectError("count = 0\ncount = 1\nwhen every(5):\n    pass", 2, "already is a count");
        expectError("when keycard(\"Lab\"):\n    if card.number = 3:\n        accept()", 2, "Use == to compare");
        helper.succeed();
    }

    /**
     * Berekeningen in een actie (hoofdstuk 20, stap 6): een bericht en de seconden mogen een berekening
     * zijn; een gewone tekst of een gewoon getal blijft zoals vroeger; een apparaat blijft gewone tekst.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_values_in_actions(GameTestHelper helper) {
        String code = """
                delay = 2

                when every(10):
                    count = 3
                    say("Count: " + count)
                    log("Night: " + is_night)
                    wait(delay * 2)
                    pulse("Lamp", delay + 0.5)
                    alarm("Level " + count)
                    say("plain text")
                    wait(1)
                    pulse("Lamp")
                """;
        String printed = ScriptPrinter.print(ScriptParser.parse(code));
        check(printed.equals(code), "Berekeningen terugschrijven gaf andere code:\n" + printed);
        LogicGraph graph = GraphConverter.toGraph(ScriptParser.parse(code), null);
        check(graph.problems().isEmpty(), "De blokjes met berekeningen hebben fouten: " + graph.problems());
        check(ScriptPrinter.print(GraphConverter.toScript(LogicGraph.load(graph.save()))).equals(code),
                "Na blokjes, opslaan en laden was het anders");
        // Een gewone tekst blijft gewone tekst (oude diagrammen zien er hetzelfde uit).
        check(graph.nodes().stream().anyMatch(node -> node.op == Op.SAY && node.text.equals("plain text") && node.expr.isEmpty()),
                "say(\"plain text\") werd een berekening");
        expectError("when every(5):\n    wait(\"x\")", 2, "wait needs a number of seconds");
        // Sinds hoofdstuk 43 mag een apparaat ook code zijn: on("A" + "B") is een berekende naam (en komt zo terug).
        String codeDevice = "when every(5):\n    on(\"A\" + \"B\")\n";
        if (!ScriptPrinter.print(ScriptParser.parse(codeDevice)).equals(codeDevice)) {
            throw new GameTestAssertException("Een apparaat als code kwam anders terug: " + ScriptPrinter.print(ScriptParser.parse(codeDevice)));
        }
        expectError("when every(x):\n    pass", 1, "every needs a plain number");
        expectError("when every(5):\n    say(missing)", 2, "If you meant text, put it between quotes");
        expectError("when every(5):\n    pulse(\"Lamp\", \"x\")", 2, "pulse needs a number of seconds");
        expectError("when every(5):\n    accept(\"x\")", 2, "accept does not take a name");
        helper.succeed();
    }

    /**
     * Oude diagrammen (hoofdstuk 20, stap 8): een def die nu een woord van de taal heet ({@code repeat}),
     * krijgt bij het laden een andere naam ({@code repeat_1}), de blokjes die hem gebruiken gaan mee, en de
     * bewaarde code is weer geldig.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_old_programs(GameTestHelper helper) {
        LogicGraph old = new LogicGraph();
        LogicGraph.Node def = old.add(Op.DEF, 0, 0);
        def.text = "repeat";
        LogicGraph.Node log = old.add(Op.LOG, 0, 0);
        log.text = "hi";
        def.next = log.id;
        LogicGraph.Node every = old.add(Op.EVERY, 0, 0);
        every.number = 5;
        LogicGraph.Node use = old.add(Op.CALL, 0, 0);
        use.text = "repeat";
        every.next = use.id;
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", "Old");
        tag.putBoolean("On", true);
        tag.put("Graph", old.save());
        tag.putString("Source", "def repeat():\n    log(\"hi\")\n\nwhen every(5):\n    repeat()\n");
        var program = com.senne.imttech.logic.program.LogicProgram.load(tag);
        check(program.graph().nodes().stream().anyMatch(node -> node.op == Op.DEF && node.text.equals("repeat_1"))
                        && program.graph().nodes().stream().anyMatch(node -> node.op == Op.CALL && node.text.equals("repeat_1")),
                "De def repeat kreeg geen nieuwe naam (of de use def ging niet mee)");
        String expected = "def repeat_1():\n    log(\"hi\")\n\nwhen every(5):\n    repeat_1()\n";
        check(program.source().equals(expected), "De bewaarde code werd niet opnieuw geschreven:\n" + program.source());
        ScriptParser.parse(program.source()); // gooit een fout als de code niet klopt
        check(program.graph().problems().isEmpty(), "Het oude diagram heeft na het laden fouten: " + program.graph().problems());
        helper.succeed();
    }

    /**
     * Tekst met namen erin (19 oktober, jouw notitie: {@code $"dit is de {aantal} test"}): in een bericht, een naam = waarde
     * en een if; tussen { en } een naam, een berekening, een gegeven of tekst; {{ en }} zijn gewone haakjes. Terugschrijven,
     * blokjes, opslaan en laden, uitrekenen, en de fouten.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_text_with_names(GameTestHelper helper) {
        String code = """
                count = 0

                when keycard("Lab door"):
                    count = count + 1
                    say($"This is test {count}")
                    label = $"Card {card.name + "!"} level {card.number * 2}"
                    log($"{{braces}} and {label}")
                    if $"{count}" == "3":
                        alarm($"Card {card.number} came {count} times")
                """;
        String printed = ScriptPrinter.print(ScriptParser.parse(code));
        check(printed.equals(code), "Tekst met namen terugschrijven gaf andere code:\n" + printed);
        LogicGraph graph = GraphConverter.toGraph(ScriptParser.parse(code), null);
        check(graph.problems().isEmpty(), "De blokjes met $\"...\" hebben fouten: " + graph.problems());
        check(ScriptPrinter.print(GraphConverter.toScript(LogicGraph.load(graph.save()))).equals(code),
                "Na blokjes, opslaan en laden was het anders");
        // Uitrekenen: een getal zonder overbodige nullen, tekst, true/false, {{ en }} als gewone haakjes.
        Expr.Facts facts = new Expr.Facts() {
            @Override
            public Object value(String variable) {
                return switch (variable) {
                    case "card.number" -> 3.0;
                    case "card.name" -> "Boss";
                    default -> true;
                };
            }

            @Override
            public boolean isOn(String device) {
                return false;
            }

            @Override
            public Object variable(String name) {
                return name.equals("count") ? 2.0 : null;
            }
        };
        String[][] cases = {
                {"$\"This is test {count}\"", "This is test 2"},
                {"$\"Round {count + 1} of {count * 2.5}\"", "Round 3 of 5"},
                {"$\"Card {card.name + \"!\"} level {card.number}\"", "Card Boss! level 3"},
                {"$\"{{count}} is {count}\"", "{count} is 2"},
                {"$\"Night: {is_night}\"", "Night: true"},
                {"$\"no names\"", "no names"},
        };
        for (String[] test : cases) {
            Expr expr = ExprCache.value(test[0]);
            check(expr != null, "Dit kon ik niet lezen: " + test[0]);
            Object value = ExprCache.eval(expr, facts);
            check(test[1].equals(value), test[0] + " werd \"" + value + "\" in plaats van \"" + test[1] + "\"");
        }
        // Fouten die een beginner snapt.
        expectError("when every(5):\n    say($\"Count {count\")", 2, "missing its }");
        expectError("when every(5):\n    say($\"Count {}\")", 2, "Something is missing between { and }");
        expectError("when every(5):\n    say($\"a } b\")", 2, "needs a { before it");
        expectError("when every(5):\n    say($\"Count {missing}\")", 2, "missing has no value yet");
        expectError("when every(5):\n    say($\"Level {card.number}\")", 2, "card.number only works inside");
        // Sinds hoofdstuk 43 mag een apparaat ook tekst met namen erin zijn (een berekende naam).
        String templateDevice = "when every(5):\n    on($\"Lamp {1}\")\n";
        if (!ScriptPrinter.print(ScriptParser.parse(templateDevice)).equals(templateDevice)) {
            throw new GameTestAssertException("Een apparaat als $\"...\" kwam anders terug: " + ScriptPrinter.print(ScriptParser.parse(templateDevice)));
        }
        expectError("when every(5):\n    say($\"Open)", 2, "missing its closing quote");
        helper.succeed();
    }

    /** Tekst met namen erin tijdens het uitvoeren: in het logboek staan de waarden zelf. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "logic_runner_text")
    public static void logic_text_with_names_run(GameTestHelper helper) {
        Setup setup = setup(helper);
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
            SecurityOs.open(setup.owner(), setup.coreAbs());
            save(setup, "Template", """
                    rounds = 0

                    when every(1):
                        rounds = rounds + 1
                        if rounds == 2:
                            log($"Round {rounds}: {rounds * 3} points, night {is_night}")
                    """);
            check(setup.core().logic().program("Template") != null, "Het diagram werd niet bewaard: " + lastLog(setup.core()));
        });
        helper.runAfterDelay(80, () -> {
            // (Dag of nacht hangt af van de tijd in de testwereld: allebei goed, als het maar ingevuld is.)
            check(logged(setup.core(), "Round 2: 6 points, night true") || logged(setup.core(), "Round 2: 6 points, night false"),
                    "De ingevulde tekst staat niet in het logboek: " + lastLog(setup.core()));
            helper.succeed();
        });
    }

    /** Berekeningen tijdens het uitvoeren: een berekend bericht in het logboek, en berekende seconden wachten. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "logic_runner_values")
    public static void logic_values_run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        BlockPos lamp = relay(helper, setup, new BlockPos(9, 3, 4), "Lamp");
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
            SecurityOs.open(setup.owner(), setup.coreAbs());
            save(setup, "Values", """
                    delay = 1

                    when every(1):
                        count = 2
                        log("Computed " + count * 3)
                        wait(delay * 2)
                        on("Lamp")
                    """);
            check(setup.core().logic().program("Values") != null, "Het diagram werd niet bewaard: " + lastLog(setup.core()));
        });
        // De eerste run begint na ~1,5 s: meteen "Computed 6" in het logboek, en dan 2 s wachten.
        helper.runAfterDelay(60, () -> {
            check(logged(setup.core(), "Computed 6"), "Het berekende bericht staat niet in het logboek: " + lastLog(setup.core()));
            check(!level.getBlockState(lamp).getValue(RelayBlock.POWERED), "De lamp wachtte niet de berekende 2 seconden");
        });
        helper.runAfterDelay(110, () -> {
            check(level.getBlockState(lamp).getValue(RelayBlock.POWERED), "De lamp ging niet aan na het wachten");
            helper.succeed();
        });
    }

    /**
     * Lussen (hoofdstuk 20, stap 4; de for-lus van 16 oktober): for, while (ook while true) en break, in code en als
     * blokjes. De naam van een for gebruik je als elke andere naam; repeat en forever bestaan niet meer, en dat zegt de
     * code ook.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_loops(GameTestHelper helper) {
        String code = """
                when every(10):
                    for i in range(3):
                        pulse("Lamp", 0.5)
                        wait(1)
                    total = 0
                    for round in range(1, total + 4):
                        total = total + round
                        say("Round " + round)
                    count = 0
                    while count < 5:
                        count = count + 1
                        if count == 4:
                            break
                    while true:
                        wait(1)
                        if is_night:
                            break
                    log("done after " + i)
                """;
        String printed = ScriptPrinter.print(ScriptParser.parse(code));
        check(printed.equals(code), "Lussen terugschrijven gaf andere code:\n" + printed);
        LogicGraph graph = GraphConverter.toGraph(ScriptParser.parse(code), null);
        check(graph.problems().isEmpty(), "De blokjes met lussen hebben fouten: " + graph.problems());
        String back = ScriptPrinter.print(GraphConverter.toScript(graph));
        check(back.equals(code), "Van blokjes terug naar code ging mis:\n" + back);
        // Een lus-blokje heeft de uitgangen "do" (YES, de stappen erin) en "after" (NEXT); een FOR bewaart zijn naam en
        // zijn bereik.
        LogicGraph.Node loop = graph.nodes().stream().filter(node -> node.op == Op.FOR).findFirst().orElseThrow();
        check(loop.ports().equals(List.of(LogicGraph.Port.YES, LogicGraph.Port.NEXT)) && loop.yes >= 0 && loop.next >= 0,
                "Het FOR-blokje heeft niet de uitgangen do en after");
        check(loop.text.equals("i") && loop.expr.equals("3"), "Het FOR-blokje bewaart " + loop.text + " / " + loop.expr);
        // Fouten.
        expectError("when every(5):\n    break", 2, "only works inside for or while");
        expectError("when every(5):\n    for i in range(\"x\"):\n        pass", 2, "range needs numbers");
        expectError("when every(5):\n    for i in range(1, 2, 3):\n        pass", 2, "one or two numbers");
        expectError("when every(5):\n    for i in range(3)\n        pass", 2, "must end with a :");
        expectError("when every(5):\n    for i range(3):\n        pass", 2, "After the name comes in");
        expectError("when every(5):\n    for i in doors:\n        pass", 2, "counts with range");
        expectError("when every(5):\n    for in range(3):\n        pass", 2, "needs a name that counts");
        expectError("when every(5):\n    for card.number in range(3):\n        pass", 2, "built-in information");
        expectError("when every(5):\n    name = \"Ann\"\n    for name in range(3):\n        pass", 3, "holds text, so it cannot count");
        expectError("when every(5):\n    for i in range(j):\n        pass", 2, "j has no value yet");
        expectError("when every(5):\n    while 3:\n        pass", 2, "must be true or false");
        expectError("for i in range(3):\n    pass", 1, "must be inside a when");
        expectError("when every(5):\n    while:\n        pass", 2, "while needs a check");
        expectError("def go():\n    break\nwhen every(5):\n    for i in range(2):\n        go()", 2, "only works inside");
        expectError("when every(5):\n    repeat(3):\n        pass", 2, "for i in range(3)");
        expectError("when every(5):\n    forever:\n        pass", 2, "while true");
        helper.succeed();
    }

    /**
     * Oude lussen (16 oktober): een diagram van vroeger met {@code repeat(3):} en {@code forever:} laadt als een for en een
     * while true. Een repeat in een repeat krijgt j (i was al van de buitenste), en een naam die het programma al gebruikt
     * (hier i) wordt overgeslagen. De bewaarde code wordt mee omgezet en houdt zijn commentaar.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_old_loops(GameTestHelper helper) {
        // Zoals een diagram van vóór 16 oktober bewaard werd: het blokje REPEAT (met zijn aantal als tekst) en FOREVER.
        CompoundTag graph = new CompoundTag();
        net.minecraft.nbt.ListTag nodes = new net.minecraft.nbt.ListTag();
        nodes.add(oldNode(1, "EVERY", "", 5, -1, -1, 2));
        CompoundTag set = oldNode(2, "SET", "i", 0, -1, -1, 3); // i = 2: de naam i is al van het programma
        set.putString("e", "2");
        nodes.add(set);
        nodes.add(oldNode(3, "REPEAT", "3", 0, 4, -1, 6));
        nodes.add(oldNode(4, "REPEAT", "i", 0, 5, -1, -1));
        nodes.add(oldNode(5, "LOG", "hi", 0, -1, -1, -1));
        nodes.add(oldNode(6, "FOREVER", "", 0, 7, -1, -1));
        nodes.add(oldNode(7, "WAIT", "", 1, -1, -1, -1));
        graph.put("Nodes", nodes);
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", "Old loops");
        tag.putBoolean("On", true);
        tag.put("Graph", graph);
        tag.putString("Source", """
                # an old diagram
                when every(5):
                    i = 2
                    repeat(3):  # three times
                        repeat(i):
                            log("hi")
                    forever:
                        wait(1)
                """);
        var program = com.senne.imttech.logic.program.LogicProgram.load(tag);
        String expected = """
                # an old diagram
                when every(5):
                    i = 2
                    for j in range(3):  # three times
                        for k in range(i):
                            log("hi")
                    while true:
                        wait(1)
                """;
        check(program.source().equals(expected), "De oude code werd niet goed omgezet:\n" + program.source());
        check(program.graph().problems().isEmpty(), "Het omgezette diagram heeft fouten: " + program.graph().problems());
        LogicGraph.Node outer = program.graph().node(3);
        LogicGraph.Node inner = program.graph().node(4);
        LogicGraph.Node forever = program.graph().node(6);
        check(outer.op == Op.FOR && outer.text.equals("j") && outer.expr.equals("3")
                        && inner.op == Op.FOR && inner.text.equals("k") && inner.expr.equals("i")
                        && forever.op == Op.WHILE && forever.text.equals("true"),
                "De omgezette blokjes: " + outer.op + " " + outer.text + "/" + outer.expr + ", " + inner.op + " " + inner.text
                        + "/" + inner.expr + ", " + forever.op + " " + forever.text);
        // Code die niet bij de blokjes past (hier: leeg), wordt gewoon opnieuw uit de blokjes geschreven.
        tag.putString("Source", "");
        String printed = com.senne.imttech.logic.program.LogicProgram.load(tag).source();
        check(printed.contains("for j in range(3):") && printed.contains("while true:"), "Zonder code: " + printed);
        helper.succeed();
    }

    /** Eén blokje zoals LogicGraph.save het bewaart, met de naam van zijn soort zoals vroeger. */
    private static CompoundTag oldNode(int id, String op, String text, double number, int yes, int no, int next) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("i", id);
        entry.putString("o", op);
        if (!text.isEmpty()) {
            entry.putString("t", text);
        }
        if (number != 0) {
            entry.putDouble("n", number);
        }
        entry.putInt("x", id * 100);
        entry.putInt("y", 0);
        entry.putInt("nx", next);
        entry.putInt("ys", yes);
        entry.putInt("no", no);
        return entry;
    }

    /**
     * Lussen tijdens het uitvoeren: for, while en break zetten samen een lamp aan (de naam van de for telt mee); en een
     * while true zonder wachten loopt over meerdere ticks (hooguit 64 blokjes per tick) zonder de server te laten hangen.
     */
    @GameTest(template = ARENA, timeoutTicks = 240, batch = "logic_runner_loops")
    public static void logic_loops_run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        BlockPos lamp = relay(helper, setup, new BlockPos(9, 3, 4), "Lamp");
        BlockPos fan = relay(helper, setup, new BlockPos(9, 3, 6), "Fan");
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
            SecurityOs.open(setup.owner(), setup.coreAbs());
            // 0 + 1 + 2 = 3 (range(3)), dan 3 + 4 = 7 (range(4, 5)), dan de while: niet meer (break bij 7).
            save(setup, "Loops", """
                    count = 0

                    when every(1):
                        for i in range(3):
                            count = count + i
                        for i in range(4, 5):
                            count = count + i
                            i = 100
                        while count < 10:
                            if count == 7:
                                break
                            count = count + 1
                        if count == 7 and i == 100:
                            on("Lamp")
                    """);
            save(setup, "Spinner", """
                    n = 0

                    when every(1):
                        while true:
                            n = n + 1
                            if n == 1000:
                                on("Fan")
                    """);
            check(setup.core().logic().program("Loops") != null && setup.core().logic().program("Spinner") != null,
                    "De diagrammen werden niet bewaard: " + lastLog(setup.core()));
        });
        // De while true loopt hooguit 64 blokjes per tick: na een paar ticks is hij nog lang niet op 1000.
        helper.runAfterDelay(40, () -> check(!level.getBlockState(fan).getValue(RelayBlock.POWERED),
                "De while true was te snel: hij liep niet over meerdere ticks"));
        // De eerste run: de for telt 0 + 1 + 2 en dan 4, de while stopt met break bij 7: de lamp aan. (En i = 100 in de
        // lus veranderde niet hoe hij telde: dat was zijn laatste ronde.)
        helper.runAfterDelay(60, () -> check(level.getBlockState(lamp).getValue(RelayBlock.POWERED),
                "for, while en break kwamen niet op 7. Logboek: " + lastLog(setup.core())));
        helper.runAfterDelay(200, () -> {
            check(level.getBlockState(fan).getValue(RelayBlock.POWERED),
                    "De while true kwam niet op 1000. Logboek: " + lastLog(setup.core()));
            helper.succeed();
        });
    }

    /**
     * Namen tijdens het uitvoeren: een naam van links houdt zijn waarde tussen de runs. Een timer telt
     * elke seconde één op, en pas bij 3 gaat de lamp aan.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "logic_runner_names")
    public static void logic_variables_run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        BlockPos lamp = relay(helper, setup, new BlockPos(9, 3, 4), "Lamp");
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
            SecurityOs.open(setup.owner(), setup.coreAbs());
            save(setup, "Counter", """
                    count = 0

                    when every(1):
                        count = count + 1
                        if count == 3:
                            on("Lamp")
                    """);
            if (setup.core().logic().program("Counter") == null) {
                throw new GameTestAssertException("Het diagram werd niet bewaard: " + lastLog(setup.core()));
            }
        });
        // De timer gaat na 1 s voor het eerst af (plus 0,5 s reactietijd): na 2,5 s is count hooguit 2.
        helper.runAfterDelay(50, () -> check(!level.getBlockState(lamp).getValue(RelayBlock.POWERED),
                "De lamp ging te vroeg aan (count was nog geen 3)"));
        helper.runAfterDelay(110, () -> {
            check(level.getBlockState(lamp).getValue(RelayBlock.POWERED),
                    "De lamp ging niet aan bij count 3. Logboek: " + lastLog(setup.core()));
            helper.succeed();
        });
    }

    /**
     * Code naar blokjes en terug geeft dezelfde code. De blokjes staan netjes (de yes-tak naast de if,
     * de no-tak eronder), en een when-blok dat niet veranderde, houdt zijn plek.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_blocks_and_code(GameTestHelper helper) {
        Script script = ScriptParser.parse(EXAMPLE);
        LogicGraph graph = GraphConverter.toGraph(script, null);
        if (graph.size() != 12 || !graph.problems().isEmpty()) {
            throw new GameTestAssertException("De blokjes kloppen niet: " + graph.size() + " blokjes, " + graph.problems());
        }
        String back = ScriptPrinter.print(GraphConverter.toScript(graph));
        if (!back.equals(EXAMPLE)) {
            throw new GameTestAssertException("Blokjes terug naar code gaf andere code:\n" + back);
        }
        // De if staat rechts van de trigger, zijn yes-tak op dezelfde rij, de no-tak eronder.
        LogicGraph.Node trigger = graph.nodes().iterator().next();
        LogicGraph.Node branch = graph.node(trigger.next);
        LogicGraph.Node yes = graph.node(branch.yes);
        LogicGraph.Node no = graph.node(branch.no);
        if (branch.x <= trigger.x || yes.y != branch.y || no.y <= branch.y || yes.x <= branch.x) {
            throw new GameTestAssertException("De blokjes staan niet netjes: trigger " + trigger.x + "," + trigger.y
                    + " if " + branch.x + "," + branch.y + " yes " + yes.x + "," + yes.y + " no " + no.x + "," + no.y);
        }
        // Schuif de trigger van het tweede blok opzij en verander alleen het eerste blok in de code.
        LogicGraph.Node timer = graph.nodes().stream().filter(node -> node.op == com.senne.imttech.logic.program.Op.EVERY)
                .findFirst().orElseThrow();
        timer.x = 500;
        timer.y = 700;
        String edited = EXAMPLE.replace("log(\"Lab door used\")", "log(\"Lab door used\")\n    wait(1)");
        LogicGraph again = GraphConverter.toGraph(ScriptParser.parse(edited), graph);
        LogicGraph.Node movedTimer = again.nodes().stream().filter(node -> node.op == com.senne.imttech.logic.program.Op.EVERY)
                .findFirst().orElseThrow();
        if (movedTimer.x != 500 || movedTimer.y != 700) {
            throw new GameTestAssertException("Het when-blok dat niet veranderde, verloor zijn plek: " + movedTimer.x + "," + movedTimer.y);
        }
        // Draadjes die niet mogen: een rondje, iets in een trigger.
        LogicGraph.Node first = again.node(again.nodes().iterator().next().next);
        if (again.connect(first.yes, LogicGraph.Port.NEXT, first.id) == null) {
            throw new GameTestAssertException("Een draadje dat een rondje maakt, werd toegelaten");
        }
        if (again.connect(first.id, LogicGraph.Port.NEXT, again.nodes().iterator().next().id) == null) {
            throw new GameTestAssertException("Een draadje naar een trigger werd toegelaten");
        }
        helper.succeed();
    }

    // ================================================================ Je eigen blokken (def)

    /** Een diagram met defs, zoals de printer het schrijft (een def mag ook onder de when staan). */
    private static final String WITH_DEFS = """
            when keycard("Lab door"):
                if card.in_group("lab"):
                    open_lab()
                else:
                    deny()

            def open_lab():
                accept()
                lights_on()
                pulse("Vault door", 3)

            when codepanel("Lab panel"):
                if code == "4321":
                    open_lab()

            def lights_on():
                on("Lab lights")
            """;

    /** Code met defs lezen, terugschrijven en omzetten naar blokjes en terug geeft dezelfde code. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_functions_round_trip(GameTestHelper helper) {
        Script script = ScriptParser.parse(WITH_DEFS);
        if (script.functions().size() != 2 || script.handlers().size() != 2 || script.blocks() != 13) {
            throw new GameTestAssertException("De defs werden niet goed gelezen: " + script.functions().size() + " defs, "
                    + script.handlers().size() + " whens, " + script.blocks() + " blokjes");
        }
        String printed = ScriptPrinter.print(script);
        if (!printed.equals(WITH_DEFS)) {
            throw new GameTestAssertException("Terugschrijven gaf andere code:\n" + printed);
        }
        LogicGraph graph = GraphConverter.toGraph(script, null);
        if (!graph.problems().isEmpty()) {
            throw new GameTestAssertException("De blokjes van een goed diagram hebben fouten: " + graph.problems());
        }
        String back = ScriptPrinter.print(GraphConverter.toScript(graph));
        if (!back.equals(WITH_DEFS)) {
            throw new GameTestAssertException("Blokjes terug naar code gaf andere code:\n" + back);
        }
        // In de blokjes: niets mag in een DEF, en een use-def naar een def die niet bestaat is een fout.
        LogicGraph.Node def = graph.nodes().stream().filter(node -> node.op == com.senne.imttech.logic.program.Op.DEF)
                .findFirst().orElseThrow();
        LogicGraph.Node trigger = graph.nodes().iterator().next();
        if (graph.connect(trigger.id, LogicGraph.Port.NEXT, def.id) == null) {
            throw new GameTestAssertException("Een draadje naar een DEF werd toegelaten");
        }
        LogicGraph.Node call = graph.nodes().stream().filter(node -> node.op == com.senne.imttech.logic.program.Op.CALL)
                .findFirst().orElseThrow();
        call.text = "opn_lab";
        boolean found = graph.problems().stream().anyMatch(problem -> problem.node() == call.id
                && problem.message().contains("Did you mean open_lab?"));
        if (!found) {
            throw new GameTestAssertException("Een use-def naar een onbekende def gaf geen goede fout: " + graph.problems());
        }
        helper.succeed();
    }

    /** De fouten met defs: op de goede regel, en uitleg die een beginner snapt. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_function_errors(GameTestHelper helper) {
        expectError("def a():\n    b()\ndef b():\n    a()\nwhen every(5):\n    a()", 1, "a() uses itself (a > b > a)");
        expectError("def open_door():\n    pass\nwhen every(5):\n    opn_door()", 4, "Did you mean open_door?");
        expectError("def greet():\n    accept()\nwhen every(5):\n    greet()", 4,
                "greet() uses accept(), which only works inside when keycard(...) or when codepanel(...)");
        expectError("def check():\n    if card.valid:\n        pass\nwhen codepanel(\"P\"):\n    check()", 5,
                "check() uses card.valid, which only works inside when keycard(...)");
        expectError("def check():\n    if card.in_group(\"lab\"):\n        pass\nwhen codepanel(\"P\"):\n    check()", 5,
                "check() uses card.in_group(...), which only works inside when keycard(...)");
        expectError("def outer():\n    inner()\ndef inner():\n    if code == \"1\":\n        pass\nwhen keycard(\"R\"):\n    outer()",
                7, "outer() uses code");
        expectError("def a():\n    pass\ndef a():\n    pass\nwhen every(5):\n    a()", 3, "There already is a def a");
        expectError("def on():\n    pass", 1, "already a word of the language");
        // Sinds hoofdstuk 43 heeft een def parameters: def open(door): is goed (zonder soort-woord past elke waarde).
        Script withParam = ScriptParser.parse("def open(door):\n    pass");
        if (withParam.functions().size() != 1 || withParam.functions().getFirst().params().size() != 1) {
            throw new GameTestAssertException("def open(door): zou één parameter moeten hebben: " + withParam);
        }
        expectError("when every(5):\n    def x():\n        pass", 2, "A def must not be indented");
        expectError("def a():\n    pass\nwhen every(5):\n    a(\"x\")", 4, "takes nothing between the brackets");
        expectError("def a():\n    pass\nwhen a():\n    pass", 3, "is your own block, not a trigger");
        expectError("def a():\n    pass\nwhen every(5):\n    a", 4, "a needs brackets");
        expectError("def 1abc():\n    pass", 1, "a name cannot start with a number");
        helper.succeed();
    }

    /**
     * Een def die twee triggers delen: een geldige kaart en de goede code doen allebei open_lab()
     * (groen, de deur gaat open), en daarna gaat het diagram verder met wat na de if of na de
     * aanroep komt.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "logic_runner_functions")
    public static void logic_functions_run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        BlockPos door = relay(helper, setup, new BlockPos(9, 3, 4), "Lab door lock");
        BlockPos reader = new BlockPos(8, 3, 8);
        BlockPos panel = new BlockPos(4, 3, 8);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        helper.setBlock(panel, ModBlocks.CODE_PANEL.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(reader)).setName("Lab door");
        ((InputDeviceBlockEntity) helper.getBlockEntity(panel)).setName("Lab panel");
        BlockPos readerAbs = helper.absolutePos(reader);
        BlockPos panelAbs = helper.absolutePos(panel);
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
            SecurityOs.open(setup.owner(), setup.coreAbs());
            save(setup, "Lab", """
                    def open_lab():
                        accept()
                        pulse("Lab door lock", 2)

                    when keycard("Lab door"):
                        if card.valid:
                            open_lab()
                        else:
                            deny()
                        log("keycard used")

                    when codepanel("Lab panel"):
                        if code == "4321":
                            open_lab()
                            log("code used")
                    """);
            if (setup.core().logic().program("Lab") == null) {
                throw new GameTestAssertException("Het diagram met een def werd niet bewaard: " + lastLog(setup.core()));
            }
            var card = setup.core().logic().issue("Lab access", 1, "test", "");
            LogicEvents.clear();
            LogicEvents.keycard(level, readerAbs, setup.owner(),
                    new KeycardData(setup.core().ip(), setup.core().hostname(), card.number(), 1, "Lab access"));
        });
        helper.runAfterDelay(20, () -> {
            helper.assertBlockProperty(reader, InputDeviceBlock.LIGHT, InputLight.OK);
            if (!level.getBlockState(door).getValue(RelayBlock.POWERED) || !logged(setup.core(), "keycard used")) {
                throw new GameTestAssertException("De kaart deed open_lab() niet (of ging daarna niet verder). Logboek: "
                        + lastLog(setup.core()));
            }
        });
        helper.runAfterDelay(70, () -> {
            if (level.getBlockState(door).getValue(RelayBlock.POWERED)) {
                throw new GameTestAssertException("De deur ging na de puls niet meer dicht");
            }
            LogicEvents.clear();
            LogicEvents.codePanel(level, panelAbs, setup.owner(), "4321");
        });
        helper.runAfterDelay(85, () -> {
            helper.assertBlockProperty(panel, InputDeviceBlock.LIGHT, InputLight.OK);
            if (!level.getBlockState(door).getValue(RelayBlock.POWERED) || !logged(setup.core(), "code used")) {
                throw new GameTestAssertException("De code deed open_lab() niet (of ging daarna niet verder). Logboek: "
                        + lastLog(setup.core()));
            }
            helper.succeed();
        });
    }

    /** Een opdracht van de app Keycards, met eventueel een kaartnummer. */
    private static void cardAction(Setup setup, String op, int number, boolean on) {
        CompoundTag args = new CompoundTag();
        args.putInt("n", number);
        args.putBoolean("on", on);
        SecurityOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), op, args));
    }

    /**
     * Keycards opruimen: een actieve kaart kun je niet weggooien (eerst blokkeren), een geblokkeerde
     * wel, "Delete blocked" gooit ze allemaal tegelijk weg, en het nummer van een weggegooide kaart
     * komt nooit meer terug (een oude kaart wordt dus nooit meer geldig).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "keycards_delete")
    public static void keycards_delete_blocked(GameTestHelper helper) {
        Setup setup = setup(helper);
        helper.runAfterDelay(2, () -> {
            SecurityOs.open(setup.owner(), setup.coreAbs());
            var store = setup.core().logic();
            var lab = store.issue("Lab access", 3, "test", "");
            var guest = store.issue("Guest", 1, "test", "");
            var lost = store.issue("Lost card", 2, "test", "");
            KeycardData labData = new KeycardData(setup.core().ip(), "x", lab.number(), 3, "Lab access");
            // Een actieve kaart: eerst blokkeren.
            cardAction(setup, "card_delete", lab.number(), false);
            if (store.card(lab.number()) == null) {
                throw new GameTestAssertException("Een actieve kaart werd zomaar weggegooid");
            }
            // Blokkeren en weggooien: weg uit de lijst, en niet meer geldig.
            cardAction(setup, "card_block", lab.number(), true);
            cardAction(setup, "card_delete", lab.number(), false);
            if (store.card(lab.number()) != null || store.isValid(setup.core().ip(), labData)) {
                throw new GameTestAssertException("Een geblokkeerde kaart werd niet weggegooid (of is nog geldig)");
            }
            // De andere twee blokkeren en allemaal tegelijk weggooien.
            cardAction(setup, "card_block", guest.number(), true);
            cardAction(setup, "card_block", lost.number(), true);
            cardAction(setup, "card_delete_blocked", 0, false);
            if (!store.cards().isEmpty()) {
                throw new GameTestAssertException("Delete blocked liet kaarten staan: " + store.cards());
            }
            // Een nieuwe kaart krijgt een nieuw nummer, niet een van de weggegooide.
            var fresh = store.issue("New", 1, "test", "");
            if (fresh.number() <= lost.number()) {
                throw new GameTestAssertException("Een weggegooid nummer kwam terug: #" + fresh.number());
            }
            // Ook na opslaan en weer laden.
            var loaded = com.senne.imttech.logic.LogicStore.load(store.save());
            var after = loaded.issue("After load", 1, "test", "");
            if (after.number() <= fresh.number()) {
                throw new GameTestAssertException("Na laden kwam een oud nummer terug: #" + after.number());
            }
            boolean logged = setup.core().log().entries().stream().anyMatch(entry -> entry.text().contains("deleted 2 blocked keycards"));
            if (!logged) {
                throw new GameTestAssertException("Het logboek zegt niet dat er kaarten weggegooid zijn: " + lastLog(setup.core()));
            }
            helper.succeed();
        });
    }

    /**
     * De app Devices toont de keycardlezers en codepanelen (met in hoeveel diagrammen ze staan), en
     * met Rename daar krijgen ze een andere naam. Een lege naam mag niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "devices_rename")
    public static void devices_rename_inputs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        BlockPos reader = new BlockPos(8, 3, 8);
        BlockPos panel = new BlockPos(4, 3, 8);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        helper.setBlock(panel, ModBlocks.CODE_PANEL.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(reader)).setName("Reader 1");
        ((InputDeviceBlockEntity) helper.getBlockEntity(panel)).setName("Panel 1");
        BlockPos readerAbs = helper.absolutePos(reader);
        BlockPos panelAbs = helper.absolutePos(panel);
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
            SecurityOs.open(setup.owner(), setup.coreAbs());
            save(setup, "Lab", """
                    when keycard("Lab door"):
                        accept()
                    """);
            com.senne.imttech.security.SecurityDevices.forget(level, setup.core());
            // Allebei in de lijst, met de goede soort; nog geen diagram gebruikt "Reader 1".
            CompoundTag before = device(level, setup, readerAbs);
            CompoundTag panelRow = device(level, setup, panelAbs);
            if (before == null || !before.getString("k").equals("reader") || !before.getString("name").equals("Reader 1")
                    || before.getInt("uses") != 0) {
                throw new GameTestAssertException("De lezer staat niet (goed) in Devices: " + before);
            }
            if (panelRow == null || !panelRow.getString("k").equals("panel") || !panelRow.getString("name").equals("Panel 1")) {
                throw new GameTestAssertException("Het codepaneel staat niet (goed) in Devices: " + panelRow);
            }
            // Rename: nu heet hij zoals in het diagram, dus dat diagram gebruikt hem.
            rename(setup, readerAbs, "Lab door");
            CompoundTag after = device(level, setup, readerAbs);
            if (after == null || !after.getString("name").equals("Lab door") || after.getInt("uses") != 1) {
                throw new GameTestAssertException("Rename in Devices werkte niet: " + after);
            }
            // Een lege naam mag niet: hij blijft "Lab door".
            rename(setup, readerAbs, "   ");
            if (!((InputDeviceBlockEntity) helper.getBlockEntity(reader)).name().equals("Lab door")) {
                throw new GameTestAssertException("Een lege naam werd toch bewaard");
            }
            helper.succeed();
        });
    }

    /** De regel van Devices voor het apparaat op deze plek, of null. */
    private static @org.jetbrains.annotations.Nullable CompoundTag device(ServerLevel level, Setup setup, BlockPos pos) {
        for (var element : com.senne.imttech.security.SecurityDevices.snapshot(level, setup.core())) {
            CompoundTag device = (CompoundTag) element;
            if (device.getInt("x") == pos.getX() && device.getInt("y") == pos.getY() && device.getInt("z") == pos.getZ()) {
                return device;
            }
        }
        return null;
    }

    /** De knop Rename (Save) van Devices, zoals de desktop hem stuurt. */
    private static void rename(Setup setup, BlockPos pos, String name) {
        CompoundTag args = new CompoundTag();
        args.putInt("x", pos.getX());
        args.putInt("y", pos.getY());
        args.putInt("z", pos.getZ());
        args.putString("name", name);
        SecurityOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), "input_rename", args));
    }

    // ================================================================ Een lezer hernoemen met sneak + rechtsklik

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    // ================================================================ Hoofdstuk 23: soorten programma's

    /**
     * Soorten programma's (hoofdstuk 23): een robot kent geen keycard, accept of card.valid (die horen bij de
     * server), de server wel. De soort blijft bij bewaren en laden; een oud diagram (zonder soort) is een
     * diagram op de server. De blokjes geven dezelfde fout als de code.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_program_kinds(GameTestHelper helper) {
        String reader = "when keycard(\"Lab\"):\n    accept()\n";
        ScriptParser.parse(reader, ProgramKind.SERVER);
        expectKindError(reader, ProgramKind.ROBOT, 1, "keycard(...) only works in a server diagram, not in a robot program");
        expectKindError("when every(5):\n    accept()\n", ProgramKind.ROBOT, 2, "accept() only works in a server diagram");
        expectKindError("when every(5):\n    if card.number > 1:\n        log(\"x\")\n", ProgramKind.ROBOT, 2,
                "card.number only works in a server diagram");
        expectKindError("when every(5):\n    if card.in_group(\"lab\"):\n        log(\"x\")\n", ProgramKind.ROBOT, 2,
                "card.in_group(...) only works in a server diagram");
        // is_night en je eigen namen werken overal.
        ScriptParser.parse("count = 0\n\nwhen every(5):\n    if is_night:\n        count = count + 1\n", ProgramKind.ROBOT);

        String code = "when every(5):\n    log(\"tick\")\n";
        LogicGraph graph = GraphConverter.toGraph(ScriptParser.parse(code, ProgramKind.ROBOT), null);
        LogicProgram robot = new LogicProgram("Patrol", true, graph, code, ProgramKind.ROBOT);
        LogicProgram loaded = LogicProgram.load(robot.save());
        check(loaded.kind() == ProgramKind.ROBOT, "Na bewaren en laden is de soort " + loaded.kind());
        CompoundTag old = new LogicProgram("Old", true, graph, code).save();
        old.remove("Kind");
        check(LogicProgram.load(old).kind() == ProgramKind.SERVER, "Een oud diagram is geen diagram op de server");

        LogicGraph keycard = GraphConverter.toGraph(ScriptParser.parse(reader), null);
        check(keycard.problems(ProgramKind.SERVER).isEmpty(), "Op de server zou een keycard mogen");
        check(keycard.problems(ProgramKind.ROBOT).stream().anyMatch(problem -> problem.message().contains("only works in a server diagram")),
                "De blokjes van een robotprogramma lieten een keycard toe: " + keycard.problems(ProgramKind.ROBOT));
        helper.succeed();
    }

    /** Een robotprogramma staat op de server (het telt mee voor de SSD's), maar de server voert het niet uit. */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "logic_runner_kinds")
    public static void logic_robot_programs_do_not_run_on_the_server(GameTestHelper helper) {
        Setup setup = setup(helper);
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
            SecurityOs.open(setup.owner(), setup.coreAbs());
            save(setup, "Server", "when every(1):\n    log(\"server tick\")\n");
            CompoundTag args = new CompoundTag();
            args.putString("old", "");
            args.putString("name", "Robot");
            String code = "when every(1):\n    log(\"robot tick\")\n";
            args.put("graph", GraphConverter.toGraph(ScriptParser.parse(code, ProgramKind.ROBOT), null).save());
            args.putString("source", code);
            args.putString("kind", "ROBOT");
            SecurityOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), "logic_save", args));
            LogicProgram robot = setup.core().logic().program("Robot");
            check(robot != null && robot.kind() == ProgramKind.ROBOT, "Het robotprogramma werd niet als robotprogramma bewaard: "
                    + lastLog(setup.core()));
            check(setup.core().logic().blocks() == robot.graph().size() + setup.core().logic().program("Server").graph().size(),
                    "Het robotprogramma telt niet mee voor de SSD's");
        });
        helper.runAfterDelay(100, () -> {
            check(logged(setup.core(), "server tick"), "Het diagram op de server draaide niet: " + lastLog(setup.core()));
            check(!logged(setup.core(), "robot tick"), "De server voerde het robotprogramma uit");
            helper.succeed();
        });
    }

    /** Het voorbeeld uit het plan (hoofdstuk 23): een robot die patrouilleert, laadt, scant en schiet. */
    private static final String PATROL = """
            scanPin = pin(1)
            facePin = pin(2)
            laserPin = pin(3)

            when started:
                write(facePin, "strangers")
                while true:
                    if robot.battery < 10:
                        charge()
                    forward(5)
                    turn_right()
                    if read(scanPin):
                        alarm("Intruder near " + robot.name)
                        write(laserPin, true)

            when ordered("attack"):
                goto(order.x, order.y, order.z)
            """;

    /**
     * De woorden van een robot (hoofdstuk 23, stap 2): {@code when started:} zonder haakjes, lopen, laden,
     * pinnen, {@code goto(x, y, z)} met drie waarden, en de woorden van de server om robots aan te sturen.
     * Code naar blokjes naar code blijft hetzelfde, en elk woord werkt alleen in zijn eigen soort programma.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_robot_words(GameTestHelper helper) {
        Script script = ScriptParser.parse(PATROL, ProgramKind.ROBOT);
        String printed = ScriptPrinter.print(script);
        check(printed.contains("when started:\n") && printed.contains("    write(facePin, \"strangers\")")
                && printed.contains("charge()") && printed.contains("goto(order.x, order.y, order.z)"),
                "Terugschrijven klopt niet:\n" + printed);
        LogicGraph graph = GraphConverter.toGraph(script, null);
        String again = ScriptPrinter.print(GraphConverter.toScript(LogicGraph.load(graph.save())));
        check(again.equals(printed), "Code > blokjes > code veranderde:\n" + printed + "\nwerd\n" + again);
        check(graph.problems(ProgramKind.ROBOT).isEmpty(), "De blokjes van een goed robotprogramma geven fouten: "
                + graph.problems(ProgramKind.ROBOT));
        // De server stuurt robots aan en hoort hun berichten.
        ScriptParser.parse("""
                when message("intruder"):
                    order("Guard 1", "attack", sender.x, sender.y, sender.z)
                    activate("Guard 2")
                    deactivate("Guard 3")
                """);

        // aim(x, y, z) en aim() (19 oktober): drie getallen of niets, en van code naar blokjes en terug blijft het zo.
        String aiming = "when ordered(\"attack\"):\n    aim(order.x, order.y + 1, order.z)\n    write(pin(3), true)\n    aim()\n";
        String printedAim = ScriptPrinter.print(ScriptParser.parse(aiming, ProgramKind.ROBOT));
        check(printedAim.contains("aim(order.x, order.y + 1, order.z)") && printedAim.contains("    aim()\n"),
                "aim schrijft zichzelf niet terug:\n" + printedAim);
        LogicGraph aimGraph = GraphConverter.toGraph(ScriptParser.parse(aiming, ProgramKind.ROBOT), null);
        check(ScriptPrinter.print(GraphConverter.toScript(LogicGraph.load(aimGraph.save()))).equals(printedAim),
                "Code > blokjes > code veranderde aim");
        check(aimGraph.problems(ProgramKind.ROBOT).isEmpty(), "De blokjes van aim geven fouten: " + aimGraph.problems(ProgramKind.ROBOT));
        expectKindError("when started:\n    aim(1, 2)", ProgramKind.ROBOT, 2,
                "aim needs 0 or 3 values (x, y, z) between the brackets, like aim(10, 64, -20) or aim()");
        expectKindError("when started:\n    aim(\"here\", 2, 3)", ProgramKind.ROBOT, 2,
                "aim: the x must be a number, like aim(10, 64, -20) or aim()");
        expectError("when every(5):\n    aim(1, 2, 3)", 2, "aim() only works in a robot program");

        // Elk woord alleen in zijn eigen soort programma.
        expectError("when started:\n    pass", 1, "when started: only works in a robot program, a drone program or an RC program, not in a server diagram");
        expectError("when every(5):\n    forward(3)", 2, "forward() only works in a robot program");
        expectError("when every(5):\n    if read(1):\n        pass", 2, "read(...) only works in a robot program");
        expectError("when every(5):\n    if robot.battery < 10:\n        pass", 2, "robot.battery only works in a robot program");
        expectKindError("when started:\n    order(\"Guard 1\", \"attack\")", ProgramKind.ROBOT, 2,
                "order() only works in a server diagram");
        expectKindError("when message(\"x\"):\n    pass", ProgramKind.ROBOT, 1, "message(...) only works in a server diagram");
        // Waarden en getallen.
        expectKindError("when started:\n    goto(1, 2)", ProgramKind.ROBOT, 2, "goto needs 3 values (x, y, z)");
        expectKindError("when started:\n    goto(\"a\", 2, 3)", ProgramKind.ROBOT, 2, "goto: the x must be a number");
        expectError("when every(5):\n    order(\"Guard 1\", \"attack\", 1)", 2, "order needs 2 or 5 values");
        expectKindError("when started:\n    forward()", ProgramKind.ROBOT, 2, "forward needs a number of blocks, like forward(3)");
        expectKindError("when started:\n    forward(100)", ProgramKind.ROBOT, 2, "forward takes 1 to 64 blocks, not 100");
        expectKindError("when started:\n    charge(150)", ProgramKind.ROBOT, 2, "charge takes 1 to 100 percent, not 150");
        expectKindError("when started:\n    x = pin(9)", ProgramKind.ROBOT, 2, "A robot has 8 pins: pin(1) to pin(8)");
        expectKindError("when started:\n    read(1)", ProgramKind.ROBOT, 2, "read(...) gives a value");
        expectKindError("when started:\n    goto(order.x, 1, 2)", ProgramKind.ROBOT, 2, "order.x only works inside when ordered(...)");
        expectError("when every(5):\n    say(sender.name)", 2, "sender.name only works inside when message(...)");
        expectKindError("when started:\n    if robot.batery < 10:\n        pass", ProgramKind.ROBOT, 2, "Did you mean robot.battery?");
        expectKindError("when started:\n    forward = 3", ProgramKind.ROBOT, 2, "is already a word of the language");
        expectKindError("when started:\n    activate(\"Guard 1\")", ProgramKind.ROBOT, 2, "activate() only works in a server diagram");
        expectError("when every(5):\n    activate(Guard)", 2, "If you meant a robot, put it between quotes");
        helper.succeed();
    }

    /**
     * Rekenen met pinnen (hoofdstuk 23): {@code pin(2)} is het getal 2, {@code read(p)} vraagt de robot wat er
     * op pin p binnenkomt. In de blokjes geeft een goto met twee waarden een fout, en een oude def die nu een
     * woord van een robot is ({@code forward}), krijgt bij het laden een andere naam.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_robot_values(GameTestHelper helper) {
        Expr.Facts facts = new Expr.Facts() {
            @Override
            public Object value(String variable) {
                return variable.equals("robot.battery") ? (Object) 42.0 : (Object) false;
            }

            @Override
            public boolean isOn(String device) {
                return false;
            }

            @Override
            public Object read(int pin) {
                return pin == 2 ? (Object) true : (Object) 7.0;
            }
        };
        check(Double.valueOf(3.0).equals(ExprCache.eval(ExprCache.value("pin(2) + 1"), facts)), "pin(2) + 1 is geen 3");
        check(Boolean.TRUE.equals(ExprCache.eval(ExprCache.value("read(pin(1) + 1)"), facts)), "read(2) gaf niet wat de robot zei");
        check(Double.valueOf(7.0).equals(ExprCache.eval(ExprCache.value("read(3)"), facts)), "read(3) gaf niet 7");
        check(ExprCache.test(ExprCache.get("robot.battery > 40 and read(2)", null), facts), "robot.battery > 40 and read(2)");

        // Blokjes: een goto met twee waarden, en een write in een serverdiagram.
        LogicGraph graph = new LogicGraph();
        LogicGraph.Node start = graph.add(Op.STARTED, 0, 0);
        LogicGraph.Node go = graph.add(Op.GOTO, 100, 0);
        go.expr = "1, 2";
        start.next = go.id;
        check(graph.problems(ProgramKind.ROBOT).stream().anyMatch(problem -> problem.message().contains("goto needs 3 values")),
                "Een goto met twee waarden gaf geen fout: " + graph.problems(ProgramKind.ROBOT));
        go.expr = "robot.x+5,robot.y ,robot.z";
        check(graph.problems(ProgramKind.ROBOT).isEmpty(), "Een goede goto gaf een fout: " + graph.problems(ProgramKind.ROBOT));
        check(ScriptPrinter.print(GraphConverter.toScript(graph)).contains("goto(robot.x + 5, robot.y, robot.z)"),
                "De goto werd niet netjes geschreven: " + ScriptPrinter.print(GraphConverter.toScript(graph)));
        check(graph.problems(ProgramKind.SERVER).stream().anyMatch(problem -> problem.message().contains("only works in a robot program")),
                "Een robotprogramma als serverdiagram gaf geen fout");

        // Een oude def die forward heette.
        LogicGraph old = new LogicGraph();
        LogicGraph.Node def = old.add(Op.DEF, 0, 0);
        def.text = "forward";
        LogicGraph.Node every = old.add(Op.EVERY, 0, 40);
        LogicGraph.Node call = old.add(Op.CALL, 100, 40);
        call.text = "forward";
        every.next = call.id;
        check(old.renameReservedDefs() && def.text.equals("forward_1") && call.text.equals("forward_1"),
                "De def forward kreeg geen andere naam: " + def.text + " / " + call.text);
        helper.succeed();
    }

    /**
     * Ctrl+Spatie in een robotprogramma (hoofdstuk 23): de woorden van een robot (forward, goto...) en zijn gegevens
     * (robot.battery, read, pin), geen keycard of card.valid. In een serverdiagram staan de namen van je robots tussen
     * de aanhalingstekens van activate, deactivate en order.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_robot_completion(GameTestHelper helper) {
        List<String> statements = complete("when started:\n    |", ProgramKind.ROBOT);
        check(statements.contains("forward(3)") && statements.contains("goto(...)") && statements.contains("charge(80)")
                && statements.contains("write(...)"), "Een robot mist woorden: " + statements);
        check(!statements.contains("accept()") && !statements.contains("order(\"...\", \"attack\")"), "Een robot krijgt woorden van de server: " + statements);
        List<String> checks = complete("when started:\n    if |", ProgramKind.ROBOT);
        check(checks.contains("robot.battery") && checks.contains("read(...)") && checks.contains("pin(...)") && !checks.contains("card.valid") && !checks.contains("card.in_group(\"...\")"),
                "In een if van een robot klopt het niet: " + checks);
        List<String> top = complete("|", ProgramKind.ROBOT);
        check(top.contains("when started:") && top.contains("when ordered(...):") && !top.contains("when keycard(...):"),
                "Bovenaan een robotprogramma klopt het niet: " + top);
        List<String> server = complete("when every(5):\n    |", ProgramKind.SERVER);
        check(server.contains("order(\"...\", \"attack\")") && server.contains("activate(\"...\")") && !server.contains("forward(3)"),
                "Een serverdiagram mist de robotwoorden: " + server);
        List<String> names = complete("when every(5):\n    activate(\"|\")", ProgramKind.SERVER);
        check(names.contains("Guard 1") && names.contains("Door guard"), "De namen van je robots ontbreken: " + names);
        helper.succeed();
    }

    /**
     * 16 november: "kun je zorgen dat bij het gebruiken van tab bij aanvullen van code ... overal waar een voorbeeldgetal
     * staat zoals wait(2) je niet na het ) komt te staan maar tussen de haakjes zonder getal". Het lijstje toont nog
     * wait(2), maar Tab vult wait() in met de cursor tussen de haakjes; zo ook every, forward, charge en range. Bij pulse
     * blijft de cursor tussen de aanhalingstekens (daar komt de naam) en is het vakje voor het getal leeg. Een blokje
     * zonder getal (accept()) zet de cursor nog altijd erachter.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_tab_leaves_out_example_numbers(GameTestHelper helper) {
        String server = "when every(5):\n    |";
        expectTyped(server, ProgramKind.SERVER, "wait(2)", "wait()", 5);
        expectTyped(server, ProgramKind.SERVER, "pulse(\"...\", 3)", "pulse(\"\", )", 7);
        expectTyped(server, ProgramKind.SERVER, "for i in range(3):", "for i in range():", 15);
        expectTyped(server, ProgramKind.SERVER, "accept()", "accept()", -1);
        expectTyped("when started:\n    |", ProgramKind.ROBOT, "forward(3)", "forward()", 8);
        expectTyped("when started:\n    |", ProgramKind.ROBOT, "charge(80)", "charge()", 7);
        expectTyped("|", ProgramKind.SERVER, "when every(10):", "when every():\n    ", 11);
        expectTyped("when |", ProgramKind.SERVER, "every(10):", "every():", 6);
        // Na het kiezen van een naam springt de cursor naar het lege vakje voor het getal, en alleen daar.
        check(com.senne.imttech.client.os.CodeCompletion.skipEmptySlot("    pulse(\"Lamp\", )", 16) == 18
                        && com.senne.imttech.client.os.CodeCompletion.skipEmptySlot("    pulse(\"Lamp\", 3)", 16) == 16
                        && com.senne.imttech.client.os.CodeCompletion.skipEmptySlot("    on(\"Lamp\")", 13) == 13,
                "De cursor springt na een naam niet (of verkeerd) naar het lege vakje voor het getal");
        // De foutmeldingen houden hun voorbeeldgetal: "like wait(2)".
        check(Op.WAIT.example().equals("wait(2)") && Op.PULSE.example().equals("pulse(\"|\", 3)"),
                "Het voorbeeld met getal is veranderd: " + Op.WAIT.example() + " / " + Op.PULSE.example());
        expectKindError("when every(5):\n    wait(1, 2)", ProgramKind.SERVER, 2, "like wait(2)");
        // Laat je het vakje voor het getal leeg, dan zegt Check wat er ontbreekt (en niet "I did not expect )"), ook in
        // range en in een functie van een check.
        expectKindError("when every(5):\n    pulse(\"Lamp\", )", ProgramKind.SERVER, 2, "Something is missing after the comma in pulse");
        expectKindError("when every(5):\n    for i in range(1, ):\n        pass", ProgramKind.SERVER, 2,
                "Something is missing after the comma in range");
        expectKindError("when started:\n    if read(1, ):\n        stop()", ProgramKind.ROBOT, 2,
                "Something is missing after the comma in read");
        helper.succeed();
    }

    /** Tab op de suggestie met dit label vult {@code insert} in, en de cursor staat dan {@code caret} tekens verder. */
    private static void expectTyped(String code, ProgramKind kind, String label, String insert, int caret) {
        var item = completeItems(code, kind).stream().filter(i -> i.label().equals(label)).findFirst()
                .orElseThrow(() -> new GameTestAssertException("Geen " + label + " in het lijstje voor: " + code));
        check(item.insert().equals(insert) && item.caret() == caret, "Tab op " + label + " vult \"" + item.insert()
                + "\" in met de cursor op " + item.caret() + ", niet \"" + insert + "\" op " + caret);
    }

    /** Ctrl+Spatie op de | in deze code, voor deze soort programma, met twee robots in het netwerk. */
    private static List<String> complete(String code, ProgramKind kind) {
        return completeItems(code, kind).stream().map(com.senne.imttech.client.os.CodeCompletion.Item::label).toList();
    }

    /** De suggesties zelf, met wat Tab invult en waar de cursor dan staat. */
    private static List<com.senne.imttech.client.os.CodeCompletion.Item> completeItems(String code, ProgramKind kind) {
        String[] lines = code.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int at = lines[i].indexOf('|');
            if (at >= 0) {
                List<String> clean = new java.util.ArrayList<>(List.of(code.replace("|", "").split("\n", -1)));
                var data = new com.senne.imttech.client.os.CodeCompletion.Data(List.of("Vault door"), List.of("Lab door"),
                        List.of("Front door"), kind, List.of("Guard 1", "Door guard"));
                return com.senne.imttech.client.os.CodeCompletion.complete(clean, i, at, data).items();
            }
        }
        throw new GameTestAssertException("Geen | in de test-code");
    }

    /** De code moet een fout geven die bij deze soort programma hoort, op deze regel. */
    private static void expectKindError(String code, ProgramKind kind, int line, String part) {
        try {
            ScriptParser.parse(code, kind);
        } catch (LogicException e) {
            if (e.line() != line || !e.getMessage().contains(part)) {
                throw new GameTestAssertException("Voor:\n" + code + "\nverwachtte ik regel " + line + " met \"" + part
                        + "\", maar kreeg: " + e.describe());
            }
            return;
        }
        throw new GameTestAssertException("Voor:\n" + code + "\nverwachtte ik een fout (" + part + "), maar het klopte");
    }

    /** Een klik op de voorkant van de lezer op {@code pos}. */
    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos) {
        BlockPos abs = helper.absolutePos(pos);
        return new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
    }

    /** Sneak + rechtsklik met een lege hand op de lezer (of gewoon klikken, zonder sneak). */
    private static void click(GameTestHelper helper, ServerPlayer player, BlockPos pos, boolean sneak) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(sneak);
        helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit(helper, pos));
        player.setShiftKeyDown(false);
    }

    private static InputDeviceBlockEntity input(GameTestHelper helper, BlockPos pos) {
        return (InputDeviceBlockEntity) helper.getBlockEntity(pos);
    }

    /** Save in het naamscherm van de lezer, zoals jouw spel het stuurt. */
    private static void renameInWorld(GameTestHelper helper, ServerPlayer player, BlockPos pos, String name) {
        InputDevices.handleRename(player, new InputRenamePayload(helper.absolutePos(pos), name));
    }

    /**
     * 13 oktober: "rename keycard reader door sneak rechts te klikken zonder item in hand, dit kan enkel de owner
     * doen of trusted players in zone". Wie de lezer neerzet, is de eigenaar en hernoemt hem (de naam wordt
     * netjes; leeg mag niet, van ver weg ook niet); een vreemde niet. Een lezer zonder eigenaar (van vóór vandaag)
     * wordt van wie er als eerste op sneak-klikt; gewoon klikken doet dat niet. (In zijn eigen batch, met een
     * kleiner bereik van de kernen, zodat de kernen van andere tests hier niet meetellen.)
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "reader_rename")
    public static void reader_rename_by_sneak_click(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 2, 1), "reader-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 2, 1), "reader-stranger");
        BlockPos wall = new BlockPos(3, 3, 4);
        helper.setBlock(wall, Blocks.STONE);
        ImtTechGameTests.withReach(4, () -> {
            // Neergezet met de muis, tegen de noordkant van een steen: dan is de eigenaar wie hem neerzette.
            check(ZoneGameTests.place(helper, owner, ModItems.KEYCARD_READER.get(), wall, Direction.NORTH), "Neerzetten lukte niet");
            BlockPos reader = wall.north();
            check(helper.getBlockState(reader).is(ModBlocks.KEYCARD_READER.get()), "Er hangt geen lezer");
            check(owner.getUUID().equals(input(helper, reader).owner()), "Wie hem neerzette, is niet de eigenaar");
            String first = input(helper, reader).name();
            check(!first.isEmpty(), "De lezer kreeg bij het neerzetten geen naam");

            // Een vreemde: sneak + klik opent niets, en Save doet niets.
            click(helper, stranger, reader, true);
            check(!InputDevices.mayRename(stranger, input(helper, reader)), "Een vreemde mag de lezer hernoemen");
            renameInWorld(helper, stranger, reader, "Hacked");
            check(input(helper, reader).name().equals(first), "Een vreemde kon de lezer hernoemen");

            // De eigenaar: de naam wordt netjes (spaties eromheen weg, aanhalingstekens eruit).
            renameInWorld(helper, owner, reader, "  Lab \"door\"  ");
            check(input(helper, reader).name().equals("Lab door"), "Hernoemen werkte niet: " + input(helper, reader).name());
            renameInWorld(helper, owner, reader, "   ");
            check(input(helper, reader).name().equals("Lab door"), "Een lege naam werd toch bewaard");
            // Van ver weg (meer dan 6 blokken) niet.
            Vec3 near = owner.position();
            Vec3 far = helper.absoluteVec(new Vec3(3.5, 2, -8.5));
            owner.moveTo(far.x, far.y, far.z);
            renameInWorld(helper, owner, reader, "Far away");
            owner.moveTo(near.x, near.y, near.z);
            check(input(helper, reader).name().equals("Lab door"), "Van ver weg kon je de lezer hernoemen");

            // Een lezer zonder eigenaar: gewoon klikken neemt hem niet over, sneak + klik wel.
            BlockPos old = new BlockPos(1, 3, 3);
            helper.setBlock(old, ModBlocks.KEYCARD_READER.get());
            check(input(helper, old).owner() == null, "Een lezer zonder neerzetter heeft toch een eigenaar");
            click(helper, stranger, old, false);
            check(input(helper, old).owner() == null, "Gewoon klikken maakte de klikker eigenaar");
            click(helper, stranger, old, true);
            check(stranger.getUUID().equals(input(helper, old).owner()), "Wie als eerste sneak-klikte, werd niet de eigenaar");
            check(!InputDevices.mayRename(owner, input(helper, old)), "Daarna mocht een ander hem nog hernoemen");
            return null;
        });
        helper.succeed();
    }

    /**
     * 13 oktober: "zorg ook dat dit bij code panels werkt". Hetzelfde als bij de lezer: wie het paneel neerzette,
     * opent met sneak + klik het naamscherm en hernoemt het; een vreemde niet (die krijgt dan gewoon de toetsen,
     * dus zijn sneak + klik neemt niets over). Een paneel zonder eigenaar wordt van wie er als eerste op
     * sneak-klikt, gewoon klikken (de toetsen) doet dat niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "reader_rename")
    public static void panel_rename_by_sneak_click(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 2, 1), "panel-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 2, 1), "panel-stranger");
        BlockPos wall = new BlockPos(3, 3, 4);
        helper.setBlock(wall, Blocks.STONE);
        ImtTechGameTests.withReach(4, () -> {
            check(ZoneGameTests.place(helper, owner, ModItems.CODE_PANEL.get(), wall, Direction.NORTH), "Neerzetten lukte niet");
            BlockPos panel = wall.north();
            BlockPos abs = helper.absolutePos(panel);
            check(helper.getBlockState(panel).is(ModBlocks.CODE_PANEL.get()), "Er hangt geen codepaneel");
            check(owner.getUUID().equals(input(helper, panel).owner()), "Wie het neerzette, is niet de eigenaar");
            String first = input(helper, panel).name();

            // De eigenaar krijgt het naamscherm, een vreemde niet (en die kan het ook niet hernoemen).
            check(InputDevices.tryOpen(owner, abs), "De eigenaar krijgt het naamscherm van zijn paneel niet");
            check(!InputDevices.tryOpen(stranger, abs), "Een vreemde krijgt het naamscherm van het paneel");
            click(helper, stranger, panel, true);
            renameInWorld(helper, stranger, panel, "Hacked");
            check(input(helper, panel).name().equals(first), "Een vreemde kon het paneel hernoemen");
            check(owner.getUUID().equals(input(helper, panel).owner()), "Een vreemde nam het paneel over");
            renameInWorld(helper, owner, panel, " Front door ");
            check(input(helper, panel).name().equals("Front door"), "Hernoemen werkte niet: " + input(helper, panel).name());

            // Een paneel zonder eigenaar: gewoon klikken neemt het niet over, sneak + klik wel.
            BlockPos old = new BlockPos(1, 3, 3);
            helper.setBlock(old, ModBlocks.CODE_PANEL.get());
            click(helper, stranger, old, false);
            check(input(helper, old).owner() == null, "Gewoon klikken (de toetsen) maakte de klikker eigenaar");
            click(helper, stranger, old, true);
            check(stranger.getUUID().equals(input(helper, old).owner()), "Wie als eerste sneak-klikte, werd niet de eigenaar");
            check(!InputDevices.tryOpen(owner, helper.absolutePos(old)), "Daarna kreeg een ander nog het naamscherm");
            return null;
        });
        helper.succeed();
    }

    /**
     * In een zone: de vertrouwde spelers van de kern hernoemen een lezer (ook een die een ander neerzette), een
     * vreemde niet, en het logboek van de kern zegt wie het deed. Een lezer zonder eigenaar in iemands basis kan
     * een vreemde niet overnemen.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void reader_rename_trusted_in_zone(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 3, 6), "zone-owner");
        ServerPlayer friend = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(9, 3, 6), "zone-friend");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(10, 3, 6), "zone-outsider");
        SecurityCoreBlockEntity core = ZoneGameTests.zoneCore(helper, owner);
        core.addTrusted(friend.getUUID(), friend.getGameProfile().getName());

        BlockPos placed = new BlockPos(8, 3, 8);
        BlockPos old = new BlockPos(10, 3, 10);
        helper.setBlock(placed, ModBlocks.KEYCARD_READER.get());
        helper.setBlock(old, ModBlocks.KEYCARD_READER.get());
        // Een nieuwe kern meldt zich pas een tick later aan (dan pas vindt een lezer zijn logboek).
        helper.runAfterDelay(2, () -> {
            ModBlocks.KEYCARD_READER.get().setPlacedBy(helper.getLevel(), helper.absolutePos(placed), helper.getBlockState(placed),
                    owner, ItemStack.EMPTY);
            String first = input(helper, placed).name();
            renameInWorld(helper, stranger, placed, "Hacked");
            check(input(helper, placed).name().equals(first), "Een vreemde in de zone kon de lezer hernoemen");
            renameInWorld(helper, friend, placed, "Vault door");
            check(input(helper, placed).name().equals("Vault door"), "De vertrouwde speler kon de lezer niet hernoemen");
            String line = "zone-friend renamed \"" + first + "\" to \"Vault door\"";
            check(core.log().entries().stream().anyMatch(entry -> entry.text().equals(line)),
                    "Het logboek zegt niet wie de lezer hernoemde: " + lastLog(core));

            // Zonder eigenaar (zoals in een oude wereld): een vreemde neemt hem niet over, een vriend mag erbij.
            click(helper, stranger, old, true);
            check(input(helper, old).owner() == null && !InputDevices.mayRename(stranger, input(helper, old)),
                    "Een vreemde kon een lezer zonder eigenaar in iemands zone overnemen");
            renameInWorld(helper, friend, old, "Back door");
            check(input(helper, old).name().equals("Back door"), "Een vriend mag een lezer zonder eigenaar in de zone niet hernoemen");
            ZoneGameTests.finish(helper, core);
        });
    }

    /** Staat deze tekst als regel van een diagram (LOGIC) in het logboek? */
    private static boolean logged(SecurityCoreBlockEntity core, String text) {
        return core.log().entries().stream().anyMatch(entry -> entry.type() == EventLog.Type.LOGIC && entry.text().equals(text));
    }

    // ================================================================ Ctrl+Spatie

    /** Wat Ctrl+Spatie op deze plek voorstelt (de cursor staat op {@code |}). */
    private static List<String> suggest(String code) {
        String[] lines = code.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int at = lines[i].indexOf('|');
            if (at >= 0) {
                List<String> clean = new java.util.ArrayList<>(List.of(code.replace("|", "").split("\n", -1)));
                var data = new com.senne.imttech.client.os.CodeCompletion.Data(List.of("Vault door", "Lab lights"),
                        List.of("Lab door"), List.of("Front door"));
                return com.senne.imttech.client.os.CodeCompletion.complete(clean, i, at, data).items().stream()
                        .map(com.senne.imttech.client.os.CodeCompletion.Item::label).toList();
            }
        }
        throw new GameTestAssertException("Geen | in de test-code");
    }

    private static void expectSuggestions(String code, List<String> wanted, List<String> notWanted) {
        List<String> got = suggest(code);
        for (String label : wanted) {
            if (!got.contains(label)) {
                throw new GameTestAssertException("Voor:\n" + code + "\nmiste ik \"" + label + "\" in " + got);
            }
        }
        for (String label : notWanted) {
            if (got.contains(label)) {
                throw new GameTestAssertException("Voor:\n" + code + "\nhoort \"" + label + "\" er niet bij: " + got);
            }
        }
    }

    /**
     * Ctrl+Spatie stelt voor wat op die plek past: whens en def aan de linkerkant, triggers na when,
     * acties en eigen defs in een blok, gegevens die bij de trigger passen na if, en namen tussen
     * aanhalingstekens. Wat je al typte, maakt de lijst korter.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_ctrl_space(GameTestHelper helper) {
        expectSuggestions("|", List.of("when keycard(...):", "when every(10):", "def name():"), List.of("accept()"));
        expectSuggestions("when |", List.of("keycard(\"...\"):", "every(10):"), List.of("accept()"));
        expectSuggestions("def open_lab():\n    pass\nwhen every(5):\n    |",
                List.of("on(\"...\")", "pulse(\"...\", 3)", "if ...:", "open_lab()"), List.of("when keycard(...):"));
        // Hoofdstuk 115: na een keycard bestaat player.* niet (de lezer weet niet wie de kaart vasthoudt).
        expectSuggestions("when keycard(\"Lab door\"):\n    if |", List.of("card.in_group(\"...\")", "card.valid"),
                List.of("code", "player.trusted", "player.name", "player.owner"));
        expectSuggestions("when codepanel(\"Front door\"):\n    if |", List.of("code", "player.trusted"), List.of("card.valid", "card.in_group(\"...\")"));
        expectSuggestions("when every(5):\n    if |", List.of("is_night", "is_on(\"...\")"), List.of("card.valid", "card.in_group(\"...\")", "player.trusted"));
        expectSuggestions("when keycard(\"|", List.of("Lab door"), List.of("Front door", "Vault door"));
        expectSuggestions("when every(5):\n    on(\"|\")", List.of("Vault door", "Lab lights"), List.of("Lab door"));
        // Wat je al typte, maakt de lijst korter (en wat ermee begint, staat bovenaan).
        List<String> pu = suggest("when every(5):\n    pu|");
        if (pu.isEmpty() || !pu.getFirst().equals("pulse(\"...\", 3)")) {
            throw new GameTestAssertException("Na \"pu\" zou pulse bovenaan moeten staan: " + pu);
        }
        List<String> va = suggest("when every(5):\n    on(\"La|");
        if (!va.equals(List.of("Lab lights"))) {
            throw new GameTestAssertException("Na on(\"La zou alleen Lab lights moeten passen: " + va);
        }
        // In een def weten we niet welke when hem gebruikt: dan mag alles.
        expectSuggestions("def check():\n    if |", List.of("card.valid", "card.in_group(\"...\")", "code", "is_night"), List.of());
        helper.succeed();
    }

    // ================================================================ De server voert uit

    /** Een kern met eigenaar, een server (rack met CPU, RAM en SSD) en de tijd om op te starten. */
    private record Setup(ServerPlayer owner, SecurityCoreBlockEntity core, BlockPos coreAbs, BlockPos rack) {}

    private static Setup setup(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6));
        BlockPos corePos = new BlockPos(2, 3, 2);
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, corePos, owner);
        BlockPos rack = new BlockPos(12, 3, 12);
        ImtTechGameTests.placeServer(helper, rack, core);
        return new Setup(owner, core, helper.absolutePos(corePos), rack);
    }

    /** Zet een relais neer en meldt het aan op de bril van de eigenaar, met deze naam. */
    private static BlockPos relay(GameTestHelper helper, Setup setup, BlockPos pos, String name) {
        helper.setBlock(pos, ModBlocks.RELAY.get());
        BlockPos abs = helper.absolutePos(pos);
        var data = setup.owner().getData(ModAttachments.RELAYS);
        setup.owner().setData(ModAttachments.RELAYS, data.with(new Relay(name, "",
                helper.getLevel().dimension(), abs)));
        return abs;
    }

    /** Een diagram bewaren zoals de app Logic dat doet (de code wordt eerst blokjes). */
    private static void save(Setup setup, String name, String code) {
        CompoundTag args = new CompoundTag();
        args.putString("old", "");
        args.putString("name", name);
        args.put("graph", GraphConverter.toGraph(ScriptParser.parse(code), null).save());
        args.putString("source", code);
        SecurityOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), "logic_save", args));
    }

    private static String lastLog(SecurityCoreBlockEntity core) {
        var entries = core.log().entries();
        return entries.isEmpty() ? "" : entries.getLast().type() + ": " + entries.getLast().text();
    }

    /**
     * Het hele verhaal met een keycard: een geldige kaart in de groep "night shift", die in "security" zit, die in
     * "staff" zit, geeft groen bij {@code card.in_group("staff")} en zet de kluisdeur even aan (en daarna weer uit);
     * een ongeldige kaart geeft rood en een alarm.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "logic_runner")
    public static void logic_keycard_opens_vault(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        BlockPos vault = relay(helper, setup, new BlockPos(9, 3, 4), "Vault door");
        BlockPos reader = new BlockPos(8, 3, 8);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(reader)).setName("Lab door");
        BlockPos readerAbs = helper.absolutePos(reader);
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
            SecurityOs.open(setup.owner(), setup.coreAbs());
            save(setup, "Lab", """
                    when keycard("Lab door"):
                        if card.in_group('Staff'):
                            accept()
                            pulse("Vault door", 2)
                        else:
                            deny()
                            alarm("Wrong card at the lab")
                    """);
            if (setup.core().logic().program("Lab") == null) {
                throw new GameTestAssertException("Het diagram werd niet bewaard: " + lastLog(setup.core()));
            }
            var store = setup.core().logic();
            check(store.addGroup("staff", "") && store.addGroup("security", "staff") && store.addGroup("night shift", "security"),
                    "De groepen maken lukte niet");
            var card = store.issue("Lab access", 3, "test", "night shift");
            LogicEvents.clear();
            LogicEvents.keycard(level, readerAbs, setup.owner(),
                    new KeycardData(setup.core().ip(), setup.core().hostname(), card.number(), 3, "Lab access"));
        });
        // Na de reactietijd (met 2 apparaten op één CPU meteen): groen, en de kluisdeur aan.
        helper.runAfterDelay(20, () -> {
            helper.assertBlockProperty(reader, InputDeviceBlock.LIGHT, InputLight.OK);
            if (!level.getBlockState(vault).getValue(RelayBlock.POWERED)) {
                throw new GameTestAssertException("De kluisdeur ging niet open. Logboek: " + lastLog(setup.core()));
            }
        });
        // Na de puls van 2 seconden weer dicht; dan een kaart van een ander netwerk.
        helper.runAfterDelay(70, () -> {
            if (level.getBlockState(vault).getValue(RelayBlock.POWERED)) {
                throw new GameTestAssertException("De kluisdeur ging na de puls niet meer dicht");
            }
            LogicEvents.clear();
            LogicEvents.keycard(level, readerAbs, setup.owner(), new KeycardData("10.9.9.9", "other", 1001, 5, "Fake"));
        });
        helper.runAfterDelay(90, () -> {
            helper.assertBlockProperty(reader, InputDeviceBlock.LIGHT, InputLight.DENIED);
            boolean alarm = setup.core().log().entries().stream()
                    .anyMatch(entry -> entry.type() == EventLog.Type.ALARM && entry.text().contains("Wrong card"));
            if (!alarm || level.getBlockState(vault).getValue(RelayBlock.POWERED)) {
                throw new GameTestAssertException("Een valse kaart gaf geen alarm (of opende de deur). Logboek: " + lastLog(setup.core()));
            }
        });
        // Pas als het rode lampje van de valse kaart weer uit is (LIGHT_TICKS na het rood): sinds hoofdstuk 86 reageert een
        // server met weinig apparaten meteen, en een tweede rood binnen die 30 ticks zou door de eerste "uit" afgekapt worden.
        helper.runAfterDelay(105, () -> {
            // De echte kaart geblokkeerd: hij zit nog in zijn groep, maar card.in_group geeft dan false.
            var card = setup.core().logic().cards().getFirst();
            setup.core().logic().setBlocked(card.number(), true);
            LogicEvents.clear();
            LogicEvents.keycard(level, readerAbs, setup.owner(),
                    new KeycardData(setup.core().ip(), setup.core().hostname(), card.number(), 3, "Lab access"));
        });
        helper.runAfterDelay(115, () -> {
            helper.assertBlockProperty(reader, InputDeviceBlock.LIGHT, InputLight.DENIED);
            boolean logged = setup.core().log().entries().stream()
                    .anyMatch(entry -> entry.text().contains("used card #1001 (not valid here)"));
            if (!logged || level.getBlockState(vault).getValue(RelayBlock.POWERED)) {
                throw new GameTestAssertException("Een geblokkeerde kaart van staff opende de deur (of het logboek klopt niet). Logboek: "
                        + lastLog(setup.core()));
            }
            helper.succeed();
        });
    }

    /**
     * Hoofdstuk 115 (jouw wens): bij een keycardlezer weet je diagram niet wie de kaart vasthoudt. player.name,
     * player.trusted en player.owner geven na when keycard een fout die zegt waarom, ook via een def die zo'n when gebruikt,
     * en Ctrl+Spatie stelt ze daar niet voor. Na een codepaneel, knop, hendel of verzoek weet de server het wel.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "logic_language")
    public static void logic_keycard_knows_no_player(GameTestHelper helper) {
        expectError("when keycard(\"Lab door\"):\n    if player.trusted:\n        accept()", 2,
                "player.trusted does not work after a keycard");
        expectError("when keycard(\"Lab door\"):\n    say(\"Hi \" + player.name)", 2, "player.name does not work after a keycard");
        expectError("when keycard(\"Lab door\"):\n    if card.valid and player.owner:\n        accept()", 2,
                "player.owner does not work after a keycard");
        expectError("def greet():\n    say(player.name)\n\nwhen keycard(\"Lab door\"):\n    greet()", 5, "greet() uses player.name");
        for (String code : List.of(
                "when codepanel(\"Front door\"):\n    if code == \"4321\" and player.trusted:\n        accept()",
                "when button(\"Doorbell\"):\n    say(player.name + \" is at the door\")",
                "when lever(\"Night mode\"):\n    if player.owner:\n        on(\"Lamp\")",
                "when request(\"Gate\"):\n    if not request.accepted:\n        say(player.name + \" may not come in\")")) {
            try {
                ScriptParser.parse(code);
            } catch (LogicException e) {
                throw new GameTestAssertException("Deze code zou moeten kloppen:\n" + code + "\nmaar gaf: " + e.describe());
            }
        }
        expectSuggestions("when keycard(\"Lab door\"):\n    if |", List.of("card.valid"),
                List.of("player.name", "player.trusted", "player.owner"));
        expectSuggestions("when button(\"Doorbell\"):\n    if |", List.of("player.name", "player.trusted"), List.of("card.valid"));
        // Een diagram van vóór hoofdstuk 115, met commentaar en player.owner na een kaart: de controle weigert het nu, maar
        // het past nog bij zijn blokjes. Zo toont de app Logic (LogicApp.codeFor) je eigen code met je commentaar.
        String old = "# master key for me\nwhen keycard(\"Lab door\"):\n    if player.owner or card.in_group(\"staff\"):\n        accept()\n";
        expectError(old, 3, "player.owner does not work after a keycard");
        Script oldScript = ScriptParser.parseUnchecked(old, ProgramKind.SERVER);
        check(ScriptPrinter.print(oldScript).equals(ScriptPrinter.print(GraphConverter.toScript(GraphConverter.toGraph(oldScript, null)))),
                "Een oud keycard-diagram past niet meer bij zijn eigen blokjes");
        helper.succeed();
    }

    /**
     * Hoofdstuk 115 (jouw wens): een keycardlezer logt niet wie de kaart gebruikte. Een dief met de kaart van de eigenaar
     * komt gewoon binnen, en het logboek zegt "Someone used card #... at Lab door" (ook bij een nagemaakte kaart van de
     * Bypass Kit). Een alarm na een kaart heeft geen naam erachter, maar de beveiliging jaagt toch op de vreemde die er
     * stond; de eigenaar is nooit een indringer. Een oud diagram dat na een kaart nog player.name, player.trusted of
     * player.owner leest, krijgt "" en false, en het logboek zegt waarom.
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "logic_runner")
    public static void logic_keycard_reader_is_anonymous(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        ServerPlayer thief = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(7, 3, 9), "keycard-thief");
        String ownerName = setup.owner().getGameProfile().getName();
        BlockPos reader = new BlockPos(8, 3, 8);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(reader)).setName("Lab door");
        BlockPos readerAbs = helper.absolutePos(reader);
        KeycardData fake = new KeycardData("10.9.9.9", "other", 1001, 5, "Fake");
        int[] number = new int[1];
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
            SecurityOs.open(setup.owner(), setup.coreAbs());
            save(setup, "Lab", """
                    when keycard("Lab door"):
                        if card.in_group("staff"):
                            accept()
                        else:
                            deny()
                            alarm("Wrong card at the lab")
                    """);
            check(setup.core().logic().program("Lab") != null, "Het diagram werd niet bewaard: " + lastLog(setup.core()));
            // Een diagram van vóór hoofdstuk 115 (zonder controle bewaard): na een kaart leest het nog de speler.
            LogicGraph old = new LogicGraph();
            LogicGraph.Node when = old.add(Op.KEYCARD, 0, 0);
            when.text = "Lab door";
            LogicGraph.Node who = old.add(Op.LOG, 0, 40);
            who.expr = "\"Old: \" + player.name";
            when.next = who.id;
            LogicGraph.Node test = old.add(Op.IF, 0, 80);
            test.text = "player.trusted or player.owner";
            who.next = test.id;
            LogicGraph.Node friend = old.add(Op.LOG, 100, 80);
            friend.text = "Old: a friend";
            test.yes = friend.id;
            setup.core().logic().put(null, new LogicProgram("Old", true, old, ""));
            check(setup.core().logic().addGroup("staff", ""), "De groep maken lukte niet");
            number[0] = setup.core().logic().issue("Staff card", 3, "test", "staff").number();
            // De dief stal de kaart van de eigenaar: voor de lezer is dat gewoon die kaart.
            LogicEvents.clear();
            LogicEvents.keycard(level, readerAbs, thief, new KeycardData(setup.core().ip(), setup.core().hostname(), number[0], 3,
                    "Staff card"));
        });
        helper.runAfterDelay(20, () -> {
            helper.assertBlockProperty(reader, InputDeviceBlock.LIGHT, InputLight.OK);
            check(hasLog(setup.core(), EventLog.Type.ENTRY, "Someone used card #" + number[0] + " (staff) at Lab door"),
                    "De lezer schreef niet \"Someone used card ...\": " + lastLog(setup.core()));
            check(hasLog(setup.core(), EventLog.Type.LOGIC, "Old: ") && !hasLog(setup.core(), EventLog.Type.LOGIC, "Old: a friend"),
                    "Het oude diagram kende de speler nog: " + lastLog(setup.core()));
            check(setup.core().log().entries().stream()
                            .anyMatch(entry -> entry.text().contains("player.name does not work after a keycard")),
                    "Het logboek zegt niet waarom player.name leeg is: " + lastLog(setup.core()));
            // De Bypass Kit: een nagemaakte kaart, ook zonder naam.
            LogicEvents.clear();
            LogicEvents.forgedKeycard(level, readerAbs, thief, "staff");
        });
        helper.runAfterDelay(40, () -> {
            check(setup.core().log().entries().stream().filter(entry -> entry.type() == EventLog.Type.ENTRY)
                            .filter(entry -> entry.text().startsWith("Someone used card #")).count() == 2,
                    "De nagemaakte kaart kwam niet (zonder naam) in het logboek: " + lastLog(setup.core()));
            // Een kaart van een ander netwerk: rood, en een alarm zonder naam erachter.
            LogicEvents.clear();
            LogicEvents.keycard(level, readerAbs, thief, fake);
        });
        helper.runAfterDelay(60, () -> {
            check(hasLog(setup.core(), EventLog.Type.ALARM, "Wrong card at the lab"),
                    "Het alarm kwam er niet, of met een naam erachter: " + lastLog(setup.core()));
            check(setup.core().isHunted(thief.getUUID()), "De beveiliging jaagt niet op de dief die er stond");
            // De eigenaar met een verkeerde kaart: ook een alarm zonder naam, maar hij is nooit een indringer.
            LogicEvents.clear();
            LogicEvents.keycard(level, readerAbs, setup.owner(), fake);
        });
        helper.runAfterDelay(80, () -> {
            check(setup.core().log().entries().stream()
                            .filter(entry -> entry.type() == EventLog.Type.ALARM && entry.text().equals("Wrong card at the lab"))
                            .count() == 2,
                    "Het tweede alarm kwam er niet zonder naam: " + lastLog(setup.core()));
            check(!com.senne.imttech.security.SecurityCores.isIntruder(level, setup.core(), setup.owner()),
                    "Na een alarm bij zijn eigen lezer is de eigenaar een indringer");
            // Nergens een naam: niet bij de lezer, niet in een alarm of een regel van een diagram, niet in de berichten.
            for (EventLog.Entry entry : setup.core().log().entries()) {
                boolean fromCards = entry.type() == EventLog.Type.ENTRY || entry.type() == EventLog.Type.ALARM
                        || entry.type() == EventLog.Type.LOGIC;
                check(!fromCards || !entry.text().contains("keycard-thief") && !entry.text().contains(ownerName),
                        "Een naam in het logboek: " + entry.text());
            }
            for (SecurityCoreBlockEntity.Message message : setup.core().messages()) {
                check(!message.text().contains("keycard-thief") && !message.text().contains(ownerName),
                        "Een naam in de berichten: " + message.text());
            }
            helper.succeed();
        });
    }

    /** Staat er een regel van deze soort met precies deze tekst in het logboek van de kern? */
    private static boolean hasLog(SecurityCoreBlockEntity core, EventLog.Type type, String text) {
        return core.log().entries().stream().anyMatch(entry -> entry.type() == type && entry.text().equals(text));
    }

    /**
     * Een timer zet een lamp aan, en "when turned_on" van die lamp zet de ventilator aan: een relais
     * activeert een ander relais (jouw voorbeeld). Een codepaneel met de goede code zet de lamp weer uit.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "logic_runner_chain")
    public static void logic_timer_and_chain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        BlockPos lamp = relay(helper, setup, new BlockPos(9, 3, 4), "Lamp");
        BlockPos fan = relay(helper, setup, new BlockPos(9, 3, 6), "Fan");
        BlockPos panel = new BlockPos(8, 3, 8);
        helper.setBlock(panel, ModBlocks.CODE_PANEL.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(panel)).setName("Front door");
        BlockPos panelAbs = helper.absolutePos(panel);
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
            SecurityOs.open(setup.owner(), setup.coreAbs());
            save(setup, "House", """
                    when every(1):
                        if not is_on("Lamp"):
                            on("Lamp")

                    when turned_on("Lamp"):
                        on("Fan")

                    when codepanel("Front door"):
                        if code == "4321" and player.owner:
                            accept()
                            off("Lamp")
                            off("Fan")
                        else:
                            deny()
                    """);
            if (setup.core().logic().program("House") == null) {
                throw new GameTestAssertException("Het diagram werd niet bewaard: " + lastLog(setup.core()));
            }
        });
        // De timer gaat na 1 s af, dan nog 0,5 s reactietijd, en dan nog eens voor de ventilator.
        helper.runAfterDelay(70, () -> {
            if (!level.getBlockState(lamp).getValue(RelayBlock.POWERED) || !level.getBlockState(fan).getValue(RelayBlock.POWERED)) {
                throw new GameTestAssertException("Lamp of ventilator ging niet aan. Logboek: " + lastLog(setup.core()));
            }
            // Het diagram uitzetten, en dan de code op het paneel.
            CompoundTag args = new CompoundTag();
            args.putString("name", "House");
            args.putBoolean("on", false);
            SecurityOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), "logic_enable", args));
            args.putBoolean("on", true);
            SecurityOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), "logic_enable", args));
            setup.owner().teleportTo(panelAbs.getX() + 0.5, panelAbs.getY(), panelAbs.getZ() + 2.5);
            LogicEvents.clear();
            LogicEvents.codePanel(level, panelAbs, setup.owner(), "4321");
        });
        helper.runAfterDelay(85, () -> {
            helper.assertBlockProperty(panel, InputDeviceBlock.LIGHT, InputLight.OK);
            if (level.getBlockState(fan).getValue(RelayBlock.POWERED)) {
                throw new GameTestAssertException("De goede code zette de ventilator niet uit. Logboek: " + lastLog(setup.core()));
            }
            helper.succeed();
        });
    }

    /**
     * Hoofdstuk 64 (jouw keuze "echte maten, limieten weg"): een SSD is standaard 200 GB en een blokje 2 MB, een RAM-latje
     * 8 GB en een bezig diagram 64 MB (de test rekent met wat er in de config staat). Met één SSD past nu ook een groot
     * diagram (26 blokjes, vroeger te veel), en met één RAM-latje lopen drie wachtende diagrammen gewoon tegelijk (vroeger
     * moest de derde wachten).
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "logic_runner_limits")
    public static void logic_ssd_and_ram_no_longer_limit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        relay(helper, setup, new BlockPos(9, 3, 4), "Lamp");
        BlockPos reader = new BlockPos(8, 3, 8);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(reader)).setName("Slow");
        BlockPos readerAbs = helper.absolutePos(reader);
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
            SecurityOs.open(setup.owner(), setup.coreAbs());
            // 25 acties onder een when = 26 blokjes (een paar MB): past ruim op één SSD.
            StringBuilder big = new StringBuilder("when every(5):\n");
            for (int i = 0; i < 25; i++) {
                big.append("    toggle(\"Lamp\")\n");
            }
            save(setup, "Big", big.toString());
            if (setup.core().logic().program("Big") == null) {
                throw new GameTestAssertException("Een diagram van 26 blokjes (" + LogicStats.bytes(LogicStats.blocksMb(26))
                        + ") zou op één SSD van " + LogicStats.bytes(LogicStats.ssdMb()) + " moeten passen");
            }
            save(setup, "Slow", "when keycard(\"Slow\"):\n    wait(5)\n    on(\"Lamp\")\n");
            for (int i = 0; i < 3; i++) {
                LogicRunner.fire(level, setup.core(), com.senne.imttech.logic.program.Op.KEYCARD, "Slow",
                        new LogicRunner.Context(com.senne.imttech.logic.program.Op.KEYCARD, "Slow", readerAbs, null, "",
                                false, false, null, false, ""));
            }
        });
        helper.runAfterDelay(30, () -> {
            int[] load = LogicRunner.load(level, setup.core());
            if (load[0] < 3 || load[1] != 0) {
                throw new GameTestAssertException("Met één RAM-latje van " + LogicStats.bytes(LogicStats.ramStickMb())
                        + " zouden de drie diagrammen tegelijk moeten lopen, niet "
                        + load[0] + " bezig en " + load[1] + " wachtend");
            }
            if (LogicServer.stats(level, setup.core()).maxRuns() != LogicStats.maxRuns(1) || LogicStats.maxRuns(1) < 100) {
                throw new GameTestAssertException("Eén latje zou plaats voor minstens honderd diagrammen moeten hebben: "
                        + LogicStats.maxRuns(1));
            }
            helper.succeed();
        });
    }
}
