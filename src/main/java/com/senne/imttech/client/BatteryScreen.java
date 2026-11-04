package com.senne.imttech.client;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.client.hud.PowerReadout;
import com.senne.imttech.network.BatteryRequestPayload;
import com.senne.imttech.network.BatteryStatsPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Het scherm van de Power Battery (hoofdstuk 47, jouw keuze: opgewekt / verbruikt / tekort per tick, opslag en
 * capaciteit, een grafiek van de laatste minuten, en de lijst van generatoren en apparaten). Dit is sinds dit hoofdstuk
 * de enige plek waar je het hele stroomnet ziet: een zonnepaneel zegt alleen nog wat het zelf maakt.
 *
 * <pre>
 *  POWER BATTERY  5 x 4 x 5, 27 cells                 [/tick]
 *  STORED  [==========      ]  123,456 / 27,000,000 FE (0%)
 *  Makes 48 per tick    Uses 31 per tick    Short 0
 *  Enough: 17 per tick to spare                 +--------------+
 *  GENERATORS                                   |   grafiek    |
 *   Solar Panel x2     48 per tick              |  5 minuten   |
 *  DEVICES                                      +--------------+
 *   Laser Emitter x3   30 per tick
 * </pre>
 * Elke tick vraagt het scherm nieuwe cijfers aan de server (BatteryRequestPayload), zodat de opslag echt loopt.
 */
public class BatteryScreen extends Screen {
    private static final int W = 300;
    private static final int H = 228;
    private static final int GRAPH_W = 120;
    private static final int GRAPH_H = 64;

    private BatteryStatsPayload stats;
    private int ticks;

    public BatteryScreen(BatteryStatsPayload stats) {
        super(Component.translatable("battery.imttech.title"));
        this.stats = stats;
    }

    /** Nieuwe cijfers van de server. */
    public void update(BatteryStatsPayload fresh) {
        if (fresh.pos().equals(stats.pos())) {
            stats = fresh;
        }
    }

    public net.minecraft.core.BlockPos pos() {
        return stats.pos();
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        addRenderableWidget(PowerUnit.button(x + W - 8 - 34, y + 6, 34, 12));
    }

    /** Elke tick nieuwe cijfers vragen (4 november: zo zie je de opslag echt lopen). */
    @Override
    public void tick() {
        super.tick();
        ticks++;
        PacketDistributor.sendToServer(new BatteryRequestPayload(stats.pos()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        g.fill(x, y, x + W, y + H, HudStyle.argb(240, 0x0E1216));
        g.renderOutline(x, y, W, H, HudStyle.argb(255, 0x465460));
        PowerUnit unit = PowerUnit.current();
        int white = HudStyle.argb(230, HudStyle.WHITE);
        int grey = HudStyle.argb(170, HudStyle.WHITE);
        int cyan = HudStyle.argb(255, HudStyle.CYAN);
        int orange = HudStyle.argb(255, HudStyle.ORANGE);

        // Kop: de naam, de maat en de cellen.
        g.drawString(font, Component.translatable("battery.imttech.title").getString().toUpperCase(), x + 8, y + 8, cyan, false);
        String size = stats.formed() ? stats.size() + ", " + stats.cells() + (stats.cells() == 1 ? " cell" : " cells") : "";
        g.drawString(font, size, x + 8 + font.width("POWER BATTERY") + 8, y + 8, grey, false);
        int line = y + 24;
        if (!stats.formed()) {
            // Geen batterij: waarom niet, en hoe het hoort.
            for (FormattedCharSequence part : font.split(Component.literal(stats.problem()), W - 16)) {
                g.drawString(font, part, x + 8, line, HudStyle.argb(255, HudStyle.RED), false);
                line += 10;
            }
            line += 4;
            for (FormattedCharSequence part : font.split(Component.translatable("battery.imttech.howto"), W - 16)) {
                g.drawString(font, part, x + 8, line, grey, false);
                line += 10;
            }
            return;
        }
        // De opslag: een balk met de getallen.
        g.drawString(font, "STORED", x + 8, line, orange, false);
        int barX = x + 52;
        int barW = W - 60;
        g.fill(barX, line - 1, barX + barW, line + 9, HudStyle.argb(255, 0x0A0E12));
        g.renderOutline(barX, line - 1, barW, 10, HudStyle.argb(255, 0x465460));
        float fraction = stats.capacity() <= 0 ? 0 : (float) ((double) stats.energy() / stats.capacity());
        int fill = Math.round((barW - 2) * Math.min(1.0f, fraction));
        if (fill > 0) {
            g.fillGradient(barX + 1, line, barX + 1 + fill, line + 8, HudStyle.argb(255, 0xFFE07F), HudStyle.argb(255, 0xB56B12));
        }
        line += 12;
        String stored = String.format("%,d / %,d FE (%d%%)", stats.energy(), stats.capacity(), Math.round(fraction * 100));
        g.drawString(font, stored, x + 52, line, white, false);
        line += 14;
        // Links: maakt, gebruikt en tekort, elk op een eigen regel (zo lopen ze nooit over elkaar), en het oordeel.
        int leftW = W - 16 - GRAPH_W - 8;
        line = y + 50;
        g.drawString(font, fit(Component.translatable("screen.imttech.grid.makes", unit.rate(stats.made())).getString(), leftW),
                x + 8, line, white, false);
        g.drawString(font, fit(Component.translatable("screen.imttech.grid.uses", unit.rate(stats.used())).getString(), leftW),
                x + 8, line + 10, white, false);
        g.drawString(font, fit(Component.translatable("battery.imttech.short", unit.rate(stats.missed())).getString(), leftW),
                x + 8, line + 20, stats.missed() > 0.005f ? HudStyle.argb(255, HudStyle.RED) : white, false);
        line += 32;
        // Het oordeel, zoals vroeger naast het zonnepaneel (hooguit drie regels: de rest valt weg).
        Component verdict;
        int colour;
        float made = stats.made();
        float used = stats.used();
        if (stats.missed() > 0.005f) {
            verdict = Component.translatable("screen.imttech.grid.short", unit.rate(stats.missed()));
            colour = HudStyle.RED;
        } else if (made + 0.005f >= used) {
            verdict = Component.translatable("screen.imttech.grid.enough", unit.rate(made - used));
            colour = HudStyle.GREEN;
        } else if (stats.capacity() > 0 && stats.energy() >= stats.capacity() * 0.95) {
            verdict = Component.translatable("screen.imttech.grid.full");
            colour = HudStyle.GREEN;
        } else {
            verdict = Component.translatable("screen.imttech.grid.draining", unit.rate(used - made),
                    PowerReadout.timeLeft((int) Math.min(Integer.MAX_VALUE, stats.energy()), (used - made) * 20));
            colour = HudStyle.ORANGE;
        }
        List<FormattedCharSequence> parts = font.split(verdict, leftW);
        for (int i = 0; i < Math.min(3, parts.size()); i++) {
            g.drawString(font, parts.get(i), x + 8, line, HudStyle.argb(255, colour), false);
            line += 10;
        }
        // De grafiek rechts, naast de cijfers.
        graph(g, x + W - 8 - GRAPH_W, y + 50, unit);
        // De lijsten eronder, over de volle breedte: de generatoren, dan de apparaten.
        int listY = y + 120;
        listY = list(g, x + 8, listY, W - 16, "GENERATORS", stats.generators(), unit, 0x9ED8FF);
        list(g, x + 8, listY + 2, W - 16, "DEVICES", stats.devices(), unit, 0xF1E87C);
    }

    /** Een lijstje: een kopje en per regel "Naam x2  48 per tick". Geeft de y eronder. */
    private int list(GuiGraphics g, int x, int y, int width, String title, List<BatteryStatsPayload.Entry> entries,
                     PowerUnit unit, int colour) {
        g.drawString(font, title, x, y, HudStyle.argb(255, HudStyle.ORANGE), false);
        y += 10;
        if (entries.isEmpty()) {
            g.drawString(font, Component.translatable("battery.imttech.none"), x + 4, y, HudStyle.argb(170, HudStyle.WHITE), false);
            return y + 10;
        }
        int max = Math.min(entries.size(), 3);
        for (int i = 0; i < max; i++) {
            BatteryStatsPayload.Entry entry = entries.get(i);
            String name = entry.name() + (entry.count() > 1 ? " x" + entry.count() : "");
            String rate = unit.rate(entry.perTick()).getString();
            int rateW = font.width(rate);
            String fitted = fit(name, width - rateW - 8);
            g.drawString(font, fitted, x + 4, y, HudStyle.argb(255, colour), false);
            g.drawString(font, rate, x + width - rateW, y, HudStyle.argb(230, HudStyle.WHITE), false);
            y += 10;
        }
        if (entries.size() > max) {
            g.drawString(font, "+" + (entries.size() - max) + " more", x + 4, y, HudStyle.argb(170, HudStyle.WHITE), false);
            y += 10;
        }
        return y;
    }

    private String fit(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String out = text;
        while (!out.isEmpty() && font.width(out + "...") > width) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "...";
    }

    /** De laatste 5 minuten: gemaakt (groen) en gebruikt (oranje), een punt per 5 seconden. */
    private void graph(GuiGraphics g, int x, int y, PowerUnit unit) {
        g.fill(x, y, x + GRAPH_W, y + GRAPH_H, HudStyle.argb(255, 0x0A0E12));
        g.renderOutline(x, y, GRAPH_W, GRAPH_H, HudStyle.argb(255, 0x465460));
        int[] made = stats.historyMade();
        int[] used = stats.historyUsed();
        int top = 1;
        for (int v : made) {
            top = Math.max(top, v);
        }
        for (int v : used) {
            top = Math.max(top, v);
        }
        int n = Math.max(made.length, used.length);
        if (n < 2) {
            g.drawString(font, Component.translatable("screen.imttech.grid.measuring"), x + 4, y + GRAPH_H / 2 - 4,
                    HudStyle.argb(170, HudStyle.WHITE), false);
        } else {
            plot(g, x, y, made, top, HudStyle.argb(255, HudStyle.GREEN));
            plot(g, x, y, used, top, HudStyle.argb(255, HudStyle.ORANGE));
        }
        g.pose().pushPose();
        g.pose().translate(x + 2, y + 2, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(font, "5 min   " + unit.rate(top / 100.0).getString(), 0, 0, HudStyle.argb(170, HudStyle.WHITE), false);
        g.pose().popPose();
    }

    private void plot(GuiGraphics g, int x, int y, int[] values, int top, int colour) {
        int inner = GRAPH_H - 12;
        int slots = com.senne.imttech.battery.BatteryPortBlockEntity.HISTORY - 1;
        for (int i = 1; i < values.length; i++) {
            int x0 = x + 2 + (i - 1) * (GRAPH_W - 4) / slots;
            int x1 = x + 2 + i * (GRAPH_W - 4) / slots;
            int y0 = y + GRAPH_H - 2 - values[i - 1] * inner / top;
            int y1 = y + GRAPH_H - 2 - values[i] * inner / top;
            // Een lijntje van (x0, y0) naar (x1, y1), met kleine rechthoekjes.
            int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
            for (int s = 0; s <= steps; s++) {
                int px = x0 + (x1 - x0) * s / steps;
                int py = y0 + (y1 - y0) * s / steps;
                g.fill(px, py, px + 1, py + 1, colour);
            }
        }
    }
}
