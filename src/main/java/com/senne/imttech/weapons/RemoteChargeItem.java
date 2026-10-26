package com.senne.imttech.weapons;

import com.senne.imttech.breach.BlastAlarm;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De Remote EMP en de Remote Frag als item (hoofdstuk 35): plak hem tegen een vloer, muur of plafond, ook in de zone
 * van een ander (een lading mag altijd, anders kun je nergens binnen). Heb je een trigger van dezelfde soort bij je, dan
 * hoort hij meteen bij die trigger. Een Remote Frag op de zone van een ander laat het alarm afgaan (zie BlastAlarm).
 */
public class RemoteChargeItem extends BlockItem {
    private final RemoteChargeBlock charge;

    public RemoteChargeItem(RemoteChargeBlock block, Properties properties) {
        super(block, properties);
        this.charge = block;
    }

    public RemoteChargeBlock.Kind kind() {
        return charge.kind();
    }

    /** Net geplakt: wie het deed, meteen koppelen aan de trigger die je bij je hebt, en (een frag) het alarm. */
    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        boolean changed = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof RemoteChargeBlockEntity placed) {
            placed.placedBy(player);
            level.playSound(null, pos, ModSounds.EMP_GRENADE_STICK.get(), SoundSource.BLOCKS, 0.8f, 0.9f);
            if (player != null) {
                ItemStack trigger = TriggerItem.find(player, kind());
                Component message;
                if (trigger == null) {
                    message = Component.translatable(charge.key("no_trigger")).withStyle(ChatFormatting.YELLOW);
                } else if (TriggerItem.link(serverLevel, pos, placed, trigger)) {
                    message = Component.translatable(charge.key("linked"), TriggerItem.ensure(trigger).charges().size())
                            .withStyle(((TriggerItem) trigger.getItem()).colour());
                } else {
                    message = Component.translatable(charge.key("full"), TriggerData.MAX_CHARGES).withStyle(ChatFormatting.YELLOW);
                }
                player.displayClientMessage(message, true);
            }
            if (kind() == RemoteChargeBlock.Kind.FRAG) {
                BlastAlarm.planted(serverLevel, pos, placed);
            }
        }
        return changed;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (kind() == RemoteChargeBlock.Kind.FRAG) {
            tooltip.add(Component.translatable("tooltip.imttech.remote_frag").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.imttech.frag_grenade.what").withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.imttech.remote_frag.defuse").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.imttech.remote_emp").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.imttech.emp_grenade.what", (int) ImtConfig.Weapons.EMP_RADIUS,
                    Math.round(ImtConfig.Weapons.EMP_SECONDS)).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
