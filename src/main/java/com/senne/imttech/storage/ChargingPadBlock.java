package com.senne.imttech.storage;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.energy.PowerConnectable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * De Charging Pad (hoofdstuk 113, jouw keuze: een laadplaat): een dunne plaat op de vloer die de energiecel laadt van de
 * RC Car die erop staat, met stroom van het net (een stroomkabel ernaast, of een generator ertegen). Zo hoef je de cel
 * van je opslag-auto nooit meer met de hand te wisselen: laat zijn programma naar de plaat rijden en wachten tot
 * {@code car.battery} hoog genoeg is.
 *
 * De plaat is twee pixels dik: de auto rijdt er gewoon op (hij staat dan in het blok van de plaat zelf).
 */
public class ChargingPadBlock extends BaseEntityBlock implements PowerConnectable {
    public static final MapCodec<ChargingPadBlock> CODEC = simpleCodec(ChargingPadBlock::new);
    /** Laadt hij nu een auto? Dan gloeien zijn strepen. */
    public static final BooleanProperty CHARGING = BooleanProperty.create("charging");

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public ChargingPadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargingPadBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ChargingPadBlockEntity pad && tickLevel instanceof ServerLevel serverLevel) {
                pad.serverTick(serverLevel, pos, tickState);
            }
        };
    }
}
