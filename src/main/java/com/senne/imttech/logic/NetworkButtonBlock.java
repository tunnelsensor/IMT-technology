package com.senne.imttech.logic;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * De netwerkknop (hoofdstuk 38): druk erop en je diagram begint, zonder redstone.
 * <pre>
 * when button("Doorbell"):
 *     say(player.name + " is at the door")
 * </pre>
 * Hij duikt even in (zo lang als een stenen knop) en komt dan vanzelf terug; zolang hij ingedrukt is, doet nog een keer
 * drukken niets. Verder zoals elke netwerkknop en -hendel: zie NetworkControlBlock.
 */
public class NetworkButtonBlock extends NetworkControlBlock {
    public static final MapCodec<NetworkButtonBlock> CODEC = simpleCodec(NetworkButtonBlock::new);

    /** Is hij nu ingedrukt? */
    public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");
    /** Zo lang (ticks) blijft hij ingedrukt, net als een stenen knop. */
    public static final int PRESS_TICKS = 20;

    public NetworkButtonBlock(Properties properties) {
        // Een plaatje van 6 x 6 met een knop van 4 x 4 die 2 pixels uitsteekt.
        super(properties, new double[] {5, 5, 13, 11, 11, 16}, new double[] {5, 0, 5, 11, 3, 11},
                new double[] {5, 13, 5, 11, 16, 11});
        registerDefaultState(defaultBlockState().setValue(PRESSED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PRESSED);
    }

    @Override
    protected String defaultNamePrefix() {
        return "Button";
    }

    @Override
    public com.senne.imttech.logic.program.Op trigger() {
        return com.senne.imttech.logic.program.Op.BUTTON;
    }

    /** Stroom die de knop elke twee seconden gebruikt (zonder stroom hoort de server hem niet). */
    @Override
    public float upkeep() {
        return com.senne.imttech.config.ImtConfig.Power.BUTTON_UPKEEP;
    }

    @Override
    protected String tooltipKey() {
        return "tooltip.imttech.network_button";
    }

    /** Indrukken: klik, en de server hoort het (als hij kan). Na PRESS_TICKS komt hij terug (zie tick). */
    @Override
    protected void use(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (state.getValue(PRESSED)) {
            return;
        }
        level.setBlock(pos, state.setValue(PRESSED, true), Block.UPDATE_ALL);
        // Eerst het terugkomen plannen: een rood lampje (LogicEvents) plant daarna niets meer, het gaat dan mee uit.
        level.scheduleTick(pos, this, PRESS_TICKS);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, 1.3f);
        LogicEvents.button(level, pos, player);
    }

    /** Hij komt terug (en het lampje gaat uit, zie InputDeviceBlock). */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PRESSED)) {
            state = state.setValue(PRESSED, false);
            level.setBlock(pos, state, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.4f, 1.1f);
        }
        super.tick(state, level, pos, random);
    }
}
