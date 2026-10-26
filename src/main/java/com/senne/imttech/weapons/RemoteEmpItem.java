package com.senne.imttech.weapons;

import com.senne.imttech.c4.DetonatorData;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De Remote EMP als item (hoofdstuk 35): plak hem tegen een vloer, muur of plafond. Heb je een EMP-trigger bij je, dan
 * hoort hij meteen bij die trigger.
 */
public class RemoteEmpItem extends BlockItem {
    public RemoteEmpItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** Net geplakt: wie het deed, en meteen koppelen aan de EMP-trigger die je bij je hebt. */
    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        boolean changed = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof RemoteEmpBlockEntity emp) {
            emp.placedBy(player);
            level.playSound(null, pos, ModSounds.EMP_GRENADE_STICK.get(), SoundSource.BLOCKS, 0.8f, 0.9f);
            if (player != null) {
                ItemStack trigger = EmpTriggerItem.find(player);
                Component message;
                if (trigger == null) {
                    message = Component.translatable("item.imttech.remote_emp.no_trigger").withStyle(ChatFormatting.YELLOW);
                } else if (EmpTriggerItem.link(serverLevel, pos, emp, trigger)) {
                    message = Component.translatable("item.imttech.remote_emp.linked",
                            EmpTriggerItem.ensure(trigger).charges().size()).withStyle(ChatFormatting.AQUA);
                } else {
                    message = Component.translatable("item.imttech.remote_emp.full", DetonatorData.MAX_CHARGES)
                            .withStyle(ChatFormatting.YELLOW);
                }
                player.displayClientMessage(message, true);
            }
        }
        return changed;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.remote_emp").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.emp_grenade.what", (int) ImtConfig.Weapons.EMP_RADIUS,
                Math.round(ImtConfig.Weapons.EMP_SECONDS)).withStyle(ChatFormatting.DARK_AQUA));
    }
}
