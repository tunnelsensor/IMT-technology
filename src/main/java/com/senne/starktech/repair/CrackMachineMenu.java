package com.senne.starktech.repair;

import com.senne.starktech.registry.ModMenus;
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
 * Het menu van een Crusher of Compactor (hoofdstuk 11): het vakje erin (links), het vakje eruit (rechts) en je
 * eigen inventaris. Hoe ver hij is en wat hij doet, komt mee als "gegevens" (zie CrackMachineBlockEntity).
 * Welke machine het is, stuurt de server mee als het scherm opengaat (voor het voorbeeld in het lege vakje).
 */
public class CrackMachineMenu extends AbstractContainerMenu {
    public static final int IN_X = 56, IN_Y = 35;
    public static final int OUT_X = 116, OUT_Y = 35;

    private final Container container;
    private final ContainerData data;
    private final CrackMachineBlock.Kind kind;

    /** Op de client: de echte inhoud komt van de server. */
    public CrackMachineMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, new SimpleContainer(CrackMachineBlockEntity.SLOT_COUNT),
                new SimpleContainerData(CrackMachineBlockEntity.DATA_COUNT), CrackMachineBlock.Kind.values()[extra.readVarInt()]);
    }

    public CrackMachineMenu(int containerId, Inventory inventory, Container container, ContainerData data,
                            CrackMachineBlock.Kind kind) {
        super(ModMenus.CRACK_MACHINE.get(), containerId);
        checkContainerSize(container, CrackMachineBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, CrackMachineBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        this.kind = kind;
        container.startOpen(inventory.player);

        addSlot(new Slot(container, CrackMachineBlockEntity.SLOT_IN, IN_X, IN_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CrackMachineBlock.accepts(CrackMachineMenu.this.kind, stack);
            }
        });
        addSlot(new Slot(container, CrackMachineBlockEntity.SLOT_OUT, OUT_X, OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
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

    public CrackMachineBlock.Kind kind() {
        return kind;
    }

    /** Hoe ver hij is, van 0 tot 1. */
    public float progress() {
        int total = data.get(CrackMachineBlockEntity.DATA_TOTAL);
        return total <= 0 ? 0 : Math.min(1.0f, (float) data.get(CrackMachineBlockEntity.DATA_PROGRESS) / total);
    }

    public CrackMachineBlockEntity.Status status() {
        CrackMachineBlockEntity.Status[] all = CrackMachineBlockEntity.Status.values();
        int index = data.get(CrackMachineBlockEntity.DATA_STATUS);
        return index >= 0 && index < all.length ? all[index] : CrackMachineBlockEntity.Status.EMPTY;
    }

    public ItemStack input() {
        return container.getItem(CrackMachineBlockEntity.SLOT_IN);
    }

    /** Shift-klik: uit de machine naar je tassen, of uit je tassen het vakje erin in. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int inventoryStart = CrackMachineBlockEntity.SLOT_COUNT;
        if (index < inventoryStart) {
            if (!moveItemStackTo(stack, inventoryStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!CrackMachineBlock.accepts(kind, stack)
                || !moveItemStackTo(stack, CrackMachineBlockEntity.SLOT_IN, CrackMachineBlockEntity.SLOT_IN + 1, false)) {
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
