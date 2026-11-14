package com.senne.imttech.car;

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
 * De RC Controller (hoofdstuk 108, jouw wens: "functions die je via een controller kunt aansturen: als je die kiest,
 * voert hij de code uit voor die functie").
 *
 * <ul>
 *   <li><b>Koppelen</b>: rechtsklik op een RC Car met de controller in je hand (als je die auto mag instellen). Zet je
 *       een auto neer terwijl je een controller bij je hebt die nog nergens mee gekoppeld is, dan koppelt die
 *       vanzelf. De koppeling blijft, ook als je de auto oppakt en ergens anders neerzet.</li>
 *   <li><b>Gebruiken</b>: rechtsklik (in de lucht of op een blok). Het scherm van de auto gaat open, van zo ver als
 *       {@code controlRange} reikt (64 blokken): zijn functies als knoppen, Start en Stop, de pijltjes, en zijn
 *       programma. Is de auto te ver, opgepakt of in een andere wereld: "No signal".</li>
 * </ul>
 */
public class RcControllerItem extends Item {
    public RcControllerItem(Properties properties) {
        super(properties);
    }

    /** Met welke auto deze controller gekoppeld is, of null. */
    public static @Nullable RcLink link(ItemStack stack) {
        return stack.get(ModDataComponents.RC_LINK.get());
    }

    public static void setLink(ItemStack stack, RcCarEntity car) {
        stack.set(ModDataComponents.RC_LINK.get(), new RcLink(car.link(), car.displayName()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            RcCars.useController(serverPlayer, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RcLink link = link(stack);
        tooltip.add(link == null
                ? Component.translatable("tooltip.imttech.rc_controller.unlinked").withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.imttech.rc_controller.linked", link.name()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.imttech.rc_controller.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
