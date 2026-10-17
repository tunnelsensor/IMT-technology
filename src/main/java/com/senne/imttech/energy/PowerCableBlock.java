package com.senne.imttech.energy;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Het OUDE kabelblok. Sinds 27 september zijn kabels geen blokken meer maar lijnen die je tekent
 * (zie CableSpoolItem en PowerCables). Dit blok bestaat alleen nog zodat oude werelden en de
 * gebouwen (die met tools/generate_structure.py en generate_bunker.py gemaakt zijn) blijven
 * werken: zodra zo'n blok in de wereld staat, wordt het een kabel op dezelfde plek (zie
 * PowerCables.convertLegacy) en verdwijnt het blok.
 *
 * PLACEMENT zegt wat er na het omzetten op die plek moet komen: EMBEDDED = de kabel zat in een
 * muur of plafond (dan komt het blok van de muur terug), OPEN = hij lag vrij (dan lucht).
 * De generators zetten dat zelf; in een oude wereld staat er UNKNOWN en raden we het.
 */
public class PowerCableBlock extends Block {
    public enum Placement implements net.minecraft.util.StringRepresentable {
        UNKNOWN, EMBEDDED, OPEN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Placement> PLACEMENT =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("placement", Placement.class);
    public static final MapCodec<PowerCableBlock> CODEC = simpleCodec(PowerCableBlock::new);

    /** Per richting: loopt er een stukje kabel naar die kant? (Dezelfde namen als bij chorus.) */
    public static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;

    /** De kern (6 pixels) en de verbindingsstukjes (4 pixels dik) naar elke kant. */
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
            Direction.NORTH, Block.box(6, 6, 0, 10, 10, 5),
            Direction.SOUTH, Block.box(6, 6, 11, 10, 10, 16),
            Direction.WEST, Block.box(0, 6, 6, 5, 10, 10),
            Direction.EAST, Block.box(11, 6, 6, 16, 10, 10),
            Direction.DOWN, Block.box(6, 0, 6, 10, 5, 10),
            Direction.UP, Block.box(6, 11, 6, 10, 16, 10));

    public PowerCableBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(PLACEMENT, Placement.UNKNOWN);
        for (BooleanProperty side : SIDES.values()) {
            state = state.setValue(side, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        SIDES.values().forEach(builder::add);
        builder.add(PLACEMENT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            if (state.getValue(side.getValue())) {
                shape = Shapes.or(shape, ARMS.get(side.getKey()));
            }
        }
        return shape;
    }

    /** Sluit aan op een andere kabel of op een apparaat met stroom. */
    public static boolean connectsTo(BlockState state) {
        return state.getBlock() instanceof PowerCableBlock || state.getBlock() instanceof PowerConnectable;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            state = state.setValue(side.getValue(), connectsTo(level.getBlockState(pos.relative(side.getKey()))));
        }
        return state;
    }

    /** Een buurblok veranderde: kijk of we er (nog) op aansluiten. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(SIDES.get(direction), connectsTo(neighbour));
    }

    /**
     * In de wereld gezet (door een gebouw, of met /setblock): na deze servertik wordt het een
     * getekende kabel (zie PowerCables.queue; geen geplande blok-tik, want die wist het spel soms
     * na het neerzetten van een gebouw). Tijdens het maken van de wereld gebeurt dit niet; dan
     * zet PowerCables.onChunkLoad het om zodra de chunk laadt.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            PowerCables.queue(serverLevel, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(this)) {
            PowerGrid.changed();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
