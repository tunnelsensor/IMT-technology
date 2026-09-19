package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.redstone.RelayBlock;
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
}
