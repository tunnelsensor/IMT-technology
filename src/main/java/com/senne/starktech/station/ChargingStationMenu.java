package com.senne.starktech.station;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.UpgradeItem;
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
 * Het menu van het laadstation: welke slots er zijn en hoe shift-klikken werkt.
 * Dit bestaat zowel op de server als op de client; ChargingStationScreen tekent het.
 */
public class ChargingStationMenu extends AbstractContainerMenu {
    // Posities van de slots op het scherm (in pixels, vanaf de linkerbovenhoek van de GUI).
    public static final int GLASSES_X = 80, GLASSES_Y = 33;
    public static final int FUEL_X = 26, FUEL_Y = 53;
    public static final int UPGRADE_X = 134, UPGRADE_Y = 33;

    private final Container container;
    private final ContainerData data;

    /** Wordt op de client gebruikt: de echte inhoud komt later van de server. */
    public ChargingStationMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(ChargingStationBlockEntity.SLOT_COUNT),
                new SimpleContainerData(ChargingStationBlockEntity.DATA_COUNT));
    }

    public ChargingStationMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModMenus.CHARGING_STATION.get(), containerId);
        checkContainerSize(container, ChargingStationBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, ChargingStationBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        container.startOpen(inventory.player);

        addSlot(new FilteredSlot(container, ChargingStationBlockEntity.SLOT_GLASSES, GLASSES_X, GLASSES_Y, 1));
        addSlot(new FilteredSlot(container, ChargingStationBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y, 64));
        addSlot(new FilteredSlot(container, ChargingStationBlockEntity.SLOT_UPGRADE, UPGRADE_X, UPGRADE_Y, 64));
        // De inventaris van de speler: drie rijen van negen, en daaronder de hotbar.
        // (In 1.21.1 moet je die zelf toevoegen.)
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

    /** Een slot dat alleen de juiste items accepteert (volgens canPlaceItem van het station). */
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

    public int getStationEnergy() {
        return data.get(ChargingStationBlockEntity.DATA_ENERGY);
    }

    public float getStationEnergyFraction() {
        return (float) getStationEnergy() / StarkConfig.Power.STATION_CAPACITY;
    }

    public float getUpgradeProgress() {
        return (float) data.get(ChargingStationBlockEntity.DATA_UPGRADE_PROGRESS) / Math.round(StarkConfig.Glasses.UPGRADE_INSTALL_TIME);
    }

    public ItemStack getGlasses() {
        return container.getItem(ChargingStationBlockEntity.SLOT_GLASSES);
    }

    // ---------------------------------------------------------------- Shift-klikken

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int stationSlots = ChargingStationBlockEntity.SLOT_COUNT;

        if (slotIndex < stationSlots) {
            // Van het station naar de speler.
            if (!moveItemStackTo(stack, stationSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Van de speler naar het juiste slot in het station.
            int target = -1;
            if (EdithData.isGlasses(stack)) {
                target = ChargingStationBlockEntity.SLOT_GLASSES;
            } else if (ChargingStationBlockEntity.getFuelValue(stack) > 0) {
                target = ChargingStationBlockEntity.SLOT_FUEL;
            } else if (stack.getItem() instanceof UpgradeItem) {
                target = ChargingStationBlockEntity.SLOT_UPGRADE;
            }
            if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) {
                return ItemStack.EMPTY;
            }
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
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
