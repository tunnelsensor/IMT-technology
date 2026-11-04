package com.senne.imttech.logic;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * De Auth Console (hoofdstuk 49): een kastje aan de muur met een luidsprekertje en één knop. Rechtsklik = toegang
 * vragen: het verzoek komt op de desktop van de kern (de app Requests, ook via de laptop en de GSM), en wie daar zit
 * zegt Accept of Deny. Het lampje is oranje zolang het verzoek loopt, dan groen of rood. Je diagram hoort het antwoord:
 * <pre>
 * when request("Gate"):
 *     if request.accepted:
 *         pulse("Gate", 5)
 *     else:
 *         say(player.name + " may not come in")
 * </pre>
 * Een andere naam: sneak + rechtsklik met een lege hand, of in de app Devices of Logic (zoals een codepaneel).
 */
public class AuthConsoleBlock extends InputDeviceBlock {
    public static final MapCodec<AuthConsoleBlock> CODEC = simpleCodec(AuthConsoleBlock::new);

    public AuthConsoleBlock(Properties properties) {
        // 8 breed, 12 hoog, 2 diep, tegen de muur (zoals het codepaneel).
        super(properties, 4, 2, 14, 12, 14, 16);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected String defaultNamePrefix() {
        return "Console";
    }

    @Override
    public com.senne.imttech.logic.program.Op trigger() {
        return com.senne.imttech.logic.program.Op.REQUEST;
    }

    /** Stroom die de console elke twee seconden gebruikt (zonder stroom doet de knop niets). */
    @Override
    public float upkeep() {
        return com.senne.imttech.config.ImtConfig.Power.AUTH_CONSOLE_UPKEEP;
    }

    /** Rechtsklik: toegang vragen. Sneak + rechtsklik met een lege hand: het naamscherm (voor wie dat mag). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isSecondaryUseActive() && InputDevices.tryOpen(serverPlayer, pos)) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            AuthRequests.ask(serverPlayer.serverLevel(), pos, serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
