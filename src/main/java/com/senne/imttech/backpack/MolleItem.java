package com.senne.imttech.backpack;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.GearContents;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.GrenadeItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Een MOLLE-pouch (hoofdstuk 72, stap 2, jouw keuze: alle vier die ik voorstelde): een kleine tas die je aan de
 * MOLLE-banden van een rugzak hangt (in het scherm van de rugzak, de vakjes rechts). Je komt erbij <b>zonder de rits</b>:
 * via het wiel (linker Alt), en sommige doen het vanzelf:
 * <ul>
 *   <li><b>MOLLE Mag Pouch</b>: 3 magazijnen of cellen; R pakt eruit als je heup-pouch leeg is (zie Molle.magazines);</li>
 *   <li><b>MOLLE Grenade Pouch</b>: 2 granaten; trekken en aanvoeren pakt eruit na je granaatpouch;</li>
 *   <li><b>Dump Pouch</b>: raapt vanzelf de hulzen rond je voeten op (voor de Reloading Bench);</li>
 *   <li><b>IFAK</b> (een medkit): 3 genees-items (gouden appels, drankjes), in het wiel snel te gebruiken;</li>
 *   <li><b>Utility Pouch</b>: 4 vakjes voor wat dan ook (behalve dozen en volle pouches).</li>
 * </ul>
 * Wat erin zit staat in het data component GEAR_CONTENTS (zoals bij de holster en de pouches van hoofdstuk 52). Je kunt
 * hem ook in je hand vullen (rechtsklik). Verven kan net als de rugzak (BackpackDyeRecipe); nieuw is hij olijfgroen.
 */
public class MolleItem extends Item {
    public enum Kind {
        MAG("molle_mag_pouch"),
        GRENADE("molle_grenade_pouch"),
        DUMP("molle_dump_pouch"),
        IFAK("ifak"),
        UTILITY("utility_pouch");

        public final String id;

        Kind(String id) {
            this.id = id;
        }

        /** Zoveel vakjes (uit de config). */
        public int size() {
            float size = switch (this) {
                case MAG -> ImtConfig.Backpacks.MOLLE_MAG_SIZE;
                case GRENADE -> ImtConfig.Backpacks.MOLLE_GRENADE_SIZE;
                case DUMP -> ImtConfig.Backpacks.DUMP_POUCH_SIZE;
                case IFAK -> ImtConfig.Backpacks.IFAK_SIZE;
                case UTILITY -> ImtConfig.Backpacks.UTILITY_POUCH_SIZE;
            };
            return Math.max(1, Math.round(size));
        }

        /** Past dit erin? */
        public boolean accepts(ItemStack stack) {
            if (stack.isEmpty()) {
                return true;
            }
            return switch (this) {
                case MAG -> MagazineItem.typeOf(stack) != null || stack.getItem() instanceof EnergyCellItem;
                case GRENADE -> stack.getItem() instanceof GrenadeItem && !GrenadeItem.isLive(stack);
                case DUMP -> com.senne.imttech.guns.Caliber.ofCasing(stack) != null; // hoofdstuk 103: elk kaliber
                case IFAK -> healing(stack);
                case UTILITY -> !(stack.getItem() instanceof MolleItem) && BackpackItem.mayHold(stack);
            };
        }

        public Component text() {
            return Component.translatable("item.imttech." + id);
        }
    }

    /** Een genees-item voor de IFAK: een gouden appel (ook de betoverde) of een drankje dat je drinkt. */
    public static boolean healing(ItemStack stack) {
        return stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.POTION);
    }

    private final Kind kind;

    public MolleItem(Properties properties, Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** Wat voor pouch dit is, of null. */
    public static Kind kindOf(ItemStack stack) {
        return stack.getItem() instanceof MolleItem item ? item.kind : null;
    }

    /** De inhoud (kopieën), precies zoveel als er vakjes zijn. */
    public static List<ItemStack> contents(ItemStack pouch) {
        Kind kind = kindOf(pouch);
        if (kind == null) {
            return List.of();
        }
        GearContents stored = pouch.get(ModDataComponents.GEAR_CONTENTS.get());
        return GearContents.sized(stored == null ? List.of() : stored.stacks(), kind.size()).mutable();
    }

    public static void setContents(ItemStack pouch, List<ItemStack> contents) {
        Kind kind = kindOf(pouch);
        if (kind == null) {
            return;
        }
        GearContents sized = GearContents.sized(contents, kind.size());
        if (sized.isEmpty()) {
            pouch.remove(ModDataComponents.GEAR_CONTENTS.get());
        } else {
            pouch.set(ModDataComponents.GEAR_CONTENTS.get(), sized);
        }
    }

    /** Zoveel vakjes zijn bezet. */
    public static int filled(ItemStack pouch) {
        int count = 0;
        for (ItemStack stack : contents(pouch)) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** Rechtsklik met de pouch in je hand: openen, om hem te vullen voor je hem aan een rugzak hangt. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            Molle.openHeld(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech." + kind.id, kind.size()).withStyle(ChatFormatting.GRAY));
        if (BackpackItem.camo(stack)) {
            tooltip.add(Component.translatable("tooltip.imttech.backpack.camo").withStyle(ChatFormatting.DARK_GREEN));
        }
        for (ItemStack inside : contents(stack)) {
            if (!inside.isEmpty()) {
                tooltip.add(Component.literal(inside.getCount() + "x ").append(inside.getHoverName()).withStyle(ChatFormatting.GOLD));
            }
        }
        tooltip.add(Component.translatable("tooltip.imttech.molle.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
