package com.senne.starktech.security.os;

import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.network.OsActionPayload;
import com.senne.starktech.network.OsOpenPayload;
import com.senne.starktech.network.OsSnapshotPayload;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De Stark OS-desktop op een beveiligingskern (jouw wens, 28 september): een echte desktop met
 * vensters waar de EIGENAAR zijn beveiliging bekijkt en bedient. Hij opent door met een lege hand
 * op de kern te rechtsklikken.
 *
 * Alles draait op de server; het scherm van de speler tekent alleen wat de server stuurt (een
 * momentopname in een CompoundTag, zie {@link #snapshot}). Zo staat het echte wachtwoord of de
 * mappenstructuur nooit in het spel van iemand anders, en kan de server elke opdracht controleren.
 *
 * Deze klasse houdt bij wie welke desktop open heeft (de sessies), ververst de momentopname terwijl
 * het scherm openstaat, en sluit hem als je wegloopt, doodgaat of uitlogt. Hacken (binnenkomen
 * zonder eigenaar te zijn) gaat NIET via de desktop maar via de laptop; de desktop is voor wie de
 * beveiliging al bezit.
 */
public final class StarkOs {
    private StarkOs() {}

    /** Zo dicht bij de kern moet je blijven om zijn desktop open te houden (blokken). */
    public static final double REACH = 8.0;
    /** Zo vaak (ticks) stuurt de server een nieuwe momentopname naar wie ingelogd is. */
    private static final int SNAPSHOT_INTERVAL = 10;

    /** Eén open desktop: op welke kern, en of het via SSH op afstand is. */
    public record Session(BlockPos core, boolean remote) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    // ================================================================ Openen en sluiten

    /**
     * Rechtsklik met lege hand op de kern. Alleen de eigenaar komt op de desktop; een vertrouwde
     * speler niet, en wie de beveiliging niet bezit moet eerst hacken (met de laptop).
     */
    public static void open(ServerPlayer player, BlockPos corePos) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(corePos) instanceof SecurityCoreBlockEntity core)
                || player.distanceToSqr(corePos.getCenter()) > REACH * REACH) {
            return;
        }
        core.ensureSystem(level);
        if (core.ownerId() == null) {
            player.displayClientMessage(Component.translatable("os.starktech.msg.no_owner")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!player.getUUID().equals(core.ownerId())) {
            player.displayClientMessage(Component.translatable("os.starktech.msg.access_denied")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        openFor(player, core, false);
        level.playSound(null, corePos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4f, 1.8f);
    }

    /** Opent de desktop echt (gedeeld door de lokale weg hierboven en straks de SSH-weg op afstand). */
    public static void openFor(ServerPlayer player, SecurityCoreBlockEntity core, boolean remote) {
        SESSIONS.put(player.getUUID(), new Session(core.getBlockPos().immutable(), remote));
        ModNetwork.sendTo(player, new OsOpenPayload(core.getBlockPos(), true, remote));
        sendSnapshot(player, core, remote);
    }

    /** Sluit de desktop van deze speler (het scherm aan zijn kant gaat ook dicht). */
    public static void close(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            ModNetwork.sendTo(player, OsOpenPayload.close(session.core()));
        }
    }

    /** Heeft deze speler nu een desktop open? (Voor de tests.) */
    public static boolean hasDesktop(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    // ================================================================ Opdrachten van het scherm

    public static void handleAction(ServerPlayer player, OsActionPayload payload) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.core().equals(payload.pos())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(session.core()) instanceof SecurityCoreBlockEntity core)) {
            close(player);
            return;
        }
        switch (payload.op()) {
            case "close" -> close(player);
            case "passwd" -> passwd(player, core, payload.args().getString("pw"));
            default -> { } // onbekende opdracht: later uitgebreid (apparaten, mappen, camera's)
        }
    }

    /** Een nieuw wachtwoord zetten of wijzigen. Dat gooit meteen alle andere sessies van deze kern eruit. */
    private static void passwd(ServerPlayer player, SecurityCoreBlockEntity core, String newPassword) {
        String problem = SystemPassword.problem(newPassword);
        if (problem != null) {
            player.displayClientMessage(Component.translatable("os.starktech.msg.bad_password", problem)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        core.setPassword(newPassword, player.serverLevel().random);
        core.logEvent(EventLog.Type.SYSTEM, player.getGameProfile().getName() + " changed the password");
        player.displayClientMessage(Component.translatable("os.starktech.msg.password_set")
                .withStyle(ChatFormatting.GREEN), true);
        // Een nieuw wachtwoord verbreekt elke andere sessie op deze kern.
        kickOthers(player, core.getBlockPos());
        sendSnapshot(player, core, remote(player));
    }

    /** Gooit iedereen behalve {@code keep} van deze kern af (bij een nieuw wachtwoord of een overname). */
    public static void kickOthers(@Nullable ServerPlayer keep, BlockPos corePos) {
        if (keep == null || keep.getServer() == null) {
            return;
        }
        SESSIONS.entrySet().removeIf(entry -> {
            if (!entry.getValue().core().equals(corePos) || entry.getKey().equals(keep.getUUID())) {
                return false;
            }
            ServerPlayer other = keep.getServer().getPlayerList().getPlayer(entry.getKey());
            if (other != null) {
                ModNetwork.sendTo(other, OsOpenPayload.close(corePos));
            }
            return true;
        });
    }

    // ================================================================ De momentopname

    private static boolean remote(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.remote();
    }

    private static void sendSnapshot(ServerPlayer player, SecurityCoreBlockEntity core, boolean remote) {
        ModNetwork.sendTo(player, new OsSnapshotPayload(core.getBlockPos(), snapshot(core, player.serverLevel(), remote)));
    }

    /**
     * De hele desktop in één CompoundTag. Hier zit nu de status en het logboek in; latere functies
     * (apparaten, mappen, camera's) voegen er hun eigen stukjes aan toe.
     */
    public static CompoundTag snapshot(SecurityCoreBlockEntity core, ServerLevel level, boolean remote) {
        CompoundTag data = new CompoundTag();
        data.putString("host", core.hostname());
        data.putString("ip", core.ip());
        data.putInt("port", core.sshPort());
        data.putString("owner", core.ownerName());
        data.putBoolean("hasPassword", core.hasPassword());
        data.putBoolean("online", core.isOnline());
        data.putBoolean("remote", remote);
        // Het logboek, met per regel hoe lang geleden het was (in seconden).
        long now = level.getGameTime();
        ListTag log = new ListTag();
        for (EventLog.Entry entry : core.log().entries()) {
            CompoundTag line = new CompoundTag();
            line.putInt("ago", (int) Math.max(0, (now - entry.time()) / 20));
            line.putString("k", entry.type().name());
            line.putString("x", entry.text());
            log.add(line);
        }
        data.put("log", log);
        return data;
    }

    // ================================================================ Elke tick en opruimen

    /** Elke seconde: nog bij de kern (lokaal) en leeft de speler nog? En de momentopname verversen. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(session.core()) instanceof SecurityCoreBlockEntity core)
                || !player.isAlive()
                || !session.remote() && player.distanceToSqr(session.core().getCenter()) > REACH * REACH) {
            close(player);
            return;
        }
        if (level.getGameTime() % SNAPSHOT_INTERVAL == 0) {
            sendSnapshot(player, core, session.remote());
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }
}
