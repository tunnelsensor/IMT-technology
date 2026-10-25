package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * De twee EMP-granaten als item (hoofdstuk 35). Rechtsklik gooit er een; met sneak gooi je hem zachtjes onderhands (de
 * bol rolt dan over de vloer, zoals bij bowlen). Ze stapelen tot 16.
 */
public class EmpGrenadeItem extends Item {
    /** Zo lang (ticks) moet je wachten tussen twee worpen. */
    private static final int COOLDOWN = 10;

    /** De sticky (de halve bol die vastplakt) of de bol die rolt. */
    private final boolean sticky;

    public EmpGrenadeItem(Properties properties, boolean sticky) {
        super(properties.stacksTo(16));
        this.sticky = sticky;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean toss = player.isShiftKeyDown();
        if (!level.isClientSide) {
            EmpGrenadeEntity grenade = sticky ? new StickyEmpGrenade(level, player) : new RollingEmpGrenade(level, player);
            float speed = toss ? ImtConfig.Weapons.TOSS_SPEED : ImtConfig.Weapons.THROW_SPEED;
            // Onderhands: vanaf je heup, schuin naar de vloer voor je (maar nooit omhoog of recht naar beneden).
            float pitch = toss ? Mth.clamp(player.getXRot(), 8, 40) : player.getXRot();
            if (toss) {
                grenade.setPos(player.getX(), player.getY() + 0.8, player.getZ());
            }
            grenade.shootFromRotation(player, pitch, player.getYRot(), 0, speed, 1.0f);
            level.addFreshEntity(grenade);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                    SoundSource.PLAYERS, 0.5f, 0.5f);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(sticky ? "tooltip.imttech.sticky_emp_grenade" : "tooltip.imttech.emp_grenade")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.emp_grenade.what", (int) ImtConfig.Weapons.EMP_RADIUS,
                Math.round(ImtConfig.Weapons.EMP_SECONDS)).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.imttech.emp_grenade.toss").withStyle(ChatFormatting.DARK_GRAY));
    }
}
