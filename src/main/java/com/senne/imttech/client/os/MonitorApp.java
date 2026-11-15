package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.Datacenter;
import com.senne.imttech.logic.LogicStats;
import com.senne.imttech.logic.RackBay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * De app Monitor op de Security OS-desktop (jouw wens, 8 oktober: "ik wil dat er stats af te lezen zijn
 * van usage van de RAM, CPU, SSD"). Zoals Taakbeheer in Windows:
 *
 * <pre>
 *  +- CPU ---- 12% +- BRAIN --- 0% +- RAM ----- 2% +- SSD ---- <1% +- TEMP --- 48° +
 *  | ..|||..|..    | ...           | ._.._.         | #              | ___---'''      |
 *  | 4 CPU, 2 GPU  | 6 power       | 1 GB / 48 GB   | 4 MB / 2 TB    | > 61°          |
 *  +---------------+---------------+----------------+----------------+----------------+
 *  RACKS (3)   click one for its stats   | rack1                          ONLINE
 *  o rack1  [C][G][R][S][F][F]      31°  | 12 64 -30
 *  o #2 12 65 -30  [C][R]   +1 fan  48°  | [C][G]  Temp 31° > 35°
 *  ...                                   | [R][S]  Heat 9 W, cooling 24 W
 *                                        | [F][F]  Cool, also at full load
 * </pre>
 * Hoofdstuk 64 (jouw wens van 8 november): het rack heeft geen eigen scherm meer, "je kunt dit enkel op de monitor zien".
 * Geef een rack een naam (sneak + rechtsklik op het rack, bijvoorbeeld "rack1") en hij staat hier onder die naam; klik
 * erop en rechts staan zijn eigen cijfers: hoe hij eruitziet (welk onderdeel in welk vakje), zijn temperatuur, warmte en
 * koeling, slijtage, en wat je moet doen. Zonder gekozen rack staat rechts de hele server: hoeveel CPU's en GPU's, of er
 * genoeg RAM is (per 10 CPU's of GPU's 64 GB; een SSD is alleen opslag), hoe snel de diagrammen en I.M.T zijn, de koeling
 * en wat er bezig is. RAM en SSD staan in MB, GB en TB.
 *
 * De tegels bovenaan (jouw opmerking, ook 8 november: "laat zien hoeveel er in totaal is en hoeveel er al in gebruik is,
 * bv. 100 GB RAM, 1% gebruik: 1 GB"): rechtsboven hoeveel procent er nu in gebruik is, eronder hoeveel dat is van het
 * totaal ("1 GB / 48 GB"). Wat de CPU's en GPU's nodig hebben om vlot te draaien staat rechts bij de server.
 *
 * Alles komt uit de momentopname van de server (het stuk "server", zie LogicServer.snapshot), die
 * twee keer per seconde ververst. De grafiekjes tonen de laatste minuut (één streepje per seconde).
 */
final class MonitorApp {
    private static final int TILE_H = 48;
    private static final int GAP = 4;
    private static final int ROW = 11;
    /** De kleuren van de tegels en de onderdelen. */
    private static final int CPU = HudStyle.CYAN;
    private static final int BRAIN = 0xB89CFF;
    private static final int RAM = HudStyle.GREEN;
    private static final int SSD = 0xE8D060;
    private static final int FAN = 0xA0B4BC;

    private final SecurityOsScreen screen;
    private int scroll;
    /** Het rack waarvan je rechts de cijfers ziet (waar het staat), of null: dan de hele server. */
    private @Nullable BlockPos selected;
    /** Waar de lijst met racks het laatst getekend werd (voor de muis): links, boven, breedte, zichtbare rijen. */
    private int listX;
    private int listY;
    private int listW;
    private int listRows;

    MonitorApp(SecurityOsScreen screen) {
        this.screen = screen;
    }

    private CompoundTag server() {
        return screen.data().getCompound("server");
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my, float partialTick) {
        CompoundTag server = server();
        if (server.getInt("racks") == 0) {
            g.drawString(font, "No datacenter yet.", a.x(), a.y() + 2, HudStyle.argb(255, HudStyle.WHITE), false);
            g.drawString(font, "Place server racks near this core and click a CPU or GPU,", a.x(), a.y() + 16,
                    HudStyle.argb(255, OsDraw.GREY), false);
            g.drawString(font, "RAM, an SSD and a fan into their bays.", a.x(), a.y() + 27, HudStyle.argb(255, OsDraw.GREY), false);
            listRows = 0;
            return;
        }
        boolean online = server.getBoolean("online");
        int tileW = (a.w() - 4 * GAP) / 5;
        int x = a.x();
        int y = a.y();
        // Elke tegel: rechtsboven hoeveel procent er nu in gebruik is, eronder hoeveel er in totaal is (hoofdstuk 64).
        // CPU: hoe hard de CPU's en GPU's werken voor je diagrammen, van hoeveel chips.
        tile(g, font, x, y, tileW, "CPU", server.getInt("cpuUse") + "%", CPU, server.getIntArray("hCpu"), 100,
                server.getInt("cpu") + " CPU, " + server.getInt("gpu") + " GPU");
        x += tileW + GAP;
        // Het brein van I.M.T: hoe hard het hier werkt, van hoeveel breinkracht.
        tile(g, font, x, y, tileW, "BRAIN", server.getInt("brainUse") + "%", BRAIN, server.getIntArray("hBrain"), 100,
                number(server.getDouble("brainPower")) + " power");
        x += tileW + GAP;
        // RAM: wat er nu in gebruik is, van het totaal ("1 GB / 48 GB"), met een grafiekje van de laatste minuut.
        double ram = server.getDouble("ramMb");
        double ramUsed = server.getDouble("ramUsedMb");
        tile(g, font, x, y, tileW, "RAM", percent(ramUsed, ram), ram <= 0 ? HudStyle.RED : RAM, server.getIntArray("hRam"), 100,
                ram <= 0 ? "no RAM" : compact(ramUsed) + " / " + compact(ram));
        x += tileW + GAP;
        // SSD: wat er op staat (je diagrammen en backups), van het totaal; een balk, want dat verandert maar traag.
        storageTile(g, font, x, y, tileW, server.getDouble("ssdUsedMb"), server.getDouble("ssdMb"));
        x += tileW + GAP;
        // Temperatuur: van het heetste rack.
        double temp = server.getDouble("temp");
        double target = server.getDouble("target");
        int tempColour = colour(temp);
        tile(g, font, x, y, tileW, "TEMP", Datacenter.degrees(temp), tempColour, server.getIntArray("hTemp"),
                Math.max((int) ImtConfig.Logic.FIRE_TEMPERATURE, max(server.getIntArray("hTemp"))),
                server.getBoolean("rising") ? "rising (no fan)"
                        : Math.abs(target - temp) >= 1.0 ? "> " + Datacenter.degrees(target) : "steady");

        // Onder de tegels: links de racks, rechts het gekozen rack of de hele server.
        int top = a.y() + TILE_H + 6;
        int splitX = a.x() + (int) (a.w() * 0.55);
        List<Tag> racks = server.getList("racksInfo", Tag.TAG_COMPOUND);
        CompoundTag chosen = chosen(racks);
        renderRacks(g, font, a.x(), top, splitX - 6 - a.x(), a.y() + a.h() - top, racks, chosen, mx, my);
        if (chosen != null) {
            renderRack(g, font, splitX, top, a.x() + a.w() - splitX, racks.indexOf(chosen), chosen);
        } else {
            renderSide(g, font, splitX, top, a.x() + a.w() - splitX, server, online);
        }
    }

    /** Het gekozen rack in de lijst (op zijn plek), of null (niets gekozen, of het is er niet meer). */
    private @Nullable CompoundTag chosen(List<Tag> racks) {
        if (selected == null) {
            return null;
        }
        for (Tag element : racks) {
            CompoundTag rack = (CompoundTag) element;
            if (pos(rack).equals(selected)) {
                return rack;
            }
        }
        return null;
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

    /**
     * De tegel SSD (hoofdstuk 64): rechtsboven hoeveel procent er in gebruik is, een balk, en eronder hoeveel dat is van het
     * totaal ("4 MB / 2 TB").
     */
    private static void storageTile(GuiGraphics g, Font font, int x, int y, int w, double used, double total) {
        tileFrame(g, font, x, y, w, "SSD", percent(used, total), total <= 0 ? HudStyle.RED : SSD);
        int barX = x + 4;
        int barW = w - 8;
        int barY = y + 18;
        double fraction = total <= 0 ? 0.0 : Math.min(1.0, used / total);
        g.fill(barX, barY, barX + barW, barY + 12, HudStyle.argb(255, 0x0A141B));
        int filled = (int) Math.round(barW * fraction);
        if (filled == 0 && used > 0) {
            filled = 1; // er staat iets op: een streepje, ook al is het minder dan een procent
        }
        g.fill(barX, barY, barX + filled, barY + 12, HudStyle.argb(200, SSD));
        small(g, font, total <= 0 ? "no SSD" : compact(used) + " / " + compact(total), x + 4, y + TILE_H - 9,
                HudStyle.argb(255, OsDraw.GREY));
    }

    /** Hoeveel procent {@code used} van {@code total} is: "2%", en "<1%" als er wel iets in gebruik is maar minder dan 1%. */
    private static String percent(double used, double total) {
        if (total <= 0) {
            return "-";
        }
        long percent = Math.round(100.0 * used / total);
        return percent == 0 && used > 0 ? "<1%" : percent + "%";
    }

    /**
     * MB, GB of TB, kort genoeg voor een tegel: één cijfer achter de komma onder de 10 ("1.5 GB"), anders hele getallen
     * ("25 GB", "1.9 TB" blijft "1.9 TB").
     */
    private static String compact(double mb) {
        String unit = mb >= 1_000_000 ? " TB" : mb >= 1000 ? " GB" : " MB";
        double value = mb >= 1_000_000 ? mb / 1_000_000 : mb >= 1000 ? mb / 1000 : mb;
        if (value >= 10) {
            return Math.round(value) + unit;
        }
        double rounded = Math.round(value * 10) / 10.0;
        return (rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded)) + unit;
    }

    /** Het kader van een tegel, met de titel links en de waarde rechts. */
    private static void tileFrame(GuiGraphics g, Font font, int x, int y, int w, String title, String value, int colour) {
        g.fill(x, y, x + w, y + TILE_H, HudStyle.argb(255, 0x0E1A24));
        HudStyle.brackets(g, x, y, x + w, y + TILE_H, 4, HudStyle.argb(255, OsDraw.DIM));
        small(g, font, title, x + 4, y + 4, HudStyle.argb(255, OsDraw.GREY));
        g.drawString(font, value, x + w - 4 - font.width(value), y + 3, HudStyle.argb(255, colour), false);
    }

    /** De racks van de server (met hun naam), elk met zijn onderdelen als gekleurde blokjes; klik er een aan. */
    private void renderRacks(GuiGraphics g, Font font, int x, int y, int w, int h, List<Tag> racks, @Nullable CompoundTag chosen,
                             int mx, int my) {
        g.drawString(font, "RACKS (" + racks.size() + ")", x, y, HudStyle.argb(255, HudStyle.CYAN), false);
        int listTop = y + 12;
        int rows = Math.max(1, (h - 12) / ROW);
        listX = x;
        listY = listTop;
        listW = w;
        listRows = rows;
        // De onderdelen beginnen voor alle racks op dezelfde plek: na de langste naam.
        int labelWidth = 0;
        for (int i = 0; i < racks.size(); i++) {
            labelWidth = Math.max(labelWidth, font.width(label(i, (CompoundTag) racks.get(i))));
        }
        int partsX = x + 6 + Math.min((int) Math.ceil(labelWidth * 0.75f), w / 2) + 5;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, racks.size() - rows)));
        if (racks.size() > rows) {
            String more = (scroll + 1) + "-" + Math.min(racks.size(), scroll + rows) + " of " + racks.size() + " (scroll)";
            small(g, font, more, x + w - (int) Math.ceil(font.width(more) * 0.75f), y + 1, HudStyle.argb(255, OsDraw.GREY));
        } else {
            String hint = chosen == null ? "click one for its stats" : "click it again: whole server";
            small(g, font, hint, x + w - (int) Math.ceil(font.width(hint) * 0.75f), y + 1, HudStyle.argb(255, OsDraw.GREY));
        }
        for (int i = scroll; i < racks.size() && i - scroll < rows; i++) {
            CompoundTag rack = (CompoundTag) racks.get(i);
            int ry = listTop + (i - scroll) * ROW;
            boolean isChosen = rack == chosen;
            if (isChosen) {
                g.fill(x - 1, ry, x + w, ry + ROW - 1, HudStyle.argb(255, 0x16303C));
            } else if (OsDraw.inside(mx, my, x, ry, w, ROW - 1)) {
                g.fill(x - 1, ry, x + w, ry + ROW - 1, HudStyle.argb(255, 0x10222C));
            }
            String status = rack.getString("status");
            boolean burning = rack.getInt("burn") > 0;
            int dot = burning ? HudStyle.RED : "ONLINE".equals(status) ? HudStyle.GREEN : HudStyle.ORANGE;
            g.fill(x, ry + 3, x + 3, ry + 6, HudStyle.argb(255, dot));
            String label = OsDraw.fitRight(font, label(i, rack), (int) ((partsX - x - 11) / 0.75f));
            small(g, font, label, x + 6, ry + 2, HudStyle.argb(255, rack.getString("name").isEmpty() ? OsDraw.GREY : HudStyle.WHITE));
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
    }

    /**
     * Rechts: één rack (hoofdstuk 64), zoals je het in de wereld ziet (de acht vakjes), met zijn temperatuur, warmte en
     * koeling, wat erin zit en hoe versleten, en wat je moet doen.
     */
    private static void renderRack(GuiGraphics g, Font font, int x, int y, int w, int index, CompoundTag rack) {
        String name = rack.getString("name");
        g.drawString(font, OsDraw.fitRight(font, name.isEmpty() ? "Rack #" + (index + 1) : name, w - 50), x, y,
                HudStyle.argb(255, HudStyle.CYAN), false);
        String status = rack.getString("status");
        boolean burning = rack.getInt("burn") > 0;
        String state = burning ? "ON FIRE" : status.replace('_', ' ');
        int stateColour = burning ? HudStyle.RED : "ONLINE".equals(status) ? HudStyle.GREEN : HudStyle.ORANGE;
        small(g, font, state, x + w - (int) Math.ceil(font.width(state) * 0.75f), y + 1, HudStyle.argb(255, stateColour));
        small(g, font, rack.getInt("x") + " " + rack.getInt("y") + " " + rack.getInt("z")
                + (name.isEmpty() ? "  (sneak + right-click it to name it)" : ""), x, y + 11, HudStyle.argb(255, OsDraw.GREY));

        // Links het rack zelf: twee kolommen van vier vakjes, zoals op de voorkant van het blok.
        int frontY = y + 22;
        drawFront(g, font, x, frontY, rack.getList("parts", Tag.TAG_COMPOUND));
        // Rechts ervan: temperatuur, warmte en koeling.
        int tx = x + 44;
        double temp = rack.getDouble("t");
        double target = Datacenter.target(rack.getDouble("heat"), rack.getDouble("cool"));
        boolean rising = Double.isInfinite(target);
        String tempText = "Temp " + Datacenter.degrees(temp) + (rising ? "  rising"
                : Math.abs(target - temp) >= 1.0 ? " > " + Datacenter.degrees(target) : "");
        small(g, font, tempText, tx, frontY, HudStyle.argb(255, colour(rising ? Math.max(temp, ImtConfig.Logic.WARM_TEMPERATURE)
                : Math.max(temp, target))));
        small(g, font, "Heat " + watts(rack.getDouble("heat")) + " (" + watts(rack.getDouble("full")) + " busy)", tx, frontY + 9,
                HudStyle.argb(255, HudStyle.WHITE));
        small(g, font, "Cooling " + watts(rack.getDouble("cool")), tx, frontY + 18, HudStyle.argb(255, HudStyle.WHITE));
        // Wat erin zit, en hoe versleten.
        int[] counts = new int[5];
        StringBuilder wear = new StringBuilder();
        for (Tag element : rack.getList("parts", Tag.TAG_COMPOUND)) {
            CompoundTag part = (CompoundTag) element;
            String kind = part.getString("k");
            int k = switch (kind) {
                case "CPU" -> 0;
                case "GPU" -> 1;
                case "RAM" -> 2;
                case "SSD" -> 3;
                default -> 4;
            };
            counts[k]++;
            if (part.getInt("w") > 0) {
                wear.append(wear.isEmpty() ? "Worn: " : ", ").append(kind).append(" ").append(part.getInt("w")).append("%");
            }
        }
        int used = counts[0] + counts[1] + counts[2] + counts[3] + counts[4];
        small(g, font, used + " of " + ImtConfig.Logic.RACK_SLOTS + " bays used", tx, frontY + 27, HudStyle.argb(255, OsDraw.GREY));
        int line = frontY + 40;
        if (!wear.isEmpty()) {
            small(g, font, OsDraw.fitRight(font, wear.toString(), (int) (w / 0.75f)), x, line, HudStyle.argb(255, HudStyle.RED));
            line += 9;
        }
        // Wat je moet doen.
        int fans = fansNeeded(rack);
        String advice;
        int adviceColour;
        if (burning) {
            boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
            advice = "ON FIRE! Click your parts out: gone in " + rack.getInt("burn") + " s.";
            adviceColour = blink ? HudStyle.RED : HudStyle.ORANGE;
        } else if ("NO_POWER".equals(status)) {
            advice = "This rack needs power (a cable).";
            adviceColour = HudStyle.RED;
        } else if ("NOT_CONNECTED".equals(status)) {
            advice = "Not touching the main server racks.";
            adviceColour = HudStyle.ORANGE;
        } else if ("NO_NETWORK".equals(status)) {
            advice = "No network: link it to the core with a cable.";
            adviceColour = HudStyle.ORANGE;
        } else if (temp >= ImtConfig.Logic.HOT_TEMPERATURE) {
            advice = "Overheating: parts wear out!" + (fans > 0 ? " Add " + fanText(fans) + "." : "");
            adviceColour = HudStyle.RED;
        } else if (temp >= ImtConfig.Logic.WARM_TEMPERATURE) {
            advice = "Too hot: the whole server at half speed." + (fans > 0 ? " Add " + fanText(fans) + "." : "");
            adviceColour = HudStyle.RED;
        } else if (rising) {
            advice = "No fan: it keeps getting warmer. Add " + fanText(Math.max(1, fans)) + ".";
            adviceColour = HudStyle.ORANGE;
        } else if (fans > 0) {
            advice = "Add " + fanText(fans) + ": too hot when busy.";
            adviceColour = HudStyle.ORANGE;
        } else {
            advice = "Cool enough, also at full load.";
            adviceColour = HudStyle.GREEN;
        }
        wrapped(g, font, advice, x, line, w, adviceColour);
    }

    /**
     * De voorkant van een rack in het klein: twee kolommen, bovenaan de CPU's/GPU's, dan RAM/SSD, onderaan vier fans (zie
     * RackBay). Een leeg vakje is donker.
     */
    private static void drawFront(GuiGraphics g, Font font, int x, int y, List<Tag> parts) {
        String[] bays = new String[ImtConfig.Logic.RACK_SLOTS];
        int[] wear = new int[ImtConfig.Logic.RACK_SLOTS];
        for (Tag element : parts) {
            CompoundTag part = (CompoundTag) element;
            int slot = part.getInt("s");
            if (slot >= 0 && slot < bays.length) {
                bays[slot] = part.getString("k");
                wear[slot] = part.getInt("w");
            }
        }
        int cellW = 17;
        int[] rowY = {0, 9, 18, 26};
        int[] rowH = {8, 8, 7, 7};
        g.fill(x - 2, y - 2, x + 2 * cellW + 3, y + 33, HudStyle.argb(255, 0x15171C));
        HudStyle.brackets(g, x - 2, y - 2, x + 2 * cellW + 3, y + 33, 3, HudStyle.argb(255, OsDraw.DIM));
        for (int slot = 0; slot < bays.length; slot++) {
            int cx = x + (slot % 2) * (cellW + 1);
            int row = RackBay.row(slot);
            int cy = y + rowY[row];
            String kind = bays[slot];
            if (kind == null) {
                g.fill(cx, cy, cx + cellW, cy + rowH[row], HudStyle.argb(255, 0x0A0C10));
                continue;
            }
            int colour = partColour(kind);
            g.fill(cx, cy, cx + cellW, cy + rowH[row], HudStyle.argb(255, 0x0A141B));
            HudStyle.brackets(g, cx, cy, cx + cellW, cy + rowH[row], 2, HudStyle.argb(255, colour));
            if (row < 2) {
                small(g, font, kind, cx + 3, cy + 2, HudStyle.argb(255, colour));
            } else {
                // Een fan: streepjes zoals een rooster.
                for (int sx = cx + 3; sx < cx + cellW - 2; sx += 2) {
                    g.fill(sx, cy + 1, sx + 1, cy + rowH[row] - 1, HudStyle.argb(200, colour));
                }
            }
            if (wear[slot] > 0) {
                g.fill(cx + 1, cy + rowH[row] - 1, cx + 1 + Math.max(1, (cellW - 2) * wear[slot] / 100), cy + rowH[row],
                        HudStyle.argb(255, HudStyle.RED));
            }
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

    /** De naam van het rack ("rack1"), of zonder naam zijn nummer en waar het staat ("#2 64 -60 -78"). */
    private static String label(int index, CompoundTag rack) {
        String name = rack.getString("name");
        return name.isEmpty() ? "#" + (index + 1) + " " + rack.getInt("x") + " " + rack.getInt("y") + " " + rack.getInt("z") : name;
    }

    private static BlockPos pos(CompoundTag rack) {
        return new BlockPos(rack.getInt("x"), rack.getInt("y"), rack.getInt("z"));
    }

    private static int partColour(String kind) {
        return switch (kind) {
            case "CPU" -> CPU;
            case "GPU" -> BRAIN;
            case "RAM" -> RAM;
            case "SSD" -> SSD;
            default -> FAN;
        };
    }

    /** Een onderdeel als blokje met een letter (C, G, R, S, F); versleten = rood eronder. */
    private static void chip(GuiGraphics g, Font font, int x, int y, String kind, int wearPercent) {
        int colour = partColour(kind);
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
     * Rechts zonder gekozen rack: de hele server (CPU's en GPU's en hoeveel apparaten je diagrammen gebruiken van hoeveel ze
     * aankunnen, of het geheugen ze genoeg voedt, hoe snel de diagrammen en
     * I.M.T zijn, hoeveel plaats je diagrammen innemen), dan warmte en koeling van alle racks samen (en in welk rack de fans
     * moeten), en wat er bezig is.
     */
    private static void renderSide(GuiGraphics g, Font font, int x, int y, int w, CompoundTag server, boolean online) {
        g.drawString(font, "SERVER", x, y, HudStyle.argb(255, HudStyle.CYAN), false);
        int cpus = server.getInt("cpu");
        int gpus = server.getInt("gpu");
        // Hoofdstuk 86: hoeveel apparaten je diagrammen gebruiken, van hoeveel de CPU's en GPU's aankunnen zonder trager te
        // worden (10 per CPU, 5 per GPU). Te veel: oranje.
        int devices = server.getInt("devices");
        int deviceCap = (int) Math.floor(server.getDouble("deviceCap") + 1.0e-9);
        boolean tooMany = online && devices > deviceCap;
        long slower = Math.round(server.getDouble("slower") * 100);
        String chips = cpus + (cpus == 1 ? " CPU, " : " CPUs, ") + gpus + (gpus == 1 ? " GPU" : " GPUs")
                + ": " + devices + " / " + deviceCap + " devices" + (online && slower > 0 ? ", " + slower + "% slower" : "");
        small(g, font, OsDraw.fitRight(font, chips, (int) (w / 0.75f)), x, y + 12,
                HudStyle.argb(255, tooMany ? HudStyle.ORANGE : HudStyle.WHITE));
        // Wat de CPU's en GPU's aan RAM nodig hebben om op volle snelheid te draaien (per 10: 64 GB). Een SSD is alleen opslag.
        memoryNeed(g, font, x, y + 21, w, "RAM", server.getDouble("ramMb"), server.getDouble("ramNeedMb"));
        double ssd = server.getDouble("ssdMb");
        small(g, font, ssd <= 0 ? "No SSD: nowhere to store diagrams" : "SSD " + LogicStats.bytes(ssd) + ": only storage", x, y + 30,
                HudStyle.argb(255, ssd <= 0 ? HudStyle.ORANGE : OsDraw.GREY));
        int delay = server.getInt("brainDelay");
        double fed = server.getDouble("fed");
        // Hoofdstuk 86: kort gehouden, zodat het in de breedte past (hoeveel trager staat op de regel van de apparaten).
        String speed = online ? "Diagrams " + LogicStats.delay(server.getInt("react")) + ", I.M.T "
                + (delay < 0 ? "-" : delay == 0 ? "no delay" : String.format(Locale.ROOT, "+%.1f s", delay / 20.0))
                + (cpus + gpus > 0 && fed < 0.999 ? String.format(Locale.ROOT, " (RAM %d%%)", Math.round(fed * 100)) : "")
                : "Server offline";
        boolean slow = cpus + gpus > 0 && fed < 0.999 || online && slower > 0;
        small(g, font, OsDraw.fitRight(font, speed, (int) (w / 0.75f)), x, y + 39,
                HudStyle.argb(255, slow ? HudStyle.ORANGE : OsDraw.GREY));

        int cy = y + 51;
        g.drawString(font, "COOLING", x, cy, HudStyle.argb(255, HudStyle.CYAN), false);
        double heat = server.getDouble("heat");
        double cooling = server.getDouble("cooling");
        small(g, font, "Heat " + watts(heat) + ", cooling " + watts(cooling), x, cy + 12, HudStyle.argb(255, HudStyle.WHITE));
        // Welke racks nog fans nodig hebben ("rack1 +1"), en of er een zonder fan warmte maakt.
        List<Tag> racks = server.getList("racksInfo", Tag.TAG_COMPOUND);
        StringBuilder where = new StringBuilder();
        boolean noFan = false;
        for (int i = 0; i < racks.size(); i++) {
            CompoundTag rack = (CompoundTag) racks.get(i);
            int need = fansNeeded(rack);
            if (need > 0) {
                String name = rack.getString("name");
                where.append(where.isEmpty() ? "Fans: " : ", ").append(name.isEmpty() ? "#" + (i + 1) : name).append(" +").append(need);
            }
            noFan |= noFan(rack);
        }
        double temp = server.getDouble("temp");
        String advice;
        int adviceColour;
        if (temp >= ImtConfig.Logic.HOT_TEMPERATURE) {
            advice = "Overheating: parts wear out!";
            adviceColour = HudStyle.RED;
        } else if (temp >= ImtConfig.Logic.WARM_TEMPERATURE) {
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
        small(g, font, advice, x, cy + 21, HudStyle.argb(255, adviceColour));
        if (!where.isEmpty()) {
            small(g, font, OsDraw.fitRight(font, where.toString(), (int) (w / 0.75f)), x, cy + 30,
                    HudStyle.argb(255, HudStyle.ORANGE));
        }
        // Wat er bezig is (zoals de lijst met processen in Taakbeheer).
        int py = cy + 42;
        g.drawString(font, "RUNNING", x, py, HudStyle.argb(255, HudStyle.CYAN), false);
        py += 12;
        // Hoofdstuk 122: wiens bril aan deze serverruimte hangt (verbonden met het IP en het wachtwoord van deze kern: wie
        // het wachtwoord kent, kan dat). Ook als de server niet draait: dan weet je wie er nu zonder zit.
        List<String> linked = new ArrayList<>();
        for (Tag element : server.getList("glassesLinked", Tag.TAG_STRING)) {
            linked.add(element.getAsString());
        }
        if (!linked.isEmpty()) {
            small(g, font, OsDraw.fitRight(font, "Glasses: " + String.join(", ", linked), (int) (w / 0.75f)), x, py,
                    HudStyle.argb(255, BRAIN));
            py += 9;
        }
        if (!online) {
            small(g, font, server.getString("problem"), x, py, HudStyle.argb(255, HudStyle.ORANGE));
            return;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Tag element : server.getList("procs", Tag.TAG_STRING)) {
            counts.merge(element.getAsString(), 1, Integer::sum);
        }
        List<String> lines = new ArrayList<>();
        // Het brein van I.M.T dat hier nadenkt (voor wie).
        for (Tag element : server.getList("brainProcs", Tag.TAG_STRING)) {
            small(g, font, OsDraw.fitRight(font, "I.M.T thinking (" + element.getAsString() + ")", (int) (w / 0.75f)), x, py,
                    HudStyle.argb(255, BRAIN));
            py += 9;
        }
        // Hoofdstuk 106: wat de brillen hier vragen ("satellite strike (Senne)"), samengeteld als het vaker loopt.
        Map<String, Integer> glasses = new LinkedHashMap<>();
        for (Tag element : server.getList("glassesProcs", Tag.TAG_STRING)) {
            glasses.merge(element.getAsString(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : glasses.entrySet()) {
            String line = "I.M.T " + entry.getKey() + (entry.getValue() > 1 ? "  x" + entry.getValue() : "");
            small(g, font, OsDraw.fitRight(font, line, (int) (w / 0.75f)), x, py, HudStyle.argb(255, BRAIN));
            py += 9;
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            lines.add(entry.getKey() + (entry.getValue() > 1 ? "  x" + entry.getValue() : ""));
        }
        // Hoofdstuk 64: wat nog op de reactietijd van de server wacht, en wat echt op RAM wacht.
        int starting = server.getInt("starting");
        if (starting > 0) {
            lines.add(starting + " starting (" + LogicStats.delay(server.getInt("react")) + ")");
        }
        int waiting = server.getInt("waiting");
        if (waiting > 0) {
            lines.add(waiting + " waiting for RAM");
        }
        if (lines.isEmpty() && server.getList("brainProcs", Tag.TAG_STRING).isEmpty() && glasses.isEmpty()) {
            small(g, font, "Nothing right now", x, py, HudStyle.argb(255, OsDraw.GREY));
        }
        for (String line : lines) {
            small(g, font, OsDraw.fitRight(font, line, (int) (w / 0.75f)), x, py, HudStyle.argb(255, HudStyle.WHITE));
            py += 9;
        }
    }

    /**
     * Eén regel bij de server: hoeveel RAM er is en hoeveel de CPU's en GPU's ervan nodig hebben, groen als het genoeg is,
     * oranje als het te weinig is (dan draait alles trager).
     */
    private static void memoryNeed(GuiGraphics g, Font font, int x, int y, int w, String what, double have, double need) {
        if (need <= 0) {
            small(g, font, what + " " + LogicStats.bytes(have) + " (no CPU or GPU yet)", x, y, HudStyle.argb(255, OsDraw.GREY));
            return;
        }
        boolean enough = have + 0.001 >= need;
        String text = what + " " + LogicStats.bytes(have) + ", need " + LogicStats.bytes(need) + (enough ? "" : ": too little");
        small(g, font, OsDraw.fitRight(font, text, (int) (w / 0.75f)), x, y,
                HudStyle.argb(255, enough ? HudStyle.GREEN : HudStyle.ORANGE));
    }

    // ================================================================ Muis

    void mouseScrolled(double amount) {
        scroll = Math.max(0, scroll - (int) Math.signum(amount));
    }

    /** Een klik op een rack in de lijst: dat rack rechts tonen (nog eens klikken: weer de hele server). */
    void mouseClicked(Font font, SecurityOsScreen.Area a, int mx, int my) {
        if (listRows <= 0 || mx < listX || mx >= listX + listW || my < listY || my >= listY + listRows * ROW) {
            return;
        }
        int index = scroll + (my - listY) / ROW;
        List<Tag> racks = server().getList("racksInfo", Tag.TAG_COMPOUND);
        if (index < 0 || index >= racks.size()) {
            return;
        }
        BlockPos clicked = pos((CompoundTag) racks.get(index));
        selected = clicked.equals(selected) ? null : clicked;
    }

    /** Alleen voor de rondleiding en de showcase: het rack met deze naam kiezen (null of onbekend = de hele server). */
    void tourSelect(@Nullable String name) {
        selected = null;
        if (name == null) {
            return;
        }
        for (Tag element : server().getList("racksInfo", Tag.TAG_COMPOUND)) {
            CompoundTag rack = (CompoundTag) element;
            if (rack.getString("name").equalsIgnoreCase(name)) {
                selected = pos(rack);
            }
        }
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

    /** Kleine tekst over meerdere regels, zodat hij in {@code w} pixels past. */
    private static void wrapped(GuiGraphics g, Font font, String text, int x, int y, int w, int colour) {
        for (net.minecraft.util.FormattedCharSequence line : font.split(net.minecraft.network.chat.Component.literal(text),
                (int) (w / 0.75f))) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            g.drawString(font, line, 0, 0, HudStyle.argb(255, colour), false);
            g.pose().popPose();
            y += 9;
        }
    }

    /** Groen als het koel is, geel als het bijna warm is, oranje als het warm is, rood als het heet is. */
    private static int colour(double temperature) {
        if (temperature >= ImtConfig.Logic.HOT_TEMPERATURE) {
            return HudStyle.RED;
        }
        if (temperature >= ImtConfig.Logic.WARM_TEMPERATURE) {
            return HudStyle.ORANGE;
        }
        return temperature >= LogicStats.coolAgainTemperature() ? SSD : HudStyle.GREEN;
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
