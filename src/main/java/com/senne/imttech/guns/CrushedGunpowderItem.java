package com.senne.imttech.guns;

import com.senne.imttech.config.ImtConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Crushed Gunpowder (hoofdstuk 76, jouw wens: "Voeg een recept toe via de crusher: crushed gunpowder, van 1 gunpowder
 * krijg je 10; voor kogels te maken heb je nu crushed gunpowder nodig, dat betekent dus dat je met 1 gunpowder 10 9mm
 * bullets kunt maken").
 *
 * Zo werkt het:
 * <ul>
 *   <li>De <b>Crusher</b> maakt van één buskruit er {@link #perGunpowder()} (standaard 10, zie
 *       {@code CrackMachineBlock.Kind.convert}).</li>
 *   <li>De <b>Reloading Bench</b> neemt er één per 9mm-patroon, en gewoon buskruit niet meer (zie
 *       {@link ReloadingBenchBlockEntity#fits}). Eén buskruit is zo tien patronen.</li>
 * </ul>
 * De tooltip zegt allebei, zodat je weet waar het vandaan komt als je het ergens vindt.
 */
public class CrushedGunpowderItem extends Item {
    public CrushedGunpowderItem(Properties properties) {
        super(properties);
    }

    /**
     * Zoveel Crushed Gunpowder maakt de Crusher van één buskruit: het getal uit de config
     * ({@link ImtConfig.Guns#CRUSHED_PER_GUNPOWDER}), afgerond en tussen 1 en 64 (meer past niet op één stapel).
     */
    public static int perGunpowder() {
        return Math.max(1, Math.min(64, Math.round(ImtConfig.Guns.CRUSHED_PER_GUNPOWDER)));
    }

    /** Twee grijze regels: waar het voor dient, en hoe je het maakt. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.crushed_gunpowder").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.crushed_gunpowder.crusher", perGunpowder())
                .withStyle(ChatFormatting.GRAY));
    }
}
