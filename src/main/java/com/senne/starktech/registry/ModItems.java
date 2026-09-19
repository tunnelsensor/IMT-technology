package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.edith.EdithGlassesItem;
import com.senne.starktech.edith.UpgradeItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Alle items van de mod.
 */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StarkTech.MODID);

    /**
     * Verwijst naar assets/starktech/equipment/edith.json: dat bestand zegt welke texture
     * er op je hoofd getekend wordt als je de bril draagt.
     */
    public static final ResourceKey<EquipmentAsset> EDITH_EQUIPMENT_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(StarkTech.MODID, "edith"));

    public static final DeferredItem<EdithGlassesItem> EDITH_GLASSES = ITEMS.registerItem("edith_glasses",
            EdithGlassesItem::new,
            properties -> properties
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    // "Equippable" maakt dat je de bril in het helm-slot kunt zetten.
                    // Hij heeft geen duurzaamheid, dus hij gaat nooit kapot.
                    .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                            .setEquipSound(SoundEvents.ARMOR_EQUIP_IRON)
                            .setAsset(EDITH_EQUIPMENT_ASSET)
                            .setDamageOnHurt(false)
                            .build()));

    /** Onderdeel om de bril en het laadstation te craften. */
    public static final DeferredItem<Item> STARK_CHIP = ITEMS.registerSimpleItem("stark_chip",
            properties -> properties.rarity(Rarity.UNCOMMON));

    public static final DeferredItem<UpgradeItem> BATTERY_UPGRADE = ITEMS.registerItem("battery_upgrade",
            properties -> new UpgradeItem(UpgradeItem.Type.BATTERY, properties),
            properties -> properties.stacksTo(16).rarity(Rarity.RARE));

    public static final DeferredItem<UpgradeItem> SCANNER_UPGRADE = ITEMS.registerItem("scanner_upgrade",
            properties -> new UpgradeItem(UpgradeItem.Type.SCANNER, properties),
            properties -> properties.stacksTo(16).rarity(Rarity.RARE));

    public static final DeferredItem<UpgradeItem> DRONE_UPGRADE = ITEMS.registerItem("drone_upgrade",
            properties -> new UpgradeItem(UpgradeItem.Type.DRONE, properties),
            properties -> properties.stacksTo(16).rarity(Rarity.RARE));

    public static final DeferredItem<BlockItem> CHARGING_STATION = ITEMS.registerSimpleBlockItem("charging_station",
            ModBlocks.CHARGING_STATION);
}
