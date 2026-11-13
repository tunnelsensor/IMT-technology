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
 * Een vuurwapen (hoofdstuk 52): de Glock 19, en sinds hoofdstuk 103 ook de MP5 en de andere wapens van
 * {@link FirearmType}. Jouw wens: "supper realistisch". Wat het wapen onthoudt (data components):
 * <ul>
 *   <li>GUN_PARTS: welke onderdelen erin zitten en hoe heel ze zijn (zie {@link GunParts});</li>
 *   <li>GUN_MAGAZINE: het magazijn dat erin zit (met zijn patronen), of geen;</li>
 *   <li>GUN_CHAMBERED: zit er een patroon in de kamer? (jouw keuze: 15 + 1);</li>
 *   <li>GUN_JAM: een storing (0 = geen, 1 = een huls die klem zit, 2 = een ketser): druk R voor een tap-rack;</li>
 *   <li>GUN_FIRE_MODE (hoofdstuk 103): de stand van de keuzeschakelaar (safe, semi, 3 schoten, auto);</li>
 *   <li>GUN_SPENT (hoofdstuk 103): een lege huls in de kamer van een wapen dat je zelf doorlaadt (een grendel of een
 *       pomp): hij komt er pas uit als je R drukt;</li>
 *   <li>GUN_SLING (hoofdstuk 103): hangt er een Gun Sling aan (alleen de lange wapens)? Dan past het in een
 *       sling-vakje.</li>
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

    /** Het vuurwapen in deze hand, of null. */
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

    /** Het magazijn in het wapen als item (een nieuw magazijn met zoveel patronen), of leeg als er geen in zit. */
    public static ItemStack magazine(ItemStack stack) {
        GunMagazine magazine = stack.get(ModDataComponents.GUN_MAGAZINE.get());
        FirearmType type = typeOf(stack);
        if (magazine == null || type == null || type.tube()) {
            return ItemStack.EMPTY;
        }
        return MagazineItem.of(type, magazine.extended(), magazine.rounds());
    }

    /** Zit er een magazijn in? Een wapen met een buis (de Remington 870) heeft die altijd. */
    public static boolean hasMagazine(ItemStack stack) {
        FirearmType type = typeOf(stack);
        return (type != null && type.tube()) || stack.has(ModDataComponents.GUN_MAGAZINE.get());
    }

    /** Dit magazijn (een item) in het wapen stoppen, of het eruit halen (een leeg item). */
    public static void setMagazine(ItemStack stack, ItemStack magazine) {
        if (magazine.isEmpty()) {
            stack.remove(ModDataComponents.GUN_MAGAZINE.get());
        } else {
            stack.set(ModDataComponents.GUN_MAGAZINE.get(),
                    new GunMagazine(MagazineItem.rounds(magazine), MagazineItem.extendedOf(magazine)));
        }
    }

    /** Zit het lange magazijn erin (de extended mag)? */
    public static boolean extendedMagazine(ItemStack stack) {
        GunMagazine magazine = stack.get(ModDataComponents.GUN_MAGAZINE.get());
        return magazine != null && magazine.extended();
    }

    /** Zoveel patronen gaan er in het magazijn dat erin zit (of in de buis); zonder magazijn: in een gewoon magazijn. */
    public static int capacity(ItemStack stack) {
        FirearmType type = typeOf(stack);
        return type == null ? 0 : type.capacity(extendedMagazine(stack));
    }

    /** Zoveel patronen zitten er in het magazijn in het wapen (of in de buis). */
    public static int roundsInMagazine(ItemStack stack) {
        GunMagazine magazine = stack.get(ModDataComponents.GUN_MAGAZINE.get());
        return magazine == null ? 0 : Math.max(0, magazine.rounds());
    }

    /** Het aantal patronen in het magazijn (of de buis) zetten; het magazijn zelf blijft hetzelfde. */
    public static void setRoundsInMagazine(ItemStack stack, int rounds) {
        GunMagazine magazine = stack.get(ModDataComponents.GUN_MAGAZINE.get());
        FirearmType type = typeOf(stack);
        if (magazine == null && (type == null || !type.tube())) {
            return;
        }
        stack.set(ModDataComponents.GUN_MAGAZINE.get(),
                magazine == null ? new GunMagazine(Math.max(0, rounds)) : magazine.withRounds(Math.max(0, rounds)));
    }

    /** Eén patroon uit het magazijn in het wapen halen (geeft false als het leeg is). */
    public static boolean takeRoundFromMagazine(ItemStack stack) {
        int rounds = roundsInMagazine(stack);
        if (rounds <= 0 || !hasMagazine(stack)) {
            return false;
        }
        setRoundsInMagazine(stack, rounds - 1);
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

    /**
     * Hoofdstuk 103: zit er een lege huls in de kamer? Alleen bij een wapen dat je zelf doorlaadt (een grendel of een
     * pomp): na het schot blijft de huls zitten tot je R drukt.
     */
    public static boolean spent(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.GUN_SPENT.get()));
    }

    public static void setSpent(ItemStack stack, boolean spent) {
        if (spent) {
            stack.set(ModDataComponents.GUN_SPENT.get(), true);
        } else {
            stack.remove(ModDataComponents.GUN_SPENT.get());
        }
    }

    /** Alle patronen bij elkaar: in het magazijn plus die in de kamer. */
    public static int totalRounds(ItemStack stack) {
        return roundsInMagazine(stack) + (chambered(stack) ? 1 : 0);
    }

    /**
     * Staat de slede naar achteren? Bij een echte Glock blijft de slede open staan na het laatste schot als er een
     * (leeg) magazijn in zit; zonder magazijn valt hij gewoon dicht. De lange wapens van hoofdstuk 103 hebben dat niet:
     * hun grendel valt dicht op een lege kamer.
     */
    public static boolean slideLocked(ItemStack stack) {
        FirearmType type = typeOf(stack);
        return type != null && !type.longGun && !chambered(stack) && hasMagazine(stack) && roundsInMagazine(stack) == 0
                && jam(stack) == JAM_NONE;
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

    // ================================================================ De keuzeschakelaar en de sling (hoofdstuk 103)

    /** De stand van de keuzeschakelaar. Een wapen zonder schakelaar (de Glock) staat altijd op één schot. */
    public static FirearmType.FireMode fireMode(ItemStack stack) {
        FirearmType type = typeOf(stack);
        if (type == null) {
            return FirearmType.FireMode.SEMI;
        }
        Integer index = stack.get(ModDataComponents.GUN_FIRE_MODE.get());
        return type.mode(index == null ? -1 : index);
    }

    /** Het nummer van de stand (in de rij van dit wapen). */
    public static int fireModeIndex(ItemStack stack) {
        FirearmType type = typeOf(stack);
        if (type == null) {
            return 0;
        }
        Integer index = stack.get(ModDataComponents.GUN_FIRE_MODE.get());
        return index == null || index < 0 || index >= type.modes() ? type.defaultMode() : index;
    }

    public static void setFireMode(ItemStack stack, int index) {
        FirearmType type = typeOf(stack);
        if (type == null || !type.selectable()) {
            return;
        }
        stack.set(ModDataComponents.GUN_FIRE_MODE.get(), Math.floorMod(index, type.modes()));
    }

    /** De schakelaar één stand verder (na de laatste weer de eerste). Geeft de nieuwe stand. */
    public static FirearmType.FireMode nextFireMode(ItemStack stack) {
        setFireMode(stack, fireModeIndex(stack) + 1);
        return fireMode(stack);
    }

    /** Hangt er een Gun Sling aan? (Alleen de lange wapens; dan past het in een sling-vakje.) */
    public static boolean hasSling(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.GUN_SLING.get()));
    }

    public static void setSling(ItemStack stack, boolean sling) {
        if (sling) {
            stack.set(ModDataComponents.GUN_SLING.get(), true);
        } else {
            stack.remove(ModDataComponents.GUN_SLING.get());
        }
    }

    // ================================================================ Attachments (hoofdstuk 103, stap 5)

    /** Wat er op dit wapen zit (leeg als er niets op zit). */
    public static GunAttachments attachments(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.GUN_ATTACHMENTS.get(), GunAttachments.EMPTY);
    }

    /** Wat er op deze plek zit, of null. */
    public static @Nullable Attachment attachment(ItemStack stack, AttachmentSlot slot) {
        return attachments(stack).get(slot);
    }

    /** De kijker op dit wapen (de Red Dot Sight of de Sniper Scope), of null: dan richt je over het ijzeren vizier. */
    public static @Nullable Attachment sight(ItemStack stack) {
        return attachment(stack, AttachmentSlot.SIGHT);
    }

    /** Dit attachment erop (wat op zijn plek zat, verdwijnt: haal dat er eerst af als je het wil houden). */
    public static void setAttachment(ItemStack stack, Attachment attachment) {
        stack.set(ModDataComponents.GUN_ATTACHMENTS.get(), attachments(stack).with(attachment));
    }

    /** Wat op deze plek zat eraf; geeft terug wat het was, of null. */
    public static @Nullable Attachment removeAttachment(ItemStack stack, AttachmentSlot slot) {
        GunAttachments now = attachments(stack);
        Attachment was = now.get(slot);
        if (was == null) {
            return null;
        }
        GunAttachments rest = now.without(slot);
        if (rest.isEmpty()) {
            stack.remove(ModDataComponents.GUN_ATTACHMENTS.get());
        } else {
            stack.set(ModDataComponents.GUN_ATTACHMENTS.get(), rest);
        }
        return was;
    }

    // ================================================================ Een nieuw wapen

    /**
     * Een compleet wapen met een vol magazijn en een patroon in de kamer (voor creative en tests). Met staal krijg je
     * het echte wapen (de Glock met een polymeer frame en trekker, de rest staal); met een ander materiaal alles daarvan
     * (waar dat mag). Een lang wapen krijgt zijn sling er meteen aan.
     */
    public static ItemStack loaded(FirearmType type, GunMaterial material) {
        ItemStack stack = new ItemStack(ModItems.firearm(type));
        setParts(stack, material == GunMaterial.STEEL ? GunParts.classic(type) : GunParts.complete(type, material));
        if (type.tube()) {
            stack.set(ModDataComponents.GUN_MAGAZINE.get(), new GunMagazine(type.capacity()));
        } else {
            setMagazine(stack, MagazineItem.of(type, type.capacity()));
        }
        setChambered(stack, true);
        if (type.longGun) {
            setSling(stack, true);
        }
        return stack;
    }

    /** Een wapen met alleen deze onderdelen, zonder magazijn (zo ontstaat het op de Gunsmith Table). */
    public static ItemStack withParts(FirearmType type, GunParts parts) {
        ItemStack stack = new ItemStack(ModItems.firearm(type));
        setParts(stack, parts);
        return stack;
    }

    // ================================================================ Geen gewoon gereedschap

    /** Met een vuurwapen in je hand breek je geen blokken (linkerklik is schieten). */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, net.minecraft.world.InteractionHand hand) {
        return true;
    }

    /** Een schot (de kamer en het magazijn veranderen) laat het wapen niet opnieuw in je hand komen. */
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
        return Math.round(13 * roundsInMagazine(stack) / (float) Math.max(1, capacity(stack)));
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
                    state.material().text(), entry.getKey().text(type), state.condition())).withStyle(colour));
        }
        if (parts.complete()) {
            if (!hasMagazine(stack)) {
                tooltip.add(Component.translatable("tooltip.imttech.firearm.no_magazine").withStyle(ChatFormatting.RED));
            } else {
                tooltip.add(Component.translatable("tooltip.imttech.firearm.magazine", roundsInMagazine(stack),
                        capacity(stack), chambered(stack) ? "+1" : "").withStyle(ChatFormatting.GOLD));
            }
            int jam = jam(stack);
            if (jam != JAM_NONE) {
                tooltip.add(Component.translatable(jam == JAM_MISFIRE ? "tooltip.imttech.firearm.misfire"
                        : "tooltip.imttech.firearm.jammed").withStyle(ChatFormatting.RED));
            } else if (spent(stack)) {
                tooltip.add(Component.translatable("tooltip.imttech.firearm.spent").withStyle(ChatFormatting.GOLD));
            }
        }
        if (type.selectable()) {
            tooltip.add(Component.translatable("tooltip.imttech.firearm.mode", fireMode(stack).text())
                    .withStyle(fireMode(stack) == FirearmType.FireMode.SAFE ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        }
        for (Attachment attachment : attachments(stack).list()) {
            tooltip.add(Component.translatable("tooltip.imttech.firearm.attachment", attachment.slot.text(), attachment.text())
                    .withStyle(ChatFormatting.AQUA));
        }
        if (type.longGun) {
            tooltip.add(Component.translatable(hasSling(stack) ? "tooltip.imttech.firearm.sling" : "tooltip.imttech.firearm.no_sling")
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("tooltip.imttech.firearm.stats", fmt(type.damage()),
                fmt(20f / type.interval()), type.capacity()).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(type.selectable() ? "tooltip.imttech.firearm.keys_selector" : "tooltip.imttech.firearm.keys")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (ImtConfig.Guns.BULLETS_BREAK_GLASS) {
            tooltip.add(Component.translatable("tooltip.imttech.firearm.glass").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String fmt(float value) {
        return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
