package com.senne.starktech.robot;

import com.senne.starktech.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Het Robot Dock onthoudt welke robot hier thuis is (hoofdstuk 21). Het laden zelf doet de robot: die weet
 * waar zijn dock staat en betaalt de stroom via het dock (zie GuardRobotEntity).
 */
public class RobotDockBlockEntity extends BlockEntity {
    private @Nullable UUID robot;

    public RobotDockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROBOT_DOCK.get(), pos, state);
    }

    /** De robot die hier thuis is, of null. */
    public @Nullable UUID robot() {
        return robot;
    }

    public void setRobot(@Nullable UUID robot) {
        this.robot = robot;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (robot != null) {
            tag.putUUID("Robot", robot);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        robot = tag.hasUUID("Robot") ? tag.getUUID("Robot") : null;
    }
}
