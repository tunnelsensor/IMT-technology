package com.senne.imttech.logic;

import com.senne.imttech.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Een keycard (jouw wens, 7 oktober). Je craft hem leeg; op de desktop van je beveiligingskern (de
 * app Keycards) schrijf je hem: een naam, een niveau van 1 tot 5, en hij krijgt een eigen nummer
 * (zie KeycardData). Daarna houd je hem tegen een keycardlezer.
 *
 * De kleur van de kaart hoort bij zijn niveau (wit, groen, blauw, paars, goud). Dat doen we met
 * "custom model data": een getal op het item dat het model kiest (zie models/item/keycard.json).
 */
public class KeycardItem extends Item {
    public KeycardItem(Properties properties) {
        super(properties);
    }

    /** Wat er op deze kaart staat, of null als hij nog leeg is. */
    public static @Nullable KeycardData data(ItemStack stack) {
        return stack.get(ModDataComponents.KEYCARD.get());
    }

    /** Is dit een lege keycard (nog niet geschreven)? */
    public static boolean isBlank(ItemStack stack) {
        return stack.getItem() instanceof KeycardItem && data(stack) == null;
    }

    /** Schrijft de kaart: de gegevens erop, en de kleur van zijn niveau. */
    public static void write(ItemStack stack, KeycardData data) {
        stack.set(ModDataComponents.KEYCARD.get(), data);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(data.level()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        KeycardData data = data(stack);
        if (data == null) {
            tooltip.add(Component.translatable("item.imttech.keycard.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!data.label().isEmpty()) {
            tooltip.add(Component.literal(data.label()).withStyle(ChatFormatting.WHITE));
        }
        tooltip.add(Component.translatable("item.imttech.keycard.level", data.level(), data.number())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.imttech.keycard.network", data.host()).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Een beschreven kaart toont zijn naam (in plaats van "Keycard"). */
    @Override
    public Component getName(ItemStack stack) {
        KeycardData data = data(stack);
        if (data != null && !data.label().isEmpty()) {
            return Component.translatable("item.imttech.keycard.named", data.label());
        }
        return super.getName(stack);
    }
}
