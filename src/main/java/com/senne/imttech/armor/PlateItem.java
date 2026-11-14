package com.senne.imttech.armor;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Een plate voor in de Plate Carrier (hoofdstuk 109): polymeer, staal, titanium of netherite (zie PlateTier). Jouw keuze:
 * hij <b>slijt per treffer</b>. Elke treffer die hij tegenhoudt kost hem wat, meer bij meer schade; is hij op, dan is hij
 * <b>gebarsten</b> en houdt hij niets meer tegen, tot je hem op een aambeeld herstelt met zijn eigen materiaal (een
 * staaf herstelt een kwart, zoals bij gereedschap) of een andere insteekt.
 *
 * Hoe het slijten werkt: elke plate heeft dezelfde balk van {@link #UNITS} stapjes (het gewone duurzaamheidsbalkje onder het
 * item), en hoeveel schade die balk waard is, staat per soort in de config ({@code plateDurability}): een titanium plate houdt
 * standaard 120 schade tegen voor hij barst. Zo kan de config het aanpassen zonder dat Minecraft de items anders moet kennen.
 *
 * Rechtsklik met een plate in je hand, terwijl je een vest draagt: hij gaat er meteen in (voorop als daar plek is of de
 * voorste gebarsten is, anders achterop); een gebarsten plate komt dan in je hand terug.
 */
public class PlateItem extends Item {
    /** Zoveel stapjes heeft de duurzaamheidsbalk van elke plate. */
    public static final int UNITS = 1000;

    private final PlateTier tier;

    public PlateItem(Properties properties, PlateTier tier) {
        super(properties.durability(UNITS));
        this.tier = tier;
    }

    public PlateTier tier() {
        return tier;
    }

    /** De soort van deze plate, of null als het geen plate is. */
    public static @Nullable PlateTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof PlateItem plate ? plate.tier : null;
    }

    /** Is hij gebarsten (helemaal versleten)? Dan houdt hij niets tegen. */
    public static boolean broken(ItemStack plate) {
        return plate.isDamageableItem() && plate.getDamageValue() >= plate.getMaxDamage();
    }

    /** Werkt deze plate nog: een plate, en niet gebarsten? */
    public static boolean works(ItemStack plate) {
        return tierOf(plate) != null && !broken(plate);
    }

    /** Zoveel schade kan hij nog tegenhouden (halve hartjes). */
    public static float left(ItemStack plate) {
        PlateTier tier = tierOf(plate);
        if (tier == null) {
            return 0;
        }
        float fraction = 1.0f - (float) plate.getDamageValue() / Math.max(1, plate.getMaxDamage());
        return Math.max(0, fraction * tier.durability());
    }

    /** Hoe heel hij nog is (0 = gebarsten, 1 = nieuw). */
    public static float health(ItemStack plate) {
        if (tierOf(plate) == null || plate.getMaxDamage() <= 0) {
            return 0;
        }
        return Mth.clamp(1.0f - (float) plate.getDamageValue() / plate.getMaxDamage(), 0, 1);
    }

    /**
     * Deze plate hield {@code absorbed} schade tegen: zoveel stapjes erbij op zijn balk (minstens een). Geeft de versleten
     * plate terug (een kopie); hij gaat nooit weg, gebarsten blijft hij in het vest zitten.
     */
    public static ItemStack wear(ItemStack plate, float absorbed) {
        PlateTier tier = tierOf(plate);
        ItemStack out = plate.copy();
        if (tier == null || absorbed <= 0) {
            return out;
        }
        int units = Math.max(1, (int) Math.ceil(absorbed * out.getMaxDamage() / tier.durability()));
        out.setDamageValue(Math.min(out.getMaxDamage(), out.getDamageValue() + units));
        return out;
    }

    /** Herstellen op een aambeeld: met het eigen materiaal (polymeer, een staal-, titanium- of netheritestaaf). */
    @Override
    public boolean isValidRepairItem(ItemStack plate, ItemStack material) {
        return material.is(tier.repairItem());
    }

    /** Rechtsklik terwijl je een vest draagt: de plate erin (zie Vests.insertPlate). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (Vests.worn(player).isEmpty()) {
            return InteractionResultHolder.pass(held);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Vests.insertPlate(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /** Het balkje onder het item blijft ook bij een gebarsten plate zichtbaar (rood en leeg). */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.isDamaged();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.plate.level", tier.level).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.plate.stops", Math.round(tier.reduction() * 100))
                .withStyle(ChatFormatting.GRAY));
        if (broken(stack)) {
            tooltip.add(Component.translatable("tooltip.imttech.plate.cracked").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable("tooltip.imttech.plate.left", Math.round(left(stack)), Math.round(tier.durability()))
                    .withStyle(health(stack) < 0.25f ? ChatFormatting.GOLD : ChatFormatting.DARK_GREEN));
        }
        tooltip.add(Component.translatable("tooltip.imttech.plate.repair", tier.repairItem().getDescription())
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.plate.insert").withStyle(ChatFormatting.DARK_GRAY));
    }
}
