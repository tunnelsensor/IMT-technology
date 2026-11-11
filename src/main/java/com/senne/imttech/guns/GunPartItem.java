package com.senne.imttech.guns;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Een los onderdeel van een pistool (hoofdstuk 52): de slede, de loop, de veren, de slagpin, de trekker of het frame.
 * Eén item per onderdeel; waar het van gemaakt is, staat in het data component GUN_MATERIAL (een "Steel Slide" en een
 * "Titanium Slide" zijn hetzelfde item met een ander materiaal; het plaatje wisselt mee, zie
 * client/guns/FirearmModels). Een onderdeel dat uit een gebruikt pistool komt, houdt zijn conditie (PART_CONDITION);
 * een nieuw onderdeel heeft dat component niet en is 100.
 *
 * Op de Gunsmith Table sleep je ze in het pistool (zie GunsmithTableBlockEntity), en sinds hoofdstuk 78 maak je ze daar
 * ook (het tabblad "Make parts", zie GunsmithRecipe): in de crafting table kan het niet meer.
 */
public class GunPartItem extends Item {
    private final GunPart part;

    public GunPartItem(Properties properties, GunPart part) {
        super(properties.stacksTo(16));
        this.part = part;
    }

    public GunPart part() {
        return part;
    }

    /** Welk onderdeel dit item is, of null als het geen onderdeel is. */
    public static GunPart partOf(ItemStack stack) {
        return stack.getItem() instanceof GunPartItem item ? item.part : null;
    }

    /** Van welk materiaal (staal als het er niet bij staat). */
    public static GunMaterial materialOf(ItemStack stack) {
        GunMaterial material = stack.get(ModDataComponents.GUN_MATERIAL.get());
        return material == null ? GunMaterial.STEEL : material;
    }

    /** Hoe heel (100 = nieuw). */
    public static int conditionOf(ItemStack stack) {
        Integer condition = stack.get(ModDataComponents.PART_CONDITION.get());
        return condition == null ? PartState.NEW : Math.max(0, Math.min(PartState.NEW, condition));
    }

    /** Een onderdeel van dit materiaal, zo heel als {@code condition}. */
    public static ItemStack of(GunPart part, GunMaterial material, int condition) {
        ItemStack stack = new ItemStack(ModItems.gunPart(part));
        stack.set(ModDataComponents.GUN_MATERIAL.get(), material);
        if (condition < PartState.NEW) {
            stack.set(ModDataComponents.PART_CONDITION.get(), Math.max(0, condition));
        }
        return stack;
    }

    public static ItemStack of(GunPart part, GunMaterial material) {
        return of(part, material, PartState.NEW);
    }

    /** Het onderdeel zoals het in een pistool komt te zitten. */
    public static PartState stateOf(ItemStack stack) {
        return new PartState(materialOf(stack), conditionOf(stack));
    }

    /** "Steel Slide", "Polymer Frame"... */
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.imttech.gun_part", materialOf(stack).text(), part.text());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return conditionOf(stack) < PartState.NEW;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13 * conditionOf(stack) / (float) PartState.NEW);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int condition = conditionOf(stack);
        return condition <= 0 ? 0xFF3030 : condition < ImtConfig.Guns.WORN_BELOW ? 0xFFA93F : 0x5CFF9D;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GunMaterial material = materialOf(stack);
        tooltip.add(Component.translatable("tooltip.imttech.gun_part." + part.id).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.gun_part.material", material.text(),
                String.format(java.util.Locale.ROOT, "%.1f", material.wearChance())).withStyle(ChatFormatting.DARK_AQUA));
        int condition = conditionOf(stack);
        if (condition <= 0) {
            tooltip.add(Component.translatable("tooltip.imttech.gun_part.broken").withStyle(ChatFormatting.RED));
        } else if (condition < PartState.NEW) {
            tooltip.add(Component.translatable("tooltip.imttech.gun_part.condition", condition)
                    .withStyle(condition < ImtConfig.Guns.WORN_BELOW ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable("tooltip.imttech.gun_part.table").withStyle(ChatFormatting.DARK_GRAY));
        // Hoofdstuk 78: niet meer in de crafting table, alleen nog op de Gunsmith Table.
        tooltip.add(Component.translatable("tooltip.imttech.gunsmith.made").withStyle(ChatFormatting.DARK_GRAY));
    }
}
