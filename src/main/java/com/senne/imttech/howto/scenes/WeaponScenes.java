package com.senne.imttech.howto.scenes;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.CrushedGunpowderItem;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunPartItem;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.repair.CrackMachineBlock;
import com.senne.imttech.robot.RobotStyle;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import com.senne.imttech.weapons.LaserPart;
import com.senne.imttech.weapons.LaserPartItem;
import com.senne.imttech.weapons.LaserParts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * De animaties van het tabblad Weapons (hoofdstuk 63, jouw vraag van 8 november: "ga verder met IMT weapons"): de
 * lasergeweren, ze bouwen uit onderdelen, de Ion Blaster, de Glock 19, de Gunsmith Table, de Reloading Bench, en de
 * holster, de pouches en de slings. De granaten en de Remote EMP en Remote Frag staan in {@link GrenadeScenes}. Hoe het
 * echt werkt staat in LEESMIJ ("Wapens", "Vuurwapens: de Glock 19 uit onderdelen", "Lasergeweren uit onderdelen").
 *
 * Wie schiet staat onderaan in het midden van het beeld, het doelwit bovenaan in het midden: zo staan de tekstballonnen
 * aan de zijkanten nergens voor (zie de les in hoofdstuk 63, stap 8).
 */
public final class WeaponScenes {
    private WeaponScenes() {}

    /** Een schot van een lasergeweer (blauw, zoals de echte straal: zie GunEffects). */
    static final int LASER = 0x6FC8FF;
    /** Het spoortje van een kogel. */
    static final int BULLET = 0xFFD86A;

    // ================================================================ Hulpjes

    /** Een richting op de grond voor wie naar {@code yaw} kijkt (0 = zuid). */
    static Vec3 ahead(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3(-Math.sin(rad), 0, Math.cos(rad));
    }

    /** Rechts van wie naar {@code yaw} kijkt. */
    static Vec3 right(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
    }

    /** Waar de loop is van een wapen in de rechterhand van wie op {@code feet} staat en naar {@code yaw} kijkt. */
    static Vec3 muzzle(Vec3 feet, float yaw) {
        return feet.add(ahead(yaw).scale(0.65)).add(right(yaw).scale(0.35)).add(0, 1.4, 0);
    }

    /** {@code shots} schoten van {@code from} naar {@code to}, om de {@code interval} ticks. De klok staat daarna erachter. */
    static void fire(HowToSceneBuilder s, Vec3 from, Vec3 to, int rgb, int shots, int interval, SoundEvent sound) {
        for (int i = 0; i < shots; i++) {
            s.beam(Math.min(5, Math.max(2, interval - 1)), from, to, rgb);
            s.sound(sound, 0.35f, 1.0f);
            s.idle(interval);
        }
    }

    /** Een husk die in {@code move} ticks langs {@code path} loopt en er {@code life} ticks is (daarna valt hij om). */
    static void husk(HowToSceneBuilder s, String key, int life, int move, Vec3... path) {
        s.actor(key, EntityType.HUSK, life, move, 0.0f, path);
    }

    /** Een holster met deze inhoud (een pistool, of leeg). */
    static ItemStack holster(ItemStack... inside) {
        ItemStack holster = new ItemStack(ModItems.HOLSTER.get());
        GearItem.setContents(holster, List.of(inside));
        return holster;
    }

    /** Een pouch van deze soort met deze inhoud. */
    static ItemStack pouch(GearItem.Kind kind, ItemStack... inside) {
        ItemStack pouch = new ItemStack(kind == GearItem.Kind.GRENADE_POUCH ? ModItems.GRENADE_POUCH.get() : ModItems.MAG_POUCH.get());
        GearItem.setContents(pouch, List.of(inside));
        return pouch;
    }

    /** Een compleet titanium lasergeweer met een sling eraan (voor een sling-vakje). */
    static ItemStack slung(GunType type) {
        ItemStack gun = LaserGunItem.complete(type, GunMaterial.TITANIUM);
        LaserGunItem.setParts(gun, LaserGunItem.parts(gun).with(LaserPart.SLING, LaserParts.extra()));
        LaserGunItem.setCell(gun, EnergyCellItem.FULL);
        return gun;
    }

    static ItemStack loadedGlock() {
        return FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
    }

    static BlockState bench(net.minecraft.world.level.block.Block block, Direction facing) {
        return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    /** Een Crusher met zijn voorkant naar je toe (zoals de bank), stil of bezig (dan draait zijn voorkant). */
    static BlockState crusher(boolean working) {
        return bench(ModBlocks.CRUSHER.get(), Direction.SOUTH).setValue(CrackMachineBlock.WORKING, working);
    }

    /** Voorwerpen naast elkaar op een rij dwars door het beeld (op hoogte {@code y}), rond {@code middle}. */
    static void row(HowToSceneBuilder s, int duration, Vec3 middle, float spacing, float scale, List<ItemStack> stacks) {
        // Dwars door het beeld: van links naar rechts op het scherm is (0,766; 0,643) in de wereld (de camera staat op 40).
        Vec3 across = new Vec3(0.766, 0, 0.643).scale(spacing);
        double first = -(stacks.size() - 1) / 2.0;
        for (int i = 0; i < stacks.size(); i++) {
            s.item(duration, middle.add(across.scale(first + i)), stacks.get(i), scale);
            s.idle(3);
        }
    }

    // ================================================================ Lasergeweren

    /**
     * De vier lasergeweren: schieten op een husk, de rifle (automatisch), de sniper (één zware straal), de shotgun (een
     * waaier), energiecellen (de HUD als plaatje) en laden in het laadstation.
     */
    public static List<HowToScene> laserGuns() {
        HowToSceneBuilder s = new HowToSceneBuilder("laser_guns", "Laser guns").plate(7, 6).height(3);
        Vec3 you = new Vec3(2.0, 1.0, 4.8);
        Vec3 target = new Vec3(5.0, 1.0, 1.2);
        float aim = RobotScenes.yawTo(you, target);
        Vec3 gun = muzzle(you, aim);
        s.idle(6);
        s.chapter("Four laser guns");
        s.player("you", 60, 0, aim, Map.of(), you);
        row(s, 110, new Vec3(3.6, 1.6, 2.6), 1.2f, 1.1f, List.of(LaserGunItem.loaded(GunType.PISTOL),
                LaserGunItem.loaded(GunType.RIFLE), LaserGunItem.loaded(GunType.SNIPER), LaserGunItem.loaded(GunType.SHOTGUN)));
        s.text(100, null, Side.RIGHT,
                "Four laser guns: the *Laser Pistol* (a click is a shot), the *Laser Rifle* (hold: automatic), the *Laser"
                        + " Sniper* (one heavy beam, a scope that zooms in 4 times) and the *Laser Shotgun* (a fan of beams).");
        s.idle(100);

        s.chapter("Shooting");
        s.player("you", 130, 0, aim, Map.of(EquipmentSlot.MAINHAND, LaserGunItem.loaded(GunType.PISTOL)), you);
        husk(s, "husk", 70, 60, target.add(1.4, 0, -0.8), target);
        s.text(120, target.add(0, 2.2, 0), Side.RIGHT,
                "*Left-click* fires, *right-click* (hold) aims through the sights: zoomed in and more accurate, but you"
                        + " walk slower. A headshot does %sx damage.", HowToSceneBuilder.number(ImtConfig.Weapons.HEADSHOT));
        s.idle(30);
        fire(s, gun, target.add(0, 1.3, 0), LASER, 3, 12, ModSounds.LASER_PISTOL_FIRE.get());
        s.sound(SoundEvents.HUSK_DEATH, 0.4f, 1.0f);
        s.idle(60);

        s.chapter("Rifle, sniper, shotgun");
        Vec3 rifleTarget = new Vec3(4.6, 1.0, 2.0);
        s.player("you", 70, 0, RobotScenes.yawTo(you, rifleTarget), Map.of(EquipmentSlot.MAINHAND, LaserGunItem.loaded(GunType.RIFLE)), you);
        husk(s, "husk2", 50, 40, new Vec3(6.6, 1.0, 0.6), rifleTarget);
        s.text(60, rifleTarget.add(0, 2.2, 0), Side.RIGHT, "The *rifle* keeps firing as long as you hold the button.");
        s.idle(22);
        fire(s, muzzle(you, RobotScenes.yawTo(you, rifleTarget)), rifleTarget.add(0, 1.3, 0), LASER, 7, 3,
                ModSounds.LASER_RIFLE_FIRE.get());
        s.sound(SoundEvents.HUSK_DEATH, 0.4f, 1.0f);
        s.idle(28);
        Vec3 far = new Vec3(6.5, 1.0, 0.4);
        float farAim = RobotScenes.yawTo(you, far);
        s.player("you", 60, 0, farAim, Map.of(EquipmentSlot.MAINHAND, LaserGunItem.loaded(GunType.SNIPER)), you);
        husk(s, "husk3", 36, 0, far);
        s.text(55, far.add(0, 2.2, 0), Side.RIGHT, "The *sniper* fires one heavy beam, far away.");
        s.idle(30);
        Vec3 sniperFrom = muzzle(you, farAim);
        s.beam(6, sniperFrom, far.add(0, 1.3, 0), LASER);
        s.beam(6, sniperFrom.add(0, 0.04, 0), far.add(0, 1.34, 0), 0xF2FCFF);
        s.pulse(10, far.add(0, 1.3, 0), 0.6f, LASER);
        s.sound(ModSounds.LASER_SNIPER_FIRE.get(), 0.4f, 1.0f);
        s.idle(6);
        s.sound(SoundEvents.HUSK_DEATH, 0.4f, 1.0f);
        s.idle(24);
        Vec3 close = new Vec3(3.4, 1.0, 3.1);
        float closeAim = RobotScenes.yawTo(you, close);
        s.player("you", 70, 0, closeAim, Map.of(EquipmentSlot.MAINHAND, LaserGunItem.loaded(GunType.SHOTGUN)), you);
        husk(s, "husk4", 44, 30, new Vec3(4.6, 1.0, 1.8), close);
        s.text(65, close.add(0, 2.2, 0), Side.LEFT, "The *shotgun* fires a fan of beams: deadly up close.");
        s.idle(34);
        Vec3 shotgunFrom = muzzle(you, closeAim);
        Vec3 dir = close.add(0, 1.2, 0).subtract(shotgunFrom);
        Vec3 side = right(closeAim);
        int rays = Math.round(ImtConfig.Weapons.SHOTGUN_RAYS);
        for (int i = 0; i < rays; i++) {
            double spread = (i - (rays - 1) / 2.0) * 0.12;
            double lift = ((i * 37) % 5 - 2) * 0.05;
            s.beam(4, shotgunFrom, shotgunFrom.add(dir.scale(1.15)).add(side.scale(spread * dir.length())).add(0, lift, 0), LASER);
        }
        s.sound(ModSounds.LASER_SHOTGUN_FIRE.get(), 0.4f, 1.0f);
        s.idle(10);
        s.sound(SoundEvents.HUSK_DEATH, 0.4f, 1.0f);
        s.idle(26);

        s.chapter("Energy cells");
        s.player("you", 150, 0, aim, Map.of(EquipmentSlot.MAINHAND, LaserGunItem.loaded(GunType.RIFLE)), you);
        s.item(140, you.add(1.0, 1.7, -0.8), EnergyCellItem.withCharge(100), 1.0f);
        s.text(140, you.add(1.0, 1.9, -0.8), Side.RIGHT,
                "A laser gun takes an *Energy Cell* as its magazine; cells don't stack, each has its own charge. Right-click"
                        + " with a cell puts it in the laser gun in your hotbar; *[R]* swaps in a fuller cell from your"
                        + " magazine pouch, *[U]* takes it out.");
        s.idle(145);
        s.picture(130, "laser_hud", 426, 240,
                s.arrow(0.9f, 0.87f, "Shots left in the cell"),
                s.arrow(0.93f, 0.94f, "Cells you carry (also in your pouch)"));
        s.idle(135);

        s.chapter("Charging cells");
        BlockPos station = new BlockPos(4, 1, 2);
        BlockPos solar = new BlockPos(5, 1, 2);
        s.place(station, PowerScenes.station(Direction.SOUTH));
        s.idle(5);
        s.place(solar, RobotScenes.solar());
        s.idle(10);
        Vec3 front = HowToSceneBuilder.face(station, Direction.SOUTH);
        s.rightClick(front, EnergyCellItem.withCharge(5));
        s.idle(14);
        s.meter(90, HowToSceneBuilder.top(station).add(0, 0.5, 0), "CELL", 0.05f, 1.0f, HowToSceneBuilder.CYAN);
        s.text(90, front, Side.RIGHT, "Charge empty cells in a *Charging Station*, in the slot of the glasses.");
        s.idle(95);
        return List.of(s.build());
    }

    // ================================================================ Uit onderdelen

    /**
     * Lasergeweren uit onderdelen: de zes onderdelen (titanium en netherite), de Nether Star als kern, de Laser Workbench
     * (zijn scherm als plaatje), slijtage zonder storingen, en een sling: het geweer op je rug.
     */
    public static List<HowToScene> laserWorkbench() {
        HowToSceneBuilder s = new HowToSceneBuilder("laser_workbench", "Laser guns from parts").plate(5, 5).height(3);
        BlockPos table = new BlockPos(2, 1, 1);
        s.idle(6);
        s.chapter("Six parts");
        List<ItemStack> parts = new ArrayList<>();
        for (LaserPart part : LaserPart.wearing()) {
            parts.add(LaserPartItem.of(GunType.RIFLE, part, GunMaterial.TITANIUM));
        }
        row(s, 170, new Vec3(2.5, 1.4, 2.5), 0.75f, 1.0f, parts);
        s.text(80, null, Side.RIGHT,
                "A laser gun is six parts: a *frame*, a *receiver*, an *emitter*, *optics*, a *capacitor* and a *trigger"
                        + " module*. Each part belongs to one gun; you make them at the *Gunsmith Table* (tab *Make parts*).");
        s.idle(85);
        s.text(80, null, Side.RIGHT,
                "Each part is *titanium* or *netherite* (a titanium part with two Netherite Ingots): a bit more damage, less"
                        + " recoil and the least wear.");
        s.idle(85);

        s.chapter("The core");
        s.item(100, new Vec3(2.5, 1.8, 2.5), new ItemStack(Items.NETHER_STAR), 1.2f);
        s.text(95, new Vec3(2.5, 2.2, 2.5), Side.RIGHT,
                "Every gun needs a *Nether Star* as its core: without it, it doesn't shoot (*NO NETHER STAR*). You can take"
                        + " it out again and put it in another gun.");
        s.idle(100);

        s.chapter("The Laser Workbench");
        s.place(table, bench(ModBlocks.LASER_WORKBENCH.get(), Direction.SOUTH));
        s.idle(10);
        s.text(60, HowToSceneBuilder.top(table), Side.RIGHT, "You build it at a *Laser Workbench*, just like a Glock at the"
                + " Gunsmith Table.");
        s.idle(65);
        s.rightClick(HowToSceneBuilder.face(table, Direction.SOUTH), ItemStack.EMPTY);
        s.idle(14);
        s.picture(150, "laser_workbench", 368, 214,
                s.arrow(0.12f, 0.4f, "Your parts, Nether Stars, slings and laser guns"),
                s.arrow(0.48f, 0.55f, "Drag a part onto the gun: start with a *frame*"),
                s.arrow(0.5f, 0.24f, "What's missing is dotted"),
                s.arrow(0.85f, 0.2f, "Each part with its quality and condition"),
                s.arrow(0.87f, 0.92f, "*Take the gun*"));
        s.idle(155);

        s.chapter("Wear, no jams");
        s.item(100, new Vec3(2.5, 1.8, 2.6), LaserPartItem.of(GunType.RIFLE, LaserPart.EMITTER, GunMaterial.TITANIUM, 12), 1.1f);
        s.text(100, new Vec3(2.5, 2.0, 2.6), Side.RIGHT,
                "Every shot may wear a part a little (half as often as a Glock), but a laser never jams. Below %s%% a worn"
                        + " part makes the gun worse; at 0%% it's broken (*EMITTER BROKEN*) until you replace it.",
                HowToSceneBuilder.number(ImtConfig.Guns.WORN_BELOW));
        s.idle(105);

        s.chapter("A sling");
        Vec3 you = new Vec3(2.5, 1.0, 3.2);
        s.item(50, new Vec3(1.2, 1.9, 3.6), new ItemStack(ModItems.GUN_SLING.get()), 1.0f);
        List<ItemStack> worn = List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, slung(GunType.RIFLE), ItemStack.EMPTY);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 220.0f, Map.of(), worn, you);
        s.text(120, you.add(0, 2.0, 0), Side.RIGHT,
                "Hang a *Gun Sling* on a rifle, sniper or shotgun at the workbench: then it goes in a sling slot, slung over"
                        + " your back or in front of your chest, and you draw it from the wheel.");
        s.idle(60);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 130.0f, Map.of(), worn, you);
        s.idle(65);
        return List.of(s.build());
    }

    // ================================================================ De Ion Blaster

    /** De Ion Blaster: een robot en een drone vallen neer zonder kapot te gaan, een koe merkt er niets van. */
    public static List<HowToScene> ionBlaster() {
        HowToSceneBuilder s = new HowToSceneBuilder("ion_blaster", "Ion Blaster").plate(7, 6).height(3);
        Vec3 you = new Vec3(2.0, 1.0, 4.8);
        Vec3 robot = new Vec3(5.0, 1.0, 1.2);
        Vec3 drone = new Vec3(3.6, 2.4, 1.4);
        Vec3 cow = new Vec3(5.4, 1.0, 3.8);
        CompoundTag guard = RobotScenes.robot(RobotStyle.COMMANDO, RobotScenes.board("scan", "", "laser"), true);
        CompoundTag flyer = RobotScenes.drone(RobotScenes.board("scan"), true);
        ItemStack blaster = new ItemStack(ModItems.ION_BLASTER.get());
        s.idle(6);
        s.actor("robot", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, RobotScenes.yawTo(robot, you), guard, robot);
        s.actor("drone", ModEntities.GUARD_DRONE.get(), HowToSceneBuilder.UNTIL_END, 0, 30.0f, flyer, drone);
        s.actor("cow", EntityType.COW, HowToSceneBuilder.UNTIL_END, 0, 60.0f, cow);
        s.chapter("For robots");
        float aim = RobotScenes.yawTo(you, robot);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(EquipmentSlot.MAINHAND, blaster), you);
        s.idle(8);
        s.text(90, you.add(0, 2.1, 0), Side.LEFT,
                "The *Ion Blaster* is your weapon against robots and drones: right-click fires a thick blue beam, up to %s"
                        + " blocks. Walls and shield domes stop it.", HowToSceneBuilder.number(ImtConfig.Robots.ION_RANGE));
        s.idle(95);

        s.chapter("Down, not broken");
        s.beam(8, muzzle(you, aim), robot.add(0, 1.1, 0), RobotScenes.ION);
        s.sound(ModSounds.ION_BLASTER_FIRE.get(), 0.4f, 1.0f);
        s.idle(4);
        s.actor("robot", ModEntities.GUARD_ROBOT.get(), HowToSceneBuilder.UNTIL_END, 0, RobotScenes.yawTo(robot, you),
                RobotScenes.down(guard), robot);
        s.sound(ModSounds.ROBOT_POWER_OFF.get(), 0.6f, 1.0f);
        s.idle(30);
        float droneAim = RobotScenes.yawTo(you, drone);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, droneAim, Map.of(EquipmentSlot.MAINHAND, blaster), you);
        s.idle(6);
        s.beam(8, muzzle(you, droneAim), drone, RobotScenes.ION);
        s.sound(ModSounds.ION_BLASTER_FIRE.get(), 0.4f, 1.0f);
        s.idle(4);
        Vec3 ground = new Vec3(drone.x, 1.0, drone.z);
        s.actor("drone", ModEntities.GUARD_DRONE.get(), HowToSceneBuilder.UNTIL_END, 10, 30.0f, RobotScenes.down(flyer), drone, ground);
        s.text(110, robot.add(0, 1.2, 0), Side.RIGHT,
                "They fall down without breaking: no damage, and their program stops. After %s seconds they get up and"
                        + " start their program again (in a training: when the training is over).",
                HowToSceneBuilder.number(ImtConfig.Robots.ION_DOWN_SECONDS));
        s.idle(115);

        s.chapter("People and animals");
        float cowAim = RobotScenes.yawTo(you, cow);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, cowAim, Map.of(EquipmentSlot.MAINHAND, blaster), you);
        s.idle(6);
        s.beam(8, muzzle(you, cowAim), cow.add(0, 0.9, 0), RobotScenes.ION);
        s.sound(ModSounds.ION_BLASTER_FIRE.get(), 0.4f, 1.0f);
        s.idle(10);
        s.text(100, cow.add(0, 1.6, 0), Side.RIGHT,
                "On people and animals it does nothing. There are %s seconds between two shots, and it wears out after %s"
                        + " shots.", HowToSceneBuilder.number(ImtConfig.Robots.ION_COOLDOWN / 20.0),
                com.senne.imttech.robot.IonBlasterItem.USES);
        s.idle(105);
        return List.of(s.build());
    }

    // ================================================================ De Glock 19

    /**
     * De Glock 19: schieten (een echte kogel, de huls rechts eruit), hulzen oprapen, het magazijn kogel per kogel, herladen
     * (de HUD als plaatje), storingen en de tap-rack, en de skilltree als plaatje.
     */
    public static List<HowToScene> glock() {
        HowToSceneBuilder s = new HowToSceneBuilder("glock", "Glock 19").plate(7, 6).height(3);
        Vec3 you = new Vec3(2.0, 1.0, 4.8);
        Vec3 target = new Vec3(5.0, 1.0, 1.2);
        float aim = RobotScenes.yawTo(you, target);
        Vec3 gun = muzzle(you, aim);
        Map<EquipmentSlot, ItemStack> hand = Map.of(EquipmentSlot.MAINHAND, loadedGlock());
        s.idle(6);
        s.chapter("A real pistol");
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, hand, you);
        husk(s, "husk", 90, 70, target.add(1.2, 0, -0.9), target);
        s.text(140, target.add(0, 2.2, 0), Side.RIGHT,
                "The *Glock 19* is a real pistol: a click is one shot. The bullet really flies (%s blocks per tick), does its"
                        + " full damage up to %s blocks and half at %s, breaks glass, and a shield dome stops it.",
                HowToSceneBuilder.number(ImtConfig.Guns.BULLET_SPEED[0]), HowToSceneBuilder.number(ImtConfig.Guns.FULL_RANGE[0]),
                HowToSceneBuilder.number(ImtConfig.Guns.RANGE[0]));
        s.idle(40);
        Vec3 port = gun.add(ahead(aim).scale(-0.25)).add(0, 0.05, 0);
        // De hulzen blijven liggen tot je ze opraapt (in het volgende hoofdstukje, na zijn tekst en de klik).
        int pickup = s.now() + 3 * 10 + 70 + 45 + HowToSceneBuilder.CLICK / 2;
        for (int i = 0; i < 3; i++) {
            s.beam(2, gun, target.add(0, 1.3, 0), BULLET);
            s.sound(ModSounds.GLOCK_FIRE.get(), 0.4f, 1.0f);
            // De huls springt rechts uit het pistool en blijft liggen.
            Vec3 land = you.add(right(aim).scale(1.1 + i * 0.25)).add(ahead(aim).scale(0.3 - i * 0.2));
            s.actor("casing" + i, ModEntities.CASING.get(), pickup - s.now(), 10, aim + 90.0f,
                    port, port.add(right(aim).scale(0.6)).add(0, 0.5, 0), land);
            s.idle(10);
            s.sound(ModSounds.CASING_TINK.get(), 0.3f, 1.0f + i * 0.1f);
        }
        s.sound(SoundEvents.HUSK_DEATH, 0.4f, 1.0f);
        s.idle(70);

        s.chapter("Pick up your casings");
        Vec3 casings = you.add(right(aim).scale(1.35));
        s.text(90, casings.add(0, 0.4, 0), Side.LEFT,
                "The casings stay on the ground (%s minutes). *Right-click the ground* near them to pick them up, and press"
                        + " new rounds from them at the Reloading Bench.",
                HowToSceneBuilder.number(ImtConfig.Guns.CASING_SECONDS / 60.0));
        s.idle(45);
        s.rightClick(casings, ItemStack.EMPTY);
        s.idle(HowToSceneBuilder.CLICK / 2);
        s.sound(SoundEvents.ITEM_PICKUP, 0.4f, 1.2f);
        s.idle(50);

        s.chapter("The magazine");
        s.item(130, you.add(1.0, 1.8, -0.8), MagazineItem.of(FirearmType.GLOCK_19, 9), 1.2f);
        s.text(125, you.add(1.0, 2.0, -0.8), Side.RIGHT,
                "A *Glock 19 Magazine* holds %s rounds, and the pistol one more in the chamber (%s + 1). Hold the magazine"
                        + " and press *[R]*: every press pushes one round in; *[U]* takes one out. *Sneak + [R]* with the"
                        + " pistol puts one round straight into the chamber.",
                HowToSceneBuilder.number(ImtConfig.Guns.MAGAZINE_CAPACITY[0]), HowToSceneBuilder.number(ImtConfig.Guns.MAGAZINE_CAPACITY[0]));
        s.idle(130);

        s.chapter("Reloading");
        s.text(110, gun.add(0, 0.6, 0), Side.LEFT,
                "With a *Magazine Pouch*, *[R]* shows your magazines: pick one. Without a pouch, *[R]* ejects the magazine"
                        + " into your inventory, right-click pushes another one in, and the slide closes when you take the"
                        + " pistol again."
                        + " *[U]* unloads.");
        s.idle(115);
        s.picture(130, "glock_hud", 426, 240,
                s.arrow(0.9f, 0.87f, "Rounds in the magazine, *+1* in the chamber"),
                s.arrow(0.85f, 0.94f, "Magazines and loose rounds you carry"));
        s.idle(135);

        s.chapter("Jams");
        s.text(120, gun.add(0, 0.6, 0), Side.LEFT,
                "Sometimes a casing gets stuck (*JAMMED*) or a round doesn't fire (*MISFIRE*): rarely with new parts, more"
                        + " and more with worn springs, slide, firing pin or trigger. *[R]* then does a tap-rack.");
        s.idle(40);
        s.sound(ModSounds.GLOCK_DRY.get(), 0.5f, 1.0f);
        s.idle(30);
        s.sound(ModSounds.TAP_RACK.get(), 0.5f, 1.0f);
        s.idle(55);

        s.chapter("Skills");
        s.text(95, gun.add(0, 0.6, 0), Side.LEFT,
                "Everything you do with a gun gives experience (a shot, a hit, a reload...), and a full bar gives a *skill"
                        + " point*. Spend them in the skill tree: *[F6]*. What you learn stays after you die.");
        s.idle(100);
        s.picture(150, "gun_skills", 426, 240,
                s.arrow(0.12f, 0.12f, "Your skill points"),
                s.arrow(0.5f, 0.2f, "Pick a weapon: firearms on top, laser guns below"),
                s.arrow(0.07f, 0.3f, "*Global*: for every gun"),
                s.arrow(0.57f, 0.3f, "For this weapon only"),
                s.arrow(0.45f, 0.39f, "Spend a point (each level costs one more)"));
        s.idle(155);
        return List.of(s.build());
    }

    // ================================================================ De Gunsmith Table

    /**
     * De Gunsmith Table: zes onderdelen, drie materialen, de tafel (als plaatje), en slijtage. Sinds hoofdstuk 103 bouw
     * je er ook de MP5, de AK-47, de AWP en de Remington 870 op, en maak je er de attachments (die hebben hun eigen
     * uitleg: zie GunScenes).
     */
    public static List<HowToScene> gunsmith() {
        HowToSceneBuilder s = new HowToSceneBuilder("gunsmith", "Gunsmith Table").plate(5, 5).height(3);
        BlockPos table = new BlockPos(2, 1, 1);
        s.idle(6);
        s.chapter("Six parts");
        List<ItemStack> parts = new ArrayList<>();
        for (GunPart part : GunPart.values()) {
            parts.add(GunPartItem.of(part, GunMaterial.STEEL));
        }
        row(s, 140, new Vec3(2.5, 1.6, 2.5), 0.75f, 1.1f, parts);
        s.text(120, null, Side.RIGHT,
                "A Glock is six parts: a *frame* (with the grip), a *slide*, a *barrel*, the *springs*, a *firing pin* and a"
                        + " *trigger*.");
        s.idle(125);

        s.chapter("Three materials");
        row(s, 170, new Vec3(2.5, 1.4, 2.5), 1.1f, 1.3f, List.of(GunPartItem.of(GunPart.FRAME, GunMaterial.POLYMER),
                GunPartItem.of(GunPart.FRAME, GunMaterial.STEEL), GunPartItem.of(GunPart.FRAME, GunMaterial.TITANIUM)));
        s.text(80, null, Side.RIGHT,
                "Each part is *polymer* (only the frame and trigger), *steel* or *titanium*.");
        s.idle(85);
        s.text(80, null, Side.RIGHT,
                "The better the material, the smaller the chance per shot that it wears (%s%%, %s%%, %s%%), and titanium jams"
                        + " less.", HowToSceneBuilder.number(ImtConfig.Guns.WEAR_CHANCE[0]),
                HowToSceneBuilder.number(ImtConfig.Guns.WEAR_CHANCE[1]), HowToSceneBuilder.number(ImtConfig.Guns.WEAR_CHANCE[2]));
        s.idle(85);

        s.chapter("The Gunsmith Table");
        s.place(table, bench(ModBlocks.GUNSMITH_TABLE.get(), Direction.SOUTH));
        s.idle(10);
        s.rightClick(HowToSceneBuilder.face(table, Direction.SOUTH), ItemStack.EMPTY);
        s.idle(14);
        s.picture(150, "gunsmith_table", 368, 214,
                s.arrow(0.12f, 0.4f, "Your parts, guns and attachments"),
                s.arrow(0.5f, 0.55f, "Drag a part to its place: it lights up and clicks on"),
                s.arrow(0.42f, 0.3f, "What's missing is dotted"),
                s.arrow(0.85f, 0.17f, "Each part with its material and condition"),
                s.arrow(0.87f, 0.92f, "*Take the gun*"));
        s.idle(155);

        // Hoofdstuk 78: onderdelen maak je alleen nog hier (niet in de crafting table), op het tabblad "Make parts".
        s.chapter("Making parts");
        s.text(80, HowToSceneBuilder.top(table), Side.RIGHT,
                "Gun parts aren't in the crafting table: you make them here, on the tab *Make parts*. Laser parts, the"
                        + " *Gun Sling*, the *magazines* and the *attachments* too.");
        s.idle(85);
        s.picture(150, "gunsmith_make", 368, 214,
                s.arrow(0.13f, 0.35f, "Pick a weapon, or *Attachments*"),
                s.arrow(0.6f, 0.45f, "A row per part, a column per material"),
                s.arrow(0.83f, 0.26f, "What it costs: green is enough"),
                s.arrow(0.87f, 0.92f, "*Make*: it takes the materials from your inventory"));
        s.idle(155);

        s.chapter("Worn parts");
        s.item(110, new Vec3(2.5, 1.7, 2.7), GunPartItem.of(GunPart.SPRINGS, GunMaterial.STEEL, 25), 1.3f);
        s.text(110, new Vec3(2.5, 1.9, 2.7), Side.RIGHT,
                "Below %s%% a part is worn and jams more and more; at 0%% it's broken and the pistol does nothing. Then drag"
                        + " the old part off on the table (it comes back with its condition) and a new one on.",
                HowToSceneBuilder.number(ImtConfig.Guns.WORN_BELOW));
        s.idle(115);
        return List.of(s.build());
    }

    // ================================================================ De Reloading Bench

    /**
     * De Reloading Bench: een patroon uit een huls, een kogelpunt en Crushed Gunpowder, dat buskruit uit de Crusher
     * (hoofdstuk 76: 1 buskruit = 10), het scherm als plaatje, en je hulzen terug.
     */
    public static List<HowToScene> reloading() {
        HowToSceneBuilder s = new HowToSceneBuilder("reloading", "Reloading Bench").plate(5, 5).height(3);
        BlockPos bench = new BlockPos(2, 1, 1);
        s.idle(6);
        s.chapter("A round");
        row(s, 120, new Vec3(2.5, 1.6, 2.6), 0.9f, 1.3f, List.of(new ItemStack(ModItems.CASING_9MM.get()),
                new ItemStack(ModItems.BULLET_9MM.get()), new ItemStack(ModItems.CRUSHED_GUNPOWDER.get())));
        s.text(115, null, Side.RIGHT,
                "A *9mm Round* is a *9mm Casing* (copper: 8 per ingot), a *9mm Bullet* (2 iron nuggets: 4 bullets) and"
                        + " *Crushed Gunpowder*.");
        s.idle(60);
        s.item(60, new Vec3(2.5, 2.3, 2.6), new ItemStack(ModItems.ROUND_9MM.get()), 1.5f);
        s.idle(60);

        // Hoofdstuk 76: het buskruit gaat eerst door een Crusher (op stroom van de warmtegenerator links ernaast). Aan het
        // eind van dit hoofdstukje gaan ze weer weg, zodat de volgende hoofdstukjes over de bank er zijn zoals voorheen.
        s.chapter("Crushed Gunpowder");
        BlockPos generator = new BlockPos(0, 1, 1);
        BlockPos crusher = new BlockPos(1, 1, 1);
        s.place(generator, PowerScenes.heat(Direction.SOUTH));
        s.idle(4);
        s.place(crusher, crusher(false));
        s.idle(10);
        int machine = Math.round(ImtConfig.Repair.MACHINE_TICKS);
        int crushed = CrushedGunpowderItem.perGunpowder();
        Vec3 above = HowToSceneBuilder.top(crusher).add(0, 0.45, 0);
        s.item(machine / 2, above, new ItemStack(Items.GUNPOWDER), 0.5f);
        s.change(crusher, crusher(true));
        s.text(machine / 2 + 80, HowToSceneBuilder.top(crusher).add(0, 0.6, 0), Side.RIGHT,
                "Plain gunpowder goes through a *Crusher* first (on mains power, %s seconds): one gunpowder gives *%s Crushed"
                        + " Gunpowder*, so one gunpowder is %s rounds.",
                HowToSceneBuilder.number(machine / 20.0), crushed, crushed);
        s.idle(machine / 2);
        s.change(crusher, crusher(false));
        s.sound(SoundEvents.SAND_BREAK, 0.4f, 1.0f);
        // Wat eruit komt: een rijtje dat voor de blokken boven het platform zweeft, onder de tekst (hooguit 10, anders past
        // het niet in beeld). Boven het platform, want het donkere poeder zie je amper tegen de donkere achtergrond. Elk
        // verschijnt 3 ticks na het vorige en blijft 55 ticks, dus het laatste is weg voor de bank komt.
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < Math.min(crushed, 10); i++) {
            out.add(new ItemStack(ModItems.CRUSHED_GUNPOWDER.get()));
        }
        row(s, 55, new Vec3(2.2, 1.4, 4.0), 0.4f, 0.4f, out);
        s.idle(85 - 3 * out.size());
        s.clear(generator, crusher);

        s.chapter("The Reloading Bench");
        s.place(bench, bench(ModBlocks.RELOADING_BENCH.get(), Direction.SOUTH));
        s.idle(10);
        s.rightClick(HowToSceneBuilder.face(bench, Direction.SOUTH), ItemStack.EMPTY);
        s.idle(14);
        s.picture(140, "reloading_bench", 176, 166,
                s.arrow(0.32f, 0.26f, "Casing, bullet and crushed gunpowder"),
                s.arrow(0.83f, 0.26f, "Your round: one at a time, or shift-click for a stack"));
        s.idle(145);

        s.chapter("Your casings back");
        Vec3 you = new Vec3(3.4, 1.0, 3.4);
        float aim = 90.0f;
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(EquipmentSlot.MAINHAND, loadedGlock()), you);
        s.idle(10);
        Vec3 gun = muzzle(you, aim);
        Vec3 port = gun.add(ahead(aim).scale(-0.25));
        int pickup = s.now() + 2 * 12 + 30 + HowToSceneBuilder.CLICK / 2;
        for (int i = 0; i < 2; i++) {
            s.beam(2, gun, gun.add(ahead(aim).scale(4.0)), BULLET);
            s.sound(ModSounds.GLOCK_FIRE.get(), 0.4f, 1.0f);
            Vec3 land = you.add(right(aim).scale(1.0 + i * 0.3)).add(ahead(aim).scale(0.2 - i * 0.3));
            s.actor("casing" + i, ModEntities.CASING.get(), pickup - s.now(), 10, aim + 90.0f, port,
                    port.add(right(aim).scale(0.5)).add(0, 0.5, 0), land);
            s.idle(12);
        }
        s.text(90, you.add(right(aim).scale(1.2)).add(0, 0.5, 0), Side.LEFT,
                "Every shot drops a casing. Right-click the ground near them to pick them up, and press them again.");
        s.idle(30);
        s.rightClick(you.add(right(aim).scale(1.15)), ItemStack.EMPTY);
        s.idle(HowToSceneBuilder.CLICK / 2);
        s.sound(SoundEvents.ITEM_PICKUP, 0.4f, 1.2f);
        s.idle(60);
        return List.of(s.build());
    }

    // ================================================================ Holster, pouches en slings

    /**
     * De holster, de pouches en de slings: je inventaris (als plaatje), jij met alles om (je draait rond), het wiel (als
     * plaatje), het pistool trekken, en een pouch vullen (als plaatje).
     */
    public static List<HowToScene> gear() {
        HowToSceneBuilder s = new HowToSceneBuilder("gear", "Holster, pouches and slings").plate(3, 3).height(3);
        Vec3 you = new Vec3(1.5, 1.0, 1.5);
        ItemStack pistol = loadedGlock();
        ItemStack grenades = pouch(GearItem.Kind.GRENADE_POUCH, new ItemStack(ModItems.EMP_GRENADE.get(), 2),
                new ItemStack(ModItems.FRAG_GRENADE.get(), 2), new ItemStack(ModItems.FLASHBANG.get()));
        ItemStack magazines = pouch(GearItem.Kind.MAG_POUCH, MagazineItem.of(FirearmType.GLOCK_19, 15),
                MagazineItem.of(FirearmType.GLOCK_19, 15), EnergyCellItem.withCharge(100));
        List<ItemStack> full = List.of(holster(pistol), grenades, magazines, slung(GunType.RIFLE), ItemStack.EMPTY);
        List<ItemStack> drawn = List.of(holster(), grenades, magazines, slung(GunType.RIFLE), ItemStack.EMPTY);
        s.idle(6);
        s.chapter("Your gear");
        s.player("you", 120, 0, 40.0f, Map.of(), full, you);
        s.text(110, you.add(0, 2.1, 0), Side.RIGHT,
                "A *Holster* holds one pistol (the Glock or the Laser Pistol), a *Grenade Pouch* %s stacks of grenades, a"
                        + " *Magazine Pouch* %s magazines or energy cells.",
                HowToSceneBuilder.number(ImtConfig.Guns.GRENADE_POUCH_SIZE), HowToSceneBuilder.number(ImtConfig.Guns.MAG_POUCH_SIZE));
        s.idle(115);
        s.picture(140, "gear_inventory", 176, 166,
                s.arrow(0.48f, 0.2f, "Holster, Grenade Pouch, Magazine Pouch"),
                s.arrow(0.66f, 0.43f, "Two sling slots: a rifle, sniper or shotgun with a sling"),
                s.arrow(0.93f, 0.06f, "*?*: every How it works topic"));
        s.idle(145);

        s.chapter("On your body");
        float[] turns = {40.0f, 130.0f, 220.0f, 310.0f, 40.0f};
        s.text(150, you.add(0, 2.1, 0), Side.RIGHT,
                "Everyone sees what you wear: the holster with the pistol on your right hip, the grenade pouch on the left,"
                        + " the magazine pouch at the back, and the rifle slung over your back.");
        for (float yaw : turns) {
            s.player("you", 32, 0, yaw, Map.of(), full, you);
            s.idle(32);
        }

        s.chapter("The wheel");
        s.player("you", 220, 0, 40.0f, Map.of(), full, you);
        s.text(70, you.add(0, 2.1, 0), Side.RIGHT,
                "Hold *[left Alt]*: the wheel. Move the mouse to a tile and let go. You keep walking while it's open.");
        s.idle(75);
        s.picture(140, "weapon_wheel", 426, 240,
                s.arrow(0.5f, 0.19f, "Your pistol, from the holster"),
                s.arrow(0.65f, 0.34f, "Your guns on their slings"),
                s.arrow(0.33f, 0.5f, "Each kind of grenade"),
                s.arrow(0.5f, 0.81f, "*Put away*: back where it belongs"),
                s.arrow(0.82f, 0.45f, "Open a pouch"));
        s.idle(145);

        s.chapter("Drawing");
        s.player("you", Math.round(ImtConfig.Guns.DRAW_TICKS), 0, 40.0f, Map.of(), full, you);
        s.sound(ModSounds.HOLSTER_DRAW.get(), 0.5f, 1.0f);
        s.idle(Math.round(ImtConfig.Guns.DRAW_TICKS));
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 40.0f, Map.of(EquipmentSlot.MAINHAND, pistol), drawn, you);
        s.text(130, you.add(0, 2.1, 0), Side.RIGHT,
                "Drawing from the holster takes a moment (from a sling a bit longer). What you held goes to its own place"
                        + " if it can: a pistol into the holster, a rifle into a free sling slot, a grenade into the pouch.");
        s.idle(135);

        s.chapter("Filling a pouch");
        s.text(70, you.add(0, 2.1, 0), Side.RIGHT,
                "Open a pouch from the wheel, with its own key (Controls), or right-click with the pouch in your hand.");
        s.idle(75);
        s.picture(140, "mag_pouch", 176, 166,
                s.arrow(0.5f, 0.26f, "The pouch: drag or shift-click magazines in and out"),
                s.arrow(0.5f, 0.66f, "Your inventory"));
        s.idle(145);
        return List.of(s.build());
    }
}
