package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.logic.Datacenter;
import com.senne.starktech.logic.LogicBalance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * De app Monitor op de Stark OS-desktop (jouw wens, 8 oktober: "ik wil dat er stats af te lezen zijn
 * van usage van de RAM, CPU, SSD"). Zoals Taakbeheer in Windows:
 *
 * <pre>
 *  +- CPU ---- 12% +- BRAIN --- 0% +- RAM ---- 50% +- SSD ---- 77% +- TEMP --- 48° +
 *  | ..|||..|..    | ...           | ||||||||||||  | ########      | ___---'''      |
 *  | 2.5 power     | +2.0 s        | 2/4 busy      | 37/48 blocks  | > 61°          |
 *  +---------------+---------------+---------------+---------------+----------------+
 *  RACKS (3)                                 COOLING
 *  #1 12 64 -30 [C][R][S][G][F]        31°   Heat 15 W, cooling 12 W
 *  #2 12 65 -30 [C][R]          +1 fan 48°   A rack has no fan: it heats up!
 *  ...                                       Fans: #2 +1
 *                                            RUNNING
 *                                            Lab door  x2
 * </pre>
 * Elk rack heeft zijn eigen temperatuur (alleen zijn eigen fans koelen het); de tegel TEMP toont het
 * heetste rack.
 *
 * Alles komt uit de momentopname van de server (het stuk "server", zie LogicServer.snapshot), die
 * twee keer per seconde ververst. De grafiekjes tonen de laatste minuut (één streepje per seconde).
 */
final class MonitorApp {
    private static final int TILE_H = 48;
    private static final int GAP = 4;
    private static final int ROW = 11;
    /** De kleuren van de tegels. */
    private static final int CPU = HudStyle.CYAN;
    private static final int BRAIN = 0xB89CFF;
    private static final int RAM = HudStyle.GREEN;
    private static final int SSD = 0xE8D060;

    private final StarkOsScreen screen;
    private int scroll;

    MonitorApp(StarkOsScreen screen) {
        this.screen = screen;
    }

    private CompoundTag server() {
        return screen.data().getCompound("server");
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, StarkOsScreen.Area a, int mx, int my, float partialTick) {
        CompoundTag server = server();
        if (server.getInt("racks") == 0) {
            g.drawString(font, "No datacenter yet.", a.x(), a.y() + 2, HudStyle.argb(255, HudStyle.WHITE), false);
            g.drawString(font, "Place server racks near this core, with a CPU", a.x(), a.y() + 16,
                    HudStyle.argb(255, OsDraw.GREY), false);
            g.drawString(font, "or GPU, RAM and an SSD in them.", a.x(), a.y() + 27, HudStyle.argb(255, OsDraw.GREY), false);
            return;
        }
        boolean online = server.getBoolean("online");
        int tileW = (a.w() - 4 * GAP) / 5;
        int x = a.x();
        int y = a.y();
        // CPU (Logic)
        String power = number(server.getDouble("logicPower")) + " power" + (server.getBoolean("warm") ? " (half)" : "");
        tile(g, font, x, y, tileW, "CPU", server.getInt("cpuUse") + "%", CPU, server.getIntArray("hCpu"), 100, power);
        x += tileW + GAP;
        // Het brein van E.D.I.T.H.
        int delay = server.getInt("brainDelay");
        String brain = !online || delay < 0 ? "can't think" : delay == 0 ? "no delay" : String.format(Locale.ROOT, "+%.1f s", delay / 20.0);
        tile(g, font, x, y, tileW, "BRAIN", server.getInt("brainUse") + "%", BRAIN, server.getIntArray("hBrain"), 100, brain);
        x += tileW + GAP;
        // RAM
        int runs = server.getInt("runs");
        int busy = server.getInt("running") + server.getInt("waiting");
        String ram = Math.min(busy, runs) + "/" + runs + " busy" + (server.getInt("waiting") > 0 ? ", " + server.getInt("waiting") + " wait" : "");
        tile(g, font, x, y, tileW, "RAM", server.getInt("ramUse") + "%", RAM, server.getIntArray("hRam"), 100, ram);
        x += tileW + GAP;
        // SSD: geen grafiek maar een balk (hoe vol).
        int cap = server.getInt("cap");
        int used = server.getInt("used");
        int ssdPercent = cap > 0 ? Math.min(999, used * 100 / cap) : 0;
        // Geen SSD die draait (of de server draait niet): dan is er geen "vol", alleen "offline".
        tileFrame(g, font, x, y, tileW, "SSD", cap > 0 ? ssdPercent + "%" : "off", cap == 0 || ssdPercent > 100 ? HudStyle.RED : SSD);
        int barY = y + 14;
        int barW = tileW - 8;
        g.fill(x + 4, barY + 6, x + 4 + barW, barY + 16, HudStyle.argb(255, 0x0A141B));
        g.fill(x + 4, barY + 6, x + 4 + barW * Math.min(100, ssdPercent) / 100, barY + 16,
                HudStyle.argb(200, ssdPercent > 100 ? HudStyle.RED : SSD));
        small(g, font, cap > 0 ? used + "/" + cap + " blocks" : used + " blocks, no SSD", x + 4, y + TILE_H - 9,
                HudStyle.argb(255, OsDraw.GREY));
        x += tileW + GAP;
        // Temperatuur: van het heetste rack.
        double temp = server.getDouble("temp");
        double target = server.getDouble("target");
        int tempColour = colour(temp);
        tile(g, font, x, y, tileW, "TEMP", Datacenter.degrees(temp), tempColour, server.getIntArray("hTemp"),
                Math.max((int) LogicBalance.FIRE_TEMPERATURE, max(server.getIntArray("hTemp"))),
                server.getBoolean("rising") ? "rising (no fan)"
                        : Math.abs(target - temp) >= 1.0 ? "> " + Datacenter.degrees(target) : "steady");

        // Onder de tegels: links de racks, rechts koeling en wat er bezig is.
        int top = a.y() + TILE_H + 6;
        int splitX = a.x() + (int) (a.w() * 0.56);
        renderRacks(g, font, a.x(), top, splitX - 6 - a.x(), a.y() + a.h() - top, server);
        renderSide(g, font, splitX, top, a.x() + a.w() - splitX, server, online);
    }

    /** Een tegel met een titel, een groot getal, een grafiekje van de laatste minuut en een regel eronder. */
    private static void tile(GuiGraphics g, Font font, int x, int y, int w, String title, String value, int colour,
                             int[] history, int scaleMax, String below) {
        tileFrame(g, font, x, y, w, title, value, colour);
        // Het grafiekje: één streepje per seconde, de nieuwste rechts.
        int gx0 = x + 4;
        int gx1 = x + w - 4;
        int gy1 = y + TILE_H - 11;
        int gy0 = y + 13;
        g.fill(gx0, gy0, gx1, gy1, HudStyle.argb(255, 0x0A141B));
        int barW = Math.max(1, (gx1 - gx0) / Datacenter.HISTORY);
        for (int i = 0; i < history.length; i++) {
            int px = gx1 - (i + 1) * barW;
            if (px < gx0) {
                break;
            }
            int sample = history[history.length - 1 - i];
            int h = (int) Math.round((gy1 - gy0) * Math.max(0, Math.min(1.0, sample / (double) Math.max(1, scaleMax))));
            if (h > 0) {
                g.fill(px, gy1 - h, px + barW, gy1, HudStyle.argb(210, colour));
            }
        }
        small(g, font, below, x + 4, y + TILE_H - 9, HudStyle.argb(255, OsDraw.GREY));
    }

    /** Het kader van een tegel, met de titel links en de waarde rechts. */
    private static void tileFrame(GuiGraphics g, Font font, int x, int y, int w, String title, String value, int colour) {
        g.fill(x, y, x + w, y + TILE_H, HudStyle.argb(255, 0x0E1A24));
        HudStyle.brackets(g, x, y, x + w, y + TILE_H, 4, HudStyle.argb(255, OsDraw.DIM));
        small(g, font, title, x + 4, y + 4, HudStyle.argb(255, OsDraw.GREY));
        g.drawString(font, value, x + w - 4 - font.width(value), y + 3, HudStyle.argb(255, colour), false);
    }

    /** De racks van de server, elk met zijn onderdelen als gekleurde blokjes. */
    private void renderRacks(GuiGraphics g, Font font, int x, int y, int w, int h, CompoundTag server) {
        List<Tag> racks = server.getList("racksInfo", Tag.TAG_COMPOUND);
        g.drawString(font, "RACKS (" + racks.size() + ")", x, y, HudStyle.argb(255, HudStyle.CYAN), false);
        int listTop = y + 12;
        int rows = Math.max(1, (h - 12) / ROW);
        // De onderdelen beginnen voor alle racks op dezelfde plek: na de langste plaats.
        int whereWidth = 0;
        for (int i = 0; i < racks.size(); i++) {
            whereWidth = Math.max(whereWidth, font.width(where(i, (CompoundTag) racks.get(i))));
        }
        int partsX = x + 6 + (int) Math.ceil(whereWidth * 0.75f) + 5;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, racks.size() - rows)));
        for (int i = scroll; i < racks.size() && i - scroll < rows; i++) {
            CompoundTag rack = (CompoundTag) racks.get(i);
            int ry = listTop + (i - scroll) * ROW;
            String status = rack.getString("status");
            boolean burning = rack.getInt("burn") > 0;
            int dot = burning ? HudStyle.RED : "ONLINE".equals(status) ? HudStyle.GREEN : HudStyle.ORANGE;
            g.fill(x, ry + 3, x + 3, ry + 6, HudStyle.argb(255, dot));
            small(g, font, where(i, rack), x + 6, ry + 2, HudStyle.argb(255, HudStyle.WHITE));
            // Rechts: de temperatuur van dit rack, en ervoor hoeveel fans er nog in moeten.
            String degrees = Datacenter.degrees(rack.getDouble("t"));
            int right = x + w - (int) Math.ceil(font.width(degrees) * 0.75f);
            small(g, font, degrees, right, ry + 2, HudStyle.argb(255, colour(rack.getDouble("t"))));
            int need = fansNeeded(rack);
            if (need > 0) {
                String fans = "+" + fanText(need);
                right -= (int) Math.ceil(font.width(fans) * 0.75f) + 4;
                small(g, font, fans, right, ry + 2, HudStyle.argb(255, HudStyle.ORANGE));
            }
            int px = partsX;
            if (burning) {
                small(g, font, "ON FIRE " + rack.getInt("burn") + " s", px, ry + 2, HudStyle.argb(255, HudStyle.RED));
                continue;
            }
            if (!"ONLINE".equals(status)) {
                small(g, font, status.replace('_', ' '), px, ry + 2, HudStyle.argb(255, HudStyle.ORANGE));
                continue;
            }
            for (Tag element : rack.getList("parts", Tag.TAG_COMPOUND)) {
                CompoundTag part = (CompoundTag) element;
                if (px + 9 > right - 3) {
                    break;
                }
                chip(g, font, px, ry, part.getString("k"), part.getInt("w"));
                px += 10;
            }
        }
        if (racks.size() > rows) {
            small(g, font, (scroll + 1) + "-" + Math.min(racks.size(), scroll + rows) + " of " + racks.size() + " (scroll)",
                    x + w - 60, y + 1, HudStyle.argb(255, OsDraw.GREY));
        }
    }

    /** Hoeveel fans er nog in dit rack moeten (uit de momentopname, zie Datacenter.fansNeeded). */
    private static int fansNeeded(CompoundTag rack) {
        return Datacenter.fansNeeded(rack.getDouble("full"), rack.getDouble("cool"));
    }

    /** Maakt dit rack warmte zonder fan? Dan wordt het steeds warmer. */
    private static boolean noFan(CompoundTag rack) {
        return Double.isInfinite(Datacenter.target(rack.getDouble("heat"), rack.getDouble("cool")));
    }

    private static String fanText(int fans) {
        return fans == 1 ? "1 fan" : fans + " fans";
    }

    /** "#2 64 -60 -78": het nummer van het rack en waar het staat. */
    private static String where(int index, CompoundTag rack) {
        return "#" + (index + 1) + " " + rack.getInt("x") + " " + rack.getInt("y") + " " + rack.getInt("z");
    }

    /** Een onderdeel als blokje met een letter (C, G, R, S, F); versleten = rood eronder. */
    private static void chip(GuiGraphics g, Font font, int x, int y, String kind, int wearPercent) {
        int colour = switch (kind) {
            case "CPU" -> CPU;
            case "GPU" -> BRAIN;
            case "RAM" -> RAM;
            case "SSD" -> SSD;
            default -> 0xA0B4BC; // fan
        };
        g.fill(x, y, x + 9, y + 9, HudStyle.argb(255, 0x0A141B));
        HudStyle.brackets(g, x, y, x + 9, y + 9, 2, HudStyle.argb(255, colour));
        String letter = kind.substring(0, 1);
        small(g, font, letter, x + 3, y + 2, HudStyle.argb(255, colour));
        if (wearPercent > 0) {
            // Hoe versleten: een rood balkje onderaan, zo lang als de slijtage.
            g.fill(x + 1, y + 7, x + 1 + Math.max(1, 7 * wearPercent / 100), y + 8, HudStyle.argb(255, HudStyle.RED));
        }
    }

    /**
     * Rechts: warmte en koeling van alle racks samen, wat je moet doen (en in welk rack de fans moeten:
     * elk rack heeft zijn eigen fans nodig), en wat er bezig is.
     */
    private static void renderSide(GuiGraphics g, Font font, int x, int y, int w, CompoundTag server, boolean online) {
        g.drawString(font, "COOLING", x, y, HudStyle.argb(255, HudStyle.CYAN), false);
        double heat = server.getDouble("heat");
        double cooling = server.getDouble("cooling");
        small(g, font, "Heat " + watts(heat) + ", cooling " + watts(cooling), x, y + 12, HudStyle.argb(255, HudStyle.WHITE));
        // Welke racks nog fans nodig hebben ("#2 +1"), en of er een zonder fan warmte maakt.
        List<Tag> racks = server.getList("racksInfo", Tag.TAG_COMPOUND);
        StringBuilder where = new StringBuilder();
        boolean noFan = false;
        for (int i = 0; i < racks.size(); i++) {
            CompoundTag rack = (CompoundTag) racks.get(i);
            int need = fansNeeded(rack);
            if (need > 0) {
                where.append(where.isEmpty() ? "Fans: " : ", ").append("#").append(i + 1).append(" +").append(need);
            }
            noFan |= noFan(rack);
        }
        double temp = server.getDouble("temp");
        String advice;
        int adviceColour;
        if (temp >= LogicBalance.HOT_TEMPERATURE) {
            advice = "Overheating: parts wear out!";
            adviceColour = HudStyle.RED;
        } else if (temp >= LogicBalance.WARM_TEMPERATURE) {
            advice = "Too hot: half speed";
            adviceColour = HudStyle.RED;
        } else if (noFan) {
            advice = "A rack has no fan: it heats up!";
            adviceColour = HudStyle.ORANGE;
        } else if (!where.isEmpty()) {
            advice = "Add fans for full load";
            adviceColour = HudStyle.ORANGE;
        } else {
            advice = "Cool enough, also at full load";
            adviceColour = HudStyle.GREEN;
        }
        small(g, font, advice, x, y + 21, HudStyle.argb(255, adviceColour));
        if (!where.isEmpty()) {
            small(g, font, OsDraw.fitRight(font, where.toString(), (int) (w / 0.75f)), x, y + 30,
                    HudStyle.argb(255, HudStyle.ORANGE));
        }
        // Wat er bezig is (zoals de lijst met processen in Taakbeheer).
        int py = y + 42;
        g.drawString(font, "RUNNING", x, py, HudStyle.argb(255, HudStyle.CYAN), false);
        py += 12;
        if (!online) {
            small(g, font, server.getString("problem"), x, py, HudStyle.argb(255, HudStyle.ORANGE));
            return;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Tag element : server.getList("procs", Tag.TAG_STRING)) {
            counts.merge(element.getAsString(), 1, Integer::sum);
        }
        List<String> lines = new ArrayList<>();
        // Het brein van E.D.I.T.H. dat hier nadenkt (voor wie).
        for (Tag element : server.getList("brainProcs", Tag.TAG_STRING)) {
            small(g, font, OsDraw.fitRight(font, "E.D.I.T.H. thinking (" + element.getAsString() + ")", (int) (w / 0.75f)), x, py,
                    HudStyle.argb(255, BRAIN));
            py += 9;
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            lines.add(entry.getKey() + (entry.getValue() > 1 ? "  x" + entry.getValue() : ""));
        }
        int waiting = server.getInt("waiting");
        if (waiting > 0) {
            lines.add(waiting + " waiting for RAM");
        }
        if (lines.isEmpty() && server.getList("brainProcs", Tag.TAG_STRING).isEmpty()) {
            small(g, font, "Nothing right now", x, py, HudStyle.argb(255, OsDraw.GREY));
        }
        for (String line : lines) {
            small(g, font, OsDraw.fitRight(font, line, (int) (w / 0.75f)), x, py, HudStyle.argb(255, HudStyle.WHITE));
            py += 9;
        }
    }

    // ================================================================ Muis

    void mouseScrolled(double amount) {
        scroll = Math.max(0, scroll - (int) Math.signum(amount));
    }

    // ================================================================ Hulpjes

    /** Kleine tekst (75%). */
    private static void small(GuiGraphics g, Font font, String text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    /** Groen als het koel is, geel als het bijna warm is, oranje als het warm is, rood als het heet is. */
    private static int colour(double temperature) {
        if (temperature >= LogicBalance.HOT_TEMPERATURE) {
            return HudStyle.RED;
        }
        if (temperature >= LogicBalance.WARM_TEMPERATURE) {
            return HudStyle.ORANGE;
        }
        return temperature >= LogicBalance.coolAgainTemperature() ? SSD : HudStyle.GREEN;
    }

    private static int max(int[] values) {
        int max = 0;
        for (int value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private static String watts(double value) {
        return number(value) + " W";
    }

    /** 2.5 blijft "2.5", 3.0 wordt "3". */
    private static String number(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }
}
