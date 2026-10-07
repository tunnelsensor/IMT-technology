package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
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
            if (load[0] != com.senne.starktech.logic.LogicBalance.RUNS_PER_RAM || load[1] != 1) {
                throw new GameTestAssertException("Met één RAM-latje zouden er 2 bezig moeten zijn en 1 wachten, niet "
                        + load[0] + " en " + load[1]);
            }
            helper.succeed();
        });
    }
}
