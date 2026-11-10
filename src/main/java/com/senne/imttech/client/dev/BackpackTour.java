package com.senne.imttech.client.dev;

import com.senne.imttech.ImtTech;
import com.senne.imttech.backpack.BackpackBlock;
import com.senne.imttech.backpack.BackpackBlockEntity;
import com.senne.imttech.backpack.BackpackItem;
import com.senne.imttech.backpack.BackpackRegistry;
import com.senne.imttech.backpack.Backpacks;
import com.senne.imttech.client.backpack.BackpackScreen;
import com.senne.imttech.guns.Gear;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * De rugzakken (hoofdstuk 72) in de rondleiding ({@code gradlew runVisualCheck -Ponly=backpack}) en de showcase: een veldje
 * met neergezette rugzakken in allerlei kleuren, de rugzak op je rug van achteren en van voren (ook met een geweer erover),
 * het rugvakje in je inventaris, de rits, het scherm en de sorteerknop, het wiel met de rugzakknop.
 *
 * {@code -Ponly=backpackwalk} meet met echte toetsen (DevKeys) of je met een open rugzak traag doorloopt; de afstanden
 * staan in de log ("[dev] backpackwalk").
 */
final class BackpackTour {
    private BackpackTour() {}

    /** Het veldje (de vloer): ver van de andere veldjes van de rondleiding. */
    static final BlockPos YARD = new BlockPos(-380, -61, -160);

    /** Een rugzak van deze maat, in deze kleur ({@code -1} = olijfgroen, {@code -2} = camo), met dit erin. */
    static ItemStack pack(BackpackItem.Size size, int colour, ItemStack... inside) {
        ItemStack stack = new ItemStack(BackpackRegistry.item(size));
        if (colour == -2) {
            stack.set(BackpackRegistry.CAMO.get(), true);
        } else if (colour >= 0) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(colour, true));
        }
        BackpackItem.setContents(stack, List.of(inside));
        return stack;
    }

    /** Stap 2: een MOLLE-pouch van deze soort, met dit erin. */
    static ItemStack pouch(com.senne.imttech.backpack.MolleItem.Kind kind, ItemStack... inside) {
        ItemStack stack = new ItemStack(BackpackRegistry.molle(kind));
        com.senne.imttech.backpack.MolleItem.setContents(stack, List.of(inside));
        return stack;
    }

    /**
     * Stap 2: dezelfde rugzak met pouches op al zijn MOLLE-plekken: een Assault Pack een dump pouch en een IFAK, een Patrol
     * Pack een MOLLE-magazijnpouch, een MOLLE-granaatpouch en een utility pouch, een Rucksack die vier. In de kleur van de
     * rugzak (camo: camo).
     */
    static ItemStack withPouches(ItemStack pack) {
        BackpackItem.Size size = BackpackItem.sizeOf(pack);
        List<ItemStack> pouches = new ArrayList<>();
        ItemStack mags = pouch(com.senne.imttech.backpack.MolleItem.Kind.MAG,
                com.senne.imttech.guns.MagazineItem.of(com.senne.imttech.guns.FirearmType.GLOCK_19, 15),
                com.senne.imttech.guns.MagazineItem.of(com.senne.imttech.guns.FirearmType.GLOCK_19, 11));
        ItemStack grenades = pouch(com.senne.imttech.backpack.MolleItem.Kind.GRENADE,
                new ItemStack(com.senne.imttech.registry.ModItems.FRAG_GRENADE.get()), new ItemStack(com.senne.imttech.registry.ModItems.EMP_GRENADE.get()));
        ItemStack ifak = pouch(com.senne.imttech.backpack.MolleItem.Kind.IFAK, new ItemStack(Items.GOLDEN_APPLE, 2),
                net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.HEALING));
        ItemStack dump = pouch(com.senne.imttech.backpack.MolleItem.Kind.DUMP, new ItemStack(com.senne.imttech.registry.ModItems.CASING_9MM.get(), 23));
        ItemStack utility = pouch(com.senne.imttech.backpack.MolleItem.Kind.UTILITY, new ItemStack(Items.SHEARS), new ItemStack(Items.TORCH, 12));
        switch (size) {
            case ASSAULT -> {
                pouches.add(dump);
                pouches.add(ifak);
            }
            case PATROL -> {
                pouches.add(mags);
                pouches.add(grenades);
                pouches.add(utility);
            }
            case RUCKSACK -> {
                pouches.add(ifak);
                pouches.add(dump);
                pouches.add(mags);
                pouches.add(utility);
            }
        }
        for (ItemStack pouch : pouches) {
            if (BackpackItem.camo(pack)) {
                pouch.set(BackpackRegistry.CAMO.get(), true);
            } else if (pack.has(DataComponents.DYED_COLOR)) {
                pouch.set(DataComponents.DYED_COLOR, pack.get(DataComponents.DYED_COLOR));
            }
        }
        ItemStack out = pack.copy();
        BackpackItem.setMolle(out, pouches);
        return out;
    }

    /** Een volle rugzak om te laten zien: munitie, eten, gereedschap, door elkaar (zodat sorteren iets doet). */
    static ItemStack loaded(BackpackItem.Size size, int colour) {
        List<ItemStack> inside = new ArrayList<>();
        ItemStack[] stuff = {
                new ItemStack(Items.BREAD, 12), new ItemStack(Items.TORCH, 40), new ItemStack(com.senne.imttech.registry.ModItems.ROUND_9MM.get(), 48),
                ItemStack.EMPTY, new ItemStack(Items.COOKED_BEEF, 6), new ItemStack(Items.TORCH, 30), ItemStack.EMPTY,
                new ItemStack(Items.IRON_INGOT, 9), new ItemStack(Items.STRING, 5), new ItemStack(Items.BREAD, 4),
                ItemStack.EMPTY, new ItemStack(Items.SHEARS), new ItemStack(com.senne.imttech.registry.ModItems.ROUND_9MM.get(), 20),
                new ItemStack(Items.COMPASS), ItemStack.EMPTY, new ItemStack(Items.GOLDEN_APPLE, 2)};
        for (ItemStack stack : stuff) {
            inside.add(stack.copy());
        }
        return pack(size, colour, inside.toArray(new ItemStack[0]));
    }

    /**
     * Het veldje: een vloer van gladde steen, een muur van stenen bakstenen erachter, en twee rijen neergezette rugzakken
     * met hun MOLLE-banden naar het noorden (naar de camera): voor de drie maten in olijfgroen, rood en camo, achter drie in
     * andere kleuren (blauw, zwart, en rood + geel gemengd = oranje).
     */
    static void buildYard(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-7, 1, -7), origin.offset(7, 6, 7))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        net.minecraft.world.phys.AABB.encapsulatingFullBlocks(origin.offset(-8, 0, -8), origin.offset(8, 8, 8)))
                .forEach(net.minecraft.world.entity.Entity::discard);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-7, 0, -7), origin.offset(7, 0, 7))) {
            level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-5, 1, 5), origin.offset(5, 4, 5))) {
            level.setBlockAndUpdate(pos, Blocks.STONE_BRICKS.defaultBlockState());
        }
        place(level, origin.offset(-2, 1, 1), withPouches(pack(BackpackItem.Size.ASSAULT, -1)));
        place(level, origin.offset(0, 1, 1), withPouches(pack(BackpackItem.Size.PATROL, 0xB02E26)));
        place(level, origin.offset(2, 1, 1), withPouches(pack(BackpackItem.Size.RUCKSACK, -2)));
        place(level, origin.offset(-2, 1, 3), pack(BackpackItem.Size.RUCKSACK, 0x3C44AA));
        place(level, origin.offset(0, 1, 3), pack(BackpackItem.Size.ASSAULT, 0x1D1D21));
        place(level, origin.offset(2, 1, 3), pack(BackpackItem.Size.PATROL, 0xD8822B));
    }

    private static void place(ServerLevel level, BlockPos pos, ItemStack pack) {
        BlockState state = BackpackRegistry.BACKPACK_BLOCK.get().defaultBlockState()
                .setValue(BackpackBlock.FACING, Direction.NORTH)
                .setValue(BackpackBlock.SIZE, BackpackBlock.sizeProperty(BackpackItem.sizeOf(pack)));
        level.setBlockAndUpdate(pos, state);
        if (level.getBlockEntity(pos) instanceof BackpackBlockEntity entity) {
            entity.setPack(pack);
        }
    }

    /** Je spullen voor de rondleiding: een Rucksack vol op je rug, en de holster, pouches en slings (met een geweer). */
    static void wear(ServerPlayer player, BackpackItem.Size size, int colour, boolean withGuns) {
        if (withGuns) {
            ShowcaseFeatures.giveSlingGear(player);
        } else {
            // Zonder geweren en heup-pouches: dan zie je de rugzak en zijn MOLLE-pouches.
            player.getInventory().clearContent();
            Gear.setSling(player, 0, ItemStack.EMPTY);
            Gear.setSling(player, 1, ItemStack.EMPTY);
            for (com.senne.imttech.guns.GearItem.Kind kind : com.senne.imttech.guns.GearItem.Kind.values()) {
                Gear.set(player, kind, ItemStack.EMPTY);
            }
        }
        Gear.setBack(player, withPouches(loaded(size, colour)));
    }

    // ================================================================ De rondleiding

    static void tour(DevScript t) {
        BlockPos o = YARD;
        double sx = o.getX() + 0.5;
        double sy = o.getY() + 1;
        double sz = o.getZ() - 3.5;
        t.onServer(0, player -> buildYard(player.serverLevel(), o));
        t.command(0, "gamemode survival @a");
        // De neergezette rugzakken, van een afstandje en van dichtbij (zonder HUD).
        t.stand(20, sx, sy, sz, 0, 25);
        t.shot(30, "pk01_backpacks_on_the_ground");
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.step(0, () -> Minecraft.getInstance().options.fov().set(40));
        t.stand(0, sx, sy, o.getZ() - 1.2, 0, 30);
        t.shot(20, "pk02_backpacks_close");
        t.stand(0, o.getX() + 4.5, sy, o.getZ() - 0.5, 60, 25);
        t.shot(20, "pk03_backpacks_side");
        t.step(0, () -> Minecraft.getInstance().options.fov().set(70));
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);

        // Het rugvakje in je inventaris, en de rugzakken in het creative-menu (de plaatjes in hun vakje).
        t.onServer(0, player -> wear(player, BackpackItem.Size.ASSAULT, -1, false));
        t.stand(10, sx, sy, sz, 0, 10);
        t.open(10, () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player));
        t.shot(20, "pk04_back_slot_in_inventory");
        t.close(0);
        t.command(0, "gamemode creative @a");
        t.step(5, () -> ShowcaseFeatures.openModTab(com.senne.imttech.registry.ModCreativeTabs.WEAPONS.get()));
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.shot(15, "pk05_creative_tab");
        t.close(0);
        t.command(0, "gamemode survival @a");

        // Op je rug: van achteren en van voren, elke maat; de Rucksack ook met een geweer erover (de sling).
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.step(0, () -> Minecraft.getInstance().options.fov().set(45));
        backView(t, sx, sy, sz, BackpackItem.Size.ASSAULT, -1, false, "pk06_assault_back", "pk07_assault_front");
        backView(t, sx, sy, sz, BackpackItem.Size.PATROL, 0xB02E26, false, "pk08_patrol_back", "pk09_patrol_front");
        backView(t, sx, sy, sz, BackpackItem.Size.RUCKSACK, -2, false, "pk10_rucksack_back", "pk11_rucksack_front");
        backView(t, sx, sy, sz, BackpackItem.Size.RUCKSACK, -1, true, "pk12_rucksack_with_rifle_back", "pk13_rucksack_with_rifle_front");
        // Schuin van boven: hoe dik hij is, en of hij tegen je rug zit.
        t.stand(0, sx, sy, sz, 30, 40);
        t.camera(0, CameraType.THIRD_PERSON_BACK);
        t.shot(12, "pk14_rucksack_from_above");
        t.camera(0, CameraType.FIRST_PERSON);
        t.step(0, () -> Minecraft.getInstance().options.fov().set(70));
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);

        // De rits en het scherm: openritsen (de rugzaktoets doet hetzelfde), halverwege, en het scherm met de spullen.
        t.onServer(0, player -> wear(player, BackpackItem.Size.PATROL, -1, false));
        t.stand(10, sx, sy, sz, 0, 10);
        t.onServer(5, Backpacks::openWorn);
        t.shot(14, "pk15_unzipping");
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof BackpackScreen);
        t.shot(10, "pk16_backpack_screen");
        t.onServer(0, Backpacks::sort);
        t.shot(10, "pk17_sorted");
        t.close(0);
        // De rugzak in je hand (eerste en derde persoon) en het wiel met de rugzakknop.
        t.onServer(0, player -> player.getInventory().setItem(0, loaded(BackpackItem.Size.RUCKSACK, 0x3C44AA)));
        t.step(5, () -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.getInventory().selected = 0;
            }
        });
        t.shot(15, "pk18_pack_in_hand");
        t.camera(0, CameraType.THIRD_PERSON_FRONT);
        t.shot(15, "pk19_pack_in_hand_third_person");
        t.camera(0, CameraType.FIRST_PERSON);
        t.onServer(0, player -> player.getInventory().setItem(0, ItemStack.EMPTY));
        t.wheel(10, true);
        t.shot(15, "pk20_wheel_backpack_button");
        t.wheel(2, false);

        // Stap 2: de MOLLE-pouches. Het scherm met de strook, een pouch open, de pouches van dichtbij op de rugzak, en R
        // met een Glock zonder heup-pouch (de magazijnen komen uit de MOLLE-pouch).
        t.onServer(0, player -> wear(player, BackpackItem.Size.RUCKSACK, -1, false));
        t.stand(5, sx, sy, sz, 0, 10);
        t.onServer(5, Backpacks::openWorn);
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof BackpackScreen);
        t.step(0, () -> HowToTour.mouseTo(2, 2));
        t.shot(10, "pk21_screen_with_molle_strip");
        t.close(0);
        t.onServer(5, player -> com.senne.imttech.backpack.Molle.openWornPouch(player, 0));
        t.shot(12, "pk22_ifak_open");
        t.close(0);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.step(0, () -> Minecraft.getInstance().options.fov().set(40));
        t.camera(0, CameraType.THIRD_PERSON_BACK);
        t.shot(12, "pk23_pouches_on_rucksack_back");
        t.stand(0, sx, sy, sz, 35, 20);
        t.shot(12, "pk24_pouches_on_rucksack_side");
        t.onServer(0, player -> wear(player, BackpackItem.Size.PATROL, 0xB02E26, false));
        t.stand(5, sx, sy, sz, -35, 15);
        t.shot(12, "pk25_pouches_on_patrol");
        t.onServer(0, player -> wear(player, BackpackItem.Size.ASSAULT, -1, false));
        t.stand(5, sx, sy, sz, 40, 15);
        t.shot(12, "pk26_pouches_on_assault");
        t.camera(0, CameraType.FIRST_PERSON);
        t.stand(0, sx, sy, o.getZ() - 1.4, 0, 30);
        t.shot(15, "pk27_placed_with_pouches");
        t.step(0, () -> Minecraft.getInstance().options.fov().set(70));
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        // Het wiel: de knoppen van de pouches rechts, de IFAK-tegels rechtsonder.
        t.onServer(0, player -> wear(player, BackpackItem.Size.RUCKSACK, -1, false));
        t.stand(5, sx, sy, sz, 0, 10);
        t.wheel(10, true);
        t.shot(15, "pk28_wheel_molle_and_ifak");
        t.wheel(2, false);
        // R met een Glock, zonder heup-pouch: het keuzescherm toont de magazijnen in de MOLLE-pouch.
        t.onServer(0, player -> {
            wear(player, BackpackItem.Size.PATROL, -1, false);
            ItemStack gun = com.senne.imttech.guns.FirearmItem.loaded(com.senne.imttech.guns.FirearmType.GLOCK_19,
                    com.senne.imttech.guns.GunMaterial.STEEL);
            com.senne.imttech.guns.FirearmItem.setMagazine(gun, com.senne.imttech.guns.MagazineItem.of(com.senne.imttech.guns.FirearmType.GLOCK_19, 3));
            player.getInventory().setItem(0, gun);
        });
        t.step(10, () -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.getInventory().selected = 0;
            }
        });
        t.step(15, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()));
        t.shot(10, "pk29_reload_from_molle_pouch");
        t.close(5);
        t.onServer(0, player -> player.getInventory().setItem(0, ItemStack.EMPTY));

        // Stap 3: van achteren. "Lotte" (een nep-speler) staat met haar rug naar je toe, met een Patrol Pack met pouches.
        // Jij sluipt achter haar met een lege hand: de hint; dan de rits (zachter), het scherm met de meter, een paar keer
        // grijpen (de meter loopt op), en dan draait ze zich om: betrapt.
        BlockPos lotte = o.offset(0, 1, -2);
        t.onServer(0, player -> {
            wear(player, BackpackItem.Size.ASSAULT, -1, false);
            ServerPlayer victim = DevWorld.fakePlayerAt(player.serverLevel(), "Lotte", lotte);
            victim.teleportTo(player.serverLevel(), lotte.getX() + 0.5, lotte.getY(), lotte.getZ() + 0.5, 180, 0);
            victim.setYHeadRot(180);
            Gear.setBack(victim, withPouches(loaded(BackpackItem.Size.PATROL, 0x6B5A3A)));
            player.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
        });
        t.stand(10, lotte.getX() + 0.5, lotte.getY(), lotte.getZ() + 1.8, 180, 15);
        t.key(0, () -> Minecraft.getInstance().options.keyShift, true);
        t.lookAt(10, new Vec3(lotte.getX() + 0.5, lotte.getY() + 1.2, lotte.getZ() + 0.6));
        t.shot(10, "pk30_theft_hint");
        t.onServer(0, player -> {
            ServerPlayer victim = player.getServer().getPlayerList().getPlayerByName("Lotte");
            if (victim != null) {
                com.senne.imttech.backpack.BackpackTheft.tryStart(player, victim);
            }
        });
        t.shot(12, "pk31_theft_unzipping");
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof BackpackScreen);
        t.key(0, () -> Minecraft.getInstance().options.keyShift, false);
        t.step(0, () -> HowToTour.mouseTo(2, 2));
        t.shot(8, "pk32_theft_screen");
        for (int slot : new int[] {0, 9, 1}) {
            t.step(3, () -> clickSlot(slot));
        }
        t.shot(3, "pk33_theft_meter_rising");
        t.onServer(0, player -> {
            ServerPlayer victim = player.getServer().getPlayerList().getPlayerByName("Lotte");
            if (victim != null) {
                victim.teleportTo(player.serverLevel(), victim.getX(), victim.getY(), victim.getZ(), 0, 0);
                victim.setYHeadRot(0);
            }
        });
        t.shot(8, "pk34_theft_caught");
        t.camera(0, CameraType.THIRD_PERSON_BACK);
        t.shot(10, "pk35_theft_caught_glowing");
        t.camera(0, CameraType.FIRST_PERSON);
        t.onServer(0, player -> player.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING));

        // Stap 4: het lichaam van een speler. Lotte (met harnas, een zwaard en haar rugzak) gaat dood: haar lichaam ligt op
        // haar buik, de rugzak bovenop. De hints (op de rugzak, op het lichaam), het doorzoek-scherm, en dan je eigen lichaam
        // met de knop "Everything back".
        t.command(0, "gamerule keepInventory false");
        t.onServer(0, player -> {
            ServerPlayer victim = player.getServer().getPlayerList().getPlayerByName("Lotte");
            if (victim == null) {
                return;
            }
            victim.teleportTo(player.serverLevel(), lotte.getX() + 0.5, lotte.getY(), lotte.getZ() + 0.5, 150, 0);
            victim.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
            victim.getInventory().setItem(12, new ItemStack(Items.DIAMOND, 9));
            victim.getInventory().setItem(30, new ItemStack(Items.BREAD, 14));
            victim.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            victim.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            victim.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            Gear.set(victim, com.senne.imttech.guns.GearItem.Kind.HOLSTER, new ItemStack(com.senne.imttech.registry.ModItems.HOLSTER.get()));
            victim.hurt(victim.damageSources().fellOutOfWorld(), 1000);
        });
        t.stand(10, lotte.getX() + 2.5, lotte.getY(), lotte.getZ() - 1.2, 60, 35);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.shot(15, "pk36_body_on_the_ground");
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        t.stand(0, lotte.getX() + 0.5, lotte.getY(), lotte.getZ() - 1.4, 0, 0);
        t.lookAt(5, new Vec3(lotte.getX() + 0.5 - 0.1, lotte.getY() + 0.35, lotte.getZ() + 0.5 + 0.15));
        t.shot(8, "pk37_body_hint_pack");
        t.lookAt(0, new Vec3(lotte.getX() + 0.5 + 0.3, lotte.getY() + 0.3, lotte.getZ() + 0.5 - 0.5));
        t.shot(8, "pk38_body_hint_search");
        t.onServer(0, player -> {
            com.senne.imttech.body.PlayerBodyEntity body = bodyNear(player);
            if (body != null) {
                com.senne.imttech.body.BodyMenu.open(player, body);
            }
        });
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.shot(8, "pk39_body_search_screen");
        t.close(0);
        // Je eigen lichaam (zoals na doodgaan), met de knop "Everything back".
        t.onServer(0, player -> {
            com.senne.imttech.body.PlayerBodyEntity old = bodyNear(player);
            if (old != null) {
                old.discard();
            }
            wear(player, BackpackItem.Size.ASSAULT, -1, false);
            Gear.setBack(player, ItemStack.EMPTY);
            com.senne.imttech.body.PlayerBodyEntity body = com.senne.imttech.backpack.BackpackRegistry.PLAYER_BODY.get().create(player.serverLevel());
            if (body == null) {
                return;
            }
            body.moveTo(lotte.getX() + 0.5, lotte.getY(), lotte.getZ() + 0.5, 30, 0);
            body.setOwner(player.getUUID(), player.getGameProfile().getName());
            body.loot().setItem(0, com.senne.imttech.guns.FirearmItem.loaded(com.senne.imttech.guns.FirearmType.GLOCK_19,
                    com.senne.imttech.guns.GunMaterial.STEEL));
            body.loot().setItem(1, new ItemStack(Items.IRON_PICKAXE));
            body.loot().setItem(15, new ItemStack(Items.COBBLESTONE, 48));
            body.loot().setItem(com.senne.imttech.body.PlayerBodyEntity.ARMOR + 2, new ItemStack(Items.IRON_CHESTPLATE));
            body.loot().setItem(com.senne.imttech.body.PlayerBodyEntity.ARMOR + 1, new ItemStack(Items.IRON_LEGGINGS));
            body.loot().setItem(com.senne.imttech.body.PlayerBodyEntity.GEAR + 2, new ItemStack(com.senne.imttech.registry.ModItems.MAG_POUCH.get()));
            body.setPack(withPouches(loaded(BackpackItem.Size.PATROL, -2)));
            player.serverLevel().addFreshEntity(body);
            com.senne.imttech.body.BodyMenu.open(player, body);
        });
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.shot(8, "pk40_own_body_everything_back");
        t.onServer(0, player -> {
            if (player.containerMenu instanceof com.senne.imttech.body.BodyMenu menu && menu.body() != null) {
                com.senne.imttech.body.PlayerBodies.startRestore(player, menu.body());
            }
        });
        t.shot(30, "pk41_putting_everything_back");
        t.step(40, () -> { });
        t.camera(0, CameraType.THIRD_PERSON_FRONT);
        t.shot(10, "pk42_everything_back");
        t.camera(0, CameraType.FIRST_PERSON);
        t.onServer(0, player -> {
            com.senne.imttech.body.PlayerBodyEntity old = bodyNear(player);
            if (old != null) {
                old.discard();
            }
            player.getInventory().clearContent();
            Gear.setBack(player, ItemStack.EMPTY);
        });

        // Stap 5: een wand van Pegboards met de drie rugzakken (met hun pouches) en losse pouches, en een Raven-soldaat met
        // zijn antracietgrijze Assault Pack (van achteren). Hij valt: zijn rugzak zit vol buit in zijn lichaam; je haalt hem
        // eruit en ritst hem open in je hand.
        t.onServer(0, player -> buildPegboard(player.serverLevel(), o));
        t.stand(10, o.getX() + 0.5, o.getY() + 1, o.getZ() + 1.2, 0, 8);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.shot(20, "pk43_pegboard_backpacks");
        t.onServer(0, player -> spawnPackedRaven(player.serverLevel(), o.getX() + 3.5, o.getY() + 1, o.getZ() - 2.5));
        t.stand(10, o.getX() + 3.5, o.getY() + 1, o.getZ() + 0.5, 180, 15);
        t.shot(20, "pk44_raven_soldier_with_pack");
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        t.onServer(0, BackpackTour::dropRavens);
        t.stand(30, o.getX() + 3.5, o.getY() + 1, o.getZ() - 0.8, 180, 55);
        t.onServer(0, BackpackTour::searchRavenBody);
        t.shot(20, "pk45_raven_body_with_pack");
        t.step(0, BackpackTour::takePackFromScreen);
        t.close(10);
        t.onServer(0, BackpackTour::unzipPackInHand);
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof BackpackScreen);
        t.shot(10, "pk46_raven_pack_loot");
        t.close(10);
        t.onServer(0, player -> {
            player.serverLevel().getEntitiesOfClass(com.senne.imttech.raven.RavenBodyEntity.class, player.getBoundingBox().inflate(10))
                    .forEach(net.minecraft.world.entity.Entity::discard);
            player.getInventory().clearContent();
        });
        t.command(10, "gamemode creative @a");
    }

    /** Stap 5: een Raven-soldaat die stilstaat met zijn rug naar het zuiden, met zijn antracietgrijze Assault Pack en een Glock. */
    static void spawnPackedRaven(ServerLevel level, double x, double y, double z) {
        com.senne.imttech.raven.RavenSoldierEntity soldier = com.senne.imttech.registry.ModEntities.RAVEN_SOLDIER.get().create(level);
        if (soldier == null) {
            return;
        }
        soldier.moveTo(x, y, z, 180, 0);
        soldier.setYHeadRot(180);
        soldier.setYBodyRot(180);
        soldier.setNoAi(true);
        soldier.setPersistenceRequired();
        soldier.setPack(com.senne.imttech.backpack.RavenPacks.pack());
        soldier.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, com.senne.imttech.guns.FirearmItem.loaded(
                com.senne.imttech.guns.FirearmType.GLOCK_19, com.senne.imttech.guns.GunMaterial.STEEL));
        level.addFreshEntity(soldier);
    }

    /** Stap 5: de Raven-soldaten bij je vallen (hun lichaam blijft liggen, met hun rugzak erin). */
    static void dropRavens(ServerPlayer player) {
        player.serverLevel().getEntitiesOfClass(com.senne.imttech.raven.RavenSoldierEntity.class, player.getBoundingBox().inflate(10),
                net.minecraft.world.entity.Entity::isAlive).forEach(net.minecraft.world.entity.LivingEntity::kill);
    }

    /** Stap 5: het dichtstbijzijnde Raven-lichaam doorzoeken, zoals een rechtsklik erop. */
    static void searchRavenBody(ServerPlayer player) {
        com.senne.imttech.raven.RavenBodyEntity nearest = null;
        for (com.senne.imttech.raven.RavenBodyEntity body : player.serverLevel().getEntitiesOfClass(
                com.senne.imttech.raven.RavenBodyEntity.class, player.getBoundingBox().inflate(10))) {
            if (nearest == null || body.distanceToSqr(player) < nearest.distanceToSqr(player)) {
                nearest = body;
            }
        }
        if (nearest != null) {
            nearest.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    /** Stap 5: shift-klik op de eerste rugzak in het open scherm (een lichaam), zoals met je muis: hij gaat naar je inventaris. */
    static void takePackFromScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null
                || !(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
            return;
        }
        net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
        for (int i = 0; i < Math.min(9, menu.slots.size()); i++) {
            if (BackpackItem.isBackpack(menu.getSlot(i).getItem())) {
                mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
                return;
            }
        }
    }

    /** Stap 5: de eerste rugzak uit je inventaris in je hand, en dan openritsen (zoals een rechtsklik ermee). */
    static void unzipPackInHand(ServerPlayer player) {
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack pack = inventory.items.get(i);
            if (BackpackItem.isBackpack(pack)) {
                ItemStack held = inventory.getSelected();
                inventory.setItem(inventory.selected, pack);
                if (i != inventory.selected) {
                    inventory.setItem(i, held);
                }
                break;
            }
        }
        com.senne.imttech.backpack.BackpackZip.start(player,
                com.senne.imttech.backpack.BackpackPlace.hand(player, net.minecraft.world.InteractionHand.MAIN_HAND));
    }

    /**
     * Stap 5: 3 x 2 Pegboards tegen de muur van het veldje (ze kijken naar het noorden, naar de camera), met de drie
     * rugzakken in hun kleuren (en hun pouches), en losse MOLLE-pouches eronder.
     */
    static void buildPegboard(ServerLevel level, BlockPos origin) {
        Direction facing = Direction.NORTH;
        BlockPos leftBottom = origin.offset(1, 1, 4);
        for (int du = 0; du < 3; du++) {
            for (int dv = 0; dv < 2; dv++) {
                BlockPos pos = com.senne.imttech.armory.Pegboard.offset(leftBottom, facing, du, dv);
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(pos, com.senne.imttech.registry.ModBlocks.PEGBOARD.get().defaultBlockState()
                        .setValue(com.senne.imttech.armory.PegboardBlock.FACING, facing));
            }
        }
        hang(level, leftBottom, facing, 1, 8, withPouches(pack(BackpackItem.Size.ASSAULT, -1)));
        hang(level, leftBottom, facing, 8, 8, withPouches(pack(BackpackItem.Size.PATROL, -2)));
        hang(level, leftBottom, facing, 16, 6, withPouches(pack(BackpackItem.Size.RUCKSACK, 0x3C44AA)));
        com.senne.imttech.backpack.MolleItem.Kind[] kinds = com.senne.imttech.backpack.MolleItem.Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            hang(level, leftBottom, facing, 1 + i * 3, 2, new ItemStack(com.senne.imttech.backpack.BackpackRegistry.molle(kinds[i])));
        }
    }

    /** Dit item op het Pegboard, met zijn linkeronderhoek op gaatje ({@code u}, {@code v}) van de wand (zoals ArmoryDisplay). */
    private static void hang(ServerLevel level, BlockPos leftBottom, Direction facing, int u, int v, ItemStack stack) {
        int[] size = com.senne.imttech.armory.Pegboard.size(stack, com.senne.imttech.armory.Pegboard.Turn.LEFT);
        double cu = com.senne.imttech.armory.Pegboard.column(leftBottom, facing) * com.senne.imttech.armory.Pegboard.CELLS + u + size[0] / 2.0;
        double cv = leftBottom.getY() * com.senne.imttech.armory.Pegboard.CELLS + v + size[1] / 2.0;
        if (!com.senne.imttech.armory.PegboardBlock.hangAt(null, level, leftBottom, facing, cu, cv, stack,
                com.senne.imttech.armory.Pegboard.Turn.LEFT)) {
            ImtTech.LOGGER.warn("[backpack tour] geen plek op het Pegboard voor {} op {}, {}", stack.getHoverName().getString(), u, v);
        }
    }

    /** Het lichaam het dichtst bij deze speler (binnen 8 blokken), of null. */
    static com.senne.imttech.body.PlayerBodyEntity bodyNear(ServerPlayer player) {
        java.util.List<com.senne.imttech.body.PlayerBodyEntity> bodies = player.serverLevel().getEntitiesOfClass(
                com.senne.imttech.body.PlayerBodyEntity.class, player.getBoundingBox().inflate(8));
        bodies.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
        return bodies.isEmpty() ? null : bodies.getFirst();
    }

    /** Een klik (oppakken of neerleggen) op dit vakje van het open scherm, zoals met je muis. */
    private static void clickSlot(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gameMode != null && mc.screen instanceof BackpackScreen screen) {
            mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
        }
    }

    /** De speler met deze rugzak (en eventueel geweren), van achteren en van voren gefotografeerd. */
    private static void backView(DevScript t, double x, double y, double z, BackpackItem.Size size, int colour, boolean guns,
                                 String backShot, String frontShot) {
        t.onServer(0, player -> wear(player, size, colour, guns));
        t.stand(5, x, y, z, 0, 0);
        t.camera(0, CameraType.THIRD_PERSON_BACK);
        t.shot(12, backShot);
        t.camera(0, CameraType.THIRD_PERSON_FRONT);
        t.shot(12, frontShot);
        t.camera(0, CameraType.FIRST_PERSON);
    }

    // ================================================================ Doorlopen met een open rugzak (echte toetsen)

    /**
     * Met echte toetsen (DevKeys): eerst 1 s lopen zonder rugzak open, dan de rugzak openritsen en met het scherm open 1 s
     * W vasthouden. Je hoort met de rugzak open zo'n 40 % van je gewone afstand te lopen ({@code openWalkSpeed}).
     */
    static void walk(DevScript t) {
        double[] mark = new double[4];
        t.onServer(0, player -> wear(player, BackpackItem.Size.ASSAULT, -1, false));
        t.command(0, "gamemode survival @a");
        t.stand(20, 120.5, -60, 120.5, 0, 10);
        t.step(30, () -> markAt(mark));
        t.step(0, () -> DevKeys.press(GLFW.GLFW_KEY_W, true));
        t.step(20, () -> report("A: 1 s lopen, rugzak dicht", mark, 2));
        t.step(0, () -> DevKeys.press(GLFW.GLFW_KEY_W, false));
        t.onServer(15, Backpacks::openWorn);
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof BackpackScreen);
        t.step(5, () -> markAt(mark));
        t.step(0, () -> DevKeys.press(GLFW.GLFW_KEY_W, true));
        t.step(20, () -> report("B: 1 s lopen, rugzak open", mark, 3));
        t.shot(0, "pk21_walking_with_backpack_open");
        t.step(0, () -> DevKeys.press(GLFW.GLFW_KEY_W, false));
        t.close(10);
        t.step(0, () -> ImtTech.LOGGER.info(String.format(java.util.Locale.ROOT,
                "[dev] backpackwalk %s: dicht %.2f blokken, open %.2f blokken (%.0f %%; verwacht rond %.0f %%, en meer dan 0)",
                mark[3] > 0.2 && mark[3] < mark[2] * 0.7 ? "GOED" : "FOUT", mark[2], mark[3],
                mark[2] > 0 ? mark[3] / mark[2] * 100 : 0, com.senne.imttech.config.ImtConfig.Backpacks.OPEN_WALK_SPEED * 100)));
        t.command(0, "gamemode creative @a");
    }

    private static void markAt(double[] mark) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            mark[0] = player.getX();
            mark[1] = player.getZ();
        }
    }

    private static void report(String label, double[] mark, int into) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        double moved = Math.hypot(player.getX() - mark[0], player.getZ() - mark[1]);
        mark[into] = moved;
        ImtTech.LOGGER.info(String.format(java.util.Locale.ROOT, "[dev] backpackwalk %-28s %.2f blokken, scherm=%s, W aan=%s",
                label, moved, Minecraft.getInstance().screen == null ? "geen" : Minecraft.getInstance().screen.getClass().getSimpleName(),
                Minecraft.getInstance().options.keyUp.isDown()));
    }
}
