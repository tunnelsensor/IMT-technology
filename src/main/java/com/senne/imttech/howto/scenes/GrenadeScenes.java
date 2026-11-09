package com.senne.imttech.howto.scenes;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.robot.RobotStyle;
import com.senne.imttech.security.CameraBlock;
import com.senne.imttech.weapons.RemoteChargeBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * De granaten (hoofdstuk 63, de wapens): EMP-granaten, fraggranaten, rook en de flashbang, en de Remote EMP en Remote Frag
 * met hun trigger. Hoe het echt werkt staat in LEESMIJ ("Wapens: granaten, de Remote EMP, de Remote Frag en
 * lasergeweren", "Inbreken met frags").
 */
public final class GrenadeScenes {
    private GrenadeScenes() {}

    /** De kleur van een EMP (zoals de echte bol: zie EmpEffects). */
    static final int EMP_BLUE = 0x3FA8FF;
    /** Een ontploffing. */
    static final int BLAST = 0xFF8A3D;
    /** Grijze rook. */
    static final int SMOKE_GREY = 0xC4CACF;

    // ================================================================ Hulpjes

    /**
     * Een granaat ({@code type}) gegooid langs {@code path} (een boog, dan stuiteren), in {@code move} ticks; hij gaat
     * {@code fuse} ticks na het gooien af en is dan weg. De klok staat daarna op het moment dat hij afgaat.
     */
    static void lob(HowToSceneBuilder s, String key, EntityType<?> type, CompoundTag extra, int move, int fuse, Vec3... path) {
        CompoundTag data = extra.copy();
        data.putInt("FuseLeft", fuse);
        s.actor(key, type, fuse, move, 0.0f, data, path);
        s.sound(SoundEvents.SNOWBALL_THROW, 0.4f, 0.7f);
        s.idle(move);
        // Hij piept steeds sneller (zoals de echte: om de 10, dan 5, dan 2 ticks).
        for (int left = fuse - move; left > 0; ) {
            int step = left > 30 ? 10 : left > 14 ? 5 : 2;
            s.sound(ModSounds.EMP_GRENADE_BEEP.get(), 0.25f, 1.0f);
            s.idle(Math.min(step, left));
            left -= step;
        }
    }

    /** Een boog van {@code from} naar {@code to}, die {@code high} boven het midden uitkomt (vijf punten). */
    static Vec3[] arc(Vec3 from, Vec3 to, double high) {
        Vec3[] points = new Vec3[5];
        for (int i = 0; i < 5; i++) {
            double f = i / 4.0;
            Vec3 p = from.lerp(to, f);
            points[i] = p.add(0, Math.sin(f * Math.PI) * high, 0);
        }
        return points;
    }

    /** Twee arrays punten achter elkaar (een boog, dan stuiteren). */
    static Vec3[] then(Vec3[] a, Vec3... b) {
        Vec3[] out = new Vec3[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    static BlockState camera(boolean online) {
        return SecurityScenes.camera(Direction.SOUTH).setValue(CameraBlock.ONLINE, online);
    }

    static BlockState charge(Block block, AttachFace face, Direction facing) {
        return block.defaultBlockState().setValue(RemoteChargeBlock.FACE, face).setValue(RemoteChargeBlock.FACING, facing);
    }

    static Map<BlockPos, BlockState> wall(int x0, int x1, int z, int height) {
        return HowToSceneBuilder.box(new BlockPos(x0, 1, z), new BlockPos(x1, height, z), pos -> Blocks.STONE_BRICKS.defaultBlockState());
    }

    // ================================================================ EMP-granaten

    /**
     * De EMP Grenade: gooien (stuiteren, piepen), de puls (een robot en een drone vallen neer, het torentje laat zijn loop
     * hangen, de camera gaat uit), kabels en uitrusting, weer aan, en de sticky op een robot.
     */
    public static List<HowToScene> emp() {
        HowToSceneBuilder s = new HowToSceneBuilder("emp_grenades", "EMP grenades").plate(8, 7).height(3);
        Vec3 you = new Vec3(2.0, 1.0, 6.0);
        Vec3 land = new Vec3(5.0, 1.0, 2.8);
        Vec3 robot = new Vec3(5.7, 1.0, 1.6);
        Vec3 drone = new Vec3(4.0, 2.5, 2.0);
        BlockPos turret = new BlockPos(6, 1, 0);
        BlockPos solar = new BlockPos(7, 1, 0);
        BlockPos camera = new BlockPos(3, 1, 0);
        CompoundTag guard = RobotScenes.robot(RobotStyle.COMMANDO, RobotScenes.board("scan", "", "laser"), true);
        CompoundTag flyer = RobotScenes.drone(RobotScenes.board("scan"), true);
        s.idle(6);
        Map<BlockPos, BlockState> base = new LinkedHashMap<>();
        base.put(turret, DefenceScenes.turret(ModBlocks.ROCKET_TURRET.get(), true, true));
        base.put(solar, RobotScenes.solar());
        base.put(camera, camera(true));
        s.place(base, HowToSceneBuilder.DROP);
        s.idle(6);
        s.actor("robot", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, 30.0f, guard, robot);
        s.actor("drone", ModEntities.GUARD_DRONE.get(), HowToSceneBuilder.UNTIL_END, 0, 30.0f, flyer, drone);

        s.chapter("Throw it");
        float aim = RobotScenes.yawTo(you, land);
        ItemStack grenade = new ItemStack(ModItems.EMP_GRENADE.get(), 8);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(EquipmentSlot.MAINHAND, grenade), you);
        s.idle(10);
        s.text(130, you.add(0, 2.1, 0), Side.LEFT,
                "Right-click throws an *EMP Grenade* (with sneak you roll it underhand). It bounces off walls, beeps faster"
                        + " and faster, and goes off %s seconds after the throw.",
                HowToSceneBuilder.number(ImtConfig.Weapons.ROLLING_FUSE_SECONDS));
        int fuse = Math.round(ImtConfig.Weapons.ROLLING_FUSE_SECONDS * 20);
        Vec3 hand = WeaponScenes.muzzle(you, aim);
        Vec3 bounce = new Vec3(4.3, 1.0, 3.6);
        lob(s, "grenade", ModEntities.ROLLING_EMP_GRENADE.get(), new CompoundTag(), 24, fuse,
                then(arc(hand, bounce, 1.2), new Vec3(4.7, 1.35, 3.15), land));

        s.chapter("The pulse");
        s.pulse(18, land.add(0, 0.4, 0), (float) ImtConfig.Weapons.EMP_RADIUS, EMP_BLUE);
        s.sound(ModSounds.EMP_BLAST.get(), 0.5f, 1.0f);
        s.actor("robot", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, 30.0f, RobotScenes.down(guard), robot);
        Vec3 ground = new Vec3(drone.x, 1.0, drone.z);
        s.actor("drone", ModEntities.GUARD_DRONE.get(), HowToSceneBuilder.UNTIL_END, 10, 30.0f, RobotScenes.down(flyer), drone, ground);
        s.change(turret, DefenceScenes.turret(ModBlocks.ROCKET_TURRET.get(), false, true));
        s.change(camera, camera(false));
        s.idle(8);
        s.text(140, land.add(0, 1.4, 0), Side.RIGHT,
                "An EMP does *no damage* to the map: everything electric within %s blocks is out for %s seconds. Robots and"
                        + " drones fall down, turrets drop their barrel, cameras go dark, keycard readers and code panels do"
                        + " nothing.", HowToSceneBuilder.number(ImtConfig.Weapons.EMP_RADIUS),
                HowToSceneBuilder.number(ImtConfig.Weapons.EMP_SECONDS));
        s.idle(145);

        s.chapter("Cables and gear");
        s.text(130, null, Side.RIGHT,
                "A cable lying *in the open* carries no power for a while, so everything behind it is off (a cable inside a"
                        + " wall is safe). Suits and glasses nearby stop too: *EMP DETECTED*. Rayshields, laser bridges,"
                        + " domes and lamps don't notice, and a dome protects what's inside it.");
        s.idle(135);

        s.chapter("Back on");
        s.actor("robot", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, 30.0f, guard, robot);
        s.actor("drone", ModEntities.GUARD_DRONE.get(), HowToSceneBuilder.UNTIL_END, 16, 30.0f, flyer, ground, drone);
        s.change(turret, DefenceScenes.turret(ModBlocks.ROCKET_TURRET.get(), true, true));
        s.change(camera, camera(true));
        s.sound(ModSounds.ROBOT_POWER_ON.get(), 0.5f, 1.0f);
        s.text(80, robot.add(0, 2.2, 0), Side.RIGHT, "After %s seconds everything switches itself back on.",
                HowToSceneBuilder.number(ImtConfig.Weapons.EMP_SECONDS));
        s.idle(85);

        s.chapter("The sticky one");
        ItemStack sticky = new ItemStack(ModItems.STICKY_EMP_GRENADE.get(), 4);
        float robotAim = RobotScenes.yawTo(you, robot);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, robotAim, Map.of(EquipmentSlot.MAINHAND, sticky), you);
        s.text(120, you.add(0, 2.1, 0), Side.LEFT,
                "A *Sticky EMP Grenade* sticks to the first thing it hits (a wall, the ground, a robot, a drone or a player;"
                        + " on a creature it goes along) and goes off %s seconds later.",
                HowToSceneBuilder.number(ImtConfig.Weapons.STICKY_FUSE_SECONDS));
        s.idle(12);
        int stickyFuse = Math.round(ImtConfig.Weapons.STICKY_FUSE_SECONDS * 20) + 14;
        Vec3 chest = robot.add(WeaponScenes.ahead(30.0f).scale(0.3)).add(0, 1.2, 0);
        lob(s, "sticky", ModEntities.STICKY_EMP_GRENADE.get(), new CompoundTag(), 14, stickyFuse,
                arc(WeaponScenes.muzzle(you, robotAim), chest, 0.8));
        s.pulse(18, chest, (float) ImtConfig.Weapons.EMP_RADIUS, EMP_BLUE);
        s.sound(ModSounds.EMP_BLAST.get(), 0.5f, 1.0f);
        s.actor("robot", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, 30.0f, RobotScenes.down(guard), robot);
        s.actor("drone", ModEntities.GUARD_DRONE.get(), HowToSceneBuilder.UNTIL_END, 10, 30.0f, RobotScenes.down(flyer), drone, ground);
        s.change(turret, DefenceScenes.turret(ModBlocks.ROCKET_TURRET.get(), false, true));
        s.change(camera, camera(false));
        s.idle(60);
        return List.of(s.build());
    }

    // ================================================================ Fraggranaten

    /** De Frag Grenade: net als een EMP-granaat, maar hij ontploft: een blok uit een stenen muur, barsten eromheen, een gat. */
    public static List<HowToScene> frag() {
        HowToSceneBuilder s = new HowToSceneBuilder("frag_grenades", "Frag grenades").plate(6, 5).height(3);
        Vec3 you = new Vec3(2.6, 1.0, 4.3);
        s.idle(6);
        s.place(wall(2, 4, 1, 3), HowToSceneBuilder.DROP);
        s.idle(10);
        s.chapter("Like an EMP grenade");
        ItemStack sticky = new ItemStack(ModItems.STICKY_FRAG_GRENADE.get(), 4);
        BlockPos middle = new BlockPos(3, 2, 1);
        Vec3 face = HowToSceneBuilder.face(middle, Direction.SOUTH);
        float aim = RobotScenes.yawTo(you, face);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(EquipmentSlot.MAINHAND, sticky), you);
        s.text(100, null, Side.RIGHT,
                "*Frag Grenades* and *Sticky Frag Grenades* fly, bounce, stick and beep just like EMP grenades, but they"
                        + " explode instead of an EMP.");
        s.idle(105);

        s.chapter("Through a wall");
        int fuse = Math.round(ImtConfig.Weapons.STICKY_FUSE_SECONDS * 20) + 14;
        lob(s, "frag", ModEntities.STICKY_FRAG_GRENADE.get(), new CompoundTag(), 14, fuse,
                arc(WeaponScenes.muzzle(you, aim), face.add(0, 0, 0.05), 0.6));
        s.pulse(12, face, 1.8f, BLAST);
        s.sound(SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 1.0f);
        s.clear(middle);
        for (BlockPos side : List.of(middle.west(), middle.east(), middle.below(), middle.above())) {
            s.crack(side, 6);
        }
        for (BlockPos corner : List.of(middle.west().below(), middle.east().below(), middle.west().above(), middle.east().above())) {
            s.crack(corner, 3);
        }
        s.idle(6);
        s.text(120, face, Side.RIGHT,
                "On a stone wall it blasts out the block it sits against. The blocks around it get *cracks*: they stay"
                        + " until you repair them, and the next blast counts on top.");
        s.idle(125);

        s.chapter("A way in");
        BlockPos below = middle.below();
        Vec3 lowFace = HowToSceneBuilder.face(below, Direction.SOUTH);
        lob(s, "frag2", ModEntities.STICKY_FRAG_GRENADE.get(), new CompoundTag(), 14, fuse,
                arc(WeaponScenes.muzzle(you, aim), lowFace.add(0, 0, 0.05), 0.5));
        s.pulse(12, lowFace, 1.8f, BLAST);
        s.sound(SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 1.0f);
        s.crack(below, -1);
        s.clear(below);
        s.idle(6);
        s.text(130, lowFace, Side.RIGHT,
                "A second frag below or next to it makes a hole you can walk through. Wood, glass and dirt fly out at once;"
                        + " obsidian takes four frags in the same spot, a security core eight.");
        s.idle(135);
        return List.of(s.build());
    }

    // ================================================================ Rook en flashbang

    /**
     * De Smoke Grenade (een wolk die in een schuurtje blijft en een beetje uit de open deur komt, in elke kleur) en de
     * Flashbang (een witte flits; het witte scherm als plaatje).
     */
    public static List<HowToScene> smokeFlash() {
        HowToSceneBuilder s = new HowToSceneBuilder("smoke_flashbang", "Smoke grenade and flashbang").plate(7, 6).height(4);
        Vec3 you = new Vec3(2.0, 1.0, 5.2);
        s.idle(6);
        // Een schuurtje: muren van twee hoog, een glazen dak (glas houdt rook tegen, en zo kijk je erin) en een open deur.
        Map<BlockPos, BlockState> shed = new LinkedHashMap<>();
        for (int y = 1; y <= 2; y++) {
            for (int x = 3; x <= 6; x++) {
                shed.put(new BlockPos(x, y, 0), Blocks.STONE_BRICKS.defaultBlockState());
                shed.put(new BlockPos(x, y, 3), Blocks.STONE_BRICKS.defaultBlockState());
            }
            for (int z = 1; z <= 2; z++) {
                shed.put(new BlockPos(3, y, z), Blocks.STONE_BRICKS.defaultBlockState());
                shed.put(new BlockPos(6, y, z), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, true);
        shed.put(new BlockPos(4, 1, 3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        shed.put(new BlockPos(4, 2, 3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        s.place(shed, HowToSceneBuilder.DROP);
        s.idle(6);
        s.place(HowToSceneBuilder.box(new BlockPos(3, 3, 0), new BlockPos(6, 3, 3), pos -> Blocks.GLASS.defaultBlockState()),
                HowToSceneBuilder.DROP);
        s.idle(10);

        s.chapter("Smoke");
        Vec3 inside = new Vec3(4.5, 1.0, 1.6);
        float aim = RobotScenes.yawTo(you, inside);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(EquipmentSlot.MAINHAND, new ItemStack(ModItems.SMOKE_GRENADE.get(), 4)), you);
        s.idle(8);
        int fuse = Math.round(ImtConfig.Weapons.SMOKE_FUSE_SECONDS * 20);
        CompoundTag grey = new CompoundTag();
        grey.putInt("SmokeColor", -1);
        Vec3 hand = WeaponScenes.muzzle(you, aim);
        s.actor("smoke", ModEntities.SMOKE_GRENADE.get(), 200, 20, 0.0f, grey,
                then(arc(hand, new Vec3(4.5, 1.6, 4.0), 0.6), new Vec3(4.5, 1.1, 2.8), inside));
        s.sound(SoundEvents.SNOWBALL_THROW, 0.4f, 0.7f);
        s.idle(fuse);
        s.sound(ModSounds.SMOKE_GRENADE_HISS.get(), 0.5f, 1.0f);
        // Tot het eind van het volgende hoofdstukje (dan zie je nog rook als het over zien gaat).
        s.smoke(290, inside.add(0, 0.5, 0), (float) ImtConfig.Weapons.SMOKE_RADIUS, SMOKE_GREY);
        s.text(150, new Vec3(5.0, 3.2, 1.5), Side.RIGHT,
                "After %s seconds a *Smoke Grenade* hisses and a cloud of %s blocks grows, for %s seconds. Smoke doesn't go"
                        + " through blocks: walls, glass and a closed door stop it, and through an open door only a little"
                        + " comes out.", HowToSceneBuilder.number(ImtConfig.Weapons.SMOKE_FUSE_SECONDS),
                HowToSceneBuilder.number(ImtConfig.Weapons.SMOKE_RADIUS), HowToSceneBuilder.number(ImtConfig.Weapons.SMOKE_SECONDS));
        s.idle(155);

        s.chapter("Nobody sees through it");
        s.text(110, new Vec3(5.0, 3.2, 1.5), Side.RIGHT,
                "Robots, turrets, drones and Raven soldiers can't see through smoke (so they don't shoot), and a camera"
                        + " looking into it only sees grey.");
        s.idle(115);

        s.chapter("Any colour");
        Vec3 out = new Vec3(1.2, 1.0, 1.4);
        float outAim = RobotScenes.yawTo(you, out);
        ItemStack redGrenade = new ItemStack(ModItems.SMOKE_GRENADE.get(), 4);
        redGrenade.set(ModDataComponents.SMOKE_COLOR.get(), DyeColor.RED);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, outAim, Map.of(EquipmentSlot.MAINHAND, redGrenade), you);
        s.idle(8);
        CompoundTag red = new CompoundTag();
        red.putInt("SmokeColor", DyeColor.RED.getId());
        s.actor("red", ModEntities.SMOKE_GRENADE.get(), 180, 18, 0.0f, red, arc(WeaponScenes.muzzle(you, outAim), out, 0.8));
        s.sound(SoundEvents.SNOWBALL_THROW, 0.4f, 0.7f);
        s.idle(fuse);
        s.sound(ModSounds.SMOKE_GRENADE_HISS.get(), 0.5f, 1.0f);
        s.smoke(140, out.add(0, 0.5, 0), 3.0f, 0xD8413A);
        s.text(110, out.add(0, 2.4, 0), Side.LEFT, "Grey, or in any colour: craft a Smoke Grenade with a *dye*.");
        s.idle(140);

        s.chapter("Flashbang");
        Vec3 bang = new Vec3(3.4, 1.0, 4.6);
        float bangAim = RobotScenes.yawTo(you, bang);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, bangAim, Map.of(EquipmentSlot.MAINHAND, new ItemStack(ModItems.FLASHBANG.get(), 4)), you);
        s.idle(8);
        int flashFuse = Math.round(ImtConfig.Weapons.FLASH_FUSE_SECONDS * 20);
        s.actor("flash", ModEntities.FLASHBANG.get(), flashFuse, 14, 0.0f, arc(WeaponScenes.muzzle(you, bangAim), bang, 0.7));
        s.sound(SoundEvents.SNOWBALL_THROW, 0.4f, 0.7f);
        s.idle(flashFuse);
        s.pulse(10, bang.add(0, 0.4, 0), 2.5f, 0xFFFFFF);
        s.sound(ModSounds.FLASHBANG_BANG.get(), 0.5f, 1.0f);
        s.idle(8);
        s.text(120, bang.add(0, 1.0, 0), Side.RIGHT,
                "A *Flashbang* goes off after %s seconds with a bright flash and a loud bang. Every player who can see it (up"
                        + " to %s blocks, no wall in between) gets a white screen and a beep, you too. Robots, turrets,"
                        + " drones and monsters don't notice.", HowToSceneBuilder.number(ImtConfig.Weapons.FLASH_FUSE_SECONDS),
                HowToSceneBuilder.number(ImtConfig.Weapons.FLASH_RADIUS));
        s.idle(125);
        s.picture(120, "flashbang", 426, 240, s.arrow(0.5f, 0.5f, "Looking straight at it: %s seconds of white",
                HowToSceneBuilder.number(ImtConfig.Weapons.FLASH_SECONDS)));
        s.idle(125);
        return List.of(s.build());
    }

    // ================================================================ De Remote EMP en de Remote Frag

    /**
     * De Remote EMP (op de vloer naast een open kabel; van ver af met de EMP Trigger: het torentje gaat uit) en de Remote
     * Frag (op een muur; met de Frag Trigger een blok eruit), en dat je een Remote Frag alleen met een schaar weghaalt.
     */
    public static List<HowToScene> remotes() {
        HowToSceneBuilder s = new HowToSceneBuilder("remote_charges", "Remote EMP and Remote Frag").plate(7, 6).height(3);
        BlockPos solar = new BlockPos(1, 1, 3);
        BlockPos turret = new BlockPos(5, 1, 2);
        BlockPos emp = new BlockPos(3, 1, 4);
        BlockPos fragSpot = new BlockPos(3, 1, 1);
        s.idle(6);
        s.place(wall(2, 4, 0, 2), HowToSceneBuilder.DROP);
        s.idle(4);
        s.place(solar, RobotScenes.solar());
        s.idle(4);
        s.place(turret, DefenceScenes.turret(ModBlocks.ROCKET_TURRET.get(), false, true));
        s.idle(8);
        Vec3[] cable = {new Vec3(2.0, 1.05, 3.5), new Vec3(5.5, 1.05, 3.5), new Vec3(5.5, 1.05, 3.0)};
        s.cable(CableKind.POWER, 16, cable);
        s.idle(16);
        s.change(turret, DefenceScenes.turret(ModBlocks.ROCKET_TURRET.get(), true, true));
        s.idle(8);

        s.chapter("Stick it on");
        Vec3 near = new Vec3(2.2, 1.0, 5.2);
        ItemStack trigger = new ItemStack(ModItems.EMP_TRIGGER.get());
        s.player("you", 140, 0, RobotScenes.yawTo(near, Vec3.atCenterOf(emp)), Map.of(EquipmentSlot.MAINHAND,
                new ItemStack(ModItems.REMOTE_EMP.get(), 2)), near);
        s.idle(10);
        s.rightClick(HowToSceneBuilder.top(emp.below()), new ItemStack(ModItems.REMOTE_EMP.get()));
        s.idle(10);
        s.place(emp, charge(ModBlocks.REMOTE_EMP.get(), AttachFace.FLOOR, Direction.NORTH));
        s.sound(ModSounds.EMP_GRENADE_STICK.get(), 0.5f, 1.0f);
        s.text(110, Vec3.atCenterOf(emp), Side.RIGHT,
                "Stick a *Remote EMP* on a floor, wall or ceiling, here next to an open cable. With an *EMP Trigger* on you, it"
                        + " belongs to that trigger right away (otherwise right-click it with the trigger).");
        s.idle(115);

        s.chapter("From far away");
        Vec3 far = new Vec3(0.6, 1.0, 5.4);
        s.player("you", HowToSceneBuilder.UNTIL_END, 30, RobotScenes.yawTo(far, Vec3.atCenterOf(emp)),
                Map.of(EquipmentSlot.MAINHAND, trigger), near, far);
        s.idle(36);
        s.rightClick(far.add(0.3, 1.4, -0.3), trigger);
        s.idle(12);
        s.clear(emp);
        s.pulse(18, Vec3.atCenterOf(emp), (float) ImtConfig.Weapons.EMP_RADIUS, EMP_BLUE);
        s.sound(ModSounds.EMP_BLAST.get(), 0.5f, 1.0f);
        s.change(turret, DefenceScenes.turret(ModBlocks.ROCKET_TURRET.get(), false, true));
        s.text(130, Vec3.atCenterOf(emp), Side.RIGHT,
                "*Right-click* with the trigger: all its Remote EMPs up to %s blocks away go off, one after another, and then"
                        + " they are used up. The open cable carries no power for a while, so the turret goes off."
                        + " *Sneak + right-click* tells how many belong to it.", HowToSceneBuilder.number(ImtConfig.Weapons.TRIGGER_RANGE));
        s.idle(135);

        s.chapter("The Remote Frag");
        ItemStack fragTrigger = new ItemStack(ModItems.FRAG_TRIGGER.get());
        Vec3 wallSpot = new Vec3(3.0, 1.0, 2.6);
        s.player("you", HowToSceneBuilder.UNTIL_END, 30, 180.0f, Map.of(EquipmentSlot.MAINHAND, new ItemStack(ModItems.REMOTE_FRAG.get())),
                far, wallSpot);
        s.idle(32);
        s.rightClick(HowToSceneBuilder.face(fragSpot.north(), Direction.SOUTH), new ItemStack(ModItems.REMOTE_FRAG.get()));
        s.idle(10);
        s.place(fragSpot, charge(ModBlocks.REMOTE_FRAG.get(), AttachFace.WALL, Direction.SOUTH));
        s.sound(ModSounds.EMP_GRENADE_STICK.get(), 0.5f, 0.8f);
        s.text(110, Vec3.atCenterOf(fragSpot).add(0, 0.6, 0), Side.RIGHT,
                "A *Remote Frag* works the same way with a *Frag Trigger*. With the trigger in your hand you see red *FRAG*"
                        + " signs above your charges.");
        s.idle(60);
        s.player("you", HowToSceneBuilder.UNTIL_END, 30, RobotScenes.yawTo(far, Vec3.atCenterOf(fragSpot)),
                Map.of(EquipmentSlot.MAINHAND, fragTrigger), wallSpot, far);
        s.idle(56);
        s.rightClick(far.add(0.3, 1.4, -0.3), fragTrigger);
        s.idle(12);
        BlockPos blasted = fragSpot.north();
        s.clear(fragSpot);
        s.pulse(12, HowToSceneBuilder.face(blasted, Direction.SOUTH), 1.8f, BLAST);
        s.sound(SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 1.0f);
        s.clear(blasted);
        s.crack(blasted.west(), 6);
        s.crack(blasted.east(), 6);
        s.crack(blasted.above(), 6);
        s.idle(20);

        s.chapter("Shears and the alarm");
        s.text(140, HowToSceneBuilder.face(blasted, Direction.SOUTH), Side.RIGHT,
                "As strong as a Sticky Frag: one block of stone per frag. You can't break a Remote Frag: *shears* cut the"
                        + " wire, *sneak + right-click with an empty hand* takes it off live. In someone else's zone it sets off"
                        + " the *alarm*. An EMP Trigger never sets off a Remote Frag, and the other way round.");
        s.idle(145);
        return List.of(s.build());
    }

    // ================================================================ Terugwerpen (hoofdstuk 69)

    /**
     * Granaten oprapen en teruggooien, en een Remote Frag verhangen (hoofdstuk 69, jouw wens: "iemand gooit een EMP maar
     * jij gooit die terug, of er hangt een remote frag op je base en jij hangt die gewoon ergens anders"). Een vreemde gooit
     * een EMP-granaat, jij raapt hem op (je Glock gaat in je holster) en gooit hem terug; een sticky op jou trek je eraf en
     * gooit hem op hun robot; een Remote Frag op jouw muur haal je eraf met sneak + lege hand en hang je op hun muur, en
     * hun eigen knop blaast die open.
     */
    public static List<HowToScene> throwback() {
        HowToSceneBuilder s = new HowToSceneBuilder("grenade_throwback", "Throwing grenades back").plate(8, 6).height(3);
        Vec3 you = new Vec3(6.0, 1.0, 4.0);
        // Hun robot links van hen, zodat het linkeruiteinde van hun muur (waar straks de Remote Frag hangt) vrij in beeld is.
        Vec3 them = new Vec3(1.2, 1.0, 3.0);
        Vec3 robot = new Vec3(0.4, 1.0, 2.5);
        float youAim = RobotScenes.yawTo(you, them);
        float themAim = RobotScenes.yawTo(them, you);
        float robotYaw = RobotScenes.yawTo(robot, you);
        CompoundTag guard = RobotScenes.robot(RobotStyle.COMMANDO, RobotScenes.board("scan", "", "laser"), true);
        ItemStack glock = WeaponScenes.loadedGlock();
        List<ItemStack> holsterEmpty = List.of(WeaponScenes.holster(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY);
        List<ItemStack> holsterFull = List.of(WeaponScenes.holster(glock), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY);
        s.idle(6);
        Map<BlockPos, BlockState> walls = new LinkedHashMap<>(wall(0, 2, 0, 2));
        walls.putAll(wall(5, 7, 0, 2));
        s.place(walls, HowToSceneBuilder.DROP);
        s.idle(8);
        s.actor("robot", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, robotYaw, guard, robot);
        s.stranger("them", "Intruder", HowToSceneBuilder.UNTIL_END, 0, themAim,
                Map.of(EquipmentSlot.MAINHAND, new ItemStack(ModItems.EMP_GRENADE.get(), 3)), them);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, youAim, Map.of(EquipmentSlot.MAINHAND, glock), holsterEmpty, you);
        s.idle(10);

        s.chapter("Catch it");
        s.text(110, you.add(0, 2.1, 0), Side.LEFT,
                "Someone throws a live grenade at you? *Right-click it*, whatever you're holding (a gun too): it's in your hand"
                        + " at once. *The fuse keeps running*, so be quick.");
        s.idle(115);
        int fuse = Math.round(ImtConfig.Weapons.ROLLING_FUSE_SECONDS * 20);
        int flight = 20;
        Vec3 land = new Vec3(5.2, 1.0, 3.3);
        CompoundTag incoming = new CompoundTag();
        incoming.putInt("FuseLeft", fuse);
        s.actor("grenade", ModEntities.ROLLING_EMP_GRENADE.get(), flight + 8, flight, 0.0f, incoming,
                then(arc(WeaponScenes.muzzle(them, themAim), new Vec3(4.7, 1.0, 3.0), 1.3), new Vec3(5.0, 1.25, 3.15), land));
        s.sound(SoundEvents.SNOWBALL_THROW, 0.4f, 0.7f);
        s.idle(flight);
        s.sound(ModSounds.EMP_GRENADE_BEEP.get(), 0.25f, 1.0f);
        s.idle(4);
        s.rightClick(land.add(0, 0.2, 0), glock);
        s.idle(4);
        ItemStack liveEmp = new ItemStack(ModItems.EMP_GRENADE.get());
        liveEmp.set(ModDataComponents.LIVE_FUSE.get(), 0L); // glimt, zoals een opgeraapte granaat
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, youAim, Map.of(EquipmentSlot.MAINHAND, liveEmp), holsterFull, you);
        s.sound(SoundEvents.ITEM_PICKUP, 0.5f, 0.8f);
        s.sound(ModSounds.EMP_GRENADE_BEEP.get(), 0.25f, 1.0f);
        s.idle(6);

        s.chapter("Throw it back");
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, youAim, Map.of(), holsterFull, you);
        Vec3 there = new Vec3(1.0, 1.0, 1.9);
        lob(s, "back", ModEntities.ROLLING_EMP_GRENADE.get(), new CompoundTag(), 14, Math.max(20, fuse - flight - 14),
                arc(WeaponScenes.muzzle(you, youAim), there, 1.0));
        s.pulse(18, there.add(0, 0.4, 0), (float) ImtConfig.Weapons.EMP_RADIUS, EMP_BLUE);
        s.sound(ModSounds.EMP_BLAST.get(), 0.5f, 1.0f);
        s.actor("robot", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, robotYaw, RobotScenes.down(guard), robot);
        s.idle(8);
        s.text(130, there.add(0, 1.6, 0), Side.RIGHT,
                "Right-click throws it back with what's left of the fuse: their robot is out. Hold it too long and it goes off"
                        + " in your hand; *Q* drops it, and it rolls again.");
        s.idle(135);

        s.chapter("What you held");
        s.text(140, you.add(0, 2.1, 0), Side.LEFT,
                "Your Glock went into your *holster* (a rifle with a sling goes onto your *sling*, a grenade into your pouch);"
                        + " anything else drops to the ground. A live grenade glows and beeps, and the bottom of your screen"
                        + " counts down. It doesn't fit in a pouch or on a pegboard.");
        s.idle(145);

        s.chapter("Stickies too");
        s.actor("robot", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, robotYaw, guard, robot);
        s.sound(ModSounds.ROBOT_POWER_ON.get(), 0.4f, 1.0f);
        s.stranger("them", "Intruder", HowToSceneBuilder.UNTIL_END, 0, themAim,
                Map.of(EquipmentSlot.MAINHAND, new ItemStack(ModItems.STICKY_EMP_GRENADE.get(), 3)), them);
        s.idle(20);
        int stickyFuse = Math.round(ImtConfig.Weapons.STICKY_FUSE_SECONDS * 20);
        int stickFlight = 14;
        Vec3 chest = you.add(WeaponScenes.ahead(youAim).scale(0.3)).add(0, 1.2, 0);
        CompoundTag sticking = new CompoundTag();
        sticking.putInt("FuseLeft", stickyFuse + stickFlight);
        s.actor("sticky", ModEntities.STICKY_EMP_GRENADE.get(), stickFlight + 10, stickFlight, 0.0f, sticking,
                arc(WeaponScenes.muzzle(them, themAim), chest, 0.8));
        s.sound(SoundEvents.SNOWBALL_THROW, 0.4f, 0.7f);
        s.idle(stickFlight);
        s.sound(ModSounds.EMP_GRENADE_STICK.get(), 0.5f, 1.0f);
        s.idle(4);
        s.rightClick(chest, ItemStack.EMPTY);
        s.idle(6);
        ItemStack liveSticky = new ItemStack(ModItems.STICKY_EMP_GRENADE.get());
        liveSticky.set(ModDataComponents.LIVE_FUSE.get(), 0L);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, youAim, Map.of(EquipmentSlot.MAINHAND, liveSticky), holsterFull, you);
        s.sound(SoundEvents.ITEM_PICKUP, 0.5f, 0.8f);
        s.idle(6);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, youAim, Map.of(), holsterFull, you);
        Vec3 robotChest = robot.add(WeaponScenes.ahead(robotYaw).scale(0.3)).add(0, 1.2, 0);
        lob(s, "sticky_back", ModEntities.STICKY_EMP_GRENADE.get(), new CompoundTag(), 12, Math.max(16, stickyFuse - 16),
                arc(WeaponScenes.muzzle(you, youAim), robotChest, 0.7));
        s.pulse(18, robotChest, (float) ImtConfig.Weapons.EMP_RADIUS, EMP_BLUE);
        s.sound(ModSounds.EMP_BLAST.get(), 0.5f, 1.0f);
        s.actor("robot", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, robotYaw, RobotScenes.down(guard), robot);
        s.idle(8);
        s.text(140, robot.add(0, 2.2, 0), Side.RIGHT,
                "Stickies come off too: off a wall, the floor, a robot, a drone or another player. One stuck on *you*? Your"
                        + " first right-click pulls it off.");
        s.idle(145);

        s.chapter("Move a Remote Frag");
        s.actor("robot", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, robotYaw, guard, robot);
        BlockPos onYours = new BlockPos(6, 1, 1);
        s.place(onYours, charge(ModBlocks.REMOTE_FRAG.get(), AttachFace.WALL, Direction.SOUTH));
        s.sound(ModSounds.EMP_GRENADE_STICK.get(), 0.5f, 0.8f);
        ItemStack fragTrigger = new ItemStack(ModItems.FRAG_TRIGGER.get());
        s.stranger("them", "Intruder", HowToSceneBuilder.UNTIL_END, 0, themAim, Map.of(EquipmentSlot.MAINHAND, fragTrigger), them);
        s.text(100, Vec3.atCenterOf(onYours).add(0, 0.6, 0), Side.RIGHT,
                "Someone hung a *Remote Frag* on your wall, and holds its Frag Trigger.");
        s.idle(105);
        Vec3 atYours = new Vec3(6.2, 1.0, 2.3);
        s.player("you", HowToSceneBuilder.UNTIL_END, 24, 180.0f, Map.of(), holsterFull, you, atYours);
        s.idle(26);
        CompoundTag crouch = new CompoundTag();
        crouch.putBoolean("Crouch", true);
        s.person("you", crouch, 20, 0, 180.0f, Map.of(), holsterFull, atYours);
        s.sneakRightClick(HowToSceneBuilder.center(onYours), ItemStack.EMPTY);
        s.idle(10);
        s.clear(onYours);
        ItemStack liveFrag = new ItemStack(ModItems.REMOTE_FRAG.get());
        liveFrag.set(ModDataComponents.LIVE_CHARGE.get(), new com.senne.imttech.weapons.LiveCharge(new java.util.UUID(0, 0),
                java.util.Optional.empty(), "Intruder", java.util.Optional.empty()));
        s.sound(ModSounds.EMP_GRENADE_STICK.get(), 0.4f, 1.4f);
        s.idle(10);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(EquipmentSlot.MAINHAND, liveFrag), holsterFull, atYours);
        s.text(140, atYours.add(0, 2.1, 0), Side.RIGHT,
                "*Sneak + right-click with an empty hand* takes it off (a Remote EMP too). It stays *live* and still belongs to"
                        + " their trigger: if they press while you carry it, it goes off in your pocket.");
        s.idle(145);
        BlockPos onTheirs = new BlockPos(0, 1, 1);
        Vec3 atTheirs = new Vec3(1.3, 1.0, 1.9);
        float hangYaw = RobotScenes.yawTo(atTheirs, HowToSceneBuilder.center(onTheirs));
        s.player("you", HowToSceneBuilder.UNTIL_END, 40, hangYaw, Map.of(EquipmentSlot.MAINHAND, liveFrag), holsterFull,
                atYours, new Vec3(3.8, 1.0, 2.4), atTheirs);
        s.idle(42);
        s.rightClick(HowToSceneBuilder.face(onTheirs.north(), Direction.SOUTH), liveFrag);
        s.idle(8);
        s.place(onTheirs, charge(ModBlocks.REMOTE_FRAG.get(), AttachFace.WALL, Direction.SOUTH));
        s.sound(ModSounds.EMP_GRENADE_STICK.get(), 0.5f, 0.8f);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, hangYaw, Map.of(), holsterFull, atTheirs);
        s.text(110, Vec3.atCenterOf(onTheirs).add(0, 0.6, 0), Side.RIGHT,
                "Hang it somewhere else, on their own wall for instance. Their trigger knows where it hangs now.");
        s.idle(115);
        s.player("you", HowToSceneBuilder.UNTIL_END, 40, youAim, Map.of(), holsterFull, atTheirs, new Vec3(3.8, 1.0, 3.2), you);
        s.idle(42);

        s.chapter("Their own button");
        s.sound(ModSounds.TRIGGER_CLICK.get(), 0.6f, 1.0f);
        s.idle(6);
        BlockPos blasted = onTheirs.north();
        s.clear(onTheirs);
        s.pulse(12, HowToSceneBuilder.face(blasted, Direction.SOUTH), 1.8f, BLAST);
        s.sound(SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 1.0f);
        s.clear(blasted);
        s.crack(blasted.east(), 6);
        s.crack(blasted.above(), 6);
        s.idle(10);
        s.text(140, HowToSceneBuilder.face(blasted, Direction.SOUTH), Side.RIGHT,
                "They press their Frag Trigger: it goes off where *you* hung it. *Shears* cut the wire instead: then it's a"
                        + " plain Remote Frag, and yours.");
        s.idle(145);
        return List.of(s.build());
    }
}
