package com.senne.starktech.robot;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.energy.PowerConnectable;
import com.senne.starktech.energy.StarkGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Het Robot Dock (hoofdstuk 21): het laadstation van één guard robot. Een lage plaat met achteraan een paal
 * met een stekker; de robot staat op de plaat met zijn rug tegen de paal. Aangesloten op het stroomnet
 * (een kabel of een generator ernaast) laadt het zijn robot.
 *
 * Zet je een Guard Robot-item op het dock, dan is de robot hier thuis: staat hij uit, dan loopt hij hierheen
 * en laadt hij; zet je hem aan, dan stapt hij er naar voren uit ({@link #FACING} wijst naar voren).
 */
public class RobotDockBlock extends BaseEntityBlock implements PowerConnectable {
    public static final MapCodec<RobotDockBlock> CODEC = simpleCodec(RobotDockBlock::new);
    /** Waar de voorkant (de open kant) naartoe wijst: daar stapt de robot uit. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** De plaat, en de paal achteraan (voor een dock dat naar het noorden kijkt). */
    private static final VoxelShape PLATE = Block.box(0, 0, 0, 16, 3, 16);
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            // De paal staat aan de achterkant, tegenover FACING.
            VoxelShape pole = switch (facing) {
                case NORTH -> Block.box(5, 3, 13, 11, 16, 16);
                case SOUTH -> Block.box(5, 3, 0, 11, 16, 3);
                case WEST -> Block.box(13, 3, 5, 16, 16, 11);
                default -> Block.box(0, 3, 5, 3, 16, 11);
            };
            SHAPES[facing.get2DDataValue()] = Shapes.or(PLATE, pole);
        }
    }

    public RobotDockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** De open kant kijkt naar wie hem neerzet. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RobotDockBlockEntity(pos, state);
    }

    /** Nieuw of weg: het stroomnet zoekt opnieuw wie er aan hangt. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        StarkGrid.changed();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock())) {
            StarkGrid.changed();
        }
    }
}
