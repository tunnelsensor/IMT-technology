package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.Library;
import com.senne.imttech.logic.program.LogicException;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.network.OsActionPayload;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.EventLog;
import com.senne.imttech.security.os.SecurityOs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Hoofdstuk 138: een serverdiagram kijkt je logboek na ({@code when logged:} met {@code log.text} en {@code log.kind}) en
 * markeert verdachte regels met {@code flag("...")}: rood in Logs, en I.M.T waarschuwt (jouw wens: "zorg ook dat je een
 * script kunt maken waar je logs door kunt flaggen").
 * <p>
 * In de bestaande batch "security_os", dus er blijven 192 batchnamen.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class LogWatchGameTests {
    private LogWatchGameTests() {}

    private static final String ARENA = "empty";

    /** Markeert elk fout wachtwoord (met de regel erin, via een f-tekst) en elke andere regel met "seen". */
    private static final String WATCH = """
            when logged:
                if log.kind == "login_fail":
                    flag(f"Wrong password: {log.text}")
                else:
                    flag("seen")
            """;

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Een diagram bewaren zoals de app Logic dat doet. */
    private static void save(ServerPlayer player, BlockPos coreAbs, String name, String code) {
        CompoundTag args = new CompoundTag();
        args.putString("old", "");
        args.putString("name", name);
        args.put("graph", GraphConverter.toGraph(ScriptParser.parseUnchecked(code, ProgramKind.SERVER, Library.EMPTY), null).save());
        args.putString("source", code);
        args.putString("kind", ProgramKind.SERVER.name());
        SecurityOs.handleAction(player, new OsActionPayload(coreAbs, "logic_save", args));
    }

    /** Wat Check over deze code zegt, of null als hij klopt. */
    private static @Nullable String problem(String code, ProgramKind kind) {
        try {
            ScriptParser.parse(code, kind);
            return null;
        } catch (LogicException e) {
            return e.describe();
        }
    }

    private static @Nullable EventLog.Entry line(SecurityCoreBlockEntity core, String text) {
        for (EventLog.Entry entry : core.log().entries()) {
            if (entry.text().equals(text)) {
                return entry;
            }
        }
        return null;
    }

    private static String flagOf(SecurityCoreBlockEntity core, String text) {
        EventLog.Entry entry = line(core, text);
        return entry == null ? "(no such line)" : entry.flag();
    }

    /**
     * Check: flag(...) alleen in een when logged:, alleen in een serverdiagram, log.text alleen na when logged:; een oud
     * programma met een naam flag of een def flag() blijft werken (die woorden zijn niet bezet).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "security_os")
    public static void log_watch_language(GameTestHelper helper) {
        String outside = problem("when every(5):\n    flag(\"x\")\n", ProgramKind.SERVER);
        check(outside != null && outside.contains("only works inside when logged"),
                "flag(...) buiten when logged: werd niet geweigerd: " + outside);
        check(problem("when logged:\n    flag(\"x\")\n", ProgramKind.ROBOT) != null,
                "when logged: werd in een robotprogramma aangenomen");
        check(problem("when every(5):\n    log(log.text)\n", ProgramKind.SERVER) != null,
                "log.text werd buiten when logged: aangenomen");
        String inside = problem("when logged:\n    if \"x\" == log.kind:\n        flag(f\"{log.kind}: {log.text}\")\n",
                ProgramKind.SERVER);
        check(inside == null, "Een geldige when logged: met een f-tekst in flag(...) werd geweigerd: " + inside);
        String oldName = problem("flag = 0\nwhen every(5):\n    flag = flag + 1\n    log(\"count \" + flag)\n", ProgramKind.SERVER);
        check(oldName == null, "Een oud diagram met een naam flag werkt niet meer: " + oldName);
        String oldDef = problem("def flag():\n    log(\"x\")\n\nwhen every(5):\n    flag()\n", ProgramKind.SERVER);
        check(oldDef == null, "Een oud diagram met een def flag() werkt niet meer: " + oldDef);
        helper.succeed();
    }

    /**
     * In het spel: een fout wachtwoord van ver wordt gemarkeerd met de regel erin (f-tekst); een SYSTEM-regel met "seen";
     * regels van de soort LOGIC en ALARM starten when logged: niet. I.M.T waarschuwt één keer (niet bij elke regel), de
     * desktop krijgt de markering, en bewaren en laden houdt hem. Veel regels tegelijk: hooguit [logic] loggedPerSecond
     * worden nagekeken, met een melding.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "security_os")
    public static void log_watch_flags_lines(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 3, 4), "lw-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(14, 3, 14), "lw-stranger");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos rack = new BlockPos(4, 3, 2);
        ImtTechGameTests.placeServer(helper, rack, core);
        BlockPos abs = core.getBlockPos();
        String wrong = "Wrong password from lw-stranger (remote)";
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    ImtTechGameTests.startServer(helper, rack, core);
                    SecurityOs.open(owner, abs);
                    core.setPassword("vault42", level.random);
                    save(owner, abs, "Watch", WATCH);
                    check(core.logic().program("Watch") != null && core.logic().program("Watch").enabled(),
                            "Het diagram Watch werd niet bewaard");
                })
                .thenExecuteAfter(2, () -> {
                    String[] said = {""};
                    check(SecurityOs.connect(stranger, core.ip(), "wrong", message -> said[0] = message) == null,
                            "Een fout wachtwoord liet de vreemde binnen");
                    core.logEvent(EventLog.Type.LOGIC, "probe logic");
                    core.logEvent(EventLog.Type.ALARM, "probe alarm");
                    core.logEvent(EventLog.Type.SYSTEM, "probe system");
                })
                .thenWaitUntil(() -> check(flagOf(core, wrong).equals("Wrong password: " + wrong),
                        "Het foute wachtwoord werd niet gemarkeerd met de regel erin: " + flagOf(core, wrong)))
                .thenWaitUntil(() -> check(flagOf(core, "probe system").equals("seen"),
                        "De SYSTEM-regel werd niet nagekeken: " + flagOf(core, "probe system")))
                .thenExecute(() -> {
                    // De runs gaan in volgorde: is de SYSTEM-regel (als laatste geschreven) nagekeken, dan zouden de LOGIC- en
                    // ALARM-regels het ook al zijn, als ze when logged: startten.
                    check(flagOf(core, "probe logic").isEmpty() && flagOf(core, "probe alarm").isEmpty(),
                            "Een LOGIC- of ALARM-regel startte when logged:");
                    long alerts = core.messages().stream().filter(message -> message.text().startsWith("Flagged on")).count();
                    check(alerts == 1, "I.M.T waarschuwde " + alerts + " keer in plaats van één keer");
                    CompoundTag shown = SecurityOs.snapshot(core, level, SecurityOs.session(owner), true);
                    check(shown.getList("log", Tag.TAG_COMPOUND).stream().anyMatch(tag -> ((CompoundTag) tag).getString("f")
                            .equals("Wrong password: " + wrong)), "Logs op de desktop krijgt de markering niet");
                    EventLog loaded = EventLog.load(core.log().save());
                    check(loaded.entries().stream().anyMatch(entry -> entry.text().equals(wrong)
                            && entry.flag().equals("Wrong password: " + wrong)), "Na bewaren en laden is de markering weg");
                    // Veel regels tegelijk: when logged: kijkt er hooguit loggedPerSecond na (10), en zegt het.
                    for (int i = 0; i < 25; i++) {
                        core.logEvent(EventLog.Type.SYSTEM, "burst " + i);
                    }
                })
                .thenExecuteAfter(15, () -> {
                    long watched = core.log().entries().stream()
                            .filter(entry -> entry.text().startsWith("burst ") && !entry.flag().isEmpty()).count();
                    check(watched > 0 && watched <= 10, watched + " van de 25 regels tegelijk werden nagekeken (hooguit 10)");
                    check(core.log().entries().stream().anyMatch(entry -> entry.type() == EventLog.Type.LOGIC
                            && entry.text().contains("Too many new log lines")), "Geen melding dat when logged: regels oversloeg");
                })
                .thenSucceed();
    }
}
