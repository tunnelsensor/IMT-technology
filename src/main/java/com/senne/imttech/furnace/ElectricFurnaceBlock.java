package com.senne.imttech.furnace;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.energy.PowerConnectable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De Electric Furnace (hoofdstuk 87, jouw wens: "een electrische furnace zodat je net zoals een crusher dingen kunt smelten
 * op power"). Dezelfde soort kast als de Crusher, op netstroom (via stroomkabels, of direct tegen een generator aan), met
 * een vakje erin en een vakje eruit (zie {@link ElectricFurnaceBlockEntity}):
 * <ul>
 *   <li>hij smelt alles wat een gewone oven smelt (erts, eten, zand, polymeer tot plastic...), zonder brandstof;</li>
 *   <li>twee keer zo snel als een oven (het getal staat bij [materials] in de config);</li>
 *   <li>de ervaring krijg je als je het resultaat eruit haalt, net als bij een oven;</li>
 *   <li>trechters doen er van boven of opzij iets in, en halen het resultaat er van onder uit.</li>
 * </ul>
 * Terwijl hij werkt, gloeien de spiralen achter zijn venster oranje en geeft hij licht (zoals een brandende oven).
 */
public class ElectricFurnaceBlock extends HorizontalDirectionalBlock implements EntityBlock, PowerConnectable {
    public static final MapCodec<ElectricFurnaceBlock> CODEC = simpleCodec(ElectricFurnaceBlock::new);

    /** Is hij nu bezig? Dan gloeien zijn spiralen (zie de blockstate) en geeft hij licht. */
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    /** Zoveel licht geeft hij terwijl hij werkt (net als een brandende oven). */
    public static final int LIGHT = 13;

    public ElectricFurnaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WORKING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WORKING);
    }

    /** De voorkant kijkt naar wie hem neerzet. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // ================================================================ Gebruiken

    /** Rechtsklik: zijn scherm. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ElectricFurnaceBlockEntity furnace) {
            serverPlayer.openMenu(furnace);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Afgebroken: wat erin zit valt eruit, en de opgespaarde ervaring komt vrij (zoals bij een oven). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ElectricFurnaceBlockEntity furnace) {
            if (level instanceof ServerLevel serverLevel) {
                Containers.dropContents(level, pos, furnace);
                furnace.popExperience(serverLevel, Vec3.atCenterOf(pos));
            }
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Een comparator ernaast leest hoe vol hij zit (zoals bij een oven). */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    /**
     * Terwijl hij werkt (alleen bij jou op het scherm): af en toe een dun rookpluimpje uit de luchtgaten bovenop. Geen
     * knetterend vuur zoals bij een gewone oven: er brandt niets.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(WORKING) && random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.02,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.02, 0.0);
        }
    }

    // ================================================================ Binnenkant

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricFurnaceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ElectricFurnaceBlockEntity furnace && tickLevel instanceof ServerLevel serverLevel) {
                furnace.serverTick(serverLevel, pos, tickState);
            }
        };
    }
}
