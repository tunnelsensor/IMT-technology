package com.senne.starktech.training;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.config.StarkConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Het Training End-blok (hoofdstuk 26, jouw wens: "een Training End-blok geeft redstone als de trainee geraakt wordt,
 * en daarin programmeer je wat er dan gebeurt"). Bij elke treffer in een training van zijn basis:
 * <ul>
 *   <li>een redstone-puls ({@code endPulse} ticks; het lampje op de zijkanten brandt dan rood);</li>
 *   <li>zijn eigen programmaatje loopt ({@code when trainee_hit:}, met {@code trainee}, {@code lives} en {@code hits}), op je
 *       server (met de grenzen van zijn RAM), bijvoorbeeld {@code if lives == 0: deactivate("Wave 1")}.</li>
 * </ul>
 * Sneak + klik met een lege hand: de mini-editor (voor wie hem neerzette of vertrouwd is in de basis).
 */
public class TrainingEndBlock extends BaseEntityBlock {
    public static final MapCodec<TrainingEndBlock> CODEC = simpleCodec(TrainingEndBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public TrainingEndBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrainingEndBlockEntity(pos, state);
    }

    /** Wie hem neerzet, mag zijn programma schrijven. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof TrainingEndBlockEntity end) {
            end.setOwner(player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            TrainingBlocks.useEnd(serverPlayer, pos, player.isSecondaryUseActive());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ================================================================ Redstone

    /** Een treffer: even redstone (en het lampje brandt). */
    public static void pulse(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TrainingEndBlock block)) {
            return;
        }
        if (!state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, block, Math.max(1, Math.round(StarkConfig.Training.END_PULSE)));
    }

    /** De puls is voorbij. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }
}
