package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.holo.HoloTableBlockEntity;
import com.senne.starktech.shield.DomeGeneratorBlockEntity;
import com.senne.starktech.station.ChargingStationBlockEntity;
import com.senne.starktech.station.SolarPanelBlockEntity;
import com.senne.starktech.station.HeatGeneratorBlockEntity;
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
                    () -> new BlockEntityType<>(ChargingStationBlockEntity::new,
                            java.util.Set.of(ModBlocks.CHARGING_STATION.get()), null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
            BLOCK_ENTITIES.register("solar_panel",
                    () -> new BlockEntityType<>(SolarPanelBlockEntity::new,
                            java.util.Set.of(ModBlocks.SOLAR_PANEL.get()), null));

    /** De schildkoepel-generator: zijn instellingen, schildkracht en of de koepel staat. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DomeGeneratorBlockEntity>> DOME_GENERATOR =
            BLOCK_ENTITIES.register("dome_generator",
                    () -> new BlockEntityType<>(DomeGeneratorBlockEntity::new,
                            java.util.Set.of(ModBlocks.DOME_GENERATOR.get()), null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HoloTableBlockEntity>> HOLO_TABLE =
            BLOCK_ENTITIES.register("holo_table",
                    () -> new BlockEntityType<>(HoloTableBlockEntity::new,
                            java.util.Set.of(ModBlocks.HOLO_TABLE.get()), null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeatGeneratorBlockEntity>> HEAT_GENERATOR =
            BLOCK_ENTITIES.register("heat_generator",
                    () -> new BlockEntityType<>(HeatGeneratorBlockEntity::new,
                            java.util.Set.of(ModBlocks.HEAT_GENERATOR.get()), null));

    /** Beide torentjes (Stark en Hydra) gebruiken dezelfde binnenkant. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.turret.TurretBlockEntity>> TURRET =
            BLOCK_ENTITIES.register("turret",
                    () -> new BlockEntityType<>(com.senne.starktech.turret.TurretBlockEntity::new,
                            java.util.Set.of(ModBlocks.STARK_TURRET.get(), ModBlocks.HYDRA_TURRET.get(),
                                    ModBlocks.SECURITY_TURRET.get()), null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.security.SecurityCoreBlockEntity>> SECURITY_CORE =
            BLOCK_ENTITIES.register("security_core",
                    () -> new BlockEntityType<>(com.senne.starktech.security.SecurityCoreBlockEntity::new,
                            java.util.Set.of(ModBlocks.SECURITY_CORE.get()), null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.workbench.SuitWorkbenchBlockEntity>> SUIT_WORKBENCH =
            BLOCK_ENTITIES.register("suit_workbench",
                    () -> new BlockEntityType<>(com.senne.starktech.workbench.SuitWorkbenchBlockEntity::new,
                            java.util.Set.of(ModBlocks.SUIT_WORKBENCH.get()), null));
}
