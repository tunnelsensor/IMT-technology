package com.senne.imttech.howto;

import com.senne.imttech.ImtTech;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.howto.scenes.DatacenterScenes;
import com.senne.imttech.howto.scenes.DefenceScenes;
import com.senne.imttech.howto.scenes.EquipmentScenes;
import com.senne.imttech.howto.scenes.GrenadeScenes;
import com.senne.imttech.howto.scenes.InteriorItemScenes;
import com.senne.imttech.howto.scenes.InteriorScenes;
import com.senne.imttech.howto.scenes.PowerScenes;
import com.senne.imttech.howto.scenes.RobotScenes;
import com.senne.imttech.howto.scenes.SecurityItemScenes;
import com.senne.imttech.howto.scenes.SecurityScenes;
import com.senne.imttech.howto.scenes.SuitScenes;
import com.senne.imttech.howto.scenes.TrainingScenes;
import com.senne.imttech.howto.scenes.WeaponScenes;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserPart;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

/**
 * Alle onderwerpen van "How it works" (hoofdstuk 63), in de volgorde van het overzicht, en welke items welk onderwerp
 * openen. Een onderwerp zonder animaties ({@code planned}) staat al in de lijst, zodat de test weet welke items nog aan de
 * beurt komen; in het spel zie je alleen onderwerpen die klaar zijn.
 *
 * Een nieuw item van de mod moet hier ergens in: bij een onderwerp, of in {@link #craftingOnly()} als het alleen in
 * recepten gaat. De test {@code every_item_has_a_how_it_works} controleert dat.
 */
public final class HowToTopics {
    private HowToTopics() {}

    /** Alle onderwerpen, per creative-tabblad in de volgorde van de tabbladen. */
    public static final List<HowToTopic> ALL = List.of(
            // ------------------------------------------------------------ Equipment
            topic("glasses", "equipment", "I.M.T glasses", EquipmentScenes::glasses, ModItems.IMT_GLASSES),
            topic("tech_suit", "equipment", "The tech suit", SuitScenes::techSuit, ModItems.TECH_CHESTPLATE, ModItems.TECH_LEGGINGS,
                    ModItems.TECH_BOOTS),
            topic("rocket_pack", "equipment", "Mini rockets", SuitScenes::rocketPack, ModItems.ROCKET_PACK, ModItems.ROCKET_WARHEAD,
                    ModItems.ROCKET_MAGAZINE),
            topic("drones", "equipment", "Drones", SuitScenes::drones, ModItems.DRONE),
            // Het item heette vroeger E.M.P.-zender; in het spel heet het nu Illusion Jammer.
            topic("illusion_jammer", "equipment", "Illusion Jammer", SuitScenes::illusionJammer, ModItems.EMP),
            // ------------------------------------------------------------ Weapons
            topic("laser_guns", "weapons", "Laser guns", WeaponScenes::laserGuns, ModItems.LASER_PISTOL, ModItems.LASER_RIFLE,
                    ModItems.LASER_SNIPER, ModItems.LASER_SHOTGUN, ModItems.ENERGY_CELL),
            topicItems("laser_workbench", "weapons", "Laser guns from parts", WeaponScenes::laserWorkbench,
                    () -> concat(laserParts(), List.of(ModItems.LASER_WORKBENCH.get(), ModItems.GUN_SLING.get()))),
            topic("ion_blaster", "weapons", "Ion Blaster", WeaponScenes::ionBlaster, ModItems.ION_BLASTER),
            topic("emp_grenades", "weapons", "EMP grenades", GrenadeScenes::emp, ModItems.EMP_GRENADE, ModItems.STICKY_EMP_GRENADE),
            topic("frag_grenades", "weapons", "Frag grenades", GrenadeScenes::frag, ModItems.FRAG_GRENADE, ModItems.STICKY_FRAG_GRENADE),
            topic("smoke_flashbang", "weapons", "Smoke grenade and flashbang", GrenadeScenes::smokeFlash, ModItems.SMOKE_GRENADE,
                    ModItems.FLASHBANG),
            topic("remote_charges", "weapons", "Remote EMP and Remote Frag", GrenadeScenes::remotes, ModItems.REMOTE_EMP,
                    ModItems.EMP_TRIGGER, ModItems.REMOTE_FRAG, ModItems.FRAG_TRIGGER),
            topic("glock", "weapons", "Glock 19", WeaponScenes::glock, ModItems.GLOCK_19, ModItems.GLOCK_19_MAGAZINE, ModItems.ROUND_9MM),
            topicItems("gunsmith", "weapons", "Gunsmith Table", WeaponScenes::gunsmith,
                    () -> concat(Arrays.stream(GunPart.values()).map(part -> (Item) ModItems.gunPart(part)).toList(),
                            List.of(ModItems.GUNSMITH_TABLE.get()))),
            topic("reloading", "weapons", "Reloading Bench", WeaponScenes::reloading, ModItems.RELOADING_BENCH, ModItems.CASING_9MM,
                    ModItems.BULLET_9MM, ModItems.ROUND_9MM),
            topic("gear", "weapons", "Holster, pouches and slings", WeaponScenes::gear, ModItems.HOLSTER, ModItems.GRENADE_POUCH,
                    ModItems.MAG_POUCH, ModItems.GUN_SLING),
            topic("armory", "weapons", "Armory: pegboard and ammo box", com.senne.imttech.howto.scenes.ArmoryScenes::armory,
                    ModItems.PEGBOARD, ModItems.AMMO_BOX),
            // ------------------------------------------------------------ Power
            topic("power_battery", "power", "Power Battery", PowerScenes::battery, ModItems.BATTERY_CASING, ModItems.BATTERY_GLASS,
                    ModItems.BATTERY_CELL, ModItems.BATTERY_PORT),
            topic("making_power", "power", "Making power", PowerScenes::makingPower, ModItems.SOLAR_PANEL, ModItems.HEAT_GENERATOR),
            topic("power_cables", "power", "Power cables", PowerScenes::cables, ModItems.CABLE),
            topic("network_cables", "power", "Network cables and PoE", PowerScenes::network, ModItems.NETWORK_CABLE, ModItems.POE_CABLE,
                    ModItems.NETWORK_SWITCH),
            topic("power_cabinet", "power", "The power cabinet", PowerScenes::cabinet, ModItems.POWER_CABINET),
            // ------------------------------------------------------------ Interior
            topic("holo_table", "interior", "Holo table", InteriorItemScenes::holoTable, ModItems.HOLO_TABLE),
            topic("suit_workbench", "interior", "Suit workbench and upgrades", InteriorItemScenes::suitWorkbench,
                    ModItems.SUIT_WORKBENCH, ModItems.THRUSTER_UPGRADE,
                    ModItems.ARMOR_PLATING, ModItems.LASER_CANNON_CAPACITOR, ModItems.ROCKET_WARHEAD, ModItems.ROCKET_MAGAZINE,
                    ModItems.BATTERY_UPGRADE, ModItems.SCANNER_UPGRADE, ModItems.DRONE_UPGRADE),
            topic("repairing_cracks", "interior", "Repairing cracks", InteriorItemScenes::repairingCracks, ModItems.CRUSHER,
                    ModItems.COMPACTOR, ModItems.REPAIR_PASTE, ModItems.CRACKED_BLOCK, ModItems.BLOCK_POWDER),
            topic("charging_station", "interior", "Charging station", InteriorScenes::chargingStation, ModItems.CHARGING_STATION, ModItems.ENERGY_CELL),
            topic("landing_pad", "interior", "Landing pad", InteriorItemScenes::landingPad, ModItems.LANDING_PAD),
            topic("arc_lamp", "interior", "Arc Lamp and light switch", InteriorItemScenes::arcLamp, ModItems.ARC_LAMP,
                    ModItems.LIGHT_SWITCH),
            topic("relays", "interior", "Redstone relays", InteriorScenes::relays, ModItems.RELAY, ModItems.RELAY_EMITTER),
            // ------------------------------------------------------------ Security
            topic("security_core", "security", "Security Core", SecurityScenes::core, ModItems.SECURITY_CORE),
            topic("computer", "security", "Computer and monitor", SecurityItemScenes::computer, ModItems.COMPUTER, ModItems.MONITOR),
            topic("zones", "security", "Protected zones", DefenceScenes::zones, ModItems.ZONE_MARKER),
            topic("block_reinforcer", "security", "Block Reinforcer", SecurityItemScenes::reinforcer, ModItems.BLOCK_REINFORCER,
                    ModItems.REINFORCEMENT_PLATE),
            topic("handcuffs", "security", "Handcuffs", SecurityItemScenes::handcuffs, ModItems.HANDCUFFS, ModItems.HANDCUFF_KEY),
            topic("keycards", "security", "Keycards and readers", SecurityScenes::keycards, ModItems.KEYCARD, ModItems.KEYCARD_READER),
            topic("code_panel", "security", "Code panel", SecurityScenes::codePanel, ModItems.CODE_PANEL),
            topic("auth_console", "security", "Auth codes and the Auth Console", SecurityItemScenes::auth, ModItems.AUTH_CONSOLE),
            topic("network_controls", "security", "Network button and lever", DefenceScenes::networkControls, ModItems.NETWORK_BUTTON, ModItems.NETWORK_LEVER),
            topic("laser_emitter", "security", "Laser gates", SecurityScenes::laserGate, ModItems.LASER_EMITTER),
            topic("laser_bridge", "security", "Laser bridge", DefenceScenes::laserBridge, ModItems.LASER_BRIDGE),
            topic("shield_dome", "security", "Shield dome", DefenceScenes::dome, ModItems.DOME_GENERATOR),
            topic("cameras", "security", "Cameras and the camera wall", DefenceScenes::cameras, ModItems.SECURITY_CAMERA, ModItems.CAMERA_MONITOR),
            topic("turrets", "security", "Turrets", DefenceScenes::turrets, ModItems.SECURITY_TURRET, ModItems.ROCKET_TURRET, ModItems.ROCKET_AMMO),
            topic("bypass_kit", "security", "Breaking in: the Bypass Kit", scenes(SecurityItemScenes::bypassReaders,
                    SecurityItemScenes::bypassWires, SecurityItemScenes::pickpocket), ModItems.BYPASS_KIT),
            topic("hacking_laptop", "security", "Hacking Laptop", SecurityItemScenes::laptop, ModItems.HACKING_LAPTOP),
            topic("phone", "security", "I.M.T Phone", SecurityItemScenes::phone, ModItems.PHONE),
            // ------------------------------------------------------------ Robots
            topicItems("guard_robot", "robots", "Guard robots", scenes(RobotScenes::build, RobotScenes::program),
                    () -> concat(robotParts(), List.of(ModItems.ROBOT_ASSEMBLER.get(), ModItems.ROBOT_DOCK.get(),
                            ModItems.GUARD_ROBOT.get(), ModItems.ROBOT_AMMO.get()))),
            topicItems("soldering", "robots", "Soldering modules", scenes(RobotScenes::soldering, RobotScenes::modules),
                    () -> concat(ModItems.ROBOT_MODULES.values().stream().map(module -> (Item) module.get()).toList(),
                            List.of(ModItems.ROBOT_CIRCUIT_BOARD.get(), ModItems.SOLDERING_IRON.get()))),
            topic("security_drones", "robots", "Security drones", RobotScenes::drones, ModItems.GUARD_DRONE, ModItems.DRONE_DOCK),
            topic("training", "robots", "Training", TrainingScenes::training, ModItems.TRAINING_START, ModItems.TRAINING_END,
                    ModItems.TRAINING_GOAL),
            // ------------------------------------------------------------ Datacenter
            topic("server_racks", "datacenter", "Server racks and Logic", DatacenterScenes::racks, ModItems.SERVER_RACK, ModItems.SERVER_CPU,
                    ModItems.SERVER_RAM, ModItems.SERVER_SSD, ModItems.SERVER_GPU),
            topic("datacenter_heat", "datacenter", "Heat and fans", DatacenterScenes::heat, ModItems.SERVER_FAN, ModItems.SERVER_RACK));

    /**
     * Items die alleen in recepten gaan (of alleen voor oude werelden bestaan): bewust geen animatie (mijn invulling bij
     * hoofdstuk 63). De spawn-eieren doen wel iets, maar dat is gewoon vanilla.
     */
    public static Set<Item> craftingOnly() {
        return Set.of(ModItems.COMPUTER_CHIP.get(), ModItems.RAVEN_CIRCUIT.get(), ModItems.STEEL_BLEND.get(),
                ModItems.STEEL_INGOT.get(), ModItems.TITANIUM_BLEND.get(), ModItems.TITANIUM_INGOT.get(), ModItems.POLYMER.get(),
                ModItems.GUARD_ROBOT_SPAWN_EGG.get(), ModItems.RAVEN_SOLDIER_SPAWN_EGG.get(), ModItems.RAVEN_TURRET.get());
    }

    private static @Nullable Map<Item, List<HowToTopic>> byItem;

    /** De onderwerpen van dit item, ook die nog niet klaar zijn. */
    public static List<HowToTopic> allFor(Item item) {
        if (byItem == null) {
            Map<Item, List<HowToTopic>> map = new HashMap<>();
            for (HowToTopic topic : ALL) {
                for (Item each : new HashSet<>(topic.items().get())) {
                    map.computeIfAbsent(each, key -> new ArrayList<>()).add(topic);
                }
            }
            byItem = map;
        }
        return byItem.getOrDefault(item, List.of());
    }

    /** De onderwerpen van dit item die klaar zijn (wat je met W op het item ziet). */
    public static List<HowToTopic> readyFor(Item item) {
        return allFor(item).stream().filter(HowToTopic::ready).toList();
    }

    /** Heeft dit item een animatie die klaar is? */
    public static boolean has(Item item) {
        return !readyFor(item).isEmpty();
    }

    /** Het onderwerp met dit id, of null. */
    public static @Nullable HowToTopic byId(String id) {
        for (HowToTopic topic : ALL) {
            if (topic.id().equals(id)) {
                return topic;
            }
        }
        return null;
    }

    // ================================================================ Lijstjes maken

    private static Component title(String id, String english) {
        return Component.translatableWithFallback("howto." + ImtTech.MODID + ".topic." + id, english);
    }

    @SafeVarargs
    private static HowToTopic topic(String id, String tab, String english, Supplier<List<HowToScene>> scenes,
                                    Supplier<? extends ItemLike>... items) {
        return new HowToTopic(id, tab, title(id, english), list(items), scenes);
    }

    @SafeVarargs
    private static HowToTopic planned(String id, String tab, String english, Supplier<? extends ItemLike>... items) {
        return new HowToTopic(id, tab, title(id, english), list(items), null);
    }

    private static HowToTopic plannedItems(String id, String tab, String english, Supplier<List<Item>> items) {
        return new HowToTopic(id, tab, title(id, english), items, null);
    }

    /** Een onderwerp met animaties, voor een lange lijst items (bv. alle robotonderdelen). */
    private static HowToTopic topicItems(String id, String tab, String english, Supplier<List<HowToScene>> scenes,
                                         Supplier<List<Item>> items) {
        return new HowToTopic(id, tab, title(id, english), items, scenes);
    }

    /** De animaties van een paar onderwerpen na elkaar (bouwen, dan programmeren). */
    @SafeVarargs
    private static Supplier<List<HowToScene>> scenes(Supplier<List<HowToScene>>... parts) {
        return () -> {
            List<HowToScene> out = new ArrayList<>();
            for (Supplier<List<HowToScene>> part : parts) {
                out.addAll(part.get());
            }
            return out;
        };
    }

    @SafeVarargs
    private static Supplier<List<Item>> list(Supplier<? extends ItemLike>... items) {
        return () -> Arrays.stream(items).map(item -> item.get().asItem()).toList();
    }

    private static List<Item> concat(List<Item> a, List<Item> b) {
        List<Item> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    /** Alle robotonderdelen: per stijl hoofd, lijf, armen en benen. */
    private static List<Item> robotParts() {
        return ModItems.ROBOT_PARTS.values().stream().flatMap(List::stream).map(part -> (Item) part.get()).toList();
    }

    /** Alle onderdelen van de lasergeweren: per geweer de zes onderdelen. */
    private static List<Item> laserParts() {
        List<Item> out = new ArrayList<>();
        for (GunType type : GunType.values()) {
            for (LaserPart part : LaserPart.wearing()) {
                out.add(ModItems.laserPart(type, part));
            }
        }
        return out;
    }
}
