package com.senne.starktech.training;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Het Training Start-blok (hoofdstuk 26, jouw antwoord: "een Training Start-blok; wie erop klikt is de trainee").
 * <ul>
 *   <li><b>Klik</b>: de training begint, met jou als trainee (alleen de eigenaar van de basis, zijn team en
 *       vertrouwde spelers). Loopt hij al, dan stopt een klik hem (van de trainee of iemand die vertrouwd is).</li>
 *   <li><b>Sneak + klik met een lege hand</b>: de instellingen (naam, trainingsschoten of lethal, levens,
 *       tijdslimiet), voor wie hem neerzette of vertrouwd is.</li>
 * </ul>
 * Het moet in het gebied van een beveiligingskern staan: die houdt de training bij (zie Training).
 */
public class TrainingStartBlock extends BaseEntityBlock {
    public static final MapCodec<TrainingStartBlock> CODEC = simpleCodec(TrainingStartBlock::new);

    public TrainingStartBlock(Properties properties) {
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
        return new TrainingStartBlockEntity(pos, state);
    }

    /** Wie hem neerzet, mag hem instellen. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof TrainingStartBlockEntity start) {
            start.setOwner(player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            TrainingBlocks.useStart(serverPlayer, pos, player.isSecondaryUseActive());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
