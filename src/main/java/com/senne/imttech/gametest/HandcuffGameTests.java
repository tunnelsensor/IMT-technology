package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.cuffs.HandcuffKeyItem;
import com.senne.imttech.cuffs.Handcuffs;
import com.senne.imttech.cuffs.HandcuffsItem;
import com.senne.imttech.raven.RavenSoldierEntity;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.robot.GuardRobotEntity;
import com.senne.imttech.robot.RobotState;
import com.senne.imttech.weapons.Flashbang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de handboeien (hoofdstuk 48, 4 november): twee seconden vasthouden, dan niets meer met je handen, meetrekken
 * aan de lijn, los met de sleutel of door te worstelen; meteen bij wie weerloos is; ook een robot en een Raven-soldaat.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class HandcuffGameTests {
    private HandcuffGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** De vanger (met twee paar boeien in zijn hand) en wie hij wil boeien, twee blokken verder naar het zuiden. */
    private record Pair(ServerPlayer captor, ServerPlayer victim) {}

    private static Pair pair(GameTestHelper helper, String suffix) {
        ServerPlayer captor = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 3, 5), "cuff-captor-" + suffix);
        captor.getAbilities().instabuild = false;
        captor.getInventory().clearContent();
        captor.getInventory().setItem(0, new ItemStack(ModItems.HANDCUFFS.get(), 2));
        captor.getInventory().selected = 0;
        look(captor, 0);
        ServerPlayer victim = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 3, 7), "cuff-victim-" + suffix);
        victim.getAbilities().instabuild = false;
        victim.getInventory().clearContent();
        look(victim, 0);
        return new Pair(captor, victim);
    }

    /** Laat een speler deze kant op kijken (0 = zuid, 180 = noord). */
    private static void look(ServerPlayer player, float yaw) {
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.yRotO = yaw;
        player.setXRot(0);
    }

    private static void teleport(GameTestHelper helper, ServerPlayer player, double x, double y, double z) {
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        player.teleportTo(at.x, at.y, at.z);
    }

    /**
     * Een gezonde speler boei je door twee seconden vast te houden (één tick eerder nog niet); dan houdt de vanger hem
     * vast en is er een paar boeien op. Geboeid kan hij niets: een blok hakken, een fakkel zetten of een varken slaan
     * gebeurt niet. Loopt de vanger weg, dan wordt hij meegetrokken (tot de vanger te ver is: dan is hij los van de
     * lijn, maar nog geboeid). Met de sleutel is hij vrij en heeft de vanger zijn boeien terug.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "cuffs")
    public static void handcuffs_catch_block_drag_and_key(GameTestHelper helper) {
        Pair p = pair(helper, "a");
        ServerPlayer captor = p.captor();
        ServerPlayer victim = p.victim();
        ItemStack cuffs = captor.getMainHandItem();
        check(!Handcuffs.instant(victim), "Een gezonde speler zou je twee seconden moeten vasthouden");
        check(Handcuffs.cannotCuff(captor, victim) == null, "De vanger zou hem moeten kunnen boeien: " + Handcuffs.cannotCuff(captor, victim));
        check(Handcuffs.cannotCuff(captor, captor) != null, "Jezelf boeien zou niet mogen");
        // De rechtsklik begint het vasthouden; na holdSeconds zijn de boeien om, een tick eerder nog niet.
        InteractionResult result = cuffs.getItem().interactLivingEntity(cuffs, captor, victim, InteractionHand.MAIN_HAND);
        check(result == InteractionResult.CONSUME && captor.isUsingItem(), "De rechtsklik zou het vasthouden moeten beginnen");
        int needed = Math.round(ImtConfig.Handcuffs.HOLD_SECONDS * 20);
        for (int tick = 1; tick < needed; tick++) {
            check(!HandcuffsItem.progress(captor, cuffs, tick), "Na " + tick + " ticks zouden de boeien nog niet om mogen zijn");
        }
        check(!Handcuffs.isCuffed(victim), "Voor het einde van het vasthouden zou hij nog vrij moeten zijn");
        check(HandcuffsItem.progress(captor, cuffs, needed), "Na " + needed + " ticks zouden de boeien om moeten zijn");
        check(Handcuffs.isCuffed(victim), "Het slachtoffer zou geboeid moeten zijn");
        check(captor.getUUID().equals(Handcuffs.holderOf(victim)), "De vanger zou hem meteen moeten vasthouden");
        check(cuffs.getCount() == 1, "Eén paar boeien zou op moeten zijn, er zijn er " + cuffs.getCount());
        check(!captor.isUsingItem(), "Na het omdoen zou de knop los moeten zijn");
        // Geboeid: niets met je handen.
        BlockPos stone = new BlockPos(5, 3, 9);
        helper.setBlock(stone, Blocks.STONE);
        victim.gameMode.destroyBlock(helper.absolutePos(stone));
        helper.assertBlockPresent(Blocks.STONE, stone);
        victim.getInventory().setItem(0, new ItemStack(Items.TORCH));
        victim.getInventory().selected = 0;
        BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(stone)).add(0, 0.5, 0), Direction.UP,
                helper.absolutePos(stone), false);
        victim.gameMode.useItemOn(victim, helper.getLevel(), victim.getMainHandItem(), InteractionHand.MAIN_HAND, top);
        helper.assertBlockPresent(Blocks.AIR, stone.above());
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(5, 3, 11));
        victim.getInventory().setItem(0, ItemStack.EMPTY);
        victim.attack(pig);
        check(pig.getLastHurtByMob() == null && pig.getHealth() == pig.getMaxHealth(), "Geboeid zou hij het varken niet mogen slaan");
        // De vanger loopt weg: het slachtoffer staat meteen weer vlak achter hem (echt vast, geen lijn).
        teleport(helper, captor, 5.5, 3, 13.5);
        helper.runAfterDelay(3, () -> {
            check(victim.distanceTo(captor) < 2.0, "Echt vast: hij zou vlak achter de vanger moeten staan, niet op " + victim.distanceTo(captor));
            check(captor.getUUID().equals(Handcuffs.holderOf(victim)), "Op 6 blokken zou de vanger hem nog vast moeten hebben");
            // Ineens heel ver weg (het slachtoffer staat nu vlak achter hem, op 13.5): dan is hij hem kwijt, maar de boeien blijven om.
            teleport(helper, captor, 5.5, 3, 30.5);
        });
        // (Pas na een halve seconde: een klik vlak na het boeien telt als de knop die nog ingedrukt is.)
        helper.runAfterDelay(15, () -> {
            check(Handcuffs.holderOf(victim) == null, "Verder dan letGo zou de vanger hem los moeten zijn");
            check(Handcuffs.isCuffed(victim), "Zonder lijn zou hij nog wel geboeid moeten zijn");
            // Weer vastpakken (rechtsklik met de boeien op wie geboeid is), en dan de sleutel.
            teleport(helper, captor, 5.5, 3, 5.5);
            ItemStack hand = captor.getMainHandItem();
            hand.getItem().interactLivingEntity(hand, captor, victim, InteractionHand.MAIN_HAND);
            check(captor.getUUID().equals(Handcuffs.holderOf(victim)), "Rechtsklik met de boeien zou hem weer vastpakken");
            ItemStack key = new ItemStack(ModItems.HANDCUFF_KEY.get());
            captor.getInventory().setItem(1, key);
            check(key.getItem() instanceof HandcuffKeyItem, "De sleutel zou een HandcuffKeyItem moeten zijn");
            key.getItem().interactLivingEntity(key, captor, victim, InteractionHand.MAIN_HAND);
            check(!Handcuffs.isCuffed(victim), "Met de sleutel zou hij vrij moeten zijn");
            check(captor.getInventory().countItem(ModItems.HANDCUFFS.get()) == 2, "De vanger zou zijn boeien terug moeten hebben: "
                    + captor.getInventory().countItem(ModItems.HANDCUFFS.get()));
            // Vrij: een blok hakken lukt weer.
            victim.gameMode.destroyBlock(helper.absolutePos(stone));
            helper.assertBlockPresent(Blocks.AIR, stone);
            helper.succeed();
        });
    }

    /**
     * Wie weerloos is, boei je meteen: een verblinde speler (flashbang), een robot die neerligt, een soldaat die bijna
     * dood is. Een geboeide speler worstelt zich los door struggleSeconds te sluipen (de boeien breken); een robot
     * blijft geboeid staan (Handcuffed, geen programma) tot de sleutel; een vastgehouden soldaat komt mee, en wurmt
     * zich los zodra niemand hem meer vasthoudt.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "cuffs")
    public static void handcuffs_instant_struggle_robot_and_soldier(GameTestHelper helper) {
        float before = ImtConfig.Handcuffs.STRUGGLE_SECONDS;
        ImtConfig.Handcuffs.STRUGGLE_SECONDS = 1.0f; // 20 ticks sluipen
        Pair p = pair(helper, "b");
        ServerPlayer captor = p.captor();
        ServerPlayer victim = p.victim();
        ItemStack cuffs = captor.getMainHandItem();
        Flashbang.markBlinded(victim, 100);
        check(Handcuffs.instant(victim), "Een verblinde speler zou meteen te boeien moeten zijn");
        HandcuffsItem.aim(captor, victim);
        check(HandcuffsItem.progress(captor, cuffs, 1), "Een verblinde speler zou meteen geboeid moeten zijn");
        victim.setShiftKeyDown(true);
        // Een robot die neerligt: meteen. Hij blijft staan tot de sleutel, ook als hij weer "opstaat".
        GuardRobotEntity robot = helper.spawnWithNoFreeWill(ModEntities.GUARD_ROBOT.get(), new BlockPos(9, 3, 7));
        robot.setOwner(captor);
        robot.knockDown(400);
        check(Handcuffs.instant(robot), "Een robot die neerligt zou meteen te boeien moeten zijn");
        teleport(helper, captor, 9.5, 3, 5.5);
        HandcuffsItem.aim(captor, robot);
        check(HandcuffsItem.progress(captor, cuffs, 1), "De robot zou meteen geboeid moeten zijn");
        check(Handcuffs.isCuffed(robot) && robot.isCuffed(), "De robot zou geboeid moeten zijn");
        check(cuffs.isEmpty(), "Twee paar boeien zouden op moeten zijn");
        robot.standUp();
        check(robot.state() == RobotState.CUFFED, "Een geboeide robot die opstaat zou Handcuffed moeten zijn, niet " + robot.state());
        // Een soldaat: gezond niet meteen, bijna dood wel. Aan de lijn komt hij mee.
        RavenSoldierEntity soldier = helper.spawn(ModEntities.RAVEN_SOLDIER.get(), new BlockPos(14, 3, 7));
        check(!Handcuffs.instant(soldier), "Een gezonde soldaat zou je moeten vasthouden");
        soldier.setHealth(1.0f);
        check(Handcuffs.instant(soldier), "Een soldaat die bijna dood is zou meteen te boeien moeten zijn");
        captor.getInventory().setItem(0, new ItemStack(ModItems.HANDCUFFS.get()));
        teleport(helper, captor, 14.5, 3, 5.5);
        HandcuffsItem.aim(captor, soldier);
        check(HandcuffsItem.progress(captor, captor.getMainHandItem(), 1), "De soldaat zou meteen geboeid moeten zijn");
        check(Handcuffs.isCuffed(soldier) && soldier.getTarget() == null, "De soldaat zou geboeid moeten zijn en niemand meer als doel hebben");
        double startZ = soldier.getZ();
        teleport(helper, captor, 14.5, 3, 14.5);
        helper.runAfterDelay(40, () -> {
            check(soldier.getZ() > startZ + 1.5, "Aan de lijn zou de soldaat mee moeten komen: van " + startZ + " naar " + soldier.getZ());
            check(Handcuffs.isCuffed(soldier), "Onderweg zou de soldaat geboeid moeten blijven");
            check(!Handcuffs.isCuffed(victim), "Na een seconde sluipen zou de speler zich los moeten hebben geworsteld");
            check(captor.getInventory().countItem(ModItems.HANDCUFFS.get()) == 0, "Losgeworstelde boeien zijn kapot: niemand krijgt ze terug");
            check(Handcuffs.isCuffed(robot) && robot.state() == RobotState.CUFFED, "De robot zou nog geboeid moeten zijn");
            // De vanger laat de soldaat los: na struggleSeconds wurmt hij zich los.
            Handcuffs.toggleHold(captor, soldier);
            check(Handcuffs.holderOf(soldier) == null, "Rechtsklik zou de soldaat loslaten");
        });
        helper.runAfterDelay(70, () -> {
            check(!Handcuffs.isCuffed(soldier), "Een losgelaten soldaat zou zich na struggleSeconds los moeten wurmen");
            // De robot: de sleutel maakt hem vrij, en hij mag weer.
            ItemStack key = new ItemStack(ModItems.HANDCUFF_KEY.get());
            captor.getInventory().setItem(1, key);
            key.getItem().interactLivingEntity(key, captor, robot, InteractionHand.MAIN_HAND);
            check(!robot.isCuffed() && robot.state() != RobotState.CUFFED, "Met de sleutel zou de robot vrij moeten zijn: " + robot.state());
            check(captor.getInventory().countItem(ModItems.HANDCUFFS.get()) == 1, "De sleutel geeft de boeien terug");
            ImtConfig.Handcuffs.STRUGGLE_SECONDS = before;
            helper.succeed();
        });
    }
}
