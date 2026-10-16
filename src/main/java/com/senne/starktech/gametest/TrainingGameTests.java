package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.logic.program.GraphConverter;
import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.logic.program.ProgramKind;
import com.senne.starktech.logic.program.ScriptParser;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.os.EventLog;
import com.senne.starktech.training.Shots;
import com.senne.starktech.training.Training;
import com.senne.starktech.training.TrainingReport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor trainingen zoals Domino squad (hoofdstuk 26): wat een treffer doet (trainingsschoten of echt), wanneer
 * een training stopt, het rapport, en de diagrammen die {@code when training_started/training_ended} horen.
 */
@GameTestHolder(StarkTech.MODID)
@PrefixGameTestTemplate(false)
public final class TrainingGameTests {
    private TrainingGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Hoe lang het aftellen duurt, plus een beetje. */
    private static int afterCountdown() {
        return Math.round(com.senne.starktech.config.StarkConfig.Training.COUNTDOWN_SECONDS * 20) + 5;
    }

    /** Een serverdiagram uit deze code (de code moet kloppen). */
    private static LogicProgram diagram(String name, String code) {
        return new LogicProgram(name, true, GraphConverter.toGraph(ScriptParser.parse(code, ProgramKind.SERVER), null), code,
                ProgramKind.SERVER);
    }

    private static boolean logged(SecurityCoreBlockEntity core, EventLog.Type type, String text) {
        return core.log().entries().stream().anyMatch(entry -> entry.type() == type && entry.text().equals(text));
    }

    /**
     * Trainingsschoten: een treffer doet geen schade, maar kost een leven (en maakt even trager). Een tweede treffer
     * vlak erna telt niet mee (één schot is één leven); een seconde later wel.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "training")
    public static void training_shots_do_no_damage_but_count(GameTestHelper helper) {
        ServerPlayer trainee = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 2, 6), "trainee-shots");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), trainee);
        helper.runAfterDelay(5, () -> check(Training.start(helper.getLevel(), core, trainee,
                new Training.Settings("Kamino", Training.Mode.TRAINING_SHOTS, 3, 0), null) == null, "De training begon niet"));
        helper.runAfterDelay(5 + afterCountdown(), () -> {
            Training.Session session = Training.of(trainee.getUUID());
            check(session != null && session.running(), "Na het aftellen is de training niet bezig");
            float health = trainee.getHealth();
            check(Shots.hit(helper.getLevel(), trainee, trainee.damageSources().generic(), 5, "Test turret"), "Het schot raakte niet");
            check(trainee.getHealth() == health, "Een trainingsschot deed toch schade");
            check(session.hits() == 1 && session.lives() == 2, "Na een treffer: " + session.hits() + " hits, " + session.lives() + " lives");
            check(trainee.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Na een treffer is de trainee niet trager");
            Shots.hit(helper.getLevel(), trainee, trainee.damageSources().generic(), 5, "Test turret");
            check(session.hits() == 1, "Een tweede treffer vlak erna telde toch mee");
        });
        helper.runAfterDelay(5 + afterCountdown() + 30, () -> {
            Training.Session session = Training.of(trainee.getUUID());
            Shots.hit(helper.getLevel(), trainee, trainee.damageSources().generic(), 5, "Test turret");
            check(session != null && session.hits() == 2 && session.lives() == 1, "Een seconde later telde de treffer niet");
            Training.end(helper.getLevel(), session, Training.Result.STOPPED);
            helper.succeed();
        });
    }

    /**
     * Lethal: een treffer doet echte schade en kost een leven. Met 1 leven is de training dan voorbij: de diagrammen
     * horen het (training_started en training_ended, met trainee en hits), en de kern bewaart het rapport.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "training_lethal")
    public static void lethal_training_hurts_and_ends(GameTestHelper helper) {
        ServerPlayer trainee = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 2, 6), "trainee-lethal");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), trainee);
        BlockPos rack = new BlockPos(12, 3, 3);
        StarkTechGameTests.placeServer(helper, rack, core);
        String name = trainee.getGameProfile().getName();
        helper.runAfterDelay(25, () -> {
            StarkTechGameTests.startServer(helper, rack, core);
            core.logic().put(null, diagram("Watch", """
                    when training_started("Kamino"):
                        log("start " + trainee)

                    when training_ended("Kamino"):
                        if not passed:
                            log("ended " + trainee + " " + hits + " " + lives)
                    """));
            check(Training.start(helper.getLevel(), core, trainee,
                    new Training.Settings("Kamino", Training.Mode.LETHAL, 1, 0), null) == null, "De training begon niet");
        });
        helper.runAfterDelay(25 + afterCountdown() + 10, () -> {
            check(logged(core, EventLog.Type.LOGIC, "start " + name), "when training_started liep niet");
            float health = trainee.getHealth();
            check(Shots.hit(helper.getLevel(), trainee, trainee.damageSources().generic(), 4, "Guard 7"), "Het schot raakte niet");
            check(trainee.getHealth() < health, "Een lethal schot deed geen schade");
            check(Training.of(trainee.getUUID()) == null, "Met 0 levens is de training niet voorbij");
            check(!core.trainingReports().isEmpty(), "De kern bewaarde geen rapport");
            TrainingReport report = core.trainingReports().getLast();
            check(report.result() == Training.Result.OUT_OF_LIVES && report.hits() == 1 && report.hitsBy().equals("Guard 7 x1"),
                    "Het rapport klopt niet: " + report.line());
        });
        helper.runAfterDelay(25 + afterCountdown() + 30, () -> {
            check(logged(core, EventLog.Type.LOGIC, "ended " + name + " 1 0"), "when training_ended liep niet (of zonder trainee/hits/lives)");
            helper.succeed();
        });
    }

    /**
     * Een tijdslimiet zonder Goal-blok: wie het volhoudt, is geslaagd. Een tweede training bij dezelfde kern kan niet
     * tegelijk, en de trainee kan niet in twee trainingen tegelijk.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "training_time")
    public static void training_time_limit_passes_without_goal(GameTestHelper helper) {
        ServerPlayer trainee = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 2, 6), "trainee-time");
        ServerPlayer other = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 2, 6), "trainee-other");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), trainee);
        helper.runAfterDelay(5, () -> {
            check(Training.start(helper.getLevel(), core, trainee,
                    new Training.Settings("Endurance", Training.Mode.TRAINING_SHOTS, 2, 2), null) == null, "De training begon niet");
            check(Training.start(helper.getLevel(), core, other, Training.Settings.DEFAULT, null) != null,
                    "Een tweede training bij dezelfde kern begon toch");
        });
        helper.runAfterDelay(5 + afterCountdown() + 50, () -> {
            check(Training.of(trainee.getUUID()) == null, "Na 2 seconden is de training niet voorbij");
            TrainingReport report = core.trainingReports().getLast();
            check(report.result() == Training.Result.SURVIVED && report.result().passed() && report.lives() == 2,
                    "Het rapport klopt niet: " + report.line());
            helper.succeed();
        });
    }
}
