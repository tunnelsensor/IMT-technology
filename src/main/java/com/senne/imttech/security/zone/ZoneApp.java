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
 * ZONE_REACH (200 blokken) van de kern steekt, valt eraf (zie SecurityCoreBlockEntity.setZoneBoxes).
 * Een kubus die de zone of het gebied van een andere kern raakt, mag niet erbij (of niet groter).
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
        data.putInt("reach", Math.round(ImtConfig.Security.ZONE_REACH));
        if (viewer != null && !remote) {
            data.put("player", xyz(viewer.blockPosition()));
        }
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
            case "zone_delete" -> deleteBox(core, boxes, args.getInt("i"));
            case "zone_grow" -> growBox(core, boxes, args.getInt("i"), args.getInt("side"), args.getInt("by"));
            case "zone_set" -> setBox(core, boxes, args.getInt("i"), args.getIntArray("box"));
            default -> new Result("", false);
        };
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
            return new Result("Your zone can't reach further (" + Math.round(ImtConfig.Security.ZONE_REACH) + " blocks from the core)", true);
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
     * De getallen van de twee hoeken, zoals je ze typte. Wat verder dan ZONE_REACH van de kern ligt,
     * valt eraf; raakt hij de zone of het gebied van een andere kern, dan blijft de oude kubus.
     */
    private static Result setBox(SecurityCoreBlockEntity core, List<ZoneBox> boxes, int index, int[] numbers) {
        ZoneBox wanted = ZoneBox.load(numbers);
        if (index < 0 || index >= boxes.size() || wanted == null) {
            return new Result("", false);
        }
        ZoneBox fitted = wanted.clamp(core.zoneLow(), core.zoneHigh());
        if (fitted == null) {
            return new Result("That is too far: a zone reaches " + Math.round(ImtConfig.Security.ZONE_REACH) + " blocks from the core", true);
        }
        if (!boxes.get(index).contains(fitted) && core.zoneBoxBlocked(fitted)) {
            return new Result(BLOCKED, true);
        }
        boxes.set(index, fitted);
        core.setZoneBoxes(boxes);
        boolean cut = !fitted.equals(wanted);
        return new Result("Box " + (index + 1) + ": " + fitted.size()
                + (cut ? " (cut: a zone reaches " + Math.round(ImtConfig.Security.ZONE_REACH) + " blocks from the core)" : ""), cut);
    }

    /** Wat de app zegt als een kubus een ander in de weg zou zitten. */
    private static final String BLOCKED = "Too close to another security network or its zone";
}
