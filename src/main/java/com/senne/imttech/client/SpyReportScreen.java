package com.senne.imttech.client;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.spy.SpyLog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Het rapport van de spionagedrone: alles wat het doelwit deed, regel voor regel,
 * met de tijd erbij. Je kunt scrollen met het wiel of de pijltjes.
 *
 * Een lange regel (zoals het rapport van elke 30 seconden) breekt af op de breedte van het
 * paneel (hoofdstuk 129; vroeger liep hij er rechts uit). De tijd staat in een eigen kolom
 * links, en de rest van de regel staat eronder, naast die kolom. Scrollen gaat per regel
 * zoals je hem op het scherm ziet.
 *
 * Dit scherm opent zelf zodra een opdracht klaar is, en je kunt het later terugvinden
 * via het G-menu (tabblad Drones > Rapport).
 */
public class SpyReportScreen extends Screen {
    /** Zo breed is het paneel (op een smal scherm wordt het smaller). */
    private static final int PANEL_W = 340;
    private static final int LINE_H = 11;
    /** Hoeveel regels er tegelijk in het scherm passen (op een laag scherm minder). */
    private static final int ROWS = 14;
    /** Zoveel regels schuift één tik van het muiswiel (ongeveer één rapport van twee regels en een beetje). */
    private static final int WHEEL_ROWS = 3;

    /**
     * Eén regel op het scherm: een stuk van een regel uit het logboek.
     *
     * @param stamp  de tijd, alleen bij het eerste stuk (anders null)
     * @param text   het stuk tekst
     * @param indent hoe ver de tekst naar rechts staat (de breedte van de tijdkolom)
     */
    private record Row(@Nullable Component stamp, FormattedCharSequence text, int indent) {}

    private final Component targetName;
    private final List<Component> lines;
    /** De regels zoals ze op het scherm staan (na het afbreken); init() maakt ze opnieuw. */
    private final List<Row> rows = new ArrayList<>();
    private int panelW = PANEL_W;
    private int rowsShown = ROWS;
    private int scroll;
    /** Wat er van het muiswiel nog overblijft: minder dan één regel (zie mouseScrolled). */
    private double wheel;
    /** Pas na de eerste init() true: dan was het scherm al open (init() loopt opnieuw als het venster verandert). */
    private boolean opened;
    private int left;
    private int top;

    public SpyReportScreen(Component targetName, List<Component> lines) {
        super(Component.translatable("screen.imttech.spy_report"));
        this.targetName = targetName;
        this.lines = lines;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        // Bij het openen begin je onderaan: het laatste nieuws is het interessantst. Stond je onderaan, dan blijf je
        // daar, ook als het venster later van grootte verandert en de regels anders afbreken; anders blijft de
        // bovenste regel de bovenste.
        boolean atBottom = !opened || scroll >= maxScroll();
        // Op een klein venster (of met een grote GUI-schaal) wordt het paneel smaller en lager, zodat het past.
        panelW = Math.min(PANEL_W, width - 16);
        rowsShown = Mth.clamp((height - 84) / LINE_H, 3, ROWS);
        left = (width - panelW) / 2;
        top = Math.max(8, (height - (rowsShown * LINE_H + 70)) / 2);
        layoutRows();
        scroll = atBottom ? maxScroll() : Mth.clamp(scroll, 0, maxScroll());
        opened = true;

        int bottom = top + 40 + rowsShown * LINE_H + 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + panelW - 88, bottom, 80, 20).build());
        if (rows.size() > rowsShown) {
            addRenderableWidget(Button.builder(Component.literal("^"), button -> scroll(-rowsShown + 1))
                    .bounds(left + 8, bottom, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("v"), button -> scroll(rowsShown - 1))
                    .bounds(left + 32, bottom, 20, 20).build());
        }
    }

    /**
     * Knipt elke regel uit het logboek in stukken die in het paneel passen. De tijd ("[3:07] ") komt in
     * een kolom links, zo breed als de breedste tijd; de tekst breekt af op de ruimte ernaast.
     */
    private void layoutRows() {
        rows.clear();
        List<SpyLog.Entry> entries = lines.stream().map(SpyLog::split).toList();
        int stampColumn = 0;
        for (SpyLog.Entry entry : entries) {
            stampColumn = Math.max(stampColumn, font.width(entry.stamp()));
        }
        for (SpyLog.Entry entry : entries) {
            // Een regel zonder tijd (die maakt de server niet, maar toch) begint gewoon helemaal links.
            boolean stamped = !entry.stamp().getString().isEmpty();
            int indent = stamped ? stampColumn : 0;
            List<FormattedCharSequence> parts = font.split(entry.text(), Math.max(20, textWidth() - indent));
            if (parts.isEmpty()) {
                parts = List.of(FormattedCharSequence.EMPTY); // een lege tekst: dan toch de tijd tonen
            }
            for (int i = 0; i < parts.size(); i++) {
                rows.add(new Row(i == 0 && stamped ? entry.stamp() : null, parts.get(i), indent));
            }
        }
    }

    /** Zo breed mag een regel zijn: het paneel min de marge links en de marge rechts (met het schuifbalkje). */
    private int textWidth() {
        return panelW - 20;
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - rowsShown);
    }

    private void scroll(int delta) {
        scroll = Mth.clamp(scroll + delta, 0, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        // Eén tik van het wiel = WHEEL_ROWS regels. Een touchpad stuurt kleinere stukjes: die tellen we op.
        wheel -= scrollY * WHEEL_ROWS;
        int whole = (int) wheel;
        wheel -= whole;
        scroll(whole);
        return true;
    }

    /**
     * Het paneel hoort bij de achtergrond. Minecraft tekent in render() eerst een wazige
     * achtergrond; wat daarvóór getekend was, werd dus mee vervaagd en verduisterd.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        int panelH = 40 + rowsShown * LINE_H + 36;
        g.fill(left - 4, top - 4, left + panelW + 4, top + panelH, HudStyle.argb(210, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + panelW + 4, top + panelH, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);

        g.drawString(font, title, left + 8, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);
        // Het doelwit kan een lange naam hebben (een naamplaatje): dan knippen we hem af.
        g.drawString(font, fit(Component.translatable("screen.imttech.spy_report.target", targetName), panelW - 16),
                left + 8, top + 22, HudStyle.argb(230, HudStyle.WHITE), true);
        String count = lines.size() + Component.translatable("screen.imttech.spy_report.lines").getString();
        g.drawString(font, count, left + panelW - 8 - font.width(count), top + 8, HudStyle.argb(170, HudStyle.WHITE), true);

        int y = top + 40;
        if (lines.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.imttech.spy_report.empty"), left + panelW / 2, y,
                    HudStyle.argb(160, HudStyle.WHITE));
            return;
        }
        int color = HudStyle.argb(235, HudStyle.WHITE);
        for (int i = scroll; i < Math.min(rows.size(), scroll + rowsShown); i++) {
            Row row = rows.get(i);
            if (row.stamp() != null) {
                g.drawString(font, row.stamp(), left + 8, y, color, false);
            }
            g.drawString(font, row.text(), left + 8 + row.indent(), y, color, false);
            y += LINE_H;
        }
        // Schuifbalkje aan de rechterkant.
        if (rows.size() > rowsShown) {
            int barTop = top + 40;
            int barHeight = rowsShown * LINE_H;
            int knob = Math.max(10, barHeight * rowsShown / rows.size());
            int offset = (barHeight - knob) * scroll / Math.max(1, maxScroll());
            g.fill(left + panelW - 4, barTop, left + panelW - 2, barTop + barHeight, HudStyle.argb(60, HudStyle.CYAN));
            g.fill(left + panelW - 5, barTop + offset, left + panelW - 1, barTop + offset + knob, HudStyle.argb(220, HudStyle.CYAN));
        }
    }

    /** Past een tekst niet in de ruimte, dan knippen we hem af met "..." erachter (de kleuren blijven). */
    private FormattedCharSequence fit(Component text, int room) {
        if (font.width(text) <= room) {
            return text.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, room - font.width("...")));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("...")));
    }
}
