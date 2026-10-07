package com.senne.starktech.client.os;

import com.senne.starktech.client.hud.HudStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Kleine tekenhulpjes die de vensters van de Stark OS-desktop delen: een knop en een vlak met een
 * kader. Alles in de kleuren van {@link HudStyle}, zodat de desktop eruitziet als de rest van
 * E.D.I.T.H.
 */
final class OsDraw {
    private OsDraw() {}

    /** Zo hoog is een knop (pixels). */
    static final int BUTTON_H = 12;
    /** De donkere achtergrond van een invoervak of lijst. */
    static final int FIELD = 0x0A141B;
    /** De kleur van een rand die niet actief is. */
    static final int DIM = 0x33505F;
    /** Grijze tekst (uitleg, uitgeschakelde knoppen). */
    static final int GREY = 0x8A9BA0;

    /** Hoe breed een knop met deze tekst is. */
    static int buttonWidth(Font font, String label) {
        return font.width(label) + 8;
    }

    /**
     * Tekent een knop op (x, y) en geeft zijn breedte terug. Een knop die niet kan ({@code enabled}
     * = false) is grijs; {@code accent} is de kleur van de tekst en de rand (meestal CYAN, RED
     * voor iets gevaarlijks).
     */
    static int button(GuiGraphics g, Font font, int x, int y, String label, boolean enabled, int accent) {
        int w = buttonWidth(font, label);
        int colour = enabled ? accent : 0x4A5A62;
        g.fill(x, y, x + w, y + BUTTON_H, HudStyle.argb(255, enabled ? 0x16303F : 0x101A21));
        HudStyle.brackets(g, x, y, x + w, y + BUTTON_H, 3, HudStyle.argb(255, colour));
        g.drawString(font, label, x + 4, y + 2, HudStyle.argb(255, colour), false);
        return w;
    }

    /** Een donker vlak met een rand (invoervak, lijst). {@code active} = de rand licht op. */
    static void field(GuiGraphics g, int x0, int y0, int x1, int y1, boolean active) {
        g.fill(x0, y0, x1, y1, HudStyle.argb(255, FIELD));
        HudStyle.brackets(g, x0, y0, x1, y1, 4, HudStyle.argb(255, active ? HudStyle.CYAN : DIM));
    }

    /** Ligt (mx, my) in dit vak? */
    static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Knipt tekst af (met ".." erachter) zodat hij in {@code width} pixels past; het begin blijft staan. */
    static String fitRight(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && font.width(cut + "..") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "..";
    }

    /** Knipt tekst af (met ".." ervoor) zodat hij in {@code width} pixels past; handig voor lange paden. */
    static String fitLeft(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && font.width(".." + cut) > width) {
            cut = cut.substring(1);
        }
        return ".." + cut;
    }
}
