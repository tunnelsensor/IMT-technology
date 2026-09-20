package com.senne.starktech.station;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** De warmtegenerator: maakt energie van brandstof én van de hitte om hem heen. */
public class HeatGeneratorBlock extends ChargerBlock {
    public static final MapCodec<HeatGeneratorBlock> CODEC = simpleCodec(HeatGeneratorBlock::new);

    public HeatGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HeatGeneratorBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeatGeneratorBlockEntity(pos, state);
    }
}
