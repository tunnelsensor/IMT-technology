package com.senne.imttech.robot;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Een onderdeel van een guard robot (hoofdstuk 21): een hoofd, lijf, armen of benen in een van de vier
 * stijlen. Er zijn er 16 (zie ModItems.ROBOT_PARTS). Je zet ze in de Robot Assembler in elkaar.
 *
 * Als je erover beweegt zie je wat het onderdeel doet, met de getallen uit de config: zo kun je kiezen.
 */
public class RobotPartItem extends Item {
    private final RobotSlot slot;
    private final RobotStyle style;

    public RobotPartItem(Properties properties, RobotSlot slot, RobotStyle style) {
        super(properties);
        this.slot = slot;
        this.style = style;
    }

    public RobotSlot slot() {
        return slot;
    }

    public RobotStyle style() {
        return style;
    }

    /** De naam in het register: "super_battle_legs". */
    public static String id(RobotSlot slot, RobotStyle style) {
        return style.id() + "_" + slot.id();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Een robot die helemaal uit deze stijl bestaat, geeft precies de getallen van dit onderdeel.
        RobotParts parts = new RobotParts(style, style, style, style);
        Component line = switch (slot) {
            case HEAD -> Component.translatable("tooltip.imttech.robot_part.head",
                    whole(parts.scanRange()), whole(parts.scanAngle()));
            case BODY -> Component.translatable("tooltip.imttech.robot_part.body",
                    whole(parts.health()), whole(parts.armor()), whole(parts.battery()));
            case ARMS -> Component.translatable("tooltip.imttech.robot_part.arms",
                    String.format(Locale.ROOT, "%.1f", parts.fireInterval() / 20.0));
            case LEGS -> Component.translatable("tooltip.imttech.robot_part.legs",
                    String.format(Locale.ROOT, "%.2f", parts.speed()));
        };
        tooltip.add(line.copy().withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.robot_part.use").withStyle(ChatFormatting.DARK_AQUA));
    }

    private static String whole(double value) {
        return Long.toString(Math.round(value));
    }
}
