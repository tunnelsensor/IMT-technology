package com.senne.imttech.client.dev;

import com.senne.imttech.armor.ArmorRegistry;
import com.senne.imttech.armor.PlateTier;
import com.senne.imttech.armor.VestData;
import com.senne.imttech.armor.VestItem;
import com.senne.imttech.armor.Vests;
import com.senne.imttech.client.armor.ArmorWorkbenchScreen;
import com.senne.imttech.client.armor.VestScreen;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Blocks;

/**
 * De body armor (hoofdstuk 109) in de rondleiding ({@code gradlew runVisualCheck -Ponly=vest}): het vest op je lijf van
 * achteren (met SWAT) en van voren (met de magazijnen), over een borstplaat, over de Tech Suit, met een rugzak en met
 * geweren aan de slings; het scherm van het vest, het vest-vakje in je inventaris, de plates naast je hartjes (heel en
 * gebarsten), het creative-tabblad, een Raven-soldaat met een vest, het Pegboard en het lichaam van een speler.
 * De foto's heten vs01 tot vs20.
 */
final class VestTour {
    private VestTour() {}

    /** Het veldje: ver van de andere veldjes (de rugzakken liggen op -380, -160). */
    static final BlockPos YARD = new BlockPos(-430, -61, -210);

    /** Een vest met deze plates (voor en achter), zoveel volle Glock-magazijnen, deze tekst en kleur, en deze stofkleur (-1 = gewoon). */
    static ItemStack vest(PlateTier front, PlateTier back, int mags, String text, DyeColor textColour, int rgb) {
        ItemStack vest = new ItemStack(ArmorRegistry.PLATE_CARRIER.get());
        if (rgb == -2) {
            vest.set(com.senne.imttech.backpack.BackpackRegistry.CAMO.get(), true);
        } else if (rgb >= 0) {
            vest.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, true));
        }
        List<ItemStack> list = new java.util.ArrayList<>();
        for (int i = 0; i < VestData.MAGS; i++) {
            list.add(i < mags ? MagazineItem.of(FirearmType.GLOCK_19, 15) : ItemStack.EMPTY);
        }
        ItemStack frontPlate = front == null ? ItemStack.EMPTY : new ItemStack(ArmorRegistry.plate(front));
        ItemStack backPlate = back == null ? ItemStack.EMPTY : new ItemStack(ArmorRegistry.plate(back));
        VestItem.setData(vest, new VestData(frontPlate, backPlate, list, text, textColour.getId()));
        return vest;
    }

    /** Alles uit, dan dit vest aan (en eventueel een borstplaat). */
    private static void wear(ServerPlayer player, ItemStack vest, ItemStack chest) {
        player.getInventory().clearContent();
        Gear.setSling(player, 0, ItemStack.EMPTY);
        Gear.setSling(player, 1, ItemStack.EMPTY);
        Gear.setBack(player, ItemStack.EMPTY);
        for (com.senne.imttech.guns.GearItem.Kind kind : com.senne.imttech.guns.GearItem.Kind.values()) {
            Gear.set(player, kind, ItemStack.EMPTY);
        }
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        Gear.setVest(player, vest);
    }

    static void buildYard(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-7, 1, -7), origin.offset(7, 6, 7))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-7, 0, -7), origin.offset(7, 0, 7))) {
            level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
        }
        // Een muur met twee Pegboards (2 hoog, 3 breed), de voorkant naar het noorden.
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, 1, 5), origin.offset(3, 3, 5))) {
            level.setBlockAndUpdate(pos, Blocks.STONE_BRICKS.defaultBlockState());
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 2; dy++) {
                level.setBlockAndUpdate(origin.offset(dx, dy, 4), com.senne.imttech.registry.ModBlocks.PEGBOARD.get().defaultBlockState()
                        .setValue(com.senne.imttech.armory.PegboardBlock.FACING, Direction.NORTH));
            }
        }
        // Bovenaan drie vesten (5 x 6 gaatjes), onderaan de vier plates (3 x 4); een paneel is 8 x 8 gaatjes.
        BlockPos board = origin.offset(0, 1, 4);
        hang(level, board, -0.94, 1.5, vest(PlateTier.STEEL, PlateTier.STEEL, 2, "SWAT", DyeColor.WHITE, -1));
        hang(level, board, 0.0, 1.5, vest(PlateTier.STEEL, PlateTier.STEEL, 0, "ARMY", DyeColor.LIME, -2));
        hang(level, board, 0.94, 1.5, vest(PlateTier.TITANIUM, PlateTier.TITANIUM, 0, "POLICE", DyeColor.YELLOW, 0x2B3A55));
        double[] across = {-1.125, -0.375, 0.375, 1.125};
        for (PlateTier tier : PlateTier.values()) {
            hang(level, board, across[tier.ordinal()], 0.4, new ItemStack(ArmorRegistry.plate(tier)));
        }
    }

    /** Iets op het Pegboard hangen, {@code du} en {@code dv} blokken van de linkeronderhoek van het middelste paneel. */
    private static void hang(ServerLevel level, BlockPos board, double du, double dv, ItemStack stack) {
        int cells = com.senne.imttech.armory.Pegboard.CELLS;
        double cu = com.senne.imttech.armory.Pegboard.column(board, Direction.NORTH) * cells + cells * (0.5 + du);
        double cv = board.getY() * cells + cells * dv;
        com.senne.imttech.armory.PegboardBlock.hangAt(null, level, board, Direction.NORTH, cu, cv, stack,
                com.senne.imttech.armory.Pegboard.Turn.LEFT);
    }

    static void tour(DevScript t) {
        BlockPos o = YARD;
        double sx = o.getX() + 0.5;
        double sy = o.getY() + 1;
        double sz = o.getZ() + 0.5;
        t.stand(0, sx, sy, sz, 0, 10);
        // Een vers stukje wereld: eerst wachten tot het echt meedoet (anders bewegen wezens niet en hangt het afsluiten).
        t.waitUntil(600, () -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            return server != null && server.overworld().isPositionEntityTicking(o);
        });
        t.onServer(5, player -> buildYard(player.serverLevel(), o));
        t.command(0, "gamemode survival @a");
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.step(0, () -> Minecraft.getInstance().options.fov().set(45));

        // Op je lijf: van achteren (SWAT) en van voren (de magazijnen), zonder en met borstplaat, en over de Tech Suit.
        view(t, sx, sy, sz, () -> vest(PlateTier.STEEL, PlateTier.STEEL, 2, "SWAT", DyeColor.WHITE, -1), () -> ItemStack.EMPTY,
                "vs01_vest_back", "vs02_vest_front");
        view(t, sx, sy, sz, () -> vest(PlateTier.STEEL, PlateTier.STEEL, 3, "POLICE", DyeColor.YELLOW, 0x2B3A55),
                () -> new ItemStack(Items.IRON_CHESTPLATE), "vs03_over_chestplate_back", "vs04_over_chestplate_front");
        view(t, sx, sy, sz, () -> vest(PlateTier.TITANIUM, PlateTier.TITANIUM, 1, "SECURITY", DyeColor.RED, -2),
                () -> new ItemStack(ModItems.TECH_CHESTPLATE.get()), "vs05_over_tech_suit_back", "vs06_over_tech_suit_front");
        // Met een rugzak (die hangt over het vest) en met de geweren aan de slings.
        t.onServer(0, player -> {
            wear(player, vest(PlateTier.STEEL, PlateTier.STEEL, 2, "SWAT", DyeColor.WHITE, -1), ItemStack.EMPTY);
            Gear.setBack(player, BackpackTour.withPouches(BackpackTour.pack(com.senne.imttech.backpack.BackpackItem.Size.ASSAULT, -1)));
        });
        shots(t, sx, sy, sz, "vs07_with_backpack_back", "vs08_with_backpack_front");
        t.onServer(0, player -> {
            ShowcaseFeatures.giveSlingGear(player);
            Gear.setVest(player, vest(PlateTier.STEEL, PlateTier.STEEL, 2, "SWAT", DyeColor.WHITE, -1));
        });
        shots(t, sx, sy, sz, "vs09_with_slings_back", "vs10_with_slings_front");
        // Schuin van boven en van opzij: hoe dik het vest is.
        t.onServer(0, player -> wear(player, vest(PlateTier.STEEL, PlateTier.STEEL, 3, "SWAT", DyeColor.WHITE, -1), ItemStack.EMPTY));
        t.stand(5, sx, sy, sz, 0, 45);
        t.camera(0, CameraType.THIRD_PERSON_BACK);
        t.shot(12, "vs11_vest_from_above");
        t.camera(0, CameraType.FIRST_PERSON);
        t.step(0, () -> Minecraft.getInstance().options.fov().set(70));
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);

        // De plates naast je hartjes: heel, en de voorste gebarsten.
        t.stand(5, sx, sy, sz, 0, 10);
        t.shot(12, "vs12_plates_hud");
        t.onServer(0, player -> {
            ItemStack vest = Gear.vest(player);
            ItemStack cracked = new ItemStack(ArmorRegistry.plate(PlateTier.STEEL));
            cracked.setDamageValue(cracked.getMaxDamage());
            ItemStack worn = new ItemStack(ArmorRegistry.plate(PlateTier.STEEL));
            worn.setDamageValue(worn.getMaxDamage() * 4 / 5);
            Gear.setVest(player, VestItem.with(vest, VestItem.data(vest).withPlate(true, cracked).withPlate(false, worn)));
        });
        t.shot(10, "vs13_plates_hud_cracked");

        // Het scherm van het vest (hoofdstuk 111: de plates en de magazijnen op een rij, zonder tekst), en het vest-vakje in je
        // inventaris.
        t.onServer(0, player -> wear(player, vest(PlateTier.STEEL, PlateTier.POLYMER, 2, "SWAT", DyeColor.WHITE, -1), ItemStack.EMPTY));
        t.onServer(5, Vests::openWorn);
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof VestScreen);
        t.step(0, () -> HowToTour.mouseTo(1, 1));
        t.step(0, () -> Minecraft.getInstance().getToasts().clear());
        t.shot(15, "vs14_vest_screen");
        t.close(0);
        // De Armor Workbench: de drie tabbladen, en op Customize de tekst die je typt.
        workbenchShots(t, o);
        t.open(10, () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player));
        t.step(3, () -> HowToTour.mouseTo(2, 2));
        t.shot(15, "vs16_vest_slot_in_inventory");
        t.close(0);
        t.command(0, "gamemode creative @a");
        t.step(5, () -> ShowcaseFeatures.openModTab(com.senne.imttech.registry.ModCreativeTabs.EQUIPMENT.get()));
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.shot(15, "vs17_creative_tab");
        t.close(0);
        t.command(0, "gamemode survival @a");

        // Een Raven-soldaat met een vest (van achteren: RAVEN), het Pegboard, en het lichaam van een speler met zijn vest.
        t.onServer(0, player -> {
            ServerLevel level = player.serverLevel();
            spawnRaven(level, o);
            com.senne.imttech.body.PlayerBodyEntity body = com.senne.imttech.backpack.BackpackRegistry.PLAYER_BODY.get().create(level);
            if (body != null) {
                body.moveTo(o.getX() - 2.5, o.getY() + 1, o.getZ() - 1.5, 0, 0);
                body.setYBodyRot(0);
                body.setOwner(player.getUUID(), player.getGameProfile().getName());
                body.loot().setItem(com.senne.imttech.body.PlayerBodyEntity.VEST, vest(PlateTier.STEEL, PlateTier.STEEL, 1, "SWAT",
                        DyeColor.WHITE, -1));
                level.addFreshEntity(body);
            }
        });
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.stand(10, o.getX() + 2.5, sy, o.getZ() - 4.0, 0, 15);
        t.shot(15, "vs18_raven_soldier_vest");
        t.stand(0, o.getX() - 2.5, sy + 0.5, o.getZ() - 3.5, 0, 45);
        t.shot(15, "vs19_player_body_vest");
        t.stand(0, sx, sy, o.getZ() + 1.5, 0, 5);
        t.shot(15, "vs20_pegboard");
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        t.command(0, "gamemode creative @a");
        t.pause(20);
    }

    // ================================================================ De Armor Workbench (hoofdstuk 111)

    /** Waar de werkbank staat op het veldje (links voor de muur). */
    static BlockPos benchPos(BlockPos o) {
        return o.offset(-3, 1, 2);
    }

    /**
     * Alles wat je nodig hebt om de werkbank te tonen: de werkbank zelf, materiaal voor vesten en plates en kleurstoffen. Je
     * inventaris wordt eerst leeggemaakt.
     */
    static void benchKit(ServerPlayer player, BlockPos o) {
        ServerLevel level = player.serverLevel();
        level.setBlockAndUpdate(benchPos(o), ArmorRegistry.ARMOR_WORKBENCH.get().defaultBlockState()
                .setValue(com.senne.imttech.armor.ArmorWorkbenchBlock.FACING, Direction.NORTH));
        var inventory = player.getInventory();
        inventory.clearContent();
        inventory.add(new ItemStack(ModItems.POLYMER.get(), 12));
        inventory.add(new ItemStack(ModItems.STEEL_INGOT.get(), 12));
        inventory.add(new ItemStack(ModItems.TITANIUM_INGOT.get(), 3));
        inventory.add(new ItemStack(Items.LEATHER, 7));
        inventory.add(new ItemStack(Items.STRING, 2));
        for (net.minecraft.world.item.Item dye : List.of(Items.RED_DYE, Items.BLUE_DYE, Items.WHITE_DYE, Items.BROWN_DYE,
                Items.LIME_DYE, Items.GREEN_DYE, Items.BLACK_DYE)) {
            inventory.add(new ItemStack(dye, 4));
        }
    }

    /** Het scherm van de werkbank openen op dit tabblad, met een versleten plate en een vest met SWAT in de vakjes. */
    static void openBench(ServerPlayer player, BlockPos o, int tab) {
        com.senne.imttech.armor.ArmorWorkbenchBlock.open(player, benchPos(o));
        if (player.containerMenu instanceof com.senne.imttech.armor.ArmorWorkbenchMenu menu) {
            menu.setTab(tab);
            ItemStack worn = new ItemStack(ArmorRegistry.plate(PlateTier.STEEL));
            worn.setDamageValue(worn.getMaxDamage() * 3 / 5);
            menu.getSlot(com.senne.imttech.armor.ArmorWorkbenchMenu.PLATE_SLOT).set(worn);
            menu.getSlot(com.senne.imttech.armor.ArmorWorkbenchMenu.VEST_SLOT).set(vest(PlateTier.STEEL, PlateTier.STEEL, 2, "SWAT",
                    DyeColor.WHITE, -1));
            menu.broadcastChanges();
        }
    }

    /** In het open scherm van de werkbank: dit tabblad, en (op Vests en Plates) dit ding gekozen. */
    static void benchTab(int tab, net.minecraft.world.item.Item select) {
        if (Minecraft.getInstance().screen instanceof ArmorWorkbenchScreen screen) {
            screen.switchTo(tab);
            if (select != null) {
                screen.select(select);
            }
        }
    }

    private static void workbenchShots(DevScript t, BlockPos o) {
        t.onServer(0, player -> benchKit(player, o));
        t.onServer(5, player -> openBench(player, o, com.senne.imttech.armor.ArmorWorkbenchMenu.TAB_VESTS));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof ArmorWorkbenchScreen);
        t.step(0, () -> benchTab(com.senne.imttech.armor.ArmorWorkbenchMenu.TAB_VESTS, ArmorRegistry.PLATE_CARRIER.get()));
        t.step(0, () -> HowToTour.mouseTo(1, 1));
        t.step(0, () -> Minecraft.getInstance().getToasts().clear());
        t.shot(15, "vs21_workbench_vests");
        t.step(0, () -> benchTab(com.senne.imttech.armor.ArmorWorkbenchMenu.TAB_PLATES, ArmorRegistry.plate(PlateTier.STEEL)));
        t.shot(15, "vs22_workbench_plates");
        t.step(0, () -> benchTab(com.senne.imttech.armor.ArmorWorkbenchMenu.TAB_CUSTOMIZE, null));
        t.shot(15, "vs23_workbench_customize");
        t.step(0, () -> {
            if (Minecraft.getInstance().screen instanceof ArmorWorkbenchScreen screen) {
                screen.type("K9 UNIT", DyeColor.ORANGE);
            }
        });
        t.shot(15, "vs24_workbench_typed");
        t.close(0);
    }

    /**
     * De twee plaatjes voor How it works ({@code -Ponly=howtopics:armor}; dat verandert bestanden van de mod): het tabblad
     * Plates (armor_workbench) en het tabblad Customize (armor_customize), elk 230 x 222.
     */
    static void pictures(DevScript t) {
        BlockPos o = YARD;
        t.stand(0, o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5, 0, 10);
        t.waitUntil(600, () -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            return server != null && server.overworld().isPositionEntityTicking(o);
        });
        t.onServer(5, player -> buildYard(player.serverLevel(), o));
        t.command(0, "gamemode survival @a");
        t.onServer(5, player -> benchKit(player, o));
        t.onServer(5, player -> openBench(player, o, com.senne.imttech.armor.ArmorWorkbenchMenu.TAB_PLATES));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof ArmorWorkbenchScreen);
        t.step(0, () -> benchTab(com.senne.imttech.armor.ArmorWorkbenchMenu.TAB_PLATES, ArmorRegistry.plate(PlateTier.STEEL)));
        t.step(0, () -> HowToTour.mouseTo(1, 1));
        t.step(20, () -> Minecraft.getInstance().getToasts().clear());
        t.step(5, () -> captureBench("armor_workbench"));
        t.step(0, () -> benchTab(com.senne.imttech.armor.ArmorWorkbenchMenu.TAB_CUSTOMIZE, null));
        t.step(10, () -> captureBench("armor_customize"));
        t.close(5);
        t.command(0, "gamemode creative @a");
    }

    private static void captureBench(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ArmorWorkbenchScreen screen) {
            int w = com.senne.imttech.armor.ArmorWorkbenchMenu.WIDTH;
            int h = com.senne.imttech.armor.ArmorWorkbenchMenu.HEIGHT;
            HowToTour.capture(name, (screen.width - w) / 2, (screen.height - h) / 2, w, h);
        }
    }

    /** Een Raven-soldaat met een vest, zonder AI, met zijn rug naar het noorden (waar de camera staat): dan zie je RAVEN. */
    static void spawnRaven(ServerLevel level, BlockPos o) {
        com.senne.imttech.raven.RavenSoldierEntity soldier = ModEntities.RAVEN_SOLDIER.get().create(level);
        if (soldier != null) {
            soldier.moveTo(o.getX() + 2.5, o.getY() + 1, o.getZ() - 1.5, 0, 0);
            soldier.setYBodyRot(0);
            soldier.setYHeadRot(0);
            soldier.setNoAi(true);
            soldier.setVest(Vests.ravenVest(level.getRandom(), ItemStack.EMPTY));
            level.addFreshEntity(soldier);
        }
    }

    /** Voor de showcase: alles uit en het SWAT-vest aan (staal voor, polymeer achter, twee magazijnen). */
    static void wearSwat(ServerPlayer player) {
        wear(player, vest(PlateTier.STEEL, PlateTier.POLYMER, 2, "SWAT", DyeColor.WHITE, -1), ItemStack.EMPTY);
    }

    /** Na de showcase: het vest uit en de soldaten van het veldje weg. */
    static void cleanUp(ServerPlayer player) {
        Gear.setVest(player, ItemStack.EMPTY);
        player.serverLevel().getEntitiesOfClass(com.senne.imttech.raven.RavenSoldierEntity.class,
                player.getBoundingBox().inflate(16)).forEach(net.minecraft.world.entity.Entity::discard);
    }

    /** Dit vest (en deze borstplaat) aan, dan een foto van achteren en een van voren. */
    private static void view(DevScript t, double x, double y, double z, java.util.function.Supplier<ItemStack> vest,
                             java.util.function.Supplier<ItemStack> chest, String back, String front) {
        // De items pas tijdens de rondleiding maken: als de rondleiding wordt opgebouwd, kent Minecraft ze nog niet.
        t.onServer(0, player -> wear(player, vest.get(), chest.get()));
        shots(t, x, y, z, back, front);
    }

    private static void shots(DevScript t, double x, double y, double z, String back, String front) {
        // Een paar blokken van de muur af, met je gezicht ernaar toe: de camera van voren (vier blokken voor je) staat dan
        // nog in het open veld. (Omdraaien kan niet: je lijf draait niet helemaal mee met je hoofd, dan stond je scheef.)
        t.stand(5, x, y, z - 3.5, 0, 0);
        t.camera(0, CameraType.THIRD_PERSON_BACK);
        t.shot(12, back);
        t.camera(0, CameraType.THIRD_PERSON_FRONT);
        t.shot(12, front);
        t.camera(0, CameraType.FIRST_PERSON);
    }
}
