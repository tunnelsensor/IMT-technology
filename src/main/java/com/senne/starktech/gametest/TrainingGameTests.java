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
     * Het Training Start-blok: een vreemde kan geen training beginnen (en de instellingen niet veranderen), de eigenaar
     * wel (hij is dan de trainee), en een tweede klik stopt hem. Wat hij instelt, bewaart het blok.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "training_start")
    public static void training_start_block_only_for_trusted(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 2, 8), "start-owner");
        ServerPlayer stranger = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 2, 6), "start-stranger");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos block = new BlockPos(6, 2, 6);
        helper.setBlock(block, com.senne.starktech.registry.ModBlocks.TRAINING_START.get());
        BlockPos abs = helper.absolutePos(block);
        var start = (com.senne.starktech.training.TrainingStartBlockEntity) helper.getBlockEntity(block);
        helper.runAfterDelay(5, () -> {
            Training.Settings waves = new Training.Settings("Waves", Training.Mode.LETHAL, 3, 60);
            com.senne.starktech.training.TrainingBlocks.handleAction(stranger,
                    new com.senne.starktech.network.TrainingActionPayload(abs, waves.save()));
            check(start.settings().equals(Training.Settings.DEFAULT), "Een vreemde veranderde de instellingen");
            com.senne.starktech.training.TrainingBlocks.handleAction(owner,
                    new com.senne.starktech.network.TrainingActionPayload(abs, waves.save()));
            check(start.settings().equals(waves), "De eigenaar kon de instellingen niet bewaren: " + start.settings());

            com.senne.starktech.training.TrainingBlocks.useStart(stranger, abs);
            check(Training.at(helper.getLevel(), core.getBlockPos()) == null, "Een vreemde begon een training");
            com.senne.starktech.training.TrainingBlocks.useStart(owner, abs);
            Training.Session session = Training.at(helper.getLevel(), core.getBlockPos());
            check(session != null && session.trainee().equals(owner.getUUID()) && session.settings().name().equals("Waves")
                    && session.lives() == 3, "De eigenaar begon geen training (met zijn instellingen)");
            com.senne.starktech.training.TrainingBlocks.useStart(owner, abs);
            check(Training.at(helper.getLevel(), core.getBlockPos()) == null, "Een tweede klik stopte de training niet");
            check(core.trainingReports().getLast().result() == Training.Result.STOPPED, "Het rapport zegt niet dat hij gestopt is");
            helper.succeed();
        });
    }

    /**
     * Het Training Goal-blok: stapt de trainee erop, dan is hij geslaagd. Staat er een doel in de basis, dan is de tijd
     * om wel mislukt (hij moest het doel halen).
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "training_goal")
    public static void training_goal_ends_with_success(GameTestHelper helper) {
        ServerPlayer trainee = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 2, 6), "goal-trainee");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), trainee);
        BlockPos goal = new BlockPos(8, 1, 6);
        helper.setBlock(goal, com.senne.starktech.registry.ModBlocks.TRAINING_GOAL.get());
        helper.runAfterDelay(5, () -> check(Training.start(helper.getLevel(), core, trainee,
                new Training.Settings("Run", Training.Mode.TRAINING_SHOTS, 1, 0), null) == null, "De training begon niet"));
        helper.runAfterDelay(5 + afterCountdown(), () -> {
            var state = helper.getBlockState(goal);
            state.getBlock().stepOn(helper.getLevel(), helper.absolutePos(goal), state, trainee);
            check(Training.of(trainee.getUUID()) == null, "Op het doel stappen stopte de training niet");
            TrainingReport report = core.trainingReports().getLast();
            check(report.result() == Training.Result.GOAL && report.result().passed(), "Het rapport klopt niet: " + report.line());
            check(Training.start(helper.getLevel(), core, trainee,
                    new Training.Settings("Run", Training.Mode.TRAINING_SHOTS, 1, 1), null) == null, "De tweede training begon niet");
        });
        helper.runAfterDelay(5 + 2 * afterCountdown() + 30, () -> {
            TrainingReport report = core.trainingReports().getLast();
            check(report.result() == Training.Result.TIME_UP && !report.result().passed(),
                    "Met een doel in de basis is de tijd om niet mislukt: " + report.line());
            helper.succeed();
        });
    }

    /**
     * Squads: twee robots in "Wave 1" gaan samen aan met activate("Wave 1"), en als ze allebei neer liggen hoort je
     * diagram {@code when squad_down("Wave 1"):}, waar {@code active("Wave 1")} dan 0 is.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "training_squad")
    public static void squads_switch_together_and_report_down(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 4), "squad-owner");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos rack = new BlockPos(14, 3, 3);
        StarkTechGameTests.placeServer(helper, rack, core);
        java.util.List<com.senne.starktech.robot.GuardRobotEntity> wave = new java.util.ArrayList<>();
        for (BlockPos pos : new BlockPos[] {new BlockPos(6, 2, 8), new BlockPos(9, 2, 8)}) {
            com.senne.starktech.robot.GuardRobotEntity robot = helper.spawn(com.senne.starktech.registry.ModEntities.GUARD_ROBOT.get(), pos);
            robot.setOwner(owner);
            robot.setSquad("Wave 1");
            wave.add(robot);
        }
        helper.runAfterDelay(25, () -> {
            StarkTechGameTests.startServer(helper, rack, core);
            core.logic().put(null, diagram("Waves", """
                    when squad_down("Wave 1"):
                        log("wave down " + active("Wave 1"))
                    """));
        });
        helper.runAfterDelay(50, () -> {
            var result = com.senne.starktech.bot.BotLink.switchOn(helper.getLevel(), core, "Wave 1", true);
            check(result == com.senne.starktech.bot.BotLink.Result.DONE, "activate(\"Wave 1\") lukte niet: " + result);
            check(wave.stream().allMatch(com.senne.starktech.robot.GuardRobotEntity::isActive), "Niet de hele squad ging aan");
        });
        helper.runAfterDelay(90, () -> {
            check(com.senne.starktech.bot.BotLink.active(helper.getLevel(), core, "Wave 1") == 2, "active(\"Wave 1\") is niet 2");
            wave.forEach(robot -> robot.knockDown(400));
        });
        helper.succeedWhen(() -> check(logged(core, EventLog.Type.LOGIC, "wave down 0"),
                "when squad_down(\"Wave 1\") liep niet (of active() was niet 0)"));
    }

    /**
     * Het Training End-blok: zijn programma bewaart hij alleen als het klopt. Bij een treffer geeft hij even redstone,
     * en zijn {@code when trainee_hit:} loopt op de server: met 0 levens zet het "Wave 1" uit, en het schrijft wie er geraakt is.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "training_end")
    public static void training_end_block_pulses_and_runs_its_program(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 4), "end-owner");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos rack = new BlockPos(14, 3, 3);
        StarkTechGameTests.placeServer(helper, rack, core);
        BlockPos block = new BlockPos(6, 2, 6);
        helper.setBlock(block, com.senne.starktech.registry.ModBlocks.TRAINING_END.get());
        BlockPos abs = helper.absolutePos(block);
        var end = (com.senne.starktech.training.TrainingEndBlockEntity) helper.getBlockEntity(block);
        java.util.List<com.senne.starktech.robot.GuardRobotEntity> wave = new java.util.ArrayList<>();
        for (BlockPos pos : new BlockPos[] {new BlockPos(9, 2, 8), new BlockPos(11, 2, 8)}) {
            com.senne.starktech.robot.GuardRobotEntity robot = helper.spawn(com.senne.starktech.registry.ModEntities.GUARD_ROBOT.get(), pos);
            robot.setOwner(owner);
            robot.setSquad("Wave 1");
            wave.add(robot);
        }
        String code = """
                when trainee_hit:
                    if lives == 0:
                        deactivate("Wave 1")
                    log("hit " + trainee + " " + hits)
                """;
        String name = owner.getGameProfile().getName();
        helper.runAfterDelay(25, () -> {
            StarkTechGameTests.startServer(helper, rack, core);
            net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
            data.putString("Code", code);
            com.senne.starktech.training.TrainingBlocks.handleAction(owner, new com.senne.starktech.network.TrainingActionPayload(abs, data));
            check(end.source().equals(code), "Het programma van het End-blok werd niet bewaard");
            data.putString("Code", "when every(5):\n    pass\n");
            com.senne.starktech.training.TrainingBlocks.handleAction(owner, new com.senne.starktech.network.TrainingActionPayload(abs, data));
            check(end.source().equals(code), "Een programma dat niet klopt (every in een End-blok) werd toch bewaard");
        });
        helper.runAfterDelay(45, () -> {
            check(com.senne.starktech.bot.BotLink.switchOn(helper.getLevel(), core, "Wave 1", true)
                    == com.senne.starktech.bot.BotLink.Result.DONE, "Wave 1 ging niet aan");
            check(Training.start(helper.getLevel(), core, owner,
                    new Training.Settings("Kamino", Training.Mode.TRAINING_SHOTS, 1, 0), null) == null, "De training begon niet");
        });
        helper.runAfterDelay(45 + afterCountdown(), () -> {
            check(!helper.getBlockState(block).getValue(com.senne.starktech.training.TrainingEndBlock.POWERED), "Het End-blok gaf al redstone");
            Shots.hit(helper.getLevel(), owner, owner.damageSources().generic(), 5, "Test turret");
            check(helper.getBlockState(block).getValue(com.senne.starktech.training.TrainingEndBlock.POWERED),
                    "Bij een treffer gaf het End-blok geen redstone");
        });
        helper.runAfterDelay(45 + afterCountdown() + 20, () -> check(
                !helper.getBlockState(block).getValue(com.senne.starktech.training.TrainingEndBlock.POWERED), "De puls hield niet op"));
        helper.runAfterDelay(45 + afterCountdown() + 22, () -> helper.succeedWhen(() -> {
            check(logged(core, EventLog.Type.LOGIC, "hit " + name + " 1"), "when trainee_hit: liep niet (of zonder trainee/hits)");
            check(wave.stream().noneMatch(com.senne.starktech.robot.GuardRobotEntity::isActive),
                    "Met 0 levens zette het End-blok Wave 1 niet uit");
        }));
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
