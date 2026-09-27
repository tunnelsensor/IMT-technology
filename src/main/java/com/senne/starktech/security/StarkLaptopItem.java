package com.senne.starktech.security;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * De Stark-laptop: klik ermee op de beveiligingskern van een Stark Tower, dan zet je hem
 * opengeklapt bovenop de kern en begint de hack (zie TowerHack). Je hebt er geen bril voor nodig.
 * Is de toren van jou, dan krijg je hem terug.
 */
public class StarkLaptopItem extends Item {
    public StarkLaptopItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof SecurityCoreBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            TowerHack.placeLaptop(player, context.getClickedPos(), context.getItemInHand());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starktech.stark_laptop").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.stark_laptop.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
