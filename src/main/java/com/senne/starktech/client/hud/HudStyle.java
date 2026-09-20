package com.senne.starktech.client.hud;

import com.senne.starktech.network.EdithMessagePayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Hoe E.D.I.T.H. eruitziet: de kleuren en de paar tekenhulpjes die overal terugkomen.
 *
 * Alles wordt getekend met rechthoekjes (fill) en tekst, dus er zijn geen plaatjes nodig.
 * Kleuren staan hier zonder doorzichtigheid (RGB); met {@link #argb} plak je er een
 * doorzichtigheid voor, want Minecraft wil 0xAARRGGBB.
 *
 * Ook de schermen (het G-menu, de navigatie, de relaislijst...) tekenen hiermee, zodat de
 * hele mod dezelfde look heeft.
 */
public final class HudStyle {
    private HudStyle() {}

    // ---------------------------------------------------------------- Kleuren (zonder alpha)
    public static final int CYAN = 0x3FD8FF;
    public static final int WHITE = 0xE8FBFF;
    public static final int ORANGE = 0xFFA93F;
    public static final int RED = 0xFF4545;
    public static final int GREEN = 0x5CFF9D;
    /** Paars: alles van de spionagedrone. */
    public static final int PURPLE = 0xD02BFF;

    /** Een kleur met doorzichtigheid: 0 = onzichtbaar, 255 = helemaal dicht. */
    public static int argb(int alpha, int rgb) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Vier L-vormige hoekjes (zoals een camera-zoeker). */
    public static void brackets(GuiGraphics g, int x0, int y0, int x1, int y1, int len, int color) {
        len = Math.min(len, Math.min((x1 - x0) / 2, (y1 - y0) / 2));
        // linksboven
        g.fill(x0, y0, x0 + len, y0 + 1, color);
        g.fill(x0, y0, x0 + 1, y0 + len, color);
        // rechtsboven
        g.fill(x1 - len, y0, x1, y0 + 1, color);
        g.fill(x1 - 1, y0, x1, y0 + len, color);
        // linksonder
        g.fill(x0, y1 - 1, x0 + len, y1, color);
        g.fill(x0, y1 - len, x0 + 1, y1, color);
        // rechtsonder
        g.fill(x1 - len, y1 - 1, x1, y1, color);
        g.fill(x1 - 1, y1 - len, x1, y1, color);
    }

    /** Een balkje dat voor een deel gevuld is (fraction 0.0 - 1.0). */
    public static void bar(GuiGraphics g, int x, int y, int width, int height, float fraction, int rgb) {
        fraction = Mth.clamp(fraction, 0.0f, 1.0f);
        g.fill(x, y, x + width, y + height, argb(60, rgb));
        g.fill(x, y, x + Math.round(width * fraction), y + height, argb(230, rgb));
    }

    /** Kleine, gecentreerde tekst (75% van normaal). */
    public static void smallText(GuiGraphics g, Font font, String text, int centerX, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(centerX, y, 0.0f);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(font, text, -font.width(text) / 2, 0, color, true);
        g.pose().popPose();
    }

    /** Kleur die bij een soort melding hoort. */
    public static int colorFor(EdithMessagePayload.Level level) {
        return switch (level) {
            case BOOT, INFO -> CYAN;
            case WARNING -> ORANGE;
            case ALERT -> RED;
            case COMMS -> GREEN;
        };
    }
}
