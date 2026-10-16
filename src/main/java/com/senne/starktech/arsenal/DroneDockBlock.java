package com.senne.starktech.arsenal;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.energy.PowerConnectable;
import com.senne.starktech.energy.StarkGrid;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Een Drone Dock: een laag platform waar je security drone (hoofdstuk 25) op landt: zijn thuis en zijn lader. Zet
 * een Security Drone erop (rechtsklik met het item) en aangesloten op het stroomnet laadt hij daar zijn batterij
 * (zie DroneDockBlockEntity en GuardDroneEntity). Docks naast elkaar geven de stroom aan elkaar door, net als een
 * kabel. Een rechtsklik vertelt hoe het met zijn drone gaat.
 */
public class DroneDockBlock extends BaseEntityBlock implements PowerConnectable {
    public static final MapCodec<DroneDockBlock> CODEC = simpleCodec(DroneDockBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    public DroneDockBlock(Properties properties) {
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
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DroneDockBlockEntity(pos, state);
    }

    /** Wie hem neerzet, is de eigenaar. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof DroneDockBlockEntity dock) {
            dock.setOwner(player.getUUID());
        }
    }

    /** Rechtsklik: hoe gaat het met de drone op deze dock? (Boven je hotbar.) */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof DroneDockBlockEntity dock) {
            player.displayClientMessage(status(serverLevel, dock, player), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Hoe het met de drone van dit dock gaat: thuis en aan het laden, op pad, ver weg, of geen drone. */
    private static Component status(ServerLevel level, DroneDockBlockEntity dock, Player player) {
        UUID owner = dock.ownerId(level);
        if (owner == null && dock.bot() == null) {
            return Component.translatable("block.starktech.drone_dock.no_owner");
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) || !dock.mayUse(serverPlayer)) {
            return Component.translatable("block.starktech.drone_dock.not_yours");
        }
        if (dock.bot() == null) {
            return Component.translatable("block.starktech.drone_dock.empty");
        }
        if (level.getEntity(dock.bot()) instanceof com.senne.starktech.robot.GuardDroneEntity drone && drone.isAlive()) {
            int percent = Math.round(drone.batteryFraction() * 100);
            if (!drone.isOnDock()) {
                return Component.translatable("block.starktech.drone_dock.out", drone.displayName(), percent);
            }
            return Component.translatable(drone.isCharging() ? "block.starktech.drone_dock.charging"
                    : percent >= 100 ? "block.starktech.drone_dock.home" : "block.starktech.drone_dock.no_power",
                    drone.displayName(), percent);
        }
        var entry = com.senne.starktech.bot.BotNetwork.get(level).get(dock.bot());
        return entry == null ? Component.translatable("block.starktech.drone_dock.gone")
                : Component.translatable("block.starktech.drone_dock.far", entry.name());
    }

    /** Een dock geeft stroom door: erbij of eraf verandert het net (StarkGrid zoekt opnieuw). */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(this)) {
            StarkGrid.changed();
        }
    }

    /** Afgebroken: het stroomnet zoekt opnieuw (zijn drone merkt zelf dat zijn thuis weg is). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            StarkGrid.changed();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
