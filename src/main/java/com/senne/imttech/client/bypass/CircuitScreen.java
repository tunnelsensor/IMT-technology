package com.senne.imttech.client.bypass;

import com.mojang.math.Axis;
import com.senne.imttech.bypass.CircuitPuzzle;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.BypassPayload;
import com.senne.imttech.registry.ModSounds;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Het stroomcircuit van een keycardlezer (hoofdstuk 12, jouw keuze): de minigame van de Bypass Kit.
 * <pre>
 *  BYPASS &gt; LAB DOOR          SECURITY CARD          0:42
 *              !
 *   CARD ==[=+][ | ][+==][ + ]
 *          [ +][==+][ |  ][==+]== ACCEPT
 * </pre>
 * Eerst kies je van welke groep van dat netwerk je een kaart namaakt (25 oktober; het raster is altijd
 * even groot en je hebt altijd even veel tijd, zoals het oude niveau 5). Dan draai je de stukjes: linksklik = rechtsom, rechtsklik = linksom. Wat aan CARD vastzit licht blauw op. Komt het
 * signaal bij ACCEPT zonder ALARM te raken, dan stuurt dit scherm de stand naar de server, die het zelf
 * narekent (zie Bypass). Raakt het signaal ALARM, dan heb je {@link ImtConfig.Bypass#ALARM_GRACE_TICKS}
 * ticks om dat stukje weg te draaien.
 *
 * Het spel loopt door terwijl dit scherm open is. Esc stopt de hack (zonder alarm).
 */
public class CircuitScreen extends Screen implements BypassScreen {
    private enum Phase { CHOOSE, PLAY, WAIT, DONE }

    /** Zo lang (ms) duurt het draaien van een stukje op het scherm. */
    private static final long TURN_MS = 110;
    /** Zo lang (ticks) blijft de uitslag staan voor het scherm dichtgaat. */
    private static final int RESULT_TICKS = 50;

    private static final int PANEL = 0x0D1318;
    private static final int TILE = 0x111A20;
    private static final int TILE_HOVER = 0x1C3340;
    private static final int WIRE_OFF = 0x56636E;
    private static final String HELP_ONE = "Lead the card signal to ACCEPT. Don't let it touch ALARM.";
    private static final String HELP_TWO = "Left click: turn right    Right click: turn left    Esc: stop";

    /** Voor de showcase: het scherm speelt zichzelf (kiest {@link #autoGroup} en draait de goede stukjes). */
    public static boolean autoPlay;
    /** Welke groep het scherm dan kiest (bestaat hij niet, dan de eerste). */
    public static String autoGroup = "";

    /** Een groep van het netwerk van de lezer, en hoe diep hij in andere groepen zit (0 = bovenaan). */
    public record Group(String name, int depth) {}

    private final BlockPos reader;
    private final String readerName;
    private final long seed;
    /** De groepen waaruit je kiest; leeg = het netwerk heeft geen groepen (dan een kaart zonder groep). */
    private final List<Group> groups;

    private Phase phase = Phase.CHOOSE;
    /** De groep van de kaart die je namaakt ("" = zonder groep). */
    private String group = "";
    /** Zoveel groepen staan er boven de eerste die je ziet (bij veel groepen scroll je). */
    private int scroll;
    private CircuitPuzzle puzzle;
    private int[] rotations;
    private CircuitPuzzle.Signal signal;
    private int ticksLeft;
    /** Hoe lang het signaal ALARM nu al raakt (ticks). */
    private int alarmTicks;
    /** Per vakje: vanaf welke hoek het nog naar zijn nieuwe stand draait, en sinds wanneer. */
    private float[] turnFrom;
    private long[] turnStart;
    /** Hoe het afliep: ok, alarm, time (of leeg). */
    private String result = "";
    private int resultTicks;
    /** De server zei "nog niet klaar" (jouw spel en de server dachten anders): even laten zien. */
    private int retryTicks;
    /** Al iets definitiefs naar de server gestuurd? Dan hoeft dichtgaan niet meer "cancel" te sturen. */
    private boolean finished;
    private int autoTicks;

    public CircuitScreen(BlockPos reader, String readerName, long seed, List<Group> groups) {
        super(Component.literal(readerName));
        this.reader = reader;
        this.readerName = readerName;
        this.seed = seed;
        this.groups = List.copyOf(groups);
    }

    @Override
    public BlockPos device() {
        return reader;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        HudStyle.dim(g, width, height);
    }

    // ================================================================ Een groep kiezen

    private static final int CHOOSE_W = 300;
    private static final int ROW_H = 20;
    /** Zoveel groepen passen er onder elkaar; zijn er meer, dan scroll je. */
    private static final int MAX_ROWS = 7;

    /** Zoveel regels staan er in de lijst: de groepen, of één regel "zonder groep". */
    private int rowCount() {
        return Math.max(1, groups.size());
    }

    private int visibleRows() {
        return Math.min(MAX_ROWS, rowCount());
    }

    private int chooseHeight() {
        return 60 + visibleRows() * ROW_H + 46;
    }

    private int chooseLeft() {
        return (width - CHOOSE_W) / 2;
    }

    private int chooseTop() {
        return (height - chooseHeight()) / 2;
    }

    /** Waar regel {@code index} van wat je ziet staat (0 = de bovenste die je ziet). */
    private int rowY(int index) {
        return chooseTop() + 50 + index * ROW_H;
    }

    /** Regel {@code row} van de lijst gekozen: het raster maken, de klok starten, en de server laten weten dat hij loopt. */
    private void choose(int row) {
        group = groups.isEmpty() ? "" : groups.get(Math.max(0, Math.min(groups.size() - 1, row))).name();
        puzzle = CircuitPuzzle.generate(seed);
        rotations = puzzle.start();
        signal = puzzle.signal(rotations);
        turnFrom = new float[puzzle.cells()];
        turnStart = new long[puzzle.cells()];
        ticksLeft = CircuitPuzzle.circuitTicks();
        phase = Phase.PLAY;
        CompoundTag args = new CompoundTag();
        args.putString("group", group);
        PacketDistributor.sendToServer(new BypassPayload(reader, "group", args));
        play(SoundEvents.IRON_TRAPDOOR_OPEN, 1.5f, 0.5f);
    }

    /** Wat bovenaan het raster staat: "SECURITY CARD", of "CARD WITHOUT A GROUP". */
    private String cardTitle() {
        return group.isEmpty() ? "CARD WITHOUT A GROUP" : group.toUpperCase(Locale.ROOT) + " CARD";
    }

    private void renderChoose(GuiGraphics g, int mouseX, int mouseY) {
        int x = chooseLeft();
        int y = chooseTop();
        int h = chooseHeight();
        g.fill(x, y, x + CHOOSE_W, y + h, HudStyle.argb(240, PANEL));
        HudStyle.brackets(g, x, y, x + CHOOSE_W, y + h, 8, HudStyle.argb(255, HudStyle.CYAN));
        g.drawString(font, "BYPASS > " + readerName.toUpperCase(Locale.ROOT), x + 10, y + 9, HudStyle.argb(255, HudStyle.CYAN), false);
        g.drawString(font, "You opened the reader. Which card do you fake?", x + 10, y + 24, HudStyle.argb(255, HudStyle.WHITE), false);
        int alarms = Math.round(ImtConfig.Bypass.CIRCUIT_ALARMS);
        String size = Math.round(ImtConfig.Bypass.CIRCUIT_COLUMNS) + " x " + Math.round(ImtConfig.Bypass.CIRCUIT_ROWS) + " wires, "
                + com.senne.imttech.config.Numbers.text(CircuitPuzzle.circuitSeconds()) + " s, " + alarms + (alarms == 1 ? " ALARM" : " ALARMS");
        g.drawString(font, "Pick a group of this network.  " + size, x + 10, y + 35, HudStyle.argb(255, 0x7F93A0), false);
        scroll = Math.max(0, Math.min(scroll, rowCount() - visibleRows()));
        for (int i = 0; i < visibleRows(); i++) {
            int ry = rowY(i);
            boolean hover = mouseX >= x + 8 && mouseX < x + CHOOSE_W - 8 && mouseY >= ry && mouseY < ry + ROW_H - 2;
            g.fill(x + 8, ry, x + CHOOSE_W - 8, ry + ROW_H - 2, HudStyle.argb(255, hover ? TILE_HOVER : TILE));
            g.fill(x + 8, ry, x + 11, ry + ROW_H - 2, HudStyle.argb(255, HudStyle.CYAN));
            int number = scroll + i + 1;
            if (number <= 9) {
                g.drawString(font, String.valueOf(number), x + 16, ry + 5, HudStyle.argb(255, 0x7F93A0), false);
            }
            if (groups.isEmpty()) {
                g.drawString(font, "A card without a group", x + 28, ry + 5, HudStyle.argb(255, HudStyle.WHITE), false);
                g.drawString(font, "(this network has no groups)", x + 150, ry + 5, HudStyle.argb(255, 0x7F93A0), false);
            } else {
                Group row = groups.get(scroll + i);
                int indent = Math.min(row.depth(), 6) * 10;
                String name = font.plainSubstrByWidth(row.name(), CHOOSE_W - 50 - indent);
                g.drawString(font, (row.depth() > 0 ? "> " : "") + name, x + 28 + indent, ry + 5, HudStyle.argb(255, HudStyle.WHITE), false);
            }
        }
        if (rowCount() > visibleRows()) {
            String more = (scroll + 1) + "-" + (scroll + visibleRows()) + " of " + rowCount() + " (scroll)";
            g.drawString(font, more, x + CHOOSE_W - 10 - font.width(more), y + 9, HudStyle.argb(255, 0x7F93A0), false);
        }
        int fy = rowY(visibleRows()) + 6;
        g.drawString(font, "The reader asks the server: is this card valid?", x + 10, fy, HudStyle.argb(255, 0x7F93A0), false);
        g.drawString(font, "Rewire it so the answer is yes.", x + 10, fy + 11, HudStyle.argb(255, 0x7F93A0), false);
        g.drawString(font, "1-9 or click: choose     Esc: stop", x + 10, fy + 25, HudStyle.argb(255, HudStyle.WHITE), false);
    }

    // ================================================================ Het raster

    /** Zoveel ruimte (pixels) heeft het paneel boven het raster (de titel, ALARM-punten) en eronder (de uitleg). */
    private static final int ABOVE = 40;
    private static final int BELOW = 50;

    /** Hoe groot één vakje is: zo groot als past, tussen 14 en 30 pixels. */
    private int cellSize() {
        int byWidth = (width - 150) / puzzle.columns();
        int byHeight = (height - ABOVE - BELOW - 6) / puzzle.rows();
        return Math.max(14, Math.min(30, Math.min(byWidth, byHeight)));
    }

    private int boardX() {
        return (width - puzzle.columns() * cellSize()) / 2;
    }

    /** Het paneel (titel, raster, uitleg) staat midden op het scherm. */
    private int boardY() {
        return (height - (ABOVE + puzzle.rows() * cellSize() + BELOW)) / 2 + ABOVE;
    }

    /** Zo breed is de breedste regel onder het raster. */
    private int widestText() {
        int widest = Math.max(font.width(HELP_ONE), font.width(HELP_TWO));
        for (String how : new String[] {"ok", "alarm", "time"}) {
            for (String line : resultText(how)) {
                widest = Math.max(widest, font.width(line));
            }
        }
        return widest;
    }

    /** Het vakje onder de muis, of -1. */
    private int cellAt(double mouseX, double mouseY) {
        int size = cellSize();
        int col = (int) Math.floor((mouseX - boardX()) / size);
        int row = (int) Math.floor((mouseY - boardY()) / size);
        if (col < 0 || row < 0 || col >= puzzle.columns() || row >= puzzle.rows()) {
            return -1;
        }
        return row * puzzle.columns() + col;
    }

    private void renderPuzzle(GuiGraphics g, int mouseX, int mouseY) {
        int size = cellSize();
        int bx = boardX();
        int by = boardY();
        int bw = puzzle.columns() * size;
        int bh = puzzle.rows() * size;
        long now = System.currentTimeMillis();
        boolean alarm = !signal.alarms().isEmpty();
        boolean blink = (now / 150) % 2 == 0;

        // Het paneel eromheen, met ruimte voor CARD, ACCEPT, de ALARM-punten en de uitleg eronder.
        int half = Math.max(bw / 2 + 64, (widestText() + 20) / 2);
        int px0 = bx + bw / 2 - half;
        int px1 = bx + bw / 2 + half;
        int py0 = by - ABOVE;
        int py1 = by + bh + BELOW;
        g.fill(px0, py0, px1, py1, HudStyle.argb(240, PANEL));
        int frame = phase == Phase.DONE ? (result.equals("ok") ? HudStyle.GREEN : HudStyle.RED)
                : alarm && blink ? HudStyle.RED : HudStyle.CYAN;
        HudStyle.brackets(g, px0, py0, px1, py1, 8, HudStyle.argb(255, frame));

        // Bovenaan: welke lezer, welke kaart, hoeveel tijd.
        g.drawString(font, "BYPASS > " + readerName.toUpperCase(Locale.ROOT), px0 + 8, py0 + 7, HudStyle.argb(255, HudStyle.CYAN), false);
        String card = cardTitle();
        g.drawString(font, card, (px0 + px1 - font.width(card)) / 2, py0 + 7, HudStyle.argb(255, HudStyle.WHITE), false);
        int seconds = (Math.max(0, ticksLeft) + 19) / 20;
        String clock = (seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
        boolean hurry = seconds <= 10 && phase == Phase.PLAY;
        g.drawString(font, clock, px1 - 8 - font.width(clock), py0 + 7,
                HudStyle.argb(255, hurry ? (blink ? HudStyle.RED : HudStyle.WHITE) : HudStyle.WHITE), false);

        // De stukjes.
        int hover = phase == Phase.PLAY ? cellAt(mouseX, mouseY) : -1;
        for (int cell = 0; cell < puzzle.cells(); cell++) {
            int x = bx + (cell % puzzle.columns()) * size;
            int y = by + (cell / puzzle.columns()) * size;
            g.fill(x, y, x + size, y + size, HudStyle.argb(255, 0x0A0F13));
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, HudStyle.argb(255, cell == hover ? TILE_HOVER : TILE));
            float angle = 0;
            if (turnStart[cell] > 0) {
                float t = (now - turnStart[cell]) / (float) TURN_MS;
                angle = t >= 1 ? 0 : turnFrom[cell] * (1 - t);
            }
            int colour = signal.lit()[cell] ? (phase == Phase.DONE && result.equals("ok") ? HudStyle.GREEN : HudStyle.CYAN) : WIRE_OFF;
            drawWires(g, x, y, size, puzzle.connections(cell, rotations), colour, angle, signal.lit()[cell]);
        }

        // CARD links (altijd aan: daar komt het signaal vandaan).
        int cardY = by + puzzle.cardRow() * size + size / 2;
        int thick = Math.max(2, size / 6);
        g.fill(bx - 14, cardY - thick / 2, bx, cardY + (thick + 1) / 2, HudStyle.argb(255, HudStyle.CYAN));
        terminal(g, bx - 50, cardY - 6, 36, "CARD", HudStyle.CYAN, true);
        // ACCEPT rechts: groen zodra het signaal er is.
        int acceptY = by + puzzle.acceptRow() * size + size / 2;
        int acceptColour = signal.accepted() ? HudStyle.GREEN : WIRE_OFF;
        g.fill(bx + bw, acceptY - thick / 2, bx + bw + 14, acceptY + (thick + 1) / 2, HudStyle.argb(255, acceptColour));
        terminal(g, bx + bw + 14, acceptY - 6, 44, "ACCEPT", signal.accepted() ? HudStyle.GREEN : 0x7F93A0, signal.accepted());
        // De ALARM-punten: rood, en knipperend als het signaal er is.
        for (CircuitPuzzle.Terminal terminal : puzzle.alarms()) {
            boolean touched = signal.alarms().contains(terminal);
            int col = terminal.cell() % puzzle.columns();
            int row = terminal.cell() / puzzle.columns();
            int cx = bx + col * size + size / 2;
            int cy = by + row * size + size / 2;
            int ax = cx + CircuitPuzzle.dx(terminal.side()) * (size / 2 + 9);
            int ay = cy + CircuitPuzzle.dy(terminal.side()) * (size / 2 + 9);
            int colour = touched && blink ? HudStyle.WHITE : HudStyle.RED;
            // Een stompje draad van de rand naar het rode kastje.
            int ex = cx + CircuitPuzzle.dx(terminal.side()) * (size / 2);
            int ey = cy + CircuitPuzzle.dy(terminal.side()) * (size / 2);
            g.fill(Math.min(ex, ax) - (thick / 2), Math.min(ey, ay) - (thick / 2),
                    Math.max(ex, ax) + (thick + 1) / 2, Math.max(ey, ay) + (thick + 1) / 2, HudStyle.argb(255, 0x5A1E1E));
            g.fill(ax - 6, ay - 6, ax + 6, ay + 6, HudStyle.argb(255, touched ? 0x6A1010 : 0x2A0C0C));
            g.renderOutline(ax - 6, ay - 6, 12, 12, HudStyle.argb(255, colour));
            g.drawString(font, "!", ax - font.width("!") / 2 + 1, ay - 4, HudStyle.argb(255, colour), false);
        }

        // Onderaan: uitleg, of hoe het afliep, of het ALARM dat aftelt.
        int ty = by + bh + 26;
        int cx = (px0 + px1) / 2;
        if (phase == Phase.DONE) {
            String[] lines = resultText(result);
            int colour = result.equals("ok") ? HudStyle.GREEN : HudStyle.RED;
            g.drawString(font, lines[0], cx - font.width(lines[0]) / 2, ty, HudStyle.argb(255, colour), false);
            g.drawString(font, lines[1], cx - font.width(lines[1]) / 2, ty + 11, HudStyle.argb(255, HudStyle.WHITE), false);
        } else if (phase == Phase.WAIT) {
            String text = "Sending the card to the server...";
            g.drawString(font, text, cx - font.width(text) / 2, ty, HudStyle.argb(255, HudStyle.CYAN), false);
        } else if (alarm) {
            float left = 1 - alarmTicks / (float) Math.max(1, Math.round(ImtConfig.Bypass.ALARM_GRACE_TICKS));
            String text = "ALARM! Turn that wire away!";
            g.drawString(font, text, cx - font.width(text) / 2, ty, HudStyle.argb(255, HudStyle.RED), false);
            HudStyle.bar(g, cx - 60, ty + 12, 120, 4, left, HudStyle.RED);
        } else if (retryTicks > 0) {
            String text = "The server saw no closed circuit. Keep going.";
            g.drawString(font, text, cx - font.width(text) / 2, ty, HudStyle.argb(255, HudStyle.ORANGE), false);
        } else {
            g.drawString(font, HELP_ONE, cx - font.width(HELP_ONE) / 2, ty, HudStyle.argb(255, HudStyle.WHITE), false);
            g.drawString(font, HELP_TWO, cx - font.width(HELP_TWO) / 2, ty + 11, HudStyle.argb(255, 0x7F93A0), false);
        }
    }

    /** Een stukje draad: een knooppunt in het midden en een staaf naar elke kant met een verbinding. */
    private static void drawWires(GuiGraphics g, int x, int y, int size, int mask, int colour, float angle, boolean lit) {
        int t = Math.max(2, size / 6);
        int c = size / 2;
        g.pose().pushPose();
        g.pose().translate(x + c, y + c, 0);
        if (angle != 0) {
            g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        }
        int a = -t / 2;
        int b = (t + 1) / 2;
        int argb = HudStyle.argb(255, colour);
        if ((mask & CircuitPuzzle.N) != 0) {
            g.fill(a, -c + 1, b, 0, argb);
        }
        if ((mask & CircuitPuzzle.S) != 0) {
            g.fill(a, 0, b, c - 1, argb);
        }
        if ((mask & CircuitPuzzle.W) != 0) {
            g.fill(-c + 1, a, 0, b, argb);
        }
        if ((mask & CircuitPuzzle.E) != 0) {
            g.fill(0, a, c - 1, b, argb);
        }
        g.fill(a - 1, a - 1, b + 1, b + 1, argb);
        if (lit && t >= 3) {
            // Een lichtere kern in een draad met stroom.
            int core = HudStyle.argb(200, 0xD8FAFF);
            if ((mask & CircuitPuzzle.N) != 0) {
                g.fill(0, -c + 1, 1, 0, core);
            }
            if ((mask & CircuitPuzzle.S) != 0) {
                g.fill(0, 0, 1, c - 1, core);
            }
            if ((mask & CircuitPuzzle.W) != 0) {
                g.fill(-c + 1, 0, 0, 1, core);
            }
            if ((mask & CircuitPuzzle.E) != 0) {
                g.fill(0, 0, c - 1, 1, core);
            }
        }
        g.pose().popPose();
    }

    /** Een kastje met een woord erin (CARD, ACCEPT). */
    private void terminal(GuiGraphics g, int x, int y, int w, String label, int colour, boolean on) {
        g.fill(x, y, x + w, y + 12, HudStyle.argb(255, on ? 0x10303A : 0x151C22));
        g.renderOutline(x, y, w, 12, HudStyle.argb(255, colour));
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 2, HudStyle.argb(255, colour), false);
    }

    private String[] resultText(String how) {
        return switch (how) {
            case "ok" -> new String[] {group.isEmpty() ? "ACCESS: the reader read a card without a group."
                    : "ACCESS: the reader read a card of \"" + group + "\".",
                    "Its server now decides what happens."};
            case "alarm" -> new String[] {"ALARM! The reader caught you.", "The owner is warned, the security hunts you."};
            default -> new String[] {"TOO SLOW: the reader raised the alarm.", "The owner is warned, the security hunts you."};
        };
    }

    // ================================================================ Tekenen en klikken

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (phase == Phase.CHOOSE) {
            renderChoose(g, mouseX, mouseY);
        } else {
            renderPuzzle(g, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (phase == Phase.CHOOSE) {
            for (int i = 0; i < visibleRows(); i++) {
                int ry = rowY(i);
                if (mouseX >= chooseLeft() + 8 && mouseX < chooseLeft() + CHOOSE_W - 8 && mouseY >= ry && mouseY < ry + ROW_H - 2) {
                    choose(scroll + i);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (phase == Phase.PLAY && (button == 0 || button == 1)) {
            int cell = cellAt(mouseX, mouseY);
            if (cell >= 0) {
                turn(cell, button == 0 ? 1 : -1);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (phase == Phase.CHOOSE && keyCode >= 49 && keyCode <= 57 && keyCode - 49 < rowCount()) { // de toetsen 1 tot 9
            choose(keyCode - 49);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (phase == Phase.CHOOSE) {
            scroll = Math.max(0, Math.min(rowCount() - visibleRows(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** De regel van deze groep (hoofdletters maken niet uit), of 0 als hij er niet is. */
    private int rowOf(String name) {
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return 0;
    }

    /** Draait een stukje een kwart (1 = rechtsom, -1 = linksom) en kijkt waar het signaal nu komt. */
    private void turn(int cell, int direction) {
        rotations[cell] = Math.floorMod(rotations[cell] + direction, 4);
        turnFrom[cell] = -90f * direction;
        turnStart[cell] = System.currentTimeMillis();
        play(ModSounds.IMT_SELECT.get(), 1.7f + 0.1f * direction, 0.25f);
        boolean wasLit = signal.lit()[cell];
        signal = puzzle.signal(rotations);
        if (signal.solved()) {
            // Klaar: de server rekent het na.
            phase = Phase.WAIT;
            resultTicks = 0;
            byte[] sent = new byte[rotations.length];
            for (int i = 0; i < rotations.length; i++) {
                sent[i] = (byte) rotations[i];
            }
            CompoundTag args = new CompoundTag();
            args.putByteArray("rot", sent);
            PacketDistributor.sendToServer(new BypassPayload(reader, "solve", args));
            play(ModSounds.IMT_CONFIRM.get(), 1.4f, 0.6f);
        } else if (!wasLit && signal.lit()[cell]) {
            play(ModSounds.IMT_BLIP.get(), 1.8f, 0.2f);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (retryTicks > 0) {
            retryTicks--;
        }
        switch (phase) {
            case CHOOSE -> {
                if (autoPlay && ++autoTicks >= 30) {
                    choose(rowOf(autoGroup));
                    autoTicks = 0;
                }
            }
            case PLAY -> {
                if (--ticksLeft <= 0) {
                    lose("time");
                    return;
                }
                if (!signal.alarms().isEmpty()) {
                    if (alarmTicks % 5 == 0) {
                        play(ModSounds.IMT_ALERT.get(), 1.6f, 0.5f);
                    }
                    if (++alarmTicks >= Math.round(ImtConfig.Bypass.ALARM_GRACE_TICKS)) {
                        lose("alarm");
                        return;
                    }
                } else {
                    alarmTicks = 0;
                }
                if (autoPlay && ++autoTicks >= 7) {
                    autoTicks = 0;
                    autoStep();
                }
            }
            case WAIT -> {
                if (++resultTicks > 100) {
                    onClose(); // geen antwoord van de server
                }
            }
            case DONE -> {
                if (++resultTicks > RESULT_TICKS) {
                    onClose();
                }
            }
        }
    }

    /** Mislukt in jouw spel (ALARM, of de tijd is om): de server krijgt het te horen en slaat alarm. */
    private void lose(String why) {
        CompoundTag args = new CompoundTag();
        args.putString("why", why);
        PacketDistributor.sendToServer(new BypassPayload(reader, "fail", args));
        finish(why);
    }

    private void finish(String how) {
        finished = true;
        result = how;
        phase = Phase.DONE;
        resultTicks = 0;
        if (how.equals("ok")) {
            play(ModSounds.IMT_CONFIRM.get(), 1.0f, 1.0f);
        } else {
            play(ModSounds.IMT_DENIED.get(), 0.8f, 1.0f);
        }
    }

    /** Het antwoord van de server: gelukt, mislukt, of "nog niet klaar". */
    @Override
    public void onResult(CompoundTag args) {
        String how = args.getString("result");
        if (phase == Phase.DONE) {
            return;
        }
        if (how.equals("retry")) {
            phase = Phase.PLAY;
            retryTicks = 60;
            return;
        }
        finish(how);
    }

    /** De server sloot de hack (je liep weg, of het apparaat is weg). */
    @Override
    public void closeFromServer() {
        finished = true;
        onClose();
    }

    @Override
    public void removed() {
        super.removed();
        if (!finished) {
            PacketDistributor.sendToServer(BypassPayload.of(reader, "cancel"));
        }
    }

    /**
     * De showcase: draai één stukje van het pad een kwart in de goede richting, van CARD naar ACCEPT
     * (zoals een speler het ook zou doen: het blauwe signaal groeit).
     */
    private void autoStep() {
        int[] goal = puzzle.solution();
        for (int cell : puzzle.pathOrder()) {
            if (puzzle.connections(cell, rotations) != puzzle.connections(cell, goal)) {
                int right = Math.floorMod(goal[cell] - rotations[cell], 4);
                turn(cell, right == 3 ? -1 : 1);
                return;
            }
        }
    }

    // ================================================================ Voor de screenshot-rondleiding

    /** Kiest meteen deze groep (bestaat hij niet, dan de eerste). */
    public void tourChoose(String chosen) {
        if (phase == Phase.CHOOSE) {
            choose(rowOf(chosen));
        }
    }

    /** Draait zoveel keer een stukje van het pad een kwart de goede kant op (-1 = de helft van wat nog moet). */
    public void tourSolve(int steps) {
        if (steps < 0) {
            int[] goal = puzzle.solution();
            int left = 0;
            for (int cell = 0; cell < puzzle.cells(); cell++) {
                if (puzzle.isOnPath(cell) && puzzle.connections(cell, rotations) != puzzle.connections(cell, goal)) {
                    left += Math.floorMod(goal[cell] - rotations[cell], 4) == 2 ? 2 : 1;
                }
            }
            steps = left / 2;
        }
        for (int i = 0; i < steps && phase == Phase.PLAY; i++) {
            autoStep();
        }
    }

    /**
     * Laat het signaal een ALARM raken: zet het pad vanaf CARD stukje voor stukje goed, tot je één stukje
     * (of anders twee) zo kunt draaien dat het signaal bij een ALARM komt, maar nog niet bij ACCEPT.
     * Rekent op een kopie, dus er gaat niets naar de server.
     */
    public void tourTouchAlarm() {
        if (phase != Phase.PLAY) {
            return;
        }
        int[] goal = puzzle.solution();
        for (int depth = 1; depth <= 2; depth++) {
            int[] trial = rotations.clone();
            for (int cellOnPath : puzzle.pathOrder()) {
                if (touchAlarm(trial, depth)) {
                    return;
                }
                trial[cellOnPath] = goal[cellOnPath];
            }
        }
    }

    /** Probeert {@code depth} stukjes te draaien zodat het signaal ALARM raakt; gelukt = die stand op het scherm. */
    private boolean touchAlarm(int[] trial, int depth) {
        for (int cell = 0; cell < puzzle.cells(); cell++) {
            int old = trial[cell];
            for (int turns = 0; turns < 4; turns++) {
                trial[cell] = turns;
                CircuitPuzzle.Signal test = puzzle.signal(trial);
                if (!test.alarms().isEmpty() && !test.accepted()) {
                    rotations = trial.clone();
                    signal = test;
                    return true;
                }
                if (depth > 1 && touchAlarm(trial, depth - 1)) {
                    return true;
                }
            }
            trial[cell] = old;
        }
        return false;
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
