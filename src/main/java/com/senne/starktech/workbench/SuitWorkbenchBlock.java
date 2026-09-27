package com.senne.starktech.workbench;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.energy.PowerConnectable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * De harnas-werkbank (Tony's lab): hier bouw je het Stark-harnas en bouw je er upgrades in.
 * Dat gaat niet met een scherm, maar met het hologram dat erboven zweeft (zie
 * SuitWorkbenchBlockEntity). Hij heeft stroom nodig van het net.
 */
public class SuitWorkbenchBlock extends BaseEntityBlock implements PowerConnectable {
    public static final MapCodec<SuitWorkbenchBlock> CODEC = simpleCodec(SuitWorkbenchBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public SuitWorkbenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SuitWorkbenchBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof SuitWorkbenchBlockEntity bench && tickLevel instanceof ServerLevel serverLevel) {
                bench.serverTick(serverLevel, pos);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        // Op het blok zelf klikken doet niets; het werk gebeurt op het hologram erboven.
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("workbench.starktech.hint"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Afgebroken: alles wat erin lag valt eruit. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SuitWorkbenchBlockEntity bench) {
            bench.dropAll(level);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
