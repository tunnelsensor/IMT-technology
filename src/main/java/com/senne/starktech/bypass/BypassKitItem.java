package com.senne.starktech.bypass;

import com.senne.starktech.logic.InputDeviceBlock;
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
 * De Bypass Kit (hoofdstuk 12): een inbraakset met een schroevendraaier, draadjes, een Stark-chip en
 * een potje poeder voor vingerafdrukken. Rechtsklik ermee op een keycardlezer of codepaneel en je
 * begint een hack (zie Bypass): bij een lezer het stroomcircuit.
 *
 * Hij gaat {@link BypassBalance#KIT_USES} keer mee: elke hack die je begint kost er één.
 *
 * De lezer en het paneel doen zelf niets met de kit (zie InputDeviceBlock.useItemOn): zo komt de
 * rechtsklik hier aan.
 */
public class BypassKitItem extends Item {
    public BypassKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof InputDeviceBlock)) {
            return InteractionResult.PASS;
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            Bypass.start(player, context.getClickedPos(), context.getHand());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starktech.bypass_kit.reader").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.bypass_kit.uses", stack.getMaxDamage() - stack.getDamageValue(),
                stack.getMaxDamage()).withStyle(ChatFormatting.DARK_AQUA));
    }
}
