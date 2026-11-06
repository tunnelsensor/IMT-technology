package com.senne.imttech.guns;

import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * De brug naar Curios (hoofdstuk 52, stap 2, jouw keuze: "zet dat in een curios slot of als je die mod niet hebt in
 * een slot dat mijn mod toevoegt"). Zit Curios in het modpack, dan draag je de holster in het slot "holster" en de
 * pouches in de twee slots "pouch" (zie data/imttech/curios/). Deze klasse wordt ALLEEN aangeraakt als Curios geladen
 * is (zie Gear.curios()); zonder Curios bestaan de klassen hierboven niet.
 */
final class CuriosGear {
    private CuriosGear() {}

    private static Optional<ICurioStacksHandler> handler(Player player, GearItem.Kind kind) {
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(player);
        return inventory.flatMap(inv -> inv.getStacksHandler(kind == GearItem.Kind.HOLSTER ? "holster" : "pouch"));
    }

    /** In welk vakje van het slot dit ding zit, of -1. */
    private static int indexOf(ICurioStacksHandler handler, GearItem.Kind kind) {
        for (int i = 0; i < handler.getStacks().getSlots(); i++) {
            if (GearItem.kindOf(handler.getStacks().getStackInSlot(i)) == kind) {
                return i;
            }
        }
        return -1;
    }

    static ItemStack get(Player player, GearItem.Kind kind) {
        return handler(player, kind).map(handler -> {
            int index = indexOf(handler, kind);
            return index < 0 ? ItemStack.EMPTY : handler.getStacks().getStackInSlot(index).copy();
        }).orElse(ItemStack.EMPTY);
    }

    /** Het gedragen ding vervangen (alleen als het er al zit, of leeg: dan in het eerste vrije vakje). Geeft false als het niet kan. */
    static boolean set(Player player, GearItem.Kind kind, ItemStack stack) {
        return handler(player, kind).map(handler -> {
            int index = indexOf(handler, kind);
            if (index < 0) {
                for (int i = 0; i < handler.getStacks().getSlots() && index < 0; i++) {
                    if (handler.getStacks().getStackInSlot(i).isEmpty()) {
                        index = i;
                    }
                }
            }
            if (index < 0) {
                return false;
            }
            handler.getStacks().setStackInSlot(index, stack);
            return true;
        }).orElse(false);
    }
}
