package com.senne.starktech.security.zone;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import com.senne.starktech.security.os.EventLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De Zone Marker (hoofdstuk 10): het gereedschap om de kubussen van je zone in de wereld aan te
 * klikken, "tot op elk blok" (jouw wens, 8 oktober).
 *
 * <ul>
 *   <li><b>Linksklik</b> op een blok: hoek 1. <b>Rechtsklik</b> op een blok: hoek 2. Je ziet een
 *       oranje doos tussen de twee, en onderaan hoe groot hij is ("12 x 6 x 20").</li>
 *   <li><b>Sluipen + rechtsklik</b> (op een blok of in de lucht): die doos wordt een kubus van je
 *       zone. Hij hoort bij je dichtstbijzijnde beveiligingskern tot ZONE_REACH (200) blokken ver
 *       (het gebied van die kern).</li>
 *   <li><b>Sluipen + linksklik</b> op een blok in een kubus van je zone: die kubus weghalen (twee
 *       keer klikken, zodat je het niet per ongeluk doet).</li>
 * </ul>
 *
 * Met de marker in je hand zie je je zone altijd, ook als "Zone border" uit staat, met een nummer
 * bij elke kubus. Fijn bijstellen (per blok, of de getallen typen) doe je in de app Zone op de desktop.
 *
 * Een linksklik gaat niet langs het gewone hakken (dat zou het blok beginnen te breken): je spel
 * vangt hem af en stuurt alleen "linksklik op dit blok" naar de server (zie client/zone/ZoneMarkerControl).
 */
public class ZoneMarkerItem extends Item {
    /** Zo ver mag het blok van je af zijn als je met de marker klikt (blokken). */
    private static final double REACH = 8.0;
    /** Zo lang (ticks) wacht de marker op de tweede klik om een kubus weg te halen. */
    private static final int CONFIRM_TICKS = 60;

    /** De kubus die iemand wil weghalen (na de eerste klik), en wanneer hij klikte. */
    private record PendingRemoval(BlockPos core, ZoneBox box, long time) {}

    private static final Map<UUID, PendingRemoval> PENDING = new HashMap<>();

    public ZoneMarkerItem(Properties properties) {
        super(properties);
    }

    /** Rechtsklik op een blok, vóór het blok zelf iets doet (zo opent een kist niet in plaats daarvan). */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player && context.getLevel() instanceof ServerLevel level) {
            if (player.isShiftKeyDown()) {
                addSelection(level, player, stack);
            } else {
                setCorner(player, stack, context.getClickedPos(), false);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /** Rechtsklik in de lucht: alleen met sluipen (de kubus toevoegen). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            addSelection(serverLevel, serverPlayer, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Met de marker breek je niets (ook niet in creative). */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starktech.zone_marker.corners").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.zone_marker.add").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.zone_marker.remove").withStyle(ChatFormatting.GRAY));
        ZoneBox box = selection(stack).box();
        if (box != null) {
            tooltip.add(Component.translatable("tooltip.starktech.zone_marker.selection", box.size(), box.volume())
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** Wat er op deze marker gekozen is (leeg als er nog niets is). */
    public static ZoneSelection selection(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ZONE_SELECTION.get(), ZoneSelection.EMPTY);
    }

    // ================================================================ Wat de klikken doen (op de server)

    /** Een linksklik op een blok, zoals je spel hem doorgeeft (zie ZoneMarkerClickPayload). */
    public static void leftClick(ServerPlayer player, BlockPos pos) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.ZONE_MARKER.get()) || player.distanceToSqr(pos.getCenter()) > REACH * REACH) {
            return;
        }
        if (player.isShiftKeyDown()) {
            removeBoxAt(player.serverLevel(), player, pos);
        } else {
            setCorner(player, stack, pos, true);
        }
    }

    /** Hoek 1 (linksklik) of hoek 2 (rechtsklik) zetten, en zeggen hoe groot de doos nu is. */
    static void setCorner(ServerPlayer player, ItemStack stack, BlockPos pos, boolean first) {
        ZoneSelection selection = selection(stack);
        selection = first ? selection.withFirst(pos) : selection.withSecond(pos);
        stack.set(ModDataComponents.ZONE_SELECTION.get(), selection);
        ZoneBox box = selection.box();
        Component message = box == null
                ? Component.translatable("zone.starktech.marker.corner", first ? 1 : 2, pos.getX(), pos.getY(), pos.getZ())
                : Component.translatable("zone.starktech.marker.size", first ? 1 : 2, box.size(), box.volume());
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.GOLD), true);
    }

    /**
     * De gekozen doos wordt een kubus van je zone (zie {@link #ownCoreFor} voor welke kern). Steekt
     * hij verder dan ZONE_REACH van de kern, dan valt dat stuk eraf. Raakt hij de zone of het gebied
     * van een andere kern, dan mag het niet.
     */
    static void addSelection(ServerLevel level, ServerPlayer player, ItemStack stack) {
        ZoneBox box = selection(stack).box();
        if (box == null) {
            tell(player, Component.translatable("zone.starktech.marker.no_selection"), true);
            return;
        }
        BlockPos middle = new BlockPos((box.min().getX() + box.max().getX()) >> 1, (box.min().getY() + box.max().getY()) >> 1,
                (box.min().getZ() + box.max().getZ()) >> 1);
        SecurityCoreBlockEntity core = ownCoreFor(level, player, middle);
        if (core == null) {
            return;
        }
        List<ZoneBox> boxes = new ArrayList<>(core.zoneBoxes());
        if (boxes.size() >= StarkConfig.Security.ZONE_MAX_BOXES) {
            tell(player, Component.translatable("zone.starktech.marker.full", StarkConfig.Security.ZONE_MAX_BOXES), true);
            return;
        }
        ZoneBox fittedBox = box.clamp(core.zoneLow(), core.zoneHigh());
        if (fittedBox == null) {
            tell(player, Component.translatable("zone.starktech.marker.no_core", StarkConfig.Security.ZONE_REACH), true);
            return;
        }
        if (core.zoneBoxBlocked(fittedBox)) {
            tell(player, Component.translatable("zone.starktech.marker.blocked"), true);
            return;
        }
        boxes.add(fittedBox);
        List<ZoneBox> fitted = core.setZoneBoxes(boxes);
        ZoneBox added = fitted.getLast();
        core.logEvent(EventLog.Type.ZONE, "Zone: box " + fitted.size() + " added (" + added.size() + ")");
        tell(player, added.equals(box)
                ? Component.translatable("zone.starktech.marker.added", fitted.size(), added.size(), added.volume())
                : Component.translatable("zone.starktech.marker.added_cut", fitted.size(), StarkConfig.Security.ZONE_REACH,
                        added.size(), added.volume()), false);
        stack.set(ModDataComponents.ZONE_SELECTION.get(), ZoneSelection.EMPTY);
        player.playNotifySound(ModSounds.EDITH_NOTIFY.get(), SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    /**
     * Sluipen + linksklik op een blok in een kubus van je zone: de eerste klik vraagt het, een tweede
     * klik binnen drie seconden haalt hem weg. Liggen er twee kubussen over dit blok, dan de laatste.
     */
    static void removeBoxAt(ServerLevel level, ServerPlayer player, BlockPos pos) {
        // De zone waar dit blok in ligt (die kan tot ZONE_REACH van zijn kern liggen).
        ZoneData.Zone zone = ZoneData.get(level).at(level.dimension(), pos);
        if (zone == null) {
            tell(player, Component.translatable("zone.starktech.marker.not_in_box"), true);
            return;
        }
        if (!player.getUUID().equals(zone.owner())) {
            tell(player, Component.translatable("zone.starktech.marker.not_yours", zone.ownerName()), true);
            return;
        }
        if (!(level.getBlockEntity(zone.core().pos()) instanceof SecurityCoreBlockEntity core)) {
            tell(player, Component.translatable("zone.starktech.marker.not_in_box"), true);
            return;
        }
        List<ZoneBox> boxes = new ArrayList<>(core.zoneBoxes());
        int index = -1;
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).contains(pos)) {
                index = i;
            }
        }
        if (index < 0) {
            tell(player, Component.translatable("zone.starktech.marker.not_in_box"), true);
            return;
        }
        ZoneBox box = boxes.get(index);
        long now = level.getGameTime();
        PendingRemoval pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.box().equals(box) || !pending.core().equals(core.getBlockPos())
                || now - pending.time() > CONFIRM_TICKS) {
            PENDING.put(player.getUUID(), new PendingRemoval(core.getBlockPos(), box, now));
            tell(player, Component.translatable("zone.starktech.marker.remove_ask", index + 1, box.size()), false);
            return;
        }
        PENDING.remove(player.getUUID());
        boxes.remove(index);
        core.setZoneBoxes(boxes);
        core.logEvent(EventLog.Type.ZONE, "Zone: box " + (index + 1) + " removed (" + box.size() + ")");
        tell(player, Component.translatable("zone.starktech.marker.removed", index + 1), false);
        player.playNotifySound(ModSounds.EDITH_NOTIFY.get(), SoundSource.PLAYERS, 0.6f, 0.9f);
    }

    /**
     * De beveiligingskern van deze speler die een kubus op deze plek krijgt: je dichtstbijzijnde kern
     * tot ZONE_REACH blokken ver. Die zoeken we in ZoneData, zodat hij ook gevonden wordt als zijn stuk
     * wereld niet geladen is. Geen eigen kern zo dichtbij: ligt de plek in het gebied van de kern van
     * een ander, dan zegt hij dat; anders dat er geen kern van jou in de buurt is (en geeft hij null).
     */
    private static @Nullable SecurityCoreBlockEntity ownCoreFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        List<BlockPos> mine = new ArrayList<>();
        for (ZoneData.Zone zone : ZoneData.get(level).owned(level.dimension())) {
            if (player.getUUID().equals(zone.owner()) && SecurityCores.inArea(zone.core().pos(), pos)) {
                mine.add(zone.core().pos());
            }
        }
        mine.sort(java.util.Comparator.comparingDouble(core -> core.distSqr(pos)));
        for (BlockPos corePos : mine) {
            // getBlockEntity laadt zijn stuk wereld als dat nodig is.
            if (level.getBlockEntity(corePos) instanceof SecurityCoreBlockEntity core && player.getUUID().equals(core.ownerId())) {
                return core;
            }
        }
        SecurityCoreBlockEntity here = SecurityCores.coreAt(level, pos);
        if (here != null && here.ownerId() != null) {
            tell(player, Component.translatable("zone.starktech.marker.not_yours", here.ownerName()), true);
        } else {
            tell(player, Component.translatable("zone.starktech.marker.no_core", StarkConfig.Security.ZONE_REACH), true);
        }
        return null;
    }

    private static void tell(ServerPlayer player, Component message, boolean error) {
        player.displayClientMessage(message.copy().withStyle(error ? ChatFormatting.RED : ChatFormatting.AQUA), true);
    }

    /** Uitgelogd of de server stopt: niets meer onthouden. */
    static void forget(@Nullable UUID player) {
        if (player == null) {
            PENDING.clear();
        } else {
            PENDING.remove(player);
        }
    }
}
