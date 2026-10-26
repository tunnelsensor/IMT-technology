package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.breach.Cracks;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.zone.ZoneData;
import com.senne.imttech.weapons.RemoteChargeBlockEntity;
import com.senne.imttech.weapons.TriggerData;
import com.senne.imttech.weapons.TriggerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
 * Tests voor de Remote Frag en de Frag Trigger (hoofdstuk 35, 26 oktober). Jouw wens: "haal de C4 weg, ik wil dat er
 * nog enkel granaten bestaan; de plak-C4 met remote moet zijn zoals een halve sticky frag, maar dan met remote zoals de
 * Remote EMP". Jouw keuzes: een eigen Frag Trigger, het alarm en de schaar zoals bij C4, en zo sterk als een Sticky Frag.
 * Dat laatste betekent (met de getallen uit [weapons] en [breach]): één frag blaast een blok steen uit een muur, voor
 * obsidiaan zijn er vier nodig en voor de beveiligingskern acht. Deze tests vervangen die van C4 (hoofdstuk 10).
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class RemoteFragGameTests {
    private RemoteFragGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Plakt een Remote Frag (nog nergens aan gekoppeld) tegen de kant {@code outward} van het blok {@code support}. */
    static BlockPos stick(GameTestHelper helper, BlockPos support, Direction outward) {
        BlockPos pos = support.relative(outward);
        BlockState state = ModBlocks.REMOTE_FRAG.get().defaultBlockState();
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

    /** Laat de Remote Frag op deze plek meteen afgaan; geeft terug hoeveel blokken er kapot gingen. */
    static int boom(GameTestHelper helper, BlockPos frag) {
        BlockPos abs = helper.absolutePos(frag);
        ServerLevel level = helper.getLevel();
        return ((RemoteChargeBlockEntity) level.getBlockEntity(abs)).goOff(level, abs, level.getBlockState(abs));
    }

    /** Een muur van één blok dik (op x = {@code x}), van y 2 tot 6 en z 4 tot 12. */
    static void wall(GameTestHelper helper, int x, Block block) {
        for (int y = 2; y <= 6; y++) {
            for (int z = 4; z <= 12; z++) {
                helper.setBlock(new BlockPos(x, y, z), block);
            }
        }
    }

    /** Een speler plakt een Remote Frag tegen de oostkant van {@code support} (rechtsklik met het item). */
    private static BlockPos plant(GameTestHelper helper, ServerPlayer player, BlockPos support) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMOTE_FRAG.get()));
        BlockPos abs = helper.absolutePos(support);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.EAST, 0.5), Direction.EAST, abs, false);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        return support.east();
    }

    // ================================================================ De klap

    /**
     * Eén Remote Frag tegen een stenen muur van twee dik: het blok waar hij op plakt is weg, de blokken eromheen en de
     * tweede laag hebben barsten. Een tweede frag op het blok erboven (dat al barsten had) maakt het gat twee hoog: daar
     * kun je door.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "remote_frag")
    public static void remote_frag_blows_a_block_out_of_a_wall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        wall(helper, 8, Blocks.STONE);
        wall(helper, 7, Blocks.STONE);
        BlockPos support = new BlockPos(8, 4, 8);
        boom(helper, stick(helper, support, Direction.EAST));
        helper.assertBlockNotPresent(Blocks.STONE, support);
        for (BlockPos stays : new BlockPos[] {support.above(), support.below(), support.north(), support.south(), support.west()}) {
            helper.assertBlockPresent(Blocks.STONE, stays);
            check(Cracks.damage(level, helper.absolutePos(stays)) > 0, "De blokken rond het gat zouden barsten moeten hebben: " + stays);
        }
        boom(helper, stick(helper, support.above(), Direction.EAST));
        helper.assertBlockNotPresent(Blocks.STONE, support.above());
        helper.succeed();
    }

    /** Obsidiaan is sterk: na één, twee en drie frags staat het blok er nog (met steeds meer barsten), na de vierde niet. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "remote_frag")
    public static void remote_frag_obsidian_needs_four(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        wall(helper, 8, Blocks.OBSIDIAN);
        BlockPos support = new BlockPos(8, 4, 8);
        float before = 0;
        for (int i = 1; i <= 3; i++) {
            boom(helper, stick(helper, support, Direction.EAST));
            helper.assertBlockPresent(Blocks.OBSIDIAN, support);
            float now = Cracks.damage(level, helper.absolutePos(support));
            check(now > before, "Na frag nummer " + i + " zouden de barsten moeten groeien: " + now);
            before = now;
        }
        boom(helper, stick(helper, support, Direction.EAST));
        helper.assertBlockNotPresent(Blocks.OBSIDIAN, support);
        helper.succeed();
    }

    /** De beveiligingskern is het sterkst: zeven frags en hij staat er nog, de achtste blaast hem op, en dan is ook zijn zone weg. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "remote_frag")
    public static void remote_frag_core_needs_eight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 20), "zone-owner");
        BlockPos corePos = new BlockPos(10, 3, 10);
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, corePos, owner);
        core.setZoneBoxes(java.util.List.of(ZoneGameTests.box(helper, new BlockPos(6, 2, 6), new BlockPos(14, 6, 14))));
        for (int i = 1; i <= 7; i++) {
            boom(helper, stick(helper, corePos, Direction.EAST));
            helper.assertBlockPresent(ModBlocks.SECURITY_CORE.get(), corePos);
        }
        boom(helper, stick(helper, corePos, Direction.EAST));
        helper.assertBlockNotPresent(ModBlocks.SECURITY_CORE.get(), corePos);
        check(ZoneData.get(level).zone(GlobalPos.of(level.dimension(), helper.absolutePos(corePos))) == null,
                "Met de kern opgeblazen zou ook zijn zone weg moeten zijn");
        helper.succeed();
    }

    /** Een kettingreactie: gaat een Remote Frag af, dan gaat de Remote Frag vlak ernaast een paar ticks later ook af. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "remote_frag")
    public static void remote_frag_chain_reaction(GameTestHelper helper) {
        wall(helper, 8, Blocks.STONE);
        BlockPos first = stick(helper, new BlockPos(8, 4, 6), Direction.EAST);
        BlockPos second = stick(helper, new BlockPos(8, 4, 8), Direction.EAST);
        boom(helper, first);
        check(helper.getBlockEntity(second) instanceof RemoteChargeBlockEntity other && other.goingOff(),
                "De tweede Remote Frag zou door de eerste moeten afgaan");
        helper.runAfterDelay(8, () -> {
            helper.assertBlockNotPresent(ModBlocks.REMOTE_FRAG.get(), second);
            helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(8, 4, 8));
            helper.succeed();
        });
    }

    // ================================================================ De Frag Trigger

    /**
     * Een speler met een Frag Trigger in zijn rugzak plakt een Remote Frag: die hoort meteen bij de trigger (en wacht).
     * Een EMP Trigger laat hem niet afgaan; op de knop van de Frag Trigger drukken wel: daarna is de muur open.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "remote_frag")
    public static void frag_trigger_links_and_fires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(14, 3, 8), "zone-outsider");
        wall(helper, 8, Blocks.STONE);
        player.getInventory().setItem(5, new ItemStack(ModItems.FRAG_TRIGGER.get()));
        BlockPos support = new BlockPos(8, 4, 8);
        BlockPos stuck = plant(helper, player, support);
        TriggerData data = TriggerItem.data(player.getInventory().getItem(5));
        check(helper.getBlockEntity(stuck) instanceof RemoteChargeBlockEntity frag && data != null
                        && data.id().equals(frag.trigger()) && data.charges().size() == 1 && !frag.goingOff(),
                "De Remote Frag zou bij de Frag Trigger in de rugzak moeten horen (en wachten)");
        check(TriggerItem.fire(level, player, new ItemStack(ModItems.EMP_TRIGGER.get())).fired() == 0,
                "Een EMP Trigger liet iets afgaan");
        player.setItemInHand(InteractionHand.MAIN_HAND, player.getInventory().getItem(5));
        TriggerItem.Fired fired = TriggerItem.fire(level, player, player.getMainHandItem());
        check(fired.fired() == 1, "De Frag Trigger zou één lading moeten laten afgaan, niet " + fired);
        helper.runAfterDelay(6, () -> {
            helper.assertBlockNotPresent(ModBlocks.REMOTE_FRAG.get(), stuck);
            helper.assertBlockNotPresent(Blocks.STONE, support);
            check(TriggerItem.data(player.getMainHandItem()).charges().isEmpty(), "De trigger onthoudt een frag die al afging");
            helper.succeed();
        });
    }

    /**
     * Een losse Remote Frag koppel je met een rechtsklik met de Frag Trigger; een andere Frag Trigger neemt hem niet over,
     * een EMP Trigger koppelt hem niet; en te ver weg gaat hij niet af (en blijft hij bij de trigger).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "remote_frag")
    public static void frag_trigger_range_and_linking(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(16, 3, 8), "zone-outsider");
        wall(helper, 8, Blocks.STONE);
        BlockPos loose = stick(helper, new BlockPos(8, 4, 8), Direction.EAST);
        ItemStack mine = new ItemStack(ModItems.FRAG_TRIGGER.get());
        ItemStack other = new ItemStack(ModItems.FRAG_TRIGGER.get());
        ItemStack emp = new ItemStack(ModItems.EMP_TRIGGER.get());
        BlockPos abs = helper.absolutePos(loose);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.EAST, abs, false);
        for (ItemStack trigger : new ItemStack[] {mine, other, emp}) {
            player.setItemInHand(InteractionHand.MAIN_HAND, trigger);
            level.getBlockState(abs).useItemOn(trigger, level, player, InteractionHand.MAIN_HAND, hit);
        }
        RemoteChargeBlockEntity frag = (RemoteChargeBlockEntity) helper.getBlockEntity(loose);
        check(frag.trigger() != null && frag.trigger().equals(TriggerItem.data(mine).id()),
                "Een rechtsklik met de Frag Trigger zou de losse frag moeten koppelen (en een andere niet overnemen)");
        check(TriggerItem.data(emp) == null, "Een EMP Trigger koppelde een Remote Frag");
        float oldRange = ImtConfig.Weapons.TRIGGER_RANGE;
        try {
            ImtConfig.Weapons.TRIGGER_RANGE = 3;
            TriggerItem.Fired fired = TriggerItem.fire(level, player, mine);
            check(fired.fired() == 0 && fired.outOfRange() == 1 && !frag.goingOff() && TriggerItem.data(mine).charges().size() == 1,
                    "Een frag te ver weg zou niet mogen afgaan (en moet bij de trigger blijven): " + fired);
        } finally {
            ImtConfig.Weapons.TRIGGER_RANGE = oldRange;
        }
        helper.setBlock(loose, Blocks.AIR);
        helper.succeed();
    }

    // ================================================================ Alarm en schaar (zoals C4)

    /**
     * Jouw keuze: "alarm en schaar, zoals C4". Een vreemde plakt een Remote Frag op een zone: het alarm gaat af (in het
     * logboek, zonder naam). Met de hand krijg je hem er niet af; de eigenaar knipt de draad door met een schaar: hij is
     * weg, ligt als item op de grond (de verdediger krijgt hem), en dat staat ook in het logboek. Je eigen Remote Frag in
     * je eigen zone geeft geen alarm.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "remote_frag")
    public static void remote_frag_alarm_and_defuse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(12, 3, 8), "zone-owner");
        ServerPlayer outsider = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(13, 3, 9), "zone-outsider");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        core.setZoneBoxes(java.util.List.of(ZoneGameTests.box(helper, new BlockPos(5, 2, 5), new BlockPos(12, 8, 12))));
        wall(helper, 8, Blocks.STONE);
        BlockPos own = plant(helper, owner, new BlockPos(8, 3, 5));
        check(helper.getBlockEntity(own) instanceof RemoteChargeBlockEntity mine && !mine.alerted(),
                "Je eigen Remote Frag in je eigen zone zou geen alarm moeten geven");
        helper.setBlock(own, Blocks.AIR);
        BlockPos stuck = plant(helper, outsider, new BlockPos(8, 4, 8));
        check(helper.getBlockEntity(stuck) instanceof RemoteChargeBlockEntity theirs && theirs.alerted()
                        && DatacenterGameTests.logged(core, com.senne.imttech.security.os.EventLog.Type.ALARM, "Remote Frag planted at"),
                "Een Remote Frag van een vreemde op de zone zou het alarm moeten laten afgaan");
        check(helper.getBlockState(stuck).getDestroySpeed(level, helper.absolutePos(stuck)) < 0,
                "Een Remote Frag zou met de hand niet los te krijgen moeten zijn");
        ItemStack shears = new ItemStack(net.minecraft.world.item.Items.SHEARS);
        owner.setItemInHand(InteractionHand.MAIN_HAND, shears);
        BlockPos abs = helper.absolutePos(stuck);
        level.getBlockState(abs).useItemOn(shears, level, owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.EAST, abs, false));
        helper.assertBlockNotPresent(ModBlocks.REMOTE_FRAG.get(), stuck);
        boolean dropped = !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(abs).inflate(1.5), item -> item.getItem().is(ModItems.REMOTE_FRAG.get())).isEmpty();
        check(dropped && DatacenterGameTests.logged(core, com.senne.imttech.security.os.EventLog.Type.ALARM, "defused"),
                "Na de schaar zou de Remote Frag als item moeten liggen en in het logboek staan");
        ZoneGameTests.finish(helper, core);
    }

    /**
     * Een klap op de zone van een eigenaar die offline is: het logboek zegt hoeveel blokken er weg zijn, en de eigenaar
     * krijgt het verslag als hij weer inlogt (hier: het verslag in de wereld).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "remote_frag")
    public static void remote_frag_blast_report_while_away(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 20), "zone-owner");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        core.setZoneBoxes(java.util.List.of(ZoneGameTests.box(helper, new BlockPos(5, 2, 5), new BlockPos(12, 8, 12))));
        wall(helper, 8, Blocks.STONE);
        java.util.UUID ownerId = owner.getUUID();
        level.getServer().getPlayerList().remove(owner); // de eigenaar logt uit
        int broken = boom(helper, stick(helper, new BlockPos(8, 4, 8), Direction.EAST));
        check(DatacenterGameTests.logged(core, com.senne.imttech.security.os.EventLog.Type.ALARM, "Remote Frag blast at"),
                "Het logboek zegt niets over de klap");
        int[] report = ZoneData.get(level).takeAwayReport(ownerId);
        check(report != null && report[0] == 1 && report[1] == broken && broken > 0,
                "De eigenaar zou bij het inloggen 1 klap en " + broken + " blokken moeten horen, niet "
                        + (report == null ? "niets" : report[0] + " / " + report[1]));
        ZoneGameTests.finish(helper, core);
    }
}
