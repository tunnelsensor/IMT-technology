package com.senne.imttech.guns;

import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * De brug naar Curios (hoofdstuk 52, stap 2, jouw keuze: "zet dat in een curios slot of als je die mod niet hebt in
 * een slot dat mijn mod toevoegt"). Zit Curios in het modpack, dan draag je de holster in het slot "holster", de
 * pouches in de twee slots "pouch", en (hoofdstuk 55) je geweren met een sling in de twee slots "sling" (zie
 * data/imttech/curios/), en (hoofdstuk 72) je rugzak in het slot "back", en (hoofdstuk 100) je keycard in het slot
 * "necklace" (het gewone halskettingslot van Curios), en (hoofdstuk 109) je vest in het slot "body". Deze klasse wordt ALLEEN aangeraakt als Curios geladen is (zie Gear.curios()); zonder Curios
 * bestaan de klassen hierboven niet.
 */
final class CuriosGear {
    private CuriosGear() {}

    private static Optional<ICurioStacksHandler> handler(Player player, String slot) {
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(player);
        return inventory.flatMap(inv -> inv.getStacksHandler(slot));
    }

    private static Optional<ICurioStacksHandler> handler(Player player, GearItem.Kind kind) {
        return handler(player, kind == GearItem.Kind.HOLSTER ? "holster" : "pouch");
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

    // ================================================================ De slings (hoofdstuk 55)

    /** Het geweer in dit vakje van het slot "sling" (een kopie), of leeg. */
    static ItemStack sling(Player player, int index) {
        return handler(player, "sling").map(handler -> index < handler.getStacks().getSlots()
                ? handler.getStacks().getStackInSlot(index).copy() : ItemStack.EMPTY).orElse(ItemStack.EMPTY);
    }

    /** Heeft deze speler zo'n vakje? */
    static boolean hasSlingSlot(Player player, int index) {
        return handler(player, "sling").map(handler -> index < handler.getStacks().getSlots()).orElse(false);
    }

    // ================================================================ De rugzak (hoofdstuk 72)

    /** De rugzak in het slot "back" (een kopie), of leeg. */
    static ItemStack back(Player player) {
        return handler(player, "back").map(handler -> handler.getStacks().getSlots() > 0
                ? handler.getStacks().getStackInSlot(0).copy() : ItemStack.EMPTY).orElse(ItemStack.EMPTY);
    }

    static boolean setBack(Player player, ItemStack stack) {
        return handler(player, "back").map(handler -> {
            if (handler.getStacks().getSlots() == 0) {
                return false;
            }
            handler.getStacks().setStackInSlot(0, stack);
            return true;
        }).orElse(false);
    }

    // ================================================================ De keycard aan je nek (hoofdstuk 100)

    /** De keycard in het slot "necklace" van Curios (een kopie), of leeg. */
    static ItemStack neck(Player player) {
        return handler(player, "necklace").map(handler -> handler.getStacks().getSlots() > 0
                ? handler.getStacks().getStackInSlot(0).copy() : ItemStack.EMPTY).orElse(ItemStack.EMPTY);
    }

    static boolean setNeck(Player player, ItemStack stack) {
        return handler(player, "necklace").map(handler -> {
            if (handler.getStacks().getSlots() == 0) {
                return false;
            }
            handler.getStacks().setStackInSlot(0, stack);
            return true;
        }).orElse(false);
    }

    // ================================================================ Het vest (hoofdstuk 109)

    /** De Plate Carrier in het slot "body" van Curios (een kopie), of leeg. */
    static ItemStack vest(Player player) {
        return handler(player, "body").map(handler -> handler.getStacks().getSlots() > 0
                ? handler.getStacks().getStackInSlot(0).copy() : ItemStack.EMPTY).orElse(ItemStack.EMPTY);
    }

    static boolean setVest(Player player, ItemStack stack) {
        return handler(player, "body").map(handler -> {
            if (handler.getStacks().getSlots() == 0) {
                return false;
            }
            handler.getStacks().setStackInSlot(0, stack);
            return true;
        }).orElse(false);
    }

    static boolean setSling(Player player, int index, ItemStack stack) {
        return handler(player, "sling").map(handler -> {
            if (index >= handler.getStacks().getSlots()) {
                return false;
            }
            handler.getStacks().setStackInSlot(index, stack);
            return true;
        }).orElse(false);
    }
}
