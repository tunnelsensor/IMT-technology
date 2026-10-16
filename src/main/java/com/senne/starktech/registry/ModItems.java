package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.edith.EdithGlassesItem;
import com.senne.starktech.edith.UpgradeItem;
import com.senne.starktech.arsenal.StarkDroneItem;
import com.senne.starktech.robot.RobotPartItem;
import com.senne.starktech.robot.RobotSlot;
import com.senne.starktech.robot.RobotStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.senne.starktech.suit.RocketPackItem;
import com.senne.starktech.suit.StarkSuitItem;
import net.minecraft.world.item.ArmorItem;
import com.senne.starktech.illusion.EmpItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Alle items van de mod.
 */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StarkTech.MODID);

    /**
     * De bril. Hij zit in het helm-slot (ArmorItem.Type.HELMET) en gaat nooit kapot
     * (geen duurzaamheid ingesteld).
     */
    public static final DeferredItem<EdithGlassesItem> EDITH_GLASSES = ITEMS.registerItem("edith_glasses",
            properties -> new EdithGlassesItem(properties),
            new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .fireResistant());

    /** Onderdeel om de bril en het laadstation te craften. */
    public static final DeferredItem<Item> STARK_CHIP = ITEMS.registerSimpleItem("stark_chip",
            new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<UpgradeItem> BATTERY_UPGRADE = ITEMS.registerItem("battery_upgrade",
            properties -> new UpgradeItem(UpgradeItem.Type.BATTERY, properties),
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

    public static final DeferredItem<UpgradeItem> SCANNER_UPGRADE = ITEMS.registerItem("scanner_upgrade",
            properties -> new UpgradeItem(UpgradeItem.Type.SCANNER, properties),
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

    public static final DeferredItem<UpgradeItem> DRONE_UPGRADE = ITEMS.registerItem("drone_upgrade",
            properties -> new UpgradeItem(UpgradeItem.Type.DRONE, properties),
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

    public static final DeferredItem<BlockItem> CHARGING_STATION = ITEMS.registerSimpleBlockItem("charging_station",
            ModBlocks.CHARGING_STATION);

    /** Het zonnepaneel: laadt je bril op met daglicht. */
    public static final DeferredItem<BlockItem> SOLAR_PANEL = ITEMS.registerSimpleBlockItem("solar_panel",
            ModBlocks.SOLAR_PANEL);

    /** De warmtegenerator: laadt op brandstof en op de hitte om hem heen. */
    public static final DeferredItem<BlockItem> HEAT_GENERATOR = ITEMS.registerSimpleBlockItem("heat_generator",
            ModBlocks.HEAT_GENERATOR);

    public static final DeferredItem<BlockItem> RELAY = ITEMS.registerSimpleBlockItem("relay", ModBlocks.RELAY);
    public static final DeferredItem<BlockItem> RELAY_EMITTER = ITEMS.registerSimpleBlockItem("relay_emitter", ModBlocks.RELAY_EMITTER);
    /** De Stark-kabel voor het stroomnet. */
    /** Een rol Stark-kabel: daarmee teken je kabels (zie CableSpoolItem). Vroeger een blok. */
    public static final DeferredItem<com.senne.starktech.energy.CableSpoolItem> CABLE = ITEMS.registerItem("cable",
            com.senne.starktech.energy.CableSpoolItem::new, new Item.Properties());

    // Fase 11: rayshield (de laserwand zelf heeft geen item: die hoort bij de zenders)
    /** Fase 12: de E.M.P.-zender waarmee je illusies platlegt. */
    public static final DeferredItem<EmpItem> EMP = ITEMS.registerItem("emp",
            EmpItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    public static final DeferredItem<BlockItem> LASER_EMITTER = ITEMS.registerSimpleBlockItem("laser_emitter",
            ModBlocks.LASER_EMITTER);
    public static final DeferredItem<BlockItem> DOME_GENERATOR = ITEMS.registerSimpleBlockItem("dome_generator",
            ModBlocks.DOME_GENERATOR);
    public static final DeferredItem<BlockItem> POWER_CABINET = ITEMS.registerSimpleBlockItem("power_cabinet",
            ModBlocks.POWER_CABINET, new Item.Properties().rarity(Rarity.UNCOMMON));
    // ---------------------------------------------------------------- Fase 15: het harnas
    /** De borstplaat: hier zit de reactor, dus hier komt de repulsor vandaan. */
    // stacksTo(1): het harnas heeft geen duurzaamheid, en dan laat Minecraft het anders tot 64
    // op elkaar stapelen.
    public static final DeferredItem<StarkSuitItem> STARK_CHESTPLATE = ITEMS.registerItem("stark_chestplate",
            properties -> new StarkSuitItem(ArmorItem.Type.CHESTPLATE, properties),
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<StarkSuitItem> STARK_LEGGINGS = ITEMS.registerItem("stark_leggings",
            properties -> new StarkSuitItem(ArmorItem.Type.LEGGINGS, properties),
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    /** De laarzen: hiermee land je zonder schade. */
    public static final DeferredItem<StarkSuitItem> STARK_BOOTS = ITEMS.registerItem("stark_boots",
            properties -> new StarkSuitItem(ArmorItem.Type.BOOTS, properties),
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    /** Een drone-dock voor je hangar. */
    public static final DeferredItem<BlockItem> DRONE_DOCK = ITEMS.registerSimpleBlockItem("drone_dock",
            ModBlocks.DRONE_DOCK, new Item.Properties().rarity(Rarity.UNCOMMON));
    /** Een landingsplatform voor het harnas. */
    public static final DeferredItem<BlockItem> LANDING_PAD = ITEMS.registerSimpleBlockItem("landing_pad",
            ModBlocks.LANDING_PAD, new Item.Properties().rarity(Rarity.UNCOMMON));
    /** De harnas-werkbank, en de upgrades die je daar in het harnas bouwt. */
    public static final DeferredItem<BlockItem> SUIT_WORKBENCH = ITEMS.registerSimpleBlockItem("suit_workbench",
            ModBlocks.SUIT_WORKBENCH, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<com.senne.starktech.workbench.SuitModuleItem> THRUSTER_UPGRADE = ITEMS.registerItem(
            "thruster_upgrade", properties -> new com.senne.starktech.workbench.SuitModuleItem(
                    com.senne.starktech.workbench.SuitModuleItem.Kind.THRUSTERS, properties),
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.senne.starktech.workbench.SuitModuleItem> ARMOR_PLATING = ITEMS.registerItem(
            "armor_plating", properties -> new com.senne.starktech.workbench.SuitModuleItem(
                    com.senne.starktech.workbench.SuitModuleItem.Kind.PLATING, properties),
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.senne.starktech.workbench.SuitModuleItem> REPULSOR_CAPACITOR = ITEMS.registerItem(
            "repulsor_capacitor", properties -> new com.senne.starktech.workbench.SuitModuleItem(
                    com.senne.starktech.workbench.SuitModuleItem.Kind.CAPACITOR, properties),
            new Item.Properties().rarity(Rarity.UNCOMMON));
    /** Sterkere mini-raketten (raketkop) en meer per salvo (magazijn); alleen met een raketpakket. */
    public static final DeferredItem<com.senne.starktech.workbench.SuitModuleItem> ROCKET_WARHEAD = ITEMS.registerItem(
            "rocket_warhead", properties -> new com.senne.starktech.workbench.SuitModuleItem(
                    com.senne.starktech.workbench.SuitModuleItem.Kind.WARHEAD, properties),
            new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<com.senne.starktech.workbench.SuitModuleItem> ROCKET_MAGAZINE = ITEMS.registerItem(
            "rocket_magazine", properties -> new com.senne.starktech.workbench.SuitModuleItem(
                    com.senne.starktech.workbench.SuitModuleItem.Kind.MAGAZINE, properties),
            new Item.Properties().rarity(Rarity.RARE));

    /** Mini-raketten voor op de borstplaat (te maken met een Hydra-onderdeel). */
    public static final DeferredItem<RocketPackItem> ROCKET_PACK = ITEMS.registerItem("rocket_pack",
            RocketPackItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    /** Fase 15: de holotafel. */
    public static final DeferredItem<BlockItem> HOLO_TABLE = ITEMS.registerSimpleBlockItem("holo_table",
            ModBlocks.HOLO_TABLE);

    /** Het automatische torentje, en de raketten die je erin stopt. */
    public static final DeferredItem<BlockItem> STARK_TURRET = ITEMS.registerSimpleBlockItem("stark_turret",
            ModBlocks.STARK_TURRET, new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<BlockItem> HYDRA_TURRET = ITEMS.registerSimpleBlockItem("hydra_turret",
            ModBlocks.HYDRA_TURRET, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> SECURITY_TURRET = ITEMS.registerSimpleBlockItem("security_turret",
            ModBlocks.SECURITY_TURRET, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> SECURITY_CAMERA = ITEMS.registerSimpleBlockItem("security_camera",
            ModBlocks.SECURITY_CAMERA, new Item.Properties().rarity(Rarity.UNCOMMON));
    /** De beveiligingskern: weggehakt houdt hij al zijn instellingen (zie SecurityCoreItem). */
    public static final DeferredItem<com.senne.starktech.security.SecurityCoreItem> SECURITY_CORE = ITEMS.registerItem("security_core",
            properties -> new com.senne.starktech.security.SecurityCoreItem(ModBlocks.SECURITY_CORE.get(), properties),
            new Item.Properties().rarity(Rarity.EPIC));
    // ---------------------------------------------------------------- De server (7 oktober)
    public static final DeferredItem<BlockItem> SERVER_RACK = ITEMS.registerSimpleBlockItem("server_rack",
            ModBlocks.SERVER_RACK, new Item.Properties().rarity(Rarity.UNCOMMON));
    /** Een CPU voor een serverrack: de server reageert sneller. */
    public static final DeferredItem<com.senne.starktech.logic.ServerPartItem> SERVER_CPU = ITEMS.registerItem("server_cpu",
            properties -> new com.senne.starktech.logic.ServerPartItem(com.senne.starktech.logic.ServerPartItem.Kind.CPU, properties),
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    /** Een GPU (8 oktober): half zo goed voor Logic, dubbel zo goed voor het brein van E.D.I.T.H. */
    public static final DeferredItem<com.senne.starktech.logic.ServerPartItem> SERVER_GPU = ITEMS.registerItem("server_gpu",
            properties -> new com.senne.starktech.logic.ServerPartItem(com.senne.starktech.logic.ServerPartItem.Kind.GPU, properties),
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
    /** Een fan (8 oktober): koelt het datacenter. */
    public static final DeferredItem<com.senne.starktech.logic.ServerPartItem> SERVER_FAN = ITEMS.registerItem("server_fan",
            properties -> new com.senne.starktech.logic.ServerPartItem(com.senne.starktech.logic.ServerPartItem.Kind.FAN, properties),
            new Item.Properties().stacksTo(16));
    /** Een RAM-latje: meer diagrammen tegelijk bezig. */
    public static final DeferredItem<com.senne.starktech.logic.ServerPartItem> SERVER_RAM = ITEMS.registerItem("server_ram",
            properties -> new com.senne.starktech.logic.ServerPartItem(com.senne.starktech.logic.ServerPartItem.Kind.RAM, properties),
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    /** Een SSD: meer blokjes in je diagrammen. */
    public static final DeferredItem<com.senne.starktech.logic.ServerPartItem> SERVER_SSD = ITEMS.registerItem("server_ssd",
            properties -> new com.senne.starktech.logic.ServerPartItem(com.senne.starktech.logic.ServerPartItem.Kind.SSD, properties),
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

    /** Een keycard: leeg gecraft, op de desktop geschreven (naam, niveau, nummer). */
    public static final DeferredItem<com.senne.starktech.logic.KeycardItem> KEYCARD = ITEMS.registerItem("keycard",
            com.senne.starktech.logic.KeycardItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<BlockItem> KEYCARD_READER = ITEMS.registerSimpleBlockItem("keycard_reader",
            ModBlocks.KEYCARD_READER, new Item.Properties());
    public static final DeferredItem<BlockItem> CODE_PANEL = ITEMS.registerSimpleBlockItem("code_panel",
            ModBlocks.CODE_PANEL, new Item.Properties());
    /** De Bypass Kit (hoofdstuk 12): een keycardlezer of codepaneel hacken met een minigame. */
    public static final DeferredItem<com.senne.starktech.bypass.BypassKitItem> BYPASS_KIT = ITEMS.registerItem("bypass_kit",
            com.senne.starktech.bypass.BypassKitItem::new,
            new Item.Properties().durability(StarkConfig.Bypass.KIT_USES).rarity(Rarity.UNCOMMON));

    /**
     * De Zone Marker (hoofdstuk 10): linksklik en rechtsklik zetten de twee hoeken van een kubus,
     * sluipen + rechtsklik voegt hem toe aan je zone.
     */
    public static final DeferredItem<com.senne.starktech.security.zone.ZoneMarkerItem> ZONE_MARKER = ITEMS.registerItem(
            "zone_marker", com.senne.starktech.security.zone.ZoneMarkerItem::new, new Item.Properties().stacksTo(1));

    /** C4 met een timer (hoofdstuk 10): rechtsklik in de lucht = de tijd kiezen, plakken = hij loopt. */
    public static final DeferredItem<com.senne.starktech.c4.C4Item> TIMED_C4 = ITEMS.registerItem("timed_c4",
            properties -> new com.senne.starktech.c4.C4Item(ModBlocks.TIMED_C4.get(), properties),
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

    /** C4 met een antenne (hoofdstuk 10): plak hem met een detonator in je rugzak, en hij hoort erbij. */
    public static final DeferredItem<com.senne.starktech.c4.C4Item> REMOTE_C4 = ITEMS.registerItem("remote_c4",
            properties -> new com.senne.starktech.c4.C4Item(ModBlocks.REMOTE_C4.get(), properties),
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    /** De detonator (hoofdstuk 10): rechtsklik = al zijn remote C4 binnen bereik gaan af. */
    public static final DeferredItem<com.senne.starktech.c4.DetonatorItem> DETONATOR = ITEMS.registerItem("detonator",
            com.senne.starktech.c4.DetonatorItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    /** Repair Paste (hoofdstuk 11): rechtsklik op een blok met barsten, en ze zijn opgevuld. */
    public static final DeferredItem<com.senne.starktech.c4.RepairPasteItem> REPAIR_PASTE = ITEMS.registerItem(
            "repair_paste", com.senne.starktech.c4.RepairPasteItem::new, new Item.Properties());
    /** Een gebarsten blok (hoofdstuk 11): wat je overhoudt als je een blok met barsten weghakt. */
    public static final DeferredItem<com.senne.starktech.repair.FromBlockItem> CRACKED_BLOCK = ITEMS.registerItem(
            "cracked_block", properties -> new com.senne.starktech.repair.FromBlockItem(properties,
                    "item.starktech.cracked_block.of", "tooltip.starktech.cracked_block"), new Item.Properties());
    /** Poeder van een blok (hoofdstuk 11): wat de Crusher van een gebarsten blok maakt. */
    public static final DeferredItem<com.senne.starktech.repair.FromBlockItem> BLOCK_POWDER = ITEMS.registerItem(
            "block_powder", properties -> new com.senne.starktech.repair.FromBlockItem(properties,
                    "item.starktech.block_powder.of", "tooltip.starktech.block_powder"), new Item.Properties());
    public static final DeferredItem<BlockItem> CRUSHER = ITEMS.registerSimpleBlockItem("crusher",
            ModBlocks.CRUSHER, new Item.Properties());
    public static final DeferredItem<BlockItem> COMPACTOR = ITEMS.registerSimpleBlockItem("compactor",
            ModBlocks.COMPACTOR, new Item.Properties());
    /** De Arc Lamp (9 oktober): lichtniveau 100 op Stark-stroom. */
    public static final DeferredItem<BlockItem> ARC_LAMP = ITEMS.registerSimpleBlockItem("arc_lamp",
            ModBlocks.ARC_LAMP, new Item.Properties().rarity(Rarity.UNCOMMON));
    /** De lichtschakelaar (9 oktober): op een kabel; aan = stroom erdoor. */
    public static final DeferredItem<BlockItem> LIGHT_SWITCH = ITEMS.registerSimpleBlockItem("light_switch",
            ModBlocks.LIGHT_SWITCH, new Item.Properties());

    /** De laptop waarmee je de beveiliging van een Stark Tower hackt. */
    public static final DeferredItem<com.senne.starktech.security.StarkLaptopItem> STARK_LAPTOP = ITEMS.registerItem(
            "stark_laptop", com.senne.starktech.security.StarkLaptopItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<Item> ROCKET_AMMO = ITEMS.registerSimpleItem("rocket_ammo",
            new Item.Properties());

    // ---------------------------------------------------------------- Hydra

    /** Hydra-tech uit de bunker: nodig voor het raketpakket en de torentjes. */
    public static final DeferredItem<Item> HYDRA_CIRCUIT = ITEMS.registerSimpleItem("hydra_circuit",
            new Item.Properties().rarity(Rarity.RARE));

    /** Het geweer van de Hydra-soldaten. Alleen zij hebben het; het valt nooit. */
    public static final DeferredItem<Item> HYDRA_RIFLE = ITEMS.registerSimpleItem("hydra_rifle",
            new Item.Properties().stacksTo(1));

    /** Om een Hydra-soldaat neer te zetten (creative). */
    public static final DeferredItem<DeferredSpawnEggItem> HYDRA_SOLDIER_SPAWN_EGG = ITEMS.register(
            "hydra_soldier_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.HYDRA_SOLDIER, 0x18191C, 0xCD2020,
                    new Item.Properties()));

    /** Hoofdstuk 21: een guard robot met willekeurige onderdelen (creative). */
    public static final DeferredItem<DeferredSpawnEggItem> GUARD_ROBOT_SPAWN_EGG = ITEMS.register(
            "guard_robot_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.GUARD_ROBOT, 0x464A50, 0xCDBA8C,
                    new Item.Properties()));

    /**
     * Hoofdstuk 21: de 16 onderdelen van een guard robot, per onderdeel (hoofd, lijf, armen, benen) de vier
     * stijlen op volgorde van RobotStyle. Ze heten "commando_head" tot "super_battle_legs".
     */
    public static final Map<RobotSlot, List<DeferredItem<RobotPartItem>>> ROBOT_PARTS = registerRobotParts();

    private static Map<RobotSlot, List<DeferredItem<RobotPartItem>>> registerRobotParts() {
        Map<RobotSlot, List<DeferredItem<RobotPartItem>>> parts = new EnumMap<>(RobotSlot.class);
        for (RobotSlot slot : RobotSlot.values()) {
            List<DeferredItem<RobotPartItem>> styles = new ArrayList<>();
            for (RobotStyle style : RobotStyle.values()) {
                styles.add(ITEMS.registerItem(RobotPartItem.id(slot, style),
                        properties -> new RobotPartItem(properties, slot, style), new Item.Properties().stacksTo(16)));
            }
            parts.put(slot, List.copyOf(styles));
        }
        return parts;
    }

    /** Hoofdstuk 21: de printplaat met 8 pinnen, het brein van een robot. */
    public static final DeferredItem<com.senne.starktech.bot.CircuitBoardItem> ROBOT_CIRCUIT_BOARD = ITEMS.registerItem(
            "robot_circuit_board", com.senne.starktech.bot.CircuitBoardItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    /** Hoofdstuk 21: de werkbank waarop je een guard robot in elkaar zet. */
    public static final DeferredItem<BlockItem> ROBOT_ASSEMBLER = ITEMS.registerSimpleBlockItem("robot_assembler",
            ModBlocks.ROBOT_ASSEMBLER, new Item.Properties().rarity(Rarity.UNCOMMON));

    /** Hoofdstuk 21: het laadstation van een guard robot. */
    public static final DeferredItem<BlockItem> ROBOT_DOCK = ITEMS.registerSimpleBlockItem("robot_dock",
            ModBlocks.ROBOT_DOCK, new Item.Properties());

    /** Hoofdstuk 21: een samengestelde guard robot (uit de Robot Assembler); rechtsklik zet hem neer. */
    public static final DeferredItem<com.senne.starktech.robot.GuardRobotItem> GUARD_ROBOT = ITEMS.registerItem(
            "guard_robot", com.senne.starktech.robot.GuardRobotItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    /** Hoofdstuk 25: een security drone die je zelf bouwt (met een printplaat), om op een Drone Dock te zetten. */
    public static final DeferredItem<com.senne.starktech.robot.GuardDroneItem> GUARD_DRONE = ITEMS.registerItem(
            "guard_drone", com.senne.starktech.robot.GuardDroneItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    /**
     * Hoofdstuk 22: de modules die je op de printplaat soldeert ("scan_module" tot "camera_module"), en de
     * verbrande module die overblijft na een kortsluiting ("burnt_module").
     */
    public static final Map<com.senne.starktech.bot.BotModule, DeferredItem<com.senne.starktech.bot.BotModuleItem>> ROBOT_MODULES =
            registerModules();

    private static Map<com.senne.starktech.bot.BotModule, DeferredItem<com.senne.starktech.bot.BotModuleItem>> registerModules() {
        Map<com.senne.starktech.bot.BotModule, DeferredItem<com.senne.starktech.bot.BotModuleItem>> modules =
                new EnumMap<>(com.senne.starktech.bot.BotModule.class);
        for (com.senne.starktech.bot.BotModule module : com.senne.starktech.bot.BotModule.values()) {
            modules.put(module, ITEMS.registerItem(module.id() + "_module",
                    properties -> new com.senne.starktech.bot.BotModuleItem(properties, module), new Item.Properties().stacksTo(16)));
        }
        return modules;
    }

    /** Hoofdstuk 22: de soldeerbout (slijt; hoeft alleen in je rugzak). */
    public static final DeferredItem<com.senne.starktech.bot.SolderingIronItem> SOLDERING_IRON = ITEMS.registerItem(
            "soldering_iron", com.senne.starktech.bot.SolderingIronItem::new,
            new Item.Properties().durability(com.senne.starktech.bot.SolderingIronItem.USES));

    /** Hoofdstuk 24: kogels voor de Gun-module (rechtsklik op een robot laadt ze). */
    public static final DeferredItem<com.senne.starktech.bot.RobotAmmoItem> ROBOT_AMMO = ITEMS.registerItem(
            "robot_ammo", com.senne.starktech.bot.RobotAmmoItem::new, new Item.Properties());

    /** Hoofdstuk 24: de Ion Blaster legt robots neer zonder ze kapot te maken (slijt). */
    public static final DeferredItem<com.senne.starktech.robot.IonBlasterItem> ION_BLASTER = ITEMS.registerItem(
            "ion_blaster", com.senne.starktech.robot.IonBlasterItem::new,
            new Item.Properties().durability(com.senne.starktech.robot.IonBlasterItem.USES));

    /** Het item van deze module. */
    public static com.senne.starktech.bot.BotModuleItem robotModule(com.senne.starktech.bot.BotModule module) {
        return ROBOT_MODULES.get(module).get();
    }

    /** Het onderdeel-item van deze soort in deze stijl. */
    public static RobotPartItem robotPart(RobotSlot slot, RobotStyle style) {
        return ROBOT_PARTS.get(slot).get(style.ordinal()).get();
    }

    /** Fase 15: een gebouwde drone. Gebruik hem en hij vliegt naar je arsenaal in een baan. */
    public static final DeferredItem<StarkDroneItem> STARK_DRONE = ITEMS.registerItem("stark_drone",
            StarkDroneItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
}
