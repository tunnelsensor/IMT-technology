package com.senne.imttech.robot;

import com.senne.imttech.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Het menu van de Robot Assembler (hoofdstuk 21): links de vier onderdelen onder elkaar, rechts de
 * printplaat en de robot, en twee knoppen (Assemble en Take apart). De knoppen gaan als "knop van een menu"
 * naar de server (zoals bij een betafel), dus daar is geen eigen netwerkbericht voor nodig.
 */
public class RobotAssemblerMenu extends AbstractContainerMenu {
    /** Waar de onderdelen staan: linksboven en dan elke PART_STEP lager. */
    public static final int PART_X = 8, PART_Y = 18, PART_STEP = 18;
    public static final int BOARD_X = 108, BOARD_Y = 24;
    public static final int ROBOT_X = 150, ROBOT_Y = 24;
    /** Hoeveel hoger het scherm is dan de andere schermen van de mod, en dus hoeveel lager je rugzak staat. */
    public static final int EXTRA_HEIGHT = 20;
    public static final int INVENTORY_Y = 84 + EXTRA_HEIGHT;

    public static final int BUTTON_ASSEMBLE = 0;
    public static final int BUTTON_TAKE_APART = 1;

    private final Container items;
    /** Op de server de assembler zelf; in je spel null (daar is alleen een kopie van de vakjes). */
    private final @Nullable RobotAssemblerBlockEntity assembler;

    /** In jouw spel: de inhoud komt van de server. */
    public RobotAssemblerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, new SimpleContainer(RobotAssemblerBlockEntity.SIZE), null);
    }

    public RobotAssemblerMenu(int containerId, Inventory inventory, Container items,
                              @Nullable RobotAssemblerBlockEntity assembler) {
        super(ModMenus.ROBOT_ASSEMBLER.get(), containerId);
        checkContainerSize(items, RobotAssemblerBlockEntity.SIZE);
        this.items = items;
        this.assembler = assembler;
        items.startOpen(inventory.player);
        for (int i = 0; i < RobotAssemblerBlockEntity.SIZE; i++) {
            addSlot(new AssemblerSlot(items, i, slotX(i), slotY(i)));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }
    }

    public static int slotX(int i) {
        return i < RobotAssemblerBlockEntity.BOARD ? PART_X : i == RobotAssemblerBlockEntity.BOARD ? BOARD_X : ROBOT_X;
    }

    public static int slotY(int i) {
        return i < RobotAssemblerBlockEntity.BOARD ? PART_Y + i * PART_STEP
                : i == RobotAssemblerBlockEntity.BOARD ? BOARD_Y : ROBOT_Y;
    }

    /** De vakjes van de assembler (in je spel een kopie), voor het scherm. */
    public Container items() {
        return items;
    }

    /** Een knop van het scherm: Assemble of Take apart (alleen de server doet echt iets). */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (assembler == null) {
            return false;
        }
        return switch (id) {
            case BUTTON_ASSEMBLE -> assembler.assemble();
            case BUTTON_TAKE_APART -> assembler.takeApart();
            default -> false;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int size = RobotAssemblerBlockEntity.SIZE;
        if (index < size) {
            // Uit de assembler naar je rugzak.
            if (!moveItemStackTo(stack, size, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Uit je rugzak naar het vakje waar het past.
            int target = -1;
            for (int i = 0; i < size && target < 0; i++) {
                if (RobotAssemblerBlockEntity.fits(i, stack)) {
                    target = i;
                }
            }
            if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return items.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        items.stopOpen(player);
    }

    /** Een vakje van de assembler: alleen wat erin past; de printplaat en de robot één tegelijk. */
    private static class AssemblerSlot extends Slot {
        AssemblerSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return RobotAssemblerBlockEntity.fits(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return getContainerSlot() < RobotAssemblerBlockEntity.BOARD ? 16 : 1;
        }
    }
}
