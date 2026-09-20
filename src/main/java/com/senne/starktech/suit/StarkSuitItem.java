package com.senne.starktech.suit;

import com.senne.starktech.edith.EdithData;
import com.senne.starktech.registry.ModArmorMaterials;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Een stuk Stark-harnas: borstplaat, broek of laarzen.
 *
 * Er is geen helm - die plek is van de bril, en dat is precies de bedoeling: **zonder de bril
 * doet het harnas niets bijzonders**. Het is dan gewoon een goed pak metaal. Met de bril op
 * (en energie erin) haalt het het grootste deel van elke klap weg, kun je vliegen, land je
 * zonder schade en schiet je repulsorstoten. De bril levert de stroom; het harnas heeft geen
 * eigen batterij.
 *
 * Wat het harnas kan zit niet in dit item maar in {@link EdithSuit}: dat is één plek waar het
 * hele pak wordt bekeken, in plaats van drie items die los van elkaar iets doen.
 */
public class StarkSuitItem extends ArmorItem {
    public StarkSuitItem(Type type, Properties properties) {
        super(ModArmorMaterials.stark(), type, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starktech.suit").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.suit.needs_glasses")
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    /** Hoeveel stukken harnas deze speler draagt (0 tot 3). */
    public static int wornPieces(Player player) {
        int worn = 0;
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.getItem() instanceof StarkSuitItem) {
                worn++;
            }
        }
        return worn;
    }

    /** Draagt de speler het hele harnas (borst, broek en laarzen)? */
    public static boolean fullSet(Player player) {
        return wornPieces(player) >= 3;
    }

    /**
     * Doet het harnas op dit moment iets? Dat is zo als je minstens één stuk draagt én je
     * eigen bril op hebt met energie erin.
     */
    public static boolean isPowered(Player player) {
        if (wornPieces(player) == 0) {
            return false;
        }
        ItemStack glasses = EdithData.getWornGlasses(player);
        return !glasses.isEmpty() && EdithData.isAuthorized(glasses, player) && EdithData.getEnergy(glasses) > 0;
    }
}
