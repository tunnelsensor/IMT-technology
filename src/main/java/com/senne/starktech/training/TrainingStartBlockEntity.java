package com.senne.starktech.training;

import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.security.BaseTrust;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van een Training Start-blok (hoofdstuk 26): de instellingen van zijn training (naam, trainingsschoten
 * of lethal, levens, tijdslimiet) en wie hem neerzette (die mag hem instellen, net als wie in de basis vertrouwd is;
 * zie BaseTrust).
 */
public class TrainingStartBlockEntity extends BlockEntity implements BaseTrust.Placed {
    private @Nullable UUID owner;
    private String ownerName = "";
    private Training.Settings settings = Training.Settings.DEFAULT;

    public TrainingStartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAINING_START.get(), pos, state);
    }

    public Training.Settings settings() {
        return settings;
    }

    public void setSettings(Training.Settings settings) {
        this.settings = settings.clean();
        setChanged();
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
        tag.put("Settings", settings.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        settings = Training.Settings.load(tag.getCompound("Settings"));
    }
}
