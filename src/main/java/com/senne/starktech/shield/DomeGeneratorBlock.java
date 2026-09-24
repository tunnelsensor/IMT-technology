package com.senne.starktech.shield;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.energy.PowerConnectable;
import com.senne.starktech.redstone.EdithRedstone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
 * De schildkoepel-generator: zet hem in je basis, sluit hem aan op stroom (een Stark-kabel naar
 * een zonnepaneel of warmtegenerator, of er direct tegenaan) en er komt een energiekoepel om je
 * basis heen.
 *
 * Rechtsklik opent zijn scherm: aan/uit, kleur, bereik en sterkte. Wat de koepel tegenhoudt en
 * wat dat kost, staat in EdithDomes en DomeSettings; hoe hij eruitziet in DomeRenderer.
 *
 * E.D.I.T.H. kan hem ook aan- en uitzetten ("zet de koepel aan"): hij wordt bij het neerzetten
 * aangemeld zoals een relais (zie EdithSwitchable).
 */
public class DomeGeneratorBlock extends BaseEntityBlock implements EdithSwitchable, PowerConnectable {
    public static final MapCodec<DomeGeneratorBlock> CODEC = simpleCodec(DomeGeneratorBlock::new);

    /** Staat de koepel? (Alleen voor het model: dan gloeit de projector.) */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 4, 14),    // voet
            Block.box(5, 4, 5, 11, 13, 11));  // zuil met de projector

    public DomeGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
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

    /** Het blok zelf is een gewoon model; de koepel tekent DomeRenderer erbij. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DomeGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof DomeGeneratorBlockEntity dome && tickLevel instanceof ServerLevel serverLevel) {
                dome.serverTick(serverLevel, pos, tickState);
            }
        };
    }

    /** Neergezet: de koepel is van jou, en E.D.I.T.H. kan hem schakelen. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof DomeGeneratorBlockEntity dome) {
            dome.setOwner(player);
            EdithRedstone.register(player, pos);
        }
    }

    /** Rechtsklikken: het instelscherm openen (alleen voor de eigenaar). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            EdithDomes.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** E.D.I.T.H. zet hem aan of uit (zie EdithSwitchable). */
    @Override
    public void setSwitched(ServerLevel level, BlockPos pos, BlockState state, boolean on) {
        if (level.getBlockEntity(pos) instanceof DomeGeneratorBlockEntity dome) {
            dome.setEnabled(on);
        }
    }

    @Override
    public boolean isSwitchedOn(BlockState state) {
        return state.getValue(POWERED);
    }
}
