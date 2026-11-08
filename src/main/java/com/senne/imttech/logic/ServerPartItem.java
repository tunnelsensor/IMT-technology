package com.senne.imttech.logic;

import com.senne.imttech.config.ImtConfig;
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
 *   <li><b>CPU</b>: rekenkracht, 1 voor Logic (hoe snel je diagrammen reageren: meteen vanaf 30) en 1 voor het brein
 *       van I.M.T (zonder vertraging vanaf 120);</li>
 *   <li><b>GPU</b>: rekenkracht, maar anders verdeeld: half zo goed voor Logic, dubbel zo goed
 *       voor het brein (zie ImtConfig.Logic.GPU_...);</li>
 *   <li><b>RAM</b>: 16 GB; elke 10 CPU's of GPU's hebben er 64 GB van nodig om vlot te draaien (hoofdstuk 64);</li>
 *   <li><b>SSD</b>: 1 TB; elke 10 CPU's of GPU's hebben er 3 TB van nodig, en je diagrammen staan erop (1 MB per blokje);</li>
 *   <li><b>Fan</b>: rekent niets, maar koelt het rack waar hij in zit.</li>
 * </ul>
 * Je klikt ze in de vakjes op de voorkant van een rack (zie ServerRackBlock en RackBay): bovenaan een CPU of GPU, dan RAM
 * of een SSD, onderaan fans. Alle racks van een server tellen samen.
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
        String key = "item.imttech.server_" + kind.name().toLowerCase(java.util.Locale.ROOT) + ".tooltip";
        Component text = switch (kind) {
            case CPU, GPU -> Component.translatable(key, number(LogicStats.logicPower(kind)),
                    number(ImtConfig.Logic.LOGIC_INSTANT_POWER), number(LogicStats.brainPower(kind)),
                    number(ImtConfig.Logic.BRAIN_INSTANT_POWER));
            case RAM -> Component.translatable(key, LogicStats.bytes(LogicStats.ramStickMb()),
                    LogicStats.bytes(LogicStats.ramNeededMb(10)));
            case SSD -> Component.translatable(key, LogicStats.bytes(LogicStats.ssdMb()),
                    LogicStats.bytes(LogicStats.ssdNeededMb(10)), LogicStats.bytes(LogicStats.blocksMb(1)));
            case FAN -> Component.translatable(key);
        };
        tooltip.add(text.copy().withStyle(ChatFormatting.GRAY));
        // De warmte (8 oktober): wat dit onderdeel aan warmte maakt, of hoeveel een fan wegkoelt.
        Component heat = switch (kind) {
            case CPU -> Component.translatable("item.imttech.server_part.heat_busy", number(ImtConfig.Logic.CPU_HEAT),
                    number(ImtConfig.Logic.CPU_HEAT + ImtConfig.Logic.CPU_BUSY_HEAT));
            case GPU -> Component.translatable("item.imttech.server_part.heat_busy", number(ImtConfig.Logic.GPU_HEAT),
                    number(ImtConfig.Logic.GPU_HEAT + ImtConfig.Logic.GPU_BUSY_HEAT));
            case RAM, SSD -> Component.translatable("item.imttech.server_part.heat", number(ImtConfig.Logic.MEMORY_HEAT));
            case FAN -> Component.translatable("item.imttech.server_part.cooling", number(ImtConfig.Logic.FAN_COOLING));
        };
        tooltip.add(heat.copy().withStyle(kind == ServerPartItem.Kind.FAN ? ChatFormatting.AQUA : ChatFormatting.GOLD));
        int wear = wear(stack);
        if (wear > 0) {
            tooltip.add(Component.translatable("item.imttech.server_part.worn", Math.min(100, wear * 100 / Math.max(1, Math.round(ImtConfig.Logic.MAX_WEAR))))
                    .withStyle(ChatFormatting.RED));
        }
        // In welke rij van het rack hij hoort (hoofdstuk 64).
        String row = kind.isProcessor() ? "top" : kind == Kind.FAN ? "bottom" : "middle";
        tooltip.add(Component.translatable("item.imttech.server_part.tooltip." + row).withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---------------------------------------------------------------- Slijten (8 oktober)

    /** Hoe versleten dit onderdeel is (0 = nieuw). Een fan slijt niet. */
    public static int wear(ItemStack stack) {
        return stack.getOrDefault(com.senne.imttech.registry.ModDataComponents.WEAR.get(), 0);
    }

    public static void setWear(ItemStack stack, int wear) {
        stack.set(com.senne.imttech.registry.ModDataComponents.WEAR.get(), Math.max(0, wear));
    }

    /** Een balkje onder het icoon, zoals bij gereedschap, zodra het onderdeel slijt. */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return wear(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int left = Math.max(0, Math.round(ImtConfig.Logic.MAX_WEAR) - wear(stack));
        return Math.round(13.0f * left / Math.max(1, Math.round(ImtConfig.Logic.MAX_WEAR)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float left = Math.max(0, Math.round(ImtConfig.Logic.MAX_WEAR) - wear(stack)) / (float) Math.max(1, Math.round(ImtConfig.Logic.MAX_WEAR));
        return net.minecraft.util.Mth.hsvToRgb(left / 3.0f, 1.0f, 1.0f); // groen > geel > rood
    }

    /** 1 blijft "1", 0.5 wordt "0.5" (geen ".0" achter hele getallen). */
    private static String number(double value) {
        double rounded = Math.round(value * 100) / 100.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }
}
