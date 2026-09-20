package com.senne.starktech.arsenal;

import com.senne.starktech.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Wat een drone kost, en wat je terugkrijgt als hij wordt neergehaald.
 *
 * Deze lijst hoort gelijk te lopen met het recept in
 * data/starktech/recipe/stark_drone.json. Er is een test die dat controleert, want anders
 * krijg je schroot terug van materialen die er nooit in zaten.
 */
public final class ArsenalCosts {
    private ArsenalCosts() {}

    /** Eén onderdeel van de bouwkosten. */
    public record Part(Item item, int count) {}

    /** Waar een drone uit bestaat (zie het recept). */
    public static final List<Part> BUILD_COST = List.of(
            new Part(ModItems.STARK_CHIP.get(), 1),
            new Part(Items.IRON_INGOT, 5),
            new Part(Items.REDSTONE, 2),
            new Part(Items.AMETHYST_SHARD, 1));

    /** Waarmee je repareert. */
    public static final Item REPAIR_PART = Items.IRON_INGOT;

    public static Component repairPartName() {
        return Component.translatable(REPAIR_PART.getDescriptionId());
    }

    /**
     * Het schroot van een neergehaalde drone: ongeveer WRECK_RECOVERY van de bouwkosten.
     *
     * Van 5 ijzer krijg je er 0,5 terug, en een half ijzerstaafje bestaat niet. Daarom gooien
     * we voor dat halve staafje een dobbelsteen: de helft van de keren krijg je er één, de
     * andere helft niets. Over veel drones klopt het gemiddelde dan precies.
     */
    public static List<ItemStack> wreckage(RandomSource random) {
        List<ItemStack> scrap = new ArrayList<>();
        for (Part part : BUILD_COST) {
            double exact = part.count() * ArsenalBalance.WRECK_RECOVERY;
            int whole = (int) Math.floor(exact);
            if (random.nextDouble() < exact - whole) {
                whole++;
            }
            if (whole > 0) {
                scrap.add(new ItemStack(part.item(), whole));
            }
        }
        return scrap;
    }

    /**
     * Haalt maximaal "wanted" reparatie-onderdelen uit de rugzak van de speler.
     * Geeft terug hoeveel er echt gevonden zijn (kan minder zijn, of 0).
     *
     * Ook in creative moet het ijzer in je rugzak zitten. Dat scheelt een uitzondering, en in
     * creative pak je een stapel ijzer zo uit je inventaris.
     */
    public static int takeRepairParts(Player player, int wanted) {
        if (wanted <= 0) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int taken = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && taken < wanted; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(REPAIR_PART)) {
                continue;
            }
            int take = Math.min(wanted - taken, stack.getCount());
            stack.shrink(take);
            taken += take;
        }
        if (taken > 0) {
            inventory.setChanged();
        }
        return taken;
    }
}
