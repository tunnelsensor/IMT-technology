package com.senne.imttech.howto.scenes;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.drone.DroneEntity;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * De laatste animaties van het tabblad Equipment (hoofdstuk 63, jouw vraag van 8 november: "werk de overschot ook nog
 * uit"): het harnas, de mini-raketten, de drones van je arsenaal en de Illusion Jammer. Hoe het echt werkt staat in LEESMIJ
 * ("Het harnas", "Het drone-arsenaal", "Drones in het veld", "Illusies").
 */
public final class SuitScenes {
    private SuitScenes() {}

    /** Het vuur achter een raketje. */
    private static final int TRAIL = 0xFFC26B;
    /** De lasers van je drones (rood) en de vonken van een gestoorde cloakingdrone (wit-blauw). */
    private static final int DRONE_RED = 0xFF4545;
    private static final int SPARK = 0xCFF0FF;

    /** De bril en het hele harnas; met {@code rocketPack} zit het raketpakket op de borstplaat (twee buizen op je rug). */
    static Map<EquipmentSlot, ItemStack> suit(boolean rocketPack) {
        ItemStack chest = new ItemStack(ModItems.TECH_CHESTPLATE.get());
        if (rocketPack) {
            chest.set(ModDataComponents.ROCKET_PACK.get(), true);
        }
        return Map.of(EquipmentSlot.HEAD, new ItemStack(ModItems.IMT_GLASSES.get()), EquipmentSlot.CHEST, chest,
                EquipmentSlot.LEGS, new ItemStack(ModItems.TECH_LEGGINGS.get()), EquipmentSlot.FEET,
                new ItemStack(ModItems.TECH_BOOTS.get()));
    }

    /** Hetzelfde, met iets in je hand. */
    static Map<EquipmentSlot, ItemStack> suit(boolean rocketPack, ItemStack hand) {
        Map<EquipmentSlot, ItemStack> gear = new java.util.HashMap<>(suit(rocketPack));
        gear.put(EquipmentSlot.MAINHAND, hand);
        return gear;
    }

    /** Gegevens voor een speler die vliegt: de stuwraketten branden met deze kracht (0 tot 1), zie SceneLife. */
    static CompoundTag flying(float power) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Thrust", power);
        return tag;
    }

    /** Gegevens voor een drone van je arsenaal in deze stand (de lampjes: rood = aanval, blauw = bewaken, wit = de rest). */
    static CompoundTag drone(DroneEntity.Mode mode) {
        CompoundTag tag = new CompoundTag();
        tag.putByte("DroneMode", (byte) mode.ordinal());
        return tag;
    }

    /** Een gebogen lijn van {@code a} naar {@code d} met trekpunten {@code b} en {@code c} (een bezierkromme in stukjes). */
    static Vec3[] curve(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int pieces) {
        Vec3[] points = new Vec3[pieces + 1];
        for (int i = 0; i <= pieces; i++) {
            double t = i / (double) pieces;
            double u = 1 - t;
            points[i] = a.scale(u * u * u).add(b.scale(3 * u * u * t)).add(c.scale(3 * u * t * t)).add(d.scale(t * t * t));
        }
        return points;
    }

    // ================================================================ Het harnas

    /**
     * Het harnas: borstplaat, broek en laarzen (geen helm, daar zit de bril), minder schade, vliegen met de stuwraketten
     * (blauwe vlammen onder je laarzen), landen, en de laser cannon.
     */
    public static List<HowToScene> techSuit() {
        HowToSceneBuilder s = new HowToSceneBuilder("tech_suit", "The tech suit").plate(6, 6).height(5);
        Vec3 you = new Vec3(2.0, 1.0, 3.6);
        ItemStack glasses = new ItemStack(ModItems.IMT_GLASSES.get());
        s.idle(6);
        s.chapter("Chestplate, leggings, boots");
        s.player("you", 20, 0, 40.0f, Map.of(EquipmentSlot.HEAD, glasses), you);
        s.idle(20);
        s.player("you", 14, 0, 40.0f, Map.of(EquipmentSlot.HEAD, glasses, EquipmentSlot.CHEST,
                new ItemStack(ModItems.TECH_CHESTPLATE.get())), you);
        s.sound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.5f, 1.0f);
        s.idle(14);
        s.player("you", 14, 0, 40.0f, Map.of(EquipmentSlot.HEAD, glasses, EquipmentSlot.CHEST,
                new ItemStack(ModItems.TECH_CHESTPLATE.get()), EquipmentSlot.LEGS, new ItemStack(ModItems.TECH_LEGGINGS.get())), you);
        s.sound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.5f, 1.1f);
        s.idle(14);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 40.0f, suit(false), you);
        s.sound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.5f, 1.2f);
        s.text(110, you.add(0, 2.0, 0), Side.RIGHT,
                "The suit: a *chestplate*, *leggings* and *boots*, made at the *Suit Workbench*. No helmet: that's where your"
                        + " glasses go. Without the glasses it's just a good suit of metal.");
        s.idle(115);

        s.chapter("Less damage");
        int walk = 40;
        Vec3 near = you.add(1.0, 0, -1.0);
        WeaponScenes.husk(s, "husk", walk + 70, walk, new Vec3(5.3, 1.0, 0.5), near);
        s.idle(walk);
        for (int i = 0; i < 3; i++) {
            s.flash(6, you, 0.8, 2.0, HowToSceneBuilder.CYAN);
            s.sound(SoundEvents.PLAYER_HURT, 0.3f, 1.0f);
            s.idle(18);
        }
        s.text(100, you.add(0, 2.0, 0), Side.RIGHT,
                "With your glasses on (and energy in them) every piece takes %s%% off each hit: %s%% with the whole suit, on top"
                        + " of normal armor. Your glasses pay for it, hit by hit.",
                HowToSceneBuilder.number(ImtConfig.Suit.POWERED_REDUCTION_PER_PIECE * 100),
                HowToSceneBuilder.number(ImtConfig.Suit.POWERED_REDUCTION_PER_PIECE * 300));
        s.idle(105);

        s.chapter("Flying");
        Vec3[] up = {you, you.add(0, 0.6, 0), you.add(0, 2.2, 0)};
        int rise = 30;
        s.person("you", flying(0.6f), rise, rise, 40.0f, suit(false), List.of(), up);
        s.sound(ModSounds.SUIT_THRUSTER.get(), 0.3f, 1.0f);
        s.idle(rise);
        Vec3 high = you.add(0, 2.2, 0);
        Vec3[] loop = {high, new Vec3(3.4, 3.4, 2.2), new Vec3(4.4, 3.6, 3.4), new Vec3(3.4, 3.4, 4.6), high};
        int round = 90;
        s.person("you", flying(1.0f), round, round, 40.0f, suit(false), List.of(), loop);
        s.text(round + 20, high.add(0, 2.0, 0), Side.LEFT,
                "With all three pieces you have thrusters: double-tap [Space]. W/S thrust where you look, A/D sideways,"
                        + " Space/Shift up and down, sprint is the afterburner. You have momentum, and flying costs energy: empty"
                        + " glasses, and you fall.");
        s.idle(round);
        s.person("you", flying(0.4f), 30, 0, 40.0f, suit(false), List.of(), high);
        s.idle(30);

        s.chapter("Landing");
        Vec3[] down = {high, you.add(0, 0.7, 0), you};
        int fall = 30;
        s.person("you", flying(0.5f), fall, fall, 40.0f, suit(false), List.of(), down);
        s.idle(fall);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 40.0f, suit(false), you);
        s.text(90, you.add(0, 2.0, 0), Side.RIGHT,
                "Close to the ground the suit brakes by itself, and the boots catch any fall up to %s blocks.",
                HowToSceneBuilder.number(ImtConfig.Suit.SAFE_FALL));
        s.idle(95);

        s.chapter("Laser cannon");
        Vec3 target = new Vec3(5.0, 1.0, 1.0);
        float aim = RobotScenes.yawTo(you, target);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, suit(false), you);
        WeaponScenes.husk(s, "husk2", 10, 0, target);
        s.idle(10);
        s.text(110, you.add(0, 2.0, 0), Side.LEFT,
                "[I] turns the *laser cannon* on (it needs the chestplate): your attack button fires, held down once a second."
                        + " It hits everything in its path, up to %s blocks, and pushes it away.",
                HowToSceneBuilder.number(ImtConfig.Suit.LASER_CANNON_RANGE));
        Vec3 palm = WeaponScenes.muzzle(you, aim);
        Vec3 pushed = target.add(WeaponScenes.ahead(aim).scale(0.6));
        for (int i = 0; i < 3; i++) {
            Vec3 at = i == 0 ? target : pushed;
            s.beam(5, palm, at.add(0, 1.2, 0), 0x6FE8FF);
            s.sound(ModSounds.LASER_CANNON_FIRE.get(), 0.35f, 1.0f);
            s.flash(5, at, 0.8, 2.0, HowToSceneBuilder.CYAN);
            if (i == 0) {
                WeaponScenes.husk(s, "husk2", 50, 6, target, pushed);
            }
            s.idle(20);
        }
        s.idle(60);
        return List.of(s.build());
    }

    // ================================================================ Mini-raketten

    /**
     * Een salvo: elk raketje (een echt wezen) vliegt langs zijn kromme, met een streepje vuur erachter, en ontploft aan het
     * eind. Het {@code i}-de raketje vertrekt {@code i * interval} ticks na het eerste. De klok staat daarna na de laatste
     * ontploffing.
     */
    private static void salvo(HowToSceneBuilder s, List<Vec3[]> paths, int interval, int flight) {
        record Event(int at, Runnable add) {}
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            Vec3[] path = paths.get(i);
            int start = i * interval;
            String key = "rocket" + i;
            events.add(new Event(start, () -> {
                s.actor(key, ModEntities.MINI_ROCKET.get(), flight, flight, 0.0f, path);
                s.sound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.3f, 1.4f);
            }));
            int pieces = path.length - 1;
            for (int k = 0; k < pieces; k++) {
                Vec3 a = path[k];
                Vec3 b = path[k + 1];
                events.add(new Event(start + flight * (k + 1) / pieces, () -> s.beam(7, a, b, TRAIL)));
            }
            Vec3 end = path[pieces];
            events.add(new Event(start + flight, () -> {
                s.pulse(10, end, 0.9f, GrenadeScenes.BLAST);
                s.sound(SoundEvents.GENERIC_EXPLODE.value(), 0.25f, 1.5f);
            }));
        }
        events.sort(Comparator.comparingInt(Event::at));
        int now = 0;
        for (Event event : events) {
            s.idle(event.at() - now);
            now = event.at();
            event.add().run();
        }
    }

    /**
     * De mini-raketten: het raketpakket op de borstplaat (twee buizen op je rug), een salvo van zes raketjes die om de beurt
     * links en rechts vertrekken, uitwaaieren en naar hun doel draaien, herladen, en het magazijn en de springkop.
     */
    public static List<HowToScene> rocketPack() {
        HowToSceneBuilder s = new HowToSceneBuilder("rocket_pack", "Mini rockets").plate(7, 7).height(4);
        Vec3 you = new Vec3(1.8, 1.0, 5.0);
        float yaw = 215.0f;
        ItemStack pack = new ItemStack(ModItems.ROCKET_PACK.get());
        s.idle(6);
        s.chapter("The rocket pack");
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, yaw, suit(false, pack), you);
        s.idle(10);
        s.rightClick(you.add(0, 1.3, 0), pack);
        s.idle(12);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, yaw, suit(true), you);
        s.sound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.6f, 0.9f);
        s.text(110, you.add(0, 2.0, 0), Side.RIGHT,
                "Craft a *Rocket Pack* (a Raven part in the middle, a computer chip, gunpowder and iron). Wear the chestplate and"
                        + " right-click with the pack: it sits on that chestplate, with two tubes on your back.");
        s.idle(115);

        s.chapter("A salvo");
        Vec3[] targets = {new Vec3(5.6, 1.0, 1.4), new Vec3(4.2, 1.0, 0.8), new Vec3(6.2, 1.0, 3.0)};
        int interval = Math.round(ImtConfig.Suit.ROCKET_INTERVAL) * 3;
        int flight = 26;
        int salvo = Math.round(ImtConfig.Suit.ROCKET_SALVO);
        for (int i = 0; i < targets.length; i++) {
            // Zijn laatste raket: de hoogste i met i % 3 == deze husk.
            int last = i;
            while (last + targets.length < salvo) {
                last += targets.length;
            }
            WeaponScenes.husk(s, "husk" + i, 50 + last * interval + flight, 0, targets[i]);
        }
        s.idle(20);
        s.text(80, you.add(0, 2.0, 0), Side.LEFT,
                "[X] (or G > Combat, or say \"fire rockets\"): a salvo of %s rockets from both tubes.", salvo);
        s.idle(30);
        Vec3 ahead = WeaponScenes.ahead(yaw);
        Vec3 right = WeaponScenes.right(yaw);
        List<Vec3[]> paths = new ArrayList<>();
        for (int i = 0; i < salvo; i++) {
            double side = i % 2 == 0 ? -1 : 1;
            Vec3 tube = you.add(0, 1.55, 0).add(ahead.scale(-0.25)).add(right.scale(0.2 * side));
            Vec3 target = targets[i % targets.length].add(0, 1.2, 0);
            Vec3 fan = tube.add(0, 1.6, 0).add(right.scale(0.9 * side * (1 + i / 2 * 0.4))).add(ahead.scale(0.4));
            Vec3 dive = target.add(0, 1.8, 0).add(ahead.scale(-0.8));
            paths.add(curve(tube, fan, dive, target, 8));
        }
        salvo(s, paths, interval, flight);
        s.idle(10);

        // Een eigen hoofdstukje voor de uitleg: zo valt de foto van het salvo-hoofdstukje midden in het salvo.
        s.chapter("Where they fly");
        s.text(110, you.add(0, 2.0, 0), Side.LEFT,
                "Left and right in turn, they fan out and turn to your marked targets ([R]), else to what you look at, else to"
                        + " where you look. They never break blocks, and never hit you, your team or your drones.");
        s.idle(115);

        s.chapter("Reloading");
        s.text(100, you.add(0, 2.0, 0), Side.RIGHT,
                "You need the whole suit. After a salvo the pack reloads for %s seconds (*ROCKETS* on the left of your HUD)."
                        + " It needs no ammo.", HowToSceneBuilder.number(ImtConfig.Suit.ROCKET_COOLDOWN / 20.0));
        s.idle(105);

        s.chapter("More and stronger");
        WeaponScenes.row(s, 120, new Vec3(3.4, 1.6, 3.2), 0.9f, 0.6f, List.of(new ItemStack(ModItems.ROCKET_MAGAZINE.get()),
                new ItemStack(ModItems.ROCKET_WARHEAD.get())));
        int magazine = Math.round(ImtConfig.Workbench.MAGAZINE_ROCKETS);
        int most = salvo + magazine * Math.round(ImtConfig.Workbench.MAX_MAGAZINES);
        s.text(115, you.add(0, 2.0, 0), Side.RIGHT,
                "At the Suit Workbench: a *Rocket Magazine* adds %s rockets to a salvo (up to %s), a *Rocket Warhead* %s%% damage"
                        + " per rocket. Up to %s of each.", magazine, most,
                HowToSceneBuilder.number(ImtConfig.Workbench.WARHEAD_DAMAGE * 100),
                HowToSceneBuilder.number(ImtConfig.Workbench.MAX_WARHEADS));
        s.idle(120);
        return List.of(s.build());
    }

    // ================================================================ Drones

    /**
     * Drones: er een bouwen (hij schiet de lucht in naar zijn baan), het arsenaal (als plaatje), terugroepen en repareren
     * met ijzer, een zwerm die van hoog komt aanduiken, en de escorte die om je heen vliegt.
     */
    public static List<HowToScene> drones() {
        HowToSceneBuilder s = new HowToSceneBuilder("drones", "Drones").plate(7, 7).height(6);
        Vec3 you = new Vec3(2.0, 1.0, 4.6);
        ItemStack drone = new ItemStack(ModItems.DRONE.get());
        s.idle(6);
        s.chapter("Build a drone");
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 40.0f, Map.of(EquipmentSlot.HEAD,
                new ItemStack(ModItems.IMT_GLASSES.get()), EquipmentSlot.MAINHAND, drone), you);
        s.idle(10);
        s.rightClick(you.add(0.6, 1.4, -0.2), drone);
        s.idle(10);
        Vec3 hand = WeaponScenes.muzzle(you, 40.0f).add(0, 0.3, 0);
        int launch = 36;
        s.actor("drone", ModEntities.DRONE.get(), launch, launch, 0.0f, drone(DroneEntity.Mode.LAUNCH), hand,
                hand.add(0, 1.5, 0), hand.add(0.4, 7.5, -0.4));
        s.sound(ModSounds.DRONE_DEPLOY.get(), 0.4f, 1.0f);
        s.text(110, you.add(0, 2.0, 0), Side.RIGHT,
                "Craft a *Drone* (a computer chip, 5 iron, 2 redstone and an amethyst) and use it: it shoots up into an orbit"
                        + " round the planet. There it waits until you need it, wherever you are.");
        s.idle(115);

        s.chapter("Your arsenal");
        s.text(80, you.add(0, 2.0, 0), Side.RIGHT,
                "The swarm, the escort, the scout, the spy, transport and illusions all take their drones from that *arsenal*:"
                        + " no drones left, and nothing goes.");
        s.idle(85);
        s.picture(150, "arsenal_screen", 426, 240,
                s.arrow(0.755f, 0.14f, "*In orbit*: ready to use"),
                s.arrow(0.66f, 0.207f, "How whole it still is"),
                s.arrow(0.762f, 0.274f, "*On a job*: busy until it's back"),
                s.arrow(0.767f, 0.34f, "*Grounded*: waiting next to you"),
                s.arrow(0.918f, 0.14f, "*Recall*: it lands in front of you"));
        s.idle(155);

        s.chapter("Recall and repair");
        Vec3 landed = new Vec3(3.4, 1.0, 3.4);
        int dive = 40;
        s.actor("drone", ModEntities.DRONE.get(), dive, dive, 0.0f, drone(DroneEntity.Mode.LAUNCH),
                curve(new Vec3(6.5, 7.5, 0.5), new Vec3(5.5, 4.5, 1.5), new Vec3(3.6, 2.4, 3.0), landed, 8));
        s.sound(ModSounds.DRONE_RECALL.get(), 0.4f, 1.0f);
        s.idle(dive);
        s.actor("drone", ModEntities.DRONE.get(), 150, 0, 0.0f, drone(DroneEntity.Mode.GROUNDED), landed);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, RobotScenes.yawTo(you, landed), Map.of(EquipmentSlot.HEAD,
                new ItemStack(ModItems.IMT_GLASSES.get()), EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT)), you);
        s.idle(10);
        s.rightClick(landed.add(0, 0.3, 0), new ItemStack(Items.IRON_INGOT));
        s.idle(10);
        s.flash(8, landed, 0.8, 0.5, HowToSceneBuilder.GREEN);
        s.sound(SoundEvents.ANVIL_USE, 0.25f, 1.6f);
        s.text(120, landed.add(0, 1.0, 0), Side.RIGHT,
                "*Recall* brings one down in an arc. Right-click it with iron to repair it (an ingot = %s health): it never heals"
                        + " by itself. Shift + right-click (or *Launch*) sends it straight back up.",
                HowToSceneBuilder.number(ImtConfig.Drones.REPAIR_PER_INGOT));
        s.idle(110);
        s.sneakRightClick(landed.add(0, 0.3, 0), ItemStack.EMPTY);
        s.idle(10);
        s.actor("drone", ModEntities.DRONE.get(), 30, 30, 0.0f, drone(DroneEntity.Mode.LAUNCH), landed,
                landed.add(0, 7.0, 0));
        s.sound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.4f, 0.8f);
        s.idle(35);

        s.chapter("A swarm");
        Vec3 husk = new Vec3(5.2, 1.0, 1.6);
        int in = 34;
        // Tot het laatste schot: 12 ticks, vier drones om de 4 ticks, de duik, en zes schoten om de 8 ticks.
        WeaponScenes.husk(s, "husk", 12 + 16 + (in - 12) + 6 * 8, 0, husk);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, RobotScenes.yawTo(you, husk), Map.of(EquipmentSlot.HEAD,
                new ItemStack(ModItems.IMT_GLASSES.get())), you);
        s.idle(12);
        Vec3[] spots = {husk.add(-1.4, 1.6, 0.4), husk.add(0.2, 1.9, 1.4), husk.add(1.1, 1.7, -0.3), husk.add(-0.4, 2.2, -1.0)};
        for (int i = 0; i < spots.length; i++) {
            Vec3 from = new Vec3(i * 2.0 - 1.0, 9.0, -1.5 + i);
            s.actor("swarm" + i, ModEntities.DRONE.get(), in + 70, in, 0.0f, drone(DroneEntity.Mode.ATTACK),
                    curve(from, from.add(0, -4.0, 1.0), spots[i].add(0, 1.5, 0.5), spots[i], 8));
            s.idle(4);
        }
        s.idle(in - 12);
        s.text(110, husk.add(0, 2.4, 0), Side.LEFT,
                "They dive from high up (%s blocks above you) and fly back up when they're done. [J] sends a swarm at your"
                        + " marked targets, [L] one drone after one target.", HowToSceneBuilder.number(ImtConfig.Drones.ORBIT_HEIGHT));
        for (int shot = 0; shot < 6; shot++) {
            Vec3 from = spots[shot % spots.length];
            s.beam(4, from, husk.add(0, 1.2, 0), DRONE_RED);
            s.sound(ModSounds.DRONE_LASER.get(), 0.25f, 1.2f);
            s.idle(8);
        }
        for (int i = 0; i < spots.length; i++) {
            s.actor("swarm" + i, ModEntities.DRONE.get(), 30, 30, 0.0f, drone(DroneEntity.Mode.ATTACK), spots[i],
                    spots[i].add(0, 7.0, 0));
        }
        s.idle(40);

        s.chapter("The escort");
        Vec3 centre = you.add(0, 1.8, 0);
        int around = 80;
        for (int i = 0; i < 4; i++) {
            Vec3[] ring = new Vec3[13];
            for (int k = 0; k <= 12; k++) {
                double angle = Math.toRadians(i * 90 + k * 30);
                ring[k] = centre.add(Math.cos(angle) * 1.6, Math.sin(angle * 2) * 0.15, Math.sin(angle) * 1.6);
            }
            s.actor("escort" + i, ModEntities.DRONE.get(), around * 2, around, 0.0f, drone(DroneEntity.Mode.GUARD), ring);
        }
        s.text(120, centre.add(0, 1.0, 0), Side.RIGHT,
                "The *escort* flies with you and shoots whatever attacks you; G > Drones > Formation: Orbit, Shield, Arrow or"
                        + " Circle. A drone that's shot down is gone (about %s%% comes back as scrap).",
                HowToSceneBuilder.number(ImtConfig.Drones.WRECK_RECOVERY * 100));
        s.idle(around);
        for (int i = 0; i < 4; i++) {
            Vec3[] ring = new Vec3[13];
            for (int k = 0; k <= 12; k++) {
                double angle = Math.toRadians(i * 90 + k * 30);
                ring[k] = centre.add(Math.cos(angle) * 1.6, Math.sin(angle * 2) * 0.15, Math.sin(angle) * 1.6);
            }
            s.actor("escort" + i, ModEntities.DRONE.get(), around, around, 0.0f, drone(DroneEntity.Mode.GUARD), ring);
        }
        s.idle(around);
        return List.of(s.build());
    }

    // ================================================================ De Illusion Jammer

    /**
     * De Illusion Jammer: een onzichtbare cloakingdrone zet een valse muur neer (je ziet alleen zijn vonkjes), de jammer
     * legt alle illusies in de buurt plat (de muur flikkert en is weg, de drone geeft vonken), en de zwakke plekken.
     */
    public static List<HowToScene> illusionJammer() {
        HowToSceneBuilder s = new HowToSceneBuilder("illusion_jammer", "Illusion Jammer").plate(7, 5).height(4);
        Vec3 you = new Vec3(1.4, 1.0, 3.8);
        Vec3 droneAt = new Vec3(5.0, 3.6, 2.0);
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        Map<BlockPos, BlockState> wall = HowToSceneBuilder.box(new BlockPos(3, 1, 1), new BlockPos(5, 2, 1), pos -> bricks);
        BlockPos[] wallSpots = wall.keySet().toArray(new BlockPos[0]);
        ItemStack jammer = new ItemStack(ModItems.EMP.get());
        float aim = RobotScenes.yawTo(you, new Vec3(4.5, 1.0, 1.5));
        s.idle(6);
        s.chapter("Illusions");
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(EquipmentSlot.HEAD,
                new ItemStack(ModItems.IMT_GLASSES.get())), you);
        s.idle(10);
        s.place(wall, HowToSceneBuilder.DROP);
        s.idle(10);
        s.text(110, HowToSceneBuilder.top(new BlockPos(5, 2, 1)), Side.RIGHT,
                "Someone's invisible *cloaking drone* projects an illusion in a sphere of %s blocks: a fake wall, a fake army or"
                        + " an invisible field. In the world nothing changes. It gives itself away with sparks and a soft buzz.",
                HowToSceneBuilder.number(ImtConfig.Illusions.RADIUS));
        for (int i = 0; i < 7; i++) {
            s.pulse(6, droneAt.add((i % 3 - 1) * 0.15, (i % 2) * 0.1, 0), 0.12f, SPARK);
            s.idle(17);
        }

        s.chapter("Jam it");
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, aim, Map.of(EquipmentSlot.HEAD,
                new ItemStack(ModItems.IMT_GLASSES.get()), EquipmentSlot.MAINHAND, jammer), you);
        s.idle(10);
        s.rightClick(you.add(0.6, 1.4, -0.2), jammer);
        s.idle(10);
        s.pulse(16, you.add(0, 1.0, 0), 4.2f, 0x6FD8FF);
        s.sound(SoundEvents.BEACON_DEACTIVATE, 0.6f, 0.5f);
        for (int i = 0; i < 6; i++) {
            s.pulse(5, droneAt.add((i % 3 - 1) * 0.3, (i % 2) * 0.2 - 0.1, (i % 2) * 0.2), 0.18f, SPARK);
        }
        s.idle(4);
        for (int i = 0; i < 2; i++) {
            s.clear(wallSpots);
            s.idle(3);
            s.place(wall, 0);
            s.idle(4);
        }
        s.clear(wallSpots);
        s.text(120, you.add(0, 2.0, 0), Side.RIGHT,
                "Right-click the *Illusion Jammer*: every illusion within %s blocks goes down for %s seconds, and their drones"
                        + " take a hit (the sparks show where they hang). Then it needs %s seconds before the next one.",
                HowToSceneBuilder.number(ImtConfig.Illusions.EMP_RADIUS),
                HowToSceneBuilder.number(ImtConfig.Illusions.EMP_JAM_TICKS / 20.0),
                HowToSceneBuilder.number(ImtConfig.Illusions.EMP_COOLDOWN / 20.0));
        s.idle(125);

        s.chapter("Weak spots");
        s.text(110, you.add(0, 2.0, 0), Side.RIGHT,
                "More weak spots: where the fields of two different players overlap, the image flickers. And a cloaking drone"
                        + " can be shot down: then its illusion is gone for good.");
        s.idle(115);
        return List.of(s.build());
    }
}
