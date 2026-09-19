package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.redstone.RelayBlock;
import com.senne.starktech.shield.DomeGeneratorBlock;
import com.senne.starktech.shield.LaserEmitterBlock;
import com.senne.starktech.shield.LaserWallBlock;
import com.senne.starktech.station.ChargingStationBlock;
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

    // ---------------------------------------------------------------- Fase 11: rayshield

    /** De laserzender: twee tegenover elkaar maken een energiewand. */
    public static final DeferredBlock<LaserEmitterBlock> LASER_EMITTER = BLOCKS.registerBlock("laser_emitter",
            LaserEmitterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(LaserEmitterBlock.POWERED) ? 10 : 3)
                    .noOcclusion());

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
}
