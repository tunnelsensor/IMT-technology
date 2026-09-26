package com.senne.starktech.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.network.HackResultPayload;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Het hack-scherm van de Stark-laptop: de minigame om een Stark Tower over te nemen.
 *
 * Er zijn drie lagen: FIREWALL, ENCRYPTION en OVERRIDE. In elke laag schuift een cursor heen en
 * weer over een balk, met ergens een groen vak. Druk op SPATIE (of klik) als de cursor in het
 * groene vak staat: raak! Dan springt het vak naar een andere plek. Mis je, dan verlies je een
 * treffer. Elke laag gaat sneller en het vak wordt kleiner.
 *
 * Het spel loopt door terwijl dit scherm open is: je kunt niet weglopen, en de beveiliging
 * schiet op je. Snel en nauwkeurig zijn dus. Esc = afbreken.
 */
public class HackScreen extends Screen {
    private static final String[] LAYERS = {"firewall", "encryption", "override"};
    /** Zoveel treffers per laag. */
    private static final int[] HITS = {3, 4, 5};
    /** Hoe vaak per seconde de cursor heen en terug gaat. */
    private static final double[] SPEED = {0.45, 0.65, 0.9};
    /** Hoe breed het groene vak is (deel van de balk). */
    private static final double[] ZONE = {0.24, 0.17, 0.12};

    /** Voor de showcase: het scherm speelt zichzelf (en mist één keer, zodat je dat ook ziet). */
    public static boolean autoPlay;

    private final BlockPos core;
    private final RandomSource random = RandomSource.create();
    private int layer;
    private int hits;
    private double zoneStart;
    private long layerStart = Util.getMillis();
    /** Tot wanneer (ms) het vak groen (raak) of rood (mis) oplicht. */
    private long flashUntil;
    private boolean flashGood;
    private long doneAt;
    private boolean autoMissed;

    public HackScreen(BlockPos core) {
        super(Component.translatable("screen.starktech.hack"));
        this.core = core;
        newZone();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean done() {
        return layer >= LAYERS.length;
    }

    /** Waar de cursor nu is, van 0 (links) tot 1 (rechts). */
    private double cursor() {
        double t = (Util.getMillis() - layerStart) / 1000.0 * SPEED[Math.min(layer, LAYERS.length - 1)] * 2;
        double phase = t % 2;
        return phase < 1 ? phase : 2 - phase;
    }

    private boolean inZone() {
        double c = cursor();
        double zone = ZONE[Math.min(layer, LAYERS.length - 1)];
        return c >= zoneStart && c <= zoneStart + zone;
    }

    private void newZone() {
        double zone = ZONE[Math.min(layer, LAYERS.length - 1)];
        zoneStart = 0.05 + random.nextDouble() * (0.9 - zone);
    }

    /** Spatie of klik: raak of mis. */
    private void press() {
        if (done()) {
            return;
        }
        long now = Util.getMillis();
        if (inZone()) {
            hits++;
            flashGood = true;
            EdithClientEvents.playUi(SoundEvents.NOTE_BLOCK_BIT.value(), 1.2f + hits * 0.1f);
            if (hits >= HITS[layer]) {
                layer++;
                hits = 0;
                layerStart = now;
                EdithClientEvents.playUi(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f + layer * 0.2f);
                if (done()) {
                    doneAt = now;
                    PacketDistributor.sendToServer(new HackResultPayload(core, true));
                    return;
                }
            }
            newZone();
        } else {
            hits = Math.max(0, hits - 1);
            flashGood = false;
            EdithClientEvents.playUi(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f);
        }
        flashUntil = now + 250;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_SPACE) {
            press();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers); // Esc = afbreken
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        press();
        return true;
    }

    @Override
    public void tick() {
        if (done()) {
            if (Util.getMillis() - doneAt > 1200) {
                onClose();
            }
            return;
        }
        if (autoPlay && Util.getMillis() - layerStart > 400) {
            // Eén keer expres missen, dan netjes in het midden van het vak.
            double middle = zoneStart + ZONE[layer] / 2;
            if (!autoMissed && layer == 1 && Math.abs(cursor() - middle) > 0.3) {
                autoMissed = true;
                press();
            } else if (Math.abs(cursor() - middle) < ZONE[layer] / 4) {
                press();
            }
        }
    }

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
        layer = LAYERS.length;
        super.onClose();
    }

    public BlockPos core() {
        return core;
    }

    // ================================================================ Tekenen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        HudStyle.dim(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int w = Math.min(320, width - 40);
        int h = 150;
        int x0 = (width - w) / 2;
        int y0 = (height - h) / 2;
        long now = Util.getMillis();
        g.fill(x0, y0, x0 + w, y0 + h, HudStyle.argb(210, 0x02121A));
        HudStyle.brackets(g, x0, y0, x0 + w, y0 + h, 8, HudStyle.argb(230, HudStyle.CYAN));
        int cx = x0 + w / 2;
        g.drawCenteredString(font, Component.translatable("screen.starktech.hack.title"), cx, y0 + 8,
                HudStyle.argb(255, HudStyle.CYAN));

        // De drie lagen: klaar (groen), bezig (wit) of nog dicht (grijs).
        for (int i = 0; i < LAYERS.length; i++) {
            int lx = x0 + w * (2 * i + 1) / 6;
            int color = i < layer ? HudStyle.GREEN : i == layer ? HudStyle.WHITE : 0x55707A;
            String mark = i < layer ? "✓ " : "";
            g.drawCenteredString(font, mark + Component.translatable("screen.starktech.hack.layer." + LAYERS[i]).getString(),
                    lx, y0 + 26, HudStyle.argb(255, color));
        }

        if (done()) {
            g.drawCenteredString(font, Component.translatable("screen.starktech.hack.granted"), cx, y0 + 70,
                    HudStyle.argb(255, HudStyle.GREEN));
            return;
        }

        // De treffers in deze laag, als bolletjes.
        int need = HITS[layer];
        int px0 = cx - need * 6;
        for (int i = 0; i < need; i++) {
            int color = i < hits ? HudStyle.GREEN : 0x33505A;
            g.fill(px0 + i * 12 + 2, y0 + 44, px0 + i * 12 + 10, y0 + 50, HudStyle.argb(255, color));
        }

        // De balk, het vak en de cursor.
        int bx0 = x0 + 20;
        int bx1 = x0 + w - 20;
        int by0 = y0 + 66;
        int by1 = y0 + 86;
        g.fill(bx0, by0, bx1, by1, HudStyle.argb(255, 0x0A2530));
        HudStyle.brackets(g, bx0, by0, bx1, by1, 4, HudStyle.argb(200, HudStyle.CYAN));
        int zx0 = bx0 + (int) ((bx1 - bx0) * zoneStart);
        int zx1 = bx0 + (int) ((bx1 - bx0) * (zoneStart + ZONE[layer]));
        boolean flashing = now < flashUntil;
        int zoneColor = flashing ? (flashGood ? HudStyle.GREEN : HudStyle.RED) : 0x2FB36A;
        g.fill(zx0, by0 + 2, zx1, by1 - 2, HudStyle.argb(flashing ? 230 : 170, zoneColor));
        int cursorX = bx0 + (int) ((bx1 - bx0) * cursor());
        g.fill(cursorX - 1, by0 - 3, cursorX + 1, by1 + 3, HudStyle.argb(255, HudStyle.WHITE));

        Component status = flashing && !flashGood
                ? Component.translatable("screen.starktech.hack.denied")
                : Component.translatable("screen.starktech.hack.hint");
        g.drawCenteredString(font, status, cx, y0 + 100, HudStyle.argb(255, flashing && !flashGood ? HudStyle.RED : HudStyle.WHITE));
        g.drawCenteredString(font, Component.translatable("screen.starktech.hack.warning"), cx, y0 + 124,
                HudStyle.argb(200, HudStyle.ORANGE));
    }
}
