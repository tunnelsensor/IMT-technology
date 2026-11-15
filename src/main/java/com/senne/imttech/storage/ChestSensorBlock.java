package com.senne.imttech.storage;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.lan.LanGrid;
import com.senne.imttech.lan.NetworkConnectable;
import com.senne.imttech.lan.RouterLinks;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

/**
 * De Chest Sensor (hoofdstuk 113, jouw wens: "een chest sensor die je op elke storage (hopper, barrel, chest...) kunt
 * plaatsen om zo een live overzicht te hebben").
 *
 * Een plat kastje dat je op een kist, ton, trechter of ander blok met vakjes plakt (gewoon rechtsklikken met de sensor
 * in je hand: de kist gaat dan niet open). Hangt hij aan hetzelfde netwerk als een Storage Interface (een netwerkkabel,
 * of een Router binnen 32 blokken), dan ziet die interface altijd wat er in de kist zit. Hij heeft geen stroom nodig
 * (jouw wens: "deze sensoren voor de chest moeten geen stroom hebben, net zoals relais").
 *
 * {@link #FACING} wijst van de kist af: de kist staat aan de andere kant.
 */
public class ChestSensorBlock extends BaseEntityBlock implements NetworkConnectable {
    public static final MapCodec<ChestSensorBlock> CODEC = simpleCodec(ChestSensorBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** Een plat doosje van 6 bij 6 pixels en 2 dik, tegen het blok waar hij op zit. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.NORTH, Block.box(5, 5, 14, 11, 11, 16));
        SHAPES.put(Direction.SOUTH, Block.box(5, 5, 0, 11, 11, 2));
        SHAPES.put(Direction.WEST, Block.box(14, 5, 5, 16, 11, 11));
        SHAPES.put(Direction.EAST, Block.box(0, 5, 5, 2, 11, 11));
        SHAPES.put(Direction.UP, Block.box(5, 0, 5, 11, 2, 11));
        SHAPES.put(Direction.DOWN, Block.box(5, 14, 5, 11, 16, 11));
    }

    public ChestSensorBlock(Properties properties) {
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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Je loopt er gewoon langs (en een RC Car rijdt eronderdoor): hij houdt niemand tegen. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    /** De plek van het blok waar deze sensor op zit (de kist). */
    public static BlockPos target(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    /** Hij komt op de kant waar je klikt, en wijst van dat blok af. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /** Hij blijft alleen zitten op een blok met een binnenkant (een kist, een ton, een trechter, een machine). */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockEntity(target(state, pos)) != null;
    }

    /** De kist weg? Dan valt de sensor eraf. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos,
                                     BlockPos neighbourPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
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
        return new ChestSensorBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof ChestSensorBlockEntity sensor) {
            sensor.setOwner(player);
        }
    }

    /**
     * Rechtsklik op de sensor: hij zegt wat hij ziet. Op welke kist hij zit, hoeveel erin ligt, en aan welke Storage
     * Interface hij hangt (of dat hij nog geen netwerk heeft: dan weet je wat je moet doen).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos chest = target(state, pos);
        var slots = serverLevel.getCapability(Capabilities.ItemHandler.BLOCK, chest, null);
        if (slots == null) {
            serverPlayer.displayClientMessage(Component.translatable("message.imttech.chest_sensor.no_slots")
                    .withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.SUCCESS;
        }
        int items = 0;
        for (int slot = 0; slot < slots.getSlots(); slot++) {
            items += slots.getStackInSlot(slot).getCount();
        }
        StorageInterfaceBlockEntity station = Storages.stationOf(serverLevel, pos);
        Component block = serverLevel.getBlockState(chest).getBlock().getName();
        serverPlayer.displayClientMessage(station == null
                ? Component.translatable("message.imttech.chest_sensor.no_station", block, items).withStyle(ChatFormatting.GOLD)
                : Component.translatable("message.imttech.chest_sensor.linked", block, items, station.title())
                        .withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    /** Een netwerkblok erbij of eraf: de netten opnieuw zoeken. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            LanGrid.changed();
        }
    }

    /** Weg: hij hangt aan geen Router meer, en de netten worden opnieuw gezocht. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel serverLevel) {
                RouterLinks.get(serverLevel).forget(pos);
            }
            LanGrid.changed();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
