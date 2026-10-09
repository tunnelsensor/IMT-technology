package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.c4.C4Blast;
import com.senne.starktech.c4.CrackData;
import com.senne.starktech.c4.Cracks;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor het herstellen van barsten (hoofdstuk 11 van het plan). Jouw wens: "elke blok met barsten
 * kun je of vervangen met een nieuwe blok of kun je met een pasta opvullen, de blok is altijd dan net
 * iets minder sterk".
 */
@GameTestHolder(StarkTech.MODID)
@PrefixGameTestTemplate(false)
public final class CrackGameTests {
    private CrackGameTests() {}

    private static final String ARENA = "empty";

    /** Het blok waar de tests C4 tegen plakken. */
    private static final BlockPos BLOCK = new BlockPos(8, 3, 8);

    /** Plakt een C4 tegen de oostkant van {@code pos} en laat hem meteen afgaan. */
    private static void blast(GameTestHelper helper, BlockPos pos) {
        C4GameTests.boom(helper, C4GameTests.stick(helper, ModBlocks.TIMED_C4.get(), pos, Direction.EAST));
    }

    /**
     * Laat de speler Repair Paste op de oostkant van {@code pos} smeren (heeft hij geen pasta in zijn hand,
     * dan krijgt hij er vier). Geeft terug of het lukte.
     */
    private static boolean paste(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        // Een testspeler komt binnen met "gratis bouwen" van creative (zie makeSurvivalOwner): uit, anders
        // gaat er nooit pasta op.
        player.getAbilities().instabuild = false;
        if (!player.getMainHandItem().is(ModItems.REPAIR_PASTE.get())) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REPAIR_PASTE.get(), 4));
        }
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.EAST, 0.5), Direction.EAST, abs, false);
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction();
    }

    private static float strength(GameTestHelper helper, BlockPos pos) {
        BlockPos abs = helper.absolutePos(pos);
        return C4Blast.strength(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
    }

    /** Houdt de wereld nog iets bij over deze plek (ook als er nu een ander blok staat)? */
    private static boolean remembered(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        return CrackData.get(level).in(level.dimension()).containsKey(helper.absolutePos(pos).asLong());
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * Jouw keuze: barsten genezen niet meer vanzelf, ze blijven (ook na een herstart). Na één C4 heeft
     * obsidiaan barsten, en die staan nog precies zo in de wereld als je hem opslaat en weer inlaadt.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "cracks")
    public static void cracks_stay_and_are_saved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(BLOCK, Blocks.OBSIDIAN);
        blast(helper, BLOCK);
        float damage = Cracks.damage(level, helper.absolutePos(BLOCK));
        check(damage > 0, "Na één C4 zou het obsidiaan barsten moeten hebben");
        CrackData.Crack saved = CrackData.afterReload(CrackData.get(level), level.dimension(), helper.absolutePos(BLOCK),
                level.registryAccess());
        check(saved != null && saved.damage() == damage && saved.block() == Blocks.OBSIDIAN && saved.patches() == 0,
                "Na opslaan en inladen zijn de barsten anders: " + saved);
        helper.succeed();
    }

    /**
     * Repair Paste: de barsten zijn weg, het blok is 15% zwakker (obsidiaan 20 -> 17 -> 14), en er gaat
     * één pasta op. Zwakker dan 40% wordt een blok nooit.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "cracks")
    public static void paste_fills_cracks_but_weakens(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 3, 20), "crack-fixer");
        helper.setBlock(BLOCK, Blocks.OBSIDIAN);
        check(strength(helper, BLOCK) == 20, "Nieuw obsidiaan zou 20 breekpunten moeten kunnen hebben");
        for (int patches = 1; patches <= 2; patches++) {
            blast(helper, BLOCK);
            check(Cracks.damage(level, helper.absolutePos(BLOCK)) > 0, "Na de C4 zou het obsidiaan barsten moeten hebben");
            check(paste(helper, player, BLOCK), "Plamuren lukte niet (keer " + patches + ")");
            check(Cracks.damage(level, helper.absolutePos(BLOCK)) == 0, "Na het plamuren zijn er nog barsten");
            check(Cracks.patches(level, helper.absolutePos(BLOCK)) == patches, "Het blok zou " + patches + " keer geplamuurd moeten zijn");
            float expected = 20 * (1 - 0.15f * patches);
            check(Math.abs(strength(helper, BLOCK) - expected) < 0.01f,
                    "Na " + patches + " keer plamuren zou obsidiaan " + expected + " moeten kunnen hebben, niet " + strength(helper, BLOCK));
            check(player.getMainHandItem().getCount() == 4 - patches, "Er zou " + patches + " pasta op moeten zijn");
            helper.assertBlockPresent(Blocks.OBSIDIAN, BLOCK);
        }
        check(Math.abs(Cracks.factor(3) - 0.55f) < 0.001f && Cracks.factor(4) == 0.40f && Cracks.factor(10) == 0.40f,
                "Plamuren zou per keer 15% moeten afnemen en nooit onder 40% komen");
        helper.succeed();
    }

    /** Geen barsten: dan doet de pasta niets (en gaat er ook geen op). */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "cracks")
    public static void paste_needs_cracks(GameTestHelper helper) {
        ServerPlayer player = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 3, 20), "crack-fixer");
        helper.setBlock(BLOCK, Blocks.STONE);
        check(!paste(helper, player, BLOCK), "Plamuren lukte op een blok zonder barsten");
        check(player.getMainHandItem().getCount() == 4, "Er ging pasta op aan een blok zonder barsten");
        check(Cracks.patches(helper.getLevel(), helper.absolutePos(BLOCK)) == 0, "Een blok zonder barsten werd geplamuurd");
        helper.succeed();
    }

    /**
     * Vervangen: een nieuw blok is weer helemaal sterk. Wie het geplamuurde blok weghakt en er nieuw
     * obsidiaan zet, heeft weer 20; en ook als het blok op een andere manier verandert (hier met
     * setblock, net als een zuiger of een explosie), vergeet de wereld de barsten meteen.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "cracks")
    public static void new_block_is_strong_again(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 3, 20), "crack-fixer");
        helper.setBlock(BLOCK, Blocks.OBSIDIAN);
        blast(helper, BLOCK);
        check(paste(helper, player, BLOCK), "Plamuren lukte niet");
        check(strength(helper, BLOCK) < 20, "Na het plamuren zou het obsidiaan zwakker moeten zijn");

        check(ZoneGameTests.breakBlock(helper, player, BLOCK), "De speler kon het obsidiaan niet weghakken");
        check(!remembered(helper, BLOCK), "Het blok is weg, maar de wereld onthoudt het plamuren nog");
        helper.setBlock(BLOCK, Blocks.OBSIDIAN);
        check(strength(helper, BLOCK) == 20, "Nieuw obsidiaan zou weer 20 moeten kunnen hebben, niet " + strength(helper, BLOCK));

        // Een ander blok ertussen (zoals een zuiger of explosie doet): de barsten zijn meteen vergeten.
        blast(helper, BLOCK);
        check(remembered(helper, BLOCK), "Na de C4 zou de wereld de barsten moeten onthouden");
        helper.setBlock(BLOCK, Blocks.DIRT);
        check(!remembered(helper, BLOCK), "Er staat aarde, maar de wereld onthoudt de barsten van het obsidiaan nog");
        helper.setBlock(BLOCK, Blocks.OBSIDIAN);
        check(Cracks.damage(level, helper.absolutePos(BLOCK)) == 0, "Nieuw obsidiaan heeft de barsten van het oude");
        helper.succeed();
    }

    /**
     * Mijn invulling: in een beveiligde zone mag alleen wie er mag bouwen plamuren (anders maakt een
     * inbreker een muur met TNT en pasta steeds zwakker). Een vreemde lukt het niet, de eigenaar wel.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "cracks")
    public static void paste_in_a_zone_only_for_builders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 20), "zone-owner");
        ServerPlayer outsider = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(14, 3, 20), "zone-outsider");
        SecurityCoreBlockEntity core = ZoneGameTests.zoneCore(helper, owner);
        helper.setBlock(BLOCK, Blocks.OBSIDIAN);
        blast(helper, BLOCK);
        float damage = Cracks.damage(level, helper.absolutePos(BLOCK));
        check(damage > 0, "Na de C4 zou het obsidiaan barsten moeten hebben");

        check(!paste(helper, outsider, BLOCK), "Een vreemde kon in de zone plamuren");
        check(Cracks.damage(level, helper.absolutePos(BLOCK)) == damage, "De barsten van de vreemde zijn toch opgevuld");
        check(outsider.getMainHandItem().getCount() == 4, "De vreemde verloor pasta");

        check(paste(helper, owner, BLOCK), "De eigenaar kon in zijn eigen zone niet plamuren");
        check(Cracks.damage(level, helper.absolutePos(BLOCK)) == 0 && Cracks.patches(level, helper.absolutePos(BLOCK)) == 1,
                "Na het plamuren door de eigenaar zouden de barsten weg moeten zijn");
        ZoneGameTests.finish(helper, core);
    }
}
