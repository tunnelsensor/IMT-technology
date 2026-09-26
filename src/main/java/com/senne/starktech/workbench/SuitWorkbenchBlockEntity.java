package com.senne.starktech.workbench;

import com.senne.starktech.energy.StarkGrid;
import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.suit.RocketPackItem;
import com.senne.starktech.suit.StarkSuitItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * De "binnenkant" van de harnas-werkbank.
 *
 * Slots 0-8 zijn de materialen, slot 9 is het werkvak (een stuk harnas: wat je bouwt komt
 * hier, en wat je upgradet leg je hier), slot 10 is de upgrade die erin moet.
 *
 * Twee soorten werk, allebei met een voortgangsbalk en stroom van het net (een Stark-kabel naar
 * een zonnepaneel of warmtegenerator, of er direct tegenaan):
 * - BOUWEN: je drukt op een knop (borstplaat, broek, laarzen). Zijn de materialen er en is het
 *   werkvak leeg, dan gaan de materialen eruit en begint het bouwen; klaar = het stuk in het
 *   werkvak.
 * - INBOUWEN: ligt er een stuk harnas in het werkvak en een upgrade die erin past, dan begint
 *   het inbouwen vanzelf.
 * Is er even geen stroom, dan wacht de werkbank tot die er weer is.
 */
public class SuitWorkbenchBlockEntity extends BaseContainerBlockEntity {
    public static final int MATERIAL_SLOTS = 9;
    public static final int SLOT_WORK = 9;
    public static final int SLOT_MODULE = 10;
    public static final int SLOT_COUNT = 11;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_JOB = 2;
    public static final int DATA_POWERED = 3;
    public static final int DATA_COUNT = 4;

    /** Wat hij aan het doen is: niets, een stuk bouwen (1 + blueprint), of een upgrade inbouwen. */
    public static final int JOB_NONE = 0;
    public static final int JOB_INSTALL = 10;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int job = JOB_NONE;
    private int progress;
    private boolean powered;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> total();
                case DATA_JOB -> job;
                case DATA_POWERED -> powered ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Alleen de server rekent; het scherm krijgt de waarden via de menu-data.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public SuitWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUIT_WORKBENCH.get(), pos, state);
    }

    private int total() {
        return job == JOB_INSTALL ? WorkbenchBalance.INSTALL_TICKS : job == JOB_NONE ? 0 : WorkbenchBalance.BUILD_TICKS;
    }

    // ================================================================ Bouwen

    /** Knop in het scherm: bouw dit stuk harnas. Geeft true als het bouwen begon. */
    public boolean startBuild(Blueprint blueprint) {
        if (job != JOB_NONE || !items.get(SLOT_WORK).isEmpty() || !hasMaterials(blueprint)) {
            return false;
        }
        for (Blueprint.Cost cost : blueprint.costs()) {
            int left = cost.count();
            for (int slot = 0; slot < MATERIAL_SLOTS && left > 0; slot++) {
                ItemStack stack = items.get(slot);
                if (cost.ingredient().test(stack)) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
        job = 1 + blueprint.ordinal();
        progress = 0;
        setChanged();
        return true;
    }

    /** Liggen er genoeg materialen in de werkbank voor dit stuk? */
    public boolean hasMaterials(Blueprint blueprint) {
        for (Blueprint.Cost cost : blueprint.costs()) {
            int found = 0;
            for (int slot = 0; slot < MATERIAL_SLOTS; slot++) {
                if (cost.ingredient().test(items.get(slot))) {
                    found += items.get(slot).getCount();
                }
            }
            if (found < cost.count()) {
                return false;
            }
        }
        return true;
    }

    // ================================================================ Elke tick

    public void serverTick(ServerLevel level, BlockPos pos) {
        if (job == JOB_NONE && canInstall()) {
            job = JOB_INSTALL;
            progress = 0;
        }
        if (job == JOB_INSTALL && !canInstall()) {
            job = JOB_NONE; // stuk of upgrade eruit gehaald
            progress = 0;
        }
        if (job == JOB_NONE) {
            powered = StarkGrid.available(level, pos) > 0;
            return;
        }
        int cost = job == JOB_INSTALL ? WorkbenchBalance.INSTALL_ENERGY : WorkbenchBalance.BUILD_ENERGY;
        powered = StarkGrid.available(level, pos) >= cost;
        if (!powered) {
            return; // wachten op stroom
        }
        StarkGrid.draw(level, pos, cost);
        progress++;
        if (level.getGameTime() % 10 == 0) {
            level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.25f, 1.6f);
        }
        if (progress >= total()) {
            finish(level, pos);
        }
        setChanged();
    }

    private void finish(ServerLevel level, BlockPos pos) {
        if (job == JOB_INSTALL) {
            ItemStack module = items.get(SLOT_MODULE);
            ItemStack piece = items.get(SLOT_WORK);
            if (module.getItem() instanceof SuitModuleItem suitModule) {
                suitModule.applyTo(piece);
            } else if (module.getItem() instanceof RocketPackItem) {
                piece.set(ModDataComponents.ROCKET_PACK.get(), true);
            }
            module.shrink(1);
        } else {
            Blueprint blueprint = Blueprint.byId(job - 1);
            if (blueprint != null) {
                items.set(SLOT_WORK, new ItemStack(blueprint.result()));
            }
        }
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.4f);
        job = JOB_NONE;
        progress = 0;
    }

    /** Ligt er een stuk harnas in het werkvak en een upgrade die er (nog) in past? */
    private boolean canInstall() {
        ItemStack piece = items.get(SLOT_WORK);
        ItemStack module = items.get(SLOT_MODULE);
        if (module.getItem() instanceof SuitModuleItem suitModule) {
            return suitModule.canApplyTo(piece);
        }
        return module.getItem() instanceof RocketPackItem && piece.getItem() instanceof StarkSuitItem suit
                && suit.getType() == net.minecraft.world.item.ArmorItem.Type.CHESTPLATE
                && !RocketPackItem.installedOn(piece);
    }

    // ================================================================ Container

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_WORK) {
            // Tijdens het bouwen niet: daar komt straks het nieuwe stuk.
            boolean building = job != JOB_NONE && job != JOB_INSTALL;
            return !building && stack.getItem() instanceof StarkSuitItem;
        }
        if (slot == SLOT_MODULE) {
            return stack.getItem() instanceof SuitModuleItem || stack.getItem() instanceof RocketPackItem;
        }
        return true;
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.starktech.suit_workbench");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new SuitWorkbenchMenu(containerId, inventory, this, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Job", job);
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        job = tag.getInt("Job");
        progress = tag.getInt("Progress");
    }
}
