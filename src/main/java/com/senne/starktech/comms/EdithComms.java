package com.senne.starktech.comms;

import com.senne.starktech.StarkTech;
import com.senne.starktech.drone.EdithDrones;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.hack.EdithHack;
import com.senne.starktech.network.CommsPayload;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.network.PingMarkerPayload;
import com.senne.starktech.network.PingPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

/**
 * E.D.I.T.H.-comms: berichten en pings tussen spelers die allemaal een E.D.I.T.H.-bril dragen.
 *
 * Regels:
 * - Je moet zelf een bril dragen waarvan je de eigenaar bent; de ontvangers ook.
 * - Zit je in een team, dan gaan berichten "aan iedereen" en pings alleen naar je teamgenoten.
 * - Berichten worden ook in het serverlogboek gezet, zodat beheerders kunnen meelezen.
 */
public final class EdithComms {
    private EdithComms() {}

    private static final Map<UUID, Long> NEXT_MESSAGE_TICK = new HashMap<>();
    private static final Map<UUID, Long> NEXT_PING_TICK = new HashMap<>();
    /** Waar elke speler zijn laatste ping zette. De satelliet kan daarop richten. */
    private static final Map<UUID, Vec3> LAST_PING = new HashMap<>();

    /** De laatste ping van deze speler, of null. */
    public static @Nullable Vec3 lastPing(ServerPlayer player) {
        return LAST_PING.get(player.getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        NEXT_MESSAGE_TICK.clear();
        NEXT_PING_TICK.clear();
        LAST_PING.clear();
    }

    // ---------------------------------------------------------------- Berichten

    public static void handleMessage(ServerPlayer sender, CommsPayload payload) {
        if (EdithServer.authorizedGlasses(sender) == null || !checkRate(sender, NEXT_MESSAGE_TICK, EdithBalance.MESSAGE_COOLDOWN)) {
            return;
        }
        String text = clean(payload.message(), EdithBalance.MAX_MESSAGE_LENGTH);
        if (text.isEmpty()) {
            return;
        }
        String senderName = sender.getGameProfile().getName();
        Component body = Component.literal(text);

        if (payload.recipient().isEmpty()) {
            // Aan iedereen met E.D.I.T.H. (of alleen je team).
            List<ServerPlayer> recipients = edithUsers(sender).stream().filter(p -> p != sender && sameSide(sender, p)).toList();
            if (recipients.isEmpty()) {
                EdithServer.send(sender, Component.translatable("edith.starktech.msg.no_recipients"), EdithMessagePayload.Level.WARNING);
                return;
            }
            Component message = Component.translatable("edith.starktech.comms.broadcast", senderName, body);
            recipients.forEach(p -> EdithServer.send(p, message, EdithMessagePayload.Level.COMMS));
            // Wie gehackt wordt, verraadt ook zijn E.D.I.T.H.-berichten (fase 10).
            EdithHack.relay(sender, recipients, text);
            EdithServer.send(sender, Component.translatable("edith.starktech.comms.sent_all", recipients.size(), body), EdithMessagePayload.Level.COMMS);
            StarkTech.LOGGER.info("[E.D.I.T.H. comms] {} -> iedereen ({}): {}", senderName, recipients.size(), text);
        } else {
            // Aan één speler.
            ServerPlayer target = sender.level().getServer().getPlayerList().getPlayerByName(payload.recipient());
            if (target == null || target == sender || !isEdithUser(target)) {
                EdithServer.send(sender, Component.translatable("edith.starktech.msg.recipient_unavailable", payload.recipient()),
                        EdithMessagePayload.Level.WARNING);
                return;
            }
            EdithHack.relay(sender, List.of(target), text);
            EdithServer.send(target, Component.translatable("edith.starktech.comms.direct", senderName, body), EdithMessagePayload.Level.COMMS);
            EdithServer.send(sender, Component.translatable("edith.starktech.comms.sent_to", target.getGameProfile().getName(), body),
                    EdithMessagePayload.Level.COMMS);
            StarkTech.LOGGER.info("[E.D.I.T.H. comms] {} -> {}: {}", senderName, target.getGameProfile().getName(), text);
        }
    }

    // ---------------------------------------------------------------- Pings

    public static void handlePing(ServerPlayer sender, PingPayload payload) {
        if (EdithServer.authorizedGlasses(sender) == null || !checkRate(sender, NEXT_PING_TICK, EdithBalance.PING_COOLDOWN)) {
            return;
        }
        Vec3 pos = payload.pos();
        // De ping moet in de buurt liggen van de speler, of van zijn verkenningsdrone.
        double max = EdithBalance.PING_PLACE_RANGE + 8;
        boolean nearPlayer = pos.distanceToSqr(sender.getEyePosition()) <= max * max;
        StarkDroneEntity scout = EdithDrones.findScout(sender);
        boolean nearScout = scout != null && pos.distanceToSqr(scout.position()) <= max * max;
        if (!nearPlayer && !nearScout) {
            return;
        }
        LAST_PING.put(sender.getUUID(), pos);
        PingMarkerPayload marker = new PingMarkerPayload(sender.getGameProfile().getName(), pos);
        double range = EdithBalance.PING_RANGE;
        for (ServerPlayer player : edithUsers(sender)) {
            if (player.level() == sender.level() && player.distanceToSqr(pos) <= range * range && sameSide(sender, player)) {
                ModNetwork.sendTo(player, marker);
            }
        }
    }

    // ---------------------------------------------------------------- Hulpjes

    /** Iedereen op de server die een E.D.I.T.H.-bril draagt waarvan hij de eigenaar is. */
    private static List<ServerPlayer> edithUsers(ServerPlayer anyPlayer) {
        return anyPlayer.level().getServer().getPlayerList().getPlayers().stream().filter(EdithComms::isEdithUser).toList();
    }

    private static boolean isEdithUser(ServerPlayer player) {
        ItemStack glasses = EdithData.getWornGlasses(player);
        return !glasses.isEmpty() && EdithData.isAuthorized(glasses, player);
    }

    /** Zit je in een team, dan alleen je teamgenoten; anders iedereen. */
    private static boolean sameSide(ServerPlayer sender, ServerPlayer other) {
        return sender == other || sender.getTeam() == null || sender.isAlliedTo(other);
    }

    /** Anti-spam: niet te vaak achter elkaar. */
    private static boolean checkRate(ServerPlayer player, Map<UUID, Long> nextTick, int cooldown) {
        long now = player.level().getGameTime();
        Long allowedAt = nextTick.get(player.getUUID());
        if (allowedAt != null && now < allowedAt && now >= allowedAt - cooldown) {
            return false;
        }
        nextTick.put(player.getUUID(), now + cooldown);
        return true;
    }

    /** Haalt opmaakcodes en onzichtbare tekens weg en knipt te lange tekst af. */
    private static String clean(String text, int maxLength) {
        // "§c" enz. zijn opmaakcodes (kleuren); die halen we er in hun geheel uit.
        String clean = text.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", "").trim();
        return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
    }
}
