package com.senne.imttech.storage;

import com.senne.imttech.security.BaseTrust;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van een Chest Sensor (zie ChestSensorBlock): alleen wie hem neerzette. Zo hangt niemand anders jouw
 * sensor aan zijn Router (zie Routers.mayLink) om in jouw kisten mee te kijken. Wat de sensor ziet, leest de Storage
 * Interface zelf uit de kist, telkens als iemand het scherm bekijkt: de sensor onthoudt niets.
 */
public class ChestSensorBlockEntity extends BlockEntity implements BaseTrust.Placed {
    private @Nullable UUID owner;
    private String ownerName = "";

    public ChestSensorBlockEntity(BlockPos pos, BlockState state) {
        super(StorageRegistry.CHEST_SENSOR_ENTITY.get(), pos, state);
    }

    @Override
    public @Nullable UUID owner() {
        return owner;
    }

    @Override
    public String ownerName() {
        return ownerName;
    }

    @Override
    public void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
    }
}
