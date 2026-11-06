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
import com.senne.imttech.weapons.GrenadeItem;
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
}
