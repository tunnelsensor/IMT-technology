package com.senne.imttech.weapons;

import com.senne.imttech.registry.ModDataComponents;
import java.util.List;
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
 * Een lasergeweer (hoofdstuk 35): het pistool, het geweer, de sniper of de shotgun (zie {@link GunType}). Jouw keuze:
 * bediening zoals TACZ. Linkerklik schiet, rechterklik richt door het vizier, R herlaadt, H bekijkt het geweer; dat
 * regelt je spel (zie client/weapons/GunControl), de server beslist (zie {@link LaserGuns}).
 *
 * In het geweer zit een energiecel (of niet): het data component GUN_CELL is hoe vol die cel is (0 tot 100), en
 * zonder component zit er geen cel in. Een nieuw geweer is leeg: druk R en hij pakt de volste cel uit je inventaris.
 */
public class LaserGunItem extends Item {
    private final GunType type;

    public LaserGunItem(Properties properties, GunType type) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    public GunType type() {
        return type;
    }

    /** Het geweer in deze hand, of null. */
    public static @Nullable GunType typeOf(ItemStack stack) {
        return stack.getItem() instanceof LaserGunItem gun ? gun.type : null;
    }

    /** Hoe vol de cel in dit geweer is (0 tot 100), of -1 als er geen cel in zit. */
    public static float cell(ItemStack stack) {
        Float charge = stack.get(ModDataComponents.GUN_CELL.get());
        return charge == null ? -1 : charge;
    }

    /** Zet een cel met deze lading in het geweer (of haal hem eruit met -1). */
    public static void setCell(ItemStack stack, float charge) {
        if (charge < 0) {
            stack.remove(ModDataComponents.GUN_CELL.get());
        } else {
            stack.set(ModDataComponents.GUN_CELL.get(), Math.min(EnergyCellItem.FULL, charge));
        }
    }

    /** Zoveel schoten zitten er nog in. */
    public static int shotsLeft(ItemStack stack) {
        GunType type = typeOf(stack);
        float charge = cell(stack);
        return type == null || charge <= 0 ? 0 : (int) Math.floor(charge / Math.max(0.01f, type.cost()) + 1.0E-4);
    }

    // ================================================================ Geen gewoon gereedschap

    /** Met een geweer in je hand breek je geen blokken (linkerklik is schieten). */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    /** En je zwaait er niet mee. */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, net.minecraft.world.InteractionHand hand) {
        return true;
    }

    /** Een schot (de cel wordt leger) laat het geweer niet opnieuw in je hand komen. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    // ================================================================ Het streepje en de uitleg

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return cell(stack) >= 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13 * Math.max(0, cell(stack)) / EnergyCellItem.FULL);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3FD8FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech." + type.id).withStyle(ChatFormatting.GRAY));
        String damage = type.rays() > 1 ? type.rays() + " x " + fmt(type.damage()) : fmt(type.damage());
        tooltip.add(Component.translatable("tooltip.imttech.laser_gun.stats", damage,
                fmt(20f / type.interval()), type.shotsPerCell()).withStyle(ChatFormatting.DARK_AQUA));
        float cell = cell(stack);
        tooltip.add(cell < 0
                ? Component.translatable("tooltip.imttech.laser_gun.no_cell").withStyle(ChatFormatting.RED)
                : Component.translatable("tooltip.imttech.laser_gun.cell", shotsLeft(stack), type.shotsPerCell())
                        .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.imttech.laser_gun.keys").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String fmt(float value) {
        return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
