package com.senne.starktech.client.hud;

import static com.senne.starktech.client.hud.HudStyle.GREEN;
import static com.senne.starktech.client.hud.HudStyle.argb;
import static com.senne.starktech.client.hud.HudStyle.brackets;
import static com.senne.starktech.client.hud.HudStyle.smallText;

import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.client.WorldProjection;
import net.minecraft.client.gui.GuiGraphics;

/** Pings van jezelf en anderen: groene beugels met naam en afstand. */
public final class PingWidget implements HudWidget {

    @Override
    public boolean visible(HudContext c) {
        return c.powered && !EdithClientState.pings.isEmpty();
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        for (EdithClientState.Ping ping : EdithClientState.pings) {
            WorldProjection.ScreenPoint p = c.projection.project(ping.pos());
            if (p == null || !c.projection.isOnScreen(p, 0)) {
                continue;
            }
            long left = ping.endMillis() - c.now;
            int alpha = left < 1500 ? (int) (255 * left / 1500.0) : 255;
            int x = (int) p.x();
            int y = (int) p.y();
            // Een uitdijende ring bij het begin, daarna een rustig pulserend kader.
            long age = c.now - ping.startMillis();
            int size = age < 600 ? 4 + (int) (age / 30) : 6 + (int) (2 * Math.sin(c.now / 150.0));
            brackets(g, x - size, y - size, x + size, y + size, 3, argb(alpha, GREEN));
            g.fill(x - 1, y - 1, x + 1, y + 1, argb(alpha, GREEN));
            int distance = (int) Math.round(Math.sqrt(c.cameraPosition().distanceToSqr(ping.pos())));
            smallText(g, c.font, ping.sender() + " · " + distance + "m", x, y - size - 8, argb(alpha, GREEN));
        }
    }
}
