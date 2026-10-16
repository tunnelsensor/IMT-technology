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

    /** De stroomkast: van wie hij is (voor schakelen op afstand). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.energy.PowerCabinetBlockEntity>> POWER_CABINET =
            BLOCK_ENTITIES.register("power_cabinet",
                    () -> new BlockEntityType<>(com.senne.starktech.energy.PowerCabinetBlockEntity::new,
                            java.util.Set.of(ModBlocks.POWER_CABINET.get()), null));

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

    /** Een lading C4: wanneer hij afgaat, wie hem plakte, bij welke detonator hij hoort (hoofdstuk 10). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.c4.C4BlockEntity>> C4 =
            BLOCK_ENTITIES.register("c4",
                    () -> new BlockEntityType<>(com.senne.starktech.c4.C4BlockEntity::new,
                            java.util.Set.of(ModBlocks.TIMED_C4.get(), ModBlocks.REMOTE_C4.get()), null));

    /** De binnenkant van een Crusher of Compactor (hoofdstuk 11): zijn twee vakjes en hoe ver hij is. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.repair.CrackMachineBlockEntity>> CRACK_MACHINE =
            BLOCK_ENTITIES.register("crack_machine",
                    () -> new BlockEntityType<>(com.senne.starktech.repair.CrackMachineBlockEntity::new,
                            java.util.Set.of(ModBlocks.CRUSHER.get(), ModBlocks.COMPACTOR.get()), null));

    /** De bewakingscamera: zijn naam en hoe hij gedraaid en gekanteld is. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.security.CameraBlockEntity>> SECURITY_CAMERA =
            BLOCK_ENTITIES.register("security_camera",
                    () -> new BlockEntityType<>(com.senne.starktech.security.CameraBlockEntity::new,
                            java.util.Set.of(ModBlocks.SECURITY_CAMERA.get()), null));

    /** Het serverrack: zijn vier vakjes (CPU, RAM, SSD) en of hij meedraait. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.logic.ServerRackBlockEntity>> SERVER_RACK =
            BLOCK_ENTITIES.register("server_rack",
                    () -> new BlockEntityType<>(com.senne.starktech.logic.ServerRackBlockEntity::new,
                            java.util.Set.of(ModBlocks.SERVER_RACK.get()), null));

    /** Een keycardlezer of codepaneel: alleen zijn naam. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.logic.InputDeviceBlockEntity>> INPUT_DEVICE =
            BLOCK_ENTITIES.register("input_device",
                    () -> new BlockEntityType<>(com.senne.starktech.logic.InputDeviceBlockEntity::new,
                            java.util.Set.of(ModBlocks.KEYCARD_READER.get(), ModBlocks.CODE_PANEL.get()), null));

    /** De laptop op een kern: van wie hij is, en wat er al getypt is (de voortgang van de hack). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.security.StarkLaptopBlockEntity>> STARK_LAPTOP =
            BLOCK_ENTITIES.register("stark_laptop",
                    () -> new BlockEntityType<>(com.senne.starktech.security.StarkLaptopBlockEntity::new,
                            java.util.Set.of(ModBlocks.STARK_LAPTOP.get()), null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.workbench.SuitWorkbenchBlockEntity>> SUIT_WORKBENCH =
            BLOCK_ENTITIES.register("suit_workbench",
                    () -> new BlockEntityType<>(com.senne.starktech.workbench.SuitWorkbenchBlockEntity::new,
                            java.util.Set.of(ModBlocks.SUIT_WORKBENCH.get()), null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.arsenal.DroneDockBlockEntity>> DRONE_DOCK =
            BLOCK_ENTITIES.register("drone_dock",
                    () -> new BlockEntityType<>(com.senne.starktech.arsenal.DroneDockBlockEntity::new,
                            java.util.Set.of(ModBlocks.DRONE_DOCK.get()), null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.suit.LandingPadBlockEntity>> LANDING_PAD =
            BLOCK_ENTITIES.register("landing_pad",
                    () -> new BlockEntityType<>(com.senne.starktech.suit.LandingPadBlockEntity::new,
                            java.util.Set.of(ModBlocks.LANDING_PAD.get()), null));

    /** De Arc Lamp: haalt elke tick stroom en zet hem aan of uit (9 oktober). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.light.ArcLampBlockEntity>> ARC_LAMP =
            BLOCK_ENTITIES.register("arc_lamp",
                    () -> new BlockEntityType<>(com.senne.starktech.light.ArcLampBlockEntity::new,
                            java.util.Set.of(ModBlocks.ARC_LAMP.get()), null));

    /** Het E.D.I.T.H.-relais: van wie het is (13 oktober: alleen de eigenaar kan het bewerken). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.redstone.RelayBlockEntity>> RELAY =
            BLOCK_ENTITIES.register("relay",
                    () -> new BlockEntityType<>(com.senne.starktech.redstone.RelayBlockEntity::new,
                            java.util.Set.of(ModBlocks.RELAY.get()), null));

    /** De relay emitter: van wie hij is, welke relais hij schakelt en hoe (13 oktober). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.redstone.RelayEmitterBlockEntity>> RELAY_EMITTER =
            BLOCK_ENTITIES.register("relay_emitter",
                    () -> new BlockEntityType<>(com.senne.starktech.redstone.RelayEmitterBlockEntity::new,
                            java.util.Set.of(ModBlocks.RELAY_EMITTER.get()), null));

    /** De laserzender: van wie hij is (13 oktober: alleen hij en vertrouwde spelers openen zijn scherm). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.shield.LaserEmitterBlockEntity>> LASER_EMITTER =
            BLOCK_ENTITIES.register("laser_emitter",
                    () -> new BlockEntityType<>(com.senne.starktech.shield.LaserEmitterBlockEntity::new,
                            java.util.Set.of(ModBlocks.LASER_EMITTER.get()), null));

    /** De Robot Assembler (hoofdstuk 21): zijn zes vakjes. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.robot.RobotAssemblerBlockEntity>> ROBOT_ASSEMBLER =
            BLOCK_ENTITIES.register("robot_assembler",
                    () -> new BlockEntityType<>(com.senne.starktech.robot.RobotAssemblerBlockEntity::new,
                            java.util.Set.of(ModBlocks.ROBOT_ASSEMBLER.get()), null));

    /** Het Robot Dock (hoofdstuk 21): welke robot hier thuis is. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.senne.starktech.robot.RobotDockBlockEntity>> ROBOT_DOCK =
            BLOCK_ENTITIES.register("robot_dock",
                    () -> new BlockEntityType<>(com.senne.starktech.robot.RobotDockBlockEntity::new,
                            java.util.Set.of(ModBlocks.ROBOT_DOCK.get()), null));
}
