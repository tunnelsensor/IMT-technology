package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.energy.StarkCableBlock;
import com.senne.starktech.redstone.RelayBlock;
import com.senne.starktech.shield.DomeGeneratorBlock;
import com.senne.starktech.holo.HoloTableBlock;
import com.senne.starktech.shield.LaserEmitterBlock;
import com.senne.starktech.shield.LaserWallBlock;
import com.senne.starktech.station.ChargingStationBlock;
import com.senne.starktech.station.SolarPanelBlock;
import com.senne.starktech.station.HeatGeneratorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Alle blokken van de mod.
 */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StarkTech.MODID);

    public static final DeferredBlock<ChargingStationBlock> CHARGING_STATION = BLOCKS.registerBlock("charging_station",
            ChargingStationBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    // Het blok geeft een beetje licht, zoals een scherm.
                    .lightLevel(state -> 7)
                    .noOcclusion());

    /**
     * Het E.D.I.T.H.-relais: een plat kastje dat redstone geeft als E.D.I.T.H. het aanzet.
     * Het lampje gloeit zachtjes als het aan staat.
     */
    public static final DeferredBlock<RelayBlock> RELAY = BLOCKS.registerBlock("relay",
            RelayBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 4.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RelayBlock.POWERED) ? 8 : 2)
                    .noOcclusion());

    /**
     * De Stark-kabel: verbindt zonnepanelen en warmtegeneratoren met alles wat stroom gebruikt.
     * Dun en snel te breken, net als een stuk draad.
     */
    public static final DeferredBlock<StarkCableBlock> CABLE = BLOCKS.registerBlock("cable",
            StarkCableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.5f, 3.0f)
                    .sound(SoundType.COPPER)
                    .noOcclusion());

    /** Het zonnepaneel: laadt je bril op met daglicht. */
    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL = BLOCKS.registerBlock("solar_panel",
            SolarPanelBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(2.5f, 5.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** De warmtegenerator: maakt energie van brandstof en van de hitte om hem heen. */
    public static final DeferredBlock<HeatGeneratorBlock> HEAT_GENERATOR = BLOCKS.registerBlock("heat_generator",
            HeatGeneratorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    // Alleen licht als hij werkt (dan gloeit zijn rooster).
                    .lightLevel(state -> state.getValue(HeatGeneratorBlock.LIT) ? 7 : 0));

    // ---------------------------------------------------------------- Fase 11: rayshield

    /** Fase 15: de holotafel, die een hologram boven zich projecteert. */
    public static final DeferredBlock<HoloTableBlock> HOLO_TABLE = BLOCKS.registerBlock("holo_table",
            HoloTableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 7)
                    .noOcclusion());

    /** De laserzender: twee tegenover elkaar maken een energiewand. */
    public static final DeferredBlock<LaserEmitterBlock> LASER_EMITTER = BLOCKS.registerBlock("laser_emitter",
            LaserEmitterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(50.0f, 1200.0f)   // zo sterk als obsidiaan
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(LaserEmitterBlock.POWERED) ? 10 : 3));

    /**
     * De energiewand zelf. Je kunt hem niet breken en hij valt nooit als item: hij hoort bij
     * de zenders. Daarom: -1 hardheid (onbreekbaar) en geen buit.
     */
    public static final DeferredBlock<LaserWallBlock> LASER_WALL = BLOCKS.registerBlock("laser_wall",
            LaserWallBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(-1.0f, 3600000.0f)
                    .noLootTable()
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 10)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false));

    /** De stroomkast: schakelaars voor de kabels die erop uitkomen (zie PowerCabinetBlock). */
    public static final DeferredBlock<com.senne.starktech.energy.PowerCabinetBlock> POWER_CABINET = BLOCKS.registerBlock(
            "power_cabinet", com.senne.starktech.energy.PowerCabinetBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 1200.0f)   // zwaar staal: explosies krijgen hem niet stuk
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** De schildkoepel-generator. */
    public static final DeferredBlock<DomeGeneratorBlock> DOME_GENERATOR = BLOCKS.registerBlock("dome_generator",
            DomeGeneratorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(DomeGeneratorBlock.POWERED) ? 12 : 3)
                    .noOcclusion());

    /** Het automatische torentje voor je basis (te maken met een Hydra-onderdeel). */
    public static final DeferredBlock<com.senne.starktech.turret.TurretBlock> STARK_TURRET = BLOCKS.registerBlock(
            "stark_turret", properties -> new com.senne.starktech.turret.TurretBlock(com.senne.starktech.turret.TurretKind.STARK, properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** Het vijandige torentje uit de Hydra-bunker. */
    public static final DeferredBlock<com.senne.starktech.turret.TurretBlock> HYDRA_TURRET = BLOCKS.registerBlock(
            "hydra_turret", properties -> new com.senne.starktech.turret.TurretBlock(com.senne.starktech.turret.TurretKind.HYDRA, properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(5.0f, 9.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** Het beveiligingstorentje van de Stark Tower (luistert naar de beveiligingskern). */
    public static final DeferredBlock<com.senne.starktech.turret.TurretBlock> SECURITY_TURRET = BLOCKS.registerBlock(
            "security_turret", properties -> new com.senne.starktech.turret.TurretBlock(
                    com.senne.starktech.turret.TurretKind.SECURITY, properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 9.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** De bewakingscamera: aan de muur, het plafond of de vloer; zijn beeld zie je op de Stark OS-desktop. */
    public static final DeferredBlock<com.senne.starktech.security.CameraBlock> SECURITY_CAMERA = BLOCKS.registerBlock(
            "security_camera", com.senne.starktech.security.CameraBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(com.senne.starktech.security.CameraBlock.ONLINE) ? 2 : 0));

    /** De beveiligingskern van de Stark Tower: heel sterk, zodat hacken de weg is. */
    public static final DeferredBlock<com.senne.starktech.security.SecurityCoreBlock> SECURITY_CORE = BLOCKS.registerBlock(
            "security_core", com.senne.starktech.security.SecurityCoreBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(50.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 4));

    /**
     * De Stark-laptop, opengeklapt op een beveiligingskern (daar loopt de hack). Het item is
     * StarkLaptopItem; dit blok ontstaat als je daarmee op de kern klikt.
     */
    public static final DeferredBlock<com.senne.starktech.security.StarkLaptopBlock> STARK_LAPTOP = BLOCKS.registerBlock(
            "stark_laptop", com.senne.starktech.security.StarkLaptopBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 5)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    /** De harnas-werkbank: het harnas bouwen en er upgrades in zetten (Tony's lab). */
    public static final DeferredBlock<com.senne.starktech.workbench.SuitWorkbenchBlock> SUIT_WORKBENCH = BLOCKS.registerBlock(
            "suit_workbench", com.senne.starktech.workbench.SuitWorkbenchBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Een drone-dock voor de hangar: hier staat een drone uit je arsenaal, en wordt hij gerepareerd. */
    public static final DeferredBlock<com.senne.starktech.arsenal.DroneDockBlock> DRONE_DOCK = BLOCKS.registerBlock(
            "drone_dock", com.senne.starktech.arsenal.DroneDockBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 5)
                    .noOcclusion());

    /** Het midden van een landingsplatform: hier land je met het harnas, en ben je thuis. */
    public static final DeferredBlock<com.senne.starktech.suit.LandingPadBlock> LANDING_PAD = BLOCKS.registerBlock(
            "landing_pad", com.senne.starktech.suit.LandingPadBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 10));
}
