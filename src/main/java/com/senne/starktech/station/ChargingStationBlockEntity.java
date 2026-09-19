package com.senne.starktech.station;

import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.UpgradeItem;
import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * De "binnenkant" van het laadstation: 3 slots, een energie-opslag en een upgrade-teller.
 *
 * Slot 0 = de bril, slot 1 = brandstof (redstone), slot 2 = een upgrade.
 */
public class ChargingStationBlockEntity extends BaseContainerBlockEntity {
    public static final int SLOT_GLASSES = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_UPGRADE = 2;
    public static final int SLOT_COUNT = 3;

    /** Nummers voor de gegevens die naar het scherm gestuurd worden. */
    public static final int DATA_ENERGY = 0;
    public static final int DATA_UPGRADE_PROGRESS = 1;
    public static final int DATA_COUNT = 2;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int energy;
    private int upgradeProgress;

    /** Stuurt energie en upgrade-voortgang naar het scherm van spelers die het station open hebben. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy;
                case DATA_UPGRADE_PROGRESS -> upgradeProgress;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_ENERGY -> energy = value;
                case DATA_UPGRADE_PROGRESS -> upgradeProgress = value;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ChargingStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHARGING_STATION.get(), pos, state);
    }

    // ---------------------------------------------------------------- Elke tick (alleen op de server)

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean changed = burnFuel();
        changed |= chargeGlasses();
        changed |= tickUpgrade(level, pos);
        if (changed) {
            setChanged();
        }
    }

    /** Stopt redstone uit het brandstof-slot in de energie-opslag, als daar plek voor is. */
    private boolean burnFuel() {
        ItemStack fuel = items.get(SLOT_FUEL);
        int value = getFuelValue(fuel);
        if (value > 0 && energy + value <= EdithBalance.STATION_CAPACITY) {
            fuel.shrink(1);
            energy += value;
            return true;
        }
        return false;
    }

    /** Stuurt energie van het station naar de bril. */
    private boolean chargeGlasses() {
        ItemStack glasses = items.get(SLOT_GLASSES);
        if (energy <= 0 || !EdithData.isGlasses(glasses)) {
            return false;
        }
        int added = EdithData.addEnergy(glasses, Math.min(EdithBalance.STATION_CHARGE_RATE, energy));
        energy -= added;
        return added > 0;
    }

    /** Bouwt langzaam de upgrade in de bril in. */
    private boolean tickUpgrade(ServerLevel level, BlockPos pos) {
        ItemStack glasses = items.get(SLOT_GLASSES);
        ItemStack upgrade = items.get(SLOT_UPGRADE);
        boolean canUpgrade = EdithData.isGlasses(glasses)
                && upgrade.getItem() instanceof UpgradeItem upgradeItem
                && upgradeItem.canApplyTo(glasses);

        if (!canUpgrade) {
            if (upgradeProgress != 0) {
                upgradeProgress = 0;
                return true;
            }
            return false;
        }

        upgradeProgress++;
        if (upgradeProgress >= EdithBalance.UPGRADE_INSTALL_TIME) {
            ((UpgradeItem) upgrade.getItem()).applyTo(glasses);
            upgrade.shrink(1);
            upgradeProgress = 0;
            level.playSound(null, pos, ModSounds.STATION_UPGRADE.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return true;
    }

    public static int getFuelValue(ItemStack stack) {
        if (stack.is(Items.REDSTONE)) {
            return EdithBalance.ENERGY_PER_REDSTONE;
        }
        if (stack.is(Items.REDSTONE_BLOCK)) {
            return EdithBalance.ENERGY_PER_REDSTONE_BLOCK;
        }
        return 0;
    }

    // ---------------------------------------------------------------- Welke items mogen in welk slot

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_GLASSES -> EdithData.isGlasses(stack);
            case SLOT_FUEL -> getFuelValue(stack) > 0;
            case SLOT_UPGRADE -> stack.getItem() instanceof UpgradeItem;
            default -> false;
        };
    }

    // ---------------------------------------------------------------- Opslaan en laden

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        energy = tag.getInt("energy");
        upgradeProgress = tag.getInt("upgrade_progress");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("energy", energy);
        tag.putInt("upgrade_progress", upgradeProgress);
    }

    // ---------------------------------------------------------------- Container-dingen

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.starktech.charging_station");
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new ChargingStationMenu(containerId, inventory, this, data);
    }
}
