package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.RED;
import static com.senne.imttech.client.hud.HudStyle.argb;
import static com.senne.imttech.client.hud.HudStyle.smallText;

import com.senne.imttech.client.ImtClientState;
import com.senne.imttech.client.WorldProjection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Wat jou aanvalt, in beeld gebracht.
 *
 * Staat de dreiging op je scherm, dan krijgt hij een rood pijltje boven zijn hoofd met de
 * afstand erbij. Zit hij achter je of buiten beeld, dan verschijnt er een pijl aan de rand
 * van het scherm die de kant op wijst waar hij vandaan komt - een kompas dus, zodat je
 * meteen weet welke kant je op moet kijken.
 */
public final class ThreatMarkerWidget implements HudWidget {
    /** Meer dan zoveel dreigingen tekenen we niet; anders staat je scherm vol pijltjes. */
    private static final int MAX_DRAWN = 16;

    @Override
    public boolean visible(HudContext c) {
        return c.powered && !ImtClientState.threats.isEmpty();
    }

    @Override
    public void render(HudContext c) {
        int drawn = 0;
        for (Entity threat : ImtClientState.threats) {
            if (drawn >= MAX_DRAWN) {
                break;
            }
            drawn++;
            Vec3 at = c.projection.drawnPosition(threat);
            Vec3 top = new Vec3(at.x, at.y + threat.getBbHeight() + 0.4, at.z);
            WorldProjection.ScreenPoint point = c.projection.project(top);
            if (point != null && c.projection.isOnScreen(point, 0)) {
                markOnScreen(c, threat, point);
            } else {
                markOffScreen(c, at);
            }
        }
    }

    /** Een pijltje boven het doelwit, met hoe ver het van je af is. */
    private void markOnScreen(HudContext c, Entity threat, WorldProjection.ScreenPoint point) {
        GuiGraphics g = c.g;
        int x = (int) point.x();
        int y = (int) point.y();
        g.fill(x - 3, y - 4, x + 4, y - 3, argb(230, RED));
        g.fill(x - 2, y - 3, x + 3, y - 2, argb(230, RED));
        g.fill(x - 1, y - 2, x + 2, y - 1, argb(230, RED));
        g.fill(x, y - 1, x + 1, y, argb(230, RED));
        smallText(g, c.font, Math.round(c.player.distanceTo(threat)) + "m", x, y - 11, argb(220, RED));
    }

    /**
     * Een kompaspijl aan de rand van het scherm: hij wijst naar de dreiging die je niet ziet.
     *
     * We rekenen de hoek uit tussen de kant waar jij naar kijkt en de kant waar de dreiging
     * vandaan komt. Die hoek zetten we op een cirkel rond het midden van je scherm, en daar
     * tekenen we een driehoekje dat naar buiten wijst.
     */
    private void markOffScreen(HudContext c, Vec3 threatPos) {
        Player player = c.player;
        double dx = threatPos.x - player.getX();
        double dz = threatPos.z - player.getZ();
        if (dx * dx + dz * dz < 0.01) {
            return;
        }
        // Hoek van de dreiging ten opzichte van waar je naartoe kijkt (0 = recht vooruit).
        float toThreat = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        float relative = Mth.wrapDegrees(toThreat - player.getYRot()) * Mth.DEG_TO_RAD;

        int centerX = c.width / 2;
        int centerY = c.height / 2;
        int radius = Math.min(c.width, c.height) / 2 - 24;
        // Rechts van je = positieve x op het scherm, vóór je = omhoog.
        int x = centerX + (int) (Math.sin(relative) * radius);
        int y = centerY - (int) (Math.cos(relative) * radius);
        arrowAt(c.g, x, y, relative, argb(235, RED));
    }

    /** Een driehoekje op (x, y) dat naar buiten wijst, weg van het midden. */
    private void arrowAt(GuiGraphics g, int x, int y, float angle, int colour) {
        // Zeven laagjes die steeds smaller worden: samen een pijl van het midden af gezien.
        double outX = Math.sin(angle);
        double outY = -Math.cos(angle);
        for (int step = 0; step < 7; step++) {
            int half = 3 - step / 2;
            int px = x + (int) Math.round(outX * step);
            int py = y + (int) Math.round(outY * step);
            // Dwars op de richting uitsmeren, zodat de punt naar buiten wijst.
            int sx = (int) Math.round(-outY * half);
            int sy = (int) Math.round(outX * half);
            g.fill(px - Math.abs(sx) - 1, py - Math.abs(sy) - 1,
                    px + Math.abs(sx) + 1, py + Math.abs(sy) + 1, colour);
        }
    }
}
