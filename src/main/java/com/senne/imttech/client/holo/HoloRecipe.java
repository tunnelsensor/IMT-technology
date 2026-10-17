package com.senne.imttech.client.holo;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Het recept van een voorwerp, klaar om als bouwtekening getekend te worden.
 *
 * De receptenlijst staat ook aan jouw kant (de server stuurt hem mee), dus we kunnen hem hier
 * gewoon doorzoeken. Wel één keer per voorwerp: doorzoeken kost tijd en de uitkomst verandert
 * niet, dus onthouden we de laatste.
 *
 * Onderdelen die uit een hele groep mogen komen (bijvoorbeeld "een willekeurige ijzerstaaf")
 * wisselen langzaam af, zodat je ziet dat er meer dan één ding past.
 */
public final class HoloRecipe {
    /** Hoeveel ticks één mogelijkheid van een onderdeel in beeld blijft. */
    private static final int CYCLE_TICKS = 30;

    private final ItemStack[][] grid;

    private HoloRecipe(ItemStack[][] grid) {
        this.grid = grid;
    }

    /** Het onderdeel op die plek in het rooster (kolom, rij), of leeg. */
    public ItemStack at(int column, int row) {
        if (row < 0 || row > 2 || column < 0 || column > 2) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = grid[row][column];
        return stack == null ? ItemStack.EMPTY : stack;
    }

    // ---------------------------------------------------------------- Opzoeken

    /** Een opgezocht recept en wanneer dat was. */
    private record Cached(@Nullable HoloRecipe recipe, long builtAt) {}

    /**
     * Wat we per voorwerp al opgezocht hebben. Eerst was er maar één plekje: stonden er twee
     * tafels met elk een ander voorwerp, dan zochten ze elke frame opnieuw de hele receptenlijst
     * door.
     */
    private static final Map<Item, Cached> CACHE = new HashMap<>();

    /**
     * Zoekt het recept dat dit voorwerp maakt. Geeft null als er geen (gewoon) recept voor is,
     * bijvoorbeeld bij de I.M.T-bril, die je alleen in een Biebia's Industries Tower vindt.
     */
    public static @Nullable HoloRecipe of(Level level, ItemStack wanted) {
        long now = level.getGameTime();
        Cached cached = CACHE.get(wanted.getItem());
        if (cached != null && now - cached.builtAt() < CYCLE_TICKS && now >= cached.builtAt()) {
            return cached.recipe();
        }
        if (CACHE.size() > 64) {
            CACHE.clear();
        }
        HoloRecipe recipe = search(level, wanted, now);
        CACHE.put(wanted.getItem(), new Cached(recipe, now));
        return recipe;
    }

    private static @Nullable HoloRecipe search(Level level, ItemStack wanted, long now) {
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack result;
            try {
                result = recipe.getResultItem(level.registryAccess());
            } catch (RuntimeException e) {
                continue; // een recept dat zijn resultaat pas tijdens het maken bepaalt
            }
            if (result.isEmpty() || !ItemStack.isSameItem(result, wanted)) {
                continue;
            }
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            if (ingredients.isEmpty()) {
                continue;
            }
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : Math.min(3, ingredients.size());
            return new HoloRecipe(layOut(ingredients, width, now));
        }
        return null;
    }

    /** Zet de onderdelen op hun plek in een 3x3-rooster, gecentreerd. */
    private static ItemStack[][] layOut(NonNullList<Ingredient> ingredients, int width, long now) {
        ItemStack[][] grid = new ItemStack[3][3];
        int height = Math.max(1, (ingredients.size() + width - 1) / width);
        // Een recept van 2x2 hoort in het midden te staan, niet in de hoek.
        int offsetX = (3 - Math.min(3, width)) / 2;
        int offsetY = (3 - Math.min(3, height)) / 2;
        for (int i = 0; i < ingredients.size(); i++) {
            int column = i % width + offsetX;
            int row = i / width + offsetY;
            if (column > 2 || row > 2) {
                continue;
            }
            grid[row][column] = pick(ingredients.get(i), now);
        }
        return grid;
    }

    /** Eén mogelijkheid van een onderdeel; bij meer dan één wisselen ze elkaar af. */
    private static @Nullable ItemStack pick(Ingredient ingredient, long now) {
        if (ingredient.isEmpty()) {
            return null;
        }
        ItemStack[] options = ingredient.getItems();
        if (options.length == 0) {
            return null;
        }
        int index = (int) ((now / CYCLE_TICKS) % options.length);
        return options[index];
    }
}
