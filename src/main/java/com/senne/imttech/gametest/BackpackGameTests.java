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
            check(menu.size() == BackpackItem.Size.ASSAULT && menu.slots.size() == 18 + 36,
                    "Een Assault Pack heeft 18 vakjes (en je inventaris 36), niet " + (menu.slots.size() - 36));
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
        check(first.slots.size() == 36 + 36, "Een Rucksack heeft 36 vakjes");
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
        check(BackpackItem.mayHold(new ItemStack(ModItems.HOLSTER.get())), "Een holster past niet");

        ServerPlayer player = player(helper, "pack-stuffer", new BlockPos(3, 2, 3));
        player.setItemInHand(InteractionHand.MAIN_HAND, pack(BackpackItem.Size.PATROL));
        player.getInventory().setItem(5, new ItemStack(Items.SHULKER_BOX));
        player.getInventory().setItem(6, pack(BackpackItem.Size.ASSAULT));
        player.getInventory().setItem(7, new ItemStack(Items.IRON_INGOT, 9));
        check(BackpackZip.open(player, BackpackPlace.hand(player, InteractionHand.MAIN_HAND)), "De rugzak in je hand ging niet open");
        BackpackMenu menu = (BackpackMenu) player.containerMenu;
        int pack = menu.size().slots();
        // De werkbalk staat achteraan: vakje 5 van je inventaris = slot pack + 27 + 5.
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
}
