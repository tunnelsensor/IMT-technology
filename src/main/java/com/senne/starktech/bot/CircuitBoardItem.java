package com.senne.starktech.bot;

import com.senne.starktech.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * De Robot Circuit Board (hoofdstuk 21): het brein van een robot, met 8 genummerde pinnen. Zonder printplaat
 * kun je geen robot in elkaar zetten. Wat erop gesoldeerd is (hoofdstuk 22), staat in het item (zie
 * BotBoard); een nieuwe printplaat is leeg.
 */
public class CircuitBoardItem extends Item {
    public CircuitBoardItem(Properties properties) {
        super(properties);
    }

    /** De printplaat op dit item (een nieuwe is leeg). */
    public static BotBoard board(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.BOT_BOARD.get(), BotBoard.EMPTY);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BotBoard board = board(stack);
        tooltip.add(Component.translatable("tooltip.starktech.circuit_board.pins", BotBoard.PINS, board.used())
                .withStyle(ChatFormatting.GRAY));
        for (int pin = 1; pin <= BotBoard.PINS; pin++) {
            if (!board.pin(pin).isEmpty()) {
                tooltip.add(Component.translatable("tooltip.starktech.circuit_board.pin", pin, board.pin(pin))
                        .withStyle(ChatFormatting.DARK_GREEN));
            }
        }
        tooltip.add(Component.translatable("tooltip.starktech.circuit_board.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
