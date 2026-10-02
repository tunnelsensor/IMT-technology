package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Het Devices-venster van de Stark OS-desktop (jouw wens, 28 september: de torentjes, drones,
 * laserwanden en camera's bekijken en bedienen). Bovenaan drie schakelaars voor het hele netwerk
 * (drones, laserwanden, camera's), daaronder elk apparaat met zijn status:
 * <ul>
 *   <li>torentjes: aan/uit (een eigen knop per torentje), stroom, raketten, waar hij op richt;</li>
 *   <li>drones: levens, wat hij doet, op wie hij schiet;</li>
 *   <li>laserpoorten: aan of open;</li>
 *   <li>camera's: beeld of niet (het beeld zelf zie je in de app Cameras).</li>
 * </ul>
 * De server zoekt zelf welke apparaten bij de kern horen (zie SecurityDevices) en stuurt de lijst
 * mee met elke momentopname.
 */
final class DevicesApp {
    /** Een regel in de lijst: een kopje ("TURRETS") of een apparaat. */
    private record Row(@Nullable String header, @Nullable CompoundTag device) {}

    private static final int HEADER_H = 12;
    private static final int DEVICE_H = 21;

    private final StarkOsScreen screen;
    private int scroll;

    DevicesApp(StarkOsScreen screen) {
        this.screen = screen;
    }

    private CompoundTag access() {
        return screen.data().getCompound("access");
    }

    /** De lijst: per soort een kopje en dan de apparaten van die soort. */
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        String[][] kinds = {{"turret", "TURRETS"}, {"drone", "DRONES"}, {"laser", "LASER GATES"}, {"camera", "CAMERAS"}};
        List<Tag> devices = screen.data().getList("devices", Tag.TAG_COMPOUND);
        for (String[] kind : kinds) {
            boolean any = false;
            for (Tag element : devices) {
                CompoundTag device = (CompoundTag) element;
                if (!device.getString("k").equals(kind[0])) {
                    continue;
                }
                if (!any) {
                    rows.add(new Row(kind[1], null));
                    any = true;
                }
                rows.add(new Row(null, device));
            }
        }
        return rows;
    }

    private static int height(Row row) {
        return row.header() != null ? HEADER_H : DEVICE_H;
    }

    private static int listTop(StarkOsScreen.Area a) {
        return a.y() + OsDraw.BUTTON_H + 5;
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my) {
        // De schakelaars van het hele netwerk.
        CompoundTag access = access();
        int bx = a.x();
        bx = switchButton(g, font, bx, a.y(), "Drones", access.getBoolean("drones")) + 6;
        bx = switchButton(g, font, bx, a.y(), "Lasers", access.getBoolean("lasers")) + 6;
        switchButton(g, font, bx, a.y(), "Cameras", access.getBoolean("cameras"));
        // De lijst.
        int top = listTop(a);
        int bottom = a.y() + a.h();
        OsDraw.field(g, a.x(), top - 1, a.x() + a.w(), bottom, false);
        List<Row> rows = rows();
        if (rows.isEmpty()) {
            g.drawString(font, "No turrets, drones, laser gates or cameras", a.x() + 5, top + 4, HudStyle.argb(255, OsDraw.GREY), false);
            g.drawString(font, "in this network's area yet.", a.x() + 5, top + 14, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        scroll = Math.max(0, Math.min(scroll, rows.size() - 1));
        int y = top + 2;
        for (int i = scroll; i < rows.size() && y + height(rows.get(i)) <= bottom; i++) {
            Row row = rows.get(i);
            if (row.header() != null) {
                g.drawString(font, row.header(), a.x() + 4, y + 2, HudStyle.argb(255, HudStyle.CYAN), false);
            } else {
                renderDevice(g, font, a, y, row.device());
            }
            y += height(row);
        }
    }

    /** Een knop "Drones: ON" (groen) of "Drones: OFF" (rood). Geeft de rechterkant terug. */
    private static int switchButton(GuiGraphics g, Font font, int x, int y, String name, boolean on) {
        String label = name + ": " + (on ? "ON" : "OFF");
        return x + OsDraw.button(g, font, x, y, label, true, on ? HudStyle.GREEN : HudStyle.RED);
    }

    private void renderDevice(GuiGraphics g, Font font, StarkOsScreen.Area a, int y, CompoundTag device) {
        String kind = device.getString("k");
        String where = device.getInt("x") + " " + device.getInt("y") + " " + device.getInt("z");
        g.drawString(font, device.getString("name"), a.x() + 6, y + 1, HudStyle.argb(255, HudStyle.WHITE), false);
        g.drawString(font, where, a.x() + 10 + font.width(device.getString("name")), y + 1,
                HudStyle.argb(255, OsDraw.GREY), false);
        // Tweede regel: de status, en wat erbij hoort.
        String status = device.getString("st");
        int colour = switch (status) {
            case "online", "patrolling", "active" -> HudStyle.GREEN;
            case "attacking" -> HudStyle.ORANGE;
            case "open" -> HudStyle.CYAN;
            default -> HudStyle.RED;
        };
        StringBuilder details = new StringBuilder();
        if (device.contains("ammo")) {
            details.append("  ").append(device.getInt("ammo")).append(" rockets");
        }
        if (kind.equals("drone")) {
            details.append("  hp ").append(device.getInt("hp")).append("/").append(device.getInt("max"));
        }
        if (device.contains("tgt")) {
            details.append("  > ").append(device.getString("tgt"));
        }
        g.drawString(font, status, a.x() + 10, y + 10, HudStyle.argb(255, colour), false);
        g.drawString(font, details.toString(), a.x() + 10 + font.width(status), y + 10,
                HudStyle.argb(255, OsDraw.GREY), false);
        // Een torentje heeft zijn eigen aan/uit-knop.
        if (kind.equals("turret")) {
            boolean on = device.getBoolean("on");
            String label = on ? "Turn off" : "Turn on";
            OsDraw.button(g, font, a.x() + a.w() - OsDraw.buttonWidth(font, label) - 4, y + 2, label, true,
                    on ? HudStyle.RED : HudStyle.GREEN);
        }
    }

    // ================================================================ Muis

    void mouseClicked(Font font, StarkOsScreen.Area a, int mx, int my) {
        CompoundTag access = access();
        // De twee schakelaars bovenaan.
        String drones = "Drones: " + (access.getBoolean("drones") ? "ON" : "OFF");
        int dronesW = OsDraw.buttonWidth(font, drones);
        if (OsDraw.inside(mx, my, a.x(), a.y(), dronesW, OsDraw.BUTTON_H)) {
            flag("drones", !access.getBoolean("drones"));
            return;
        }
        String lasers = "Lasers: " + (access.getBoolean("lasers") ? "ON" : "OFF");
        int lasersW = OsDraw.buttonWidth(font, lasers);
        if (OsDraw.inside(mx, my, a.x() + dronesW + 6, a.y(), lasersW, OsDraw.BUTTON_H)) {
            flag("lasers", !access.getBoolean("lasers"));
            return;
        }
        String cameras = "Cameras: " + (access.getBoolean("cameras") ? "ON" : "OFF");
        if (OsDraw.inside(mx, my, a.x() + dronesW + lasersW + 12, a.y(), OsDraw.buttonWidth(font, cameras), OsDraw.BUTTON_H)) {
            flag("cameras", !access.getBoolean("cameras"));
            return;
        }
        // De aan/uit-knop van een torentje.
        List<Row> rows = rows();
        int y = listTop(a) + 2;
        int bottom = a.y() + a.h();
        for (int i = scroll; i < rows.size() && y + height(rows.get(i)) <= bottom; i++) {
            Row row = rows.get(i);
            if (row.device() != null && row.device().getString("k").equals("turret")) {
                boolean on = row.device().getBoolean("on");
                String label = on ? "Turn off" : "Turn on";
                int w = OsDraw.buttonWidth(font, label);
                if (OsDraw.inside(mx, my, a.x() + a.w() - w - 4, y + 2, w, OsDraw.BUTTON_H)) {
                    CompoundTag args = new CompoundTag();
                    args.putInt("x", row.device().getInt("x"));
                    args.putInt("y", row.device().getInt("y"));
                    args.putInt("z", row.device().getInt("z"));
                    args.putBoolean("on", !on);
                    screen.send("dev_toggle", args);
                    return;
                }
            }
            y += height(row);
        }
    }

    void mouseScrolled(double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
    }

    private void flag(String name, boolean on) {
        CompoundTag args = new CompoundTag();
        args.putString("name", name);
        args.putBoolean("on", on);
        screen.send("flag", args);
    }
}
