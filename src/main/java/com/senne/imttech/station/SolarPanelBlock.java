package com.senne.imttech.station;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

/**
 * Het zonnepaneel: een paneel op een paaltje dat daglicht omzet in energie voor je bril.
 * Het paneel staat schuin (naar het zuiden), net als echte zonnepanelen.
 */
public class SolarPanelBlock extends ChargerBlock {
    public static final MapCodec<SolarPanelBlock> CODEC = simpleCodec(SolarPanelBlock::new);

    /**
     * Zo hoog als het schuine paneel (9 pixels): lager dan een trede, dus je loopt er nog
     * gewoon overheen, net als over een halve plaat.
     */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 9, 16);

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
