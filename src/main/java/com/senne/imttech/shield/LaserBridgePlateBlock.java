package com.senne.imttech.shield;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Eén blok van de laserbrug (hoofdstuk 34, zie LaserBridgeBlock): een plaat van licht waar je over loopt.
 *
 * Een dunne plaat (1 pixel dik), met de bovenkant 2 pixels onder de rand van het blok (jouw keuze: "een heel klein
 * beetje onder de rand, tussen rand en midden, dichter bij de rand"). Dat is even hoog als zielenzand: je stapt er
 * vanaf de vloer waar de zender in zit gewoon op, zonder te springen.
 *
 * Hij doet geen pijn en iedereen kan erop lopen: spelers, dieren en monsters (die zien hem als vloer, net als
 * zielenzand, dus ze lopen er ook zelf overheen). Wat erop valt, blijft liggen. Je kunt hem niet breken of oprapen:
 * hij verschijnt en verdwijnt met de zender.
 *
 * <b>De randen:</b> waar de brug ophoudt (opzij en aan het eind), licht de rand van de plaat fel op, zodat je ziet
 * waar je kunt lopen. Daarvoor onthoudt elke plaat aan welke kanten er nog brug ligt (NORTH, EAST, SOUTH, WEST): naast
 * een andere plaat of een brugzender komt geen rand. Een brug van drie breed heeft dus alleen randen aan de buitenkant.
 * Welke rand waar komt, staat in de blockstate (tools/generate_block_models.py, laser_bridge_models).
 */
public class LaserBridgePlateBlock extends Block {
    public static final MapCodec<LaserBridgePlateBlock> CODEC = simpleCodec(LaserBridgePlateBlock::new);

    /** De kleur; de zender bepaalt hem (hetzelfde "etiket" als bij de zenders). */
    public static final EnumProperty<ShieldColor> COLOR = LaserEmitterBlock.COLOR;
    /**
     * In welke richting de brug loopt (oost-west of noord-zuid). Je ziet het niet, maar zo weet een zender of een plaat
     * van zijn eigen brug is, of van een brug die dwars loopt (zie LaserBridgeBlock.platePositions).
     */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** Ligt er aan deze kant nog brug (een plaat of een brugzender)? Dan komt daar geen felle rand. */
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    /** Waar de bovenkant van de plaat zit, in pixels (een blok is 16 hoog). Hier loop je over. */
    public static final double TOP = 14.0;
    private static final VoxelShape SHAPE = Block.box(0, TOP - 1, 0, 16, TOP, 16);

    public LaserBridgePlateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(COLOR, ShieldColor.CYAN)
                .setValue(AXIS, Direction.Axis.X)
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR, AXIS, NORTH, EAST, SOUTH, WEST);
    }

    /** De dunne plaat: dit is ook waar je op staat (en wat je ziet als je ernaar kijkt). */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Voor dieren en monsters is de plaat vloer, geen lucht (net als zielenzand). Zonder dit dachten ze dat ze er
     * doorheen vallen, en liepen ze nooit zelf over een brug.
     */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ---------------------------------------------------------------- De randen

    /** De eigenschap voor deze kant (alleen de vier zijkanten). */
    private static BooleanProperty side(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    /** Hoort dit buurblok bij de brug (een plaat of een brugzender)? Dan geen rand aan die kant. */
    private static boolean isBridge(BlockState neighbour) {
        return neighbour.getBlock() instanceof LaserBridgePlateBlock || neighbour.getBlock() instanceof LaserBridgeBlock;
    }

    /** Een buurblok veranderde: aan die kant komt de rand (of hij gaat weg). */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(side(direction), isBridge(neighbour));
        }
        return state;
    }

    /**
     * Net neergezet (de brug schuift uit): de buren horen dat zelf (updateShape), maar deze plaat moet nog zelf kijken
     * aan welke kanten er al brug ligt.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        BlockState sides = state;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            sides = sides.setValue(side(direction), isBridge(level.getBlockState(pos.relative(direction))));
        }
        if (sides != state) {
            level.setBlock(pos, sides, Block.UPDATE_CLIENTS);
        }
    }

    // ---------------------------------------------------------------- Licht stuift niet

    /** Wie erop landt, schopt geen stofwolkje op (het is licht, geen zand). */
    @Override
    public boolean addLandingEffects(BlockState state, ServerLevel level, BlockPos pos, BlockState entityState,
                                     LivingEntity entity, int numberOfParticles) {
        return true;
    }

    /** En wie erover rent ook niet. */
    @Override
    public boolean addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
        return true;
    }

    /** Je kunt hem niet oprapen (ook niet met de middelste muisknop in creative): hij hoort bij de zender. */
    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }
}
