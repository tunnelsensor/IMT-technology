package com.senne.imttech.robot;

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
 * De Robot Assembler (hoofdstuk 21): een werkbank waarop je een guard robot in elkaar zet. Rechtsklik opent
 * zijn scherm: vier vakjes voor de onderdelen, één voor de printplaat, een draaiend voorbeeld van de robot,
 * en de knoppen Assemble en Take apart (zie RobotAssemblerBlockEntity).
 */
public class RobotAssemblerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<RobotAssemblerBlock> CODEC = simpleCodec(RobotAssemblerBlock::new);

    public RobotAssemblerBlock(Properties properties) {
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

    /** Het scherm aan de voorkant kijkt naar wie hem neerzet. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RobotAssemblerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof RobotAssemblerBlockEntity assembler) {
            serverPlayer.openMenu(assembler, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Afgebroken: wat erin lag, valt eruit. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof RobotAssemblerBlockEntity assembler) {
            Containers.dropContents(level, pos, assembler.items());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
