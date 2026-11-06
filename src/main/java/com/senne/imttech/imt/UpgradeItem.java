package com.senne.imttech.imt;

import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import com.senne.imttech.config.ImtConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Een upgrade voor de bril (batterij, scanner, drones). Sinds hoofdstuk 54 bouw je hem in aan de harnas-werkbank: de
 * bril op het hologram, de upgrade in je hand, klik (zoals de upgrades van het harnas).
 */
public class UpgradeItem extends Item {
    public enum Type {
        BATTERY, SCANNER, DRONE
    }

    private final Type type;

    public UpgradeItem(Type type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public Type getUpgradeType() {
        return type;
    }

    /** Kan deze upgrade nog in deze bril? (Niet als het maximum al bereikt is.) */
    public boolean canApplyTo(ItemStack glasses) {
        ImtUpgrades upgrades = ImtData.getUpgrades(glasses);
        return switch (type) {
            case BATTERY -> upgrades.battery() < Math.round(ImtConfig.Glasses.MAX_BATTERY_UPGRADES);
            case SCANNER -> upgrades.scanner() < ImtConfig.Scanner.MAX_SCANNER_UPGRADES;
            case DRONE -> upgrades.drones() < Math.round(ImtConfig.Drones.MAX_UPGRADES);
        };
    }

    /** Bouwt de upgrade in. Controleer eerst canApplyTo! */
    public void applyTo(ItemStack glasses) {
        ImtUpgrades upgrades = ImtData.getUpgrades(glasses);
        ImtData.setUpgrades(glasses, switch (type) {
            case BATTERY -> upgrades.withBattery(upgrades.battery() + 1);
            case SCANNER -> upgrades.withScanner(upgrades.scanner() + 1);
            case DRONE -> upgrades.withDrones(upgrades.drones() + 1);
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String key = switch (type) {
            case BATTERY -> "tooltip.imttech.battery_upgrade";
            case SCANNER -> "tooltip.imttech.scanner_upgrade";
            case DRONE -> "tooltip.imttech.drone_upgrade";
        };
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.upgrade_hint").withStyle(ChatFormatting.DARK_AQUA));
    }

    /** Sneak + klik op de harnas-werkbank gaat naar de werkbank (het volgende stuk kiezen, hoofdstuk 54), ook met dit in je hand. */
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, net.minecraft.world.level.LevelReader level, BlockPos pos, Player player) {
        return level.getBlockState(pos).is(com.senne.imttech.registry.ModBlocks.SUIT_WORKBENCH.get());
    }
}
