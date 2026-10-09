package com.senne.starktech.c4;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Een lading C4 die tegen een vloer, muur of plafond geplakt zit (hoofdstuk 10, 8 oktober: "C4 die
 * alles binnen of buiten zo een zone kan laten ontploffen").
 *
 * Er zijn twee soorten: {@link Kind#TIMED} (je stelt de tijd in op het item; zodra je hem plakt, loopt
 * hij) en {@link Kind#REMOTE} (hij wacht tot iemand op de detonator drukt). De klap zelf staat in
 * {@link C4Blast}; de timer in {@link C4BlockEntity}.
 *
 * Met je hand krijg je hem er niet af, ook niet als je hem zelf plakte: alleen met een schaar knip je
 * de draad door (zie hoofdstuk 10, stap 7). Een explosie maakt hem ook niet kapot: dan gaat hij zelf
 * af (een kettingreactie). En hij blijft zitten, ook als het blok achter hem weg is: het is plakkerige
 * springstof.
 */
public class C4Block extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    /** De twee soorten C4. */
    public enum Kind { TIMED, REMOTE }

    public static final MapCodec<C4Block> TIMED_CODEC = simpleCodec(properties -> new C4Block(Kind.TIMED, properties));
    public static final MapCodec<C4Block> REMOTE_CODEC = simpleCodec(properties -> new C4Block(Kind.REMOTE, properties));

    /** De vorm (voor het aanklikken), per kant waar hij tegenaan plakt: een plat pakketje van 10 x 10. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.DOWN, Block.box(3, 0, 3, 13, 5, 13));
        SHAPES.put(Direction.UP, Block.box(3, 11, 3, 13, 16, 13));
        SHAPES.put(Direction.NORTH, Block.box(3, 3, 0, 13, 13, 5));
        SHAPES.put(Direction.SOUTH, Block.box(3, 3, 11, 13, 13, 16));
        SHAPES.put(Direction.WEST, Block.box(0, 3, 3, 5, 13, 13));
        SHAPES.put(Direction.EAST, Block.box(11, 3, 3, 16, 13, 13));
    }

    private final Kind kind;

    public C4Block(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                .setValue(FACING, Direction.NORTH));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return kind == Kind.TIMED ? TIMED_CODEC : REMOTE_CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    /** De kant van het pakketje die van het blok af wijst (daar zit het schermpje). */
    public static Direction outward(BlockState state) {
        return getConnectedDirection(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(outward(state).getOpposite());
    }

    /** Plakkerig: hij blijft zitten, ook als het blok erachter verdwijnt. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return state;
    }

    // ================================================================ Binnenkant

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new C4BlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof C4BlockEntity c4 && tickLevel instanceof ServerLevel serverLevel) {
                c4.serverTick(serverLevel, pos, tickState);
            }
        };
    }
}
