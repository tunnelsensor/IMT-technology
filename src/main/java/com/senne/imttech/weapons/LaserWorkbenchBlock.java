package com.senne.imttech.weapons;

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
 * De Laser Workbench (hoofdstuk 55, jouw keuze: "een nieuwe soort tafel op dezelfde manier als de Glock"). Rechtsklik
 * opent het scherm: het lasergeweer groot in het midden, links de onderdelen uit je inventaris die erin passen; slepen
 * en loslaten op de juiste plek (het klikt vast). Ook de Nether Star (de kern) en de Gun Sling steek je hier in. Zie
 * LaserWorkbenchBlockEntity (de regels) en client/weapons/LaserWorkbenchScreen (het slepen).
 */
public class LaserWorkbenchBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<LaserWorkbenchBlock> CODEC = simpleCodec(LaserWorkbenchBlock::new);

    public LaserWorkbenchBlock(Properties properties) {
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
        return new LaserWorkbenchBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof LaserWorkbenchBlockEntity bench) {
            serverPlayer.openMenu(bench, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Afgebroken: het geweer dat erop lag, valt eruit. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LaserWorkbenchBlockEntity bench) {
            Containers.dropContents(level, pos, bench.items());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
