package com.senne.starktech.c4;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Het item van een lading C4. Je plakt hem met rechtsklik op een vloer, muur of plafond; ook in de
 * zone van een ander (C4 mag altijd, anders werkt het niet).
 *
 * Een C4 (Timer) onthoudt zijn tijd: rechtsklik in de lucht opent een klein scherm om hem in te
 * stellen (5 seconden tot 2 minuten, zie client/c4/C4TimerScreen). Zodra je hem plakt, loopt hij.
 */
public class C4Item extends BlockItem {
    public C4Item(Block block, Properties properties) {
        super(block, properties);
    }

    private C4Block.Kind kind() {
        return getBlock() instanceof C4Block c4 ? c4.kind() : C4Block.Kind.TIMED;
    }

    /** De tijd op deze C4 (Timer), in seconden. */
    public static int timerSeconds(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(ModDataComponents.C4_TIMER.get(), Math.round(StarkConfig.C4.TIMER_DEFAULT)),
                Math.round(StarkConfig.C4.TIMER_MIN), Math.round(StarkConfig.C4.TIMER_MAX));
    }

    /** "0:10", "1:30". */
    public static String clock(int seconds) {
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    /** Rechtsklik in de lucht met een C4 (Timer): het scherm om de tijd te kiezen (alleen in je eigen spel). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (kind() != C4Block.Kind.TIMED) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            com.senne.starktech.client.c4.C4TimerScreen.open(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Net geplakt: wie het deed, en (de timer) meteen laten lopen. */
    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        boolean changed = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof C4BlockEntity c4) {
            c4.placedBy(player);
            if (kind() == C4Block.Kind.TIMED) {
                c4.detonateIn(timerSeconds(stack) * 20);
                level.playSound(null, pos, ModSounds.C4_ARM.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            } else if (player != null && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                // Een remote C4 hoort meteen bij de detonator die je bij je hebt.
                ItemStack detonator = DetonatorItem.find(player);
                Component message;
                if (detonator == null) {
                    message = Component.translatable("item.starktech.remote_c4.no_detonator").withStyle(ChatFormatting.YELLOW);
                } else if (DetonatorItem.link(serverLevel, pos, c4, detonator)) {
                    level.playSound(null, pos, ModSounds.C4_ARM.get(), SoundSource.BLOCKS, 0.8f, 0.7f);
                    message = Component.translatable("item.starktech.remote_c4.linked",
                            DetonatorItem.ensure(detonator).charges().size()).withStyle(ChatFormatting.RED);
                } else {
                    message = Component.translatable("item.starktech.remote_c4.full", DetonatorData.MAX_CHARGES)
                            .withStyle(ChatFormatting.YELLOW);
                }
                player.displayClientMessage(message, true);
            }
            // Op de zone van een ander? Dan gaat het alarm af (zie C4Alarm).
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                C4Alarm.planted(serverLevel, pos, c4);
            }
        }
        return changed;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (kind() == C4Block.Kind.TIMED) {
            tooltip.add(Component.translatable("tooltip.starktech.c4.timer", clock(timerSeconds(stack))).withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.starktech.c4.set_timer").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.starktech.c4.remote").withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.translatable("tooltip.starktech.c4.place").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.c4.defuse").withStyle(ChatFormatting.DARK_GRAY));
    }
}
