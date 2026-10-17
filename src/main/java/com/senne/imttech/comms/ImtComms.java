package com.senne.imttech.comms;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.drone.ImtDrones;
import com.senne.imttech.drone.DroneEntity;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.hack.ImtHack;
import com.senne.imttech.network.CommsPayload;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.network.PingMarkerPayload;
import com.senne.imttech.network.PingPayload;
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
 * I.M.T-comms: berichten en pings tussen spelers die allemaal een I.M.T-bril dragen.
 *
 * Regels:
 * - Je moet zelf een bril dragen waarvan je de eigenaar bent; de ontvangers ook.
 * - Zit je in een team, dan gaan berichten "aan iedereen" en pings alleen naar je teamgenoten.
 * - Berichten worden ook in het serverlogboek gezet, zodat beheerders kunnen meelezen.
 */
public final class ImtComms {
    private ImtComms() {}

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
        if (ImtServer.authorizedGlasses(sender) == null || !checkRate(sender, NEXT_MESSAGE_TICK, Math.round(ImtConfig.Comms.MESSAGE_COOLDOWN))) {
            return;
        }
        String text = clean(payload.message(), Math.round(ImtConfig.Comms.MAX_MESSAGE_LENGTH));
        if (text.isEmpty()) {
            return;
        }
        String senderName = sender.getGameProfile().getName();
        Component body = Component.literal(text);

        if (payload.recipient().isEmpty()) {
            // Aan iedereen met I.M.T (of alleen je team).
            List<ServerPlayer> recipients = imtUsers(sender).stream().filter(p -> p != sender && sameSide(sender, p)).toList();
            if (recipients.isEmpty()) {
                ImtServer.send(sender, Component.translatable("imt.imttech.msg.no_recipients"), ImtMessagePayload.Level.WARNING);
                return;
            }
            Component message = Component.translatable("imt.imttech.comms.broadcast", senderName, body);
            recipients.forEach(p -> ImtServer.send(p, message, ImtMessagePayload.Level.COMMS));
            // Wie gehackt wordt, verraadt ook zijn I.M.T-berichten (fase 10).
            ImtHack.relay(sender, recipients, text);
            ImtServer.send(sender, Component.translatable("imt.imttech.comms.sent_all", recipients.size(), body), ImtMessagePayload.Level.COMMS);
            ImtTech.LOGGER.info("[I.M.T comms] {} -> everyone ({}): {}", senderName, recipients.size(), text);
        } else {
            // Aan één speler.
            ServerPlayer target = sender.level().getServer().getPlayerList().getPlayerByName(payload.recipient());
            if (target == null || target == sender || !isImtUser(target)) {
                ImtServer.send(sender, Component.translatable("imt.imttech.msg.recipient_unavailable", payload.recipient()),
                        ImtMessagePayload.Level.WARNING);
                return;
            }
            ImtHack.relay(sender, List.of(target), text);
            ImtServer.send(target, Component.translatable("imt.imttech.comms.direct", senderName, body), ImtMessagePayload.Level.COMMS);
            ImtServer.send(sender, Component.translatable("imt.imttech.comms.sent_to", target.getGameProfile().getName(), body),
                    ImtMessagePayload.Level.COMMS);
            ImtTech.LOGGER.info("[I.M.T comms] {} -> {}: {}", senderName, target.getGameProfile().getName(), text);
        }
    }

    // ---------------------------------------------------------------- Pings

    public static void handlePing(ServerPlayer sender, PingPayload payload) {
        if (ImtServer.authorizedGlasses(sender) == null || !checkRate(sender, NEXT_PING_TICK, Math.round(ImtConfig.Comms.PING_COOLDOWN))) {
            return;
        }
        Vec3 pos = payload.pos();
        // De ping moet in de buurt liggen van de speler, of van zijn verkenningsdrone.
        double max = ImtConfig.Comms.PING_PLACE_RANGE + 8;
        boolean nearPlayer = pos.distanceToSqr(sender.getEyePosition()) <= max * max;
        DroneEntity scout = ImtDrones.findScout(sender);
        boolean nearScout = scout != null && pos.distanceToSqr(scout.position()) <= max * max;
        if (!nearPlayer && !nearScout) {
            return;
        }
        LAST_PING.put(sender.getUUID(), pos);
        PingMarkerPayload marker = new PingMarkerPayload(sender.getGameProfile().getName(), pos);
        double range = ImtConfig.Comms.PING_RANGE;
        for (ServerPlayer player : imtUsers(sender)) {
            if (player.level() == sender.level() && player.distanceToSqr(pos) <= range * range && sameSide(sender, player)) {
                ModNetwork.sendTo(player, marker);
            }
        }
    }

    // ---------------------------------------------------------------- Hulpjes

    /** Iedereen op de server die een I.M.T-bril draagt waarvan hij de eigenaar is. */
    private static List<ServerPlayer> imtUsers(ServerPlayer anyPlayer) {
        return anyPlayer.level().getServer().getPlayerList().getPlayers().stream().filter(ImtComms::isImtUser).toList();
    }

    private static boolean isImtUser(ServerPlayer player) {
        ItemStack glasses = ImtData.getWornGlasses(player);
        return !glasses.isEmpty() && ImtData.isAuthorized(glasses, player);
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
