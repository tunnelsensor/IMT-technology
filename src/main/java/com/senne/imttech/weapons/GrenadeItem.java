package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModDataComponents;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * De granaten als item (hoofdstuk 35): de EMP Grenade (een bol die rolt) en de Sticky EMP Grenade (een halve bol die
 * plakt), hun kopieën als explosief (de Frag Grenade en de Sticky Frag Grenade), en de Smoke Grenade en de Flashbang
 * (allebei een bol). Rechtsklik gooit er een; met sneak gooi je hem zachtjes
 * onderhands (een bol rolt dan over de vloer, zoals bij bowlen). Ze stapelen tot 16.
 *
 * Een smokebom kan een kleur hebben (het data component SMOKE_COLOR): je maakt hem met een kleurstof (jouw keuze).
 */
public class GrenadeItem extends Item {
    public enum Kind { EMP, STICKY_EMP, FRAG, STICKY_FRAG, SMOKE, FLASH }

    /** Zo lang (ticks) moet je wachten tussen twee worpen. */
    private static final int COOLDOWN = 10;

    private final Kind kind;

    public GrenadeItem(Properties properties, Kind kind) {
        super(properties.stacksTo(16));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean toss = player.isShiftKeyDown();
        if (!level.isClientSide) {
            GrenadeEntity grenade = switch (kind) {
                case STICKY_EMP -> new StickyGrenade(level, player, false);
                case STICKY_FRAG -> new StickyGrenade(level, player, true);
                case EMP -> RollingGrenade.thrown(RollingGrenade.Kind.EMP, level, player);
                case FRAG -> RollingGrenade.thrown(RollingGrenade.Kind.FRAG, level, player);
                case SMOKE -> RollingGrenade.thrown(RollingGrenade.Kind.SMOKE, level, player);
                case FLASH -> RollingGrenade.thrown(RollingGrenade.Kind.FLASH, level, player);
            };
            if (grenade instanceof RollingGrenade ball) {
                ball.setSmokeColor(stack.get(ModDataComponents.SMOKE_COLOR.get()));
            }
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
    public Component getName(ItemStack stack) {
        DyeColor color = stack.get(ModDataComponents.SMOKE_COLOR.get());
        if (kind == Kind.SMOKE && color != null) {
            return Component.translatable("item.imttech.smoke_grenade.colored",
                    Component.translatable("color.minecraft." + color.getName()));
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        switch (kind) {
            case EMP, STICKY_EMP -> {
                tooltip.add(Component.translatable(kind == Kind.STICKY_EMP ? "tooltip.imttech.sticky_emp_grenade"
                        : "tooltip.imttech.emp_grenade").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tooltip.imttech.emp_grenade.what", (int) ImtConfig.Weapons.EMP_RADIUS,
                        Math.round(ImtConfig.Weapons.EMP_SECONDS)).withStyle(ChatFormatting.DARK_AQUA));
            }
            case FRAG, STICKY_FRAG -> {
                tooltip.add(Component.translatable(kind == Kind.STICKY_FRAG ? "tooltip.imttech.sticky_emp_grenade"
                        : "tooltip.imttech.emp_grenade").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tooltip.imttech.frag_grenade.what").withStyle(ChatFormatting.RED));
            }
            case SMOKE -> {
                tooltip.add(Component.translatable("tooltip.imttech.smoke_grenade").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tooltip.imttech.smoke_grenade.what").withStyle(ChatFormatting.DARK_AQUA));
                if (stack.get(ModDataComponents.SMOKE_COLOR.get()) == null) {
                    tooltip.add(Component.translatable("tooltip.imttech.smoke_grenade.dye").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            case FLASH -> {
                tooltip.add(Component.translatable("tooltip.imttech.flashbang").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tooltip.imttech.flashbang.what").withStyle(ChatFormatting.DARK_AQUA));
            }
        }
        if (kind != Kind.STICKY_EMP && kind != Kind.STICKY_FRAG) {
            tooltip.add(Component.translatable("tooltip.imttech.emp_grenade.toss").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
