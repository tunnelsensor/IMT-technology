package com.senne.starktech.logic;

import com.senne.starktech.registry.ModMenus;
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
 * Het menu van een serverrack: de zes vakjes (twee kolommen van drie, zoals de bays van een echt
 * rack; eerst de linker kolom van boven naar beneden, dan de rechter), je eigen rugzak, en wat de
 * hele server kan (als "data slots", zie ServerRackBlockEntity).
 */
public class ServerRackMenu extends AbstractContainerMenu {
    /** Waar het vakje linksboven staat; de andere staan eronder (BAY_STEP) en ernaast (COLUMN_STEP). */
    public static final int BAY_X = 14, BAY_Y = 14, BAY_STEP = 18, COLUMN_STEP = 22;
    /** Hoe hoog het scherm extra is (voor de getallen van het datacenter), en dus hoeveel lager je rugzak staat. */
    public static final int EXTRA_HEIGHT = 24;
    /** Waar je rugzak begint (bij de andere schermen van de mod op 84). */
    public static final int INVENTORY_Y = 84 + EXTRA_HEIGHT;

    public static final int DATA_STATUS = 0;
    public static final int DATA_RACKS = 1;
    public static final int DATA_CPU = 2;
    public static final int DATA_RAM = 3;
    public static final int DATA_SSD = 4;
    public static final int DATA_CORE_ONLINE = 5;
    public static final int DATA_GPU = 6;
    public static final int DATA_FAN = 7;
    /** Temperatuur, warmte, warmte als alles hard werkt, en koeling: allemaal in tienden (72.4 graden = 724). */
    public static final int DATA_TEMP = 8;
    public static final int DATA_HEAT = 9;
    public static final int DATA_FULL_HEAT = 10;
    public static final int DATA_COOLING = 11;
    /** Hoeveel seconden dit rack nog brandt (0 = het brandt niet). */
    public static final int DATA_BURN = 12;
    public static final int DATA_COUNT = 13;

    private final Container parts;
    private final ContainerData data;
    private final BlockPos pos;

    /** In jouw spel: de echte inhoud komt van de server; de plek van het rack stuurt hij mee. */
    public ServerRackMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, new SimpleContainer(LogicBalance.RACK_SLOTS), new SimpleContainerData(DATA_COUNT),
                extra.readBlockPos());
    }

    public ServerRackMenu(int containerId, Inventory inventory, Container parts, ContainerData data) {
        this(containerId, inventory, parts, data, BlockPos.ZERO);
    }

    private ServerRackMenu(int containerId, Inventory inventory, Container parts, ContainerData data, BlockPos pos) {
        super(ModMenus.SERVER_RACK.get(), containerId);
        checkContainerSize(parts, LogicBalance.RACK_SLOTS);
        checkContainerDataCount(data, DATA_COUNT);
        this.parts = parts;
        this.data = data;
        this.pos = pos;
        parts.startOpen(inventory.player);
        for (int i = 0; i < LogicBalance.RACK_SLOTS; i++) {
            addSlot(new Slot(parts, i, bayX(i), bayY(i)) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof ServerPartItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    /** Waar vakje {@code i} staat (links). */
    public static int bayX(int i) {
        return BAY_X + (i / LogicBalance.RACK_ROWS) * COLUMN_STEP;
    }

    /** Waar vakje {@code i} staat (boven). */
    public static int bayY(int i) {
        return BAY_Y + (i % LogicBalance.RACK_ROWS) * BAY_STEP;
    }

    // ---------------------------------------------------------------- Gegevens voor het scherm

    public ServerRackBlockEntity.Status status() {
        ServerRackBlockEntity.Status[] values = ServerRackBlockEntity.Status.values();
        int index = data.get(DATA_STATUS);
        return index >= 0 && index < values.length ? values[index] : ServerRackBlockEntity.Status.NO_NETWORK;
    }

    public int racks() {
        return data.get(DATA_RACKS);
    }

    public int cpus() {
        return data.get(DATA_CPU);
    }

    public int gpus() {
        return data.get(DATA_GPU);
    }

    public int fans() {
        return data.get(DATA_FAN);
    }

    public double temperature() {
        return data.get(DATA_TEMP) / 10.0;
    }

    public double heat() {
        return data.get(DATA_HEAT) / 10.0;
    }

    public double fullHeat() {
        return data.get(DATA_FULL_HEAT) / 10.0;
    }

    public double cooling() {
        return data.get(DATA_COOLING) / 10.0;
    }

    /** Seconden tot dit rack afgebrand is (0 = het brandt niet). */
    public int burnSeconds() {
        return data.get(DATA_BURN);
    }

    public int ram() {
        return data.get(DATA_RAM);
    }

    public int ssd() {
        return data.get(DATA_SSD);
    }

    public boolean coreOnline() {
        return data.get(DATA_CORE_ONLINE) != 0;
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
        int bays = LogicBalance.RACK_SLOTS;
        if (index < bays) {
            // Uit het rack naar je rugzak.
            if (!moveItemStackTo(stack, bays, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!(stack.getItem() instanceof ServerPartItem) || !moveItemStackTo(stack, 0, bays, false)) {
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
        return parts.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        parts.stopOpen(player);
    }
}
