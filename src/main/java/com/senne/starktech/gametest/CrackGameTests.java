package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.c4.C4Blast;
import com.senne.starktech.c4.CrackData;
import com.senne.starktech.c4.Cracks;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.repair.CrackMachineBlock;
import com.senne.starktech.repair.CrackMachineBlockEntity;
import com.senne.starktech.repair.FromBlockItem;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.station.ChargerBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
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

    // ================================================================ Stap 2: gebarsten blokken en de Crusher

    /** Een klein klapje van 3 breekpunten op alleen dit blok: barsten, maar het breekt niet (steen kan 6 hebben). */
    private static void dent(GameTestHelper helper, BlockPos pos) {
        BlockPos abs = helper.absolutePos(pos);
        C4Blast.breach(helper.getLevel(), Vec3.atCenterOf(abs).add(0.95, 0, 0), 3.0f, 3.0f, here -> here.equals(abs), null);
    }

    /**
     * Een testspeler die echt in survival speelt (anders valt er niets uit wat hij breekt), met een
     * diamanten pikhouweel in zijn hand.
     */
    private static ServerPlayer miner(GameTestHelper helper) {
        ServerPlayer player = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 3, 20), "crack-miner");
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        return player;
    }

    /** Wat er rond dit blok op de grond ligt (en ruimt het meteen op voor de volgende keer). */
    private static List<ItemStack> pickUp(GameTestHelper helper, BlockPos pos) {
        List<ItemStack> found = new ArrayList<>();
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(2))) {
            found.add(item.getItem().copy());
            item.discard();
        }
        return found;
    }

    private static boolean crackedOf(ItemStack stack, Block block) {
        return stack.is(ModItems.CRACKED_BLOCK.get()) && FromBlockItem.block(stack) == block;
    }

    /**
     * Jouw wens: "als je de blok vervangt hou je dus de gebarsten blok". Weghakken: stenen bakstenen met
     * barsten geven gebarsten stenen bakstenen, steen met barsten gebarsten steen (geen keien), en
     * geplamuurd obsidiaan (geen barsten meer, wel zwakker) gebarsten obsidiaan. Steen zonder barsten
     * geeft gewoon keien.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "cracks")
    public static void cracked_blocks_drop_cracked(GameTestHelper helper) {
        ServerPlayer player = miner(helper);
        for (Block block : new Block[] {Blocks.STONE_BRICKS, Blocks.STONE}) {
            helper.setBlock(BLOCK, block);
            dent(helper, BLOCK);
            check(Cracks.damage(helper.getLevel(), helper.absolutePos(BLOCK)) > 0, "Het klapje gaf " + block + " geen barsten");
            check(ZoneGameTests.breakBlock(helper, player, BLOCK), "Hakken lukte niet");
            List<ItemStack> drops = pickUp(helper, BLOCK);
            check(drops.size() == 1 && crackedOf(drops.get(0), block) && drops.get(0).getCount() == 1,
                    "Gebarsten " + block + " zou één gebarsten blok moeten geven, niet " + drops);
        }

        helper.setBlock(BLOCK, Blocks.OBSIDIAN);
        dent(helper, BLOCK);
        check(paste(helper, player, BLOCK), "Plamuren lukte niet");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        check(ZoneGameTests.breakBlock(helper, player, BLOCK), "Hakken lukte niet");
        List<ItemStack> drops = pickUp(helper, BLOCK);
        check(drops.size() == 1 && crackedOf(drops.get(0), Blocks.OBSIDIAN), "Geplamuurd obsidiaan gaf " + drops);

        helper.setBlock(BLOCK, Blocks.STONE);
        check(ZoneGameTests.breakBlock(helper, player, BLOCK), "Hakken lukte niet");
        drops = pickUp(helper, BLOCK);
        check(drops.size() == 1 && drops.get(0).is(Items.COBBLESTONE), "Steen zonder barsten gaf " + drops);
        helper.succeed();
    }

    /** Wat C4 kapotmaakt, valt eruit zoals altijd, ook als het blok al barsten had (mijn invulling). */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "cracks")
    public static void blasted_blocks_drop_normally(GameTestHelper helper) {
        helper.setBlock(BLOCK, Blocks.STONE_BRICKS);
        dent(helper, BLOCK);
        blast(helper, BLOCK);
        helper.assertBlockNotPresent(Blocks.STONE_BRICKS, BLOCK);
        List<ItemStack> drops = pickUp(helper, BLOCK);
        check(drops.stream().anyMatch(stack -> stack.is(Items.STONE_BRICKS)),
                "De opgeblazen stenen bakstenen vielen er niet uit: " + drops);
        check(drops.stream().noneMatch(stack -> stack.is(ModItems.CRACKED_BLOCK.get())),
                "Een opgeblazen blok gaf een gebarsten blok: " + drops);
        helper.succeed();
    }

    /** Een Crusher of Compactor op deze plek, met (als {@code power}) een warmtegenerator vol stroom ernaast. */
    private static CrackMachineBlockEntity machine(GameTestHelper helper, Block block, BlockPos pos, boolean power) {
        helper.setBlock(pos, block);
        if (power) {
            helper.setBlock(pos.west(), ModBlocks.HEAT_GENERATOR.get());
            ((ChargerBlockEntity) helper.getBlockEntity(pos.west())).fill(5_000);
        }
        return (CrackMachineBlockEntity) helper.getBlockEntity(pos);
    }

    private static CrackMachineBlockEntity crusher(GameTestHelper helper, BlockPos pos, boolean power) {
        return machine(helper, ModBlocks.CRUSHER.get(), pos, power);
    }

    /**
     * Jouw wens: "deze kun je in een crushing block steken die er powder van maakt". Twee gebarsten
     * stenen bakstenen erin: na vijf seconden ligt er één poeder van stenen bakstenen in het vakje eruit
     * (en het tweede is bezig), hij draait, en er ging stroom op (4 per tick).
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "cracks")
    public static void crusher_makes_powder(GameTestHelper helper) {
        BlockPos pos = new BlockPos(6, 2, 6);
        CrackMachineBlockEntity crusher = crusher(helper, pos, true);
        ChargerBlockEntity generator = (ChargerBlockEntity) helper.getBlockEntity(pos.west());
        int before = generator.energy();
        crusher.setItem(CrackMachineBlockEntity.SLOT_IN, FromBlockItem.of(ModItems.CRACKED_BLOCK.get(), Blocks.STONE_BRICKS, 2));
        helper.runAfterDelay(StarkConfig.Repair.MACHINE_TICKS + 5, () -> {
            ItemStack out = crusher.getItem(CrackMachineBlockEntity.SLOT_OUT);
            check(out.is(ModItems.BLOCK_POWDER.get()) && FromBlockItem.block(out) == Blocks.STONE_BRICKS && out.getCount() == 1,
                    "Na vijf seconden zou er één poeder van stenen bakstenen uit moeten komen, niet " + out);
            check(crusher.getItem(CrackMachineBlockEntity.SLOT_IN).getCount() == 1, "Er zou nog één gebarsten blok in moeten zitten");
            int used = before - generator.energy();
            check(used >= StarkConfig.Repair.MACHINE_TICKS * StarkConfig.Power.CRUSHER_COMPACTOR, "De Crusher gebruikte te weinig stroom: " + used);
            helper.assertBlockProperty(pos, CrackMachineBlock.WORKING, true);
            helper.succeed();
        });
    }

    /** Zonder stroom doet hij niets, en zegt hij waarom ("No power"). */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "cracks")
    public static void crusher_needs_power(GameTestHelper helper) {
        CrackMachineBlockEntity crusher = crusher(helper, new BlockPos(6, 2, 6), false);
        crusher.setItem(CrackMachineBlockEntity.SLOT_IN, FromBlockItem.of(ModItems.CRACKED_BLOCK.get(), Blocks.OBSIDIAN, 1));
        helper.runAfterDelay(StarkConfig.Repair.MACHINE_TICKS + 10, () -> {
            check(crusher.getItem(CrackMachineBlockEntity.SLOT_OUT).isEmpty(), "Zonder stroom maakte de Crusher toch poeder");
            check(crusher.progress() == 0 && crusher.status() == CrackMachineBlockEntity.Status.NO_POWER,
                    "Zonder stroom zou hij moeten wachten met 'No power', niet " + crusher.status());
            helper.succeed();
        });
    }

    /**
     * Trechters (mijn invulling): een trechter erboven doet er gebarsten blokken in (gewone steen niet,
     * daar kan hij niets mee), een trechter eronder haalt het poeder eruit.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "cracks")
    public static void crusher_works_with_hoppers(GameTestHelper helper) {
        BlockPos pos = new BlockPos(6, 3, 6);
        crusher(helper, pos, true);
        helper.setBlock(pos.above(), Blocks.HOPPER);
        helper.setBlock(pos.below(), Blocks.HOPPER);
        HopperBlockEntity top = (HopperBlockEntity) helper.getBlockEntity(pos.above());
        HopperBlockEntity bottom = (HopperBlockEntity) helper.getBlockEntity(pos.below());
        top.setItem(0, new ItemStack(Blocks.STONE));
        top.setItem(1, FromBlockItem.of(ModItems.CRACKED_BLOCK.get(), Blocks.DEEPSLATE_BRICKS, 1));
        helper.succeedWhen(() -> {
            ItemStack powder = bottom.getItem(0);
            check(powder.is(ModItems.BLOCK_POWDER.get()) && FromBlockItem.block(powder) == Blocks.DEEPSLATE_BRICKS,
                    "Het poeder kwam (nog) niet in de onderste trechter: " + powder);
            check(top.getItem(0).is(Blocks.STONE.asItem()), "Gewone steen ging toch de Crusher in");
        });
    }

    // ================================================================ Stap 3: de Compactor

    /**
     * Jouw wens: "die kun je compacten tot een volledig nieuwe sterke blok". Obsidiaanpoeder in de
     * Compactor: na vijf seconden een gewoon blok obsidiaan (er is niets bijzonders meer aan). Een
     * gebarsten blok neemt hij niet aan (dat moet eerst door de Crusher), en de Crusher geen poeder.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "cracks")
    public static void compactor_presses_a_new_block(GameTestHelper helper) {
        CrackMachineBlockEntity compactor = machine(helper, ModBlocks.COMPACTOR.get(), new BlockPos(6, 2, 6), true);
        CrackMachineBlockEntity crusher = crusher(helper, new BlockPos(10, 2, 6), false);
        ItemStack powder = FromBlockItem.of(ModItems.BLOCK_POWDER.get(), Blocks.OBSIDIAN, 1);
        ItemStack cracked = FromBlockItem.of(ModItems.CRACKED_BLOCK.get(), Blocks.OBSIDIAN, 1);
        check(!compactor.canPlaceItem(CrackMachineBlockEntity.SLOT_IN, cracked)
                && compactor.canPlaceItem(CrackMachineBlockEntity.SLOT_IN, powder), "De Compactor zou alleen poeder moeten aannemen");
        check(!crusher.canPlaceItem(CrackMachineBlockEntity.SLOT_IN, powder)
                && crusher.canPlaceItem(CrackMachineBlockEntity.SLOT_IN, cracked), "De Crusher zou alleen gebarsten blokken moeten aannemen");
        compactor.setItem(CrackMachineBlockEntity.SLOT_IN, powder);
        helper.runAfterDelay(StarkConfig.Repair.MACHINE_TICKS + 5, () -> {
            ItemStack out = compactor.getItem(CrackMachineBlockEntity.SLOT_OUT);
            check(ItemStack.isSameItemSameComponents(out, new ItemStack(Blocks.OBSIDIAN)) && out.getCount() == 1,
                    "Na vijf seconden zou er een gewoon blok obsidiaan uit moeten komen, niet " + out);
            check(compactor.getItem(CrackMachineBlockEntity.SLOT_IN).isEmpty(), "Het poeder zou op moeten zijn");
            helper.succeed();
        });
    }

    /**
     * De hele weg rond: obsidiaan met barsten, twee keer geplamuurd (nog 70% sterk), weghakken, door de
     * Crusher, door de Compactor, en het nieuwe blok terugzetten: weer helemaal sterk (20), zonder barsten.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "cracks")
    public static void repair_loop_gives_a_strong_block(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = miner(helper);
        CrackMachineBlockEntity crusher = crusher(helper, new BlockPos(3, 2, 3), true);
        CrackMachineBlockEntity compactor = machine(helper, ModBlocks.COMPACTOR.get(), new BlockPos(3, 2, 12), true);
        helper.setBlock(BLOCK.below(), Blocks.STONE);
        helper.setBlock(BLOCK, Blocks.OBSIDIAN);
        for (int i = 0; i < 2; i++) {
            dent(helper, BLOCK);
            check(paste(helper, player, BLOCK), "Plamuren lukte niet");
        }
        check(Math.abs(strength(helper, BLOCK) - 14) < 0.01f, "Twee keer geplamuurd obsidiaan zou 14 moeten kunnen hebben");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        check(ZoneGameTests.breakBlock(helper, player, BLOCK), "Hakken lukte niet");
        List<ItemStack> drops = pickUp(helper, BLOCK);
        check(drops.size() == 1 && crackedOf(drops.get(0), Blocks.OBSIDIAN), "Weghakken gaf geen gebarsten obsidiaan: " + drops);
        crusher.setItem(CrackMachineBlockEntity.SLOT_IN, drops.get(0));
        helper.startSequence()
                .thenWaitUntil(() -> check(!crusher.getItem(CrackMachineBlockEntity.SLOT_OUT).isEmpty(), "De Crusher is nog bezig"))
                .thenExecute(() -> compactor.setItem(CrackMachineBlockEntity.SLOT_IN,
                        crusher.removeItem(CrackMachineBlockEntity.SLOT_OUT, 64)))
                .thenWaitUntil(() -> check(!compactor.getItem(CrackMachineBlockEntity.SLOT_OUT).isEmpty(), "De Compactor is nog bezig"))
                .thenExecute(() -> {
                    ItemStack block = compactor.removeItem(CrackMachineBlockEntity.SLOT_OUT, 64);
                    check(block.is(Items.OBSIDIAN) && block.getCount() == 1, "Uit de Compactor kwam " + block);
                    player.setItemInHand(InteractionHand.MAIN_HAND, block);
                    BlockPos below = helper.absolutePos(BLOCK.below());
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).relative(Direction.UP, 0.5), Direction.UP, below, false);
                    check(player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                            "Het nieuwe obsidiaan neerzetten lukte niet");
                    helper.assertBlockPresent(Blocks.OBSIDIAN, BLOCK);
                    check(strength(helper, BLOCK) == 20 && Cracks.damage(level, helper.absolutePos(BLOCK)) == 0,
                            "Het nieuwe obsidiaan zou weer helemaal sterk moeten zijn, niet " + strength(helper, BLOCK));
                })
                .thenSucceed();
    }
}
