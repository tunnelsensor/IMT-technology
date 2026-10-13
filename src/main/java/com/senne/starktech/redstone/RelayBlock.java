package com.senne.starktech.redstone;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Het E.D.I.T.H.-relais: een plat kastje dat je naast een deur, lamp, zuiger of trechter zet.
 *
 * Zet je het aan (via E.D.I.T.H.), dan geeft het een redstone-signaal van 15 af, net als een
 * redstone-blok. Zet je het uit, dan geeft het niets. Zo kun je met je bril ALLES aansturen
 * wat op redstone reageert.
 *
 * Rechtsklikken (ook zonder bril, sinds 13 oktober) opent het scherm waarin je het een naam en
 * tags geeft. Jouw bril (E.D.I.T.H.) weet welke relais je hebt, waar ze staan en hoe ze heten
 * (zie EdithRelays en EdithRedstone). Het relais zelf onthoudt maar twee dingen: staat het aan,
 * en is het omgekeerd?
 *
 * <b>Omgekeerd</b> (13 oktober, de knop "invert" in zijn scherm): dan geeft het redstone als het UIT
 * staat, en niets als het aan staat. Handig voor een deur die dicht moet als E.D.I.T.H. iets aanzet.
 */
public class RelayBlock extends Block {
    /** Staat het aan (volgens E.D.I.T.H.)? Het lampje bovenop is dan groen. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Omgekeerd: redstone als het uit staat. Je ziet het aan het rode streepje boven het lampje. */
    public static final BooleanProperty INVERTED = BlockStateProperties.INVERTED;

    /** Elk blok moet in 1.21.1 kunnen zeggen hoe het opgeslagen wordt. */
    public static final MapCodec<RelayBlock> CODEC = simpleCodec(RelayBlock::new);

    /** Een plat kastje van 12 x 4 x 12 pixels. */
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 4, 14);

    public RelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false).setValue(INVERTED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, INVERTED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ---------------------------------------------------------------- Redstone

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** Geeft het nu redstone? Aan en niet omgekeerd, of uit en wel omgekeerd. */
    public static boolean emits(BlockState state) {
        return state.getValue(POWERED) != state.getValue(INVERTED);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return emits(state) ? 15 : 0;
    }

    /** Ook naar boven en onder een sterk signaal geven, zoals een redstone-blok. */
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return emits(state) ? 15 : 0;
    }

    // ---------------------------------------------------------------- Neerzetten en gebruiken

    /** Net neergezet: meld het relais meteen aan bij de bril van de speler. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player) {
            EdithRedstone.register(player, pos);
        }
    }

    /**
     * Het kastje is weg: haal het ook uit de lijst van de brillen, anders blijft het
     * naamplaatje boven een lege plek zweven.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            EdithRedstone.forget(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Rechtsklikken: het scherm openen waarin je naam en tags instelt (ook zonder bril). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            EdithRedstone.openEditor(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
