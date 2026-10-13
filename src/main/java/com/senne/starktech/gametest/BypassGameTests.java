package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.bypass.Bypass;
import com.senne.starktech.bypass.CircuitPuzzle;
import com.senne.starktech.bypass.PanelOrders;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.logic.InputDeviceBlock;
import com.senne.starktech.logic.InputDeviceBlockEntity;
import com.senne.starktech.logic.InputLight;
import com.senne.starktech.logic.KeycardData;
import com.senne.starktech.logic.KeycardItem;
import com.senne.starktech.logic.LogicEvents;
import com.senne.starktech.logic.PanelCodes;
import com.senne.starktech.logic.program.GraphConverter;
import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.logic.program.ScriptParser;
import com.senne.starktech.network.BypassPayload;
import com.senne.starktech.network.OsActionPayload;
import com.senne.starktech.network.PocketPayload;
import com.senne.starktech.pickpocket.Pickpocket;
import com.senne.starktech.redstone.Relay;
import com.senne.starktech.redstone.RelayBlock;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.os.EventLog;
import com.senne.starktech.security.os.StarkOs;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor inbreken (hoofdstuk 12 van het plan): de Bypass Kit, het stroomcircuit van een
 * keycardlezer, de vingerafdrukken en de volgorde van een codepaneel, en zakkenrollen.
 */
@GameTestHolder(StarkTech.MODID)
@PrefixGameTestTemplate(false)
public final class BypassGameTests {
    private BypassGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    // ================================================================ Het stroomcircuit zelf

    /**
     * Honderden rasters van elk niveau: altijd op te lossen, nooit al klaar of al op ALARM aan het
     * begin, even groot als in de config, en hetzelfde getal geeft precies hetzelfde raster (dat moet:
     * de server maakt het raster zelf na om je oplossing te controleren).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "bypass_circuit")
    public static void bypass_circuits_always_solvable(GameTestHelper helper) {
        for (int level = 1; level <= 5; level++) {
            int index = level - 1;
            int dangerous = 0;
            for (long seed = 0; seed < 300; seed++) {
                CircuitPuzzle puzzle = CircuitPuzzle.generate(seed * 7919L - 12345L, level);
                String which = "Niveau " + level + ", getal " + seed + ": ";
                check(puzzle.columns() == Math.round(StarkConfig.Bypass.CIRCUIT_COLUMNS[index]) && puzzle.rows() == Math.round(StarkConfig.Bypass.CIRCUIT_ROWS[index]),
                        which + "het raster is " + puzzle.columns() + " x " + puzzle.rows());
                check(puzzle.alarms().size() == Math.round(StarkConfig.Bypass.CIRCUIT_ALARMS[index]),
                        which + puzzle.alarms().size() + " ALARM-punten in plaats van " + Math.round(StarkConfig.Bypass.CIRCUIT_ALARMS[index]));
                CircuitPuzzle.Signal begin = puzzle.signal(puzzle.start());
                check(!begin.accepted() && begin.alarms().isEmpty(), which + "het begint al opgelost of op ALARM");
                CircuitPuzzle.Signal solved = puzzle.signal(puzzle.solution());
                check(solved.solved(), which + "de oplossing komt niet bij ACCEPT, of raakt ALARM");
                CircuitPuzzle again = CircuitPuzzle.generate(seed * 7919L - 12345L, level);
                check(Arrays.equals(again.start(), puzzle.start()) && again.cardRow() == puzzle.cardRow()
                        && again.acceptRow() == puzzle.acceptRow() && again.alarms().equals(puzzle.alarms()),
                        which + "hetzelfde getal gaf een ander raster");
                for (int cell = 0; cell < puzzle.cells(); cell++) {
                    check(again.shape(cell) == puzzle.shape(cell), which + "hetzelfde getal gaf andere stukjes");
                }
                if (alarmOneTurnAway(puzzle)) {
                    dangerous++;
                }
            }
            // ALARM moet echt gevaarlijk zijn: meestal raak je hem met één verkeerde draai van een stukje
            // op het pad (zo staat het in het plan: "draai het stukje binnen 1 seconde weg").
            check(dangerous >= 300 * 6 / 10, "Niveau " + level + ": maar in " + dangerous
                    + " van de 300 rasters raak je ALARM met één verkeerde draai op het pad");
        }
        // Wat een speler terugstuurt moet precies passen: één getal 0-3 per vakje.
        CircuitPuzzle puzzle = CircuitPuzzle.generate(42, 3);
        check(puzzle.readRotations(new byte[puzzle.cells() - 1]) == null, "Een te korte stand werd aangenomen");
        byte[] weird = new byte[puzzle.cells()];
        weird[0] = 7;
        check(puzzle.readRotations(weird) == null, "Een stand met een 7 erin werd aangenomen");
        helper.succeed();
    }

    /** Raakt het signaal een ALARM als je, met de rest opgelost, één stukje van het pad anders draait? */
    private static boolean alarmOneTurnAway(CircuitPuzzle puzzle) {
        int[] rotations = puzzle.solution();
        for (int cell : puzzle.pathOrder()) {
            int good = rotations[cell];
            for (int turns = 0; turns < 4; turns++) {
                rotations[cell] = turns;
                if (!puzzle.signal(rotations).alarms().isEmpty()) {
                    return true;
                }
            }
            rotations[cell] = good;
        }
        return false;
    }

    // ================================================================ Een lezer hacken

    /**
     * Een kern met eigenaar en server, een kluisdeur, een lezer "Lab door", een codepaneel "Front door" (en
     * een paneel "Side door" dat in geen diagram staat), en een hacker met een kit.
     */
    private record Setup(ServerPlayer owner, ServerPlayer hacker, SecurityCoreBlockEntity core, BlockPos coreAbs,
                         BlockPos reader, BlockPos readerAbs, BlockPos vault, BlockPos panel, BlockPos panelAbs,
                         BlockPos sidePanelAbs) {}

    private static Setup setup(GameTestHelper helper, boolean server) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(2, 3, 6), "bypass-owner");
        BlockPos corePos = new BlockPos(2, 3, 2);
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, corePos, owner);
        BlockPos rack = new BlockPos(12, 3, 12);
        if (server) {
            StarkTechGameTests.placeServer(helper, rack, core);
        }
        BlockPos vault = new BlockPos(9, 3, 4);
        helper.setBlock(vault, ModBlocks.RELAY.get());
        BlockPos vaultAbs = helper.absolutePos(vault);
        owner.setData(ModAttachments.RELAYS, owner.getData(ModAttachments.RELAYS).with(new Relay("Vault door",
                java.util.List.of(), helper.getLevel().dimension(), vaultAbs)));
        BlockPos reader = new BlockPos(8, 3, 8);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(reader)).setName("Lab door");
        BlockPos panel = new BlockPos(7, 3, 8);
        helper.setBlock(panel, ModBlocks.CODE_PANEL.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(panel)).setName("Front door");
        BlockPos sidePanel = new BlockPos(6, 3, 8);
        helper.setBlock(sidePanel, ModBlocks.CODE_PANEL.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(sidePanel)).setName("Side door");
        ServerPlayer hacker = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 3, 6), "bypass-hacker");
        hacker.getAbilities().instabuild = false; // anders slijt de kit niet (zoals in creative)
        hacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BYPASS_KIT.get()));
        Bypass.clear();
        LogicEvents.clear();
        return new Setup(owner, hacker, core, helper.absolutePos(corePos), reader, helper.absolutePos(reader), vaultAbs,
                panel, helper.absolutePos(panel), helper.absolutePos(sidePanel));
    }

    /**
     * Start de server en zet het diagram van het lab erop: een kaart van niveau 3 of hoger, of de code
     * 4711 op het voorpaneel, zet de kluis even open.
     */
    private static void startLab(GameTestHelper helper, Setup setup) {
        StarkTechGameTests.startServer(helper, new BlockPos(12, 3, 12), setup.core());
        StarkOs.open(setup.owner(), setup.coreAbs());
        String code = """
                when keycard("Lab door"):
                    if card.valid and card.level >= 3:
                        accept()
                        pulse("Vault door", 2)
                    else:
                        deny()

                when codepanel("Front door"):
                    if code == "4711":
                        accept()
                        pulse("Vault door", 2)
                    else:
                        deny()
                """;
        CompoundTag args = new CompoundTag();
        args.putString("old", "");
        args.putString("name", "Lab");
        args.put("graph", GraphConverter.toGraph(ScriptParser.parse(code), null).save());
        args.putString("source", code);
        StarkOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), "logic_save", args));
        check(setup.core().logic().program("Lab") != null, "Het diagram werd niet bewaard");
    }

    /** Het scherm van de hacker stuurt iets naar de server. */
    private static void send(Setup setup, String op, CompoundTag args) {
        Bypass.handle(setup.hacker(), new BypassPayload(setup.readerAbs(), op, args));
    }

    /** Het scherm van de hacker probeert een volgorde op het voorpaneel. */
    private static void guess(Setup setup, String code) {
        CompoundTag args = new CompoundTag();
        args.putString("code", code);
        Bypass.handle(setup.hacker(), new BypassPayload(setup.panelAbs(), "guess", args));
    }

    private static void chooseLevel(Setup setup, int level) {
        CompoundTag args = new CompoundTag();
        args.putInt("level", level);
        send(setup, "level", args);
    }

    private static void sendRotations(Setup setup, int[] rotations) {
        byte[] bytes = new byte[rotations.length];
        for (int i = 0; i < rotations.length; i++) {
            bytes[i] = (byte) rotations[i];
        }
        CompoundTag args = new CompoundTag();
        args.putByteArray("rot", bytes);
        send(setup, "solve", args);
    }

    /** Het raster dat de server voor deze hack maakte (hetzelfde als op het scherm van de hacker). */
    private static CircuitPuzzle puzzleOf(Setup setup, int level) {
        Long seed = Bypass.seedOf(setup.hacker());
        check(seed != null, "De hacker is niet aan het hacken");
        return CircuitPuzzle.generate(seed, level);
    }

    private static String lastEntry(SecurityCoreBlockEntity core) {
        var entries = core.log().entries();
        return entries.isEmpty() ? "" : entries.getLast().text();
    }

    /**
     * Een lezer hacken met de kit: een raster dat niet klopt telt niet (je mag verder puzzelen); een
     * goed opgelost raster van niveau 3 laat de lezer een geldige kaart van niveau 3 lezen, met een
     * verzonnen nummer dat niet op de lijst staat, en de kluisdeur gaat open. Een nagemaakte kaart van
     * niveau 2 geeft rood: het diagram beslist. Elke hack kost één gebruik van de kit.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "bypass_reader")
    public static void bypass_reader_fake_card(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper, true);
        helper.runAfterDelay(2, () -> {
            startLab(helper, setup);
            Bypass.start(setup.hacker(), setup.readerAbs(), InteractionHand.MAIN_HAND);
            check(Bypass.isHacking(setup.hacker()), "De hack begon niet");
            check(setup.hacker().getMainHandItem().getDamageValue() == 1, "De kit werd niet gebruikt");
            chooseLevel(setup, 3);
            CircuitPuzzle puzzle = puzzleOf(setup, 3);
            sendRotations(setup, puzzle.start());
            check(Bypass.isHacking(setup.hacker()), "Een raster dat niet klopt, stopte de hack");
            check(!lastEntry(setup.core()).contains("used card"), "Een raster dat niet klopt, liet de lezer een kaart lezen");
            sendRotations(setup, puzzle.solution());
            check(!Bypass.isHacking(setup.hacker()), "Na een goed raster liep de hack nog");
        });
        helper.runAfterDelay(22, () -> {
            helper.assertBlockProperty(setup.reader(), InputDeviceBlock.LIGHT, InputLight.OK);
            check(level.getBlockState(setup.vault()).getValue(RelayBlock.POWERED), "De kluisdeur ging niet open: "
                    + lastEntry(setup.core()));
            String read = setup.core().log().entries().stream().map(EventLog.Entry::text)
                    .filter(text -> text.contains("used card")).findFirst().orElse("");
            Matcher matcher = Pattern.compile("card #(\\d+) \\(level 3\\)").matcher(read);
            check(matcher.find(), "In het logboek staat geen kaart van niveau 3: " + read);
            int number = Integer.parseInt(matcher.group(1));
            check(number >= 2000 && setup.core().logic().cards().stream().noneMatch(card -> card.number() == number),
                    "De nagemaakte kaart heeft een nummer van de lijst: " + number);
        });
        // Nog een keer, nu met een kaart van niveau 2: die is te laag voor het diagram.
        helper.runAfterDelay(80, () -> {
            check(!level.getBlockState(setup.vault()).getValue(RelayBlock.POWERED), "De kluisdeur ging niet meer dicht");
            Bypass.start(setup.hacker(), setup.readerAbs(), InteractionHand.MAIN_HAND);
            chooseLevel(setup, 2);
            sendRotations(setup, puzzleOf(setup, 2).solution());
        });
        helper.runAfterDelay(100, () -> {
            helper.assertBlockProperty(setup.reader(), InputDeviceBlock.LIGHT, InputLight.DENIED);
            check(!level.getBlockState(setup.vault()).getValue(RelayBlock.POWERED), "Een kaart van niveau 2 opende de kluis");
            check(setup.hacker().getMainHandItem().getDamageValue() == 2, "Twee hacks kostten niet twee gebruiken");
            helper.succeed();
        });
    }

    /**
     * Mislukt (ALARM, of de tijd is om): rood lampje, een alarm in het logboek, de beveiliging jaagt op
     * de hacker, en die lezer laat hem een minuut niet meer proberen (dat kost hem dan ook niets).
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "bypass_alarm")
    public static void bypass_reader_alarm_and_lockout(GameTestHelper helper) {
        Setup setup = setup(helper, true);
        helper.runAfterDelay(2, () -> {
            startLab(helper, setup);
            Bypass.start(setup.hacker(), setup.readerAbs(), InteractionHand.MAIN_HAND);
            chooseLevel(setup, 1);
            CompoundTag args = new CompoundTag();
            args.putString("why", "alarm");
            send(setup, "fail", args);
            check(!Bypass.isHacking(setup.hacker()), "Na het alarm liep de hack nog");
            helper.assertBlockProperty(setup.reader(), InputDeviceBlock.LIGHT, InputLight.DENIED);
            boolean alarm = setup.core().log().entries().stream().anyMatch(entry -> entry.type() == EventLog.Type.ALARM
                    && entry.text().contains("Someone is tampering with Lab door") && entry.text().contains("bypass-hacker"));
            check(alarm, "Geen alarm in het logboek: " + lastEntry(setup.core()));
            check(setup.core().isHunted(setup.hacker().getUUID()), "De beveiliging jaagt niet op de hacker");
            check(Bypass.lockedFor(setup.hacker(), setup.readerAbs()) > 20 * (StarkConfig.Bypass.LOCKOUT_SECONDS - 2),
                    "De lezer laat de hacker meteen weer proberen");
            Bypass.start(setup.hacker(), setup.readerAbs(), InteractionHand.MAIN_HAND);
            check(!Bypass.isHacking(setup.hacker()), "Tijdens de wachttijd begon er toch een hack");
            check(setup.hacker().getMainHandItem().getDamageValue() == 1, "Een geweigerde hack kostte toch een gebruik");
            helper.succeed();
        });
    }

    /**
     * Zonder draaiende server (geen racks) valt er niets te bedotten: geen hack, en de kit blijft heel.
     * Van te ver weg (meer dan 6 blokken) ook niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "bypass_no_server")
    public static void bypass_needs_running_server(GameTestHelper helper) {
        Setup setup = setup(helper, false);
        helper.runAfterDelay(2, () -> {
            Bypass.start(setup.hacker(), setup.readerAbs(), InteractionHand.MAIN_HAND);
            check(!Bypass.isHacking(setup.hacker()), "Een lezer zonder server liet zich hacken");
            check(setup.hacker().getMainHandItem().getDamageValue() == 0, "Een lezer zonder server kostte een gebruik");
            setup.hacker().teleportTo(setup.readerAbs().getX() + 0.5, setup.readerAbs().getY(), setup.readerAbs().getZ() - 7.5);
            Bypass.start(setup.hacker(), setup.readerAbs(), InteractionHand.MAIN_HAND);
            check(!Bypass.isHacking(setup.hacker()), "Een hack begon van te ver weg");
            helper.succeed();
        });
    }


    // ================================================================ Een codepaneel kraken

    /**
     * De code van een paneel staat in je diagram. De kit vindt elke code == "..." onder when codepanel(...)
     * van dat paneel (ook andersom geschreven, en in een def die daar gebruikt wordt), niet die van een
     * ander paneel en niet die van een diagram dat uit staat. Het rekenwerk: de afdrukken van "4711",
     * hoeveel er op de goede plek staan, en hoeveel volgordes er nog kunnen.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "bypass_codes")
    public static void bypass_panel_codes_from_diagram(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(2, 3, 6), "bypass-codes");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        String code = """
                def open_back():
                    if code == "4711":
                        accept()

                when codepanel("Front door"):
                    if code == "1234":
                        accept()
                    elif player.trusted and "9999" == code:
                        accept()

                when codepanel("Back door"):
                    open_back()

                when keycard("Lab door"):
                    if card.valid:
                        accept()
                """;
        core.logic().put(null, new LogicProgram("Doors", true, GraphConverter.toGraph(ScriptParser.parse(code), null), code));
        String off = """
                when codepanel("Front door"):
                    if code == "5555":
                        accept()
                """;
        core.logic().put(null, new LogicProgram("Old", false, GraphConverter.toGraph(ScriptParser.parse(off), null), off));
        check(PanelCodes.forPanel(core, "Front door").equals(List.of("1234", "9999")),
                "Codes van het voorpaneel: " + PanelCodes.forPanel(core, "Front door"));
        check(PanelCodes.forPanel(core, "back door").equals(List.of("4711")),
                "Code uit de def van het achterpaneel: " + PanelCodes.forPanel(core, "back door"));
        check(PanelCodes.forPanel(core, "Lab door").isEmpty(), "Een lezer heeft geen code");
        // Het rekenwerk achter de afdrukken en de volgorde.
        check(Arrays.equals(PanelOrders.prints("4711"), new int[] {0, 2, 0, 0, 1, 0, 0, 1, 0, 0}),
                "De afdrukken van 4711: " + Arrays.toString(PanelOrders.prints("4711")));
        check(PanelOrders.rightPlaces("1147", "4711") == 0 && PanelOrders.rightPlaces("4117", "4711") == 2,
                "Op de goede plek klopt niet");
        int[] prints = PanelOrders.prints("4711");
        check(PanelOrders.count(prints, 4, List.of()) == 12, "4711 heeft 12 volgordes (4! / 2!)");
        List<PanelOrders.Try> tries = List.of(new PanelOrders.Try("4117", 2), new PanelOrders.Try("1147", 0));
        long left = PanelOrders.count(prints, 4, tries);
        String first = PanelOrders.firstPossible(prints, 4, tries);
        check(left >= 1 && left < 12 && first != null && PanelOrders.rightPlaces("4117", first) == 2
                && PanelOrders.rightPlaces("1147", first) == 0, "Na twee pogingen: " + left + " over, eerste " + first);
        helper.succeed();
    }

    /**
     * Een paneel kraken: je krijgt de lengte + 2 pogingen. Een volgorde met een cijfer zonder afdrukken
     * telt niet. Elke andere poging kost er één, en zet niets in het logboek. De goede volgorde: de kit
     * typt de code in, het diagram zegt accept() en de kluis gaat open.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "bypass_panel")
    public static void bypass_panel_fingerprints_and_order(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper, true);
        helper.runAfterDelay(2, () -> {
            startLab(helper, setup);
            Bypass.start(setup.hacker(), setup.panelAbs(), InteractionHand.MAIN_HAND);
            check(Bypass.isHacking(setup.hacker()), "De hack van het paneel begon niet");
            check(Bypass.triesLeftOf(setup.hacker()) == 4 + Math.round(StarkConfig.Bypass.ORDER_EXTRA_TRIES),
                    "Pogingen aan het begin: " + Bypass.triesLeftOf(setup.hacker()));
            int start = Bypass.triesLeftOf(setup.hacker());
            guess(setup, "4712");
            guess(setup, "471");
            check(Bypass.triesLeftOf(setup.hacker()) == start, "Een volgorde die niet kan, kostte een poging");
            guess(setup, "1147");
            guess(setup, "4117");
            check(Bypass.triesLeftOf(setup.hacker()) == start - 2, "Twee pogingen kostten niet twee pogingen");
            check(!lastEntry(setup.core()).contains("entered a code"), "Een poging kwam in het logboek");
            guess(setup, "4711");
            check(!Bypass.isHacking(setup.hacker()), "Na de goede code liep de hack nog");
        });
        helper.runAfterDelay(22, () -> {
            helper.assertBlockProperty(setup.panel(), InputDeviceBlock.LIGHT, InputLight.OK);
            check(level.getBlockState(setup.vault()).getValue(RelayBlock.POWERED), "De kluis ging niet open: "
                    + lastEntry(setup.core()));
            check(setup.core().log().entries().stream().anyMatch(entry -> entry.text().equals("bypass-hacker entered a code at Front door")),
                    "De gekraakte code staat niet als ingetypte code in het logboek");
            helper.succeed();
        });
    }

    /**
     * Te veel pogingen: het paneel slaat alarm en laat je een minuut niet meer proberen. Een paneel waarvan
     * geen diagram de code controleert, valt niet te kraken (en kost niets).
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "bypass_panel_alarm")
    public static void bypass_panel_out_of_tries(GameTestHelper helper) {
        Setup setup = setup(helper, true);
        helper.runAfterDelay(2, () -> {
            startLab(helper, setup);
            Bypass.start(setup.hacker(), setup.sidePanelAbs(), InteractionHand.MAIN_HAND);
            check(!Bypass.isHacking(setup.hacker()), "Een paneel zonder code liet zich kraken");
            check(setup.hacker().getMainHandItem().getDamageValue() == 0, "Een paneel zonder code kostte een gebruik");
            Bypass.start(setup.hacker(), setup.panelAbs(), InteractionHand.MAIN_HAND);
            int tries = Bypass.triesLeftOf(setup.hacker());
            for (int i = 0; i < tries; i++) {
                guess(setup, "1147");
            }
            check(!Bypass.isHacking(setup.hacker()), "Na alle pogingen liep de hack nog");
            helper.assertBlockProperty(setup.panel(), InputDeviceBlock.LIGHT, InputLight.DENIED);
            boolean alarm = setup.core().log().entries().stream().anyMatch(entry -> entry.type() == EventLog.Type.ALARM
                    && entry.text().contains("Someone is tampering with Front door"));
            check(alarm, "Geen alarm na te veel pogingen: " + lastEntry(setup.core()));
            check(Bypass.lockedFor(setup.hacker(), setup.panelAbs()) > 0, "Het paneel laat de hacker meteen weer proberen");
            helper.succeed();
        });
    }


    // ================================================================ Zakkenrollen

    /** Een slachtoffer met keycards in zijn zakken (en één in zijn hand), en een dief. */
    private record Pockets(ServerPlayer victim, ServerPlayer thief) {}

    /**
     * Het slachtoffer staat op (5, 3, 5) en kijkt naar het noorden; in zijn hand een keycard, in zijn
     * rugzak twee beschreven kaarten en een stapel van drie lege. De dief staat achter hem (zuidelijk).
     */
    private static Pockets pockets(GameTestHelper helper, String thiefName) {
        ServerPlayer victim = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 3, 5), "pocket-victim");
        victim.getAbilities().instabuild = false;
        victim.getInventory().clearContent();
        victim.getInventory().selected = 0;
        victim.getInventory().setItem(0, card(1001, 2, "Hand card"));
        victim.getInventory().setItem(10, card(1002, 3, "Lab access"));
        victim.getInventory().setItem(11, card(1003, 5, "Vault"));
        victim.getInventory().setItem(12, new ItemStack(ModItems.KEYCARD.get(), 3));
        look(victim, 180); // naar het noorden
        ServerPlayer thief = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 3, 5), thiefName);
        thief.getAbilities().instabuild = false;
        thief.getInventory().clearContent();
        Vec3 behind = helper.absoluteVec(new Vec3(5.5, 3, 7.0));
        thief.teleportTo(behind.x, behind.y, behind.z);
        look(thief, 180);
        Pickpocket.clear();
        return new Pockets(victim, thief);
    }

    private static ItemStack card(int number, int level, String label) {
        ItemStack stack = new ItemStack(ModItems.KEYCARD.get());
        KeycardItem.write(stack, new KeycardData("10.0.0.9", "pocket-net", number, level, label));
        return stack;
    }

    /** Laat een speler deze kant op kijken (0 = zuid, 180 = noord), ook met zijn hoofd. */
    private static void look(ServerPlayer player, float yaw) {
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.yRotO = yaw;
        player.setXRot(0);
    }

    /** Hoeveel keycards iemand bij zich heeft. */
    private static int keycards(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof KeycardItem) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Van voren lukt het niet ("hij zou je zien"). Van achteren opent de zak: drie kaarten (de kaart in
     * zijn hand zit niet in een zak). Trek je er één uit, dan heb jij hem en is hij hem kwijt (uit een
     * stapel één). Daarna moet je even wachten voor je het bij hem weer mag proberen.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "pickpocket")
    public static void pickpocket_from_behind(GameTestHelper helper) {
        Pockets p = pockets(helper, "pocket-thief");
        helper.runAfterDelay(2, () -> {
            Vec3 front = helper.absoluteVec(new Vec3(5.5, 3, 4.0));
            p.thief().teleportTo(front.x, front.y, front.z);
            check(!Pickpocket.tryStart(p.thief(), p.victim()), "Van voren kon de dief toch in de zak");
            Vec3 behind = helper.absoluteVec(new Vec3(5.5, 3, 7.0));
            p.thief().teleportTo(behind.x, behind.y, behind.z);
            check(Pickpocket.tryStart(p.thief(), p.victim()), "Van achteren ging de zak niet open");
            check(Pickpocket.cardsInPocket(p.thief()) == 3, "Kaarten in de zak: " + Pickpocket.cardsInPocket(p.thief()));
            int before = keycards(p.victim());
            CompoundTag args = new CompoundTag();
            args.putInt("index", 0);
            Pickpocket.handle(p.thief(), new PocketPayload(p.victim().getId(), "take", args));
            check(keycards(p.thief()) == 1, "De dief heeft geen kaart: " + keycards(p.thief()));
            check(keycards(p.victim()) == before - 1, "Het slachtoffer is niets kwijt");
            check(p.victim().getInventory().getItem(0).getItem() instanceof KeycardItem, "De kaart uit zijn hand is weg");
            check(!Pickpocket.isStealing(p.thief()), "Na het pikken stond de zak nog open");
            check(!Pickpocket.tryStart(p.thief(), p.victim()) && Pickpocket.waitFor(p.thief(), p.victim()) > 0,
                    "Meteen nog een keer mocht");
            check(!p.thief().hasEffect(MobEffects.GLOWING), "Een gelukte poging liet de dief oplichten");
            helper.succeed();
        });
    }

    /**
     * Draait het slachtoffer zich om en ziet hij de dief, dan is die betrapt: de zak gaat dicht en de
     * dief licht op. Dat gebeurt ook als de meter op het scherm vol is ("caught").
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "pickpocket_caught")
    public static void pickpocket_caught_when_seen(GameTestHelper helper) {
        Pockets p = pockets(helper, "pocket-thief");
        ServerPlayer second = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 3, 5), "pocket-thief-2");
        Vec3 behind = helper.absoluteVec(new Vec3(6.2, 3, 6.6));
        second.teleportTo(behind.x, behind.y, behind.z);
        helper.runAfterDelay(2, () -> {
            check(Pickpocket.tryStart(p.thief(), p.victim()), "De zak ging niet open");
            look(p.victim(), 0); // hij draait zich om, naar het zuiden
        });
        helper.runAfterDelay(5, () -> {
            check(!Pickpocket.isStealing(p.thief()), "Hij draaide zich om, maar de zak stond nog open");
            check(p.thief().hasEffect(MobEffects.GLOWING), "De betrapte dief licht niet op");
            look(p.victim(), 180);
            check(Pickpocket.tryStart(second, p.victim()), "De tweede dief kon niet in de zak");
            Pickpocket.handle(second, PocketPayload.of(p.victim().getId(), "caught"));
            check(!Pickpocket.isStealing(second) && second.hasEffect(MobEffects.GLOWING),
                    "Een volle meter betrapte de tweede dief niet");
            check(keycards(second) == 0, "De betrapte dief kreeg toch een kaart");
            helper.succeed();
        });
    }

    /**
     * Loopt het slachtoffer weg, dan houdt het op (zonder betrapt te zijn). Spelers in creative hebben
     * niets in hun zakken te pikken.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "pickpocket_away")
    public static void pickpocket_victim_walks_away(GameTestHelper helper) {
        Pockets p = pockets(helper, "pocket-thief");
        helper.runAfterDelay(2, () -> {
            check(Pickpocket.tryStart(p.thief(), p.victim()), "De zak ging niet open");
            Vec3 far = helper.absoluteVec(new Vec3(5.5, 3, 1.0));
            p.victim().teleportTo(far.x, far.y, far.z);
        });
        helper.runAfterDelay(5, () -> {
            check(!Pickpocket.isStealing(p.thief()), "Hij liep weg, maar de zak stond nog open");
            check(!p.thief().hasEffect(MobEffects.GLOWING), "Weglopen telde als betrapt");
            ServerPlayer creative = helper.makeMockServerPlayerInLevel();
            Vec3 at = helper.absoluteVec(new Vec3(5.5, 3, 5.5));
            creative.teleportTo(at.x, at.y, at.z);
            look(creative, 180);
            check(!creative.isCreative() || !Pickpocket.tryStart(p.thief(), creative), "Een speler in creative werd gezakkenrold");
            helper.succeed();
        });
    }
}
