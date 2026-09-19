package com.senne.starktech.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Het rapport van de spionagedrone: alles wat het doelwit deed, regel voor regel,
 * met de tijd erbij. Je kunt scrollen met het wiel of de pijltjes.
 *
 * Dit scherm opent zelf zodra een opdracht klaar is, en je kunt het later terugvinden
 * via het G-menu (tabblad Drones > Rapport).
 */
public class SpyReportScreen extends Screen {
    private static final int PANEL_W = 340;
    private static final int LINE_H = 11;
    /** Hoeveel regels er tegelijk in het scherm passen. */
    private static final int ROWS = 14;

    private final Component targetName;
    private final List<Component> lines;
    private int scroll;
    private int left;
    private int top;

    public SpyReportScreen(Component targetName, List<Component> lines) {
        super(Component.translatable("screen.starktech.spy_report"));
        this.targetName = targetName;
        this.lines = lines;
        // Meteen onderaan beginnen: het laatste nieuws is het interessantst.
        this.scroll = Math.max(0, lines.size() - ROWS);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(8, (height - (ROWS * LINE_H + 70)) / 2);
        int bottom = top + 40 + ROWS * LINE_H + 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + PANEL_W - 88, bottom, 80, 20).build());
        if (lines.size() > ROWS) {
            addRenderableWidget(Button.builder(Component.literal("^"), button -> scroll(-ROWS + 1))
                    .bounds(left + 8, bottom, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("v"), button -> scroll(ROWS - 1))
                    .bounds(left + 32, bottom, 20, 20).build());
        }
    }

    private void scroll(int delta) {
        scroll = Mth.clamp(scroll + delta, 0, Math.max(0, lines.size() - ROWS));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll((int) -Math.signum(scrollY));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int height = 40 + ROWS * LINE_H + 36;
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + height, EdithHud.argb(210, 0x08121A));
        EdithHud.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + height, 8, EdithHud.argb(220, EdithHud.CYAN));
        super.extractRenderState(g, mouseX, mouseY, a);

        g.text(font, title, left + 8, top + 8, EdithHud.argb(255, EdithHud.CYAN), true);
        g.text(font, Component.translatable("screen.starktech.spy_report.target", targetName), left + 8, top + 22,
                EdithHud.argb(230, EdithHud.WHITE), true);
        String count = lines.size() + Component.translatable("screen.starktech.spy_report.lines").getString();
        g.text(font, count, left + PANEL_W - 8 - font.width(count), top + 8, EdithHud.argb(170, EdithHud.WHITE), true);

        int y = top + 40;
        if (lines.isEmpty()) {
            g.centeredText(font, Component.translatable("screen.starktech.spy_report.empty"), left + PANEL_W / 2, y,
                    EdithHud.argb(160, EdithHud.WHITE));
            return;
        }
        for (int i = scroll; i < Math.min(lines.size(), scroll + ROWS); i++) {
            g.text(font, lines.get(i), left + 8, y, EdithHud.argb(235, EdithHud.WHITE), false);
            y += LINE_H;
        }
        // Schuifbalkje aan de rechterkant.
        if (lines.size() > ROWS) {
            int barTop = top + 40;
            int barHeight = ROWS * LINE_H;
            int knob = Math.max(10, barHeight * ROWS / lines.size());
            int offset = (barHeight - knob) * scroll / Math.max(1, lines.size() - ROWS);
            g.fill(left + PANEL_W - 4, barTop, left + PANEL_W - 2, barTop + barHeight, EdithHud.argb(60, EdithHud.CYAN));
            g.fill(left + PANEL_W - 5, barTop + offset, left + PANEL_W - 1, barTop + offset + knob, EdithHud.argb(220, EdithHud.CYAN));
        }
    }
}
