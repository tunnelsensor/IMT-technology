package com.senne.starktech.energy;

import com.senne.starktech.registry.ModItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Een rol Stark-kabel. Daarmee TEKEN je kabels, in plaats van blokken neer te zetten:
 *
 * <ul>
 *   <li><b>Rechtermuisknop ingedrukt houden</b> en rondkijken: waar je kijkt komt een kabel te
 *       liggen, over de vloer, tegen een muur op, onder een plafond. Beweeg je sneller dan één
 *       blok per keer, dan trekt hij netjes een lijn van waar je was naar waar je nu kijkt.
 *       Elk stukje kost één kabel van de rol.</li>
 *   <li><b>Shift + rechtsklik</b> op een kabel: oprapen (je krijgt hem terug).</li>
 *   <li>Een <b>schaar</b> (rechtsklik) knipt een kabel door (zie onRightClick hieronder).</li>
 * </ul>
 *
 * Ook in de zone van iemand anders mag dat allemaal (jouw wens, 9 oktober): zet je kabels dus goed
 * weg, in een muur of vloer, anders knipt een inbreker de stroom van je zone eruit.
 *
 * Een kabel is geen blok: je kunt er gewoon een muur of een vloer overheen bouwen, dan loopt hij
 * erin door (zie StarkCables). Een verborgen kabel zie je alleen met de bril op.
 */
public class CableSpoolItem extends Item {
    /** Waar elke speler de vorige tick zijn kabel legde (zolang hij de knop vasthoudt). */
    private static final Map<UUID, BlockPos> DRAWING = new HashMap<>();
    /** Zo ver kun je een kabel leggen of oprapen (blokken). */
    private static final double REACH = 6.0;

    public CableSpoolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                BlockPos cable = lookedAtCable(serverLevel, serverPlayer);
                if (cable != null) {
                    take(serverLevel, serverPlayer, cable);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** Klik je op een blok, dan doen we hetzelfde als in de lucht klikken (zie use). */
    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000; // zo lang als je wilt
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    /** Elke tick zolang je de knop vasthoudt: een stukje kabel leggen waar je kijkt. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            return;
        }
        BlockPos cell = lookedAtCell(player);
        if (cell == null) {
            return;
        }
        BlockPos last = DRAWING.get(player.getUUID());
        List<BlockPos> path = last == null ? List.of(cell) : StarkCables.route(serverLevel, last, cell);
        int placed = 0;
        for (BlockPos pos : path) {
            if (StarkCables.has(serverLevel, pos)) {
                continue;
            }
            if (!player.getAbilities().instabuild && stack.isEmpty()) {
                break;
            }
            if (StarkCables.add(serverLevel, pos)) {
                placed++;
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }
        DRAWING.put(player.getUUID(), cell);
        if (placed > 0 && remaining % 3 == 0) {
            serverLevel.playSound(null, cell, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.4f, 1.3f);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        DRAWING.remove(entity.getUUID());
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DRAWING.remove(event.getEntity().getUUID());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starktech.cable.draw").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.cable.take").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.cable.cut").withStyle(ChatFormatting.DARK_AQUA));
    }

    // ================================================================ Waar kijk je naar?

    /** De plek vóór het blok waar je naar kijkt: daar komt de kabel te liggen (op dat blok). */
    private static @Nullable BlockPos lookedAtCell(ServerPlayer player) {
        HitResult hit = player.pick(REACH, 0.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return blockHit.getBlockPos().relative(blockHit.getDirection());
    }

    /**
     * De eerste kabel die je ziet als je kijkt: we lopen in kleine stapjes langs je kijklijn.
     * Een kabel die in een blok zit (verborgen), kun je niet pakken: daar zit het blok voor.
     */
    public static @Nullable BlockPos lookedAtCable(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 step = player.getViewVector(1.0f).scale(0.1);
        BlockPos lastCell = null;
        for (int i = 0; i < REACH * 10; i++) {
            Vec3 at = eye.add(step.scale(i));
            BlockPos cell = BlockPos.containing(at);
            if (cell.equals(lastCell)) {
                continue;
            }
            lastCell = cell;
            if (StarkCables.isSolid(level, cell)) {
                return null; // een muur: verder kijken we niet
            }
            if (StarkCables.has(level, cell)) {
                return cell;
            }
        }
        return null;
    }

    /** Opraapt (shift + rechtsklik met de rol): de kabel weg, jij krijgt hem terug. */
    private static void take(ServerLevel level, ServerPlayer player, BlockPos cable) {
        if (StarkCables.remove(level, cable)) {
            if (!player.getAbilities().instabuild) {
                ItemStack back = new ItemStack(ModItems.CABLE.get());
                if (!player.getInventory().add(back)) {
                    player.drop(back, false);
                }
            }
            level.playSound(null, cable, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.5f, 1.2f);
        }
    }

    // ================================================================ Doorknippen met een schaar

    /**
     * Rechtsklik met een schaar: ligt er een kabel in je kijklijn (vóór het blok waar je naar
     * kijkt), dan knip je hem door. Er valt een stukje kabel uit, en er springen vonken.
     * Zo kun je bijvoorbeeld de stroom naar de lasers of torentjes van een gebouw afsnijden.
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (tryCut(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (tryCut(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }

    private static boolean tryCut(Player player, ItemStack held) {
        if (!held.is(Items.SHEARS) || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos cable = lookedAtCable(level, player);
        if (cable == null) {
            return false;
        }
        if (!StarkCables.remove(level, cable)) {
            return false;
        }
        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level,
                cable.getX() + 0.5, cable.getY() + 0.3, cable.getZ() + 0.5, new ItemStack(ModItems.CABLE.get()));
        level.addFreshEntity(drop);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cable.getX() + 0.5, cable.getY() + 0.2, cable.getZ() + 0.5,
                12, 0.2, 0.1, 0.2, 0.2);
        level.playSound(null, cable, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0f, 1.2f);
        held.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        return true;
    }
}
