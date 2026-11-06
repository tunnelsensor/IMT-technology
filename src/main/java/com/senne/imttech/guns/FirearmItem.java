package com.senne.imttech.guns;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModItems;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Een vuurwapen (hoofdstuk 52): de Glock 19. Jouw wens: "supper realistisch". Wat het pistool onthoudt (data
 * components):
 * <ul>
 *   <li>GUN_PARTS: welke onderdelen erin zitten en hoe heel ze zijn (zie {@link GunParts});</li>
 *   <li>GUN_MAGAZINE: het magazijn dat erin zit (met zijn patronen), of geen;</li>
 *   <li>GUN_CHAMBERED: zit er een patroon in de kamer? (jouw keuze: 15 + 1);</li>
 *   <li>GUN_JAM: een storing (0 = geen, 1 = een huls die klem zit, 2 = een ketser): druk R voor een tap-rack.</li>
 * </ul>
 * Schieten, herladen, storingen en slijtage regelt {@link Firearms} op de server; wat je ziet, client/guns.
 */
public class FirearmItem extends Item {
    /** Geen storing, een huls die klem zit (failure to eject), of een patroon dat niet afging (misfire). */
    public static final int JAM_NONE = 0;
    public static final int JAM_STOVEPIPE = 1;
    public static final int JAM_MISFIRE = 2;

    private final FirearmType type;

    public FirearmItem(Properties properties, FirearmType type) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    public FirearmType type() {
        return type;
    }

    /** Het pistool in deze hand, of null. */
    public static @Nullable FirearmType typeOf(ItemStack stack) {
        return stack.getItem() instanceof FirearmItem gun ? gun.type : null;
    }

    // ================================================================ De onderdelen

    public static GunParts parts(ItemStack stack) {
        GunParts parts = stack.get(ModDataComponents.GUN_PARTS.get());
        return parts == null ? GunParts.EMPTY : parts;
    }

    public static void setParts(ItemStack stack, GunParts parts) {
        if (parts.parts().isEmpty()) {
            stack.remove(ModDataComponents.GUN_PARTS.get());
        } else {
            stack.set(ModDataComponents.GUN_PARTS.get(), parts);
        }
    }

    /** Zitten alle onderdelen erin en is er geen kapot? */
    public static boolean works(ItemStack stack) {
        GunParts parts = parts(stack);
        return parts.complete() && parts.broken().isEmpty();
    }

    // ================================================================ Het magazijn en de kamer

    /** Het magazijn in het pistool als item (een nieuw magazijn met zoveel patronen), of leeg als er geen in zit. */
    public static ItemStack magazine(ItemStack stack) {
        GunMagazine magazine = stack.get(ModDataComponents.GUN_MAGAZINE.get());
        FirearmType type = typeOf(stack);
        return magazine == null || type == null ? ItemStack.EMPTY : MagazineItem.of(type, magazine.rounds());
    }

    public static boolean hasMagazine(ItemStack stack) {
        return stack.has(ModDataComponents.GUN_MAGAZINE.get());
    }

    /** Dit magazijn (een item) in het pistool stoppen, of het eruit halen (een leeg item). */
    public static void setMagazine(ItemStack stack, ItemStack magazine) {
        if (magazine.isEmpty()) {
            stack.remove(ModDataComponents.GUN_MAGAZINE.get());
        } else {
            stack.set(ModDataComponents.GUN_MAGAZINE.get(), new GunMagazine(MagazineItem.rounds(magazine)));
        }
    }

    /** Zoveel patronen zitten er in het magazijn in het pistool. */
    public static int roundsInMagazine(ItemStack stack) {
        GunMagazine magazine = stack.get(ModDataComponents.GUN_MAGAZINE.get());
        return magazine == null ? 0 : Math.max(0, magazine.rounds());
    }

    /** Eén patroon uit het magazijn in het pistool halen (geeft false als het leeg is). */
    public static boolean takeRoundFromMagazine(ItemStack stack) {
        int rounds = roundsInMagazine(stack);
        if (rounds <= 0 || !hasMagazine(stack)) {
            return false;
        }
        stack.set(ModDataComponents.GUN_MAGAZINE.get(), new GunMagazine(rounds - 1));
        return true;
    }

    public static boolean chambered(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.GUN_CHAMBERED.get()));
    }

    public static void setChambered(ItemStack stack, boolean chambered) {
        if (chambered) {
            stack.set(ModDataComponents.GUN_CHAMBERED.get(), true);
        } else {
            stack.remove(ModDataComponents.GUN_CHAMBERED.get());
        }
    }

    /** Alle patronen bij elkaar: in het magazijn plus die in de kamer. */
    public static int totalRounds(ItemStack stack) {
        return roundsInMagazine(stack) + (chambered(stack) ? 1 : 0);
    }

    /**
     * Staat de slede naar achteren? Bij een echte Glock blijft de slede open staan na het laatste schot als er een
     * (leeg) magazijn in zit; zonder magazijn valt hij gewoon dicht.
     */
    public static boolean slideLocked(ItemStack stack) {
        return !chambered(stack) && hasMagazine(stack) && roundsInMagazine(stack) == 0 && jam(stack) == JAM_NONE;
    }

    // ================================================================ Storingen

    public static int jam(ItemStack stack) {
        Integer jam = stack.get(ModDataComponents.GUN_JAM.get());
        return jam == null ? JAM_NONE : jam;
    }

    public static void setJam(ItemStack stack, int jam) {
        if (jam == JAM_NONE) {
            stack.remove(ModDataComponents.GUN_JAM.get());
        } else {
            stack.set(ModDataComponents.GUN_JAM.get(), jam);
        }
    }

    // ================================================================ Een nieuw pistool

    /**
     * Een compleet pistool met een vol magazijn en een patroon in de kamer (voor creative en tests). Met staal krijg je
     * een echte Glock (polymeer frame en trekker, de rest staal); met een ander materiaal alles daarvan (waar dat mag).
     */
    public static ItemStack loaded(FirearmType type, GunMaterial material) {
        ItemStack stack = new ItemStack(ModItems.firearm(type));
        setParts(stack, material == GunMaterial.STEEL ? GunParts.classic() : GunParts.complete(material));
        setMagazine(stack, MagazineItem.of(type, type.capacity()));
        setChambered(stack, true);
        return stack;
    }

    /** Een pistool met alleen deze onderdelen, zonder magazijn (zo ontstaat het op de Gunsmith Table). */
    public static ItemStack withParts(FirearmType type, GunParts parts) {
        ItemStack stack = new ItemStack(ModItems.firearm(type));
        setParts(stack, parts);
        return stack;
    }

    // ================================================================ Geen gewoon gereedschap

    /** Met een pistool in je hand breek je geen blokken (linkerklik is schieten). */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, net.minecraft.world.InteractionHand hand) {
        return true;
    }

    /** Een schot (de kamer en het magazijn veranderen) laat het pistool niet opnieuw in je hand komen. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    // ================================================================ Het streepje en de uitleg

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return hasMagazine(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13 * roundsInMagazine(stack) / (float) Math.max(1, type.capacity()));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return totalRounds(stack) == 0 ? 0xFF3030 : 0xD6AC58;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GunParts parts = parts(stack);
        tooltip.add(Component.translatable("tooltip.imttech." + type.id).withStyle(ChatFormatting.GRAY));
        if (!parts.complete()) {
            tooltip.add(Component.translatable("tooltip.imttech.firearm.incomplete", parts.missing().size())
                    .withStyle(ChatFormatting.RED));
        }
        for (Map.Entry<GunPart, PartState> entry : parts.parts().entrySet()) {
            PartState state = entry.getValue();
            ChatFormatting colour = state.broken() ? ChatFormatting.RED : state.worn() ? ChatFormatting.GOLD : ChatFormatting.DARK_AQUA;
            tooltip.add(Component.literal("  ").append(Component.translatable("tooltip.imttech.firearm.part",
                    state.material().text(), entry.getKey().text(), state.condition())).withStyle(colour));
        }
        if (parts.complete()) {
            ItemStack magazine = magazine(stack);
            if (magazine.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.imttech.firearm.no_magazine").withStyle(ChatFormatting.RED));
            } else {
                tooltip.add(Component.translatable("tooltip.imttech.firearm.magazine", MagazineItem.rounds(magazine),
                        type.capacity(), chambered(stack) ? "+1" : "").withStyle(ChatFormatting.GOLD));
            }
            int jam = jam(stack);
            if (jam != JAM_NONE) {
                tooltip.add(Component.translatable(jam == JAM_MISFIRE ? "tooltip.imttech.firearm.misfire"
                        : "tooltip.imttech.firearm.jammed").withStyle(ChatFormatting.RED));
            }
        }
        tooltip.add(Component.translatable("tooltip.imttech.firearm.stats", fmt(type.damage()),
                fmt(20f / type.interval()), type.capacity()).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.imttech.firearm.keys").withStyle(ChatFormatting.DARK_GRAY));
        if (ImtConfig.Guns.BULLETS_BREAK_GLASS) {
            tooltip.add(Component.translatable("tooltip.imttech.firearm.glass").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String fmt(float value) {
        return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
