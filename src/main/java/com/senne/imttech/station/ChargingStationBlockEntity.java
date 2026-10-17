package com.senne.imttech.station;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.UpgradeItem;
import com.senne.imttech.energy.EnergyCarry;
import com.senne.imttech.energy.EnergySource;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.registry.ModSounds;
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
 *
 * Stroom komt uit redstone (de noodvoorraad) of van het stroomnet: zonnepanelen en
 * warmtegeneratoren die via stroomkabels (of er direct tegenaan) verbonden zijn.
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
    /** Hoeveel stroom erin zit; een kommagetal, zodat ook 0.5 per redstone werkt. */
    private double energy;
    /** De stukjes van de snelheden in de config die nog geen hele stroom zijn (zie EnergyCarry). */
    private final EnergyCarry gridCarry = new EnergyCarry();
    private final EnergyCarry storeCarry = new EnergyCarry();
    private final EnergyCarry chargeCarry = new EnergyCarry();
    private int upgradeProgress;

    /** Stuurt energie en upgrade-voortgang naar het scherm van spelers die het station open hebben. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> (int) energy;
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
        changed |= pullFromGrid(level, pos);
        changed |= storeSpareFromGrid(level, pos);
        changed |= chargeGlasses();
        changed |= tickUpgrade(level, pos);
        if (changed) {
            setChanged();
        }
    }

    /** Stopt redstone uit het brandstof-slot in de energie-opslag, als daar plek voor is. */
    private boolean burnFuel() {
        ItemStack fuel = items.get(SLOT_FUEL);
        float value = getFuelValue(fuel);
        if (value > 0 && energy + value <= ImtConfig.Power.STATION_CAPACITY) {
            fuel.shrink(1);
            energy += value;
            return true;
        }
        return false;
    }

    /**
     * Haalt stroom uit het net, maar alleen voor een bril die erin ligt en nog niet vol is (en
     * nooit meer dan STATION_GRID_RATE per tick). Anders bleef het station zijn eigen grote
     * opslag vullen en zoog het het net leeg, en had bijvoorbeeld een holotafel op hetzelfde
     * net niets meer. Stroom die in de generatoren blijft, is niet verloren: die wacht daar.
     */
    private boolean pullFromGrid(ServerLevel level, BlockPos pos) {
        ItemStack glasses = items.get(SLOT_GLASSES);
        if (!ImtData.isGlasses(glasses)) {
            return false;
        }
        double missing = ImtData.getMaxEnergy(glasses) - ImtData.getEnergy(glasses) - energy;
        double room = ImtConfig.Power.STATION_CAPACITY - energy;
        int wanted = (int) Math.min(Math.min(missing, room), gridCarry.take(ImtConfig.Power.STATION_GRID_RATE));
        if (wanted <= 0) {
            return false;
        }
        int got = PowerGrid.draw(level, pos, wanted);
        energy += got;
        return got > 0;
    }

    /**
     * De eigen voorraad (de balk die je ook met redstone vult) bijvullen uit het net, maar alleen
     * met wat over is. De generatoren houden de helft van wat erin past achter
     * (STATION_GRID_RESERVE), zodat de holotafel, laserpoorten en de koepel op hetzelfde net
     * altijd voorgaan; alleen wat daarboven zit, gaat langzaam het station in.
     */
    private boolean storeSpareFromGrid(ServerLevel level, BlockPos pos) {
        double room = ImtConfig.Power.STATION_CAPACITY - energy;
        if (room < 1) {
            return false;
        }
        long stored = 0;
        long capacity = 0;
        for (EnergySource source : PowerGrid.sources(level, pos)) {
            stored += source.energy();
            capacity += source.capacity();
        }
        long spare = stored - (long) (capacity * ImtConfig.Power.STATION_GRID_RESERVE);
        int wanted = (int) Math.min(Math.min(spare, room), storeCarry.take(ImtConfig.Power.STATION_STORE_RATE));
        if (wanted <= 0) {
            return false;
        }
        int got = PowerGrid.draw(level, pos, wanted);
        energy += got;
        return got > 0;
    }

    /** Hoeveel energie er in het station zit. */
    public int energy() {
        return (int) energy;
    }

    /** Stuurt energie van het station naar de bril. */
    private boolean chargeGlasses() {
        ItemStack glasses = items.get(SLOT_GLASSES);
        if (energy < 1 || !ImtData.isGlasses(glasses)) {
            return false;
        }
        int give = Math.min(chargeCarry.take(Math.min(ImtConfig.Power.STATION_CHARGE_RATE, energy)), energy());
        int added = ImtData.addEnergy(glasses, give);
        energy -= added;
        return added > 0;
    }

    /** Bouwt langzaam de upgrade in de bril in. */
    private boolean tickUpgrade(ServerLevel level, BlockPos pos) {
        ItemStack glasses = items.get(SLOT_GLASSES);
        ItemStack upgrade = items.get(SLOT_UPGRADE);
        boolean canUpgrade = ImtData.isGlasses(glasses)
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
        if (upgradeProgress >= Math.round(ImtConfig.Glasses.UPGRADE_INSTALL_TIME)) {
            ((UpgradeItem) upgrade.getItem()).applyTo(glasses);
            upgrade.shrink(1);
            upgradeProgress = 0;
            level.playSound(null, pos, ModSounds.STATION_UPGRADE.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return true;
    }

    public static float getFuelValue(ItemStack stack) {
        if (stack.is(Items.REDSTONE)) {
            return ImtConfig.Power.ENERGY_PER_REDSTONE;
        }
        if (stack.is(Items.REDSTONE_BLOCK)) {
            return ImtConfig.Power.ENERGY_PER_REDSTONE_BLOCK;
        }
        return 0;
    }

    // ---------------------------------------------------------------- Welke items mogen in welk slot

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_GLASSES -> ImtData.isGlasses(stack);
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
        energy = tag.getDouble("energy"); // ook goed voor een oude wereld, waar het nog een heel getal was
        upgradeProgress = tag.getInt("upgrade_progress");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putDouble("energy", energy);
        tag.putInt("upgrade_progress", upgradeProgress);
    }

    // ---------------------------------------------------------------- Container-dingen

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.imttech.charging_station");
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
