package com.senne.imttech.cuffs;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * De sleutel van de handboeien (hoofdstuk 48): rechtsklik op wie geboeid is, en hij is los. Jij krijgt de boeien terug.
 * (Wie zelf geboeid is, kan geen sleutel gebruiken: zijn handen zitten vast.)
 */
public class HandcuffKeyItem extends Item {
    public HandcuffKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(player instanceof ServerPlayer by)) {
            return Handcuffs.isCuffed(target) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        if (!Handcuffs.release(target, by, true)) {
            by.displayClientMessage(Component.translatable("cuffs.imttech.not_cuffed", target.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.PASS;
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.imttech.handcuff_key").withStyle(ChatFormatting.GRAY));
    }
}
