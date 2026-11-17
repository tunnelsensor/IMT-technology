package com.senne.imttech.howto.scenes;

import com.senne.imttech.energy.CableKind;
import com.senne.imttech.howto.HowToCue;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.logic.InputDeviceBlock;
import com.senne.imttech.logic.InputLight;
import com.senne.imttech.logic.NetworkButtonBlock;
import com.senne.imttech.logic.NetworkControlBlock;
import com.senne.imttech.logic.NetworkLeverBlock;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.security.wall.CameraMonitorBlock;
import com.senne.imttech.shield.DomeGeneratorBlock;
import com.senne.imttech.shield.LaserBridgeBlock;
import com.senne.imttech.shield.LaserBridgePlateBlock;
import com.senne.imttech.shield.ShieldColor;
import com.senne.imttech.turret.TurretBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

/**
 * De verdediging van je basis (hoofdstuk 63, stap 7, de volgende tien na de belangrijkste): torentjes, camera's en de
 * schermmuur, zones, de laserbrug, de netwerkknop en -hendel, en de schildkoepel. Hoe het echt werkt staat in LEESMIJ
 * ("Torentjes", "De schermmuur", "Je basis beveiligen: zones", "Rayshield", "Het netwerk").
 */
public final class DefenceScenes {
    private DefenceScenes() {}

    private static final int LASER_RED = 0xFF4545;
    private static final int POE_GREEN = 0x5CFF7A;

    // ================================================================ Torentjes

    static BlockState turret(Block block, boolean online, boolean enabled) {
        return block.defaultBlockState().setValue(TurretBlock.ONLINE, online).setValue(TurretBlock.ENABLED, enabled);
    }

    /**
     * Een husk loopt in {@code move} ticks langs {@code path} en het torentje op {@code muzzle} schiet {@code shots} keer
     * (elke 15 ticks een laser); daarna valt hij om. De klok staat daarna achter het laatste schot.
     */
    private static void attack(HowToSceneBuilder s, String key, Vec3 muzzle, int move, int shots, Vec3... path) {
        int start = s.now();
        int firstShot = move / 3;
        int life = firstShot + shots * 15 + 4;
        s.actor(key, EntityType.HUSK, life, move, 0.0f, path);
        s.idle(firstShot);
        List<Vec3> line = List.of(path);
        for (int i = 0; i < shots; i++) {
            float progress = Math.min(1.0f, (s.now() - start) / (float) move);
            Vec3 target = HowToSceneBuilder.along(line, progress).add(0, 1.2, 0);
            s.beam(6, muzzle, target, LASER_RED);
            s.sound(ModSounds.DRONE_LASER.get(), 0.25f, 1.4f);
            s.idle(15);
        }
        s.sound(SoundEvents.HUSK_DEATH, 0.4f, 1.0f);
        s.idle(6);
    }

    /** Torentjes: de Rocket Turret op stroom, hij schiet op monsters, raketten, de Laser Turret aan je kern, en uit op naam. */
    public static List<HowToScene> turrets() {
        HowToSceneBuilder s = new HowToSceneBuilder("turrets", "Turrets").plate(8, 8).height(3);
        BlockPos rocket = new BlockPos(2, 1, 2);
        BlockPos solar = new BlockPos(1, 1, 2);
        BlockPos laser = new BlockPos(5, 1, 2);
        BlockPos core = new BlockPos(5, 1, 0);
        Vec3 rocketMuzzle = new Vec3(2.5, 1.75, 2.5);
        Vec3 laserMuzzle = new Vec3(5.5, 1.75, 2.5);
        s.idle(8);
        s.chapter("A Rocket Turret");
        s.place(rocket, turret(ModBlocks.ROCKET_TURRET.get(), false, true));
        s.idle(14);
        s.place(solar, ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        s.idle(8);
        s.change(rocket, turret(ModBlocks.ROCKET_TURRET.get(), true, true));
        s.text(90, HowToSceneBuilder.top(rocket), Side.RIGHT,
                "A *Rocket Turret* defends your base. It needs power: a generator against it, or a *Power Cable*. Without"
                        + " power its barrel hangs down.");
        s.idle(95);

        s.chapter("Monsters");
        s.text(110, rocketMuzzle, Side.RIGHT,
                "Monsters within 16 blocks get its laser, until they drop. It never shoots you, your team, animals or your"
                        + " drones.");
        attack(s, "husk", rocketMuzzle, 90, 5, new Vec3(7.0, 1.0, 7.0), new Vec3(3.8, 1.0, 3.8));
        s.idle(20);

        s.chapter("Rockets");
        s.rightClick(HowToSceneBuilder.face(rocket, Direction.SOUTH), new ItemStack(ModItems.ROCKET_AMMO.get(), 16));
        s.text(85, HowToSceneBuilder.face(rocket, Direction.SOUTH), Side.RIGHT,
                "Right-click it with *Rocket Ammo* (or put a hopper on top): every 2.5 seconds it also fires a rocket, up to"
                        + " 24 blocks.");
        s.idle(90);

        s.chapter("A Laser Turret");
        s.place(core, SecurityScenes.core(Direction.SOUTH, true));
        s.idle(4);
        s.place(core.east(), SecurityScenes.heat(Direction.WEST));
        s.idle(4);
        s.place(laser, turret(ModBlocks.SECURITY_TURRET.get(), false, true));
        s.idle(10);
        Vec3[] cable = {new Vec3(5.5, 1.05, 1.0), new Vec3(5.5, 1.05, 2.0)};
        s.holdRight(14, new ItemStack(ModItems.POE_CABLE.get()), cable);
        s.cable(CableKind.POE, 14, cable);
        s.idle(16);
        s.change(laser, turret(ModBlocks.SECURITY_TURRET.get(), true, true));
        s.text(130, laserMuzzle, Side.RIGHT,
                "A *Laser Turret* belongs to your *Security Core* through its cable, and shoots whoever your core calls an"
                        + " intruder (the *Access* app).");
        attack(s, "husk2", laserMuzzle, 80, 4, new Vec3(7.5, 1.0, 6.5), new Vec3(5.6, 1.0, 4.2));
        s.idle(20);

        s.chapter("Switch them by name");
        s.change(rocket, turret(ModBlocks.ROCKET_TURRET.get(), true, false));
        s.change(laser, turret(ModBlocks.SECURITY_TURRET.get(), true, false));
        s.text(90, HowToSceneBuilder.top(rocket), Side.RIGHT,
                "Turn turrets off and on by their name: they are in your I.M.T list (['] > Turrets), and listen to your voice.");
        s.idle(60);
        s.change(rocket, turret(ModBlocks.ROCKET_TURRET.get(), true, true));
        s.change(laser, turret(ModBlocks.SECURITY_TURRET.get(), true, true));
        s.idle(40);
        return List.of(s.build());
    }

    // ================================================================ Camera's en de schermmuur

    static BlockState monitor(boolean on) {
        return ModBlocks.CAMERA_MONITOR.get().defaultBlockState().setValue(CameraMonitorBlock.FACING, Direction.SOUTH)
                .setValue(CameraMonitorBlock.ON, on);
    }

    /** Camera's: een camera op een paal aan je kern, en een schermmuur van vier monitoren (samen één scherm). */
    public static List<HowToScene> cameras() {
        HowToSceneBuilder s = new HowToSceneBuilder("cameras", "Cameras and the camera wall").plate(7, 6);
        List<BlockPos> monitors = List.of(new BlockPos(4, 1, 4), new BlockPos(5, 1, 4), new BlockPos(4, 2, 4),
                new BlockPos(5, 2, 4));
        s.idle(8);
        s.chapter("A camera");
        s.place(SecurityScenes.CORE, SecurityScenes.core(Direction.SOUTH, true));
        s.idle(4);
        s.place(SecurityScenes.CORE_GEN, SecurityScenes.heat(Direction.WEST));
        s.idle(4);
        Map<BlockPos, BlockState> post = new LinkedHashMap<>();
        post.put(new BlockPos(4, 1, 0), Blocks.STONE_BRICK_WALL.defaultBlockState());
        post.put(new BlockPos(4, 2, 0), Blocks.STONE_BRICK_WALL.defaultBlockState());
        s.place(post, HowToSceneBuilder.DROP);
        s.idle(6);
        s.place(SecurityScenes.CORE_CAMERA, SecurityScenes.camera(Direction.SOUTH).setValue(
                com.senne.imttech.security.CameraBlock.ONLINE, false));
        s.idle(10);
        s.holdRight(26, new ItemStack(ModItems.POE_CABLE.get()), SecurityScenes.CORE_TO_CAMERA);
        s.cable(CableKind.POE, 26, SecurityScenes.CORE_TO_CAMERA);
        s.idle(28);
        s.change(SecurityScenes.CORE_CAMERA, SecurityScenes.camera(Direction.SOUTH));
        s.text(90, HowToSceneBuilder.top(SecurityScenes.CORE_CAMERA), Side.RIGHT,
                "A *Security Camera* needs a cable to your *Security Core* (a PoE cable brings power too). Then you watch it"
                        + " in the *Cameras* app on the desktop.");
        s.idle(95);

        s.chapter("A camera wall");
        Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
        for (BlockPos pos : monitors) {
            wall.put(pos, monitor(false));
        }
        s.layers(wall, 6);
        s.idle(6);
        Vec3[] cable = {new Vec3(3.0, 1.05, 2.5), new Vec3(3.5, 1.05, 2.5), new Vec3(3.5, 1.05, 4.5), new Vec3(4.0, 1.05, 4.5)};
        s.holdRight(20, new ItemStack(ModItems.POE_CABLE.get()), cable);
        s.cable(CableKind.POE, 20, cable);
        s.idle(22);
        for (BlockPos pos : monitors) {
            s.change(pos, monitor(true));
        }
        s.outline(90, monitors.get(0), monitors.get(3), HowToSceneBuilder.CYAN);
        s.text(95, HowToSceneBuilder.face(new BlockPos(4, 2, 4), Direction.SOUTH), Side.LEFT,
                "*Camera Monitors* next to and on top of each other are one screen, up to 8 x 6. One cable to one monitor"
                        + " is enough.");
        s.idle(100);

        s.chapter("Set it up");
        s.rightClick(HowToSceneBuilder.face(new BlockPos(4, 1, 4), Direction.SOUTH), ItemStack.EMPTY);
        s.text(90, HowToSceneBuilder.face(new BlockPos(4, 1, 4), Direction.SOUTH), Side.LEFT,
                "Right-click it, pick a camera and drag a box on the grid: there it shows, live. Everyone can watch; only you"
                        + " set it up.");
        s.idle(95);
        return List.of(s.build());
    }

    // ================================================================ Speaker (hoofdstuk 126, sinds hoofdstuk 136 "Speaker")

    /**
     * De Speaker: een kabel naar je kern; zijn geluiden staan in Files van de kern (map sounds); een diagram laat hem iets
     * spelen ({@code playsound}) of zeggen ({@code playtext}); vanzelf doet hij niets (hoofdstuk 128). Met een foto van het
     * echte scherm, gemaakt door de rondleiding (-Ponly=howtopics:alarm).
     */
    public static List<HowToScene> alarm() {
        HowToSceneBuilder s = new HowToSceneBuilder("alarm_speaker", "Speaker").plate(7, 6);
        BlockPos speaker = new BlockPos(4, 2, 1);
        s.idle(8);
        s.chapter("A speaker on your network");
        s.place(SecurityScenes.CORE, SecurityScenes.core(Direction.SOUTH, true));
        s.idle(4);
        s.place(SecurityScenes.CORE_GEN, SecurityScenes.heat(Direction.WEST));
        s.idle(4);
        Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
        for (int y = 1; y <= 3; y++) {
            for (int x = 3; x <= 5; x++) {
                wall.put(new BlockPos(x, y, 0), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        s.layers(wall, 6);
        s.idle(6);
        s.place(speaker, alarmSpeaker(false, false));
        s.idle(10);
        Vec3[] cable = {new Vec3(2.5, 1.05, 2.0), new Vec3(2.5, 1.05, 1.12), new Vec3(4.5, 1.05, 1.12), new Vec3(4.5, 2.1, 1.12)};
        s.holdRight(26, new ItemStack(ModItems.POE_CABLE.get()), cable);
        s.cable(CableKind.POE, 26, cable);
        s.idle(28);
        s.change(speaker, alarmSpeaker(true, false));
        s.text(90, HowToSceneBuilder.top(speaker), Side.RIGHT,
                "A *Speaker* needs a cable to your *Security Core* (a PoE cable brings power too). Its small green light"
                        + " shows it is ready.");
        s.idle(95);

        s.chapter("Its sounds are in Files");
        s.pulse(30, Vec3.atCenterOf(SecurityScenes.CORE), 2.0f, HowToSceneBuilder.CYAN);
        s.text(100, HowToSceneBuilder.top(SecurityScenes.CORE), Side.LEFT,
                "The sounds are not on the speaker but in your core: open *Files*, go to the folder *sounds* and press"
                        + " *+Sound* (mp4, m4a, mp3, wav or ogg; a video works too). Double-click a sound to hear it.");
        s.idle(105);

        s.chapter("A diagram plays them");
        s.change(speaker, alarmSpeaker(true, true));
        s.sound(ModSounds.ALARM_SIREN.get(), 0.4f, 1.0f);
        s.pulse(40, Vec3.atCenterOf(speaker), 3.0f, HowToSceneBuilder.RED);
        s.text(110, HowToSceneBuilder.top(speaker), Side.RIGHT,
                "It never plays by itself. A diagram you run says what: *playsound(\"siren\", \"Speaker 1\")* plays a sound"
                        + " once (*\"default\"* is the built-in siren, *\"all\"* every speaker). Your script does not wait:"
                        + " in a loop, add *wait(...)*.");
        s.idle(115);
        s.change(speaker, alarmSpeaker(true, false));

        s.chapter("Let it speak");
        s.change(speaker, alarmSpeaker(true, true));
        s.text(100, HowToSceneBuilder.top(speaker), Side.RIGHT,
                "*playtext(\"Alarm armed\", \"all\")* says it with the I.M.T voice, from the speaker. Players without"
                        + " the voice see it in the chat. While it speaks, its sound is quiet.");
        s.idle(105);
        s.change(speaker, alarmSpeaker(true, false));

        s.chapter("Its screen");
        s.sneakRightClick(HowToSceneBuilder.face(speaker, Direction.SOUTH), ItemStack.EMPTY);
        s.text(60, HowToSceneBuilder.face(speaker, Direction.SOUTH), Side.LEFT,
                "Sneak + right-click with an empty hand: its screen.");
        s.idle(65);
        // De foto van het echte scherm (300 x 186, net als AlarmSpeakerScreen; hier vast getypt: deze code draait ook op een
        // server, die het scherm niet kent). De pijlen van boven naar beneden.
        s.picture(150, "alarm_screen", 300, 186,
                s.arrow(0.62f, 0.16f, "Its name"),
                s.arrow(0.50f, 0.37f, "How loud and how far"),
                s.arrow(0.14f, 0.56f, "Test: 5 seconds"),
                s.arrow(0.40f, 0.66f, "Where its sounds are"),
                s.arrow(0.30f, 0.80f, "In a diagram"));
        s.idle(155);
        return List.of(s.build());
    }

    /** Een Speaker aan de muur (de muur aan de noordkant, de voorkant naar het zuiden): uit, klaar of spelend. */
    private static BlockState alarmSpeaker(boolean online, boolean sounding) {
        return ModBlocks.ALARM_SPEAKER.get().defaultBlockState()
                .setValue(com.senne.imttech.alarm.AlarmSpeakerBlock.FACE, AttachFace.WALL)
                .setValue(com.senne.imttech.alarm.AlarmSpeakerBlock.FACING, Direction.SOUTH)
                .setValue(com.senne.imttech.alarm.AlarmSpeakerBlock.ONLINE, online)
                .setValue(com.senne.imttech.alarm.AlarmSpeakerBlock.SOUNDING, sounding);
    }

    // ================================================================ Zones

    /** Zones: twee hoeken met de Zone Marker, sneak + rechtsklik maakt er een kubus van, en dan bouwt alleen jij daar. */
    public static List<HowToScene> zones() {
        HowToSceneBuilder s = new HowToSceneBuilder("zones", "Protected zones").plate(9, 9).height(4);
        BlockPos core = new BlockPos(4, 1, 4);
        BlockPos gen = new BlockPos(3, 1, 4);
        BlockPos a = new BlockPos(2, 1, 2);
        BlockPos b = new BlockPos(6, 3, 6);
        ItemStack marker = new ItemStack(ModItems.ZONE_MARKER.get());
        Vec3 stand = new Vec3(1.5, 1.0, 7.5);
        s.idle(8);
        s.chapter("Your base");
        Map<BlockPos, BlockState> walls = HowToSceneBuilder.box(new BlockPos(2, 1, 2), new BlockPos(6, 1, 6), pos -> {
            boolean edge = pos.getX() == 2 || pos.getX() == 6 || pos.getZ() == 2 || pos.getZ() == 6;
            boolean door = pos.getX() == 4 && pos.getZ() == 6;
            return edge && !door ? Blocks.STONE_BRICKS.defaultBlockState() : null;
        });
        s.place(walls, HowToSceneBuilder.DROP);
        s.idle(6);
        s.place(core, SecurityScenes.core(Direction.SOUTH, true));
        s.idle(4);
        s.place(gen, SecurityScenes.heat(Direction.WEST));
        s.idle(6);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, -135.0f, Map.of(EquipmentSlot.MAINHAND, marker), stand);
        s.text(80, HowToSceneBuilder.top(core), Side.RIGHT,
                "In Minecraft anyone can break any block. A *zone* around your base changes that.");
        s.idle(85);

        s.chapter("Mark two corners");
        s.click(HowToSceneBuilder.CLICK, List.of(HowToSceneBuilder.top(a)), HowToCue.Button.LEFT, false, false, marker);
        s.idle(10);
        s.outline(40, a, a, HowToSceneBuilder.ORANGE);
        s.idle(24);
        s.rightClick(HowToSceneBuilder.top(new BlockPos(6, 1, 6)), marker);
        s.idle(10);
        s.outline(80, a, b, HowToSceneBuilder.ORANGE);
        s.text(80, HowToSceneBuilder.top(b), Side.RIGHT,
                "With the *Zone Marker*: left-click a block for corner 1, right-click for corner 2. You see an orange box.");
        s.idle(85);

        s.chapter("Make it a zone");
        s.sneakRightClick(HowToSceneBuilder.top(new BlockPos(6, 1, 6)), marker);
        s.idle(12);
        s.outline(260, a, b, HowToSceneBuilder.CYAN);
        s.sound(ModSounds.IMT_CONFIRM.get(), 0.4f, 1.0f);
        s.text(90, HowToSceneBuilder.top(b), Side.RIGHT,
                "Sneak + right-click: the box becomes part of your zone. A zone can be several boxes, up to 500 blocks from"
                        + " your *Security Core*.");
        s.idle(95);

        s.chapter("Only you build here");
        s.text(80, HowToSceneBuilder.top(b), Side.RIGHT,
                "Inside, only you and the people you trust can place and break blocks. Breaking in takes *frags*.");
        s.idle(85);
        s.outline(70, gen, gen, HowToSceneBuilder.ORANGE);
        s.text(80, HowToSceneBuilder.face(gen, Direction.WEST), Side.LEFT,
                "A zone costs power (a bigger zone more). Fine-tune it in the *Zone* app, or let *Autoscan* find your rooms.");
        s.idle(85);
        // Hoofdstuk 120: de zone blijft geladen (behalve de buitenste rand).
        s.text(80, HowToSceneBuilder.top(core), Side.RIGHT,
                "Your zone stays loaded when you are far away (not its outer 10 blocks): turrets and machines keep working.");
        s.idle(85);
        return List.of(s.build());
    }

    // ================================================================ De laserbrug

    static BlockState bridge(boolean on) {
        return ModBlocks.LASER_BRIDGE.get().defaultBlockState().setValue(LaserBridgeBlock.FACING, Direction.EAST)
                .setValue(LaserBridgeBlock.POWERED, on);
    }

    static BlockState plate(boolean east, boolean north, boolean south) {
        return ModBlocks.LASER_BRIDGE_PLATE.get().defaultBlockState().setValue(LaserBridgePlateBlock.COLOR, ShieldColor.BLUE)
                .setValue(LaserBridgePlateBlock.AXIS, Direction.Axis.X).setValue(LaserBridgePlateBlock.WEST, true)
                .setValue(LaserBridgePlateBlock.EAST, east).setValue(LaserBridgePlateBlock.NORTH, north)
                .setValue(LaserBridgePlateBlock.SOUTH, south);
    }

    /** De laserbrug: een vloer van licht over een gat, iedereen loopt erover, twee zenders = twee breed, en een val. */
    public static List<HowToScene> laserBridge() {
        HowToSceneBuilder s = new HowToSceneBuilder("laser_bridge", "The laser bridge").plate(9, 4).height(4);
        s.idle(8);
        s.chapter("A floor of light");
        Map<BlockPos, BlockState> platforms = HowToSceneBuilder.box(new BlockPos(0, 1, 1), new BlockPos(8, 1, 2),
                pos -> pos.getX() <= 1 || pos.getX() >= 6 ? Blocks.STONE_BRICKS.defaultBlockState() : null);
        s.place(platforms, HowToSceneBuilder.DROP);
        s.idle(8);
        s.place(new BlockPos(2, 1, 1), bridge(false));
        s.idle(4);
        s.place(new BlockPos(2, 1, 2), bridge(false));
        s.idle(4);
        s.place(new BlockPos(2, 1, 0), SecurityScenes.heat(Direction.WEST));
        s.idle(10);
        s.change(new BlockPos(2, 1, 1), bridge(true));
        s.change(new BlockPos(2, 1, 2), bridge(true));
        s.sound(ModSounds.RAYSHIELD_ON.get(), 0.4f, 1.2f);
        for (int x = 3; x <= 5; x++) {
            s.change(new BlockPos(x, 1, 1), plate(x < 5, true, false));
            s.change(new BlockPos(x, 1, 2), plate(x < 5, false, true));
            s.idle(3);
        }
        s.text(90, HowToSceneBuilder.top(new BlockPos(4, 1, 1)), Side.RIGHT,
                "A *Laser Bridge* in the edge of a floor makes a floor of light toward where you looked, up to 16 blocks or"
                        + " the first block. It runs on power.");
        s.idle(95);

        s.chapter("Walk across");
        s.player("you", 110, 90, 270.0f, Map.of(), new Vec3(0.5, 2.0, 1.5), new Vec3(1.5, 2.0, 1.5),
                new Vec3(4.5, 1.9, 1.5), new Vec3(7.5, 2.0, 1.5));
        s.text(90, HowToSceneBuilder.top(new BlockPos(4, 1, 2)), Side.RIGHT,
                "Everyone can walk on it: players, animals and monsters. It doesn't hurt. Two emitters side by side make it"
                        + " two wide.");
        s.idle(110);

        s.chapter("A trap");
        s.player("you2", 60, 40, 270.0f, Map.of(), new Vec3(0.5, 2.0, 2.5), new Vec3(4.5, 1.9, 2.5));
        s.idle(42);
        s.change(new BlockPos(2, 1, 1), bridge(false));
        s.change(new BlockPos(2, 1, 2), bridge(false));
        s.sound(ModSounds.RAYSHIELD_OFF.get(), 0.4f, 1.0f);
        for (int x = 5; x >= 3; x--) {
            s.clear(new BlockPos(x, 1, 1), new BlockPos(x, 1, 2));
            s.idle(2);
        }
        s.player("you2", 18, 18, 270.0f, Map.of(), new Vec3(4.5, 1.9, 2.5), new Vec3(4.5, -1.5, 2.5));
        s.text(85, new Vec3(4.5, 1.5, 2.5), Side.RIGHT,
                "Turn it off (by its name, like a laser gate) and whoever stands on it falls.");
        s.idle(90);
        return List.of(s.build());
    }

    // ================================================================ Netwerkknop en -hendel

    static BlockState button(boolean pressed) {
        return ModBlocks.NETWORK_BUTTON.get().defaultBlockState().setValue(NetworkControlBlock.FACE, AttachFace.WALL)
                .setValue(InputDeviceBlock.FACING, Direction.SOUTH).setValue(NetworkButtonBlock.PRESSED, pressed);
    }

    static BlockState lever(boolean on) {
        return ModBlocks.NETWORK_LEVER.get().defaultBlockState().setValue(NetworkControlBlock.FACE, AttachFace.WALL)
                .setValue(InputDeviceBlock.FACING, Direction.SOUTH).setValue(NetworkLeverBlock.ON, on)
                .setValue(InputDeviceBlock.LIGHT, InputLight.OFF);
    }

    /** De netwerkknop en -hendel: geen redstone, ze starten je diagram op de server (when button, when lever). */
    public static List<HowToScene> networkControls() {
        HowToSceneBuilder s = new HowToSceneBuilder("network_controls", "Network button and lever").plate(6, 5).height(3);
        BlockPos core = new BlockPos(1, 1, 1);
        BlockPos button = new BlockPos(2, 2, 4);
        BlockPos lever = new BlockPos(4, 2, 4);
        Vec3[] toButton = {new Vec3(1.5, 1.05, 2.0), new Vec3(1.5, 1.05, 4.05), new Vec3(2.5, 1.05, 4.05), new Vec3(2.5, 2.3, 4.05)};
        Vec3[] toLever = {new Vec3(2.5, 1.05, 4.05), new Vec3(4.5, 1.05, 4.05), new Vec3(4.5, 2.3, 4.05)};
        Vec3[] buttonBack = {toButton[3], toButton[2], toButton[1], toButton[0]};
        s.idle(8);
        s.chapter("No redstone");
        s.place(HowToSceneBuilder.box(new BlockPos(1, 1, 3), new BlockPos(5, 3, 3), pos -> Blocks.STONE_BRICKS.defaultBlockState()),
                HowToSceneBuilder.DROP);
        s.idle(6);
        s.place(core, SecurityScenes.core(Direction.SOUTH, true));
        s.idle(4);
        s.place(core.west(), SecurityScenes.heat(Direction.WEST));
        s.idle(4);
        s.place(button, button(false));
        s.idle(4);
        s.place(lever, lever(false));
        s.idle(8);
        s.holdRight(24, new ItemStack(ModItems.POE_CABLE.get()), toButton);
        s.cable(CableKind.POE, 24, toButton);
        s.idle(26);
        s.cable(CableKind.POE, 16, toLever);
        s.idle(18);
        s.text(90, HowToSceneBuilder.face(button, Direction.SOUTH), Side.RIGHT,
                "A *Network Button* and a *Network Lever* work without redstone: with a cable to your *Security Core* they"
                        + " start your diagram on the server.");
        s.idle(95);

        s.chapter("Press the button");
        s.rightClick(HowToSceneBuilder.face(button, Direction.SOUTH), ItemStack.EMPTY);
        s.idle(8);
        s.change(button, button(true));
        s.sound(SoundEvents.STONE_BUTTON_CLICK_ON, 0.4f, 1.0f);
        s.signal(14, POE_GREEN, buttonBack);
        s.idle(20);
        s.change(button, button(false));
        s.picture(120, "logic_button", 426, 240,
                s.arrow(0.40f, 0.228f, "Runs when someone presses *Doorbell*"),
                s.arrow(0.45f, 0.269f, "*player.name* says who it was"),
                s.arrow(0.42f, 0.394f, "Runs when someone flips *Night mode*"),
                s.arrow(0.45f, 0.436f, "*lever.on*: is it on now?"));
        s.idle(125);

        s.chapter("Flip the lever");
        s.rightClick(HowToSceneBuilder.face(lever, Direction.SOUTH), ItemStack.EMPTY);
        s.idle(8);
        s.change(lever, lever(true));
        s.sound(SoundEvents.LEVER_CLICK, 0.4f, 1.0f);
        s.text(85, HowToSceneBuilder.face(lever, Direction.SOUTH), Side.RIGHT,
                "Rename them with sneak + right-click (empty hand), or in the *Devices* app.");
        s.idle(90);
        return List.of(s.build());
    }

    // ================================================================ De schildkoepel

    /** De koepel aan (opslaan zoals het spel dat doet: Up, ChangedAt en de maat), voor het tekenen. */
    private static CompoundTag domeOn() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Up", true);
        tag.putBoolean("Enabled", true);
        tag.putBoolean("ChangedAtScene", true);
        tag.putByte("Radius", (byte) 0);
        tag.putByte("Strength", (byte) 3);
        tag.putByte("Colour", (byte) ShieldColor.CYAN.ordinal());
        tag.putInt("Shield", 1_000_000);
        return tag;
    }

    /** De schildkoepel: aan met een show, wat hij tegenhoudt (een raiddrone die terugkaatst), en zijn scherm. */
    public static List<HowToScene> dome() {
        HowToSceneBuilder s = new HowToSceneBuilder("shield_dome", "The shield dome").plate(17, 17).height(10);
        BlockPos generator = new BlockPos(8, 1, 8);
        s.idle(8);
        s.chapter("A dome over your base");
        s.place(HowToSceneBuilder.box(new BlockPos(10, 1, 10), new BlockPos(12, 2, 12), pos -> pos.getY() == 2 && pos.getX() == 11
                && pos.getZ() == 11 ? null : Blocks.OAK_PLANKS.defaultBlockState()), HowToSceneBuilder.DROP);
        s.idle(8);
        s.place(generator, ModBlocks.DOME_GENERATOR.get().defaultBlockState().setValue(DomeGeneratorBlock.POWERED, false));
        s.idle(6);
        s.place(generator.south(), SecurityScenes.heat(Direction.SOUTH));
        s.idle(12);
        s.change(generator, ModBlocks.DOME_GENERATOR.get().defaultBlockState().setValue(DomeGeneratorBlock.POWERED, true));
        s.blockData(generator, domeOn());
        s.sound(ModSounds.RAYSHIELD_ON.get(), 0.5f, 0.7f);
        s.text(110, HowToSceneBuilder.top(generator), Side.RIGHT,
                "A *Dome Generator* on power puts a shield around your base: a beam up, then it unfolds and drops as a whole"
                        + " sphere (under the generator too).");
        s.idle(120);

        s.chapter("What it stops");
        s.actor("raider", ModEntities.RAID_DRONE.get(), 80, 80, 0.0f, new Vec3(16.0, 7.0, 16.0), new Vec3(13.6, 6.0, 13.6),
                new Vec3(16.5, 7.5, 16.5));
        s.idle(30);
        s.sound(ModSounds.RAYSHIELD_HIT.get(), 0.5f, 1.0f);
        s.text(100, new Vec3(13.6, 6.0, 13.6), Side.RIGHT,
                "It stops what isn't yours: arrows, monsters, raid drones (pushed out) and enemy satellite strikes. You and"
                        + " your team walk right through. Every hit costs shield power.");
        s.idle(80);

        s.chapter("Its screen");
        s.rightClick(HowToSceneBuilder.face(generator, Direction.WEST), ItemStack.EMPTY);
        s.idle(24);
        s.picture(130, "dome_screen", 300, 206,
                s.arrow(0.45f, 0.27f, "On or off"),
                s.arrow(0.55f, 0.39f, "Its colour"),
                s.arrow(0.55f, 0.5f, "*Range*: 8 to 32 blocks"),
                s.arrow(0.5f, 0.62f, "*Strength* 1 to 5: bigger and stronger costs more power"));
        s.idle(135);
        return List.of(s.build());
    }
}
