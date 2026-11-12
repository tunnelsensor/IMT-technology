package com.senne.imttech.plastic;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van de Polymerizer (hoofdstuk 85): drie vakjes (kool erin, een emmer water erin, polymeer eruit), een
 * watertank en hoe ver hij is.
 *
 * Zolang er kool in zit, er genoeg water in de tank is, er plaats is in het vakje eruit en er stroom is, werkt hij:
 * {@link ImtConfig.Power#POLYMERIZER} stroom per tick, {@link CableMaterials#ticks()} ticks per kool (mijn invulling: net
 * als de Crusher, 40 per tick en 5 seconden). Is hij klaar, dan gaat er één kool en {@link CableMaterials#waterPerCoal()}
 * mB water uit, en komt er {@link CableMaterials#polymerPerCoal()} polymeer bij. Valt iets weg, dan wacht hij (hij vergeet
 * niet hoe ver hij was).
 *
 * Trechters: van boven en opzij gaat er kool of een emmer water in, van onder komt het polymeer en de lege emmer eruit.
 * Buizen van andere mods kunnen water in de tank pompen (de vloeistof-capability, zie {@link #water()}); eruit halen kan
 * niet.
 */
public class PolymerizerBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SLOT_COAL = 0;
    public static final int SLOT_BUCKET = 1;
    public static final int SLOT_OUT = 2;
    public static final int SLOT_COUNT = 3;
    private static final int[] TOP_AND_SIDES = {SLOT_COAL, SLOT_BUCKET};
    private static final int[] BOTTOM = {SLOT_OUT, SLOT_BUCKET};

    /** Nummers voor de gegevens die naar het scherm gaan. */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_WATER = 3;
    public static final int DATA_TANK = 4;
    public static final int DATA_COUNT = 5;

    /** Na zoveel ticks zonder werk staat zijn voorkant weer stil (zo knippert hij niet bij haperende stroom). */
    private static final int STOP_AFTER = 10;

    /** Wat de machine nu doet (voor de regel onderaan het scherm). */
    public enum Status {
        /** Er zit geen kool in. */
        EMPTY,
        WORKING,
        /** Te weinig water in de tank voor één kool. */
        NO_WATER,
        NO_POWER,
        /** Het vakje eruit zit vol. */
        FULL
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    /** Hoe ver hij is met de kool in het vakje (ticks). */
    private int progress;
    /** Hoeveel water er in de tank zit (mB). */
    private int water;
    private Status status = Status.EMPTY;
    /** Hoe lang hij al niets deed (voor zijn voorkant, zie STOP_AFTER). */
    private int idle;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> CableMaterials.ticks();
                case DATA_STATUS -> status.ordinal();
                case DATA_WATER -> water;
                case DATA_TANK -> CableMaterials.tank();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Alleen het scherm van de speler krijgt waarden binnen (zie PolymerizerMenu).
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    /**
     * De tank voor buizen van andere mods: neemt alleen water aan (alles met de tag minecraft:water), en geeft niets af.
     */
    private final IFluidHandler waterIntake = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return water <= 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, water);
        }

        @Override
        public int getTankCapacity(int tank) {
            return CableMaterials.tank();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.getFluid().is(FluidTags.WATER);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !resource.getFluid().is(FluidTags.WATER)) {
                return 0;
            }
            int amount = Math.max(0, Math.min(resource.getAmount(), CableMaterials.tank() - water));
            if (amount > 0 && action.execute()) {
                water += amount;
                setChanged();
            }
            return amount;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    public PolymerizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POLYMERIZER.get(), pos, state);
    }

    public Status status() {
        return status;
    }

    public int progress() {
        return progress;
    }

    /** Hoeveel water er in de tank zit (mB). */
    public int waterAmount() {
        return water;
    }

    /** Voor de tests en de uitleg: de tank op een bepaalde hoeveelheid zetten. */
    public void setWater(int amount) {
        water = Math.max(0, Math.min(CableMaterials.tank(), amount));
        setChanged();
    }

    /** De tank voor buizen (de vloeistof-capability, zie ImtTech). */
    public IFluidHandler water() {
        return waterIntake;
    }

    /** Past er nog een hele emmer water in de tank? */
    public boolean fitsBucket() {
        return water + CableMaterials.BUCKET <= CableMaterials.tank();
    }

    /** Een emmer water erbij (de aanroeper kijkt eerst of hij past). */
    public void addBucket() {
        water = Math.min(CableMaterials.tank(), water + CableMaterials.BUCKET);
        setChanged();
    }

    /** Kool of houtskool (alles met de tag minecraft:coals). */
    public static boolean isCoal(ItemStack stack) {
        return stack.is(ItemTags.COALS);
    }

    // ================================================================ Elke tick (alleen server)

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        // Een emmer water in het emmervakje: leeg hem in de tank (de lege emmer blijft liggen).
        ItemStack bucket = items.get(SLOT_BUCKET);
        if (bucket.is(Items.WATER_BUCKET) && fitsBucket()) {
            addBucket();
            items.set(SLOT_BUCKET, new ItemStack(Items.BUCKET));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.4f, 1.0f);
        }

        ItemStack coal = items.get(SLOT_COAL);
        ItemStack result = new ItemStack(ModItems.POLYMER.get(), CableMaterials.polymerPerCoal());
        Status now;
        if (!isCoal(coal)) {
            now = Status.EMPTY;
            progress = 0;
        } else if (water < CableMaterials.waterPerCoal()) {
            now = Status.NO_WATER;
        } else if (!fits(result)) {
            now = Status.FULL;
        } else if (!PowerGrid.pay(level, pos, ImtConfig.Power.POLYMERIZER)) {
            now = Status.NO_POWER;
        } else {
            now = Status.WORKING;
            progress++;
            effects(level, pos);
            if (progress >= CableMaterials.ticks()) {
                progress = 0;
                coal.shrink(1);
                water -= CableMaterials.waterPerCoal();
                ItemStack out = items.get(SLOT_OUT);
                if (out.isEmpty()) {
                    items.set(SLOT_OUT, result);
                } else {
                    out.grow(result.getCount());
                }
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.25f, 1.3f);
            }
            setChanged();
        }
        if (now != status) {
            status = now;
            setChanged();
        }
        idle = now == Status.WORKING ? 0 : idle + 1;
        boolean working = idle < STOP_AFTER;
        if (state.getValue(PolymerizerBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(PolymerizerBlock.WORKING, working), Block.UPDATE_CLIENTS);
        }
    }

    /** Past {@code result} nog in het vakje eruit? */
    private boolean fits(ItemStack result) {
        ItemStack out = items.get(SLOT_OUT);
        return out.isEmpty() || ItemStack.isSameItemSameComponents(out, result)
                && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    /** Terwijl hij werkt: af en toe een stil geborrel, en belletjes bovenuit. */
    private void effects(ServerLevel level, BlockPos pos) {
        if (progress % 20 == 1) {
            level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.12f,
                    0.9f + level.random.nextFloat() * 0.2f);
        }
        if (progress % 6 == 0) {
            level.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 1.02, pos.getZ() + 0.5, 2, 0.2, 0.0, 0.2, 0.01);
        }
    }

    // ================================================================ Vakjes en trechters

    /** Een ander voorwerp in het koolvakje: opnieuw beginnen. */
    @Override
    public void setItem(int slot, ItemStack stack) {
        ItemStack old = items.get(slot);
        super.setItem(slot, stack);
        if (slot == SLOT_COAL && !ItemStack.isSameItemSameComponents(old, stack)) {
            progress = 0;
        }
    }

    /** In het koolvakje alleen kool, in het emmervakje alleen een emmer water, in het vakje eruit niets. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_COAL && isCoal(stack) || slot == SLOT_BUCKET && stack.is(Items.WATER_BUCKET);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? BOTTOM : TOP_AND_SIDES;
    }

    /** Een trechter legt alleen een emmer water in het emmervakje als dat leeg is (er ligt dus nooit een volle op een lege). */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack) && (slot != SLOT_BUCKET || items.get(SLOT_BUCKET).isEmpty());
    }

    /** Van onder: het polymeer, en uit het emmervakje alleen een lege emmer. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUT || slot == SLOT_BUCKET && stack.is(Items.BUCKET);
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
        water = tag.getInt("Water");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress", progress);
        tag.putInt("Water", water);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new PolymerizerMenu(containerId, inventory, this, data);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.imttech.polymerizer");
    }
}
