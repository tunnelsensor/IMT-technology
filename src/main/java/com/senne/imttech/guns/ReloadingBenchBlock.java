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
 * De Reloading Bench (hoofdstuk 52, jouw wens: "een nieuwe craftingbench voor kogels: je moet een casing hebben en een
 * bullet, deze casing combineer je met gunpowder en de bullet en dat geeft je dan een 9mm bullet"). Rechtsklik opent
 * de pers: drie vakjes (huls, kogelpunt, buskruit) en het resultaat: één 9mm-patroon per keer (shift-klik perst er
 * meteen een stapel). Wat erin ligt, blijft liggen.
 */
public class ReloadingBenchBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ReloadingBenchBlock> CODEC = simpleCodec(ReloadingBenchBlock::new);

    public ReloadingBenchBlock(Properties properties) {
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
        return new ReloadingBenchBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ReloadingBenchBlockEntity bench) {
            serverPlayer.openMenu(bench, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ReloadingBenchBlockEntity bench) {
            Containers.dropContents(level, pos, bench.items());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
