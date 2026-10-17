package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.CYAN;
import static com.senne.imttech.client.hud.HudStyle.ORANGE;
import static com.senne.imttech.client.hud.HudStyle.argb;

import com.senne.imttech.client.DroneCamera;
import com.senne.imttech.client.LaserCannonControl;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Zolang de laser cannon aan staat: een ring van puntjes rond je vizier die na elk schot weer
 * volloopt. Vol = klaar om te vuren.
 */
public final class LaserCannonWidget implements HudWidget {
    /** Zoveel puntjes in de ring. */
    private static final int DOTS = 24;
    private static final int RADIUS = 11;

    @Override
    public boolean visible(HudContext c) {
        return c.powered && !c.menuOpen && LaserCannonControl.isArmed() && !DroneCamera.isActive();
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        int cx = c.width / 2;
        int cy = c.height / 2;
        float charge = LaserCannonControl.charge(c.mc.getTimer().getGameTimeDeltaPartialTick(false));
        boolean ready = charge >= 1.0f;
        // Klaar: de ring "ademt" een beetje, zodat je ziet dat hij geladen is.
        int readyAlpha = (int) (190 + 60 * Mth.sin(c.now / 160.0f));

        int lit = Math.round(charge * DOTS);
        for (int i = 0; i < DOTS; i++) {
            // Beginnen bovenaan en met de klok mee.
            double angle = -Math.PI / 2 + i * (Math.PI * 2 / DOTS);
            int x = cx + (int) Math.round(Math.cos(angle) * RADIUS);
            int y = cy + (int) Math.round(Math.sin(angle) * RADIUS);
            int colour;
            if (ready) {
                colour = argb(readyAlpha, CYAN);
            } else if (i < lit) {
                colour = argb(220, ORANGE);
            } else {
                colour = argb(60, CYAN);
            }
            g.fill(x, y, x + 1, y + 1, colour);
        }
        // Vier streepjes buiten de ring, als op een vizier.
        int tick = argb(ready ? 220 : 110, CYAN);
        g.fill(cx - RADIUS - 5, cy, cx - RADIUS - 2, cy + 1, tick);
        g.fill(cx + RADIUS + 3, cy, cx + RADIUS + 6, cy + 1, tick);
        g.fill(cx, cy - RADIUS - 5, cx + 1, cy - RADIUS - 2, tick);
        g.fill(cx, cy + RADIUS + 3, cx + 1, cy + RADIUS + 6, tick);

        HudStyle.smallText(g, c.font, Component.translatable("hud.imttech.laser_cannon").getString(), cx + 1,
                cy + RADIUS + 9, ready ? argb(220, CYAN) : argb(220, ORANGE));
    }
}
