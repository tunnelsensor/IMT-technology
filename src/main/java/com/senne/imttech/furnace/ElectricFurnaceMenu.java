package com.senne.imttech.furnace;

import com.senne.imttech.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
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
 * Het menu van de Electric Furnace (hoofdstuk 87): links het vakje erin, een pijl, rechts het vakje eruit, en daaronder je
 * eigen inventaris. Op dezelfde plekken als bij de Crusher. Hoe ver hij is en wat hij doet, komt mee als "gegevens" (zie
 * ElectricFurnaceBlockEntity).
 */
public class ElectricFurnaceMenu extends AbstractContainerMenu {
    public static final int IN_X = 56, IN_Y = 35;
    public static final int OUT_X = 116, OUT_Y = 35;

    private final Container container;
    private final ContainerData data;
    private final Player player;

    /** Op de client: de echte inhoud komt van de server. */
    public ElectricFurnaceMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, new SimpleContainer(ElectricFurnaceBlockEntity.SLOT_COUNT),
                new SimpleContainerData(ElectricFurnaceBlockEntity.DATA_COUNT));
    }

    public ElectricFurnaceMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModMenus.ELECTRIC_FURNACE.get(), containerId);
        checkContainerSize(container, ElectricFurnaceBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, ElectricFurnaceBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        this.player = inventory.player;
        container.startOpen(inventory.player);

        addSlot(new Slot(container, ElectricFurnaceBlockEntity.SLOT_IN, IN_X, IN_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ElectricFurnaceBlockEntity.canSmelt(player.level(), stack);
            }
        });
        addSlot(new Slot(container, ElectricFurnaceBlockEntity.SLOT_OUT, OUT_X, OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            /** Je haalt het resultaat eruit: dan krijg je de opgespaarde ervaring (zoals bij een oven). */
            @Override
            public void onTake(Player taker, ItemStack stack) {
                if (container instanceof ElectricFurnaceBlockEntity furnace && taker.level() instanceof ServerLevel level) {
                    furnace.popExperience(level, taker.position());
                }
                super.onTake(taker, stack);
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

    /** Hoe ver hij is, van 0 tot 1. */
    public float progress() {
        int total = data.get(ElectricFurnaceBlockEntity.DATA_TOTAL);
        return total <= 0 ? 0 : Math.min(1.0f, (float) data.get(ElectricFurnaceBlockEntity.DATA_PROGRESS) / total);
    }

    public ElectricFurnaceBlockEntity.Status status() {
        ElectricFurnaceBlockEntity.Status[] all = ElectricFurnaceBlockEntity.Status.values();
        int index = data.get(ElectricFurnaceBlockEntity.DATA_STATUS);
        return index >= 0 && index < all.length ? all[index] : ElectricFurnaceBlockEntity.Status.EMPTY;
    }

    public ItemStack input() {
        return container.getItem(ElectricFurnaceBlockEntity.SLOT_IN);
    }

    /**
     * Shift-klik: uit de oven naar je tassen (het resultaat geeft dan ook de ervaring), of uit je tassen het vakje erin
     * (alleen wat smelt).
     */
    @Override
    public ItemStack quickMoveStack(Player taker, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int inventoryStart = ElectricFurnaceBlockEntity.SLOT_COUNT;
        if (index < inventoryStart) {
            if (!moveItemStackTo(stack, inventoryStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (ElectricFurnaceBlockEntity.canSmelt(taker.level(), stack)) {
            if (!moveItemStackTo(stack, ElectricFurnaceBlockEntity.SLOT_IN, ElectricFurnaceBlockEntity.SLOT_IN + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(taker, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player taker) {
        return container.stillValid(taker);
    }

    @Override
    public void removed(Player taker) {
        super.removed(taker);
        container.stopOpen(taker);
    }
}
