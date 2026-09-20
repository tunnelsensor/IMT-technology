package com.senne.starktech.shield;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * De laserzender: zet er twee tegenover elkaar en er ontstaat een rode energiewand tussen
 * (zoals de "ray shields" uit Star Wars). Handig als deur die je niet ziet aankomen, of als
 * val in een gang.
 *
 * Aanzetten kan op twee manieren:
 * - met redstone (een hendel, een drukplaat, of een E.D.I.T.H.-relais ernaast);
 * - of je meldt de zender zelf aan bij je bril (rechtsklikken met de bril op) en zegt
 *   bijvoorbeeld "zet de poort aan".
 *
 * De wand loopt in de richting waarin de zender kijkt, tot maximaal LASER_MAX_LENGTH blokken,
 * en stopt bij de tegenoverliggende zender (of bij het eerste blok dat in de weg staat).
 */
public class LaserEmitterBlock extends Block implements EdithSwitchable {
    public static final MapCodec<LaserEmitterBlock> CODEC = simpleCodec(LaserEmitterBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /**
     * Aangezet door E.D.I.T.H. (of een spraakcommando) in plaats van door redstone.
     *
     * Zonder dit ging de wand weer uit zodra er iets in de buurt veranderde: elke blok-update
     * liet de zender kijken of er redstone op stond, en dat was niet zo.
     */
    public static final BooleanProperty SWITCHED = BooleanProperty.create("switched");
    /** De kleur van het veld dat deze zender maakt. Sluipen + rechtsklik bladert erdoorheen. */
    public static final EnumProperty<ShieldColor> COLOR = EnumProperty.create("color", ShieldColor.class);

    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 6, 13);

    public LaserEmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(SWITCHED, false)
                .setValue(COLOR, ShieldColor.BLUE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, SWITCHED, COLOR);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Hij kijkt de kant op waar je hem tegenaan zet (dus de wand loopt van je af).
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
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

    // ---------------------------------------------------------------- Aan en uit

    /** Redstone erop? Dan gaat de wand aan; eraf, dan weer uit. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean movedByPiston) {
        if (level.isClientSide) {
            return;
        }
        // Aan als er redstone op staat OF als E.D.I.T.H. hem heeft aangezet.
        boolean wanted = level.hasNeighborSignal(pos) || state.getValue(SWITCHED);
        if (wanted != state.getValue(POWERED)) {
            setActive((ServerLevel) level, pos, state, wanted);
        }
    }

    /** Net neergezet: als er al redstone op staat, meteen aan. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            if (level.hasNeighborSignal(pos)) {
                setActive(serverLevel, pos, state, true);
            }
            if (placer instanceof ServerPlayer player) {
                // Ook aanmelden bij de bril, zodat E.D.I.T.H. hem met een naam kan aansturen.
                EdithRedstone.register(player, pos);
            }
        }
    }

    /** Weggehaald: de wand verdwijnt mee. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && state.getValue(POWERED) && !newState.is(this)) {
            clearWall((ServerLevel) level, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Rechtsklikken met de bril op: naam en groep geven (dan kan E.D.I.T.H. hem aansturen). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Sluipen erbij: dan blader je door de kleuren in plaats van het naamscherm te openen.
        if (player.isShiftKeyDown()) {
            if (level instanceof ServerLevel serverLevel) {
                recolour(serverLevel, pos, state);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            EdithRedstone.openEditor(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Volgende kleur: de zender én de wand die er al staat kleuren mee. */
    private static void recolour(ServerLevel level, BlockPos pos, BlockState state) {
        ShieldColor colour = state.getValue(COLOR).next();
        level.setBlock(pos, state.setValue(COLOR, colour), Block.UPDATE_ALL);
        Direction facing = state.getValue(FACING);
        for (BlockPos target : wallPositions(level, pos, facing)) {
            BlockState wall = level.getBlockState(target);
            if (wall.is(ModBlocks.LASER_WALL.get())) {
                level.setBlock(target, wall.setValue(LaserWallBlock.COLOR, colour), Block.UPDATE_ALL);
            }
        }
        level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    /** E.D.I.T.H. zet hem aan of uit (zie EdithSwitchable). */
    @Override
    public void setSwitched(ServerLevel level, BlockPos pos, BlockState state, boolean on) {
        // Onthouden dat E.D.I.T.H. hem aanzette, zodat een blok-update hem niet weer uitdoet.
        setActive(level, pos, state.setValue(SWITCHED, on), on);
    }

    @Override
    public boolean isSwitchedOn(BlockState state) {
        return state.getValue(POWERED);
    }

    // ---------------------------------------------------------------- De wand maken en weghalen

    /** Zet de zender aan of uit en maakt (of ruimt) de wand op. */
    public static void setActive(ServerLevel level, BlockPos pos, BlockState state, boolean on) {
        Direction facing = state.getValue(FACING);
        level.setBlock(pos, state.setValue(POWERED, on), Block.UPDATE_ALL);
        if (on) {
            buildWall(level, pos, facing);
        } else {
            clearWall(level, pos, facing);
        }
        level.playSound(null, pos, on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.BLOCKS, 0.5f, on ? 1.6f : 1.2f);
    }

    /**
     * Het veld schuift dicht vanaf de twee uiteinden naar het midden (zie LaserAnimations).
     * Je kunt er dus nog net doorheen duiken als je snel bent.
     */
    private static void buildWall(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        ShieldColor colour = state.hasProperty(COLOR) ? state.getValue(COLOR) : ShieldColor.BLUE;
        BlockState wall = ModBlocks.LASER_WALL.get().defaultBlockState()
                .setValue(LaserWallBlock.AXIS_FACING, facing)
                .setValue(LaserWallBlock.COLOR, colour);
        LaserAnimations.start(level, wallPositions(level, pos, facing), wall, colour, true);
    }

    /** En weer open, vanuit het midden naar buiten. */
    private static void clearWall(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        ShieldColor colour = state.hasProperty(COLOR) ? state.getValue(COLOR) : ShieldColor.BLUE;
        LaserAnimations.start(level, wallPositions(level, pos, facing),
                Blocks.AIR.defaultBlockState(), colour, false);
    }

    /**
     * Welke plekken horen bij de wand van deze zender? We lopen vooruit tot we iets tegenkomen
     * dat geen lucht (of laserwand) is; is dat een andere zender die terugkijkt, dan is de wand
     * "gesloten".
     */
    private static List<BlockPos> wallPositions(ServerLevel level, BlockPos pos, Direction facing) {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 1; i <= EdithBalance.LASER_MAX_LENGTH; i++) {
            BlockPos target = pos.relative(facing, i);
            BlockState state = level.getBlockState(target);
            if (state.isAir() || state.is(ModBlocks.LASER_WALL.get())) {
                positions.add(target);
                continue;
            }
            // Een tegenoverliggende zender sluit de wand netjes af.
            if (state.is(ModBlocks.LASER_EMITTER.get()) && state.getValue(FACING) == facing.getOpposite()) {
                return positions;
            }
            // Iets anders in de weg: de wand stopt hier.
            return positions;
        }
        return positions;
    }
}
