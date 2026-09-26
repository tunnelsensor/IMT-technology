package com.senne.starktech.workbench;

import com.senne.starktech.suit.StarkSuitItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Een upgrade voor het harnas, om in te bouwen aan de harnas-werkbank.
 *
 * - STUWRAKETTEN: in de laarzen, tot 3 keer: sneller en zuiniger vliegen.
 * - BEPANTSERING: in elk stuk, tot 2 keer per stuk: het harnas houdt meer schade tegen.
 * - CONDENSATOR:  in de borstplaat, tot 2 keer: een sterkere repulsor.
 *
 * (Het raketpakket is een apart item, RocketPackItem; dat kan ook aan de werkbank.)
 */
public class SuitModuleItem extends Item {
    public enum Kind {
        THRUSTERS, PLATING, CAPACITOR
    }

    private final Kind kind;

    public SuitModuleItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** Kan deze upgrade (nog) in dit stuk harnas? */
    public boolean canApplyTo(ItemStack piece) {
        if (!(piece.getItem() instanceof StarkSuitItem suit)) {
            return false;
        }
        SuitUpgrades upgrades = SuitUpgrades.of(piece);
        return switch (kind) {
            case THRUSTERS -> suit.getType() == ArmorItem.Type.BOOTS && upgrades.thrusters() < WorkbenchBalance.MAX_THRUSTERS;
            case PLATING -> upgrades.plating() < WorkbenchBalance.MAX_PLATING;
            case CAPACITOR -> suit.getType() == ArmorItem.Type.CHESTPLATE && upgrades.capacitor() < WorkbenchBalance.MAX_CAPACITORS;
        };
    }

    /** Bouwt de upgrade in. Kijk eerst met canApplyTo! */
    public void applyTo(ItemStack piece) {
        SuitUpgrades u = SuitUpgrades.of(piece);
        SuitUpgrades.set(piece, switch (kind) {
            case THRUSTERS -> new SuitUpgrades(u.thrusters() + 1, u.plating(), u.capacitor());
            case PLATING -> new SuitUpgrades(u.thrusters(), u.plating() + 1, u.capacitor());
            case CAPACITOR -> new SuitUpgrades(u.thrusters(), u.plating(), u.capacitor() + 1);
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String id = kind.name().toLowerCase(java.util.Locale.ROOT);
        tooltip.add(Component.translatable("tooltip.starktech.module." + id).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.module.hint").withStyle(ChatFormatting.DARK_AQUA));
    }
}
