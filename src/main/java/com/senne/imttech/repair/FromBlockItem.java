package com.senne.imttech.repair;

import com.senne.imttech.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Een voorwerp dat onthoudt van welk blok het komt (hoofdstuk 11, jouw keuze: "per soort blok"):
 * het <b>gebarsten blok</b> dat je overhoudt als je een blok met barsten weghakt, en het <b>poeder</b>
 * dat de Crusher daarvan maakt. Stenen bakstenen blijven zo stenen bakstenen, obsidiaan blijft obsidiaan.
 *
 * Het blok staat in de data component {@code imttech:from_block}; de naam wordt er vanzelf bij
 * gemaakt ("Cracked Stone Bricks", "Obsidian Powder").
 */
public class FromBlockItem extends Item {
    /** De naam met het blok erin, bijvoorbeeld "Cracked %s". */
    private final String nameKey;
    /** Een regel uitleg in de tooltip: wat je ermee doet. */
    private final String hintKey;

    public FromBlockItem(Properties properties, String nameKey, String hintKey) {
        super(properties);
        this.nameKey = nameKey;
        this.hintKey = hintKey;
    }

    /** {@code count} keer dit voorwerp, van {@code block}. */
    public static ItemStack of(Item item, Block block, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(ModDataComponents.FROM_BLOCK.get(), block);
        return stack;
    }

    /** Van welk blok dit voorwerp komt (of null als het nergens van komt, bijvoorbeeld uit /give). */
    public static @Nullable Block block(ItemStack stack) {
        return stack.get(ModDataComponents.FROM_BLOCK.get());
    }

    /**
     * De kleur van het blok (zoals op een kaart), voor het poeder: zo is obsidiaanpoeder bijna zwart en
     * bakstenenpoeder rood. Erg donker maken we wat lichter, anders zie je het niet in een donker vakje.
     */
    public static int color(ItemStack stack) {
        Block block = block(stack);
        int rgb = block == null ? 0 : block.defaultMapColor().col;
        if (rgb == 0) {
            rgb = 0x9A9A9A;
        }
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        if (r * 3 + g * 6 + b < 600) {
            r = (r + 0x80) / 2;
            g = (g + 0x80) / 2;
            b = (b + 0x80) / 2;
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override
    public Component getName(ItemStack stack) {
        Block block = block(stack);
        return block == null ? super.getName(stack) : Component.translatable(nameKey, block.getName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(hintKey).withStyle(ChatFormatting.GRAY));
    }
}
