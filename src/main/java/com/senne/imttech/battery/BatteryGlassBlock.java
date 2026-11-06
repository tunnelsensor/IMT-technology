package com.senne.imttech.battery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Battery Glass dat aan elkaar hangt, zoals het framed glass van Create (hoofdstuk 54, jouw notitie). Zes vlaggetjes
 * (north, east, south, west, up, down) zeggen of er aan die kant ook Battery Glass zit; het model tekent het stalen
 * randje alleen op de kanten waar dat niet zo is (zie blockstates/battery_glass.json: een "multipart" met per vlak vier
 * randstukjes). Zo wordt een wand van glas één groot raam met één rand eromheen.
 */
public class BatteryGlassBlock extends BatteryCasingBlock {
    public BatteryGlassBlock(Properties properties) {
        super(properties, true);
        BlockState state = stateDefinition.any();
        for (BooleanProperty link : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(link, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.NORTH, BlockStateProperties.EAST, BlockStateProperties.SOUTH,
                BlockStateProperties.WEST, BlockStateProperties.UP, BlockStateProperties.DOWN);
    }

    /** Dezelfde toestand, met de vlaggetjes zoals de buren nu zijn. */
    private static BlockState linked(BlockState state, LevelAccessor level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            boolean same = level.getBlockState(pos.relative(direction)).is(state.getBlock());
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), same);
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return linked(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), neighbourState.is(this));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Neergezet zonder naar de buren te kijken (een structuur, een commando, een test)? Dan nu alsnog verbinden.
        BlockState linked = linked(state, level, pos);
        if (linked != state) {
            level.setBlock(pos, linked, Block.UPDATE_CLIENTS);
        }
    }
}
