package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunPartItem;
import com.senne.imttech.guns.GunsmithMenu;
import com.senne.imttech.guns.GunsmithRecipe;
import com.senne.imttech.guns.GunsmithTableBlockEntity;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModRecipes;
import com.senne.imttech.weapons.GunSlingItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserPart;
import com.senne.imttech.weapons.LaserPartItem;
import com.senne.imttech.weapons.LaserWorkbenchBlockEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor hoofdstuk 78. Jouw wens: "Zorg dat je gun onderdelen niet meer in de crafting table kunt maken maar enkel
 * in de gun smit table, zorg dat geen enkel recepie het zelfde is, nu zijn de laser shotgun frame en laser sniper frame
 * het zelfde". Jouw keuzes: alles (ook de laseronderdelen, de Gun Sling en het magazijn) op de Gunsmith Table, met een
 * tabblad "Make parts"; de Laser Workbench zet alleen in elkaar.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class GunsmithRecipeGameTests {
    private GunsmithRecipeGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Hoort dit alleen op de Gunsmith Table gemaakt te worden (een onderdeel, de sling, een magazijn, een attachment)? */
    private static boolean onlyAtTheTable(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof GunPartItem || item instanceof LaserPartItem || item instanceof GunSlingItem
                || item instanceof MagazineItem || item instanceof com.senne.imttech.guns.AttachmentItem;
    }

    /** Geen enkel ander recept (de crafting table, een oven, de smithing table...) maakt een onderdeel, sling of magazijn. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "gunsmithing")
    public static void gun_parts_are_not_in_the_crafting_table(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        for (RecipeHolder<?> holder : helper.getLevel().getRecipeManager().getRecipes()) {
            if (holder.value().getType() == ModRecipes.GUNSMITHING.get()) {
                continue;
            }
            ItemStack result = holder.value().getResultItem(registries);
            check(!onlyAtTheTable(result), "Er is nog een ander recept voor " + result.getHoverName().getString() + ": "
                    + holder.id() + " (" + BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()) + ")");
        }
        helper.succeed();
    }

    /**
     * Op de tafel staat precies één recept voor elk onderdeel van de Glock (in elk materiaal waar het in bestaat), voor
     * elk onderdeel van elk lasergeweer (titanium en netherite), voor de sling, voor het magazijn en (hoofdstuk 103,
     * stap 5) voor elk attachment. Niet meer, niet minder.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "gunsmithing")
    public static void gunsmith_table_has_one_recipe_per_part(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        List<ItemStack> wanted = new ArrayList<>();
        // Hoofdstuk 103: elk vuurwapen zijn eigen onderdelen (in de materialen waarin ze bestaan) en zijn eigen magazijn.
        for (com.senne.imttech.guns.FirearmType firearm : com.senne.imttech.guns.FirearmType.values()) {
            for (GunPart part : GunPart.values()) {
                for (GunMaterial material : part.materials(firearm)) {
                    wanted.add(GunPartItem.of(firearm, part, material));
                }
            }
            if (!firearm.tube()) {
                wanted.add(new ItemStack(ModItems.magazine(firearm)));
                // Stap 6b: en zijn lange magazijn (de extended mag).
                wanted.add(new ItemStack(ModItems.magazine(firearm, true)));
            }
        }
        for (GunType type : GunType.values()) {
            for (LaserPart part : LaserPart.wearing()) {
                wanted.add(LaserPartItem.of(type, part, GunMaterial.TITANIUM));
                wanted.add(LaserPartItem.of(type, part, GunMaterial.NETHERITE));
            }
        }
        wanted.add(new ItemStack(ModItems.GUN_SLING.get()));
        for (com.senne.imttech.guns.Attachment attachment : com.senne.imttech.guns.Attachment.values()) {
            wanted.add(new ItemStack(ModItems.attachment(attachment)));
        }
        var all = GunsmithRecipe.all(helper.getLevel().getRecipeManager());
        for (ItemStack want : wanted) {
            List<ResourceLocation> found = new ArrayList<>();
            for (var holder : all) {
                if (ItemStack.isSameItemSameComponents(holder.value().getResultItem(registries), want)) {
                    found.add(holder.id());
                }
            }
            check(found.size() == 1, "Voor " + want.getHoverName().getString() + " zijn er " + found.size()
                    + " recepten op de Gunsmith Table (verwacht 1): " + found);
        }
        check(all.size() == wanted.size(), "Er staan " + all.size() + " recepten op de Gunsmith Table, verwacht " + wanted.size());
        helper.succeed();
    }

    /**
     * Geen twee recepten zijn hetzelfde (jouw wens; eerst waren de frames van de shotgun en de sniper hetzelfde, en nog
     * veel meer laseronderdelen, en dan kon je er maar één van maken). Op de tafel: geen twee lijstjes met dezelfde
     * ingrediënten en aantallen. In de crafting table: geen twee recepten met hetzelfde patroon (ook niet gespiegeld), en
     * geen recept zonder vorm met dezelfde ingrediënten als een ander; telkens als er minstens één van ons bij is.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "gunsmithing")
    public static void no_two_recipes_are_the_same(GameTestHelper helper) {
        RecipeManager recipes = helper.getLevel().getRecipeManager();
        // De Gunsmith Table.
        Map<String, ResourceLocation> table = new HashMap<>();
        for (var holder : GunsmithRecipe.all(recipes)) {
            List<String> parts = new ArrayList<>();
            for (SizedIngredient ingredient : holder.value().ingredients()) {
                parts.add(signature(ingredient.ingredient()) + " x" + ingredient.count());
            }
            Collections.sort(parts);
            String key = String.join(" + ", parts);
            ResourceLocation before = table.put(key, holder.id());
            check(before == null, "Twee recepten op de Gunsmith Table kosten hetzelfde: " + before + " en " + holder.id() + " (" + key + ")");
        }
        // De crafting table.
        Map<String, ResourceLocation> shapes = new HashMap<>();
        Map<String, ResourceLocation> heaps = new HashMap<>();
        Map<String, ResourceLocation> shapeless = new HashMap<>();
        for (RecipeHolder<CraftingRecipe> holder : recipes.getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            if (recipe.isSpecial()) {
                continue;
            }
            if (recipe instanceof ShapedRecipe shaped) {
                String normal = grid(shaped, false);
                String mirrored = grid(shaped, true);
                String key = normal.compareTo(mirrored) <= 0 ? normal : mirrored;
                same(shapes.put(key, holder.id()), holder.id(), "hetzelfde patroon");
                heaps.putIfAbsent(heap(shaped.getIngredients()), holder.id());
            } else if (recipe instanceof ShapelessRecipe) {
                String key = heap(recipe.getIngredients());
                same(shapeless.put(key, holder.id()), holder.id(), "dezelfde ingrediënten");
                // Een recept zonder vorm past ook op het patroon van een recept met vorm met dezelfde ingrediënten.
                same(heaps.get(key), holder.id(), "dezelfde ingrediënten (een met en een zonder vorm)");
            }
        }
        helper.succeed();
    }

    /** Twee recepten met dezelfde invoer: fout als er minstens één van ons bij is (vanilla tegen vanilla gaat ons niet aan). */
    private static void same(ResourceLocation before, ResourceLocation now, String what) {
        if (before != null && !before.equals(now)
                && (before.getNamespace().equals(ImtTech.MODID) || now.getNamespace().equals(ImtTech.MODID))) {
            throw new GameTestAssertException("Twee recepten in de crafting table hebben " + what + ": " + before + " en " + now);
        }
    }

    /** Wat een ingrediënt aanneemt: de items (een tag uitgeschreven), met hun data components. */
    private static String signature(Ingredient ingredient) {
        List<String> items = new ArrayList<>();
        for (ItemStack stack : ingredient.getItems()) {
            items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + stack.getComponentsPatch().toString());
        }
        Collections.sort(items);
        return items.toString();
    }

    /** Het patroon van een recept met vorm, rij voor rij (gespiegeld: elke rij omgedraaid). */
    private static String grid(ShapedRecipe recipe, boolean mirrored) {
        StringBuilder out = new StringBuilder(recipe.getWidth() + "x" + recipe.getHeight());
        List<Ingredient> ingredients = recipe.getIngredients();
        for (int y = 0; y < recipe.getHeight(); y++) {
            out.append(" |");
            for (int x = 0; x < recipe.getWidth(); x++) {
                int column = mirrored ? recipe.getWidth() - 1 - x : x;
                out.append(' ').append(signature(ingredients.get(y * recipe.getWidth() + column)));
            }
        }
        return out.toString();
    }

    /** De ingrediënten op een hoop (zonder de lege vakjes), gesorteerd. */
    private static String heap(List<Ingredient> ingredients) {
        List<String> all = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                all.add(signature(ingredient));
            }
        }
        Collections.sort(all);
        return all.toString();
    }

    /** Het nummer van dit recept op de tafel (zoals het scherm het stuurt). */
    private static int index(RecipeManager recipes, String id) {
        var all = GunsmithRecipe.all(recipes);
        ResourceLocation name = ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, id);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(name)) {
                return i;
            }
        }
        throw new GameTestAssertException("Er is geen recept " + name + " op de Gunsmith Table");
    }

    private static int count(Inventory inventory, Item item) {
        int found = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                found += inventory.getItem(slot).getCount();
            }
        }
        return found;
    }

    /**
     * De knop Make (zoals het scherm hem stuurt): een stalen slede kost vijf staalstaven, dus met zes lukt het één keer
     * en blijft er één over; een tweede keer lukt niet en neemt niets. Een netherite emitter neemt de meest versleten
     * titanium emitter (en de Netherite Ingot uit je tweede hand telt mee), en is zelf nieuw. De Laser Workbench maakt
     * niets (jouw keuze: alles op de Gunsmith Table).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "gunsmithing")
    public static void gunsmith_table_makes_parts_from_your_inventory(GameTestHelper helper) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 4), "parts-maker");
        player.getAbilities().instabuild = false;
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        RecipeManager recipes = helper.getLevel().getRecipeManager();
        BlockPos at = new BlockPos(2, 2, 2);
        helper.setBlock(at, ModBlocks.GUNSMITH_TABLE.get());
        GunsmithTableBlockEntity table = (GunsmithTableBlockEntity) helper.getBlockEntity(at);
        GunsmithMenu menu = (GunsmithMenu) table.createMenu(1, inventory, player);

        // Een stalen slede: 5 staal.
        inventory.setItem(3, new ItemStack(ModItems.STEEL_INGOT.get(), 6));
        int slide = index(recipes, "gun_slide_steel");
        check(menu.clickMenuButton(player, GunsmithMenu.MAKE + slide), "Make gaf geen stalen slede (met 6 staal)");
        check(count(inventory, ModItems.STEEL_INGOT.get()) == 1, "Er hoort 1 staal over te blijven, niet "
                + count(inventory, ModItems.STEEL_INGOT.get()));
        boolean made = false;
        for (ItemStack stack : inventory.items) {
            made |= ItemStack.isSameItemSameComponents(stack, GunPartItem.of(GunPart.SLIDE, GunMaterial.STEEL));
        }
        check(made, "De stalen slede zit niet in je inventaris");
        check(!menu.clickMenuButton(player, GunsmithMenu.MAKE + slide), "Make lukte met maar 1 staal");
        check(count(inventory, ModItems.STEEL_INGOT.get()) == 1, "Een mislukte Make nam toch staal");

        // Netherite: de versleten emitter (30%) gaat weg, de bijna nieuwe (90%) blijft.
        inventory.setItem(5, LaserPartItem.of(GunType.RIFLE, LaserPart.EMITTER, GunMaterial.TITANIUM, 90));
        inventory.setItem(6, LaserPartItem.of(GunType.RIFLE, LaserPart.EMITTER, GunMaterial.TITANIUM, 30));
        inventory.setItem(7, new ItemStack(Items.NETHERITE_INGOT));
        inventory.offhand.set(0, new ItemStack(Items.NETHERITE_INGOT));
        int netherite = index(recipes, "laser_rifle_emitter_netherite");
        check(menu.clickMenuButton(player, GunsmithMenu.MAKE + netherite), "Make gaf geen netherite emitter");
        check(LaserPartItem.conditionOf(inventory.getItem(5)) == 90 && inventory.getItem(6).isEmpty(),
                "Niet de meest versleten emitter ging weg: " + inventory.getItem(5) + " / " + inventory.getItem(6));
        check(inventory.getItem(7).isEmpty() && inventory.offhand.get(0).isEmpty(), "De twee Netherite Ingots zijn niet weg");
        ItemStack upgraded = ItemStack.EMPTY;
        for (ItemStack stack : inventory.items) {
            if (LaserPartItem.partOf(stack) == LaserPart.EMITTER && LaserPartItem.materialOf(stack) == GunMaterial.NETHERITE) {
                upgraded = stack;
            }
        }
        check(!upgraded.isEmpty() && LaserPartItem.gunOf(upgraded) == GunType.RIFLE && LaserPartItem.conditionOf(upgraded) == 100,
                "Er is geen nieuwe netherite rifle emitter: " + upgraded);

        // De Laser Workbench maakt niets.
        BlockPos benchAt = new BlockPos(4, 2, 2);
        helper.setBlock(benchAt, ModBlocks.LASER_WORKBENCH.get());
        LaserWorkbenchBlockEntity bench = (LaserWorkbenchBlockEntity) helper.getBlockEntity(benchAt);
        GunsmithMenu laserMenu = (GunsmithMenu) bench.createMenu(2, inventory, player);
        inventory.setItem(3, new ItemStack(ModItems.STEEL_INGOT.get(), 6));
        check(!laserMenu.clickMenuButton(player, GunsmithMenu.MAKE + slide), "De Laser Workbench maakte een onderdeel");
        check(count(inventory, ModItems.STEEL_INGOT.get()) == 6, "De Laser Workbench nam staal");
        helper.succeed();
    }
}
