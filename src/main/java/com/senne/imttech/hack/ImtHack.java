package com.senne.imttech.hack;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.network.HackMessagePayload;
import com.senne.imttech.network.ModNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * "Hack player": je kijkt een speler aan, hackt hem, en daarna zie je zijn PRIVÉBERICHTEN.
 *
 * Wat je onderschept: /msg, /tell, /w, /teammsg, /tm en de I.M.T-comms-berichten -
 * zowel wat hij stuurt ALS wat anderen naar hem sturen (zo kun je het gesprek volgen).
 *
 * Belangrijk (zoals afgesproken): je ziet een bericht alleen als je die speler op dat moment
 * ook echt kunt ZIEN. Kijk je weg, sta je te ver, of zit er een muur tussen? Dan mis je het.
 * De tekst komt in een ballonnetje boven zijn hoofd, alleen bij jou in beeld.
 *
 * De hack blijft actief tot je hem stopt (of tot de speler uitlogt) en kost energie per seconde.
 * Op een server kan de eigenaar dit uitzetten in de config (imttech.toml, [rules] hackAllowed).
 */
public final class ImtHack {
    private ImtHack() {}

    /** Wie hackt wie: de sleutel is de hacker, de waarde zijn zijn doelwitten. */
    private static final Map<UUID, Set<UUID>> HACKS = new HashMap<>();

    // ================================================================ Starten en stoppen

    /** Hackt deze speler dat doelwit? */
    public static boolean isHacking(ServerPlayer hacker, UUID target) {
        Set<UUID> targets = HACKS.get(hacker.getUUID());
        return targets != null && targets.contains(target);
    }

    /** De doelwitten van deze hacker (nooit null). */
    public static Set<UUID> targetsOf(ServerPlayer hacker) {
        return HACKS.getOrDefault(hacker.getUUID(), Set.of());
    }

    /**
     * Hackt het doelwit, of stopt de hack als die al liep.
     * Geeft terug of er nu gehackt wordt.
     */
    public static boolean toggle(ServerPlayer hacker, ServerPlayer target) {
        if (!ImtConfig.Rules.HACK_ALLOWED) {
            ImtServer.send(hacker, Component.translatable("imt.imttech.msg.hack_disabled"),
                    ImtMessagePayload.Level.WARNING);
            return false;
        }
        if (target == hacker) {
            return false;
        }
        Set<UUID> targets = HACKS.computeIfAbsent(hacker.getUUID(), id -> new LinkedHashSet<>());
        if (targets.remove(target.getUUID())) {
            ImtServer.send(hacker, Component.translatable("imt.imttech.msg.hack_stopped", target.getDisplayName()),
                    ImtMessagePayload.Level.INFO);
            return false;
        }
        if (targets.size() >= Math.round(ImtConfig.Hack.MAX_TARGETS)) {
            ImtServer.send(hacker, Component.translatable("imt.imttech.msg.hack_too_many", Math.round(ImtConfig.Hack.MAX_TARGETS)),
                    ImtMessagePayload.Level.WARNING);
            return false;
        }
        targets.add(target.getUUID());
        ImtServer.send(hacker, Component.translatable("imt.imttech.msg.hack_started", target.getDisplayName()),
                ImtMessagePayload.Level.INFO);
        ImtTech.LOGGER.info("[I.M.T] {} hackt nu {}", hacker.getGameProfile().getName(),
                target.getGameProfile().getName());
        return true;
    }

    /** Stopt alle hacks van deze speler (bv. als zijn bril leeg is). */
    public static void stopAll(ServerPlayer hacker, boolean tell) {
        Set<UUID> targets = HACKS.remove(hacker.getUUID());
        if (tell && targets != null && !targets.isEmpty()) {
            ImtServer.send(hacker, Component.translatable("imt.imttech.msg.hack_all_stopped"),
                    ImtMessagePayload.Level.INFO);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        HACKS.remove(id);
        // Ook uit de lijstjes van anderen halen.
        HACKS.values().forEach(targets -> targets.remove(id));
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        HACKS.clear();
    }

    /** Elke seconde: energie voor elke gehackte speler, en hacks stoppen die niet meer kunnen. */
    public static void tick(ServerPlayer hacker, ItemStack glasses) {
        Set<UUID> targets = HACKS.get(hacker.getUUID());
        if (targets == null || targets.isEmpty()) {
            return;
        }
        double cost = ImtConfig.Hack.DRAIN * targets.size();
        if (!ImtData.tryUseEnergy(glasses, cost)) {
            stopAll(hacker, true);
        }
    }

    // ================================================================ Berichten onderscheppen

    /**
     * Wordt aangeroepen voordat een commando uitgevoerd wordt. Zo zien we /msg, /tell, /w,
     * /teammsg en /tm langskomen.
     */
    public static void onCommand(CommandEvent event) {
        if (HACKS.isEmpty()) {
            return; // niemand hackt: niets te doen
        }
        ServerPlayer sender = event.getParseResults().getContext().getSource().getPlayer();
        if (sender == null) {
            return; // van de console
        }
        String input = event.getParseResults().getReader().getString().trim();
        if (input.startsWith("/")) {
            input = input.substring(1);
        }
        String[] parts = input.split("\\s+", 3);
        String command = parts[0].toLowerCase(Locale.ROOT);
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return;
        }

        if ((command.equals("msg") || command.equals("tell") || command.equals("w")) && parts.length >= 3) {
            // "/msg <speler> <bericht>"
            List<ServerPlayer> recipients = new ArrayList<>();
            ServerPlayer named = server.getPlayerList().getPlayerByName(parts[1]);
            if (named != null) {
                recipients.add(named);
            }
            relay(sender, recipients, parts[2]);
        } else if ((command.equals("teammsg") || command.equals("tm")) && parts.length >= 2) {
            // "/teammsg <bericht>": naar je hele team
            String message = input.substring(command.length()).trim();
            List<ServerPlayer> recipients = new ArrayList<>();
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (other != sender && sender.getTeam() != null && sender.getTeam() == other.getTeam()) {
                    recipients.add(other);
                }
            }
            relay(sender, recipients, message);
        }
    }

    /**
     * Een privébericht is verstuurd: stuur het door naar iedereen die de zender of een
     * ontvanger hackt EN die persoon op dit moment kan zien.
     *
     * Wordt ook gebruikt door de I.M.T-comms (zie ImtComms).
     */
    public static void relay(ServerPlayer sender, List<ServerPlayer> recipients, String text) {
        if (HACKS.isEmpty() || text.isBlank()) {
            return;
        }
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return;
        }
        String clean = text.length() > Math.round(ImtConfig.Comms.MAX_MESSAGE_LENGTH)
                ? text.substring(0, Math.round(ImtConfig.Comms.MAX_MESSAGE_LENGTH))
                : text;

        for (Map.Entry<UUID, Set<UUID>> entry : HACKS.entrySet()) {
            ServerPlayer hacker = server.getPlayerList().getPlayer(entry.getKey());
            if (hacker == null || !ImtData.isOperational(hacker)) {
                continue;
            }
            Set<UUID> targets = entry.getValue();
            // Wie van de gesprekspartners wordt door deze hacker gehackt?
            Set<ServerPlayer> seen = new HashSet<>();
            if (targets.contains(sender.getUUID())) {
                seen.add(sender);
            }
            for (ServerPlayer recipient : recipients) {
                if (targets.contains(recipient.getUUID())) {
                    seen.add(recipient);
                }
            }
            for (ServerPlayer watched : seen) {
                if (!canSee(hacker, watched)) {
                    continue; // je kijkt niet naar hem: dan mis je dit bericht
                }
                boolean outgoing = watched == sender;
                Component line = outgoing
                        ? Component.translatable("imt.imttech.hack.outgoing", names(recipients), clean)
                        : Component.translatable("imt.imttech.hack.incoming", sender.getDisplayName(), clean);
                ModNetwork.sendTo(hacker, new HackMessagePayload(watched.getId(), line, outgoing));
            }
        }
    }

    /** De namen van de ontvangers, of "?" als we die niet kennen (bv. bij een selector). */
    private static Component names(List<ServerPlayer> recipients) {
        if (recipients.isEmpty()) {
            return Component.literal("?");
        }
        Component result = recipients.getFirst().getDisplayName();
        for (int i = 1; i < recipients.size(); i++) {
            result = Component.empty().append(result).append(", ").append(recipients.get(i).getDisplayName());
        }
        return result;
    }

    /**
     * Kan de hacker deze speler op dit moment echt zien? Zelfde wereld, niet te ver, geen muur
     * ertussen, en hij moet ongeveer in de kijkrichting staan (dus je moet die kant op kijken).
     */
    private static boolean canSee(ServerPlayer hacker, ServerPlayer target) {
        if (hacker.level() != target.level()) {
            return false;
        }
        double range = ImtConfig.Hack.RANGE;
        if (hacker.distanceToSqr(target) > range * range) {
            return false;
        }
        if (!hacker.hasLineOfSight(target)) {
            return false;
        }
        // Staat hij ongeveer voor je? (1 = precies in het midden van je beeld, 0 = naast je)
        Vec3 look = hacker.getViewVector(1.0f).normalize();
        Vec3 toTarget = target.getEyePosition().subtract(hacker.getEyePosition()).normalize();
        return look.dot(toTarget) > ImtConfig.Hack.VIEW_DOT;
    }
}
