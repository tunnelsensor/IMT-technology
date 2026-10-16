package com.senne.starktech.training;

import com.senne.starktech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * De binnenkant van een Training Goal-blok: hij onthoudt niets, maar meldt zich aan en af bij TrainingGoals, zodat een
 * training weet of er een doel in de basis staat.
 */
public class TrainingGoalBlockEntity extends BlockEntity {
    public TrainingGoalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAINING_GOAL.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            TrainingGoals.add(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            TrainingGoals.remove(level, worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            TrainingGoals.remove(level, worldPosition);
        }
    }
}
