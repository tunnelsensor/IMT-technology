package com.senne.starktech.bot;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Robot Ammo (hoofdstuk 24): de kogels van de Gun-module. Rechtsklik een guard robot met een stapel in je hand
 * en hij laadt ze (zie GuardRobotEntity.mobInteract); elk schot kost er één.
 */
public class RobotAmmoItem extends Item {
    public RobotAmmoItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starktech.robot_ammo").withStyle(ChatFormatting.GRAY));
    }
}
