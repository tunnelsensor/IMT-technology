package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.CasingEntity;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Firearms;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunPartItem;
import com.senne.imttech.guns.GunParts;
import com.senne.imttech.guns.GunsmithTableBlockEntity;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.guns.ReloadingBenchBlockEntity;
import com.senne.imttech.guns.ReloadingBenchMenu;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de vuurwapens (hoofdstuk 52, 6 november). Jouw wens: een Glock 19 uit zes onderdelen (elk kan slijten en
 * kapot gaan, beter materiaal gaat langer mee), magazijnen die je kogel per kogel vult, een patroon in de kamer (15 +
 * 1), hulzen die eruit springen en die je opraapt, storingen met een tap-rack, de Gunsmith Table om te slepen en de
 * Reloading Bench om patronen te persen.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class GunGameTests {
    private GunGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Alles voorspelbaar: geen storingen, geen slijtage, geen spreiding (de tests zetten zelf aan wat ze testen). */
    private static void deterministic() {
        ImtConfig.Guns.JAM_CHANCE = 0;
        ImtConfig.Guns.MISFIRE_CHANCE = 0;
        ImtConfig.Guns.WEAR_CHANCE = new float[]{0, 0, 0};
        ImtConfig.Guns.SPREAD = new float[]{0};
        ImtConfig.Guns.SPREAD_AIMING = new float[]{0};
    }

    private static void restore() {
        ImtConfig.Guns.JAM_CHANCE = 0.4f;
        ImtConfig.Guns.MISFIRE_CHANCE = 0.15f;
        ImtConfig.Guns.WEAR_CHANCE = new float[]{4.0f, 2.0f, 0.8f};
        ImtConfig.Guns.SPREAD = new float[]{3.5f};
        ImtConfig.Guns.SPREAD_AIMING = new float[]{0.8f};
        ImtConfig.Guns.BULLETS_BREAK_GLASS = true;
    }

    /** Een speler (survival) met dit item in zijn hand (vakje 0), op deze plek, die naar {@code look} kijkt. */
    private static ServerPlayer holder(GameTestHelper helper, String name, BlockPos at, ItemStack held, Vec3 look) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, at, name);
        player.getAbilities().instabuild = false;
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, look);
        Firearms.forgetCooldown(player);
        return player;
    }

    private static Firearms.Result fire(ServerPlayer player) {
        Firearms.forgetCooldown(player);
        return Firearms.fire(player, player.getYRot(), player.getXRot(), false);
    }

    private static List<CasingEntity> casings(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(CasingEntity.class, helper.getBounds().inflate(4));
    }

    // ================================================================ Schieten

    /**
     * Een schot: de kogel vliegt echt en raakt een husk 8 blokken verder (jouw keuze: een vliegtijd, geen instant hit);
     * de volgende patroon gaat vanzelf in de kamer (14 + 1), de huls springt eruit en ligt op de grond, en met een
     * rechtsklik op die plek raap je hem op als item.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "guns")
    public static void glock_fires_chambers_next_round_and_ejects_a_casing(GameTestHelper helper) {
        deterministic();
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 12));
        husk.setNoAi(true);
        float before = husk.getHealth();
        ServerPlayer player = holder(helper, "gun-shooter", new BlockPos(4, 2, 4),
                FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL), helper.absoluteVec(new Vec3(4.5, 3.2, 12.5)));
        check(fire(player) == Firearms.Result.FIRED, "Het pistool schoot niet");
        ItemStack gun = player.getMainHandItem();
        check(FirearmItem.roundsInMagazine(gun) == 14 && FirearmItem.chambered(gun),
                "Na een schot hoort er 14 + 1 in te zitten, niet " + FirearmItem.roundsInMagazine(gun) + " + " + FirearmItem.chambered(gun));
        check(casings(helper).size() == 1, "Er sprong geen huls uit het pistool");
        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> check(husk.getHealth() < before, "De kogel raakte de husk niet (" + husk.getHealth() + ")"))
                .thenIdle(30)
                .thenExecute(() -> {
                    CasingEntity casing = casings(helper).getFirst();
                    check(casing.onGround(), "De huls ligt niet op de grond");
                    int picked = Firearms.pickUpCasings(player, casing.position());
                    check(picked == 1, "Oprapen gaf " + picked + " hulzen");
                    check(player.getInventory().countItem(ModItems.CASING_9MM.get()) == 1, "De huls kwam niet in je inventaris");
                    check(casings(helper).isEmpty(), "De huls ligt er nog");
                    restore();
                })
                .thenSucceed();
    }

    /**
     * Jouw voorbeeld: een magazijn van 5, herladen: het magazijn heeft er 4 en de kamer 1. Een nieuw magazijn van 6 erin:
     * 7 in totaal ("1 extra kogel"). Zonder pouch gaat dat zo: R gooit het magazijn eruit (het komt in je inventaris),
     * rechtsklik met het andere magazijn duwt het in het pistool. Na 7 schoten is hij leeg en staat de slede open.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "guns")
    public static void glock_chamber_and_magazine_math(GameTestHelper helper) {
        deterministic();
        ItemStack gun = FirearmItem.withParts(FirearmType.GLOCK_19, GunParts.complete(GunMaterial.STEEL));
        FirearmItem.setMagazine(gun, MagazineItem.of(FirearmType.GLOCK_19, 5));
        ServerPlayer player = holder(helper, "gun-counter", new BlockPos(4, 2, 4), gun, helper.absoluteVec(new Vec3(4.5, 3, 14.5)));
        check(!FirearmItem.chambered(player.getMainHandItem()), "Een pistool met een nieuw magazijn heeft nog niets in de kamer");
        helper.startSequence()
                // De server laat de slede vanzelf los: de eerste patroon gaat in de kamer.
                .thenWaitUntil(() -> check(FirearmItem.chambered(player.getMainHandItem()), "De slede ging niet vanzelf los"))
                .thenExecute(() -> {
                    ItemStack held = player.getMainHandItem();
                    check(FirearmItem.roundsInMagazine(held) == 4, "Het magazijn hoort er 4 te hebben, niet " + FirearmItem.roundsInMagazine(held));
                    check(FirearmItem.totalRounds(held) == 5, "Samen hoort het 5 te zijn");
                    // Zonder pouch: R = het magazijn eruit.
                    player.getInventory().setItem(1, MagazineItem.of(FirearmType.GLOCK_19, 6));
                    check(Firearms.request(player, Firearms.ActionKind.EJECT_MAG, 0), "Het magazijn ging er niet uit");
                    Firearms.finishNow(player);
                    check(!FirearmItem.hasMagazine(player.getMainHandItem()), "Het magazijn zit er nog in");
                    check(FirearmItem.chambered(player.getMainHandItem()), "De patroon in de kamer verdween");
                    int fours = 0;
                    for (ItemStack stack : player.getInventory().items) {
                        if (MagazineItem.typeOf(stack) != null && MagazineItem.rounds(stack) == 4) {
                            fours++;
                        }
                    }
                    check(fours == 1, "Het magazijn met 4 kwam niet in je inventaris");
                    // Scrollen naar het magazijn van 6 en rechtsklikken.
                    player.getInventory().selected = 1;
                    check(Firearms.insertMagazine(player, InteractionHand.MAIN_HAND), "Het magazijn ging er niet in");
                    Firearms.finishNow(player);
                    ItemStack back = player.getInventory().getItem(0);
                    check(FirearmItem.roundsInMagazine(back) == 6 && FirearmItem.chambered(back), "Het pistool hoort 6 + 1 te hebben");
                    check(player.getInventory().getItem(1).isEmpty(), "Het magazijn bleef ook in je hand");
                    check(FirearmItem.totalRounds(back) == 7, "Samen hoort het 7 te zijn (1 extra kogel)");
                    // Terug naar het pistool en 7 keer schieten.
                    player.getInventory().selected = 0;
                    player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(4.5, 3, 14.5)));
                    for (int shot = 1; shot <= 7; shot++) {
                        check(fire(player) == Firearms.Result.FIRED, "Schot " + shot + " ging niet af");
                    }
                    check(fire(player) == Firearms.Result.EMPTY, "Een leeg pistool schoot toch");
                    check(FirearmItem.slideLocked(player.getMainHandItem()), "De slede staat niet open na het laatste schot");
                    check(casings(helper).size() == 7, "Er horen 7 hulzen te liggen, niet " + casings(helper).size());
                    restore();
                })
                .thenSucceed();
    }

    /**
     * Storingen (jouw wens: ook een tap-rack via R): met de kans op 100% blijft de huls klem zitten na een schot; dan
     * schiet het pistool niet meer tot de tap-rack klaar is (de huls valt er dan alsnog uit, de volgende patroon gaat
     * erin). Een ketser: de trekker klikt, de slechte patroon gaat verloren bij de tap-rack.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guns")
    public static void glock_jams_and_tap_rack_clears(GameTestHelper helper) {
        deterministic();
        ServerPlayer player = holder(helper, "gun-jammer", new BlockPos(4, 2, 4),
                FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL), helper.absoluteVec(new Vec3(4.5, 3, 14.5)));
        ImtConfig.Guns.JAM_CHANCE = 100;
        check(fire(player) == Firearms.Result.FIRED, "Het eerste schot ging niet af");
        ItemStack gun = player.getMainHandItem();
        check(FirearmItem.jam(gun) == FirearmItem.JAM_STOVEPIPE, "De huls bleef niet klem zitten");
        check(casings(helper).isEmpty(), "De klemzittende huls sprong er toch uit");
        check(!FirearmItem.chambered(gun), "Bij een storing hoort de kamer leeg te zijn");
        check(fire(player) == Firearms.Result.JAMMED, "Met een storing schoot het pistool toch");
        check(Firearms.request(player, Firearms.ActionKind.TAP_RACK, 0), "De tap-rack begon niet");
        check(fire(player) == Firearms.Result.BUSY, "Tijdens de tap-rack kon je schieten");
        Firearms.finishNow(player);
        check(FirearmItem.jam(gun) == FirearmItem.JAM_NONE, "De tap-rack verhielp de storing niet");
        check(casings(helper).size() == 1, "De klemzittende huls viel er niet uit bij de tap-rack");
        check(FirearmItem.chambered(gun) && FirearmItem.roundsInMagazine(gun) == 14, "Na de tap-rack hoort het 14 + 1 te zijn");
        // Een ketser.
        ImtConfig.Guns.JAM_CHANCE = 0;
        ImtConfig.Guns.MISFIRE_CHANCE = 100;
        check(fire(player) == Firearms.Result.MISFIRE, "Geen ketser");
        check(FirearmItem.jam(gun) == FirearmItem.JAM_MISFIRE && FirearmItem.chambered(gun), "De slechte patroon hoort in de kamer te blijven");
        check(Firearms.request(player, Firearms.ActionKind.TAP_RACK, 0), "De tweede tap-rack begon niet");
        Firearms.finishNow(player);
        check(FirearmItem.jam(gun) == FirearmItem.JAM_NONE && FirearmItem.chambered(gun) && FirearmItem.roundsInMagazine(gun) == 13,
                "Na de ketser hoort het 13 + 1 te zijn (de slechte patroon is weg)");
        restore();
        helper.succeed();
    }

    /**
     * Slijtage (jouw wens: hoe beter het materiaal, hoe kleiner de kans om kapot te gaan): met de kans op 100% slijt elk
     * onderdeel elk schot een punt; een loop op 2 is na twee schoten kapot en dan doet het pistool niets meer. Een pistool
     * zonder alle onderdelen schiet niet. En per materiaal is de kans echt kleiner.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guns")
    public static void glock_parts_wear_and_break(GameTestHelper helper) {
        deterministic();
        ItemStack gun = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
        FirearmItem.setParts(gun, FirearmItem.parts(gun).with(GunPart.BARREL, new PartState(GunMaterial.STEEL, 2)));
        ServerPlayer player = holder(helper, "gun-wearer", new BlockPos(4, 2, 4), gun, helper.absoluteVec(new Vec3(4.5, 3, 14.5)));
        ImtConfig.Guns.WEAR_CHANCE = new float[]{100, 100, 100};
        check(fire(player) == Firearms.Result.FIRED, "Schot 1 ging niet af");
        ItemStack held = player.getMainHandItem();
        check(FirearmItem.parts(held).get(GunPart.BARREL).condition() == 1, "De loop sleet niet een punt");
        check(FirearmItem.parts(held).get(GunPart.SLIDE).condition() == 99, "De slede sleet niet een punt");
        check(fire(player) == Firearms.Result.FIRED, "Schot 2 ging niet af");
        check(FirearmItem.parts(held).get(GunPart.BARREL).broken(), "De loop ging niet kapot");
        check(fire(player) == Firearms.Result.BROKEN, "Met een kapotte loop schoot het pistool toch");
        check(!FirearmItem.works(held), "Een pistool met een kapot onderdeel telt als werkend");
        // Incompleet.
        ItemStack bare = FirearmItem.withParts(FirearmType.GLOCK_19, GunParts.EMPTY.with(GunPart.FRAME, PartState.fresh(GunMaterial.POLYMER)));
        FirearmItem.setChambered(bare, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, bare);
        check(fire(player) == Firearms.Result.INCOMPLETE, "Een pistool van alleen een frame schoot");
        // De materialen (met de gewone getallen uit de config).
        restore();
        check(GunMaterial.POLYMER.wearChance() > GunMaterial.STEEL.wearChance()
                && GunMaterial.STEEL.wearChance() > GunMaterial.TITANIUM.wearChance(), "Beter materiaal slijt niet minder");
        check(!GunPart.SLIDE.accepts(GunMaterial.POLYMER) && GunPart.FRAME.accepts(GunMaterial.POLYMER), "Polymeer hoort alleen bij frame en trekker");
        helper.succeed();
    }

    /**
     * Een magazijn vul je kogel per kogel met R (jouw wens), en zonder magazijn kun je met de hand een patroon in de
     * kamer steken (sneak + R) en daarmee schieten.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guns")
    public static void magazine_loads_round_by_round_and_chamber_by_hand(GameTestHelper helper) {
        deterministic();
        ServerPlayer player = holder(helper, "gun-loader", new BlockPos(4, 2, 4), MagazineItem.of(FirearmType.GLOCK_19, 0),
                helper.absoluteVec(new Vec3(4.5, 3, 14.5)));
        player.getInventory().setItem(1, new ItemStack(ModItems.ROUND_9MM.get(), 10));
        check(new ItemStack(ModItems.GLOCK_19_MAGAZINE.get()).getMaxStackSize() == 1, "Magazijnen stapelen");
        for (int round = 1; round <= 3; round++) {
            check(Firearms.request(player, Firearms.ActionKind.LOAD_ROUND, 0), "Patroon " + round + " ging er niet in");
            Firearms.finishNow(player);
        }
        check(MagazineItem.rounds(player.getMainHandItem()) == 3, "Het magazijn hoort er 3 te hebben");
        check(player.getInventory().getItem(1).getCount() == 7, "Er horen 7 losse patronen over te zijn");
        // Met de hand in de kamer, zonder magazijn.
        ItemStack gun = FirearmItem.withParts(FirearmType.GLOCK_19, GunParts.complete(GunMaterial.TITANIUM));
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        check(!Firearms.request(player, Firearms.ActionKind.EJECT_MAG, 0), "Zonder magazijn ging er toch een uit");
        check(Firearms.request(player, Firearms.ActionKind.CHAMBER_ROUND, 0), "De patroon ging niet met de hand in de kamer");
        Firearms.finishNow(player);
        ItemStack held = player.getMainHandItem();
        check(FirearmItem.chambered(held) && !FirearmItem.hasMagazine(held), "Er hoort 1 in de kamer te zitten, zonder magazijn");
        check(player.getInventory().getItem(1).getCount() == 6, "De patroon in de kamer kwam niet uit je inventaris");
        check(fire(player) == Firearms.Result.FIRED, "Met alleen een patroon in de kamer schoot het pistool niet");
        check(!FirearmItem.chambered(held) && !FirearmItem.slideLocked(held), "Zonder magazijn staat de slede niet open");
        check(fire(player) == Firearms.Result.EMPTY, "Een leeg pistool schoot");
        restore();
        helper.succeed();
    }

    // ================================================================ De banken

    /**
     * De Gunsmith Table (jouw wens: slepen, niet in vakjes): eerst het frame, dan de rest; een onderdeel eruit komt met
     * zijn conditie terug; het frame kan er pas als laatste uit; het pistool meenemen en terugleggen.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guns")
    public static void gunsmith_table_assembles_and_takes_apart(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 2, 4);
        helper.setBlock(at, ModBlocks.GUNSMITH_TABLE.get());
        GunsmithTableBlockEntity table = (GunsmithTableBlockEntity) helper.getBlockEntity(at);
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 6), "gunsmith");
        player.getInventory().setItem(0, GunPartItem.of(GunPart.FRAME, GunMaterial.POLYMER));
        player.getInventory().setItem(1, GunPartItem.of(GunPart.SLIDE, GunMaterial.STEEL, 50));
        player.getInventory().setItem(2, new ItemStack(Items.DIAMOND));
        check(!table.install(player, 1), "Een slede ging erin zonder frame");
        check(!table.install(player, 2), "Een diamant ging erin");
        check(table.install(player, 0), "Het frame ging er niet in");
        check(FirearmItem.typeOf(table.gun()) == FirearmType.GLOCK_19, "Er ligt geen pistool op de tafel");
        check(player.getInventory().getItem(0).isEmpty(), "Het frame bleef in je inventaris");
        check(table.install(player, 1), "De slede ging er niet in");
        GunParts parts = FirearmItem.parts(table.gun());
        check(parts.get(GunPart.SLIDE).condition() == 50 && parts.get(GunPart.SLIDE).material() == GunMaterial.STEEL,
                "De slede hield zijn conditie en materiaal niet");
        check(!table.install(player, 1), "Een leeg vakje ging erin");
        check(!table.remove(player, GunPart.FRAME), "Het frame kon eruit terwijl de slede erin zat");
        check(table.remove(player, GunPart.SLIDE), "De slede ging er niet uit");
        ItemStack slide = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (GunPartItem.partOf(stack) == GunPart.SLIDE) {
                slide = stack;
            }
        }
        check(GunPartItem.partOf(slide) == GunPart.SLIDE && GunPartItem.conditionOf(slide) == 50, "De slede kwam niet met conditie 50 terug");
        check(table.takeGun(player), "Het pistool kon niet mee");
        check(table.gun().isEmpty(), "Het pistool ligt er nog");
        int gunSlot = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (FirearmItem.typeOf(player.getInventory().getItem(slot)) != null) {
                gunSlot = slot;
            }
        }
        check(gunSlot >= 0, "Het pistool kwam niet in je inventaris");
        check(table.putGun(player, gunSlot), "Het pistool ging niet terug op de tafel");
        check(table.remove(player, GunPart.FRAME), "Het frame kon er niet uit als laatste");
        check(table.gun().isEmpty(), "Zonder frame hoort er geen pistool meer te liggen");
        check(player.getInventory().countItem(ModItems.gunPart(GunPart.FRAME)) == 1, "Het frame kwam niet terug");
        helper.succeed();
    }

    /** De Reloading Bench (jouw wens): een huls, een kogelpunt en buskruit geven één 9mm-patroon per keer. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guns")
    public static void reloading_bench_presses_rounds(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 2, 4);
        helper.setBlock(at, ModBlocks.RELOADING_BENCH.get());
        ReloadingBenchBlockEntity bench = (ReloadingBenchBlockEntity) helper.getBlockEntity(at);
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 6), "reloader");
        check(!ReloadingBenchBlockEntity.fits(ReloadingBenchBlockEntity.SLOT_CASING, new ItemStack(Items.GUNPOWDER)), "Buskruit past in het hulsvakje");
        bench.items().setItem(ReloadingBenchBlockEntity.SLOT_CASING, new ItemStack(ModItems.CASING_9MM.get(), 3));
        bench.items().setItem(ReloadingBenchBlockEntity.SLOT_BULLET, new ItemStack(ModItems.BULLET_9MM.get(), 2));
        bench.items().setItem(ReloadingBenchBlockEntity.SLOT_POWDER, new ItemStack(Items.GUNPOWDER, 5));
        ReloadingBenchMenu menu = new ReloadingBenchMenu(1, player.getInventory(), bench.items());
        check(menu.getSlot(ReloadingBenchMenu.RESULT_SLOT).getItem().is(ModItems.ROUND_9MM.get()), "Er ligt geen patroon klaar");
        check(!menu.quickMoveStack(player, ReloadingBenchMenu.RESULT_SLOT).isEmpty(), "De eerste patroon kon niet gepakt worden");
        check(!menu.quickMoveStack(player, ReloadingBenchMenu.RESULT_SLOT).isEmpty(), "De tweede patroon kon niet gepakt worden");
        check(menu.getSlot(ReloadingBenchMenu.RESULT_SLOT).getItem().isEmpty(), "Zonder kogelpunten ligt er toch een patroon klaar");
        check(player.getInventory().countItem(ModItems.ROUND_9MM.get()) == 2, "Je kreeg niet precies 2 patronen");
        check(bench.items().getItem(ReloadingBenchBlockEntity.SLOT_CASING).getCount() == 1
                && bench.items().getItem(ReloadingBenchBlockEntity.SLOT_BULLET).isEmpty()
                && bench.items().getItem(ReloadingBenchBlockEntity.SLOT_POWDER).getCount() == 3, "De ingrediënten klopten niet na het persen");
        helper.succeed();
    }

    /** Kogels breken glas (als de config dat toelaat), en gewone blokken niet. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "guns")
    public static void bullets_break_glass_only_when_allowed(GameTestHelper helper) {
        deterministic();
        helper.setBlock(new BlockPos(4, 3, 9), Blocks.GLASS);
        helper.setBlock(new BlockPos(4, 3, 10), Blocks.STONE);
        ServerPlayer player = holder(helper, "gun-glazier", new BlockPos(4, 2, 4),
                FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL), helper.absoluteVec(new Vec3(4.5, 3.5, 9.5)));
        ImtConfig.Guns.BULLETS_BREAK_GLASS = false;
        check(fire(player) == Firearms.Result.FIRED, "Schot 1 ging niet af");
        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.GLASS, new BlockPos(4, 3, 9));
                    ImtConfig.Guns.BULLETS_BREAK_GLASS = true;
                    check(fire(player) == Firearms.Result.FIRED, "Schot 2 ging niet af");
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, 3, 9));
                    check(fire(player) == Firearms.Result.FIRED, "Schot 3 ging niet af");
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 3, 10));
                    check(helper.getLevel().getEntitiesOfClass(com.senne.imttech.guns.BulletEntity.class,
                            helper.getBounds().inflate(2)).isEmpty(), "Een kogel bleef hangen");
                    restore();
                })
                .thenSucceed();
    }

    /** Een los onderdeel uit creative heeft zijn materiaal, en een nieuw pistool uit creative is geladen. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "guns")
    public static void gun_items_carry_their_data(GameTestHelper helper) {
        ItemStack slide = GunPartItem.of(GunPart.SLIDE, GunMaterial.TITANIUM);
        check(slide.get(ModDataComponents.GUN_MATERIAL.get()) == GunMaterial.TITANIUM, "Het materiaal zit niet aan het onderdeel");
        check(slide.getHoverName().getString().equals("Titanium Slide"), "De naam klopt niet: " + slide.getHoverName().getString());
        ItemStack gun = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
        check(FirearmItem.totalRounds(gun) == 16, "Een geladen Glock 19 hoort 15 + 1 te hebben");
        check(FirearmItem.works(gun) && FirearmItem.parts(gun).complete(), "Een nieuw pistool is niet compleet");
        check(FirearmItem.parts(gun).get(GunPart.FRAME).material() == GunMaterial.POLYMER
                && FirearmItem.parts(gun).get(GunPart.SLIDE).material() == GunMaterial.STEEL, "Een echte Glock: polymeer frame, stalen slede");
        GunParts polymer = GunParts.complete(GunMaterial.POLYMER);
        check(polymer.get(GunPart.FRAME).material() == GunMaterial.POLYMER && polymer.get(GunPart.SLIDE).material() == GunMaterial.STEEL,
                "Een polymeer pistool hoort een stalen slede te hebben");
        AABB bounds = helper.getBounds();
        check(bounds != null, "");
        helper.succeed();
    }

    /**
     * Unload (hoofdstuk 54, jouw wens): U met een magazijn in je hand haalt er één patroon uit (in je inventaris); U met
     * een pistool gooit het magazijn eruit, en zonder magazijn trekt U de patroon uit de kamer. Met patronen in het
     * magazijn kan de kamer niet leeg (eerst het magazijn eruit), en een leeg magazijn geeft niets.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "guns")
    public static void unload_takes_rounds_out_of_magazine_and_chamber(GameTestHelper helper) {
        deterministic();
        ServerPlayer player = holder(helper, "unloader", new BlockPos(4, 2, 4), MagazineItem.of(FirearmType.GLOCK_19, 5),
                new Vec3(4.5, 3, 10));
        check(Firearms.request(player, Firearms.ActionKind.UNLOAD_ROUND, 0), "Een patroon uit het magazijn halen begon niet");
        Firearms.finishNow(player);
        check(MagazineItem.rounds(player.getMainHandItem()) == 4, "Het magazijn hoort 4 te hebben");
        check(player.getInventory().countItem(ModItems.ROUND_9MM.get()) == 1, "De patroon kwam niet in je inventaris");
        player.setItemInHand(InteractionHand.MAIN_HAND, MagazineItem.of(FirearmType.GLOCK_19, 0));
        check(!Firearms.request(player, Firearms.ActionKind.UNLOAD_ROUND, 0), "Een leeg magazijn gaf een patroon");
        // Een pistool met 3 + 1: eerst moet het magazijn eruit, dan de kamer.
        ItemStack gun = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
        FirearmItem.setMagazine(gun, MagazineItem.of(FirearmType.GLOCK_19, 3));
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        check(!Firearms.request(player, Firearms.ActionKind.UNLOAD_CHAMBER, 0), "De kamer ging leeg terwijl het magazijn er nog in zat");
        check(Firearms.request(player, Firearms.ActionKind.EJECT_MAG, 0), "Het magazijn eruit begon niet");
        Firearms.finishNow(player);
        check(!FirearmItem.hasMagazine(player.getMainHandItem()), "Het magazijn zit er nog in");
        check(player.getInventory().countItem(ModItems.GLOCK_19_MAGAZINE.get()) == 1, "Het magazijn kwam niet in je inventaris");
        check(FirearmItem.chambered(player.getMainHandItem()), "De patroon in de kamer verdween met het magazijn");
        check(Firearms.request(player, Firearms.ActionKind.UNLOAD_CHAMBER, 0), "De kamer leegmaken begon niet");
        Firearms.finishNow(player);
        check(!FirearmItem.chambered(player.getMainHandItem()), "De kamer is niet leeg");
        check(player.getInventory().countItem(ModItems.ROUND_9MM.get()) == 2, "De patroon uit de kamer kwam niet in je inventaris");
        check(!Firearms.request(player, Firearms.ActionKind.UNLOAD_CHAMBER, 0), "Een lege kamer gaf nog een patroon");
        restore();
        helper.succeed();
    }
}
