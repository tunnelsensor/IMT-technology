package com.senne.starktech.client.hud;

import static com.senne.starktech.client.hud.HudStyle.WHITE;
import static com.senne.starktech.client.hud.HudStyle.argb;
import static com.senne.starktech.client.hud.HudStyle.colorFor;

import com.senne.starktech.client.EdithClientState;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Meldingen van E.D.I.T.H., met een typmachine-effect.
 *
 * Ze staan midden bovenaan, maar schuiven naar rechts als het paneel links in de weg zit
 * (daarom leest dit onderdeel c.panelRight, dat het statuspaneel net heeft gezet).
 */
public final class NotificationWidget implements HudWidget {
    /** Hoeveel milliseconde er tussen twee letters zit bij het "typen". */
    private static final int MILLIS_PER_CHAR = 18;

    @Override
    public boolean visible(HudContext c) {
        return !c.menuOpen && !EdithClientState.NOTIFICATIONS.isEmpty();
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        EdithClientState.NOTIFICATIONS.removeIf(n -> c.now - n.startMillis() > EdithClientState.NOTIFICATION_MILLIS);
        int y = 36;
        for (EdithClientState.Notification n : EdithClientState.NOTIFICATIONS) {
            long age = c.now - n.startMillis();
            int alpha = age > EdithClientState.NOTIFICATION_MILLIS - 1000
                    ? (int) (255 * (EdithClientState.NOTIFICATION_MILLIS - age) / 1000.0)
                    : 255;
            if (alpha < 8) {
                continue;
            }
            String full = n.text().getString();
            int chars = (int) Math.min(full.length(), age / MILLIS_PER_CHAR);
            String shown = full.substring(0, chars);
            int color = colorFor(n.level());
            String prefix = "E.D.I.T.H. › ";
            int totalWidth = c.font.width(prefix + full);
            int x = c.width / 2 - totalWidth / 2;
            // Zou de melding over het paneel links vallen? Schuif hem er dan naast (als dat past).
            int besidePanel = c.panelRight + 8;
            if (x < besidePanel && besidePanel + totalWidth + 10 <= c.width) {
                x = besidePanel;
            }
            g.fill(x - 4, y - 2, x + totalWidth + 4, y + 10, argb(alpha / 3, 0x001018));
            g.fill(x - 4, y - 2, x - 3, y + 10, argb(alpha, color));
            g.drawString(c.font, prefix, x, y, argb(alpha * 2 / 3, color), true);
            g.drawString(c.font, shown, x + c.font.width(prefix), y, argb(alpha, WHITE), true);
            y += 14;
        }
    }
}
