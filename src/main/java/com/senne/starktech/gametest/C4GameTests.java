package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.c4.C4Blast;
import com.senne.starktech.c4.C4Block;
import com.senne.starktech.c4.C4BlockEntity;
import com.senne.starktech.c4.C4Item;
import com.senne.starktech.c4.Cracks;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.zone.ZoneData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor C4 (hoofdstuk 10 van het plan): de klap met breekpunten en barsten, de timer, en de
 * kettingreactie. Jouw keuze: "sterke muren kosten meer C4".
 */
@GameTestHolder(StarkTech.MODID)
@PrefixGameTestTemplate(false)
public final class C4GameTests {
    private C4GameTests() {}

    private static final String ARENA = "empty";

    /** Plakt een C4 (Timer, nog niet lopend) tegen de kant {@code outward} van het blok {@code support}. */
    static BlockPos stick(GameTestHelper helper, Block c4, BlockPos support, Direction outward) {
        BlockPos pos = support.relative(outward);
        BlockState state = c4.defaultBlockState();
        if (outward == Direction.UP) {
            state = state.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR);
        } else if (outward == Direction.DOWN) {
            state = state.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.CEILING);
        } else {
            state = state.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
                    .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, outward);
        }
        helper.setBlock(pos, state);
        return pos;
    }

    /** Laat de C4 op deze plek meteen afgaan; geeft terug hoeveel blokken er kapot gingen. */
    static int boom(GameTestHelper helper, BlockPos c4) {
        BlockPos abs = helper.absolutePos(c4);
        return C4Blast.detonate(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
    }

    /** Een muur van één blok dik (op x = {@code x}), van y 2 tot 6 en z 4 tot 12. */
    static void wall(GameTestHelper helper, int x, Block block) {
        for (int y = 2; y <= 6; y++) {
            for (int z = 4; z <= 12; z++) {
                helper.setBlock(new BlockPos(x, y, z), block);
            }
        }
    }

    /**
     * Eén C4 tegen een stenen muur van twee dik: het blok erachter en de vier ernaast zijn weg (een
     * gat van drie hoog, daar kun je door), de schuine blokken en de tweede laag hebben barsten.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "c4")
    public static void c4_breaks_a_stone_wall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        wall(helper, 8, Blocks.STONE);
        wall(helper, 7, Blocks.STONE);
        BlockPos support = new BlockPos(8, 4, 8);
        boom(helper, stick(helper, ModBlocks.TIMED_C4.get(), support, Direction.EAST));
        for (BlockPos hole : new BlockPos[] {support, support.above(), support.below(), support.north(), support.south()}) {
            helper.assertBlockNotPresent(Blocks.STONE, hole);
        }
        helper.assertBlockPresent(Blocks.STONE, support.above().north());
        helper.assertBlockPresent(Blocks.STONE, support.west());
        if (Cracks.damage(level, helper.absolutePos(support.west())) <= 0
                || Cracks.damage(level, helper.absolutePos(support.above().north())) <= 0) {
            throw new GameTestAssertException("De blokken die net niet braken, zouden barsten moeten hebben");
        }
        helper.succeed();
    }

    /**
     * Obsidiaan is sterk: na één en na twee C4 staat het blok er nog (met steeds meer barsten), na de
     * derde is er een gat (het blok erachter en de vier ernaast).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "c4")
    public static void c4_obsidian_needs_three(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        wall(helper, 8, Blocks.OBSIDIAN);
        BlockPos support = new BlockPos(8, 4, 8);
        float before = 0;
        for (int i = 1; i <= 2; i++) {
            boom(helper, stick(helper, ModBlocks.TIMED_C4.get(), support, Direction.EAST));
            helper.assertBlockPresent(Blocks.OBSIDIAN, support);
            float now = Cracks.damage(level, helper.absolutePos(support));
            if (now <= before) {
                throw new GameTestAssertException("Na C4 nummer " + i + " zouden de barsten moeten groeien: " + now);
            }
            before = now;
        }
        boom(helper, stick(helper, ModBlocks.TIMED_C4.get(), support, Direction.EAST));
        for (BlockPos hole : new BlockPos[] {support, support.above(), support.below(), support.north(), support.south()}) {
            helper.assertBlockNotPresent(Blocks.OBSIDIAN, hole);
        }
        helper.succeed();
    }

    /**
     * De beveiligingskern is het sterkst: vijf C4 en hij staat er nog, de zesde blaast hem op, en dan is
     * ook zijn zone weg.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "c4")
    public static void c4_core_needs_six(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 20), "zone-owner");
        BlockPos corePos = new BlockPos(10, 3, 10);
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, corePos, owner);
        core.setZoneBoxes(java.util.List.of(ZoneGameTests.box(helper, new BlockPos(6, 2, 6), new BlockPos(14, 6, 14))));
        for (int i = 1; i <= 5; i++) {
            boom(helper, stick(helper, ModBlocks.TIMED_C4.get(), corePos, Direction.EAST));
            helper.assertBlockPresent(ModBlocks.SECURITY_CORE.get(), corePos);
        }
        boom(helper, stick(helper, ModBlocks.TIMED_C4.get(), corePos, Direction.EAST));
        helper.assertBlockNotPresent(ModBlocks.SECURITY_CORE.get(), corePos);
        if (ZoneData.get(level).zone(GlobalPos.of(level.dimension(), helper.absolutePos(corePos))) != null) {
            throw new GameTestAssertException("Met de kern opgeblazen zou ook zijn zone weg moeten zijn");
        }
        helper.succeed();
    }

    /**
     * De timer: een vreemde plakt een C4 (Timer, 5 seconden) tegen een muur in iemands zone (C4 mag
     * altijd). Na 4,5 seconden staat de muur er nog, na 5,5 seconden is er een gat (een zone houdt C4
     * niet tegen).
     */
    @GameTest(template = ARENA, timeoutTicks = 160, batch = "c4")
    public static void c4_timer_counts_down_in_a_zone(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 20), "zone-owner");
        ServerPlayer outsider = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(18, 3, 20), "zone-outsider");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        core.setZoneBoxes(java.util.List.of(ZoneGameTests.box(helper, new BlockPos(5, 2, 5), new BlockPos(12, 8, 12))));
        wall(helper, 8, Blocks.STONE);
        BlockPos support = new BlockPos(8, 4, 8);
        ItemStack c4 = new ItemStack(ModItems.TIMED_C4.get());
        c4.set(ModDataComponents.C4_TIMER.get(), 5);
        outsider.setItemInHand(InteractionHand.MAIN_HAND, c4);
        BlockPos abs = helper.absolutePos(support);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.EAST, 0.5), Direction.EAST, abs, false);
        InteractionResult result = outsider.getMainHandItem().useOn(new UseOnContext(outsider, InteractionHand.MAIN_HAND, hit));
        if (!result.consumesAction()) {
            throw new GameTestAssertException("Een vreemde zou C4 in een zone moeten kunnen plakken");
        }
        BlockPos stuck = support.east();
        if (!(helper.getBlockEntity(stuck) instanceof C4BlockEntity timer) || timer.explodeAt() < 0) {
            throw new GameTestAssertException("De C4 zou meteen moeten lopen");
        }
        if (!outsider.getUUID().equals(timer.placer())) {
            throw new GameTestAssertException("De C4 weet niet wie hem plakte");
        }
        helper.runAfterDelay(90, () -> helper.assertBlockPresent(Blocks.STONE, support));
        helper.runAfterDelay(112, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, support);
            helper.assertBlockNotPresent(ModBlocks.TIMED_C4.get(), stuck);
            ZoneGameTests.finish(helper, core);
        });
    }

    /** Een kettingreactie: gaat een C4 af, dan gaat de C4 vlak ernaast een paar ticks later ook af. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "c4")
    public static void c4_chain_reaction(GameTestHelper helper) {
        wall(helper, 8, Blocks.STONE);
        BlockPos first = stick(helper, ModBlocks.TIMED_C4.get(), new BlockPos(8, 4, 6), Direction.EAST);
        BlockPos second = stick(helper, ModBlocks.TIMED_C4.get(), new BlockPos(8, 4, 9), Direction.EAST);
        boom(helper, first);
        if (!(helper.getBlockEntity(second) instanceof C4BlockEntity other) || other.explodeAt() < 0) {
            throw new GameTestAssertException("De tweede C4 zou door de eerste moeten gaan lopen");
        }
        helper.runAfterDelay(8, () -> {
            helper.assertBlockNotPresent(ModBlocks.TIMED_C4.get(), second);
            helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(8, 4, 9));
            helper.succeed();
        });
    }

    /** De tijd op het item blijft tussen 5 seconden en 2 minuten, en een nieuw item staat op 10. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "c4")
    public static void c4_timer_limits(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.TIMED_C4.get());
        if (C4Item.timerSeconds(stack) != 10) {
            throw new GameTestAssertException("Een nieuwe C4 zou op 10 seconden moeten staan");
        }
        stack.set(ModDataComponents.C4_TIMER.get(), 1);
        int low = C4Item.timerSeconds(stack);
        stack.set(ModDataComponents.C4_TIMER.get(), 999);
        int high = C4Item.timerSeconds(stack);
        if (low != 5 || high != 120 || !C4Item.clock(75).equals("1:15")) {
            throw new GameTestAssertException("De timer zou tussen 5 en 120 seconden moeten blijven: " + low + " / " + high);
        }
        if (!(ModBlocks.TIMED_C4.get() instanceof C4Block block) || block.kind() != C4Block.Kind.TIMED) {
            throw new GameTestAssertException("De C4 (Timer) is van de verkeerde soort");
        }
        helper.succeed();
    }

    // ================================================================ Stap 6: de remote C4 en de detonator

    /**
     * Een speler met een detonator in zijn rugzak plakt een remote C4: die hoort meteen bij de
     * detonator (en wacht). Op de knop drukken: hij gaat af, en de muur heeft een gat.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "c4")
    public static void remote_c4_links_and_fires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(14, 3, 8), "zone-outsider");
        wall(helper, 8, Blocks.STONE);
        ItemStack detonator = new ItemStack(ModItems.DETONATOR.get());
        player.getInventory().setItem(5, detonator);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMOTE_C4.get()));
        BlockPos support = new BlockPos(8, 4, 8);
        BlockPos abs = helper.absolutePos(support);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.EAST, 0.5), Direction.EAST, abs, false);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        BlockPos stuck = support.east();
        com.senne.starktech.c4.DetonatorData data = com.senne.starktech.c4.DetonatorItem.data(player.getInventory().getItem(5));
        if (!(helper.getBlockEntity(stuck) instanceof C4BlockEntity c4) || data == null || !data.id().equals(c4.detonator())
                || data.charges().size() != 1 || c4.explodeAt() >= 0) {
            throw new GameTestAssertException("De remote C4 zou bij de detonator in de rugzak moeten horen (en wachten)");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, player.getInventory().getItem(5));
        com.senne.starktech.c4.DetonatorItem.Fired fired = com.senne.starktech.c4.DetonatorItem.fire(level, player, player.getMainHandItem());
        if (fired.fired() != 1) {
            throw new GameTestAssertException("De detonator zou één lading moeten laten afgaan, niet " + fired);
        }
        helper.runAfterDelay(6, () -> {
            helper.assertBlockNotPresent(ModBlocks.REMOTE_C4.get(), stuck);
            helper.assertBlockNotPresent(Blocks.STONE, support);
            helper.succeed();
        });
    }

    /**
     * Te ver weg gaat een lading niet af (en blijft hij bij de detonator); een lading van een andere
     * detonator koppel je niet over; een losse lading koppel je met een rechtsklik met de detonator.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "c4")
    public static void remote_c4_range_and_linking(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(16, 3, 8), "zone-outsider");
        wall(helper, 8, Blocks.STONE);
        BlockPos loose = stick(helper, ModBlocks.REMOTE_C4.get(), new BlockPos(8, 4, 8), Direction.EAST);
        ItemStack mine = new ItemStack(ModItems.DETONATOR.get());
        ItemStack other = new ItemStack(ModItems.DETONATOR.get());
        BlockPos abs = helper.absolutePos(loose);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.EAST, abs, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, mine);
        level.getBlockState(abs).useItemOn(mine, level, player, InteractionHand.MAIN_HAND, hit);
        C4BlockEntity c4 = (C4BlockEntity) helper.getBlockEntity(loose);
        if (c4.detonator() == null || !c4.detonator().equals(com.senne.starktech.c4.DetonatorItem.data(mine).id())) {
            throw new GameTestAssertException("Een rechtsklik met de detonator zou de losse lading moeten koppelen");
        }
        // Een andere detonator neemt hem niet over.
        player.setItemInHand(InteractionHand.MAIN_HAND, other);
        level.getBlockState(abs).useItemOn(other, level, player, InteractionHand.MAIN_HAND, hit);
        if (!c4.detonator().equals(com.senne.starktech.c4.DetonatorItem.data(mine).id())) {
            throw new GameTestAssertException("Een andere detonator zou de lading niet mogen overnemen");
        }
        // Te ver weg: niets.
        int oldRange = StarkConfig.C4.DETONATOR_RANGE;
        try {
            StarkConfig.C4.DETONATOR_RANGE = 3;
            player.setItemInHand(InteractionHand.MAIN_HAND, mine);
            com.senne.starktech.c4.DetonatorItem.Fired fired = com.senne.starktech.c4.DetonatorItem.fire(level, player, mine);
            if (fired.fired() != 0 || fired.outOfRange() != 1 || c4.explodeAt() >= 0
                    || com.senne.starktech.c4.DetonatorItem.data(mine).charges().size() != 1) {
                throw new GameTestAssertException("Een lading te ver weg zou niet mogen afgaan (en moet blijven): " + fired);
            }
        } finally {
            StarkConfig.C4.DETONATOR_RANGE = oldRange;
        }
        helper.setBlock(loose, Blocks.AIR);
        helper.succeed();
    }

    // ================================================================ Stap 7: ontmantelen en alarm

    /** Plakt een C4 (Timer, 60 seconden) zoals een speler dat doet, tegen de oostkant van {@code support}. */
    private static BlockPos plant(GameTestHelper helper, ServerPlayer player, BlockPos support) {
        ItemStack c4 = new ItemStack(ModItems.TIMED_C4.get());
        c4.set(ModDataComponents.C4_TIMER.get(), 60);
        player.setItemInHand(InteractionHand.MAIN_HAND, c4);
        BlockPos abs = helper.absolutePos(support);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.EAST, 0.5), Direction.EAST, abs, false);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        return support.east();
    }

    /**
     * Een vreemde plakt C4 op een zone: het alarm gaat af (in het logboek, zonder naam). De eigenaar
     * knipt de draad door met een schaar: de C4 is weg, ligt als item op de grond (de verdediger krijgt
     * hem), en dat staat ook in het logboek. Je eigen C4 in je eigen zone geeft geen alarm.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "c4")
    public static void c4_alarm_and_defuse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(12, 3, 8), "zone-owner");
        ServerPlayer outsider = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(13, 3, 9), "zone-outsider");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        core.setZoneBoxes(java.util.List.of(ZoneGameTests.box(helper, new BlockPos(5, 2, 5), new BlockPos(12, 8, 12))));
        wall(helper, 8, Blocks.STONE);
        // Je eigen C4: geen alarm.
        BlockPos own = plant(helper, owner, new BlockPos(8, 3, 5));
        if (((C4BlockEntity) helper.getBlockEntity(own)).alerted()) {
            throw new GameTestAssertException("Je eigen C4 in je eigen zone zou geen alarm moeten geven");
        }
        helper.setBlock(own, Blocks.AIR);
        // Een vreemde: alarm.
        BlockPos stuck = plant(helper, outsider, new BlockPos(8, 4, 8));
        if (!((C4BlockEntity) helper.getBlockEntity(stuck)).alerted()
                || !DatacenterGameTests.logged(core, com.senne.starktech.security.os.EventLog.Type.ALARM, "C4 planted at")) {
            throw new GameTestAssertException("C4 van een vreemde op de zone zou het alarm moeten laten afgaan");
        }
        // Met de hand krijg je hem er niet af (hij is onbreekbaar); met een schaar wel.
        if (helper.getBlockState(stuck).getDestroySpeed(level, helper.absolutePos(stuck)) >= 0) {
            throw new GameTestAssertException("C4 zou met de hand niet los te krijgen moeten zijn");
        }
        ItemStack shears = new ItemStack(net.minecraft.world.item.Items.SHEARS);
        owner.setItemInHand(InteractionHand.MAIN_HAND, shears);
        BlockPos abs = helper.absolutePos(stuck);
        level.getBlockState(abs).useItemOn(shears, level, owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.EAST, abs, false));
        helper.assertBlockNotPresent(ModBlocks.TIMED_C4.get(), stuck);
        boolean dropped = !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(abs).inflate(1.5), item -> item.getItem().is(ModItems.TIMED_C4.get())).isEmpty();
        if (!dropped || !DatacenterGameTests.logged(core, com.senne.starktech.security.os.EventLog.Type.ALARM, "defused")) {
            throw new GameTestAssertException("Na de schaar zou de C4 als item moeten liggen en in het logboek staan");
        }
        ZoneGameTests.finish(helper, core);
    }

    /**
     * Een klap op de zone van een eigenaar die offline is: het logboek zegt hoeveel blokken er weg zijn,
     * en de eigenaar krijgt het verslag als hij weer inlogt (hier: het verslag in de wereld).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "c4")
    public static void c4_blast_report_while_away(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 20), "zone-owner");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        core.setZoneBoxes(java.util.List.of(ZoneGameTests.box(helper, new BlockPos(5, 2, 5), new BlockPos(12, 8, 12))));
        wall(helper, 8, Blocks.STONE);
        java.util.UUID ownerId = owner.getUUID();
        level.getServer().getPlayerList().remove(owner); // de eigenaar logt uit
        BlockPos c4 = stick(helper, ModBlocks.TIMED_C4.get(), new BlockPos(8, 4, 8), Direction.EAST);
        int broken = boom(helper, c4);
        if (!DatacenterGameTests.logged(core, com.senne.starktech.security.os.EventLog.Type.ALARM, "C4 blast at")) {
            throw new GameTestAssertException("Het logboek zegt niets over de klap");
        }
        int[] report = ZoneData.get(level).takeAwayReport(ownerId);
        if (report == null || report[0] != 1 || report[1] != broken) {
            throw new GameTestAssertException("De eigenaar zou bij het inloggen 1 klap en " + broken + " blokken moeten horen, niet "
                    + (report == null ? "niets" : report[0] + " / " + report[1]));
        }
        ZoneGameTests.finish(helper, core);
    }
}
