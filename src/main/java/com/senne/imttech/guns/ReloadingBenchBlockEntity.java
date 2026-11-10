package com.senne.imttech.guns;

import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van de Reloading Bench (hoofdstuk 52): drie vakjes, een huls, een kogelpunt en Crushed Gunpowder. Eén
 * van elk geeft één 9mm-patroon (zie ReloadingBenchMenu).
 *
 * Hoofdstuk 76 (jouw wens: "voor kogels te maken heb je nu crushed gunpowder nodig"): gewoon buskruit past niet meer in
 * het derde vakje; dat gaat eerst door de Crusher, die er 10 Crushed Gunpowder van maakt (zie CrushedGunpowderItem).
 * Zo is één buskruit tien patronen. Lag er in een oude wereld nog buskruit in dat vakje, dan kun je het er gewoon uit
 * halen (en door de Crusher halen).
 */
public class ReloadingBenchBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_CASING = 0;
    public static final int SLOT_BULLET = 1;
    public static final int SLOT_POWDER = 2;
    public static final int SIZE = 3;

    private final SimpleContainer items = new SimpleContainer(SIZE) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return fits(slot, stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            ReloadingBenchBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(ReloadingBenchBlockEntity.this, player);
        }
    };

    public ReloadingBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RELOADING_BENCH.get(), pos, state);
    }

    public SimpleContainer items() {
        return items;
    }

    /** Wat in welk vakje mag. */
    public static boolean fits(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_CASING -> stack.is(ModItems.CASING_9MM.get());
            case SLOT_BULLET -> stack.is(ModItems.BULLET_9MM.get());
            case SLOT_POWDER -> stack.is(ModItems.CRUSHED_GUNPOWDER.get());
            default -> false;
        };
    }

    /** Zoveel patronen kun je nu persen (het minste van de drie). */
    public static int pressable(Container items) {
        int count = Integer.MAX_VALUE;
        for (int slot = 0; slot < SIZE; slot++) {
            ItemStack stack = items.getItem(slot);
            count = Math.min(count, fits(slot, stack) ? stack.getCount() : 0);
        }
        return count == Integer.MAX_VALUE ? 0 : count;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.imttech.reloading_bench");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ReloadingBenchMenu(containerId, inventory, items);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        for (int i = 0; i < SIZE; i++) {
            list.set(i, items.getItem(i));
        }
        ContainerHelper.saveAllItems(tag, list, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, registries);
        for (int i = 0; i < SIZE; i++) {
            items.setItem(i, list.get(i));
        }
    }
}
