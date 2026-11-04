package com.senne.imttech.security;

import com.senne.imttech.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * De I.M.T Phone (hoofdstuk 50): de desktop van je kern in je zak, maar kleiner dan de laptop. Rechtsklik: de eerste
 * keer verbind je hem met een kern (IP + wachtwoord, zoals de laptop) en kies je een PIN van vier cijfers; daarna vraagt
 * hij elke keer die PIN. Je ziet Status (met de autorisatiecode), Logs, Cameras, Devices, Requests (accepteren of
 * weigeren) en Messages. Een gestolen telefoon is zonder PIN niets waard, en de eigenaar kan hem in de app Access
 * blokkeren. Zie security/os/Phones.
 */
public class PhoneItem extends Item {
    public PhoneItem(Properties properties) {
        super(properties);
    }

    /** Aan welke kern deze telefoon hangt, of null (nog niet verbonden). */
    public static @Nullable PhoneLink link(ItemStack stack) {
        return stack.is(com.senne.imttech.registry.ModItems.PHONE.get()) ? stack.get(ModDataComponents.PHONE.get()) : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            com.senne.imttech.security.os.Phones.use(serverPlayer, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PhoneLink link = stack.get(ModDataComponents.PHONE.get());
        if (link == null) {
            tooltip.add(Component.translatable("tooltip.imttech.phone.unlinked").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.imttech.phone.linked", link.host(), link.ip()).withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("tooltip.imttech.phone.apps").withStyle(ChatFormatting.GRAY));
    }
}
