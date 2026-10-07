package com.senne.starktech.logic;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.network.CodePanelPayload;
import com.senne.starktech.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Het codepaneel (jouw wens, 7 oktober): een toetsenbordje aan de muur. Rechtsklik opent de toetsen
 * (0-9, wissen, OK); de code die je intypt gaat naar de server van je beveiligingskern.
 *
 * Welke code goed is, staat NIET in het paneel maar in je diagram:
 * <pre>
 * when codepanel("Front door"):
 *     if code == "4321":
 *         accept()
 *         pulse("Front door lock", 3)
 *     else:
 *         deny()
 * </pre>
 * Zo kan een hacker die het paneel openbreekt de code er niet uit halen.
 */
public class CodePanelBlock extends InputDeviceBlock {
    public static final MapCodec<CodePanelBlock> CODEC = simpleCodec(CodePanelBlock::new);
    /** Zo lang mag een code hooguit zijn (cijfers). */
    public static final int MAX_CODE = 8;
    /** Zo dicht moet je erbij staan om een code in te typen. */
    public static final double REACH = 6.0;

    public CodePanelBlock(Properties properties) {
        // 8 breed, 12 hoog, 2 diep, tegen de muur.
        super(properties, 4, 2, 14, 12, 14, 16);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected String defaultNamePrefix() {
        return "Panel";
    }

    /** Rechtsklik: de toetsen openen (op jouw scherm). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof InputDeviceBlockEntity device) {
            ModNetwork.sendTo(serverPlayer, new CodePanelPayload(pos, device.name()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Is dit een code die je kunt intypen (alleen cijfers, 1 tot MAX_CODE lang)? */
    public static boolean isValidCode(String code) {
        return !code.isEmpty() && code.length() <= MAX_CODE && code.chars().allMatch(c -> c >= '0' && c <= '9');
    }
}
