package com.senne.starktech.station;

import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.registry.ModMenus;
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
 * Het menu van een lader (zonnepaneel of warmtegenerator): welke slots er zijn.
 *
 * Links de bril die opgeladen wordt, en bij de warmtegenerator daaronder een slot voor
 * brandstof. Het zonnepaneel heeft dat niet nodig - dat slot zit er dan wel, maar accepteert
 * niets en het scherm tekent er geen vakje.
 */
public class ChargerMenu extends AbstractContainerMenu {
    public static final int GLASSES_X = 80, GLASSES_Y = 25;
    public static final int FUEL_X = 80, FUEL_Y = 53;

    private final Container container;
    private final ContainerData data;
    private final boolean usesFuel;

    /** Wordt op de client gebruikt: de echte inhoud komt van de server. */
    public ChargerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(ChargerBlockEntity.SLOT_COUNT),
                new SimpleContainerData(ChargerBlockEntity.DATA_COUNT), true);
    }

    public ChargerMenu(int containerId, Inventory inventory, Container container, ContainerData data, boolean usesFuel) {
        super(ModMenus.CHARGER.get(), containerId);
        checkContainerSize(container, ChargerBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, ChargerBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        this.usesFuel = usesFuel;
        container.startOpen(inventory.player);

        addSlot(new FilteredSlot(container, ChargerBlockEntity.SLOT_GLASSES, GLASSES_X, GLASSES_Y, 1));
        addSlot(new FilteredSlot(container, ChargerBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y, 64));
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

    private static class FilteredSlot extends Slot {
        private final int maxStackSize;

        FilteredSlot(Container container, int slot, int x, int y, int maxStackSize) {
            super(container, slot, x, y);
            this.maxStackSize = maxStackSize;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return maxStackSize;
        }
    }

    // ---------------------------------------------------------------- Gegevens voor het scherm

    public boolean usesFuel() {
        return usesFuel;
    }

    public int getEnergy() {
        return data.get(ChargerBlockEntity.DATA_ENERGY);
    }

    /** Hoeveel het blok op dit moment oplevert (per tick). 0 = hij doet even niets. */
    public int getRate() {
        return data.get(ChargerBlockEntity.DATA_RATE);
    }

    /** Alles wat dit blok ooit heeft opgewekt (in twee helften verstuurd; zie DATA_TOTAL_LOW). */
    public int getTotalProduced() {
        return (data.get(ChargerBlockEntity.DATA_TOTAL_LOW) & 0xFFFF)
                | ((data.get(ChargerBlockEntity.DATA_TOTAL_HIGH) & 0xFFFF) << 16);
    }

    /** Hoeveel dit apparaat kan opslaan. */
    public int getCapacity() {
        return usesFuel ? EdithBalance.HEAT_CAPACITY : EdithBalance.SOLAR_CAPACITY;
    }

    public ItemStack getGlasses() {
        return container.getItem(ChargerBlockEntity.SLOT_GLASSES);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int inventoryStart = ChargerBlockEntity.SLOT_COUNT;
        if (index < inventoryStart) {
            // Uit het apparaat naar je eigen tassen.
            if (!moveItemStackTo(stack, inventoryStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, inventoryStart, false)) {
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
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
