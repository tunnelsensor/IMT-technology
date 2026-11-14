package com.senne.imttech.car;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Een boorkop voor je RC Car (hoofdstuk 108, zie {@link DrillHead}): ijzer, steel, titanium of netherite. Rechtsklik op
 * de auto met de kop in je hand: hij zit erop, en de kop die erop zat krijg je terug (zie RcCars.swapDrill). De ijzeren
 * kop is ook een onderdeel van de auto zelf (zijn recept).
 */
public class DrillHeadItem extends Item {
    private final DrillHead head;

    public DrillHeadItem(DrillHead head, Properties properties) {
        super(properties);
        this.head = head;
    }

    public DrillHead head() {
        return head;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.drill_head.speed", Math.round(head.speed() * 100))
                .withStyle(ChatFormatting.GRAY));
        if (head.canDrill(net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState())) {
            tooltip.add(Component.translatable("tooltip.imttech.drill_head.obsidian").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.imttech.drill_head.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
