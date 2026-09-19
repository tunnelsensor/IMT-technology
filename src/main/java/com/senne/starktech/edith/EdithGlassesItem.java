package com.senne.starktech.edith;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * De E.D.I.T.H.-bril zelf. Hij wordt gedragen in het helm-slot. Alle slimme dingen
 * (HUD, scans, energie) gebeuren in EdithServer (server) en in het client-pakket (scherm).
 */
public class EdithGlassesItem extends Item {
    /** Kleur van de energiebalk onder het item: Stark-blauw. */
    private static final int ENERGY_BAR_COLOR = 0x3FD8FF;

    public EdithGlassesItem(Properties properties) {
        super(properties);
    }

    // ---------------------------------------------------------------- Energiebalk op het item-icoon

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        // Minecraft tekent de balk 13 pixels breed.
        return Math.round(13.0f * EdithData.getEnergyFraction(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ENERGY_BAR_COLOR;
    }

    // ---------------------------------------------------------------- Tooltip (tekst als je met de muis over het item gaat)

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.starktech.edith_glasses.subtitle").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));

        EdithOwner owner = EdithData.getOwner(stack);
        if (owner == null) {
            tooltip.accept(Component.translatable("tooltip.starktech.owner.none").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(Component.translatable("tooltip.starktech.owner", owner.name()).withStyle(ChatFormatting.GRAY));
            if (owner.transferPending()) {
                tooltip.accept(Component.translatable("tooltip.starktech.transfer_pending").withStyle(ChatFormatting.GOLD));
            }
        }

        int energy = EdithData.getEnergy(stack);
        int max = EdithData.getMaxEnergy(stack);
        int percent = Mth.floor(100.0f * energy / max);
        tooltip.accept(Component.translatable("tooltip.starktech.energy", energy, max, percent).withStyle(ChatFormatting.AQUA));

        EdithUpgrades upgrades = EdithData.getUpgrades(stack);
        tooltip.accept(Component.translatable("tooltip.starktech.upgrade.battery", upgrades.battery(), EdithBalance.MAX_BATTERY_UPGRADES).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.starktech.upgrade.scanner", upgrades.scanner(), EdithBalance.MAX_SCANNER_UPGRADES).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.starktech.upgrade.drones", upgrades.drones(), EdithConfig.maxDroneUpgrades()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
