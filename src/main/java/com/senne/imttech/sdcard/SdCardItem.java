package com.senne.imttech.sdcard;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * De SD Card (hoofdstuk 130, jouw wens: "zorg dat je programs ook op een sd card kunt zetten, deze kun je in je security
 * core zetten of op een desktop aansluiten, zo kun je makklijk securiy files/programs doorgeven aan ander bv je hebt een
 * goeie minerobot gemaakt en je wilt dat doorgeven/verkopen aan iemand").
 * <ul>
 *   <li>Er zijn drie maten (jouw keuze): 8 GB, 32 GB en 128 GB. Hoe groter, hoe meer programma's en bestanden erop passen
 *       (zie {@code [sdCards]} in de config) en hoe duurder het recept.</li>
 *   <li>Rechtsklik ermee op een Security Core of een Computer (de kast): hij steekt erin, en je ziet hem zitten. Sneak met
 *       een lege hand haalt hem eruit (zie {@link SdCardSlots}).</li>
 *   <li>Zit hij erin, dan staat hij op de desktop in de app Logic (een lijstje "SD card" bij je programma's) en in Files
 *       (de map sd). Daar kopieer je heen en weer, en zet je een programma eventueel <b>op slot</b>: wie het krijgt, kan het
 *       gebruiken maar nooit de code zien (zie SdDesktop).</li>
 * </ul>
 * Op het item staat alleen het nummer van de kaart en een samenvatting ({@link SdCardData}); wat erop staat, staat op de
 * server ({@link SdCards}).
 */
public class SdCardItem extends Item {
    /** De maat: 0 = 8 GB, 1 = 32 GB, 2 = 128 GB (de plek in de rijtjes van {@code [sdCards]}). */
    private final int size;

    public SdCardItem(int size, Properties properties) {
        super(properties);
        this.size = size;
    }

    /** Hoeveel gigabyte er op deze maat staat (8, 32 of 128). */
    public int gigabytes() {
        return switch (size) {
            case 0 -> 8;
            case 1 -> 32;
            default -> 128;
        };
    }

    /** Zoveel programma's passen erop. */
    public int maxPrograms() {
        return Math.max(1, Math.round(pick(ImtConfig.SdCards.PROGRAMS)));
    }

    /** Zoveel bestanden passen erop (en evenveel mappen). */
    public int maxFiles() {
        return Math.max(1, Math.round(pick(ImtConfig.SdCards.FILES)));
    }

    private float pick(float[] list) {
        return list.length == 0 ? 1 : list[Math.min(size, list.length - 1)];
    }

    /** Is dit een SD-kaart? */
    public static boolean isCard(ItemStack stack) {
        return stack.getItem() instanceof SdCardItem;
    }

    /** Het nummer en de samenvatting van deze kaart, of null (een lege kaart waar nog nooit iets op stond). */
    public static @Nullable SdCardData data(ItemStack stack) {
        return stack.get(ModDataComponents.SD_CARD.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        SdCardData data = data(stack);
        if (data == null || data.programs() == 0 && data.files() == 0) {
            tooltip.add(Component.translatable("tooltip.imttech.sd_card.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.imttech.sd_card.contents", data.programs(), data.files())
                    .withStyle(ChatFormatting.AQUA));
            if (data.locked() > 0) {
                tooltip.add(Component.translatable("tooltip.imttech.sd_card.locked", data.locked())
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        tooltip.add(Component.translatable("tooltip.imttech.sd_card.holds", maxPrograms(), maxFiles())
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.sd_card.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
