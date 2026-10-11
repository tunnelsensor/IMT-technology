package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.logic.InputDeviceBlock;
import com.senne.starktech.logic.InputDeviceBlockEntity;
import com.senne.starktech.logic.InputLight;
import com.senne.starktech.logic.KeycardData;
import com.senne.starktech.logic.LogicEvents;
import com.senne.starktech.logic.LogicRunner;
import com.senne.starktech.logic.program.GraphConverter;
import com.senne.starktech.logic.program.LogicException;
import com.senne.starktech.logic.program.LogicGraph;
import com.senne.starktech.logic.program.Script;
import com.senne.starktech.logic.program.ScriptParser;
import com.senne.starktech.logic.program.ScriptPrinter;
import com.senne.starktech.network.OsActionPayload;
import com.senne.starktech.redstone.Relay;
import com.senne.starktech.redstone.RelayBlock;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.os.EventLog;
import com.senne.starktech.security.os.StarkOs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de redstone-diagrammen op de server (hoofdstuk 8 van het plan): de taal (code lezen,
 * terugschrijven, foutmeldingen), code naar blokjes en terug, en de server die de diagrammen echt
 * uitvoert (keycard, codepaneel, timers, relais, RAM en SSD's).
 */
@GameTestHolder(StarkTech.MODID)
@PrefixGameTestTemplate(false)
public final class LogicGameTests {
    private LogicGameTests() {}

    private static final String ARENA = "empty";

    /** Het voorbeeld uit het plan: alles wat de taal kan, netjes geschreven zoals de printer het doet. */
    private static final String EXAMPLE = """
            when keycard("Lab door"):
                if card.valid and card.level >= 3:
                    accept()
                    pulse("Vault door", 3)
                elif card.level >= 1:
                    say("Someone with a low card tried the lab")
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
        expectError("when keycard(\"Lab\"):\n    on(Lamp)", 2, "Put names between quotes");
        expectError("when keycard(\"Lab\"):\n    accept()\n      deny()", 3, "indented more");
        expectError("when keycard(\"Lab\"):\n    if card.level = 3:\n        accept()", 2, "Use == to compare");
        expectError("when keycard(\"Lab\"):\n    if card.level >= \"3\":\n        accept()", 2, "cannot compare a number with text");
        expectError("when keycard(\"Lab\"):\nwhen every(5):\n    on(\"A\")", 1, "must be lines that are indented");
        expectError("when keycard(\"Lab\"):\n    else:\n        accept()", 2, "no if above it");
        expectError("when keycard(\"Lab\"):\n    pulse(\"Gate\", 99)", 2, "0.05 to 60 seconds");
        helper.succeed();
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
        LogicGraph.Node timer = graph.nodes().stream().filter(node -> node.op == com.senne.starktech.logic.program.Op.EVERY)
                .findFirst().orElseThrow();
        timer.x = 500;
        timer.y = 700;
        String edited = EXAMPLE.replace("log(\"Lab door used\")", "log(\"Lab door used\")\n    wait(1)");
        LogicGraph again = GraphConverter.toGraph(ScriptParser.parse(edited), graph);
        LogicGraph.Node movedTimer = again.nodes().stream().filter(node -> node.op == com.senne.starktech.logic.program.Op.EVERY)
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
                if card.valid and card.level >= 3:
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
        LogicGraph.Node def = graph.nodes().stream().filter(node -> node.op == com.senne.starktech.logic.program.Op.DEF)
                .findFirst().orElseThrow();
        LogicGraph.Node trigger = graph.nodes().iterator().next();
        if (graph.connect(trigger.id, LogicGraph.Port.NEXT, def.id) == null) {
            throw new GameTestAssertException("Een draadje naar een DEF werd toegelaten");
        }
        LogicGraph.Node call = graph.nodes().stream().filter(node -> node.op == com.senne.starktech.logic.program.Op.CALL)
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
        expectError("def check():\n    if card.level >= 3:\n        pass\nwhen codepanel(\"P\"):\n    check()", 5,
                "check() uses card.level, which only works inside when keycard(...)");
        expectError("def outer():\n    inner()\ndef inner():\n    if code == \"1\":\n        pass\nwhen keycard(\"R\"):\n    outer()",
                7, "outer() uses code");
        expectError("def a():\n    pass\ndef a():\n    pass\nwhen every(5):\n    a()", 3, "There already is a def a");
        expectError("def on():\n    pass", 1, "already a word of the language");
        expectError("def open(door):\n    pass", 1, "take nothing between the brackets");
        expectError("when every(5):\n    def x():\n        pass", 2, "A def must not be indented");
        expectError("def a():\n    pass\nwhen every(5):\n    a(\"x\")", 4, "take nothing between the brackets");
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
            StarkTechGameTests.startServer(helper, setup.rack(), setup.core());
            StarkOs.open(setup.owner(), setup.coreAbs());
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
            var card = setup.core().logic().issue("Lab access", 1, "test");
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
        StarkOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), op, args));
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
            StarkOs.open(setup.owner(), setup.coreAbs());
            var store = setup.core().logic();
            var lab = store.issue("Lab access", 3, "test");
            var guest = store.issue("Guest", 1, "test");
            var lost = store.issue("Lost card", 2, "test");
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
            var fresh = store.issue("New", 1, "test");
            if (fresh.number() <= lost.number()) {
                throw new GameTestAssertException("Een weggegooid nummer kwam terug: #" + fresh.number());
            }
            // Ook na opslaan en weer laden.
            var loaded = com.senne.starktech.logic.LogicStore.load(store.save());
            var after = loaded.issue("After load", 1, "test");
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
            StarkTechGameTests.startServer(helper, setup.rack(), setup.core());
            StarkOs.open(setup.owner(), setup.coreAbs());
            save(setup, "Lab", """
                    when keycard("Lab door"):
                        accept()
                    """);
            com.senne.starktech.security.SecurityDevices.forget(level, setup.core());
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
        for (var element : com.senne.starktech.security.SecurityDevices.snapshot(level, setup.core())) {
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
        StarkOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), "input_rename", args));
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
                var data = new com.senne.starktech.client.os.CodeCompletion.Data(List.of("Vault door", "Lab lights"),
                        List.of("Lab door"), List.of("Front door"));
                return com.senne.starktech.client.os.CodeCompletion.complete(clean, i, at, data).items().stream()
                        .map(com.senne.starktech.client.os.CodeCompletion.Item::label).toList();
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
        expectSuggestions("when keycard(\"Lab door\"):\n    if |", List.of("card.level", "card.valid", "player.trusted"),
                List.of("code"));
        expectSuggestions("when codepanel(\"Front door\"):\n    if |", List.of("code", "player.trusted"), List.of("card.level"));
        expectSuggestions("when every(5):\n    if |", List.of("is_night", "is_on(\"...\")"), List.of("card.level", "player.trusted"));
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
        expectSuggestions("def check():\n    if |", List.of("card.level", "code", "is_night"), List.of());
        helper.succeed();
    }

    // ================================================================ De server voert uit

    /** Een kern met eigenaar, een server (rack met CPU, RAM en SSD) en de tijd om op te starten. */
    private record Setup(ServerPlayer owner, SecurityCoreBlockEntity core, BlockPos coreAbs, BlockPos rack) {}

    private static Setup setup(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6));
        BlockPos corePos = new BlockPos(2, 3, 2);
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, corePos, owner);
        BlockPos rack = new BlockPos(12, 3, 12);
        StarkTechGameTests.placeServer(helper, rack, core);
        return new Setup(owner, core, helper.absolutePos(corePos), rack);
    }

    /** Zet een relais neer en meldt het aan op de bril van de eigenaar, met deze naam. */
    private static BlockPos relay(GameTestHelper helper, Setup setup, BlockPos pos, String name) {
        helper.setBlock(pos, ModBlocks.RELAY.get());
        BlockPos abs = helper.absolutePos(pos);
        var data = setup.owner().getData(ModAttachments.RELAYS);
        setup.owner().setData(ModAttachments.RELAYS, data.with(new Relay(name, Relay.DEFAULT_GROUP,
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
        StarkOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), "logic_save", args));
    }

    private static String lastLog(SecurityCoreBlockEntity core) {
        var entries = core.log().entries();
        return entries.isEmpty() ? "" : entries.getLast().type() + ": " + entries.getLast().text();
    }

    /**
     * Het hele verhaal met een keycard: een geldige kaart van niveau 3 geeft groen en zet de kluisdeur
     * even aan (en daarna weer uit); een ongeldige kaart geeft rood en een alarm.
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
            StarkTechGameTests.startServer(helper, setup.rack(), setup.core());
            StarkOs.open(setup.owner(), setup.coreAbs());
            save(setup, "Lab", """
                    when keycard("Lab door"):
                        if card.valid and card.level >= 3:
                            accept()
                            pulse("Vault door", 2)
                        else:
                            deny()
                            alarm("Wrong card at the lab")
                    """);
            if (setup.core().logic().program("Lab") == null) {
                throw new GameTestAssertException("Het diagram werd niet bewaard: " + lastLog(setup.core()));
            }
            var card = setup.core().logic().issue("Lab access", 3, "test");
            LogicEvents.clear();
            LogicEvents.keycard(level, readerAbs, setup.owner(),
                    new KeycardData(setup.core().ip(), setup.core().hostname(), card.number(), 3, "Lab access"));
        });
        // Na de reactietijd (een halve seconde met één CPU): groen, en de kluisdeur aan.
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
            helper.succeed();
        });
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
            StarkTechGameTests.startServer(helper, setup.rack(), setup.core());
            StarkOs.open(setup.owner(), setup.coreAbs());
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
            StarkOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), "logic_enable", args));
            args.putBoolean("on", true);
            StarkOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), "logic_enable", args));
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
     * De onderdelen tellen: met één SSD (24 blokjes) past een te groot diagram niet, en met één RAM-latje
     * (2 tegelijk) wacht de derde run in de rij.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "logic_runner_limits")
    public static void logic_ssd_and_ram_limits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        relay(helper, setup, new BlockPos(9, 3, 4), "Lamp");
        BlockPos reader = new BlockPos(8, 3, 8);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(reader)).setName("Slow");
        BlockPos readerAbs = helper.absolutePos(reader);
        helper.runAfterDelay(2, () -> {
            StarkTechGameTests.startServer(helper, setup.rack(), setup.core());
            StarkOs.open(setup.owner(), setup.coreAbs());
            // 25 acties onder een when = 26 blokjes: past niet op één SSD.
            StringBuilder big = new StringBuilder("when every(5):\n");
            for (int i = 0; i < 25; i++) {
                big.append("    toggle(\"Lamp\")\n");
            }
            save(setup, "Too big", big.toString());
            if (setup.core().logic().program("Too big") != null) {
                throw new GameTestAssertException("Een diagram dat niet op de SSD past, werd toch bewaard");
            }
            save(setup, "Slow", "when keycard(\"Slow\"):\n    wait(5)\n    on(\"Lamp\")\n");
            for (int i = 0; i < 3; i++) {
                LogicRunner.fire(level, setup.core(), com.senne.starktech.logic.program.Op.KEYCARD, "Slow",
                        new LogicRunner.Context(com.senne.starktech.logic.program.Op.KEYCARD, "Slow", readerAbs, null, "",
                                false, false, null, false, ""));
            }
        });
        helper.runAfterDelay(30, () -> {
            int[] load = LogicRunner.load(level, setup.core());
            if (load[0] != StarkConfig.Logic.RUNS_PER_RAM || load[1] != 1) {
                throw new GameTestAssertException("Met één RAM-latje zouden er 2 bezig moeten zijn en 1 wachten, niet "
                        + load[0] + " en " + load[1]);
            }
            helper.succeed();
        });
    }
}
