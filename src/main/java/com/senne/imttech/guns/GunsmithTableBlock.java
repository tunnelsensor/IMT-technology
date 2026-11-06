package com.senne.imttech.guns;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * De Gunsmith Table (hoofdstuk 52, jouw wens: "een tafel met de onderdelen die je kunt gebruiken en in je inventory
 * hebt; dan versleep je de onderdelen naar waar het moet komen"). Rechtsklik opent het scherm: het pistool groot in
 * het midden, links de onderdelen uit je inventaris die erin passen; slepen en loslaten op de juiste plek (het klikt
 * vast). Zie GunsmithTableBlockEntity (de regels) en client/guns/GunsmithScreen (het slepen).
 */
public class GunsmithTableBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<GunsmithTableBlock> CODEC = simpleCodec(GunsmithTableBlock::new);

    public GunsmithTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GunsmithTableBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof GunsmithTableBlockEntity table) {
            serverPlayer.openMenu(table, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Afgebroken: het pistool dat erop lag, valt eruit. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GunsmithTableBlockEntity table) {
            Containers.dropContents(level, pos, table.items());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
