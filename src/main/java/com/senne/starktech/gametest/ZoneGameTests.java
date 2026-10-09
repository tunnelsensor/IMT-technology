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
import com.senne.starktech.security.zone.ZoneApp;
import com.senne.starktech.security.zone.ZoneData;
import com.senne.starktech.security.zone.ZoneMarkerItem;
import com.senne.starktech.security.zone.ZoneShape;
import com.senne.starktech.security.zone.ZoneSync;
import com.senne.starktech.station.ChargerBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
     * Jouw wens (9 oktober): een vreemde mag in een zone een emmer leeggieten, maar alleen op de grond.
     * Tegen een muur niet (anders klom hij met water omhoog).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void zone_buckets_only_on_the_ground(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 7), "zone-owner");
        ServerPlayer outsider = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(14, 3, 14), "zone-outsider");
        SecurityCoreBlockEntity core = zoneCore(helper, owner);
        BlockPos floor = new BlockPos(6, 2, 10);
        helper.setBlock(floor, Blocks.STONE);
        // Op de grond: mag.
        lookDownAt(helper, outsider, floor);
        outsider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        outsider.gameMode.useItem(outsider, level, outsider.getMainHandItem(), InteractionHand.MAIN_HAND);
        helper.assertBlockPresent(Blocks.WATER, floor.above());
        // Tegen een muur: niet.
        BlockPos wall = new BlockPos(8, 5, 8);
        helper.setBlock(wall, Blocks.STONE);
        Vec3 eye = helper.absoluteVec(new Vec3(10.5, 5.5 - 1.62, 8.5));
        outsider.moveTo(eye.x, eye.y, eye.z, 90.0f, 0.0f);
        outsider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        outsider.gameMode.useItem(outsider, level, outsider.getMainHandItem(), InteractionHand.MAIN_HAND);
        helper.assertBlockNotPresent(Blocks.WATER, wall.east());
        if (!outsider.getMainHandItem().is(Items.WATER_BUCKET)) {
            throw new GameTestAssertException("Tegen een muur zou de emmer vol moeten blijven");
        }
        finish(helper, core);
    }

    /**
     * Wat een vreemde in een zone ook mag (jouw wens, 9 oktober): vuur aansteken, een bijl op een stam,
     * een schoffel op aarde, en Stark-kabels leggen en knippen. Een gewoon blok neerzetten nog steeds niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void zone_allows_fire_tools_and_cables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 7), "zone-owner");
        ServerPlayer outsider = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(14, 3, 14), "zone-outsider");
        SecurityCoreBlockEntity core = zoneCore(helper, owner);
        helper.setBlock(new BlockPos(6, 2, 6), Blocks.STONE);
        helper.setBlock(new BlockPos(8, 3, 8), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(10, 2, 10), Blocks.DIRT);
        if (!place(helper, outsider, Items.FLINT_AND_STEEL, new BlockPos(6, 2, 6), Direction.UP)) {
            throw new GameTestAssertException("Een vreemde zou in een zone vuur moeten kunnen aansteken");
        }
        helper.assertBlockPresent(Blocks.FIRE, new BlockPos(6, 3, 6));
        helper.setBlock(new BlockPos(6, 3, 6), Blocks.AIR);
        place(helper, outsider, Items.IRON_AXE, new BlockPos(8, 3, 8), Direction.UP);
        helper.assertBlockPresent(Blocks.STRIPPED_OAK_LOG, new BlockPos(8, 3, 8));
        place(helper, outsider, Items.IRON_HOE, new BlockPos(10, 2, 10), Direction.UP);
        helper.assertBlockPresent(Blocks.FARMLAND, new BlockPos(10, 2, 10));
        if (place(helper, outsider, Items.COBBLESTONE, new BlockPos(6, 2, 6), Direction.UP)) {
            throw new GameTestAssertException("Een gewoon blok neerzetten zou nog steeds niet mogen");
        }
        // Kabels: leggen en knippen.
        BlockPos floor = new BlockPos(8, 2, 10);
        helper.setBlock(floor, Blocks.STONE);
        BlockPos cell = helper.absolutePos(floor.above());
        CableSpoolItem spool = (CableSpoolItem) ModItems.CABLE.get();
        lookDownAt(helper, outsider, floor);
        ItemStack cables = new ItemStack(ModItems.CABLE.get(), 16);
        outsider.setItemInHand(InteractionHand.MAIN_HAND, cables);
        spool.onUseTick(level, outsider, cables, 0);
        spool.releaseUsing(cables, level, outsider, 0);
        if (!StarkCables.has(level, cell)) {
            throw new GameTestAssertException("Een vreemde zou in een zone een kabel moeten kunnen leggen");
        }
        outsider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        CableSpoolItem.onRightClickItem(new PlayerInteractEvent.RightClickItem(outsider, InteractionHand.MAIN_HAND));
        if (StarkCables.has(level, cell)) {
            throw new GameTestAssertException("Een vreemde zou in een zone een kabel moeten kunnen doorknippen");
        }
        finish(helper, core);
    }

    /**
     * De rand: alleen de echte buitenkant van de zone. Een L van twee kubussen die elkaar raken
     * (4x2x2 en 2x2x3): 40 + 32 vlakjes, min twee keer de 4 waar ze tegen elkaar liggen = 64. Twee
     * kubussen die half over elkaar liggen zijn samen één doos van 6x4x4: 128 vlakjes, niets dubbel.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "zones")
    public static void zone_border_is_only_the_outside(GameTestHelper helper) {
        List<ZoneBox> l = List.of(ZoneBox.of(new BlockPos(0, 0, 0), new BlockPos(3, 1, 1)),
                ZoneBox.of(new BlockPos(0, 0, 2), new BlockPos(1, 1, 4)));
        long lArea = ZoneShape.boundary(l).stream().mapToLong(ZoneShape.Rect::area).sum();
        if (lArea != 64) {
            throw new GameTestAssertException("De L zou 64 vlakjes buitenkant moeten hebben, niet " + lArea);
        }
        List<ZoneBox> overlap = List.of(ZoneBox.of(new BlockPos(0, 0, 0), new BlockPos(3, 3, 3)),
                ZoneBox.of(new BlockPos(2, 0, 0), new BlockPos(5, 3, 3)));
        long overlapArea = ZoneShape.boundary(overlap).stream().mapToLong(ZoneShape.Rect::area).sum();
        if (overlapArea != 128) {
            throw new GameTestAssertException("Twee kubussen over elkaar zouden 128 vlakjes moeten hebben, niet " + overlapArea);
        }
        helper.succeed();
    }

    /**
     * Jouw keuze: de rand zien alleen de eigenaar en zijn vrienden. De server stuurt de zone dus naar
     * de eigenaar en een vertrouwde speler, niet naar een vreemde. Zonder stroom krijgt de eigenaar
     * hem nog wel (zijn spel tekent hem dan rood).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void zone_border_only_for_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 7), "zone-owner");
        ServerPlayer outsider = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(14, 3, 14), "zone-outsider");
        ServerPlayer friend = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(16, 3, 14), "zone-friend");
        SecurityCoreBlockEntity core = zoneCore(helper, owner);
        core.addTrusted(friend.getUUID(), friend.getGameProfile().getName());
        BlockPos coreAbs = helper.absolutePos(CORE);
        if (!seesZone(owner, coreAbs, true) || !seesZone(friend, coreAbs, true)) {
            throw new GameTestAssertException("De eigenaar en zijn vriend zouden de zone moeten krijgen");
        }
        if (seesZone(outsider, coreAbs, true) || seesZone(outsider, coreAbs, false)) {
            throw new GameTestAssertException("Een vreemde zou de zone niet mogen krijgen");
        }
        power(helper, false);
        core.powerTick(level);
        if (!seesZone(owner, coreAbs, false)) {
            throw new GameTestAssertException("Zonder stroom zou de eigenaar de zone nog krijgen, als 'uit'");
        }
        finish(helper, core);
    }

    /** Krijgt deze speler van de server de zone van de kern op {@code core} (actief of niet)? */
    private static boolean seesZone(ServerPlayer player, BlockPos core, boolean active) {
        return ZoneSync.visibleTo(player).stream().anyMatch(view -> view.core().equals(core) && view.active() == active);
    }

    /** Rechtsklik met de Zone Marker op dit blok (via onItemUseFirst, zoals in het echt). */
    private static void markerRightClick(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        ItemStack marker = player.getMainHandItem();
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.UP, 0.5), Direction.UP, abs, false);
        ((ZoneMarkerItem) marker.getItem()).onItemUseFirst(marker, new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /**
     * De Zone Marker: linksklik = hoek 1, rechtsklik = hoek 2, sluipen + rechtsklik = de kubus erbij
     * (de keuze is daarna weer leeg). Sluipen + linksklik in die kubus vraagt het eerst, de tweede klik
     * haalt hem weg. Een vreemde kan met een marker niets aan jouw zone doen.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void zone_marker_adds_and_removes_boxes(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 3, 4), "zone-owner");
        ServerPlayer outsider = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(9, 3, 4), "zone-outsider");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, CORE, owner);
        // Een kern meldt zich pas een tick na het neerzetten aan (dan pas vindt de marker hem).
        helper.runAfterDelay(2, () -> {
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ZONE_MARKER.get()));
            ZoneMarkerItem.leftClick(owner, helper.absolutePos(new BlockPos(5, 2, 5)));
            markerRightClick(helper, owner, new BlockPos(10, 6, 9));
            ZoneBox chosen = ZoneMarkerItem.selection(owner.getMainHandItem()).box();
            ZoneBox expected = box(helper, new BlockPos(5, 2, 5), new BlockPos(10, 6, 9));
            if (!expected.equals(chosen)) {
                throw new GameTestAssertException("De marker zou de kubus " + expected + " moeten kiezen, niet " + chosen);
            }
            // Een vreemde met dezelfde keuze kan hem niet aan de zone van de eigenaar toevoegen.
            ItemStack outsiderMarker = new ItemStack(ModItems.ZONE_MARKER.get());
            outsiderMarker.set(com.senne.starktech.registry.ModDataComponents.ZONE_SELECTION.get(),
                    ZoneMarkerItem.selection(owner.getMainHandItem()));
            outsider.setItemInHand(InteractionHand.MAIN_HAND, outsiderMarker);
            outsider.setShiftKeyDown(true);
            outsiderMarker.getItem().use(helper.getLevel(), outsider, InteractionHand.MAIN_HAND);
            if (!core.zoneBoxes().isEmpty()) {
                throw new GameTestAssertException("Een vreemde kon een kubus aan de zone van een ander toevoegen");
            }
            // De eigenaar: sluipen + rechtsklik voegt hem toe.
            owner.setShiftKeyDown(true);
            owner.getMainHandItem().getItem().use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
            if (!core.zoneBoxes().equals(List.of(expected))) {
                throw new GameTestAssertException("Na sluipen + rechtsklik zou de zone die kubus moeten hebben: " + core.zoneBoxes());
            }
            if (ZoneMarkerItem.selection(owner.getMainHandItem()).box() != null) {
                throw new GameTestAssertException("Na het toevoegen zou de keuze van de marker leeg moeten zijn");
            }
            if (!DatacenterGameTests.logged(core, EventLog.Type.ZONE, "box 1 added")) {
                throw new GameTestAssertException("Het logboek zegt niet dat er een kubus bij kwam");
            }
            // Sluipen + linksklik in de kubus: eerst de vraag, dan weg.
            BlockPos inside = helper.absolutePos(new BlockPos(7, 3, 7));
            ZoneMarkerItem.leftClick(owner, inside);
            if (core.zoneBoxes().isEmpty()) {
                throw new GameTestAssertException("De eerste klik zou het alleen moeten vragen");
            }
            ZoneMarkerItem.leftClick(owner, inside);
            if (!core.zoneBoxes().isEmpty()) {
                throw new GameTestAssertException("De tweede klik zou de kubus moeten weghalen: " + core.zoneBoxes());
            }
            finish(helper, core);
        });
    }

    /**
     * De app Zone: New box zet een kubus van 7 x 5 x 7 waar je staat; een + schuift een kant een blok
     * naar buiten, een - naar binnen (maar nooit kleiner dan één blok); getypte getallen worden de
     * hoeken (buiten het gebied van de kern: nee); Delete haalt hem weg; meer dan 16 kan niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void zone_app_edits_boxes(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 3, 8), "zone-owner");
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, CORE, owner);
        BlockPos feet = owner.blockPosition();
        ZoneApp.handle(owner, core, "zone_new", new CompoundTag(), false);
        ZoneBox first = ZoneBox.of(feet.offset(-3, -1, -3), feet.offset(3, 3, 3));
        if (!core.zoneBoxes().equals(List.of(first))) {
            throw new GameTestAssertException("New box zou " + first + " moeten maken, niet " + core.zoneBoxes());
        }
        // De oostkant een blok naar buiten, dan de westkant een naar binnen.
        CompoundTag grow = new CompoundTag();
        grow.putInt("i", 0);
        grow.putInt("side", 5);
        grow.putInt("by", 1);
        ZoneApp.handle(owner, core, "zone_grow", grow, false);
        grow.putInt("side", 4);
        grow.putInt("by", -1);
        ZoneApp.handle(owner, core, "zone_grow", grow, false);
        ZoneBox moved = core.zoneBoxes().getFirst();
        if (moved.max().getX() != first.max().getX() + 1 || moved.min().getX() != first.min().getX() + 1 || moved.sizeX() != 7) {
            throw new GameTestAssertException("Na + oost en - west zou de kubus een blok naar het oosten moeten liggen: " + moved);
        }
        // Kleiner dan één blok kan niet.
        grow.putInt("side", 1);
        grow.putInt("by", -10);
        ZoneApp.Result tooSmall = ZoneApp.handle(owner, core, "zone_grow", grow, false);
        if (!tooSmall.error() || !core.zoneBoxes().getFirst().equals(moved)) {
            throw new GameTestAssertException("Een kubus kleiner dan één blok zou niet mogen");
        }
        // Getypte getallen.
        BlockPos a = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos b = helper.absolutePos(new BlockPos(12, 9, 11));
        CompoundTag set = new CompoundTag();
        set.putInt("i", 0);
        set.putIntArray("box", new int[] {a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()});
        ZoneApp.handle(owner, core, "zone_set", set, false);
        if (!core.zoneBoxes().getFirst().equals(ZoneBox.of(a, b))) {
            throw new GameTestAssertException("De getypte hoeken zouden de kubus moeten worden: " + core.zoneBoxes());
        }
        BlockPos far = helper.absolutePos(CORE).offset(200, 0, 0);
        set.putIntArray("box", new int[] {far.getX(), far.getY(), far.getZ(), far.getX() + 3, far.getY() + 3, far.getZ() + 3});
        ZoneApp.Result outside = ZoneApp.handle(owner, core, "zone_set", set, false);
        if (!outside.error() || !core.zoneBoxes().getFirst().equals(ZoneBox.of(a, b))) {
            throw new GameTestAssertException("Een kubus helemaal buiten het gebied zou niet mogen (en de oude zou moeten blijven)");
        }
        // Weg ermee.
        CompoundTag delete = new CompoundTag();
        delete.putInt("i", 0);
        ZoneApp.handle(owner, core, "zone_delete", delete, false);
        if (!core.zoneBoxes().isEmpty()) {
            throw new GameTestAssertException("Delete zou de kubus moeten weghalen");
        }
        // Meer dan het maximum kan niet.
        for (int i = 0; i < SecurityBalance.ZONE_MAX_BOXES; i++) {
            ZoneApp.handle(owner, core, "zone_new", new CompoundTag(), false);
        }
        ZoneApp.Result full = ZoneApp.handle(owner, core, "zone_new", new CompoundTag(), false);
        if (!full.error() || core.zoneBoxes().size() != SecurityBalance.ZONE_MAX_BOXES) {
            throw new GameTestAssertException("Meer dan " + SecurityBalance.ZONE_MAX_BOXES + " kubussen zou niet mogen");
        }
        finish(helper, core);
    }

    // ================================================================ Stap 4: alleen C4 komt erdoor

    /**
     * Een explosie zonder TNT of satelliet (zoals een creeper) naast de zone: de aarde erbuiten vliegt
     * weg, de aarde in de zone blijft.
     * Stak de eigenaar hem zelf aan, dan gaat zijn eigen zone wel kapot (je eigen TNT in je eigen basis).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void zone_stops_explosions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 18), "zone-owner");
        SecurityCoreBlockEntity core = zoneCore(helper, owner);
        for (int x = 10; x <= 15; x++) {
            helper.setBlock(new BlockPos(x, 3, 8), Blocks.DIRT);
        }
        Vec3 blast = helper.absoluteVec(new Vec3(13.5, 3.5, 8.5));
        level.explode(null, blast.x, blast.y, blast.z, 3.0f, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
        helper.assertBlockNotPresent(Blocks.DIRT, new BlockPos(14, 3, 8));
        helper.assertBlockPresent(Blocks.DIRT, new BlockPos(12, 3, 8));
        helper.assertBlockPresent(Blocks.DIRT, new BlockPos(11, 3, 8));
        // Nu door de eigenaar zelf.
        level.explode(owner, blast.x, blast.y, blast.z, 3.0f, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
        helper.assertBlockNotPresent(Blocks.DIRT, new BlockPos(12, 3, 8));
        finish(helper, core);
    }

    /**
     * Vuur brandt geen blokken van een zone weg (FireBlockMixin): een vuur op een plank in de zone en
     * een op een plank erbuiten branden 300 keer; de plank erbuiten is weg, die in de zone niet. Lava
     * steekt in de zone ook niets aan.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void zone_stops_fire_and_lava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 18), "zone-owner");
        SecurityCoreBlockEntity core = zoneCore(helper, owner);
        BlockPos inside = new BlockPos(8, 3, 8);
        BlockPos outside = new BlockPos(18, 3, 18);
        helper.setBlock(inside, Blocks.OAK_PLANKS);
        helper.setBlock(outside, Blocks.OAK_PLANKS);
        // De testwereld zet vuur standaard stil (doFireTick uit); alleen voor deze lus even aan.
        var fireTick = level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOFIRETICK);
        boolean wasOn = fireTick.get();
        fireTick.set(true, level.getServer());
        try {
            for (int i = 0; i < 300; i++) {
                for (BlockPos planks : new BlockPos[] {inside, outside}) {
                    BlockPos fire = helper.absolutePos(planks.above());
                    if (!level.getBlockState(helper.absolutePos(planks)).is(Blocks.OAK_PLANKS)) {
                        continue;
                    }
                    if (!level.getBlockState(fire).is(Blocks.FIRE)) {
                        level.setBlock(fire, Blocks.FIRE.defaultBlockState(), 2);
                    }
                    level.getBlockState(fire).tick(level, fire, level.random);
                }
            }
        } finally {
            fireTick.set(wasOn, level.getServer());
        }
        helper.setBlock(inside.above(), Blocks.AIR);
        helper.setBlock(outside.above(), Blocks.AIR);
        helper.assertBlockNotPresent(Blocks.OAK_PLANKS, outside);
        helper.assertBlockPresent(Blocks.OAK_PLANKS, inside);
        // Lava die vuur zou maken: in de zone niet, erbuiten wel.
        BlockPos lavaInside = helper.absolutePos(new BlockPos(9, 4, 8));
        BlockPos lavaOutside = helper.absolutePos(new BlockPos(19, 4, 18));
        if (net.neoforged.neoforge.event.EventHooks.fireFluidPlaceBlockEvent(level, lavaInside.west(), lavaInside,
                Blocks.FIRE.defaultBlockState()).is(Blocks.FIRE)) {
            throw new GameTestAssertException("Lava stak in de zone iets aan");
        }
        if (!net.neoforged.neoforge.event.EventHooks.fireFluidPlaceBlockEvent(level, lavaOutside.west(), lavaOutside,
                Blocks.FIRE.defaultBlockState()).is(Blocks.FIRE)) {
            throw new GameTestAssertException("Lava buiten de zone zou wel iets moeten aansteken");
        }
        finish(helper, core);
    }

    /**
     * Een zuiger buiten de zone duwt geen blok de zone in (of erdoor); een zuiger erbuiten, zonder
     * zone, duwt gewoon.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "zones")
    public static void zone_stops_pistons(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 18), "zone-owner");
        SecurityCoreBlockEntity core = zoneCore(helper, owner);
        net.minecraft.world.level.block.state.BlockState piston = Blocks.PISTON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, Direction.WEST);
        helper.setBlock(new BlockPos(12, 3, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(13, 3, 8), piston);
        helper.setBlock(new BlockPos(17, 3, 16), Blocks.STONE);
        helper.setBlock(new BlockPos(18, 3, 16), piston);
        helper.setBlock(new BlockPos(13, 4, 8), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(18, 4, 16), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(8, () -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(16, 3, 16)); // buiten de zone: geduwd
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(12, 3, 8));  // de zone: niet
            helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(11, 3, 8));
            finish(helper, core);
        });
    }

    /**
     * Monsters (hier een enderman) mogen in en vlak bij een zone geen blokken pakken of breken; ver
     * ervandaan wel (zoals altijd in Minecraft).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void zone_stops_mob_griefing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 18), "zone-owner");
        SecurityCoreBlockEntity core = zoneCore(helper, owner);
        var near = helper.spawn(net.minecraft.world.entity.EntityType.ENDERMAN, new BlockPos(13, 3, 8));
        var far = helper.spawn(net.minecraft.world.entity.EntityType.ENDERMAN, new BlockPos(21, 3, 21));
        near.setNoAi(true);
        far.setNoAi(true);
        boolean griefing = level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        if (net.neoforged.neoforge.event.EventHooks.canEntityGrief(level, near)) {
            throw new GameTestAssertException("Een enderman vlak bij de zone zou geen blokken mogen pakken");
        }
        if (griefing && !net.neoforged.neoforge.event.EventHooks.canEntityGrief(level, far)) {
            throw new GameTestAssertException("Een enderman ver van de zone zou wel blokken mogen pakken");
        }
        near.discard();
        far.discard();
        finish(helper, core);
    }

    /**
     * Jouw wens (9 oktober): TNT en de satelliet komen ook door een zone, maar TNT doet heel weinig. TNT
     * van een vreemde breekt in de zone alleen een plank vlakbij; de stenen muur krijgt barsten en
     * breekt pas na drie TNT op dezelfde plek. Een raket van de satelliet doet meer: na twee is de steen weg.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void zone_tnt_and_satellite_do_a_little(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 20), "zone-owner");
        ServerPlayer outsider = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(18, 3, 20), "zone-outsider");
        SecurityCoreBlockEntity core = zoneCore(helper, owner);
        C4GameTests.wall(helper, 8, Blocks.STONE);
        BlockPos stone = new BlockPos(8, 3, 8);
        BlockPos planks = new BlockPos(9, 4, 8);
        helper.setBlock(planks, Blocks.OAK_PLANKS);
        Vec3 at = helper.absoluteVec(new Vec3(9.5, 3.5, 8.5));
        for (int i = 1; i <= 3; i++) {
            net.minecraft.world.entity.item.PrimedTnt tnt = new net.minecraft.world.entity.item.PrimedTnt(level, at.x, at.y, at.z, outsider);
            level.explode(tnt, at.x, at.y, at.z, 4.0f, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
            if (i == 1) {
                helper.assertBlockNotPresent(Blocks.OAK_PLANKS, planks);
                helper.assertBlockPresent(Blocks.STONE, stone);
                if (com.senne.starktech.c4.Cracks.damage(level, helper.absolutePos(stone)) <= 0) {
                    throw new GameTestAssertException("Na één TNT zou de steen barsten moeten hebben");
                }
            } else if (i == 2) {
                helper.assertBlockPresent(Blocks.STONE, stone);
            }
        }
        helper.assertBlockNotPresent(Blocks.STONE, stone);
        if (!DatacenterGameTests.logged(core, EventLog.Type.ALARM, "TNT blast at")) {
            throw new GameTestAssertException("Het logboek zegt niets over de TNT");
        }
        // De satelliet: twee raketten tegen een ander stuk muur (ver genoeg van de TNT, zonder barsten).
        BlockPos high = new BlockPos(8, 5, 6);
        Vec3 sky = helper.absoluteVec(new Vec3(9.5, 5.5, 6.5));
        Runnable rocket = () -> level.explode(outsider, sky.x, sky.y, sky.z, 5.0f, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
        com.senne.starktech.security.zone.Zones.satelliteExplosion(rocket);
        helper.assertBlockPresent(Blocks.STONE, high);
        com.senne.starktech.security.zone.Zones.satelliteExplosion(rocket);
        helper.assertBlockNotPresent(Blocks.STONE, high);
        finish(helper, core);
    }
}
