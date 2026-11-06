package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Firearms;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunSkill;
import com.senne.imttech.guns.GunSkills;
import com.senne.imttech.guns.GunSkillsServer;
import com.senne.imttech.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de skills (hoofdstuk 52, stap 3). Jouw keuzes: punten die je verdeelt (meer tactiek), punten door alles
 * wat je met een pistool doet, en skills die globaal én per wapen bestaan en bij elkaar optellen.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class SkillGameTests {
    private SkillGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Ervaring geeft punten (elk volgend punt kost meer); een punt uitgeven maakt herladen korter; globaal en per wapen tellen op. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "guns")
    public static void skills_earn_points_and_make_reloading_faster(GameTestHelper helper) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 4), "skill-learner");
        player.getInventory().selected = 0;
        ItemStack gun = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        // Herladen uit de pouch (30 ticks): lang genoeg om het verschil na afronden te zien.
        int plain = Firearms.ticksFor(player, Firearms.ActionKind.RELOAD_POUCH, gun);
        GunSkills start = GunSkillsServer.of(player);
        check(start.points() == 0 && start.earned() == 0, "Een nieuwe speler hoort geen punten te hebben");
        int first = start.nextPointXp();
        GunSkillsServer.award(player, first - 1);
        check(GunSkillsServer.of(player).points() == 0, "Een punt kwam te vroeg");
        GunSkillsServer.award(player, 1);
        GunSkills one = GunSkillsServer.of(player);
        check(one.points() == 1 && one.earned() == 1 && one.xp() == 0, "Het eerste punt kwam niet precies op tijd");
        check(one.nextPointXp() > first, "Het tweede punt kost niet meer dan het eerste");
        // Een punt op herladen voor de Glock: sneller.
        GunSkillsServer.award(player, 10_000);
        GunSkills rich = GunSkillsServer.of(player);
        check(rich.points() > 10, "Veel ervaring gaf weinig punten: " + rich.points());
        check(GunSkillsServer.learn(player, "glock_19", GunSkill.RELOAD), "Een punt op herladen (Glock) lukte niet");
        int faster = Firearms.ticksFor(player, Firearms.ActionKind.RELOAD_POUCH, gun);
        check(faster < plain, "Herladen werd niet sneller: " + faster + " van " + plain);
        check(GunSkillsServer.learn(player, GunSkills.GLOBAL, GunSkill.RELOAD), "Een globaal punt op herladen lukte niet");
        int fastest = Firearms.ticksFor(player, Firearms.ActionKind.RELOAD_POUCH, gun);
        check(fastest < faster, "Globaal en per wapen tellen niet op: " + fastest + " van " + faster);
        GunSkills after = GunSkillsServer.of(player);
        check(after.level("glock_19", GunSkill.RELOAD) == 1 && after.level(GunSkills.GLOBAL, GunSkill.RELOAD) == 1, "De niveaus kloppen niet");
        check(after.spent() == 1 + 2, "Een wapenpunt kost 1 en een globaal punt 2, samen 3; uitgegeven: " + after.spent());
        check(after.cost("glock_19", GunSkill.RELOAD) == 2, "Het tweede niveau hoort 2 punten te kosten");
        check(!GunSkillsServer.learn(player, "unknown_gun", GunSkill.RELOAD), "Een onbekend wapen nam een punt aan");
        // Tot het maximum, en niet verder.
        int max = GunSkills.maxLevel();
        while (GunSkillsServer.of(player).level("glock_19", GunSkill.RELOAD) < max) {
            check(GunSkillsServer.learn(player, "glock_19", GunSkill.RELOAD), "Een niveau lukte niet");
        }
        check(!GunSkillsServer.learn(player, "glock_19", GunSkill.RELOAD), "Boven het maximum lukte nog een niveau");
        float factor = GunSkillsServer.of(player).factor(GunSkill.RELOAD, FirearmType.GLOCK_19);
        check(factor >= ImtConfig.Guns.SKILL_MIN_FACTOR - 1.0E-4 && factor < 1, "De factor klopt niet: " + factor);
        // Schieten geeft ervaring.
        GunSkills before = GunSkillsServer.of(player);
        Firearms.forgetCooldown(player);
        check(Firearms.fire(player, player.getYRot(), player.getXRot(), false) == Firearms.Result.FIRED, "Het schot ging niet af");
        GunSkills shot = GunSkillsServer.of(player);
        check(shot.xp() + (shot.earned() - before.earned()) * 1_000_000 > before.xp() || shot.earned() > before.earned(),
                "Een schot gaf geen ervaring");
        player.setData(ModAttachments.GUN_SKILLS, GunSkills.EMPTY);
        helper.succeed();
    }
}
