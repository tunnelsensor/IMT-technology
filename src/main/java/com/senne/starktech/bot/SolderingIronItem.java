package com.senne.starktech.bot;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * De Soldering Iron (hoofdstuk 22): daarmee soldeer je modules op de printplaat van een robot, en weer los.
 * Hij hoeft niet in je hand: ergens in je rugzak is genoeg. Elke keer solderen of lossolderen kost hem
 * een beetje (hij slijt, {@link #USES} keer).
 */
public class SolderingIronItem extends Item {
    /** Zo vaak kun je ermee solderen of lossolderen (vast: hoort bij het item zelf). */
    public static final int USES = 128;

    public SolderingIronItem(Properties properties) {
        super(properties);
    }

    /** De soldeerbout in de rugzak van deze speler (de eerste), of null. */
    public static @Nullable ItemStack find(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof SolderingIronItem) {
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof SolderingIronItem) {
                return stack;
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starktech.soldering_iron").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.soldering_iron.uses", stack.getMaxDamage() - stack.getDamageValue(),
                stack.getMaxDamage()).withStyle(ChatFormatting.DARK_AQUA));
    }
}
