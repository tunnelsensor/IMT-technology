package com.senne.imttech.storage;

import com.senne.imttech.car.CarWork;
import net.minecraft.world.item.ItemStack;

/**
 * Eén soort voorwerp in het opslagsysteem (hoofdstuk 113): het item met alles wat erop zit. Een betoverd boek is dus een
 * andere soort dan een gewoon boek, en een drankje van genezing een andere dan een drankje van snelheid: zo bestel je
 * precies wat je bedoelt.
 *
 * Minecraft vergelijkt twee stapeltjes zelf niet op "is dit hetzelfde soort" (alleen: is dit hetzelfde stapeltje); deze
 * klasse doet dat wel, zodat je er een lijst per soort mee kunt bijhouden.
 */
public final class ItemKind {
    /** Eén voorwerp van deze soort. Niet veranderen: gebruik {@link #stack(int)} voor een eigen stapeltje. */
    private final ItemStack one;

    private ItemKind(ItemStack one) {
        this.one = one;
    }

    /** De soort van dit stapeltje (het aantal telt niet mee). */
    public static ItemKind of(ItemStack stack) {
        return new ItemKind(stack.copyWithCount(1));
    }

    /** Een nieuw stapeltje van deze soort, van zoveel stuks. */
    public ItemStack stack(int count) {
        return one.copyWithCount(count);
    }

    /** Is dit stapeltje van deze soort? */
    public boolean is(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(one, stack);
    }

    /** De naam zoals de taal van de RC Car hem schrijft: "iron_ingot", "imttech:polymer". */
    public String code() {
        return CarWork.itemName(one);
    }

    /** De naam zoals je hem in het spel leest: "Iron Ingot". */
    public String title() {
        return one.getHoverName().getString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemKind kind && ItemStack.isSameItemSameComponents(one, kind.one);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(one);
    }

    @Override
    public String toString() {
        return code();
    }
}
