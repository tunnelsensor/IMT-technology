package com.senne.imttech.shield;

import com.senne.imttech.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * De laserzender als item. Een gewone zender is gewoon een blok om neer te zetten. Een zender die je weghakte terwijl
 * zijn draden doorgeknipt waren (hoofdstuk 33), onthoudt dat ({@link ModDataComponents#LASER_CUT}): zet je hem weer
 * neer, dan is hij nog steeds kapot (zie LaserEmitterBlock.setPlacedBy). Zo kun je de draden niet gratis herstellen
 * door de zender weg te hakken en terug te zetten. De tooltip zegt het, en zo'n zender stapelt niet met hele.
 */
public class LaserEmitterItem extends BlockItem {
    public LaserEmitterItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** Heeft deze zender doorgeknipte draden? */
    public static boolean isCut(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.LASER_CUT.get()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (isCut(stack)) {
            tooltip.add(Component.translatable("tooltip.imttech.laser_emitter.cut").withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.imttech.laser_emitter.cut_repair").withStyle(ChatFormatting.GRAY));
        }
    }
}
