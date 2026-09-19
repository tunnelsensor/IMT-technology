package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.station.ChargingStationBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * "Block entities" zijn blokken met extra gegevens, zoals een inventaris of opgeslagen energie.
 */
public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StarkTech.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargingStationBlockEntity>> CHARGING_STATION =
            BLOCK_ENTITIES.register("charging_station",
                    () -> new BlockEntityType<>(ChargingStationBlockEntity::new, ModBlocks.CHARGING_STATION.get()));
}
