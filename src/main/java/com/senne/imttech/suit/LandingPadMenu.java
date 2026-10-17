package com.senne.imttech.suit;

import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Het menu van een landingsplatform: één vakje voor batterijcellen (Battery Upgrades), je eigen
 * rugzak, en de getallen van de batterij (zie LandingPadBlockEntity).
 *
 * Getallen gaan als "data slots" naar jouw spel, en die kunnen maar tot 32767: de voorraad en
 * de grootte van de batterij sturen we daarom in twee helften (LOW en HIGH).
 */
public class LandingPadMenu extends AbstractContainerMenu {
    public static final int CELLS_X = 44, CELLS_Y = 47;

    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_CAPACITY_LOW = 2;
    public static final int DATA_CAPACITY_HIGH = 3;
    /** Hoeveel stroom er in het net zit, gedeeld door 10 (anders past het niet). */
    public static final int DATA_GRID = 4;
    /** Wat er de vorige seconde uit het net in de batterij ging. */
    public static final int DATA_FILLED = 5;
    /** Wat je bril er de vorige seconde bij kreeg. */
    public static final int DATA_CHARGED = 6;
    public static final int DATA_COUNT = 7;

    private final Container cells;
    private final ContainerData data;
    private final BlockPos pos;

    /** In jouw spel: de echte inhoud komt van de server; de plek van het platform stuurt hij mee. */
    public LandingPadMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, new SimpleContainer(1), new SimpleContainerData(DATA_COUNT), extra.readBlockPos());
    }

    public LandingPadMenu(int containerId, Inventory inventory, Container cells, ContainerData data) {
        this(containerId, inventory, cells, data, BlockPos.ZERO);
    }

    private LandingPadMenu(int containerId, Inventory inventory, Container cells, ContainerData data, BlockPos pos) {
        super(ModMenus.LANDING_PAD.get(), containerId);
        checkContainerSize(cells, 1);
        checkContainerDataCount(data, DATA_COUNT);
        this.cells = cells;
        this.data = data;
        this.pos = pos;
        cells.startOpen(inventory.player);

        addSlot(new Slot(cells, 0, CELLS_X, CELLS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.BATTERY_UPGRADE.get());
            }

            @Override
            public int getMaxStackSize() {
                return LandingPadBlockEntity.MAX_CELLS;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    // ---------------------------------------------------------------- Gegevens voor het scherm

    public int energy() {
        return (data.get(DATA_ENERGY_LOW) & 0xFFFF) | ((data.get(DATA_ENERGY_HIGH) & 0xFFFF) << 16);
    }

    public int capacity() {
        return (data.get(DATA_CAPACITY_LOW) & 0xFFFF) | ((data.get(DATA_CAPACITY_HIGH) & 0xFFFF) << 16);
    }

    public int grid() {
        return data.get(DATA_GRID) * 10;
    }

    public int filled() {
        return data.get(DATA_FILLED);
    }

    public int charged() {
        return data.get(DATA_CHARGED);
    }

    public ItemStack cellsItem() {
        return cells.getItem(0);
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            // Uit het platform naar je rugzak.
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!stack.is(ModItems.BATTERY_UPGRADE.get()) || !moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
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
        return cells.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        cells.stopOpen(player);
    }
}
