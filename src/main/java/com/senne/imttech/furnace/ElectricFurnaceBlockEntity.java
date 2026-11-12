package com.senne.imttech.furnace;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.registry.ModBlockEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van de Electric Furnace (hoofdstuk 87): twee vakjes (erin en eruit), hoe ver hij is, en de ervaring die hij
 * voor je bewaart.
 *
 * Hij smelt alles wat een gewone oven smelt (dezelfde recepten: "minecraft:smelting", ook die van andere mods), maar zonder
 * brandstof: zolang er iets in zit dat smelt, er plaats is in het vakje eruit en er stroom is, werkt hij.
 * {@link ImtConfig.Power#ELECTRIC_FURNACE} stroom per tick, en {@link #speed()} keer zo snel als een oven (mijn invulling:
 * 40 per tick en twee keer zo snel, dus 5 seconden per ijzererts). Valt de stroom weg, dan wacht hij (hij vergeet niet hoe
 * ver hij was).
 *
 * De ervaring van wat hij smelt, spaart hij op, net als een oven: je krijgt ze als je het resultaat eruit haalt, of als je
 * hem afbreekt.
 *
 * Trechters: van boven en opzij gaat er iets in, van onder komt het resultaat eruit.
 */
public class ElectricFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
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

    /** Na zoveel ticks zonder werk gaat zijn gloed uit (zo knippert hij niet bij haperende stroom). */
    private static final int STOP_AFTER = 10;

    /** Wat de oven nu doet (voor de regel onderaan het scherm). */
    public enum Status {
        /** Er zit niets in (of niets wat smelt). */
        EMPTY,
        WORKING,
        NO_POWER,
        /** Het vakje eruit zit vol (of er ligt iets anders in). */
        FULL
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    /** Hoe ver hij is met wat er in het vakje ligt (ticks). */
    private int progress;
    /** Hoe lang hij over wat er nu in ligt doet (ticks, voor het scherm). */
    private int total;
    /** De opgespaarde ervaring van alles wat hij smolt (zoals bij een oven). */
    private float experience;
    private Status status = Status.EMPTY;
    /**
     * Hoe lang hij al niets deed (voor zijn gloed, zie STOP_AFTER). Begint op STOP_AFTER: een nieuwe oven die nog niets
     * deed, gloeit niet even op.
     */
    private int idle = STOP_AFTER;
    /** Zoekt het recept snel op (onthoudt het vorige, zoals een gewone oven). */
    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> recipes =
            RecipeManager.createCheck(RecipeType.SMELTING);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> total;
                case DATA_STATUS -> status.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Alleen het scherm van de speler krijgt waarden binnen (zie ElectricFurnaceMenu).
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_FURNACE.get(), pos, state);
    }

    public Status status() {
        return status;
    }

    public int progress() {
        return progress;
    }

    /** De opgespaarde ervaring (voor de tests). */
    public float experience() {
        return experience;
    }

    /** Voor de tests: de opgespaarde ervaring op een bepaalde hoeveelheid zetten. */
    public void setExperience(float amount) {
        experience = Math.max(0, amount);
        setChanged();
    }

    /** Zoveel keer sneller dan een gewone oven (minstens 0,1 keer, zie [materials] in de config). */
    public static float speed() {
        return Math.max(0.1f, ImtConfig.Materials.ELECTRIC_FURNACE_SPEED);
    }

    /**
     * Zo lang doet hij over iets waar een gewone oven {@code cookingTime} ticks over doet. Hooguit 32000, want het scherm
     * krijgt het getal als een "short" door.
     */
    public static int ticksFor(int cookingTime) {
        return Mth.clamp(Math.round(cookingTime / speed()), 1, 32000);
    }

    /** Smelt {@code stack} in een oven? (Ook op de client: die kent de recepten ook.) */
    public static boolean canSmelt(Level level, ItemStack stack) {
        return !stack.isEmpty() && level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level).isPresent();
    }

    // ================================================================ Elke tick (alleen server)

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack input = items.get(SLOT_IN);
        Optional<RecipeHolder<SmeltingRecipe>> recipe = input.isEmpty() ? Optional.empty()
                : recipes.getRecipeFor(new SingleRecipeInput(input), level);
        Status now;
        if (recipe.isEmpty()) {
            now = Status.EMPTY;
            progress = 0;
            total = 0;
        } else {
            SmeltingRecipe smelting = recipe.get().value();
            ItemStack result = smelting.assemble(new SingleRecipeInput(input), level.registryAccess());
            total = ticksFor(smelting.getCookingTime());
            if (result.isEmpty() || !fits(result)) {
                now = Status.FULL;
            } else if (!PowerGrid.pay(level, pos, ImtConfig.Power.ELECTRIC_FURNACE)) {
                now = Status.NO_POWER;
            } else {
                now = Status.WORKING;
                progress++;
                if (progress >= total) {
                    progress = 0;
                    input.shrink(1);
                    ItemStack out = items.get(SLOT_OUT);
                    if (out.isEmpty()) {
                        items.set(SLOT_OUT, result.copy());
                    } else {
                        out.grow(result.getCount());
                    }
                    experience += smelting.getExperience();
                }
                setChanged();
            }
        }
        if (now != status) {
            status = now;
            setChanged();
        }
        idle = now == Status.WORKING ? 0 : idle + 1;
        boolean working = idle < STOP_AFTER;
        if (state.getValue(ElectricFurnaceBlock.WORKING) != working) {
            // Het licht gaat vanzelf mee aan en uit (de gloed geeft licht, zie ElectricFurnaceBlock).
            level.setBlock(pos, state.setValue(ElectricFurnaceBlock.WORKING, working), Block.UPDATE_CLIENTS);
        }
    }

    /** Past {@code result} nog in het vakje eruit? */
    private boolean fits(ItemStack result) {
        ItemStack out = items.get(SLOT_OUT);
        return out.isEmpty() || ItemStack.isSameItemSameComponents(out, result)
                && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    /**
     * De opgespaarde ervaring als bolletjes op deze plek (als je het resultaat eruit haalt, of als hij afgebroken wordt).
     * Net als bij een oven: het deel achter de komma wordt een kans op één bolletje meer.
     */
    public void popExperience(ServerLevel level, Vec3 at) {
        int whole = Mth.floor(experience);
        float rest = experience - whole;
        if (rest > 0 && level.random.nextFloat() < rest) {
            whole++;
        }
        experience = 0;
        setChanged();
        if (whole > 0) {
            ExperienceOrb.award(level, at, whole);
        }
    }

    // ================================================================ Vakjes en trechters

    /** Iets anders in het vakje erin: opnieuw beginnen. */
    @Override
    public void setItem(int slot, ItemStack stack) {
        ItemStack old = items.get(slot);
        super.setItem(slot, stack);
        if (slot == SLOT_IN && !ItemStack.isSameItemSameComponents(old, stack)) {
            progress = 0;
        }
    }

    /** In het vakje erin alleen wat smelt, in het vakje eruit niets. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_IN && (level == null || canSmelt(level, stack));
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? BOTTOM : TOP_AND_SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    /** Van onder: alleen het resultaat. */
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
        experience = tag.getFloat("Experience");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress", progress);
        tag.putFloat("Experience", experience);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new ElectricFurnaceMenu(containerId, inventory, this, data);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.imttech.electric_furnace");
    }
}
