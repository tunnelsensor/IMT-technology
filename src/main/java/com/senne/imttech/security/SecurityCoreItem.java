package com.senne.imttech.security;

import com.senne.imttech.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * De beveiligingskern als item. Een nieuwe kern is van wie hem neerzet; een weggehakte kern heeft een
 * etiket ({@link PackedCore}) en krijgt bij het neerzetten al zijn instellingen terug (zie
 * SecurityCoreBlock.setPlacedBy). De tooltip laat zien welke kern het is.
 */
public class SecurityCoreItem extends BlockItem {
    public SecurityCoreItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PackedCore packed = stack.get(ModDataComponents.PACKED_CORE.get());
        if (packed == null) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.imttech.security_core.packed", packed.host(), packed.ip())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.imttech.security_core.owner", packed.owner())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.security_core.kept").withStyle(ChatFormatting.GRAY));
    }
}
