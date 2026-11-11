package com.senne.imttech.registry;

import com.senne.imttech.ImtTech;
import com.senne.imttech.guns.GunsmithRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Onze eigen soorten recepten. Voor nu één: <b>gunsmithing</b> (hoofdstuk 78), de recepten van de Gunsmith Table. Een
 * recepttype zegt "dit is een eigen soort" (de crafting table kijkt er dus niet naar); de serializer leest de JSON-bestanden
 * in data/imttech/recipe en stuurt de recepten naar de spelers, zodat het scherm van de tafel ze ook kent.
 */
public final class ModRecipes {
    private ModRecipes() {}

    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, ImtTech.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ImtTech.MODID);

    /** Hoofdstuk 78: alles wat je op de Gunsmith Table maakt (onderdelen, de sling, het magazijn). */
    public static final DeferredHolder<RecipeType<?>, RecipeType<GunsmithRecipe>> GUNSMITHING = TYPES.register("gunsmithing",
            () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "gunsmithing")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GunsmithRecipe>> GUNSMITHING_SERIALIZER =
            SERIALIZERS.register("gunsmithing", GunsmithRecipe.Serializer::new);
}
