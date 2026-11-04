package com.senne.imttech.battery;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.energy.PowerConnectable;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
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
 * De Battery Port (hoofdstuk 47): een wandblok van de Power Battery waar de stroomkabels op aansluiten (en de kabels en
 * machines van andere mods, via Forge Energy). Zijn blokentiteit is het brein van de batterij: zie
 * {@link BatteryPortBlockEntity}. Rechtsklik opent het scherm met de stats van het hele stroomnet.
 */
public class BatteryPortBlock extends BaseEntityBlock implements PowerConnectable {
    public static final MapCodec<BatteryPortBlock> CODEC = simpleCodec(BatteryPortBlock::new);

    public BatteryPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BatteryPortBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.BATTERY_PORT.get(),
                (tickLevel, pos, tickState, port) -> port.serverTick((net.minecraft.server.level.ServerLevel) tickLevel));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            Batteries.open(level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            Batteries.changed(level, pos);
            PowerGrid.changed();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(state.getBlock())) {
            super.onRemove(state, level, pos, newState, movedByPiston);
            Batteries.changed(level, pos);
            PowerGrid.changed();
            return;
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
