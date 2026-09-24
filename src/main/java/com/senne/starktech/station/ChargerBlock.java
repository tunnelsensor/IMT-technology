package com.senne.starktech.station;

import com.senne.starktech.energy.PowerConnectable;
import com.senne.starktech.energy.StarkGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Het blok onder een lader. Het zonnepaneel en de warmtegenerator delen dit: allebei open je
 * ze met een rechtsklik om je bril erin te leggen, en allebei tikken ze elke tick.
 *
 * Wat ze precies opwekken staat in hun block entity (SolarPanelBlockEntity en
 * HeatGeneratorBlockEntity).
 */
public abstract class ChargerBlock extends BaseEntityBlock implements PowerConnectable {
    protected ChargerBlock(Properties properties) {
        super(properties);
    }

    /**
     * Blokken met een block entity zijn standaard onzichtbaar; wij hebben gewoon een model.
     * (Dezelfde valkuil als bij het laadstation.)
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ChargerBlockEntity charger && tickLevel instanceof ServerLevel serverLevel) {
                charger.serverTick(serverLevel, pos, tickState);
            }
        };
    }

    /** Rechtsklikken: het scherm openen waarin je je bril neerlegt. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ChargerBlockEntity charger) {
            player.openMenu(charger);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Een nieuwe leverancier: de stroomnetten eromheen moeten opnieuw zoeken (zie StarkGrid). */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            StarkGrid.changed();
        }
    }

    /** Sloop je hem, dan vallen je bril en je brandstof eruit (en het net verandert). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            StarkGrid.changed();
            if (level.getBlockEntity(pos) instanceof ChargerBlockEntity charger) {
                Containers.dropContents(level, pos, charger);
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Een comparator ernaast leest hoe vol de buffer zit. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ChargerBlockEntity charger && charger.capacity() > 0) {
            return Math.round(15.0f * charger.energy() / charger.capacity());
        }
        return 0;
    }
}
