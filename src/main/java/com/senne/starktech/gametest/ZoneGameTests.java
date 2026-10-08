package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.energy.CableSpoolItem;
import com.senne.starktech.energy.StarkCables;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.security.SecurityBalance;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.os.EventLog;
import com.senne.starktech.security.zone.ZoneBox;
import com.senne.starktech.security.zone.ZoneData;
import com.senne.starktech.station.ChargerBlockEntity;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor zones en C4 (hoofdstuk 10 van het plan, 8 oktober): in een zone mag alleen wie
 * toestemming heeft plaatsen en breken.
 */
@GameTestHolder(StarkTech.MODID)
@PrefixGameTestTemplate(false)
public final class ZoneGameTests {
    private ZoneGameTests() {}

    private static final String ARENA = "empty";
    /** De kern staat in elke test hier, met zijn generator eronder (zie StarkTechGameTests.placeCore). */
    private static final BlockPos CORE = new BlockPos(2, 3, 2);

    /** Een kern van {@code owner} met één kubus van (5, 2, 5) tot (12, 8, 12). */
    static SecurityCoreBlockEntity zoneCore(GameTestHelper helper, ServerPlayer owner) {
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, CORE, owner);
        core.setZoneBoxes(List.of(box(helper, new BlockPos(5, 2, 5), new BlockPos(12, 8, 12))));
        return core;
    }

    /**
     * Klaar: de zone weg (zodat hij niet blijft liggen waar een latere test komt), en geslaagd.
     */
    static void finish(GameTestHelper helper, SecurityCoreBlockEntity core) {
        core.setZoneBoxes(List.of());
        helper.succeed();
    }

    /** Een kubus tussen twee plekken van de test. */
    static ZoneBox box(GameTestHelper helper, BlockPos a, BlockPos b) {
        return ZoneBox.of(helper.absolutePos(a), helper.absolutePos(b));
    }

    /**
     * Laat de speler {@code item} op een kant van het blok {@code against} zetten, zoals een
     * rechtsklik dat doet (via NeoForge, dus met het plaats-event). Geeft terug of het lukte.
     */
    static boolean place(GameTestHelper helper, ServerPlayer player, Item item, BlockPos against, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        BlockPos abs = helper.absolutePos(against);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(face, 0.5), face, abs, false);
        InteractionResult result = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        return result.consumesAction();
    }

    /** Laat de speler een blok breken (alsof hij het helemaal kapot gehakt heeft). Geeft terug of het lukte. */
    static boolean breakBlock(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        return player.gameMode.destroyBlock(helper.absolutePos(pos));
    }

    /** Zet de speler boven {@code pos} en laat hem recht naar beneden kijken. */
    static void lookDownAt(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        Vec3 above = helper.absoluteVec(Vec3.atBottomCenterOf(pos.above(2)));
        player.moveTo(above.x, above.y, above.z, 0.0f, 90.0f);
    }

    /** Stroom onder de kern: weg (null) of weer vol. */
    static void power(GameTestHelper helper, boolean on) {
        BlockPos generator = CORE.below();
        if (on) {
            helper.setBlock(generator, ModBlocks.HEAT_GENERATOR.get());
            ((ChargerBlockEntity) helper.getBlockEntity(generator)).fill(5_000);
        } else {
            helper.setBlock(generator, Blocks.AIR);
        }
    }

    /**
     * Jouw wens: "binnen deze zone kunnen enkel players met toestemming plaatsen/breken". Een vreemde
     * kan in de zone niets breken of neerzetten (wel erbuiten); de eigenaar wel, een vertrouwde speler
     * ook, en iemand uit het team van de eigenaar ook. Wie de kern overneemt, krijgt de zone: dan mag
     * de oude eigenaar er niet meer bouwen.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void zone_only_builders_place_and_break(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 7), "zone-owner");
        ServerPlayer outsider = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(14, 3, 14), "zone-outsider");
        SecurityCoreBlockEntity core = zoneCore(helper, owner);
        BlockPos wall = new BlockPos(8, 3, 8);
        helper.setBlock(wall, Blocks.STONE);

        // Een vreemde: niet breken, niet bovenop zetten.
        if (breakBlock(helper, outsider, wall)) {
            throw new GameTestAssertException("Een vreemde kon een blok in de zone breken");
        }
        helper.assertBlockPresent(Blocks.STONE, wall);
        if (place(helper, outsider, Items.COBBLESTONE, wall, Direction.UP)) {
            throw new GameTestAssertException("Een vreemde kon een blok in de zone zetten");
        }
        helper.assertBlockNotPresent(Blocks.COBBLESTONE, wall.above());
        // De kern zelf hoort ook bij de zone (hij staat buiten de kubus).
        if (breakBlock(helper, outsider, CORE)) {
            throw new GameTestAssertException("Een vreemde kon de kern van de zone weghakken");
        }
        // Buiten de zone mag hij gewoon alles.
        BlockPos outside = new BlockPos(16, 3, 16);
        helper.setBlock(outside, Blocks.STONE);
        if (!place(helper, outsider, Items.COBBLESTONE, outside, Direction.UP) || !breakBlock(helper, outsider, outside)) {
            throw new GameTestAssertException("Buiten de zone kon de vreemde niet bouwen of breken");
        }

        // De eigenaar mag alles.
        if (!place(helper, owner, Items.COBBLESTONE, wall, Direction.UP) || !breakBlock(helper, owner, wall.above())) {
            throw new GameTestAssertException("De eigenaar kon in zijn eigen zone niet bouwen of breken");
        }

        // Een vertrouwde speler (de app Access) ook, en daarna weer niet.
        core.addTrusted(outsider.getUUID(), outsider.getGameProfile().getName());
        if (!place(helper, outsider, Items.COBBLESTONE, wall, Direction.UP) || !breakBlock(helper, outsider, wall.above())) {
            throw new GameTestAssertException("Een vertrouwde speler kon in de zone niet bouwen of breken");
        }
        core.removeTrusted(outsider.getGameProfile().getName());
        if (breakBlock(helper, outsider, wall)) {
            throw new GameTestAssertException("Na weghalen uit Access kon de speler nog steeds breken");
        }

        // Iemand uit het team van de eigenaar ook.
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("zone_test_team");
        try {
            scoreboard.addPlayerToTeam("zone-owner", team);
            scoreboard.addPlayerToTeam("zone-outsider", team);
            if (!place(helper, outsider, Items.COBBLESTONE, wall, Direction.UP) || !breakBlock(helper, outsider, wall.above())) {
                throw new GameTestAssertException("Een teamgenoot van de eigenaar kon in de zone niet bouwen of breken");
            }
        } finally {
            scoreboard.removePlayerTeam(team);
        }

        // Overgenomen (zoals na een hack): de zone is van de nieuwe eigenaar.
        core.setOwner(outsider);
        core.resetAccessForNewOwner();
        if (breakBlock(helper, owner, wall)) {
            throw new GameTestAssertException("Na de overname kon de oude eigenaar nog in de zone breken");
        }
        if (!breakBlock(helper, outsider, wall)) {
            throw new GameTestAssertException("Na de overname kon de nieuwe eigenaar niet breken");
        }
        finish(helper, core);
    }

    /**
     * Jouw wens: ladders, scaffolding, klimplanten, fakkels en lantaarns mag iedereen in een zone
     * plaatsen en weer weghalen ("in het echt zou dat ook gaan"). Een gewoon blok nog steeds niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void zone_free_blocks_for_everyone(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 7), "zone-owner");
        ServerPlayer outsider = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(14, 3, 14), "zone-outsider");
        SecurityCoreBlockEntity core = zoneCore(helper, owner);
        BlockPos wall = new BlockPos(8, 3, 8);
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(new BlockPos(10, 2, 10), Blocks.STONE);
        helper.setBlock(new BlockPos(6, 2, 10), Blocks.STONE);

        // Een ladder tegen de muur (zuidkant), een fakkel en een lantaarn op de grond, scaffolding.
        if (!place(helper, outsider, Items.LADDER, wall, Direction.SOUTH)) {
            throw new GameTestAssertException("Een vreemde kon geen ladder in de zone zetten");
        }
        helper.assertBlockPresent(Blocks.LADDER, wall.south());
        if (!place(helper, outsider, Items.TORCH, wall, Direction.UP)) {
            throw new GameTestAssertException("Een vreemde kon geen fakkel in de zone zetten");
        }
        if (!place(helper, outsider, Items.LANTERN, new BlockPos(10, 2, 10), Direction.UP)) {
            throw new GameTestAssertException("Een vreemde kon geen lantaarn in de zone zetten");
        }
        if (!place(helper, outsider, Items.SCAFFOLDING, new BlockPos(6, 2, 10), Direction.UP)) {
            throw new GameTestAssertException("Een vreemde kon geen scaffolding in de zone zetten");
        }
        // En weer weghalen mag ook (ook een ladder die de eigenaar zelf zette).
        if (!breakBlock(helper, outsider, wall.south()) || !breakBlock(helper, outsider, wall.above())) {
            throw new GameTestAssertException("Een vreemde kon de ladder of fakkel niet weghalen");
        }
        // Maar de muur zelf blijft op slot.
        if (breakBlock(helper, outsider, wall)) {
            throw new GameTestAssertException("Een vreemde kon de muur breken");
        }
        finish(helper, core);
    }

    /**
     * Jouw keuze: de zone werkt alleen als de kern stroom heeft. Zonder stroom (of met te weinig voor
     * de zone) kan iedereen er weer bouwen; dat staat in het logboek. Komt de stroom terug, dan is hij
     * weer beschermd. Een grote zone kost meer stroom (1 per ZONE_BLOCKS_PER_POWER blokken).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void zone_needs_power(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 7), "zone-owner");
        ServerPlayer outsider = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(14, 3, 14), "zone-outsider");
        SecurityCoreBlockEntity core = zoneCore(helper, owner);
        BlockPos wall = new BlockPos(8, 3, 8);
        helper.setBlock(wall, Blocks.STONE);
        // 8 x 7 x 8 = 448 blokken: 1 stroom extra.
        if (core.zoneUpkeep() != 1) {
            throw new GameTestAssertException("Een zone van 448 blokken zou 1 stroom moeten kosten, niet " + core.zoneUpkeep());
        }
        core.powerTick(level);
        if (breakBlock(helper, outsider, wall)) {
            throw new GameTestAssertException("Met stroom kon een vreemde in de zone breken");
        }

        // De stroom weg: de zone staat uit.
        power(helper, false);
        core.powerTick(level);
        if (core.isOnline() || core.zone() == null || core.zone().powered()) {
            throw new GameTestAssertException("Zonder stroom zou de zone uit moeten staan");
        }
        if (!DatacenterGameTests.logged(core, EventLog.Type.ZONE, "Zone offline")) {
            throw new GameTestAssertException("Het logboek zegt niet dat de zone uitviel");
        }
        if (!breakBlock(helper, outsider, wall)) {
            throw new GameTestAssertException("Zonder stroom zou iedereen in de zone moeten kunnen breken");
        }

        // De stroom terug: weer beschermd.
        power(helper, true);
        helper.setBlock(wall, Blocks.STONE);
        core.powerTick(level);
        if (!core.zone().powered() || breakBlock(helper, outsider, wall)) {
            throw new GameTestAssertException("Met de stroom terug zou de zone weer moeten beschermen");
        }
        if (!DatacenterGameTests.logged(core, EventLog.Type.ZONE, "Zone online")) {
            throw new GameTestAssertException("Het logboek zegt niet dat de zone terug is");
        }

        // Genoeg voor de kern, maar niet voor een grote zone: alleen de zone valt uit.
        int oldPerPower = SecurityBalance.ZONE_BLOCKS_PER_POWER;
        try {
            SecurityBalance.ZONE_BLOCKS_PER_POWER = 1; // elk blok 1 stroom: 448 extra
            BlockPos generator = CORE.below();
            ChargerBlockEntity charger = (ChargerBlockEntity) helper.getBlockEntity(generator);
            charger.drain(charger.energy());
            charger.fill(SecurityBalance.CORE_UPKEEP + 100);
            core.powerTick(level);
            if (!core.isOnline() || core.zone().powered()) {
                throw new GameTestAssertException("Met stroom voor de kern maar niet voor de zone zou alleen de zone uit moeten zijn");
            }
        } finally {
            SecurityBalance.ZONE_BLOCKS_PER_POWER = oldPerPower;
        }
        finish(helper, core);
    }

    /**
     * De kubussen: wat buiten het gebied van de kern steekt valt eraf, een kubus helemaal erbuiten
     * verdwijnt, meer dan ZONE_MAX_BOXES kan niet, kubussen die over elkaar liggen tellen één keer, en
     * na opslaan en laden is alles er nog.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "zones_fit")
    public static void zone_boxes_fit_and_are_saved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 7), "zone-owner");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, CORE, owner);
        BlockPos coreAbs = helper.absolutePos(CORE);
        int r = SecurityBalance.AREA_RADIUS;
        List<ZoneBox> wanted = new ArrayList<>();
        // Steekt 10 blokken uit naar het oosten: wordt afgeknipt op de rand van het gebied.
        wanted.add(ZoneBox.of(coreAbs.offset(r - 5, 0, 0), coreAbs.offset(r + 10, 3, 4)));
        // Helemaal buiten het gebied: valt weg.
        wanted.add(ZoneBox.of(coreAbs.offset(r + 5, 0, 0), coreAbs.offset(r + 8, 2, 2)));
        // Twee kubussen die voor de helft over elkaar liggen: 4x4x4 + 4x4x4 - 2x4x4 = 96 blokken.
        wanted.add(ZoneBox.of(coreAbs.offset(-10, 0, -10), coreAbs.offset(-7, 3, -7)));
        wanted.add(ZoneBox.of(coreAbs.offset(-8, 0, -10), coreAbs.offset(-5, 3, -7)));
        List<ZoneBox> fitted = core.setZoneBoxes(wanted);
        if (fitted.size() != 3) {
            throw new GameTestAssertException("Er zouden 3 kubussen over moeten blijven, niet " + fitted.size());
        }
        if (fitted.getFirst().max().getX() != coreAbs.getX() + r || fitted.getFirst().sizeX() != 6) {
            throw new GameTestAssertException("De eerste kubus is niet afgeknipt op de rand: " + fitted.getFirst());
        }
        long expected = 6L * 4 * 5 + 96;
        if (core.zone().volume() != expected) {
            throw new GameTestAssertException("De zone zou " + expected + " blokken moeten tellen, niet " + core.zone().volume());
        }
        // Meer dan het maximum: de rest valt weg.
        List<ZoneBox> many = new ArrayList<>();
        for (int i = 0; i < SecurityBalance.ZONE_MAX_BOXES + 3; i++) {
            many.add(ZoneBox.of(coreAbs.offset(i - 20, 0, 5), coreAbs.offset(i - 20, 1, 6)));
        }
        if (core.setZoneBoxes(many).size() != SecurityBalance.ZONE_MAX_BOXES) {
            throw new GameTestAssertException("Meer dan " + SecurityBalance.ZONE_MAX_BOXES + " kubussen zou niet mogen");
        }
        // Opslaan en weer laden.
        core.setZoneBoxes(fitted);
        GlobalPos here = GlobalPos.of(level.dimension(), coreAbs);
        List<ZoneBox> reloaded = ZoneData.boxesAfterReload(ZoneData.get(level), here, level.registryAccess());
        if (!reloaded.equals(fitted)) {
            throw new GameTestAssertException("Na opslaan en laden zijn de kubussen anders: " + reloaded);
        }
        finish(helper, core);
    }

    /**
     * Een emmer leeggieten telt als plaatsen: een vreemde giet in de zone geen water (buiten de zone
     * wel; dat bewijst dat de test goed mikt).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void zone_blocks_buckets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 7), "zone-owner");
        ServerPlayer outsider = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(14, 3, 14), "zone-outsider");
        SecurityCoreBlockEntity core = zoneCore(helper, owner);
        BlockPos outsideFloor = new BlockPos(18, 2, 18);
        BlockPos insideFloor = new BlockPos(8, 2, 8);
        helper.setBlock(outsideFloor, Blocks.STONE);
        helper.setBlock(insideFloor, Blocks.STONE);

        lookDownAt(helper, outsider, outsideFloor);
        outsider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        outsider.gameMode.useItem(outsider, level, outsider.getMainHandItem(), InteractionHand.MAIN_HAND);
        helper.assertBlockPresent(Blocks.WATER, outsideFloor.above());

        lookDownAt(helper, outsider, insideFloor);
        outsider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        outsider.gameMode.useItem(outsider, level, outsider.getMainHandItem(), InteractionHand.MAIN_HAND);
        helper.assertBlockNotPresent(Blocks.WATER, insideFloor.above());
        if (!outsider.getMainHandItem().is(Items.WATER_BUCKET)) {
            throw new GameTestAssertException("De emmer van de vreemde is leeg, maar er kwam geen water");
        }
        finish(helper, core);
    }

    /**
     * Stark-kabels leggen of doorknippen telt als bouwen en breken: anders knipte een inbreker zo de
     * stroom van de zone eruit. De eigenaar kan het wel.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void zone_protects_cables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 7), "zone-owner");
        ServerPlayer outsider = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(14, 3, 14), "zone-outsider");
        SecurityCoreBlockEntity core = zoneCore(helper, owner);
        BlockPos floor = new BlockPos(8, 2, 8);
        helper.setBlock(floor, Blocks.STONE);
        BlockPos cell = helper.absolutePos(floor.above());
        CableSpoolItem spool = (CableSpoolItem) ModItems.CABLE.get();

        // Een vreemde legt geen kabel in de zone.
        lookDownAt(helper, outsider, floor);
        ItemStack outsiderSpool = new ItemStack(ModItems.CABLE.get(), 16);
        outsider.setItemInHand(InteractionHand.MAIN_HAND, outsiderSpool);
        spool.onUseTick(level, outsider, outsiderSpool, 0);
        if (StarkCables.has(level, cell)) {
            throw new GameTestAssertException("Een vreemde kon een kabel in de zone leggen");
        }
        spool.releaseUsing(outsiderSpool, level, outsider, 0);

        // De eigenaar wel.
        lookDownAt(helper, owner, floor);
        ItemStack ownerSpool = new ItemStack(ModItems.CABLE.get(), 16);
        owner.setItemInHand(InteractionHand.MAIN_HAND, ownerSpool);
        spool.onUseTick(level, owner, ownerSpool, 0);
        spool.releaseUsing(ownerSpool, level, owner, 0);
        if (!StarkCables.has(level, cell)) {
            throw new GameTestAssertException("De eigenaar kon geen kabel in zijn zone leggen");
        }

        // Een vreemde knipt hem niet door met een schaar.
        outsider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        PlayerInteractEvent.RightClickItem cut = new PlayerInteractEvent.RightClickItem(outsider, InteractionHand.MAIN_HAND);
        CableSpoolItem.onRightClickItem(cut);
        if (!StarkCables.has(level, cell)) {
            throw new GameTestAssertException("Een vreemde kon een kabel in de zone doorknippen");
        }
        finish(helper, core);
    }
}
