package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.cuffs.Handcuffs;
import com.senne.imttech.raven.Bodies;
import com.senne.imttech.raven.Noises;
import com.senne.imttech.raven.RavenBodyEntity;
import com.senne.imttech.robot.GuardRobotEntity;
import com.senne.imttech.robot.RobotStyle;
import com.senne.imttech.robot.TowerRobots;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.bot.BotBoard;
import com.senne.imttech.logic.program.LogicException;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.raven.RavenAlert;
import com.senne.imttech.raven.RavenRoutes;
import com.senne.imttech.raven.RavenSites;
import com.senne.imttech.raven.RavenSoldierEntity;
import com.senne.imttech.raven.Takedown;
import com.senne.imttech.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de bewakers zoals in Hitman (hoofdstuk 60): een Raven-soldaat kijkt in een kegel, het duurt even voor hij
 * je heeft, sluipen helpt, en raakt hij je kwijt dan zoekt hij waar hij je het laatst zag; hij loopt zijn route, hoort
 * lawaai, roept via de radio en krijgt versterking.
 *
 * Elke test waarin een soldaat een speler kan zien of iets kan horen heeft zijn <b>eigen batch</b>: de arena's van één
 * batch staan naast elkaar, en een radiogesprek (48 blokken) of lawaai bereikt anders de soldaten van de test ernaast.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class GuardGameTests {
    private GuardGameTests() {}

    private static final String ARENA = "empty";

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * Een soldaat op deze plek die deze kant op kijkt (yaw 0 = naar +z, 180 = naar -z). Zonder AI blijft zijn hoofd
     * stilstaan (anders kijkt RandomLookAroundGoal alle kanten op), maar zijn zintuigen werken wel (die zitten in aiStep).
     */
    static RavenSoldierEntity soldier(GameTestHelper helper, BlockPos at, float yaw, boolean ai) {
        RavenSoldierEntity soldier = helper.spawn(ModEntities.RAVEN_SOLDIER.get(), at);
        soldier.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(soldier.blockPosition()),
                MobSpawnType.SPAWN_EGG, null);
        face(soldier, yaw);
        soldier.setNoAi(!ai);
        return soldier;
    }

    static void face(RavenSoldierEntity soldier, float yaw) {
        soldier.setYRot(yaw);
        soldier.yRotO = yaw;
        soldier.yBodyRot = yaw;
        soldier.yBodyRotO = yaw;
        soldier.setYHeadRot(yaw);
        soldier.yHeadRotO = yaw;
    }

    /** Een speler in survival (de gewone testspeler staat altijd in creative, en die ziet een bewaker niet). */
    static ServerPlayer player(GameTestHelper helper, BlockPos at, String name) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, at, name);
        player.getAbilities().instabuild = false;
        return player;
    }

    /** Een bewaker ziet je niet meteen: zijn meter loopt eerst vol, en pas dan ben je zijn doelwit. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "guards_spot")
    public static void guard_needs_a_while_to_spot_you(GameTestHelper helper) {
        RavenSoldierEntity guard = soldier(helper, new BlockPos(5, 2, 5), 0, false);
        ServerPlayer player = player(helper, new BlockPos(5, 2, 13), "guard-spot");
        helper.runAfterDelay(5, () -> {
            check(guard.getTarget() == null, "De bewaker had de speler meteen als doelwit (de meter zou eerst vol moeten lopen)");
            check(guard.senses().suspicion() > 0, "De meter van de bewaker loopt niet vol terwijl de speler recht voor hem staat");
            check(guard.senses().suspect() == player, "De bewaker heeft niet de speler als verdachte");
        });
        helper.succeedWhen(() -> {
            check(guard.getTarget() == player, "De bewaker heeft de speler nog niet (meter " + guard.senses().suspicion() + ")");
            check(guard.senses().state() == RavenAlert.COMBAT, "De bewaker heeft zijn doelwit maar is niet in gevecht");
            check(guard.alertState() == RavenAlert.COMBAT, "Je spel krijgt de toestand van de bewaker niet mee");
        });
    }

    /** Achter zijn rug ziet hij je niet: de meter blijft op nul. */
    @GameTest(template = ARENA, timeoutTicks = 150, batch = "guards_behind")
    public static void guard_does_not_see_behind_his_back(GameTestHelper helper) {
        RavenSoldierEntity guard = soldier(helper, new BlockPos(5, 2, 5), 180, false);
        player(helper, new BlockPos(5, 2, 11), "guard-behind");
        helper.runAfterDelay(100, () -> {
            check(guard.getTarget() == null, "De bewaker zag de speler achter zijn rug");
            check(guard.senses().suspicion() == 0, "De meter van de bewaker liep vol terwijl de speler achter hem stond");
            check(guard.senses().state() == RavenAlert.CALM, "De bewaker is niet rustig");
            helper.succeed();
        });
    }

    /** Sluipen helpt: de meter van een bewaker loopt dan half zo snel vol (een muur houdt de twee lanen uit elkaar). */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guards_sneak")
    public static void guard_notices_a_sneaking_player_slower(GameTestHelper helper) {
        for (int z = 1; z <= 17; z++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(8, y, z), Blocks.STONE_BRICKS);
            }
        }
        RavenSoldierEntity walking = soldier(helper, new BlockPos(3, 2, 3), 0, false);
        player(helper, new BlockPos(3, 2, 11), "guard-walker");
        RavenSoldierEntity sneaking = soldier(helper, new BlockPos(13, 2, 3), 0, false);
        ServerPlayer sneaker = player(helper, new BlockPos(13, 2, 11), "guard-sneaker");
        sneaker.setShiftKeyDown(true);
        helper.runAfterDelay(40, () -> {
            float loud = walking.senses().suspicion();
            float quiet = sneaking.senses().suspicion();
            check(loud > 0 && quiet > 0, "Een van de bewakers ziet zijn speler niet (" + loud + ", " + quiet + ")");
            check(loud > quiet * 1.5f, "Sluipen helpt niet: " + loud + " tegenover " + quiet);
            check(walking.getTarget() == null && sneaking.getTarget() == null, "Een bewaker had zijn speler al na twee seconden");
            helper.succeed();
        });
    }

    /**
     * Raakt hij je kwijt (je verdwijnt in een dicht hokje), dan laat hij zijn doelwit na {@code lostSeconds} los en gaat
     * hij zoeken op de plek waar hij je het laatst zag.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "guards_search")
    public static void guard_searches_where_he_lost_you(GameTestHelper helper) {
        float lostBefore = ImtConfig.Guards.LOST_SECONDS;
        ImtConfig.Guards.LOST_SECONDS = 1.5f;
        RavenSoldierEntity guard = soldier(helper, new BlockPos(5, 2, 4), 0, true);
        BlockPos seenAt = new BlockPos(5, 2, 11);
        ServerPlayer player = player(helper, seenAt, "guard-lost");
        // Het dichte hokje, een eind verderop.
        BlockPos box = new BlockPos(15, 2, 15);
        for (BlockPos pos : BlockPos.betweenClosed(box.offset(-1, -1, -1), box.offset(1, 2, 1))) {
            if (!(pos.getX() == box.getX() && pos.getZ() == box.getZ() && pos.getY() >= box.getY() && pos.getY() <= box.getY() + 1)) {
                helper.setBlock(pos, Blocks.STONE_BRICKS);
            }
        }
        Vec3 lastSeen = helper.absoluteVec(Vec3.atBottomCenterOf(seenAt));
        helper.startSequence()
                .thenWaitUntil(() -> check(guard.getTarget() == player, "De bewaker heeft de speler nog niet"))
                .thenExecute(() -> {
                    Vec3 inside = helper.absoluteVec(Vec3.atBottomCenterOf(box));
                    player.teleportTo(inside.x, inside.y, inside.z);
                })
                .thenWaitUntil(() -> {
                    check(guard.getTarget() == null, "De bewaker houdt zijn doelwit vast terwijl hij het niet meer ziet");
                    check(guard.senses().state() == RavenAlert.SEARCHING, "De bewaker zoekt niet (" + guard.senses().state() + ")");
                })
                .thenWaitUntil(() -> check(guard.position().distanceTo(lastSeen) < 3.0,
                        "De bewaker kwam niet kijken waar hij de speler het laatst zag (" + guard.position() + ")"))
                .thenExecute(() -> ImtConfig.Guards.LOST_SECONDS = lostBefore)
                .thenSucceed();
    }

    // ================================================================ Routes en posten (stap 2)

    /** Een bewaker met een route loopt zijn punten af, kijkt bij elk punt even rond, en begint dan weer van voren af aan. */
    @GameTest(template = ARENA, timeoutTicks = 600, batch = "guards")
    public static void guard_walks_his_route(GameTestHelper helper) {
        RavenSoldierEntity guard = soldier(helper, new BlockPos(4, 2, 4), 0, true);
        BlockPos a = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos b = helper.absolutePos(new BlockPos(4, 2, 14));
        guard.setRoute(List.of(a, b));
        Vec3 atA = Vec3.atBottomCenterOf(a);
        Vec3 atB = Vec3.atBottomCenterOf(b);
        helper.startSequence()
                .thenWaitUntil(() -> check(guard.position().distanceTo(atB) < 1.6, "De bewaker kwam niet bij het tweede punt van zijn route"))
                .thenWaitUntil(() -> check(guard.position().distanceTo(atA) < 1.6, "De bewaker kwam niet terug bij het eerste punt"))
                .thenExecute(() -> check(guard.senses().state() == RavenAlert.CALM, "De bewaker is niet rustig terwijl hij zijn route loopt"))
                .thenSucceed();
    }

    /** Zonder route staat hij op zijn post (waar hij verscheen): zet je hem ergens anders neer, dan loopt hij terug. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "guards")
    public static void guard_returns_to_his_post(GameTestHelper helper) {
        RavenSoldierEntity guard = soldier(helper, new BlockPos(4, 2, 4), 0, true);
        BlockPos post = helper.absolutePos(new BlockPos(4, 2, 4));
        check(post.equals(guard.post()), "De post van de bewaker is niet waar hij verscheen: " + guard.post());
        check(guard.route().isEmpty(), "Een soldaat uit een spawn egg heeft geen route");
        Vec3 away = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(12, 2, 12)));
        guard.teleportTo(away.x, away.y, away.z);
        Vec3 home = Vec3.atBottomCenterOf(post);
        helper.succeedWhen(() -> check(guard.position().distanceTo(home) < 2.0,
                "De bewaker liep niet terug naar zijn post (" + guard.position() + ")"));
    }

    /** De route in een markering draait mee met het stuk waar hij in staat (RavenRoutes). */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "guards")
    public static void route_markers_turn_with_the_room(GameTestHelper helper) {
        BlockPos at = new BlockPos(10, 1, 10);
        List<BlockPos> plain = RavenRoutes.route("raven_soldier;3,0,0;0,0,2", at, Rotation.NONE);
        check(plain.equals(List.of(new BlockPos(13, 1, 10), new BlockPos(10, 1, 12))), "De route zonder draaiing klopt niet: " + plain);
        List<BlockPos> turned = RavenRoutes.route("raven_soldier;3,0,0;0,0,2", at, Rotation.CLOCKWISE_90);
        check(turned.size() == 2 && !turned.equals(plain), "De gedraaide route is niet anders: " + turned);
        check(turned.get(0).distManhattan(at) == 3 && turned.get(1).distManhattan(at) == 2, "De gedraaide route is niet even ver: " + turned);
        check(RavenRoutes.isSoldier("raven_soldier;3,0,0") && RavenRoutes.isSoldier("raven_soldier"), "Een soldaat met of zonder route wordt niet herkend");
        check(!RavenRoutes.isSoldier("raven_guard;1,0,0") && RavenRoutes.isGuard("raven_guard;1,0,0"), "Een bewaker van de toren wordt niet herkend");
        check(RavenRoutes.route("raven_soldier", at, Rotation.NONE).isEmpty(), "Zonder route hoort de lijst leeg te zijn");
        check(RavenRoutes.route("raven_soldier;a,b", at, Rotation.NONE).isEmpty(), "Een kapotte verschuiving hoort overgeslagen te worden");
        helper.succeed();
    }

    // ================================================================ Geluid (stap 3)

    /** Lawaai: een rustige bewaker gaat kijken op die plek, kijkt rond, wordt weer rustig en gaat terug naar zijn post. */
    @GameTest(template = ARENA, timeoutTicks = 600, batch = "guards_noise")
    public static void guard_investigates_a_noise_and_returns(GameTestHelper helper) {
        RavenSoldierEntity guard = soldier(helper, new BlockPos(4, 2, 4), 0, true);
        Vec3 post = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
        Vec3 noise = helper.absoluteVec(new Vec3(13.5, 2.5, 13.5));
        Noises.make(helper.getLevel(), noise, 14, Noises.Kind.BLOCK, null); // niet verder: de arena ernaast heeft ook bewakers
        check(guard.senses().state() == RavenAlert.SUSPICIOUS, "De bewaker hoorde het lawaai niet (" + guard.senses().state() + ")");
        check(guard.getTarget() == null, "Lawaai alleen mag geen doelwit geven");
        helper.startSequence()
                .thenWaitUntil(() -> check(guard.position().distanceTo(noise) < 2.5, "De bewaker ging niet kijken bij het lawaai (" + guard.position() + ")"))
                .thenWaitUntil(() -> check(guard.senses().state() == RavenAlert.CALM, "De bewaker werd niet weer rustig"))
                .thenWaitUntil(() -> check(guard.position().distanceTo(post) < 2.0, "De bewaker ging niet terug naar zijn post"))
                .thenSucceed();
    }

    /** Door een muur draagt lawaai maar half zo ver, en lawaai van een soldaat zelf telt niet. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "guards_noise_wall")
    public static void walls_muffle_a_noise(GameTestHelper helper) {
        for (int x = 11; x <= 18; x++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 10), Blocks.STONE_BRICKS);
            }
        }
        RavenSoldierEntity open = soldier(helper, new BlockPos(3, 2, 6), 0, false);
        RavenSoldierEntity walled = soldier(helper, new BlockPos(15, 2, 6), 0, false);
        Vec3 noise = helper.absoluteVec(new Vec3(9.5, 2.5, 16.5));
        Noises.make(helper.getLevel(), noise, 16, Noises.Kind.SHOT, null);
        check(open.senses().state() == RavenAlert.SUSPICIOUS, "De bewaker in de open ruimte hoorde het schot niet");
        check(walled.senses().state() == RavenAlert.CALM, "De bewaker achter de muur hoorde het schot toch (even ver weg)");
        Noises.make(helper.getLevel(), noise, 40, Noises.Kind.SHOT, walled);
        check(walled.senses().state() == RavenAlert.CALM, "Een soldaat hoort zijn eigen schot als lawaai");
        helper.succeed();
    }

    // ================================================================ De radio en de versterking (stap 4)

    /** Hoeveel soldaten er in de arena staan. */
    private static int soldiersIn(GameTestHelper helper) {
        AABB box = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(20, 8, 20)));
        return helper.getLevel().getEntitiesOfClass(RavenSoldierEntity.class, box, RavenSoldierEntity::isAlive).size();
    }

    /**
     * Wie je ziet, roept eerst via de radio. Stop je hem na de eerste seconde (hij heeft zijn radio al vast), dan hoorden
     * zijn maten een half gesprek: ze worden achterdochtig en komen langzaam naar hem toe (jouw antwoord 4).
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "guards_radio_half")
    public static void interrupted_radio_call_makes_the_others_suspicious(GameTestHelper helper) {
        RavenSoldierEntity caller = soldier(helper, new BlockPos(5, 2, 4), 0, false);
        RavenSoldierEntity mate = soldier(helper, new BlockPos(16, 2, 4), 180, false); // kijkt van de speler weg
        player(helper, new BlockPos(5, 2, 8), "radio-half");
        Vec3 callerAt = caller.position();
        helper.startSequence()
                .thenWaitUntil(() -> check(caller.senses().state() == RavenAlert.CALLING, "De soldaat roept niet via de radio (" + caller.senses().state() + ")"))
                .thenExecute(() -> {
                    check(caller.getTarget() == null, "Hij schiet al terwijl hij nog roept");
                    check(mate.senses().state() == RavenAlert.CALM, "De maat is al onrustig voor er iets gebeurde");
                })
                .thenExecuteAfter(30, () -> {
                    check(caller.senses().callTicks() >= 20 && caller.senses().callTicks() < 60, "De test mikt naast het gesprek: " + caller.senses().callTicks());
                    caller.kill();
                })
                .thenExecuteAfter(2, () -> {
                    check(mate.senses().state() == RavenAlert.SUSPICIOUS, "De maat hoorde het halve gesprek niet (" + mate.senses().state() + ")");
                    check(mate.senses().slow(), "De maat komt niet langzaam kijken");
                    check(mate.senses().lookAt() != null && mate.senses().lookAt().distanceTo(callerAt) < 2.0, "De maat gaat niet naar de plek van de roeper");
                    check(mate.getTarget() == null, "Een half gesprek mag geen doelwit geven");
                })
                .thenSucceed();
    }

    /** Stop je hem binnen de eerste seconde, dan heeft niemand iets gehoord. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "guards_radio_quiet")
    public static void a_call_stopped_in_time_stays_silent(GameTestHelper helper) {
        RavenSoldierEntity caller = soldier(helper, new BlockPos(5, 2, 4), 0, false);
        RavenSoldierEntity mate = soldier(helper, new BlockPos(16, 2, 4), 180, false);
        player(helper, new BlockPos(5, 2, 8), "radio-quiet");
        helper.startSequence()
                .thenWaitUntil(() -> check(caller.senses().state() == RavenAlert.CALLING, "De soldaat roept niet via de radio"))
                .thenExecuteAfter(10, () -> {
                    check(caller.senses().callTicks() < 20, "De test mikt naast de eerste seconde: " + caller.senses().callTicks());
                    caller.kill();
                })
                .thenExecuteAfter(5, () -> check(mate.senses().state() == RavenAlert.CALM, "De maat hoorde iets terwijl het gesprek nog niet begonnen was"))
                .thenSucceed();
    }

    /**
     * Een heel gesprek: alarm voor de hele site (de maat krijgt je als doelwit) en na de wachttijd komt een team door de
     * ingang (de markering raven_entry) dat op jou afkomt.
     */
    @GameTest(template = ARENA, timeoutTicks = 500, batch = "guards_radio_team")
    public static void a_finished_call_alarms_the_base_and_brings_a_team(GameTestHelper helper) {
        float delayBefore = ImtConfig.Guards.BACKUP_DELAY_SECONDS;
        float rangeBefore = ImtConfig.Guards.SITE_RANGE;
        ImtConfig.Guards.BACKUP_DELAY_SECONDS = 1.0f;
        ImtConfig.Guards.SITE_RANGE = 40.0f;
        Marker entry = EntityType.MARKER.create(helper.getLevel());
        check(entry != null, "Geen Marker");
        BlockPos entryAt = helper.absolutePos(new BlockPos(10, 2, 17));
        entry.moveTo(entryAt.getX() + 0.5, entryAt.getY(), entryAt.getZ() + 0.5, 0, 0);
        entry.addTag(RavenSites.ENTRY_TAG);
        helper.getLevel().addFreshEntity(entry);
        RavenSoldierEntity caller = soldier(helper, new BlockPos(5, 2, 4), 0, false);
        RavenSoldierEntity mate = soldier(helper, new BlockPos(16, 2, 4), 180, false);
        ServerPlayer player = player(helper, new BlockPos(5, 2, 8), "radio-team");
        int before = soldiersIn(helper);
        helper.startSequence()
                .thenWaitUntil(() -> check(caller.senses().state() == RavenAlert.CALLING, "De soldaat roept niet via de radio"))
                .thenWaitUntil(() -> check(caller.getTarget() == player, "Het gesprek is nog niet af (" + caller.senses().callTicks() + ")"))
                .thenExecute(() -> {
                    check(caller.senses().state() == RavenAlert.COMBAT, "Na het gesprek vecht hij niet");
                    check(mate.getTarget() == player, "Het alarm bereikte de maat niet");
                    RavenSites.Site site = RavenSites.get(helper.getLevel()).site(entryAt);
                    check(site.alarmed(helper.getLevel().getGameTime()), "De site heeft geen alarm");
                    check(site.backupPending(), "Er is geen team onderweg");
                })
                .thenWaitUntil(() -> check(soldiersIn(helper) > before, "Er kwam geen team binnen"))
                .thenExecute(() -> {
                    check(soldiersIn(helper) >= before + Math.round(ImtConfig.Guards.BACKUP_SIZE), "Het team is niet compleet: " + soldiersIn(helper));
                    AABB door = new AABB(entryAt).inflate(4, 2, 4);
                    boolean atTheDoor = !helper.getLevel().getEntitiesOfClass(RavenSoldierEntity.class, door,
                            s -> s != caller && s != mate && s.getTarget() == player).isEmpty();
                    check(atTheDoor, "Het team staat niet bij de ingang, of komt niet op de speler af");
                    check(RavenSites.get(helper.getLevel()).site(entryAt).waves() == 1, "Het aantal teams klopt niet");
                    ImtConfig.Guards.BACKUP_DELAY_SECONDS = delayBefore;
                    ImtConfig.Guards.SITE_RANGE = rangeBefore;
                })
                .thenSucceed();
    }

    /**
     * Na "stand down" gaat het versterkingsteam weer naar buiten: zolang het alarm loopt, lopen ze een route tussen de
     * ingang en je laatste plek, en daarna lopen ze naar de ingang en zijn ze weg (8 november: minder bewakers, en niet
     * stilstaan; vroeger bleven ze voorgoed bij de ingang staan).
     */
    @GameTest(template = ARENA, timeoutTicks = 600, batch = "guards_team_leaves")
    public static void backup_team_leaves_after_stand_down(GameTestHelper helper) {
        float delayBefore = ImtConfig.Guards.BACKUP_DELAY_SECONDS;
        float rangeBefore = ImtConfig.Guards.SITE_RANGE;
        float alarmBefore = ImtConfig.Guards.ALARM_SECONDS;
        float searchBefore = ImtConfig.Guards.SEARCH_SECONDS;
        ImtConfig.Guards.BACKUP_DELAY_SECONDS = 1.0f;
        ImtConfig.Guards.SITE_RANGE = 40.0f;
        ImtConfig.Guards.ALARM_SECONDS = 3.0f;
        ImtConfig.Guards.SEARCH_SECONDS = 1.0f;
        Marker entry = EntityType.MARKER.create(helper.getLevel());
        check(entry != null, "Geen Marker");
        BlockPos entryAt = helper.absolutePos(new BlockPos(10, 2, 17));
        entry.moveTo(entryAt.getX() + 0.5, entryAt.getY(), entryAt.getZ() + 0.5, 0, 0);
        entry.addTag(RavenSites.ENTRY_TAG);
        helper.getLevel().addFreshEntity(entry);
        RavenSoldierEntity caller = soldier(helper, new BlockPos(5, 2, 4), 0, false);
        int before = soldiersIn(helper);
        RavenSites.raiseAlarm(helper.getLevel(), caller, null, helper.absoluteVec(new Vec3(10.5, 2, 8.5)), false);
        helper.startSequence()
                .thenWaitUntil(() -> check(soldiersIn(helper) > before, "Er kwam geen team binnen"))
                .thenExecute(() -> {
                    AABB arena = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(20, 8, 20)));
                    List<RavenSoldierEntity> team = helper.getLevel().getEntitiesOfClass(RavenSoldierEntity.class, arena, s -> s != caller);
                    check(!team.isEmpty() && team.stream().allMatch(RavenSoldierEntity::isBackup), "Het team staat niet als versterking");
                    check(team.stream().allMatch(s -> s.route().size() == 2), "Het team loopt geen route tussen de ingang en de laatste plek");
                    check(!caller.isBackup(), "De soldaat die riep staat als versterking");
                })
                .thenWaitUntil(() -> check(soldiersIn(helper) == before,
                        "Het team ging na stand down niet weg (" + (soldiersIn(helper) - before) + " over)"))
                .thenExecute(() -> {
                    check(caller.isAlive(), "De soldaat die riep is ook weg (alleen de versterking gaat naar buiten)");
                    ImtConfig.Guards.BACKUP_DELAY_SECONDS = delayBefore;
                    ImtConfig.Guards.SITE_RANGE = rangeBefore;
                    ImtConfig.Guards.ALARM_SECONDS = alarmBefore;
                    ImtConfig.Guards.SEARCH_SECONDS = searchBefore;
                })
                .thenSucceed();
    }

    // ================================================================ Een sluipaanval (8 november)

    /**
     * Met een zwaard langs achter (jouw wens: "met een zwaard een guard kunt neersteken langs achter zodat je die stealth
     * kunt killen"): hij is in één keer neer, er ligt een lichaam, het zwaard sleet, en zijn maat merkte niets.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guards_takedown")
    public static void sword_takedown_from_behind_is_silent(GameTestHelper helper) {
        RavenSoldierEntity guard = soldier(helper, new BlockPos(5, 2, 8), 0, false); // kijkt naar +z
        RavenSoldierEntity mate = soldier(helper, new BlockPos(16, 2, 8), -90, false); // kijkt naar +x, van alles weg
        ServerPlayer player = player(helper, new BlockPos(5, 2, 6), "takedown");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        AABB arena = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(20, 8, 20)));
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    check(guard.senses().suspicion() == 0, "De bewaker ziet de speler achter zijn rug");
                    check(Takedown.possible(player, guard, guard.senses().state()), "De sluipaanval kan niet met een zwaard achter de bewaker");
                    player.attack(guard);
                })
                .thenExecuteAfter(2, () -> {
                    check(!guard.isAlive(), "De bewaker leeft nog na de sluipaanval (" + guard.getHealth() + ")");
                    check(helper.getLevel().getEntitiesOfClass(RavenBodyEntity.class, arena).size() == 1, "Er ligt geen lichaam");
                    check(player.getMainHandItem().getDamageValue() == 1, "Het zwaard sleet niet (" + player.getMainHandItem().getDamageValue() + ")");
                })
                .thenExecuteAfter(40, () -> check(mate.senses().state() == RavenAlert.CALM && mate.getTarget() == null
                        && mate.senses().suspicion() == 0, "De maat merkte de sluipaanval (" + mate.senses().state() + ")"))
                .thenSucceed();
    }

    /**
     * Geen sluipaanval als hij je ziet (van voren), zonder zwaard, of als hij al vecht: dan is het een gewone slag, hij
     * leeft nog.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guards_takedown_front")
    public static void no_takedown_from_the_front_without_a_sword_or_in_a_fight(GameTestHelper helper) {
        RavenSoldierEntity front = soldier(helper, new BlockPos(3, 2, 3), 0, false);
        ServerPlayer facing = player(helper, new BlockPos(3, 2, 5), "takedown-front");
        facing.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        RavenSoldierEntity unarmed = soldier(helper, new BlockPos(10, 2, 12), 0, false);
        ServerPlayer bare = player(helper, new BlockPos(10, 2, 10), "takedown-bare");
        RavenSoldierEntity fighting = soldier(helper, new BlockPos(16, 2, 12), 0, false);
        ServerPlayer late = player(helper, new BlockPos(16, 2, 10), "takedown-late");
        late.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        fighting.setTarget(late);
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    check(!Takedown.possible(facing, front, front.senses().state()), "Een sluipaanval van voren zou niet mogen");
                    check(!Takedown.possible(bare, unarmed, unarmed.senses().state()), "Een sluipaanval zonder zwaard zou niet mogen");
                    check(fighting.senses().state() == RavenAlert.COMBAT, "De bewaker met een doelwit vecht niet");
                    facing.attack(front);
                    bare.attack(unarmed);
                    late.attack(fighting);
                })
                .thenExecuteAfter(2, () -> {
                    for (RavenSoldierEntity guard : List.of(front, unarmed, fighting)) {
                        check(guard.isAlive(), "Een gewone slag doodde een bewaker in één keer");
                        check(guard.getHealth() < guard.getMaxHealth(), "De gewone slag kwam niet aan (de sluipaanval hield hem tegen)");
                    }
                })
                .thenSucceed();
    }

    // ================================================================ Lichamen (stap 5)

    /** Een dode soldaat laat een lichaam liggen met zijn buit erin; na een tijd verdwijnt het en valt de buit op de grond. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "guards_body")
    public static void a_dead_guard_leaves_a_body_with_his_loot(GameTestHelper helper) {
        float minutesBefore = ImtConfig.Guards.BODY_MINUTES;
        ImtConfig.Guards.BODY_MINUTES = 0.05f; // 60 ticks
        RavenSoldierEntity guard = soldier(helper, new BlockPos(5, 2, 5), 0, false);
        guard.setDropChance(EquipmentSlot.MAINHAND, 2.0f); // altijd (ook zonder een speler die hem raakte)
        Item weapon = guard.getMainHandItem().getItem();
        AABB arena = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(20, 8, 20)));
        guard.kill();
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    List<RavenBodyEntity> bodies = helper.getLevel().getEntitiesOfClass(RavenBodyEntity.class, arena);
                    check(bodies.size() == 1, "Er ligt geen lichaam (" + bodies.size() + ")");
                    check(!guard.isAlive(), "De soldaat zelf ligt er nog");
                    check(bodies.getFirst().loot().hasAnyMatching(stack -> stack.is(weapon)), "Zijn wapen zit niet in het lichaam");
                    check(helper.getLevel().getEntitiesOfClass(ItemEntity.class, arena, drop -> drop.getItem().is(weapon)).isEmpty(),
                            "Zijn wapen ligt op de grond in plaats van in het lichaam");
                })
                .thenWaitUntil(() -> check(helper.getLevel().getEntitiesOfClass(RavenBodyEntity.class, arena).isEmpty(), "Het lichaam verdwijnt niet"))
                .thenExecute(() -> {
                    check(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, arena, drop -> drop.getItem().is(weapon)).isEmpty(),
                            "Na het verdwijnen ligt zijn wapen niet op de grond");
                    ImtConfig.Guards.BODY_MINUTES = minutesBefore;
                })
                .thenSucceed();
    }

    /** Een bewaker die een lichaam ziet, loopt erheen en meldt het via de radio: zijn maten gaan daar zoeken. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "guards_body_seen")
    public static void a_guard_who_sees_a_body_calls_it_in(GameTestHelper helper) {
        RavenSoldierEntity guard = soldier(helper, new BlockPos(5, 2, 4), 0, true);
        RavenSoldierEntity mate = soldier(helper, new BlockPos(16, 2, 4), 180, false);
        RavenBodyEntity body = ModEntities.RAVEN_BODY.get().create(helper.getLevel());
        check(body != null, "Geen lichaam");
        Vec3 at = helper.absoluteVec(new Vec3(5.5, 2, 11.5));
        body.moveTo(at.x, at.y, at.z, 0, 0);
        helper.getLevel().addFreshEntity(body);
        helper.startSequence()
                .thenWaitUntil(() -> check(guard.senses().state() == RavenAlert.CALLING, "De bewaker meldt het lichaam niet (" + guard.senses().state() + ")"))
                .thenExecute(() -> {
                    check(guard.getTarget() == null, "Een lichaam alleen mag geen doelwit geven");
                    check(body.reported(), "Het lichaam staat niet als gemeld");
                    check(guard.distanceTo(body) < 4.5, "Hij roept van te ver: " + guard.distanceTo(body));
                })
                .thenWaitUntil(() -> check(mate.senses().state() == RavenAlert.SEARCHING, "Het alarm om het lichaam bereikte de maat niet (" + mate.senses().state() + ")"))
                .thenExecute(() -> check(mate.senses().lookAt() != null && mate.senses().lookAt().distanceTo(body.position()) < 2.0, "De maat zoekt niet rond het lichaam"))
                .thenSucceed();
    }

    /** Een geboeide soldaat en een gevloerde robot tellen ook als lichaam (jouw antwoord 5). */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "guards_body_cuffed")
    public static void a_cuffed_guard_and_a_downed_robot_count_as_bodies(GameTestHelper helper) {
        RavenSoldierEntity guard = soldier(helper, new BlockPos(5, 2, 4), 0, true);
        RavenSoldierEntity cuffed = soldier(helper, new BlockPos(9, 2, 11), 0, false);
        // De vanger staat in creative: die ziet de bewaker niet, alleen zijn geboeide maat voor hem.
        ServerPlayer captor = ImtTechGameTests.makeSwitchablePlayer(helper, new BlockPos(12, 2, 12), "body-captor", () -> true);
        captor.setYRot(90); // kijkt naar -x: de geboeide staat tussen hem en de bewaker in
        Handcuffs.cuff(captor, cuffed);
        check(Bodies.isBody(cuffed), "Een geboeide soldaat telt niet als lichaam");
        check(!Bodies.isBody(guard), "Een gewone soldaat telt als lichaam");
        GuardRobotEntity robot = helper.spawn(ModEntities.GUARD_ROBOT.get(), new BlockPos(5, 2, 1));
        check(!Bodies.isBody(robot), "Een robot die gewoon staat telt als lichaam");
        robot.knockDown(400);
        check(Bodies.isBody(robot), "Een gevloerde robot telt niet als lichaam");
        helper.succeedWhen(() -> {
            check(guard.senses().state() == RavenAlert.CALLING, "De bewaker meldt zijn geboeide maat niet (" + guard.senses().state() + ")");
            check(guard.getTarget() == null && guard.senses().suspect() == null, "De bewaker ziet de vanger in creative");
        });
    }

    // ================================================================ De taal (stap 6): je eigen robots en drones

    private static final String HEARD_PROGRAM = """
            heard_count = 0
            kind = ""
            sx = 0
            when heard:
                heard_count = heard_count + 1
                kind = sound.kind
                sx = sound.x
            """;

    private static final String SPOT_PROGRAM = """
            facePin = pin(2)
            spotted_count = 0
            who = ""
            when started:
                write(facePin, "everyone")
            when spotted:
                spotted_count = spotted_count + 1
                who = target.name
            """;

    private static final String BODY_PROGRAM = """
            bodies = 0
            bx = 0
            when body_seen:
                bodies = bodies + 1
                bx = body.x
            """;

    /** Een robot hoort lawaai binnen zijn bereik: when heard begint, met de plek en de soort; te ver (of door een muur) niet. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guards_robot_hears")
    public static void robot_hears_noises(GameTestHelper helper) {
        GuardRobotEntity robot = BotSenseGameTests.robot(helper, new BlockPos(6, 2, 10), RobotStyle.COMMANDO,
                BotBoard.EMPTY.with(1, "scan"), HEARD_PROGRAM);
        Vec3 near = helper.absoluteVec(new Vec3(10.5, 2.5, 10.5));
        Vec3 far = helper.absoluteVec(new Vec3(18.5, 2.5, 10.5));
        helper.runAfterDelay(5, () -> Noises.make(helper.getLevel(), near, 16, Noises.Kind.SHOT, null));
        helper.runAfterDelay(15, () -> {
            check(Double.valueOf(1).equals(robot.brain().global("heard_count")), "De robot hoorde het schot niet: " + robot.brain().global("heard_count"));
            check("shot".equals(robot.brain().global("kind")), "sound.kind klopt niet: " + robot.brain().global("kind"));
            check(Double.valueOf(Math.floor(near.x)).equals(robot.brain().global("sx")), "sound.x klopt niet: " + robot.brain().global("sx"));
            Noises.make(helper.getLevel(), far, 8, Noises.Kind.BLOCK, null); // 12 blokken ver, 8 te horen: niet
        });
        helper.runAfterDelay(25, () -> {
            check(Double.valueOf(1).equals(robot.brain().global("heard_count")), "De robot hoorde lawaai dat te ver weg was");
            helper.succeed();
        });
    }

    /** Een robot heeft je pas na een tijdje (robot.alert loopt vol); when spotted begint dan één keer; read(scanPin) blijft meteen waar. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "guards_robot_spots")
    public static void robot_spots_a_player_after_a_while(GameTestHelper helper) {
        GuardRobotEntity robot = BotSenseGameTests.robot(helper, new BlockPos(6, 2, 12), RobotStyle.COMMANDO,
                BotBoard.EMPTY.with(1, "scan").with(2, "face_recognition"), SPOT_PROGRAM);
        ServerPlayer player = player(helper, new BlockPos(6, 2, 6), "robot-spot");
        helper.runAfterDelay(5, () -> {
            check(Double.valueOf(0).equals(robot.brain().global("spotted_count")), "when spotted begon meteen");
            check(robot.alert().level() > 0, "De meter van de robot loopt niet vol");
            check(Boolean.TRUE.equals(robot.brain().pins().read(1)), "read(scanPin) is niet meer meteen waar");
        });
        helper.startSequence()
                .thenWaitUntil(() -> check(Double.valueOf(1).equals(robot.brain().global("spotted_count")), "when spotted begon nog niet (meter " + robot.alert().level() + ")"))
                .thenExecute(() -> {
                    check(player.getName().getString().equals(robot.brain().global("who")), "target.name klopt niet: " + robot.brain().global("who"));
                    check(robot.alertState() == RavenAlert.COMBAT, "Je spel krijgt de toestand van de robot niet mee");
                })
                .thenExecuteAfter(30, () -> check(Double.valueOf(1).equals(robot.brain().global("spotted_count")), "when spotted begon een tweede keer terwijl de speler in beeld bleef"))
                .thenSucceed();
    }

    /** Een robot meldt een lichaam dat hij ziet één keer (when body_seen), en een tweede lichaam apart. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "guards_robot_body")
    public static void robot_reports_a_body_once(GameTestHelper helper) {
        GuardRobotEntity robot = BotSenseGameTests.robot(helper, new BlockPos(6, 2, 12), RobotStyle.COMMANDO,
                BotBoard.EMPTY.with(1, "scan"), BODY_PROGRAM);
        RavenBodyEntity first = ModEntities.RAVEN_BODY.get().create(helper.getLevel());
        check(first != null, "Geen lichaam");
        Vec3 at = helper.absoluteVec(new Vec3(6.5, 2, 7.5));
        first.moveTo(at.x, at.y, at.z, 0, 0);
        helper.getLevel().addFreshEntity(first);
        helper.runAfterDelay(40, () -> {
            check(Double.valueOf(1).equals(robot.brain().global("bodies")), "De robot zag het lichaam niet: " + robot.brain().global("bodies"));
            check(Double.valueOf(Math.floor(at.x)).equals(robot.brain().global("bx")), "body.x klopt niet: " + robot.brain().global("bx"));
        });
        helper.runAfterDelay(80, () -> {
            check(Double.valueOf(1).equals(robot.brain().global("bodies")), "De robot meldde hetzelfde lichaam nog eens");
            RavenBodyEntity second = ModEntities.RAVEN_BODY.get().create(helper.getLevel());
            Vec3 at2 = helper.absoluteVec(new Vec3(8.5, 2, 6.5));
            second.moveTo(at2.x, at2.y, at2.z, 0, 0);
            helper.getLevel().addFreshEntity(second);
        });
        helper.runAfterDelay(130, () -> {
            check(Double.valueOf(2).equals(robot.brain().global("bodies")), "De robot zag het tweede lichaam niet: " + robot.brain().global("bodies"));
            helper.succeed();
        });
    }

    /** De nieuwe woorden werken in een robot- of droneprogramma, niet in een serverdiagram. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "guards")
    public static void guard_words_only_work_in_robot_programs(GameTestHelper helper) {
        ScriptParser.parse("when heard:\n    goto(sound.x, sound.y, sound.z)\n", ProgramKind.ROBOT);
        ScriptParser.parse("when spotted:\n    aim(target.x, target.y, target.z)\n    send(\"contact \" + target.name)\n", ProgramKind.DRONE);
        ScriptParser.parse("when body_seen:\n    send(\"man down\")\n", ProgramKind.ROBOT);
        ScriptParser.parse("when every(5):\n    if robot.alert > 50:\n        turn_left()\n", ProgramKind.ROBOT);
        for (String bad : new String[] {"when heard:\n    pass\n", "when spotted:\n    pass\n", "when body_seen:\n    pass\n"}) {
            try {
                ScriptParser.parse(bad, ProgramKind.SERVER);
                throw new GameTestAssertException("Dit hoort niet in een serverdiagram te mogen: " + bad.split(":")[0]);
            } catch (LogicException expected) {
                // Goed: de parser zegt waar het wel werkt.
            }
        }
        helper.succeed();
    }

    // ================================================================ De toren (stap 7)

    /**
     * Biebia's bewakersrobots (jouw antwoord 6): een Biebia's Industries Tower van niemand zet op de Robot Docks van de
     * lobby en de beveiligingsverdieping guard robots, aan, van niemand, in de squad "Tower guards" met het programma
     * "Tower guard".
     */
    @GameTest(template = "biebia_tower", timeoutTicks = 400, batch = "tower_robots")
    public static void biebia_tower_has_guard_robots(GameTestHelper helper) {
        int base = helper.absolutePos(BlockPos.ZERO).getY();
        helper.runAfterDelay(100, () -> {
            List<GuardRobotEntity> robots = helper.getLevel().getEntitiesOfClass(GuardRobotEntity.class, helper.getBounds());
            check(robots.size() == Math.round(ImtConfig.Security.TOWER_ROBOTS),
                    "In de toren staan " + robots.size() + " robots in plaats van " + Math.round(ImtConfig.Security.TOWER_ROBOTS));
            int lobby = 0;
            int security = 0;
            for (GuardRobotEntity robot : robots) {
                String name = robot.getName().getString();
                check(robot.owner() == null && robot.isActive(), name + " is van iemand, of staat uit");
                check(robot.program() != null && robot.program().name().equals(TowerRobots.PROGRAM_NAME), name + " heeft Biebia's programma niet");
                check(TowerRobots.SQUAD.equals(robot.squad()), name + " zit niet in de squad Tower guards: " + robot.squad());
                check(robot.board().has(com.senne.imttech.bot.BotModule.SCAN) && robot.board().has(com.senne.imttech.bot.BotModule.LASER),
                        name + " mist modules: " + robot.board());
                check(robot.home() != null && helper.getLevel().getBlockState(robot.home()).is(ModBlocks.ROBOT_DOCK.get()),
                        name + " heeft geen Robot Dock als thuis");
                if (robot.blockPosition().getY() - base < 20) {
                    lobby++;
                } else {
                    security++;
                }
            }
            check(lobby == 2 && security == 2, "Niet twee in de lobby en twee op de beveiligingsverdieping: " + lobby + " / " + security);
            helper.succeed();
        });
    }

    /**
     * Een bewakersrobot van Biebia die iemand heeft, meldt "contact" aan Biebia's kern; diens diagram "Security" geeft
     * de hele squad "Tower guards" de opdracht "alarm" (een robot van niemand hoort bij een kern van niemand).
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "tower_robots_contact")
    public static void biebia_robot_contact_alarms_the_squad(GameTestHelper helper) {
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), null);
        BlockPos rack = new BlockPos(16, 3, 16);
        ImtTechGameTests.placeServer(helper, rack, core);
        GuardRobotEntity spotter = BotSenseGameTests.robot(helper, new BlockPos(6, 2, 10), RobotStyle.COMMANDO, TowerRobots.board(),
                "when started:\n    pass\n");
        spotter.setProgram(TowerRobots.program());
        spotter.setSquad(TowerRobots.SQUAD);
        GuardRobotEntity mate = BotSenseGameTests.robot(helper, new BlockPos(12, 2, 12), RobotStyle.COMMANDO, TowerRobots.board(),
                "got = 0\nwhen ordered(\"alarm\"):\n    got = got + 1\n");
        mate.setSquad(TowerRobots.SQUAD);
        ServerPlayer stranger = player(helper, new BlockPos(6, 2, 5), "tower-stranger");
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, rack, core);
            core.ensureSystem(helper.getLevel()); // Biebia's systeem, met het diagram "Security"
            check(core.logic().program(com.senne.imttech.structures.TowerGuards.SECURITY) != null, "Biebia's kern heeft het diagram Security niet");
        });
        helper.succeedWhen(() -> {
            check(spotter.alertState() == RavenAlert.COMBAT, "De robot heeft de vreemde nog niet (meter " + spotter.alert().level() + ")");
            check(Double.valueOf(1).equals(mate.brain().global("got")), "De squad kreeg de opdracht alarm niet: " + mate.brain().global("got"));
            check(stranger.isAlive(), "De vreemde is al dood");
        });
    }
}
