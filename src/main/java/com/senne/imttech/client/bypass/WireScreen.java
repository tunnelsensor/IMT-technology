package com.senne.imttech.client.bypass;

import com.senne.imttech.bypass.WireTangle;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.network.BypassPayload;
import com.senne.imttech.registry.ModSounds;
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
 * De draden van een laserzender (hoofdstuk 33, jouw keuze: "draden volgen"): de derde minigame van de Bypass Kit.
 * <pre>
 *  BYPASS &gt; LASER GATE             6 WIRES             0:47
 *  [1]===\ /=======\ /===[LAMP ]
 *  [2]====X===\ /===X====[LENS ]
 *  [3]===/ \===X===/ \===[ALARM]
 *              [ CUT WIRE 2 ]
 * </pre>
 * Links de stekkers, rechts waar elke draad heen gaat. Het is donker in de zender: alleen waar je muis is, licht
 * het op (een zaklamp), dus je moet de draad van de LENS echt terug volgen naar zijn stekker. Kies die stekker
 * (klik erop, of de toetsen 1 tot 8) en knip (CUT, of Enter). Kiezen laat niet zien waar de draad heen gaat.
 *
 * Je kunt maar één keer knippen. Dit scherm stuurt alleen welke stekker; de server rekent zelf na of dat de draad
 * naar de LENS is (zie bypass/Bypass). De klok loopt meteen. Het spel loopt door terwijl dit scherm open is; Esc
 * stopt de hack (zonder alarm).
 */
public class WireScreen extends Screen implements BypassScreen {
    private enum Phase { PLAY, WAIT, DONE }

    /** Zo lang (ticks) blijft de uitslag staan voor het scherm dichtgaat. */
    private static final int RESULT_TICKS = 60;

    private static final int PANEL = 0x0D1318;
    private static final int BOARD = 0x080C0F;
    /** Een draad in het donker... */
    private static final int WIRE_DARK = 0x222B32;
    /** ...en in het licht van je zaklamp (koper). */
    private static final int WIRE_LIT = 0xFFC27A;
    private static final int GREY = 0x7F93A0;

    /** Ruimte boven de kluwen (de titel) en eronder (de knop en de uitleg). */
    private static final int ABOVE = 24;
    private static final int BELOW = 56;
    private static final int PLUG_W = 18;
    private static final int LABEL_W = 50;
    /** Zo ver van de stekker knip je (op het stukje draad voor de kluwen). */
    private static final int CUT_GAP = 4;
    private static final int BUTTON_W = 96;
    private static final int BUTTON_H = 16;

    /** Voor de showcase: het scherm speelt zichzelf (volgt de draad van de LENS terug, kiest zijn stekker en knipt). */
    public static boolean autoPlay;
    /** Zo lang (ticks) doet de showcase over het volgen van de draad. */
    private static final int AUTO_TRACE = 70;

    private final BlockPos emitter;
    private final String emitterName;
    private final WireTangle tangle;
    /** Hoe lang de poort op slot gaat na een verkeerde knip (seconden; zegt de server). */
    private final int lockSeconds;

    private Phase phase = Phase.PLAY;
    private int ticksLeft;
    /** De stekker die je koos (-1 = nog geen), en die je doorknipte. */
    private int selected = -1;
    private int cutWire = -1;
    /** Hoe het afliep: ok, wrong, time (of leeg). */
    private String result = "";
    private int resultTicks;
    /** Al iets definitiefs naar de server gestuurd? Dan hoeft dichtgaan niet meer "cancel" te sturen. */
    private boolean finished;
    private int autoTicks;
    /** Waar de zaklamp nu schijnt als dat niet je muis is (de showcase, de screenshots); -1 = je muis. */
    private float lightX = -1;
    private float lightY = -1;

    public WireScreen(BlockPos emitter, String emitterName, long seed, int wires, int ticks, int lockSeconds) {
        super(Component.literal(emitterName));
        this.emitter = emitter;
        this.emitterName = emitterName;
        this.tangle = WireTangle.generate(seed, wires);
        this.ticksLeft = ticks;
        this.lockSeconds = lockSeconds;
    }

    @Override
    public BlockPos device() {
        return emitter;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        HudStyle.dim(g, width, height);
    }

    // ================================================================ Waar alles staat

    /** De afstand tussen twee draden: zo groot als past, tussen 12 en 24 pixels. */
    private int rowHeight() {
        return Math.max(12, Math.min(24, (height - ABOVE - BELOW - 12) / tangle.wires()));
    }

    private int panelWidth() {
        return Math.min(width - 16, 420);
    }

    private int panelHeight() {
        return ABOVE + tangle.wires() * rowHeight() + BELOW;
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelTop() {
        return Math.max(4, (height - panelHeight()) / 2);
    }

    /** Waar de stekkers staan, en waar de kluwen begint en eindigt. */
    private int plugX() {
        return panelLeft() + 8;
    }

    private int boardLeft() {
        return plugX() + PLUG_W + 16;
    }

    private int boardRight() {
        return labelX() - 14;
    }

    private int labelX() {
        return panelLeft() + panelWidth() - 8 - LABEL_W;
    }

    private int boardTop() {
        return panelTop() + ABOVE;
    }

    /** Het midden van rij {@code row}. */
    private float rowY(int row) {
        return boardTop() + row * rowHeight() + rowHeight() / 2f;
    }

    /** De knop CUT, onder de kluwen. */
    private int buttonX() {
        return panelLeft() + (panelWidth() - BUTTON_W) / 2;
    }

    private int buttonY() {
        return boardTop() + tangle.wires() * rowHeight() + 8;
    }

    /**
     * Waar draad {@code wire} loopt op hoogte {@code x}. Tussen de stekker en de kluwen, en tussen de kluwen en zijn
     * onderdeel, loopt hij rechtdoor; in de kluwen gaat hij per kolom misschien schuin naar een buurrij (met een mooie
     * bocht in het midden van de kolom: zo zie je de X van twee draden die elkaar kruisen).
     */
    private float wireY(int wire, float x) {
        float left = boardLeft();
        float column = (boardRight() - left) / (float) tangle.columns();
        if (x <= left) {
            return rowY(tangle.rowAt(wire, 0));
        }
        if (x >= boardRight()) {
            return rowY(tangle.endRow(wire));
        }
        int c = Math.min(tangle.columns() - 1, (int) ((x - left) / column));
        float t = (x - left - c * column) / column;
        float bend = Math.max(0, Math.min(1, (t - 0.15f) / 0.7f));
        bend = bend * bend * (3 - 2 * bend);
        float from = rowY(tangle.rowAt(wire, c));
        float to = rowY(tangle.rowAt(wire, c + 1));
        return from + (to - from) * bend;
    }

    /** De stekker onder de muis, of -1. */
    private int plugAt(double mouseX, double mouseY) {
        if (mouseX < plugX() - 2 || mouseX >= plugX() + PLUG_W + 2) {
            return -1;
        }
        for (int wire = 0; wire < tangle.wires(); wire++) {
            float y = rowY(wire);
            if (mouseY >= y - rowHeight() / 2f && mouseY < y + rowHeight() / 2f) {
                return wire;
            }
        }
        return -1;
    }

    private boolean overButton(double mouseX, double mouseY) {
        return mouseX >= buttonX() && mouseX < buttonX() + BUTTON_W && mouseY >= buttonY() && mouseY < buttonY() + BUTTON_H;
    }

    // ================================================================ Tekenen

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int px0 = panelLeft();
        int py0 = panelTop();
        int px1 = px0 + panelWidth();
        int py1 = py0 + panelHeight();
        long now = System.currentTimeMillis();
        boolean blink = (now / 150) % 2 == 0;
        g.fill(px0, py0, px1, py1, HudStyle.argb(240, PANEL));
        int frame = phase == Phase.DONE ? (result.equals("ok") ? HudStyle.GREEN : HudStyle.RED) : HudStyle.CYAN;
        HudStyle.brackets(g, px0, py0, px1, py1, 8, HudStyle.argb(255, frame));

        // Bovenaan: welke zender, hoeveel draden, hoeveel tijd.
        g.drawString(font, "BYPASS > " + emitterName.toUpperCase(Locale.ROOT), px0 + 8, py0 + 8, HudStyle.argb(255, HudStyle.CYAN), false);
        String count = tangle.wires() + " WIRES";
        g.drawString(font, count, (px0 + px1 - font.width(count)) / 2, py0 + 8, HudStyle.argb(255, HudStyle.WHITE), false);
        int seconds = (Math.max(0, ticksLeft) + 19) / 20;
        String clock = (seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
        boolean hurry = seconds <= 10 && phase == Phase.PLAY;
        g.drawString(font, clock, px1 - 8 - font.width(clock), py0 + 8,
                HudStyle.argb(255, hurry ? (blink ? HudStyle.RED : HudStyle.WHITE) : HudStyle.WHITE), false);

        // De binnenkant van de zender: donker.
        int top = boardTop();
        int bottom = top + tangle.wires() * rowHeight();
        g.fill(plugX() + PLUG_W, top, labelX(), bottom, HudStyle.argb(255, BOARD));

        // De zaklamp: je muis (alleen in de zender), of de showcase.
        float lx = lightX >= 0 ? lightX : mouseX;
        float ly = lightX >= 0 ? lightY : mouseY;
        boolean light = phase != Phase.DONE && lx >= plugX() + PLUG_W && lx < labelX() && ly >= top - 4 && ly < bottom + 4;
        float radius = Math.max(16, rowHeight() * 1.3f);
        for (int wire = 0; wire < tangle.wires(); wire++) {
            if (wire != cutWire || phase != Phase.DONE) {
                drawWire(g, wire, light, lx, ly, radius, -1);
            }
        }
        // Na het knippen: de draad die je knipte helemaal te zien, groen (goed) of rood (fout).
        if (phase == Phase.DONE && cutWire >= 0) {
            drawWire(g, cutWire, false, 0, 0, 0, result.equals("ok") ? HudStyle.GREEN : HudStyle.RED);
        }
        if (light && lightX >= 0) {
            // De showcase: een ringetje waar de "muis" is.
            g.renderOutline(Math.round(lx) - 3, Math.round(ly) - 3, 6, 6, HudStyle.argb(200, HudStyle.WHITE));
        }

        // Links de stekkers, rechts de onderdelen.
        int hoverPlug = phase == Phase.PLAY ? plugAt(mouseX, mouseY) : -1;
        for (int wire = 0; wire < tangle.wires(); wire++) {
            int y = Math.round(rowY(wire)) - 6;
            boolean chosen = wire == selected || wire == cutWire;
            int colour = chosen ? HudStyle.CYAN : wire == hoverPlug ? HudStyle.WHITE : GREY;
            g.fill(plugX(), y, plugX() + PLUG_W, y + 12, HudStyle.argb(255, chosen ? 0x10303A : 0x151C22));
            g.renderOutline(plugX(), y, PLUG_W, 12, HudStyle.argb(255, colour));
            String number = String.valueOf(wire + 1);
            g.drawString(font, number, plugX() + (PLUG_W - font.width(number)) / 2 + 1, y + 2, HudStyle.argb(255, colour), false);
        }
        for (int row = 0; row < tangle.wires(); row++) {
            int y = Math.round(rowY(row)) - 6;
            String label = tangle.label(row);
            boolean lens = label.equals(WireTangle.LENS);
            boolean hit = phase == Phase.DONE && cutWire >= 0 && tangle.endRow(cutWire) == row;
            int colour = hit ? (result.equals("ok") ? HudStyle.GREEN : blink ? HudStyle.WHITE : HudStyle.RED)
                    : lens ? HudStyle.CYAN : label.equals("ALARM") ? HudStyle.RED : GREY;
            g.fill(labelX(), y, labelX() + LABEL_W, y + 12, HudStyle.argb(255, lens ? 0x10303A : 0x151C22));
            g.renderOutline(labelX(), y, LABEL_W, 12, HudStyle.argb(255, colour));
            g.drawString(font, label, labelX() + (LABEL_W - font.width(label)) / 2, y + 2, HudStyle.argb(255, colour), false);
        }

        // Onderaan: de knop en de uitleg, of wat er gebeurde.
        int cx = (px0 + px1) / 2;
        int ty = buttonY() + BUTTON_H + 8;
        if (phase == Phase.PLAY) {
            boolean ready = selected >= 0;
            boolean hover = ready && overButton(mouseX, mouseY);
            g.fill(buttonX(), buttonY(), buttonX() + BUTTON_W, buttonY() + BUTTON_H,
                    HudStyle.argb(255, hover ? 0x3A1414 : ready ? 0x2A0C0C : 0x151C22));
            g.renderOutline(buttonX(), buttonY(), BUTTON_W, BUTTON_H, HudStyle.argb(255, ready ? HudStyle.RED : GREY));
            String text = ready ? "CUT WIRE " + (selected + 1) : "CHOOSE A PLUG";
            g.drawString(font, text, cx - font.width(text) / 2, buttonY() + 4, HudStyle.argb(255, ready ? HudStyle.WHITE : GREY), false);
            centred(g, "Follow the wire from the LENS back to its plug, then cut it.", cx, ty, HudStyle.WHITE);
            centred(g, "1-" + tangle.wires() + " or click a plug: choose    Enter: cut    Esc: stop", cx, ty + 11, GREY);
        } else if (phase == Phase.WAIT) {
            centred(g, "Snip...", cx, buttonY() + 4, HudStyle.CYAN);
        } else {
            String[] lines = resultText(result);
            centred(g, lines[0], cx, buttonY() + 4, result.equals("ok") ? HudStyle.GREEN : HudStyle.RED);
            centred(g, lines[1], cx, buttonY() + 17, HudStyle.WHITE);
        }
    }

    /**
     * Eén draad, pixel voor pixel van de stekker naar zijn onderdeel. {@code colour} -1: donker, en licht waar de
     * zaklamp schijnt; anders helemaal in die kleur. Een doorgeknipte draad heeft een gaatje vlak na zijn stekker.
     */
    private void drawWire(GuiGraphics g, int wire, boolean light, float lx, float ly, float radius, int colour) {
        int thick = rowHeight() >= 16 ? 3 : 2;
        int start = plugX() + PLUG_W;
        int end = labelX();
        int gapFrom = boardLeft() - 8 - CUT_GAP;
        boolean cut = wire == cutWire;
        int previous = Integer.MIN_VALUE;
        for (int x = start; x < end; x++) {
            int y = Math.round(wireY(wire, x + 0.5f));
            int from = previous == Integer.MIN_VALUE ? y : Math.min(previous, y);
            int to = previous == Integer.MIN_VALUE ? y : Math.max(previous, y);
            previous = y;
            if (cut && x >= gapFrom && x < gapFrom + CUT_GAP) {
                continue; // hier is hij doorgeknipt
            }
            int rgb = colour;
            if (rgb < 0) {
                rgb = WIRE_DARK;
                if (light) {
                    float dx = x - lx;
                    float dy = y - ly;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d < radius) {
                        float f = 1 - d / radius;
                        rgb = mix(WIRE_DARK, WIRE_LIT, f * f * (3 - 2 * f));
                    }
                }
            }
            g.fill(x, from - thick / 2, x + 1, to - thick / 2 + thick, HudStyle.argb(255, rgb));
        }
    }

    /** Twee kleuren gemengd: f = 0 de eerste, f = 1 de tweede. */
    private static int mix(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
        int gr = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * f);
        return (r << 16) | (gr << 8) | bl;
    }

    private void centred(GuiGraphics g, String text, int cx, int y, int colour) {
        g.drawString(font, text, cx - font.width(text) / 2, y, HudStyle.argb(255, colour), false);
    }

    /** Hoe lang de poort op slot gaat, als "5:00". */
    private String lockText() {
        return (lockSeconds / 60) + ":" + String.format(Locale.ROOT, "%02d", lockSeconds % 60);
    }

    private String[] resultText(String how) {
        return switch (how) {
            case "ok" -> new String[] {"CUT! The laser gate is dead.", "It stays off until someone repairs the wires."};
            case "wrong" -> new String[] {"ZAP! That wire went to the " + (cutWire >= 0 ? tangle.label(tangle.endRow(cutWire)) : "wrong part") + ".",
                    "Locked for " + lockText() + ". If it guards a base, the owner is warned."};
            default -> new String[] {"TOO SLOW: the gate locked up for " + lockText() + ".",
                    "If it guards a base, the owner is warned."};
        };
    }

    // ================================================================ Klikken en toetsen

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (phase == Phase.PLAY && button == 0) {
            int plug = plugAt(mouseX, mouseY);
            if (plug >= 0) {
                choose(plug);
                return true;
            }
            if (selected >= 0 && overButton(mouseX, mouseY)) {
                cut();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (phase == Phase.PLAY) {
            if (keyCode >= 49 && keyCode < 49 + tangle.wires()) { // de toetsen 1 tot 8
                choose(keyCode - 49);
                return true;
            }
            if ((keyCode == 257 || keyCode == 335 || keyCode == 32) && selected >= 0) { // Enter, Enter op het numpad, spatie
                cut();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Een stekker gekozen (nog eens = weer los). */
    private void choose(int wire) {
        selected = selected == wire ? -1 : wire;
        play(ModSounds.IMT_SELECT.get(), 1.6f, 0.3f);
    }

    /** Knip de gekozen draad door: de server zegt of het de goede was. */
    private void cut() {
        cutWire = selected;
        selected = -1;
        phase = Phase.WAIT;
        resultTicks = 0;
        finished = true;
        CompoundTag args = new CompoundTag();
        args.putInt("wire", cutWire);
        PacketDistributor.sendToServer(new BypassPayload(emitter, "cut", args));
        play(SoundEvents.SHEEP_SHEAR, 1.2f, 0.8f);
    }

    @Override
    public void tick() {
        super.tick();
        switch (phase) {
            case PLAY -> {
                if (--ticksLeft <= 0) {
                    lose();
                    return;
                }
                if (autoPlay) {
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

    /** De tijd is om in jouw spel: de server krijgt het te horen (de poort gaat op slot). */
    private void lose() {
        PacketDistributor.sendToServer(BypassPayload.of(emitter, "fail"));
        finished = true;
        finish("time");
    }

    private void finish(String how) {
        result = how;
        phase = Phase.DONE;
        resultTicks = 0;
        lightX = -1;
        if (how.equals("ok")) {
            play(ModSounds.IMT_CONFIRM.get(), 1.0f, 1.0f);
        } else {
            play(ModSounds.IMT_DENIED.get(), 0.8f, 1.0f);
            if (how.equals("wrong")) {
                play(ModSounds.RAYSHIELD_HIT.get(), 1.4f, 0.8f);
            }
        }
    }

    /** Het antwoord van de server: goed geknipt, de verkeerde draad, of te laat. */
    @Override
    public void onResult(CompoundTag args) {
        if (phase != Phase.DONE) {
            finish(args.getString("result"));
        }
    }

    /** De server sloot de hack (je liep weg, of de zender is weg). */
    @Override
    public void closeFromServer() {
        finished = true;
        onClose();
    }

    @Override
    public void removed() {
        super.removed();
        if (!finished) {
            PacketDistributor.sendToServer(BypassPayload.of(emitter, "cancel"));
        }
    }

    // ================================================================ De showcase en de screenshots

    /**
     * De showcase: de zaklamp volgt de draad van de LENS terug naar links (zoals een speler het doet), dan kiest het
     * scherm zijn stekker en knipt.
     */
    private void autoStep() {
        autoTicks++;
        if (autoTicks <= AUTO_TRACE) {
            tourTrace(autoTicks / (float) AUTO_TRACE);
        } else if (autoTicks == AUTO_TRACE + 10) {
            choose(tangle.lensStart());
        } else if (autoTicks == AUTO_TRACE + 25 && selected >= 0) {
            cut();
        }
    }

    /**
     * Zet de zaklamp op de draad naar de LENS: {@code fraction} 0 = bij de LENS (rechts), 1 = bij zijn stekker (links).
     * Rekent alleen op het scherm; er gaat niets naar de server.
     */
    public void tourTrace(float fraction) {
        float right = labelX() - 4;
        float left = plugX() + PLUG_W + 4;
        lightX = right + (left - right) * Math.max(0, Math.min(1, fraction));
        lightY = wireY(tangle.lensStart(), lightX);
    }

    /** Zet de zaklamp uit (alles donker), wat je muis ook doet. */
    public void tourDark() {
        lightX = 0;
        lightY = 0;
    }

    /** Kiest een stekker zonder te knippen (-1 = de goede: die van de draad naar de LENS; -2 = een verkeerde). */
    public void tourSelect(int wire) {
        if (phase == Phase.PLAY) {
            selected = wire == -1 ? tangle.lensStart() : wire == -2 ? (tangle.lensStart() + 1) % tangle.wires() : wire;
        }
    }

    /** Knipt de gekozen stekker door (zoals CUT; dit gaat echt naar de server). */
    public void tourCut() {
        if (phase == Phase.PLAY && selected >= 0) {
            cut();
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
