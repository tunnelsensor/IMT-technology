package com.senne.imttech.howto.scenes;

import com.senne.imttech.car.DrillHead;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.storage.ChargingPadBlock;
import com.senne.imttech.storage.ChestSensorBlock;
import com.senne.imttech.storage.StorageInterfaceBlock;
import com.senne.imttech.storage.StorageRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * De animatie van How it works voor het opslagsysteem (hoofdstuk 113, jouw wens: "een storage system via de robots: je
 * kunt een item orderen via een storage interface, deze interface activeert een robot die je hebt geprogrammeerd"): de
 * Storage Interface, de Chest Sensor en de Charging Pad.
 *
 * Het toneel: links de interface met de auto ervoor (zijn "thuis"), en rechts een gang langs vier stapels tonnen van drie
 * hoog. De auto in de animatie doet wat het voorbeeldprogramma doet (CarProgram.EXAMPLE_STORAGE): de gang in, met zijn arm
 * langs de tonnen, en terug naar de interface. De twee plaatjes (het echte scherm, en sinds hoofdstuk 119 de app Logic
 * met het programma als RC program op je server) maakt de
 * rondleiding: {@code gradlew runVisualCheck -Ponly=howtopics:storage}.
 */
public final class StorageScenes {
    private StorageScenes() {}

    /** Zo kijkt de auto: naar het oosten (0 = zuid, 90 = west), en naar het noorden. */
    private static final float EAST = -90.0f;
    private static final float NORTH = 180.0f;
    /** Zo lang (ticks) rijdt hij één blok, en zo lang doet zijn arm over één blok. */
    private static final int DRIVE = 8;
    private static final int ARM = 8;

    /** De auto van de animatie, met zijn arm op deze hoogte (0, 1 of 2) en een cel die zo vol is (0 tot 100). */
    private static CompoundTag car(int arm, float cell) {
        // Hoofdstuk 118: om bij kisten hoger dan het blok vóór hem te komen, heeft hij een RC Robot Arm.
        CompoundTag tag = CarScenes.car(cell, false, DrillHead.IRON, true);
        tag.putByte("Arm", (byte) arm);
        return tag;
    }

    private static BlockState barrel() {
        return Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.SOUTH);
    }

    public static List<HowToScene> storage() {
        HowToSceneBuilder s = new HowToSceneBuilder("storage", "Storage system").plate(7, 4).height(5);
        var type = ModEntities.RC_CAR.get();
        BlockPos station = new BlockPos(1, 1, 1);
        BlockPos sensor = new BlockPos(3, 4, 1);
        Vec3 home = new Vec3(1.5, 1, 2.5);
        Vec3 atStack = new Vec3(3.5, 1, 2.5);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);

        s.idle(6);
        s.chapter("A storage room that works for you");
        s.place(station, StorageRegistry.STORAGE_INTERFACE.get().defaultBlockState().setValue(StorageInterfaceBlock.FACING, Direction.SOUTH));
        s.idle(6);
        // Vier stapels tonnen van drie hoog, langs de gang.
        s.place(HowToSceneBuilder.box(new BlockPos(2, 1, 1), new BlockPos(5, 3, 1), pos -> barrel()), 3);
        s.idle(14);
        s.actor("car", type, 120, 0, NORTH, car(0, 100), home);
        s.outline(110, station, station, HowToSceneBuilder.CYAN);
        s.text(125, HowToSceneBuilder.top(station), Side.RIGHT,
                "The *Storage Interface* shows what is in your chests. You *order* an item there, and an *RC Car* fetches it: with"
                        + " the program *you* wrote. It can also put away what you hand in.");
        s.idle(130);

        s.chapter("What it knows");
        s.actor("car", type, 150, 0, NORTH, car(0, 100), home);
        s.place(sensor, StorageRegistry.CHEST_SENSOR.get().defaultBlockState().setValue(ChestSensorBlock.FACING, Direction.UP));
        s.sound(SoundEvents.STONE_BUTTON_CLICK_ON, 0.4f, 1.5f);
        s.idle(8);
        s.signal(16, HowToSceneBuilder.CYAN, new Vec3(3.5, 4.2, 1.5), new Vec3(1.5, 2.2, 1.5));
        s.text(140, new Vec3(3.5, 4.4, 1.5), Side.RIGHT,
                "Stick a *Chest Sensor* on a chest, barrel or hopper: the interface sees it *live*, over a network cable or a"
                        + " *Router*. It needs no power. Chests without a sensor it learns when a car *looks inside*.");
        s.idle(145);

        // Het echte scherm (het plaatje maakt de rondleiding).
        s.chapter("Order an item");
        s.actor("car", type, 180, 0, NORTH, car(0, 100), home);
        s.rightClick(HowToSceneBuilder.face(station, Direction.SOUTH), ItemStack.EMPTY);
        s.idle(26);
        // De pijltjes van boven naar beneden (bij gelijke hoogte eerst het linkse): dan kruisen hun lijntjes niet. Elk stipje
        // staat naast de tekst van het scherm, niet erop.
        s.picture(150, "storage_screen", 300, 222,
                s.arrow(0.848f, 0.047f, "*Store* takes items in, *Cars* links your RC Cars"),
                s.arrow(0.30f, 0.19f, "What is in your storage: click an item"),
                s.arrow(0.62f, 0.395f, "How many, then *Order*"),
                s.arrow(0.20f, 0.575f, "*PICK-UP*: here the car puts what it fetched"),
                s.arrow(0.76f, 0.567f, "Your orders, and how far they are"));
        s.idle(155);

        // De auto doet het werk: de gang in, de arm omhoog, nemen, terug, en afleveren.
        s.chapter("The car fetches it");
        s.actor("car", type, 10, 10, EAST, car(0, 100), home, home);
        s.idle(10);
        s.actor("car", type, 2 * DRIVE, 2 * DRIVE, EAST, car(0, 100), home, atStack);
        s.text(200, new Vec3(3.5, 4.6, 2.5), Side.RIGHT,
                "The car drives where its program says. With an *RC Robot Arm* it reaches *six chests high* (*arm_up()*)."
                        + " *if item(...) in chest():* looks inside, *take(...)* takes it into the trunk. Back home, *put()*"
                        + " delivers it.");
        s.idle(2 * DRIVE);
        s.actor("car", type, ARM, 0, NORTH, car(0, 100), atStack);
        s.idle(ARM);
        for (int level = 1; level <= 2; level++) {
            s.actor("car", type, ARM + 6, 0, NORTH, car(level, 100), atStack);
            s.sound(com.senne.imttech.registry.ModSounds.ROBOT_STEP.get(), 0.2f, 1.2f);
            s.idle(ARM + 6);
        }
        // Daar ligt het: het ijzer springt uit de ton in de kofferbak.
        s.actor("car", type, 30, 0, NORTH, car(2, 100), atStack);
        s.outline(28, new BlockPos(3, 3, 1), new BlockPos(3, 3, 1), HowToSceneBuilder.GREEN);
        s.item(24, new Vec3(3.5, 3.5, 2.1), iron, 0.6f);
        s.sound(SoundEvents.ITEM_PICKUP, 0.4f, 1.3f);
        s.idle(30);
        s.actor("car", type, 2 * ARM, 0, NORTH, car(0, 100), atStack);
        s.idle(2 * ARM);
        s.actor("car", type, 2 * DRIVE, 2 * DRIVE, EAST, car(0, 100), atStack, home);
        s.idle(2 * DRIVE);
        s.actor("car", type, 60, 0, NORTH, car(0, 100), home);
        s.item(40, new Vec3(1.5, 1.9, 2.0), iron, 0.6f);
        s.sound(SoundEvents.ITEM_PICKUP, 0.4f, 0.9f);
        s.outline(50, station, station, HowToSceneBuilder.GREEN);
        s.idle(60);

        // Het programma, als plaatje van de app Logic (hoofdstuk 119: een RC program op je server): zo typ je het zelf.
        s.chapter("You write the program");
        s.actor("car", type, 200, 0, NORTH, car(0, 100), home);
        s.sneakRightClick(home.add(0, 0.4, 0), ItemStack.EMPTY);
        s.idle(20);
        // Vijf korte teksten (elk twee regels): meer past er niet onder elkaar naast het plaatje. Elk stipje staat net
        // achter de regel code waar het over gaat.
        s.picture(175, "logic_rc_storage", 427, 240,
                s.arrow(0.24f, 0.232f, "An *RC program* on your server, loaded onto the car"),
                s.arrow(0.60f, 0.311f, "*when ordered(\"fetch\"):* runs for an order"),
                s.arrow(0.83f, 0.436f, "*order.left*: how many are still missing"),
                s.arrow(0.47f, 0.603f, "*look()*: your own def, checks a stack"),
                s.arrow(0.46f, 0.769f, "Back home, *put()* lays it in the interface"));
        s.idle(180);

        s.chapter("Storing, and a Charging Pad");
        BlockPos pad = new BlockPos(1, 1, 2);
        s.place(pad, StorageRegistry.CHARGING_PAD.get().defaultBlockState().setValue(ChargingPadBlock.CHARGING, true));
        s.actor("car", type, HowToSceneBuilder.UNTIL_END, 0, NORTH, car(0, 40), home.add(0, 0.125, 0));
        s.meter(150, home.add(0, 1.4, 0), "ENERGY CELL", 0.4f, 1.0f, HowToSceneBuilder.GREEN);
        // De tekst wijst naar de laadplaat (de voorste hoek): boven de auto hangt het balkje van zijn cel al.
        s.text(150, home.add(0.45, 0.1, 0.45), Side.RIGHT,
                "On the tab *Store* you hand items in: the car takes them here and puts them away. A *Charging Pad* charges the"
                        + " car that stands on it from the grid (%s energy per tick): park it there, and it never runs empty.",
                HowToSceneBuilder.number(ImtConfig.RcCar.PAD_RATE));
        s.idle(160);
        return List.of(s.build());
    }
}
