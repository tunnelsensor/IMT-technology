package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.logic.InputDeviceBlockEntity;
import com.senne.imttech.logic.KeycardData;
import com.senne.imttech.logic.KeycardItem;
import com.senne.imttech.logic.LogicEvents;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.robot.Robots;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.os.SecurityOs;
import com.senne.imttech.security.zone.ZoneData;
import com.senne.imttech.training.Training;
import com.senne.imttech.training.TrainingReport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de inbraak- en uitbraakoefening (hoofdstuk 27): tijdens een oefening behandelt je hele basis jou als
 * indringer, ook als het je eigen basis is. Daarna is alles weer gewoon.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class DrillGameTests {
    private DrillGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Hoe lang het aftellen duurt, plus een beetje. */
    private static int afterCountdown() {
        return Math.round(com.senne.imttech.config.ImtConfig.Training.COUNTDOWN_SECONDS * 20) + 5;
    }

    private static Training.Settings drill(String name, Training.Kind kind) {
        return new Training.Settings(name, Training.Mode.TRAINING_SHOTS, 3, 0, kind);
    }

    /**
     * Een inbraakoefening van de eigenaar zelf: tijdens het aftellen is hij nog een vriend; daarna is hij voor zijn eigen
     * basis een indringer (ook zonder PvP: in de testwereld staat die uit). Zijn zone laat hem niet bouwen of slopen,
     * zijn robot stelt hij niet in, en zijn desktop gaat niet open. Gaat hij dood, dan is de oefening voorbij, met een
     * rapport; daarna is alles weer gewoon.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "drill_base")
    public static void drill_turns_your_base_against_you(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 2, 8), "drill-owner");
        SecurityCoreBlockEntity core = ZoneGameTests.zoneCore(helper, owner);
        BlockPos coreAbs = helper.absolutePos(new BlockPos(2, 3, 2));
        var robot = RobotGameTests.robot(helper, new BlockPos(10, 2, 10), com.senne.imttech.robot.RobotParts.DEFAULT);
        robot.setOwner(owner);
        BlockPos inZone = helper.absolutePos(new BlockPos(8, 3, 8));
        helper.runAfterDelay(5, () -> {
            check(Training.start(level, core, owner, drill("Vault", Training.Kind.BREAK_IN), null) == null, "De oefening begon niet");
            check(SecurityCores.isFriend(level, core, owner), "Tijdens het aftellen is de eigenaar al geen vriend meer");
        });
        helper.runAfterDelay(5 + afterCountdown(), () -> {
            Training.Session session = Training.of(owner.getUUID());
            check(session != null && session.drilling(), "Na het aftellen is de oefening niet bezig");
            check(!SecurityCores.isFriend(level, core, owner) && SecurityCores.isIntruder(level, core, owner),
                    "Tijdens de oefening is de eigenaar geen indringer in zijn eigen basis");
            ZoneData.Zone zone = ZoneData.get(level).at(level.dimension(), inZone);
            check(zone != null && !zone.isBuilder(level, owner), "Tijdens de oefening mag hij in zijn zone toch bouwen");
            check(!Robots.mayEdit(owner, robot), "Tijdens de oefening stelt hij zijn robot toch in");
            SecurityOs.open(owner, coreAbs);
            check(!SecurityOs.hasDesktop(owner), "Tijdens de oefening ging zijn desktop toch open");
            owner.kill();
        });
        helper.runAfterDelay(5 + afterCountdown() + 5, () -> {
            check(Training.of(owner.getUUID()) == null, "Dood is de oefening niet voorbij");
            TrainingReport report = core.trainingReports().getLast();
            check(report.kind() == Training.Kind.BREAK_IN && report.result() == Training.Result.DIED && report.closest() >= 0
                    && report.line().startsWith("Break-in drill Vault: drill-owner died after"), "Het rapport klopt niet: " + report.line());
            check(SecurityCores.isFriend(level, core, owner), "Na de oefening is de eigenaar geen vriend meer");
            core.setZoneBoxes(java.util.List.of());
            robot.discard();
            cleanUp(helper, new BlockPos(2, 3, 2));
        });
    }

    /**
     * Kaarten (hoofdstuk 27): een keycard die de trainee bij het begin al had, werkt tijdens de oefening niet ("not valid
     * here" in het logboek); een kaart die hij onderweg kreeg, wel.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "drill_cards")
    public static void drill_cards_from_before_do_not_work(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "drill-cards");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos rack = new BlockPos(12, 3, 12);
        ImtTechGameTests.placeServer(helper, rack, core);
        BlockPos reader = new BlockPos(6, 3, 3);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(reader)).setName("Vault door");
        BlockPos readerAbs = helper.absolutePos(reader);
        KeycardData[] cards = new KeycardData[2];
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, rack, core);
            var old = core.logic().issue("Old card", 1, "test", "");
            cards[0] = new KeycardData(core.ip(), core.hostname(), old.number(), 1, "Old card");
            ItemStack stack = new ItemStack(ModItems.KEYCARD.get());
            KeycardItem.write(stack, cards[0]);
            owner.getInventory().add(stack);
            check(Training.start(level, core, owner, drill("Vault", Training.Kind.BREAK_IN), null) == null, "De oefening begon niet");
        });
        helper.runAfterDelay(2 + afterCountdown(), () -> {
            var found = core.logic().issue("Found card", 1, "test", "");
            cards[1] = new KeycardData(core.ip(), core.hostname(), found.number(), 1, "Found card");
            check(Training.isCardFromBefore(level, core.getBlockPos(), owner.getUUID(), cards[0].number())
                    && !Training.isCardFromBefore(level, core.getBlockPos(), owner.getUUID(), cards[1].number()),
                    "De oefening onthield de kaarten van het begin niet goed");
            LogicEvents.clear();
            LogicEvents.keycard(level, readerAbs, owner, cards[0]);
            LogicEvents.clear();
            LogicEvents.keycard(level, readerAbs, owner, cards[1]);
            String before = "drill-cards used card #" + cards[0].number() + " (not valid here) at Vault door";
            String during = "drill-cards used card #" + cards[1].number() + " (no group) at Vault door";
            check(core.log().entries().stream().anyMatch(entry -> entry.text().equals(before)),
                    "De kaart van het begin werkte toch. Logboek: " + core.log().entries().getLast().text());
            check(core.log().entries().stream().anyMatch(entry -> entry.text().equals(during)),
                    "De gevonden kaart werkte niet. Logboek: " + core.log().entries().getLast().text());
            Training.end(level, Training.of(owner.getUUID()), Training.Result.STOPPED);
            cleanUp(helper, new BlockPos(2, 3, 2));
        });
    }

    /**
     * De hack (hoofdstuk 27): de eigenaar zet tijdens zijn inbraakoefening de Hacking Laptop op zijn eigen kern (dat mag nu:
     * hij is een indringer) en speelt de terminal uit. De oefening is geslaagd ("hacked the core"), de kern blijft van
     * hem, en de laptop gaat terug in zijn rugzak.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "drill_hack")
    public static void drill_hack_passes_and_keeps_the_owner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corePos = new BlockPos(12, 3, 12);
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(12, 3, 15), "drill-hacker");
        owner.getAbilities().invulnerable = true;
        owner.getAbilities().instabuild = false;
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, corePos, owner);
        BlockPos coreAbs = helper.absolutePos(corePos);
        helper.runAfterDelay(5, () -> check(Training.start(level, core, owner, drill("Vault", Training.Kind.BREAK_IN), null) == null,
                "De oefening begon niet"));
        helper.runAfterDelay(5 + afterCountdown(), () -> {
            ItemStack laptopItem = new ItemStack(ModItems.HACKING_LAPTOP.get());
            check(com.senne.imttech.security.TowerHack.placeLaptop(owner, coreAbs, laptopItem),
                    "De laptop ging niet op zijn eigen kern (hij is toch een indringer?)");
        });
        helper.runAfterDelay(5 + afterCountdown() + com.senne.imttech.security.TowerHack.MIN_TICKS + 5, () -> {
            var laptop = (com.senne.imttech.security.HackingLaptopBlockEntity) helper.getBlockEntity(corePos.above());
            ImtTechGameTests.typeOnLaptop(owner, coreAbs.above(), ImtTechGameTests.hackSolution(laptop));
            check(Training.of(owner.getUUID()) == null, "Na de hack is de oefening niet voorbij");
            TrainingReport report = core.trainingReports().getLast();
            check(report.result() == Training.Result.HACKED && report.result().passed() && report.closest() == 0,
                    "Het rapport klopt niet: " + report.line());
            check(owner.getUUID().equals(core.ownerId()), "De kern is niet meer van de eigenaar");
            helper.assertBlockNotPresent(ModBlocks.HACKING_LAPTOP.get(), corePos.above());
            check(owner.getInventory().contains(new ItemStack(ModItems.HACKING_LAPTOP.get())), "De laptop kwam niet terug");
            check(SecurityCores.isFriend(level, core, owner), "Na de oefening is de eigenaar geen vriend meer");
            cleanUp(helper, corePos);
        });
    }

    /**
     * De regels: een oefening kan alleen in survival, en een uitbraak heeft een Training Goal-blok nodig (het doel buiten
     * de cel). Met een doel begint hij wel; wie dan naar creative gaat, stopt ermee.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "drill_rules")
    public static void drill_only_in_survival_and_break_out_needs_a_goal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean[] creativeMode = {false};
        ServerPlayer owner = ImtTechGameTests.makeSwitchablePlayer(helper, new BlockPos(6, 2, 6), "drill-rules", () -> creativeMode[0]);
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        helper.runAfterDelay(5, () -> {
            creativeMode[0] = true;
            String creative = Training.start(level, core, owner, drill("Vault", Training.Kind.BREAK_IN), null);
            check(creative != null && creative.contains("only works in survival"), "In creative begon de oefening toch: " + creative);
            creativeMode[0] = false;
            // Even een klein gebied: in de testwereld liggen overal nog Goal-blokken van andere tests (binnen 200 blokken).
            String noGoal = ImtTechGameTests.withReach(8, () -> Training.start(level, core, owner, drill("Cell", Training.Kind.BREAK_OUT), null));
            check(noGoal != null && noGoal.contains("Training Goal"), "Een uitbraak zonder doel begon toch: " + noGoal);
            helper.setBlock(new BlockPos(10, 2, 10), ModBlocks.TRAINING_GOAL.get());
        });
        helper.runAfterDelay(10, () -> check(Training.start(level, core, owner, drill("Cell", Training.Kind.BREAK_OUT), null) == null,
                "Met een doel begon de uitbraak niet"));
        helper.runAfterDelay(10 + afterCountdown(), () -> {
            check(Training.of(owner.getUUID()) != null && Training.of(owner.getUUID()).drilling(), "De uitbraak is niet bezig");
            creativeMode[0] = true;
        });
        helper.runAfterDelay(10 + afterCountdown() + 3, () -> {
            check(Training.of(owner.getUUID()) == null, "Naar creative en de oefening liep door");
            TrainingReport report = core.trainingReports().getLast();
            check(report.kind() == Training.Kind.BREAK_OUT && report.result() == Training.Result.LEFT && report.closest() == -1,
                    "Het rapport klopt niet: " + report.line());
            creativeMode[0] = false;
            cleanUp(helper, new BlockPos(2, 3, 2));
        });
    }

    /**
     * Klaar: de kern weg en geslaagd. De testwereld ruimt niets op, en een kern die blijft liggen, pikt anders de
     * camera's en robots van een latere test in (die horen bij de dichtstbijzijnde kern).
     */
    private static void cleanUp(GameTestHelper helper, BlockPos core) {
        helper.setBlock(core, net.minecraft.world.level.block.Blocks.AIR);
        helper.succeed();
    }
}
