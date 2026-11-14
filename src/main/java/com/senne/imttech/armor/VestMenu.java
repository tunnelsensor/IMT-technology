package com.senne.imttech.armor;

import com.senne.imttech.guns.Gear;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Het scherm van een Plate Carrier (hoofdstuk 109): links de plate voor en de plate achter, rechts de drie magazijnvakjes en
 * de tekst op je rug met zijn kleur, in het midden het vest zelf (van achteren, met je tekst), eronder je inventaris. Je
 * opent het met een rechtsklik op het vest in je hand, of gedragen uit het wiel (linker Alt).
 *
 * De vakjes lezen en schrijven <b>altijd het vest zelf</b> (gedragen via Gear, in je hand via het item), niet een kopie van
 * toen het scherm openging: word je beschoten terwijl het open staat, dan zie je je plate slijten en zet een klik hem niet
 * per ongeluk terug. Het vakje van je werkbalk met het open vest erin zit op slot zolang het scherm open is.
 */
public class VestMenu extends AbstractContainerMenu {
    /** Waar het open vest zit: gedragen, of in je hoofdhand of andere hand. */
    public static final int WORN = -1;
    public static final int MAIN_HAND = 0;
    public static final int OFF_HAND = 1;

    /** De vakjes van het vest: 0 de plate voor, 1 de plate achter, 2 tot 4 de magazijnen. */
    public static final int FRONT = 0;
    public static final int BACK = 1;
    public static final int MAGS = 2;
    public static final int SIZE = MAGS + VestData.MAGS;

    /** Waar alles staat (pixels in het scherm, de linkerbovenhoek van het item in een vakje). */
    public static final int PLATE_X = 12;
    public static final int FRONT_Y = 29;
    public static final int BACK_Y = 72;
    public static final int MAGS_X = 106;
    public static final int MAGS_Y = 29;
    /** De inventaris: lager dan bij een pouch, zodat de namen, de tekst en de kleuren erboven niet over elkaar lopen. */
    public static final int INVENTORY_Y = 116;
    public static final int HEIGHT = INVENTORY_Y + 82;

    private final int where;
    private final Player player;

    /** Op de client: waar het vest zit komt van de server mee (zonder: gedragen). */
    public VestMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, extra == null || extra.readableBytes() == 0 ? WORN : extra.readVarInt());
    }

    public VestMenu(int containerId, Inventory inventory, int where) {
        super(ArmorRegistry.VEST_MENU.get(), containerId);
        this.where = where;
        this.player = inventory.player;
        // Op de client vult de server de vakjes (een gewone doos); op de server lezen en schrijven ze het vest zelf.
        Container vest = player.level().isClientSide ? new SimpleContainer(SIZE) : new LiveVest();
        addSlot(new PlateSlot(vest, FRONT, PLATE_X, FRONT_Y));
        addSlot(new PlateSlot(vest, BACK, PLATE_X, BACK_Y));
        for (int i = 0; i < VestData.MAGS; i++) {
            addSlot(new MagSlot(vest, MAGS + i, MAGS_X + i * 18, MAGS_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new HotbarSlot(inventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }
    }

    public int where() {
        return where;
    }

    /** Het vest dat open is (gedragen of in je hand; een kopie als het gedragen is), of leeg als het weg is. */
    public ItemStack vest() {
        if (where == WORN) {
            return Gear.vest(player);
        }
        ItemStack held = player.getItemInHand(where == MAIN_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        return VestItem.isVest(held) ? held : ItemStack.EMPTY;
    }

    /** Wat er nu in het open vest zit. */
    public VestData data() {
        return VestItem.data(vest());
    }

    /** Het open vest met deze inhoud (alleen op de server; vanuit de vakjes en het tekstbericht). */
    private void write(VestData data) {
        ItemStack vest = vest();
        if (!VestItem.isVest(vest) || data.equals(VestItem.data(vest))) {
            return;
        }
        if (where == WORN) {
            Gear.setVest(player, VestItem.with(vest, data));
        } else {
            VestItem.setData(vest, data);
        }
    }

    /** Van de server, uit VestTextPayload: de tekst op je rug en zijn kleur. */
    public void setText(String text, int colour) {
        if (!player.level().isClientSide) {
            write(data().withText(text, colour));
        }
    }

    /** De vakjes op de server: elke lees- en schrijfbeurt gaat rechtstreeks naar het vest. */
    private final class LiveVest implements Container {
        @Override
        public int getContainerSize() {
            return SIZE;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < SIZE; i++) {
                if (!getItem(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int index) {
            VestData data = data();
            return switch (index) {
                case FRONT -> data.plate(true);
                case BACK -> data.plate(false);
                default -> index >= MAGS && index < SIZE ? data.mags().get(index - MAGS).copy() : ItemStack.EMPTY;
            };
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            ItemStack inside = getItem(index);
            if (inside.isEmpty() || count <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = inside.split(count);
            setItem(index, inside);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            ItemStack inside = getItem(index);
            setItem(index, ItemStack.EMPTY);
            return inside;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            VestData data = data();
            if (index == FRONT || index == BACK) {
                write(data.withPlate(index == FRONT, stack));
            } else if (index >= MAGS && index < SIZE) {
                List<ItemStack> magazines = data.magazines();
                magazines.set(index - MAGS, stack.copy());
                write(data.withMagazines(magazines));
            }
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return VestMenu.this.stillValid(player);
        }

        @Override
        public void clearContent() {
            write(VestData.EMPTY.withText(data().text(), data().textColour()));
        }
    }

    /** Een vakje voor een plate: alleen plates (ook een gebarsten, om hem te kunnen wisselen). */
    private static final class PlateSlot extends Slot {
        PlateSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return PlateItem.tierOf(stack) != null;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Een magazijnvakje: wat in de magazijnpouch past (een magazijn of een energiecel), een per vakje. */
    private static final class MagSlot extends Slot {
        MagSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return Vests.accepts(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Je werkbalk: het vakje met het open vest erin zit op slot. */
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
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SIZE) {
            if (!moveItemStackTo(stack, SIZE, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (PlateItem.tierOf(stack) != null) {
            if (!moveItemStackTo(stack, FRONT, MAGS, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!Vests.accepts(stack) || !moveItemStackTo(stack, MAGS, SIZE, false)) {
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
        return VestItem.isVest(vest());
    }
}
