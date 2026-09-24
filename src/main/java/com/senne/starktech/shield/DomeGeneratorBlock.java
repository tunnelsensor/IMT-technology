package com.senne.starktech.shield;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.energy.PowerConnectable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import com.senne.starktech.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * De schildkoepel-generator: zet hem in je basis, geef hem redstone (of laat E.D.I.T.H. hem
 * aanzetten) en er komt een energiekoepel om je basis heen.
 *
 * Wat de koepel doet (zie EdithShield):
 * - pijlen, vuurballen en andere projectielen die naar binnen willen, verdampen;
 * - vijandige monsters worden naar buiten geduwd;
 * - spelers en dieren kunnen er gewoon door (anders kwam je zelf niet meer thuis).
 */
public class DomeGeneratorBlock extends Block implements EdithSwitchable, PowerConnectable {
    public static final MapCodec<DomeGeneratorBlock> CODEC = simpleCodec(DomeGeneratorBlock::new);

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** Hoe vaak de koepel zijn werk doet (in ticks). 10 ticks = een halve seconde. */
    private static final int DOME_TICK_INTERVAL = 10;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 4, 14),    // voet
            Block.box(5, 4, 5, 11, 13, 11));  // zuil met de projector

    public DomeGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ---------------------------------------------------------------- Aan en uit

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean movedByPiston) {
        if (level.isClientSide) {
            return;
        }
        boolean redstone = level.hasNeighborSignal(pos);
        if (redstone != state.getValue(POWERED)) {
            setActive((ServerLevel) level, pos, state, redstone);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            if (level.hasNeighborSignal(pos)) {
                setActive(serverLevel, pos, state, true);
            }
            if (placer instanceof ServerPlayer player) {
                com.senne.starktech.redstone.EdithRedstone.register(player, pos);
            }
        }
    }

    /** Rechtsklikken met de bril op: naam en groep geven. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            com.senne.starktech.redstone.EdithRedstone.openEditor(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setSwitched(ServerLevel level, BlockPos pos, BlockState state, boolean on) {
        setActive(level, pos, state, on);
    }

    @Override
    public boolean isSwitchedOn(BlockState state) {
        return state.getValue(POWERED);
    }

    private static void setActive(ServerLevel level, BlockPos pos, BlockState state, boolean on) {
        level.setBlock(pos, state.setValue(POWERED, on), Block.UPDATE_ALL);
        level.playSound(null, pos, on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.BLOCKS, 0.8f, on ? 0.9f : 0.7f);
        if (on) {
            // Laat het blok vanaf nu elke halve seconde "tikken": dan doet de koepel zijn werk.
            level.scheduleTick(pos, ModBlocks.DOME_GENERATOR.get(), DOME_TICK_INTERVAL);
        }
    }

    /**
     * Elke halve seconde terwijl de koepel aan staat: projectielen tegenhouden en monsters
     * wegduwen. Daarna planned hij zichzelf opnieuw in. (Zo'n geplande tick wordt ook
     * opgeslagen in de wereld, dus na een herstart loopt de koepel gewoon door.)
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!state.getValue(POWERED)) {
            return;
        }
        EdithShield.tickDome(level, pos);
        level.scheduleTick(pos, this, DOME_TICK_INTERVAL);
    }
}
