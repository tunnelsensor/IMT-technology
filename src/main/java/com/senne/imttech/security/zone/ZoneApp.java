package com.senne.imttech.security.zone;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.EventLog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * De app Zone op de Security OS-desktop, aan de kant van de server (hoofdstuk 10): wat de app te zien
 * krijgt ({@link #snapshot}), en wat de knoppen doen ({@link #handle}). Het scherm zelf staat in
 * client/os/ZoneApp.
 *
 * In de app stel je je kubussen per blok bij: per kant een − en een + (die kant één blok naar
 * binnen of naar buiten), of je typt de getallen van de twee hoeken. "New box" zet een nieuwe kubus
 * van 7 x 5 x 7 waar je staat (of bij de kern, als je via SSH van ver werkt). Alles wat verder dan
 * ZONE_RANGE (500 blokken) van de kern steekt, valt eraf (zie SecurityCoreBlockEntity.setZoneBoxes).
 * Een kubus die een ander in de weg zit (de zone van een andere kern, een andere kern zelf, het stuk
 * rond een Biebia's Industries Tower), mag niet erbij (of niet groter).
 */
public final class ZoneApp {
    private ZoneApp() {}

    /** Wat een knop deed: de tekst onderaan de desktop, en of het een fout was. */
    public record Result(String text, boolean error) {}

    /** De kanten in de volgorde van Minecraft (0 = onder, 1 = boven, 2 = noord, 3 = zuid, 4 = west, 5 = oost). */
    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST};

    // ================================================================ Wat de app ziet

    /**
     * Alles voor de app: of de zone beschermt, de kubussen, hoe groot en hoeveel stroom, hoe ver de zone
     * mag komen (voor de kaart), en waar jij staat (alleen als je bij de kern bent, niet via SSH).
     */
    public static CompoundTag snapshot(ServerLevel level, SecurityCoreBlockEntity core, @Nullable ServerPlayer viewer, boolean remote) {
        CompoundTag data = new CompoundTag();
        ZoneData.Zone zone = core.zone();
        data.putBoolean("active", zone != null && zone.active());
        data.putBoolean("powered", zone == null || zone.powered());
        data.put("core", xyz(core.getBlockPos()));
        data.put("low", xyz(core.zoneLow()));
        data.put("high", xyz(core.zoneHigh()));
        ListTag boxes = new ListTag();
        for (ZoneBox box : core.zoneBoxes()) {
            boxes.add(box.save());
        }
        data.put("boxes", boxes);
        data.putLong("volume", zone == null ? 0 : zone.volume());
        data.putInt("upkeep", core.zoneUpkeep());
        data.putDouble("coreUpkeep", ImtConfig.Power.SECURITY_CORE_UPKEEP);
        data.putInt("max", ImtConfig.Security.ZONE_MAX_BOXES);
        data.putInt("reach", Math.round(ImtConfig.Security.ZONE_RANGE));
        data.putInt("margin", ZoneScan.margin());
        if (viewer != null && !remote) {
            data.put("player", xyz(viewer.blockPosition()));
        }
        // Hoofdstuk 116: wat jouw laatste Autoscan zag (waar je kamers openliggen, en of de grot erbij mag), en of je hem
        // nog kunt terugdraaien.
        ZoneScanReports.Report report = viewer == null ? null : ZoneScanReports.get(viewer, core);
        if (report != null) {
            data.put("scan", report.save());
        }
        data.putBoolean("undo", viewer != null && ZoneScanReports.canUndo(viewer, core));
        return data;
    }

    private static IntArrayTag xyz(BlockPos pos) {
        return new IntArrayTag(new int[] {pos.getX(), pos.getY(), pos.getZ()});
    }

    // ================================================================ De knoppen

    /** Een opdracht van de app ("zone_new", "zone_delete", "zone_grow", "zone_set"). */
    public static Result handle(ServerPlayer player, SecurityCoreBlockEntity core, String op, CompoundTag args, boolean remote) {
        List<ZoneBox> boxes = new ArrayList<>(core.zoneBoxes());
        return switch (op) {
            case "zone_new" -> addBox(player, core, boxes, remote);
            case "zone_scan" -> scan(player, core, remote, args.getBoolean("cave"));
            case "zone_undo" -> undo(player, core);
            case "zone_delete" -> deleteBox(core, boxes, args.getInt("i"));
            case "zone_grow" -> growBox(core, boxes, args.getInt("i"), args.getInt("side"), args.getInt("by"));
            case "zone_set" -> setBox(core, boxes, args.getInt("i"), args.getIntArray("box"));
            default -> new Result("", false);
        };
    }

    /**
     * De Autoscan (hoofdstuk 45): vanaf de kern (de kamer waar hij staat), of waar jij staat als je bij de kern bent. Een
     * dichte bunker wordt kamers plus muur; een open basis een doos om het gebouw (zie ZoneScan).
     * <p>
     * Hoofdstuk 116: lopen je kamers door in een grot, dan zegt de app dat (met een ping op de opening) en verschijnt de
     * knop "Include cave". Die stuurt {@code cave} = true: dezelfde scan opnieuw, vanaf waar de vorige begon, met de grot
     * erbij (tenzij het een giga cave is).
     */
    private static Result scan(ServerPlayer player, SecurityCoreBlockEntity core, boolean remote, boolean cave) {
        ServerLevel level = player.serverLevel();
        ZoneBox limit = ZoneBox.of(core.zoneLow(), core.zoneHigh());
        BlockPos start;
        ZoneScan.Mode mode = ZoneScan.Mode.NORMAL;
        if (cave) {
            // De knop geldt één keer, en alleen zolang het aanbod er is (een dubbele klik scant de grot niet twee keer).
            ZoneScanReports.Report offer = ZoneScanReports.offer(player, core);
            if (offer == null) {
                // Een tweede klik vlak na de eerste: niets doen (geen rode melding over het goede resultaat heen).
                return new Result(ZoneScanReports.justPressed(player) ? "" : "Autoscan: nothing to include, scan again first", true);
            }
            if (!ZoneScanReports.mayScan(player, true)) {
                return new Result("Autoscan: busy, try again in a moment", true);
            }
            ZoneScanReports.takeOffer(player, core);
            start = offer.start();
            mode = ZoneScan.modeFor(offer.offer());
        } else {
            start = scanStart(player, core, level, limit, remote);
        }
        ZoneScan.Result found = ZoneScan.scan(level, start, limit, ImtConfig.Security.ZONE_MAX_BOXES, mode);
        // Op afstand (SSH, telefoon, of ver van de plek) zie je de ping niet: dan staat hij op de kaart van de app.
        boolean far = remote || player.distanceToSqr(start.getCenter()) > FAR * FAR;
        ZoneScanReports.report(player, core, start, found, far);
        if (!found.ok()) {
            return new Result("Autoscan: " + where(found.problem(), far), true);
        }
        List<ZoneBox> before = List.copyOf(core.zoneBoxes());
        ZoneScan.Applied applied = ZoneScan.apply(core, found, ImtConfig.Security.ZONE_MAX_BOXES);
        if (applied.changed()) {
            ZoneScanReports.remember(player, core, before);
        }
        return new Result(applied.text(), false);
    }

    /** Verder dan dit (blokken) van waar de scan begon zie je de ping niet goed: dan verwijzen we naar de kaart. */
    private static final double FAR = 64;

    /** "(see the ping)" wordt "(see the map)" als je ver weg bent. */
    private static String where(@Nullable String text, boolean far) {
        String said = text == null ? "" : text;
        return far ? said.replace("(see the ping)", "(see the map)") : said;
    }

    /** Undo scan (hoofdstuk 116): de zone terug zoals hij voor je laatste Autoscan was. */
    private static Result undo(ServerPlayer player, SecurityCoreBlockEntity core) {
        List<ZoneBox> before = ZoneScanReports.undo(player, core);
        if (before == null) {
            // Een tweede klik vlak na de eerste: niets doen.
            return new Result(ZoneScanReports.justPressed(player) ? "" : "Undo is no longer possible: your zone changed, or it was too long ago", true);
        }
        List<ZoneBox> fitted = core.setZoneBoxes(before);
        core.logEvent(EventLog.Type.ZONE, "Zone: autoscan undone (" + fitted.size() + " boxes)");
        return new Result("Autoscan undone: your zone has " + fitted.size() + (fitted.size() == 1 ? " box" : " boxes") + " again", false);
    }

    /**
     * Waar de Autoscan begint: waar jij staat (bij de kern, binnen zijn bereik; sta je op tapijt of een halve plaat, het
     * blok erboven), of in de lucht naast de kern.
     */
    private static BlockPos scanStart(ServerPlayer player, SecurityCoreBlockEntity core, ServerLevel level, ZoneBox limit,
                                      boolean remote) {
        if (!remote && limit.contains(player.blockPosition())) {
            return ZoneScan.roomStart(level, player.blockPosition());
        }
        // De kern zelf is een vast blok: begin in de lucht ernaast (boven, of een andere kant).
        for (Direction direction : new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos next = core.getBlockPos().relative(direction);
            if (ZoneScan.passable(level, next, level.getBlockState(next))) {
                return next;
            }
        }
        return core.getBlockPos();
    }

    /** Een nieuwe kubus van 7 x 5 x 7: waar je staat (de vloer onder je hoort erbij), of bij de kern. */
    private static Result addBox(ServerPlayer player, SecurityCoreBlockEntity core, List<ZoneBox> boxes, boolean remote) {
        if (boxes.size() >= ImtConfig.Security.ZONE_MAX_BOXES) {
            return new Result("Your zone already has " + ImtConfig.Security.ZONE_MAX_BOXES + " boxes", true);
        }
        BlockPos centre = core.getBlockPos();
        if (!remote && ZoneBox.of(core.zoneLow(), core.zoneHigh()).contains(player.blockPosition())) {
            centre = player.blockPosition();
        }
        ZoneBox box = ZoneBox.of(centre.offset(-3, -1, -3), centre.offset(3, 3, 3)).clamp(core.zoneLow(), core.zoneHigh());
        if (box == null || core.zoneBoxBlocked(box)) {
            return new Result(BLOCKED, true);
        }
        boxes.add(box);
        List<ZoneBox> fitted = core.setZoneBoxes(boxes);
        ZoneBox added = fitted.getLast();
        core.logEvent(EventLog.Type.ZONE, "Zone: box " + fitted.size() + " added (" + added.size() + ")");
        return new Result("Box " + fitted.size() + " added: " + added.size(), false);
    }

    private static Result deleteBox(SecurityCoreBlockEntity core, List<ZoneBox> boxes, int index) {
        if (index < 0 || index >= boxes.size()) {
            return new Result("That box is gone already", true);
        }
        ZoneBox removed = boxes.remove(index);
        core.setZoneBoxes(boxes);
        core.logEvent(EventLog.Type.ZONE, "Zone: box " + (index + 1) + " removed (" + removed.size() + ")");
        return new Result("Box " + (index + 1) + " removed", false);
    }

    /** Eén kant van een kubus één blok naar buiten ({@code by} = 1) of naar binnen ({@code by} = -1). */
    private static Result growBox(SecurityCoreBlockEntity core, List<ZoneBox> boxes, int index, int side, int by) {
        if (index < 0 || index >= boxes.size() || side < 0 || side >= SIDES.length || Math.abs(by) > 64) {
            return new Result("", false);
        }
        ZoneBox old = boxes.get(index);
        ZoneBox grown = old.grow(SIDES[side], by);
        if (grown == null) {
            return new Result("A box is at least 1 block", true);
        }
        ZoneBox fitted = grown.clamp(core.zoneLow(), core.zoneHigh());
        if (fitted == null || fitted.equals(old)) {
            return new Result("Your zone can't reach further (" + Math.round(ImtConfig.Security.ZONE_RANGE) + " blocks from the core)", true);
        }
        // Kleiner maken mag altijd; groter alleen als hij dan niemand in de weg zit.
        if (by > 0 && core.zoneBoxBlocked(fitted)) {
            return new Result(BLOCKED, true);
        }
        boxes.set(index, fitted);
        core.setZoneBoxes(boxes);
        return new Result("Box " + (index + 1) + ": " + fitted.size(), false);
    }

    /**
     * De getallen van de twee hoeken, zoals je ze typte. Wat verder dan ZONE_RANGE van de kern ligt,
     * valt eraf; zit hij een ander in de weg, dan blijft de oude kubus.
     */
    private static Result setBox(SecurityCoreBlockEntity core, List<ZoneBox> boxes, int index, int[] numbers) {
        ZoneBox wanted = ZoneBox.load(numbers);
        if (index < 0 || index >= boxes.size() || wanted == null) {
            return new Result("", false);
        }
        ZoneBox fitted = wanted.clamp(core.zoneLow(), core.zoneHigh());
        if (fitted == null) {
            return new Result("That is too far: a zone reaches " + Math.round(ImtConfig.Security.ZONE_RANGE) + " blocks from the core", true);
        }
        if (!boxes.get(index).contains(fitted) && core.zoneBoxBlocked(fitted)) {
            return new Result(BLOCKED, true);
        }
        boxes.set(index, fitted);
        core.setZoneBoxes(boxes);
        boolean cut = !fitted.equals(wanted);
        return new Result("Box " + (index + 1) + ": " + fitted.size()
                + (cut ? " (cut: a zone reaches " + Math.round(ImtConfig.Security.ZONE_RANGE) + " blocks from the core)" : ""), cut);
    }

    /** Wat de app zegt als een kubus een ander in de weg zou zitten (zie SecurityCores.zoneBlocked). */
    private static final String BLOCKED = "Another core is in the way: its zone, the core itself, or a Biebia tower";
}
