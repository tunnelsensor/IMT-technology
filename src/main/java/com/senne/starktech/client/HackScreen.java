package com.senne.starktech.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.network.HackCommandPayload;
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
 *       scrollwiel = terugscrollen, Esc = dicht.</li>
 *   <li>De terminal hoort bij de laptop op de kern: elk commando gaat ernaartoe en de laptop
 *       onthoudt het. Gaat het scherm open, dan speelt dit scherm alles wat je al typte na
 *       (HackTerminal.replay), en ga je verder waar je was. Esc is dus niet erg.</li>
 *   <li>Een scan loopt even (met een voortgangsbalk); na te veel foute wachtwoorden ben je even
 *       geblokkeerd.</li>
 *   <li>De laatste stap (OVERRIDE) is de console van het beveiligingsprogramma: ook commando's
 *       ({@code disable watchdog}, {@code set owner <jouw naam>}). Je naam komt van je account.</li>
 * </ul>
 *
 * Het spel loopt door terwijl dit scherm open is: de beveiliging schiet op je. Snel zijn dus.
 */
public class HackScreen extends Screen {
    private static final int LINE_H = 10;

    /** Voor de showcase: het scherm speelt zichzelf (en maakt onderweg een paar fouten). */
    public static boolean autoPlay;
    /**
     * Bij welk commando de showcase is. Dit hoort niet bij één scherm: sluit je het scherm en open
     * je de laptop weer, dan gaat hij verder waar hij was (net als de terminal zelf).
     */
    private static int autoStep;

    /** De showcase laten beginnen bij het eerste commando. */
    public static void startAutoPlay() {
        autoPlay = true;
        autoStep = 0;
    }

    /** Waar de laptop staat (bovenop de kern). */
    private final BlockPos laptop;
    private final HackTerminal terminal;

    /** De puzzel achter dit scherm (voor de screenshots). */
    public HackTerminal terminal() {
        return terminal;
    }
    private final RandomSource random = RandomSource.create();
    private String input = "";
    private final List<String> history = new ArrayList<>();
    private int historyIndex;
    private int scroll;
    private int shownLines;

    /** Wanneer de hack gelukt is (het scherm blijft dan nog even staan). */
    private long doneAt;
    private final String playerName;

    // De showcase: wat hij nog gaat typen.
    private final Deque<String> autoQueue = new ArrayDeque<>();
    private long autoNext;

    /**
     * @param laptop   waar de laptop staat
     * @param seed     het getal van de puzzel (van de laptop)
     * @param commands wat je al typte (van de laptop): dat spelen we na
     */
    public HackScreen(BlockPos laptop, long seed, List<String> commands) {
        super(Component.translatable("screen.starktech.hack"));
        this.laptop = laptop;
        var player = net.minecraft.client.Minecraft.getInstance().player;
        this.playerName = player == null ? "player" : player.getGameProfile().getName();
        this.terminal = HackTerminal.replay(seed, playerName, commands);
        if (!commands.isEmpty()) {
            terminal.restored();
            history.addAll(commands);
            historyIndex = history.size();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public BlockPos laptop() {
        return laptop;
    }

    private boolean done() {
        return terminal.stage() == HackTerminal.Stage.DONE;
    }

    // ================================================================ Typen

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
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
            // De laptop onthoudt het (en de server kijkt of de toren nu van jou is).
            PacketDistributor.sendToServer(new HackCommandPayload(laptop, input.strip()));
        }
        historyIndex = history.size();
        input = "";
        scroll = 0;
        HackTerminal.Line last = terminal.output().isEmpty() ? null : terminal.output().getLast();
        if (terminal.stage() != before) {
            EdithClientEvents.playUi(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f + terminal.stage().ordinal() * 0.2f);
            if (done()) {
                // Jij bent de eigenaar: de toren is van jou (de server heeft het ook nagespeeld).
                doneAt = now;
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
        return true;
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
     * (een verkeerde poort, een fout wachtwoord, de eigenaar zetten met de waakhond nog aan),
     * zodat je die ook ziet.
     */
    private void autoPlay(long now) {
        if (now < autoNext || terminal.busy(now)) {
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
                case 8 -> autoQueue.add("cat README.txt");
                case 9 -> autoQueue.add("cat security.conf");
                case 10 -> autoQueue.add("ps");
                case 11 -> autoQueue.add("override " + terminal.guardPid());
                case 12 -> autoQueue.add("set owner " + playerName);
                case 13 -> autoQueue.add("disable watchdog");
                case 14 -> autoQueue.add("set owner " + playerName);
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

    /** Dicht terwijl de showcase nog aan het typen was: dat commando straks opnieuw. */
    @Override
    public void removed() {
        if (!autoQueue.isEmpty()) {
            autoStep = Math.max(0, autoStep - 1);
        }
        super.removed();
    }

    /** Door de server gesloten (weggelopen, dood, of de laptop is weg). Je voortgang blijft op de laptop. */
    public void closeFromServer() {
        onClose();
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

        // Het gebied voor de tekst.
        int textTop = y0 + 22;
        int textBottom = y0 + h - 28;
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
        if (done()) {
            g.drawString(font, Component.translatable("screen.starktech.hack.granted"), x0 + 8, inputY,
                    HudStyle.argb(255, HudStyle.GREEN), false);
        } else if (terminal.busy(now)) {
            double progress = terminal.scanProgress(now);
            String busy = progress >= 0 ? "scanning " + (int) (progress * 100) + "%" : "LOCKED - please wait";
            g.drawString(font, busy, x0 + 8, inputY, HudStyle.argb(255, HudStyle.ORANGE), false);
            if (progress >= 0) {
                int bx = x0 + 90;
                g.fill(bx, inputY + 2, x0 + w - 10, inputY + 6, HudStyle.argb(120, 0x3FFF7A));
                g.fill(bx, inputY + 2, bx + (int) ((x0 + w - 10 - bx) * progress), inputY + 6, HudStyle.argb(255, 0x3FFF7A));
            }
        } else {
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
