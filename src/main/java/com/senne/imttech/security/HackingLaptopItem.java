package com.senne.imttech.security;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * De hacklaptop: klik ermee op de beveiligingskern van een Biebia's Industries Tower, dan zet je hem
 * opengeklapt bovenop de kern en begint de hack (zie TowerHack). Je hebt er geen bril voor nodig.
 * Is de toren van jou, dan krijg je hem terug.
 *
 * Sinds hoofdstuk 40 (30 oktober) is hij ook je laptop voor op afstand: rechtsklik in de lucht (of op een blok dat
 * geen kern is) opent het inlogscherm. Met het IP en het wachtwoord van een kern kom je op zijn desktop, waar je ook
 * bent in dezelfde dimensie (zie SecurityOs.login).
 */
public class HackingLaptopItem extends Item {
    public HackingLaptopItem(Properties properties) {
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

    /** In de lucht gebruikt: het inlogscherm (IP + wachtwoord, zoals SSH). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            com.senne.imttech.security.os.SecurityOs.promptLogin(serverPlayer, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.hacking_laptop").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.hacking_laptop.use").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.imttech.hacking_laptop.ssh").withStyle(ChatFormatting.DARK_AQUA));
        String lastIp = stack.get(com.senne.imttech.registry.ModDataComponents.LAST_IP.get());
        if (lastIp != null && !lastIp.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.imttech.hacking_laptop.last_ip", lastIp).withStyle(ChatFormatting.GRAY));
        }
    }
}
