package com.senne.imttech.armor;

import com.senne.imttech.backpack.BackpackRegistry;
import com.senne.imttech.guns.MagazineItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

/**
 * De Plate Carrier (hoofdstuk 109, jouw wens: "body armor waar je plates in kunt steken"): een tactisch vest met een plate
 * voor en een plate achter, drie magazijnvakjes op je borst en een eigen tekst op je rug.
 * <ul>
 *   <li><b>Waar</b> (jouw keuze): in een eigen vest-vakje, <b>over</b> je borstplaat of de Tech Suit (met Curios het slot
 *       "body"). De bescherming van de plates komt bovenop die van je borstplaat.</li>
 *   <li><b>Rechtsklik</b> met het vest in je hand: het scherm met de twee plates, de drie magazijnen en de tekst. Gedragen open
 *       je het uit het wiel (linker Alt). <b>Sneak + rechtsklik</b>: aantrekken (een vest dat je al droeg komt in je hand).</li>
 *   <li><b>Kleur</b> (jouw keuze: zoals de rugzakken): verven met de 16 kleurstoffen, gemengd zoals leer, of camo (bruin +
 *       lichtgroen + groen). Nieuw is hij antracietgrijs, met een zwart klittenbandvlak op de rug voor de tekst.</li>
 * </ul>
 */
public class VestItem extends Item {
    /** De kleur van een nieuw vest: antracietgrijs (de tekst op het zwarte vlak op de rug blijft zo goed leesbaar). */
    public static final int CHARCOAL = 0x3A3D42;

    public VestItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static boolean isVest(ItemStack stack) {
        return stack.getItem() instanceof VestItem;
    }

    /** Wat erin zit (nooit null). */
    public static VestData data(ItemStack vest) {
        VestData data = vest.get(ArmorRegistry.VEST.get());
        return data == null ? VestData.EMPTY : data;
    }

    public static void setData(ItemStack vest, VestData data) {
        if (!isVest(vest)) {
            return;
        }
        if (data.isEmpty()) {
            vest.remove(ArmorRegistry.VEST.get());
        } else {
            vest.set(ArmorRegistry.VEST.get(), data);
        }
    }

    /** Hetzelfde vest (een kopie) met deze inhoud. */
    public static ItemStack with(ItemStack vest, VestData data) {
        ItemStack out = vest.copy();
        setData(out, data);
        return out;
    }

    /** De kleur van de stof (zonder camo): wat je erop verfde, of antracietgrijs. */
    public static int colour(ItemStack vest) {
        DyedItemColor dyed = vest.get(DataComponents.DYED_COLOR);
        return dyed == null ? CHARCOAL : dyed.rgb();
    }

    /** Camo (dezelfde camo als de rugzakken: het data component imttech:camo)? */
    public static boolean camo(ItemStack vest) {
        return Boolean.TRUE.equals(vest.get(BackpackRegistry.CAMO.get()));
    }

    /** Rechtsklik: het scherm van het vest openen; met sneak: aantrekken. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                Vests.putOn(serverPlayer, hand);
            } else {
                Vests.openHeld(serverPlayer, hand);
            }
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        VestData data = data(stack);
        tooltip.add(plateLine("front", data.front()));
        tooltip.add(plateLine("back", data.back()));
        tooltip.add(Component.translatable("tooltip.imttech.vest.mags", data.filledMagazines(), VestData.MAGS)
                .withStyle(ChatFormatting.GRAY));
        for (ItemStack mag : data.mags()) {
            if (!mag.isEmpty()) {
                Component line = mag.getHoverName().copy();
                if (MagazineItem.typeOf(mag) != null) {
                    line = line.copy().append(" (" + MagazineItem.rounds(mag) + ")");
                } else if (mag.getItem() instanceof com.senne.imttech.weapons.EnergyCellItem) {
                    line = line.copy().append(" (" + Math.round(com.senne.imttech.weapons.EnergyCellItem.charge(mag)) + "%)");
                }
                tooltip.add(Component.literal("  ").append(line).withStyle(ChatFormatting.GOLD));
            }
        }
        if (!data.text().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.imttech.vest.text", Component.literal(data.text())
                    .withStyle(style -> style.withColor(data.dye().getTextColor()))).withStyle(ChatFormatting.GRAY));
        }
        if (camo(stack)) {
            tooltip.add(Component.translatable("tooltip.imttech.backpack.camo").withStyle(ChatFormatting.DARK_GREEN));
        }
        tooltip.add(Component.translatable("tooltip.imttech.vest.wear").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.vest.open").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.vest.workbench").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** "Front: Titanium Plate (90/120)", "Back: none" of "Back: Steel Plate (cracked)". */
    private static Component plateLine(String side, ItemStack plate) {
        Component what;
        ChatFormatting colour;
        if (PlateItem.tierOf(plate) == null) {
            what = Component.translatable("tooltip.imttech.vest.no_plate");
            colour = ChatFormatting.DARK_GRAY;
        } else if (PlateItem.broken(plate)) {
            what = plate.getHoverName().copy().append(" ").append(Component.translatable("tooltip.imttech.vest.cracked"));
            colour = ChatFormatting.RED;
        } else {
            PlateTier tier = PlateItem.tierOf(plate);
            what = plate.getHoverName().copy().append(" (" + Math.round(PlateItem.left(plate)) + "/" + Math.round(tier.durability()) + ")");
            colour = PlateItem.health(plate) < 0.25f ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        }
        return Component.translatable("tooltip.imttech.vest." + side, what).withStyle(colour);
    }

    /** Een nieuw vest met deze tekst (voor de tests, de showcase en de Raven-soldaten). */
    public static ItemStack withText(ItemStack vest, String text, DyeColor colour) {
        return with(vest, data(vest).withText(text, colour.getId()));
    }
}
