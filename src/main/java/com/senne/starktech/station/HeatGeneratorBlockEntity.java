package com.senne.starktech.station;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * De warmtegenerator: zet hitte om in energie voor je bril.
 *
 * Twee manieren tegelijk:
 * - Gooi er brandstof in (kolen, hout, blaze rods) en hij verbrandt dat rustig tot energie.
 * - Zet hem naast iets heets - lava, vuur, magmablokken, een kampvuur - en hij pakt die
 *   warmte er gratis bij op. Hoe meer hitte om hem heen, hoe sneller hij laadt.
 *
 * Bouw hem dus bij een lavameer, en je hoeft er nooit meer naar om te kijken.
 */
public class HeatGeneratorBlockEntity extends ChargerBlockEntity {
    /** Hoeveel ticks de huidige brandstof nog brandt. */
    private int burnTicks;
    /** Wat hij de laatste tick opwekte (brandstof plus hitte), ook als zijn buffer vol zat. */
    private int heat;

    public HeatGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEAT_GENERATOR.get(), pos, state);
    }

    @Override
    public int capacity() {
        return StarkConfig.Power.HEAT_CAPACITY;
    }

    @Override
    public boolean usesFuel() {
        return true;
    }

    public boolean isBurning() {
        return burnTicks > 0;
    }

    @Override
    protected int produce(ServerLevel level, BlockPos pos) {
        int made = 0;
        // 1. Brandstof.
        if (burnTicks > 0) {
            burnTicks--;
            made += StarkConfig.Power.HEAT_RATE_FUEL;
        } else {
            ItemStack fuel = getItem(SLOT_FUEL);
            int burnTime = fuel.getBurnTime(null);
            if (burnTime > 0 && energy() < capacity()) {
                fuel.shrink(1);
                burnTicks = burnTime;
                made += StarkConfig.Power.HEAT_RATE_FUEL;
            }
        }
        // 2. Hitte uit de buurt (dit kost een blik op de 26 blokken om hem heen).
        made += StarkConfig.Power.HEAT_RATE_AMBIENT * hotNeighbours(level, pos);
        heat = made;
        return made;
    }

    /**
     * Na elke tick: gloeit het rooster nog zoals het hoort? Alleen als het verandert passen we het
     * blok aan (dat is een beetje werk: het stukje wereld moet opnieuw getekend worden).
     *
     * We kijken naar de hitte die binnenkomt, niet naar wat er in de buffer bijkwam: met een volle
     * buffer bij een lavameer blijft hij dus gewoon gloeien, in plaats van steeds aan en uit.
     */
    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        super.serverTick(level, pos, state);
        boolean lit = heat > 0;
        if (state.hasProperty(HeatGeneratorBlock.LIT) && state.getValue(HeatGeneratorBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(HeatGeneratorBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    /** Hoeveel hete blokken staan er tegen hem aan? (Maximaal een stuk of acht telt mee.) */
    private int hotNeighbours(ServerLevel level, BlockPos pos) {
        int hot = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (isHot(level.getBlockState(cursor))) {
                        hot++;
                    }
                }
            }
        }
        return Math.min(hot, StarkConfig.Power.HEAT_MAX_NEIGHBOURS);
    }

    private static boolean isHot(BlockState state) {
        return !state.getFluidState().isEmpty() && state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.LAVA_CAULDRON)
                || state.is(Blocks.CAMPFIRE) && state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                || state.is(Blocks.SOUL_CAMPFIRE) && state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_GLASSES -> EdithData.isGlasses(stack);
            case SLOT_FUEL -> stack.getBurnTime(null) > 0;
            default -> false;
        };
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        burnTicks = tag.getInt("BurnTicks");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("BurnTicks", burnTicks);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.starktech.heat_generator");
    }
}
