package com.senne.starktech.workbench;

import com.senne.starktech.registry.ModItems;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.Tags;

/**
 * Wat je aan de harnas-werkbank kunt bouwen, en wat het kost (dezelfde materialen als vroeger
 * in de crafting table).
 */
public enum Blueprint {
    CHESTPLATE(() -> ModItems.STARK_CHESTPLATE.get(), List.of(
            new Cost(Ingredient.of(Tags.Items.INGOTS_NETHERITE), 5),
            new Cost(Ingredient.of(Items.AMETHYST_SHARD), 1),
            new Cost(Ingredient.of(ModItems.STARK_CHIP.get()), 2))),
    LEGGINGS(() -> ModItems.STARK_LEGGINGS.get(), List.of(
            new Cost(Ingredient.of(Tags.Items.INGOTS_NETHERITE), 4),
            new Cost(Ingredient.of(Tags.Items.INGOTS_IRON), 2),
            new Cost(Ingredient.of(ModItems.STARK_CHIP.get()), 1))),
    BOOTS(() -> ModItems.STARK_BOOTS.get(), List.of(
            new Cost(Ingredient.of(Tags.Items.INGOTS_NETHERITE), 2),
            new Cost(Ingredient.of(Tags.Items.DUSTS_REDSTONE), 1),
            new Cost(Ingredient.of(ModItems.STARK_CHIP.get()), 2)));

    /** Zoveel van dit soort spul is er nodig. */
    public record Cost(Ingredient ingredient, int count) {}

    private final Supplier<Item> result;
    private final List<Cost> costs;

    Blueprint(Supplier<Item> result, List<Cost> costs) {
        this.result = result;
        this.costs = costs;
    }

    public Item result() {
        return result.get();
    }

    public List<Cost> costs() {
        return costs;
    }

    public static Blueprint byId(int id) {
        Blueprint[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }
}
