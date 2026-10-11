package com.senne.starktech.station;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.energy.EnergySource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * De binnenkant van een lader: een zonnepaneel of een warmtegenerator.
 *
 * Allebei werken ze hetzelfde: ze maken energie en stoppen die in de bril die je erin legt.
 * Het verschil zit in wáár die energie vandaan komt, en dat regelt {@link #produce}.
 *
 * Wat ze opwekken kan ook het stroomnet op: apparaten die via Stark-kabels (of er direct
 * tegenaan) verbonden zijn, halen het hier weg (zie EnergySource en StarkGrid).
 *
 * Twee slots: 0 is de bril, 1 is brandstof (alleen de warmtegenerator doet daar iets mee).
 */
public abstract class ChargerBlockEntity extends BaseContainerBlockEntity implements EnergySource {
    public static final int SLOT_GLASSES = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_COUNT = 2;

    /** Nummers voor de gegevens die naar het scherm gestuurd worden. */
    public static final int DATA_ENERGY = 0;
    /** Hoeveel het blok op dit moment oplevert (per tick), zodat het scherm dat kan tonen. */
    public static final int DATA_RATE = 1;
    /**
     * Hoeveel dit blok in totaal heeft opgewekt, in twee helften.
     *
     * Een scherm-getal past maar in 16 bits (tot 32767), en een totaal loopt veel hoger op.
     * Daarom sturen we het in twee stukken en plakt het menu ze weer aan elkaar.
     */
    public static final int DATA_TOTAL_LOW = 2;
    public static final int DATA_TOTAL_HIGH = 3;
    public static final int DATA_COUNT = 4;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int energy;
    /** Wat we deze tick opwekten; puur om in het scherm te laten zien. */
    private int rate;
    /** Alles wat dit blok ooit heeft opgewekt (voor het tekstje bij de balk). */
    private int totalProduced;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy;
                case DATA_RATE -> rate;
                case DATA_TOTAL_LOW -> totalProduced & 0xFFFF;
                case DATA_TOTAL_HIGH -> (totalProduced >>> 16) & 0xFFFF;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_ENERGY -> energy = value;
                case DATA_RATE -> rate = value;
                case DATA_TOTAL_LOW -> totalProduced = (totalProduced & ~0xFFFF) | (value & 0xFFFF);
                case DATA_TOTAL_HIGH -> totalProduced = (totalProduced & 0xFFFF) | ((value & 0xFFFF) << 16);
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    protected ChargerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Hoeveel energie dit blok deze tick opwekt.
     * Het zonnepaneel kijkt naar de zon, de warmtegenerator naar zijn brandstof en de hitte.
     */
    protected abstract int produce(ServerLevel level, BlockPos pos);

    /** Hoeveel dit blok kan opslaan. */
    public abstract int capacity();

    @Override
    public int energy() {
        return energy;
    }

    /** Het stroomnet haalt hier stroom weg (zie StarkGrid). */
    @Override
    public int drain(int max) {
        int taken = Math.min(max, energy);
        if (taken > 0) {
            energy -= taken;
            setChanged();
        }
        return taken;
    }

    /** Stroom erbij zetten zonder dat hij die zelf opwekt (voor de tests). */
    public void fill(int amount) {
        energy = Math.min(capacity(), energy + amount);
        setChanged();
    }

    /** Alles wat dit blok ooit heeft opgewekt. */
    public int totalProduced() {
        return totalProduced;
    }

    // ---------------------------------------------------------------- Elke tick (alleen server)

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        int made = Math.min(produce(level, pos), capacity() - energy);
        if (made > 0) {
            energy += made;
            // Bij een paar miljard houdt het op; dat haalt niemand, maar overlopen is lelijk.
            totalProduced = (int) Math.min(Integer.MAX_VALUE, (long) totalProduced + made);
        }
        rate = made;
        boolean changed = made > 0;
        changed |= chargeGlasses();
        if (changed) {
            setChanged();
        }
    }

    /** Stuurt energie uit de buffer naar de bril in het slot. */
    private boolean chargeGlasses() {
        ItemStack glasses = items.get(SLOT_GLASSES);
        if (energy <= 0 || !EdithData.isGlasses(glasses)) {
            return false;
        }
        int added = EdithData.addEnergy(glasses, Math.min(StarkConfig.Power.STATION_CHARGE_RATE, energy));
        energy -= added;
        return added > 0;
    }

    // ---------------------------------------------------------------- Opslag

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> newItems) {
        items = newItems;
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        energy = tag.getInt("Energy");
        totalProduced = tag.getInt("TotalProduced");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Energy", energy);
        tag.putInt("TotalProduced", totalProduced);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new ChargerMenu(containerId, inventory, this, data, usesFuel());
    }

    /** Heeft dit blok iets aan brandstof? (Het zonnepaneel niet.) */
    public abstract boolean usesFuel();

    @Override
    protected abstract Component getDefaultName();
}
