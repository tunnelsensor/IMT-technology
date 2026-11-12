package com.senne.imttech.plastic;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.energy.PowerConnectable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
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
import org.jetbrains.annotations.Nullable;

/**
 * De Polymerizer (hoofdstuk 85, jouw wens: "polymers krijg je van coal met water in een nieuwe machine zoals een
 * crusher"). Hij lijkt op de Crusher: een kast op netstroom met een scherm, een vakje erin en een vakje eruit. Maar hij
 * heeft ook een <b>watertank</b>:
 * <ul>
 *   <li>vullen doe je met een emmer water: rechtsklik op de machine, of leg de emmer in zijn emmervakje (de lege emmer
 *       blijft daar liggen; een trechter eronder haalt hem eruit);</li>
 *   <li>of je pompt er water in met buizen van een andere mod (Create, Mekanism...): hij heeft de vloeistof-capability
 *       (zie PolymerizerBlockEntity.water).</li>
 * </ul>
 * Per kool (of houtskool) gebruikt hij een beetje water en maakt hij polymeer (de getallen staan bij [materials] in de
 * config). Polymeer smelt je in een oven tot plastic, en van koperdraad en plastic maak je kabels.
 */
public class PolymerizerBlock extends HorizontalDirectionalBlock implements EntityBlock, PowerConnectable {
    public static final MapCodec<PolymerizerBlock> CODEC = simpleCodec(PolymerizerBlock::new);

    /** Is hij nu bezig? Dan borrelt zijn voorkant (zie de blockstate). */
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public PolymerizerBlock(Properties properties) {
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

    /**
     * Rechtsklik met een emmer water: de emmer gaat leeg in zijn tank (als er nog een hele emmer bij past) en je houdt de
     * lege emmer over. Met iets anders: zijn scherm.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.WATER_BUCKET)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof PolymerizerBlockEntity machine)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!machine.fitsBucket()) {
            // De tank is (bijna) vol: dan gewoon zijn scherm, zodat je ziet hoeveel erin zit.
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            machine.addBucket();
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Rechtsklik: zijn scherm. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof PolymerizerBlockEntity machine) {
            serverPlayer.openMenu(machine);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Afgebroken: wat erin zit, valt eruit (het water niet: dat loopt weg). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PolymerizerBlockEntity machine) {
            Containers.dropContents(level, pos, machine);
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

    // ================================================================ Binnenkant

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PolymerizerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof PolymerizerBlockEntity machine && tickLevel instanceof ServerLevel serverLevel) {
                machine.serverTick(serverLevel, pos, tickState);
            }
        };
    }
}
