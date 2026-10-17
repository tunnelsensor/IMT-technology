package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.WHITE;
import static com.senne.imttech.client.hud.HudStyle.argb;
import static com.senne.imttech.client.hud.HudStyle.colorFor;

import com.senne.imttech.client.ImtClientState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Meldingen van I.M.T, met een typmachine-effect.
 *
 * Ze staan midden bovenaan, maar schuiven naar rechts als het paneel links in de weg zit
 * (daarom leest dit onderdeel c.panelRight, dat het statuspaneel net heeft gezet).
 */
public final class NotificationWidget implements HudWidget {
    /** Hoeveel milliseconde er tussen twee letters zit bij het "typen". */
    private static final int MILLIS_PER_CHAR = 18;

    @Override
    public boolean visible(HudContext c) {
        return !c.menuOpen && !ImtClientState.NOTIFICATIONS.isEmpty();
    }

    /** Knipt een tekst in regels van hooguit {@code width} pixels breed (op de spaties). */
    static List<String> wrap(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(candidate) > width) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        lines.add(line.toString());
        return lines;
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        ImtClientState.NOTIFICATIONS.removeIf(n -> c.now - n.startMillis() > ImtClientState.NOTIFICATION_MILLIS);
        int y = 36;
        for (ImtClientState.Notification n : ImtClientState.NOTIFICATIONS) {
            long age = c.now - n.startMillis();
            int alpha = age > ImtClientState.NOTIFICATION_MILLIS - 1000
                    ? (int) (255 * (ImtClientState.NOTIFICATION_MILLIS - age) / 1000.0)
                    : 255;
            if (alpha < 8) {
                continue;
            }
            String full = n.text().getString();
            int chars = (int) Math.min(full.length(), age / MILLIS_PER_CHAR);
            int color = colorFor(n.level());
            String prefix = "I.M.T › ";
            int prefixWidth = c.font.width(prefix);
            // Een lange melding (bv. een antwoord van het AI-brein) over meerdere regels.
            List<String> lines = wrap(c.font, full, Math.max(80, Math.min(c.width - 40, 360) - prefixWidth));
            int textWidth = lines.stream().mapToInt(c.font::width).max().orElse(0);
            int totalWidth = prefixWidth + textWidth;
            int height = lines.size() * 10;
            int x = c.width / 2 - totalWidth / 2;
            // Zou de melding over het paneel links vallen? Schuif hem er dan naast (als dat past).
            int besidePanel = c.panelRight + 8;
            if (x < besidePanel && besidePanel + totalWidth + 10 <= c.width) {
                x = besidePanel;
            }
            g.fill(x - 4, y - 2, x + totalWidth + 4, y + height, argb(alpha / 3, 0x001018));
            g.fill(x - 4, y - 2, x - 3, y + height, argb(alpha, color));
            g.drawString(c.font, prefix, x, y, argb(alpha * 2 / 3, color), true);
            // Letter voor letter verschijnen, regel na regel.
            int left = chars;
            for (int i = 0; i < lines.size() && left > 0; i++) {
                String line = lines.get(i);
                g.drawString(c.font, line.substring(0, Math.min(line.length(), left)), x + prefixWidth, y + i * 10,
                        argb(alpha, WHITE), true);
                left -= line.length() + 1;
            }
            y += height + 4;
        }
    }
}
