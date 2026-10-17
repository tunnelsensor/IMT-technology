package com.senne.imttech.client;

import com.senne.imttech.client.hud.HudStyle;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
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
        super(Component.translatable("screen.imttech.spy_report"));
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

    /**
     * Het paneel hoort bij de achtergrond. Minecraft tekent in render() eerst een wazige
     * achtergrond; wat daarvóór getekend was, werd dus mee vervaagd en verduisterd.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        int panelH = 40 + ROWS * LINE_H + 36;
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + panelH, HudStyle.argb(210, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + panelH, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);

        g.drawString(font, title, left + 8, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);
        g.drawString(font, Component.translatable("screen.imttech.spy_report.target", targetName), left + 8, top + 22,
                HudStyle.argb(230, HudStyle.WHITE), true);
        String count = lines.size() + Component.translatable("screen.imttech.spy_report.lines").getString();
        g.drawString(font, count, left + PANEL_W - 8 - font.width(count), top + 8, HudStyle.argb(170, HudStyle.WHITE), true);

        int y = top + 40;
        if (lines.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.imttech.spy_report.empty"), left + PANEL_W / 2, y,
                    HudStyle.argb(160, HudStyle.WHITE));
            return;
        }
        for (int i = scroll; i < Math.min(lines.size(), scroll + ROWS); i++) {
            g.drawString(font, lines.get(i), left + 8, y, HudStyle.argb(235, HudStyle.WHITE), false);
            y += LINE_H;
        }
        // Schuifbalkje aan de rechterkant.
        if (lines.size() > ROWS) {
            int barTop = top + 40;
            int barHeight = ROWS * LINE_H;
            int knob = Math.max(10, barHeight * ROWS / lines.size());
            int offset = (barHeight - knob) * scroll / Math.max(1, lines.size() - ROWS);
            g.fill(left + PANEL_W - 4, barTop, left + PANEL_W - 2, barTop + barHeight, HudStyle.argb(60, HudStyle.CYAN));
            g.fill(left + PANEL_W - 5, barTop + offset, left + PANEL_W - 1, barTop + offset + knob, HudStyle.argb(220, HudStyle.CYAN));
        }
    }
}
