package com.senne.imttech.security.wall;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.energy.PowerConnectable;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.lan.NetworkConnectable;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * De Camera Monitor (19 oktober, zie CameraWall): een plat scherm voor aan de muur. Monitoren die tegen elkaar staan en
 * dezelfde kant op kijken, zijn samen één scherm. Hij heeft een netwerkkabel (of PoE-kabel) naar een Security Core nodig en een
 * beetje stroom; monitoren die tegen elkaar staan, geven allebei aan elkaar door, dus één kabel is genoeg voor het hele
 * scherm.
 *
 * Rechtsklik opent het instelscherm: welke camera waar staat, en hoe groot.
 */
public class CameraMonitorBlock extends BaseEntityBlock implements PowerConnectable, NetworkConnectable {
    public static final MapCodec<CameraMonitorBlock> CODEC = simpleCodec(CameraMonitorBlock::new);

    /** De kant waar het scherm naartoe kijkt. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Werkt hij (netwerk, stroom, een goed scherm)? Zie CameraMonitorBlockEntity.updateOn. */
    public static final BooleanProperty ON = BooleanProperty.create("on");
    /** Zit er een monitor van hetzelfde scherm aan deze kant (gezien vanaf de voorkant)? Dan geen rand aan die kant. */
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    /** Zo dik is het scherm (pixels); hij hangt tegen de achterkant van zijn blok. */
    public static final double DEPTH = 3.0;

    private static final VoxelShape NORTH = Block.box(0, 0, 16 - DEPTH, 16, 16, 16);
    private static final VoxelShape SOUTH = Block.box(0, 0, 0, 16, 16, DEPTH);
    private static final VoxelShape WEST = Block.box(16 - DEPTH, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST = Block.box(0, 0, 0, DEPTH, 16, 16);

    public CameraMonitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false)
                .setValue(UP, false).setValue(DOWN, false).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON, UP, DOWN, LEFT, RIGHT);
    }

    /** Geklikt op een muur: het scherm kijkt van die muur af. Anders kijkt het naar wie hem neerzet. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
        return connections(defaultBlockState().setValue(FACING, facing), context.getLevel(), context.getClickedPos());
    }

    /** Waar zitten er monitoren tegenaan die dezelfde kant op kijken? */
    private BlockState connections(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return state.setValue(UP, sameScreen(level, pos.above(), facing))
                .setValue(DOWN, sameScreen(level, pos.below(), facing))
                .setValue(LEFT, sameScreen(level, pos.relative(facing.getClockWise()), facing))
                .setValue(RIGHT, sameScreen(level, pos.relative(facing.getCounterClockWise()), facing));
    }

    private boolean sameScreen(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState other = level.getBlockState(pos);
        return other.is(this) && other.getValue(FACING) == facing;
    }

    /** Er veranderde iets naast de monitor: de randjes volgen. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return connections(state, level, pos);
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
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CameraMonitorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof CameraMonitorBlockEntity entity && tickLevel instanceof ServerLevel serverLevel) {
                entity.serverTick(serverLevel, pos);
            }
        };
    }

    // ================================================================ Neerzetten, gebruiken, weghalen

    /** Wie hem neerzet, mag hem instellen (zie BaseTrust). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof CameraMonitorBlockEntity entity) {
            if (placer instanceof ServerPlayer player) {
                entity.setOwner(player);
            }
            entity.updateOn(serverLevel);
        }
    }

    /** Neergezet: de netten (stroom en netwerk) lopen nu anders, en het scherm is anders. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            PowerGrid.changed();
            CameraWall.changed();
        }
    }

    /** Weggehaald: hetzelfde. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            PowerGrid.changed();
            CameraWall.changed();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Rechtsklik: het instelscherm (of waarom dat niet gaat). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            CameraWall.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.camera_monitor").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.camera_monitor.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
