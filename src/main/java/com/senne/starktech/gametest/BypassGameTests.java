package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.bypass.Bypass;
import com.senne.starktech.bypass.BypassBalance;
import com.senne.starktech.bypass.CircuitPuzzle;
import com.senne.starktech.logic.InputDeviceBlock;
import com.senne.starktech.logic.InputDeviceBlockEntity;
import com.senne.starktech.logic.InputLight;
import com.senne.starktech.logic.LogicEvents;
import com.senne.starktech.logic.program.GraphConverter;
import com.senne.starktech.logic.program.ScriptParser;
import com.senne.starktech.network.BypassPayload;
import com.senne.starktech.network.OsActionPayload;
import com.senne.starktech.redstone.Relay;
import com.senne.starktech.redstone.RelayBlock;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.os.EventLog;
import com.senne.starktech.security.os.StarkOs;
import java.util.Arrays;
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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor inbreken (hoofdstuk 12 van het plan): de Bypass Kit en het stroomcircuit van een
 * keycardlezer.
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
                check(puzzle.columns() == BypassBalance.CIRCUIT_COLUMNS[index] && puzzle.rows() == BypassBalance.CIRCUIT_ROWS[index],
                        which + "het raster is " + puzzle.columns() + " x " + puzzle.rows());
                check(puzzle.alarms().size() == BypassBalance.CIRCUIT_ALARMS[index],
                        which + puzzle.alarms().size() + " ALARM-punten in plaats van " + BypassBalance.CIRCUIT_ALARMS[index]);
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

    /** Een kern met eigenaar en server, een kluisdeur, een lezer "Lab door", en een hacker met een kit. */
    private record Setup(ServerPlayer owner, ServerPlayer hacker, SecurityCoreBlockEntity core, BlockPos coreAbs,
                         BlockPos reader, BlockPos readerAbs, BlockPos vault) {}

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
                Relay.DEFAULT_GROUP, helper.getLevel().dimension(), vaultAbs)));
        BlockPos reader = new BlockPos(8, 3, 8);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(reader)).setName("Lab door");
        ServerPlayer hacker = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 3, 6), "bypass-hacker");
        hacker.getAbilities().instabuild = false; // anders slijt de kit niet (zoals in creative)
        hacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BYPASS_KIT.get()));
        Bypass.clear();
        LogicEvents.clear();
        return new Setup(owner, hacker, core, helper.absolutePos(corePos), reader, helper.absolutePos(reader), vaultAbs);
    }

    /** Start de server en zet het diagram van de lab-deur erop: niveau 3 of hoger = de kluis even open. */
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
            check(Bypass.lockedFor(setup.hacker(), setup.readerAbs()) > 20 * (BypassBalance.LOCKOUT_SECONDS - 2),
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
}
