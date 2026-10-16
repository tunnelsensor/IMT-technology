package com.senne.starktech.robot;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.network.BeamEffectPayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.shield.EdithDomes;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De Ion Blaster (hoofdstuk 24, jouw wens: "een wapen waardoor ze worden gedisabled maar niet kapot gaan"). Rechtsklik
 * en hij schiet een blauwe ionenstraal, recht vooruit. Raakt hij een guard robot, dan valt die neer
 * ({@link RobotState#DOWN}): zijn programma staat stil, en na een tijdje (ionDownSeconds) staat hij weer op. Kapot
 * gaat hij er niet van; wil je hem echt kapot, gebruik dan een gewoon wapen. Op mensen en dieren doet de straal
 * niets. Een muur en een schildkoepel houden hem tegen, zoals andere schoten.
 *
 * Hij slijt een beetje bij elk schot ({@link #USES} keer), en tussen twee schoten moet hij even opladen
 * (ionCooldown).
 */
public class IonBlasterItem extends Item {
    /** Zo vaak kun je ermee schieten (vast: hoort bij het item zelf). */
    public static final int USES = 200;

    public IonBlasterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            fire(server, player);
            player.getCooldowns().addCooldown(this, Math.max(1, Math.round(StarkConfig.Robots.ION_COOLDOWN)));
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** Eén ionenstraal, waar deze speler naar kijkt. Geeft wat hij raakte (een robot, een dier, een speler), of null. */
    public static @Nullable Entity fire(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(StarkConfig.Robots.ION_RANGE));
        HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getLocation();
        }
        EntityHitResult first = ProjectileUtil.getEntityHitResult(level, player, eye, end, new AABB(eye, end).inflate(1.0),
                entity -> entity.isAlive() && entity.isPickable() && !entity.isSpectator(), 0.3f);
        Entity hit = null;
        if (first != null) {
            hit = first.getEntity();
            end = first.getLocation();
        }
        Vec3 shield = EdithDomes.blockedAt(level, end, eye, player.getUUID(), Math.round(StarkConfig.Dome.COST_LASER));
        if (shield != null) {
            end = shield;
            hit = null;
        }
        // De straal komt uit de loop: een stukje voor je, rechts en iets lager dan je ogen.
        Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 muzzle = eye.add(look.scale(0.6)).add(side.scale(0.25)).add(0, -0.2, 0);
        level.playSound(null, player.blockPosition(), ModSounds.ION_BLASTER_FIRE.get(), SoundSource.PLAYERS, 0.9f,
                0.95f + level.getRandom().nextFloat() * 0.1f);
        ModNetwork.sendNear(level, player.position(), 64, new BeamEffectPayload(muzzle, end, BeamEffectPayload.Kind.ION_BEAM));
        if (hit instanceof GuardRobotEntity robot) {
            robot.knockDown(Math.round(StarkConfig.Robots.ION_DOWN_SECONDS * 20));
        }
        return hit;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starktech.ion_blaster").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.ion_blaster.harmless").withStyle(ChatFormatting.DARK_AQUA));
    }
}
