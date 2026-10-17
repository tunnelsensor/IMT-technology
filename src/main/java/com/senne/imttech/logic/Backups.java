package com.senne.imttech.logic;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.EventLog;
import java.util.List;
import java.util.Map;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Backups van je server naar een ander datacenter van jezelf (jouw wens, 8 oktober; automatisch
 * én met de hand).
 *
 * <ul>
 *   <li>Een backup is een kopie van je diagrammen en keycards (alles wat bij een brand verloren
 *       gaat, zie Datacenter). Hij staat op het andere datacenter en neemt daar plek op de SSD's:
 *       zoveel blokjes als je diagrammen, plus één per 10 keycards.</li>
 *   <li>Op één datacenter blijven van elk netwerk de laatste {@code ImtConfig.Logic.BACKUPS_KEPT} staan; een nieuwe duwt
 *       de oudste eruit.</li>
 *   <li>Automatisch: om de 5, 15 of 60 minuten (speltijd), naar het datacenter dat je koos.</li>
 *   <li>Terugzetten (Restore) vervangt je diagrammen door die van de backup. De keycards alleen als
 *       de backup van dit netwerk is (een kaart hoort bij het IP dat hem schreef). Het andere
 *       datacenter moet daarvoor draaien.</li>
 *   <li>Brandt het andere datacenter af (een SSD), dan zijn de backups daar ook weg.</li>
 * </ul>
 */
public final class Backups {
    private Backups() {}

    /** De keuzes voor automatisch (minuten); 0 = uit. */
    public static final int[] INTERVALS = {0, 5, 15, 60};

    /** Hoe het afliep: een korte zin voor onderaan de desktop, en of het fout ging. */
    public record Result(String text, boolean error) {
        static Result ok(String text) {
            return new Result(text, false);
        }

        static Result fail(String text) {
            return new Result(text, true);
        }
    }

    // ================================================================ Opdrachten van de desktop

    /** Een knop in de app Backup (zie SecurityOs.handleAction). */
    public static Result handle(ServerPlayer player, SecurityCoreBlockEntity core, String op, CompoundTag args) {
        ServerLevel level = player.serverLevel();
        String who = player.getGameProfile().getName();
        return switch (op) {
            case "backup_target" -> chooseTarget(level, core, args.getString("key"));
            case "backup_auto" -> setInterval(core, args.getInt("minutes"));
            case "backup_now" -> backup(level, core, false, who);
            case "backup_restore" -> restore(level, core, args.getInt("id"), who);
            case "backup_delete" -> delete(level, core, args.getInt("id"), who);
            default -> Result.fail("Unknown backup command");
        };
    }

    private static Result chooseTarget(ServerLevel level, SecurityCoreBlockEntity core, String key) {
        if (key.isEmpty()) {
            core.datacenter().setBackupTarget(null);
            core.setChanged();
            return Result.ok("No backup datacenter");
        }
        GlobalPos target = Datacenters.fromKey(key);
        Datacenters.Summary summary = target == null ? null : Datacenters.get(level).summary(target);
        if (summary == null || !java.util.Objects.equals(summary.owner(), core.ownerId()) || target.equals(here(level, core))) {
            return Result.fail("That is not another datacenter of yours");
        }
        core.datacenter().setBackupTarget(target);
        core.setChanged();
        return Result.ok("Backups go to " + summary.host());
    }

    private static Result setInterval(SecurityCoreBlockEntity core, int minutes) {
        for (int allowed : INTERVALS) {
            if (allowed == minutes) {
                core.datacenter().setBackupMinutes(minutes);
                core.setChanged();
                return Result.ok(minutes == 0 ? "Automatic backups off" : "Automatic backup every " + minutes + " minutes");
            }
        }
        return Result.fail("Choose off, 5, 15 or 60 minutes");
    }

    // ================================================================ Een backup maken

    /**
     * Maakt een backup van deze server op het gekozen datacenter. {@code auto} = de server deed het
     * zelf (om de zoveel minuten); {@code who} staat in het logboek.
     */
    public static Result backup(ServerLevel level, SecurityCoreBlockEntity core, boolean auto, String who) {
        GlobalPos target = core.datacenter().backupTarget();
        if (target == null) {
            return Result.fail("Choose a datacenter to back up to");
        }
        Datacenters datacenters = Datacenters.get(level);
        Datacenters.Summary summary = datacenters.summary(target);
        if (summary == null || !java.util.Objects.equals(summary.owner(), core.ownerId())) {
            return Result.fail("That datacenter is gone or not yours anymore");
        }
        if (!LogicServer.stats(level, core).online()) {
            return Result.fail("This server is not running: nothing to send");
        }
        if (!summary.online()) {
            return Result.fail(summary.host() + " is offline");
        }
        int size = size(core.logic());
        GlobalPos here = here(level, core);
        int free = datacenters.free(target, here, Math.round(ImtConfig.Logic.BACKUPS_KEPT));
        if (size > free) {
            return Result.fail("Not enough room on " + summary.host() + ": needs " + size + " blocks, " + Math.max(0, free) + " free");
        }
        CompoundTag data = new CompoundTag();
        data.put("Logic", core.logic().save());
        datacenters.add(target, here, core.ip(), core.hostname(), core.ownerId(), level.getGameTime(), size, auto, data, Math.round(ImtConfig.Logic.BACKUPS_KEPT));
        core.datacenter().backedUp(level.getGameTime());
        core.setChanged();
        core.logEvent(EventLog.Type.SYSTEM, (auto ? "Automatic backup" : "Backup by " + who) + " to " + summary.host()
                + " (" + size + " blocks)");
        return Result.ok("Backed up to " + summary.host() + " (" + size + " blocks)");
    }

    /** Zoveel blokjes neemt een backup van deze server in: zijn diagrammen, plus één per 10 keycards (minstens 1). */
    public static int size(LogicStore store) {
        return Math.max(1, store.blocks() + (store.cards().size() + 9) / 10);
    }

    /**
     * Elke twee seconden (vanuit Datacenter): tijd voor een automatische backup? Lukt het niet, dan
     * zegt I.M.T dat één keer (en het staat in het logboek), tot het weer lukt.
     */
    static void tick(ServerLevel level, SecurityCoreBlockEntity core) {
        Datacenter datacenter = core.datacenter();
        int minutes = datacenter.backupMinutes();
        if (minutes <= 0 || datacenter.backupTarget() == null) {
            return;
        }
        long now = level.getGameTime();
        long last = datacenter.lastBackup();
        if (last > 0 && now - last < minutes * 1200L && now >= last || now < datacenter.retryAt()) {
            return;
        }
        Result result = backup(level, core, true, "");
        if (!result.error()) {
            datacenter.setAutoError("");
            return;
        }
        // Lukt niet: over een minuut nog eens proberen, en alleen iets zeggen als het een nieuwe fout is.
        datacenter.setRetryAt(now + 1200L);
        if (!result.text().equals(datacenter.autoError())) {
            datacenter.setAutoError(result.text());
            core.logEvent(EventLog.Type.SYSTEM, "Automatic backup failed: " + result.text());
            ServerPlayer owner = core.ownerId() == null || level.getServer() == null ? null
                    : level.getServer().getPlayerList().getPlayer(core.ownerId());
            if (owner != null) {
                ImtServer.send(owner, Component.translatable("logic.imttech.backup.failed", core.hostname(), result.text()),
                        ImtMessagePayload.Level.WARNING);
            }
        }
        core.setChanged();
    }

    // ================================================================ Terugzetten en weggooien

    private static Result restore(ServerLevel level, SecurityCoreBlockEntity core, int id, String who) {
        Datacenters datacenters = Datacenters.get(level);
        Datacenters.Backup backup = datacenters.backup(id);
        if (backup == null || !java.util.Objects.equals(backup.owner(), core.ownerId())) {
            return Result.fail("That backup is gone");
        }
        Datacenters.Summary at = datacenters.summary(backup.target());
        if (at == null || !at.online()) {
            return Result.fail((at == null ? "Its datacenter" : at.host()) + " is offline: the backup can't be read");
        }
        LogicStore saved = LogicStore.load(backup.data().getCompound("Logic"));
        boolean sameNetwork = backup.sourceIp().equals(core.ip());
        core.logic().restoreFrom(saved, sameNetwork);
        LogicRunner.stop(level, core.getBlockPos());
        core.setChanged();
        int programs = saved.programs().size();
        String what = programs + " diagram" + (programs == 1 ? "" : "s")
                + (sameNetwork ? " and " + saved.cards().size() + " keycard" + (saved.cards().size() == 1 ? "" : "s") : "");
        core.logEvent(EventLog.Type.SYSTEM, who + " restored a backup from " + at.host() + ": " + what);
        return Result.ok("Restored " + what + (sameNetwork ? "" : " (keycards belong to " + backup.sourceIp() + ")"));
    }

    private static Result delete(ServerLevel level, SecurityCoreBlockEntity core, int id, String who) {
        Datacenters datacenters = Datacenters.get(level);
        Datacenters.Backup backup = datacenters.backup(id);
        if (backup == null || !java.util.Objects.equals(backup.owner(), core.ownerId())) {
            return Result.fail("That backup is gone");
        }
        datacenters.remove(id);
        core.logEvent(EventLog.Type.SYSTEM, who + " deleted a backup of " + backup.sourceHost());
        return Result.ok("Backup deleted");
    }

    // ================================================================ Voor de desktop

    /**
     * Wat de app Backup laat zien: waar de backups heen gaan (en welke datacenters je kunt kiezen,
     * met hoeveel plek), automatisch of niet, en alle backups van de eigenaar.
     */
    public static CompoundTag snapshot(ServerLevel level, SecurityCoreBlockEntity core) {
        Datacenters datacenters = Datacenters.get(level);
        Datacenter datacenter = core.datacenter();
        GlobalPos here = here(level, core);
        long now = level.getGameTime();
        CompoundTag tag = new CompoundTag();
        GlobalPos target = datacenter.backupTarget();
        tag.putString("target", target == null ? "" : Datacenters.key(target));
        tag.putInt("minutes", datacenter.backupMinutes());
        tag.putInt("lastAgo", datacenter.lastBackup() <= 0 ? -1 : (int) Math.max(0, (now - datacenter.lastBackup()) / 20));
        tag.putInt("size", size(core.logic()));
        tag.putString("error", datacenter.autoError());
        ListTag targets = new ListTag();
        for (Map.Entry<GlobalPos, Datacenters.Summary> entry : datacenters.ownedBy(core.ownerId())) {
            if (entry.getKey().equals(here)) {
                continue;
            }
            Datacenters.Summary summary = entry.getValue();
            CompoundTag item = new CompoundTag();
            item.putString("key", Datacenters.key(entry.getKey()));
            item.putString("host", summary.host());
            item.putString("ip", summary.ip());
            item.putBoolean("online", summary.online());
            item.putInt("free", Math.max(0, datacenters.free(entry.getKey(), here, Math.round(ImtConfig.Logic.BACKUPS_KEPT))));
            item.putInt("cap", summary.capacity());
            targets.add(item);
        }
        tag.put("targets", targets);
        ListTag list = new ListTag();
        List<Datacenters.Backup> backups = datacenters.backupsOf(core.ownerId());
        for (Datacenters.Backup backup : backups) {
            Datacenters.Summary at = datacenters.summary(backup.target());
            CompoundTag item = new CompoundTag();
            item.putInt("id", backup.id());
            item.putString("from", backup.sourceHost());
            item.putString("at", at == null ? "?" : at.host());
            item.putBoolean("online", at != null && at.online());
            item.putBoolean("same", backup.sourceIp().equals(core.ip()));
            item.putInt("size", backup.size());
            item.putInt("ago", (int) Math.max(0, (now - backup.time()) / 20));
            item.putBoolean("auto", backup.auto());
            list.add(item);
        }
        tag.put("list", list);
        return tag;
    }

    /** Waar deze kern staat (dimensie en plek). */
    static GlobalPos here(ServerLevel level, SecurityCoreBlockEntity core) {
        return GlobalPos.of(level.dimension(), core.getBlockPos());
    }

    /** Voor de tests: de backup met de hoogste id van deze eigenaar, of null. */
    public static @Nullable Datacenters.Backup newest(ServerLevel level, @Nullable java.util.UUID owner) {
        List<Datacenters.Backup> backups = Datacenters.get(level).backupsOf(owner);
        return backups.isEmpty() ? null : backups.getFirst();
    }
}
