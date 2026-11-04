package com.senne.imttech.cuffs;

import com.senne.imttech.config.ImtConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * De handboeien (hoofdstuk 48). Rechtsklik op een speler, een Raven-soldaat of een guard robot en houd de knop
 * ongeveer twee seconden vast ({@code [handcuffs] holdSeconds}): dan gaan de boeien om, en jij houdt hem vast. Is hij
 * weerloos (verblind door een flashbang, in slaap, bijna dood, een robot die neerligt), dan gaat het meteen. Rechtsklik
 * op wie al geboeid is: loslaten of weer vastpakken (zie {@link Handcuffs#toggleHold}). Eén paar boeien per persoon;
 * met de sleutel krijg je ze terug, losworstelen breekt ze.
 */
public class HandcuffsItem extends Item {
    /** Per speler (op de server): op wie hij de boeien aan het omdoen is (zijn entity-id). */
    private static final Map<UUID, Integer> AIMING = new HashMap<>();
    /**
     * Per speler: wanneer hij voor het laatst met de boeien klikte of iemand boeide. Houd je de knop ingedrukt, dan klikt je
     * spel elke vier ticks opnieuw; zo'n herhaalde klik mag niet steeds vastpakken en loslaten (alleen een nieuwe klik telt).
     */
    private static final Map<UUID, Long> LAST_CLICK = new HashMap<>();
    private static final int NEW_CLICK_TICKS = 10;

    public HandcuffsItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (Handcuffs.isCuffed(target)) {
            if (player instanceof ServerPlayer captor && freshClick(captor)) {
                Handcuffs.toggleHold(captor, target);
            }
            return InteractionResult.CONSUME;
        }
        Component why = Handcuffs.cannotCuff(player, target);
        if (why != null) {
            if (player instanceof ServerPlayer captor) {
                captor.displayClientMessage(why.copy().withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer captor) {
            if (Handcuffs.instant(target)) {
                finish(captor, target, stack);
                return InteractionResult.CONSUME;
            }
            AIMING.put(captor.getUUID(), target.getId());
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    /** Elke tick zolang je de knop vasthoudt (op de server): verder omdoen, of stoppen als hij weg is. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity instanceof ServerPlayer captor) {
            progress(captor, stack, getUseDuration(stack, entity) - remaining);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof ServerPlayer captor) {
            AIMING.remove(captor.getUUID());
        }
    }

    /** Begin het omdoen bij {@code target} (wat een rechtsklik doet; voor de tests). */
    public static void aim(ServerPlayer captor, LivingEntity target) {
        AIMING.put(captor.getUUID(), target.getId());
    }

    /**
     * Eén tick vasthouden, {@code used} ticks na het begin: de boeien gaan om als het lang genoeg was (of hij intussen
     * weerloos werd). Geeft true als ze net om zijn. Kijkt hij weg of staat hij te ver, dan stopt het.
     */
    public static boolean progress(ServerPlayer captor, ItemStack stack, int used) {
        Integer id = AIMING.get(captor.getUUID());
        Entity target = id == null ? null : captor.serverLevel().getEntity(id);
        if (!(target instanceof LivingEntity living) || Handcuffs.cannotCuff(captor, living) != null || !looksAt(captor, living)) {
            stop(captor);
            return false;
        }
        int needed = Math.max(1, Math.round(ImtConfig.Handcuffs.HOLD_SECONDS * 20));
        if (used >= needed || Handcuffs.instant(living)) {
            finish(captor, living, stack);
            stop(captor);
            return true;
        }
        if (used % 5 == 0) {
            captor.displayClientMessage(Component.translatable("cuffs.imttech.progress", living.getDisplayName(),
                    String.format(Locale.ROOT, "%.1f", (needed - used) / 20.0)).withStyle(ChatFormatting.AQUA), true);
        }
        return false;
    }

    /** Kijkt hij nog ongeveer naar hem, en dichtbij genoeg? */
    private static boolean looksAt(ServerPlayer captor, LivingEntity target) {
        Vec3 to = target.getEyePosition().subtract(captor.getEyePosition());
        double distance = to.length();
        return distance <= ImtConfig.Handcuffs.REACH + 0.5 && (distance < 0.5 || captor.getViewVector(1.0f).dot(to.scale(1 / distance)) > 0.3);
    }

    /** Is dit een nieuwe klik (en niet de knop die nog ingedrukt is)? Onthoudt meteen het moment. */
    private static boolean freshClick(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_CLICK.put(player.getUUID(), now);
        return last == null || now - last >= NEW_CLICK_TICKS;
    }

    private static void finish(ServerPlayer captor, LivingEntity target, ItemStack stack) {
        LAST_CLICK.put(captor.getUUID(), captor.serverLevel().getGameTime());
        Handcuffs.cuff(captor, target);
        if (!captor.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    private static void stop(ServerPlayer captor) {
        AIMING.remove(captor.getUUID());
        if (captor.isUsingItem()) {
            captor.stopUsingItem();
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.imttech.handcuffs", String.format(Locale.ROOT, "%.0f", ImtConfig.Handcuffs.HOLD_SECONDS))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.imttech.handcuffs.hold").withStyle(ChatFormatting.DARK_GRAY));
    }
}
