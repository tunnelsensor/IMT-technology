package com.senne.imttech.imt;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModArmorMaterials;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * De I.M.T-bril zelf. Hij wordt gedragen in het helm-slot. Alle slimme dingen
 * (HUD, scans, energie) gebeuren in ImtServer (server) en in het client-pakket (scherm).
 */
public class ImtGlassesItem extends ArmorItem {
    /** Kleur van de energiebalk onder het item: I.M.T-blauw. */
    private static final int ENERGY_BAR_COLOR = 0x3FD8FF;

    public ImtGlassesItem(Properties properties) {
        // HELMET: de bril gaat in het helm-slot. Het materiaal bepaalt de texture op je hoofd.
        super(ModArmorMaterials.imt(), Type.HELMET, properties);
    }

    // ---------------------------------------------------------------- Energiebalk op het item-icoon

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        // Minecraft tekent de balk 13 pixels breed.
        return Math.round(13.0f * ImtData.getEnergyFraction(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ENERGY_BAR_COLOR;
    }

    // ---------------------------------------------------------------- Tooltip (tekst als je met de muis over het item gaat)

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.imttech.imt_glasses.subtitle").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));

        ImtOwner owner = ImtData.getOwner(stack);
        if (owner == null) {
            tooltip.add(Component.translatable("tooltip.imttech.owner.none").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.imttech.owner", owner.name()).withStyle(ChatFormatting.GRAY));
            if (owner.transferPending()) {
                tooltip.add(Component.translatable("tooltip.imttech.transfer_pending").withStyle(ChatFormatting.GOLD));
            }
        }

        int energy = ImtData.getEnergy(stack);
        int max = ImtData.getMaxEnergy(stack);
        int percent = Mth.floor(100.0f * energy / max);
        tooltip.add(Component.translatable("tooltip.imttech.energy", energy, max, percent).withStyle(ChatFormatting.AQUA));

        ImtUpgrades upgrades = ImtData.getUpgrades(stack);
        tooltip.add(Component.translatable("tooltip.imttech.upgrade.battery", upgrades.battery(), Math.round(ImtConfig.Glasses.MAX_BATTERY_UPGRADES)).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.upgrade.scanner", upgrades.scanner(), ImtConfig.Scanner.MAX_SCANNER_UPGRADES).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.upgrade.drones", upgrades.drones(), Math.round(ImtConfig.Drones.MAX_UPGRADES)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
