package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.raven.RavenAlert;
import com.senne.imttech.raven.RavenRoutes;
import com.senne.imttech.raven.RavenSoldierEntity;
import com.senne.imttech.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de bewakers zoals in Hitman (hoofdstuk 60): een Raven-soldaat kijkt in een kegel, het duurt even voor hij
 * je heeft, sluipen helpt, en raakt hij je kwijt dan zoekt hij waar hij je het laatst zag.
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
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "guards")
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
    @GameTest(template = ARENA, timeoutTicks = 150, batch = "guards")
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
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guards")
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
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "guards")
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
}
