package com.senne.imttech.training;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Het Training Goal-blok (hoofdstuk 26): stapt de trainee erop, dan is de training geslaagd ("reached the goal").
 * Staat er een in de basis, dan is een training met een tijdslimiet mislukt als de tijd om is (je moest het doel
 * halen); zie TrainingGoals.
 */
public class TrainingGoalBlock extends BaseEntityBlock {
    public static final MapCodec<TrainingGoalBlock> CODEC = simpleCodec(TrainingGoalBlock::new);

    public TrainingGoalBlock(Properties properties) {
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
        return new TrainingGoalBlockEntity(pos, state);
    }

    /** Iemand stapt erop: is het de trainee, dan heeft hij het doel gehaald. */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (entity instanceof ServerPlayer player) {
            TrainingBlocks.goal(player, pos);
        }
    }
}
