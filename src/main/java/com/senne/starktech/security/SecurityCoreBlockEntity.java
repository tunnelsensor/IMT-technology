package com.senne.starktech.security;

import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.registry.ModEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * De "binnenkant" van een beveiligingskern: van wie de toren is (niemand, tot iemand hem hackt),
 * en de beveiligingsdrones die de kern in de lucht houdt.
 *
 * Elke twee seconden telt de kern zijn drones. Zijn het er minder dan MAX_DRONES (eentje
 * neergeschoten), dan stuurt hij na DRONE_RESPAWN ticks een nieuwe. Zo blijft een toren die van
 * niemand is gevaarlijk; na het hacken zijn diezelfde drones jouw bewakers.
 */
public class SecurityCoreBlockEntity extends BlockEntity {
    private @Nullable UUID ownerId;
    private String ownerName = "";
    /** Tot wanneer (speltijd) hij wacht voor hij een nieuwe drone stuurt. */
    private long nextDrone;

    public SecurityCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SECURITY_CORE.get(), pos, state);
    }

    public @Nullable UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    /** De toren is van deze speler (na het hacken met de laptop). */
    public void setOwner(ServerPlayer player) {
        ownerId = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
        if (level != null) {
            level.setBlock(worldPosition, getBlockState().setValue(SecurityCoreBlock.CLAIMED, true), Block.UPDATE_ALL);
        }
    }

    // ================================================================ Aanmelden

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            SecurityCores.loaded(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            SecurityCores.unloaded(level, worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            SecurityCores.unloaded(level, worldPosition);
        }
    }

    // ================================================================ Drones

    public void serverTick(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        if ((now + pos.hashCode()) % 40 != 0) {
            return;
        }
        int drones = level.getEntitiesOfClass(SecurityDroneEntity.class, area(),
                drone -> pos.equals(drone.home())).size();
        if (drones >= SecurityBalance.MAX_DRONES) {
            filled = true;
            nextDrone = 0;
            return;
        }
        if (!filled) {
            // De allereerste keer: de drones komen snel achter elkaar (één per twee seconden).
            launchDrone(level, pos);
            return;
        }
        // Er is er een neergeschoten: na DRONE_RESPAWN ticks komt er een nieuwe.
        if (nextDrone == 0) {
            nextDrone = now + SecurityBalance.DRONE_RESPAWN;
        } else if (now >= nextDrone) {
            launchDrone(level, pos);
            nextDrone = 0;
        }
    }

    /** Zijn de drones al eens allemaal in de lucht geweest? Daarna gaat vervangen langzaam. */
    private boolean filled;

    /** Een nieuwe drone op een vrije plek vlak bij de kern. */
    private void launchDrone(ServerLevel level, BlockPos pos) {
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos spot = pos.offset(level.random.nextInt(13) - 6, 1 + level.random.nextInt(3),
                    level.random.nextInt(13) - 6);
            if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.above()).isAir()) {
                continue;
            }
            SecurityDroneEntity drone = ModEntities.SECURITY_DRONE.get().create(level);
            if (drone == null) {
                return;
            }
            drone.moveTo(spot.getX() + 0.5, spot.getY() + 0.3, spot.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            drone.setHome(pos);
            level.addFreshEntity(drone);
            setChanged();
            return;
        }
    }

    /** Het gebied dat deze kern beschermt. */
    public AABB area() {
        return new AABB(worldPosition.getX() - SecurityBalance.AREA_RADIUS, worldPosition.getY() - SecurityBalance.AREA_DOWN,
                worldPosition.getZ() - SecurityBalance.AREA_RADIUS, worldPosition.getX() + SecurityBalance.AREA_RADIUS + 1,
                worldPosition.getY() + SecurityBalance.AREA_UP + 1, worldPosition.getZ() + SecurityBalance.AREA_RADIUS + 1);
    }

    // ================================================================ Opslaan

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putString("OwnerName", ownerName);
        tag.putBoolean("Filled", filled);
        tag.putLong("NextDrone", nextDrone);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        filled = tag.getBoolean("Filled");
        nextDrone = tag.getLong("NextDrone");
    }
}
