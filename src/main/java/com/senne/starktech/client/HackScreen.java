package com.senne.starktech.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.network.HackResultPayload;
import com.senne.starktech.security.HackTerminal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Het hack-scherm van de Stark-laptop: een terminal waarin je commando's typt om een Stark Tower
 * over te nemen. De puzzel zelf (poorten, wachtwoord, bestanden, processen) zit in HackTerminal;
 * dit scherm tekent hem en laat je typen:
 *
 * <ul>
 *   <li>Enter = uitvoeren, Tab = aanvullen, pijltje omhoog/omlaag = vorige commando's,
 *       scrollwiel = terugscrollen, Esc = afbreken.</li>
 *   <li>Een scan loopt even (met een voortgangsbalk); na te veel foute wachtwoorden ben je even
 *       geblokkeerd.</li>
 *   <li>De laatste stap (OVERRIDE) is de timingbalk van vroeger: SPATIE of klik als de cursor in
 *       het groene vak staat, vier keer.</li>
 * </ul>
 *
 * Het spel loopt door terwijl dit scherm open is: de beveiliging schiet op je. Snel zijn dus.
 */
public class HackScreen extends Screen {
    /** Zoveel treffers in de laatste stap, hoe snel de cursor gaat en hoe breed het vak is. */
    private static final int OVERRIDE_HITS = 4;
    private static final double OVERRIDE_SPEED = 0.7;
    private static final double OVERRIDE_ZONE = 0.16;
    private static final int LINE_H = 10;

    /** Voor de showcase: het scherm speelt zichzelf (en maakt onderweg een paar fouten). */
    public static boolean autoPlay;

    private final BlockPos core;
    private final HackTerminal terminal;
    private final RandomSource random = RandomSource.create();
    private String input = "";
    private final List<String> history = new ArrayList<>();
    private int historyIndex;
    private int scroll;
    private int shownLines;

    // De timingbalk van de laatste stap.
    private int hits;
    private double zoneStart;
    private long barStart;
    private long flashUntil;
    private boolean flashGood;
    private long doneAt;

    // De showcase: wat hij nog gaat typen.
    private final Deque<String> autoQueue = new ArrayDeque<>();
    private int autoStep;
    private long autoNext;

    public HackScreen(BlockPos core) {
        super(Component.translatable("screen.starktech.hack"));
        this.core = core;
        this.terminal = new HackTerminal(random.nextLong());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public BlockPos core() {
        return core;
    }

    private boolean done() {
        return terminal.stage() == HackTerminal.Stage.DONE;
    }

    // ================================================================ Typen

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (terminal.stage() == HackTerminal.Stage.OVERRIDE) {
            return true; // spatie telt al in keyPressed
        }
        if (codePoint >= ' ' && codePoint != 127 && input.length() < 48 && !done()) {
            input += codePoint;
            scroll = 0;
            click(1.8f + random.nextFloat() * 0.3f);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers); // afbreken
        }
        if (terminal.stage() == HackTerminal.Stage.OVERRIDE) {
            if (keyCode == InputConstants.KEY_SPACE) {
                press();
            }
            return true;
        }
        switch (keyCode) {
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> submit();
            case InputConstants.KEY_BACKSPACE -> {
                if (!input.isEmpty()) {
                    input = input.substring(0, input.length() - 1);
                }
            }
            case InputConstants.KEY_TAB -> input = terminal.complete(input);
            case InputConstants.KEY_UP -> {
                if (!history.isEmpty()) {
                    historyIndex = Math.max(0, historyIndex - 1);
                    input = history.get(historyIndex);
                }
            }
            case InputConstants.KEY_DOWN -> {
                if (historyIndex < history.size() - 1) {
                    historyIndex++;
                    input = history.get(historyIndex);
                } else {
                    historyIndex = history.size();
                    input = "";
                }
            }
            default -> { }
        }
        return true;
    }

    /** Enter: het commando uitvoeren. */
    private void submit() {
        long now = Util.getMillis();
        if (terminal.busy(now) || done()) {
            return;
        }
        HackTerminal.Stage before = terminal.stage();
        terminal.run(input, now);
        if (!input.isBlank()) {
            history.add(input);
        }
        historyIndex = history.size();
        input = "";
        scroll = 0;
        HackTerminal.Line last = terminal.output().isEmpty() ? null : terminal.output().getLast();
        if (terminal.stage() != before) {
            EdithClientEvents.playUi(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f + terminal.stage().ordinal() * 0.2f);
            if (terminal.stage() == HackTerminal.Stage.OVERRIDE) {
                barStart = now;
                newZone();
            }
        } else if (last != null && last.kind() == HackTerminal.Kind.ERROR) {
            EdithClientEvents.playUi(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f);
        }
    }

    private void click(float pitch) {
        EdithClientEvents.playUi(SoundEvents.NOTE_BLOCK_HAT.value(), pitch);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(Math.max(0, terminal.output().size() - shownLines), scroll + (int) Math.signum(scrollY) * 3));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (terminal.stage() == HackTerminal.Stage.OVERRIDE) {
            press();
        }
        return true;
    }

    // ================================================================ De laatste stap: de timingbalk

    private double cursor() {
        double t = (Util.getMillis() - barStart) / 1000.0 * OVERRIDE_SPEED * 2;
        double phase = t % 2;
        return phase < 1 ? phase : 2 - phase;
    }

    private boolean inZone() {
        double c = cursor();
        return c >= zoneStart && c <= zoneStart + OVERRIDE_ZONE;
    }

    private void newZone() {
        zoneStart = 0.05 + random.nextDouble() * (0.9 - OVERRIDE_ZONE);
    }

    private void press() {
        if (terminal.stage() != HackTerminal.Stage.OVERRIDE) {
            return;
        }
        long now = Util.getMillis();
        if (inZone()) {
            hits++;
            flashGood = true;
            EdithClientEvents.playUi(SoundEvents.NOTE_BLOCK_BIT.value(), 1.2f + hits * 0.1f);
            if (hits >= OVERRIDE_HITS) {
                terminal.overrideDone();
                doneAt = now;
                EdithClientEvents.playUi(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6f);
                PacketDistributor.sendToServer(new HackResultPayload(core, true));
                return;
            }
            newZone();
        } else {
            hits = Math.max(0, hits - 1);
            flashGood = false;
            EdithClientEvents.playUi(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f);
        }
        flashUntil = now + 250;
    }

    // ================================================================ Elke tick

    @Override
    public void tick() {
        long now = Util.getMillis();
        terminal.tick(now);
        if (done()) {
            if (now - doneAt > 1800) {
                onClose();
            }
            return;
        }
        if (autoPlay) {
            autoPlay(now);
        }
    }

    /**
     * De showcase: typt de oplossing zelf, letter voor letter, met een paar echte fouten erin
     * (een verkeerde poort, een fout wachtwoord, de verkeerde daemon), zodat je die ook ziet.
     */
    private void autoPlay(long now) {
        if (now < autoNext || terminal.busy(now)) {
            return;
        }
        if (terminal.stage() == HackTerminal.Stage.OVERRIDE) {
            if (now - barStart > 400 && Math.abs(cursor() - (zoneStart + OVERRIDE_ZONE / 2)) < OVERRIDE_ZONE / 4) {
                press();
            }
            return;
        }
        if (autoQueue.isEmpty()) {
            switch (autoStep++) {
                case 0 -> autoQueue.add("help");
                case 1 -> autoQueue.add("scan");
                case 2 -> autoQueue.add("connect 8080");
                case 3 -> autoQueue.add("logs");
                case 4 -> autoQueue.add("connect " + terminal.openPort());
                case 5 -> {
                    String wrong = terminal.candidates().stream().filter(word -> !word.equals(terminal.password()))
                            .findFirst().orElse("SHIELD");
                    autoQueue.add("login " + wrong.toLowerCase());
                }
                case 6 -> autoQueue.add("login " + terminal.password().toLowerCase());
                case 7 -> autoQueue.add("ls");
                case 8 -> autoQueue.add("cat home/tony/notes.txt");
                case 9 -> autoQueue.add("cat etc/security.conf");
                case 10 -> autoQueue.add("ps");
                case 11 -> autoQueue.add("override " + terminal.guardPid());
                default -> { }
            }
            autoNext = now + 700;
            return;
        }
        String next = autoQueue.peek();
        if (input.length() < next.length()) {
            charTyped(next.charAt(input.length()), 0);
            autoNext = now + 45;
        } else {
            autoQueue.poll();
            submit();
            autoNext = now + 900;
        }
    }

    // ================================================================ Dicht

    /** Dicht: was de hack nog niet klaar, dan is hij afgebroken. */
    @Override
    public void onClose() {
        if (!done()) {
            PacketDistributor.sendToServer(new HackResultPayload(core, false));
        }
        super.onClose();
    }

    /** Door de server gesloten (te ver weg, dood): niets meer terugsturen. */
    public void closeFromServer() {
        terminal.overrideDone();
        super.onClose();
    }

    // ================================================================ Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        HudStyle.dim(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        long now = Util.getMillis();
        int w = Math.min(440, width - 16);
        int h = Math.min(260, height - 16);
        int x0 = (width - w) / 2;
        int y0 = (height - h) / 2;
        g.fill(x0, y0, x0 + w, y0 + h, HudStyle.argb(235, 0x020A06));
        HudStyle.brackets(g, x0, y0, x0 + w, y0 + h, 8, HudStyle.argb(230, 0x3FFF7A));

        // Bovenaan: de titel en de vier stappen.
        g.drawString(font, Component.translatable("screen.starktech.hack.title"), x0 + 8, y0 + 6,
                HudStyle.argb(255, 0x3FFF7A), false);
        String[] steps = {"RECON", "BREACH", "SEARCH", "OVERRIDE"};
        int stage = terminal.stage().ordinal();
        int sx = x0 + w - 8;
        for (int i = steps.length - 1; i >= 0; i--) {
            String label = (i < stage ? "✓" : "") + steps[i];
            sx -= font.width(label);
            int colour = i < stage ? HudStyle.GREEN : i == stage ? HudStyle.WHITE : 0x3A5A48;
            g.drawString(font, label, sx, y0 + 6, HudStyle.argb(255, colour), false);
            sx -= 10;
        }
        g.fill(x0 + 4, y0 + 17, x0 + w - 4, y0 + 18, HudStyle.argb(120, 0x3FFF7A));

        // Het gebied voor de tekst (onderaan minder als de timingbalk er staat).
        boolean overrideBar = terminal.stage() == HackTerminal.Stage.OVERRIDE || done();
        int textTop = y0 + 22;
        int textBottom = y0 + h - (overrideBar ? 58 : 28);
        int textWidth = w - 16;
        List<HackTerminal.Line> visual = wrap(terminal.output(), textWidth);
        shownLines = Math.max(1, (textBottom - textTop) / LINE_H);
        int first = Math.max(0, visual.size() - shownLines - scroll);
        int end = Math.min(visual.size(), first + shownLines);
        int y = textTop;
        for (int i = first; i < end; i++) {
            HackTerminal.Line line = visual.get(i);
            g.drawString(font, line.text(), x0 + 8, y, HudStyle.argb(255, colour(line.kind())), false);
            y += LINE_H;
        }
        if (scroll > 0) {
            g.drawString(font, "▼ " + scroll, x0 + w - 40, textBottom - LINE_H, HudStyle.argb(160, HudStyle.ORANGE), false);
        }

        // De invoerregel (of wat er bezig is).
        int inputY = y0 + h - 22;
        if (overrideBar) {
            drawBar(g, x0 + 20, y0 + h - 52, x0 + w - 20, y0 + h - 34, now);
        } else if (terminal.busy(now)) {
            double progress = terminal.scanProgress(now);
            String busy = progress >= 0 ? "scanning " + (int) (progress * 100) + "%" : "LOCKED - please wait";
            g.drawString(font, busy, x0 + 8, inputY, HudStyle.argb(255, HudStyle.ORANGE), false);
            if (progress >= 0) {
                int bx = x0 + 90;
                g.fill(bx, inputY + 2, x0 + w - 10, inputY + 6, HudStyle.argb(120, 0x3FFF7A));
                g.fill(bx, inputY + 2, bx + (int) ((x0 + w - 10 - bx) * progress), inputY + 6, HudStyle.argb(255, 0x3FFF7A));
            }
        } else if (!done()) {
            String line = terminal.prompt() + input;
            line = font.plainSubstrByWidth(line, textWidth - 8);
            g.drawString(font, line, x0 + 8, inputY, HudStyle.argb(255, HudStyle.WHITE), false);
            if ((now / 400) % 2 == 0) {
                int cx = x0 + 8 + font.width(line);
                g.fill(cx + 1, inputY - 1, cx + 6, inputY + 8, HudStyle.argb(220, 0x3FFF7A));
            }
        }
        g.drawCenteredString(font, Component.translatable("screen.starktech.hack.warning"), x0 + w / 2, y0 + h - 10,
                HudStyle.argb(200, HudStyle.ORANGE));
    }

    /** De timingbalk: het groene vak, de cursor, en de treffers als blokjes erboven. */
    private void drawBar(GuiGraphics g, int bx0, int by0, int bx1, int by1, long now) {
        int need = OVERRIDE_HITS;
        int px0 = (bx0 + bx1) / 2 - need * 6;
        for (int i = 0; i < need; i++) {
            int colour = i < hits || done() ? HudStyle.GREEN : 0x33505A;
            g.fill(px0 + i * 12 + 2, by0 - 9, px0 + i * 12 + 10, by0 - 4, HudStyle.argb(255, colour));
        }
        g.fill(bx0, by0, bx1, by1, HudStyle.argb(255, 0x0A2518));
        HudStyle.brackets(g, bx0, by0, bx1, by1, 4, HudStyle.argb(200, 0x3FFF7A));
        if (done()) {
            g.drawCenteredString(font, Component.translatable("screen.starktech.hack.granted"), (bx0 + bx1) / 2, by0 + 5,
                    HudStyle.argb(255, HudStyle.GREEN));
            return;
        }
        boolean flashing = now < flashUntil;
        int zoneColour = flashing ? (flashGood ? HudStyle.GREEN : HudStyle.RED) : 0x2FB36A;
        int zx0 = bx0 + (int) ((bx1 - bx0) * zoneStart);
        int zx1 = bx0 + (int) ((bx1 - bx0) * (zoneStart + OVERRIDE_ZONE));
        g.fill(zx0, by0 + 2, zx1, by1 - 2, HudStyle.argb(flashing ? 230 : 170, zoneColour));
        int cursorX = bx0 + (int) ((bx1 - bx0) * cursor());
        g.fill(cursorX - 1, by0 - 3, cursorX + 1, by1 + 3, HudStyle.argb(255, HudStyle.WHITE));
    }

    /** Knipt lange regels op de breedte van de terminal (de rest op de volgende regel). */
    private List<HackTerminal.Line> wrap(List<HackTerminal.Line> lines, int width) {
        List<HackTerminal.Line> result = new ArrayList<>();
        for (HackTerminal.Line line : lines) {
            String text = line.text();
            while (font.width(text) > width) {
                String part = font.plainSubstrByWidth(text, width);
                if (part.isEmpty()) {
                    break;
                }
                result.add(new HackTerminal.Line(part, line.kind()));
                text = "  " + text.substring(part.length());
            }
            result.add(new HackTerminal.Line(text, line.kind()));
        }
        return result;
    }

    private static int colour(HackTerminal.Kind kind) {
        return switch (kind) {
            case NORMAL -> 0x7DF2A0;
            case INPUT -> 0xE8FBFF;
            case ERROR -> 0xFF5A5A;
            case HEADER -> 0x3FD8FF;
            case HINT -> 0xFFC83F;
            case GOOD -> 0x5CFF9D;
        };
    }
}
