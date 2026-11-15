package com.senne.imttech.storage;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.lan.LanGrid;
import com.senne.imttech.lan.NetworkConnectable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * De Storage Interface (hoofdstuk 113, jouw wens: "een storage system via de robots: je kunt een item orderen via een
 * storage interface (GUI), deze interface activeert een robot die je hebt geprogrammeerd").
 *
 * Een kast met een scherm. Rechtsklik: je ziet wat er in je opslag zit, bestelt een item (een gekoppelde RC Car haalt
 * het), of legt spullen in om op te bergen. Wat er achter het scherm gebeurt, staat in
 * {@link StorageInterfaceBlockEntity}. Het is een netwerkblok (zijn Chest Sensors hangen aan hetzelfde netwerk: een
 * kabel, of een Router), maar het heeft geen stroom nodig.
 */
public class StorageInterfaceBlock extends BaseEntityBlock implements NetworkConnectable {
    public static final MapCodec<StorageInterfaceBlock> CODEC = simpleCodec(StorageInterfaceBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public StorageInterfaceBlock(Properties properties) {
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

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Het scherm kijkt naar wie hem neerzet. */
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageInterfaceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof StorageInterfaceBlockEntity station && tickLevel instanceof ServerLevel serverLevel) {
                station.serverTick(serverLevel);
            }
        };
    }

    /** Wie hem neerzet is de eigenaar; een naam van het aambeeld gaat mee. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof StorageInterfaceBlockEntity station) {
            if (placer instanceof ServerPlayer player) {
                station.setOwner(player);
            }
            Component custom = stack.get(DataComponents.CUSTOM_NAME);
            if (custom != null) {
                station.setName(custom.getString());
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof StorageInterfaceBlockEntity station) {
            Storages.open(serverPlayer, station);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Een netwerkblok erbij of eraf: de netten opnieuw zoeken. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            LanGrid.changed();
        }
    }

    /** Afgebroken: wat er in zijn vakjes lag, valt op de grond. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof StorageInterfaceBlockEntity station && level instanceof ServerLevel) {
                Containers.dropContents(level, pos, station.in());
                Containers.dropContents(level, pos, station.out());
            }
            LanGrid.changed();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
