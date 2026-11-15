package com.senne.imttech.car;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * De RC Robot Arm (hoofdstuk 118, jouw wens: "zorg dat tijdens het minen met de rc car, als die hoger dan 1 block wil
 * minen, dus boven of onder zich, die een robotarm met drill nodig heeft").
 *
 * Een nieuwe RC Car heeft alleen de boor op zijn neus: hij breekt het blok recht voor zich, en hij kan alleen bij de kist
 * recht voor zich. Met deze arm erop zit zijn boor op een arm die hij programmeert: dan kan hij ook hoger
 * ({@code arm_up()}), boven ({@code mine_up()}) en onder zich ({@code mine_down()}) minen, en kisten tot twee blokken
 * hoger bereiken (jouw keuze: "één onderdeel voor alles"). De boor op de arm is gewoon zijn boorkop.
 *
 * Erop zetten: rechtsklik op de auto met de arm, zoals een boorkop. Hij gaat mee als je de auto oppakt. Eraf halen: de
 * knop Arm off op het scherm van de auto (zie RcCars.takeArmOff); je krijgt hem terug.
 */
public class RcRobotArmItem extends Item {
    public RcRobotArmItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.rc_robot_arm.what").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.rc_robot_arm.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
