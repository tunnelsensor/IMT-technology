package com.senne.starktech.security.os;

import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.network.OsActionPayload;
import com.senne.starktech.network.OsOpenPayload;
import com.senne.starktech.network.OsSnapshotPayload;
import com.senne.starktech.security.SecurityBalance;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import com.senne.starktech.security.SecurityDroneEntity;
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
 * beveiliging al bezit (of straks het wachtwoord kent, via SSH).
 *
 * Sinds stap 5 ook de mappen en bestanden (de apps Files en Notes) en de VALSTRIKKEN: een bestand
 * dat de eigenaar als valstrik markeerde. Opent iemand anders dan de eigenaar het, dan gaat het
 * alarm af, wordt hij eruit gegooid en een tijdje buitengesloten, en jagen de drones op hem.
 */
public final class StarkOs {
    private StarkOs() {}

    /** Zo dicht bij de kern moet je blijven om zijn desktop open te houden (blokken). */
    public static final double REACH = 8.0;
    /** Zo vaak (ticks) stuurt de server een nieuwe momentopname naar wie ingelogd is. */
    private static final int SNAPSHOT_INTERVAL = 10;

    /**
     * Eén open desktop: op welke kern, of het via SSH op afstand is, en wat er nog naar het scherm
     * moet (een geopend bestand, een melding). Ook welke versie van de mappen het scherm al heeft,
     * zodat de server de lijst alleen opnieuw stuurt als er iets veranderde.
     */
    public static final class Session {
        private final BlockPos core;
        private final boolean remote;
        /** Het bestand dat net geopend is en nog (één keer) naar het scherm moet. */
        private @Nullable String fileToSend;
        /** Een melding die nog naar het scherm moet (het actiebalk-bericht zie je niet achter de desktop). */
        private @Nullable String notice;
        private boolean noticeError;
        /** Welke mappen (en welke versie ervan) het scherm al kreeg. */
        private @Nullable VirtualFs sentFs;
        private int sentRevision = -1;

        Session(BlockPos core, boolean remote) {
            this.core = core;
            this.remote = remote;
        }

        public BlockPos core() {
            return core;
        }

        public boolean remote() {
            return remote;
        }
    }

    /** Wie op welk systeem buitengesloten is (na een valstrik), en tot wanneer (speltijd). */
    private record Lockout(BlockPos core, long until) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Lockout> LOCKOUTS = new HashMap<>();

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
        if (!isOwner(player, core)) {
            player.displayClientMessage(Component.translatable("os.starktech.msg.access_denied")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (openFor(player, core, false)) {
            level.playSound(null, corePos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4f, 1.8f);
        }
    }

    /**
     * Opent de desktop echt (gedeeld door de lokale weg hierboven en straks de SSH-weg op afstand).
     * Geeft false als deze speler hier nog buitengesloten is (na een valstrik).
     */
    public static boolean openFor(ServerPlayer player, SecurityCoreBlockEntity core, boolean remote) {
        int locked = lockedSeconds(player, core);
        if (locked > 0) {
            player.displayClientMessage(Component.translatable("os.starktech.msg.locked_out", locked)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        Session session = new Session(core.getBlockPos().immutable(), remote);
        SESSIONS.put(player.getUUID(), session);
        ModNetwork.sendTo(player, new OsOpenPayload(core.getBlockPos(), true, remote));
        sendSnapshot(player, core, session);
        return true;
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

    /** Is deze speler de eigenaar van de kern? (Alleen die ziet en zet valstrikken.) */
    private static boolean isOwner(ServerPlayer player, SecurityCoreBlockEntity core) {
        return player.getUUID().equals(core.ownerId());
    }

    /** Hoeveel seconden deze speler nog buitengesloten is op deze kern (0 = niet). */
    public static int lockedSeconds(ServerPlayer player, SecurityCoreBlockEntity core) {
        Lockout lockout = LOCKOUTS.get(player.getUUID());
        if (lockout == null || !lockout.core().equals(core.getBlockPos())) {
            return 0;
        }
        long left = lockout.until() - player.serverLevel().getGameTime();
        if (left <= 0) {
            LOCKOUTS.remove(player.getUUID());
            return 0;
        }
        return (int) Math.ceil(left / 20.0);
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
        CompoundTag args = payload.args();
        switch (payload.op()) {
            case "close" -> close(player);
            case "passwd" -> passwd(player, core, session, args.getString("pw"));
            case "fs_open" -> openFile(player, core, session, path(args, "path"));
            case "fs_mkdir" -> fileResult(player, core, session, core.fs().mkdir(path(args, "path")));
            case "fs_touch" -> fileResult(player, core, session, touch(core.fs(), path(args, "path")));
            case "fs_save" -> saveFile(player, core, session, path(args, "path"), args.getString("text"));
            case "fs_move" -> fileResult(player, core, session, core.fs().move(path(args, "from"), path(args, "to")));
            case "fs_delete" -> fileResult(player, core, session, core.fs().delete(path(args, "path")));
            case "fs_trap" -> setTrap(player, core, session, path(args, "path"), args.getBoolean("on"));
            case "flag" -> setFlag(player, core, session, args.getString("name"), args.getBoolean("on"));
            case "dev_toggle" -> toggleTurret(player, core, session,
                    new BlockPos(args.getInt("x"), args.getInt("y"), args.getInt("z")), args.getBoolean("on"));
            case "cam_aim" -> aimCamera(player, core, session,
                    new BlockPos(args.getInt("x"), args.getInt("y"), args.getInt("z")), args.getFloat("pan"), args.getFloat("tilt"));
            case "cam_rename" -> renameCamera(player, core, session,
                    new BlockPos(args.getInt("x"), args.getInt("y"), args.getInt("z")), args.getString("name"));
            case "trust_add" -> trust(player, core, session, args.getString("name").strip());
            case "trust_remove" -> untrust(player, core, session, args.getString("name").strip());
            default -> { } // onbekende opdracht: later uitgebreid (camera's)
        }
    }

    // ================================================================ Apparaten en toegang

    /** Zoveel spelers kan een eigenaar hooguit vertrouwen. */
    public static final int MAX_TRUSTED = 32;

    /**
     * Een schakelaar van het hele netwerk (de apps Access en Devices): monsters aanvallen, andere
     * spelers aanvallen, de drones, de laserwanden.
     */
    private static void setFlag(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String name, boolean on) {
        String text;
        switch (name) {
            case "mobs" -> {
                core.setAttackMobs(on);
                text = on ? "Security attacks monsters" : "Security ignores monsters";
            }
            case "players" -> {
                core.setAttackPlayers(on);
                text = on ? "Security attacks other players" : "Security ignores other players";
            }
            case "drones" -> {
                core.setDronesEnabled(on);
                text = on ? "Drones on" : "Drones landing";
            }
            case "lasers" -> {
                com.senne.starktech.security.SecurityDevices.setLasers(player.serverLevel(), core, on);
                text = on ? "Laser walls on" : "Laser walls off";
            }
            case "cameras" -> {
                com.senne.starktech.security.SecurityDevices.setCameras(player.serverLevel(), core, on);
                text = on ? "Cameras on" : "Cameras off";
            }
            default -> {
                return;
            }
        }
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + ": " + text.toLowerCase(java.util.Locale.ROOT));
        notify(player, core, session, text, false);
    }

    /** Een camera een stukje draaien of kantelen (de pijltjes bij het grote camerabeeld). */
    private static void aimCamera(ServerPlayer player, SecurityCoreBlockEntity core, Session session, BlockPos pos,
                                  float dPan, float dTilt) {
        String error = com.senne.starktech.security.SecurityDevices.aimCamera(player.serverLevel(), core, pos, dPan, dTilt);
        if (error != null) {
            notify(player, core, session, error, true);
            return;
        }
        sendSnapshot(player, core, session);
    }

    /** Een camera een andere naam geven (de knop Rename bij het grote camerabeeld). Leeg = weer "Camera 3". */
    private static void renameCamera(ServerPlayer player, SecurityCoreBlockEntity core, Session session, BlockPos pos, String name) {
        String clean = com.senne.starktech.security.CameraBlockEntity.cleanName(name);
        String error = com.senne.starktech.security.SecurityDevices.renameCamera(player.serverLevel(), core, pos, clean);
        if (error != null) {
            notify(player, core, session, error, true);
            return;
        }
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " renamed the camera at "
                + pos.toShortString() + (clean.isEmpty() ? " back to its number" : " to \"" + clean + "\""));
        notify(player, core, session, clean.isEmpty() ? "Camera name cleared" : "Camera renamed to " + clean, false);
    }

    /** Eén torentje aan of uit. */
    private static void toggleTurret(ServerPlayer player, SecurityCoreBlockEntity core, Session session, BlockPos pos, boolean on) {
        String error = com.senne.starktech.security.SecurityDevices.switchTurret(player.serverLevel(), core, pos, on);
        if (error != null) {
            notify(player, core, session, error, true);
            return;
        }
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " switched the turret at "
                + pos.toShortString() + (on ? " on" : " off"));
        notify(player, core, session, "Turret " + (on ? "on" : "off"), false);
    }

    /**
     * Een speler vertrouwen (op naam): de beveiliging schiet niet meer op hem. Hij moet wel al eens
     * op deze server geweest zijn, anders weten we niet wie het is.
     */
    private static void trust(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String name) {
        if (name.isEmpty()) {
            return;
        }
        if (core.trustedNames().size() >= MAX_TRUSTED) {
            notify(player, core, session, "The trusted list is full (max " + MAX_TRUSTED + ")", true);
            return;
        }
        com.mojang.authlib.GameProfile profile = findProfile(player, name);
        if (profile == null) {
            notify(player, core, session, "No player called " + name + " has been on this server", true);
            return;
        }
        if (profile.getId().equals(core.ownerId())) {
            notify(player, core, session, profile.getName() + " owns this system", true);
            return;
        }
        if (core.isTrusted(profile.getId())) {
            notify(player, core, session, profile.getName() + " is already trusted", true);
            return;
        }
        core.addTrusted(profile.getId(), profile.getName());
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " trusted " + profile.getName());
        notify(player, core, session, profile.getName() + " is now trusted", false);
    }

    private static void untrust(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String name) {
        if (!core.removeTrusted(name)) {
            notify(player, core, session, name + " was not trusted", true);
            return;
        }
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " no longer trusts " + name);
        notify(player, core, session, name + " is no longer trusted", false);
    }

    /** Wie heet zo? Eerst wie nu online is, anders wie hier ooit was (Minecraft onthoudt dat). */
    private static @Nullable com.mojang.authlib.GameProfile findProfile(ServerPlayer asker, String name) {
        if (asker.getServer() == null) {
            return null;
        }
        ServerPlayer online = asker.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getGameProfile();
        }
        var cache = asker.getServer().getProfileCache();
        return cache == null ? null : cache.get(name).orElse(null);
    }

    /** Een nieuw, leeg bestand. Bestaat het al, dan blijft het zoals het is (en hoor je dat). */
    private static @Nullable String touch(VirtualFs fs, String path) {
        return fs.get(path) != null ? VirtualFs.nameOf(path) + " already exists" : fs.write(path, "");
    }

    /** Een pad uit de opdracht, altijd netjes gemaakt (absoluut, zonder {@code ..}). */
    private static String path(CompoundTag args, String key) {
        return VirtualFs.resolve("/", args.getString(key));
    }

    /** Een nieuw wachtwoord zetten of wijzigen. Dat gooit meteen alle andere sessies van deze kern eruit. */
    private static void passwd(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String newPassword) {
        String problem = SystemPassword.problem(newPassword);
        if (problem != null) {
            notify(player, core, session, "Password rejected: " + problem, true);
            return;
        }
        core.setPassword(newPassword, player.serverLevel().random);
        core.logEvent(EventLog.Type.SYSTEM, player.getGameProfile().getName() + " changed the password");
        // Een nieuw wachtwoord verbreekt elke andere sessie op deze kern.
        kickOthers(player, core.getBlockPos());
        notify(player, core, session, "Password updated. Other sessions were logged out.", false);
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

    // ================================================================ Mappen en bestanden

    /** Na een opdracht op de mappen: lukte het niet, dan zie je waarom; anders meteen de nieuwe lijst. */
    private static void fileResult(ServerPlayer player, SecurityCoreBlockEntity core, Session session, @Nullable String error) {
        if (error != null) {
            notify(player, core, session, error, true);
            return;
        }
        core.setChanged();
        sendSnapshot(player, core, session);
    }

    /**
     * Een bestand openen (in Notes). Is het een valstrik en ben jij niet de eigenaar, dan gaat hij
     * af. De inhoud gaat één keer naar het scherm, zodat wat je aan het typen bent niet steeds
     * overschreven wordt.
     */
    public static void openFile(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String path) {
        VirtualFs.Node node = core.fs().get(path);
        if (node == null || node.isDir()) {
            notify(player, core, session, VirtualFs.nameOf(path) + ": no such file", true);
            return;
        }
        if (node.isTrap() && !isOwner(player, core)) {
            springTrap(player, core, path);
            return;
        }
        session.fileToSend = path;
        sendSnapshot(player, core, session);
    }

    /** Een notitie bewaren (de Save-knop van Notes). */
    private static void saveFile(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String path, String text) {
        // Een valstrik overschrijven is ook "erin kijken": wie niet de eigenaar is, laat hem afgaan.
        VirtualFs.Node node = core.fs().get(path);
        if (node != null && node.isTrap() && !isOwner(player, core)) {
            springTrap(player, core, path);
            return;
        }
        String error = core.fs().write(path, text);
        if (error != null) {
            notify(player, core, session, error, true);
            return;
        }
        core.setChanged();
        notify(player, core, session, "Saved " + VirtualFs.nameOf(path), false);
    }

    /** Een bestand als valstrik markeren (of weer gewoon maken). Alleen de eigenaar mag dat. */
    private static void setTrap(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String path, boolean on) {
        if (!isOwner(player, core)) {
            notify(player, core, session, "Only the owner can set traps", true);
            return;
        }
        String error = core.fs().setTrap(path, on);
        if (error != null) {
            notify(player, core, session, error, true);
            return;
        }
        core.setChanged();
        notify(player, core, session, VirtualFs.nameOf(path) + (on ? " is now a trap" : " is no longer a trap"), false);
    }

    /**
     * Iemand die niet de eigenaar is, opende een valstrik. Dan:
     * <ul>
     *   <li>een regel in het logboek, en de eigenaar krijgt een alarm van E.D.I.T.H.;</li>
     *   <li>de lezer wordt uit zijn sessie gegooid en is TRAP_LOCKOUT ticks buitengesloten;</li>
     *   <li>de beveiliging jaagt even op hem (ook als "attack players" uit staat), en de drones
     *       gaan meteen op hem af als hij in het gebied is.</li>
     * </ul>
     * Publiek, zodat straks ook de terminal (cat, grep) hem kan laten afgaan.
     */
    public static void springTrap(ServerPlayer reader, SecurityCoreBlockEntity core, String path) {
        ServerLevel level = reader.serverLevel();
        String name = reader.getGameProfile().getName();
        long until = level.getGameTime() + SecurityBalance.TRAP_LOCKOUT;
        core.logEvent(EventLog.Type.TRAP, name + " opened the trap file " + path);
        LOCKOUTS.put(reader.getUUID(), new Lockout(core.getBlockPos().immutable(), until));
        close(reader);
        reader.displayClientMessage(Component.translatable("os.starktech.msg.trap_sprung",
                VirtualFs.nameOf(path), SecurityBalance.TRAP_LOCKOUT / 20).withStyle(ChatFormatting.RED), true);
        level.playSound(null, core.getBlockPos(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 0.6f);
        // De eigenaar weet het meteen (waar hij ook is).
        ServerPlayer owner = core.ownerId() == null || level.getServer() == null ? null
                : level.getServer().getPlayerList().getPlayer(core.ownerId());
        if (owner != null && owner != reader) {
            EdithServer.send(owner, Component.translatable("os.starktech.msg.trap_alert", name,
                    VirtualFs.nameOf(path), core.hostname()), EdithMessagePayload.Level.ALERT);
        }
        // De beveiliging jaagt op hem.
        core.hunt(reader.getUUID(), until);
        if (core.isOnline() && SecurityCores.inArea(core.getBlockPos(), reader.blockPosition())
                && SecurityCores.isIntruder(level, core, reader)) {
            for (SecurityDroneEntity drone : level.getEntitiesOfClass(SecurityDroneEntity.class, core.area(),
                    drone -> core.getBlockPos().equals(drone.home()))) {
                drone.setTarget(reader);
            }
        }
    }

    // ================================================================ De momentopname

    /** Een melding in de desktop (onderaan, een paar seconden), met meteen een nieuwe momentopname. */
    private static void notify(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String text, boolean error) {
        session.notice = text;
        session.noticeError = error;
        sendSnapshot(player, core, session);
    }

    private static void sendSnapshot(ServerPlayer player, SecurityCoreBlockEntity core, Session session) {
        ModNetwork.sendTo(player, new OsSnapshotPayload(core.getBlockPos(),
                snapshot(core, player.serverLevel(), session, isOwner(player, core))));
    }

    /**
     * De hele desktop in één CompoundTag: de status, het logboek, en (alleen als ze veranderd zijn)
     * de mappen. Een geopend bestand en een melding gaan er maar één keer in. Wie niet de eigenaar
     * is, ziet niet welke bestanden valstrikken zijn.
     */
    public static CompoundTag snapshot(SecurityCoreBlockEntity core, ServerLevel level, Session session, boolean owner) {
        CompoundTag data = new CompoundTag();
        data.putString("host", core.hostname());
        data.putString("ip", core.ip());
        data.putInt("port", core.sshPort());
        data.putString("owner", core.ownerName());
        data.putBoolean("isOwner", owner);
        data.putBoolean("hasPassword", core.hasPassword());
        data.putBoolean("online", core.isOnline());
        data.putBoolean("remote", session.remote());
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
        // Toegang en de schakelaars van het netwerk (de apps Access en Devices).
        CompoundTag access = new CompoundTag();
        access.putBoolean("mobs", core.attackMobs());
        access.putBoolean("players", core.attackPlayers());
        access.putBoolean("pvp", level.getServer().isPvpAllowed());
        access.putBoolean("drones", core.dronesEnabled());
        access.putBoolean("lasers", core.lasersEnabled());
        access.putBoolean("cameras", core.camerasEnabled());
        ListTag trusted = new ListTag();
        for (String name : core.trustedNames()) {
            trusted.add(net.minecraft.nbt.StringTag.valueOf(name));
        }
        access.put("trusted", trusted);
        data.put("access", access);
        // De apparaten met hun status.
        data.put("devices", com.senne.starktech.security.SecurityDevices.snapshot(level, core));
        data.put("cameras", com.senne.starktech.security.SecurityDevices.cameras(level, core));
        // De server (serverracks) waar de redstone-diagrammen op draaien.
        data.put("server", com.senne.starktech.logic.LogicServer.snapshot(level, core));
        // De mappen: alleen als het scherm een oudere versie heeft.
        VirtualFs fs = core.fs();
        if (session.sentFs != fs || session.sentRevision != fs.revision()) {
            data.put("fs", fsList(fs, owner));
            session.sentFs = fs;
            session.sentRevision = fs.revision();
        }
        // Een net geopend bestand (één keer).
        if (session.fileToSend != null) {
            VirtualFs.Node node = fs.get(session.fileToSend);
            if (node != null && !node.isDir()) {
                CompoundTag file = new CompoundTag();
                file.putString("p", session.fileToSend);
                file.putString("c", node.content());
                file.putBoolean("s", node.isSystem());
                data.put("file", file);
            }
            session.fileToSend = null;
        }
        // Een melding (één keer).
        if (session.notice != null) {
            CompoundTag notice = new CompoundTag();
            notice.putString("x", session.notice);
            notice.putBoolean("err", session.noticeError);
            data.put("notice", notice);
            session.notice = null;
        }
        return data;
    }

    /** Alle mappen en bestanden als lijst: pad, map of niet, systeem, valstrik (alleen voor de eigenaar), grootte. */
    private static ListTag fsList(VirtualFs fs, boolean owner) {
        ListTag list = new ListTag();
        for (Map.Entry<String, VirtualFs.Node> entry : fs.walk().entrySet()) {
            VirtualFs.Node node = entry.getValue();
            CompoundTag item = new CompoundTag();
            item.putString("p", entry.getKey());
            if (node.isDir()) {
                item.putBoolean("d", true);
            } else {
                item.putInt("n", node.content().length());
                if (node.isSystem()) {
                    item.putBoolean("s", true);
                }
                if (owner && node.isTrap()) {
                    item.putBoolean("t", true);
                }
            }
            list.add(item);
        }
        return list;
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
            sendSnapshot(player, core, session);
        }
    }

    /** De sessie van deze speler (of null). Voor de tests. */
    public static @Nullable Session session(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        LOCKOUTS.clear();
        com.senne.starktech.security.SecurityDevices.clear();
        com.senne.starktech.logic.LogicServer.clear();
    }
}
