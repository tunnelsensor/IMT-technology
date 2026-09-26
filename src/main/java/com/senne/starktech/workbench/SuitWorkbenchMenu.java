package com.senne.starktech.workbench;

import com.senne.starktech.registry.ModMenus;
import com.senne.starktech.suit.RocketPackItem;
import com.senne.starktech.suit.StarkSuitItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Wat er in het scherm van de harnas-werkbank zit: 9 vakken voor materialen (links), het
 * werkvak en het upgradevak (rechts), en jouw inventaris eronder. De knoppen om te bouwen
 * gebruiken het gewone "knop in een scherm"-bericht van Minecraft (zoals de betoveringstafel):
 * clickMenuButton hieronder.
 */
public class SuitWorkbenchMenu extends AbstractContainerMenu {
    public static final int MATERIALS_X = 8, MATERIALS_Y = 22;
    public static final int WORK_X = 144, WORK_Y = 30;
    public static final int MODULE_X = 144, MODULE_Y = 82;
    public static final int INVENTORY_Y = 128;

    private final Container container;
    private final ContainerData data;
    /** Alleen op de server bekend: de werkbank zelf (om te bouwen). */
    private final @Nullable SuitWorkbenchBlockEntity workbench;

    /** In jouw spel: de echte inhoud komt van de server. */
    public SuitWorkbenchMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, new SimpleContainer(SuitWorkbenchBlockEntity.SLOT_COUNT),
                new SimpleContainerData(SuitWorkbenchBlockEntity.DATA_COUNT));
    }

    public SuitWorkbenchMenu(int containerId, Inventory inventory, @Nullable SuitWorkbenchBlockEntity workbench,
                             Container container, ContainerData data) {
        super(ModMenus.SUIT_WORKBENCH.get(), containerId);
        checkContainerSize(container, SuitWorkbenchBlockEntity.SLOT_COUNT);
        this.container = container;
        this.data = data;
        this.workbench = workbench;
        container.startOpen(inventory.player);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(container, column + row * 3, MATERIALS_X + column * 18, MATERIALS_Y + row * 18));
            }
        }
        addSlot(new CheckedSlot(container, SuitWorkbenchBlockEntity.SLOT_WORK, WORK_X, WORK_Y));
        addSlot(new CheckedSlot(container, SuitWorkbenchBlockEntity.SLOT_MODULE, MODULE_X, MODULE_Y));
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

    /** Een vak dat alleen de juiste spullen aanneemt (zie canPlaceItem van de werkbank). */
    private static class CheckedSlot extends Slot {
        CheckedSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    // ---------------------------------------------------------------- Knoppen en gegevens

    /** Een knop in het scherm: 0 = borstplaat, 1 = broek, 2 = laarzen bouwen. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        Blueprint blueprint = Blueprint.byId(id);
        return blueprint != null && workbench != null && workbench.startBuild(blueprint);
    }

    public float progress() {
        int total = data.get(SuitWorkbenchBlockEntity.DATA_TOTAL);
        return total <= 0 ? 0 : (float) data.get(SuitWorkbenchBlockEntity.DATA_PROGRESS) / total;
    }

    public int job() {
        return data.get(SuitWorkbenchBlockEntity.DATA_JOB);
    }

    public boolean powered() {
        return data.get(SuitWorkbenchBlockEntity.DATA_POWERED) == 1;
    }

    /** Liggen er (in dit scherm) genoeg materialen voor dit stuk? Voor de kleur van de knop. */
    public boolean hasMaterials(Blueprint blueprint) {
        for (Blueprint.Cost cost : blueprint.costs()) {
            int found = 0;
            for (int slot = 0; slot < SuitWorkbenchBlockEntity.MATERIAL_SLOTS; slot++) {
                if (cost.ingredient().test(container.getItem(slot))) {
                    found += container.getItem(slot).getCount();
                }
            }
            if (found < cost.count()) {
                return false;
            }
        }
        return true;
    }

    public ItemStack workItem() {
        return container.getItem(SuitWorkbenchBlockEntity.SLOT_WORK);
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
        int benchSlots = SuitWorkbenchBlockEntity.SLOT_COUNT;
        if (slotIndex < benchSlots) {
            if (!moveItemStackTo(stack, benchSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof StarkSuitItem) {
            if (!moveItemStackTo(stack, SuitWorkbenchBlockEntity.SLOT_WORK, SuitWorkbenchBlockEntity.SLOT_WORK + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof SuitModuleItem || stack.getItem() instanceof RocketPackItem) {
            if (!moveItemStackTo(stack, SuitWorkbenchBlockEntity.SLOT_MODULE, SuitWorkbenchBlockEntity.SLOT_MODULE + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, SuitWorkbenchBlockEntity.MATERIAL_SLOTS, false)) {
            return ItemStack.EMPTY;
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
