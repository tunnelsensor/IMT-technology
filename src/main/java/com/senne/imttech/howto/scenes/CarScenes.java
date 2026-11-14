package com.senne.imttech.howto.scenes;

import com.senne.imttech.car.DrillHead;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.weapons.EnergyCellItem;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * De animatie van How it works voor de RC Car, de RC Controller en de boorkoppen (hoofdstuk 108): wat hij is, zijn
 * energiecel, zijn programma (dat je zelf schrijft), een gang mijnen (jouw voorbeeld: "breek voor jou, stap naar voor,
 * breek boven jou"), een kamer van 5x3x5 (jouw tweede voorbeeld), de controller, een betere boorkop, en de kofferbak
 * en oppakken.
 *
 * Een nieuwe auto heeft geen programma (jouw wens: "ik wil dat je de rc car vanaf 0 moet programmeren; in de how it
 * works kun je dit wel als voorbeeld geven"). De twee voorbeelden staan daarom hier: als plaatje van de echte editor met
 * die code erin (de rondleiding maakt ze: {@code gradlew runVisualCheck -Ponly=howtopics:car}).
 *
 * De auto in de animatie is een echte RC Car met de gegevens zoals het spel ze bewaart ({@link #car}): met of zonder
 * cel (dan branden zijn lichtjes wel of niet), boren of niet (dan draait zijn boor), en met een boorkop (de kleur van
 * zijn boor).
 */
public final class CarScenes {
    private CarScenes() {}

    /** Zo kijkt de auto in de animatie: naar het oosten (0 = zuid, 90 = west). */
    private static final float EAST = -90.0f;
    /** Zo lang (ticks) boort hij in de animatie aan één blok steen, en zo lang rijdt hij één blok. */
    private static final int DRILL = 9;
    private static final int DRIVE = 8;

    /**
     * Een RC Car zoals het spel hem bewaart: {@code cell} = hoe vol zijn energiecel is (0 tot 100; kleiner dan 0 = geen
     * cel, dan zijn zijn lichtjes uit), {@code drilling} = of zijn boor draait, {@code head} = zijn boorkop.
     */
    static CompoundTag car(float cell, boolean drilling, DrillHead head) {
        CompoundTag tag = new CompoundTag();
        if (cell >= 0) {
            tag.putFloat("Cell", cell);
        }
        tag.putBoolean("Drilling", drilling);
        tag.putString("DrillHead", head.getSerializedName());
        return tag;
    }

    public static List<HowToScene> rcCar() {
        // Je kijkt op de zuid- en de westkant: de auto rijdt naar het oosten langs de zuidkant van de steen, zodat je hem
        // blijft zien (een gang met de voorkant open).
        HowToSceneBuilder s = new HowToSceneBuilder("rc_car", "RC Car").plate(8, 4).height(4);
        CompoundTag off = car(-1, false, DrillHead.IRON);
        CompoundTag idle = car(100, false, DrillHead.IRON);
        CompoundTag drill = car(100, true, DrillHead.IRON);
        Vec3 start = new Vec3(1.5, 1, 2.5);
        var type = ModEntities.RC_CAR.get();

        s.idle(6);
        s.chapter("A car you program");
        Vec3 show = new Vec3(4.0, 2.2, 2.0);
        s.actor("show", type, 120, 0, 35.0f, idle, show);
        s.text(115, show.add(0, 0.7, 0), Side.RIGHT,
                "The *RC Car* is a little car with a drill and a trunk. It does nothing by itself, and a new one has *no"
                        + " program*: you *write it yourself*. Then it mines and builds for you.");
        s.idle(122);

        s.chapter("An Energy Cell");
        s.actor("car", type, 46, 0, EAST, off, start);
        s.text(110, start.add(0, 0.9, 0), Side.RIGHT,
                "It runs on an *Energy Cell*, the one of the laser guns: charge it in a *Charging Station*. [Right Button] the"
                        + " car with it: the cell goes in and you get the old one back. Driving, breaking and placing all use"
                        + " a little.");
        s.idle(20);
        s.rightClick(start.add(0, 0.4, 0), EnergyCellItem.withCharge(EnergyCellItem.FULL));
        s.idle(26);
        s.sound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.6f, 1.5f);
        s.actor("car", type, 80, 0, EAST, idle, start);
        s.meter(70, start.add(0, 1.3, 0), "ENERGY CELL", 1.0f, 1.0f, HowToSceneBuilder.GREEN);
        s.idle(80);

        // Jouw eerste voorbeeld, als plaatje van de editor: zo typ je het zelf. De pijltjes staan van boven naar beneden
        // (dan kruisen hun lijntjes niet).
        s.chapter("You write its program");
        s.actor("car", type, 170, 0, EAST, idle, start);
        s.sneakRightClick(start.add(0, 0.4, 0), ItemStack.EMPTY);
        s.idle(20);
        s.picture(145, "rc_car_code", 427, 240,
                s.arrow(0.19f, 0.16f, "An example. *def* makes a function: a button on the car"),
                s.arrow(0.27f, 0.20f, "*for i in range(20):* does the steps under it 20 times"),
                s.arrow(0.73f, 0.33f, "Everything the car knows: click a line to put it in your code"),
                s.arrow(0.78f, 0.89f, "*Check* looks for mistakes, *Save* puts it on the car"));
        s.idle(150);

        s.chapter("A mining tunnel");
        // Een blok steen van 4 lang, 2 breed en 2 hoog voor de auto: de rij waar hij doorheen boort, en een rij erachter.
        s.place(HowToSceneBuilder.box(new BlockPos(3, 1, 1), new BlockPos(6, 2, 2), pos -> Blocks.STONE.defaultBlockState()), 4);
        s.actor("car", type, 30, 0, EAST, idle, start);
        s.idle(30);
        // Naar de steen toe.
        Vec3 at = new Vec3(2.5, 1, 2.5);
        s.actor("car", type, DRIVE, DRIVE, EAST, idle, start, at);
        s.idle(DRIVE);
        s.text(150, new Vec3(4.5, 3.2, 1.5), Side.RIGHT,
                "*mine()* breaks the block in front, *forward(1)* drives into the gap, *mine_up()* breaks the block above."
                        + " In a loop that is a mining tunnel. What it breaks goes into its *trunk*.");
        for (int step = 0; step < 4; step++) {
            BlockPos front = new BlockPos(3 + step, 1, 2);
            // mine(): de barstjes groeien, dan is het blok weg.
            s.actor("car", type, DRILL, 0, EAST, drill, at);
            s.sound(ModSounds.ROBOT_STEP.get(), 0.25f, 0.6f);
            drill(s, front);
            // forward(1): in het gat.
            Vec3 next = at.add(1, 0, 0);
            s.actor("car", type, DRIVE, DRIVE, EAST, idle, at, next);
            s.idle(DRIVE);
            at = next;
            // mine_up(): het blok erboven.
            s.actor("car", type, DRILL, 0, EAST, drill, at);
            drill(s, front.above());
        }
        s.actor("car", type, 60, 0, EAST, idle, at);
        s.idle(60);

        // Jouw tweede voorbeeld: de kamer van 5x3x5, weer als plaatje van de editor.
        s.chapter("A bigger example: a room");
        s.actor("car", type, 175, 0, EAST, idle, at);
        s.picture(170, "rc_car_code_room", 427, 240,
                s.arrow(0.17f, 0.16f, "*room* carves a room 5 wide, 3 high and 5 deep. Put the car in a corner, with the wall"
                        + " on its left"),
                s.arrow(0.24f, 0.24f, "*mine_up(2)* breaks the two blocks above the car: the room is 3 high"),
                s.arrow(0.21f, 0.45f, "*back(4)* drives back to the start of the lane"),
                s.arrow(0.23f, 0.49f, "After every lane but the last: one lane to the right"));
        s.idle(175);

        s.chapter("The RC Controller");
        Vec3 you = new Vec3(0.5, 1, 2.5);
        s.actor("car", type, 150, 0, EAST, idle, at);
        s.player("you", 150, 0, EAST, Map.of(EquipmentSlot.MAINHAND, new ItemStack(ModItems.RC_CONTROLLER.get())), you);
        s.text(140, you.add(0, 2.3, 0), Side.RIGHT,
                "Link an *RC Controller*: [Right Button] the car with it. Then [Right Button] anywhere, up to %s blocks away: you"
                        + " see the functions of your car. Pick one and the car runs it *from where it stands*.",
                HowToSceneBuilder.number(ImtConfig.RcCar.CONTROL_RANGE));
        s.idle(30);
        s.signal(20, HowToSceneBuilder.CYAN, you.add(0, 1.2, 0), at.add(0, 0.6, 0));
        s.idle(20);
        s.picture(95, "rc_car_screen", 338, 238,
                s.arrow(0.92f, 0.06f, "What it is doing now"),
                s.arrow(0.30f, 0.42f, "The functions you wrote: click one to run it"),
                s.arrow(0.78f, 0.51f, "Drive it by hand, one step per click"),
                s.arrow(0.17f, 0.93f, "*Start* runs *when started:*, *Stop* stops what it does"));
        s.idle(100);

        // De boorkop: de ijzeren kop van een nieuwe auto is traag; met rechtsklik zet je er een betere op.
        s.chapter("A better drill head");
        Vec3 meterAt = at.add(0, 1.3, 0);
        s.actor("car", type, 50, 0, EAST, idle, at);
        s.text(240, at.add(0, 0.9, 0), Side.LEFT,
                "A new car has an *Iron Drill Head*: it breaks a block of stone in %s seconds. [Right Button] the car with a"
                        + " better head and you get the old one back. *Steel*, *Titanium* and *Netherite* are each faster:"
                        + " Netherite needs %s seconds. Titanium and Netherite also break *obsidian*.",
                seconds(DrillHead.IRON), seconds(DrillHead.NETHERITE));
        s.meter(50, meterAt, "IRON DRILL HEAD", DrillHead.IRON.speed(), DrillHead.IRON.speed(), DrillHead.IRON.colour());
        s.idle(50);
        DrillHead before = DrillHead.IRON;
        for (DrillHead head : List.of(DrillHead.STEEL, DrillHead.TITANIUM, DrillHead.NETHERITE)) {
            s.actor("car", type, HowToSceneBuilder.CLICK, 0, EAST, car(100, false, before), at);
            s.rightClick(at.add(0, 0.4, 0), new ItemStack(head.item()));
            s.idle(HowToSceneBuilder.CLICK);
            s.sound(SoundEvents.ANVIL_USE, 0.3f, 1.6f);
            int stay = head == DrillHead.NETHERITE ? 70 : 38;
            s.actor("car", type, stay, 0, EAST, car(100, false, head), at);
            s.meter(stay, meterAt, head.displayName().toUpperCase(Locale.ROOT), before.speed(), head.speed(), head.colour());
            s.idle(stay);
            before = head;
        }
        CompoundTag best = car(100, false, DrillHead.NETHERITE);

        s.chapter("Trunk and pick up");
        s.actor("car", type, HowToSceneBuilder.UNTIL_END, 0, EAST, best, at);
        s.rightClick(at.add(0, 0.4, 0), ItemStack.EMPTY);
        s.text(130, at.add(0, 1.6, 0), Side.LEFT,
                "[Right Button] the car: its trunk opens. *unload()* empties it into a chest in front of it, *place()* builds"
                        + " with the blocks in it. *Pick up* on its screen puts the car in your inventory, with its program, its"
                        + " cell, its drill head and its trunk.");
        s.idle(140);
        return List.of(s.build());
    }

    /**
     * Hoe lang deze boorkop over een blok steen doet, in seconden met één cijfer achter de komma (uit de config, zodat de
     * tekst klopt met jouw wereld). Steen heeft hardheid 1,5 en een pickaxe telt voor 6: zie car/CarWork.progressPerTick.
     */
    private static String seconds(DrillHead head) {
        double perTick = 6.0 * ImtConfig.RcCar.MINE_SPEED * head.speed() / 1.5 / 30.0;
        return HowToSceneBuilder.number(1.0 / Math.max(1.0e-6, perTick) / 20.0);
    }

    /** De boor op dit blok: de barstjes groeien in {@link #DRILL} ticks, en dan is het weg. */
    private static void drill(HowToSceneBuilder s, BlockPos pos) {
        for (int tick = 0; tick < DRILL; tick++) {
            s.crack(pos, Math.min(9, tick * 10 / DRILL));
            s.idle(1);
        }
        s.crack(pos, -1);
        s.remove(pos);
        s.sound(SoundEvents.STONE_BREAK, 0.6f, 0.9f);
    }
}
