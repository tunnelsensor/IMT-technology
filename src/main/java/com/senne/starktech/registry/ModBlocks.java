package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
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
}
