package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Firearms;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.GrenadeItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import com.senne.imttech.weapons.LaserGuns;
import com.senne.imttech.weapons.LaserPart;
import com.senne.imttech.weapons.LaserParts;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de holster, de pouches en het wiel (hoofdstuk 52, stap 2). Jouw keuzes: trekken laat vallen wat je
 * vasthield; de granaatpouch voert de volgende aan; R met een magazijnpouch laat je een magazijn kiezen; zonder Curios
 * draag je alles in de eigen vakjes naast je harnas.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class GearGameTests {
    private GearGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 4), name);
        player.getAbilities().instabuild = false;
        player.getInventory().selected = 0;
        return player;
    }

    private static ItemStack gear(GearItem.Kind kind, ItemStack... inside) {
        ItemStack stack = new ItemStack(switch (kind) {
            case HOLSTER -> ModItems.HOLSTER.get();
            case GRENADE_POUCH -> ModItems.GRENADE_POUCH.get();
            case MAG_POUCH -> ModItems.MAG_POUCH.get();
        });
        GearItem.setContents(stack, List.of(inside));
        return stack;
    }

    /**
     * Trekken uit de holster: het pistool komt in je hand, wat je vasthield valt op de grond, en trekken duurt even
     * (schieten kan nog niet). Wegsteken zet het terug. De granaatpouch: trekken, gooien, en de volgende komt vanzelf.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guns")
    public static void holster_quickdraw_drops_held_item_and_grenade_pouch_feeds(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer player = player(helper, "gear-drawer");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 3));
        ItemStack gun = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
        check(Gear.set(player, GearItem.Kind.HOLSTER, gear(GearItem.Kind.HOLSTER, gun)), "De holster ging niet om");
        check(Gear.set(player, GearItem.Kind.GRENADE_POUCH, gear(GearItem.Kind.GRENADE_POUCH,
                new ItemStack(ModItems.EMP_GRENADE.get()), new ItemStack(ModItems.EMP_GRENADE.get()))), "De pouch ging niet om");
        check(Gear.drawGun(player), "Trekken lukte niet");
        check(FirearmItem.typeOf(player.getMainHandItem()) == FirearmType.GLOCK_19, "Het pistool kwam niet in je hand");
        check(Gear.contents(player, GearItem.Kind.HOLSTER).getFirst().isEmpty(), "Het pistool zit nog in de holster");
        check(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(3),
                item -> item.getItem().is(Items.STONE)).isEmpty(), "De steen viel niet op de grond");
        check(Firearms.isBusy(player) && Firearms.currentAction(player) == Firearms.ActionKind.DRAW, "Trekken duurt niet even");
        check(Firearms.fire(player, player.getYRot(), player.getXRot(), false) == Firearms.Result.BUSY, "Tijdens het trekken kon je schieten");
        Firearms.finishNow(player);
        check(!Firearms.isBusy(player), "Het trekken werd niet klaar");
        check(Gear.holsterHand(player), "Wegsteken lukte niet");
        check(player.getMainHandItem().isEmpty(), "Het pistool bleef in je hand");
        check(FirearmItem.typeOf(Gear.contents(player, GearItem.Kind.HOLSTER).getFirst()) == FirearmType.GLOCK_19,
                "Het pistool kwam niet terug in de holster");
        check(!Gear.drawGrenade(player, GrenadeItem.Kind.FLASH.ordinal()), "Een flashbang kwam uit een pouch zonder flashbangs");
        check(Gear.drawGrenade(player, GrenadeItem.Kind.EMP.ordinal()), "De EMP-granaat kwam niet uit de pouch");
        check(player.getMainHandItem().is(ModItems.EMP_GRENADE.get()) && player.getMainHandItem().getCount() == 1, "Er hoort 1 EMP-granaat in je hand te zitten");
        // Gooien: de laatste van die stapel; de volgende komt uit de pouch.
        ModItems.EMP_GRENADE.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        check(player.getMainHandItem().is(ModItems.EMP_GRENADE.get()), "De volgende granaat kwam niet uit de pouch");
        check(GearItem.filled(Gear.get(player, GearItem.Kind.GRENADE_POUCH)) == 0, "De pouch hoort nu leeg te zijn");
        check(Gear.roomFor(player, new ItemStack(Items.STONE)) == null, "Een steen past in een holster of pouch");
        check(Gear.roomFor(player, new ItemStack(ModItems.FLASHBANG.get())) == GearItem.Kind.GRENADE_POUCH, "Een flashbang past niet in de lege pouch");
        helper.succeed();
    }

    /**
     * De magazijnpouch (jouw wens): R laat je een magazijn kiezen; het gekozen magazijn gaat in het pistool en het oude
     * komt op zijn plek in de pouch. En een magazijn dat je eruit gooit (zonder te kiezen) gaat in de pouch als daar
     * plek is, anders in je inventaris.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guns")
    public static void mag_pouch_reload_swaps_the_chosen_magazine(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ImtConfig.Guns.JAM_CHANCE = 0;
        ImtConfig.Guns.MISFIRE_CHANCE = 0;
        ServerPlayer player = player(helper, "gear-reloader");
        ItemStack gun = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
        FirearmItem.setMagazine(gun, MagazineItem.of(FirearmType.GLOCK_19, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        check(Gear.set(player, GearItem.Kind.MAG_POUCH, gear(GearItem.Kind.MAG_POUCH,
                MagazineItem.of(FirearmType.GLOCK_19, 15), ItemStack.EMPTY, MagazineItem.of(FirearmType.GLOCK_19, 7))), "De pouch ging niet om");
        check(!Gear.reloadFromPouch(player, 1), "Een leeg vakje gaf een magazijn");
        check(Gear.reloadFromPouch(player, 2), "Herladen uit vakje 3 begon niet");
        check(Firearms.currentAction(player) == Firearms.ActionKind.RELOAD_POUCH, "De handeling is niet RELOAD_POUCH");
        Firearms.finishNow(player);
        ItemStack held = player.getMainHandItem();
        check(FirearmItem.roundsInMagazine(held) == 7 && FirearmItem.chambered(held), "Het pistool hoort 7 + 1 te hebben");
        List<ItemStack> pouch = Gear.contents(player, GearItem.Kind.MAG_POUCH);
        check(MagazineItem.rounds(pouch.get(2)) == 2, "Het oude magazijn (2) kwam niet in vakje 3");
        check(MagazineItem.rounds(pouch.get(0)) == 15 && pouch.get(1).isEmpty(), "De andere vakjes veranderden");
        // R zonder kiezen (uitwerpen): het magazijn gaat in het lege vakje van de pouch.
        check(Firearms.request(player, Firearms.ActionKind.EJECT_MAG, 0), "Uitwerpen begon niet");
        Firearms.finishNow(player);
        pouch = Gear.contents(player, GearItem.Kind.MAG_POUCH);
        check(!FirearmItem.hasMagazine(player.getMainHandItem()), "Het magazijn zit er nog in");
        check(MagazineItem.rounds(pouch.get(1)) == 7, "Het uitgeworpen magazijn kwam niet in het lege vakje");
        check(player.getInventory().countItem(ModItems.GLOCK_19_MAGAZINE.get()) == 0, "Het magazijn kwam in je inventaris in plaats van de pouch");
        ImtConfig.Guns.JAM_CHANCE = 0.4f;
        ImtConfig.Guns.MISFIRE_CHANCE = 0.15f;
        helper.succeed();
    }

    /** De eigen vakjes naast je harnas: wat je aan je muis hebt gaat erin, wat erin zat komt aan je muis. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "guns")
    public static void gear_slots_take_and_give_back(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer player = player(helper, "gear-wearer");
        player.containerMenu = player.inventoryMenu;
        ItemStack holster = new ItemStack(ModItems.HOLSTER.get());
        player.inventoryMenu.setCarried(holster.copy());
        Gear.click(player, GearItem.Kind.HOLSTER.ordinal());
        check(player.inventoryMenu.getCarried().isEmpty(), "De holster bleef aan je muis");
        check(Gear.get(player, GearItem.Kind.HOLSTER).is(ModItems.HOLSTER.get()), "De holster zit niet om");
        // Een pouch past niet in het holstervakje.
        player.inventoryMenu.setCarried(new ItemStack(ModItems.MAG_POUCH.get()));
        Gear.click(player, GearItem.Kind.HOLSTER.ordinal());
        check(player.inventoryMenu.getCarried().is(ModItems.MAG_POUCH.get()), "Een pouch ging in het holstervakje");
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        Gear.click(player, GearItem.Kind.HOLSTER.ordinal());
        check(player.inventoryMenu.getCarried().is(ModItems.HOLSTER.get()), "De holster kwam niet terug aan je muis");
        check(Gear.get(player, GearItem.Kind.HOLSTER).isEmpty(), "De holster zit nog om");
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        helper.succeed();
    }

    /**
     * Het scherm van een pouch (hoofdstuk 54, jouw notitie: volle magazijnen erin, lege eruit): een gedragen pouch openen
     * geeft een menu met zijn vakjes; shift-klik zet een magazijn uit je inventaris erin en de pouch om je heup weet het
     * meteen. Een diamant past er niet in. Een pouch in je hand gaat ook open; dan zit dat vakje van je werkbalk op slot.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "guns")
    public static void pouch_screen_moves_magazines_in_and_out(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer player = player(helper, "pouch-filler");
        check(Gear.set(player, GearItem.Kind.MAG_POUCH, gear(GearItem.Kind.MAG_POUCH, MagazineItem.of(FirearmType.GLOCK_19, 15))),
                "De pouch ging niet om");
        player.getInventory().setItem(1, MagazineItem.of(FirearmType.GLOCK_19, 7));
        player.getInventory().setItem(2, new ItemStack(Items.DIAMOND));
        check(!Gear.openPouch(player, GearItem.Kind.GRENADE_POUCH.ordinal()), "Een pouch die je niet draagt ging open");
        // Het menu zelf (het openen stuurt een pakketje, en een nep-speler heeft geen verbinding).
        com.senne.imttech.guns.GearPouchMenu menu = new com.senne.imttech.guns.GearPouchMenu(7, player.getInventory(),
                GearItem.Kind.MAG_POUCH, com.senne.imttech.guns.GearPouchMenu.WORN);
        player.containerMenu = menu;
        check(menu.stillValid(player), "Het pouchscherm van een gedragen pouch is niet geldig");
        int pouchSlots = GearItem.Kind.MAG_POUCH.size();
        check(!menu.getSlot(1).mayPlace(new ItemStack(Items.DIAMOND)), "Een diamant past in de pouch");
        // Shift-klik op het magazijn van 7 in werkbalkvakje 1: het gaat in het eerste lege vakje van de pouch.
        menu.quickMoveStack(player, pouchSlots + 27 + 1);
        List<ItemStack> contents = Gear.contents(player, GearItem.Kind.MAG_POUCH);
        check(MagazineItem.rounds(contents.get(1)) == 7, "Het magazijn van 7 kwam niet in vakje 2 van de pouch");
        check(player.getInventory().getItem(1).isEmpty(), "Het magazijn zit nog in je werkbalk");
        // Shift-klik op het volle magazijn in de pouch: terug in je inventaris.
        menu.quickMoveStack(player, 0);
        contents = Gear.contents(player, GearItem.Kind.MAG_POUCH);
        check(contents.get(0).isEmpty(), "Het magazijn van 15 zit nog in de pouch");
        check(player.getInventory().countItem(ModItems.GLOCK_19_MAGAZINE.get()) == 1, "Het magazijn van 15 kwam niet in je inventaris");
        player.closeContainer();
        // Een pouch in je hand: open, en het vakje van je hand op slot.
        player.setItemInHand(InteractionHand.MAIN_HAND, gear(GearItem.Kind.GRENADE_POUCH));
        player.getInventory().setItem(3, new ItemStack(ModItems.FRAG_GRENADE.get(), 2));
        com.senne.imttech.guns.GearPouchMenu held = new com.senne.imttech.guns.GearPouchMenu(8, player.getInventory(),
                GearItem.Kind.GRENADE_POUCH, com.senne.imttech.guns.GearPouchMenu.MAIN_HAND);
        player.containerMenu = held;
        check(held.stillValid(player), "Het pouchscherm van een pouch in je hand is niet geldig");
        int grenadeSlots = GearItem.Kind.GRENADE_POUCH.size();
        check(!held.getSlot(grenadeSlots + 27).mayPickup(player), "Het vakje met de open pouch zit niet op slot");
        held.quickMoveStack(player, grenadeSlots + 27 + 3);
        check(GearItem.contents(player.getMainHandItem()).get(0).getCount() == 2, "De granaten kwamen niet in de pouch in je hand");
        player.closeContainer();
        helper.succeed();
    }

    /**
     * Hoofdstuk 55: het laserpistool past in de holster; een geweer met sling past in een sling-vakje (zonder sling niet,
     * en een pistool nooit); trekken wisselt (jouw keuze: wat je vasthield gaat in zijn eigen plek in plaats van op de
     * grond, "heb je de glock vast en pak je je laserrifle, dan holster je de glock"); van je sling trekken duurt langer
     * dan uit de holster; en "Put away" hangt een geweer met sling op je rug.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guns")
    public static void laser_pistol_holsters_and_slung_guns_swap_on_quickdraw(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer player = player(helper, "sling-drawer");
        ItemStack pistol = LaserGunItem.loaded(GunType.PISTOL);
        check(GearItem.Kind.HOLSTER.accepts(pistol), "Het laserpistool past niet in de holster");
        check(Gear.set(player, GearItem.Kind.HOLSTER, gear(GearItem.Kind.HOLSTER, pistol)), "De holster ging niet om");
        ItemStack rifle = LaserGunItem.loaded(GunType.RIFLE);
        check(!Gear.slingAccepts(rifle), "Een rifle zonder sling past in een sling-vakje");
        LaserGunItem.setParts(rifle, LaserGunItem.parts(rifle).with(LaserPart.SLING, LaserParts.extra()));
        check(Gear.slingAccepts(rifle), "Een rifle met sling past niet in een sling-vakje");
        check(!Gear.slingAccepts(pistol), "Een pistool past in een sling-vakje");
        check(Gear.setSling(player, 0, rifle), "De rifle ging niet op je rug");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 3));
        // Van je rug trekken: de steen valt op de grond, en het duurt langer dan uit de holster.
        check(Gear.drawSling(player, 0), "Van je sling trekken lukte niet");
        check(LaserGunItem.typeOf(player.getMainHandItem()) == GunType.RIFLE, "De rifle kwam niet in je hand");
        check(Gear.sling(player, 0).isEmpty(), "De rifle hangt nog op je rug");
        check(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(3),
                item -> item.getItem().is(Items.STONE)).isEmpty(), "De steen viel niet op de grond");
        check(Firearms.currentAction(player) == Firearms.ActionKind.DRAW, "Trekken duurt niet even");
        int fromSling = Firearms.ticksFor(player, Firearms.ActionKind.DRAW, Firearms.DRAW_FROM_SLING, rifle);
        int fromHolster = Firearms.ticksFor(player, Firearms.ActionKind.DRAW, Firearms.DRAW_FROM_HOLSTER, pistol);
        check(fromSling > fromHolster, "Van je sling trekken duurt niet langer dan uit de holster: " + fromSling + " / " + fromHolster);
        check(LaserGuns.fire(player, player.getYRot(), player.getXRot(), false) == LaserGuns.Result.BUSY, "Tijdens het trekken kon je schieten");
        Firearms.finishNow(player);
        // Het pistool trekken: de rifle gaat terug op je rug (niet op de grond).
        check(Gear.drawGun(player), "Het pistool trekken lukte niet");
        check(LaserGunItem.typeOf(player.getMainHandItem()) == GunType.PISTOL, "Het pistool kwam niet in je hand");
        check(LaserGunItem.typeOf(Gear.sling(player, 0)) == GunType.RIFLE, "De rifle ging niet terug op je rug");
        Firearms.finishNow(player);
        // En andersom: de rifle trekken, het pistool gaat in de holster.
        check(Gear.drawSling(player, 0), "De rifle trekken lukte niet (2)");
        check(LaserGunItem.typeOf(Gear.contents(player, GearItem.Kind.HOLSTER).getFirst()) == GunType.PISTOL,
                "Het pistool ging niet terug in de holster");
        Firearms.finishNow(player);
        // Wegsteken: de rifle hangt weer op je rug.
        check(Gear.holsterHand(player), "Wegsteken lukte niet");
        check(player.getMainHandItem().isEmpty() && LaserGunItem.typeOf(Gear.sling(player, 0)) == GunType.RIFLE,
                "De rifle ging niet in het sling-vakje");
        // De eigen vakjes in je inventaris: een sniper zonder sling past niet, met sling wel (voor je borst).
        player.containerMenu = player.inventoryMenu;
        player.inventoryMenu.setCarried(LaserGunItem.loaded(GunType.SNIPER));
        Gear.click(player, GearItem.Kind.values().length + 1);
        check(LaserGunItem.typeOf(player.inventoryMenu.getCarried()) == GunType.SNIPER, "Een sniper zonder sling ging in een sling-vakje");
        ItemStack sniper = player.inventoryMenu.getCarried();
        LaserGunItem.setParts(sniper, LaserGunItem.parts(sniper).with(LaserPart.SLING, LaserParts.extra()));
        Gear.click(player, GearItem.Kind.values().length + 1);
        check(player.inventoryMenu.getCarried().isEmpty() && LaserGunItem.typeOf(Gear.sling(player, 1)) == GunType.SNIPER,
                "De sniper met sling ging niet voor je borst");
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        helper.succeed();
    }

    /**
     * Hoofdstuk 55: energiecellen passen in de magazijnpouch, en R met een lasergeweer kiest er een (zoals de magazijnen
     * van de Glock); de oude cel komt op de plek van de nieuwe. Een leeg vakje of een legere cel: niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guns")
    public static void energy_cells_fit_the_magazine_pouch_and_reload_from_it(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer player = player(helper, "cell-reloader");
        ItemStack gun = LaserGunItem.complete(GunType.RIFLE, GunMaterial.TITANIUM);
        LaserGunItem.setCell(gun, 20);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        check(GearItem.Kind.MAG_POUCH.accepts(EnergyCellItem.withCharge(50)), "Een cel past niet in de magazijnpouch");
        check(Gear.set(player, GearItem.Kind.MAG_POUCH, gear(GearItem.Kind.MAG_POUCH,
                EnergyCellItem.withCharge(60), ItemStack.EMPTY, EnergyCellItem.withCharge(90))), "De pouch ging niet om");
        check(!LaserGuns.startReload(player, 1), "Een leeg vakje gaf een cel");
        check(LaserGuns.startReload(player, 2), "Herladen uit vakje 3 begon niet");
        check(LaserGuns.isReloading(player), "Het herladen loopt niet");
        LaserGuns.finishReloadNow(player);
        check(Math.abs(LaserGunItem.cell(player.getMainHandItem()) - 90) < 1e-3,
                "Het geweer kreeg niet de cel van 90: " + LaserGunItem.cell(player.getMainHandItem()));
        List<ItemStack> pouch = Gear.contents(player, GearItem.Kind.MAG_POUCH);
        check(Math.abs(EnergyCellItem.charge(pouch.get(2)) - 20) < 1e-3, "De oude cel (20) kwam niet in vakje 3");
        check(Math.abs(EnergyCellItem.charge(pouch.get(0)) - 60) < 1e-3 && pouch.get(1).isEmpty(), "De andere vakjes veranderden");
        check(!LaserGuns.startReload(player, 0), "Een legere cel (60) ging in een geweer met 90");
        helper.succeed();
    }
}
