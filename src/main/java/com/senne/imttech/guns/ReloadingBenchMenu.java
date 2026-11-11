package com.senne.imttech.guns;

import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModMenus;
import com.senne.imttech.registry.ModSounds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Het menu van de Reloading Bench (hoofdstuk 52): drie vakjes (huls, kogelpunt, Crushed Gunpowder) en rechts de pers met het
 * resultaat. Zolang er van alle drie iets ligt, ligt er één 9mm-patroon klaar; pak je hem, dan gaat er van elk één af
 * en ligt de volgende klaar. Shift-klik op het resultaat perst er zoveel als er in passen.
 */
public class ReloadingBenchMenu extends AbstractContainerMenu {
    public static final int CASING_X = 20, BULLET_X = 50, POWDER_X = 80, INPUT_Y = 35;
    public static final int RESULT_X = 138, RESULT_Y = 35;
    public static final int RESULT_SLOT = ReloadingBenchBlockEntity.SIZE;

    private final Container items;
    private final ResultContainer result = new ResultContainer();

    public ReloadingBenchMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, new SimpleContainer(ReloadingBenchBlockEntity.SIZE));
    }

    public ReloadingBenchMenu(int containerId, Inventory inventory, Container items) {
        super(ModMenus.RELOADING_BENCH.get(), containerId);
        checkContainerSize(items, ReloadingBenchBlockEntity.SIZE);
        this.items = items;
        items.startOpen(inventory.player);
        int[] xs = {CASING_X, BULLET_X, POWDER_X};
        for (int slot = 0; slot < ReloadingBenchBlockEntity.SIZE; slot++) {
            int index = slot;
            addSlot(new Slot(items, slot, xs[slot], INPUT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return ReloadingBenchBlockEntity.fits(index, stack);
                }

                @Override
                public void setChanged() {
                    // Hoofdstuk 79: iets erin gelegd of eruit gehaald, dan meteen de patroon klaarleggen (of weghalen).
                    // Eerst gebeurde dat alleen bij het openen, dus moest je de bank dicht en weer open doen.
                    super.setChanged();
                    refresh();
                }
            });
        }
        addSlot(new Slot(result, 0, RESULT_X, RESULT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                // Eén patroon gepakt: van elk ingrediënt één eraf, en de volgende klaarleggen.
                for (int slot = 0; slot < ReloadingBenchBlockEntity.SIZE; slot++) {
                    items.getItem(slot).shrink(1);
                }
                items.setChanged();
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GLOCK_ROUND_LOAD.get(),
                        SoundSource.BLOCKS, 0.5f, 0.8f);
                refresh();
                super.onTake(player, stack);
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
        refresh();
    }

    public Container items() {
        return items;
    }

    /** Het resultaat klaarleggen: één patroon als van alle drie iets ligt. */
    private void refresh() {
        boolean ready = ReloadingBenchBlockEntity.pressable(items) > 0;
        if (ready != result.getItem(0).is(ModItems.ROUND_9MM.get())) {
            result.setItem(0, ready ? new ItemStack(ModItems.ROUND_9MM.get()) : ItemStack.EMPTY);
        }
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        refresh();
    }

    /**
     * Hoofdstuk 79: elke tick (en na elke klik) nog eens kijken. Staan er twee spelers aan dezelfde bank en legt de ene er
     * iets in, dan ziet de andere de patroon ook meteen.
     */
    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int firstInventory = RESULT_SLOT + 1;
        if (index == RESULT_SLOT) {
            if (!moveItemStackTo(stack, firstInventory, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (index < RESULT_SLOT) {
            if (!moveItemStackTo(stack, firstInventory, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Uit je rugzak in het juiste vakje.
            boolean moved = false;
            for (int target = 0; target < ReloadingBenchBlockEntity.SIZE && !moved; target++) {
                if (ReloadingBenchBlockEntity.fits(target, stack)) {
                    moved = moveItemStackTo(stack, target, target + 1, false);
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return items.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        items.stopOpen(player);
    }
}
