package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.armor.ArmorRegistry;
import com.senne.imttech.armor.HitSource;
import com.senne.imttech.armor.PlateItem;
import com.senne.imttech.armor.PlateTier;
import com.senne.imttech.armor.VestData;
import com.senne.imttech.armor.VestItem;
import com.senne.imttech.armor.VestMenu;
import com.senne.imttech.armor.Vests;
import com.senne.imttech.armor.ArmorRecipe;
import com.senne.imttech.armor.ArmorWorkbenchBlock;
import com.senne.imttech.armor.ArmorWorkbenchMenu;
import com.senne.imttech.backpack.BackpackDyeRecipe;
import com.senne.imttech.backpack.BackpackRegistry;
import com.senne.imttech.backpack.Molle;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearContents;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.howto.HowToTopic;
import com.senne.imttech.howto.HowToTopics;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModCreativeTabs;
import com.senne.imttech.registry.ModDamageTypes;
import com.senne.imttech.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de body armor (hoofdstuk 109). Jouw wens: "body armor waar je plates in kunt steken, hoe sterker de plate hoe
 * minder damage je neemt, de bodyarmor kan ook mags hebben (3) deze komen bij de ander 6 mags van een magpouch, je kunt op
 * de achterkant van je vest zelf een tekst zetten zoals 'SWAT'". Jouw keuzes: een eigen vest-vakje over je borstplaat, een
 * plate voor en een achter (een headshot gaat erlangs), slijten per treffer, en als extra's Raven-soldaten met een vest,
 * kleuren en camo, het Pegboard en plates in de buit.
 *
 * De tests gebruiken bestaande batches ("backpacks" en "bodies"): een nieuwe batchnaam verandert de volgorde van alle
 * batches (de server zet ze in een HashMap), en dan staan andere tests naast andere buren.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class ArmorGameTests {
    private ArmorGameTests() {}

    private static final String ARENA = "empty";
    /**
     * Zo lang is een testspeler na het verschijnen onkwetsbaar (ServerPlayer: 60 ticks, zoals elke speler die net inlogt);
     * pas daarna komt een klap aan.
     */
    private static final int SPAWN_INVULNERABLE_TICKS = 70;

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name, BlockPos at) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, at, name);
        player.getAbilities().instabuild = false;
        player.getInventory().selected = 0;
        // Hij kijkt naar het zuiden (+z): voor hem is +z, achter hem -z.
        player.setYRot(0);
        player.setYHeadRot(0);
        player.setYBodyRot(0);
        return player;
    }

    private static ItemStack plate(PlateTier tier) {
        return new ItemStack(ArmorRegistry.plate(tier));
    }

    private static ItemStack vest(ItemStack front, ItemStack back, ItemStack... mags) {
        ItemStack vest = new ItemStack(ArmorRegistry.PLATE_CARRIER.get());
        VestItem.setData(vest, new VestData(front, back, List.of(mags), "", DyeColor.WHITE.getId()));
        return vest;
    }

    /** Een kogel zonder schutter die van {@code from} komt (dan schaalt Minecraft hem niet met de moeilijkheid). */
    private static DamageSource shot(ServerLevel level, Vec3 from, boolean head) {
        DamageSource bullet = ModDamageTypes.bullet(level, null);
        return HitSource.of(new DamageSource(bullet.typeHolder(), null, null, from), head);
    }

    /** {@code amount} schade op deze speler (met volle hartjes en zonder "even onkwetsbaar"); geeft hoeveel hij verloor. */
    private static float hit(ServerPlayer player, DamageSource source, float amount) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        player.hurt(source, amount);
        return player.getMaxHealth() - player.getHealth();
    }

    /** Een lijstje spullen als "wat je bij je hebt" voor een recept van de werkbank. */
    private record GunsmithRecipeCarried(List<ItemStack> stacks) {
        com.senne.imttech.guns.GunsmithRecipe.Carried carried() {
            List<ItemStack> copies = new ArrayList<>();
            for (ItemStack stack : stacks) {
                copies.add(stack.copy());
            }
            return new com.senne.imttech.guns.GunsmithRecipe.Carried(copies);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.02f;
    }

    // ================================================================ Wat de plates tegenhouden

    /**
     * Een vest met staal voor en polymeer achter: een kogel van voren verliest het deel dat staal tegenhoudt, een van achteren
     * het deel van polymeer, een headshot niets. Zonder plate achter (tweede speler) komt een schot in de rug helemaal aan.
     * Vallen en vuur houden de plates niet tegen. Het tegengehouden deel kost de plate duurzaamheid.
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "backpacks")
    public static void vest_plates_stop_hits_from_their_own_side(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "vest-sides", new BlockPos(3, 2, 3));
        check(Gear.setVest(player, vest(plate(PlateTier.STEEL), plate(PlateTier.POLYMER))), "Het vest ging niet aan");
        ServerPlayer bare = player(helper, "vest-no-back", new BlockPos(6, 2, 3));
        Gear.setVest(bare, vest(plate(PlateTier.NETHERITE), ItemStack.EMPTY));
        helper.runAfterDelay(SPAWN_INVULNERABLE_TICKS, () -> {
            Vec3 at = player.position();
            Vec3 front = at.add(0, 1.2, 4);
            Vec3 back = at.add(0, 1.2, -4);
            float fromFront = hit(player, shot(level, front, false), 10);
            check(near(fromFront, 10 * (1 - PlateTier.STEEL.reduction())),
                    "Van voren had de stalen plate " + Math.round(PlateTier.STEEL.reduction() * 100) + " % moeten tegenhouden: " + fromFront + " schade");
            ItemStack steel = VestItem.data(Gear.vest(player)).front();
            int units = (int) Math.ceil(10 * PlateTier.STEEL.reduction() * PlateItem.UNITS / PlateTier.STEEL.durability());
            check(steel.getDamageValue() == units, "De stalen plate sleet " + steel.getDamageValue() + " stapjes in plaats van " + units);
            float fromBehind = hit(player, shot(level, back, false), 10);
            check(near(fromBehind, 10 * (1 - PlateTier.POLYMER.reduction())), "Van achteren had de polymeren plate moeten tellen: " + fromBehind);
            check(VestItem.data(Gear.vest(player)).back().getDamageValue() > 0, "De achterste plate sleet niet");
            float headshot = hit(player, shot(level, front, true), 10);
            check(near(headshot, 10), "Een headshot ging niet langs de plates: " + headshot + " schade");
            float fall = hit(player, player.damageSources().fall(), 4);
            check(near(fall, 4), "Een plate hield valschade tegen: " + fall);
            float fire = hit(player, player.damageSources().inFire(), 2);
            check(near(fire, 2), "Een plate hield vuur tegen: " + fire);

            Vec3 bareAt = bare.position();
            check(near(hit(bare, shot(level, bareAt.add(0, 1.2, -4), false), 10), 10), "Zonder plate achter werd een schot in de rug toch kleiner");
            check(near(hit(bare, shot(level, bareAt.add(0, 1.2, 4), false), 10), 10 * (1 - PlateTier.NETHERITE.reduction())),
                    "De netherite plate voorop telde niet");
            // Hoe sterker de plate, hoe minder schade (jouw wens).
            check(PlateTier.POLYMER.reduction() > 0 && PlateTier.POLYMER.reduction() < PlateTier.STEEL.reduction() && PlateTier.STEEL.reduction() < PlateTier.TITANIUM.reduction()
                    && PlateTier.TITANIUM.reduction() < PlateTier.NETHERITE.reduction(), "De plates staan niet van zwak naar sterk");
            helper.succeed();
        });
    }

    /**
     * Een bijna versleten titanium plate barst bij de volgende treffer (die hij nog half opvangt); daarna houdt hij niets meer
     * tegen. Een aambeeld herstelt hem niet (hoofdstuk 111: dat doe je op de Armor Workbench).
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "backpacks")
    public static void a_worn_plate_cracks_and_stops_nothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "vest-crack", new BlockPos(3, 2, 3));
        ItemStack worn = plate(PlateTier.TITANIUM);
        worn.setDamageValue(worn.getMaxDamage() - 10);
        Gear.setVest(player, vest(worn, ItemStack.EMPTY));
        helper.runAfterDelay(SPAWN_INVULNERABLE_TICKS, () -> {
            Vec3 front = player.position().add(0, 1.2, 4);
            float first = hit(player, shot(level, front, false), 10);
            check(near(first, 10 * (1 - PlateTier.TITANIUM.reduction())), "De laatste treffer ving hij niet meer op: " + first);
            ItemStack cracked = VestItem.data(Gear.vest(player)).front();
            check(PlateItem.broken(cracked) && PlateItem.tierOf(cracked) == PlateTier.TITANIUM, "De plate barstte niet (of verdween)");
            check(near(hit(player, shot(level, front, false), 10), 10), "Een gebarsten plate hield nog iets tegen");
            // Hoofdstuk 111 (jouw keuze): herstellen op de Armor Workbench, niet op een aambeeld of in de crafting table.
            check(!cracked.getItem().isValidRepairItem(cracked, new ItemStack(ModItems.TITANIUM_INGOT.get())), "Een aambeeld herstelt een plate");
            check(!cracked.getItem().isRepairable(cracked), "Twee plates in de crafting table worden samen een betere");
            check(PlateItem.left(plate(PlateTier.STEEL)) == PlateTier.STEEL.durability(), "Een nieuwe plate kan niet zijn hele duurzaamheid tegenhouden");
            helper.succeed();
        });
    }

    // ================================================================ De magazijnen

    /**
     * Jouw wens: de drie magazijnen van het vest komen bij die van de magazijnpouch. R ziet eerst de pouch, dan het vest; een
     * leeg magazijn gaat bij het wegsteken eerst in de pouch en dan in het vest; herladen uit een vakje van het vest wisselt
     * het magazijn in het pistool met dat vakje.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "backpacks")
    public static void vest_magazines_join_the_mag_pouch(GameTestHelper helper) {
        ServerPlayer player = player(helper, "vest-mags", new BlockPos(3, 2, 3));
        int hip = GearItem.Kind.MAG_POUCH.size();
        ItemStack pouch = new ItemStack(ModItems.MAG_POUCH.get());
        List<ItemStack> inPouch = new ArrayList<>();
        for (int i = 0; i < hip; i++) {
            inPouch.add(MagazineItem.of(FirearmType.GLOCK_19, 15));
        }
        GearItem.setContents(pouch, inPouch);
        Gear.set(player, GearItem.Kind.MAG_POUCH, pouch);
        Gear.setVest(player, vest(ItemStack.EMPTY, ItemStack.EMPTY, MagazineItem.of(FirearmType.GLOCK_19, 10), ItemStack.EMPTY));
        List<ItemStack> all = Molle.magazines(player);
        check(all.size() == hip + VestData.MAGS, "R ziet " + all.size() + " vakjes in plaats van " + hip + " + " + VestData.MAGS);
        check(MagazineItem.rounds(all.get(hip)) == 10, "Het eerste vakje na de pouch is niet het eerste van het vest");

        // De pouch is vol: wegsteken gaat in het vest, in zijn eerste lege vakje.
        check(Gear.storeMagazine(player, MagazineItem.of(FirearmType.GLOCK_19, 7)), "Een magazijn paste niet in het vest");
        check(MagazineItem.rounds(VestItem.data(Gear.vest(player)).mags().get(1)) == 7, "Het magazijn kwam niet in het tweede vakje van het vest");
        check(Gear.storeMagazine(player, MagazineItem.of(FirearmType.GLOCK_19, 3)), "Het derde vakje van het vest nam niets aan");
        check(!Gear.storeMagazine(player, MagazineItem.of(FirearmType.GLOCK_19, 1)), "Een vierde magazijn paste nog in een vol vest");

        // Herladen uit het eerste vakje van het vest.
        ItemStack gun = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
        FirearmItem.setMagazine(gun, MagazineItem.of(FirearmType.GLOCK_19, 0));
        player.getInventory().setItem(0, gun);
        Gear.finishPouchReload(player, player.getMainHandItem(), hip);
        check(MagazineItem.rounds(FirearmItem.magazine(player.getMainHandItem())) == 10, "Het pistool kreeg het magazijn uit het vest niet");
        check(MagazineItem.rounds(VestItem.data(Gear.vest(player)).mags().getFirst()) == 0, "Het lege magazijn kwam niet in het vest");
        helper.succeed();
    }

    // ================================================================ Het vakje en het scherm

    /**
     * Het eigen vest-vakje: een klik met het vest aan je muis trekt het aan (een ander ding past er niet in). Een speler uit een
     * oude wereld (7 vakjes uitrusting) krijgt het vakje er vanzelf bij. Sneak + rechtsklik met een vest wisselt het om.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "backpacks")
    public static void vest_goes_in_its_own_slot(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer player = player(helper, "vest-slot", new BlockPos(3, 2, 3));
        player.setData(ModAttachments.GEAR, GearContents.sized(List.of(), Gear.VEST));
        check(Gear.vest(player).isEmpty(), "Een oude speler draagt al een vest");
        player.inventoryMenu.setCarried(new ItemStack(Items.IRON_CHESTPLATE));
        Gear.click(player, Gear.VEST);
        check(Gear.vest(player).isEmpty(), "Een borstplaat ging in het vest-vakje");
        ItemStack first = VestItem.withText(new ItemStack(ArmorRegistry.PLATE_CARRIER.get()), "ONE", DyeColor.WHITE);
        player.inventoryMenu.setCarried(first);
        Gear.click(player, Gear.VEST);
        check(VestItem.data(Gear.vest(player)).text().equals("ONE") && player.inventoryMenu.getCarried().isEmpty(),
                "Het vest ging niet in zijn vakje");
        check(player.getData(ModAttachments.GEAR).size() == Gear.SLOTS, "Het rijtje uitrusting groeide niet mee");
        ItemStack second = VestItem.withText(new ItemStack(ArmorRegistry.PLATE_CARRIER.get()), "TWO", DyeColor.RED);
        player.getInventory().setItem(0, second);
        check(Vests.putOn(player, net.minecraft.world.InteractionHand.MAIN_HAND), "Sneak + rechtsklik trok het vest niet aan");
        check(VestItem.data(Gear.vest(player)).text().equals("TWO"), "Het tweede vest zit niet aan");
        check(VestItem.data(player.getMainHandItem()).text().equals("ONE"), "Het oude vest kwam niet in je hand");
        helper.succeed();
    }

    /**
     * Het scherm leest het vest zelf: een plate die slijt terwijl het open staat, zie je meteen. Een plate past niet in een
     * magazijnvakje. (De tekst pas je sinds hoofdstuk 111 aan op de Armor Workbench, zie de test daarvan.)
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "backpacks")
    public static void vest_screen_shows_the_live_vest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "vest-screen", new BlockPos(3, 2, 3));
        Gear.setVest(player, vest(plate(PlateTier.STEEL), ItemStack.EMPTY));
        check(Vests.openWorn(player), "Het scherm van het vest ging niet open");
        check(player.containerMenu instanceof VestMenu, "Het scherm is geen VestMenu");
        VestMenu menu = (VestMenu) player.containerMenu;
        check(PlateItem.tierOf(menu.getSlot(VestMenu.FRONT).getItem()) == PlateTier.STEEL, "Het scherm toont de voorste plate niet");
        helper.runAfterDelay(SPAWN_INVULNERABLE_TICKS, () -> {
            hit(player, shot(level, player.position().add(0, 1.2, 4), false), 10);
            check(menu.getSlot(VestMenu.FRONT).getItem().getDamageValue() > 0, "Het scherm toont de versleten plate niet");
            check(!menu.getSlot(VestMenu.MAGS).mayPlace(plate(PlateTier.POLYMER)), "Een plate paste in een magazijnvakje");
            check(menu.getSlot(VestMenu.MAGS).mayPlace(MagazineItem.of(FirearmType.GLOCK_19, 5)), "Een magazijn paste niet in een magazijnvakje");
            menu.getSlot(VestMenu.BACK).set(plate(PlateTier.TITANIUM));
            check(PlateItem.tierOf(VestItem.data(Gear.vest(player)).back()) == PlateTier.TITANIUM, "De plate uit het scherm zit niet in het vest");
            player.closeContainer();
            helper.succeed();
        });
    }

    // ================================================================ De extra's

    /** Jouw keuze: een Raven-soldaat met een vest laat het na zijn dood in zijn lichaam achter (met RAVEN op de rug). */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "bodies")
    public static void raven_soldier_vest_goes_into_his_body(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
        com.senne.imttech.raven.RavenSoldierEntity soldier = helper.spawn(com.senne.imttech.registry.ModEntities.RAVEN_SOLDIER.get(),
                new BlockPos(3, 2, 3));
        soldier.setVest(Vests.ravenVest(net.minecraft.util.RandomSource.create(42), ItemStack.EMPTY));
        check(VestItem.isVest(Vests.worn(soldier)) && VestItem.data(Vests.worn(soldier)).text().equals("RAVEN"),
                "De soldaat draagt geen vest met RAVEN op zijn rug");
        check(PlateItem.works(VestItem.data(soldier.vest()).front()) && PlateItem.works(VestItem.data(soldier.vest()).back()),
                "Het vest van de soldaat heeft geen twee werkende plates");
        soldier.kill();
        helper.succeedWhen(() -> {
            List<com.senne.imttech.raven.RavenBodyEntity> bodies = helper.getLevel().getEntitiesOfClass(
                    com.senne.imttech.raven.RavenBodyEntity.class, helper.getBounds().inflate(2));
            check(!bodies.isEmpty(), "De soldaat liet geen lichaam achter");
            boolean found = false;
            for (int i = 0; i < bodies.getFirst().loot().getContainerSize(); i++) {
                found |= VestItem.isVest(bodies.getFirst().loot().getItem(i));
            }
            check(found, "Zijn vest zit niet in zijn lichaam");
            bodies.getFirst().discard();
        });
    }

    /**
     * Het vest en de plates horen bij Equipment en bij het onderwerp "Body armor" van How it works; ze hangen aan het Pegboard
     * (het vest 5 x 6 gaatjes, een plate 3 x 4).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "backpacks")
    public static void armor_items_have_a_tab_a_topic_and_a_pegboard_spot(GameTestHelper helper) {
        List<Item> items = new ArrayList<>(List.of(ArmorRegistry.PLATE_CARRIER.get()));
        for (PlateTier tier : PlateTier.values()) {
            items.add(ArmorRegistry.plate(tier));
        }
        HowToTopic topic = null;
        for (HowToTopic candidate : HowToTopics.ALL) {
            if (candidate.id().equals("body_armor")) {
                topic = candidate;
            }
        }
        check(topic != null && topic.tab().equals("equipment"), "Er is geen onderwerp body_armor bij Equipment");
        check(topic.items().get().containsAll(items), "Niet alle armor-items openen Body armor");
        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess());
        for (var tab : ModCreativeTabs.TABS.getEntries()) {
            tab.get().buildContents(parameters);
            for (Item item : items) {
                boolean in = tab.get().getDisplayItems().stream().anyMatch(stack -> stack.is(item));
                check(in == tab.getId().getPath().equals("equipment"), item + " staat " + (in ? "in" : "niet in") + " " + tab.getId().getPath());
            }
        }
        int[] vest = com.senne.imttech.armory.Pegboard.size(new ItemStack(ArmorRegistry.PLATE_CARRIER.get()));
        int[] plate = com.senne.imttech.armory.Pegboard.size(plate(PlateTier.STEEL));
        check(vest != null && vest[0] == 5 && vest[1] == 6, "Een vest is geen 5 x 6 gaatjes op het Pegboard");
        check(plate != null && plate[0] == 3 && plate[1] == 4, "Een plate is geen 3 x 4 gaatjes op het Pegboard");
        helper.succeed();
    }

    /**
     * Een speler met een Armor Workbench voor zich en zijn scherm open (player.containerMenu), met deze spullen in zijn
     * inventaris.
     */
    private static ServerPlayer bench(GameTestHelper helper, String name, ItemStack... items) {
        BlockPos bench = new BlockPos(3, 2, 5);
        helper.setBlock(bench.below(), Blocks.STONE);
        helper.setBlock(bench, ArmorRegistry.ARMOR_WORKBENCH.get().defaultBlockState());
        ServerPlayer player = player(helper, name, new BlockPos(3, 2, 3));
        for (ItemStack item : items) {
            player.getInventory().add(item);
        }
        ArmorWorkbenchBlock.open(player, helper.absolutePos(bench));
        check(player.containerMenu instanceof ArmorWorkbenchMenu, "De werkbank ging niet open");
        return player;
    }

    /** Het nummer van het recept van de werkbank dat dit maakt (zoals het scherm het stuurt), of -1. */
    private static int recipeFor(ServerLevel level, Item result) {
        var all = ArmorRecipe.allArmor(level.getRecipeManager());
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).value().result().is(result)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Hoofdstuk 111 (jouw wens): alles voor de armor maak je op de Armor Workbench, niet meer in de crafting table. Make haalt
     * het materiaal uit je inventaris (een stalen plate: 5 staal en 1 polymeer); zonder genoeg gebeurt er niets. Herstellen
     * (jouw keuze, in plaats van het aambeeld): elk stuk eigen materiaal herstelt een kwart, zoveel als je hebt.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "backpacks")
    public static void armor_workbench_makes_and_repairs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = bench(helper, "bench-maker", new ItemStack(ModItems.STEEL_INGOT.get(), 7),
                new ItemStack(ModItems.POLYMER.get(), 1));
        ArmorWorkbenchMenu menu = (ArmorWorkbenchMenu) player.containerMenu;
        int steel = recipeFor(level, ArmorRegistry.plate(PlateTier.STEEL));
        check(steel >= 0, "De werkbank kent geen recept voor een stalen plate");
        check(menu.clickMenuButton(player, ArmorWorkbenchMenu.MAKE + steel), "Make maakte geen stalen plate");
        check(player.getInventory().countItem(ArmorRegistry.plate(PlateTier.STEEL)) == 1, "De stalen plate zit niet in je inventaris");
        check(player.getInventory().countItem(ModItems.STEEL_INGOT.get()) == 2 && player.getInventory().countItem(ModItems.POLYMER.get()) == 0,
                "Het materiaal ging er niet uit (5 staal en 1 polymeer)");
        check(!menu.clickMenuButton(player, ArmorWorkbenchMenu.MAKE + steel), "Zonder genoeg materiaal maakte hij toch een plate");
        // De crafting table kent de plates en het vest niet meer.
        CraftingInput old = CraftingInput.of(3, 2, List.of(new ItemStack(ModItems.STEEL_INGOT.get()), new ItemStack(ModItems.STEEL_INGOT.get()),
                new ItemStack(ModItems.STEEL_INGOT.get()), new ItemStack(ModItems.STEEL_INGOT.get()), new ItemStack(ModItems.POLYMER.get()),
                new ItemStack(ModItems.STEEL_INGOT.get())));
        check(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, old, level).isEmpty(), "De crafting table maakt nog plates");
        // Herstellen: 600 van de 1000 stapjes versleten = 3 staven nodig; met 2 staven wordt hij voor 2 kwarten heel.
        ItemStack worn = plate(PlateTier.STEEL);
        worn.setDamageValue(600);
        menu.setTab(ArmorWorkbenchMenu.TAB_PLATES);
        menu.getSlot(ArmorWorkbenchMenu.PLATE_SLOT).set(worn);
        check(ArmorWorkbenchMenu.repairCost(worn) == 3, "Een plate op 40 % heeft geen 3 staven nodig");
        check(menu.clickMenuButton(player, ArmorWorkbenchMenu.REPAIR), "Repair deed niets");
        check(menu.plate().getDamageValue() == 600 - 2 * ArmorWorkbenchMenu.REPAIR_PER_MATERIAL,
                "Twee staven herstelden niet twee kwart: nog " + menu.plate().getDamageValue() + " versleten");
        check(player.getInventory().countItem(ModItems.STEEL_INGOT.get()) == 0, "Herstellen nam de staven niet");
        // Sluit je het scherm, dan krijg je de plate uit het vakje terug.
        player.closeContainer();
        check(player.getInventory().countItem(ArmorRegistry.plate(PlateTier.STEEL)) == 2, "De plate uit het vakje kwam niet terug");
        helper.succeed();
    }

    /**
     * Hoofdstuk 111 (jouw wensen): de tekst op je rug pas je alleen op de werkbank aan (tabblad Customize), op maat geknipt (12
     * tekens, geen kleurcodes). Verven kan daar ook (niet meer in de crafting table): de eerste kleur op een nieuw vest is
     * precies die kleur en kost een kleurstof; een kleur die je niet hebt kan niet; bruin + lichtgroen + groen is camo. De
     * plates blijven.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "backpacks")
    public static void armor_workbench_letters_and_dyes(GameTestHelper helper) {
        ServerPlayer player = bench(helper, "bench-painter", new ItemStack(Items.RED_DYE, 2), new ItemStack(Items.BROWN_DYE),
                new ItemStack(Items.LIME_DYE), new ItemStack(Items.GREEN_DYE));
        ArmorWorkbenchMenu menu = (ArmorWorkbenchMenu) player.containerMenu;
        menu.setTab(ArmorWorkbenchMenu.TAB_CUSTOMIZE);
        menu.getSlot(ArmorWorkbenchMenu.VEST_SLOT).set(vest(plate(PlateTier.STEEL), ItemStack.EMPTY));
        menu.setText("\u00a7cSWAT TEAM ALPHA", DyeColor.YELLOW.getId());
        VestData data = VestItem.data(menu.vest());
        check(data.text().equals("cSWAT TEAM A") && data.dye() == DyeColor.YELLOW,
                "De tekst werd \"" + data.text() + "\" in " + data.dye() + " (hoort cSWAT TEAM A in geel)");
        check(menu.clickMenuButton(player, ArmorWorkbenchMenu.DYE + DyeColor.RED.getId()), "Verven met rood lukte niet");
        check(VestItem.colour(menu.vest()) == (DyeColor.RED.getTextureDiffuseColor() & 0xFFFFFF), "Een nieuw vest werd niet precies rood");
        check(player.getInventory().countItem(Items.RED_DYE) == 1, "Verven nam geen kleurstof");
        check(!menu.clickMenuButton(player, ArmorWorkbenchMenu.DYE + DyeColor.BLUE.getId()), "Verven met blauw lukte zonder blauwe kleurstof");
        check(menu.clickMenuButton(player, ArmorWorkbenchMenu.CAMO), "Camo lukte niet");
        check(VestItem.camo(menu.vest()) && player.getInventory().countItem(Items.GREEN_DYE) == 0, "Het vest werd geen camo (of nam niets)");
        check(PlateItem.tierOf(VestItem.data(menu.vest()).front()) == PlateTier.STEEL && VestItem.data(menu.vest()).text().equals("cSWAT TEAM A"),
                "Verven nam de plate of de tekst weg");
        // De crafting table verft het vest niet meer.
        check(!new BackpackDyeRecipe(CraftingBookCategory.MISC).matches(CraftingInput.of(2, 1,
                List.of(new ItemStack(ArmorRegistry.PLATE_CARRIER.get()), new ItemStack(Items.RED_DYE))), helper.getLevel()),
                "De crafting table verft nog een vest");
        player.closeContainer();
        helper.succeed();
    }

    /**
     * Hoofdstuk 111 (gevonden bij de nalezing): op een aambeeld voeg je geen twee plates samen en herstel je er geen met
     * materiaal (dat doe je op de Armor Workbench), maar een naam geven kan nog, zoals elk ander item.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "backpacks")
    public static void plates_get_a_name_but_no_repair_on_an_anvil(GameTestHelper helper) {
        ServerPlayer player = player(helper, "anvil-smith", new BlockPos(3, 2, 3));
        AnvilMenu anvil = new AnvilMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 5))));
        ItemStack worn = plate(PlateTier.STEEL);
        worn.setDamageValue(600);
        ItemStack other = plate(PlateTier.STEEL);
        other.setDamageValue(300);
        anvil.getSlot(0).set(worn.copy());
        anvil.getSlot(1).set(other);
        check(anvil.getSlot(2).getItem().isEmpty(), "Twee plates op een aambeeld werden samen een betere");
        anvil.getSlot(1).set(new ItemStack(ModItems.STEEL_INGOT.get(), 4));
        check(anvil.getSlot(2).getItem().isEmpty(), "Een aambeeld herstelt een plate met staal");
        anvil.getSlot(1).set(ItemStack.EMPTY);
        anvil.setItemName("Lucky");
        ItemStack named = anvil.getSlot(2).getItem();
        check(named.is(ArmorRegistry.plate(PlateTier.STEEL)) && named.getHoverName().getString().equals("Lucky"),
                "Een plate krijgt op een aambeeld geen naam meer");
        check(named.getDamageValue() == 600, "Een naam geven herstelde de plate ook");
        helper.succeed();
    }

    /**
     * Jouw keuze: plates in de buit. De kluis van de bunker geeft in 60 kisten minstens een plate of vest; het recept van de
     * netherite plate (op de Armor Workbench) wil een hele titanium plate (een versleten zou je zo gratis herstellen).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "backpacks")
    public static void plates_in_loot_and_recipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceKey<LootTable> vault = ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "chests/raven_vault"));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(vault);
        int found = 0;
        for (int i = 0; i < 60; i++) {
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                    .create(LootContextParamSets.CHEST);
            for (ItemStack stack : table.getRandomItems(params, 1000L + i)) {
                if (PlateItem.tierOf(stack) != null || VestItem.isVest(stack)) {
                    found++;
                }
            }
        }
        check(found > 0, "In 60 kisten van de kluis zat geen plate en geen vest");
        // De netherite plate (op de werkbank): een hele titanium plate en 2 netherite; een versleten plate telt niet.
        int netherite = recipeFor(level, ArmorRegistry.plate(PlateTier.NETHERITE));
        check(netherite >= 0, "De werkbank kent geen recept voor een netherite plate");
        ArmorRecipe recipe = ArmorRecipe.allArmor(level.getRecipeManager()).get(netherite).value();
        ItemStack titanium = plate(PlateTier.TITANIUM);
        ItemStack damaged = titanium.copy();
        damaged.setDamageValue(300);
        GunsmithRecipeCarried good = new GunsmithRecipeCarried(List.of(titanium, new ItemStack(Items.NETHERITE_INGOT, 2)));
        GunsmithRecipeCarried bad = new GunsmithRecipeCarried(List.of(damaged, new ItemStack(Items.NETHERITE_INGOT, 2)));
        check(recipe.affordable(good.carried()), "Een hele titanium plate en 2 netherite maken geen netherite plate");
        check(!recipe.affordable(bad.carried()), "Een versleten titanium plate werd een nieuwe netherite plate");
        check(recipeFor(level, ArmorRegistry.PLATE_CARRIER.get()) >= 0, "Het vest heeft geen recept op de werkbank");
        helper.succeed();
    }
}
