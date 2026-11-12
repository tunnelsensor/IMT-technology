package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.armory.AmmoBoxBlockEntity;
import com.senne.imttech.armory.AmmoBoxes;
import com.senne.imttech.armory.Pegboard;
import com.senne.imttech.armory.PegboardBlock;
import com.senne.imttech.armory.PegboardBlockEntity;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Firearms;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import com.senne.imttech.weapons.LaserGuns;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de armory (hoofdstuk 67, 9 november). Jouw wens: een Pegboard aan de muur waar je al je wapens mooi aan
 * hangt (vastgeklikt op een raster, panelen naast elkaar worden één wand, iedereen mag eraan), en een Ammo Box waar je
 * zonder scherm munitie in en uit klikt (één soort per kist, sneak = alles of één, rechtsklik inhouden met een magazijn
 * = patroon na patroon laden, en hij houdt zijn inhoud als je hem afbreekt).
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class ArmoryGameTests {
    private ArmoryGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Een speler (survival) met dit item in zijn hand (vakje 0). */
    private static ServerPlayer player(GameTestHelper helper, String name, BlockPos at, ItemStack held) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, at, name);
        player.getAbilities().instabuild = false;
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return player;
    }

    /** Een Ammo Box op deze plek (relatief), met het etiket naar het noorden. */
    private static AmmoBoxBlockEntity box(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        helper.getLevel().setBlockAndUpdate(pos, ModBlocks.AMMO_BOX.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        AmmoBoxBlockEntity box = (AmmoBoxBlockEntity) helper.getLevel().getBlockEntity(pos);
        check(box != null, "Er staat geen Ammo Box");
        return box;
    }

    /** Rechtsklik op de kist met wat je vasthoudt (zoals Minecraft het doet: eerst met het item, dan met een lege hand). */
    private static void click(ServerPlayer player, AmmoBoxBlockEntity box, boolean sneak) {
        player.setShiftKeyDown(sneak);
        BlockPos pos = box.getBlockPos();
        BlockState state = box.getBlockState();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
        ItemStack held = player.getMainHandItem();
        var result = state.useItemOn(held, player.level(), player, InteractionHand.MAIN_HAND, hit);
        if (result == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            state.useWithoutItem(player.level(), player, hit);
        }
        player.setShiftKeyDown(false);
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().countItem(item);
    }

    // ================================================================ De Ammo Box: erin en eruit

    /**
     * Eén soort per kist: rechtsklik doet de stapel in je hand erin, sneak + rechtsklik alles van die soort uit je
     * inventaris; hulzen passen dan niet meer. Met een lege hand komt er een stapel uit (in je inventaris, niet in je
     * hand), met sneak één. Vol is vol.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "armory")
    public static void ammo_box_one_sort_in_and_out(GameTestHelper helper) {
        AmmoBoxBlockEntity box = box(helper, new BlockPos(4, 2, 4));
        ServerPlayer player = player(helper, "armory-loader", new BlockPos(4, 2, 2), new ItemStack(ModItems.ROUND_9MM.get(), 40));
        player.getInventory().setItem(5, new ItemStack(ModItems.ROUND_9MM.get(), 30));
        player.getInventory().setItem(6, new ItemStack(ModItems.CASING_9MM.get(), 20));

        click(player, box, false);
        check(box.count() == 40 && player.getMainHandItem().isEmpty(), "De 40 patronen uit je hand zitten er niet in: " + box.count());

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROUND_9MM.get(), 10));
        click(player, box, true);
        check(box.count() == 80, "Sneak + rechtsklik deed niet alle patronen erin (verwacht 80): " + box.count());
        check(count(player, ModItems.ROUND_9MM.get()) == 0, "Er bleven patronen in je inventaris");
        check(count(player, ModItems.CASING_9MM.get()) == 20, "De hulzen gingen mee in de kist");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CASING_9MM.get(), 5));
        click(player, box, false);
        check(box.count() == 80 && player.getMainHandItem().getCount() == 5, "Hulzen gingen in een kist met patronen");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        click(player, box, false);
        check(box.count() == 16, "Een lege hand nam geen stapel van 64 (er zitten er nog " + box.count() + ")");
        check(player.getMainHandItem().isEmpty(), "De stapel kwam in je hand (dan stopt de volgende klik hem er weer in)");
        check(count(player, ModItems.ROUND_9MM.get()) == 64, "De 64 patronen zitten niet in je inventaris");

        click(player, box, true);
        check(box.count() == 15 && count(player, ModItems.ROUND_9MM.get()) == 65, "Sneak + lege hand nam er niet één uit");

        // Leeg: dan mag er een andere soort in.
        box.clear();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CASING_9MM.get(), 5));
        click(player, box, false);
        check(box.sort().is(ModItems.CASING_9MM.get()) && box.count() == 5, "Een lege kist nam geen hulzen aan");

        // Vol is vol.
        box.clear();
        int capacity = Math.round(ImtConfig.Armory.AMMO_BOX_LOOSE);
        ItemStack many = new ItemStack(ModItems.ROUND_9MM.get(), 64);
        for (int i = 0; i < capacity / 64 + 2; i++) {
            box.insert(many.copy(), 64);
        }
        check(box.count() == capacity, "De kist zit niet precies vol: " + box.count() + " in plaats van " + capacity);
        helper.succeed();
    }

    /** Afbreken: de inhoud gaat mee op het item (zoals een shulkerdoos), en komt terug als je hem weer neerzet. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "armory")
    public static void ammo_box_keeps_contents_when_mined(GameTestHelper helper) {
        AmmoBoxBlockEntity box = box(helper, new BlockPos(4, 2, 4));
        box.insert(new ItemStack(ModItems.ROUND_9MM.get(), 64), 64);
        box.insert(new ItemStack(ModItems.ROUND_9MM.get(), 36), 36);
        ServerLevel level = helper.getLevel();
        List<ItemStack> drops = Block.getDrops(box.getBlockState(), level, box.getBlockPos(), box);
        check(drops.size() == 1 && drops.getFirst().is(ModItems.AMMO_BOX.get()), "De kist viel niet als één item");
        ItemStack item = drops.getFirst();
        ItemContainerContents contents = item.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        int rounds = 0;
        for (ItemStack inside : contents.nonEmptyItems()) {
            check(inside.is(ModItems.ROUND_9MM.get()), "Er zit iets anders dan patronen in het item");
            rounds += inside.getCount();
        }
        check(rounds == 100, "Het item heeft niet de 100 patronen mee: " + rounds);

        AmmoBoxBlockEntity placed = box(helper, new BlockPos(6, 2, 4));
        placed.applyComponentsFromItemStack(item);
        check(placed.count() == 100 && placed.sort().is(ModItems.ROUND_9MM.get()), "Neergezet zat er niet weer 100 in: " + placed.count());

        // Een lege kist krijgt niets mee, zodat hij stapelt met nieuwe kisten.
        box.clear();
        ItemStack empty = Block.getDrops(box.getBlockState(), level, box.getBlockPos(), box).getFirst();
        check(ItemStack.isSameItemSameComponents(empty, new ItemStack(ModItems.AMMO_BOX.get())), "Een lege kist stapelt niet met een nieuwe");
        helper.succeed();
    }

    // ================================================================ De Ammo Box: laden en wisselen

    /**
     * Rechtsklik met een magazijn op een kist patronen: één patroon erin, dezelfde handeling als R (zonder patronen in je
     * inventaris: ze komen uit de kist). Met sneak gaat er een terug in de kist.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "armory")
    public static void ammo_box_loads_a_magazine_round_by_round(GameTestHelper helper) {
        AmmoBoxBlockEntity box = box(helper, new BlockPos(4, 2, 4));
        box.insert(new ItemStack(ModItems.ROUND_9MM.get(), 20), 20);
        ServerPlayer player = player(helper, "armory-mag", new BlockPos(4, 2, 2), MagazineItem.of(FirearmType.GLOCK_19, 0));
        for (int i = 1; i <= 3; i++) {
            click(player, box, false);
            check(Firearms.currentAction(player) == Firearms.ActionKind.LOAD_ROUND, "Rechtsklik " + i + " begon niet met laden");
            Firearms.finishNow(player);
            check(MagazineItem.rounds(player.getMainHandItem()) == i, "Na klik " + i + " zitten er "
                    + MagazineItem.rounds(player.getMainHandItem()) + " patronen in het magazijn");
        }
        check(box.count() == 17, "Er gingen geen 3 patronen uit de kist: " + box.count());

        click(player, box, true);
        check(Firearms.currentAction(player) == Firearms.ActionKind.UNLOAD_ROUND, "Sneak + rechtsklik begon niet met lossen");
        Firearms.finishNow(player);
        check(MagazineItem.rounds(player.getMainHandItem()) == 2 && box.count() == 18, "De patroon ging niet terug in de kist");
        check(count(player, ModItems.ROUND_9MM.get()) == 0, "De patroon kwam in je inventaris in plaats van in de kist");

        // Een magazijn op een lege kist: dan gaat het magazijn er zelf in.
        AmmoBoxBlockEntity mags = box(helper, new BlockPos(6, 2, 4));
        click(player, mags, false);
        check(mags.count() == 1 && player.getMainHandItem().isEmpty(), "Het magazijn ging niet in de lege kist");
        check(MagazineItem.rounds(mags.contents().getFirst()) == 2, "Het magazijn in de kist verloor zijn patronen");
        helper.succeed();
    }

    /**
     * Hoofdstuk 103: elk kaliber is zijn eigen soort. In een kist met 7.62-patronen past geen 9mm; een AK-magazijn laadt
     * eruit (en met sneak terug), een MP5-magazijn niet (dat krijgt de melding dat de kist iets anders houdt). Ook de
     * hulzen en kogelpunten van 7.62 mogen in een kist.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "armory")
    public static void ammo_box_keeps_calibers_apart(GameTestHelper helper) {
        AmmoBoxBlockEntity box = box(helper, new BlockPos(4, 2, 4));
        box.insert(new ItemStack(ModItems.ROUND_762.get(), 20), 20);
        check(!box.fits(new ItemStack(ModItems.ROUND_9MM.get())), "9mm past bij 7.62 in dezelfde kist");
        ServerPlayer player = player(helper, "armory-ak", new BlockPos(4, 2, 2), MagazineItem.of(FirearmType.AK_47, 0));
        click(player, box, false);
        check(Firearms.currentAction(player) == Firearms.ActionKind.LOAD_ROUND, "Een AK-magazijn laadde niet uit een kist 7.62");
        Firearms.finishNow(player);
        check(MagazineItem.rounds(player.getMainHandItem()) == 1 && box.count() == 19, "De patroon ging niet uit de kist in het magazijn");
        click(player, box, true);
        check(Firearms.currentAction(player) == Firearms.ActionKind.UNLOAD_ROUND, "Sneak + rechtsklik begon niet met lossen");
        Firearms.finishNow(player);
        check(MagazineItem.rounds(player.getMainHandItem()) == 0 && box.count() == 20, "De patroon ging niet terug in de kist");
        check(count(player, ModItems.ROUND_762.get()) == 0, "De patroon kwam in je inventaris in plaats van in de kist");
        // Een MP5-magazijn (9mm) op een kist 7.62: niets.
        player.setItemInHand(InteractionHand.MAIN_HAND, MagazineItem.of(FirearmType.MP5, 0));
        check(AmmoBoxes.useFor(player, player.getMainHandItem(), box) == AmmoBoxes.Use.WRONG,
                "Een MP5-magazijn op een kist 7.62 hoort de melding 'andere soort' te geven");
        click(player, box, false);
        check(Firearms.currentAction(player) != Firearms.ActionKind.LOAD_ROUND && box.count() == 20
                && MagazineItem.rounds(player.getMainHandItem()) == 0, "Een MP5-magazijn laadde 7.62-patronen");
        check(!player.getMainHandItem().isEmpty(), "Het MP5-magazijn ging in de kist met patronen");
        // De hulzen en kogelpunten van 7.62 mogen ook in een kist; 9mm-hulzen niet bij 7.62-hulzen.
        check(AmmoBoxBlockEntity.accepts(new ItemStack(ModItems.BULLET_762.get())), "Een 7.62-kogelpunt mag niet in een Ammo Box");
        AmmoBoxBlockEntity casings = box(helper, new BlockPos(6, 2, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CASING_762.get(), 12));
        click(player, casings, false);
        check(casings.count() == 12 && casings.sort().is(ModItems.CASING_762.get()), "De 7.62-hulzen gingen niet in de lege kist");
        check(!casings.fits(new ItemStack(ModItems.CASING_9MM.get())), "9mm-hulzen passen bij 7.62-hulzen in dezelfde kist");
        helper.succeed();
    }

    /**
     * Een lasergeweer op een kist cellen: je cel gaat erin en de volste komt eruit (net zo lang als herladen uit je
     * pouch). Een Glock op een kist magazijnen: hetzelfde met je magazijn.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "armory")
    public static void ammo_box_swaps_cells_and_magazines(GameTestHelper helper) {
        AmmoBoxBlockEntity cells = box(helper, new BlockPos(4, 2, 4));
        for (float charge : new float[]{30, 90, 60}) {
            cells.insert(EnergyCellItem.withCharge(charge), 1);
        }
        ItemStack rifle = LaserGunItem.loaded(GunType.RIFLE);
        LaserGunItem.setCell(rifle, 10);
        ServerPlayer player = player(helper, "armory-swap", new BlockPos(4, 2, 2), rifle);
        click(player, cells, false);
        check(LaserGuns.isReloading(player), "Het lasergeweer begon niet met wisselen");
        LaserGuns.finishReloadNow(player);
        check(Math.abs(LaserGunItem.cell(player.getMainHandItem()) - 90) < 0.01f, "De volste cel (90) zit niet in het geweer: "
                + LaserGunItem.cell(player.getMainHandItem()));
        check(cells.count() == 3 && Math.abs(cells.fullest() - 60) < 0.01f, "De oude cel ging niet in de kist");

        AmmoBoxBlockEntity mags = box(helper, new BlockPos(6, 2, 4));
        mags.insert(MagazineItem.of(FirearmType.GLOCK_19, 7), 1);
        mags.insert(MagazineItem.of(FirearmType.GLOCK_19, 15), 1);
        ItemStack glock = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
        FirearmItem.setMagazine(glock, MagazineItem.of(FirearmType.GLOCK_19, 3));
        player.setItemInHand(InteractionHand.MAIN_HAND, glock);
        click(player, mags, false);
        check(Firearms.currentAction(player) == Firearms.ActionKind.RELOAD_POUCH, "De Glock begon niet met wisselen");
        Firearms.finishNow(player);
        check(FirearmItem.roundsInMagazine(player.getMainHandItem()) == 15, "Het volle magazijn zit niet in de Glock: "
                + FirearmItem.roundsInMagazine(player.getMainHandItem()));
        check(mags.count() == 2 && mags.fullest() == 7, "Het oude magazijn (3) ging niet in de kist");

        // Nog eens: nu zit er geen voller magazijn meer in (7 < 15), dus er gebeurt niets.
        click(player, mags, false);
        check(Firearms.currentAction(player) == null, "Hij wisselde voor een leger magazijn");
        helper.succeed();
    }

    // ================================================================ Het Pegboard

    /** Een rijtje panelen tegen een stenen muur, met hun voorkant naar het zuiden (rechts is dan het oosten). */
    private static BlockPos[] boards(GameTestHelper helper, int y, int... xs) {
        BlockPos[] out = new BlockPos[xs.length];
        for (int i = 0; i < xs.length; i++) {
            BlockPos wall = helper.absolutePos(new BlockPos(xs[i], y, 3));
            helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            out[i] = wall.south();
            helper.getLevel().setBlockAndUpdate(out[i], ModBlocks.PEGBOARD.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        }
        return out;
    }

    private static PegboardBlockEntity board(GameTestHelper helper, BlockPos pos) {
        return (PegboardBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    /**
     * Ophangen op het raster: een sniper (10 gaatjes breed) past niet op één paneel, wel over twee; een Glock komt niet
     * over de sniper heen. Wat erop hangt blijft precies hetzelfde item. Draaien: de Glock wel, de sniper niet (10
     * gaatjes hoog past niet op één rij panelen: hij gaat van links naar rechts en weer terug).
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "armory")
    public static void pegboard_snaps_items_to_the_grid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos[] lone = boards(helper, 2, 7);
        double[] centre = {Pegboard.column(lone[0], Direction.SOUTH) * 8 + 4, lone[0].getY() * 8 + 4};
        ItemStack sniper = LaserGunItem.loaded(GunType.SNIPER);
        check(!PegboardBlock.hangAt(null, level, lone[0], Direction.SOUTH, centre[0], centre[1], sniper.copy(), Pegboard.Turn.LEFT),
                "Een sniper paste op één paneel");

        BlockPos[] pair = boards(helper, 2, 2, 3);
        double cu = Pegboard.column(pair[0], Direction.SOUTH) * 8 + 4;
        double cv = pair[0].getY() * 8 + 4;
        ItemStack held = sniper.copy();
        check(PegboardBlock.hangAt(null, level, pair[0], Direction.SOUTH, cu, cv, held, Pegboard.Turn.LEFT),
                "Een sniper paste niet over twee panelen");
        check(held.isEmpty(), "De sniper bleef in je hand");
        PegboardBlockEntity left = board(helper, pair[0]);
        check(left.hung().size() == 1, "De sniper hangt niet aan het linkerpaneel");
        Pegboard.Spot spot = left.spot(0);
        check(spot.w() == 10 && spot.h() == 3, "De sniper is geen 10 x 3 gaatjes");
        Pegboard.Spot found = Pegboard.itemAt(level, pair[1], Direction.SOUTH, Pegboard.column(pair[1], Direction.SOUTH) * 8 + 1.5,
                spot.v() + 1.5);
        check(found != null && found.board() == left, "Over het rechterpaneel vind je de sniper niet");
        check(ItemStack.isSameItemSameComponents(left.hung().getFirst().stack(), sniper), "Het item veranderde door op te hangen");

        // Een Glock net onder de sniper: hij schuift naar het vrije plekje eronder, nooit over de sniper.
        ItemStack glock = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
        check(PegboardBlock.hangAt(null, level, pair[0], Direction.SOUTH, spot.u() + 3, spot.v() - 1, glock.copy(), Pegboard.Turn.LEFT),
                "De Glock vond geen plekje onder de sniper");
        List<Pegboard.Spot> spots = new java.util.ArrayList<>();
        for (BlockPos pos : pair) {
            PegboardBlockEntity one = board(helper, pos);
            for (int i = 0; i < one.hung().size(); i++) {
                spots.add(one.spot(i));
            }
        }
        check(spots.size() == 2, "Er hangen geen twee dingen");
        Pegboard.Spot a = spots.get(0), b = spots.get(1);
        check(!a.overlaps(b.u(), b.v(), b.w(), b.h()), "De Glock hangt over de sniper");

        // Draaien: de sniper (10 gaatjes hoog) past niet op één rij panelen. Een Glock op het losse paneel wel:
        // naar rechts (zelfde vak), dan naar beneden (4 x 3 wordt 3 x 4).
        // De sniper draait dan van links naar rechts en weer terug (naar beneden en naar boven passen niet).
        Pegboard.Spot sniperSpot = a.w() == 10 ? a : b;
        PegboardBlockEntity sniperBoard = sniperSpot.board();
        check(PegboardBlock.turn(null, sniperBoard.getBlockPos(), Direction.SOUTH, sniperSpot), "De sniper draaide niet naar rechts");
        int sniperIndex = sniperBoard.hung().size() - 1;
        for (int i = 0; i < sniperBoard.hung().size(); i++) {
            if (sniperBoard.spot(i).w() == 10) {
                sniperIndex = i;
            }
        }
        check(sniperBoard.hung().get(sniperIndex).turn() == Pegboard.Turn.RIGHT, "De sniper hangt niet met de loop naar rechts");
        check(PegboardBlock.turn(null, sniperBoard.getBlockPos(), Direction.SOUTH, sniperBoard.spot(sniperIndex)),
                "De sniper draaide niet terug");
        check(sniperBoard.hung().get(sniperIndex).turn() == Pegboard.Turn.LEFT,
                "De sniper draaide door de rand heen (stand " + sniperBoard.hung().get(sniperIndex).turn() + ")");
        check(PegboardBlock.hangAt(null, level, lone[0], Direction.SOUTH, centre[0], centre[1], glock.copy(), Pegboard.Turn.LEFT),
                "De Glock hangt niet op het losse paneel");
        PegboardBlockEntity single = board(helper, lone[0]);
        check(PegboardBlock.turn(null, lone[0], Direction.SOUTH, single.spot(0)), "De Glock draaide niet naar rechts");
        check(PegboardBlock.turn(null, lone[0], Direction.SOUTH, single.spot(0)), "De Glock draaide niet naar beneden");
        check(single.hung().getFirst().turn() == Pegboard.Turn.DOWN && single.spot(0).w() == 3 && single.spot(0).h() == 4,
                "De Glock hangt niet met de loop naar beneden (3 x 4)");
        helper.succeed();
    }

    /**
     * Pakken met een lege hand (je kijkt ernaar: het item komt in je hand, precies zoals het was), sneak + rechtsklik
     * draait, en haal je een paneel weg, dan valt wat erover hing eraf (ook van het paneel ernaast).
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "armory")
    public static void pegboard_take_turn_and_drop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos[] pair = boards(helper, 2, 2, 3);
        ItemStack glock = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.TITANIUM);
        FirearmItem.setMagazine(glock, MagazineItem.of(FirearmType.GLOCK_19, 9));
        double cu = Pegboard.column(pair[0], Direction.SOUTH) * 8 + 4;
        double cv = pair[0].getY() * 8 + 4;
        check(PegboardBlock.hangAt(null, level, pair[0], Direction.SOUTH, cu, cv, glock.copy(), Pegboard.Turn.LEFT), "De Glock hangt niet");
        PegboardBlockEntity left = board(helper, pair[0]);

        // Iemand (iedereen mag) kijkt naar de Glock en draait hem met sneak + rechtsklik, en pakt hem dan. Hij staat in het
        // blok voor het paneel (dichtbij genoeg).
        ServerPlayer stranger = player(helper, "armory-stranger", new BlockPos(2, 2, 5), ItemStack.EMPTY);
        Vec3 centre = left.centre(0);
        stranger.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, centre);
        BlockHitResult hit = new BlockHitResult(centre.add(0, 0, -2.0 / 16), Direction.SOUTH, pair[0], false);
        stranger.setShiftKeyDown(true);
        left.getBlockState().useWithoutItem(level, stranger, hit);
        stranger.setShiftKeyDown(false);
        check(left.hung().size() == 1 && left.hung().getFirst().turn() == Pegboard.Turn.RIGHT, "Sneak + rechtsklik draaide de Glock niet");
        left.getBlockState().useWithoutItem(level, stranger, hit);
        check(left.hung().isEmpty(), "De Glock hangt er nog");
        check(ItemStack.isSameItemSameComponents(stranger.getMainHandItem(), glock), "Je kreeg niet precies dezelfde Glock terug");

        // Een rifle over beide panelen; haal het rechterpaneel weg en hij valt eraf.
        ItemStack rifle = LaserGunItem.loaded(GunType.RIFLE);
        check(PegboardBlock.hangAt(null, level, pair[0], Direction.SOUTH, cu + 4, cv, rifle.copy(), Pegboard.Turn.LEFT), "De rifle hangt niet");
        Pegboard.Spot spot = Pegboard.itemAt(level, pair[0], Direction.SOUTH, cu + 4, cv);
        check(spot != null, "Je vindt de rifle niet");
        check(Pegboard.boardAt(pair[0], Direction.SOUTH, spot.u() + spot.w() - 1, spot.v()).equals(pair[1]),
                "De rifle hangt niet over het rechterpaneel");
        PegboardBlockEntity holder = spot.board();
        level.removeBlock(pair[1], false);
        check(holder.hung().isEmpty(), "De rifle bleef hangen boven niets");
        List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2),
                entity -> LaserGunItem.typeOf(entity.getItem()) == GunType.RIFLE);
        check(dropped.size() == 1, "De rifle viel niet op de grond");
        helper.succeed();
    }

    // ================================================================ Hoofdstuk 70: vastzetten, hoeken, bereik

    /** Rechtsklik met een lege hand op wapen {@code index} van dit paneel (pakken, of met sneak draaien), zoals Minecraft het doet. */
    private static void clickOn(ServerPlayer player, PegboardBlockEntity board, int index, boolean sneak) {
        Vec3 centre = board.centre(index);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, centre);
        Direction wall = board.wall(index);
        Vec3 onPanel = centre.subtract(wall.getStepX() * 2.0 / 16, 0, wall.getStepZ() * 2.0 / 16);
        player.setShiftKeyDown(sneak);
        board.getBlockState().useWithoutItem(player.level(), player, new BlockHitResult(onPanel, wall, board.getBlockPos(), false));
        player.setShiftKeyDown(false);
    }

    /** Het midden van wapen {@code index}, in gaatjes over de wand: {U, V}. */
    private static double[] middle(PegboardBlockEntity board, int index) {
        Pegboard.Spot spot = board.spot(index);
        return new double[]{spot.u() + spot.w() / 2.0, spot.v() + spot.h() / 2.0};
    }

    /**
     * Vastzetten (jouw keuze: sneak + linksklik): pak je een vastgezette Glock, dan blijft zijn schaduw op de plek, zoals
     * hij hing (ook gedraaid). Een magazijn kan er niet over, een andere Glock (titanium) wel: door op de schaduw te
     * klikken, en dan hangt hij precies daar, weer vastgezet. Sneak + linksklik op een schaduw veegt hem weg, en een
     * schaduw valt nooit als item op de grond.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "armory")
    public static void pegboard_lock_leaves_a_shadow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos[] pair = boards(helper, 2, 2, 3);
        ItemStack steel = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
        double cu = Pegboard.column(pair[0], Direction.SOUTH) * 8 + 4;
        double cv = pair[0].getY() * 8 + 4;
        check(PegboardBlock.hangAt(null, level, pair[0], Direction.SOUTH, cu, cv, steel.copy(), Pegboard.Turn.LEFT), "De Glock hangt niet");
        PegboardBlockEntity left = board(helper, pair[0]);
        ServerPlayer player = player(helper, "armory-locker", new BlockPos(2, 2, 5), ItemStack.EMPTY);

        // Draaien (loop naar rechts), dan vastzetten.
        clickOn(player, left, 0, true);
        check(left.hung().getFirst().turn() == Pegboard.Turn.RIGHT, "De Glock draaide niet");
        double[] at = middle(left, 0);
        check(PegboardBlock.toggleLock(player, level, pair[0], Direction.SOUTH, at[0], at[1]), "Vastzetten lukte niet");
        check(left.hung().getFirst().locked(), "De Glock is niet vastgezet");
        Pegboard.Spot before = left.spot(0);

        // Pakken: in je hand, en zijn schaduw blijft (zelfde plek, zelfde stand).
        clickOn(player, left, 0, false);
        check(ItemStack.isSameItemSameComponents(player.getMainHandItem(), steel), "Je kreeg de Glock niet terug");
        check(left.hung().size() == 1 && left.hung().getFirst().shadow(), "Er bleef geen schaduw op de plek");
        Pegboard.Spot shadow = left.spot(0);
        check(shadow.u() == before.u() && shadow.v() == before.v() && left.hung().getFirst().turn() == Pegboard.Turn.RIGHT,
                "De schaduw ligt niet waar de Glock hing");
        check(Pegboard.itemAt(level, pair[0], Direction.SOUTH, at[0], at[1]) != null, "Op de schaduw vind je niets");

        // Een magazijn op de schaduw: dat hangt ernaast (of nergens), nooit eroverheen.
        ItemStack magazine = MagazineItem.of(FirearmType.GLOCK_19, 15);
        PegboardBlock.hangAt(null, level, pair[0], Direction.SOUTH, at[0], at[1], magazine.copy(), Pegboard.Turn.LEFT);
        for (BlockPos pos : pair) {
            PegboardBlockEntity one = board(helper, pos);
            for (int i = 0; i < one.hung().size(); i++) {
                Pegboard.Spot spot = one.spot(i);
                check(spot.shadow() || !spot.overlaps(shadow.u(), shadow.v(), shadow.w(), shadow.h()),
                        "Het magazijn hangt over de schaduw van de Glock");
            }
            // Het magazijn mag weer weg (verder gaat het om de Glock).
            for (int i = one.hung().size() - 1; i >= 0; i--) {
                if (MagazineItem.typeOf(one.hung().get(i).stack()) != null) {
                    one.remove(i);
                }
            }
        }

        // Een titanium Glock (hetzelfde soort) op de schaduw: precies daar, gedraaid zoals de schaduw, weer vastgezet.
        ItemStack titanium = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.TITANIUM);
        ItemStack held = titanium.copy();
        check(PegboardBlock.hangAt(null, level, pair[0], Direction.SOUTH, at[0], at[1], held, Pegboard.Turn.LEFT),
                "De titanium Glock ging niet op de schaduw");
        check(held.isEmpty(), "De titanium Glock bleef in je hand");
        PegboardBlockEntity.Hung back = left.hung().getFirst();
        check(!back.shadow() && back.locked() && back.turn() == Pegboard.Turn.RIGHT
                        && ItemStack.isSameItemSameComponents(back.stack(), titanium),
                "De titanium Glock hangt niet als de schaduw (vastgezet, loop naar rechts)");
        check(left.spot(0).u() == before.u() && left.spot(0).v() == before.v(), "De titanium Glock hangt niet op de plek van de schaduw");

        // Weer pakken, en de schaduw wegvegen (sneak + linksklik op de schaduw): de plek is vrij.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        clickOn(player, left, 0, false);
        check(left.hung().getFirst().shadow(), "Na het pakken bleef er geen schaduw");
        check(PegboardBlock.toggleLock(player, level, pair[0], Direction.SOUTH, at[0], at[1]), "De schaduw wegvegen lukte niet");
        check(left.hung().isEmpty(), "De schaduw ligt er nog");

        // Losgezet: pakken laat niets achter. En een schaduw valt nooit als item op de grond.
        check(PegboardBlock.hangAt(null, level, pair[0], Direction.SOUTH, cu, cv, steel.copy(), Pegboard.Turn.LEFT), "De Glock hangt niet weer");
        double[] again = middle(left, 0);
        PegboardBlock.toggleLock(null, level, pair[0], Direction.SOUTH, again[0], again[1]);
        PegboardBlock.toggleLock(null, level, pair[0], Direction.SOUTH, again[0], again[1]);
        check(!left.hung().getFirst().locked(), "Twee keer sneak + linksklik zette hem niet los");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        clickOn(player, left, 0, false);
        check(left.hung().isEmpty(), "Een losse Glock liet een schaduw achter");
        check(PegboardBlock.hangAt(null, level, pair[0], Direction.SOUTH, cu, cv, steel.copy(), Pegboard.Turn.LEFT), "De Glock hangt niet weer");
        PegboardBlock.toggleLock(null, level, pair[0], Direction.SOUTH, middle(left, 0)[0], middle(left, 0)[1]);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        clickOn(player, left, 0, false);
        player.getInventory().clearContent();
        level.removeBlock(pair[0], false);
        List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2),
                entity -> FirearmItem.typeOf(entity.getItem()) != null);
        check(dropped.isEmpty(), "Een schaduw viel als Glock op de grond");
        helper.succeed();
    }

    /**
     * Een hoek (hoofdstuk 70): met een Pegboard op de andere muur van een binnenhoek klikken zet het tweede paneel in
     * hetzelfde blok. Het eerste gaatje naast de andere wand is dicht; in de twee gaatjes daarna mag iets hangen, maar niet
     * als op de andere wand op die hoogte ook iets in de hoek hangt. Hangt er al iets in dat eerste gaatje, dan komt er
     * geen tweede paneel bij. Afgebroken: twee Pegboards en alles wat erop hing.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "armory")
    public static void pegboard_corner_two_walls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // De muur in het noorden (z = 3) met panelen op x = 2 en 3, en een muur in het westen (x = 1) naast de hoek.
        BlockPos[] main = boards(helper, 2, 2, 3);
        BlockPos westWall = helper.absolutePos(new BlockPos(1, 2, 4));
        level.setBlockAndUpdate(westWall, Blocks.STONE.defaultBlockState());
        BlockPos corner = main[0];
        check(corner.equals(westWall.east()), "De hoek ligt niet naast de westmuur");

        // Met een Pegboard op de westmuur klikken (zijn oostkant, in het blok van de hoek).
        ServerPlayer player = player(helper, "armory-corner", new BlockPos(3, 2, 6), new ItemStack(ModItems.PEGBOARD.get(), 3));
        Vec3 onWall = new Vec3(westWall.getX() + 1, westWall.getY() + 0.5, westWall.getZ() + 0.5);
        player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(onWall, Direction.EAST, westWall, false)));
        BlockState state = level.getBlockState(corner);
        check(state.getValue(PegboardBlock.CORNER) == PegboardBlock.Corner.LEFT && Pegboard.sideFacing(state) == Direction.EAST,
                "Er kwam geen tweede paneel tegen de westmuur (" + state + ")");
        check(player.getMainHandItem().getCount() == 2, "Het tweede paneel kostte geen Pegboard");
        check(Pegboard.isBoard(level, corner, Direction.SOUTH) && Pegboard.isBoard(level, corner, Direction.EAST),
                "Het blok is niet allebei de wanden");

        // De noordwand (kijkt naar het zuiden): de westmuur ligt links, dus gaatje 0 is dicht en gaatje 1 open.
        int northCol = Pegboard.column(corner, Direction.SOUTH) * 8;
        int row = corner.getY() * 8;
        check(!Pegboard.fits(level, corner, Direction.SOUTH, northCol, row + 2, 1, 3, null), "Gaatje 0 naast de westwand is open");
        check(Pegboard.fits(level, corner, Direction.SOUTH, northCol + 1, row + 2, 1, 3, null), "Gaatje 1 naast de westwand is dicht");
        // De westwand (kijkt naar het oosten, rechts is het noorden): gaatje 7 ligt tegen de noordmuur.
        int westCol = Pegboard.column(corner, Direction.EAST) * 8;
        check(!Pegboard.fits(level, corner, Direction.EAST, westCol + 7, row + 2, 1, 3, null), "Gaatje 7 naast de noordwand is open");

        // Een magazijn in de hoekstrook van de noordwand (gaatje 1): op de westwand op die hoogte niets in de hoekstrook.
        ItemStack magazine = MagazineItem.of(FirearmType.GLOCK_19, 15);
        PegboardBlockEntity board = board(helper, corner);
        board.add(magazine, Direction.SOUTH, northCol + 1, row + 2, Pegboard.Turn.LEFT, false);
        check(!Pegboard.fits(level, corner, Direction.EAST, westCol + 6, row + 2, 1, 3, null),
                "Op de westwand mocht iets in de hoek, door het magazijn op de noordwand heen");
        check(Pegboard.fits(level, corner, Direction.EAST, westCol + 4, row + 2, 1, 3, null), "Buiten de hoekstrook mocht niets");
        check(Pegboard.fits(level, corner, Direction.EAST, westCol + 6, row + 5, 1, 3, null), "Hoger in de hoek mocht niets");
        // Ophangen aan de westwand via een klik (zoals een speler): het komt op het tweede paneel.
        check(PegboardBlock.hangAt(null, level, corner, Direction.EAST, westCol + 4.5, row + 3.5, magazine.copy(), Pegboard.Turn.LEFT),
                "Op de westwand hangt niets");
        int onWest = 0;
        for (int i = 0; i < board.hung().size(); i++) {
            if (board.wall(i) == Direction.EAST) {
                onWest++;
                check(board.hung().get(i).side(), "Het magazijn op de westwand hangt niet op het tweede paneel");
            }
        }
        check(onWest == 1, "Er hangen " + onWest + " dingen op de westwand");

        // Een hoek waar al iets in het eerste gaatje hangt: geen tweede paneel.
        BlockPos eastWall = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(eastWall, Blocks.STONE.defaultBlockState());
        PegboardBlockEntity right = board(helper, main[1]);
        right.add(magazine, Direction.SOUTH, Pegboard.column(main[1], Direction.SOUTH) * 8 + 7, row + 2, Pegboard.Turn.LEFT, false);
        player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(eastWall.getX(), eastWall.getY() + 0.5, eastWall.getZ() + 0.5), Direction.WEST, eastWall, false)));
        check(level.getBlockState(main[1]).getValue(PegboardBlock.CORNER) == PegboardBlock.Corner.NONE,
                "Er kwam een tweede paneel over het magazijn heen");
        check(player.getMainHandItem().getCount() == 2, "Een geweigerd paneel kostte toch een Pegboard");

        // Afbreken: twee Pegboards en de twee magazijnen.
        level.destroyBlock(corner, true);
        List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2), entity -> true);
        int boards = 0, magazines = 0;
        for (ItemEntity entity : dropped) {
            if (entity.getItem().is(ModItems.PEGBOARD.get())) {
                boards += entity.getItem().getCount();
            } else if (MagazineItem.typeOf(entity.getItem()) != null) {
                magazines += entity.getItem().getCount();
            }
        }
        check(boards == 2, "Een hoek gaf " + boards + " Pegboards terug");
        check(magazines == 2, "Er vielen " + magazines + " magazijnen");
        helper.succeed();
    }

    /**
     * Alleen van dichtbij (hoofdstuk 71, jouw keuze: maximaal 2 blokken ervan; in hoofdstuk 70 was het 1): van 3
     * blokken ver pak je niets en hang je niets op (het magazijn blijft in je hand); van 2 blokken ver en in het blok
     * ervoor wel.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "armory")
    public static void pegboard_only_from_close_by(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos[] one = boards(helper, 2, 2);
        double cu = Pegboard.column(one[0], Direction.SOUTH) * 8 + 4;
        double cv = one[0].getY() * 8 + 4;
        ItemStack glock = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
        check(PegboardBlock.hangAt(null, level, one[0], Direction.SOUTH, cu, cv, glock.copy(), Pegboard.Turn.LEFT), "De Glock hangt niet");
        PegboardBlockEntity board = board(helper, one[0]);

        ServerPlayer far = player(helper, "armory-far", new BlockPos(2, 2, 7), ItemStack.EMPTY);
        clickOn(far, board, 0, false);
        check(board.hung().size() == 1 && far.getMainHandItem().isEmpty(), "Van 3 blokken ver pakte je de Glock");
        // Ophangen van ver: het magazijn blijft in je hand.
        far.setItemInHand(InteractionHand.MAIN_HAND, MagazineItem.of(FirearmType.GLOCK_19, 15));
        Vec3 onPanel = new Vec3(one[0].getX() + 0.2, one[0].getY() + 0.5, one[0].getZ() + 1.0 / 16);
        far.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, onPanel);
        BlockState state = board.getBlockState();
        state.useItemOn(far.getMainHandItem(), level, far, InteractionHand.MAIN_HAND,
                new BlockHitResult(onPanel, Direction.SOUTH, one[0], false));
        check(board.hung().size() == 1 && !far.getMainHandItem().isEmpty(), "Van 3 blokken ver hing je een magazijn op");

        // Van 2 blokken ver (hoofdstuk 71): ophangen en pakken lukt.
        ServerPlayer two = player(helper, "armory-two", new BlockPos(2, 2, 6), MagazineItem.of(FirearmType.GLOCK_19, 15));
        two.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, onPanel);
        state.useItemOn(two.getMainHandItem(), level, two, InteractionHand.MAIN_HAND,
                new BlockHitResult(onPanel, Direction.SOUTH, one[0], false));
        check(board.hung().size() == 2 && two.getMainHandItem().isEmpty(), "Van 2 blokken ver hing je geen magazijn op");
        two.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        clickOn(two, board, 0, false);
        check(board.hung().size() == 1 && FirearmItem.typeOf(two.getMainHandItem()) != null, "Van 2 blokken ver pakte je de Glock niet");

        ServerPlayer near = player(helper, "armory-near", new BlockPos(2, 2, 5), ItemStack.EMPTY);
        clickOn(near, board, 0, false);
        check(board.hung().isEmpty() && MagazineItem.typeOf(near.getMainHandItem()) != null, "Vlak ervoor pakte je het magazijn niet");
        helper.succeed();
    }
}
