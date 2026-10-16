package com.senne.starktech.bot;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Een module als item (hoofdstuk 22): die soldeer je met een Soldering Iron op een pin van de printplaat
 * van een robot (sneak + rechtsklik op de robot, dan Board).
 */
public class BotModuleItem extends Item {
    private final BotModule module;

    public BotModuleItem(Properties properties, BotModule module) {
        super(properties);
        this.module = module;
    }

    public BotModule module() {
        return module;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(module.description()).withStyle(ChatFormatting.GRAY));
        if (module != BotModule.BURNT) {
            tooltip.add(Component.translatable("tooltip.starktech.robot_module.use").withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
