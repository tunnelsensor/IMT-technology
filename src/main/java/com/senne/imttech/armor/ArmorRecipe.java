package com.senne.imttech.armor;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.imttech.guns.GunsmithRecipe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.Nullable;

/**
 * Een recept van de Armor Workbench (hoofdstuk 111, jouw wens: "maak voor de bodyarmor een eigen workbench, hier maak je
 * alles voor de armor"). Precies zoals een recept van de Gunsmith Table (een lijstje: zoveel van dit, zoveel van dat; de
 * materialen komen uit je inventaris), maar een eigen soort ({@link ArmorRegistry#ARMORING}): de crafting table en de
 * Gunsmith Table kennen ze dus niet. Het rekenwerk (wat je hebt, hoe vaak je het kunt maken, de materialen eruit halen) erft
 * het van GunsmithRecipe.
 * <pre>
 * {
 *   "type": "imttech:armoring",
 *   "ingredients": [ { "item": "imttech:steel_ingot", "count": 5 }, { "item": "imttech:polymer" } ],
 *   "result": { "id": "imttech:steel_plate" }
 * }
 * </pre>
 */
public class ArmorRecipe extends GunsmithRecipe {
    public static final MapCodec<ArmorRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ExtraCodecs.nonEmptyList(SizedIngredient.FLAT_CODEC.listOf()).fieldOf("ingredients").forGetter(ArmorRecipe::ingredients),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result()))
            .apply(instance, ArmorRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorRecipe> STREAM_CODEC = StreamCodec.composite(
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), ArmorRecipe::ingredients,
            ItemStack.STREAM_CODEC, ArmorRecipe::result,
            ArmorRecipe::new);

    /** Het tabblad waar een recept staat: vesten, plates, of (een recept van een datapack) "anders" (bij de plates). */
    public enum Tab { VESTS, PLATES }

    public ArmorRecipe(List<SizedIngredient> ingredients, ItemStack result) {
        super(ingredients, result);
    }

    /** Op welk tabblad dit recept staat: een vest bij Vests, al de rest (de plates) bij Plates. */
    public Tab tab() {
        return VestItem.isVest(result()) ? Tab.VESTS : Tab.PLATES;
    }

    /**
     * Alle recepten van de workbench in een vaste volgorde: eerst de vesten, dan de plates van zwak naar sterk (polymer, steel,
     * titanium, netherite), en verder op naam. Zo heeft elk recept op de server en in jouw spel hetzelfde nummer (dat stuurt
     * het scherm als je op Make klikt), en staan de tegels in een logische volgorde. (Een andere naam dan
     * GunsmithRecipe.all: die geeft recepten van de Gunsmith Table.)
     */
    public static List<RecipeHolder<ArmorRecipe>> allArmor(RecipeManager recipes) {
        List<RecipeHolder<ArmorRecipe>> all = new ArrayList<>(recipes.getAllRecipesFor(ArmorRegistry.ARMORING.get()));
        all.sort(Comparator.<RecipeHolder<ArmorRecipe>>comparingInt(holder -> holder.value().tab().ordinal())
                .thenComparingInt(holder -> {
                    PlateTier tier = PlateItem.tierOf(holder.value().result());
                    return tier == null ? -1 : tier.ordinal();
                })
                .thenComparing(RecipeHolder::id));
        return all;
    }

    /** Het recept met dit nummer, of null. */
    public static @Nullable RecipeHolder<ArmorRecipe> byIndexOf(RecipeManager recipes, int index) {
        List<RecipeHolder<ArmorRecipe>> all = allArmor(recipes);
        return index >= 0 && index < all.size() ? all.get(index) : null;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(ArmorRegistry.ARMOR_WORKBENCH.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ArmorRegistry.ARMORING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ArmorRegistry.ARMORING.get();
    }

    /** Leest een recept uit zijn JSON-bestand, en stuurt het naar de spelers (het scherm toont wat het kost). */
    public static class Serializer implements RecipeSerializer<ArmorRecipe> {
        @Override
        public MapCodec<ArmorRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ArmorRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
