package com.senne.imttech.security.zone;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.network.ZoneScanMarksPayload;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Wat de laatste Autoscan van een speler zag (hoofdstuk 116): waar de kamers openliggen (de pings), of hij de grot erbij
 * mag nemen ("Include cave"), een kopje met uitleg voor het venster van de app Zone, en hoe de zone eruitzag voor de scan
 * (voor Undo scan). Eén verslag per speler, alleen in het geheugen van de server (na uitloggen is het weg). Het blijft
 * {@link ImtConfig.Security#ZONE_SCAN_PING_SECONDS} seconden staan: lang genoeg om van de desktop naar de ping te lopen
 * en terug.
 */
public final class ZoneScanReports {
    private ZoneScanReports() {}

    /**
     * Zo lang (ticks) moet je na "Include cave" wachten voor je de grot weer erbij kunt nemen: die scan is zwaar, dus niet
     * twee keer vlak na elkaar. (Een gewone scan mag altijd; een ingehouden knop van de Zone Marker telt daar niet mee.)
     */
    public static final int INCLUDE_COOLDOWN = 40;

    /**
     * Eén verslag.
     *
     * @param core     de kern waarvoor gescand werd
     * @param start    waar de scan begon ("Include cave" begint daar opnieuw)
     * @param marks    de pings
     * @param offer    de tekst van de knop die je nu kunt kiezen ("Include cave", "Scan bigger"), of leeg
     * @param headline het kopje ("OPEN TO A CAVE"), of leeg
     * @param text     de uitleg eronder
     * @param made     wanneer (speltijd in ticks) de scan was
     * @param until    tot wanneer het geldt
     * @param id       een volgnummer: zo ziet de app dat er een nieuw verslag is (ook al lijkt het op het vorige)
     */
    public record Report(GlobalPos core, BlockPos start, List<ZoneScan.Mark> marks, String offer, String headline, String text,
                         long made, long until, long id) {
        /** Voor de momentopname van de desktop (zie ZoneApp.snapshot). */
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("title", headline);
            tag.putString("text", text);
            tag.putString("offer", offer);
            tag.putLong("id", id);
            ListTag list = new ListTag();
            for (ZoneScan.Mark mark : marks) {
                list.add(new IntArrayTag(new int[] {mark.pos().getX(), mark.pos().getY(), mark.pos().getZ(), mark.kind().ordinal()}));
            }
            tag.put("marks", list);
            return tag;
        }
    }

    /** Hoe de zone van een kern eruitzag voor en na een Autoscan (voor Undo scan), en tot wanneer dat mag. */
    private record Undo(GlobalPos core, List<ZoneBox> before, List<ZoneBox> after, long until) {}

    private static final Map<UUID, Report> REPORTS = new HashMap<>();
    private static final Map<UUID, Undo> UNDO = new HashMap<>();
    /** Wanneer (speltijd) een speler voor het laatst de grot erbij nam. */
    private static final Map<UUID, Long> LAST_INCLUDE = new HashMap<>();
    private static long counter;

    /** Zo lang (ticks) blijven een verslag, de pings en Undo staan. */
    public static int keepTicks() {
        return Math.max(20, Math.round(ImtConfig.Security.ZONE_SCAN_PING_SECONDS * 20));
    }

    /**
     * Mag deze speler nu scannen? Een gewone scan altijd; "Include cave" niet binnen {@link #INCLUDE_COOLDOWN} ticks na
     * zijn vorige (die scan is zwaar). Een "Include cave" die mag, telt meteen als zijn laatste.
     */
    public static boolean mayScan(ServerPlayer player, boolean include) {
        if (!include) {
            return true;
        }
        long now = player.serverLevel().getGameTime();
        Long last = LAST_INCLUDE.get(player.getUUID());
        if (last != null && now >= last && now - last < INCLUDE_COOLDOWN) {
            return false;
        }
        LAST_INCLUDE.put(player.getUUID(), now);
        return true;
    }

    /**
     * Na elke Autoscan: onthouden wat hij zag (niets te melden = het oude verslag weg), en de pings naar de speler sturen
     * (een lege lijst haalt de oude pings weg).
     */
    public static void report(ServerPlayer player, SecurityCoreBlockEntity core, BlockPos start, ZoneScan.Result result) {
        String text = result.ok() ? result.note() : result.problem();
        if (result.headline() == null && result.marks().isEmpty() || text == null) {
            REPORTS.remove(player.getUUID());
        } else {
            long now = player.serverLevel().getGameTime();
            REPORTS.put(player.getUUID(), new Report(GlobalPos.of(player.serverLevel().dimension(), core.getBlockPos()),
                    start.immutable(), List.copyOf(result.marks()), result.offer() == null ? "" : result.offer(),
                    result.headline() == null ? "" : result.headline(), text, now, now + keepTicks(), ++counter));
        }
        ModNetwork.sendTo(player, ZoneScanMarksPayload.of(result, keepTicks() / 20));
    }

    /** Het verslag van deze speler voor deze kern, of null (geen, te oud, of van een andere kern). */
    public static @Nullable Report get(ServerPlayer player, SecurityCoreBlockEntity core) {
        Report report = REPORTS.get(player.getUUID());
        if (report == null || core.getLevel() == null) {
            return null;
        }
        if (core.getLevel().getGameTime() > report.until()) {
            REPORTS.remove(player.getUUID());
            return null;
        }
        return report.core().equals(GlobalPos.of(core.getLevel().dimension(), core.getBlockPos())) ? report : null;
    }

    /** Het aanbod (de grot erbij, of groter scannen), of null als er geen is. */
    public static @Nullable Report offer(ServerPlayer player, SecurityCoreBlockEntity core) {
        Report report = get(player, core);
        return report != null && !report.offer().isEmpty() ? report : null;
    }

    /** Het aanbod gebruiken: het geldt maar één keer (een dubbele klik scant de grot niet twee keer). */
    public static @Nullable Report takeOffer(ServerPlayer player, SecurityCoreBlockEntity core) {
        Report report = offer(player, core);
        if (report != null) {
            REPORTS.put(player.getUUID(), new Report(report.core(), report.start(), report.marks(), "", report.headline(),
                    report.text(), report.made(), report.until(), report.id()));
        }
        return report;
    }

    // ================================================================ Undo scan

    /** Na een Autoscan die de zone veranderde: onthouden hoe hij ervoor en erna was (voor Undo scan). */
    public static void remember(ServerPlayer player, SecurityCoreBlockEntity core, List<ZoneBox> before) {
        if (core.getLevel() == null) {
            return;
        }
        UNDO.put(player.getUUID(), new Undo(GlobalPos.of(core.getLevel().dimension(), core.getBlockPos()), List.copyOf(before),
                List.copyOf(core.zoneBoxes()), core.getLevel().getGameTime() + keepTicks()));
    }

    /** Kan deze speler zijn laatste Autoscan op deze kern nog terugdraaien (op tijd, en de zone is sindsdien niet veranderd)? */
    public static boolean canUndo(ServerPlayer player, SecurityCoreBlockEntity core) {
        Undo undo = UNDO.get(player.getUUID());
        return undo != null && core.getLevel() != null && core.getLevel().getGameTime() <= undo.until()
                && undo.core().equals(GlobalPos.of(core.getLevel().dimension(), core.getBlockPos()))
                && undo.after().equals(core.zoneBoxes());
    }

    /** Undo scan: de zone terug zoals hij voor de laatste Autoscan was. Geeft de oude kubussen, of null als het niet kan. */
    public static @Nullable List<ZoneBox> undo(ServerPlayer player, SecurityCoreBlockEntity core) {
        if (!canUndo(player, core)) {
            return null;
        }
        Undo undo = UNDO.remove(player.getUUID());
        REPORTS.remove(player.getUUID());
        ModNetwork.sendTo(player, new ZoneScanMarksPayload(List.of(), List.of(), false, 0));
        return undo.before();
    }

    /** Uitgelogd (of de server stopt, {@code player} = null): niets meer onthouden. */
    static void forget(@Nullable UUID player) {
        if (player == null) {
            REPORTS.clear();
            UNDO.clear();
            LAST_INCLUDE.clear();
        } else {
            REPORTS.remove(player);
            UNDO.remove(player);
            LAST_INCLUDE.remove(player);
        }
    }
}
