package com.senne.imttech.plastic;

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
import net.minecraft.world.item.Items;

/**
 * Het menu van de Polymerizer (hoofdstuk 85): links de watertank, dan het koolvakje met het emmervakje eronder, een pijl,
 * en rechts het vakje eruit; daaronder je eigen inventaris. Hoe ver hij is, wat hij doet en hoeveel water erin zit, komt
 * mee als "gegevens" (zie PolymerizerBlockEntity).
 */
public class PolymerizerMenu extends AbstractContainerMenu {
    public static final int COAL_X = 56, COAL_Y = 18;
    public static final int BUCKET_X = 56, BUCKET_Y = 39;
    public static final int OUT_X = 116, OUT_Y = 28;

    private final Container container;
    private final ContainerData data;

    /** Op de client: de echte inhoud komt van de server. */
    public PolymerizerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, new SimpleContainer(PolymerizerBlockEntity.SLOT_COUNT),
                new SimpleContainerData(PolymerizerBlockEntity.DATA_COUNT));
    }

    public PolymerizerMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModMenus.POLYMERIZER.get(), containerId);
        checkContainerSize(container, PolymerizerBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, PolymerizerBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        container.startOpen(inventory.player);

        addSlot(new Slot(container, PolymerizerBlockEntity.SLOT_COAL, COAL_X, COAL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return PolymerizerBlockEntity.isCoal(stack);
            }
        });
        addSlot(new Slot(container, PolymerizerBlockEntity.SLOT_BUCKET, BUCKET_X, BUCKET_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.WATER_BUCKET);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(container, PolymerizerBlockEntity.SLOT_OUT, OUT_X, OUT_Y) {
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

    /** Hoe ver hij is, van 0 tot 1. */
    public float progress() {
        int total = data.get(PolymerizerBlockEntity.DATA_TOTAL);
        return total <= 0 ? 0 : Math.min(1.0f, (float) data.get(PolymerizerBlockEntity.DATA_PROGRESS) / total);
    }

    public PolymerizerBlockEntity.Status status() {
        PolymerizerBlockEntity.Status[] all = PolymerizerBlockEntity.Status.values();
        int index = data.get(PolymerizerBlockEntity.DATA_STATUS);
        return index >= 0 && index < all.length ? all[index] : PolymerizerBlockEntity.Status.EMPTY;
    }

    /** Hoeveel water er in de tank zit (mB). */
    public int water() {
        return data.get(PolymerizerBlockEntity.DATA_WATER);
    }

    /** Hoeveel water er in de tank past (mB). */
    public int tank() {
        return Math.max(1, data.get(PolymerizerBlockEntity.DATA_TANK));
    }

    public ItemStack coal() {
        return container.getItem(PolymerizerBlockEntity.SLOT_COAL);
    }

    public ItemStack bucket() {
        return container.getItem(PolymerizerBlockEntity.SLOT_BUCKET);
    }

    /** Shift-klik: uit de machine naar je tassen, of uit je tassen het koolvakje of het emmervakje in. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int inventoryStart = PolymerizerBlockEntity.SLOT_COUNT;
        if (index < inventoryStart) {
            if (!moveItemStackTo(stack, inventoryStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (PolymerizerBlockEntity.isCoal(stack)) {
            if (!moveItemStackTo(stack, PolymerizerBlockEntity.SLOT_COAL, PolymerizerBlockEntity.SLOT_COAL + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(Items.WATER_BUCKET)) {
            if (!moveItemStackTo(stack, PolymerizerBlockEntity.SLOT_BUCKET, PolymerizerBlockEntity.SLOT_BUCKET + 1, false)) {
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
