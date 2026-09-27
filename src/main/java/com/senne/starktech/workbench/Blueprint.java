package com.senne.starktech.workbench;

import com.senne.starktech.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.Tags;

/**
 * Wat je aan de harnas-werkbank kunt bouwen, en wat het kost (dezelfde materialen als vroeger
 * in de crafting table). De materialen komen uit je rugzak: je hotbar, de rest van je
 * inventaris en je tweede hand (niet uit wat je aanhebt).
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

    /** Hoeveel heb je van dit materiaal bij je? */
    public static int count(Inventory inventory, Cost cost) {
        int found = 0;
        for (ItemStack stack : carried(inventory)) {
            if (cost.ingredient().test(stack)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /** Heb je alles bij je om dit stuk te bouwen? */
    public boolean affordable(Inventory inventory) {
        for (Cost cost : costs) {
            if (count(inventory, cost) < cost.count()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Haalt de materialen uit je rugzak (kijk eerst met affordable!). Geeft terug wat eruit ging,
     * zodat de werkbank het terug kan geven als hij kapot gaat voordat het stuk klaar is.
     */
    public List<ItemStack> take(Inventory inventory) {
        List<ItemStack> taken = new ArrayList<>();
        for (Cost cost : costs) {
            int left = cost.count();
            for (ItemStack stack : carried(inventory)) {
                if (left <= 0) {
                    break;
                }
                if (cost.ingredient().test(stack)) {
                    int take = Math.min(left, stack.getCount());
                    taken.add(stack.copyWithCount(take));
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
        inventory.setChanged();
        return taken;
    }

    /** De vakjes waar materiaal uit mag komen: de rugzak en de tweede hand. */
    private static List<ItemStack> carried(Inventory inventory) {
        List<ItemStack> all = new ArrayList<>(inventory.items);
        all.addAll(inventory.offhand);
        return all;
    }

    public static Blueprint byId(int id) {
        Blueprint[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }
}
