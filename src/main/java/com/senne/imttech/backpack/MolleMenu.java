package com.senne.imttech.backpack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Het scherm van een open MOLLE-pouch (hoofdstuk 72, stap 2): bovenaan zijn vakjes (2 tot 4), eronder je inventaris, net
 * als bij de holster en de pouches van hoofdstuk 54 (GearPouchMenu). Je opent het zonder rits: uit het wiel als de pouch
 * aan de rugzak op je rug hangt ({@link #WORN}, met de plek), of met rechtsklik op de pouch in je hand.
 *
 * Elke verandering gaat meteen terug naar de pouch: op je rug via Molle.setWorn (ook als de rugzak zelf open is), in je
 * hand via het item. De pouch op je rug heeft een eigen nummer (PACK_ID): haalt iemand hem eraf terwijl jij hem open hebt
 * (een dief van achteren), dan gaat jouw scherm dicht.
 */
public class MolleMenu extends AbstractContainerMenu {
    public static final int WORN = -1;
    public static final int MAIN_HAND = 0;
    public static final int OFF_HAND = 1;
    /** Waar de vakjes staan (de bovenkant), zoals het scherm ze tekent. */
    public static final int SLOT_Y = 34;

    private final MolleItem.Kind kind;
    private final int where;
    /** De MOLLE-plek op je rugzak ({@link #WORN}), anders -1. */
    private final int index;
    private final Player player;
    private final PouchContainer container;
    /** Het nummer van de pouch op je rug toen je hem opende (null in je hand of in jouw spel). */
    private final UUID pouchId;

    /** Op de client: soort, plek en MOLLE-plek komen van de server mee. */
    public MolleMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, MolleItem.Kind.values()[Math.floorMod(extra.readVarInt(), MolleItem.Kind.values().length)],
                extra.readVarInt(), extra.readVarInt());
    }

    public MolleMenu(int containerId, Inventory inventory, MolleItem.Kind kind, int where, int index) {
        super(BackpackRegistry.MOLLE_MENU.get(), containerId);
        this.kind = kind;
        this.where = where;
        this.index = index;
        this.player = inventory.player;
        ItemStack pouch = pouch();
        this.pouchId = player.level().isClientSide ? null : pouch.get(BackpackRegistry.PACK_ID.get());
        List<ItemStack> contents = MolleItem.contents(pouch);
        while (contents.size() < kind.size()) {
            contents.add(ItemStack.EMPTY);
        }
        container = new PouchContainer(contents.toArray(new ItemStack[0]));
        int count = kind.size();
        int x0 = 88 - count * 9;
        for (int i = 0; i < count; i++) {
            addSlot(new PouchSlot(container, i, x0 + i * 18 + 1, SLOT_Y + 1));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new HotbarSlot(inventory, column, 8 + column * 18, 142));
        }
    }

    public MolleItem.Kind kind() {
        return kind;
    }

    /** De pouch die open is (op je rug of in je hand), of leeg als hij weg is. */
    public ItemStack pouch() {
        if (where == WORN) {
            List<ItemStack> pouches = Molle.worn(player);
            return index >= 0 && index < pouches.size() && MolleItem.kindOf(pouches.get(index)) == kind ? pouches.get(index) : ItemStack.EMPTY;
        }
        ItemStack held = player.getItemInHand(where == MAIN_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        return MolleItem.kindOf(held) == kind ? held : ItemStack.EMPTY;
    }

    /** De vakjes: alles wat erin verandert, gaat meteen naar de pouch zelf (alleen op de server). */
    private final class PouchContainer extends SimpleContainer {
        PouchContainer(ItemStack... items) {
            super(items);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (player.level().isClientSide) {
                return;
            }
            List<ItemStack> contents = new ArrayList<>();
            for (int i = 0; i < getContainerSize(); i++) {
                contents.add(getItem(i).copy());
            }
            ItemStack pouch = pouch();
            if (pouch.isEmpty()) {
                return;
            }
            if (where == WORN) {
                ItemStack changed = pouch.copy();
                MolleItem.setContents(changed, contents);
                Molle.setWorn(player, index, changed);
            } else {
                MolleItem.setContents(pouch, contents);
            }
        }
    }

    /** Een vakje van de pouch: alleen wat erin past. */
    private final class PouchSlot extends Slot {
        PouchSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return kind.accepts(stack);
        }
    }

    /** Je werkbalk: het vakje met de open pouch erin zit op slot. */
    private final class HotbarSlot extends Slot {
        HotbarSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        private boolean locked() {
            return where == MAIN_HAND && getContainerSlot() == player.getInventory().selected;
        }

        @Override
        public boolean mayPickup(Player player) {
            return !locked();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !locked();
        }
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
        if (clickType == net.minecraft.world.inventory.ClickType.SWAP && where == MAIN_HAND && button == player.getInventory().selected) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int pouch = kind.size();
        if (slotIndex < pouch) {
            if (!moveItemStackTo(stack, pouch, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!kind.accepts(stack) || !moveItemStackTo(stack, 0, pouch, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        ItemStack pouch = pouch();
        if (pouch.isEmpty()) {
            return false;
        }
        return pouchId == null || pouchId.equals(pouch.get(BackpackRegistry.PACK_ID.get()));
    }
}
