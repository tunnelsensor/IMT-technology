package com.senne.starktech.workbench;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.suit.RocketPackItem;
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
 * - RAKETKOP:     in de borstplaat met raketpakket, tot 3 keer: sterkere mini-raketten.
 * - MAGAZIJN:     in de borstplaat met raketpakket, tot 3 keer: meer raketten per salvo.
 *
 * (Het raketpakket is een apart item, RocketPackItem; dat kan ook aan de werkbank. De twee
 * raket-upgrades passen pas als het pakket erop zit.)
 */
public class SuitModuleItem extends Item {
    public enum Kind {
        THRUSTERS, PLATING, CAPACITOR, WARHEAD, MAGAZINE
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
            case THRUSTERS -> suit.getType() == ArmorItem.Type.BOOTS && upgrades.thrusters() < Math.round(StarkConfig.Workbench.MAX_THRUSTERS);
            case PLATING -> upgrades.plating() < Math.round(StarkConfig.Workbench.MAX_PLATING);
            case CAPACITOR -> suit.getType() == ArmorItem.Type.CHESTPLATE && upgrades.capacitor() < Math.round(StarkConfig.Workbench.MAX_CAPACITORS);
            case WARHEAD -> RocketPackItem.installedOn(piece) && upgrades.warheads() < Math.round(StarkConfig.Workbench.MAX_WARHEADS);
            case MAGAZINE -> RocketPackItem.installedOn(piece) && upgrades.magazines() < Math.round(StarkConfig.Workbench.MAX_MAGAZINES);
        };
    }

    /** Bouwt de upgrade in. Kijk eerst met canApplyTo! */
    public void applyTo(ItemStack piece) {
        SuitUpgrades u = SuitUpgrades.of(piece);
        SuitUpgrades.set(piece, switch (kind) {
            case THRUSTERS -> new SuitUpgrades(u.thrusters() + 1, u.plating(), u.capacitor(), u.warheads(), u.magazines());
            case PLATING -> new SuitUpgrades(u.thrusters(), u.plating() + 1, u.capacitor(), u.warheads(), u.magazines());
            case CAPACITOR -> new SuitUpgrades(u.thrusters(), u.plating(), u.capacitor() + 1, u.warheads(), u.magazines());
            case WARHEAD -> new SuitUpgrades(u.thrusters(), u.plating(), u.capacitor(), u.warheads() + 1, u.magazines());
            case MAGAZINE -> new SuitUpgrades(u.thrusters(), u.plating(), u.capacitor(), u.warheads(), u.magazines() + 1);
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String id = kind.name().toLowerCase(java.util.Locale.ROOT);
        tooltip.add(Component.translatable("tooltip.starktech.module." + id).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.module.hint").withStyle(ChatFormatting.DARK_AQUA));
    }
}
