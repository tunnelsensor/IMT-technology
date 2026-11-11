package com.senne.imttech.guns;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModRecipes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.Nullable;

/**
 * Een recept van de Gunsmith Table (hoofdstuk 78, jouw wens: "Zorg dat je gun onderdelen niet meer in de crafting table
 * kunt maken maar enkel in de gun smit table"). Alles voor wapens maak je daar: de onderdelen van de Glock, de onderdelen
 * van de lasergeweren (ook netherite), de Gun Sling en het magazijn (jouw keuzes). De crafting table kent deze recepten
 * niet: het is een eigen soort ({@link ModRecipes#GUNSMITHING}).
 *
 * Geen patroon in een raster, maar een lijstje: zoveel van dit, zoveel van dat (jouw keuze: een tabblad met tegels in de
 * tafel, geen vakjes). De materialen komen uit je inventaris en je tweede hand, zoals bij de harnas-werkbank.
 *
 * Elk recept staat als JSON-bestand in data/imttech/recipe, net als de andere recepten:
 * <pre>
 * {
 *   "type": "imttech:gunsmithing",
 *   "ingredients": [
 *     { "item": "imttech:steel_ingot", "count": 5 }
 *   ],
 *   "result": { "id": "imttech:gun_slide", "components": { "imttech:gun_material": "steel" } }
 * }
 * </pre>
 * Een ingrediënt kan alles wat in een gewoon recept ook kan ("item", "tag", of een soort van NeoForge zoals
 * "neoforge:components" voor "een titanium onderdeel"), met "count" erbij (zonder count is het er één).
 */
public class GunsmithRecipe implements Recipe<GunsmithRecipe.Carried> {
    public static final MapCodec<GunsmithRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ExtraCodecs.nonEmptyList(SizedIngredient.FLAT_CODEC.listOf()).fieldOf("ingredients").forGetter(GunsmithRecipe::ingredients),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result))
            .apply(instance, GunsmithRecipe::new));

    /** Zo gaat een recept naar de spelers (het scherm van de tafel toont wat het kost). */
    public static final StreamCodec<RegistryFriendlyByteBuf, GunsmithRecipe> STREAM_CODEC = StreamCodec.composite(
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), GunsmithRecipe::ingredients,
            ItemStack.STREAM_CODEC, recipe -> recipe.result,
            GunsmithRecipe::new);

    /** Hoe vaak het scherm hoogstens zegt dat je iets kunt maken. */
    public static final int MAX_TIMES = 64;

    private final List<SizedIngredient> ingredients;
    private final ItemStack result;

    public GunsmithRecipe(List<SizedIngredient> ingredients, ItemStack result) {
        this.ingredients = List.copyOf(ingredients);
        this.result = result;
    }

    /** Wat het kost: elk ingrediënt met zijn aantal, in de volgorde van het bestand. */
    public List<SizedIngredient> ingredients() {
        return ingredients;
    }

    /** Wat eruit komt (een kopie: daar mag je aan veranderen). */
    public ItemStack result() {
        return result.copy();
    }

    // ================================================================ Wat je bij je hebt

    /**
     * De vakjes waar materiaal uit mag komen: je inventaris (hotbar en rugzak) en je tweede hand, niet wat je aanhebt.
     * Het zijn de echte stapels, geen kopieën: {@link #take} haalt er meteen uit.
     */
    public record Carried(List<ItemStack> stacks) implements RecipeInput {
        public static Carried of(Inventory inventory) {
            List<ItemStack> all = new ArrayList<>(inventory.items);
            all.addAll(inventory.offhand);
            return new Carried(all);
        }

        @Override
        public ItemStack getItem(int index) {
            return stacks.get(index);
        }

        @Override
        public int size() {
            return stacks.size();
        }
    }

    /** Hoeveel heb je van dit ingrediënt bij je (alle stapels samen)? */
    public static int have(Carried carried, SizedIngredient ingredient) {
        int found = 0;
        for (ItemStack stack : carried.stacks()) {
            if (!stack.isEmpty() && ingredient.ingredient().test(stack)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /**
     * Hoeveel er uit elk vakje moet om dit {@code times} keer te maken, of null als je niet genoeg hebt. Ingrediënt na
     * ingrediënt; wat het ene al nam, telt niet meer voor het volgende. Van onderdelen gaan eerst de meest versleten weg:
     * een titanium onderdeel met twee Netherite Ingots wordt een nieuw netherite onderdeel, dus dan het slechtste.
     */
    private int[] plan(Carried carried, int times) {
        List<ItemStack> stacks = carried.stacks();
        int[] take = new int[stacks.size()];
        for (SizedIngredient ingredient : ingredients) {
            int left = ingredient.count() * times;
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = stacks.get(i);
                if (!stack.isEmpty() && ingredient.ingredient().test(stack)) {
                    slots.add(i);
                }
            }
            // Sorteren houdt de volgorde van je inventaris bij gelijke conditie (de meeste dingen zijn gewoon "100").
            slots.sort(Comparator.comparingInt(slot -> condition(stacks.get(slot))));
            for (int slot : slots) {
                if (left <= 0) {
                    break;
                }
                int amount = Math.min(left, stacks.get(slot).getCount() - take[slot]);
                if (amount > 0) {
                    take[slot] += amount;
                    left -= amount;
                }
            }
            if (left > 0) {
                return null;
            }
        }
        return take;
    }

    /** Hoe heel een onderdeel is (100 = nieuw; alles wat geen onderdeel is, telt als nieuw). */
    private static int condition(ItemStack stack) {
        Integer condition = stack.get(ModDataComponents.PART_CONDITION.get());
        return condition == null ? PartState.NEW : condition;
    }

    /** Heb je alles bij je om dit één keer te maken? */
    public boolean affordable(Carried carried) {
        return plan(carried, 1) != null;
    }

    /** Hoe vaak kun je dit maken met wat je bij je hebt (hoogstens {@link #MAX_TIMES})? */
    public int times(Carried carried) {
        int times = 0;
        while (times < MAX_TIMES && plan(carried, times + 1) != null) {
            times++;
        }
        return times;
    }

    /**
     * Haalt de materialen voor één keer uit je inventaris. Geeft null (en neemt niets) als je niet genoeg hebt; anders
     * wat er van de materialen overblijft (zoals een lege emmer na een emmer water; bij onze recepten niets).
     */
    public @Nullable List<ItemStack> take(Carried carried) {
        int[] take = plan(carried, 1);
        if (take == null) {
            return null;
        }
        List<ItemStack> leftovers = new ArrayList<>();
        for (int i = 0; i < take.length; i++) {
            if (take[i] <= 0) {
                continue;
            }
            ItemStack stack = carried.stacks().get(i);
            ItemStack remainder = stack.getCraftingRemainingItem();
            if (!remainder.isEmpty()) {
                leftovers.add(remainder.copyWithCount(remainder.getCount() * take[i]));
            }
            stack.shrink(take[i]);
        }
        return leftovers;
    }

    // ================================================================ Alle recepten van de tafel

    /**
     * Alle recepten van de tafel, op naam gesorteerd. Zo heeft elk recept op de server en in jouw spel hetzelfde nummer:
     * dat nummer stuurt het scherm als je op Make klikt (zie GunsmithMenu).
     */
    public static List<RecipeHolder<GunsmithRecipe>> all(RecipeManager recipes) {
        List<RecipeHolder<GunsmithRecipe>> all = new ArrayList<>(recipes.getAllRecipesFor(ModRecipes.GUNSMITHING.get()));
        all.sort(Comparator.comparing(RecipeHolder::id));
        return all;
    }

    /** Het recept met dit nummer, of null. */
    public static @Nullable RecipeHolder<GunsmithRecipe> byIndex(RecipeManager recipes, int index) {
        List<RecipeHolder<GunsmithRecipe>> all = all(recipes);
        return index >= 0 && index < all.size() ? all.get(index) : null;
    }

    // ================================================================ Wat Minecraft van een recept wil weten

    @Override
    public boolean matches(Carried input, Level level) {
        return affordable(input);
    }

    @Override
    public ItemStack assemble(Carried input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (SizedIngredient ingredient : ingredients) {
            list.add(ingredient.ingredient());
        }
        return list;
    }

    /**
     * "Bijzonder": dan laat het receptenboek van Minecraft het met rust. Dat kent alleen de crafting table, de ovens en
     * dergelijke, en zou anders bij elk recept "Unknown recipe category" in het log zetten.
     */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(ModItems.GUNSMITH_TABLE.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.GUNSMITHING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.GUNSMITHING.get();
    }

    /** Leest een recept uit zijn JSON-bestand, en stuurt het naar de spelers. */
    public static class Serializer implements RecipeSerializer<GunsmithRecipe> {
        @Override
        public MapCodec<GunsmithRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, GunsmithRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
