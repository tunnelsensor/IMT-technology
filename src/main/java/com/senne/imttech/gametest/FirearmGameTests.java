package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.Caliber;
import com.senne.imttech.guns.CasingEntity;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Firearms;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunPartItem;
import com.senne.imttech.guns.GunParts;
import com.senne.imttech.guns.GunSlot;
import com.senne.imttech.guns.GunsmithTableBlockEntity;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.guns.ReloadingBenchBlockEntity;
import com.senne.imttech.guns.ReloadingBenchMenu;
import com.senne.imttech.registry.ModBlocks;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de grote update (hoofdstuk 103, 12 november). Jouw wens: "meer guns, deze werken net zoals de Glock": de
 * MP5 (en daarna de AK-47, de AWP en de Remington 870), elk uit zijn eigen zes onderdelen, met zijn eigen magazijn, zijn
 * kaliber, zijn keuzeschakelaar (jouw keuze: met "safe") en een sling om het te dragen.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class FirearmGameTests {
    private FirearmGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Alles voorspelbaar: geen storingen, geen slijtage, geen spreiding. */
    private static void deterministic() {
        ImtConfig.Guns.JAM_CHANCE = 0;
        ImtConfig.Guns.MISFIRE_CHANCE = 0;
        ImtConfig.Guns.WEAR_CHANCE = new float[]{0, 0, 0, 0};
        ImtConfig.Guns.SPREAD = new float[]{0, 0, 0, 0, 0};
        ImtConfig.Guns.SPREAD_AIMING = new float[]{0, 0, 0, 0, 0};
    }

    private static void restore() {
        ImtConfig.Guns.JAM_CHANCE = 0.4f;
        ImtConfig.Guns.MISFIRE_CHANCE = 0.15f;
        ImtConfig.Guns.WEAR_CHANCE = new float[]{4.0f, 2.0f, 0.8f, 0.3f};
        ImtConfig.Guns.SPREAD = new float[]{3.5f, 3.0f, 4.0f, 6.0f, 2.5f};
        ImtConfig.Guns.SPREAD_AIMING = new float[]{0.8f, 0.7f, 0.5f, 0.05f, 1.5f};
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
        return Firearms.fire(player, player.getYRot(), player.getXRot(), false);
    }

    // ================================================================ De MP5: schieten en de keuzeschakelaar

    /**
     * De MP5 schiet 9mm zoals de Glock (30 + 1), maar sneller: twee schoten vlak na elkaar gaat niet (de server houdt het
     * tempo van het wapen aan), twee ticks later wel. De keuzeschakelaar (jouw keuze: "met ook safe") gaat rond: semi,
     * 3 schoten, auto, safe, en weer semi. Op safe doet de trekker niets. De Glock heeft geen schakelaar.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "firearms")
    public static void mp5_fires_and_obeys_its_fire_selector(GameTestHelper helper) {
        deterministic();
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 12));
        husk.setNoAi(true);
        float before = husk.getHealth();
        ServerPlayer player = holder(helper, "mp5-shooter", new BlockPos(4, 2, 4),
                FirearmItem.loaded(FirearmType.MP5, GunMaterial.STEEL), helper.absoluteVec(new Vec3(4.5, 3.2, 12.5)));
        ItemStack gun = player.getMainHandItem();
        check(FirearmType.MP5.caliber == Caliber.MM9 && FirearmType.MP5.capacity() == 30, "De MP5 hoort 30 patronen 9mm te hebben");
        check(FirearmItem.fireMode(gun) == FirearmType.FireMode.SEMI, "Een nieuwe MP5 hoort op semi te staan");
        check(fire(player) == Firearms.Result.FIRED, "De MP5 schoot niet");
        check(FirearmItem.roundsInMagazine(gun) == 29 && FirearmItem.chambered(gun), "Na een schot hoort er 29 + 1 in te zitten");
        check(fire(player) == Firearms.Result.TOO_SOON, "Twee schoten in dezelfde tick: de server hoort het tempo te bewaken");
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    check(fire(player) == Firearms.Result.FIRED, "Na 3 ticks hoort de MP5 weer te schieten");
                    check(FirearmItem.roundsInMagazine(gun) == 28, "Er horen nu 28 patronen in het magazijn te zitten");
                    check(husk.getHealth() < before, "De kogels raakten de husk niet");
                    // De schakelaar rond.
                    check(Firearms.selectFire(player) == FirearmType.FireMode.BURST, "Na semi hoort 3 schoten te komen");
                    check(Firearms.selectFire(player) == FirearmType.FireMode.AUTO, "Na 3 schoten hoort auto te komen");
                    check(Firearms.selectFire(player) == FirearmType.FireMode.SAFE, "Na auto hoort safe te komen");
                    Firearms.forgetCooldown(player);
                    check(fire(player) == Firearms.Result.SAFE, "Op safe schoot de MP5 toch");
                    check(FirearmItem.roundsInMagazine(gun) == 28 && FirearmItem.chambered(gun), "Op safe ging er een patroon weg");
                    check(Firearms.selectFire(player) == FirearmType.FireMode.SEMI, "Na safe hoort weer semi te komen");
                    Firearms.forgetCooldown(player);
                    check(fire(player) == Firearms.Result.FIRED, "Terug op semi schoot de MP5 niet");
                    // De Glock heeft geen schakelaar.
                    player.setItemInHand(InteractionHand.MAIN_HAND, FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL));
                    check(Firearms.selectFire(player) == null, "De Glock hoort geen keuzeschakelaar te hebben");
                    check(FirearmItem.fireMode(player.getMainHandItem()) == FirearmType.FireMode.SEMI, "De Glock hoort op een schot te staan");
                    restore();
                })
                .thenSucceed();
    }

    /**
     * De MP5 en de Glock schieten dezelfde patronen, maar hun magazijnen zijn niet dezelfde: een MP5-magazijn vul je met
     * 9mm Rounds (R, een per keer, tot 30), een Glock-magazijn past niet in een MP5, en met het MP5-magazijn erin laadt
     * de MP5 zelf door (de spanhendel): 1 in de kamer.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "firearms")
    public static void mp5_takes_9mm_rounds_but_only_its_own_magazine(GameTestHelper helper) {
        deterministic();
        ServerPlayer player = holder(helper, "mp5-loader", new BlockPos(4, 2, 4), MagazineItem.of(FirearmType.MP5, 0),
                helper.absoluteVec(new Vec3(4.5, 3.2, 12.5)));
        player.getInventory().setItem(1, new ItemStack(ModItems.ROUND_9MM.get(), 8));
        player.getInventory().setItem(2, FirearmItem.withParts(FirearmType.MP5, GunParts.classic(FirearmType.MP5)));
        player.getInventory().setItem(3, MagazineItem.of(FirearmType.GLOCK_19, 15));
        check(MagazineItem.capacityOf(player.getMainHandItem()) == 30, "Een MP5-magazijn hoort 30 patronen te houden");
        for (int i = 0; i < 3; i++) {
            check(Firearms.request(player, Firearms.ActionKind.LOAD_ROUND, 0), "Patroon " + (i + 1) + " ging er niet in");
            Firearms.finishNow(player);
        }
        check(MagazineItem.rounds(player.getMainHandItem()) == 3, "Er horen 3 patronen in het magazijn te zitten");
        check(player.getInventory().getItem(1).getCount() == 5, "Er horen 5 losse patronen over te zijn");
        // Een Glock-magazijn past niet in de MP5.
        player.getInventory().selected = 3;
        check(!Firearms.insertMagazine(player, InteractionHand.MAIN_HAND), "Een Glock-magazijn ging in de MP5");
        // Het eigen magazijn wel; daarna laadt de MP5 zelf door.
        player.getInventory().selected = 0;
        check(Firearms.insertMagazine(player, InteractionHand.MAIN_HAND), "Het MP5-magazijn ging er niet in");
        Firearms.finishNow(player);
        ItemStack gun = player.getInventory().getItem(2);
        check(FirearmItem.hasMagazine(gun) && FirearmItem.roundsInMagazine(gun) == 3, "Het magazijn zit niet in de MP5");
        check(player.getInventory().getItem(0).isEmpty(), "Het magazijn bleef in je hand");
        player.getInventory().selected = 2;
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    check(Firearms.currentAction(player) == Firearms.ActionKind.SLIDE_RELEASE, "De MP5 laadde niet vanzelf door");
                    Firearms.finishNow(player);
                    check(FirearmItem.chambered(gun) && FirearmItem.roundsInMagazine(gun) == 2,
                            "Na het doorladen hoort er 2 + 1 in te zitten");
                    restore();
                })
                .thenSucceed();
    }

    // ================================================================ De Gunsmith Table: elk wapen zijn eigen onderdelen

    /**
     * Op de lege tafel zegt het frame welk wapen het wordt: een MP5 Receiver maakt een MP5. Een onderdeel van de Glock
     * past er niet in. De veer en de slagpin zitten achter de Bolt Carrier (eerst die eraf); de loop van een lang wapen
     * steekt gewoon uit. Met alle zes is hij compleet, en de onderdelen komen terug als onderdelen van de MP5.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "firearms")
    public static void gunsmith_table_builds_an_mp5_from_its_own_parts(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 2, 4);
        helper.setBlock(at, ModBlocks.GUNSMITH_TABLE.get());
        GunsmithTableBlockEntity table = (GunsmithTableBlockEntity) helper.getBlockEntity(at);
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 6), "mp5-smith");
        GunPart[] order = {GunPart.FRAME, GunPart.BARREL, GunPart.SPRINGS, GunPart.STRIKER, GunPart.TRIGGER, GunPart.SLIDE};
        for (int i = 0; i < order.length; i++) {
            player.getInventory().setItem(i, GunPartItem.of(FirearmType.MP5, order[i], i == 5 ? GunMaterial.TITANIUM : GunMaterial.STEEL));
        }
        player.getInventory().setItem(8, GunPartItem.of(GunPart.SLIDE, GunMaterial.STEEL));
        check(!table.install(player, 1), "Een loop ging erin zonder frame");
        check(table.install(player, 0), "De MP5 Receiver ging er niet in");
        check(FirearmItem.typeOf(table.gun()) == FirearmType.MP5, "Een MP5 Receiver hoort een MP5 te maken");
        check(!table.install(player, 8), "Een slede van de Glock ging in de MP5");
        for (int i = 1; i < order.length; i++) {
            check(table.install(player, i), "Onderdeel " + order[i] + " ging er niet in");
        }
        GunParts parts = FirearmItem.parts(table.gun());
        check(parts.complete(), "De MP5 hoort compleet te zijn, er ontbreekt " + parts.missing());
        check(parts.get(GunPart.SLIDE).material() == GunMaterial.TITANIUM, "De Bolt Carrier hield zijn materiaal niet");
        // De veer zit achter de Bolt Carrier; de loop niet.
        check(!table.remove(player, GunPart.SPRINGS), "De veer kon eruit terwijl de Bolt Carrier erin zat");
        check(table.remove(player, GunPart.BARREL), "De loop van een lang wapen hoort er zo uit te kunnen");
        check(table.remove(player, GunPart.SLIDE), "De Bolt Carrier ging er niet uit");
        check(table.remove(player, GunPart.SPRINGS), "Zonder Bolt Carrier hoort de veer eruit te kunnen");
        ItemStack carrier = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (GunPartItem.partOf(stack) == GunPart.SLIDE && GunPartItem.typeOf(stack) == FirearmType.MP5) {
                carrier = stack;
            }
        }
        check(!carrier.isEmpty() && GunPartItem.materialOf(carrier) == GunMaterial.TITANIUM,
                "De Bolt Carrier kwam niet terug als titanium onderdeel van de MP5");
        check(carrier.getHoverName().getString().equals("Titanium MP5 Bolt Carrier"),
                "Het onderdeel heet '" + carrier.getHoverName().getString() + "', verwacht 'Titanium MP5 Bolt Carrier'");
        helper.succeed();
    }

    // ================================================================ Dragen: de sling

    /**
     * Een lang wapen past niet in de holster. Met een Gun Sling eraan (het vak "sling" op de Gunsmith Table) past het in
     * een sling-vakje, zoals de lasergeweren; zonder niet. Op een pistool past geen sling. Haal je de sling eraf, dan
     * krijg je hem terug.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "firearms")
    public static void mp5_needs_a_sling_to_hang_on_your_back(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 2, 4);
        helper.setBlock(at, ModBlocks.GUNSMITH_TABLE.get());
        GunsmithTableBlockEntity table = (GunsmithTableBlockEntity) helper.getBlockEntity(at);
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 6), "mp5-sling");
        ItemStack bare = FirearmItem.withParts(FirearmType.MP5, GunParts.classic(FirearmType.MP5));
        check(!GearItem.Kind.HOLSTER.accepts(bare), "De MP5 hoort niet in de holster te passen");
        check(GearItem.Kind.HOLSTER.accepts(FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL)), "De Glock hoort in de holster te passen");
        check(!Gear.slingAccepts(bare), "Een MP5 zonder sling hoort niet in een sling-vakje te passen");
        check(Gear.needsSling(bare), "De MP5 hoort een sling nodig te hebben");
        player.getInventory().setItem(0, bare);
        player.getInventory().setItem(1, new ItemStack(ModItems.GUN_SLING.get(), 2));
        check(table.putGun(player, 0), "De MP5 ging niet op de tafel");
        check(table.install(player, 1), "De sling ging niet aan de MP5");
        check(FirearmItem.hasSling(table.gun()), "De MP5 heeft geen sling");
        check(!table.install(player, 1), "Er ging een tweede sling aan");
        check(player.getInventory().getItem(1).getCount() == 1, "Er hoort precies een sling gebruikt te zijn");
        check(Gear.slingAccepts(table.gun()), "Een MP5 met sling hoort in een sling-vakje te passen");
        check(table.remove(player, GunSlot.SLING.ordinal()), "De sling ging er niet af");
        check(!FirearmItem.hasSling(table.gun()), "De sling hangt er nog aan");
        check(player.getInventory().countItem(ModItems.GUN_SLING.get()) == 2, "De sling kwam niet terug");
        check(table.takeGun(player), "De MP5 kon niet mee");
        // Op een pistool past geen sling.
        ItemStack glock = FirearmItem.withParts(FirearmType.GLOCK_19, GunParts.classic());
        player.getInventory().setItem(5, glock);
        check(table.putGun(player, 5), "De Glock ging niet op de tafel");
        int slingSlot = player.getInventory().findSlotMatchingItem(new ItemStack(ModItems.GUN_SLING.get()));
        check(slingSlot >= 0 && !table.install(player, slingSlot), "Er ging een sling aan de Glock");
        // Een geladen MP5 (creative) heeft zijn sling al.
        check(Gear.slingAccepts(FirearmItem.loaded(FirearmType.MP5, GunMaterial.STEEL)), "Een geladen MP5 hoort een sling te hebben");
        helper.succeed();
    }

    // ================================================================ De AK-47 (stap 2): 7.62x39mm

    /**
     * De AK-47 schiet zijn eigen patroon (7.62x39mm, 30 + 1) en raakt harder dan de MP5. De huls die eruit springt is een
     * 7.62-huls, en zo raap je hem ook op. Zijn schakelaar heeft geen stand voor drie schoten: semi, auto, safe.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "firearms")
    public static void ak47_fires_762_and_ejects_its_own_casing(GameTestHelper helper) {
        deterministic();
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 12));
        husk.setNoAi(true);
        float before = husk.getHealth();
        ServerPlayer player = holder(helper, "ak-shooter", new BlockPos(4, 2, 4),
                FirearmItem.loaded(FirearmType.AK_47, GunMaterial.STEEL), helper.absoluteVec(new Vec3(4.5, 3.2, 12.5)));
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, FirearmItem.loaded(FirearmType.AK_47, GunMaterial.STEEL));
        ItemStack gun = player.getMainHandItem();
        check(FirearmType.AK_47.caliber == Caliber.R762 && FirearmType.AK_47.capacity() == 30,
                "De AK-47 hoort 30 patronen 7.62x39mm te hebben");
        check(FirearmType.AK_47.damage() > FirearmType.MP5.damage(), "De AK-47 hoort harder te raken dan de MP5");
        check(FirearmItem.fireMode(gun) == FirearmType.FireMode.SEMI, "Een nieuwe AK-47 hoort op semi te staan");
        check(fire(player) == Firearms.Result.FIRED, "De AK-47 schoot niet");
        check(FirearmItem.roundsInMagazine(gun) == 29 && FirearmItem.chambered(gun), "Na een schot hoort er 29 + 1 in te zitten");
        List<CasingEntity> casings = helper.getLevel().getEntitiesOfClass(CasingEntity.class, helper.getBounds().inflate(4));
        check(casings.size() == 1 && casings.getFirst().caliber() == Caliber.R762,
                "Er hoort een 7.62-huls uit de AK-47 te springen");
        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    check(husk.getHealth() < before, "De kogel raakte de husk niet");
                    // De schakelaar: geen drie schoten.
                    check(Firearms.selectFire(player) == FirearmType.FireMode.AUTO, "Na semi hoort bij de AK-47 auto te komen");
                    check(Firearms.selectFire(player) == FirearmType.FireMode.SAFE, "Na auto hoort safe te komen");
                    Firearms.forgetCooldown(player);
                    check(fire(player) == Firearms.Result.SAFE, "Op safe schoot de AK-47 toch");
                    check(Firearms.selectFire(player) == FirearmType.FireMode.SEMI, "Na safe hoort weer semi te komen");
                    // Oprapen: een 7.62-huls, geen 9mm.
                    int picked = Firearms.pickUpCasings(player, casings.getFirst().position());
                    check(picked == 1, "Oprapen gaf " + picked + " hulzen");
                    check(player.getInventory().countItem(ModItems.CASING_762.get()) == 1
                            && player.getInventory().countItem(ModItems.CASING_9MM.get()) == 0,
                            "De huls van de AK-47 kwam niet als 7.62x39mm Casing in je inventaris");
                    restore();
                })
                .thenSucceed();
    }

    /**
     * In een AK-magazijn gaan alleen 7.62-patronen: met alleen 9mm in je inventaris gebeurt er niets. Een MP5-magazijn
     * past niet in een AK-47, zijn eigen magazijn wel.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "firearms")
    public static void ak47_magazine_takes_only_762_rounds(GameTestHelper helper) {
        deterministic();
        ServerPlayer player = holder(helper, "ak-loader", new BlockPos(4, 2, 4), MagazineItem.of(FirearmType.AK_47, 0),
                helper.absoluteVec(new Vec3(4.5, 3.2, 12.5)));
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, MagazineItem.of(FirearmType.AK_47, 0));
        player.getInventory().setItem(1, new ItemStack(ModItems.ROUND_9MM.get(), 8));
        player.getInventory().setItem(2, FirearmItem.withParts(FirearmType.AK_47, GunParts.classic(FirearmType.AK_47)));
        player.getInventory().setItem(3, MagazineItem.of(FirearmType.MP5, 30));
        check(MagazineItem.capacityOf(player.getMainHandItem()) == 30, "Een AK-magazijn hoort 30 patronen te houden");
        check(!Firearms.request(player, Firearms.ActionKind.LOAD_ROUND, 0), "Zonder 7.62-patronen begon het laden toch");
        check(MagazineItem.rounds(player.getMainHandItem()) == 0 && player.getInventory().getItem(1).getCount() == 8,
                "Een 9mm-patroon ging in een AK-magazijn");
        player.getInventory().setItem(4, new ItemStack(ModItems.ROUND_762.get(), 5));
        for (int i = 0; i < 2; i++) {
            check(Firearms.request(player, Firearms.ActionKind.LOAD_ROUND, 0), "Patroon " + (i + 1) + " ging er niet in");
            Firearms.finishNow(player);
        }
        check(MagazineItem.rounds(player.getMainHandItem()) == 2, "Er horen 2 patronen in het magazijn te zitten");
        check(player.getInventory().getItem(4).getCount() == 3 && player.getInventory().getItem(1).getCount() == 8,
                "Er horen 3 patronen 7.62 en nog alle 8 patronen 9mm over te zijn");
        // Een MP5-magazijn past niet in de AK-47.
        player.getInventory().selected = 3;
        check(!Firearms.insertMagazine(player, InteractionHand.MAIN_HAND), "Een MP5-magazijn ging in de AK-47");
        player.getInventory().selected = 0;
        check(Firearms.insertMagazine(player, InteractionHand.MAIN_HAND), "Het AK-magazijn ging er niet in");
        Firearms.finishNow(player);
        ItemStack gun = player.getInventory().getItem(2);
        check(FirearmItem.hasMagazine(gun) && FirearmItem.roundsInMagazine(gun) == 2, "Het magazijn zit niet in de AK-47");
        restore();
        helper.succeed();
    }

    /**
     * De Gunsmith Table houdt de wapens uit elkaar: een AK-47 Receiver maakt een AK-47, een Bolt Carrier van de MP5 past
     * er niet in, zijn eigen wel, en er kan een sling aan. De onderdelen heten naar hun wapen.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "firearms")
    public static void gunsmith_table_keeps_ak47_and_mp5_parts_apart(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 2, 4);
        helper.setBlock(at, ModBlocks.GUNSMITH_TABLE.get());
        GunsmithTableBlockEntity table = (GunsmithTableBlockEntity) helper.getBlockEntity(at);
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 6), "ak-smith");
        player.getInventory().setItem(0, GunPartItem.of(FirearmType.AK_47, GunPart.FRAME, GunMaterial.STEEL));
        player.getInventory().setItem(1, GunPartItem.of(FirearmType.MP5, GunPart.SLIDE, GunMaterial.STEEL));
        player.getInventory().setItem(2, GunPartItem.of(FirearmType.AK_47, GunPart.SLIDE, GunMaterial.TITANIUM));
        player.getInventory().setItem(3, new ItemStack(ModItems.GUN_SLING.get()));
        String name = player.getInventory().getItem(2).getHoverName().getString();
        check(name.equals("Titanium AK-47 Bolt Carrier"), "Het onderdeel heet '" + name + "', verwacht 'Titanium AK-47 Bolt Carrier'");
        check(table.install(player, 0), "De AK-47 Receiver ging er niet in");
        check(FirearmItem.typeOf(table.gun()) == FirearmType.AK_47, "Een AK-47 Receiver hoort een AK-47 te maken");
        check(!table.install(player, 1), "Een Bolt Carrier van de MP5 ging in de AK-47");
        check(table.install(player, 2), "De Bolt Carrier van de AK-47 ging er niet in");
        check(table.install(player, 3) && FirearmItem.hasSling(table.gun()), "De sling ging niet aan de AK-47");
        check(GunParts.classic(FirearmType.AK_47).complete(), "Een AK-47 uit de creative tab hoort compleet te zijn");
        helper.succeed();
    }

    // ================================================================ De Reloading Bench: elk kaliber

    /**
     * De Reloading Bench perst elk kaliber: de huls zegt welke patroon het wordt en de kogelpunt moet erbij passen. Een
     * 7.62-patroon kost twee Crushed Gunpowder (een 9mm een): met 5 kruit pers je er dus 2. Een 9mm-kogelpunt bij een
     * 7.62-huls geeft niets.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "firearms")
    public static void reloading_bench_presses_each_caliber(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 2, 4);
        helper.setBlock(at, ModBlocks.RELOADING_BENCH.get());
        ReloadingBenchBlockEntity bench = (ReloadingBenchBlockEntity) helper.getBlockEntity(at);
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 6), "ak-reloader");
        player.getInventory().clearContent();
        bench.items().setItem(ReloadingBenchBlockEntity.SLOT_CASING, new ItemStack(ModItems.CASING_762.get(), 4));
        bench.items().setItem(ReloadingBenchBlockEntity.SLOT_BULLET, new ItemStack(ModItems.BULLET_762.get(), 4));
        bench.items().setItem(ReloadingBenchBlockEntity.SLOT_POWDER, new ItemStack(ModItems.CRUSHED_GUNPOWDER.get(), 5));
        check(ReloadingBenchBlockEntity.caliber(bench.items()) == Caliber.R762, "De bank ziet geen 7.62 in de huls en de kogelpunt");
        check(ReloadingBenchBlockEntity.pressable(bench.items()) == 2,
                "Met 5 kruit hoor je 2 patronen 7.62 te kunnen persen, niet " + ReloadingBenchBlockEntity.pressable(bench.items()));
        ReloadingBenchMenu menu = new ReloadingBenchMenu(1, player.getInventory(), bench.items());
        check(menu.getSlot(ReloadingBenchMenu.RESULT_SLOT).getItem().is(ModItems.ROUND_762.get()), "Er ligt geen 7.62-patroon klaar");
        check(!menu.quickMoveStack(player, ReloadingBenchMenu.RESULT_SLOT).isEmpty(), "De eerste patroon kon niet gepakt worden");
        check(!menu.quickMoveStack(player, ReloadingBenchMenu.RESULT_SLOT).isEmpty(), "De tweede patroon kon niet gepakt worden");
        check(menu.getSlot(ReloadingBenchMenu.RESULT_SLOT).getItem().isEmpty(), "Met 1 kruit ligt er toch een 7.62-patroon klaar");
        check(player.getInventory().countItem(ModItems.ROUND_762.get()) == 2, "Je kreeg niet precies 2 patronen 7.62");
        check(bench.items().getItem(ReloadingBenchBlockEntity.SLOT_CASING).getCount() == 2
                && bench.items().getItem(ReloadingBenchBlockEntity.SLOT_BULLET).getCount() == 2
                && bench.items().getItem(ReloadingBenchBlockEntity.SLOT_POWDER).getCount() == 1,
                "Na 2 patronen 7.62 horen er 2 hulzen, 2 kogelpunten en 1 kruit over te zijn");
        // Een 9mm-kogelpunt bij een 7.62-huls: er komt niets uit.
        bench.items().setItem(ReloadingBenchBlockEntity.SLOT_BULLET, new ItemStack(ModItems.BULLET_9MM.get(), 4));
        bench.items().setItem(ReloadingBenchBlockEntity.SLOT_POWDER, new ItemStack(ModItems.CRUSHED_GUNPOWDER.get(), 8));
        menu.slotsChanged(bench.items());
        check(ReloadingBenchBlockEntity.mismatch(bench.items()) && ReloadingBenchBlockEntity.pressable(bench.items()) == 0
                && menu.getSlot(ReloadingBenchMenu.RESULT_SLOT).getItem().isEmpty(),
                "Een 9mm-kogelpunt op een 7.62-huls gaf toch een patroon");
        // 9mm bij 9mm: een kruit per patroon.
        bench.items().setItem(ReloadingBenchBlockEntity.SLOT_CASING, new ItemStack(ModItems.CASING_9MM.get(), 4));
        menu.slotsChanged(bench.items());
        check(menu.getSlot(ReloadingBenchMenu.RESULT_SLOT).getItem().is(ModItems.ROUND_9MM.get())
                && ReloadingBenchBlockEntity.pressable(bench.items()) == 4, "9mm bij 9mm hoort 4 patronen te geven");
        helper.succeed();
    }
}
