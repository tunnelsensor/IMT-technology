package com.senne.starktech.station;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

/** Het zonnepaneel: een platte plaat die daglicht omzet in energie voor je bril. */
public class SolarPanelBlock extends ChargerBlock {
    public static final MapCodec<SolarPanelBlock> CODEC = simpleCodec(SolarPanelBlock::new);

    /** Een platte plaat van 6 pixels hoog: hij moet de lucht kunnen zien. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);

    public SolarPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends SolarPanelBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarPanelBlockEntity(pos, state);
    }
}
