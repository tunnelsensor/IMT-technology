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
        place(level, origin.offset(-2, 1, 1), pack(BackpackItem.Size.ASSAULT, -1));
        place(level, origin.offset(0, 1, 1), pack(BackpackItem.Size.PATROL, 0xB02E26));
        place(level, origin.offset(2, 1, 1), pack(BackpackItem.Size.RUCKSACK, -2));
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
            player.getInventory().clearContent();
        }
        Gear.setBack(player, loaded(size, colour));
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
        t.command(10, "gamemode creative @a");
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
