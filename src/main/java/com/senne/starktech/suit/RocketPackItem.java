package com.senne.starktech.suit;

import com.senne.starktech.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Het raketpakket: twee kleine lanceerbuizen voor op de rug van de Stark-borstplaat.
 *
 * Je bouwt het in door het in je hand te nemen en rechts te klikken terwijl je de borstplaat
 * aan hebt. Dan zit het vast aan DIE borstplaat (een "data component", net als de energie van
 * de bril): trek je hem uit en een ander aan, dan gaat het pakket mee met de eerste. Wat de
 * raketten doen staat in {@link SuitRockets}.
 */
public class RocketPackItem extends Item {
    public RocketPackItem(Properties properties) {
        super(properties);
    }

    /** Zit er een raketpakket op deze borstplaat? */
    public static boolean installedOn(ItemStack chestplate) {
        return chestplate.getItem() instanceof StarkSuitItem
                && Boolean.TRUE.equals(chestplate.get(ModDataComponents.ROCKET_PACK.get()));
    }

    /** Heeft deze speler een borstplaat met raketpakket aan? */
    public static boolean worn(Player player) {
        return installedOn(player.getItemBySlot(EquipmentSlot.CHEST));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack pack = player.getItemInHand(hand);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof StarkSuitItem)) {
            player.displayClientMessage(Component.translatable("suit.starktech.msg.pack_needs_chest")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(pack);
        }
        if (installedOn(chest)) {
            player.displayClientMessage(Component.translatable("suit.starktech.msg.pack_already")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(pack);
        }
        if (!level.isClientSide) {
            chest.set(ModDataComponents.ROCKET_PACK.get(), true);
            pack.consume(1, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE,
                    SoundSource.PLAYERS, 0.8f, 1.2f);
            player.displayClientMessage(Component.translatable("suit.starktech.msg.pack_installed")
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return InteractionResultHolder.sidedSuccess(pack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starktech.rocket_pack").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.rocket_pack.install").withStyle(ChatFormatting.DARK_AQUA));
    }
}
