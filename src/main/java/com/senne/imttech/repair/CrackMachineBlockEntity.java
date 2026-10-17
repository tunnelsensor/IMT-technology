package com.senne.imttech.repair;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van een Crusher of Compactor (hoofdstuk 11): een vakje erin, een vakje eruit, en hoe
 * ver hij is.
 *
 * Zolang er iets in zit waar hij iets mee kan, er plaats is in het vakje eruit en er stroom is, werkt
 * hij: {@link ImtConfig.Power#CRUSHER_COMPACTOR} stroom per tick, {@link ImtConfig.Repair#MACHINE_TICKS} ticks
 * per voorwerp (mijn invulling: 4 en 5 seconden). Valt de stroom weg of zit het vakje eruit vol, dan
 * wacht hij (hij vergeet niet hoe ver hij was). Wat hij van wat maakt, staat in
 * {@link CrackMachineBlock.Kind}.
 *
 * Trechters: van boven en opzij gaat er iets in, van onder komt er iets uit (zoals bij een oven).
 */
public class CrackMachineBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SLOT_IN = 0;
    public static final int SLOT_OUT = 1;
    public static final int SLOT_COUNT = 2;
    private static final int[] TOP_AND_SIDES = {SLOT_IN};
    private static final int[] BOTTOM = {SLOT_OUT};

    /** Nummers voor de gegevens die naar het scherm gaan. */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_COUNT = 3;

    /** Na zoveel ticks zonder werk staat zijn voorkant weer stil (zo knippert hij niet bij haperende stroom). */
    private static final int STOP_AFTER = 10;

    /** Wat de machine nu doet (voor de regel onderaan het scherm). */
    public enum Status {
        /** Er zit niets in waar hij iets mee kan. */
        EMPTY,
        WORKING,
        NO_POWER,
        /** Het vakje eruit zit vol (of er zit iets anders in). */
        FULL
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    /** Hoe ver hij is met het voorwerp in het vakje erin (ticks). */
    private int progress;
    private Status status = Status.EMPTY;
    /** Hoe lang hij al niets deed (voor zijn voorkant, zie STOP_AFTER). */
    private int idle;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> Math.round(ImtConfig.Repair.MACHINE_TICKS);
                case DATA_STATUS -> status.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Alleen het scherm van de speler krijgt waarden binnen (zie CrackMachineMenu).
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public CrackMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRACK_MACHINE.get(), pos, state);
    }

    /** Welke machine dit is (dat zegt zijn blok). */
    public CrackMachineBlock.Kind kind() {
        return getBlockState().getBlock() instanceof CrackMachineBlock block ? block.kind() : CrackMachineBlock.Kind.CRUSHER;
    }

    public Status status() {
        return status;
    }

    public int progress() {
        return progress;
    }

    // ================================================================ Elke tick (alleen server)

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack input = items.get(SLOT_IN);
        ItemStack result = kind().convert(input);
        Status now;
        if (result.isEmpty()) {
            now = Status.EMPTY;
            progress = 0;
        } else if (!fits(result)) {
            now = Status.FULL;
        } else if (!PowerGrid.pay(level, pos, ImtConfig.Power.CRUSHER_COMPACTOR)) {
            now = Status.NO_POWER;
        } else {
            now = Status.WORKING;
            progress++;
            effects(level, pos, input);
            if (progress >= Math.round(ImtConfig.Repair.MACHINE_TICKS)) {
                progress = 0;
                Block block = FromBlockItem.block(input);
                input.shrink(1);
                ItemStack out = items.get(SLOT_OUT);
                if (out.isEmpty()) {
                    items.set(SLOT_OUT, result);
                } else {
                    out.grow(result.getCount());
                }
                if (block != null) {
                    level.playSound(null, pos, kind().doneSound(block), SoundSource.BLOCKS, 0.3f, 0.9f + level.random.nextFloat() * 0.2f);
                }
            }
            setChanged();
        }
        if (now != status) {
            status = now;
            setChanged();
        }
        idle = now == Status.WORKING ? 0 : idle + 1;
        boolean working = idle < STOP_AFTER;
        if (state.getValue(CrackMachineBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(CrackMachineBlock.WORKING, working), Block.UPDATE_CLIENTS);
        }
    }

    /** Past {@code result} nog in het vakje eruit? */
    private boolean fits(ItemStack result) {
        ItemStack out = items.get(SLOT_OUT);
        return out.isEmpty() || ItemStack.isSameItemSameComponents(out, result)
                && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    /** Terwijl hij werkt: af en toe een stil geluidje, en wat stof van het blok bovenuit. */
    private void effects(ServerLevel level, BlockPos pos, ItemStack input) {
        if (progress % 20 == 1) {
            level.playSound(null, pos, kind().workSound(), SoundSource.BLOCKS, 0.08f, 0.8f + level.random.nextFloat() * 0.2f);
        }
        Block block = FromBlockItem.block(input);
        if (block != null && progress % 8 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState()),
                    pos.getX() + 0.5, pos.getY() + 1.02, pos.getZ() + 0.5, 2, 0.2, 0.0, 0.2, 0.02);
        }
    }

    // ================================================================ Vakjes en trechters

    /** Een ander voorwerp in het vakje erin: opnieuw beginnen. */
    @Override
    public void setItem(int slot, ItemStack stack) {
        ItemStack old = items.get(slot);
        super.setItem(slot, stack);
        if (slot == SLOT_IN && !ItemStack.isSameItemSameComponents(old, stack)) {
            progress = 0;
        }
    }

    /** In het vakje erin alleen wat deze machine kan verwerken; in het vakje eruit niets. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_IN && CrackMachineBlock.accepts(kind(), stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? BOTTOM : TOP_AND_SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUT;
    }

    // ================================================================ Opslag

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
        progress = tag.getInt("Progress");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress", progress);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new CrackMachineMenu(containerId, inventory, this, data, kind());
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.imttech." + kind().getSerializedName());
    }
}
