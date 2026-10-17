package com.senne.imttech.registry;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Het "materiaal" van de I.M.T-bril.
 *
 * In Minecraft 1.21.1 is alles wat je op je hoofd draagt een stuk armor, en elk stuk armor
 * heeft een materiaal. Dat materiaal zegt hoeveel bescherming het geeft en - belangrijk voor
 * ons - welke texture er op je hoofd getekend wordt:
 * assets/imttech/textures/models/armor/imt_layer_1.png
 *
 * De bril geeft geen bescherming (0 armor): hij is er voor de slimme functies.
 */
public final class ModArmorMaterials {
    private ModArmorMaterials() {}

    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, ImtTech.MODID);

    /** De naam waarmee de texture gezocht wordt (models/armor/imt_layer_1.png). */
    public static final ResourceLocation IMT_ASSET = ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "imt");

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> IMT = MATERIALS.register("imt", () -> {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.HELMET, 0);
        return new ArmorMaterial(
                defense,
                0,                                                   // geen betovering-waarde
                SoundEvents.ARMOR_EQUIP_IRON,                        // geluid als je hem opzet
                () -> Ingredient.of(ModItems.COMPUTER_CHIP.get()),      // repareren met een computerchip
                List.of(new ArmorMaterial.Layer(IMT_ASSET)),
                0.0f,                                                // geen toughness
                0.0f);                                               // geen knockback-weerstand
    });

    /** De naam waarmee de texture van het harnas gezocht wordt. */
    public static final ResourceLocation TECH_ASSET = ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "tech");

    /**
     * Het harnas: borstplaat, broek en laarzen.
     *
     * De bescherming die hier staat is wat het pak zonder stroom doet. Alles wat het pak
     * bijzonder maakt (schade wegnemen, vliegen, laser cannons) zit in ImtSuit en werkt alleen
     * met de bril op.
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> TECH = MATERIALS.register("tech", () -> {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.CHESTPLATE, ImtConfig.Suit.ARMOR_CHEST);
        defense.put(ArmorItem.Type.LEGGINGS, ImtConfig.Suit.ARMOR_LEGS);
        defense.put(ArmorItem.Type.BOOTS, ImtConfig.Suit.ARMOR_BOOTS);
        return new ArmorMaterial(
                defense,
                12,                                                  // betovering-waarde
                SoundEvents.ARMOR_EQUIP_NETHERITE,
                () -> Ingredient.of(ModItems.COMPUTER_CHIP.get()),      // repareren met een computerchip
                List.of(new ArmorMaterial.Layer(TECH_ASSET)),
                ImtConfig.Suit.TOUGHNESS,
                ImtConfig.Suit.KNOCKBACK_RESISTANCE);
    });

    /** Handig voor de items: het materiaal als Holder. */
    public static Holder<ArmorMaterial> imt() {
        return IMT;
    }

    public static Holder<ArmorMaterial> tech() {
        return TECH;
    }
}
