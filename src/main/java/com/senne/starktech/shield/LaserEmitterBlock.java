package com.senne.starktech.shield;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.energy.PowerConnectable;
import com.senne.starktech.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import org.jetbrains.annotations.Nullable;

/**
 * De laserzender: zet er twee tegenover elkaar en er ontstaat een energiewand tussen
 * (zoals de "ray shields" uit Star Wars). Handig als deur die je niet ziet aankomen, of als
 * val in een gang.
 *
 * Een volledig blok met een lens aan de voorkant. Aan en uit gaat met redstone: een hendel,
 * een drukplaat, of een E.D.I.T.H.-relais ernaast (dan kan E.D.I.T.H. hem toch schakelen).
 * Rechtsklikken wisselt de kleur.
 *
 * De wand loopt in de richting waarin de zender kijkt, tot maximaal LASER_MAX_LENGTH blokken,
 * en stopt bij de tegenoverliggende zender (of bij het eerste blok dat in de weg staat).
 */
public class LaserEmitterBlock extends Block implements PowerConnectable {
    public static final MapCodec<LaserEmitterBlock> CODEC = simpleCodec(LaserEmitterBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** De kleur van het veld dat deze zender maakt. Rechtsklikken bladert erdoorheen. */
    public static final EnumProperty<ShieldColor> COLOR = EnumProperty.create("color", ShieldColor.class);

    public LaserEmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(COLOR, ShieldColor.BLUE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, COLOR);
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

    // ---------------------------------------------------------------- Aan en uit

    /** Redstone erop? Dan gaat de wand aan; eraf, dan weer uit. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean movedByPiston) {
        if (level.isClientSide) {
            return;
        }
        boolean wanted = level.hasNeighborSignal(pos);
        if (wanted != state.getValue(POWERED)) {
            setActive((ServerLevel) level, pos, state, wanted);
        }
    }

    /** Net neergezet: als er al redstone op staat, meteen aan. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && level.hasNeighborSignal(pos)) {
            setActive(serverLevel, pos, state, true);
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

    /** Rechtsklikken: de volgende kleur. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            recolour(serverLevel, pos, state);
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
