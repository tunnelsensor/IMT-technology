package com.senne.imttech.weapons;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * De Gun Sling (hoofdstuk 55, jouw keuze): een leren draagriem die je op de Laser Workbench aan een laser rifle, sniper
 * of shotgun hangt (het vak "sling" op de tafel; een pistool heeft er geen). Een geweer met een sling past in een van de
 * twee sling-vakjes naast je harnas (op je rug en voor je borst) en kun je daar met het wiel uit trekken. Leer en touw
 * aan de werkbank.
 */
public class GunSlingItem extends Item {
    public GunSlingItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.gun_sling").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.gun_sling.table").withStyle(ChatFormatting.DARK_GRAY));
    }
}
