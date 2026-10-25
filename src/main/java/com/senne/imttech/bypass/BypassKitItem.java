package com.senne.imttech.bypass;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.InputDeviceBlock;
import com.senne.imttech.shield.LaserEmitterBlock;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/**
 * De Bypass Kit (hoofdstuk 12): een inbraakset met een schroevendraaier, draadjes, een computerchip, een
 * schaar en een potje poeder voor vingerafdrukken. Rechtsklik ermee op een keycardlezer, codepaneel of
 * laserzender en je begint een hack (zie Bypass): bij een lezer het stroomcircuit, bij een paneel de
 * vingerafdrukken, bij een laserzender de kluwen van draden (hoofdstuk 33).
 *
 * Hij gaat {@link ImtConfig.Bypass#KIT_USES} keer mee: elke hack die je begint kost er één.
 *
 * De lezer, het paneel en de laserzender doen zelf niets met de kit (zie InputDeviceBlock.useItemOn en
 * LaserEmitterBlock.useWithoutItem): zo komt de rechtsklik hier aan.
 */
public class BypassKitItem extends Item {
    public BypassKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Block block = context.getLevel().getBlockState(context.getClickedPos()).getBlock();
        if (!(block instanceof InputDeviceBlock) && !(block instanceof LaserEmitterBlock)) {
            return InteractionResult.PASS;
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            Bypass.start(player, context.getClickedPos(), context.getHand());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.bypass_kit.reader").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.bypass_kit.panel").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.bypass_kit.laser").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.bypass_kit.uses", stack.getMaxDamage() - stack.getDamageValue(),
                stack.getMaxDamage()).withStyle(ChatFormatting.DARK_AQUA));
    }
}
