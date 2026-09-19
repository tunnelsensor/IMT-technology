package com.senne.starktech.illusion;

import com.senne.starktech.edith.EdithBalance;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * De E.M.P.-zender: een handapparaat dat alle illusies in de buurt platlegt.
 *
 * Denk aan de film: word je voor de gek gehouden door cloakingdrones, dan geeft dit ding een
 * puls die hun beeld uitschakelt (en de drones een tik geeft). Daarna zie je de echte wereld
 * weer - en meteen ook waar die drones hangen.
 *
 * Hij gaat niet kapot, maar je moet er wel even op wachten (cooldown).
 */
public class EmpItem extends Item {
    public EmpItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel) {
            int hit = EdithIllusions.emp(serverLevel, player.position(), EdithBalance.EMP_RADIUS);
            player.displayClientMessage(hit > 0
                    ? Component.translatable("item.starktech.emp.hit", hit)
                    : Component.translatable("item.starktech.emp.empty"), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELYTRA_FLYING,
                    SoundSource.PLAYERS, 0.6f, 0.4f);
        }
        player.getCooldowns().addCooldown(this, EdithBalance.EMP_COOLDOWN);
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starktech.emp").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.emp.range", (int) EdithBalance.EMP_RADIUS)
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
