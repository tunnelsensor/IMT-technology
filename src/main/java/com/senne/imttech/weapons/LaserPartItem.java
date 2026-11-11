package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * Een los onderdeel van een lasergeweer (hoofdstuk 55): het frame, de receiver, de emitter, de optics, de capacitor of
 * de trigger van één bepaald geweer (een emitter van een rifle past niet op een sniper). Eén item per onderdeel per
 * geweer; de kwaliteit (titanium of netherite) staat in het data component GUN_MATERIAL (het plaatje wisselt mee), en
 * een onderdeel uit een gebruikt geweer houdt zijn conditie (PART_CONDITION). Zoals GunPartItem voor de Glock.
 *
 * Jouw keuze: "deze onderdelen moeten wel heel duur zijn, ze moeten endgame zijn": titanium (met diamanten, chips en
 * lasermodules) en netherite (een titanium onderdeel plus twee Netherite Ingots). Op de Laser Workbench sleep je ze in
 * het geweer (zie LaserWorkbenchBlockEntity). Maken doe je ze sinds hoofdstuk 78 op de Gunsmith Table (het tabblad
 * "Make parts", zie GunsmithRecipe), niet meer in de crafting table; elk onderdeel van de rifle, sniper en shotgun kost
 * er het kenmerk van zijn geweer bij (kwarts, amethist of glowstone), zodat geen twee recepten hetzelfde zijn.
 */
public class LaserPartItem extends Item {
    private final GunType gun;
    private final LaserPart part;

    public LaserPartItem(Properties properties, GunType gun, LaserPart part) {
        super(properties.stacksTo(16));
        this.gun = gun;
        this.part = part;
    }

    public GunType gun() {
        return gun;
    }

    public LaserPart part() {
        return part;
    }

    /** Welk onderdeel dit item is, of null als het geen laseronderdeel is. */
    public static @Nullable LaserPart partOf(ItemStack stack) {
        return stack.getItem() instanceof LaserPartItem item ? item.part : null;
    }

    /** Voor welk geweer dit onderdeel is, of null. */
    public static @Nullable GunType gunOf(ItemStack stack) {
        return stack.getItem() instanceof LaserPartItem item ? item.gun : null;
    }

    /** Van welke kwaliteit (titanium als het er niet bij staat). */
    public static GunMaterial materialOf(ItemStack stack) {
        GunMaterial material = stack.get(ModDataComponents.GUN_MATERIAL.get());
        return material == null || !LaserPart.MATERIALS.contains(material) ? GunMaterial.TITANIUM : material;
    }

    /** Hoe heel (100 = nieuw). */
    public static int conditionOf(ItemStack stack) {
        Integer condition = stack.get(ModDataComponents.PART_CONDITION.get());
        return condition == null ? PartState.NEW : Math.max(0, Math.min(PartState.NEW, condition));
    }

    /** Een onderdeel van dit geweer in deze kwaliteit, zo heel als {@code condition}. */
    public static ItemStack of(GunType gun, LaserPart part, GunMaterial material, int condition) {
        ItemStack stack = new ItemStack(ModItems.laserPart(gun, part));
        stack.set(ModDataComponents.GUN_MATERIAL.get(), LaserPart.MATERIALS.contains(material) ? material : GunMaterial.TITANIUM);
        if (condition < PartState.NEW) {
            stack.set(ModDataComponents.PART_CONDITION.get(), Math.max(0, condition));
        }
        return stack;
    }

    public static ItemStack of(GunType gun, LaserPart part, GunMaterial material) {
        return of(gun, part, material, PartState.NEW);
    }

    /** Het onderdeel zoals het in een geweer komt te zitten. */
    public static PartState stateOf(ItemStack stack) {
        return new PartState(materialOf(stack), conditionOf(stack));
    }

    /** "Titanium Laser Rifle Emitter", "Netherite Laser Pistol Frame"... */
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.imttech.laser_part", materialOf(stack).text(),
                Component.translatable("item.imttech." + gun.id), part.text());
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
        tooltip.add(Component.translatable("tooltip.imttech.laser_part." + part.id).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.laser_part.material", material.text(),
                String.format(java.util.Locale.ROOT, "%.2f", LaserGuns.wearChance(material))).withStyle(ChatFormatting.DARK_AQUA));
        int condition = conditionOf(stack);
        if (condition <= 0) {
            tooltip.add(Component.translatable("tooltip.imttech.laser_part.broken").withStyle(ChatFormatting.RED));
        } else if (condition < PartState.NEW) {
            tooltip.add(Component.translatable("tooltip.imttech.gun_part.condition", condition)
                    .withStyle(condition < ImtConfig.Guns.WORN_BELOW ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable("tooltip.imttech.laser_part.table", Component.translatable("item.imttech." + gun.id))
                .withStyle(ChatFormatting.DARK_GRAY));
        // Hoofdstuk 78: je maakt het op de Gunsmith Table (in elkaar zetten doe je op de Laser Workbench).
        tooltip.add(Component.translatable("tooltip.imttech.gunsmith.made").withStyle(ChatFormatting.DARK_GRAY));
    }
}
