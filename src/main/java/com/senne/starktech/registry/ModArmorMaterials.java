package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.suit.SuitBalance;
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
 * Het "materiaal" van de E.D.I.T.H.-bril.
 *
 * In Minecraft 1.21.1 is alles wat je op je hoofd draagt een stuk armor, en elk stuk armor
 * heeft een materiaal. Dat materiaal zegt hoeveel bescherming het geeft en - belangrijk voor
 * ons - welke texture er op je hoofd getekend wordt:
 * assets/starktech/textures/models/armor/edith_layer_1.png
 *
 * De bril geeft geen bescherming (0 armor): hij is er voor de slimme functies.
 */
public final class ModArmorMaterials {
    private ModArmorMaterials() {}

    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, StarkTech.MODID);

    /** De naam waarmee de texture gezocht wordt (models/armor/edith_layer_1.png). */
    public static final ResourceLocation EDITH_ASSET = ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "edith");

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> EDITH = MATERIALS.register("edith", () -> {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.HELMET, 0);
        return new ArmorMaterial(
                defense,
                0,                                                   // geen betovering-waarde
                SoundEvents.ARMOR_EQUIP_IRON,                        // geluid als je hem opzet
                () -> Ingredient.of(ModItems.STARK_CHIP.get()),      // repareren met een Stark-chip
                List.of(new ArmorMaterial.Layer(EDITH_ASSET)),
                0.0f,                                                // geen toughness
                0.0f);                                               // geen knockback-weerstand
    });

    /** De naam waarmee de texture van het harnas gezocht wordt. */
    public static final ResourceLocation STARK_ASSET = ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "stark");

    /**
     * Het harnas: borstplaat, broek en laarzen.
     *
     * De bescherming die hier staat is wat het pak zonder stroom doet. Alles wat het pak
     * bijzonder maakt (schade wegnemen, vliegen, repulsors) zit in EdithSuit en werkt alleen
     * met de bril op.
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> STARK = MATERIALS.register("stark", () -> {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.CHESTPLATE, SuitBalance.ARMOR_CHEST);
        defense.put(ArmorItem.Type.LEGGINGS, SuitBalance.ARMOR_LEGS);
        defense.put(ArmorItem.Type.BOOTS, SuitBalance.ARMOR_BOOTS);
        return new ArmorMaterial(
                defense,
                12,                                                  // betovering-waarde
                SoundEvents.ARMOR_EQUIP_NETHERITE,
                () -> Ingredient.of(ModItems.STARK_CHIP.get()),      // repareren met een Stark-chip
                List.of(new ArmorMaterial.Layer(STARK_ASSET)),
                SuitBalance.TOUGHNESS,
                SuitBalance.KNOCKBACK_RESISTANCE);
    });

    /** Handig voor de items: het materiaal als Holder. */
    public static Holder<ArmorMaterial> edith() {
        return EDITH;
    }

    public static Holder<ArmorMaterial> stark() {
        return STARK;
    }
}
