package com.senne.starktech.edith;

import com.senne.starktech.registry.ModArmorMaterials;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * De E.D.I.T.H.-bril zelf. Hij wordt gedragen in het helm-slot. Alle slimme dingen
 * (HUD, scans, energie) gebeuren in EdithServer (server) en in het client-pakket (scherm).
 */
public class EdithGlassesItem extends ArmorItem {
    /** Kleur van de energiebalk onder het item: Stark-blauw. */
    private static final int ENERGY_BAR_COLOR = 0x3FD8FF;

    public EdithGlassesItem(Properties properties) {
        // HELMET: de bril gaat in het helm-slot. Het materiaal bepaalt de texture op je hoofd.
        super(ModArmorMaterials.edith(), Type.HELMET, properties);
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.starktech.edith_glasses.subtitle").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));

        EdithOwner owner = EdithData.getOwner(stack);
        if (owner == null) {
            tooltip.add(Component.translatable("tooltip.starktech.owner.none").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.starktech.owner", owner.name()).withStyle(ChatFormatting.GRAY));
            if (owner.transferPending()) {
                tooltip.add(Component.translatable("tooltip.starktech.transfer_pending").withStyle(ChatFormatting.GOLD));
            }
        }

        int energy = EdithData.getEnergy(stack);
        int max = EdithData.getMaxEnergy(stack);
        int percent = Mth.floor(100.0f * energy / max);
        tooltip.add(Component.translatable("tooltip.starktech.energy", energy, max, percent).withStyle(ChatFormatting.AQUA));

        EdithUpgrades upgrades = EdithData.getUpgrades(stack);
        tooltip.add(Component.translatable("tooltip.starktech.upgrade.battery", upgrades.battery(), EdithBalance.MAX_BATTERY_UPGRADES).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.upgrade.scanner", upgrades.scanner(), EdithBalance.MAX_SCANNER_UPGRADES).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.upgrade.drones", upgrades.drones(), EdithConfig.maxDroneUpgrades()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
