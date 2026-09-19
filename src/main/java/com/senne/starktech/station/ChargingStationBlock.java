package com.senne.starktech.station;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Het Stark-laadstation: laadt de bril op met redstone en bouwt upgrades in.
 */
public class ChargingStationBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * Elk blok moet in 1.21.1 kunnen zeggen hoe het opgeslagen wordt (bv. in structuren).
     * simpleCodec regelt dat voor ons.
     */
    public static final MapCodec<ChargingStationBlock> CODEC = simpleCodec(ChargingStationBlock::new);

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** De vorm van het blok (voor botsingen en de zwarte lijntjes als je ernaar kijkt). */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),   // voet
            Block.box(4, 3, 4, 12, 10, 12),  // zuil
            Block.box(2, 10, 2, 14, 12, 14)  // bovenplaat
    );

    public ChargingStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // ---------------------------------------------------------------- Draairichting

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // De voorkant kijkt naar de speler die het blok plaatst.
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
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ---------------------------------------------------------------- Block entity

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargingStationBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Alleen de server rekent; de client krijgt de resultaten gestuurd.
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntities.CHARGING_STATION.get(),
                        (lvl, pos, st, station) -> station.serverTick(serverLevel, pos, st))
                : null;
    }

    // ---------------------------------------------------------------- Rechtsklikken opent het scherm

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ChargingStationBlockEntity station) {
            player.openMenu(station);
        }
        return InteractionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- Comparator-signaal = hoe vol het station is

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    // ---------------------------------------------------------------- Blauwe vonkjes als decoratie

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
            double y = pos.getY() + 0.8 + random.nextDouble() * 0.3;
            double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0.02, 0);
        }
    }
}
