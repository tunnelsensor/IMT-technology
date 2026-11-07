package com.senne.imttech.guns;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModDataComponents;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * De dingen die je draagt om snel bij je wapens te kunnen (hoofdstuk 52, stap 2, jouw wens): de <b>Holster</b> (één
 * pistool), de <b>Grenade Pouch</b> (granaten, 4 vakjes) en de <b>Magazine Pouch</b> (magazijnen, 3 vakjes). Je draagt
 * ze in een Curios-slot als die mod erbij zit, anders in de eigen vakjes naast je harnas (zie Gear). Wat erin zit,
 * staat in het data component GEAR_CONTENTS. Met het wiel (linker Alt) trek je het pistool of een granaat.
 */
public class GearItem extends Item {
    public enum Kind {
        HOLSTER("holster"),
        GRENADE_POUCH("grenade_pouch"),
        MAG_POUCH("mag_pouch");

        public final String id;

        Kind(String id) {
            this.id = id;
        }

        /** Zoveel vakjes. */
        public int size() {
            return switch (this) {
                case HOLSTER -> 1;
                case GRENADE_POUCH -> Math.max(1, Math.round(ImtConfig.Guns.GRENADE_POUCH_SIZE));
                case MAG_POUCH -> Math.max(1, Math.round(ImtConfig.Guns.MAG_POUCH_SIZE));
            };
        }

        /** Past dit item erin? */
        public boolean accepts(ItemStack stack) {
            return switch (this) {
                // Sinds hoofdstuk 55 ook het laserpistool (een pistool tegelijk, jouw keuze).
                case HOLSTER -> FirearmItem.typeOf(stack) != null
                        || com.senne.imttech.weapons.LaserGunItem.typeOf(stack) == com.senne.imttech.weapons.GunType.PISTOL;
                case GRENADE_POUCH -> stack.getItem() instanceof GrenadeItem;
                // Sinds hoofdstuk 55 ook energiecellen (het magazijn van een lasergeweer).
                case MAG_POUCH -> MagazineItem.typeOf(stack) != null || stack.getItem() instanceof com.senne.imttech.weapons.EnergyCellItem;
            };
        }

        public Component text() {
            return Component.translatable("item.imttech." + id);
        }
    }

    private final Kind kind;

    public GearItem(Properties properties, Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** Rechtsklik met de holster of pouch in je hand: openen, om hem te vullen (hoofdstuk 54). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            Gear.openHeld(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /** Wat voor ding dit is, of null. */
    public static Kind kindOf(ItemStack stack) {
        return stack.getItem() instanceof GearItem item ? item.kind : null;
    }

    /** De inhoud (kopieën), altijd precies zoveel als er vakjes zijn. */
    public static List<ItemStack> contents(ItemStack gear) {
        Kind kind = kindOf(gear);
        if (kind == null) {
            return List.of();
        }
        GearContents stored = gear.get(ModDataComponents.GEAR_CONTENTS.get());
        return GearContents.sized(stored == null ? List.of() : stored.stacks(), kind.size()).mutable();
    }

    public static void setContents(ItemStack gear, List<ItemStack> contents) {
        Kind kind = kindOf(gear);
        if (kind == null) {
            return;
        }
        GearContents sized = GearContents.sized(contents, kind.size());
        if (sized.isEmpty()) {
            gear.remove(ModDataComponents.GEAR_CONTENTS.get());
        } else {
            gear.set(ModDataComponents.GEAR_CONTENTS.get(), sized);
        }
    }

    /** Zoveel vakjes zijn bezet. */
    public static int filled(ItemStack gear) {
        int count = 0;
        for (ItemStack stack : contents(gear)) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech." + kind.id).withStyle(ChatFormatting.GRAY));
        List<ItemStack> contents = contents(stack);
        boolean any = false;
        for (ItemStack inside : contents) {
            if (!inside.isEmpty()) {
                any = true;
                Component line = inside.getCount() > 1 ? Component.literal(inside.getCount() + "x ").append(inside.getHoverName())
                        : inside.getHoverName().copy();
                if (FirearmItem.typeOf(inside) != null) {
                    line = line.copy().append(" (" + FirearmItem.roundsInMagazine(inside) + (FirearmItem.chambered(inside) ? "+1" : "") + ")");
                } else if (MagazineItem.typeOf(inside) != null) {
                    line = line.copy().append(" (" + MagazineItem.rounds(inside) + ")");
                } else if (com.senne.imttech.weapons.LaserGunItem.typeOf(inside) != null) {
                    line = line.copy().append(" (" + com.senne.imttech.weapons.LaserGunItem.shotsLeft(inside) + ")");
                } else if (inside.getItem() instanceof com.senne.imttech.weapons.EnergyCellItem) {
                    line = line.copy().append(" (" + Math.round(com.senne.imttech.weapons.EnergyCellItem.charge(inside)) + "%)");
                }
                tooltip.add(Component.literal("  ").append(line).withStyle(ChatFormatting.GOLD));
            }
        }
        if (!any) {
            tooltip.add(Component.translatable("tooltip.imttech.gear.empty").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.imttech.gear.wear").withStyle(ChatFormatting.DARK_GRAY));
    }
}
