package com.senne.starktech.logic;

import com.senne.starktech.config.StarkConfig;
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
 *       voor het brein (zie StarkConfig.Logic.GPU_...);</li>
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
            case CPU, GPU -> Component.translatable(key, number(LogicStats.logicPower(kind)), number(LogicStats.brainPower(kind)));
            case RAM -> Component.translatable(key, Math.round(StarkConfig.Logic.RUNS_PER_RAM));
            case SSD -> Component.translatable(key, Math.round(StarkConfig.Logic.BLOCKS_PER_SSD));
            case FAN -> Component.translatable(key);
        };
        tooltip.add(text.copy().withStyle(ChatFormatting.GRAY));
        // De warmte (8 oktober): wat dit onderdeel aan warmte maakt, of hoeveel een fan wegkoelt.
        Component heat = switch (kind) {
            case CPU -> Component.translatable("item.starktech.server_part.heat_busy", number(StarkConfig.Logic.CPU_HEAT),
                    number(StarkConfig.Logic.CPU_HEAT + StarkConfig.Logic.CPU_BUSY_HEAT));
            case GPU -> Component.translatable("item.starktech.server_part.heat_busy", number(StarkConfig.Logic.GPU_HEAT),
                    number(StarkConfig.Logic.GPU_HEAT + StarkConfig.Logic.GPU_BUSY_HEAT));
            case RAM, SSD -> Component.translatable("item.starktech.server_part.heat", number(StarkConfig.Logic.MEMORY_HEAT));
            case FAN -> Component.translatable("item.starktech.server_part.cooling", number(StarkConfig.Logic.FAN_COOLING));
        };
        tooltip.add(heat.copy().withStyle(kind == ServerPartItem.Kind.FAN ? ChatFormatting.AQUA : ChatFormatting.GOLD));
        int wear = wear(stack);
        if (wear > 0) {
            tooltip.add(Component.translatable("item.starktech.server_part.worn", Math.min(100, wear * 100 / Math.max(1, Math.round(StarkConfig.Logic.MAX_WEAR))))
                    .withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.translatable("item.starktech.server_part.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---------------------------------------------------------------- Slijten (8 oktober)

    /** Hoe versleten dit onderdeel is (0 = nieuw). Een fan slijt niet. */
    public static int wear(ItemStack stack) {
        return stack.getOrDefault(com.senne.starktech.registry.ModDataComponents.WEAR.get(), 0);
    }

    public static void setWear(ItemStack stack, int wear) {
        stack.set(com.senne.starktech.registry.ModDataComponents.WEAR.get(), Math.max(0, wear));
    }

    /** Een balkje onder het icoon, zoals bij gereedschap, zodra het onderdeel slijt. */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return wear(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int left = Math.max(0, Math.round(StarkConfig.Logic.MAX_WEAR) - wear(stack));
        return Math.round(13.0f * left / Math.max(1, Math.round(StarkConfig.Logic.MAX_WEAR)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float left = Math.max(0, Math.round(StarkConfig.Logic.MAX_WEAR) - wear(stack)) / (float) Math.max(1, Math.round(StarkConfig.Logic.MAX_WEAR));
        return net.minecraft.util.Mth.hsvToRgb(left / 3.0f, 1.0f, 1.0f); // groen > geel > rood
    }

    /** 1 blijft "1", 0.5 wordt "0.5" (geen ".0" achter hele getallen). */
    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
