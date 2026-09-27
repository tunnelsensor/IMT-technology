package com.senne.starktech.energy;

import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Van wie een stroomkast is. Dat telt alleen voor schakelen op afstand (het redstone-tabblad):
 * wie voor de kast staat, kan altijd schakelen.
 *
 * Een kast die je zelf neerzet, is van jou. Een kast in een Stark Tower (neergezet door de toren)
 * is van wie de toren gehackt heeft; die in de Hydra-bunker is van niemand.
 */
public class PowerCabinetBlockEntity extends BlockEntity {
    private @Nullable UUID owner;

    public PowerCabinetBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_CABINET.get(), pos, state);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** De eigenaar: wie hem neerzette, of anders wie de toren eromheen gehackt heeft (of niemand). */
    public @Nullable UUID ownerId(ServerLevel level) {
        if (owner != null) {
            return owner;
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, worldPosition);
        return core == null ? null : core.ownerId();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
}
