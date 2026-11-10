package com.senne.imttech.backpack;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Een rugzak verven in de crafting table (hoofdstuk 72, jouw wens: "de 16 kleuren van Minecraft, en combineren van deze
 * kleuren zoals leather armor, plus een preset camokleur die je maakt van bruin, licht- en donkergroen; dit is dan een
 * kleur op zich").
 * <ul>
 *   <li>Een rugzak met <b>een of meer kleurstoffen</b>: de kleuren worden gemengd, met de kleur die hij al had erbij,
 *       precies zoals leren harnas (Minecraft zelf rekent het uit: DyedItemColor.applyDyes).</li>
 *   <li>Een rugzak met <b>precies bruin + lichtgroen + groen</b> (elk één): <b>camo</b>. Dat is geen mengkleur maar een
 *       eigen patroon (vlekken bruin en donkergroen op lichtgroen).</li>
 *   <li>Een camo-rugzak met een andere kleurstof: weer een gewone kleur (alleen die kleurstof).</li>
 * </ul>
 * De inhoud, de pouches en de rest van de rugzak blijven zoals ze waren.
 */
public class BackpackDyeRecipe extends CustomRecipe {
    public BackpackDyeRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** De rugzak en de kleurstoffen in het raster, of null als het geen verfrecept is. */
    private static Parts parts(CraftingInput input) {
        ItemStack pack = ItemStack.EMPTY;
        List<DyeItem> dyes = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (BackpackItem.isBackpack(stack)) {
                if (!pack.isEmpty()) {
                    return null;
                }
                pack = stack;
            } else if (stack.getItem() instanceof DyeItem dye) {
                dyes.add(dye);
            } else {
                return null;
            }
        }
        return pack.isEmpty() || dyes.isEmpty() ? null : new Parts(pack, dyes);
    }

    private record Parts(ItemStack pack, List<DyeItem> dyes) {
        /** Precies bruin, lichtgroen en groen (elk één keer)? */
        boolean camo() {
            if (dyes.size() != 3) {
                return false;
            }
            List<DyeColor> colours = new ArrayList<>();
            for (DyeItem dye : dyes) {
                colours.add(dye.getDyeColor());
            }
            return colours.contains(DyeColor.BROWN) && colours.contains(DyeColor.LIME) && colours.contains(DyeColor.GREEN);
        }
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return parts(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Parts parts = parts(input);
        if (parts == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = parts.pack().copyWithCount(1);
        // Een geverfde rugzak is een nieuw ding: niet meer "dezelfde" als een die iemand open heeft.
        result.remove(BackpackRegistry.PACK_ID.get());
        if (parts.camo()) {
            result.set(BackpackRegistry.CAMO.get(), true);
            result.remove(DataComponents.DYED_COLOR);
            return result;
        }
        if (BackpackItem.camo(result)) {
            // Van camo naar een kleur: alleen de nieuwe kleurstoffen tellen (camo is geen kleur om mee te mengen).
            result.remove(BackpackRegistry.CAMO.get());
            result.remove(DataComponents.DYED_COLOR);
        } else if (!result.has(DataComponents.DYED_COLOR)) {
            // Een nieuwe rugzak is olijfgroen; mengen begint daarmee, zoals bij leer met zijn bruin.
            result.set(DataComponents.DYED_COLOR, new DyedItemColor(BackpackItem.OLIVE, true));
        }
        result.set(DataComponents.DYED_COLOR, new DyedItemColor(mix(result.get(DataComponents.DYED_COLOR), parts.dyes()), true));
        return result;
    }

    /**
     * Kleuren mengen zoals Minecraft het bij leer doet (DyedItemColor.applyDyes; die werkt alleen voor items met de tag
     * "dyeable", en dan zou ook het verfrecept van leer op de rugzak werken en met camo botsen). Het gemiddelde van alle
     * kleuren, en dan zo licht als de lichtste kleuren gemiddeld waren.
     */
    static int mix(DyedItemColor current, List<DyeItem> dyes) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int brightest = 0;
        int count = 0;
        List<Integer> colours = new ArrayList<>();
        if (current != null) {
            colours.add(current.rgb());
        }
        for (DyeItem dye : dyes) {
            colours.add(dye.getDyeColor().getTextureDiffuseColor());
        }
        for (int colour : colours) {
            int r = (colour >> 16) & 0xFF;
            int g = (colour >> 8) & 0xFF;
            int b = colour & 0xFF;
            brightest += Math.max(r, Math.max(g, b));
            red += r;
            green += g;
            blue += b;
            count++;
        }
        if (count == 0) {
            return BackpackItem.OLIVE;
        }
        int r = red / count;
        int g = green / count;
        int b = blue / count;
        float bright = (float) brightest / count;
        float max = Math.max(1, Math.max(r, Math.max(g, b)));
        r = (int) (r * bright / max);
        g = (int) (g * bright / max);
        b = (int) (b * bright / max);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BackpackRegistry.DYE_RECIPE.get();
    }
}
