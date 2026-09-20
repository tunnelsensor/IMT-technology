package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.edith.EdithGlassesItem;
import com.senne.starktech.edith.UpgradeItem;
import com.senne.starktech.illusion.EmpItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
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

    // Fase 11: rayshield (de laserwand zelf heeft geen item: die hoort bij de zenders)
    /** Fase 12: de E.M.P.-zender waarmee je illusies platlegt. */
    public static final DeferredItem<EmpItem> EMP = ITEMS.registerItem("emp",
            EmpItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    public static final DeferredItem<BlockItem> LASER_EMITTER = ITEMS.registerSimpleBlockItem("laser_emitter",
            ModBlocks.LASER_EMITTER);
    public static final DeferredItem<BlockItem> DOME_GENERATOR = ITEMS.registerSimpleBlockItem("dome_generator",
            ModBlocks.DOME_GENERATOR);
}
