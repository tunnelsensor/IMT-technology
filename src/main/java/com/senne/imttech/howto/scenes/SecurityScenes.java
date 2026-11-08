package com.senne.imttech.howto.scenes;

import com.senne.imttech.energy.CableKind;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.logic.InputDeviceBlock;
import com.senne.imttech.logic.InputLight;
import com.senne.imttech.logic.KeycardData;
import com.senne.imttech.logic.KeycardItem;
import com.senne.imttech.redstone.RelayBlock;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.security.CameraBlock;
import com.senne.imttech.security.SecurityCoreBlock;
import com.senne.imttech.shield.LaserEmitterBlock;
import com.senne.imttech.shield.LaserWallBlock;
import com.senne.imttech.shield.ShieldColor;
import com.senne.imttech.station.HeatGeneratorBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * De animaties van het tabblad Security (hoofdstuk 63). Eerst de belangrijkste (jouw keuze van 8 november: "de
 * belangrijkste topics die simpel zijn en veel in de mod gebruikt worden, denk maar aan keycards, security core"): de
 * kern, keycards en lezers, het codepaneel en laserpoorten. Hoe het echt werkt staat in LEESMIJ ("Het netwerk", "De
 * server", "Rayshield").
 */
public final class SecurityScenes {
    private SecurityScenes() {}

    private static final int WHITE = 0xF2F6FA;
    private static final int POE_GREEN = 0x5CFF7A;

    // ================================================================ Blokken in de juiste stand

    static BlockState core(Direction facing, boolean claimed) {
        return ModBlocks.SECURITY_CORE.get().defaultBlockState().setValue(SecurityCoreBlock.FACING, facing)
                .setValue(SecurityCoreBlock.CLAIMED, claimed);
    }

    static BlockState heat(Direction facing) {
        return ModBlocks.HEAT_GENERATOR.get().defaultBlockState().setValue(HeatGeneratorBlock.FACING, facing)
                .setValue(HeatGeneratorBlock.LIT, true);
    }

    static BlockState reader(Direction facing, InputLight light) {
        return ModBlocks.KEYCARD_READER.get().defaultBlockState().setValue(InputDeviceBlock.FACING, facing)
                .setValue(InputDeviceBlock.LIGHT, light);
    }

    static BlockState panel(Direction facing, InputLight light) {
        return ModBlocks.CODE_PANEL.get().defaultBlockState().setValue(InputDeviceBlock.FACING, facing)
                .setValue(InputDeviceBlock.LIGHT, light);
    }

    static BlockState camera(Direction facing) {
        return ModBlocks.SECURITY_CAMERA.get().defaultBlockState().setValue(CameraBlock.FACE, AttachFace.FLOOR)
                .setValue(CameraBlock.FACING, facing).setValue(CameraBlock.ONLINE, true);
    }

    static BlockState door(DoubleBlockHalf half, boolean open) {
        return Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, half)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, open);
    }

    static BlockState relay(boolean powered) {
        return ModBlocks.RELAY.get().defaultBlockState().setValue(RelayBlock.POWERED, powered);
    }

    /** Een geschreven keycard, om in de hand te hebben (de kleur 1 tot 8, zie KeycardData). */
    static ItemStack card(int colour, String label, int number) {
        ItemStack stack = new ItemStack(ModItems.KEYCARD.get());
        KeycardItem.write(stack, new KeycardData("10.42.0.7", "base", number, colour, label));
        return stack;
    }

    // ================================================================ De Security Core

    /*
     * De opstelling van de kern (ook in de animatie van de netwerkkabels): de kern met zijn generator links, een lezer op
     * een pilaar rechtsvoor, en een camera op een paal rechtsachter (achter de kern, zodat hij niets verbergt).
     */
    static final BlockPos CORE = new BlockPos(2, 1, 2);
    static final BlockPos CORE_GEN = new BlockPos(1, 1, 2);
    static final BlockPos CORE_READER = new BlockPos(4, 2, 3);
    static final BlockPos CORE_CAMERA = new BlockPos(4, 3, 0);
    /** Van de oostkant van de kern over de vloer naar de pilaar, en die op naar de lezer. */
    static final Vec3[] CORE_TO_READER = {new Vec3(3.0, 1.05, 2.5), new Vec3(3.5, 1.05, 2.5), new Vec3(3.5, 1.05, 3.5),
            new Vec3(4.95, 1.05, 3.5), new Vec3(4.95, 2.19, 3.5)};
    /** Van de noordkant van de kern naar de paal, en die op naar de camera. */
    static final Vec3[] CORE_TO_CAMERA = {new Vec3(2.5, 1.05, 2.0), new Vec3(2.5, 1.05, 0.5), new Vec3(4.2, 1.05, 0.5),
            new Vec3(4.2, 3.0, 0.5)};

    /** De pilaar van de lezer en de paal van de camera. */
    static Map<BlockPos, BlockState> coreProps() {
        Map<BlockPos, BlockState> props = new LinkedHashMap<>();
        props.put(new BlockPos(5, 1, 3), Blocks.STONE_BRICKS.defaultBlockState());
        props.put(new BlockPos(5, 2, 3), Blocks.STONE_BRICKS.defaultBlockState());
        props.put(new BlockPos(4, 1, 0), Blocks.STONE_BRICK_WALL.defaultBlockState());
        props.put(new BlockPos(4, 2, 0), Blocks.STONE_BRICK_WALL.defaultBlockState());
        return props;
    }

    /**
     * De kern: het hart van je basis, op stroom, met kabels naar je apparaten (zonder kabel doet een apparaat niets), de
     * desktop (Security OS) als plaatje, en dat alleen jij hem opent.
     */
    public static List<HowToScene> core() {
        HowToSceneBuilder s = new HowToSceneBuilder("security_core", "The Security Core").plate(7, 6);
        s.idle(10);
        s.chapter("The heart of your base");
        s.place(CORE, core(Direction.SOUTH, false));
        s.idle(12);
        s.change(CORE, core(Direction.SOUTH, true));
        s.outline(70, CORE, CORE, HowToSceneBuilder.CYAN);
        s.text(80, HowToSceneBuilder.top(CORE), Side.RIGHT,
                "The *Security Core* is the heart of your base. Readers, code panels, cameras, turrets and laser gates all"
                        + " connect to it.");
        s.idle(85);

        s.chapter("Power");
        s.place(CORE_GEN, heat(Direction.WEST));
        s.idle(8);
        s.text(70, HowToSceneBuilder.face(CORE_GEN, Direction.WEST), Side.LEFT,
                "It runs on power: a generator right against it, or a *Power Cable*.");
        s.idle(75);

        s.chapter("Cables to your devices");
        s.place(coreProps(), HowToSceneBuilder.DROP);
        s.idle(6);
        s.place(CORE_READER, reader(Direction.WEST, InputLight.OFF));
        s.idle(4);
        s.place(CORE_CAMERA, camera(Direction.SOUTH).setValue(CameraBlock.ONLINE, false));
        s.idle(12);
        Vec3[] toReader = CORE_TO_READER;
        Vec3[] toCamera = CORE_TO_CAMERA;
        ItemStack poe = new ItemStack(ModItems.POE_CABLE.get());
        s.holdRight(28, poe, toReader);
        s.cable(CableKind.POE, 28, toReader);
        s.idle(32);
        s.holdRight(24, poe, toCamera);
        s.cable(CableKind.POE, 24, toCamera);
        s.idle(24);
        s.change(CORE_CAMERA, camera(Direction.SOUTH));
        s.signal(14, POE_GREEN, toReader);
        s.text(90, new Vec3(3.5, 1.05, 3.0), Side.RIGHT,
                "Draw a *Network Cable*, or a *PoE Cable* (network and power in one), from the core to every security device."
                        + " Without a cable a device does nothing.");
        s.idle(95);

        s.chapter("The desktop");
        Vec3 front = HowToSceneBuilder.face(CORE, Direction.SOUTH);
        s.text(40, front, Side.RIGHT, "Right-click the core: its desktop, *Security OS*.");
        s.rightClick(front, ItemStack.EMPTY);
        s.idle(30);
        s.picture(140, "security_os", 426, 240,
                s.arrow(0.048f, 0.528f, "*Devices*: everything on your network"),
                s.arrow(0.273f, 0.528f, "*Keycards*: write and block cards"),
                s.arrow(0.385f, 0.528f, "*Zone*: the area it protects"),
                s.arrow(0.273f, 0.711f, "*Logic*: program what your base does"));
        s.idle(145);

        s.chapter("Only yours");
        s.outline(80, CORE, CORE, HowToSceneBuilder.CYAN);
        s.text(80, HowToSceneBuilder.top(CORE), Side.RIGHT,
                "Only you open its desktop; others get *Access denied*. Mine it and it keeps all its settings.");
        s.idle(85);
        return List.of(s.build());
    }

    // ================================================================ Een deur met een lezer of een paneel

    /** De muur met de deur: x 1 tot 5 op z = 2, drie hoog; de deur op x = 3, het relais "Lab lock" ernaast. */
    private static final BlockPos DOOR_LOW = new BlockPos(3, 1, 2);
    private static final BlockPos DOOR_HIGH = new BlockPos(3, 2, 2);
    private static final BlockPos LOCK = new BlockPos(2, 1, 2);
    /** De lezer of het paneel: naast de deur, tegen de zuidkant van de muur. */
    private static final BlockPos DEVICE = new BlockPos(4, 2, 3);
    private static final BlockPos DOOR_CORE = new BlockPos(6, 1, 4);
    private static final BlockPos DOOR_GEN = new BlockPos(6, 1, 5);
    /** De PoE-kabel van de kern naar de lezer: over de vloer, dan de muur op. */
    private static final Vec3[] TO_DEVICE = {new Vec3(6.0, 1.05, 4.5), new Vec3(4.5, 1.05, 4.5), new Vec3(4.5, 1.05, 3.05),
            new Vec3(4.5, 2.19, 3.05)};
    private static final Vec3[] FROM_DEVICE = {TO_DEVICE[3], TO_DEVICE[2], TO_DEVICE[1], TO_DEVICE[0]};

    /** De muur, de deur (dicht) en het relais. */
    private static void doorWall(HowToSceneBuilder s) {
        Map<BlockPos, BlockState> wall = HowToSceneBuilder.box(new BlockPos(1, 1, 2), new BlockPos(5, 3, 2), pos -> {
            if (pos.equals(DOOR_LOW) || pos.equals(DOOR_HIGH)) {
                return null;
            }
            return pos.equals(LOCK) ? relay(false) : Blocks.STONE_BRICKS.defaultBlockState();
        });
        s.place(wall, HowToSceneBuilder.DROP);
        s.idle(6);
        Map<BlockPos, BlockState> door = new LinkedHashMap<>();
        door.put(DOOR_LOW, door(DoubleBlockHalf.LOWER, false));
        door.put(DOOR_HIGH, door(DoubleBlockHalf.UPPER, false));
        s.place(door, HowToSceneBuilder.DROP);
    }

    /** De kern met zijn generator rechts, en de PoE-kabel naar de lezer of het paneel. */
    private static void connect(HowToSceneBuilder s) {
        s.place(DOOR_CORE, core(Direction.WEST, true));
        s.idle(4);
        s.place(DOOR_GEN, heat(Direction.WEST));
        s.idle(10);
        s.holdRight(28, new ItemStack(ModItems.POE_CABLE.get()), TO_DEVICE);
        s.cable(CableKind.POE, 28, TO_DEVICE);
        s.idle(30);
    }

    /** De deur opent 3 seconden: het relais "Lab lock" krijgt stroom, en de deur gaat open (en weer dicht). */
    private static void openDoor(HowToSceneBuilder s, int seconds) {
        s.change(LOCK, relay(true));
        s.change(DOOR_LOW, door(DoubleBlockHalf.LOWER, true));
        s.change(DOOR_HIGH, door(DoubleBlockHalf.UPPER, true));
        s.sound(SoundEvents.IRON_DOOR_OPEN, 0.5f, 1.0f);
        s.idle(seconds * 20);
        s.change(LOCK, relay(false));
        s.change(DOOR_LOW, door(DoubleBlockHalf.LOWER, false));
        s.change(DOOR_HIGH, door(DoubleBlockHalf.UPPER, false));
        s.sound(SoundEvents.IRON_DOOR_CLOSE, 0.5f, 1.0f);
    }

    /** Een signaal van de lezer naar de kern en het antwoord terug (wit over netwerk, groen over PoE). */
    private static void askTheCore(HowToSceneBuilder s) {
        s.signal(14, POE_GREEN, FROM_DEVICE);
        s.idle(16);
        s.signal(14, POE_GREEN, TO_DEVICE);
        s.idle(14);
    }

    // ================================================================ Keycards en lezers

    /**
     * Keycards: een lezer naast de deur, met een PoE-kabel aan de kern; kaarten schrijf je in de app Keycards; je diagram
     * in Logic beslist (beide als plaatje); een goede kaart geeft groen en de deur gaat open, een verkeerde geeft rood.
     */
    public static List<HowToScene> keycards() {
        HowToSceneBuilder s = new HowToSceneBuilder("keycards", "Keycards and readers").plate(7, 6);
        s.idle(8);
        s.chapter("A reader at the door");
        doorWall(s);
        s.idle(10);
        s.place(DEVICE, reader(Direction.SOUTH, InputLight.OFF));
        s.outline(70, DEVICE, DEVICE, HowToSceneBuilder.CYAN);
        s.text(75, HowToSceneBuilder.face(DEVICE, Direction.SOUTH), Side.RIGHT,
                "Put a *Keycard Reader* on the wall next to your door.");
        s.idle(80);

        s.chapter("Connect it");
        connect(s);
        s.text(80, new Vec3(4.5, 1.05, 4.0), Side.LEFT,
                "Connect it to your *Security Core* with a *PoE Cable*: network and power in one.");
        s.idle(85);

        s.chapter("Write a card");
        s.picture(130, "keycards_app", 426, 240,
                s.arrow(0.18f, 0.235f, "Write a blank *Keycard*: a name and a colour"),
                s.arrow(0.23f, 0.55f, "*Groups*: a card can be in more than one"),
                s.arrow(0.55f, 0.46f, "Your cards, each with its own number"),
                s.arrow(0.93f, 0.41f, "*Block* a lost or stolen card"));
        s.idle(135);

        s.chapter("Your diagram decides");
        s.picture(130, "logic", 426, 240,
                s.arrow(0.40f, 0.228f, "Runs when a card is read at the reader *Lab door*"),
                s.arrow(0.47f, 0.27f, "Is the card in the group *lab*?"),
                s.arrow(0.40f, 0.31f, "*accept()*: the lamp turns green"),
                s.arrow(0.50f, 0.353f, "The relay *Lab lock* opens the door for 3 seconds"));
        s.idle(135);

        s.chapter("Swipe your card");
        Vec3 front = HowToSceneBuilder.face(DEVICE, Direction.SOUTH);
        s.rightClick(front, card(2, "Lab access", 1001));
        s.idle(8);
        s.change(DEVICE, reader(Direction.SOUTH, InputLight.BUSY));
        askTheCore(s);
        s.change(DEVICE, reader(Direction.SOUTH, InputLight.OK));
        s.sound(ModSounds.IMT_CONFIRM.get(), 0.4f, 1.0f);
        s.text(70, front, Side.RIGHT,
                "Right-click the reader with your card. Your diagram says yes: green, and the door opens for 3 seconds.");
        openDoor(s, 3);
        s.change(DEVICE, reader(Direction.SOUTH, InputLight.OFF));
        s.idle(20);

        s.chapter("The wrong card");
        s.rightClick(front, card(6, "Visitor", 1002));
        s.idle(8);
        s.change(DEVICE, reader(Direction.SOUTH, InputLight.BUSY));
        askTheCore(s);
        s.change(DEVICE, reader(Direction.SOUTH, InputLight.DENIED));
        s.sound(ModSounds.IMT_DENIED.get(), 0.4f, 1.0f);
        s.text(75, front, Side.RIGHT,
                "A card that is not in the group: red, and the door stays shut. Your diagram can also sound the alarm.");
        s.idle(80);
        s.change(DEVICE, reader(Direction.SOUTH, InputLight.OFF));
        s.idle(10);
        return List.of(s.build());
    }

    // ================================================================ Het codepaneel

    /** Het codepaneel: net als een lezer, maar met een code; het scherm met de toetsen en het diagram als plaatje. */
    public static List<HowToScene> codePanel() {
        HowToSceneBuilder s = new HowToSceneBuilder("code_panel", "The code panel").plate(7, 6);
        s.idle(8);
        s.chapter("A code panel");
        doorWall(s);
        s.idle(10);
        s.place(DEVICE, panel(Direction.SOUTH, InputLight.OFF));
        s.idle(8);
        connect(s);
        s.text(85, HowToSceneBuilder.face(DEVICE, Direction.SOUTH), Side.RIGHT,
                "A *Code Panel* opens a door with a code instead of a card. Connect it like a reader, with a *PoE Cable* to"
                        + " your *Security Core*.");
        s.idle(90);

        s.chapter("Type the code");
        Vec3 front = HowToSceneBuilder.face(DEVICE, Direction.SOUTH);
        s.rightClick(front, ItemStack.EMPTY);
        s.idle(24);
        s.picture(110, "code_panel", 94, 150,
                s.arrow(0.5f, 0.19f, "The code you typed (as stars)"),
                s.arrow(0.5f, 0.55f, "Click the keys, or type on your keyboard"),
                s.arrow(0.77f, 0.855f, "*OK* (or Enter) sends it"));
        s.idle(115);

        s.chapter("Your diagram checks it");
        s.picture(120, "logic", 426, 240,
                s.arrow(0.40f, 0.519f, "Runs when someone enters a code at *Front door*"),
                s.arrow(0.45f, 0.561f, "Is it the right code?"),
                s.arrow(0.50f, 0.644f, "Then the relay *Lab lock* opens the door"));
        s.idle(125);

        s.chapter("Open sesame");
        s.change(DEVICE, panel(Direction.SOUTH, InputLight.BUSY));
        askTheCore(s);
        s.change(DEVICE, panel(Direction.SOUTH, InputLight.OK));
        s.sound(ModSounds.IMT_CONFIRM.get(), 0.4f, 1.0f);
        s.text(75, front, Side.RIGHT, "The right code: green, and the door opens. A wrong code: red, and it stays shut.");
        openDoor(s, 3);
        s.change(DEVICE, panel(Direction.SOUTH, InputLight.OFF));
        s.idle(20);
        return List.of(s.build());
    }

    // ================================================================ Laserpoorten

    /** De poort: drie zenders boven elkaar op x = 2, z = 0, die naar het zuiden schieten, tot de pilaar op z = 4. */
    private static final BlockPos EMITTER = new BlockPos(2, 1, 0);
    private static final BlockPos LASER_GEN = new BlockPos(1, 1, 0);

    static BlockState emitter(boolean on, ShieldColor colour) {
        return ModBlocks.LASER_EMITTER.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.SOUTH)
                .setValue(LaserEmitterBlock.POWERED, on).setValue(LaserEmitterBlock.COLOR, colour);
    }

    static BlockState wall(ShieldColor colour) {
        return ModBlocks.LASER_WALL.get().defaultBlockState().setValue(LaserWallBlock.AXIS_FACING, Direction.SOUTH)
                .setValue(LaserWallBlock.COLOR, colour);
    }

    /** De wand schuift uit de zenders naar voren (een plek per 3 ticks), van rij {@code fromY} tot {@code toY}. */
    private static void slideOut(HowToSceneBuilder s, int fromY, int toY, ShieldColor colour) {
        s.sound(ModSounds.RAYSHIELD_ON.get(), 0.4f, 1.0f);
        for (int z = 1; z <= 3; z++) {
            for (int y = fromY; y <= toY; y++) {
                s.change(new BlockPos(2, y, z), wall(colour));
            }
            s.idle(3);
        }
    }

    /** En weer terug de zenders in. */
    private static void slideIn(HowToSceneBuilder s) {
        s.sound(ModSounds.RAYSHIELD_OFF.get(), 0.4f, 1.0f);
        for (int z = 3; z >= 1; z--) {
            s.clear(new BlockPos(2, 1, z), new BlockPos(2, 2, z), new BlockPos(2, 3, z));
            s.idle(3);
        }
    }

    /**
     * Laserpoorten: een zender schiet een wand tot het eerste blok, aan zolang hij stroom heeft; zenders tegen elkaar zijn
     * één poort; je schakelt hem op zijn naam; en het instelscherm (sneak + rechtsklik met een lege hand) als plaatje.
     */
    public static List<HowToScene> laserGate() {
        HowToSceneBuilder s = new HowToSceneBuilder("laser_emitter", "Laser gates").plate(5, 6);
        s.idle(8);
        s.chapter("A wall of energy");
        s.place(HowToSceneBuilder.box(new BlockPos(2, 1, 4), new BlockPos(2, 3, 4), pos -> Blocks.STONE_BRICKS.defaultBlockState()),
                HowToSceneBuilder.DROP);
        s.idle(8);
        s.place(EMITTER, emitter(false, ShieldColor.BLUE));
        s.idle(8);
        s.place(LASER_GEN, heat(Direction.WEST));
        s.idle(10);
        s.change(EMITTER, emitter(true, ShieldColor.BLUE));
        slideOut(s, 1, 1, ShieldColor.BLUE);
        s.text(85, HowToSceneBuilder.face(EMITTER, Direction.SOUTH), Side.RIGHT,
                "A *Laser Emitter* shoots a wall of energy up to the first block in front of it. Nobody gets through.");
        s.idle(90);

        s.chapter("On with power");
        s.outline(70, LASER_GEN, LASER_GEN, HowToSceneBuilder.ORANGE);
        s.text(75, HowToSceneBuilder.face(LASER_GEN, Direction.SOUTH), Side.RIGHT,
                "It stays on as long as it has power: a generator against it, or a *Power Cable*. One emitter is enough.");
        s.idle(80);

        s.chapter("A taller gate");
        s.place(EMITTER.above(), emitter(true, ShieldColor.BLUE));
        s.idle(6);
        s.place(EMITTER.above(2), emitter(true, ShieldColor.BLUE));
        s.idle(8);
        slideOut(s, 2, 3, ShieldColor.BLUE);
        s.text(80, HowToSceneBuilder.face(EMITTER.above(2), Direction.SOUTH), Side.RIGHT,
                "Emitters against each other are one gate: they share one cable and one name.");
        s.idle(85);

        s.chapter("Switch it by name");
        slideIn(s);
        for (int y = 1; y <= 3; y++) {
            s.change(EMITTER.above(y - 1), emitter(false, ShieldColor.BLUE));
        }
        s.text(110, HowToSceneBuilder.face(EMITTER.above(), Direction.SOUTH), Side.RIGHT,
                "Turn the gate off and on by its name, like *Gate 1*: with your glasses ([']), your voice, or a diagram:"
                        + " off(\"Gate 1\").");
        s.idle(50);
        for (int y = 1; y <= 3; y++) {
            s.change(EMITTER.above(y - 1), emitter(true, ShieldColor.BLUE));
        }
        slideOut(s, 1, 3, ShieldColor.BLUE);
        s.idle(60);

        s.chapter("Its settings");
        Vec3 side = HowToSceneBuilder.face(EMITTER.above(), Direction.WEST);
        s.sneakRightClick(side, ItemStack.EMPTY);
        s.idle(26);
        s.picture(120, "laser_settings", 270, 186,
                s.arrow(0.45f, 0.245f, "*Color*: six colours"),
                s.arrow(0.45f, 0.385f, "*Name*: how you switch it"),
                s.arrow(0.6f, 0.525f, "*Switch*: on or off"),
                s.arrow(0.6f, 0.665f, "*Wall*: standing or lying flat"));
        s.idle(125);
        for (int y = 1; y <= 3; y++) {
            s.change(EMITTER.above(y - 1), emitter(true, ShieldColor.RED));
            for (int z = 1; z <= 3; z++) {
                s.change(new BlockPos(2, y, z), wall(ShieldColor.RED));
            }
        }
        s.text(75, HowToSceneBuilder.face(EMITTER.above(), Direction.WEST), Side.LEFT,
                "Sneak + right-click with an empty hand: only you (or whoever you trust) can open it.");
        s.idle(80);
        return List.of(s.build());
    }
}
