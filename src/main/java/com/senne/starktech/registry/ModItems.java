package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.edith.EdithGlassesItem;
import com.senne.starktech.edith.UpgradeItem;
import com.senne.starktech.arsenal.StarkDroneItem;
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
    public static final DeferredItem<BlockItem> SECURITY_CORE = ITEMS.registerSimpleBlockItem("security_core",
            ModBlocks.SECURITY_CORE, new Item.Properties().rarity(Rarity.EPIC));
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

    /** Fase 15: een gebouwde drone. Gebruik hem en hij vliegt naar je arsenaal in een baan. */
    public static final DeferredItem<StarkDroneItem> STARK_DRONE = ITEMS.registerItem("stark_drone",
            StarkDroneItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
}
