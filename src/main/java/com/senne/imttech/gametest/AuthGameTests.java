package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.AuthRequests;
import com.senne.imttech.logic.InputDeviceBlock;
import com.senne.imttech.logic.InputDeviceBlockEntity;
import com.senne.imttech.logic.InputLight;
import com.senne.imttech.logic.LogicEvents;
import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.network.OsActionPayload;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.EventLog;
import com.senne.imttech.security.os.SecurityOs;
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
 * Tests voor de autorisatiecodes en de Auth Console (hoofdstuk 49, 4 november): de code van de kern draait elke paar
 * minuten, het codepaneel kent hem ({@code auth.valid}, {@code auth.old}), de vorige code werkt alleen als dat aan
 * staat (en komt altijd in het logboek); een verzoek bij de console wordt op de desktop geaccepteerd of geweigerd,
 * of loopt af, en je diagram hoort het met {@code when request("Gate")} en {@code request.accepted}.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class AuthGameTests {
    private AuthGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    private record Setup(ServerPlayer owner, SecurityCoreBlockEntity core, BlockPos coreAbs, BlockPos rack) {}

    /** Een eigenaar met een kern en een server (nog niet gestart: eerst de apparaten neerzetten, zie {@link #start}). */
    private static Setup setup(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "auth-owner");
        BlockPos corePos = new BlockPos(2, 3, 2);
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, corePos, owner);
        BlockPos rack = new BlockPos(12, 3, 12);
        ImtTechGameTests.placeServer(helper, rack, core);
        return new Setup(owner, core, helper.absolutePos(corePos), rack);
    }

    /** De server starten (hij kent dan de apparaten die er al staan), de desktop openen en een diagram bewaren. */
    private static void start(GameTestHelper helper, Setup setup, String program, String code) {
        ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
        SecurityOs.open(setup.owner(), setup.coreAbs());
        CompoundTag args = new CompoundTag();
        args.putString("old", "");
        args.putString("name", program);
        args.put("graph", GraphConverter.toGraph(ScriptParser.parse(code), null).save());
        args.putString("source", code);
        SecurityOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), "logic_save", args));
        check(setup.core().logic().program(program) != null, "Het diagram \"" + program + "\" zou bewaard moeten zijn: " + lastLog(setup.core()));
    }

    private static String lastLog(SecurityCoreBlockEntity core) {
        var entries = core.log().entries();
        return entries.isEmpty() ? "" : entries.getLast().type() + ": " + entries.getLast().text();
    }

    /** Hoe vaak {@code log(text)} uit een diagram in het logboek staat. */
    private static long logged(SecurityCoreBlockEntity core, String text) {
        return core.log().entries().stream().filter(entry -> entry.type() == EventLog.Type.LOGIC && entry.text().equals(text)).count();
    }

    /** Staat er een regel (van een lezer, paneel of console) met deze tekst erin? */
    private static boolean entryLogged(SecurityCoreBlockEntity core, String part) {
        return core.log().entries().stream().anyMatch(entry -> entry.type() == EventLog.Type.ENTRY && entry.text().contains(part));
    }

    /**
     * De code van de kern: zoveel cijfers als ingesteld (een andere lengte geeft meteen een nieuwe), na authMinutes een
     * nieuwe (de oude is dan "de vorige"). Op het paneel: de huidige code is auth.valid; de vorige is auth.old en alleen
     * geldig als "old codes" aan staat, maar komt altijd in het logboek; een verkeerde code is fout.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "auth")
    public static void auth_codes_rotate_and_open_the_panel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        SecurityCoreBlockEntity core = setup.core();
        core.authTick(level, level.getGameTime());
        core.setAuthSettings(6, 5, false);
        String code = core.authCode();
        check(code.length() == 6 && code.chars().allMatch(Character::isDigit), "De code zou zes cijfers moeten zijn: " + code);
        core.setAuthSettings(4, 5, false);
        check(core.authCode().length() == 4 && !core.authCode().equals(code),
                "Een andere lengte geeft meteen een nieuwe code van vier cijfers: " + core.authCode());
        String first = core.authCode();
        core.authTick(level, core.authSince() + 5 * 1200 - 1);
        check(core.authCode().equals(first), "Voor de tijd om is, blijft de code dezelfde");
        core.authTick(level, core.authSince() + 5 * 1200);
        check(!core.authCode().equals(first) && core.authPrevious().equals(first), "Na vijf minuten een nieuwe code; de oude is de vorige");
        BlockPos panel = new BlockPos(8, 3, 8);
        helper.setBlock(panel, ModBlocks.CODE_PANEL.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(panel)).setName("Vault");
        BlockPos panelAbs = helper.absolutePos(panel);
        helper.runAfterDelay(2, () -> start(helper, setup, "Vault", """
                when codepanel("Vault"):
                    if auth.valid:
                        accept()
                        log("auth ok")
                    else:
                        deny()
                        log("auth bad")
                    if auth.old:
                        log("old code")
                """));
        helper.runAfterDelay(10, () -> {
            LogicEvents.clear();
            LogicEvents.codePanel(level, panelAbs, setup.owner(), core.authCode());
        });
        helper.runAfterDelay(25, () -> {
            helper.assertBlockProperty(panel, InputDeviceBlock.LIGHT, InputLight.OK);
            check(logged(core, "auth ok") == 1 && logged(core, "old code") == 0, "De huidige code zou goed moeten zijn: " + lastLog(core));
        });
        helper.runAfterDelay(50, () -> {
            LogicEvents.clear();
            LogicEvents.codePanel(level, panelAbs, setup.owner(), core.authPrevious());
        });
        helper.runAfterDelay(65, () -> {
            helper.assertBlockProperty(panel, InputDeviceBlock.LIGHT, InputLight.DENIED);
            check(logged(core, "auth bad") == 1 && logged(core, "old code") == 1,
                    "De vorige code zou fout moeten zijn, maar wel auth.old: " + lastLog(core));
            check(entryLogged(core, "used the previous authorization code"), "De vorige code zou in het logboek moeten staan");
            core.setAuthSettings(4, 5, true);
        });
        helper.runAfterDelay(90, () -> {
            LogicEvents.clear();
            LogicEvents.codePanel(level, panelAbs, setup.owner(), core.authPrevious());
        });
        helper.runAfterDelay(105, () -> {
            helper.assertBlockProperty(panel, InputDeviceBlock.LIGHT, InputLight.OK);
            check(logged(core, "auth ok") == 2 && logged(core, "old code") == 2,
                    "Met old codes aan zou de vorige code goed moeten zijn: " + lastLog(core));
        });
        helper.runAfterDelay(130, () -> {
            LogicEvents.clear();
            LogicEvents.codePanel(level, panelAbs, setup.owner(), "99");
        });
        helper.runAfterDelay(145, () -> {
            helper.assertBlockProperty(panel, InputDeviceBlock.LIGHT, InputLight.DENIED);
            check(logged(core, "auth bad") == 2, "Een verkeerde code blijft fout: " + lastLog(core));
            helper.succeed();
        });
    }

    /** Het antwoord van de eigenaar op zijn desktop (de app Requests). */
    private static void answer(Setup setup, int id, boolean accepted) {
        CompoundTag args = new CompoundTag();
        args.putInt("id", id);
        args.putBoolean("ok", accepted);
        SecurityOs.handleAction(setup.owner(), new OsActionPayload(setup.coreAbs(), "req_answer", args));
    }

    /**
     * Een bezoeker drukt op de Auth Console: het verzoek wacht (oranje), staat in het logboek, en een tweede van hem telt
     * niet. De eigenaar accepteert: groen, en het diagram ziet request.accepted (met player.name). Weigeren: rood, en
     * request.accepted is false. Niemand antwoordt: na authRequestSeconds geweigerd.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "auth")
    public static void auth_console_requests_are_accepted_denied_or_time_out(GameTestHelper helper) {
        float before = ImtConfig.Security.AUTH_REQUEST_SECONDS;
        ImtConfig.Security.AUTH_REQUEST_SECONDS = 2.0f; // 40 ticks
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        SecurityCoreBlockEntity core = setup.core();
        ServerPlayer visitor = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(9, 3, 9), "auth-visitor");
        BlockPos console = new BlockPos(8, 3, 8);
        helper.setBlock(console, ModBlocks.AUTH_CONSOLE.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(console)).setName("Gate");
        BlockPos consoleAbs = helper.absolutePos(console);
        AuthRequests.clear();
        helper.runAfterDelay(2, () -> start(helper, setup, "Gate", """
                when request("Gate"):
                    if request.accepted:
                        log("open for " + player.name)
                    else:
                        log("closed for " + player.name)
                """));
        helper.runAfterDelay(10, () -> {
            check(AuthRequests.ask(level, consoleAbs, visitor), "Het verzoek zou moeten lopen");
            check(!AuthRequests.ask(level, consoleAbs, visitor), "Een tweede verzoek van dezelfde speler telt niet zolang het eerste loopt");
            check(AuthRequests.pendingAt(level, core).size() == 1, "Eén verzoek zou moeten wachten");
            helper.assertBlockProperty(console, InputDeviceBlock.LIGHT, InputLight.BUSY);
            check(entryLogged(core, "auth-visitor asked for access at Gate"), "Het verzoek zou in het logboek moeten staan: " + lastLog(core));
            answer(setup, AuthRequests.pendingAt(level, core).getFirst().id(), true);
        });
        helper.runAfterDelay(20, () -> {
            check(AuthRequests.pendingAt(level, core).isEmpty(), "Na het antwoord zou het verzoek weg moeten zijn");
            helper.assertBlockProperty(console, InputDeviceBlock.LIGHT, InputLight.OK);
            check(logged(core, "open for auth-visitor") == 1, "Accept zou het diagram moeten starten met request.accepted: " + lastLog(core));
            check(entryLogged(core, "auth-owner accepted auth-visitor at Gate"), "Het antwoord zou in het logboek moeten staan");
            check(AuthRequests.ask(level, consoleAbs, visitor), "Een nieuw verzoek zou moeten lopen");
            answer(setup, AuthRequests.pendingAt(level, core).getFirst().id(), false);
        });
        helper.runAfterDelay(30, () -> {
            helper.assertBlockProperty(console, InputDeviceBlock.LIGHT, InputLight.DENIED);
            check(logged(core, "closed for auth-visitor") == 1, "Deny zou het diagram moeten starten zonder request.accepted: " + lastLog(core));
            check(AuthRequests.ask(level, consoleAbs, visitor), "Een derde verzoek zou moeten lopen");
        });
        // Na 40 ticks (t = 70) is de tijd om: eerst het lampje (voor de flits van t = 50 het op t = 80 uitzet), dan het diagram.
        helper.runAfterDelay(76, () -> {
            check(AuthRequests.pendingAt(level, core).isEmpty(), "Na de tijd zou het verzoek weg moeten zijn");
            check(entryLogged(core, "Nobody answered auth-visitor at Gate"), "Geen antwoord zou in het logboek moeten staan: " + lastLog(core));
            helper.assertBlockProperty(console, InputDeviceBlock.LIGHT, InputLight.DENIED);
        });
        helper.runAfterDelay(90, () -> {
            check(logged(core, "closed for auth-visitor") == 2, "Geen antwoord telt als geweigerd: " + lastLog(core));
            ImtConfig.Security.AUTH_REQUEST_SECONDS = before;
            helper.succeed();
        });
    }
}
