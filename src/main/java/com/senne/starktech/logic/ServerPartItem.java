package com.senne.starktech.logic;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Een onderdeel voor een serverrack (jouw wens, 7 oktober: "hier kun je de RAM, CPU verbeteren
 * waardoor je redstone sneller reageert"; 8 oktober kwamen de GPU en de fan erbij). Er zijn er vijf:
 * <ul>
 *   <li><b>CPU</b>: rekenkracht, 1 voor Logic (hoe snel de server reageert) en 1 voor het brein
 *       van E.D.I.T.H.;</li>
 *   <li><b>GPU</b>: rekenkracht, maar anders verdeeld: half zo goed voor Logic, dubbel zo goed
 *       voor het brein (zie LogicBalance.GPU_...);</li>
 *   <li><b>RAM</b>: hoeveel diagrammen tegelijk bezig kunnen zijn (bijvoorbeeld allemaal aan het
 *       wachten met {@code wait(5)});</li>
 *   <li><b>SSD</b>: hoeveel blokjes je in totaal in je diagrammen kwijt kunt;</li>
 *   <li><b>Fan</b>: rekent niets, maar koelt het datacenter.</li>
 * </ul>
 * Je steekt ze in een rack (zie ServerRackBlockEntity); alle racks die elkaar raken tellen samen.
 */
public class ServerPartItem extends Item {
    /** Welk onderdeel het is. */
    public enum Kind {
        CPU, GPU, RAM, SSD, FAN;

        /** Rekent dit onderdeel (een CPU of GPU)? */
        public boolean isProcessor() {
            return this == CPU || this == GPU;
        }
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
        Component text = switch (kind) {
            case CPU, GPU -> Component.translatable(key, number(LogicBalance.logicPower(kind)), number(LogicBalance.brainPower(kind)));
            case RAM -> Component.translatable(key, LogicBalance.RUNS_PER_RAM);
            case SSD -> Component.translatable(key, LogicBalance.BLOCKS_PER_SSD);
            case FAN -> Component.translatable(key);
        };
        tooltip.add(text.copy().withStyle(ChatFormatting.GRAY));
        // De warmte (8 oktober): wat dit onderdeel aan warmte maakt, of hoeveel een fan wegkoelt.
        Component heat = switch (kind) {
            case CPU -> Component.translatable("item.starktech.server_part.heat_busy", number(LogicBalance.CPU_HEAT),
                    number(LogicBalance.CPU_HEAT + LogicBalance.CPU_BUSY_HEAT));
            case GPU -> Component.translatable("item.starktech.server_part.heat_busy", number(LogicBalance.GPU_HEAT),
                    number(LogicBalance.GPU_HEAT + LogicBalance.GPU_BUSY_HEAT));
            case RAM, SSD -> Component.translatable("item.starktech.server_part.heat", number(LogicBalance.MEMORY_HEAT));
            case FAN -> Component.translatable("item.starktech.server_part.cooling", number(LogicBalance.FAN_COOLING));
        };
        tooltip.add(heat.copy().withStyle(kind == ServerPartItem.Kind.FAN ? ChatFormatting.AQUA : ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.starktech.server_part.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 1 blijft "1", 0.5 wordt "0.5" (geen ".0" achter hele getallen). */
    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
