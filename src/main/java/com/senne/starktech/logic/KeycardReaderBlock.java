package com.senne.starktech.logic;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * De keycardlezer (jouw wens, 7 oktober): een kastje aan de muur. Rechtsklik met een keycard en de
 * server van je beveiligingskern krijgt "kaart gelezen" (met het niveau, het nummer en of de kaart
 * geldig is). Wat er dan gebeurt (een deur open, een alarm), staat in je diagram:
 * <pre>
 * when keycard("Lab door"):
 *     if card.valid and card.level &gt;= 3:
 *         accept()
 * </pre>
 * Ook een lege kaart of een kaart van een ander netwerk lees je: dan is {@code card.valid} false, en
 * kan je diagram bijvoorbeeld alarm slaan.
 *
 * Een andere naam geven: sneak + rechtsklik met een lege hand (13 oktober), voor wie hem neerzette of wie
 * vertrouwd is in de basis (zie InputDevices). Of op de desktop, in de app Devices of Logic.
 */
public class KeycardReaderBlock extends InputDeviceBlock {
    public static final MapCodec<KeycardReaderBlock> CODEC = simpleCodec(KeycardReaderBlock::new);

    public KeycardReaderBlock(Properties properties) {
        // 6 breed, 10 hoog, 2 diep, tegen de muur.
        super(properties, 5, 3, 14, 11, 13, 16);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected String defaultNamePrefix() {
        return "Reader";
    }

    /** Een kaart tegen de lezer houden. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof KeycardItem)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit); // bijvoorbeeld de Bypass Kit
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            LogicEvents.keycard(serverLevel, pos, serverPlayer, KeycardItem.data(stack));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Zonder kaart: zeg wat je moet doen. Sneak + rechtsklik met een lege hand (13 oktober): het naamscherm, als
     * je mag (zie InputDevices). Met iets in je hand en sneak slaat Minecraft dit zelf over: dan gebruik je dat
     * ding (de Bypass Kit begint dan gewoon zijn hack).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isSecondaryUseActive()) {
                InputDevices.open(serverPlayer, pos);
            } else {
                player.displayClientMessage(Component.translatable("logic.starktech.reader.hint").withStyle(ChatFormatting.GRAY), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
