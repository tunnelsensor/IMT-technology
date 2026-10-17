package com.senne.imttech.client.bypass;

import com.senne.imttech.bypass.PanelOrders;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.network.BypassPayload;
import com.senne.imttech.registry.ModSounds;
import java.util.ArrayList;
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
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Een codepaneel kraken (hoofdstuk 12, jouw keuze): vingerafdrukken en de volgorde.
 * <pre>
 *  BYPASS &gt; FRONT DOOR                 CODE: 4 digits
 *  [1*][2 ][3 ]     1 1 4 7   ..      0 right
 *  [4*][5 ][6 ]     4 1 1 7   ##      2 right
 *  [7*][8 ][9 ]
 *  [C ][0 ][OK]     Possible orders: 3     Tries left: 4
 * </pre>
 * <b>Stoffen</b>: houd de muisknop ingedrukt en veeg over de toetsen. Op de cijfers van de code
 * verschijnen vingerafdrukken (een cijfer dat twee keer in de code zit: dikkere afdrukken). Als alle
 * cijfers gestoft zijn, begint vanzelf <b>de volgorde</b>: je typt een volgorde met alleen die cijfers,
 * de kit probeert hem op de chip van het paneel en hoort één klik per cijfer op de goede plek. De server
 * kent de code en zegt hoeveel er goed staan; de code zelf komt pas op je scherm als je hem hebt.
 */
public class FingerprintScreen extends Screen implements BypassScreen {
    private enum Phase { DUST, ORDER, WAIT, DONE }

    private static final int KEY = 28;
    private static final int GAP = 4;
    private static final int W = 340;
    private static final int H = 236;
    private static final String[] KEYS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK"};
    /** Zo snel (per tick) wordt een toets gestoft terwijl je erover veegt: ongeveer een derde seconde per toets. */
    private static final float DUST_PER_TICK = 0.16f;
    private static final int RESULT_TICKS = 70;

    /** Voor de showcase: het scherm speelt zichzelf (stoffen, raden). */
    public static boolean autoPlay;

    private final BlockPos panel;
    private final String panelName;
    private final int length;
    /** Per cijfer 0-9 hoe vaak het in de code zit (zo dik zijn de afdrukken). */
    private final int[] prints;
    private int triesLeft;

    private Phase phase = Phase.DUST;
    /** Per toets (zoals KEYS) hoeveel stof erop ligt, 0 tot 1. */
    private final float[] dust = new float[KEYS.length];
    private boolean brushing;
    private double mouseX;
    private double mouseY;
    private int dustedTicks;
    /** Wat je nu typt. */
    private String typed = "";
    /** Je pogingen en hoeveel er op de goede plek stonden. */
    private final List<PanelOrders.Try> tries = new ArrayList<>();
    private long possible;
    /** Klikjes die nog moeten klinken (één per goed cijfer), en wanneer de volgende. */
    private int clicksToPlay;
    private int clickTimer;
    private String result = "";
    private String cracked = "";
    private int resultTicks;
    private boolean finished;
    private int autoTicks;
    /** Kleine stofjes die van de kwast vallen. */
    private final List<float[]> specks = new ArrayList<>();

    public FingerprintScreen(BlockPos panel, String panelName, int length, int[] prints, int triesLeft) {
        super(Component.literal(panelName));
        this.panel = panel;
        this.panelName = panelName;
        this.length = length;
        this.prints = prints.length == 10 ? prints.clone() : new int[10];
        this.triesLeft = triesLeft;
        this.possible = PanelOrders.count(this.prints, length, tries);
    }

    @Override
    public BlockPos device() {
        return panel;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        HudStyle.dim(g, width, height);
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int keyX(int index) {
        return left() + 14 + (index % 3) * (KEY + GAP);
    }

    private int keyY(int index) {
        return top() + 62 + (index / 3) * (KEY + GAP);
    }

    private int keyAt(double x, double y) {
        for (int i = 0; i < KEYS.length; i++) {
            if (x >= keyX(i) && x < keyX(i) + KEY && y >= keyY(i) && y < keyY(i) + KEY) {
                return i;
            }
        }
        return -1;
    }

    /** Het cijfer van een toets, of -1 voor C en OK. */
    private static int digitOf(int index) {
        String key = KEYS[index];
        return key.length() == 1 && Character.isDigit(key.charAt(0)) ? key.charAt(0) - '0' : -1;
    }

    /** Zitten er afdrukken op deze toets? (C en OK gebruik je altijd, dus daar ook.) */
    private boolean printed(int index) {
        int digit = digitOf(index);
        return digit < 0 || prints[digit] > 0;
    }

    private boolean allDusted() {
        for (int i = 0; i < KEYS.length; i++) {
            if (digitOf(i) >= 0 && dust[i] < 1) {
                return false;
            }
        }
        return true;
    }

    // ================================================================ Tekenen

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        int frame = phase == Phase.DONE ? (result.equals("cracked") ? HudStyle.GREEN : HudStyle.RED) : HudStyle.CYAN;
        g.fill(x, y, x + W, y + H, HudStyle.argb(240, 0x0D1318));
        HudStyle.brackets(g, x, y, x + W, y + H, 8, HudStyle.argb(255, frame));
        g.drawString(font, "BYPASS > " + panelName.toUpperCase(Locale.ROOT), x + 10, y + 9, HudStyle.argb(255, HudStyle.CYAN), false);
        String size = "CODE: " + length + (length == 1 ? " digit" : " digits");
        g.drawString(font, size, x + W - 10 - font.width(size), y + 9, HudStyle.argb(255, HudStyle.WHITE), false);

        // Het schermpje van het paneel: wat je typt, met streepjes voor wat nog moet.
        int sy = y + 26;
        g.fill(x + 14, sy, x + 14 + 3 * KEY + 2 * GAP, sy + 26, HudStyle.argb(255, 0x0A1418));
        g.renderOutline(x + 14, sy, 3 * KEY + 2 * GAP, 26, HudStyle.argb(255, 0x33505F));
        String shown = phase == Phase.DONE && !cracked.isEmpty() ? cracked : typed;
        StringBuilder slots = new StringBuilder();
        for (int i = 0; i < length; i++) {
            slots.append(i < shown.length() ? shown.charAt(i) : '_');
            if (i < length - 1) {
                slots.append(' ');
            }
        }
        int slotColour = phase == Phase.DONE && result.equals("cracked") ? HudStyle.GREEN : HudStyle.WHITE;
        g.drawString(font, slots.toString(), x + 14 + (3 * KEY + 2 * GAP - font.width(slots.toString())) / 2, sy + 9,
                HudStyle.argb(255, slotColour), false);

        // De toetsen, met stof en afdrukken.
        for (int i = 0; i < KEYS.length; i++) {
            drawKey(g, i, mouseX, mouseY);
        }
        for (float[] speck : specks) {
            g.fill((int) speck[0], (int) speck[1], (int) speck[0] + 1, (int) speck[1] + 1,
                    HudStyle.argb((int) (speck[3] * 220), 0xD8D2C4));
        }

        // Rechts: uitleg, of je pogingen.
        int rx = x + 14 + 3 * KEY + 2 * GAP + 18;
        int rw = x + W - 12 - rx;
        if (phase == Phase.DUST) {
            int ty = y + 30;
            ty = paragraph(g, "Fingers leave grease on the keys they press.", rx, ty, rw, HudStyle.WHITE);
            ty = paragraph(g, "Hold the mouse button and brush powder over every number key.", rx, ty + 4, rw, 0x7F93A0);
            int done = 0;
            for (int i = 0; i < KEYS.length; i++) {
                if (digitOf(i) >= 0 && dust[i] >= 1) {
                    done++;
                }
            }
            g.drawString(font, "Keys dusted: " + done + " / 10", rx, ty + 10, HudStyle.argb(255, HudStyle.CYAN), false);
            HudStyle.bar(g, rx, ty + 22, rw, 4, done / 10f, HudStyle.CYAN);
            if (done == 10) {
                paragraph(g, summary(), rx, ty + 34, rw, HudStyle.GREEN);
            }
        } else {
            renderOrder(g, rx, y + 28, rw);
        }

        // Onderaan: wat je kunt doen, of hoe het afliep.
        int by = y + H - 26;
        int cx = x + W / 2;
        String one;
        String two;
        int colour = HudStyle.WHITE;
        if (phase == Phase.DONE) {
            boolean ok = result.equals("cracked");
            one = ok ? "CODE CRACKED: " + cracked + ". The kit typed it in." : "The panel locked itself and raised the alarm.";
            two = ok ? "Remember it: next time you just type it." : "The owner is warned, the security hunts you.";
            colour = ok ? HudStyle.GREEN : HudStyle.RED;
        } else if (phase == Phase.DUST) {
            one = "Hold the mouse button and move over the keys.";
            two = "Esc: stop";
        } else {
            one = "The kit tries your order and counts the panel's clicks.";
            two = "Click or type the digits    Enter: try    Backspace    Esc: stop";
        }
        g.drawString(font, one, cx - font.width(one) / 2, by, HudStyle.argb(255, colour), false);
        g.drawString(font, two, cx - font.width(two) / 2, by + 11, HudStyle.argb(255, 0x7F93A0), false);
    }

    /** Een toets: grijs, met stof (lichte spikkels) en, als hij gestoft is, de vingerafdrukken. */
    private void drawKey(GuiGraphics g, int index, int mouseX, int mouseY) {
        int kx = keyX(index);
        int ky = keyY(index);
        boolean hover = keyAt(mouseX, mouseY) == index;
        int digit = digitOf(index);
        boolean usable = phase == Phase.ORDER && (digit >= 0 ? prints[digit] > 0 : true);
        boolean dim = phase != Phase.DUST && digit >= 0 && prints[digit] == 0;
        int base = dim ? 0x101820 : hover && (phase == Phase.DUST || usable) ? 0x1E3A48 : 0x16232C;
        g.fill(kx, ky, kx + KEY, ky + KEY, HudStyle.argb(255, base));
        int accent = KEYS[index].equals("OK") ? HudStyle.GREEN : KEYS[index].equals("C") ? HudStyle.RED : HudStyle.CYAN;
        HudStyle.brackets(g, kx, ky, kx + KEY, ky + KEY, 4, HudStyle.argb(dim ? 90 : 255, accent));
        // Het stof: spikkels op vaste plekken (per toets altijd dezelfde), meer naarmate er meer ligt.
        int specksOnKey = (int) (Mth.clamp(dust[index], 0, 1) * 34);
        long seed = index * 7919L + 17;
        for (int i = 0; i < specksOnKey; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int sx = kx + 2 + (int) ((seed >>> 33) % (KEY - 4));
            int sy = ky + 2 + (int) ((seed >>> 17) % (KEY - 4));
            g.fill(sx, sy, sx + 1, sy + 1, HudStyle.argb(150, 0xC8C2B4));
        }
        // De afdruk: een paar ovale ringen, schuin, dikker bij een cijfer dat vaker in de code zit.
        int lx = kx + (KEY - font.width(KEYS[index])) / 2;
        int ly = ky + (KEY - 8) / 2;
        if (dust[index] >= 1 && printed(index)) {
            int count = digit < 0 ? 1 : prints[digit];
            drawPrint(g, kx + KEY / 2, ky + KEY / 2, count, index);
            // Een donker vlakje achter het cijfer, anders lees je het niet door de afdruk heen.
            g.fill(lx - 2, ly - 2, lx + font.width(KEYS[index]) + 1, ly + 9, HudStyle.argb(210, 0x0D1318));
        }
        g.drawString(font, KEYS[index], lx, ly, HudStyle.argb(dim ? 90 : 255, dim ? 0x5A6670 : HudStyle.WHITE), false);
    }

    /**
     * Een vingerafdruk: 3 (of meer) ovale ringen van stipjes, een beetje scheef. Lichtgeel: het poeder is
     * glowstonestof, dat in de afdruk blijft plakken en zacht oplicht.
     */
    private static void drawPrint(GuiGraphics g, int cx, int cy, int count, int index) {
        int rings = Math.min(6, 2 + count);
        int alpha = Math.min(255, 120 + 60 * count);
        double tilt = 0.4 + index * 0.37;
        for (int ring = 1; ring <= rings; ring++) {
            double rx = 2.0 + ring * 1.7;
            double ry = 1.4 + ring * 1.3;
            int points = 8 + ring * 6;
            for (int p = 0; p < points; p++) {
                if ((p + ring) % 5 == 0) {
                    continue; // een onderbroken lijntje, zoals een echte afdruk
                }
                double a = p * Math.PI * 2 / points;
                double px = Math.cos(a) * rx;
                double py = Math.sin(a) * ry;
                int x = cx + (int) Math.round(px * Math.cos(tilt) - py * Math.sin(tilt));
                int y = cy + (int) Math.round(px * Math.sin(tilt) + py * Math.cos(tilt));
                g.fill(x, y, x + 1, y + 1, HudStyle.argb(alpha, 0xFFE89A));
            }
        }
    }

    private void renderOrder(GuiGraphics g, int rx, int ty, int rw) {
        ty = paragraph(g, summary(), rx, ty, rw, HudStyle.GREEN);
        ty += 4;
        if (tries.isEmpty()) {
            ty = paragraph(g, "Type an order with these digits and press Enter. You hear one click for every digit "
                    + "in the right place.", rx, ty, rw, 0x7F93A0);
        }
        int shown = Math.max(0, tries.size() - 6);
        for (int i = shown; i < tries.size(); i++) {
            PanelOrders.Try attempt = tries.get(i);
            StringBuilder digits = new StringBuilder();
            for (char c : attempt.code().toCharArray()) {
                digits.append(c).append(' ');
            }
            g.drawString(font, digits.toString().trim(), rx, ty, HudStyle.argb(255, HudStyle.WHITE), false);
            int dx = rx + font.width("0 ") * length + 6;
            for (int d = 0; d < length; d++) {
                g.fill(dx + d * 6, ty + 2, dx + d * 6 + 4, ty + 6,
                        HudStyle.argb(255, d < attempt.right() ? HudStyle.GREEN : 0x2A3A44));
            }
            String right = attempt.right() + " right";
            g.drawString(font, right, rx + rw - font.width(right), ty, HudStyle.argb(255, HudStyle.GREEN), false);
            ty += 11;
        }
        ty += 6;
        g.drawString(font, "Possible orders: " + possible, rx, ty, HudStyle.argb(255, HudStyle.CYAN), false);
        String left = "Tries left: " + triesLeft;
        g.drawString(font, left, rx, ty + 11, HudStyle.argb(255, triesLeft <= 1 ? HudStyle.RED : HudStyle.WHITE), false);
        if (phase == Phase.WAIT) {
            g.drawString(font, "Listening...", rx, ty + 24, HudStyle.argb(255, HudStyle.ORANGE), false);
        }
    }

    /** "Prints on 1, 4 and 7. The 1 is extra dirty: twice in the code." */
    private String summary() {
        List<String> digits = new ArrayList<>();
        List<String> twice = new ArrayList<>();
        for (int d = 0; d < 10; d++) {
            if (prints[d] > 0) {
                digits.add(String.valueOf(d));
            }
            if (prints[d] > 1) {
                twice.add(d + (prints[d] > 2 ? " (" + prints[d] + "x)" : ""));
            }
        }
        StringBuilder text = new StringBuilder("Prints on ").append(join(digits)).append('.');
        if (!twice.isEmpty()) {
            text.append(" Extra dirty: ").append(join(twice))
                    .append(twice.size() == 1 ? " is in the code more than once." : " are in the code more than once.");
        }
        return text.toString();
    }

    private static String join(List<String> parts) {
        if (parts.size() <= 1) {
            return parts.isEmpty() ? "nothing" : parts.getFirst();
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
    }

    /** Tekst die over meer regels mag; geeft de y onder de laatste regel. */
    private int paragraph(GuiGraphics g, String text, int x, int y, int width, int colour) {
        for (var line : font.split(Component.literal(text), width)) {
            g.drawString(font, line, x, y, HudStyle.argb(255, colour), false);
            y += 10;
        }
        return y;
    }

    // ================================================================ Muis en toetsen

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && phase == Phase.DUST) {
            brushing = true;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            return true;
        }
        if (button == 0 && phase == Phase.ORDER) {
            int key = keyAt(mouseX, mouseY);
            if (key >= 0) {
                press(key);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            brushing = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (phase == Phase.ORDER && codePoint >= '0' && codePoint <= '9') {
            press(codePoint == '0' ? 10 : codePoint - '1');
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (phase == Phase.ORDER) {
            if (keyCode == 259) { // backspace
                if (!typed.isEmpty()) {
                    typed = typed.substring(0, typed.length() - 1);
                    play(ModSounds.IMT_SELECT.get(), 1.2f, 0.3f);
                }
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // enter
                press(11);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Een toets in de volgorde-stap: een cijfer erbij (alleen met afdrukken), C = wissen, OK = proberen. */
    private void press(int index) {
        String key = KEYS[index];
        if (key.equals("C")) {
            typed = "";
            play(ModSounds.IMT_SELECT.get(), 0.9f, 0.3f);
        } else if (key.equals("OK")) {
            if (typed.length() == length) {
                CompoundTag args = new CompoundTag();
                args.putString("code", typed);
                PacketDistributor.sendToServer(new BypassPayload(panel, "guess", args));
                phase = Phase.WAIT;
                resultTicks = 0;
                play(ModSounds.IMT_BLIP.get(), 1.0f, 0.5f);
            } else {
                play(ModSounds.IMT_DENIED.get(), 1.4f, 0.3f);
            }
        } else if (prints[digitOf(index)] > 0 && typed.length() < length) {
            typed += key;
            play(ModSounds.IMT_SELECT.get(), 1.5f + 0.05f * digitOf(index), 0.3f);
        } else {
            play(ModSounds.IMT_DENIED.get(), 1.6f, 0.2f);
        }
    }

    // ================================================================ Elke tick

    @Override
    public void tick() {
        super.tick();
        specks.removeIf(speck -> {
            speck[1] += speck[2];
            speck[2] += 0.15f;
            speck[3] -= 0.05f;
            return speck[3] <= 0;
        });
        if (clicksToPlay > 0 && --clickTimer <= 0) {
            clicksToPlay--;
            clickTimer = 4;
            play(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f, 0.35f);
        }
        switch (phase) {
            case DUST -> tickDust();
            case ORDER -> {
                if (autoPlay && ++autoTicks >= 25) {
                    autoTicks = 0;
                    autoGuess();
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

    private void tickDust() {
        if (autoPlay) {
            // De showcase veegt van links naar rechts over alle toetsen.
            int key = (autoTicks++ / 3) % KEYS.length;
            mouseX = keyX(key) + KEY / 2.0;
            mouseY = keyY(key) + KEY / 2.0;
            brushing = true;
        }
        if (brushing) {
            int key = keyAt(mouseX, mouseY);
            if (key >= 0 && dust[key] < 1) {
                dust[key] = Math.min(1, dust[key] + DUST_PER_TICK * (autoPlay ? 2 : 1));
                if (minecraft != null && minecraft.level != null) {
                    var random = minecraft.level.random;
                    specks.add(new float[] {(float) mouseX + random.nextInt(9) - 4, (float) mouseY, random.nextFloat() * 0.5f, 1f});
                }
                if (autoTicks % 4 == 0 || !autoPlay) {
                    play(SoundEvents.BRUSH_GENERIC, 1.2f, 0.25f);
                }
                if (dust[key] >= 1 && printed(key) && digitOf(key) >= 0) {
                    play(ModSounds.IMT_BLIP.get(), 1.7f, 0.35f);
                }
            }
        }
        if (allDusted() && ++dustedTicks >= 25) {
            phase = Phase.ORDER;
            brushing = false;
            autoTicks = 0;
            play(ModSounds.IMT_CONFIRM.get(), 1.3f, 0.5f);
        }
    }

    /** De showcase raadt zelf: steeds een volgorde die nog kan. */
    private void autoGuess() {
        String guess = PanelOrders.firstPossible(prints, length, tries);
        if (guess != null) {
            typed = guess;
            press(11);
        }
    }

    // ================================================================ Antwoorden van de server

    /** De server probeerde je volgorde: zoveel cijfers stonden goed. */
    public void onGuess(String code, int right, int left) {
        if (phase != Phase.WAIT) {
            return;
        }
        tries.add(new PanelOrders.Try(code, right));
        triesLeft = left;
        possible = PanelOrders.count(prints, length, tries);
        typed = "";
        clicksToPlay = right;
        clickTimer = 6;
        phase = Phase.ORDER;
        if (right == 0) {
            play(ModSounds.IMT_DENIED.get(), 1.2f, 0.3f);
        }
    }

    @Override
    public void onResult(CompoundTag args) {
        if (phase == Phase.DONE) {
            return;
        }
        finished = true;
        result = args.getString("result");
        cracked = args.getString("code");
        if (result.equals("cracked")) {
            tries.add(new PanelOrders.Try(cracked, length));
            clicksToPlay = length;
            clickTimer = 6;
            play(ModSounds.IMT_CONFIRM.get(), 1.0f, 1.0f);
        } else {
            play(ModSounds.IMT_DENIED.get(), 0.8f, 1.0f);
        }
        phase = Phase.DONE;
        resultTicks = 0;
    }

    @Override
    public void closeFromServer() {
        finished = true;
        onClose();
    }

    @Override
    public void removed() {
        super.removed();
        if (!finished) {
            PacketDistributor.sendToServer(BypassPayload.of(panel, "cancel"));
        }
    }

    // ================================================================ Voor de screenshot-rondleiding

    /** Stoft zoveel toetsen in één keer (in de volgorde 1, 2, 3, ...). */
    public void tourDust(int keys) {
        for (int i = 0; i < Math.min(keys, KEYS.length); i++) {
            dust[i] = 1;
        }
    }

    /** Typt deze cijfers alvast (in de volgorde-stap). */
    public void tourType(String digits) {
        if (phase == Phase.DUST) {
            tourDust(KEYS.length);
            phase = Phase.ORDER;
        }
        typed = digits.length() > length ? digits.substring(0, length) : digits;
    }

    /** Probeert de volgorde die er nu staat. */
    public void tourTry() {
        if (phase == Phase.ORDER) {
            press(11);
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
