package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.backpack.BackpackBlock;
import com.senne.imttech.backpack.BackpackBlockEntity;
import com.senne.imttech.backpack.BackpackDyeRecipe;
import com.senne.imttech.backpack.BackpackItem;
import com.senne.imttech.backpack.BackpackMenu;
import com.senne.imttech.backpack.BackpackPlace;
import com.senne.imttech.backpack.BackpackRegistry;
import com.senne.imttech.backpack.BackpackZip;
import com.senne.imttech.backpack.Backpacks;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.registry.ModItems;
import java.util.ArrayList;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de militaire rugzakken (hoofdstuk 72). Jouw keuzes: drie maten (18, 27 en 36 vakjes); openen kost tijd (de
 * rits, stilstaan, en traag lopen met de rugzak open); geen dozen in dozen; een eigen rugvakje; sneak + rechtsklik zet
 * hem neer en dan mag iedereen erin; verven zoals leer, met bruin + lichtgroen + groen = camo; een sorteerknop.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class BackpackGameTests {
    private BackpackGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name, BlockPos at) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, at, name);
        player.getAbilities().instabuild = false;
        player.getInventory().selected = 0;
        return player;
    }

    private static ItemStack pack(BackpackItem.Size size, ItemStack... inside) {
        ItemStack stack = new ItemStack(BackpackRegistry.item(size));
        BackpackItem.setContents(stack, List.of(inside));
        return stack;
    }

    /** Zo lang duurt de rits (uit de config), in ticks. */
    private static int zipTicks() {
        return Math.max(1, Math.round(ImtConfig.Backpacks.ZIP_SECONDS * 20));
    }

    /** Een stevige vloer onder dit vakje (de arena is leeg). */
    private static void floor(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative.below(), Blocks.STONE);
    }

    /**
     * De rits: het scherm gaat niet meteen open, maar pas na {@code zipSeconds}; halverwege staat het nog dicht. Wie
     * tijdens de rits wegloopt, stopt hem. Met de rugzak open loop je trager, en wat je erin legt zit meteen in de rugzak
     * op je rug. Na het sluiten loop je weer gewoon.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "backpacks")
    public static void backpack_zip_takes_time_and_stops_when_you_move(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer player = player(helper, "pack-wearer", new BlockPos(2, 2, 2));
        check(Gear.setBack(player, pack(BackpackItem.Size.ASSAULT)), "De rugzak ging niet op je rug");
        check(Backpacks.openWorn(player), "Openritsen begon niet");
        check(BackpackZip.busy(player), "Er loopt geen rits");
        check(!(player.containerMenu instanceof BackpackMenu), "Het scherm ging meteen open, zonder rits");

        ServerPlayer mover = player(helper, "pack-mover", new BlockPos(6, 2, 6));
        check(Gear.setBack(mover, pack(BackpackItem.Size.PATROL)), "De tweede rugzak ging niet op zijn rug");
        check(Backpacks.openWorn(mover), "De tweede rits begon niet");

        int ticks = zipTicks();
        helper.runAfterDelay(Math.max(1, ticks / 2), () -> {
            check(!(player.containerMenu instanceof BackpackMenu), "Halverwege de rits stond het scherm al open");
            // De tweede speler loopt weg: zijn rits stopt.
            mover.teleportTo(mover.getX() + 1.5, mover.getY(), mover.getZ());
        });
        helper.runAfterDelay(ticks + 3, () -> {
            check(!BackpackZip.busy(mover) && !(mover.containerMenu instanceof BackpackMenu), "De rits van wie wegliep ging toch door");
            check(player.containerMenu instanceof BackpackMenu, "Na de rits stond het scherm niet open");
            BackpackMenu menu = (BackpackMenu) player.containerMenu;
            check(menu.size() == BackpackItem.Size.ASSAULT && menu.slots.size() == 18 + 2 + 36,
                    "Een Assault Pack heeft 18 vakjes en 2 MOLLE-plekken (en je inventaris 36), niet " + (menu.slots.size() - 36));
            menu.getSlot(0).set(new ItemStack(Items.ARROW, 12));
            check(BackpackItem.contents(Gear.back(player)).getFirst().is(Items.ARROW), "Wat je erin legde zit niet in de rugzak op je rug");
        });
        helper.runAfterDelay(ticks + 6, () -> {
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            check(speed != null && speed.hasModifier(Backpacks.SLOW), "Met de rugzak open loop je niet trager");
            player.closeContainer();
        });
        helper.runAfterDelay(ticks + 9, () -> {
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            check(speed != null && !speed.hasModifier(Backpacks.SLOW), "Na het sluiten loop je nog traag");
            helper.succeed();
        });
    }

    /**
     * Neerzetten met sneak + rechtsklik: een blok met de rugzak erin (en alles wat erin zat). Twee spelers die hem open
     * hebben, delen één inhoud: wat de een eruit haalt, is bij de ander ook weg, en ook in de rugzak zelf. Kapot = de
     * rugzak valt eraf met alles erin. Sneak + lege hand pakt een neergezette rugzak op, op je rug als die leeg is.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "backpacks")
    public static void backpack_placed_shared_and_dropped_whole(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spot = new BlockPos(3, 2, 3);
        floor(helper, spot);
        ServerPlayer owner = player(helper, "pack-placer", new BlockPos(3, 2, 6));
        owner.setItemInHand(InteractionHand.MAIN_HAND, pack(BackpackItem.Size.RUCKSACK, new ItemStack(Items.BREAD, 5), new ItemStack(Items.TORCH, 20)));
        owner.setShiftKeyDown(true);
        BlockPos below = helper.absolutePos(spot.below());
        owner.getMainHandItem().useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
        owner.setShiftKeyDown(false);
        BlockPos abs = helper.absolutePos(spot);
        check(level.getBlockState(abs).is(BackpackRegistry.BACKPACK_BLOCK.get()), "Er staat geen rugzak op de grond");
        check(level.getBlockState(abs).getValue(BackpackBlock.SIZE) == BackpackBlock.SizeProperty.RUCKSACK, "Het blok heeft de verkeerde maat");
        check(owner.getMainHandItem().isEmpty(), "De rugzak bleef in je hand");
        BackpackBlockEntity entity = (BackpackBlockEntity) level.getBlockEntity(abs);
        check(entity != null && BackpackItem.contents(entity.pack()).get(1).is(Items.TORCH), "Wat erin zat ging niet mee in het blok");

        // Twee spelers openen hem (de rits slaan we over: die test staat hierboven).
        ServerPlayer other = player(helper, "pack-friend", new BlockPos(4, 2, 5));
        check(BackpackZip.open(owner, BackpackPlace.block(level, abs)), "De eigenaar kon hem niet openen");
        check(BackpackZip.open(other, BackpackPlace.block(level, abs)), "Iemand anders kon hem niet openen (iedereen mag erin)");
        BackpackMenu first = (BackpackMenu) owner.containerMenu;
        BackpackMenu second = (BackpackMenu) other.containerMenu;
        check(first.open() != null && first.open() == second.open(), "Twee spelers hebben niet dezelfde inhoud open");
        check(first.slots.size() == 36 + 4 + 36, "Een Rucksack heeft 36 vakjes en 4 MOLLE-plekken");
        second.getSlot(0).set(ItemStack.EMPTY);
        check(!first.getSlot(0).hasItem(), "Wat de een eruit haalde, ligt bij de ander nog");
        check(BackpackItem.contents(entity.pack()).getFirst().isEmpty(), "Wat eruit ging, zit nog in de rugzak zelf");
        owner.closeContainer();
        other.closeContainer();

        // Kapot: de rugzak valt eraf, met de fakkels erin.
        level.destroyBlock(abs, true);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2),
                item -> BackpackItem.sizeOf(item.getItem()) == BackpackItem.Size.RUCKSACK);
        check(drops.size() == 1, "Er viel geen (of meer dan een) Rucksack: " + drops.size());
        check(BackpackItem.contents(drops.getFirst().getItem()).get(1).is(Items.TORCH), "De fakkels vielen niet mee in de rugzak");

        // Opnieuw neerzetten en met sneak + lege hand oppakken: op je rug.
        ItemStack again = drops.getFirst().getItem().copy();
        drops.getFirst().discard();
        owner.setItemInHand(InteractionHand.MAIN_HAND, again);
        owner.setShiftKeyDown(true);
        owner.getMainHandItem().useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
        check(level.getBlockState(abs).is(BackpackRegistry.BACKPACK_BLOCK.get()), "Opnieuw neerzetten lukte niet");
        level.getBlockState(abs).useWithoutItem(level, owner, new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false));
        owner.setShiftKeyDown(false);
        check(level.getBlockState(abs).isAir(), "Na oppakken staat hij er nog");
        check(BackpackItem.sizeOf(Gear.back(owner)) == BackpackItem.Size.RUCKSACK, "Opgepakt ging hij niet op je (lege) rug");
        check(BackpackItem.contents(Gear.back(owner)).get(1).is(Items.TORCH), "Opgepakt is wat erin zat weg");
        helper.succeed();
    }

    /**
     * Geen dozen in dozen (jouw keuze bij "minder OP"): geen rugzak, shulkerdoos, Ammo Box of bundel in een rugzak; een
     * holster of een gewoon item wel. Ook het scherm laat ze niet toe, ook niet met shift-klikken.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "backpacks")
    public static void backpack_refuses_boxes_in_boxes(GameTestHelper helper) {
        check(!BackpackItem.mayHold(pack(BackpackItem.Size.ASSAULT)), "Een rugzak past in een rugzak");
        check(!BackpackItem.mayHold(new ItemStack(Items.SHULKER_BOX)), "Een shulkerdoos past in een rugzak");
        check(!BackpackItem.mayHold(new ItemStack(Items.RED_SHULKER_BOX)), "Een gekleurde shulkerdoos past in een rugzak");
        ItemStack box = new ItemStack(ModItems.AMMO_BOX.get());
        box.set(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(
                List.of(new ItemStack(ModItems.ROUND_9MM.get(), 64))));
        check(!BackpackItem.mayHold(box), "Een volle Ammo Box past in een rugzak");
        check(!BackpackItem.mayHold(new ItemStack(Items.BUNDLE)), "Een bundel past in een rugzak");
        check(BackpackItem.mayHold(new ItemStack(Items.COBBLESTONE, 64)), "Gewone blokken passen niet");
        check(BackpackItem.mayHold(new ItemStack(ModItems.HOLSTER.get())), "Een lege holster past niet");
        ItemStack fullHolster = new ItemStack(ModItems.HOLSTER.get());
        com.senne.imttech.guns.GearItem.setContents(fullHolster, List.of(com.senne.imttech.guns.FirearmItem.loaded(
                com.senne.imttech.guns.FirearmType.GLOCK_19, com.senne.imttech.guns.GunMaterial.STEEL)));
        check(!BackpackItem.mayHold(fullHolster), "Een holster met een Glock erin past in een rugzak (een volle pouch hoort niet)");

        ServerPlayer player = player(helper, "pack-stuffer", new BlockPos(3, 2, 3));
        player.setItemInHand(InteractionHand.MAIN_HAND, pack(BackpackItem.Size.PATROL));
        player.getInventory().setItem(5, new ItemStack(Items.SHULKER_BOX));
        player.getInventory().setItem(6, pack(BackpackItem.Size.ASSAULT));
        player.getInventory().setItem(7, new ItemStack(Items.IRON_INGOT, 9));
        check(BackpackZip.open(player, BackpackPlace.hand(player, InteractionHand.MAIN_HAND)), "De rugzak in je hand ging niet open");
        BackpackMenu menu = (BackpackMenu) player.containerMenu;
        int pack = menu.inventoryStart();
        // De werkbalk staat achteraan: vakje 5 van je inventaris = slot (rugzak + MOLLE) + 27 + 5.
        check(!menu.getSlot(0).mayPlace(new ItemStack(Items.SHULKER_BOX)), "Het vakje neemt een shulkerdoos aan");
        menu.quickMoveStack(player, pack + 27 + 5);
        menu.quickMoveStack(player, pack + 27 + 6);
        menu.quickMoveStack(player, pack + 27 + 7);
        check(player.getInventory().getItem(5).is(Items.SHULKER_BOX), "Shift-klik stopte de shulkerdoos in de rugzak");
        check(BackpackItem.isBackpack(player.getInventory().getItem(6)), "Shift-klik stopte een rugzak in de rugzak");
        check(player.getInventory().getItem(7).isEmpty() && menu.getSlot(0).getItem().is(Items.IRON_INGOT), "Het ijzer ging niet in de rugzak");
        // Het vakje met de open rugzak (je hand) zit op slot.
        check(!menu.getSlot(pack + 27).mayPickup(player), "Je kon de open rugzak uit je hand pakken");
        player.closeContainer();
        check(BackpackItem.contents(player.getMainHandItem()).getFirst().is(Items.IRON_INGOT), "Het ijzer zit niet in de rugzak in je hand");
        helper.succeed();
    }

    /**
     * Verven (jouw wens): de kleurstoffen van Minecraft mengen zoals leer (de olijfgroene kleur telt mee); precies bruin +
     * lichtgroen + groen = camo, zonder kleur; een camo-rugzak met een kleurstof is weer gewoon die kleur. De inhoud blijft.
     * Het recept is het enige dat past (het verfrecept van leer niet: een rugzak is geen "dyeable").
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "backpacks")
    public static void backpack_dye_mix_and_camo(GameTestHelper helper) {
        BackpackDyeRecipe recipe = new BackpackDyeRecipe(CraftingBookCategory.MISC);
        ItemStack bag = pack(BackpackItem.Size.ASSAULT, new ItemStack(Items.APPLE, 3));

        ItemStack red = recipe.assemble(input(bag, new ItemStack(Items.RED_DYE)), helper.getLevel().registryAccess());
        DyedItemColor colour = red.get(DataComponents.DYED_COLOR);
        check(colour != null && colour.rgb() != BackpackItem.OLIVE, "Rood verven gaf geen kleur");
        int r = (colour.rgb() >> 16) & 0xFF;
        int g = (colour.rgb() >> 8) & 0xFF;
        check(r > g, "Olijfgroen + rood is niet roodachtig: " + Integer.toHexString(colour.rgb()));
        check(BackpackItem.contents(red).getFirst().is(Items.APPLE), "Bij het verven ging de inhoud weg");

        ItemStack camo = recipe.assemble(input(bag, new ItemStack(Items.BROWN_DYE), new ItemStack(Items.LIME_DYE),
                new ItemStack(Items.GREEN_DYE)), helper.getLevel().registryAccess());
        check(BackpackItem.camo(camo), "Bruin + lichtgroen + groen gaf geen camo");
        check(!camo.has(DataComponents.DYED_COLOR), "Een camo-rugzak heeft nog een kleur");

        ItemStack blue = recipe.assemble(input(camo, new ItemStack(Items.BLUE_DYE)), helper.getLevel().registryAccess());
        check(!BackpackItem.camo(blue), "Camo bleef na het verven");
        DyedItemColor blueColour = blue.get(DataComponents.DYED_COLOR);
        check(blueColour != null && (blueColour.rgb() & 0xFFFFFF) == (DyeColor.BLUE.getTextureDiffuseColor() & 0xFFFFFF),
                "Camo + blauw is niet gewoon blauw");

        // Het recept staat in het spel, en het is het enige voor rugzak + kleurstoffen.
        CraftingInput mixed = input(bag, new ItemStack(Items.BROWN_DYE), new ItemStack(Items.LIME_DYE), new ItemStack(Items.GREEN_DYE));
        var found = helper.getLevel().getRecipeManager().getRecipesFor(RecipeType.CRAFTING, mixed, helper.getLevel());
        check(found.size() == 1 && found.getFirst().value() instanceof BackpackDyeRecipe,
                "Er passen " + found.size() + " recepten op een rugzak met kleurstoffen (hoort 1: het verfrecept van de rugzak)");
        check(!recipe.matches(input(bag, new ItemStack(Items.STICK)), helper.getLevel()), "Een stok is geen kleurstof");
        helper.succeed();
    }

    private static CraftingInput input(ItemStack... stacks) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            items.add(i < stacks.length ? stacks[i].copy() : ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, items);
    }

    /** De sorteerknop: gelijke stapels samen, alles op een rij (op soort), lege vakjes achteraan. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "backpacks")
    public static void backpack_sort_merges_and_lines_up(GameTestHelper helper) {
        ServerPlayer player = player(helper, "pack-sorter", new BlockPos(3, 2, 3));
        ItemStack bag = pack(BackpackItem.Size.ASSAULT);
        List<ItemStack> mess = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            mess.add(ItemStack.EMPTY);
        }
        mess.set(3, new ItemStack(Items.TORCH, 40));
        mess.set(7, new ItemStack(Items.APPLE, 5));
        mess.set(11, new ItemStack(Items.TORCH, 40));
        mess.set(17, new ItemStack(Items.APPLE, 2));
        BackpackItem.setContents(bag, mess);
        check(Gear.setBack(player, bag), "De rugzak ging niet op je rug");
        check(BackpackZip.open(player, BackpackPlace.worn(player)), "De rugzak op je rug ging niet open");
        Backpacks.sort(player);
        List<ItemStack> sorted = BackpackItem.contents(Gear.back(player));
        check(sorted.get(0).is(Items.APPLE) && sorted.get(0).getCount() == 7, "De appels zijn niet samengevoegd vooraan");
        check(sorted.get(1).is(Items.TORCH) && sorted.get(1).getCount() == 64, "De fakkels zijn niet samengevoegd (64)");
        check(sorted.get(2).is(Items.TORCH) && sorted.get(2).getCount() == 16, "De rest van de fakkels staat er niet achter");
        for (int i = 3; i < sorted.size(); i++) {
            check(sorted.get(i).isEmpty(), "Na het sorteren staat er nog iets in vakje " + i);
        }
        player.closeContainer();
        helper.succeed();
    }

    /**
     * Het rugvakje (jouw keuze: een eigen vakje naast je harnas): alleen een rugzak past erin; een rugzak die je op je rug
     * opent en die iemand afpakt (of die je zelf afdoet), sluit het scherm, zodat er niets dubbel komt.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "backpacks")
    public static void backpack_back_slot_and_screen_closes_when_pack_leaves(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer player = player(helper, "pack-slot", new BlockPos(3, 2, 3));
        // Klikken op het rugvakje (index BACK) met een steen aan je muis: past niet.
        player.inventoryMenu.setCarried(new ItemStack(Items.STONE));
        Gear.click(player, Gear.BACK);
        check(Gear.back(player).isEmpty() && player.inventoryMenu.getCarried().is(Items.STONE), "Een steen ging in het rugvakje");
        player.inventoryMenu.setCarried(pack(BackpackItem.Size.PATROL, new ItemStack(Items.GOLD_INGOT, 3)));
        Gear.click(player, Gear.BACK);
        check(BackpackItem.sizeOf(Gear.back(player)) == BackpackItem.Size.PATROL && player.inventoryMenu.getCarried().isEmpty(),
                "De Patrol Pack ging niet in het rugvakje");

        check(BackpackZip.open(player, BackpackPlace.worn(player)), "De rugzak op je rug ging niet open");
        check(player.containerMenu instanceof BackpackMenu, "Het scherm staat niet open");
        // De rugzak verdwijnt van je rug (bijvoorbeeld afgedaan): het scherm hoort dicht te gaan.
        ItemStack taken = Gear.back(player);
        Gear.setBack(player, ItemStack.EMPTY);
        helper.runAfterDelay(2, () -> {
            check(!(player.containerMenu instanceof BackpackMenu), "Het scherm bleef open terwijl de rugzak weg is");
            check(BackpackItem.contents(taken).getFirst().is(Items.GOLD_INGOT), "De inhoud van de afgedane rugzak klopt niet");
            helper.succeed();
        });
    }

    // ================================================================ Stap 2: de MOLLE-pouches

    private static ItemStack pouch(com.senne.imttech.backpack.MolleItem.Kind kind, ItemStack... inside) {
        ItemStack stack = new ItemStack(BackpackRegistry.molle(kind));
        com.senne.imttech.backpack.MolleItem.setContents(stack, List.of(inside));
        return stack;
    }

    /** Een rugzak met deze pouches op zijn MOLLE-plekken. */
    private static ItemStack packWith(BackpackItem.Size size, ItemStack... pouches) {
        ItemStack pack = pack(size);
        BackpackItem.setMolle(pack, List.of(pouches));
        return pack;
    }

    /**
     * Een pouch hang je aan de rugzak in zijn scherm: shift-klik zet hem op een vrije MOLLE-plek, met wat erin zat. Een
     * volle pouch mag niet in de rugzak zelf (wel aan de buitenkant); een gewoon item niet op een MOLLE-plek.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "backpacks")
    public static void molle_pouch_hangs_on_the_pack(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer player = player(helper, "molle-hanger", new BlockPos(3, 2, 3));
        check(Gear.setBack(player, pack(BackpackItem.Size.PATROL)), "De rugzak ging niet op je rug");
        ItemStack utility = pouch(com.senne.imttech.backpack.MolleItem.Kind.UTILITY, new ItemStack(Items.TORCH, 16), new ItemStack(Items.SHEARS));
        player.getInventory().setItem(4, utility);
        check(BackpackZip.open(player, BackpackPlace.worn(player)), "De rugzak ging niet open");
        BackpackMenu menu = (BackpackMenu) player.containerMenu;
        check(!menu.getSlot(0).mayPlace(utility), "Een volle utility pouch past in de rugzak zelf");
        check(!menu.getSlot(menu.size().slots()).mayPlace(new ItemStack(Items.TORCH)), "Een fakkel past op een MOLLE-plek");
        menu.quickMoveStack(player, menu.inventoryStart() + 27 + 4);
        check(player.getInventory().getItem(4).isEmpty(), "Shift-klik hing de pouch niet aan de rugzak");
        List<ItemStack> molle = BackpackItem.molle(Gear.back(player));
        check(com.senne.imttech.backpack.MolleItem.kindOf(molle.get(0)) == com.senne.imttech.backpack.MolleItem.Kind.UTILITY,
                "De utility pouch hangt niet op de eerste MOLLE-plek");
        check(com.senne.imttech.backpack.MolleItem.contents(molle.get(0)).get(1).is(Items.SHEARS), "Wat in de pouch zat is weg");
        player.closeContainer();
        helper.succeed();
    }

    /**
     * Een pouch op je rug open je zonder rits (uit het wiel): het scherm staat meteen open, en wat je erin legt zit in de
     * pouch op je rugzak.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "backpacks")
    public static void molle_pouch_opens_without_zip(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer player = player(helper, "molle-opener", new BlockPos(3, 2, 3));
        check(Gear.setBack(player, packWith(BackpackItem.Size.ASSAULT, ItemStack.EMPTY,
                pouch(com.senne.imttech.backpack.MolleItem.Kind.UTILITY))), "De rugzak ging niet op je rug");
        check(!com.senne.imttech.backpack.Molle.openWornPouch(player, 0), "Een lege MOLLE-plek ging open");
        check(com.senne.imttech.backpack.Molle.openWornPouch(player, 1), "De utility pouch ging niet open");
        check(player.containerMenu instanceof com.senne.imttech.backpack.MolleMenu, "Het scherm van de pouch staat niet meteen open");
        check(!BackpackZip.busy(player), "Er liep toch een rits");
        player.containerMenu.getSlot(2).set(new ItemStack(Items.COMPASS));
        check(com.senne.imttech.backpack.MolleItem.contents(BackpackItem.molle(Gear.back(player)).get(1)).get(2).is(Items.COMPASS),
                "Het kompas zit niet in de pouch op je rugzak");
        player.closeContainer();
        helper.succeed();
    }

    /**
     * R zonder heup-pouch: het magazijn komt uit de MOLLE-magazijnpouch (het rijtje begint dan bij de MOLLE-pouch), en het
     * oude magazijn komt op zijn plek. Uitwerpen zonder kiezen: in een vrij vakje van de MOLLE-pouch.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "backpacks")
    public static void molle_mag_pouch_feeds_reload(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        float jam = ImtConfig.Guns.JAM_CHANCE;
        float misfire = ImtConfig.Guns.MISFIRE_CHANCE;
        ImtConfig.Guns.JAM_CHANCE = 0;
        ImtConfig.Guns.MISFIRE_CHANCE = 0;
        try {
            ServerPlayer player = player(helper, "molle-reloader", new BlockPos(3, 2, 3));
            ItemStack gun = com.senne.imttech.guns.FirearmItem.loaded(com.senne.imttech.guns.FirearmType.GLOCK_19,
                    com.senne.imttech.guns.GunMaterial.STEEL);
            com.senne.imttech.guns.FirearmItem.setMagazine(gun, com.senne.imttech.guns.MagazineItem.of(com.senne.imttech.guns.FirearmType.GLOCK_19, 2));
            player.setItemInHand(InteractionHand.MAIN_HAND, gun);
            check(Gear.setBack(player, packWith(BackpackItem.Size.ASSAULT, pouch(com.senne.imttech.backpack.MolleItem.Kind.MAG,
                    com.senne.imttech.guns.MagazineItem.of(com.senne.imttech.guns.FirearmType.GLOCK_19, 15)))), "De rugzak ging niet op je rug");
            check(com.senne.imttech.backpack.Molle.magazines(player).size() == 3, "Het rijtje magazijnen is niet de MOLLE-pouch (3 vakjes)");
            check(Gear.reloadFromPouch(player, 0), "Herladen uit de MOLLE-pouch begon niet");
            com.senne.imttech.guns.Firearms.finishNow(player);
            check(com.senne.imttech.guns.FirearmItem.roundsInMagazine(player.getMainHandItem()) == 15,
                    "Het magazijn uit de MOLLE-pouch zit niet in het pistool");
            List<ItemStack> inside = com.senne.imttech.backpack.MolleItem.contents(BackpackItem.molle(Gear.back(player)).getFirst());
            check(com.senne.imttech.guns.MagazineItem.rounds(inside.getFirst()) == 2, "Het oude magazijn (2) kwam niet in de MOLLE-pouch");
            check(com.senne.imttech.guns.Firearms.request(player, com.senne.imttech.guns.Firearms.ActionKind.EJECT_MAG, 0), "Uitwerpen begon niet");
            com.senne.imttech.guns.Firearms.finishNow(player);
            inside = com.senne.imttech.backpack.MolleItem.contents(BackpackItem.molle(Gear.back(player)).getFirst());
            check(com.senne.imttech.guns.MagazineItem.rounds(inside.get(1)) == 15, "Het uitgeworpen magazijn kwam niet in de MOLLE-pouch");
        } finally {
            ImtConfig.Guns.JAM_CHANCE = jam;
            ImtConfig.Guns.MISFIRE_CHANCE = misfire;
        }
        helper.succeed();
    }

    /** Zonder granaatpouch: trekken uit de MOLLE-granaatpouch, en na het gooien komt de volgende vanzelf in je hand. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "backpacks")
    public static void molle_grenade_pouch_draws_and_feeds(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer player = player(helper, "molle-thrower", new BlockPos(3, 2, 3));
        check(Gear.setBack(player, packWith(BackpackItem.Size.ASSAULT, pouch(com.senne.imttech.backpack.MolleItem.Kind.GRENADE,
                new ItemStack(ModItems.EMP_GRENADE.get()), new ItemStack(ModItems.EMP_GRENADE.get())))), "De rugzak ging niet op je rug");
        check(Gear.drawGrenade(player, com.senne.imttech.weapons.GrenadeItem.Kind.EMP.ordinal()), "Trekken uit de MOLLE-granaatpouch lukte niet");
        check(player.getMainHandItem().is(ModItems.EMP_GRENADE.get()), "De granaat kwam niet in je hand");
        ModItems.EMP_GRENADE.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        // De gegooide granaat meteen weg: anders gaat hij na zijn lont af bij een andere test (een EMP stoort drones).
        helper.getLevel().getEntitiesOfClass(com.senne.imttech.weapons.GrenadeEntity.class, player.getBoundingBox().inflate(6))
                .forEach(net.minecraft.world.entity.Entity::discard);
        check(player.getMainHandItem().is(ModItems.EMP_GRENADE.get()), "De volgende granaat kwam niet uit de MOLLE-pouch");
        check(com.senne.imttech.backpack.MolleItem.filled(BackpackItem.molle(Gear.back(player)).getFirst()) == 0,
                "De MOLLE-granaatpouch hoort nu leeg te zijn");
        helper.succeed();
    }

    /** De dump pouch raapt de hulzen rond je voeten op (en niet die verder weg). */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "backpacks")
    public static void dump_pouch_picks_up_casings(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer player = player(helper, "molle-dumper", new BlockPos(3, 2, 3));
        check(Gear.setBack(player, packWith(BackpackItem.Size.ASSAULT, pouch(com.senne.imttech.backpack.MolleItem.Kind.DUMP))),
                "De rugzak ging niet op je rug");
        List<com.senne.imttech.guns.CasingEntity> near = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            com.senne.imttech.guns.CasingEntity casing = new com.senne.imttech.guns.CasingEntity(
                    com.senne.imttech.registry.ModEntities.CASING.get(), helper.getLevel());
            casing.setPos(player.getX() + 0.6 + i * 0.3, player.getY() + 0.1, player.getZ() + 0.4);
            helper.getLevel().addFreshEntity(casing);
            near.add(casing);
        }
        com.senne.imttech.guns.CasingEntity far = new com.senne.imttech.guns.CasingEntity(
                com.senne.imttech.registry.ModEntities.CASING.get(), helper.getLevel());
        far.setPos(player.getX() + 9, player.getY() + 0.1, player.getZ());
        helper.getLevel().addFreshEntity(far);
        helper.runAfterDelay(10, () -> {
            check(near.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved), "De hulzen bij je voeten liggen er nog");
            check(!far.isRemoved(), "Een huls 9 blokken verder werd ook opgeraapt");
            ItemStack dump = BackpackItem.molle(Gear.back(player)).getFirst();
            int casings = 0;
            for (ItemStack stack : com.senne.imttech.backpack.MolleItem.contents(dump)) {
                if (stack.is(ModItems.CASING_9MM.get())) {
                    casings += stack.getCount();
                }
            }
            check(casings == 3, "De dump pouch heeft " + casings + " hulzen, niet 3");
            far.discard();
            helper.succeed();
        });
    }

    /** De IFAK: een gouden appel uit het wiel geneest meteen (absorptie), en daarna even wachten. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "backpacks")
    public static void ifak_heals_and_then_waits(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer player = player(helper, "molle-medic", new BlockPos(3, 2, 3));
        player.setHealth(8);
        check(Gear.setBack(player, packWith(BackpackItem.Size.ASSAULT, ItemStack.EMPTY,
                pouch(com.senne.imttech.backpack.MolleItem.Kind.IFAK, new ItemStack(Items.GOLDEN_APPLE, 2)))), "De rugzak ging niet op je rug");
        check(com.senne.imttech.backpack.Molle.useIfak(player, 1, 0), "De IFAK werd niet gebruikt");
        check(player.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION)
                || player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), "De gouden appel gaf geen effect");
        ItemStack ifak = BackpackItem.molle(Gear.back(player)).get(1);
        check(com.senne.imttech.backpack.MolleItem.contents(ifak).getFirst().getCount() == 1, "Er ging niet precies 1 appel uit de IFAK");
        check(!com.senne.imttech.backpack.Molle.useIfak(player, 1, 0), "Meteen nog eens de IFAK gebruiken lukte (er hoort even wachten)");
        check(!com.senne.imttech.backpack.MolleItem.Kind.IFAK.accepts(new ItemStack(Items.BREAD)), "Brood past in een IFAK");
        helper.succeed();
    }

    // ================================================================ Stap 3: van achteren openen

    /** Zet {@code who} op deze plek, kijkend naar {@code yaw} (0 = zuid, +z). */
    private static void place(GameTestHelper helper, ServerPlayer who, double x, double z, float yaw) {
        Vec3 at = helper.absoluteVec(new Vec3(x, 2, z));
        who.teleportTo(at.x, at.y, at.z);
        who.setYRot(yaw);
        who.yRotO = yaw;
        who.setYHeadRot(yaw);
        who.setXRot(0);
        who.xRotO = 0;
    }

    /** Een slachtoffer met een volle Patrol Pack, kijkend naar het zuiden, en een dief (sluipend, lege hand). */
    private static ServerPlayer[] victimAndThief(GameTestHelper helper, String name) {
        ServerPlayer victim = player(helper, name + "-victim", new BlockPos(3, 2, 4));
        ServerPlayer thief = player(helper, name + "-thief", new BlockPos(3, 2, 2));
        check(Gear.setBack(victim, pack(BackpackItem.Size.PATROL, new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.BREAD, 10))),
                "De rugzak ging niet op de rug van het slachtoffer");
        place(helper, victim, 3.5, 4.5, 0);
        place(helper, thief, 3.5, 3.3, 0);
        thief.setShiftKeyDown(true);
        return new ServerPlayer[]{victim, thief};
    }

    /**
     * Van achteren: klik op zijn rugzak (de bovenste helft van zijn lijf; lager is zakkenrollen), dan de rits, en pas daarna
     * het scherm (met de meter). Wat je eruit haalt is weg uit zijn rugzak. Van voren lukt het niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "backpacks_theft")
    public static void backpack_theft_from_behind_after_the_zip(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer[] both = victimAndThief(helper, "theft-a");
        ServerPlayer victim = both[0];
        ServerPlayer thief = both[1];
        check(!com.senne.imttech.backpack.BackpackTheft.aimsAtPack(thief, victim, new Vec3(0, 0.8, -0.3)),
                "Een klik op zijn heupen telt als de rugzak (daar is het zakkenrollen)");
        // Van voren: niet.
        place(helper, thief, 3.5, 5.7, 180);
        check(!com.senne.imttech.backpack.BackpackTheft.tryStart(thief, victim), "Van voren ging de rugzak open");
        // Van achteren, met de echte klik (op zijn rug, met de plek op zijn lijf): de rits begint.
        place(helper, thief, 3.5, 3.3, 0);
        net.neoforged.neoforge.common.CommonHooks.onInteractEntityAt(thief, victim, new Vec3(0, 1.4, -0.3), InteractionHand.MAIN_HAND);
        check(BackpackZip.busy(thief), "Van achteren begon de rits niet");
        check(!(thief.containerMenu instanceof BackpackMenu), "Het scherm ging meteen open, zonder rits");
        helper.runAfterDelay(zipTicks() + 3, () -> {
            check(thief.containerMenu instanceof BackpackMenu menu && menu.theft(), "Na de rits stond het scherm van de dief niet open");
            BackpackMenu menu = (BackpackMenu) thief.containerMenu;
            menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, thief);
            check(menu.getCarried().is(Items.DIAMOND), "De dief heeft de diamanten niet vast");
            check(BackpackItem.contents(Gear.back(victim)).getFirst().isEmpty(), "De diamanten zitten nog in de rugzak van het slachtoffer");
            check(com.senne.imttech.backpack.BackpackTheft.meter(thief) > 0, "De meter liep niet op");
            menu.clicked(menu.inventoryStart(), 0, net.minecraft.world.inventory.ClickType.PICKUP, thief);
            thief.closeContainer();
            check(thief.getInventory().countItem(Items.DIAMOND) == 5, "De diamanten zitten niet in de inventaris van de dief");
            check(!thief.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "Eén greep was al betrapt");
            helper.succeed();
        });
    }

    /** Te veel grijpen: de meter loopt vol, het slachtoffer hoort het, de dief licht op, en hij moet even wachten. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "backpacks_theft")
    public static void backpack_theft_noise_gets_you_caught(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        float zip = ImtConfig.Backpacks.ZIP_SECONDS;
        ImtConfig.Backpacks.ZIP_SECONDS = 0;
        try {
            ServerPlayer[] both = victimAndThief(helper, "theft-b");
            ServerPlayer victim = both[0];
            ServerPlayer thief = both[1];
            check(com.senne.imttech.backpack.BackpackTheft.tryStart(thief, victim), "Van achteren lukte het niet");
            check(thief.containerMenu instanceof BackpackMenu menu && menu.theft(), "Het scherm van de dief staat niet open");
            BackpackMenu menu = (BackpackMenu) thief.containerMenu;
            for (int i = 0; i < 8 && thief.containerMenu == menu; i++) {
                menu.clicked(2 + i, 0, net.minecraft.world.inventory.ClickType.PICKUP, thief);
            }
            check(!(thief.containerMenu instanceof BackpackMenu), "Na een volle meter staat het scherm nog open");
            check(thief.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "De betrapte dief licht niet op");
            check(!com.senne.imttech.backpack.BackpackTheft.tryStart(thief, victim), "Meteen opnieuw mocht (er hoort een wachttijd)");
        } finally {
            ImtConfig.Backpacks.ZIP_SECONDS = zip;
        }
        helper.succeed();
    }

    /** Het slachtoffer draait zich om en ziet de dief: betrapt (ook met een lege meter). */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "backpacks_theft")
    public static void backpack_theft_turning_around_catches_the_thief(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        float zip = ImtConfig.Backpacks.ZIP_SECONDS;
        ImtConfig.Backpacks.ZIP_SECONDS = 0;
        ServerPlayer[] both;
        try {
            both = victimAndThief(helper, "theft-c");
            check(com.senne.imttech.backpack.BackpackTheft.tryStart(both[1], both[0]), "Van achteren lukte het niet");
        } finally {
            ImtConfig.Backpacks.ZIP_SECONDS = zip;
        }
        ServerPlayer victim = both[0];
        ServerPlayer thief = both[1];
        place(helper, victim, 3.5, 4.5, 180);
        helper.runAfterDelay(3, () -> {
            check(!(thief.containerMenu instanceof BackpackMenu), "Het slachtoffer zag de dief, maar het scherm staat nog open");
            check(thief.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "Gezien = betrapt, maar de dief licht niet op");
            helper.succeed();
        });
    }

    /** Het slachtoffer loopt weg: het scherm van de dief gaat dicht, zonder betrapt. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "backpacks_theft")
    public static void backpack_theft_victim_walks_away(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        float zip = ImtConfig.Backpacks.ZIP_SECONDS;
        ImtConfig.Backpacks.ZIP_SECONDS = 0;
        ServerPlayer[] both;
        try {
            both = victimAndThief(helper, "theft-d");
            check(com.senne.imttech.backpack.BackpackTheft.tryStart(both[1], both[0]), "Van achteren lukte het niet");
        } finally {
            ImtConfig.Backpacks.ZIP_SECONDS = zip;
        }
        ServerPlayer victim = both[0];
        ServerPlayer thief = both[1];
        place(helper, victim, 3.5, 10.5, 0);
        helper.runAfterDelay(3, () -> {
            check(!(thief.containerMenu instanceof BackpackMenu), "Het slachtoffer liep weg, maar het scherm staat nog open");
            check(!thief.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "Weglopen is geen betrapt");
            helper.succeed();
        });
    }

    // ================================================================ Stap 4: het lichaam van een speler

    /** Het lichaam bij deze speler (binnen 4 blokken), of null. */
    private static com.senne.imttech.body.PlayerBodyEntity bodyNear(GameTestHelper helper, ServerPlayer player) {
        List<com.senne.imttech.body.PlayerBodyEntity> bodies = helper.getLevel().getEntitiesOfClass(
                com.senne.imttech.body.PlayerBodyEntity.class, player.getBoundingBox().inflate(4));
        return bodies.isEmpty() ? null : bodies.getFirst();
    }

    /** Een lichaam van {@code owner} op deze plek, met dit erin (vakje, item, vakje, item, ...). */
    private static com.senne.imttech.body.PlayerBodyEntity body(GameTestHelper helper, ServerPlayer owner, BlockPos at, Object... slotsAndItems) {
        com.senne.imttech.body.PlayerBodyEntity body = BackpackRegistry.PLAYER_BODY.get().create(helper.getLevel());
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        body.moveTo(pos.x, pos.y, pos.z, 0, 0);
        body.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        for (int i = 0; i + 1 < slotsAndItems.length; i += 2) {
            body.loot().setItem((Integer) slotsAndItems[i], (ItemStack) slotsAndItems[i + 1]);
        }
        helper.getLevel().addFreshEntity(body);
        return body;
    }

    /**
     * Doodgaan zonder keepInventory: een lichaam met alles op zijn plek (werkbalk, inventaris, harnas, andere hand, holster),
     * de rugzak apart op zijn rug; een zwaard met de vloek van verdwijnen verdwijnt, en er valt niets op de grond.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "bodies")
    public static void player_death_leaves_a_body_with_everything(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        var keep = helper.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        boolean kept = keep.get();
        keep.set(false, helper.getLevel().getServer());
        try {
            floor(helper, new BlockPos(3, 2, 3));
            ServerPlayer player = player(helper, "body-dier", new BlockPos(3, 2, 3));
            player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
            player.getInventory().setItem(20, new ItemStack(Items.DIAMOND, 7));
            ItemStack cursed = new ItemStack(Items.GOLDEN_SWORD);
            cursed.enchant(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                    .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.VANISHING_CURSE), 1);
            player.getInventory().setItem(5, cursed);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            ItemStack holster = new ItemStack(ModItems.HOLSTER.get());
            com.senne.imttech.guns.GearItem.setContents(holster, List.of(com.senne.imttech.guns.FirearmItem.loaded(
                    com.senne.imttech.guns.FirearmType.GLOCK_19, com.senne.imttech.guns.GunMaterial.STEEL)));
            Gear.set(player, com.senne.imttech.guns.GearItem.Kind.HOLSTER, holster);
            Gear.setBack(player, pack(BackpackItem.Size.ASSAULT, new ItemStack(Items.GOLD_INGOT, 3)));
            player.hurt(player.damageSources().fellOutOfWorld(), 1000);
            check(!player.isAlive(), "De speler ging niet dood");
            com.senne.imttech.body.PlayerBodyEntity body = bodyNear(helper, player);
            check(body != null, "Er ligt geen lichaam");
            check(body.isOwner(player) && body.ownerName().equals("body-dier"), "Het lichaam is niet van de speler");
            check(body.loot().getItem(0).is(Items.IRON_SWORD), "Het zwaard ligt niet in vakje 0 (de werkbalk)");
            check(body.loot().getItem(20).is(Items.DIAMOND) && body.loot().getItem(20).getCount() == 7, "De diamanten liggen niet in vakje 20");
            check(body.loot().getItem(5).isEmpty(), "Het zwaard met de vloek van verdwijnen zit in het lichaam");
            check(body.loot().getItem(com.senne.imttech.body.PlayerBodyEntity.ARMOR + 2).is(Items.IRON_CHESTPLATE), "Het harnas ligt niet op zijn plek");
            check(body.loot().getItem(com.senne.imttech.body.PlayerBodyEntity.OFFHAND).is(Items.SHIELD), "Het schild ligt niet op zijn plek");
            check(com.senne.imttech.guns.GearItem.kindOf(body.loot().getItem(com.senne.imttech.body.PlayerBodyEntity.GEAR))
                    == com.senne.imttech.guns.GearItem.Kind.HOLSTER, "De holster ligt niet op zijn plek");
            check(BackpackItem.sizeOf(body.pack()) == BackpackItem.Size.ASSAULT
                    && BackpackItem.contents(body.pack()).getFirst().is(Items.GOLD_INGOT), "De rugzak ligt niet (met inhoud) op zijn rug");
            check(Gear.back(player).isEmpty() && player.getInventory().isEmpty(), "De dode speler heeft nog spullen");
            check(helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(3)).isEmpty(), "Er viel toch iets op de grond");
            body.discard();
            // De dode testspeler weg (anders mikt een raiddrone van een test ernaast nog op hem).
            helper.getLevel().getServer().getPlayerList().remove(player);
        } finally {
            keep.set(kept, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    /** Met keepInventory geen lichaam: je houdt alles, zoals altijd. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "bodies")
    public static void keep_inventory_leaves_no_body(GameTestHelper helper) {
        var keep = helper.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        boolean kept = keep.get();
        keep.set(true, helper.getLevel().getServer());
        try {
            ServerPlayer player = player(helper, "body-keeper", new BlockPos(3, 2, 3));
            player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
            player.hurt(player.damageSources().fellOutOfWorld(), 1000);
            check(bodyNear(helper, player) == null, "Met keepInventory kwam er toch een lichaam");
            helper.getLevel().getServer().getPlayerList().remove(player);
        } finally {
            keep.set(kept, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    /**
     * "Everything back" (de eigenaar): alles terug op zijn plek (borstplaat aan, holster om, rugzak op je rug, de diamanten
     * in vakje 20); vakje 0 en je hoofd (de bril) zijn bezet, dus het zwaard en de helm blijven in het lichaam. Een ander mag
     * de knop niet gebruiken.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "bodies")
    public static void body_everything_back_restores_slots(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer owner = player(helper, "body-owner", new BlockPos(3, 2, 3));
        ServerPlayer stranger = player(helper, "body-stranger", new BlockPos(5, 2, 3));
        com.senne.imttech.body.PlayerBodyEntity body = body(helper, owner, new BlockPos(3, 2, 4),
                0, new ItemStack(Items.IRON_SWORD), 20, new ItemStack(Items.DIAMOND, 7),
                com.senne.imttech.body.PlayerBodyEntity.ARMOR + 2, new ItemStack(Items.IRON_CHESTPLATE),
                com.senne.imttech.body.PlayerBodyEntity.ARMOR + 3, new ItemStack(Items.IRON_HELMET),
                com.senne.imttech.body.PlayerBodyEntity.GEAR, new ItemStack(ModItems.HOLSTER.get()),
                com.senne.imttech.body.PlayerBodyEntity.EXTRA, new ItemStack(Items.ARROW, 16));
        body.setPack(pack(BackpackItem.Size.PATROL));
        owner.getInventory().setItem(0, new ItemStack(Items.STONE));
        check(!com.senne.imttech.body.PlayerBodies.startRestore(stranger, body), "Een ander kon \"Everything back\" gebruiken");
        com.senne.imttech.body.PlayerBodies.restore(owner, body);
        check(owner.getInventory().getItem(20).is(Items.DIAMOND), "De diamanten kwamen niet terug in vakje 20");
        check(owner.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "De borstplaat ging niet terug aan");
        // De testspeler draagt de I.M.T-bril op zijn hoofd: daar past de helm niet, dus die blijft in het lichaam.
        check(body.loot().getItem(com.senne.imttech.body.PlayerBodyEntity.ARMOR + 3).is(Items.IRON_HELMET),
                "Je hoofd was bezet (de bril), maar de helm verdween uit het lichaam");
        check(com.senne.imttech.guns.GearItem.kindOf(Gear.get(owner, com.senne.imttech.guns.GearItem.Kind.HOLSTER))
                == com.senne.imttech.guns.GearItem.Kind.HOLSTER, "De holster ging niet terug om");
        check(BackpackItem.sizeOf(Gear.back(owner)) == BackpackItem.Size.PATROL && !body.hasPack(), "De rugzak ging niet terug op je rug");
        check(owner.getInventory().countItem(Items.ARROW) == 16, "De pijlen (van een andere mod) kwamen niet in je inventaris");
        check(owner.getInventory().getItem(0).is(Items.STONE) && body.loot().getItem(0).is(Items.IRON_SWORD),
                "Vakje 0 was bezet: het zwaard hoort in het lichaam te blijven");
        helper.succeed();
    }

    /**
     * De rugzak van een lichaam: rechtsklik op de rugzak ritst hem open (iedereen mag erin); sneak + rechtsklik haalt hem
     * eraf (3 s), op je rug. Een klik lager (zijn benen) is het lichaam, niet de rugzak.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "bodies")
    public static void body_backpack_zip_and_take(GameTestHelper helper) {
        check(!Gear.curios(), "Deze test is voor zonder Curios");
        ServerPlayer owner = player(helper, "body-pack-owner", new BlockPos(1, 2, 1));
        ServerPlayer finder = player(helper, "body-finder", new BlockPos(3, 2, 2));
        com.senne.imttech.body.PlayerBodyEntity body = body(helper, owner, new BlockPos(3, 2, 4));
        body.setPack(pack(BackpackItem.Size.RUCKSACK, new ItemStack(Items.EMERALD, 4)));
        check(body.onPack(new Vec3(0, 0.4, 0.2)), "Een klik op zijn rug telt niet als de rugzak");
        check(!body.onPack(new Vec3(0, 0.4, -0.55)), "Een klik op zijn benen telt als de rugzak");
        // Rechtsklik op de rugzak (zoals Minecraft het stuurt, met de plek): de rits.
        body.interactAt(finder, new Vec3(0, 0.4, 0.2), InteractionHand.MAIN_HAND);
        check(BackpackZip.busy(finder), "Rechtsklik op de rugzak begon geen rits");
        helper.runAfterDelay(zipTicks() + 3, () -> {
            check(finder.containerMenu instanceof BackpackMenu, "Na de rits staat de rugzak van het lichaam niet open");
            BackpackMenu menu = (BackpackMenu) finder.containerMenu;
            menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, finder);
            check(finder.getInventory().countItem(Items.EMERALD) == 4, "De smaragden kwamen niet uit de rugzak van het lichaam");
            check(BackpackItem.contents(body.pack()).getFirst().isEmpty(), "De smaragden zitten nog in de rugzak van het lichaam");
            finder.closeContainer();
            finder.setShiftKeyDown(true);
            body.interactAt(finder, new Vec3(0, 0.4, 0.2), InteractionHand.MAIN_HAND);
            finder.setShiftKeyDown(false);
            check(BackpackZip.busy(finder), "Sneak + rechtsklik op de rugzak begon niet (3 s eraf halen)");
        });
        int take = zipTicks() + 3 + Math.round(ImtConfig.Backpacks.BODY_TAKE_PACK_SECONDS * 20) + 4;
        helper.runAfterDelay(take, () -> {
            check(!body.hasPack(), "De rugzak ligt nog op het lichaam");
            check(BackpackItem.sizeOf(Gear.back(finder)) == BackpackItem.Size.RUCKSACK, "De rugzak van het lichaam is niet op je (lege) rug");
            body.discard();
            helper.succeed();
        });
    }

    /** Doorzoeken: eruit halen mag, erin leggen niet; en een leeg lichaam verdwijnt vanzelf. */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "bodies")
    public static void empty_body_disappears(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "body-empty-owner", new BlockPos(1, 2, 1));
        ServerPlayer looter = player(helper, "body-looter", new BlockPos(3, 2, 2));
        com.senne.imttech.body.PlayerBodyEntity body = body(helper, owner, new BlockPos(3, 2, 4), 9, new ItemStack(Items.APPLE, 3));
        com.senne.imttech.body.BodyMenu.open(looter, body);
        check(looter.containerMenu instanceof com.senne.imttech.body.BodyMenu, "Het lichaam ging niet open");
        com.senne.imttech.body.BodyMenu menu = (com.senne.imttech.body.BodyMenu) looter.containerMenu;
        check(!menu.getSlot(0).mayPlace(new ItemStack(Items.STONE)), "Je kon iets in het lichaam leggen");
        // Vakje 9 van het lichaam staat in rij 1, het eerste vakje: slot 9 van het menu.
        menu.quickMoveStack(looter, 9);
        check(looter.getInventory().countItem(Items.APPLE) == 3, "De appels kwamen niet uit het lichaam");
        looter.closeContainer();
        helper.runAfterDelay(30, () -> {
            check(body.isRemoved(), "Het lege lichaam ligt er nog");
            helper.succeed();
        });
    }
}
