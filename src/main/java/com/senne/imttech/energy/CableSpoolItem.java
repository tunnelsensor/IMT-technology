package com.senne.imttech.energy;

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
 * Een rol kabel: stroomkabel, netwerkkabel of PoE-kabel (hoofdstuk 38, zie CableKind). Daarmee TEKEN je kabels, in
 * plaats van blokken neer te zetten:
 *
 * <ul>
 *   <li><b>Rechtermuisknop ingedrukt houden</b> en rondkijken: waar je kijkt komt een kabel te
 *       liggen, over de vloer, tegen een muur op, onder een plafond. Beweeg je sneller dan één
 *       blok per keer, dan trekt hij netjes een lijn van waar je was naar waar je nu kijkt.
 *       Elk stukje kost één kabel van de rol. Ligt het eind van je kabel te ver weg (meer dan 24
 *       blokken, of er is geen weg langs de oppervlakken), dan begint hij een nieuwe lijn waar je
 *       kijkt, en zegt hij dat boven je werkbalk (hoofdstuk 132).</li>
 *   <li><b>Shift + rechtsklik</b> op een kabel: oprapen (je krijgt hem terug). Alleen de soort van de rol in je
 *       hand: liggen er een stroom- en een netwerkkabel op dezelfde plek, dan raapt een netwerkrol alleen de
 *       netwerkkabel op.</li>
 *   <li>Een <b>schaar</b> (rechtsklik) knipt een kabel door (zie onRightClick hieronder): de kabel die het
 *       dichtst bij je kijklijn ligt.</li>
 * </ul>
 *
 * Ook in de zone van iemand anders mag dat allemaal (jouw wens, 9 oktober): zet je kabels dus goed
 * weg, in een muur of vloer, anders knipt een inbreker de stroom (of het netwerk) van je zone eruit.
 *
 * Een kabel is geen blok: je kunt er gewoon een muur of een vloer overheen bouwen, dan loopt hij
 * erin door (zie Cables). Een verborgen kabel zie je alleen met de bril op.
 */
public class CableSpoolItem extends Item {
    /**
     * Waar elke speler de vorige tick zijn kabel liet ophouden (zolang hij de knop vasthoudt): het laatste stukje dat er echt
     * ligt, en in welke wereld (Overworld, Nether...).
     */
    private record Anchor(net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos) {}

    private static final Map<UUID, Anchor> DRAWING = new HashMap<>();
    /** Zo ver kun je een kabel leggen of oprapen (blokken). */
    private static final double REACH = 6.0;

    /** Welke soort kabel er op deze rol zit. */
    private final CableKind kind;

    public CableSpoolItem(Properties properties, CableKind kind) {
        super(properties);
        this.kind = kind;
    }

    public CableKind kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                Hit cable = lookedAtCable(serverLevel, serverPlayer, kind);
                if (cable != null) {
                    take(serverLevel, serverPlayer, cable);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        // Hoofdstuk 124: elke keer dat je de knop indrukt, begint een nieuwe lijn waar je kijkt. Raakte je vorige rol leeg
        // terwijl je trok, dan liet Minecraft de knop los zonder releaseUsing: de lijn begon dan bij een oude plek, en de nieuwe
        // rol legde een kabel van daar naar waar je nu kijkt (met een gat ertussen).
        if (!level.isClientSide) {
            DRAWING.remove(player.getUUID());
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
        // Hoofdstuk 132: ook niets als die plek buiten de wereld ligt (boven op een blok op de bouwhoogte): daar kan geen
        // kabel liggen, en de zoektocht ernaartoe zou elke tick vergeefs duizenden plekken aflopen.
        if (cell == null || !serverLevel.isLoaded(cell)) {
            return;
        }
        Anchor anchor = DRAWING.get(player.getUUID());
        BlockPos last = anchor != null && anchor.dimension() == serverLevel.dimension() ? anchor.pos() : null;
        // Hoofdstuk 132: ligt het eind van je kabel te ver weg (je keek even in de lucht en liep door, of je werd
        // geteleporteerd), dan geeft route null: we beginnen een nieuwe lijn waar je kijkt. Vroeger trok hij dan een rechte
        // lijn dwars door de muren, en ging je hele rol in één keer op.
        List<BlockPos> path = last == null ? null : PowerCables.route(serverLevel, last, cell);
        boolean newLine = last != null && path == null;
        if (path == null) {
            path = List.of(cell);
        }
        int placed = 0;
        // Hoofdstuk 124: tot waar de kabel nu echt ligt. Raakt de rol leeg halverwege, dan is dat niet waar je kijkt.
        BlockPos reached = null;
        for (BlockPos pos : path) {
            // Hoofdstuk 132: nooit een kabel in een stuk wereld dat niet geladen is (dan zou de server het moeten laden).
            if (!serverLevel.isLoaded(pos)) {
                break;
            }
            if (Cables.has(kind, serverLevel, pos)) {
                reached = pos;
                continue;
            }
            if (!player.getAbilities().instabuild && stack.isEmpty()) {
                break;
            }
            // Het stuk kabel is van jou: met de bril zie je het door een muur heen, een ander niet (hoofdstuk 92).
            if (Cables.add(kind, serverLevel, pos, player.getUUID())) {
                placed++;
                reached = pos;
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }
        if (reached != null) {
            DRAWING.put(player.getUUID(), new Anchor(serverLevel.dimension(), reached));
        } else {
            DRAWING.remove(player.getUUID());
        }
        if (placed > 0 && remaining % 3 == 0) {
            serverLevel.playSound(null, cell, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.4f, 1.3f);
        }
        // Hoofdstuk 132: zeg het als de kabel opnieuw begon, want er zit dan een gat tussen het oude eind en hier.
        if (newLine && placed > 0) {
            player.displayClientMessage(Component.translatable("message.imttech.cable.new_line"), true);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide) { // alleen de server houdt dit bij (in singleplayer delen spel en server deze lijst)
            DRAWING.remove(entity.getUUID());
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DRAWING.remove(event.getEntity().getUUID());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Wat deze soort doet (de stroomkabel spreekt voor zich).
        if (kind == CableKind.NETWORK) {
            tooltip.add(Component.translatable("tooltip.imttech.network_cable").withStyle(ChatFormatting.WHITE));
        } else if (kind == CableKind.POE) {
            tooltip.add(Component.translatable("tooltip.imttech.poe_cable").withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable("tooltip.imttech.cable.draw").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.cable.take").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.cable.cut").withStyle(ChatFormatting.DARK_AQUA));
        // Hoofdstuk 85: de drie soorten zitten niet aan elkaar vast.
        tooltip.add(Component.translatable("tooltip.imttech.cable.apart").withStyle(ChatFormatting.DARK_GRAY));
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

    /** Een stukje kabel waar je naar kijkt: de plek, en welke soort. */
    public record Hit(BlockPos pos, CableKind kind) {}

    /**
     * Het eerste stukje kabel dat je ziet als je kijkt: we lopen in kleine stapjes langs je kijklijn. Een kabel die in een
     * blok zit (verborgen), kun je niet pakken: daar zit het blok voor.
     *
     * @param only alleen deze soort (een rol raapt alleen zijn eigen soort op), of null = elke soort. Liggen er dan meer
     *             soorten op die plek, dan krijg je de kabel die het dichtst bij je kijklijn ligt (zie CableGeometry: ze
     *             liggen boven elkaar).
     */
    public static @Nullable Hit lookedAtCable(ServerLevel level, Player player, @Nullable CableKind only) {
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0f);
        Vec3 step = view.scale(0.1);
        BlockPos lastCell = null;
        for (int i = 0; i < REACH * 10; i++) {
            Vec3 at = eye.add(step.scale(i));
            BlockPos cell = BlockPos.containing(at);
            if (cell.equals(lastCell)) {
                continue;
            }
            lastCell = cell;
            if (PowerCables.isSolid(level, cell)) {
                return null; // een muur: verder kijken we niet
            }
            if (only != null) {
                if (Cables.has(only, level, cell)) {
                    return new Hit(cell, only);
                }
                continue;
            }
            CableKind best = null;
            double bestDistance = Double.MAX_VALUE;
            for (CableKind kind : Cables.at(level, cell)) {
                Vec3 point = Vec3.atLowerCornerOf(cell).add(CableGeometry.lay(level, cell, kind, false).point());
                double distance = point.subtract(eye).cross(view).lengthSqr();
                if (distance < bestDistance) {
                    best = kind;
                    bestDistance = distance;
                }
            }
            if (best != null) {
                return new Hit(cell, best);
            }
        }
        return null;
    }

    /** Opraapt (shift + rechtsklik met de rol): de kabel weg, jij krijgt hem terug. */
    private static void take(ServerLevel level, ServerPlayer player, Hit cable) {
        if (Cables.remove(cable.kind(), level, cable.pos())) {
            if (!player.getAbilities().instabuild) {
                ItemStack back = new ItemStack(cable.kind().item());
                if (!player.getInventory().add(back)) {
                    player.drop(back, false);
                }
            }
            level.playSound(null, cable.pos(), SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.5f, 1.2f);
        }
    }

    // ================================================================ Doorknippen met een schaar

    /**
     * Rechtsklik met een schaar: ligt er een kabel in je kijklijn (vóór het blok waar je naar
     * kijkt), dan knip je hem door. Er valt een stukje kabel uit, en er springen vonken.
     * Zo kun je bijvoorbeeld de stroom naar de lasers of torentjes van een gebouw afsnijden, of het
     * netwerk naar een camera. Liggen er meer soorten op één plek, dan knip je de kabel die het
     * dichtst bij je kijklijn ligt.
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
        Hit hit = lookedAtCable(level, player, null);
        if (hit == null) {
            return false;
        }
        if (!Cables.remove(hit.kind(), level, hit.pos())) {
            return false;
        }
        BlockPos cable = hit.pos();
        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level,
                cable.getX() + 0.5, cable.getY() + 0.3, cable.getZ() + 0.5, new ItemStack(hit.kind().item()));
        level.addFreshEntity(drop);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cable.getX() + 0.5, cable.getY() + 0.2, cable.getZ() + 0.5,
                12, 0.2, 0.1, 0.2, 0.2);
        level.playSound(null, cable, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0f, 1.2f);
        held.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        return true;
    }
}
