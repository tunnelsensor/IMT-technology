package com.senne.starktech.client;

import com.senne.starktech.client.hud.HudStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Een scrollbalk: een smalle goot met een blokje (de "duim") dat laat zien welk stuk je ziet.
 * Je kunt het blokje slepen, of ergens in de goot klikken om daarheen te springen.
 *
 * De balk rekent in "eenheden": dat mogen regels zijn (een lijst) of pixels (een kolom met
 * velden). Het scherm vertelt hem hoeveel er in totaal is ({@code content}), hoeveel er in
 * beeld past ({@code view}) en waar je nu bent ({@code scroll}); hij geeft terug waar je heen
 * wilt als je sleept.
 *
 * Gebruik:
 * 1. in init(): {@code bar.set(x, y, hoogte, totaal, inBeeld, scroll)};
 * 2. in render(): {@code bar.render(g, mouseX, mouseY)};
 * 3. in mouseClicked / mouseDragged / mouseReleased: doorgeven, en bij een nieuwe waarde het
 *    scherm opnieuw opbouwen.
 */
public final class ScrollBar {
    public static final int WIDTH = 5;

    private int x;
    private int y;
    private int height;
    private int content;
    private int view;
    private int scroll;
    private boolean dragging;
    /** Waar je het blokje vastpakte, gemeten vanaf de bovenkant van het blokje. */
    private double grabOffset;

    public void set(int x, int y, int height, int content, int view, int scroll) {
        this.x = x;
        this.y = y;
        this.height = height;
        this.content = content;
        this.view = view;
        this.scroll = scroll;
    }

    /** Is er meer dan in beeld past? Anders hoeft de balk er niet te zijn. */
    public boolean needed() {
        return content > view && height > 8;
    }

    /** Hoe ver je maximaal kunt scrollen. */
    public int max() {
        return Math.max(0, content - view);
    }

    private int thumbHeight() {
        return Math.max(10, Math.round(height * (float) view / content));
    }

    private int thumbY() {
        return y + Math.round((height - thumbHeight()) * (max() == 0 ? 0 : (float) scroll / max()));
    }

    public boolean isOver(double mouseX, double mouseY) {
        return needed() && mouseX >= x - 1 && mouseX < x + WIDTH + 1 && mouseY >= y && mouseY < y + height;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (!needed()) {
            return;
        }
        g.fill(x, y, x + WIDTH, y + height, HudStyle.argb(90, 0x000000));
        g.fill(x + 2, y + 1, x + 3, y + height - 1, HudStyle.argb(50, HudStyle.CYAN));
        int ty = thumbY();
        boolean hot = dragging || (mouseX >= x - 1 && mouseX < x + WIDTH + 1 && mouseY >= ty && mouseY < ty + thumbHeight());
        g.fill(x, ty, x + WIDTH, ty + thumbHeight(), HudStyle.argb(hot ? 240 : 180, HudStyle.CYAN));
        g.fill(x + 1, ty + 1, x + WIDTH - 1, ty + thumbHeight() - 1, HudStyle.argb(hot ? 255 : 210, 0x9BEBFF));
    }

    /**
     * Klik op de balk. Op het blokje: vastpakken. Ergens anders in de goot: het blokje springt
     * erheen (met zijn midden onder je muis). Geeft de nieuwe scroll terug, of -1 als de klik
     * niet op de balk was.
     */
    public int mouseClicked(double mouseX, double mouseY) {
        if (!isOver(mouseX, mouseY)) {
            return -1;
        }
        int ty = thumbY();
        if (mouseY >= ty && mouseY < ty + thumbHeight()) {
            grabOffset = mouseY - ty;
        } else {
            grabOffset = thumbHeight() / 2.0;
        }
        dragging = true;
        return scrollFor(mouseY);
    }

    /** Slepen: de nieuwe scroll, of -1 als je de balk niet vasthebt. */
    public int mouseDragged(double mouseY) {
        return dragging ? scrollFor(mouseY) : -1;
    }

    public void mouseReleased() {
        dragging = false;
    }

    public boolean isDragging() {
        return dragging;
    }

    private int scrollFor(double mouseY) {
        int room = height - thumbHeight();
        if (room <= 0) {
            return 0;
        }
        double fraction = (mouseY - grabOffset - y) / room;
        return Mth.clamp((int) Math.round(fraction * max()), 0, max());
    }
}
