package com.senne.starktech.logic;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Een onderdeel voor een serverrack (jouw wens, 7 oktober: "hier kun je de RAM, CPU verbeteren
 * waardoor je redstone sneller reageert"). Er zijn er drie:
 * <ul>
 *   <li><b>CPU</b>: hoe sneller de server reageert als er iets gebeurt;</li>
 *   <li><b>RAM</b>: hoeveel diagrammen tegelijk bezig kunnen zijn (bijvoorbeeld allemaal aan het
 *       wachten met {@code wait(5)});</li>
 *   <li><b>SSD</b>: hoeveel blokjes je in totaal in je diagrammen kwijt kunt.</li>
 * </ul>
 * Je steekt ze in een rack (zie ServerRackBlockEntity); alle racks die elkaar raken tellen samen.
 */
public class ServerPartItem extends Item {
    /** Welk onderdeel het is. */
    public enum Kind {
        CPU, RAM, SSD
    }

    private final Kind kind;

    public ServerPartItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String key = "item.starktech.server_" + kind.name().toLowerCase(java.util.Locale.ROOT) + ".tooltip";
        Object value = switch (kind) {
            case CPU -> String.format(java.util.Locale.ROOT, "%.2f", LogicBalance.CPU_SPEEDUP_TICKS / 20.0);
            case RAM -> LogicBalance.RUNS_PER_RAM;
            case SSD -> LogicBalance.BLOCKS_PER_SSD;
        };
        tooltip.add(Component.translatable(key, value).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.starktech.server_part.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
