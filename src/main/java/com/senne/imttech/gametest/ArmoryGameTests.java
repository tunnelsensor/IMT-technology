package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.armory.AmmoBoxBlockEntity;
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

        // Iemand (iedereen mag) kijkt naar de Glock en draait hem met sneak + rechtsklik, en pakt hem dan.
        ServerPlayer stranger = player(helper, "armory-stranger", new BlockPos(2, 2, 6), ItemStack.EMPTY);
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
}
