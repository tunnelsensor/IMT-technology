package com.senne.imttech.security.zone;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.os.EventLog;
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
                if (selection(stack).box() == null) {
                    // Geen hoeken gekozen (hoofdstuk 45): de Autoscan, vanuit de kamer voor het aangeklikte blok (of
                    // het gebouw zelf, als dat blok gebouwd is).
                    autoscan(level, player, context.getClickedPos().relative(context.getClickedFace()), context.getClickedPos());
                } else {
                    addSelection(level, player, stack);
                }
            } else {
                setCorner(player, stack, context.getClickedPos(), false);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /** Rechtsklik in de lucht: alleen met sluipen (de kubus toevoegen, of zonder hoeken de Autoscan van waar je staat). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (selection(stack).box() == null) {
                autoscan(serverLevel, serverPlayer, ZoneScan.roomStart(serverLevel, serverPlayer.blockPosition()), null);
            } else {
                addSelection(serverLevel, serverPlayer, stack);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * De Autoscan met de Zone Marker (hoofdstuk 45, jouw keuze: "sneak + klik met de Zone Marker scant vanaf die kamer").
     * {@code start} is de kamer (lucht) waar we beginnen; {@code clicked} het aangeklikte blok (of null in de lucht): is
     * de kamer niet dicht, dan proberen we vanaf dat blok het gebouw. De kubussen gaan naar je dichtstbijzijnde kern.
     */
    public static void autoscan(ServerLevel level, ServerPlayer player, BlockPos start, @Nullable BlockPos clicked) {
        // Een ingehouden knop klikt elke 4 ticks opnieuw: alleen een nieuwe klik telt (hoofdstuk 116), anders zou een
        // te lange klik meteen "ja, neem de grot erbij" zeggen.
        long now = level.getGameTime();
        Long previous = LAST_USE.put(player.getUUID(), now);
        if (previous != null && now - previous < NEW_CLICK && now >= previous) {
            return;
        }
        SecurityCoreBlockEntity core = ownCoreFor(level, player, start);
        if (core == null) {
            return;
        }
        ZoneBox limit = ZoneBox.of(core.zoneLow(), core.zoneHigh());
        // Hoofdstuk 116 (jouw wens: "bevestigen dat cave er bij moet"): kijk je naar de ping (of klik je ernaast) en doe je
        // opnieuw sluipen + rechtsklik, dan neem je de grot erbij, vanaf waar de vorige scan begon. Elke andere klik is
        // gewoon een nieuwe scan.
        ZoneScanReports.Report offer = ZoneScanReports.offer(player, core);
        boolean include = offer != null && now - offer.made() >= CONFIRM_DELAY && aimsAt(player, start, offer);
        if (!ZoneScanReports.mayScan(player, include)) {
            return;
        }
        BlockPos from = start;
        if (include) {
            ZoneScanReports.takeOffer(player, core);
            from = offer.start();
        }
        ZoneScan.Result found = ZoneScan.scan(level, from, limit, ImtConfig.Security.ZONE_MAX_BOXES, include);
        if (!include && !found.ok() && found.offer() == null && clicked != null && ZoneScan.isBuilt(level.getBlockState(clicked))) {
            // De kamer is niet dicht (open naar buiten, te ver, niet geladen): dan het gebouw vanaf het aangeklikte blok,
            // met de ping van waar de kamer openlag.
            found = ZoneScan.scan(level, clicked, limit, ImtConfig.Security.ZONE_MAX_BOXES).after(found);
        }
        ZoneScanReports.report(player, core, from, found);
        if (!found.ok()) {
            if (found.offer() != null) {
                tell(player, Component.literal(found.marks().isEmpty()
                        ? "Rooms too big: sneak + right-click here again to scan bigger"
                        : "Open to a cave: look at the ping and sneak + right-click to include it").withStyle(ChatFormatting.GOLD));
                player.playNotifySound(ModSounds.IMT_NOTIFY.get(), SoundSource.PLAYERS, 0.6f, 0.8f);
            } else {
                tell(player, Component.literal("Autoscan: " + found.problem()), true);
            }
            return;
        }
        List<ZoneBox> before = List.copyOf(core.zoneBoxes());
        ZoneScan.Applied applied = ZoneScan.apply(core, found, ImtConfig.Security.ZONE_MAX_BOXES);
        if (applied.changed()) {
            ZoneScanReports.remember(player, core, before);
        }
        // Een doos om het gebouw, maar de kamer liep door in een grot: dat zeggen we erbij (de ping zegt hoe je hem erbij neemt).
        String said = found.offer() != null ? applied.summary() + " · open to a cave: see the ping" : applied.shortText();
        tell(player, Component.literal(said), false);
        player.playNotifySound(ModSounds.IMT_NOTIFY.get(), SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    /** Wanneer (speltijd) een speler de marker voor het laatst gebruikte (ook een klik die niets deed). */
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();
    /** Zoveel ticks moeten er tussen twee klikken zitten om als een nieuwe klik te tellen (ingehouden: om de 4). */
    private static final int NEW_CLICK = 6;
    /** Pas zo lang (ticks) na het aanbod telt een klik als "ja": een snelle dubbelklik is geen keuze. */
    private static final int CONFIRM_DELAY = 20;
    /** Zo dicht (blokken) bij een ping telt een klik als een klik op die ping, en zo ver mag je hem nog aankijken. */
    private static final double NEAR_PING = 3.0;
    private static final double LOOK_RANGE = 64.0;
    /** Kijk je zo recht naar een ping (cosinus van de hoek, hier tien graden), dan kijk je ernaar. */
    private static final double LOOK_COS = 0.985;

    /**
     * Bedoelt deze klik de ping: kijk je er (door muren heen) naar, of klik je er vlak naast? Zonder pings (je kamers zijn
     * gewoon heel groot): een klik vlak bij waar de vorige scan begon.
     */
    private static boolean aimsAt(ServerPlayer player, BlockPos start, ZoneScanReports.Report offer) {
        if (offer.marks().isEmpty()) {
            return start.closerThan(offer.start(), NEAR_PING);
        }
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0f);
        for (ZoneScan.Mark mark : offer.marks()) {
            net.minecraft.world.phys.Vec3 centre = net.minecraft.world.phys.Vec3.atCenterOf(mark.pos());
            if (centre.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(start)) <= NEAR_PING * NEAR_PING) {
                return true;
            }
            net.minecraft.world.phys.Vec3 toward = centre.subtract(eye);
            double distance = toward.length();
            if (distance > 0.01 && distance <= LOOK_RANGE && toward.scale(1.0 / distance).dot(look) >= LOOK_COS) {
                return true;
            }
        }
        return false;
    }

    /** Met de marker breek je niets (ook niet in creative). */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.zone_marker.corners").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.zone_marker.add").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.zone_marker.remove").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.zone_marker.scan").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.zone_marker.cave").withStyle(ChatFormatting.GRAY));
        ZoneBox box = selection(stack).box();
        if (box != null) {
            tooltip.add(Component.translatable("tooltip.imttech.zone_marker.selection", box.size(), box.volume())
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
                ? Component.translatable("zone.imttech.marker.corner", first ? 1 : 2, pos.getX(), pos.getY(), pos.getZ())
                : Component.translatable("zone.imttech.marker.size", first ? 1 : 2, box.size(), box.volume());
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
            tell(player, Component.translatable("zone.imttech.marker.no_selection"), true);
            return;
        }
        BlockPos middle = new BlockPos((box.min().getX() + box.max().getX()) >> 1, (box.min().getY() + box.max().getY()) >> 1,
                (box.min().getZ() + box.max().getZ()) >> 1);
        SecurityCoreBlockEntity core = ownCoreFor(level, player, middle);
        if (core == null) {
            return;
        }
        List<ZoneBox> boxes = new ArrayList<>(core.zoneBoxes());
        if (boxes.size() >= ImtConfig.Security.ZONE_MAX_BOXES) {
            tell(player, Component.translatable("zone.imttech.marker.full", ImtConfig.Security.ZONE_MAX_BOXES), true);
            return;
        }
        ZoneBox fittedBox = box.clamp(core.zoneLow(), core.zoneHigh());
        if (fittedBox == null) {
            tell(player, Component.translatable("zone.imttech.marker.no_core", Math.round(ImtConfig.Security.ZONE_REACH)), true);
            return;
        }
        if (core.zoneBoxBlocked(fittedBox)) {
            tell(player, Component.translatable("zone.imttech.marker.blocked"), true);
            return;
        }
        boxes.add(fittedBox);
        List<ZoneBox> fitted = core.setZoneBoxes(boxes);
        ZoneBox added = fitted.getLast();
        core.logEvent(EventLog.Type.ZONE, "Zone: box " + fitted.size() + " added (" + added.size() + ")");
        tell(player, added.equals(box)
                ? Component.translatable("zone.imttech.marker.added", fitted.size(), added.size(), added.volume())
                : Component.translatable("zone.imttech.marker.added_cut", fitted.size(), Math.round(ImtConfig.Security.ZONE_REACH),
                        added.size(), added.volume()), false);
        stack.set(ModDataComponents.ZONE_SELECTION.get(), ZoneSelection.EMPTY);
        player.playNotifySound(ModSounds.IMT_NOTIFY.get(), SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    /**
     * Sluipen + linksklik op een blok in een kubus van je zone: de eerste klik vraagt het, een tweede
     * klik binnen drie seconden haalt hem weg. Liggen er twee kubussen over dit blok, dan de laatste.
     */
    static void removeBoxAt(ServerLevel level, ServerPlayer player, BlockPos pos) {
        // De zone waar dit blok in ligt (die kan tot ZONE_REACH van zijn kern liggen).
        ZoneData.Zone zone = ZoneData.get(level).at(level.dimension(), pos);
        if (zone == null) {
            tell(player, Component.translatable("zone.imttech.marker.not_in_box"), true);
            return;
        }
        if (!player.getUUID().equals(zone.owner())) {
            tell(player, Component.translatable("zone.imttech.marker.not_yours", zone.ownerName()), true);
            return;
        }
        if (com.senne.imttech.training.Training.isDrillTrainee(zone.core(), player.getUUID())) {
            tell(player, Component.literal("Not during a drill: you are the intruder here"), true);
            return;
        }
        if (!(level.getBlockEntity(zone.core().pos()) instanceof SecurityCoreBlockEntity core)) {
            tell(player, Component.translatable("zone.imttech.marker.not_in_box"), true);
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
            tell(player, Component.translatable("zone.imttech.marker.not_in_box"), true);
            return;
        }
        ZoneBox box = boxes.get(index);
        long now = level.getGameTime();
        PendingRemoval pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.box().equals(box) || !pending.core().equals(core.getBlockPos())
                || now - pending.time() > CONFIRM_TICKS) {
            PENDING.put(player.getUUID(), new PendingRemoval(core.getBlockPos(), box, now));
            tell(player, Component.translatable("zone.imttech.marker.remove_ask", index + 1, box.size()), false);
            return;
        }
        PENDING.remove(player.getUUID());
        boxes.remove(index);
        core.setZoneBoxes(boxes);
        core.logEvent(EventLog.Type.ZONE, "Zone: box " + (index + 1) + " removed (" + box.size() + ")");
        tell(player, Component.translatable("zone.imttech.marker.removed", index + 1), false);
        player.playNotifySound(ModSounds.IMT_NOTIFY.get(), SoundSource.PLAYERS, 0.6f, 0.9f);
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
                if (com.senne.imttech.training.Training.isDrillTrainee(level, corePos, player.getUUID())) {
                    // Tijdens een oefening (hoofdstuk 27) is hij hier een indringer: zijn zone verandert hij niet.
                    tell(player, Component.literal("Not during a drill: you are the intruder here"), true);
                    return null;
                }
                return core;
            }
        }
        SecurityCoreBlockEntity here = SecurityCores.coreAt(level, pos);
        if (here != null && here.ownerId() != null) {
            tell(player, Component.translatable("zone.imttech.marker.not_yours", here.ownerName()), true);
        } else {
            tell(player, Component.translatable("zone.imttech.marker.no_core", Math.round(ImtConfig.Security.ZONE_REACH)), true);
        }
        return null;
    }

    private static void tell(ServerPlayer player, Component message, boolean error) {
        player.displayClientMessage(message.copy().withStyle(error ? ChatFormatting.RED : ChatFormatting.AQUA), true);
    }

    /** Een melding in de kleur die hij al heeft (zoals het goudgele aanbod om de grot erbij te nemen). */
    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    /** Uitgelogd of de server stopt: niets meer onthouden (ook niet wat de laatste Autoscan zag, hoofdstuk 116). */
    static void forget(@Nullable UUID player) {
        if (player == null) {
            PENDING.clear();
            LAST_USE.clear();
        } else {
            PENDING.remove(player);
            LAST_USE.remove(player);
        }
        ZoneScanReports.forget(player);
    }
}
