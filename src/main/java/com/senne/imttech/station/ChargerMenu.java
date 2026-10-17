package com.senne.imttech.station;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModMenus;
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
 * Het menu van een lader (zonnepaneel of warmtegenerator): welke slots er zijn.
 *
 * Een slot voor de bril die opgeladen wordt, en alleen bij de warmtegenerator een slot voor
 * brandstof. Het zonnepaneel heeft dat niet nodig en krijgt het dus ook niet.
 *
 * Welk van de twee het is, stuurt de server mee als het scherm opengaat (zie ChargerBlock):
 * anders zou jouw spel het niet weten, want ze delen dit menu.
 */
public class ChargerMenu extends AbstractContainerMenu {
    public static final int GLASSES_X = 80, GLASSES_Y = 24;
    public static final int FUEL_X = 80, FUEL_Y = 52;

    private final Container container;
    private final ContainerData data;
    private final boolean usesFuel;

    /**
     * Wordt op de client gebruikt: de echte inhoud komt van de server. Of het een
     * warmtegenerator is, staat in {@code extra} (door de server meegestuurd).
     */
    public ChargerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, new SimpleContainer(ChargerBlockEntity.SLOT_COUNT),
                new SimpleContainerData(ChargerBlockEntity.DATA_COUNT), extra.readBoolean());
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
        if (usesFuel) {
            addSlot(new FilteredSlot(container, ChargerBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y, 64));
        }
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

    /** Hoeveel het blok op dit moment oplevert (per tick, kan 0.3 zijn). 0 = hij doet even niets. */
    public float getRate() {
        return (data.get(ChargerBlockEntity.DATA_RATE) & 0xFFFF) / 100.0f; // verstuurd in honderdsten
    }

    /** Alles wat dit blok ooit heeft opgewekt (in twee helften verstuurd; zie DATA_TOTAL_LOW). */
    public int getTotalProduced() {
        return (data.get(ChargerBlockEntity.DATA_TOTAL_LOW) & 0xFFFF)
                | ((data.get(ChargerBlockEntity.DATA_TOTAL_HIGH) & 0xFFFF) << 16);
    }

    // ---------------------------------------------------------------- Het hele net (overzicht naast het scherm)

    /** Een getal dat in twee helften kwam (zie ChargerBlockEntity.DATA_GRID_...). */
    private int pair(int low) {
        return (data.get(low) & 0xFFFF) | ((data.get(low + 1) & 0xFFFF) << 16);
    }

    /** Wat alle generatoren op dit net samen per tick maken (of kunnen maken). */
    public float gridMade() {
        return pair(ChargerBlockEntity.DATA_GRID_MADE) / 100.0f;
    }

    /** Wat het net per tick gebruikt. */
    public float gridUsed() {
        return pair(ChargerBlockEntity.DATA_GRID_USED) / 100.0f;
    }

    /** Wat apparaten op het net per tick tekortkomen (0 = niemand). */
    public float gridMissed() {
        return pair(ChargerBlockEntity.DATA_GRID_MISSED) / 100.0f;
    }

    /** Wat er in alle generatoren samen zit. */
    public int gridStored() {
        return pair(ChargerBlockEntity.DATA_GRID_STORED);
    }

    /** Wat er in alle generatoren samen past. */
    public int gridCapacity() {
        return pair(ChargerBlockEntity.DATA_GRID_CAPACITY);
    }

    /** Hoeveel generatoren er op het net hangen; 0 = nog aan het meten. */
    public int gridSources() {
        return data.get(ChargerBlockEntity.DATA_GRID_SOURCES);
    }

    /** Hoeveel dit apparaat kan opslaan. */
    public int getCapacity() {
        return (int) (usesFuel ? ImtConfig.Power.HEAT_CAPACITY : ImtConfig.Power.SOLAR_CAPACITY);
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
        // De slots van het apparaat komen eerst (1 of 2), daarna die van jou.
        int inventoryStart = usesFuel ? 2 : 1;
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
