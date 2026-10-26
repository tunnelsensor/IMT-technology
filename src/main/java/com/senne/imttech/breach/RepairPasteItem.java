package com.senne.imttech.c4;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.security.zone.Zones;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/**
 * Repair Paste (hoofdstuk 11, jouw wens: "met een pasta opvullen, een beetje plamuren"). Rechtsklik op
 * een blok met barsten, en de barsten zijn weg. Het blok is daarna wel {@link ImtConfig.Repair#PATCH_WEAKER}
 * zwakker, elke keer opnieuw (nooit zwakker dan {@link ImtConfig.Repair#PATCH_WEAKEST}). Wil je een blok dat
 * weer helemaal sterk is, vervang het dan.
 *
 * In een beveiligde zone mag alleen wie er mag bouwen plamuren (mijn invulling): anders kan een
 * inbreker een muur met wat TNT en pasta steeds zwakker maken, tot één C4 genoeg is.
 */
public class RepairPasteItem extends Item {
    public RepairPasteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            // Alleen de server weet waar barsten zitten: die beslist (en laat je arm dan zwaaien).
            return InteractionResult.CONSUME;
        }
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (Cracks.damage(level, pos) <= 0) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("item.imttech.repair_paste.no_cracks"), true);
            }
            return InteractionResult.PASS;
        }
        if (Zones.denies(level, pos, player)) {
            if (player != null) {
                Zones.tellProtected(player);
            }
            return InteractionResult.FAIL;
        }
        Cracks.patch(level, pos);

        // Een smeergeluid en wat pasta die eraf spat, op de plek waar je klikte.
        level.playSound(null, pos, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 1.0f, 0.8f);
        Vec3 at = context.getClickLocation();
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, context.getItemInHand().copyWithCount(1)),
                at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.05);
        if (player != null) {
            int percent = Math.round(Cracks.factor(Cracks.patches(level, pos)) * 100);
            player.displayClientMessage(Component.translatable("item.imttech.repair_paste.patched", percent), true);
        }
        context.getItemInHand().consume(1, player); // in creative gaat er niets op
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.repair_paste.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.repair_paste.weaker",
                Math.round(ImtConfig.Repair.PATCH_WEAKER * 100), Math.round(ImtConfig.Repair.PATCH_WEAKEST * 100))
                .withStyle(ChatFormatting.GRAY));
    }
}
