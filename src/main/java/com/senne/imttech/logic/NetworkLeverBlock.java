package com.senne.imttech.logic;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * De netwerkhendel (hoofdstuk 38): zet hem om en je diagram begint, zonder redstone. {@code lever.on} zegt of hij nu aan
 * of uit staat, en {@code is_on("Night mode")} kan dat altijd opvragen (ook in een andere trigger).
 * <pre>
 * when lever("Night mode"):
 *     if lever.on:
 *         on("Vault lasers")
 *     else:
 *         off("Vault lasers")
 * </pre>
 * Verder zoals elke netwerkknop en -hendel: zie NetworkControlBlock.
 */
public class NetworkLeverBlock extends NetworkControlBlock {
    public static final MapCodec<NetworkLeverBlock> CODEC = simpleCodec(NetworkLeverBlock::new);

    /** Staat hij aan (de hendel omhoog)? */
    public static final BooleanProperty ON = BooleanProperty.create("on");

    public NetworkLeverBlock(Properties properties) {
        // Een plaatje van 6 x 8 met een hendel die 6 pixels uitsteekt.
        super(properties, new double[] {5, 4, 10, 11, 12, 16}, new double[] {5, 0, 4, 11, 6, 12},
                new double[] {5, 10, 4, 11, 16, 12});
        registerDefaultState(defaultBlockState().setValue(ON, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ON);
    }

    @Override
    protected String defaultNamePrefix() {
        return "Lever";
    }

    @Override
    public com.senne.imttech.logic.program.Op trigger() {
        return com.senne.imttech.logic.program.Op.LEVER;
    }

    /** Stroom die de hendel elke twee seconden gebruikt (zonder stroom hoort de server hem niet). */
    @Override
    public float upkeep() {
        return com.senne.imttech.config.ImtConfig.Power.LEVER_UPKEEP;
    }

    @Override
    protected String tooltipKey() {
        return "tooltip.imttech.network_lever";
    }

    /** Staat deze hendel aan? */
    public static boolean isOn(BlockState state) {
        return state.getBlock() instanceof NetworkLeverBlock && state.getValue(ON);
    }

    /** Omzetten: klik, en de server hoort het (als hij kan). */
    @Override
    protected void use(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        boolean on = !state.getValue(ON);
        level.setBlock(pos, state.setValue(ON, on), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, on ? 0.7f : 0.6f);
        LogicEvents.lever(level, pos, player, on);
    }
}
